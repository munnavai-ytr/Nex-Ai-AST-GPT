package com.example.memory.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.memory.model.MemoryCategory
import com.example.memory.model.MemoryConsentStatus
import com.example.memory.model.MemorySensitivity
import com.example.memory.model.MemorySource
import com.example.memory.model.StructuredMemory

@Entity(
    tableName = "structured_memories",
    indices = [
        Index(value = ["category"]),
        Index(value = ["key"]),
        Index(value = ["category", "key"], unique = false),
        Index(value = ["expiration"]),
        Index(value = ["updatedAt"])
    ]
)
data class StructuredMemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,
    val key: String,
    val value: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val source: String = MemorySource.USER_MANUAL_ENTRY.name,
    val consentStatus: String = MemoryConsentStatus.EXPLICIT_USER_CONSENT.name,
    val sensitivity: String = MemorySensitivity.NORMAL.name,
    val expiration: Long? = null,
    val lastAccessedAt: Long = System.currentTimeMillis(),
    val isEncrypted: Boolean = false,
    val metadataJson: String = "{}"
) {
    fun toDomain(decryptedValue: String = value): StructuredMemory {
        return StructuredMemory(
            id = id,
            category = MemoryCategory.fromString(category),
            key = key,
            value = decryptedValue,
            createdAt = createdAt,
            updatedAt = updatedAt,
            source = MemorySource.fromString(source),
            consentStatus = MemoryConsentStatus.fromString(consentStatus),
            sensitivity = MemorySensitivity.fromString(sensitivity),
            expiration = expiration,
            lastAccessedAt = lastAccessedAt,
            isEncrypted = isEncrypted
        )
    }

    companion object {
        fun fromDomain(memory: StructuredMemory, storedValue: String = memory.value): StructuredMemoryEntity {
            return StructuredMemoryEntity(
                id = memory.id,
                category = memory.category.name,
                key = memory.key,
                value = storedValue,
                createdAt = memory.createdAt,
                updatedAt = memory.updatedAt,
                source = memory.source.name,
                consentStatus = memory.consentStatus.name,
                sensitivity = memory.sensitivity.name,
                expiration = memory.expiration,
                lastAccessedAt = memory.lastAccessedAt,
                isEncrypted = memory.isEncrypted
            )
        }
    }
}

@Entity(
    tableName = "workflows",
    indices = [
        Index(value = ["workflowId"], unique = true),
        Index(value = ["triggerPhrase"])
    ]
)
data class WorkflowEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workflowId: String,
    val name: String,
    val description: String = "",
    val triggerPhrase: String = "",
    val actionsJson: String,
    val requiredParamsJson: String = "{}",
    val preconditionsJson: String = "[]",
    val requiresConfirmation: Boolean = false,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "memory_change_history",
    indices = [
        Index(value = ["memoryKey"]),
        Index(value = ["timestamp"])
    ]
)
data class MemoryChangeHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val memoryKey: String,
    val category: String,
    val action: String, // INSERT, UPDATE, DELETE, FORGET, EXPIRE
    val timestamp: Long = System.currentTimeMillis(),
    val changeSummary: String = ""
)

// Legacy / Compatibility entities preserved from Push 1–7
@Entity(tableName = "user_preferences")
data class UserPreferenceEntity(
    @PrimaryKey val key: String,
    val value: String,
    val category: String = "general",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "memory_items")
data class MemoryItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val category: String = "user_fact",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "command_aliases")
data class CommandAliasEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val alias: String, // e.g. "yt"
    val expansion: String, // e.g. "YouTube"
    val description: String = "",
    val usageCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val triggerPhrase: String,
    val description: String = "",
    val actionsJson: String,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "task_history")
data class TaskHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: String,
    val prompt: String,
    val status: String,
    val actionsCount: Int,
    val executionTimeMs: Long,
    val summary: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "execution_logs")
data class ExecutionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: String,
    val actionType: String,
    val status: String,
    val errorMessage: String? = null,
    val durationMs: Long = 0L,
    val details: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
