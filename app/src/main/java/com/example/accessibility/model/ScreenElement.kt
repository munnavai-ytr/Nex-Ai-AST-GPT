package com.example.accessibility.model

data class ScreenElement(
    val id: Int,
    val className: String,
    val packageName: String? = null,
    val text: String? = null,
    val contentDescription: String? = null,
    val resourceId: String? = null,
    val clickable: Boolean = false,
    val focusable: Boolean = false,
    val scrollable: Boolean = false,
    val editable: Boolean = false,
    val enabled: Boolean = true,
    val visible: Boolean = true,
    val isPassword: Boolean = false,
    val bounds: ScreenBounds = ScreenBounds(),
    val depth: Int = 0,
    val childCount: Int = 0
) {
    val meaningfulLabel: String?
        get() = when {
            isPassword -> "[REDACTED_SENSITIVE_FIELD]"
            !text.isNullOrBlank() -> text
            !contentDescription.isNullOrBlank() -> contentDescription
            !resourceId.isNullOrBlank() -> resourceId.substringAfterLast(":id/")
            else -> null
        }

    val isInteractive: Boolean
        get() = clickable || editable || scrollable
}
