package com.example.automation

import com.example.memory.MemoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ExecutionLogger(
    private val repository: MemoryRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    fun logAction(
        taskId: String,
        actionType: ActionType,
        status: ActionStatus,
        durationMs: Long = 0L,
        errorMessage: String? = null,
        details: String = ""
    ) {
        scope.launch {
            try {
                repository.recordLog(
                    taskId = taskId,
                    actionType = actionType.name,
                    status = status.name,
                    errorMessage = errorMessage,
                    durationMs = durationMs,
                    details = details
                )
            } catch (_: Exception) {
                // Ignore logging failures to prevent disrupting action flow
            }
        }
    }
}
