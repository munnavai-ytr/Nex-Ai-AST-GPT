package com.example.automation

import java.util.UUID

enum class ActionType {
    OPEN_APP,
    BACK,
    HOME,
    TAP,
    LONG_PRESS,
    TYPE_TEXT,
    CLEAR_TEXT,
    SCROLL_FORWARD,
    SCROLL_BACKWARD,
    SCROLL, // For backwards compatibility
    SWIPE,
    WAIT,
    READ_SCREEN,
    VERIFY,
    ASK_CONFIRMATION,
    SEARCH // For backwards compatibility
}

enum class ActionStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    CANCELLED,
    TIMEOUT,
    REQUIRES_CONFIRMATION,
    NOT_FOUND,
    NOT_CLICKABLE,
    DISABLED,
    UNSUPPORTED,
    VALIDATION_FAILED,
    INVALID_ACTION,
    SECURITY_BLOCKED,
    APP_NOT_INSTALLED,
    APP_LAUNCH_FAILED
}

data class RetryPolicy(
    val maxAttempts: Int = 1,
    val delayBetweenAttemptsMs: Long = 500L,
    val retryable: Boolean = true
)

// Strongly typed parameter models (Section 2 of PUSH 4)
data class TapParameters(
    val targetText: String? = null,
    val viewId: String? = null,
    val xPercent: Float? = null,
    val yPercent: Float? = null,
    val xPixel: Float? = null,
    val yPixel: Float? = null
)

data class TextInputParameters(
    val targetText: String? = null,
    val inputText: String,
    val viewId: String? = null
)

data class ClearTextParameters(
    val targetText: String? = null,
    val viewId: String? = null
)

data class SwipeParameters(
    val startX: Float? = null,
    val startY: Float? = null,
    val endX: Float? = null,
    val endY: Float? = null,
    val direction: String? = null, // UP, DOWN, LEFT, RIGHT
    val durationMs: Long = 300L
)

data class ScrollParameters(
    val direction: String? = null, // UP, DOWN, FORWARD, BACKWARD
    val forward: Boolean = true,
    val viewId: String? = null
)

data class OpenAppParameters(
    val packageName: String? = null,
    val appName: String? = null
)

data class WaitParameters(
    val durationMs: Long = 1000L
)

data class VerifyParameters(
    val targetText: String? = null,
    val packageName: String? = null,
    val viewId: String? = null,
    val exists: Boolean = true,
    val isEditable: Boolean? = null,
    val isClickable: Boolean? = null
)

data class AskConfirmationParameters(
    val prompt: String,
    val sensitiveCategory: String? = null
)

data class ActionParameters(
    val packageName: String? = null,
    val appName: String? = null,
    val targetText: String? = null,
    val viewId: String? = null,
    val inputText: String? = null,
    val direction: String? = null, // UP, DOWN, LEFT, RIGHT, FORWARD, BACKWARD
    val durationMs: Long? = null,
    val query: String? = null,
    val confirmationPrompt: String? = null,
    val xPercent: Float? = null,
    val yPercent: Float? = null,
    val xPixel: Float? = null,
    val yPixel: Float? = null,
    val exists: Boolean? = null,
    val isEditable: Boolean? = null,
    val isClickable: Boolean? = null,
    val sensitiveCategory: String? = null
) {
    fun toTapParameters(): TapParameters =
        TapParameters(targetText, viewId, xPercent, yPercent, xPixel, yPixel)

    fun toTextInputParameters(): TextInputParameters =
        TextInputParameters(targetText, inputText ?: "", viewId)

    fun toClearTextParameters(): ClearTextParameters =
        ClearTextParameters(targetText, viewId)

    fun toSwipeParameters(): SwipeParameters =
        SwipeParameters(xPixel, yPixel, null, null, direction, durationMs ?: 300L)

    fun toScrollParameters(): ScrollParameters =
        ScrollParameters(direction, direction?.uppercase() != "UP" && direction?.uppercase() != "BACKWARD", viewId)

    fun toOpenAppParameters(): OpenAppParameters =
        OpenAppParameters(packageName, appName)

    fun toWaitParameters(): WaitParameters =
        WaitParameters(durationMs ?: 1000L)

    fun toVerifyParameters(): VerifyParameters =
        VerifyParameters(targetText, packageName, viewId, exists ?: true, isEditable, isClickable)

    fun toAskConfirmationParameters(): AskConfirmationParameters =
        AskConfirmationParameters(confirmationPrompt ?: "Confirmation required", sensitiveCategory)

    companion object {
        fun forOpenApp(packageName: String? = null, appName: String? = null) =
            ActionParameters(packageName = packageName, appName = appName)

        fun forTapText(text: String) =
            ActionParameters(targetText = text)

        fun forTapViewId(viewId: String) =
            ActionParameters(viewId = viewId)

        fun forTapCoordinates(xPercent: Float, yPercent: Float) =
            ActionParameters(xPercent = xPercent, yPercent = yPercent)

        fun forTextInput(text: String, targetText: String? = null, viewId: String? = null) =
            ActionParameters(inputText = text, targetText = targetText, viewId = viewId)

        fun forClearText(targetText: String? = null, viewId: String? = null) =
            ActionParameters(targetText = targetText, viewId = viewId)

        fun forScroll(direction: String = "DOWN") =
            ActionParameters(direction = direction)

        fun forWait(durationMs: Long) =
            ActionParameters(durationMs = durationMs)

        fun forVerify(targetText: String? = null, packageName: String? = null, viewId: String? = null) =
            ActionParameters(targetText = targetText, packageName = packageName, viewId = viewId)
    }
}

data class Action(
    val id: String = UUID.randomUUID().toString(),
    val type: ActionType,
    val parameters: ActionParameters = ActionParameters(),
    val description: String,
    val timeoutMs: Long = 8000L,
    val requiresConfirmation: Boolean = false,
    val retryPolicy: RetryPolicy = RetryPolicy(),
    val status: ActionStatus = ActionStatus.PENDING,
    val executionResult: ActionResult? = null
)

data class ActionResult(
    val actionId: String,
    val status: ActionStatus,
    val message: String,
    val durationMs: Long = 0L,
    val outputData: Map<String, String> = emptyMap(),
    val actionType: ActionType? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val targetDescription: String? = null
) {
    val isSuccess: Boolean
        get() = status == ActionStatus.SUCCESS
}

data class ValidationResult(
    val isValid: Boolean,
    val reason: String? = null,
    val requiresConfirmation: Boolean = false,
    val missingCapability: String? = null,
    val errorCode: String? = if (!isValid) "INVALID_ACTION" else null
)
