package com.example.automation.permissions

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
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

enum class AccessibilityStatus {
    ENABLED,
    DISABLED,
    UNAVAILABLE
}

data class AutomationPermissionState(
    val accessibilityStatus: AccessibilityStatus = AccessibilityStatus.DISABLED,
    val isAccessibilityConnected: Boolean = false,
    val hasAudioPermission: Boolean = false,
    val hasNotificationPermission: Boolean = false,
    val hasOverlayPermission: Boolean = false,
    val canPerformGestures: Boolean = false
) {
    val isFullyReady: Boolean
        get() = accessibilityStatus == AccessibilityStatus.ENABLED && isAccessibilityConnected
}

class AutomationPermissionManager(private val context: Context) {

    private val _permissionState = MutableStateFlow(checkPermissions())
    val permissionState: StateFlow<AutomationPermissionState> = _permissionState.asStateFlow()

    fun refresh(): AutomationPermissionState {
        val state = checkPermissions()
        _permissionState.value = state
        return state
    }

    fun isAccessibilityServiceEnabled(): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            ?: return false

        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC or AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        val expectedComponent = ComponentName(context, NexAccessibilityService::class.java)

        return enabledServices.any { service ->
            val info = service.resolveInfo?.serviceInfo
            info != null && info.packageName == expectedComponent.packageName && info.name == expectedComponent.className
        }
    }

    fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            val fallbackIntent = Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(fallbackIntent)
        }
    }

    fun openOverlaySettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (_: Exception) {}
        }
    }

    fun openAppDetailsSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    private fun checkPermissions(): AutomationPermissionState {
        val isServiceEnabled = isAccessibilityServiceEnabled()
        val isConnected = NexAccessibilityService.isServiceConnected()

        val accStatus = when {
            isConnected || isServiceEnabled -> AccessibilityStatus.ENABLED
            else -> AccessibilityStatus.DISABLED
        }

        val hasAudio = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val hasNotifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }

        return AutomationPermissionState(
            accessibilityStatus = accStatus,
            isAccessibilityConnected = isConnected,
            hasAudioPermission = hasAudio,
            hasNotificationPermission = hasNotifications,
            hasOverlayPermission = hasOverlay,
            canPerformGestures = isConnected
        )
    }
}
