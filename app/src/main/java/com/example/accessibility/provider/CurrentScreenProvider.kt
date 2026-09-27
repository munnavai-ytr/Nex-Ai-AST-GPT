package com.example.accessibility.provider

import android.content.Context
import com.example.accessibility.NexAccessibilityService
import com.example.accessibility.model.ScreenSnapshot
import com.example.accessibility.model.ScreenSnapshotResult
import com.example.accessibility.privacy.ScreenPrivacyFilter
import com.example.accessibility.service.AccessibilityStatusManager
import com.example.accessibility.tree.AccessibilityTreeReader

class CurrentScreenProvider(
    private val context: Context,
    private val statusManager: AccessibilityStatusManager,
    private val treeReader: AccessibilityTreeReader = AccessibilityTreeReader()
) {

    fun getCurrentScreenSnapshot(filterSensitive: Boolean = true): ScreenSnapshotResult {
        if (!NexAccessibilityService.isServiceConnected()) {
            return ScreenSnapshotResult.Error(
                errorCode = "ACCESSIBILITY_NOT_CONNECTED",
                message = "NEX Accessibility Service is not active. Please enable it in system settings."
            )
        }

        val service = NexAccessibilityService.getInstance()
            ?: return ScreenSnapshotResult.Error(
                errorCode = "SERVICE_INSTANCE_NULL",
                message = "Accessibility Service instance is unavailable."
            )

        val rootNode = service.rootInActiveWindow
            ?: return ScreenSnapshotResult.Error(
                errorCode = "ROOT_NODE_UNAVAILABLE",
                message = "Active window root node cannot be retrieved. Screen may be off or locked."
            )

        return try {
            val elements = treeReader.readTree(rootNode)
            val pkg = rootNode.packageName?.toString() ?: service.currentActivePackage ?: "unknown"
            val displayName = statusManager.resolveAppLabel(pkg)
            val displayMetrics = context.resources.displayMetrics

            val rawSnapshot = ScreenSnapshot(
                packageName = pkg,
                displayName = displayName,
                timestamp = System.currentTimeMillis(),
                screenWidth = displayMetrics.widthPixels,
                screenHeight = displayMetrics.heightPixels,
                elements = elements,
                isRedacted = false
            )

            val finalSnapshot = if (filterSensitive) {
                ScreenPrivacyFilter.filterSnapshot(rawSnapshot)
            } else {
                rawSnapshot
            }

            statusManager.updateObservedElementCount(finalSnapshot.elementCount)
            ScreenSnapshotResult.Success(finalSnapshot)
        } catch (e: Exception) {
            ScreenSnapshotResult.Error(
                errorCode = "TREE_READ_ERROR",
                message = "Failed to parse accessibility tree: ${e.message}"
            )
        }
    }
}
