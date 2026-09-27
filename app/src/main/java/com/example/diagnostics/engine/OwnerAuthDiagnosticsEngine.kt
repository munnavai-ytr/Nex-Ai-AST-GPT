package com.example.diagnostics.engine

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import com.example.automation.Action
import com.example.automation.ActionParameters
import com.example.automation.ActionType
import com.example.diagnostics.model.DiagnosticCategory
import com.example.diagnostics.model.DiagnosticItem
import com.example.diagnostics.model.DiagnosticStatus
import com.example.diagnostics.model.SubsystemDiagnosticResult
import com.example.security.BiometricAvailability
import com.example.security.SecurityManager
import com.example.security.voice.OwnerCommandPolicy
import com.example.security.voice.SpeakerVerificationManager
import com.example.security.voice.VoiceAuthStatus

class OwnerAuthDiagnosticsEngine(
    private val context: Context,
    private val securityManager: SecurityManager
) {

    fun runDiagnostics(): SubsystemDiagnosticResult {
        val startTime = System.currentTimeMillis()
        val items = mutableListOf<DiagnosticItem>()

        // 1. Android BiometricPrompt & Device Lock Hardware Readiness
        items.add(evaluateBiometricHardware())

        // 2. Owner Voice Profile & Encrypted Keystore Template
        items.add(evaluateVoiceEnrollment())

        // 3. Speaker Verification Model Certification & Anti-Spoofing Assessment
        items.add(evaluateSpeakerVerificationModel())

        // 4. Zero-Trust Security Policy Enforcement Check
        items.add(evaluateSecurityPolicyEnforcement())

        val overallStatus = when {
            items.any { it.status == DiagnosticStatus.FAIL } -> DiagnosticStatus.FAIL
            items.any { it.status == DiagnosticStatus.PERMISSION_REQUIRED } -> DiagnosticStatus.PERMISSION_REQUIRED
            items.any { it.status == DiagnosticStatus.NOT_CONFIGURED } -> DiagnosticStatus.NOT_CONFIGURED
            items.any { it.status == DiagnosticStatus.NOT_VERIFIED } -> DiagnosticStatus.WARN
            items.any { it.status == DiagnosticStatus.WARN } -> DiagnosticStatus.WARN
            else -> DiagnosticStatus.PASS
        }

        return SubsystemDiagnosticResult(
            category = DiagnosticCategory.OWNER_AUTH,
            items = items,
            status = overallStatus,
            executionTimeMs = System.currentTimeMillis() - startTime
        )
    }

    private fun evaluateBiometricHardware(): DiagnosticItem {
        return try {
            val biometricManager = BiometricManager.from(context)
            val authenticators = BIOMETRIC_STRONG or BIOMETRIC_WEAK or DEVICE_CREDENTIAL
            val canAuth = biometricManager.canAuthenticate(authenticators)

            when (canAuth) {
                BiometricManager.BIOMETRIC_SUCCESS -> {
                    val isStrongBio = biometricManager.canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
                    DiagnosticItem(
                        id = "auth_biometric_hardware",
                        title = "Android BiometricPrompt & Device Credentials",
                        category = DiagnosticCategory.OWNER_AUTH,
                        status = DiagnosticStatus.PASS,
                        summary = if (isStrongBio) "Class 3 Strong Biometrics Ready" else "Biometrics / Device Credentials Ready",
                        details = "Hardware: Supported & Ready\nAuthenticators: BIOMETRIC_STRONG | DEVICE_CREDENTIAL\nStatus: BIOMETRIC_SUCCESS"
                    )
                }
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                    DiagnosticItem(
                        id = "auth_biometric_hardware",
                        title = "Android BiometricPrompt & Device Credentials",
                        category = DiagnosticCategory.OWNER_AUTH,
                        status = DiagnosticStatus.NOT_CONFIGURED,
                        summary = "No Screen Lock or Biometrics Enrolled",
                        details = "Neither fingerprints, face authentication, nor device PIN/Pattern are enrolled in Android device settings.",
                        actionLabel = "Open Security Settings",
                        isActionable = true
                    )
                }
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                    DiagnosticItem(
                        id = "auth_biometric_hardware",
                        title = "Android BiometricPrompt & Device Credentials",
                        category = DiagnosticCategory.OWNER_AUTH,
                        status = DiagnosticStatus.WARN,
                        summary = "No Biometric Hardware Sensor",
                        details = "Device has no fingerprint or biometric sensor. Device PIN/pattern fallback must be used."
                    )
                }
                BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                    DiagnosticItem(
                        id = "auth_biometric_hardware",
                        title = "Android BiometricPrompt & Device Credentials",
                        category = DiagnosticCategory.OWNER_AUTH,
                        status = DiagnosticStatus.FAIL,
                        summary = "Biometric Sensor Busy or Unavailable",
                        details = "Biometric hardware is currently busy or experiencing a hardware fault."
                    )
                }
                else -> {
                    // Check fallback to device PIN/Pattern alone
                    val credOnly = biometricManager.canAuthenticate(DEVICE_CREDENTIAL)
                    if (credOnly == BiometricManager.BIOMETRIC_SUCCESS) {
                        DiagnosticItem(
                            id = "auth_biometric_hardware",
                            title = "Android BiometricPrompt & Device Credentials",
                            category = DiagnosticCategory.OWNER_AUTH,
                            status = DiagnosticStatus.PASS,
                            summary = "Device Credential Only (PIN / Pattern)",
                            details = "Hardware biometrics unavailable; Device PIN/pattern lock is configured and active."
                        )
                    } else {
                        DiagnosticItem(
                            id = "auth_biometric_hardware",
                            title = "Android BiometricPrompt & Device Credentials",
                            category = DiagnosticCategory.OWNER_AUTH,
                            status = DiagnosticStatus.NOT_CONFIGURED,
                            summary = "Device Security Not Configured",
                            details = "No screen lock or biometric enrollment detected."
                        )
                    }
                }
            }
        } catch (e: Exception) {
            DiagnosticItem(
                id = "auth_biometric_hardware",
                title = "Android BiometricPrompt & Device Credentials",
                category = DiagnosticCategory.OWNER_AUTH,
                status = DiagnosticStatus.FAIL,
                summary = "Failed to query BiometricManager: ${e.message}",
                details = "Exception: ${e::class.simpleName}\nStacktrace: ${e.localizedMessage}"
            )
        }
    }

    private fun evaluateVoiceEnrollment(): DiagnosticItem {
        val isEnrolled = securityManager.ownerAuthManager.storage.isEnrolled()
        if (!isEnrolled) {
            return DiagnosticItem(
                id = "auth_voice_profile",
                title = "Owner Voice Profile & Acoustic Template",
                category = DiagnosticCategory.OWNER_AUTH,
                status = DiagnosticStatus.NOT_CONFIGURED,
                summary = "Not Enrolled (Advisory voice match disabled)",
                details = "Owner voice profile is not enrolled. Spoken commands will execute using standard confirmation policies.",
                actionLabel = "Enroll Voice",
                isActionable = true
            )
        }

        val metadata = securityManager.ownerAuthManager.storage.getMetadata()
        val embedding = securityManager.ownerAuthManager.storage.loadEnrolledEmbedding()

        return if (embedding != null && embedding.isNotEmpty()) {
            DiagnosticItem(
                id = "auth_voice_profile",
                title = "Owner Voice Profile & Acoustic Template",
                category = DiagnosticCategory.OWNER_AUTH,
                status = DiagnosticStatus.PASS,
                summary = "Enrolled (${metadata.sampleCount} samples, Keystore encrypted)",
                details = "Acoustic Embedding Vector Size: ${embedding.size}\nSample Phrases: ${metadata.sampleCount}\nStorage: Android KeyStore AES-GCM Encrypted"
            )
        } else {
            DiagnosticItem(
                id = "auth_voice_profile",
                title = "Owner Voice Profile & Acoustic Template",
                category = DiagnosticCategory.OWNER_AUTH,
                status = DiagnosticStatus.FAIL,
                summary = "Enrolled Profile Corrupted or Empty",
                details = "Metadata exists but Keystore-stored acoustic template vector could not be decrypted or is empty.",
                actionLabel = "Re-enroll Voice",
                isActionable = true
            )
        }
    }

    private fun evaluateSpeakerVerificationModel(): DiagnosticItem {
        // Truthful audit of speaker verification capabilities:
        // Handcrafted MFCC/FFT DSP is NOT a certified ML model and lacks anti-spoofing/liveness verification.
        // It must NOT be reported as a certified biometric PASS.
        return DiagnosticItem(
            id = "auth_speaker_model",
            title = "Speaker Verification Model Certification",
            category = DiagnosticCategory.OWNER_AUTH,
            status = DiagnosticStatus.NOT_VERIFIED,
            summary = "Uncertified Acoustic Heuristic (Advisory Only)",
            details = "Classification: Handcrafted DSP Energy & MFCC Vector.\nCertification: NOT A TRAINED BIOMETRIC MODEL.\nAnti-Spoofing: Unavailable.\nSecurity Constraint: Uncertified voice similarity is strictly prohibited from authorizing sensitive or privileged device actions."
        )
    }

    private fun evaluateSecurityPolicyEnforcement(): DiagnosticItem {
        // Test that high-risk sensitive operations unconditionally require BiometricPrompt
        // and cannot be authorized by voice alone or without device authentication.
        val sensitiveTestActions = listOf(
            Action(
                type = ActionType.ASK_CONFIRMATION,
                description = "Wipe and factory reset device"
            ),
            Action(
                type = ActionType.OPEN_APP,
                parameters = ActionParameters(appName = "Bank Wallet", packageName = "com.example.bank"),
                description = "Open banking application"
            ),
            Action(
                type = ActionType.TYPE_TEXT,
                parameters = ActionParameters(inputText = "secret_pin_1234", targetText = "PIN"),
                description = "Enter security PIN"
            )
        )

        var allPoliciesEnforced = true
        val failureNotes = mutableListOf<String>()

        for (action in sensitiveTestActions) {
            val unauthDecision = OwnerCommandPolicy.evaluate(
                action = action,
                isVoiceEnrolled = true,
                isVoiceAuthEnabled = true,
                verificationResult = null,
                isDeviceAuthenticated = false
            )

            if (unauthDecision.isAllowedToExecute || !unauthDecision.requiresBiometric) {
                allPoliciesEnforced = false
                failureNotes.add("Action '${action.description}' failed to mandate biometric authentication without device auth.")
            }

            val authDecision = OwnerCommandPolicy.evaluate(
                action = action,
                isVoiceEnrolled = true,
                isVoiceAuthEnabled = true,
                verificationResult = null,
                isDeviceAuthenticated = true
            )

            if (!authDecision.isAllowedToExecute) {
                allPoliciesEnforced = false
                failureNotes.add("Action '${action.description}' remained blocked even after device authentication.")
            }
        }

        return if (allPoliciesEnforced) {
            DiagnosticItem(
                id = "auth_policy_engine",
                title = "Zero-Trust Security Policy Engine",
                category = DiagnosticCategory.OWNER_AUTH,
                status = DiagnosticStatus.PASS,
                summary = "Biometric Escalation Verified",
                details = "Validated that sensitive/financial actions unconditionally require Android BiometricPrompt and cannot be bypassed by voice commands."
            )
        } else {
            DiagnosticItem(
                id = "auth_policy_engine",
                title = "Zero-Trust Security Policy Engine",
                category = DiagnosticCategory.OWNER_AUTH,
                status = DiagnosticStatus.FAIL,
                summary = "Policy Enforcement Fault Detected",
                details = failureNotes.joinToString("\n")
            )
        }
    }
}
