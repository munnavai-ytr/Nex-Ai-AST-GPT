package com.example.automation.logging

import com.example.automation.Action
import com.example.automation.ActionResult
import com.example.automation.ActionStatus
import com.example.automation.ActionType
import com.example.automation.verifier.AutomationVerificationResult
import com.example.memory.MemoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class AutomationLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val taskId: String,
    val actionType: ActionType,
    val description: String,
    val status: ActionStatus,
    val verificationStatus: String,
    val durationMs: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val details: String = "",
    val errorMessage: String? = null
)

class AutomationExecutionLogger(
    private val memoryRepository: MemoryRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    private val _recentLogs = MutableStateFlow<List<AutomationLogEntry>>(emptyList())
    val recentLogs: StateFlow<List<AutomationLogEntry>> = _recentLogs.asStateFlow()

    fun logExecution(
        taskId: String,
        action: Action,
        result: ActionResult,
        verification: AutomationVerificationResult?
    ) {
        val verificationText = when (verification) {
            is AutomationVerificationResult.Verified -> "VERIFIED"
            is AutomationVerificationResult.Unchanged -> "UNCHANGED"
            is AutomationVerificationResult.Failed -> "FAILED"
            null -> "NOT_VERIFIED"
        }

        val entry = AutomationLogEntry(
            taskId = taskId,
            actionType = action.type,
            description = action.description,
            status = result.status,
            verificationStatus = verificationText,
            durationMs = result.durationMs,
            details = result.message,
            errorMessage = result.errorMessage
        )

        val updated = listOf(entry) + _recentLogs.value.take(49)
        _recentLogs.value = updated

        scope.launch {
            try {
                memoryRepository.recordLog(
                    taskId = taskId,
                    actionType = action.type.name,
                    status = result.status.name,
                    errorMessage = result.errorMessage,
                    durationMs = result.durationMs,
                    details = "${action.description} -> ${result.message} [Verified: $verificationText]"
                )
            } catch (_: Exception) {}
        }
    }

    fun clearLogs() {
        _recentLogs.value = emptyList()
    }
}
