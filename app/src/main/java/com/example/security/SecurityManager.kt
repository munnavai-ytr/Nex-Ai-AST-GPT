package com.example.security

import android.app.KeyguardManager
import android.content.Context
import android.content.SharedPreferences
import androidx.fragment.app.FragmentActivity
import com.example.automation.Action
import com.example.automation.confirmation.ConfirmationManager
import com.example.security.events.SecurityEventDao
import com.example.security.events.SecurityEventLogger
import com.example.security.events.SecurityEventType
import com.example.security.voice.CommandSecurityDecision
import com.example.security.voice.OwnerAuthenticationManager
import com.example.security.voice.OwnerCommandPolicy
import com.example.security.voice.SpeakerVerificationManager
import com.example.security.voice.SpeakerVerificationResult
import com.example.security.voice.VoiceAuthStatus
import com.example.security.voice.VoiceEnrollmentMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SecurityState(
    val isDeviceSecure: Boolean = false,
    val isPinProtectionEnabled: Boolean = false,
    val isBiometricAvailable: Boolean = false,
    val biometricAvailability: BiometricAvailability = BiometricAvailability.Unknown,
    val isVoiceEnrolled: Boolean = false,
    val isVoiceAuthEnabled: Boolean = true,
    val isSpeakerModelAvailable: Boolean = true,
    val requireConfirmationForSensitiveActions: Boolean = true,
    val enrollmentMetadata: VoiceEnrollmentMetadata? = null,
    val isSessionUnlocked: Boolean = true,
    val lastAuthTimestamp: Long = 0L,
    val lastAuthStatus: VoiceAuthStatus = VoiceAuthStatus.VOICE_UNAVAILABLE
)

class SecurityManager(
    private val context: Context,
    val ownerAuthManager: OwnerAuthenticationManager = OwnerAuthenticationManager(context),
    val biometricAuthManager: BiometricAuthManager = BiometricAuthManager(context),
    val securityEventDao: SecurityEventDao? = null
) {

    val eventLogger = SecurityEventLogger(securityEventDao)
    private val prefs: SharedPreferences = context.getSharedPreferences("nex_security_prefs", Context.MODE_PRIVATE)
    private val _securityState = MutableStateFlow(evaluateSecurityState())
    val securityState: StateFlow<SecurityState> = _securityState.asStateFlow()

    fun refreshState() {
        ownerAuthManager.refreshState()
        biometricAuthManager.checkAvailability()
        _securityState.value = evaluateSecurityState()
    }

    private fun evaluateSecurityState(): SecurityState {
        val keyguardManager = try {
            context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        } catch (_: Exception) {
            null
        }
        val isDeviceSecure = try {
            keyguardManager?.isDeviceSecure ?: false
        } catch (_: Exception) {
            false
        }

        val pinEnabled = prefs.getBoolean("pin_protection_enabled", false)
        val requireSensitiveConfirm = prefs.getBoolean("require_sensitive_confirm", true)
        val voiceAuthEnabled = prefs.getBoolean("voice_auth_enabled", true)
        val lastAuth = prefs.getLong("last_auth_timestamp", 0L)

        val bioAvailability = biometricAuthManager.checkAvailability()
        val isVoiceEnrolled = ownerAuthManager.storage.isEnrolled()
        val metadata = ownerAuthManager.storage.getMetadata()
        val isModelAvailable = ownerAuthManager.speakerVerificationManager.isModelAvailable.value

        return SecurityState(
            isDeviceSecure = isDeviceSecure,
            isPinProtectionEnabled = pinEnabled,
            isBiometricAvailable = bioAvailability.isAvailable,
            biometricAvailability = bioAvailability,
            isVoiceEnrolled = isVoiceEnrolled,
            isVoiceAuthEnabled = voiceAuthEnabled,
            isSpeakerModelAvailable = isModelAvailable,
            requireConfirmationForSensitiveActions = requireSensitiveConfirm,
            enrollmentMetadata = if (isVoiceEnrolled) metadata else null,
            isSessionUnlocked = true,
            lastAuthTimestamp = lastAuth
        )
    }

    fun isActionSensitive(action: Action, confirmationManager: ConfirmationManager? = null): Boolean {
        val level = SecurityPolicyEngine.evaluateRequiredLevel(action, confirmationManager)
        return level == AuthenticationLevel.LEVEL_2_DEVICE_CREDENTIAL
    }

    fun evaluateActionLevel(action: Action, confirmationManager: ConfirmationManager? = null): AuthenticationLevel {
        return SecurityPolicyEngine.evaluateRequiredLevel(action, confirmationManager)
    }

    fun evaluateCommandPolicy(
        action: Action,
        verificationResult: SpeakerVerificationResult?,
        isDeviceAuthenticated: Boolean = false
    ): CommandSecurityDecision {
        val decision = OwnerCommandPolicy.evaluate(
            action = action,
            isVoiceEnrolled = _securityState.value.isVoiceEnrolled,
            isVoiceAuthEnabled = _securityState.value.isVoiceAuthEnabled,
            verificationResult = verificationResult,
            isDeviceAuthenticated = isDeviceAuthenticated
        )

        // Log decision outcome
        if (verificationResult != null) {
            val eventType = when (decision.status) {
                VoiceAuthStatus.VOICE_MATCH -> SecurityEventType.VOICE_VERIFICATION_MATCH
                VoiceAuthStatus.VOICE_NO_MATCH -> SecurityEventType.VOICE_VERIFICATION_MISMATCH
                VoiceAuthStatus.OWNER_NOT_VERIFIED -> SecurityEventType.VOICE_VERIFICATION_INCONCLUSIVE
                else -> SecurityEventType.CONFIG_CHANGED
            }
            eventLogger.logEvent(
                eventType = eventType,
                description = "Evaluated command security policy for ${action.type}",
                outcome = decision.status.name,
                details = decision.reason
            )
        }

        return decision
    }

    fun verifySpeakerVoice(pcmBytes: ByteArray): SpeakerVerificationResult {
        val result = ownerAuthManager.speakerVerificationManager.verifySpeaker(pcmBytes)
        if (result.isVerified) {
            prefs.edit().putLong("last_auth_timestamp", System.currentTimeMillis()).apply()
            _securityState.value = _securityState.value.copy(
                lastAuthTimestamp = System.currentTimeMillis(),
                lastAuthStatus = VoiceAuthStatus.VOICE_MATCH
            )
        }
        return result
    }

    suspend fun authenticateBiometric(
        activity: FragmentActivity,
        title: String = "NEX Security Authentication",
        subtitle: String = "Authorize Sensitive Action",
        description: String = "Android biometric verification is required for this operation."
    ): BiometricAuthResult {
        val result = biometricAuthManager.authenticate(activity, title, subtitle, description)
        when (result) {
            is BiometricAuthResult.Success -> {
                eventLogger.logEvent(
                    eventType = SecurityEventType.BIOMETRIC_AUTH_SUCCESS,
                    description = "Android biometric authentication succeeded",
                    outcome = "SUCCESS"
                )
                prefs.edit().putLong("last_auth_timestamp", System.currentTimeMillis()).apply()
                _securityState.value = _securityState.value.copy(
                    lastAuthTimestamp = System.currentTimeMillis(),
                    lastAuthStatus = VoiceAuthStatus.OWNER_VERIFIED
                )
            }
            is BiometricAuthResult.Failed, is BiometricAuthResult.Error -> {
                eventLogger.logEvent(
                    eventType = SecurityEventType.BIOMETRIC_AUTH_FAILED,
                    description = "Android biometric authentication failed",
                    outcome = "FAILED"
                )
            }
            is BiometricAuthResult.Cancelled -> {
                eventLogger.logEvent(
                    eventType = SecurityEventType.BIOMETRIC_AUTH_CANCELLED,
                    description = "Biometric prompt cancelled by user",
                    outcome = "CANCELLED"
                )
            }
            else -> {}
        }
        return result
    }

    suspend fun authenticateOwnerForAction(
        activity: FragmentActivity,
        actionTitle: String
    ): Boolean {
        val result = authenticateBiometric(
            activity = activity,
            title = "NEX Security Authentication",
            subtitle = actionTitle,
            description = "Android biometric verification is required to authorize this sensitive action."
        )
        return result is BiometricAuthResult.Success
    }

    suspend fun deleteVoiceProfileProtected(activity: FragmentActivity): Boolean {
        val authResult = authenticateBiometric(
            activity = activity,
            title = "Delete Voice Profile",
            subtitle = "Confirm Owner Identity",
            description = "Biometric authentication is required to permanently delete the enrolled voice profile."
        )
        if (authResult !is BiometricAuthResult.Success) {
            return false
        }
        return deleteVoiceProfile()
    }

    suspend fun setRequireConfirmationProtected(activity: FragmentActivity, require: Boolean): Boolean {
        val authResult = authenticateBiometric(
            activity = activity,
            title = "Security Settings Modification",
            subtitle = "Confirm Owner Identity",
            description = "Biometric authentication is required to modify sensitive action confirmation policy."
        )
        if (authResult !is BiometricAuthResult.Success) {
            return false
        }
        setRequireConfirmationForSensitiveActions(require)
        return true
    }

    suspend fun setVoiceAuthEnabledProtected(activity: FragmentActivity, enabled: Boolean): Boolean {
        val authResult = authenticateBiometric(
            activity = activity,
            title = "Security Settings Modification",
            subtitle = "Confirm Owner Identity",
            description = "Biometric authentication is required to toggle voice verification policy."
        )
        if (authResult !is BiometricAuthResult.Success) {
            return false
        }
        setVoiceAuthEnabled(enabled)
        return true
    }

    suspend fun setPinProtectionProtected(activity: FragmentActivity, enabled: Boolean): Boolean {
        val authResult = authenticateBiometric(
            activity = activity,
            title = "Security Settings Modification",
            subtitle = "Confirm Owner Identity",
            description = "Biometric authentication is required to modify device PIN protection policy."
        )
        if (authResult !is BiometricAuthResult.Success) {
            return false
        }
        setPinProtectionEnabled(enabled)
        return true
    }

    fun deleteVoiceProfile(): Boolean {
        val deleted = ownerAuthManager.deleteEnrollment()
        if (deleted) {
            eventLogger.logEvent(
                eventType = SecurityEventType.VOICE_PROFILE_DELETED,
                description = "Owner voice profile deleted from Keystore storage",
                outcome = "SUCCESS"
            )
        }
        refreshState()
        return deleted
    }

    fun setRequireConfirmationForSensitiveActions(require: Boolean) {
        prefs.edit().putBoolean("require_sensitive_confirm", require).apply()
        eventLogger.logEvent(
            eventType = SecurityEventType.CONFIG_CHANGED,
            description = "Changed sensitive action confirmation policy to: $require",
            outcome = "SUCCESS"
        )
        refreshState()
    }

    fun setVoiceAuthEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("voice_auth_enabled", enabled).apply()
        ownerAuthManager.setVoiceAuthEnabled(enabled)
        eventLogger.logEvent(
            eventType = SecurityEventType.CONFIG_CHANGED,
            description = "Changed voice authentication enabled setting to: $enabled",
            outcome = "SUCCESS"
        )
        refreshState()
    }

    fun setPinProtectionEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("pin_protection_enabled", enabled).apply()
        eventLogger.logEvent(
            eventType = SecurityEventType.CONFIG_CHANGED,
            description = "Changed PIN protection policy to: $enabled",
            outcome = "SUCCESS"
        )
        refreshState()
    }
}
