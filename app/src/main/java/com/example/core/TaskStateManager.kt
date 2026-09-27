package com.example.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class TaskStateManager {

    private val _executionState = MutableStateFlow(TaskExecutionState())
    val executionState: StateFlow<TaskExecutionState> = _executionState.asStateFlow()

    fun startListening(): String {
        val taskId = UUID.randomUUID().toString()
        _executionState.value = TaskExecutionState(
            state = TaskState.LISTENING,
            currentTaskId = taskId,
            timestamp = System.currentTimeMillis()
        )
        return taskId
    }

    fun startTaskWithPrompt(prompt: String): String {
        val taskId = UUID.randomUUID().toString()
        _executionState.value = TaskExecutionState(
            state = TaskState.PROCESSING,
            currentTaskId = taskId,
            prompt = prompt,
            timestamp = System.currentTimeMillis()
        )
        return taskId
    }

    fun updateState(
        state: TaskState,
        actionDescription: String? = null,
        currentIndex: Int = 0,
        totalActions: Int = 0,
        errorMessage: String? = null,
        resultSummary: String? = null,
        pendingConfirmationPrompt: String? = null
    ) {
        val current = _executionState.value
        val progress = if (totalActions > 0) {
            (currentIndex.toFloat() / totalActions.toFloat()).coerceIn(0f, 1f)
        } else {
            when (state) {
                TaskState.IDLE -> 0f
                TaskState.LISTENING -> 0.1f
                TaskState.PROCESSING -> 0.25f
                TaskState.PLANNING -> 0.4f
                TaskState.EXECUTING -> 0.7f
                TaskState.VERIFYING -> 0.9f
                TaskState.COMPLETED -> 1f
                else -> current.progressPercent
            }
        }

        _executionState.value = current.copy(
            state = state,
            currentActionDescription = actionDescription ?: current.currentActionDescription,
            currentActionIndex = currentIndex,
            totalActions = totalActions,
            progressPercent = progress,
            errorMessage = errorMessage,
            resultSummary = resultSummary ?: current.resultSummary,
            pendingConfirmationPrompt = pendingConfirmationPrompt,
            timestamp = System.currentTimeMillis()
        )
    }

    fun completeTask(summary: String) {
        val current = _executionState.value
        _executionState.value = current.copy(
            state = TaskState.COMPLETED,
            resultSummary = summary,
            progressPercent = 1f,
            timestamp = System.currentTimeMillis()
        )
    }

    fun failTask(errorMessage: String) {
        val current = _executionState.value
        _executionState.value = current.copy(
            state = TaskState.FAILED,
            errorMessage = errorMessage,
            timestamp = System.currentTimeMillis()
        )
    }

    fun cancelTask() {
        val current = _executionState.value
        _executionState.value = current.copy(
            state = TaskState.CANCELLED,
            timestamp = System.currentTimeMillis()
        )
    }

    fun resetToIdle() {
        _executionState.value = TaskExecutionState(state = TaskState.IDLE)
    }
}
