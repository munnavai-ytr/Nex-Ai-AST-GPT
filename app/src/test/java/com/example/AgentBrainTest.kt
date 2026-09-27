package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.accessibility.AccessibilityController
import com.example.accessibility.model.AccessibilityServiceState
import com.example.accessibility.model.ForegroundAppInfo
import com.example.accessibility.model.ScreenElement
import com.example.accessibility.model.ScreenSnapshot
import com.example.accessibility.model.ScreenSnapshotResult
import com.example.ai.GeminiConfig
import com.example.ai.GeminiEngine
import com.example.automation.Action
import com.example.automation.ActionExecutor
import com.example.automation.ActionParameters
import com.example.automation.ActionResult
import com.example.automation.ActionStatus
import com.example.automation.ActionType
import com.example.automation.ActionValidator
import com.example.automation.ExecutionLogger
import com.example.automation.TaskPlanner
import com.example.automation.agent.AgentOrchestrator
import com.example.automation.agent.AgentTaskState
import com.example.automation.agent.LoopDetector
import com.example.automation.agent.RetryBudget
import com.example.automation.agent.ScreenSignature
import com.example.automation.agent.StepSuccessCriteria
import com.example.automation.agent.TaskPlan
import com.example.automation.agent.TaskStep
import com.example.automation.agent.VerificationEngine
import com.example.automation.confirmation.ConfirmationManager
import com.example.core.TaskStateManager
import com.example.memory.MemoryManager
import com.example.memory.MemoryRepository
import com.example.memory.NexDatabase
import com.example.permissions.PermissionManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AgentBrainTest {

    private lateinit var context: Context
    private lateinit var database: NexDatabase
    private lateinit var memoryRepository: MemoryRepository
    private lateinit var memoryManager: MemoryManager
    private lateinit var permissionManager: PermissionManager
    private lateinit var confirmationManager: ConfirmationManager
    private lateinit var taskStateManager: TaskStateManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = NexDatabase.getInstance(context)
        memoryRepository = MemoryRepository(database.memoryDao(), database.taskDao())
        memoryManager = MemoryManager(memoryRepository)
        permissionManager = PermissionManager(context)
        confirmationManager = ConfirmationManager()
        taskStateManager = TaskStateManager()
    }

    @Test
    fun `ScreenSignature correctly computes hashes and detects changes`() {
        val element1 = ScreenElement(
            id = 1,
            className = "android.widget.Button",
            text = "Search",
            packageName = "com.google.android.youtube",
            clickable = true
        )
        val element2 = ScreenElement(
            id = 2,
            className = "android.widget.TextView",
            text = "Trending Videos",
            packageName = "com.google.android.youtube"
        )

        val snapshotA = ScreenSnapshot(
            packageName = "com.google.android.youtube",
            displayName = "YouTube",
            elements = listOf(element1, element2)
        )
        val sigA = ScreenSignature.from(snapshotA)

        val snapshotSame = ScreenSnapshot(
            packageName = "com.google.android.youtube",
            displayName = "YouTube",
            elements = listOf(element1, element2)
        )
        val sigSame = ScreenSignature.from(snapshotSame)

        // Same UI must not report change
        assertFalse(sigSame.hasChangedFrom(sigA))
        assertEquals(1.0f, sigA.similarity(sigSame), 0.01f)

        // Mutated UI (element added)
        val element3 = ScreenElement(
            id = 3,
            className = "android.widget.TextView",
            text = "Video 1: Highlights",
            packageName = "com.google.android.youtube"
        )
        val snapshotChanged = ScreenSnapshot(
            packageName = "com.google.android.youtube",
            displayName = "YouTube",
            elements = listOf(element1, element2, element3)
        )
        val sigChanged = ScreenSignature.from(snapshotChanged)

        assertTrue(sigChanged.hasChangedFrom(sigA))
    }

    @Test
    fun `LoopDetector detects repetitive actions on unchanged screens`() {
        val detector = LoopDetector(loopThreshold = 2)

        val signature = ScreenSignature(
            packageName = "com.google.android.youtube",
            elementCount = 5,
            interactiveCount = 2,
            structureHash = 12345,
            textHashSample = 67890
        )

        val tapAction = Action(
            type = ActionType.TAP,
            parameters = ActionParameters(targetText = "Search"),
            description = "Tap Search button"
        )

        // First attempt
        detector.recordAction(tapAction, signature)
        assertFalse(detector.isLoopDetected())

        // Second attempt on same screen signature
        detector.recordAction(tapAction, signature)
        assertFalse(detector.isLoopDetected())

        // Third attempt on same screen signature -> Loop threshold reached!
        detector.recordAction(tapAction, signature)
        assertTrue(detector.isLoopDetected())
    }

    @Test
    fun `RetryBudget enforces limits on steps retries and replans`() {
        val budget = RetryBudget(
            maxSteps = 3,
            maxRetriesPerStep = 2,
            maxReplans = 2,
            maxTaskDurationMs = 5000L
        )

        assertEquals(RetryBudget.BudgetCheckResult.Allowed, budget.checkBudget())

        budget.recordStepExecution()
        budget.recordStepExecution()
        assertEquals(RetryBudget.BudgetCheckResult.Allowed, budget.checkBudget())

        // Step retry limit check
        budget.recordStepRetry()
        budget.recordStepRetry()
        budget.recordStepRetry() // Exceeds maxRetriesPerStep = 2
        val retryExceeded = budget.checkBudget()
        assertTrue(retryExceeded is RetryBudget.BudgetCheckResult.Exceeded)
        assertEquals("STEP_RETRY_LIMIT", (retryExceeded as RetryBudget.BudgetCheckResult.Exceeded).limitType)

        // Reset and check replan limit
        budget.reset()
        budget.recordReplan()
        budget.recordReplan()
        budget.recordReplan() // Exceeds maxReplans = 2
        val replanExceeded = budget.checkBudget()
        assertTrue(replanExceeded is RetryBudget.BudgetCheckResult.Exceeded)
        assertEquals("REPLAN_LIMIT", (replanExceeded as RetryBudget.BudgetCheckResult.Exceeded).limitType)

        // Reset and check max steps limit
        budget.reset()
        budget.recordStepExecution()
        budget.recordStepExecution()
        budget.recordStepExecution() // Reaches maxSteps = 3
        val stepExceeded = budget.checkBudget()
        assertTrue(stepExceeded is RetryBudget.BudgetCheckResult.Exceeded)
        assertEquals("STEP_LIMIT", (stepExceeded as RetryBudget.BudgetCheckResult.Exceeded).limitType)
    }

    @Test
    fun `TaskPlan parses structured schema and validates strictly`() {
        val validJson = """
            {
              "goal": "Open YouTube and search for football highlights",
              "spokenResponse": "Opening YouTube and searching for highlights.",
              "steps": [
                {
                  "id": "step_1",
                  "action": "OPEN_APP",
                  "description": "Opening YouTube",
                  "parameters": { "appName": "YouTube" },
                  "successCriteria": { "expectedPackage": "youtube" }
                },
                {
                  "id": "step_2",
                  "action": "TAP",
                  "description": "Tap Search",
                  "target": { "text": "Search" },
                  "parameters": {}
                },
                {
                  "id": "step_3",
                  "action": "TYPE_TEXT",
                  "description": "Enter search query",
                  "parameters": { "inputText": "football highlights" }
                }
              ]
            }
        """.trimIndent()

        val parseResult = TaskPlan.parseAndValidate(validJson)
        assertTrue(parseResult is TaskPlan.Companion.ParseResult.Success)
        val plan = (parseResult as TaskPlan.Companion.ParseResult.Success).plan
        assertEquals("Open YouTube and search for football highlights", plan.goal)
        assertEquals(3, plan.steps.size)
        assertEquals(ActionType.OPEN_APP, plan.steps[0].action)
        assertEquals("Search", plan.steps[1].target?.text)
        assertEquals("football highlights", plan.steps[2].parameters.inputText)

        // Invalid JSON missing goal
        val invalidJson = """{ "steps": [] }"""
        val invalidResult = TaskPlan.parseAndValidate(invalidJson)
        assertTrue(invalidResult is TaskPlan.Companion.ParseResult.Invalid)

        // Unsupported capability
        val unsupportedJson = """
            {
              "goal": "Fly to Paris",
              "unsupportedCapability": "flight_booking",
              "spokenResponse": "I cannot book flights directly."
            }
        """.trimIndent()
        val unsupportedResult = TaskPlan.parseAndValidate(unsupportedJson)
        assertTrue(unsupportedResult is TaskPlan.Companion.ParseResult.Success)
        val unsuppPlan = (unsupportedResult as TaskPlan.Companion.ParseResult.Success).plan
        assertEquals("flight_booking", unsuppPlan.unsupportedCapability)
    }

    @Test
    fun `VerificationEngine correctly evaluates observable criteria`() {
        val mockController = DummyAccessibilityController()
        val engine = VerificationEngine(mockController)

        val step = TaskStep(
            id = "step_1",
            action = ActionType.OPEN_APP,
            successCriteria = StepSuccessCriteria(expectedPackage = "youtube")
        )

        val actionResult = ActionResult(
            actionId = "step_1",
            actionType = ActionType.OPEN_APP,
            status = ActionStatus.SUCCESS,
            message = "Opened YouTube"
        )

        val postSnapshot = ScreenSnapshot(
            packageName = "com.google.android.youtube",
            displayName = "YouTube",
            elements = listOf(
                ScreenElement(id = 1, className = "android.widget.Button", text = "Search")
            )
        )

        val sig = ScreenSignature.from(postSnapshot)
        val stepResult = engine.verifyStep(step, actionResult, null, sig, postSnapshot)
        assertTrue(stepResult.isSuccess)

        // Goal verification
        val goalResult = engine.verifyGoal("Open YouTube", postSnapshot, listOf(step))
        assertTrue(goalResult.isSuccess)
    }

    @Test
    fun `AgentOrchestrator executes and supports safe user cancellation`() = runTest {
        val mockController = DummyAccessibilityController()
        val geminiConfig = GeminiConfig(context)
        val geminiEngine = GeminiEngine(geminiConfig)
        val actionValidator = ActionValidator(mockController, permissionManager, confirmationManager)
        val actionExecutor = ActionExecutor(context, mockController, ExecutionLogger(memoryRepository))
        val taskPlanner = TaskPlanner(geminiEngine, memoryManager, actionValidator, mockController)
        val verificationEngine = VerificationEngine(mockController)

        val orchestrator = AgentOrchestrator(
            accessibilityController = mockController,
            taskPlanner = taskPlanner,
            actionValidator = actionValidator,
            actionExecutor = actionExecutor,
            verificationEngine = verificationEngine,
            confirmationManager = confirmationManager,
            taskStateManager = taskStateManager,
            memoryRepository = memoryRepository,
            geminiEngine = geminiEngine,
            coroutineScope = this
        )

        var spokenMessage: String? = null
        orchestrator.onSpokenFeedback = { spokenMessage = it }

        // Start task: "go home"
        orchestrator.startTask("go home")
        assertTrue(orchestrator.isRunning.value)
        assertNotNull(orchestrator.taskContext.value)

        // Interrupt / Stop Task
        orchestrator.cancelTask("User voice cancel", informUser = true)
        assertFalse(orchestrator.isRunning.value)
        assertEquals(AgentTaskState.CANCELLED, orchestrator.taskContext.value?.taskState)
        assertEquals("Task stopped.", spokenMessage)
    }
}

open class DummyAccessibilityController : AccessibilityController {
    override fun isAccessibilityActive(): Boolean = true
    override fun getActivePackage(): String? = "com.google.android.youtube"
    override fun readScreenContent(): List<String> = emptyList()
    override fun openAccessibilitySettings() {}
    override fun getServiceState(): AccessibilityServiceState = AccessibilityServiceState.CONNECTED
    override fun getCurrentForegroundApp(): ForegroundAppInfo? = ForegroundAppInfo("com.google.android.youtube", "YouTube")
    override fun captureScreenSnapshot(filterSensitive: Boolean): ScreenSnapshotResult =
        ScreenSnapshotResult.Success(ScreenSnapshot(packageName = "com.google.android.youtube", displayName = "YouTube"))
    override fun performClickOnText(text: String): Boolean = true
    override fun performClickById(viewId: String): Boolean = true
    override fun performInputText(targetText: String?, text: String): Boolean = true
    override fun performClearText(targetText: String?, viewId: String?): Boolean = true
    override fun performScroll(forward: Boolean): Boolean = true
    override fun performScrollForward(viewId: String?): Boolean = true
    override fun performScrollBackward(viewId: String?): Boolean = true
    override suspend fun performTapCoordinates(x: Float, y: Float): Boolean = true
    override suspend fun performLongPress(x: Float, y: Float, durationMs: Long): Boolean = true
    override suspend fun performSwipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long): Boolean = true
    override fun performHome(): Boolean = true
    override fun performBack(): Boolean = true
}
