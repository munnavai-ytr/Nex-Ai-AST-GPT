package com.example.automation

import com.example.automation.agent.AgentOrchestrator
import com.example.automation.confirmation.ConfirmationManager
import com.example.automation.queue.ActionQueue
import com.example.core.TaskState
import com.example.core.TaskStateManager
import com.example.memory.MemoryRepository
import com.example.security.SecurityManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CommandProcessor(
    private val taskPlanner: TaskPlanner,
    private val actionExecutor: ActionExecutor,
    private val taskStateManager: TaskStateManager,
    private val securityManager: SecurityManager,
    private val memoryRepository: MemoryRepository,
    private val confirmationManager: ConfirmationManager = ConfirmationManager(),
    private val actionQueue: ActionQueue? = null,
    val agentOrchestrator: AgentOrchestrator? = null,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {

    var onResponseGeneratedListener: ((String) -> Unit)? = null
        set(value) {
            field = value
            actionQueue?.onSpokenFeedback = value
            agentOrchestrator?.onSpokenFeedback = value
        }

    private var currentActiveTaskId: String? = null
    private var currentPrompt: String = ""
    private var currentPlanningJob: kotlinx.coroutines.Job? = null

    init {
        actionQueue?.onSpokenFeedback = { speech ->
            onResponseGeneratedListener?.invoke(speech)
        }
        agentOrchestrator?.onSpokenFeedback = { speech ->
            onResponseGeneratedListener?.invoke(speech)
        }
    }

    fun processCommand(prompt: String) {
        val trimmed = prompt.trim()
        if (trimmed.isBlank()) return

        // Cancel previous in-flight planning job to prevent stale responses from overwriting newer state
        currentPlanningJob?.cancel()

        // 1. Voice Cancellation Check ("Stop", "Cancel", "থামো", "বন্ধ করো")
        val cleanLower = trimmed.lowercase()
        if (cleanLower == "stop" || cleanLower == "cancel" || cleanLower == "stop task" || cleanLower == "cancel task" ||
            cleanLower == "থামো" || cleanLower == "বন্ধ করো" || cleanLower == "থামাও" || cleanLower == "বাতিল করো"
        ) {
            val isAgentActive = agentOrchestrator?.isRunning?.value == true
            val isQueueActive = actionQueue?.progress?.value?.isRunning == true
            if (isAgentActive || isQueueActive || taskStateManager.executionState.value.state == TaskState.EXECUTING) {
                cancelPendingAction()
                return
            }
        }

        currentPrompt = trimmed
        val taskId = taskStateManager.startTaskWithPrompt(trimmed)
        currentActiveTaskId = taskId

        // 2. Delegate to Autonomous Agent Brain if configured
        if (agentOrchestrator != null) {
            agentOrchestrator.startTask(trimmed)
            return
        }

        currentPlanningJob = coroutineScope.launch {
            try {
                val startTime = System.currentTimeMillis()
                taskStateManager.updateState(TaskState.PLANNING, actionDescription = "Perceiving & synthesizing action plan...")

                val planningResult = taskPlanner.plan(trimmed)
                when (planningResult) {
                    is PlanningResult.Conversational -> {
                        taskStateManager.completeTask(planningResult.spokenResponse)
                        memoryRepository.recordTaskHistory(
                            taskId = taskId,
                            prompt = trimmed,
                            status = "COMPLETED",
                            actionsCount = 0,
                            executionTimeMs = System.currentTimeMillis() - startTime,
                            summary = planningResult.spokenResponse
                        )
                        onResponseGeneratedListener?.invoke(planningResult.spokenResponse)
                    }

                    is PlanningResult.Unsupported -> {
                        val summary = "Capability not yet available: ${planningResult.capability}"
                        taskStateManager.failTask(summary)
                        memoryRepository.recordTaskHistory(
                            taskId = taskId,
                            prompt = trimmed,
                            status = "FAILED",
                            actionsCount = 0,
                            executionTimeMs = System.currentTimeMillis() - startTime,
                            summary = summary
                        )
                        onResponseGeneratedListener?.invoke(planningResult.spokenResponse)
                    }

                    is PlanningResult.Error -> {
                        taskStateManager.failTask(planningResult.message)
                        memoryRepository.recordTaskHistory(
                            taskId = taskId,
                            prompt = trimmed,
                            status = "FAILED",
                            actionsCount = 0,
                            executionTimeMs = System.currentTimeMillis() - startTime,
                            summary = planningResult.message
                        )
                        val errorSpoken = if (planningResult.isKeyMissing) {
                            "Gemini API key is not configured. Please configure it in Settings."
                        } else {
                            "I could not process that command: ${planningResult.message}"
                        }
                        onResponseGeneratedListener?.invoke(errorSpoken)
                    }

                    is PlanningResult.Success -> {
                        val actions = planningResult.actions
                        val plannedSpoken = planningResult.spokenResponse

                        if (actionQueue != null) {
                            actionQueue.enqueueTask(
                                taskId = taskId,
                                prompt = trimmed,
                                actions = actions,
                                introductorySpeech = plannedSpoken
                            )
                        } else {
                            // Fallback sequential execution if queue is not injected
                            executeActionQueueFallback(taskId, startTime, actions, plannedSpoken)
                        }
                    }
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
                // Request superseded or cancelled gracefully
            }
        }
    }

    private suspend fun executeActionQueueFallback(
        taskId: String,
        startTime: Long,
        actions: List<Action>,
        introductoryResponse: String
    ) {
        val totalActions = actions.size
        var executedCount = 0
        var allSucceeded = true
        var failureReason: String? = null

        if (introductoryResponse.isNotBlank()) {
            onResponseGeneratedListener?.invoke(introductoryResponse)
        }

        for (action in actions) {
            executedCount++

            if (confirmationManager.isActionSensitive(action) || securityManager.isActionSensitive(action) || action.requiresConfirmation) {
                taskStateManager.updateState(
                    state = TaskState.WAITING_FOR_USER,
                    actionDescription = "Requires confirmation: ${action.description}",
                    currentIndex = executedCount,
                    totalActions = totalActions,
                    pendingConfirmationPrompt = "Allow NEX to execute: ${action.description}?"
                )
                return
            }

            taskStateManager.updateState(
                state = TaskState.EXECUTING,
                actionDescription = action.description,
                currentIndex = executedCount,
                totalActions = totalActions
            )

            val result = actionExecutor.execute(action, taskId)
            if (result.status == ActionStatus.FAILED) {
                allSucceeded = false
                failureReason = result.message
                break
            } else if (result.status == ActionStatus.REQUIRES_CONFIRMATION) {
                taskStateManager.updateState(
                    state = TaskState.WAITING_FOR_USER,
                    actionDescription = result.message,
                    currentIndex = executedCount,
                    totalActions = totalActions,
                    pendingConfirmationPrompt = result.message
                )
                return
            }
        }

        val totalDuration = System.currentTimeMillis() - startTime
        if (allSucceeded) {
            val summary = "Successfully executed $totalActions steps for \"$currentPrompt\""
            taskStateManager.completeTask(summary)
            memoryRepository.recordTaskHistory(
                taskId = taskId,
                prompt = currentPrompt,
                status = "COMPLETED",
                actionsCount = totalActions,
                executionTimeMs = totalDuration,
                summary = summary
            )
        } else {
            val summary = failureReason ?: "Execution encountered an error"
            taskStateManager.failTask(summary)
            memoryRepository.recordTaskHistory(
                taskId = taskId,
                prompt = currentPrompt,
                status = "FAILED",
                actionsCount = executedCount,
                executionTimeMs = totalDuration,
                summary = summary
            )
            onResponseGeneratedListener?.invoke("Action could not be completed: $summary")
        }
    }

    suspend fun confirmPendingAction(activity: androidx.fragment.app.FragmentActivity?): Boolean {
        val pendingAction = agentOrchestrator?.currentPendingConfirmationAction
        val isSensitive = pendingAction != null && (securityManager.isActionSensitive(pendingAction) || confirmationManager.isActionSensitive(pendingAction))

        if (isSensitive) {
            if (activity == null) {
                // Sensitive action requires host activity to display Android BiometricPrompt
                return false
            }
            val authed = securityManager.authenticateOwnerForAction(
                activity = activity,
                actionTitle = "Authorize: ${pendingAction.description}"
            )
            if (!authed) {
                return false
            }
        }

        if (agentOrchestrator?.isRunning?.value == true) {
            agentOrchestrator.confirmPendingAction(approved = true)
            return true
        }
        actionQueue?.resumeWithConfirmation(approved = true)
        return true
    }

    fun confirmPendingAction() {
        if (agentOrchestrator?.isRunning?.value == true) {
            agentOrchestrator.confirmPendingAction(approved = true)
            return
        }
        actionQueue?.resumeWithConfirmation(approved = true)
    }

    fun cancelPendingAction() {
        currentPlanningJob?.cancel()
        if (agentOrchestrator?.isRunning?.value == true) {
            agentOrchestrator.cancelTask(reason = "User cancelled pending action", informUser = true)
            return
        }
        actionQueue?.cancelCurrentTask(informUser = true)
            ?: run {
                taskStateManager.cancelTask()
                onResponseGeneratedListener?.invoke("Task cancelled.")
            }
    }
}
