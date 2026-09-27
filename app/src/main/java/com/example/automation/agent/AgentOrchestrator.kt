package com.example.automation.agent

import com.example.accessibility.AccessibilityController
import com.example.accessibility.model.ScreenSnapshot
import com.example.accessibility.model.ScreenSnapshotResult
import com.example.accessibility.tree.ElementFinder
import com.example.ai.GeminiEngine
import com.example.automation.Action
import com.example.automation.ActionExecutor
import com.example.automation.ActionParameters
import com.example.automation.ActionResult
import com.example.automation.ActionStatus
import com.example.automation.ActionType
import com.example.automation.ActionValidator
import com.example.automation.PlanningResult
import com.example.automation.TaskPlanner
import com.example.automation.confirmation.ConfirmationManager
import com.example.core.TaskState
import com.example.core.TaskStateManager
import com.example.memory.MemoryManager
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
import java.util.UUID

class AgentOrchestrator(
    private val accessibilityController: AccessibilityController,
    private val taskPlanner: TaskPlanner,
    private val actionValidator: ActionValidator,
    private val actionExecutor: ActionExecutor,
    private val verificationEngine: VerificationEngine,
    private val confirmationManager: ConfirmationManager,
    private val taskStateManager: TaskStateManager,
    private val memoryRepository: MemoryRepository,
    private val geminiEngine: GeminiEngine,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val appResolver: com.example.automation.appcontrol.AppResolver? = null,
    private val appAutomationRegistry: com.example.automation.appcontrol.AppAutomationRegistry? = null,
    private val memoryManager: MemoryManager? = null
) {

    private val _taskContext = MutableStateFlow<AgentTaskContext?>(null)
    val taskContext: StateFlow<AgentTaskContext?> = _taskContext.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private var executionJob: Job? = null
    private val loopDetector = LoopDetector(loopThreshold = 2)
    private val retryBudget = RetryBudget()

    var onSpokenFeedback: ((String) -> Unit)? = null
    var onExplanationUpdate: ((String) -> Unit)? = null

    private var pendingConfirmationStep: TaskStep? = null
    private var pendingConfirmationAction: Action? = null
    val currentPendingConfirmationAction: Action? get() = pendingConfirmationAction

    /**
     * Entry point: Receive user task and begin autonomous Observe -> Act -> Verify loop.
     */
    fun startTask(userRequest: String, introductorySpeech: String? = null) {
        val trimmed = userRequest.trim()
        if (trimmed.isBlank()) return

        // Cancel previous task if running
        cancelTask(reason = "New task started", informUser = false)

        val taskId = UUID.randomUUID().toString()
        retryBudget.reset()
        loopDetector.clear()

        _isRunning.value = true
        val initialContext = AgentTaskContext(
            taskId = taskId,
            userRequest = trimmed,
            goal = trimmed,
            taskState = AgentTaskState.OBSERVING,
            conciseStatusExplanation = "Observing device state..."
        )
        _taskContext.value = initialContext

        taskStateManager.updateState(
            state = TaskState.PLANNING,
            actionDescription = "Observing screen & formulating plan...",
            currentIndex = 0,
            totalActions = 0
        )

        if (!introductorySpeech.isNullOrBlank()) {
            speak(introductorySpeech)
        }

        executionJob = coroutineScope.launch {
            runAutonomousLoop(taskId, trimmed)
        }
    }

    private suspend fun runAutonomousLoop(taskId: String, userRequest: String) {
        try {
            // Check for explicit memory command first
            if (memoryManager != null) {
                var isMemoryHandled = false
                val processed = memoryManager.handlePotentialMemoryCommand(
                    prompt = userRequest,
                    onConfirmed = { responseText: String ->
                        completeTask(taskId, userRequest, responseText, 0, 0L)
                        speak(responseText)
                        isMemoryHandled = true
                    },
                    onRequireConsent = { candidate: com.example.memory.model.MemoryCandidate ->
                        val msg = "A conflicting memory for '${candidate.key}' was detected. Please review in Memory Center."
                        completeTask(taskId, userRequest, msg, 0, 0L)
                        speak(msg)
                        isMemoryHandled = true
                    }
                )
                if (processed && isMemoryHandled) return
            }

            // ==========================================
            // PHASE 1: OBSERVE INITIAL SCREEN STATE
            // ==========================================
            updateStatus(AgentTaskState.OBSERVING, "Observing screen context...")
            val initialObservation = observeScreen()
            val initialSignature = ScreenSignature.from(initialObservation)

            _taskContext.value = _taskContext.value?.copy(
                currentPackage = initialObservation?.packageName ?: accessibilityController.getActivePackage(),
                currentScreenSnapshot = initialObservation,
                currentSignature = initialSignature,
                lastObservationTime = System.currentTimeMillis()
            )

            // ==========================================
            // PHASE 2: PLAN VIA GEMINI / TASK PLANNER
            // ==========================================
            updateStatus(AgentTaskState.PLANNING, "Synthesizing structured task plan...")
            val plan = formulatePlan(userRequest, initialObservation)

            if (plan == null || plan.steps.isEmpty()) {
                val unsupported = plan?.unsupportedCapability
                if (unsupported != null) {
                    val msg = "The capability to '$unsupported' is not available yet in NEX."
                    failTask(taskId, userRequest, msg, "UNSUPPORTED_CAPABILITY")
                    speak(msg)
                } else {
                    val msg = plan?.spokenResponse?.ifBlank { "Could not formulate a viable plan for this request." }
                        ?: "Could not formulate a viable plan for this request."
                    failTask(taskId, userRequest, msg, "PLANNING_FAILED")
                    speak(msg)
                }
                return
            }

            if (plan.spokenResponse.isNotBlank()) {
                speak(plan.spokenResponse)
            }

            _taskContext.value = _taskContext.value?.copy(
                goal = plan.goal,
                plannedActions = plan.steps.map { it.toAction() }
            )

            val remainingSteps = plan.steps.toMutableList()
            val completedSteps = mutableListOf<TaskStep>()
            val failedSteps = mutableListOf<TaskStep>()

            taskStateManager.updateState(
                state = TaskState.EXECUTING,
                actionDescription = "Executing autonomous plan (${remainingSteps.size} steps)...",
                currentIndex = 0,
                totalActions = remainingSteps.size
            )

            var stepIndex = 0
            val totalSteps = remainingSteps.size

            // ==========================================
            // PHASE 3: OBSERVE -> ACT -> VERIFY LOOP
            // ==========================================
            while (remainingSteps.isNotEmpty()) {
                val currentStep = remainingSteps.removeAt(0)
                stepIndex++

                // 1. Budget check
                val budgetResult = retryBudget.checkBudget()
                if (budgetResult is RetryBudget.BudgetCheckResult.Exceeded) {
                    failTask(taskId, userRequest, budgetResult.reason, budgetResult.limitType)
                    speak(budgetResult.reason)
                    return
                }

                // 2. Loop detection check
                if (loopDetector.isLoopDetected()) {
                    updateStatus(AgentTaskState.RECOVERING, "Loop detected. Initiating adaptive recovery...")
                    val replanned = attemptReplan(userRequest, currentStep, "Repeated action loop detected without UI change", completedSteps)
                    if (replanned != null && replanned.steps.isNotEmpty()) {
                        remainingSteps.clear()
                        remainingSteps.addAll(replanned.steps)
                        loopDetector.clear()
                        continue
                    } else {
                        failTask(taskId, userRequest, "Loop detected: unable to progress past current screen state safely.", "LOOP_DETECTED")
                        return
                    }
                }

                val action = currentStep.toAction()
                _taskContext.value = _taskContext.value?.copy(
                    currentAction = action,
                    taskState = AgentTaskState.VALIDATING,
                    conciseStatusExplanation = humanExplanationForStep(currentStep)
                )

                // 3. Validation check
                val validation = actionValidator.validate(action)
                if (!validation.isValid) {
                    val reason = validation.reason ?: "Action failed schema validation"
                    val replanned = attemptReplan(userRequest, currentStep, reason, completedSteps)
                    if (replanned != null && replanned.steps.isNotEmpty()) {
                        remainingSteps.clear()
                        remainingSteps.addAll(replanned.steps)
                        continue
                    } else {
                        failTask(taskId, userRequest, "Validation failed: $reason", "VALIDATION_FAILED")
                        return
                    }
                }

                // 4. Sensitive action confirmation check
                if (validation.requiresConfirmation || confirmationManager.isActionSensitive(action)) {
                    val explanation = "Confirmation required before: ${action.description}"
                    updateStatus(AgentTaskState.WAITING_FOR_CONFIRMATION, explanation)
                    taskStateManager.updateState(
                        state = TaskState.WAITING_FOR_USER,
                        actionDescription = explanation,
                        currentIndex = stepIndex,
                        totalActions = totalSteps,
                        pendingConfirmationPrompt = "Allow NEX to: ${action.description}?"
                    )

                    pendingConfirmationStep = currentStep
                    pendingConfirmationAction = action
                    speak("I need your confirmation before continuing with this action.")
                    return // Loop will resume via confirmPendingAction()
                }

                // 5. Execution
                retryBudget.recordStepExecution()
                val preObservation = observeScreen()
                val preSignature = ScreenSignature.from(preObservation)

                updateStatus(AgentTaskState.EXECUTING, humanExplanationForStep(currentStep))
                taskStateManager.updateState(
                    state = TaskState.EXECUTING,
                    actionDescription = humanExplanationForStep(currentStep),
                    currentIndex = stepIndex,
                    totalActions = totalSteps
                )

                var actionResult = executeStepWithTimeout(action, taskId)

                // 6. Post-Action Observation
                updateStatus(AgentTaskState.OBSERVING, "Observing screen update...")
                delay(350L) // Wait for Android UI to settle
                val postObservation = observeScreen()
                val postSignature = ScreenSignature.from(postObservation)

                loopDetector.recordAction(action, postSignature)

                _taskContext.value = _taskContext.value?.copy(
                    currentScreenSnapshot = postObservation,
                    currentSignature = postSignature,
                    currentPackage = postObservation?.packageName ?: accessibilityController.getActivePackage(),
                    lastObservationTime = System.currentTimeMillis()
                )

                // 7. Step Verification
                updateStatus(AgentTaskState.VERIFYING, "Verifying step completion...")
                val verification = verificationEngine.verifyStep(
                    step = currentStep,
                    actionResult = actionResult,
                    preSignature = preSignature,
                    postSignature = postSignature,
                    postSnapshot = postObservation
                )

                if (verification.isSuccess) {
                    completedSteps.add(currentStep)
                    _taskContext.value = _taskContext.value?.copy(
                        completedActions = completedSteps.map { it.toAction() }
                    )
                } else {
                    // 8. Failure Recovery Flow
                    val failureReason = if (verification is StepVerificationResult.Failed) {
                        verification.reason
                    } else actionResult.errorMessage ?: "Step verification failed"

                    updateStatus(AgentTaskState.RECOVERING, "Attempting recovery: $failureReason")
                    val recovered = attemptStepRecovery(currentStep, failureReason, postObservation)

                    if (recovered) {
                        completedSteps.add(currentStep)
                        _taskContext.value = _taskContext.value?.copy(
                            completedActions = completedSteps.map { it.toAction() }
                        )
                    } else {
                        // Replan via Gemini
                        val replanned = attemptReplan(userRequest, currentStep, failureReason, completedSteps)
                        if (replanned != null && replanned.steps.isNotEmpty()) {
                            remainingSteps.clear()
                            remainingSteps.addAll(replanned.steps)
                        } else {
                            failedSteps.add(currentStep)
                            _taskContext.value = _taskContext.value?.copy(
                                failedActions = failedSteps.map { it.toAction() }
                            )
                            failTask(taskId, userRequest, "Could not safely recover step: $failureReason", "STEP_FAILED")
                            speak("I couldn't safely complete this step: $failureReason")
                            return
                        }
                    }
                }
            }

            // ==========================================
            // PHASE 4: FINAL GOAL VERIFICATION
            // ==========================================
            updateStatus(AgentTaskState.VERIFYING, "Verifying final goal...")
            val finalObservation = observeScreen()
            val goalVerification = verificationEngine.verifyGoal(
                goal = plan.goal,
                lastSnapshot = finalObservation,
                completedSteps = completedSteps
            )

            val totalDuration = _taskContext.value?.durationMs ?: 0L
            if (goalVerification.isSuccess) {
                val completionSummary = when (goalVerification) {
                    is GoalVerificationResult.Verified -> goalVerification.summary
                    is GoalVerificationResult.PartiallyVerified -> goalVerification.summary
                    else -> "Goal achieved successfully."
                }
                completeTask(taskId, userRequest, completionSummary, completedSteps.size, totalDuration)
                speak("Task completed.")
            } else {
                val failSummary = (goalVerification as GoalVerificationResult.Failed).reason
                failTask(taskId, userRequest, failSummary, "GOAL_VERIFICATION_FAILED")
                speak("Task ended: $failSummary")
            }

        } catch (e: CancellationException) {
            handleCancellation(taskId, userRequest)
        } catch (e: Exception) {
            failTask(taskId, userRequest, "Unexpected orchestrator error: ${e.message}", "UNEXPECTED_ERROR")
        } finally {
            _isRunning.value = false
        }
    }

    private suspend fun formulatePlan(userRequest: String, screenSnapshot: ScreenSnapshot?): TaskPlan? {
        val visibleTexts = if (screenSnapshot != null) {
            screenSnapshot.elements.mapNotNull { it.meaningfulLabel }
        } else if (accessibilityController.isAccessibilityActive()) {
            accessibilityController.readScreenContent()
        } else {
            emptyList()
        }

        val activePkg = screenSnapshot?.packageName ?: accessibilityController.getActivePackage()

        // 1. Try Gemini Planning with dedicated Agent prompt
        val planningResult = taskPlanner.plan(userRequest)
        when (planningResult) {
            is PlanningResult.Success -> {
                val steps = planningResult.actions.mapIndexed { index, act ->
                    TaskStep(
                        id = "step_${index + 1}",
                        action = act.type,
                        target = TaskTarget(
                            text = act.parameters.targetText,
                            viewId = act.parameters.viewId,
                            xPercent = act.parameters.xPercent,
                            yPercent = act.parameters.yPercent
                        ),
                        parameters = act.parameters,
                        description = act.description
                    )
                }

                return TaskPlan(
                    goal = userRequest,
                    steps = steps,
                    spokenResponse = planningResult.spokenResponse,
                    unsupportedCapability = null
                )
            }
            is PlanningResult.Unsupported -> {
                return TaskPlan(
                    goal = userRequest,
                    steps = emptyList(),
                    spokenResponse = planningResult.spokenResponse,
                    unsupportedCapability = planningResult.capability
                )
            }
            is PlanningResult.Conversational -> {
                return TaskPlan(
                    goal = userRequest,
                    steps = emptyList(),
                    spokenResponse = planningResult.spokenResponse,
                    unsupportedCapability = null
                )
            }
            is PlanningResult.Error -> {
                return null
            }
        }
    }

    private suspend fun attemptReplan(
        userRequest: String,
        failedStep: TaskStep,
        failureReason: String,
        completedSteps: List<TaskStep>
    ): TaskPlan? {
        retryBudget.recordReplan()
        val budgetCheck = retryBudget.checkBudget()
        if (budgetCheck is RetryBudget.BudgetCheckResult.Exceeded) {
            return null
        }

        updateStatus(AgentTaskState.PLANNING, "Replanning strategy with updated screen...")
        val freshSnapshot = observeScreen()
        val visibleTexts = freshSnapshot?.elements?.mapNotNull { it.meaningfulLabel } ?: emptyList()
        val activePkg = freshSnapshot?.packageName ?: accessibilityController.getActivePackage()

        val promptWithContext = "Remaining goal: $userRequest. Already completed: ${completedSteps.joinToString { it.description }}. Failed step: ${failedStep.description}. Reason: $failureReason"
        return formulatePlan(promptWithContext, freshSnapshot)
    }

    /**
     * Controlled failure recovery without random clicking:
     * 1. Refresh screen.
     * 2. Search by content description.
     * 3. Search by resource ID.
     * 4. Search by compatible/fuzzy text.
     */
    private suspend fun attemptStepRecovery(
        step: TaskStep,
        failureReason: String,
        snapshot: ScreenSnapshot?
    ): Boolean {
        retryBudget.recordStepRetry()
        if (snapshot == null) return false

        // Case 1: TAP target not found
        if (step.action == ActionType.TAP) {
            val targetText = step.target?.text ?: step.parameters.targetText
            val targetId = step.target?.viewId ?: step.parameters.viewId

            // 1. Try finding by resource ID
            if (!targetId.isNullOrBlank()) {
                val match = ElementFinder.findByResourceId(snapshot.elements, targetId).firstOrNull()
                if (match != null) {
                    val bounds = match.bounds
                    val cx = (bounds.left + bounds.right) / 2f
                    val cy = (bounds.top + bounds.bottom) / 2f
                    return accessibilityController.performTapCoordinates(cx, cy)
                }
            }

            // 2. Try finding by content description
            if (!targetText.isNullOrBlank()) {
                val descMatch = snapshot.elements.firstOrNull {
                    it.contentDescription?.contains(targetText, ignoreCase = true) == true
                }
                if (descMatch != null) {
                    val bounds = descMatch.bounds
                    val cx = (bounds.left + bounds.right) / 2f
                    val cy = (bounds.top + bounds.bottom) / 2f
                    return accessibilityController.performTapCoordinates(cx, cy)
                }

                // 3. Try best fuzzy text match
                val best = ElementFinder.findBestMatch(snapshot.elements, targetText)
                if (best != null) {
                    val bounds = best.bounds
                    val cx = (bounds.left + bounds.right) / 2f
                    val cy = (bounds.top + bounds.bottom) / 2f
                    return accessibilityController.performTapCoordinates(cx, cy)
                }
            }
        }

        return false
    }

    private suspend fun executeStepWithTimeout(action: Action, taskId: String): ActionResult {
        val timeout = action.timeoutMs.coerceIn(1000L, 30000L)
        return try {
            withTimeout(timeout) {
                actionExecutor.execute(action, taskId)
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            ActionResult(
                actionId = action.id,
                actionType = action.type,
                status = ActionStatus.TIMEOUT,
                message = "Step execution timed out after ${action.timeoutMs}ms",
                errorCode = "TIMEOUT",
                errorMessage = "Action execution timed out"
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ActionResult(
                actionId = action.id,
                actionType = action.type,
                status = ActionStatus.FAILED,
                message = "Action failed: ${e.message}",
                errorCode = "EXECUTION_EXCEPTION"
            )
        }
    }

    private fun observeScreen(): ScreenSnapshot? {
        val result = accessibilityController.captureScreenSnapshot(filterSensitive = true)
        return if (result is ScreenSnapshotResult.Success) {
            result.snapshot
        } else {
            null
        }
    }

    private fun humanExplanationForStep(step: TaskStep): String {
        return when (step.action) {
            ActionType.OPEN_APP -> "Opening ${step.parameters.appName ?: step.parameters.packageName ?: "application"}."
            ActionType.TAP -> "Tapping ${step.target?.text ?: step.target?.contentDescription ?: step.target?.viewId ?: "target"}."
            ActionType.TYPE_TEXT -> "Entering text \"${step.parameters.inputText ?: ""}\"."
            ActionType.CLEAR_TEXT -> "Clearing text."
            ActionType.SCROLL_FORWARD, ActionType.SCROLL -> "Scrolling down."
            ActionType.SCROLL_BACKWARD -> "Scrolling up."
            ActionType.BACK -> "Navigating back."
            ActionType.HOME -> "Going to home screen."
            ActionType.SEARCH -> "Searching for \"${step.parameters.query ?: ""}\"."
            ActionType.WAIT -> "Waiting for screen to update."
            ActionType.VERIFY -> "Verifying target state."
            ActionType.ASK_CONFIRMATION -> "Waiting for confirmation."
            else -> step.description.ifBlank { "Executing ${step.action}." }
        }
    }

    private fun updateStatus(state: AgentTaskState, explanation: String) {
        _taskContext.value = _taskContext.value?.copy(
            taskState = state,
            conciseStatusExplanation = explanation
        )
        onExplanationUpdate?.invoke(explanation)
    }

    private fun completeTask(
        taskId: String,
        userRequest: String,
        summary: String,
        completedCount: Int,
        durationMs: Long
    ) {
        updateStatus(AgentTaskState.COMPLETED, summary)
        taskStateManager.completeTask(summary)
        _isRunning.value = false

        coroutineScope.launch {
            memoryRepository.recordTaskHistory(
                taskId = taskId,
                prompt = userRequest,
                status = "COMPLETED",
                actionsCount = completedCount,
                executionTimeMs = durationMs,
                summary = summary
            )
        }
    }

    private fun failTask(
        taskId: String,
        userRequest: String,
        errorMessage: String,
        errorCode: String
    ) {
        updateStatus(AgentTaskState.FAILED, errorMessage)
        taskStateManager.failTask(errorMessage)
        _isRunning.value = false

        val duration = _taskContext.value?.durationMs ?: 0L
        coroutineScope.launch {
            memoryRepository.recordTaskHistory(
                taskId = taskId,
                prompt = userRequest,
                status = "FAILED",
                actionsCount = _taskContext.value?.completedActions?.size ?: 0,
                executionTimeMs = duration,
                summary = errorMessage
            )
        }
    }

    private fun handleCancellation(taskId: String, userRequest: String) {
        updateStatus(AgentTaskState.CANCELLED, "Task cancelled by user.")
        taskStateManager.cancelTask()
        _isRunning.value = false

        val duration = _taskContext.value?.durationMs ?: 0L
        coroutineScope.launch {
            memoryRepository.recordTaskHistory(
                taskId = taskId,
                prompt = userRequest,
                status = "CANCELLED",
                actionsCount = _taskContext.value?.completedActions?.size ?: 0,
                executionTimeMs = duration,
                summary = "Task cancelled by user."
            )
        }
    }

    fun cancelTask(reason: String = "User requested stop", informUser: Boolean = true) {
        executionJob?.cancel()
        executionJob = null
        confirmationManager.clearPending()
        pendingConfirmationStep = null
        pendingConfirmationAction = null

        val current = _taskContext.value
        if (current != null && !current.isTerminal) {
            handleCancellation(current.taskId, current.userRequest)
            if (informUser) {
                speak("Task stopped.")
            }
        } else {
            taskStateManager.cancelTask()
            _isRunning.value = false
            if (informUser) {
                speak("Task stopped.")
            }
        }
    }

    fun confirmPendingAction(approved: Boolean) {
        val action = pendingConfirmationAction ?: run {
            cancelTask(reason = "No pending action", informUser = false)
            return
        }
        val step = pendingConfirmationStep ?: run {
            cancelTask(reason = "No pending step", informUser = false)
            return
        }

        pendingConfirmationAction = null
        pendingConfirmationStep = null

        if (!approved) {
            cancelTask(reason = "User denied confirmation", informUser = true)
            return
        }

        // User confirmed: resume execution of the step
        val taskId = _taskContext.value?.taskId ?: UUID.randomUUID().toString()
        val request = _taskContext.value?.userRequest ?: ""

        executionJob = coroutineScope.launch {
            // Re-run execution of this confirmed step and continue remaining
            updateStatus(AgentTaskState.EXECUTING, "Authorized. Executing ${action.description}...")
            val actionResult = executeStepWithTimeout(action.copy(requiresConfirmation = false), taskId)

            delay(350L)
            val postObservation = observeScreen()
            val postSignature = ScreenSignature.from(postObservation)

            if (actionResult.isSuccess) {
                val updatedCompleted = (_taskContext.value?.completedActions ?: emptyList()) + action
                _taskContext.value = _taskContext.value?.copy(
                    completedActions = updatedCompleted,
                    currentScreenSnapshot = postObservation,
                    currentSignature = postSignature
                )
                completeTask(taskId, request, "Action '${action.description}' executed successfully.", updatedCompleted.size, _taskContext.value?.durationMs ?: 0L)
                speak("Completed.")
            } else {
                failTask(taskId, request, actionResult.errorMessage ?: "Execution failed", "EXECUTION_FAILED")
            }
        }
    }

    fun pauseTask() {
        if (!_isRunning.value) return
        executionJob?.cancel()
        executionJob = null
        _taskContext.value = _taskContext.value?.createResumableState()
        _isRunning.value = false
        speak("Task paused.")
    }

    fun resumeTask() {
        val ctx = _taskContext.value
        if (ctx != null && ctx.isResumable) {
            startTask(ctx.userRequest, "Resuming task: ${ctx.goal}")
        }
    }

    private fun speak(text: String) {
        if (text.isNotBlank()) {
            onSpokenFeedback?.invoke(text)
        }
    }
}
