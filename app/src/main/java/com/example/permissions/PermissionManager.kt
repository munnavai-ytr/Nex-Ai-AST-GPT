package com.example.permissions

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import com.example.accessibility.NexAccessibilityService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PermissionItem(
    val id: String,
    val name: String,
    val description: String,
    val isGranted: Boolean,
    val isRequired: Boolean = true,
    val requiresSystemSettings: Boolean = false
)

data class PermissionStateSummary(
    val isMicrophoneGranted: Boolean = false,
    val isNotificationGranted: Boolean = false,
    val isAccessibilityEnabled: Boolean = false,
    val allRequiredGranted: Boolean = false,
    val items: List<PermissionItem> = emptyList()
)

class PermissionManager(private val context: Context) {

    private val _permissionState = MutableStateFlow(checkAllPermissions())
    val permissionState: StateFlow<PermissionStateSummary> = _permissionState.asStateFlow()

    fun refreshPermissions(): PermissionStateSummary {
        val summary = checkAllPermissions()
        _permissionState.value = summary
        return summary
    }

    fun isMicrophoneGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isNotificationGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun isAccessibilityEnabled(): Boolean {
        // Double check: first directly from NexAccessibilityService live instance
        if (NexAccessibilityService.isServiceConnected()) {
            return true
        }

        // Secondary check via AccessibilityManager enabled service list
        val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        if (accessibilityManager != null) {
            val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            )
            val serviceName = "${context.packageName}/${NexAccessibilityService::class.java.canonicalName}"
            for (service in enabledServices) {
                if (service.id.equals(serviceName, ignoreCase = true) ||
                    service.resolveInfo.serviceInfo.packageName == context.packageName
                ) {
                    return true
                }
            }
        }

        // Tertiary check via Settings.Secure
        return try {
            val enabledServicesSetting = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            enabledServicesSetting.contains(context.packageName)
        } catch (_: Exception) {
            false
        }
    }

    private fun checkAllPermissions(): PermissionStateSummary {
        val mic = isMicrophoneGranted()
        val notif = isNotificationGranted()
        val access = isAccessibilityEnabled()

        val items = listOf(
            PermissionItem(
                id = "microphone",
                name = "Microphone Access",
                description = "Required to capture natural voice commands directly on-device.",
                isGranted = mic,
                isRequired = true,
                requiresSystemSettings = false
            ),
            PermissionItem(
                id = "accessibility",
                name = "Accessibility Service",
                description = "Required for UI perception, active app awareness, and automated actions.",
                isGranted = access,
                isRequired = true,
                requiresSystemSettings = true
            ),
            PermissionItem(
                id = "notification",
                name = "System Notifications",
                description = "Required to display active background status and completion alerts.",
                isGranted = notif,
                isRequired = false,
                requiresSystemSettings = false
            )
        )

        return PermissionStateSummary(
            isMicrophoneGranted = mic,
            isNotificationGranted = notif,
            isAccessibilityEnabled = access,
            allRequiredGranted = mic && access,
            items = items
        )
    }

    fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun openNotificationSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }
        context.startActivity(intent)
    }
}
