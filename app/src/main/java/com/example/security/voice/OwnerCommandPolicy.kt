package com.example.security.voice

import com.example.automation.Action
import com.example.automation.ActionType
import com.example.security.AuthenticationLevel

enum class VoiceAuthStatus {
    OWNER_VERIFIED,
    OWNER_NOT_VERIFIED,
    VOICE_MATCH,
    VOICE_NO_MATCH,
    VOICE_UNAVAILABLE,
    DEVICE_AUTH_REQUIRED,
    AUTHENTICATION_EXPIRED
}

data class CommandSecurityDecision(
    val status: VoiceAuthStatus,
    val isAllowedToExecute: Boolean,
    val requiresBiometric: Boolean,
    val requiresExplicitConfirmation: Boolean,
    val reason: String
)

object OwnerCommandPolicy {

    /**
     * Evaluates whether an action can execute given the speaker verification result
     * and the action's inherent risk classification.
     */
    fun evaluate(
        action: Action,
        isVoiceEnrolled: Boolean,
        isVoiceAuthEnabled: Boolean,
        verificationResult: SpeakerVerificationResult?,
        isDeviceAuthenticated: Boolean = false
    ): CommandSecurityDecision {

        val isHighRisk = isHighRiskAction(action)
        val isMediumRisk = isMediumRiskAction(action)

        // 1. HIGH-RISK ACTIONS: Always require device authentication (Biometric/PIN) + confirmation.
        // Voice recognition alone is NEVER sufficient for high-risk operations.
        if (isHighRisk) {
            if (!isDeviceAuthenticated) {
                return CommandSecurityDecision(
                    status = VoiceAuthStatus.DEVICE_AUTH_REQUIRED,
                    isAllowedToExecute = false,
                    requiresBiometric = true,
                    requiresExplicitConfirmation = true,
                    reason = "High-risk operation requires Android Biometric / Device PIN authentication and explicit confirmation."
                )
            }
            return CommandSecurityDecision(
                status = VoiceAuthStatus.OWNER_VERIFIED,
                isAllowedToExecute = true,
                requiresBiometric = false,
                requiresExplicitConfirmation = true,
                reason = "Owner authenticated via device credentials. Ready for confirmation."
            )
        }

        // 2. If voice authentication is disabled by user policy, allow standard low/medium-risk flow
        if (!isVoiceAuthEnabled || !isVoiceEnrolled) {
            return CommandSecurityDecision(
                status = VoiceAuthStatus.VOICE_UNAVAILABLE,
                isAllowedToExecute = true,
                requiresBiometric = false,
                requiresExplicitConfirmation = isMediumRisk,
                reason = if (!isVoiceEnrolled) "Voice not enrolled. Executing with standard confirmation policy." else "Voice auth disabled in settings."
            )
        }

        // 3. Evaluate actual speaker verification output
        if (verificationResult == null) {
            return CommandSecurityDecision(
                status = VoiceAuthStatus.VOICE_UNAVAILABLE,
                isAllowedToExecute = !isMediumRisk,
                requiresBiometric = false,
                requiresExplicitConfirmation = isMediumRisk,
                reason = "No acoustic voice sample available for verification."
            )
        }

        return when (verificationResult.status) {
            SpeakerVerificationStatus.VERIFIED -> {
                CommandSecurityDecision(
                    status = VoiceAuthStatus.VOICE_MATCH,
                    isAllowedToExecute = true,
                    requiresBiometric = false,
                    requiresExplicitConfirmation = isMediumRisk,
                    reason = "Acoustic match detected (heuristic similarity: ${(verificationResult.similarityScore * 100).toInt()}%). Uncertified for high-risk authorization."
                )
            }
            SpeakerVerificationStatus.REJECTED -> {
                CommandSecurityDecision(
                    status = VoiceAuthStatus.VOICE_NO_MATCH,
                    isAllowedToExecute = false,
                    requiresBiometric = true,
                    requiresExplicitConfirmation = true,
                    reason = "Voice mismatch detected. Security policy blocked unauthenticated speaker."
                )
            }
            SpeakerVerificationStatus.INCONCLUSIVE -> {
                CommandSecurityDecision(
                    status = VoiceAuthStatus.OWNER_NOT_VERIFIED,
                    isAllowedToExecute = !isMediumRisk,
                    requiresBiometric = isMediumRisk,
                    requiresExplicitConfirmation = true,
                    reason = "Voice confidence is inconclusive. Confirmation required."
                )
            }
            SpeakerVerificationStatus.LOW_QUALITY_AUDIO -> {
                CommandSecurityDecision(
                    status = VoiceAuthStatus.OWNER_NOT_VERIFIED,
                    isAllowedToExecute = !isMediumRisk,
                    requiresBiometric = false,
                    requiresExplicitConfirmation = isMediumRisk,
                    reason = "Audio quality insufficient for acoustic speaker verification."
                )
            }
            SpeakerVerificationStatus.NOT_ENROLLED,
            SpeakerVerificationStatus.MODEL_UNAVAILABLE,
            SpeakerVerificationStatus.ERROR -> {
                CommandSecurityDecision(
                    status = VoiceAuthStatus.VOICE_UNAVAILABLE,
                    isAllowedToExecute = !isMediumRisk,
                    requiresBiometric = false,
                    requiresExplicitConfirmation = isMediumRisk,
                    reason = verificationResult.message
                )
            }
        }
    }

    private fun isHighRiskAction(action: Action): Boolean {
        val target = (action.parameters.targetText ?: action.parameters.appName ?: action.description).lowercase()
        val pkg = (action.parameters.packageName ?: "").lowercase()

        val isSensitiveType = action.type == ActionType.ASK_CONFIRMATION
        val isFinancialOrSecurity = target.contains("delete") || target.contains("purchase") ||
                target.contains("buy") || target.contains("pay") || target.contains("transfer") ||
                target.contains("send message") || target.contains("password") || target.contains("pin") ||
                target.contains("wipe") || target.contains("factory reset") || target.contains("export") ||
                target.contains("memory") || target.contains("credential") || target.contains("security") ||
                pkg.contains("wallet") || pkg.contains("bank") || pkg.contains("security") || pkg.contains("settings")

        return isSensitiveType || isFinancialOrSecurity || action.requiresConfirmation
    }

    private fun isMediumRiskAction(action: Action): Boolean {
        return action.type in listOf(ActionType.TYPE_TEXT, ActionType.CLEAR_TEXT)
    }
}
