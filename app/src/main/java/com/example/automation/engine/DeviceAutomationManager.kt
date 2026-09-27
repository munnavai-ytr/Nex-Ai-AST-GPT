package com.example.automation.engine

import android.content.Context
import com.example.accessibility.AccessibilityController
import com.example.accessibility.inspector.UIHierarchyInspector
import com.example.accessibility.inspector.UIHierarchySnapshot
import com.example.automation.Action
import com.example.automation.ActionResult
import com.example.automation.ActionStatus
import com.example.automation.ActionValidator
import com.example.automation.PlanningResult
import com.example.automation.TaskPlanner
import com.example.automation.agent.AgentOrchestrator
import com.example.automation.logging.AutomationExecutionLogger
import com.example.automation.permissions.AutomationPermissionManager
import com.example.automation.permissions.AutomationPermissionState
import com.example.automation.state.ScreenState
import com.example.automation.state.ScreenStateManager
import com.example.automation.verifier.AutomationResultVerifier
import com.example.automation.verifier.AutomationVerificationResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

enum class DeviceAutomationStatus {
    IDLE,
    PLANNING,
    EXECUTING,
    VERIFYING,
    WAITING_CONFIRMATION,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class AutomationSessionState(
    val status: DeviceAutomationStatus = DeviceAutomationStatus.IDLE,
    val currentCommand: String? = null,
    val activeStepDescription: String? = null,
    val totalSteps: Int = 0,
    val currentStepIndex: Int = 0,
    val lastResult: ActionResult? = null,
    val lastVerification: AutomationVerificationResult? = null,
    val spokenFeedback: String? = null
)

class DeviceAutomationManager(
    private val context: Context,
    val accessibilityController: AccessibilityController,
    val uiInspector: UIHierarchyInspector,
    val screenStateManager: ScreenStateManager,
    val deviceActionExecutor: DeviceActionExecutor,
    val actionValidator: ActionValidator,
    val permissionManager: AutomationPermissionManager,
    val resultVerifier: AutomationResultVerifier,
    val executionLogger: AutomationExecutionLogger,
    val taskPlanner: TaskPlanner,
    val agentOrchestrator: AgentOrchestrator,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {

    private val _sessionState = MutableStateFlow(AutomationSessionState())
    val sessionState: StateFlow<AutomationSessionState> = _sessionState.asStateFlow()

    private var activeJob: Job? = null

    val screenState: StateFlow<ScreenState> = screenStateManager.screenState
    val permissionState: StateFlow<AutomationPermissionState> = permissionManager.permissionState

    fun executeCommand(command: String, onFeedback: ((String) -> Unit)? = null) {
        val trimmed = command.trim()
        if (trimmed.isBlank()) return

        activeJob?.cancel()
        _sessionState.value = AutomationSessionState(
            status = DeviceAutomationStatus.PLANNING,
            currentCommand = trimmed,
            activeStepDescription = "Formulating automation plan for: \"$trimmed\""
        )

        activeJob = scope.launch {
            try {
                // 1. Plan command via TaskPlanner (Bengali & English multi-step planning)
                val planResult = taskPlanner.plan(trimmed)
                when (planResult) {
                    is PlanningResult.Success -> {
                        _sessionState.value = _sessionState.value.copy(
                            spokenFeedback = planResult.spokenResponse,
                            totalSteps = planResult.actions.size
                        )
                        onFeedback?.invoke(planResult.spokenResponse)

                        if (planResult.actions.isEmpty()) {
                            _sessionState.value = _sessionState.value.copy(
                                status = DeviceAutomationStatus.COMPLETED,
                                activeStepDescription = planResult.spokenResponse
                            )
                            return@launch
                        }

                        // Run actions sequentially with real observation and verification
                        executeActionSequence(planResult.actions, trimmed, onFeedback)
                    }

                    is PlanningResult.Conversational -> {
                        _sessionState.value = _sessionState.value.copy(
                            status = DeviceAutomationStatus.COMPLETED,
                            activeStepDescription = planResult.spokenResponse,
                            spokenFeedback = planResult.spokenResponse
                        )
                        onFeedback?.invoke(planResult.spokenResponse)
                    }

                    is PlanningResult.Unsupported -> {
                        val msg = "The capability to '${planResult.capability}' is not supported yet on this device."
                        _sessionState.value = _sessionState.value.copy(
                            status = DeviceAutomationStatus.FAILED,
                            activeStepDescription = msg,
                            spokenFeedback = msg
                        )
                        onFeedback?.invoke(msg)
                    }

                    is PlanningResult.Error -> {
                        _sessionState.value = _sessionState.value.copy(
                            status = DeviceAutomationStatus.FAILED,
                            activeStepDescription = planResult.message,
                            spokenFeedback = planResult.message
                        )
                        onFeedback?.invoke(planResult.message)
                    }
                }
            } catch (e: Exception) {
                _sessionState.value = _sessionState.value.copy(
                    status = DeviceAutomationStatus.FAILED,
                    activeStepDescription = "Execution error: ${e.message}"
                )
            }
        }
    }

    suspend fun executeActionSequence(
        actions: List<Action>,
        command: String = "Manual Action Sequence",
        onFeedback: ((String) -> Unit)? = null
    ) {
        val taskId = UUID.randomUUID().toString()
        val total = actions.size

        for ((index, action) in actions.withIndex()) {
            _sessionState.value = _sessionState.value.copy(
                status = DeviceAutomationStatus.EXECUTING,
                activeStepDescription = action.description,
                currentStepIndex = index + 1,
                totalSteps = total
            )

            // Validate action
            val validation = actionValidator.validate(action)
            if (!validation.isValid) {
                val errorMsg = "Action rejected: ${validation.reason}"
                _sessionState.value = _sessionState.value.copy(
                    status = DeviceAutomationStatus.FAILED,
                    activeStepDescription = errorMsg
                )
                onFeedback?.invoke(errorMsg)
                return
            }

            // Capture pre-execution screen snapshot
            val preSnapshot = uiInspector.inspectCurrentScreen()

            // Execute action
            val result = deviceActionExecutor.executeAction(action, taskId)

            // Wait for UI render
            delay(400L)

            // Capture post-execution screen snapshot
            val postSnapshot = uiInspector.inspectCurrentScreen()

            // Verify action effect
            _sessionState.value = _sessionState.value.copy(
                status = DeviceAutomationStatus.VERIFYING,
                activeStepDescription = "Verifying: ${action.description}"
            )
            val verification = resultVerifier.verifyAction(action, result, preSnapshot, postSnapshot)

            // Log execution
            executionLogger.logExecution(taskId, action, result, verification)

            _sessionState.value = _sessionState.value.copy(
                lastResult = result,
                lastVerification = verification
            )

            if (!result.isSuccess) {
                _sessionState.value = _sessionState.value.copy(
                    status = DeviceAutomationStatus.FAILED,
                    activeStepDescription = "Step failed: ${result.message}"
                )
                onFeedback?.invoke("Step failed: ${result.message}")
                return
            }
        }

        _sessionState.value = _sessionState.value.copy(
            status = DeviceAutomationStatus.COMPLETED,
            activeStepDescription = "Task completed successfully ($total steps executed)."
        )
        onFeedback?.invoke("Task completed successfully.")
    }

    suspend fun executeSingleAction(action: Action): Pair<ActionResult, AutomationVerificationResult> {
        val taskId = UUID.randomUUID().toString()
        val preSnapshot = uiInspector.inspectCurrentScreen()
        val result = deviceActionExecutor.executeAction(action, taskId)
        delay(350L)
        val postSnapshot = uiInspector.inspectCurrentScreen()
        val verification = resultVerifier.verifyAction(action, result, preSnapshot, postSnapshot)
        executionLogger.logExecution(taskId, action, result, verification)
        return Pair(result, verification)
    }

    fun inspectScreen(): UIHierarchySnapshot {
        return screenStateManager.captureFreshSnapshot()
    }

    fun cancelActiveAutomation() {
        activeJob?.cancel()
        activeJob = null
        agentOrchestrator.cancelTask("Automation cancelled by user")
        _sessionState.value = _sessionState.value.copy(
            status = DeviceAutomationStatus.CANCELLED,
            activeStepDescription = "Automation cancelled"
        )
    }
}
