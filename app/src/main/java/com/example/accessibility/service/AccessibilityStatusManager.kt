package com.example.accessibility.service

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import com.example.accessibility.NexAccessibilityService
import com.example.accessibility.model.AccessibilityServiceState
import com.example.accessibility.model.ForegroundAppInfo
import com.example.automation.ActionResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AccessibilityStatusManager(private val context: Context) {

    private val _isAccessibilityEnabled = MutableStateFlow(false)
    val isAccessibilityEnabled: StateFlow<Boolean> = _isAccessibilityEnabled.asStateFlow()

    private val _serviceConnectionState = MutableStateFlow(AccessibilityServiceState.DISABLED)
    val serviceConnectionState: StateFlow<AccessibilityServiceState> = _serviceConnectionState.asStateFlow()

    private val _currentForegroundApp = MutableStateFlow<ForegroundAppInfo?>(null)
    val currentForegroundApp: StateFlow<ForegroundAppInfo?> = _currentForegroundApp.asStateFlow()

    private val _lastScreenUpdateTime = MutableStateFlow(0L)
    val lastScreenUpdateTime: StateFlow<Long> = _lastScreenUpdateTime.asStateFlow()

    private val _lastObservedElementCount = MutableStateFlow(0)
    val lastObservedElementCount: StateFlow<Int> = _lastObservedElementCount.asStateFlow()

    private val _lastActionResult = MutableStateFlow<ActionResult?>(null)
    val lastActionResult: StateFlow<ActionResult?> = _lastActionResult.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    init {
        refreshState()
    }

    fun refreshState(): Boolean {
        val enabledInSettings = isServiceEnabledInSettings()
        val connected = NexAccessibilityService.isServiceConnected()

        _isAccessibilityEnabled.value = enabledInSettings

        val state = when {
            !enabledInSettings -> AccessibilityServiceState.DISABLED
            !connected -> AccessibilityServiceState.DISCONNECTED
            else -> AccessibilityServiceState.CONNECTED
        }
        _serviceConnectionState.value = state

        if (connected) {
            val service = NexAccessibilityService.getInstance()
            val pkg = service?.currentActivePackage
            if (!pkg.isNullOrBlank()) {
                val appLabel = resolveAppLabel(pkg)
                _currentForegroundApp.value = ForegroundAppInfo(
                    packageName = pkg,
                    displayName = appLabel
                )
            }
        }

        return enabledInSettings
    }

    fun onServiceConnected() {
        refreshState()
    }

    fun onServiceDisconnected() {
        _serviceConnectionState.value = if (isServiceEnabledInSettings()) {
            AccessibilityServiceState.DISCONNECTED
        } else {
            AccessibilityServiceState.DISABLED
        }
    }

    fun onWindowOrContentChanged(packageName: String?) {
        val now = System.currentTimeMillis()
        _lastScreenUpdateTime.value = now

        if (!packageName.isNullOrBlank()) {
            val label = resolveAppLabel(packageName)
            _currentForegroundApp.value = ForegroundAppInfo(
                packageName = packageName,
                displayName = label,
                timestamp = now
            )
        }
    }

    fun updateObservedElementCount(count: Int) {
        _lastObservedElementCount.value = count
    }

    fun recordActionResult(result: ActionResult) {
        _lastActionResult.value = result
        if (result.status != com.example.automation.ActionStatus.SUCCESS) {
            _lastError.value = result.errorMessage ?: result.message
        }
    }

    fun recordError(error: String) {
        _lastError.value = error
    }

    fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            // Fallback to general settings if accessibility settings cannot be resolved
            val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(fallback)
        }
    }

    private fun isServiceEnabledInSettings(): Boolean {
        val expectedCanonical = "${context.packageName}/${NexAccessibilityService::class.java.name}"
        val expectedSimple = "${context.packageName}/.accessibility.NexAccessibilityService"

        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)

        while (splitter.hasNext()) {
            val component = splitter.next()
            if (component.equals(expectedCanonical, ignoreCase = true) ||
                component.equals(expectedSimple, ignoreCase = true) ||
                (component.contains(context.packageName) && component.contains("NexAccessibilityService"))
            ) {
                return true
            }
        }
        return false
    }

    fun resolveAppLabel(packageName: String): String {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            packageName
        }
    }
}
