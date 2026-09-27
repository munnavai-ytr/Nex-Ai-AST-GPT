package com.example.automation.agent

import com.example.accessibility.model.ScreenSnapshot
import com.example.automation.Action
import java.util.UUID

enum class AgentTaskState {
    IDLE,
    OBSERVING,
    PLANNING,
    VALIDATING,
    EXECUTING,
    VERIFYING,
    RECOVERING,
    WAITING_FOR_CONFIRMATION,
    WAITING_FOR_USER_INPUT,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class AgentTaskContext(
    val taskId: String = UUID.randomUUID().toString(),
    val userRequest: String,
    val goal: String = "",
    val currentPackage: String? = null,
    val currentScreenSnapshot: ScreenSnapshot? = null,
    val currentSignature: ScreenSignature? = null,
    val plannedActions: List<Action> = emptyList(),
    val completedActions: List<Action> = emptyList(),
    val failedActions: List<Action> = emptyList(),
    val currentAction: Action? = null,
    val taskState: AgentTaskState = AgentTaskState.IDLE,
    val attemptCount: Int = 0,
    val replanCount: Int = 0,
    val startTime: Long = System.currentTimeMillis(),
    val lastObservationTime: Long = System.currentTimeMillis(),
    val securityContext: Map<String, String> = emptyMap(),
    val conciseStatusExplanation: String = "Initializing agent...",
    val isResumable: Boolean = false,
    val pausedStepIndex: Int = 0
) {
    val durationMs: Long
        get() = System.currentTimeMillis() - startTime

    val isTerminal: Boolean
        get() = taskState == AgentTaskState.COMPLETED ||
                taskState == AgentTaskState.FAILED ||
                taskState == AgentTaskState.CANCELLED

    /**
     * Creates a safe snapshot of task state suitable for pausing or resuming.
     * Sensitive parameters and raw snapshots are redacted.
     */
    fun createResumableState(): AgentTaskContext {
        return this.copy(
            taskState = AgentTaskState.PAUSED,
            isResumable = true,
            currentScreenSnapshot = null, // Do not store memory-heavy or sensitive raw trees
            securityContext = securityContext.filterKeys { !it.contains("secret", ignoreCase = true) }
        )
    }
}
