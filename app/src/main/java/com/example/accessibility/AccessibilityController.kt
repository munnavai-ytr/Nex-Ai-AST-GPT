package com.example.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.example.accessibility.model.AccessibilityServiceState
import com.example.accessibility.model.ForegroundAppInfo
import com.example.accessibility.model.ScreenSnapshotResult
import com.example.accessibility.provider.CurrentScreenProvider
import com.example.accessibility.service.AccessibilityStatusManager
import com.example.permissions.PermissionManager

interface AccessibilityController {
    fun isAccessibilityActive(): Boolean
    fun getActivePackage(): String?
    fun performHome(): Boolean
    fun performBack(): Boolean
    fun performClickOnText(text: String): Boolean
    fun performClickById(viewId: String): Boolean
    fun performInputText(targetText: String?, text: String): Boolean
    fun performClearText(targetText: String? = null, viewId: String? = null): Boolean
    fun performScroll(forward: Boolean): Boolean
    fun performScrollForward(viewId: String? = null): Boolean
    fun performScrollBackward(viewId: String? = null): Boolean
    fun readScreenContent(): List<String>
    fun openAccessibilitySettings()

    // Screen understanding and gesture foundation for PUSH 3
    fun getServiceState(): AccessibilityServiceState
    fun getCurrentForegroundApp(): ForegroundAppInfo?
    fun captureScreenSnapshot(filterSensitive: Boolean = true): ScreenSnapshotResult
    suspend fun performTapCoordinates(x: Float, y: Float): Boolean
    suspend fun performSwipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long): Boolean
    suspend fun performLongPress(x: Float, y: Float, durationMs: Long): Boolean
}

class AndroidAccessibilityController(
    private val context: Context,
    private val permissionManager: PermissionManager,
    private val statusManager: AccessibilityStatusManager,
    private val screenProvider: CurrentScreenProvider
) : AccessibilityController {

    override fun isAccessibilityActive(): Boolean {
        return NexAccessibilityService.isServiceConnected()
    }

    override fun getActivePackage(): String? {
        return NexAccessibilityService.getInstance()?.currentActivePackage
            ?: statusManager.currentForegroundApp.value?.packageName
    }

    override fun getServiceState(): AccessibilityServiceState {
        return statusManager.serviceConnectionState.value
    }

    override fun getCurrentForegroundApp(): ForegroundAppInfo? {
        return statusManager.currentForegroundApp.value
    }

    override fun captureScreenSnapshot(filterSensitive: Boolean): ScreenSnapshotResult {
        return screenProvider.getCurrentScreenSnapshot(filterSensitive)
    }

    override fun performHome(): Boolean {
        val service = NexAccessibilityService.getInstance()
        return if (service != null) {
            service.performGlobalActionNav(AccessibilityService.GLOBAL_ACTION_HOME)
        } else {
            try {
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    override fun performBack(): Boolean {
        val service = NexAccessibilityService.getInstance() ?: return false
        return service.performGlobalActionNav(AccessibilityService.GLOBAL_ACTION_BACK)
    }

    override fun performClickOnText(text: String): Boolean {
        val service = NexAccessibilityService.getInstance() ?: return false
        return service.performClickByText(text)
    }

    override fun performClickById(viewId: String): Boolean {
        val service = NexAccessibilityService.getInstance() ?: return false
        return service.performClickByViewId(viewId)
    }

    override fun performInputText(targetText: String?, text: String): Boolean {
        val service = NexAccessibilityService.getInstance() ?: return false
        return service.performInputText(targetText, text)
    }

    override fun performClearText(targetText: String?, viewId: String?): Boolean {
        val service = NexAccessibilityService.getInstance() ?: return false
        return service.performClearText(targetText, viewId)
    }

    override fun performScroll(forward: Boolean): Boolean {
        val service = NexAccessibilityService.getInstance() ?: return false
        return service.performScroll(forward)
    }

    override fun performScrollForward(viewId: String?): Boolean {
        val service = NexAccessibilityService.getInstance() ?: return false
        return service.performScrollForward(viewId)
    }

    override fun performScrollBackward(viewId: String?): Boolean {
        val service = NexAccessibilityService.getInstance() ?: return false
        return service.performScrollBackward(viewId)
    }

    override fun readScreenContent(): List<String> {
        val service = NexAccessibilityService.getInstance() ?: return emptyList()
        return service.readAllVisibleText()
    }

    override fun openAccessibilitySettings() {
        statusManager.openAccessibilitySettings()
    }

    override suspend fun performTapCoordinates(x: Float, y: Float): Boolean {
        val service = NexAccessibilityService.getInstance() ?: return false
        val result = service.performTapCoordinates(x, y)
        return result.success
    }

    override suspend fun performSwipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long
    ): Boolean {
        val service = NexAccessibilityService.getInstance() ?: return false
        val result = service.performSwipeCoordinates(startX, startY, endX, endY, durationMs)
        return result.success
    }

    override suspend fun performLongPress(x: Float, y: Float, durationMs: Long): Boolean {
        val service = NexAccessibilityService.getInstance() ?: return false
        val result = service.performLongPressCoordinates(x, y, durationMs)
        return result.success
    }
}
