package com.example.memory.policy

import com.example.memory.model.MemoryCandidate
import com.example.memory.model.MemoryCandidateAction
import com.example.memory.model.MemoryCategory
import com.example.memory.model.MemoryConsentStatus
import com.example.memory.model.MemorySensitivity
import com.example.memory.model.StructuredMemory

sealed class PolicyEvaluationResult {
    object Allowed : PolicyEvaluationResult()
    data class RequiresExplicitConsent(val reason: String) : PolicyEvaluationResult()
    data class Rejected(val reason: String) : PolicyEvaluationResult()
}

class MemoryPolicy {

    private val forbiddenKeyKeywords = listOf(
        "password", "passwd", "passcode", "pin", "otp", "token", "auth_token",
        "secret", "api_key", "cvv", "credit_card", "debit_card", "ssn",
        "biometric_raw", "voice_embedding", "speaker_embedding"
    )

    private val forbiddenValuePatterns = listOf(
        Regex("^[0-9]{3,4}$"), // Typical 3-4 digit PIN/CVV isolated value
        Regex("^Bearer\\s+[A-Za-z0-9-_.]+$", RegexOption.IGNORE_CASE),
        Regex("^AIza[0-9A-Za-z-_]{35}$") // Google API key pattern
    )

    /**
     * Evaluates whether a memory candidate is safe and permissible to persist.
     */
    fun evaluateCandidate(
        candidate: MemoryCandidate,
        consentGiven: Boolean = false
    ): PolicyEvaluationResult {
        val cleanKey = candidate.key.trim().lowercase()
        val cleanValue = candidate.value.trim()

        if (cleanKey.isBlank()) {
            return PolicyEvaluationResult.Rejected("Memory key cannot be empty.")
        }

        if (cleanValue.isBlank() && candidate.action != MemoryCandidateAction.FORGET && candidate.action != MemoryCandidateAction.QUERY) {
            return PolicyEvaluationResult.Rejected("Memory value cannot be empty.")
        }

        // Security check: Never allow storing raw credentials or security secrets
        if (forbiddenKeyKeywords.any { cleanKey.contains(it) }) {
            return PolicyEvaluationResult.Rejected(
                "NEX policy strictly prohibits saving passwords, PINs, OTPs, or authentication tokens in memory."
            )
        }

        if (forbiddenValuePatterns.any { it.matches(cleanValue) }) {
            return PolicyEvaluationResult.Rejected(
                "NEX policy rejected this memory candidate because it resembles a sensitive credential or secret."
            )
        }

        // Schema limits
        if (cleanKey.length > 120) {
            return PolicyEvaluationResult.Rejected("Memory key exceeds maximum length of 120 characters.")
        }
        if (cleanValue.length > 5000) {
            return PolicyEvaluationResult.Rejected("Memory value exceeds maximum length of 5000 characters.")
        }

        // Consent verification
        if (!consentGiven) {
            return PolicyEvaluationResult.RequiresExplicitConsent(
                "Explicit owner consent is required before persisting new information to memory."
            )
        }

        return PolicyEvaluationResult.Allowed
    }

    /**
     * Automatically classifies the sensitivity level of a memory candidate.
     */
    fun classifySensitivity(key: String, value: String, category: MemoryCategory): MemorySensitivity {
        val combined = "$key $value".lowercase()

        val strictlyConfidentialTerms = listOf(
            "bank", "salary", "medical", "doctor", "health", "hospital", "prescription",
            "passport", "financial", "tax", "emergency contact", "security question"
        )
        if (strictlyConfidentialTerms.any { combined.contains(it) }) {
            return MemorySensitivity.STRICTLY_CONFIDENTIAL
        }

        val sensitiveTerms = listOf(
            "address", "home", "workplace", "location", "phone", "email", "schedule",
            "family", "meeting", "contact", "partner", "wife", "husband", "child"
        )
        if (sensitiveTerms.any { combined.contains(it) } || category == MemoryCategory.USER_APPROVED_CONTACT_ALIASES) {
            return MemorySensitivity.SENSITIVE
        }

        return MemorySensitivity.NORMAL
    }

    /**
     * Detects if an existing memory conflicts with a proposed candidate.
     */
    fun hasConflict(existing: StructuredMemory, candidate: MemoryCandidate): Boolean {
        if (existing.key.equals(candidate.key, ignoreCase = true) &&
            existing.category == candidate.category
        ) {
            return !existing.value.equals(candidate.value.trim(), ignoreCase = false)
        }
        return false
    }
}
