package com.example.accessibility.gemini

import com.example.accessibility.model.ScreenElement
import com.example.accessibility.model.ScreenSnapshot
import com.example.accessibility.privacy.ScreenPrivacyFilter

object ScreenContextBuilder {

    private const val MAX_CONTEXT_ELEMENTS = 45

    /**
     * Converts a ScreenSnapshot into a safe, compact representation suitable for Gemini.
     * Only includes interactive or labeled elements, redacting any sensitive data.
     */
    fun buildContext(snapshot: ScreenSnapshot): String {
        val sanitized = ScreenPrivacyFilter.filterSnapshot(snapshot)
        val appName = sanitized.displayName ?: sanitized.packageName

        val relevantElements = sanitized.elements
            .filter { elem ->
                elem.visible && (elem.isInteractive || !elem.text.isNullOrBlank() || !elem.contentDescription.isNullOrBlank())
            }
            .take(MAX_CONTEXT_ELEMENTS)

        val sb = StringBuilder()
        sb.appendLine("CURRENT APPLICATION:")
        sb.appendLine(appName)
        if (!sanitized.packageName.isBlank() && sanitized.packageName != appName) {
            sb.appendLine("package: ${sanitized.packageName}")
        }
        sb.appendLine()
        sb.appendLine("VISIBLE UI:")

        if (relevantElements.isEmpty()) {
            sb.appendLine("[No interactive UI elements detected]")
            return sb.toString().trimEnd()
        }

        relevantElements.forEachIndexed { index, elem ->
            sb.appendLine("[${index + 1}]")
            val label = elem.meaningfulLabel
            if (!label.isNullOrBlank()) {
                sb.appendLine("text: $label")
            }
            if (elem.clickable) {
                sb.appendLine("clickable: true")
            }
            if (elem.editable) {
                sb.appendLine("editable: true")
            }
            if (elem.scrollable) {
                sb.appendLine("scrollable: true")
            }
            val resId = elem.resourceId?.substringAfterLast(":id/")
            if (!resId.isNullOrBlank() && label.isNullOrBlank()) {
                sb.appendLine("id: $resId")
            }
            sb.appendLine()
        }

        return sb.toString().trimEnd()
    }
}
