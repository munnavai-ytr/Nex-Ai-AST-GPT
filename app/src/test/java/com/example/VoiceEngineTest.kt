package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.accessibility.AccessibilityController
import com.example.ai.GeminiConfig
import com.example.ai.GeminiEngine
import com.example.automation.ActionType
import com.example.automation.ActionValidator
import com.example.automation.PlanningResult
import com.example.automation.TaskPlanner
import com.example.memory.MemoryManager
import com.example.memory.MemoryRepository
import com.example.memory.NexDatabase
import com.example.permissions.PermissionManager
import com.example.voice.TranscriptSender
import com.example.voice.VoiceLanguage
import com.example.voice.VoicePermissionManager
import com.example.voice.VoiceSettingsRepository
import com.example.voice.VoiceState
import com.example.voice.VoiceStateManager
import com.example.voice.WakeWordAvailability
import com.example.voice.WakeWordStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VoiceEngineTest {

    private lateinit var context: Context
    private lateinit var voiceStateManager: VoiceStateManager
    private lateinit var voiceSettingsRepository: VoiceSettingsRepository
    private lateinit var voicePermissionManager: VoicePermissionManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        voiceStateManager = VoiceStateManager()
        voiceSettingsRepository = VoiceSettingsRepository(context)
        voicePermissionManager = VoicePermissionManager(context)
    }

    @Test
    fun testVoiceStateTransitions() {
        assertEquals(VoiceState.Idle, voiceStateManager.voiceState.value)

        // Transition to Listening
        voiceStateManager.setState(VoiceState.Listening(rmsDb = 3.5f, partialText = "open"))
        val listeningState = voiceStateManager.voiceState.value as VoiceState.Listening
        assertEquals(3.5f, listeningState.rmsDb)
        assertEquals("open", listeningState.partialText)

        // Transition to Processing
        voiceStateManager.setState(VoiceState.Processing)
        assertEquals(VoiceState.Processing, voiceStateManager.voiceState.value)

        // Transition to Speaking
        voiceStateManager.setState(VoiceState.Speaking("Opening YouTube now."))
        val speakingState = voiceStateManager.voiceState.value as VoiceState.Speaking
        assertEquals("Opening YouTube now.", speakingState.text)

        // Transition to Error
        voiceStateManager.setState(VoiceState.Error("Audio error", 1))
        val errorState = voiceStateManager.voiceState.value as VoiceState.Error
        assertEquals("Audio error", errorState.message)

        // Transition to PermissionRequired
        voiceStateManager.setState(VoiceState.PermissionRequired("Mic required"))
        assertTrue(voiceStateManager.voiceState.value is VoiceState.PermissionRequired)

        // Transition to Unavailable
        voiceStateManager.setState(VoiceState.Unavailable("No recognizer"))
        assertTrue(voiceStateManager.voiceState.value is VoiceState.Unavailable)
    }

    @Test
    fun testVoiceTranscriptsManagement() {
        assertTrue(voiceStateManager.transcripts.value.isEmpty())

        voiceStateManager.addUserTranscript("Hey NEX open YouTube")
        assertEquals(1, voiceStateManager.transcripts.value.size)
        assertEquals(TranscriptSender.USER, voiceStateManager.transcripts.value[0].sender)
        assertEquals("Hey NEX open YouTube", voiceStateManager.transcripts.value[0].text)

        voiceStateManager.addNexResponse("Opening YouTube now.")
        assertEquals(2, voiceStateManager.transcripts.value.size)
        assertEquals(TranscriptSender.NEX, voiceStateManager.transcripts.value[1].sender)
        assertEquals("Opening YouTube now.", voiceStateManager.transcripts.value[1].text)

        // Clear transcripts
        voiceStateManager.clearTranscripts()
        assertTrue(voiceStateManager.transcripts.value.isEmpty())
    }

    @Test
    fun testVoiceSettingsPersistence() {
        voiceSettingsRepository.setLanguage(VoiceLanguage.BENGALI_BD)
        assertEquals(VoiceLanguage.BENGALI_BD, voiceSettingsRepository.settings.value.language)

        // Test speech rate clamping
        voiceSettingsRepository.setSpeechRate(1.25f)
        assertEquals(1.25f, voiceSettingsRepository.settings.value.speechRate, 0.01f)

        voiceSettingsRepository.setSpeechRate(5.0f) // Clamped to 2.0f
        assertEquals(2.0f, voiceSettingsRepository.settings.value.speechRate, 0.01f)

        voiceSettingsRepository.setSpeechRate(0.1f) // Clamped to 0.5f
        assertEquals(0.5f, voiceSettingsRepository.settings.value.speechRate, 0.01f)

        // Test pitch clamping
        voiceSettingsRepository.setPitch(1.5f)
        assertEquals(1.5f, voiceSettingsRepository.settings.value.pitch, 0.01f)

        // Auto speak toggle
        voiceSettingsRepository.setAutoSpeakResponses(false)
        assertFalse(voiceSettingsRepository.settings.value.autoSpeakResponses)
        voiceSettingsRepository.setAutoSpeakResponses(true)
        assertTrue(voiceSettingsRepository.settings.value.autoSpeakResponses)
    }

    @Test
    fun testVoicePermissionManagerRationale() {
        val explanation = voicePermissionManager.rationaleExplanation
        assertTrue(explanation.contains("microphone access", ignoreCase = true))
        assertNotNull(voicePermissionManager.permissionName)
    }

    @Test
    fun testBilingualTaskPlanningOfflineFallback() = runBlocking {
        val db = NexDatabase.getInstance(context)
        val memoryRepo = MemoryRepository(db.memoryDao(), db.taskDao())
        val memoryManager = MemoryManager(memoryRepo)
        val permManager = PermissionManager(context)
        val accessController = object : AccessibilityController {
            override fun isAccessibilityActive(): Boolean = false
            override fun getActivePackage(): String? = null
            override fun performHome(): Boolean = false
            override fun performBack(): Boolean = false
            override fun performClickOnText(text: String): Boolean = false
            override fun performClickById(viewId: String): Boolean = false
            override fun performInputText(targetText: String?, text: String): Boolean = false
            override fun performClearText(targetText: String?, viewId: String?): Boolean = false
            override fun performScroll(forward: Boolean): Boolean = false
            override fun performScrollForward(viewId: String?): Boolean = false
            override fun performScrollBackward(viewId: String?): Boolean = false
            override fun readScreenContent(): List<String> = emptyList()
            override fun openAccessibilitySettings() {}
            override fun getServiceState(): com.example.accessibility.model.AccessibilityServiceState =
                com.example.accessibility.model.AccessibilityServiceState.DISABLED
            override fun getCurrentForegroundApp(): com.example.accessibility.model.ForegroundAppInfo? = null
            override fun captureScreenSnapshot(filterSensitive: Boolean): com.example.accessibility.model.ScreenSnapshotResult =
                com.example.accessibility.model.ScreenSnapshotResult.Error("DISABLED", "Test stub")
            override suspend fun performTapCoordinates(x: Float, y: Float): Boolean = false
            override suspend fun performSwipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long): Boolean = false
            override suspend fun performLongPress(x: Float, y: Float, durationMs: Long): Boolean = false
        }
        val actionValidator = ActionValidator(accessController, permManager)
        val geminiConfig = GeminiConfig(context)
        // With blank key, GeminiEngine returns error, triggering TaskPlanner's deterministic fallback
        val geminiEngine = GeminiEngine(geminiConfig)
        val planner = TaskPlanner(geminiEngine, memoryManager, actionValidator, accessController)

        // 1. English Open YouTube
        val englishResult = planner.plan("open YouTube")
        assertTrue(englishResult is PlanningResult.Success)
        val engActions = (englishResult as PlanningResult.Success).actions
        assertEquals(1, engActions.size)
        assertEquals(ActionType.OPEN_APP, engActions[0].type)
        assertEquals("YouTube", engActions[0].parameters.appName)

        // 2. Bengali Open YouTube
        val bnResult = planner.plan("ইউটিউব ওপেন করো")
        assertTrue(bnResult is PlanningResult.Success)
        val bnActions = (bnResult as PlanningResult.Success).actions
        assertEquals(1, bnActions.size)
        assertEquals(ActionType.OPEN_APP, bnActions[0].type)
        assertEquals("YouTube", bnActions[0].parameters.appName)

        // 3. Bengali Settings
        val bnSettings = planner.plan("সেটিংস এ যাও")
        assertTrue("Expected Success but got: $bnSettings", bnSettings is PlanningResult.Success)
        val bnSettingsActions = (bnSettings as PlanningResult.Success).actions
        assertEquals(ActionType.OPEN_APP, bnSettingsActions[0].type)
        assertEquals("Settings", bnSettingsActions[0].parameters.appName)

        // 4. Bengali Chrome Search
        val bnSearch = planner.plan("Chrome open করে search করো Android 15")
        assertTrue(bnSearch is PlanningResult.Success)
        val bnSearchActions = (bnSearch as PlanningResult.Success).actions
        assertEquals(ActionType.SEARCH, bnSearchActions[0].type)
        assertEquals("Android 15", bnSearchActions[0].parameters.query)

        // 5. Conversational greeting
        val greeting = planner.plan("Hello NEX")
        assertTrue(greeting is PlanningResult.Conversational)
        assertTrue((greeting as PlanningResult.Conversational).spokenResponse.contains("Hello"))
    }
}
