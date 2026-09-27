package com.example.accessibility.tree

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.example.accessibility.model.ScreenBounds
import com.example.accessibility.model.ScreenElement

class AccessibilityTreeReader(
    private val maxDepth: Int = 30,
    private val maxElements: Int = 200
) {

    fun readTree(rootNode: AccessibilityNodeInfo?): List<ScreenElement> {
        if (rootNode == null) return emptyList()

        val elements = mutableListOf<ScreenElement>()
        var nextId = 1
        val boundsRect = Rect()

        fun traverse(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > maxDepth || elements.size >= maxElements) return

            node.getBoundsInScreen(boundsRect)
            val bounds = ScreenBounds(
                left = boundsRect.left,
                top = boundsRect.top,
                right = boundsRect.right,
                bottom = boundsRect.bottom
            )

            val text = node.text?.toString()?.trim()
            val desc = node.contentDescription?.toString()?.trim()
            val viewId = node.viewIdResourceName
            val isClickable = node.isClickable
            val isEditable = node.isEditable
            val isScrollable = node.isScrollable
            val isFocusable = node.isFocusable
            val isEnabled = node.isEnabled
            val isVisible = node.isVisibleToUser
            val isPassword = node.isPassword
            val className = node.className?.toString() ?: ""
            val packageName = node.packageName?.toString()

            // Include if interactive, visible with text/desc/viewId, or a structural container
            val hasContent = !text.isNullOrBlank() || !desc.isNullOrBlank() || !viewId.isNullOrBlank()
            val isInteractive = isClickable || isEditable || isScrollable

            val element = ScreenElement(
                id = nextId++,
                className = className,
                packageName = packageName,
                text = text,
                contentDescription = desc,
                resourceId = viewId,
                clickable = isClickable,
                focusable = isFocusable,
                scrollable = isScrollable,
                editable = isEditable,
                enabled = isEnabled,
                visible = isVisible,
                isPassword = isPassword,
                bounds = bounds,
                depth = depth,
                childCount = node.childCount
            )

            // Avoid adding completely blank, non-interactive, zero-area nodes
            if (hasContent || isInteractive || node.childCount > 0) {
                elements.add(element)
            }

            // Traverse children
            val count = node.childCount
            for (i in 0 until count) {
                val child = node.getChild(i) ?: continue
                traverse(child, depth + 1)
            }
        }

        try {
            traverse(rootNode, 0)
        } catch (_: Exception) {
            // Protect against concurrent window state changes
        }

        return elements
    }
}
