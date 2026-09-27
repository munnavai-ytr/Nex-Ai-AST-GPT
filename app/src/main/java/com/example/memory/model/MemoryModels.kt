package com.example.memory.model

enum class MemoryCategory(val displayName: String, val description: String) {
    USER_PREFERENCES("User Preferences", "Personal device and assistant preferences"),
    APP_ALIASES("App Aliases", "Custom application shortcuts and aliases"),
    USER_PROVIDED_FACTS("User Facts", "Explicitly saved personal facts and notes"),
    WORKFLOWS("Workflows", "Saved multi-step autonomous action routines"),
    ROUTINE_PREFERENCES("Routine Preferences", "Scheduled and recurring trigger preferences"),
    TASK_CONTEXT("Task Context", "Persistent context for ongoing tasks"),
    USER_APPROVED_CONTACT_ALIASES("Contact Aliases", "Approved contact shortcuts and aliases");

    companion object {
        fun fromString(value: String): MemoryCategory {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: USER_PROVIDED_FACTS
        }
    }
}

enum class MemorySensitivity(val displayName: String, val requiresBiometricAuth: Boolean) {
    NORMAL("Standard", false),
    SENSITIVE("Sensitive (Encrypted)", false),
    STRICTLY_CONFIDENTIAL("Confidential (Biometric Required)", true);

    companion object {
        fun fromString(value: String): MemorySensitivity {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: NORMAL
        }
    }
}

enum class MemoryConsentStatus {
    EXPLICIT_USER_CONSENT,
    SYSTEM_APPROVED_POLICY,
    PENDING_CONFIRMATION,
    DENIED;

    companion object {
        fun fromString(value: String): MemoryConsentStatus {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: EXPLICIT_USER_CONSENT
        }
    }
}

enum class MemorySource {
    USER_VOICE_COMMAND,
    USER_TEXT_COMMAND,
    USER_MANUAL_ENTRY,
    GEMINI_EXTRACTED_EXPLICIT,
    SAVED_WORKFLOW,
    APP_ALIAS_BINDING,
    SYSTEM_DEFAULT;

    companion object {
        fun fromString(value: String): MemorySource {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: USER_MANUAL_ENTRY
        }
    }
}

enum class MemoryRetentionPolicy(val displayName: String, val durationMs: Long?) {
    PERMANENT("Never Expire (Permanent)", null),
    DAYS_7("7 Days", 7L * 24 * 60 * 60 * 1000),
    DAYS_30("30 Days", 30L * 24 * 60 * 60 * 1000),
    DAYS_90("90 Days", 90L * 24 * 60 * 60 * 1000),
    DAYS_180("180 Days", 180L * 24 * 60 * 60 * 1000),
    DAYS_365("1 Year", 365L * 24 * 60 * 60 * 1000);

    companion object {
        fun fromDuration(ms: Long?): MemoryRetentionPolicy {
            if (ms == null) return PERMANENT
            return entries.filter { it.durationMs != null }.minByOrNull { kotlin.math.abs(it.durationMs!! - ms) } ?: PERMANENT
        }
    }
}

data class StructuredMemory(
    val id: Long = 0,
    val category: MemoryCategory,
    val key: String,
    val value: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val source: MemorySource = MemorySource.USER_MANUAL_ENTRY,
    val consentStatus: MemoryConsentStatus = MemoryConsentStatus.EXPLICIT_USER_CONSENT,
    val sensitivity: MemorySensitivity = MemorySensitivity.NORMAL,
    val expiration: Long? = null,
    val lastAccessedAt: Long = System.currentTimeMillis(),
    val isEncrypted: Boolean = false,
    val metadata: Map<String, String> = emptyMap()
) {
    val isExpired: Boolean
        get() = expiration != null && System.currentTimeMillis() > expiration
}

enum class MemoryCandidateAction {
    SAVE,
    FORGET,
    QUERY,
    UPDATE
}

data class MemoryCandidate(
    val action: MemoryCandidateAction,
    val category: MemoryCategory,
    val key: String,
    val value: String,
    val sensitivity: MemorySensitivity = MemorySensitivity.NORMAL,
    val reason: String = "",
    val confidence: Float = 1.0f,
    val rawSource: String = ""
)

data class MemorySearchResult(
    val memory: StructuredMemory,
    val relevanceScore: Double,
    val matchReason: String
)

sealed class MemoryWriteResult {
    data class Success(val memory: StructuredMemory, val message: String) : MemoryWriteResult()
    data class Conflict(val existingMemory: StructuredMemory, val candidate: MemoryCandidate, val message: String) : MemoryWriteResult()
    data class Rejected(val reason: String) : MemoryWriteResult()
    data class Error(val message: String, val cause: Throwable? = null) : MemoryWriteResult()
}
