package com.example.automation.queue

import com.example.automation.Action
import com.example.automation.ActionResult
import com.example.automation.ActionStatus
import com.example.automation.ActionType
import com.example.automation.ActionValidator
import com.example.automation.ActionExecutor
import com.example.automation.confirmation.ConfirmationManager
import com.example.core.TaskState
import com.example.core.TaskStateManager
import com.example.memory.MemoryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

data class ActionQueueProgress(
    val taskId: String? = null,
    val taskPrompt: String = "",
    val totalActions: Int = 0,
    val currentIndex: Int = 0,
    val currentAction: Action? = null,
    val executionState: TaskState = TaskState.IDLE,
    val elapsedTimeMs: Long = 0L,
    val currentAppName: String? = null,
    val isRunning: Boolean = false
)

class ActionQueue(
    private val actionValidator: ActionValidator,
    private val actionExecutor: ActionExecutor,
    private val taskStateManager: TaskStateManager,
    private val confirmationManager: ConfirmationManager,
    private val memoryRepository: MemoryRepository,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {

    private val _progress = MutableStateFlow(ActionQueueProgress())
    val progress: StateFlow<ActionQueueProgress> = _progress.asStateFlow()

    private var executionJob: Job? = null
    private val pendingActions = mutableListOf<Action>()
    private var currentTaskId: String? = null
    private var currentPrompt: String = ""
    private var startTimeMs: Long = 0L

    var onSpokenFeedback: ((String) -> Unit)? = null

    fun enqueueTask(
        taskId: String,
        prompt: String,
        actions: List<Action>,
        introductorySpeech: String? = null
    ) {
        cancelCurrentTask(informUser = false)

        currentTaskId = taskId
        currentPrompt = prompt
        startTimeMs = System.currentTimeMillis()
        pendingActions.clear()
        pendingActions.addAll(actions)

        if (!introductorySpeech.isNullOrBlank()) {
            onSpokenFeedback?.invoke(introductorySpeech)
        }

        executionJob = coroutineScope.launch {
            runQueue(taskId, prompt)
        }
    }

    private suspend fun runQueue(taskId: String, prompt: String) {
        val totalActions = pendingActions.size
        var executedCount = 0
        var allSucceeded = true
        var failureMessage: String? = null

        taskStateManager.updateState(
            state = TaskState.EXECUTING,
            actionDescription = "Executing action sequence...",
            currentIndex = 0,
            totalActions = totalActions
        )

        while (pendingActions.isNotEmpty()) {
            val action = pendingActions.removeAt(0)
            executedCount++

            _progress.value = ActionQueueProgress(
                taskId = taskId,
                taskPrompt = prompt,
                totalActions = totalActions,
                currentIndex = executedCount,
                currentAction = action,
                executionState = if (action.type == ActionType.VERIFY) TaskState.VERIFYING else TaskState.EXECUTING,
                elapsedTimeMs = System.currentTimeMillis() - startTimeMs,
                currentAppName = action.parameters.appName ?: action.parameters.packageName,
                isRunning = true
            )

            // 1. Validation check
            val validation = actionValidator.validate(action)
            if (!validation.isValid) {
                val errorResult = ActionResult(
                    actionId = action.id,
                    actionType = action.type,
                    status = ActionStatus.VALIDATION_FAILED,
                    message = "Validation failed: ${validation.reason ?: "Invalid action"}",
                    errorCode = "INVALID_ACTION",
                    errorMessage = validation.reason
                )
                handleFailure(taskId, prompt, executedCount, totalActions, errorResult.message)
                return
            }

            // 2. Sensitive Action / Confirmation check
            if (validation.requiresConfirmation || confirmationManager.isActionSensitive(action)) {
                taskStateManager.updateState(
                    state = TaskState.WAITING_FOR_USER,
                    actionDescription = "Requires user confirmation: ${action.description}",
                    currentIndex = executedCount,
                    totalActions = totalActions,
                    pendingConfirmationPrompt = "Allow NEX to execute: ${action.description}?"
                )

                _progress.value = _progress.value.copy(
                    executionState = TaskState.WAITING_FOR_USER
                )

                // Re-queue the action without requiresConfirmation flag and wait for user response
                pendingActions.add(0, action.copy(requiresConfirmation = false))
                confirmationManager.requestConfirmation(action) { userApproved ->
                    resumeWithConfirmation(userApproved)
                }
                return
            }

            // 3. Execution with Timeout & Retry Policy
            taskStateManager.updateState(
                state = if (action.type == ActionType.VERIFY) TaskState.VERIFYING else TaskState.EXECUTING,
                actionDescription = action.description,
                currentIndex = executedCount,
                totalActions = totalActions
            )

            val maxAttempts = if (action.retryPolicy.retryable && !confirmationManager.isActionSensitive(action)) {
                action.retryPolicy.maxAttempts.coerceAtLeast(1)
            } else 1

            var currentAttempt = 0
            var actionResult: ActionResult? = null

            while (currentAttempt < maxAttempts) {
                currentAttempt++
                try {
                    val timeout = action.timeoutMs.coerceIn(1000L, 30000L)
                    actionResult = withTimeout(timeout) {
                        actionExecutor.execute(action, taskId)
                    }

                    if (actionResult.isSuccess || actionResult.status == ActionStatus.REQUIRES_CONFIRMATION) {
                        break
                    } else if (currentAttempt < maxAttempts && action.retryPolicy.retryable) {
                        delay(action.retryPolicy.delayBetweenAttemptsMs)
                    }
                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                    actionResult = ActionResult(
                        actionId = action.id,
                        actionType = action.type,
                        status = ActionStatus.TIMEOUT,
                        message = "Action timed out after ${action.timeoutMs}ms",
                        errorCode = "TIMEOUT",
                        errorMessage = "Action timed out"
                    )
                    break
                } catch (e: CancellationException) {
                    handleCancellation(taskId, prompt, executedCount)
                    return
                } catch (e: Exception) {
                    actionResult = ActionResult(
                        actionId = action.id,
                        actionType = action.type,
                        status = ActionStatus.FAILED,
                        message = "Unexpected execution error: ${e.message}",
                        errorCode = "EXECUTION_EXCEPTION"
                    )
                    break
                }
            }

            val finalResult = actionResult ?: ActionResult(
                actionId = action.id,
                actionType = action.type,
                status = ActionStatus.FAILED,
                message = "Action failed without result",
                errorCode = "NO_RESULT"
            )

            if (!finalResult.isSuccess) {
                allSucceeded = false
                failureMessage = finalResult.message
                handleFailure(taskId, prompt, executedCount, totalActions, failureMessage)
                return
            }
        }

        // Completion
        if (allSucceeded) {
            val totalDuration = System.currentTimeMillis() - startTimeMs
            val summary = "Successfully executed $totalActions actions for \"$prompt\""
            taskStateManager.completeTask(summary)
            _progress.value = ActionQueueProgress(
                taskId = taskId,
                taskPrompt = prompt,
                totalActions = totalActions,
                currentIndex = totalActions,
                executionState = TaskState.COMPLETED,
                elapsedTimeMs = totalDuration,
                isRunning = false
            )
            memoryRepository.recordTaskHistory(
                taskId = taskId,
                prompt = prompt,
                status = "COMPLETED",
                actionsCount = totalActions,
                executionTimeMs = totalDuration,
                summary = summary
            )
        }
    }

    private fun handleFailure(
        taskId: String,
        prompt: String,
        executedCount: Int,
        totalActions: Int,
        message: String
    ) {
        val totalDuration = System.currentTimeMillis() - startTimeMs
        taskStateManager.failTask(message)
        _progress.value = _progress.value.copy(
            executionState = TaskState.FAILED,
            elapsedTimeMs = totalDuration,
            isRunning = false
        )
        coroutineScope.launch {
            memoryRepository.recordTaskHistory(
                taskId = taskId,
                prompt = prompt,
                status = "FAILED",
                actionsCount = executedCount,
                executionTimeMs = totalDuration,
                summary = message
            )
        }
        onSpokenFeedback?.invoke("Task failed: $message")
    }

    private fun handleCancellation(taskId: String, prompt: String, executedCount: Int) {
        val totalDuration = System.currentTimeMillis() - startTimeMs
        taskStateManager.cancelTask()
        _progress.value = _progress.value.copy(
            executionState = TaskState.CANCELLED,
            elapsedTimeMs = totalDuration,
            isRunning = false
        )
        coroutineScope.launch {
            memoryRepository.recordTaskHistory(
                taskId = taskId,
                prompt = prompt,
                status = "CANCELLED",
                actionsCount = executedCount,
                executionTimeMs = totalDuration,
                summary = "Task cancelled by user"
            )
        }
        onSpokenFeedback?.invoke("Task cancelled.")
    }

    fun resumeWithConfirmation(approved: Boolean) {
        if (!approved) {
            cancelCurrentTask(informUser = true)
            return
        }
        val taskId = currentTaskId ?: return
        val prompt = currentPrompt
        executionJob = coroutineScope.launch {
            runQueue(taskId, prompt)
        }
    }

    fun cancelCurrentTask(informUser: Boolean = true) {
        executionJob?.cancel()
        executionJob = null
        pendingActions.clear()
        confirmationManager.clearPending()
        val taskId = currentTaskId
        val prompt = currentPrompt
        if (taskId != null) {
            handleCancellation(taskId, prompt, _progress.value.currentIndex)
        } else {
            taskStateManager.cancelTask()
            if (informUser) {
                onSpokenFeedback?.invoke("Task cancelled.")
            }
        }
    }
}
