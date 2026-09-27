package com.example.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.accessibility.gesture.GestureExecutor
import com.example.accessibility.gesture.GestureResult
import com.example.accessibility.service.AccessibilityStatusManager

class NexAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        private var instance: NexAccessibilityService? = null

        @Volatile
        var statusManager: AccessibilityStatusManager? = null

        @Volatile
        var screenStateManager: com.example.automation.state.ScreenStateManager? = null

        fun isServiceConnected(): Boolean = instance != null

        fun getInstance(): NexAccessibilityService? = instance
    }

    var currentActivePackage: String? = null
        private set

    var lastEventType: Int = 0
        private set

    var lastEventTimestamp: Long = 0L
        private set

    val gestureExecutor = GestureExecutor()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        statusManager?.onServiceConnected()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        lastEventType = event.eventType
        lastEventTimestamp = System.currentTimeMillis()
        screenStateManager?.updateFromAccessibilityEvent(event.eventType, event.packageName?.toString())

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val pkg = event.packageName?.toString()
                if (!pkg.isNullOrBlank()) {
                    currentActivePackage = pkg
                    statusManager?.onWindowOrContentChanged(pkg)
                }
            }
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                val pkg = event.packageName?.toString() ?: currentActivePackage
                statusManager?.onWindowOrContentChanged(pkg)
            }
        }
    }

    override fun onInterrupt() {
        // System paused or interrupted accessibility feedback
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
        statusManager?.onServiceDisconnected()
    }

    fun findNodesByText(text: String): List<AccessibilityNodeInfo> {
        val root = rootInActiveWindow ?: return emptyList()
        return root.findAccessibilityNodeInfosByText(text) ?: emptyList()
    }

    fun findNodesByViewId(viewId: String): List<AccessibilityNodeInfo> {
        val root = rootInActiveWindow ?: return emptyList()
        return root.findAccessibilityNodeInfosByViewId(viewId) ?: emptyList()
    }

    fun findFirstClickableNode(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        var found: AccessibilityNodeInfo? = null
        traverseNodes(root) { node ->
            if (found == null && node.isClickable && node.isEnabled) {
                found = node
            }
        }
        return found
    }

    fun readAllVisibleText(): List<String> {
        val root = rootInActiveWindow ?: return emptyList()
        val texts = mutableListOf<String>()
        traverseNodes(root) { node ->
            if (!node.isPassword) {
                val text = node.text?.toString()?.trim()
                if (!text.isNullOrBlank()) {
                    texts.add(text)
                }
                val desc = node.contentDescription?.toString()?.trim()
                if (!desc.isNullOrBlank() && desc != text) {
                    texts.add(desc)
                }
            }
        }
        return texts
    }

    fun performClickByText(text: String): Boolean {
        val matchingNodes = findNodesByText(text)
        for (node in matchingNodes) {
            if (!node.isEnabled) continue

            if (node.isClickable) {
                return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }

            // Climb up ancestors if parent container is the clickable element
            var parent = node.parent
            var depth = 0
            while (parent != null && depth < 5) {
                if (parent.isClickable && parent.isEnabled) {
                    return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
                parent = parent.parent
                depth++
            }
        }
        return false
    }

    fun performClickByViewId(viewId: String): Boolean {
        val matchingNodes = findNodesByViewId(viewId)
        for (node in matchingNodes) {
            if (!node.isEnabled) continue
            if (node.isClickable) {
                return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            var parent = node.parent
            var depth = 0
            while (parent != null && depth < 5) {
                if (parent.isClickable && parent.isEnabled) {
                    return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
                parent = parent.parent
                depth++
            }
        }
        return false
    }

    fun performInputText(targetText: String?, inputText: String): Boolean {
        val root = rootInActiveWindow ?: return false

        val targetNode: AccessibilityNodeInfo? = if (!targetText.isNullOrBlank()) {
            findNodesByText(targetText).firstOrNull { it.isEditable && it.isEnabled }
                ?: findNodesByText(targetText).firstOrNull { it.isFocusable && it.isEnabled }
        } else {
            // Find first editable node in active window
            var foundEditable: AccessibilityNodeInfo? = null
            traverseNodes(root) { node ->
                if (foundEditable == null && node.isEditable && node.isEnabled) {
                    foundEditable = node
                }
            }
            foundEditable
        }

        if (targetNode != null) {
            targetNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, inputText)
            }
            return targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }
        return false
    }

    fun performScroll(forward: Boolean): Boolean {
        val root = rootInActiveWindow ?: return false
        var scrollableNode: AccessibilityNodeInfo? = null
        traverseNodes(root) { node ->
            if (scrollableNode == null && node.isScrollable && node.isEnabled) {
                scrollableNode = node
            }
        }

        return scrollableNode?.let {
            val action = if (forward) {
                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            } else {
                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            }
            it.performAction(action)
        } ?: false
    }

    fun performClearText(targetText: String? = null, viewId: String? = null): Boolean {
        val root = rootInActiveWindow ?: return false

        val targetNode: AccessibilityNodeInfo? = when {
            !viewId.isNullOrBlank() -> {
                findNodesByViewId(viewId).firstOrNull { it.isEditable && it.isEnabled }
            }
            !targetText.isNullOrBlank() -> {
                findNodesByText(targetText).firstOrNull { it.isEditable && it.isEnabled }
            }
            else -> {
                var found: AccessibilityNodeInfo? = null
                traverseNodes(root) { node ->
                    if (found == null && node.isEditable && node.isEnabled) {
                        found = node
                    }
                }
                found
            }
        }

        if (targetNode != null) {
            targetNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
            }
            return targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }
        return false
    }

    fun performScrollForward(viewId: String? = null): Boolean {
        val root = rootInActiveWindow ?: return false
        val targetNode: AccessibilityNodeInfo? = if (!viewId.isNullOrBlank()) {
            findNodesByViewId(viewId).firstOrNull { it.isScrollable && it.isEnabled }
        } else {
            var found: AccessibilityNodeInfo? = null
            traverseNodes(root) { node ->
                if (found == null && node.isScrollable && node.isEnabled) {
                    found = node
                }
            }
            found
        }
        return targetNode?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) ?: false
    }

    fun performScrollBackward(viewId: String? = null): Boolean {
        val root = rootInActiveWindow ?: return false
        val targetNode: AccessibilityNodeInfo? = if (!viewId.isNullOrBlank()) {
            findNodesByViewId(viewId).firstOrNull { it.isScrollable && it.isEnabled }
        } else {
            var found: AccessibilityNodeInfo? = null
            traverseNodes(root) { node ->
                if (found == null && node.isScrollable && node.isEnabled) {
                    found = node
                }
            }
            found
        }
        return targetNode?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) ?: false
    }

    fun performGlobalActionNav(action: Int): Boolean {
        return performGlobalAction(action)
    }

    suspend fun performTapCoordinates(x: Float, y: Float): GestureResult {
        return gestureExecutor.executeTap(this, x, y)
    }

    suspend fun performSwipeCoordinates(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long
    ): GestureResult {
        return gestureExecutor.executeSwipe(this, startX, startY, endX, endY, durationMs)
    }

    suspend fun performLongPressCoordinates(x: Float, y: Float, durationMs: Long): GestureResult {
        return gestureExecutor.executeLongPress(this, x, y, durationMs)
    }

    private fun traverseNodes(node: AccessibilityNodeInfo, onVisit: (AccessibilityNodeInfo) -> Unit) {
        onVisit(node)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            traverseNodes(child, onVisit)
        }
    }
}
