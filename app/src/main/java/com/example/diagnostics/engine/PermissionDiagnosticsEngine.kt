package com.example.diagnostics.engine

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.biometric.BiometricManager
import androidx.core.content.ContextCompat
import com.example.accessibility.AccessibilityController
import com.example.diagnostics.model.DiagnosticCategory
import com.example.diagnostics.model.DiagnosticItem
import com.example.diagnostics.model.DiagnosticStatus
import com.example.diagnostics.model.SubsystemDiagnosticResult
import com.example.permissions.PermissionManager

class PermissionDiagnosticsEngine(
    private val context: Context,
    private val permissionManager: PermissionManager,
    private val accessibilityController: AccessibilityController
) {

    fun runDiagnostics(): SubsystemDiagnosticResult {
        val startTime = System.currentTimeMillis()
        val items = mutableListOf<DiagnosticItem>()

        // 1. Microphone
        val micGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        items.add(
            DiagnosticItem(
                id = "perm_mic",
                title = "Microphone Permission (Audio Record)",
                category = DiagnosticCategory.PERMISSIONS,
                status = if (micGranted) DiagnosticStatus.PASS else DiagnosticStatus.FAIL,
                summary = if (micGranted) "Granted - Voice perception ready" else "Denied - Cannot hear commands",
                details = "Permission: android.permission.RECORD_AUDIO\nRequired for wake-word, voice transcription, and owner acoustic modeling.",
                actionLabel = if (!micGranted) "Grant Microphone" else null,
                isActionable = !micGranted
            )
        )

        // 2. Accessibility Automation Service
        val isA11yActive = accessibilityController.isAccessibilityActive()
        items.add(
            DiagnosticItem(
                id = "perm_accessibility",
                title = "Accessibility Automation Service",
                category = DiagnosticCategory.PERMISSIONS,
                status = if (isA11yActive) DiagnosticStatus.PASS else DiagnosticStatus.WARN,
                summary = if (isA11yActive) "Active & Bound" else "Inactive - Tap to open Accessibility Settings",
                details = "Service: com.example.accessibility.NexAccessibilityService\nAllows NEX to perceive UI hierarchy, inspect nodes, and perform taps/swipes/text entry.",
                actionLabel = if (!isA11yActive) "Enable Accessibility" else null,
                isActionable = !isA11yActive
            )
        )

        // 3. Post Notifications (Android 13+)
        val notifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        items.add(
            DiagnosticItem(
                id = "perm_notifications",
                title = "Notification Delivery Permission",
                category = DiagnosticCategory.PERMISSIONS,
                status = if (notifGranted) DiagnosticStatus.PASS else DiagnosticStatus.WARN,
                summary = if (notifGranted) "Granted - Foreground & task alerts active" else "Denied - Background notifications muted",
                details = "Permission: android.permission.POST_NOTIFICATIONS\nNeeded for foreground service notification and task status alerts.",
                actionLabel = if (!notifGranted) "Allow Notifications" else null,
                isActionable = !notifGranted
            )
        )

        // 4. Battery Optimization Exemption
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isIgnoringBatteryOpt = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
        items.add(
            DiagnosticItem(
                id = "perm_battery_opt",
                title = "Battery Optimization Whitelist",
                category = DiagnosticCategory.PERMISSIONS,
                status = if (isIgnoringBatteryOpt) DiagnosticStatus.PASS else DiagnosticStatus.WARN,
                summary = if (isIgnoringBatteryOpt) "Unrestricted background execution" else "Optimized (May be killed in deep sleep)",
                details = "Setting: REQUEST_IGNORE_BATTERY_OPTIMIZATIONS\nEnsures persistent wake-word listener and automation tasks aren't killed by Doze mode.",
                actionLabel = if (!isIgnoringBatteryOpt) "Request Exemption" else null,
                isActionable = !isIgnoringBatteryOpt
            )
        )

        // 5. Biometrics & Hardware Auth Capability
        val biometricManager = BiometricManager.from(context)
        val canAuth = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        val bioStatus = when (canAuth) {
            BiometricManager.BIOMETRIC_SUCCESS -> DiagnosticStatus.PASS
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> DiagnosticStatus.WARN
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> DiagnosticStatus.INFO
            else -> DiagnosticStatus.WARN
        }
        val bioSummary = when (canAuth) {
            BiometricManager.BIOMETRIC_SUCCESS -> "Biometrics / Device PIN enrolled & ready"
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "No biometric/PIN enrolled on device"
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "No biometric hardware present"
            else -> "Biometric status code: $canAuth"
        }
        items.add(
            DiagnosticItem(
                id = "perm_biometric",
                title = "Biometrics & Device Credential Security",
                category = DiagnosticCategory.PERMISSIONS,
                status = bioStatus,
                summary = bioSummary,
                details = "BiometricManager code: $canAuth\nUsed for confirming sensitive high-risk actions (passwords, banking apps, data deletion).",
                actionLabel = if (canAuth == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) "Setup Screen Lock" else null,
                isActionable = canAuth == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
            )
        )

        val overallStatus = when {
            items.any { it.status == DiagnosticStatus.FAIL } -> DiagnosticStatus.FAIL
            items.any { it.status == DiagnosticStatus.WARN } -> DiagnosticStatus.WARN
            else -> DiagnosticStatus.PASS
        }

        return SubsystemDiagnosticResult(
            category = DiagnosticCategory.PERMISSIONS,
            items = items,
            status = overallStatus,
            executionTimeMs = System.currentTimeMillis() - startTime
        )
    }
}
