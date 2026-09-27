package com.example.core

enum class TaskState {
    IDLE,
    LISTENING,
    PROCESSING,
    PLANNING,
    EXECUTING,
    VERIFYING,
    WAITING_FOR_USER,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class TaskExecutionState(
    val state: TaskState = TaskState.IDLE,
    val currentTaskId: String? = null,
    val prompt: String? = null,
    val currentActionDescription: String? = null,
    val currentActionIndex: Int = 0,
    val totalActions: Int = 0,
    val progressPercent: Float = 0f,
    val resultSummary: String? = null,
    val errorMessage: String? = null,
    val pendingConfirmationPrompt: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
