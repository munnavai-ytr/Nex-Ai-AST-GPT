package com.example.security

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

sealed class BiometricAvailability(val displayName: String, val isAvailable: Boolean) {
    object Available : BiometricAvailability("Biometrics & Device Lock Available", true)
    object DeviceCredentialOnly : BiometricAvailability("PIN / Pattern Lock Only", true)
    object NoneEnrolled : BiometricAvailability("No Biometrics or Device PIN Enrolled", false)
    object UnsupportedHardware : BiometricAvailability("Biometric Hardware Missing", false)
    object HardwareUnavailable : BiometricAvailability("Biometric Hardware Busy or Unavailable", false)
    object Unknown : BiometricAvailability("Checking Security Hardware...", false)
}

sealed class BiometricAuthResult {
    object Success : BiometricAuthResult()
    data class Error(val errorCode: Int, val errString: CharSequence) : BiometricAuthResult()
    object Failed : BiometricAuthResult()
    object Cancelled : BiometricAuthResult()
    data class NotAvailable(val reason: String = "Biometric / Device credential authentication is not available.") : BiometricAuthResult()

    val isSuccess: Boolean get() = this is Success
}

class BiometricAuthManager(private val context: Context) {

    private val _availability = MutableStateFlow<BiometricAvailability>(BiometricAvailability.Unknown)
    val availability: StateFlow<BiometricAvailability> = _availability.asStateFlow()

    init {
        checkAvailability()
    }

    fun checkAvailability(): BiometricAvailability {
        val result = try {
            val biometricManager = BiometricManager.from(context)
            val authenticators = BIOMETRIC_STRONG or BIOMETRIC_WEAK or DEVICE_CREDENTIAL

            when (biometricManager.canAuthenticate(authenticators)) {
                BiometricManager.BIOMETRIC_SUCCESS -> {
                    val isStrong = biometricManager.canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
                    if (isStrong) BiometricAvailability.Available else BiometricAvailability.DeviceCredentialOnly
                }
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NoneEnrolled
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                    // Check if PIN/pattern lock is available without biometric sensor
                    val credOnly = biometricManager.canAuthenticate(DEVICE_CREDENTIAL)
                    if (credOnly == BiometricManager.BIOMETRIC_SUCCESS) {
                        BiometricAvailability.DeviceCredentialOnly
                    } else {
                        BiometricAvailability.UnsupportedHardware
                    }
                }
                BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                    val credOnly = biometricManager.canAuthenticate(DEVICE_CREDENTIAL)
                    if (credOnly == BiometricManager.BIOMETRIC_SUCCESS) {
                        BiometricAvailability.DeviceCredentialOnly
                    } else {
                        BiometricAvailability.HardwareUnavailable
                    }
                }
                else -> {
                    val credOnly = biometricManager.canAuthenticate(DEVICE_CREDENTIAL)
                    if (credOnly == BiometricManager.BIOMETRIC_SUCCESS) {
                        BiometricAvailability.DeviceCredentialOnly
                    } else {
                        BiometricAvailability.NoneEnrolled
                    }
                }
            }
        } catch (_: Exception) {
            BiometricAvailability.UnsupportedHardware
        }

        _availability.value = result
        return result
    }

    /**
     * Shows a real Android BiometricPrompt attached to the host activity.
     */
    suspend fun authenticate(
        activity: FragmentActivity,
        title: String = "NEX Security Authentication",
        subtitle: String = "Confirm Owner Identity",
        description: String = "Android biometric verification is required for sensitive operations."
    ): BiometricAuthResult = suspendCancellableCoroutine { continuation ->

        val availability = checkAvailability()
        if (!availability.isAvailable) {
            continuation.resume(BiometricAuthResult.NotAvailable(availability.displayName))
            return@suspendCancellableCoroutine
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val promptBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setDescription(description)

        val authenticators = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        } else {
            BIOMETRIC_STRONG or BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        }

        try {
            promptBuilder.setAllowedAuthenticators(authenticators)
        } catch (_: Exception) {
            @Suppress("DEPRECATION")
            promptBuilder.setDeviceCredentialAllowed(true)
        }

        val promptInfo = promptBuilder.build()

        val biometricPrompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                if (continuation.isActive) {
                    continuation.resume(BiometricAuthResult.Success)
                }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (continuation.isActive) {
                    if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_CANCELED
                    ) {
                        continuation.resume(BiometricAuthResult.Cancelled)
                    } else {
                        continuation.resume(BiometricAuthResult.Error(errorCode, errString))
                    }
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                // Individual fingerprint attempt failure - biometric prompt remains open until error or success
            }
        })

        continuation.invokeOnCancellation {
            try {
                biometricPrompt.cancelAuthentication()
            } catch (_: Exception) {}
        }

        try {
            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            if (continuation.isActive) {
                continuation.resume(BiometricAuthResult.Error(-1, e.message ?: "Authentication failed"))
            }
        }
    }
}
