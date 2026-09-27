package com.example.accessibility.inspector

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.example.accessibility.NexAccessibilityService

/**
 * Structured, immutable snapshot of an accessible UI element.
 */
data class AccessibleElement(
    val id: Int,
    val text: String?,
    val contentDescription: String?,
    val resourceId: String?,
    val className: String,
    val packageName: String?,
    val bounds: Rect,
    val isClickable: Boolean,
    val isScrollable: Boolean,
    val isEditable: Boolean,
    val isFocusable: Boolean,
    val isFocused: Boolean,
    val isEnabled: Boolean,
    val isPassword: Boolean,
    val isVisibleToUser: Boolean,
    val depth: Int,
    val childCount: Int,
    val indexInParent: Int = 0
) {
    val displayLabel: String?
        get() = when {
            !text.isNullOrBlank() -> text
            !contentDescription.isNullOrBlank() -> contentDescription
            !resourceId.isNullOrBlank() -> resourceId.substringAfterLast(":id/")
            else -> null
        }

    val isInteractive: Boolean
        get() = isClickable || isEditable || isScrollable || isFocusable
}

/**
 * High-level structured representation of the complete accessible screen hierarchy.
 */
data class UIHierarchySnapshot(
    val packageName: String?,
    val windowTitle: String?,
    val timestamp: Long = System.currentTimeMillis(),
    val totalElements: Int,
    val interactiveCount: Int,
    val editableCount: Int,
    val scrollableCount: Int,
    val elements: List<AccessibleElement>,
    val isSecureWindow: Boolean = false
) {
    fun findByText(query: String, exactMatch: Boolean = false): List<AccessibleElement> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()
        return elements.filter { element ->
            val label = element.text ?: element.contentDescription ?: ""
            if (exactMatch) {
                label.equals(q, ignoreCase = true)
            } else {
                label.contains(q, ignoreCase = true)
            }
        }
    }

    fun findByResourceId(resourceIdQuery: String): List<AccessibleElement> {
        val q = resourceIdQuery.trim()
        if (q.isBlank()) return emptyList()
        return elements.filter { element ->
            element.resourceId?.contains(q, ignoreCase = true) == true
        }
    }

    fun findByContentDescription(descQuery: String): List<AccessibleElement> {
        val q = descQuery.trim()
        if (q.isBlank()) return emptyList()
        return elements.filter { element ->
            element.contentDescription?.contains(q, ignoreCase = true) == true
        }
    }

    fun findFirstEditable(): AccessibleElement? {
        return elements.firstOrNull { it.isEditable && it.isEnabled }
    }

    fun findFirstScrollable(): AccessibleElement? {
        return elements.firstOrNull { it.isScrollable && it.isEnabled }
    }

    fun findFirstClickableResult(): AccessibleElement? {
        // Look for items that look like list items or search results (clickable with visible text)
        return elements.firstOrNull { it.isClickable && it.isEnabled && !it.text.isNullOrBlank() && it.depth > 1 }
            ?: elements.firstOrNull { it.isClickable && it.isEnabled && !it.contentDescription.isNullOrBlank() && it.depth > 1 }
    }

    /**
     * Compact JSON-like string representation for Gemini AI reasoning.
     */
    fun toGeminiContextString(maxItems: Int = 40): String {
        val meaningful = elements.filter { it.isInteractive || !it.displayLabel.isNullOrBlank() }
            .take(maxItems)

        if (meaningful.isEmpty()) {
            return "Screen: [Package: ${packageName ?: "Unknown"}, Elements: 0 visible interactive items]"
        }

        val builder = StringBuilder()
        builder.append("Active Package: ${packageName ?: "Unknown"}\n")
        builder.append("Visible Interactive Hierarchy:\n")
        for (item in meaningful) {
            val label = item.displayLabel ?: "unlabeled"
            val type = item.className.substringAfterLast(".")
            val flags = mutableListOf<String>()
            if (item.isClickable) flags.add("clickable")
            if (item.isEditable) flags.add("editable")
            if (item.isScrollable) flags.add("scrollable")
            if (item.isFocused) flags.add("focused")

            val idStr = item.resourceId?.substringAfterLast(":id/")?.let { " id=\"$it\"" } ?: ""
            builder.append("- [$type]$idStr \"$label\" (${flags.joinToString(",")}) [bounds: ${item.bounds.left},${item.bounds.top},${item.bounds.right},${item.bounds.bottom}]\n")
        }
        return builder.toString()
    }
}

/**
 * Real Android UI Hierarchy Inspector.
 * Uses AccessibilityNodeInfo to safely traverse, inspect, and extract UI structure.
 */
class UIHierarchyInspector(
    private val maxDepth: Int = 30,
    private val maxElements: Int = 300
) {

    fun inspectCurrentScreen(): UIHierarchySnapshot {
        val service = NexAccessibilityService.getInstance()
        if (service == null) {
            return UIHierarchySnapshot(
                packageName = null,
                windowTitle = null,
                totalElements = 0,
                interactiveCount = 0,
                editableCount = 0,
                scrollableCount = 0,
                elements = emptyList()
            )
        }

        val rootNode = try {
            service.rootInActiveWindow
        } catch (_: Exception) {
            null
        }

        if (rootNode == null) {
            return UIHierarchySnapshot(
                packageName = service.currentActivePackage,
                windowTitle = null,
                totalElements = 0,
                interactiveCount = 0,
                editableCount = 0,
                scrollableCount = 0,
                elements = emptyList()
            )
        }

        val elements = mutableListOf<AccessibleElement>()
        var nextId = 1
        var interactiveCount = 0
        var editableCount = 0
        var scrollableCount = 0
        val boundsRect = Rect()

        fun traverse(node: AccessibilityNodeInfo, depth: Int, indexInParent: Int) {
            if (depth > maxDepth || elements.size >= maxElements) return

            node.getBoundsInScreen(boundsRect)
            val rectCopy = Rect(boundsRect)

            val text = if (!node.isPassword) node.text?.toString()?.trim() else "[SECURE_PASSWORD]"
            val desc = if (!node.isPassword) node.contentDescription?.toString()?.trim() else null
            val resId = node.viewIdResourceName
            val isClickable = node.isClickable
            val isScrollable = node.isScrollable
            val isEditable = node.isEditable
            val isFocusable = node.isFocusable
            val isFocused = node.isFocused
            val isEnabled = node.isEnabled
            val isPassword = node.isPassword
            val isVisible = node.isVisibleToUser
            val className = node.className?.toString() ?: "android.view.View"
            val packageName = node.packageName?.toString()

            val hasContent = !text.isNullOrBlank() || !desc.isNullOrBlank() || !resId.isNullOrBlank()
            val isInteractive = isClickable || isEditable || isScrollable || isFocusable

            if (isInteractive) interactiveCount++
            if (isEditable) editableCount++
            if (isScrollable) scrollableCount++

            val element = AccessibleElement(
                id = nextId++,
                text = text,
                contentDescription = desc,
                resourceId = resId,
                className = className,
                packageName = packageName,
                bounds = rectCopy,
                isClickable = isClickable,
                isScrollable = isScrollable,
                isEditable = isEditable,
                isFocusable = isFocusable,
                isFocused = isFocused,
                isEnabled = isEnabled,
                isPassword = isPassword,
                isVisibleToUser = isVisible,
                depth = depth,
                childCount = node.childCount,
                indexInParent = indexInParent
            )

            if (hasContent || isInteractive || node.childCount > 0) {
                elements.add(element)
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                try {
                    traverse(child, depth + 1, i)
                } finally {
                    try {
                        child.recycle()
                    } catch (_: Exception) {}
                }
            }
        }

        try {
            traverse(rootNode, 0, 0)
        } catch (_: Exception) {
            // Protect against concurrent window state updates
        } finally {
            try {
                rootNode.recycle()
            } catch (_: Exception) {}
        }

        return UIHierarchySnapshot(
            packageName = service.currentActivePackage ?: elements.firstOrNull()?.packageName,
            windowTitle = null,
            totalElements = elements.size,
            interactiveCount = interactiveCount,
            editableCount = editableCount,
            scrollableCount = scrollableCount,
            elements = elements
        )
    }
}
