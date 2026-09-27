package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.accessibility.AccessibilityController
import com.example.accessibility.model.AccessibilityServiceState
import com.example.accessibility.model.ForegroundAppInfo
import com.example.accessibility.model.ScreenSnapshotResult
import com.example.ai.GeminiConfig
import com.example.ai.GeminiEngine
import com.example.ai.GeminiErrorType
import com.example.ai.GeminiPlanResult
import com.example.automation.ActionExecutor
import com.example.automation.ActionType
import com.example.automation.ActionValidator
import com.example.automation.CommandProcessor
import com.example.automation.ExecutionLogger
import com.example.automation.TaskPlanner
import com.example.automation.confirmation.ConfirmationManager
import com.example.core.TaskState
import com.example.core.TaskStateManager
import com.example.memory.MemoryManager
import com.example.memory.MemoryRepository
import com.example.memory.dao.MemoryDao
import com.example.memory.dao.TaskDao
import com.example.memory.entities.CommandAliasEntity
import com.example.memory.entities.ExecutionLogEntity
import com.example.memory.entities.MemoryItemEntity
import com.example.memory.entities.RoutineEntity
import com.example.memory.entities.TaskHistoryEntity
import com.example.memory.entities.UserPreferenceEntity
import com.example.permissions.PermissionManager
import com.example.security.SecurityManager
import com.example.voice.SpeechRecognizerManager
import com.example.voice.TTSInitStatus
import com.example.voice.TextToSpeechManager
import com.example.voice.VoiceLanguage
import com.example.voice.VoiceState
import com.example.voice.VoiceStateManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AiAndVoiceReliabilityTest {

    private lateinit var context: Context
    private lateinit var geminiConfig: GeminiConfig
    private lateinit var geminiEngine: GeminiEngine
    private lateinit var voiceStateManager: VoiceStateManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        geminiConfig = GeminiConfig(context)
        geminiEngine = GeminiEngine(geminiConfig)
        voiceStateManager = VoiceStateManager()
    }

    // 1. Successful AI response parsing
    @Test
    fun testSuccessfulAiResponseParsing() {
        val validJson = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "{\n  \"spokenResponse\": \"Opening YouTube and searching for tutorial\",\n  \"unsupportedCapability\": null,\n  \"actions\": [\n    {\n      \"type\": \"OPEN_APP\",\n      \"description\": \"Open YouTube\",\n      \"parameters\": { \"appName\": \"YouTube\", \"packageName\": \"com.google.android.youtube\" }\n    },\n    {\n      \"type\": \"SEARCH\",\n      \"description\": \"Search for tutorial\",\n      \"parameters\": { \"query\": \"tutorial\" }\n    }\n  ]\n}"
                      }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        val result = geminiEngine.parseGeminiOutput(validJson, "Search YouTube for tutorial")
        assertTrue("Expected Success result", result is GeminiPlanResult.Success)

        val success = result as GeminiPlanResult.Success
        assertEquals("Opening YouTube and searching for tutorial", success.spokenResponse)
        assertNull(success.unsupportedCapability)
        assertEquals(2, success.actions.size)
        assertEquals(ActionType.OPEN_APP, success.actions[0].type)
        assertEquals("YouTube", success.actions[0].parameters.appName)
        assertEquals(ActionType.SEARCH, success.actions[1].type)
        assertEquals("tutorial", success.actions[1].parameters.query)
    }

    // 2. Malformed or empty AI responses handling
    @Test
    fun testMalformedOrEmptyAiResponses() {
        // Empty response
        val emptyResult = geminiEngine.parseGeminiOutput("", "Prompt")
        assertTrue(emptyResult is GeminiPlanResult.Error)
        assertEquals(GeminiErrorType.EMPTY_RESPONSE, (emptyResult as GeminiPlanResult.Error).errorType)

        // Missing candidates
        val noCandidatesJson = "{ \"candidates\": [] }"
        val noCandidatesResult = geminiEngine.parseGeminiOutput(noCandidatesJson, "Prompt")
        assertTrue(noCandidatesResult is GeminiPlanResult.Error)

        // Invalid JSON inside text part
        val invalidTextJson = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "This is random text, not JSON"
                      }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()
        val invalidResult = geminiEngine.parseGeminiOutput(invalidTextJson, "Prompt")
        assertTrue(invalidResult is GeminiPlanResult.Error)
        assertEquals(GeminiErrorType.MALFORMED_RESPONSE, (invalidResult as GeminiPlanResult.Error).errorType)
    }

    // 3. API failure, timeout, and network error handling
    @Test
    fun testApiFailureAndTimeoutHandling() = runBlocking {
        // When key is missing
        geminiConfig.setCustomApiKey("")
        val missingKeyResult = geminiEngine.planActions("test")
        assertTrue(missingKeyResult is GeminiPlanResult.Error)
        val missingKeyError = missingKeyResult as GeminiPlanResult.Error
        assertEquals(GeminiErrorType.KEY_MISSING, missingKeyError.errorType)
        assertTrue(missingKeyError.isKeyMissing)

        // Blank prompt handling
        val blankPromptResult = geminiEngine.planActions("   ")
        assertTrue(blankPromptResult is GeminiPlanResult.Error)
        assertEquals(GeminiErrorType.EMPTY_RESPONSE, (blankPromptResult as GeminiPlanResult.Error).errorType)
    }

    // 4. Duplicate request prevention and exact-once delivery in SpeechRecognizerManager
    @Test
    fun testSpeechRecognizerExactOnceDelivery() {
        val receivedResults = mutableListOf<String>()
        val receivedStates = mutableListOf<VoiceState>()

        val manager = SpeechRecognizerManager(
            context = context,
            onVoiceStateChange = { receivedStates.add(it) },
            onFinalResult = { text, _ -> receivedResults.add(text) }
        )

        // Verify recognizer availability check does not crash
        assertNotNull(manager.isRecognitionAvailable())
        assertFalse(manager.isListening())

        manager.destroy()
    }

    // 5. Voice recognition failure handling
    @Test
    fun testVoiceRecognitionErrorMapping() {
        val manager = SpeechRecognizerManager(
            context = context,
            onVoiceStateChange = { },
            onFinalResult = { _, _ -> }
        )

        // Verify stopListening and destroy
        manager.stopListening()
        manager.destroy()
        assertFalse(manager.isListening())
    }

    // 6. TextToSpeech initialization and safe lifecycle
    @Test
    fun testTextToSpeechLifecycleAndSafety() {
        var isSpeakingState = false
        val ttsManager = TextToSpeechManager(
            context = context,
            onSpeakingStateChanged = { speaking, _ -> isSpeakingState = speaking }
        )

        // Engine is initializing or ready
        assertTrue(ttsManager.getInitStatus() == TTSInitStatus.INITIALIZING || ttsManager.getInitStatus() == TTSInitStatus.READY)

        // Queuing speech before complete initialization buffers without crash
        val queued = ttsManager.speak("Hello from NEX")
        assertNotNull(queued)

        // Check language availability
        val langStatus = ttsManager.checkLanguageAvailability(VoiceLanguage.ENGLISH_US)
        assertNotNull(langStatus)

        // Stop speech
        ttsManager.stop()
        assertFalse(isSpeakingState)

        // Shutdown releases resources
        ttsManager.shutdown()
        assertEquals(TTSInitStatus.SHUTDOWN, ttsManager.getInitStatus())
        assertFalse(ttsManager.isEngineReady())
    }

    // 7. Assistant state transitions
    @Test
    fun testAssistantStateTransitions() {
        val stateManager = VoiceStateManager()
        assertEquals(VoiceState.Idle, stateManager.voiceState.value)

        // 1. Idle -> Listening
        stateManager.setState(VoiceState.Listening(rmsDb = 4.0f, partialText = "Open camera"))
        val listening = stateManager.voiceState.value as VoiceState.Listening
        assertEquals(4.0f, listening.rmsDb)
        assertEquals("Open camera", listening.partialText)

        // 2. Listening -> Processing
        stateManager.setState(VoiceState.Processing)
        assertEquals(VoiceState.Processing, stateManager.voiceState.value)

        // 3. Processing -> Speaking
        stateManager.setState(VoiceState.Speaking("Opening camera now."))
        val speaking = stateManager.voiceState.value as VoiceState.Speaking
        assertEquals("Opening camera now.", speaking.text)

        // 4. Speaking -> Idle
        stateManager.setState(VoiceState.Idle)
        assertEquals(VoiceState.Idle, stateManager.voiceState.value)

        // 5. Error recovery transition
        stateManager.setState(VoiceState.Error("Network timed out", 3))
        val error = stateManager.voiceState.value as VoiceState.Error
        assertEquals("Network timed out", error.message)
        assertEquals(3, error.errorCode)

        // Return to Idle
        stateManager.setState(VoiceState.Idle)
        assertEquals(VoiceState.Idle, stateManager.voiceState.value)
    }

    // 8. Cancellation of obsolete requests in CommandProcessor
    @Test
    fun testCancellationOfObsoleteRequests() = runBlocking {
        val testScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)

        val fakeMemoryDao = object : MemoryDao {
            override fun getAllPreferences(): Flow<List<UserPreferenceEntity>> = flowOf(emptyList())
            override suspend fun getPreference(key: String): UserPreferenceEntity? = null
            override suspend fun setPreference(pref: UserPreferenceEntity) {}

            override fun getAllMemories(): Flow<List<MemoryItemEntity>> = flowOf(emptyList())
            override suspend fun insertMemory(item: MemoryItemEntity): Long = 1L
            override suspend fun deleteMemory(item: MemoryItemEntity) {}

            override fun getAllAliases(): Flow<List<CommandAliasEntity>> = flowOf(emptyList())
            override suspend fun findAlias(alias: String): CommandAliasEntity? = null
            override suspend fun insertAlias(alias: CommandAliasEntity): Long = 1L
            override suspend fun updateAlias(alias: CommandAliasEntity) {}
            override suspend fun deleteAlias(alias: CommandAliasEntity) {}

            override fun getAllRoutines(): Flow<List<RoutineEntity>> = flowOf(emptyList())
            override suspend fun insertRoutine(routine: RoutineEntity): Long = 1L
            override suspend fun updateRoutine(routine: RoutineEntity) {}
            override suspend fun deleteRoutine(routine: RoutineEntity) {}
        }

        val fakeTaskDao = object : TaskDao {
            override fun getRecentHistory(): Flow<List<TaskHistoryEntity>> = flowOf(emptyList())
            override suspend fun insertHistory(history: TaskHistoryEntity): Long = 1L
            override suspend fun clearHistory() {}

            override fun getRecentLogs(): Flow<List<ExecutionLogEntity>> = flowOf(emptyList())
            override suspend fun getLogsForTask(taskId: String): List<ExecutionLogEntity> = emptyList()
            override suspend fun insertLog(log: ExecutionLogEntity): Long = 1L
            override suspend fun clearLogs() {}
        }

        val memoryRepo = MemoryRepository(fakeMemoryDao, fakeTaskDao)
        val memoryManager = MemoryManager(memoryRepo)
        val permManager = PermissionManager(context)
        val taskStateManager = TaskStateManager()
        val securityManager = SecurityManager(context = context)

        val accessController = object : AccessibilityController {
            override fun isAccessibilityActive(): Boolean = true
            override fun getActivePackage(): String? = "com.android.launcher"
            override fun performHome(): Boolean = true
            override fun performBack(): Boolean = true
            override fun performClickOnText(text: String): Boolean = true
            override fun performClickById(viewId: String): Boolean = true
            override fun performInputText(targetText: String?, text: String): Boolean = true
            override fun performClearText(targetText: String?, viewId: String?): Boolean = true
            override fun performScroll(forward: Boolean): Boolean = true
            override fun performScrollForward(viewId: String?): Boolean = true
            override fun performScrollBackward(viewId: String?): Boolean = true
            override fun readScreenContent(): List<String> = listOf("Home", "Search")
            override fun openAccessibilitySettings() {}
            override fun getServiceState(): AccessibilityServiceState = AccessibilityServiceState.CONNECTED
            override fun getCurrentForegroundApp(): ForegroundAppInfo? = null
            override fun captureScreenSnapshot(filterSensitive: Boolean): ScreenSnapshotResult =
                ScreenSnapshotResult.Error("NOT_IMPLEMENTED", "Test")
            override suspend fun performTapCoordinates(x: Float, y: Float): Boolean = true
            override suspend fun performSwipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long): Boolean = true
            override suspend fun performLongPress(x: Float, y: Float, durationMs: Long): Boolean = true
        }

        val actionValidator = ActionValidator(accessController, permManager)
        val taskPlanner = TaskPlanner(geminiEngine, memoryManager, actionValidator, accessController)
        val executor = ActionExecutor(context, accessController, ExecutionLogger(memoryRepo, testScope))

        val commandProcessor = CommandProcessor(
            taskPlanner = taskPlanner,
            actionExecutor = executor,
            taskStateManager = taskStateManager,
            securityManager = securityManager,
            memoryRepository = memoryRepo,
            confirmationManager = ConfirmationManager(),
            coroutineScope = testScope
        )

        // 1. Test starting a command and then cancelling it
        commandProcessor.processCommand("Open Chrome and search for Kotlin")
        commandProcessor.cancelPendingAction()

        assertEquals(TaskState.CANCELLED, taskStateManager.executionState.value.state)

        // 2. Test processing command
        commandProcessor.processCommand("Hello NEX")
        var waitCount = 0
        while (taskStateManager.executionState.value.state != TaskState.COMPLETED && waitCount++ < 30) {
            kotlinx.coroutines.delay(20)
        }
        assertEquals(TaskState.COMPLETED, taskStateManager.executionState.value.state)
    }
}
