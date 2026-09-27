package com.example.automation.agent

data class RetryBudget(
    val maxSteps: Int = 15,
    val maxRetriesPerStep: Int = 2,
    val maxReplans: Int = 3,
    val maxTaskDurationMs: Long = 120_000L
) {
    var stepCount: Int = 0
        private set
    var currentStepRetries: Int = 0
        private set
    var replanCount: Int = 0
        private set
    var startTimeMs: Long = System.currentTimeMillis()
        private set

    fun reset() {
        stepCount = 0
        currentStepRetries = 0
        replanCount = 0
        startTimeMs = System.currentTimeMillis()
    }

    fun recordStepExecution() {
        stepCount++
        currentStepRetries = 0
    }

    fun recordStepRetry() {
        currentStepRetries++
    }

    fun recordReplan() {
        replanCount++
    }

    sealed class BudgetCheckResult {
        object Allowed : BudgetCheckResult()
        data class Exceeded(val reason: String, val limitType: String) : BudgetCheckResult()
    }

    fun checkBudget(): BudgetCheckResult {
        val elapsed = System.currentTimeMillis() - startTimeMs
        if (elapsed > maxTaskDurationMs) {
            return BudgetCheckResult.Exceeded(
                reason = "Task execution timed out after ${elapsed / 1000}s (max limit: ${maxTaskDurationMs / 1000}s)",
                limitType = "DURATION_LIMIT"
            )
        }

        if (stepCount >= maxSteps) {
            return BudgetCheckResult.Exceeded(
                reason = "Exceeded maximum allowed steps ($maxSteps). Task halted safely.",
                limitType = "STEP_LIMIT"
            )
        }

        if (currentStepRetries > maxRetriesPerStep) {
            return BudgetCheckResult.Exceeded(
                reason = "Exceeded maximum retries for current step ($maxRetriesPerStep).",
                limitType = "STEP_RETRY_LIMIT"
            )
        }

        if (replanCount > maxReplans) {
            return BudgetCheckResult.Exceeded(
                reason = "Exceeded maximum autonomous replans ($maxReplans). Stopped to prevent thrashing.",
                limitType = "REPLAN_LIMIT"
            )
        }

        return BudgetCheckResult.Allowed
    }
}
