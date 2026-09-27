package com.example.automation

import com.example.accessibility.AccessibilityController
import com.example.automation.confirmation.ConfirmationManager
import com.example.permissions.PermissionManager

class ActionValidator(
    private val accessibilityController: AccessibilityController,
    private val permissionManager: PermissionManager,
    private val confirmationManager: ConfirmationManager = ConfirmationManager()
) {

    companion object {
        const val MAX_INPUT_TEXT_LENGTH = 1000
        const val MIN_WAIT_MS = 50L
        const val MAX_WAIT_MS = 30000L
        const val MAX_SWIPE_DURATION_MS = 5000L
    }

    fun validate(action: Action): ValidationResult {
        // 1. Sensitive Action check
        val isSensitive = confirmationManager.isActionSensitive(action) || action.requiresConfirmation

        // 2. Schema and parameter verification
        when (action.type) {
            ActionType.OPEN_APP -> {
                val pkg = action.parameters.packageName
                val app = action.parameters.appName
                if (pkg.isNullOrBlank() && app.isNullOrBlank()) {
                    return ValidationResult(
                        isValid = false,
                        reason = "OPEN_APP requires either packageName or appName parameter",
                        errorCode = "INVALID_ACTION"
                    )
                }
                if (pkg != null && pkg.length > 200) {
                    return ValidationResult(
                        isValid = false,
                        reason = "Package name exceeds maximum length of 200 characters",
                        errorCode = "INVALID_ACTION"
                    )
                }
            }

            ActionType.TYPE_TEXT -> {
                val inputText = action.parameters.inputText
                if (inputText == null) {
                    return ValidationResult(
                        isValid = false,
                        reason = "TYPE_TEXT requires inputText parameter",
                        errorCode = "INVALID_ACTION"
                    )
                }
                if (inputText.length > MAX_INPUT_TEXT_LENGTH) {
                    return ValidationResult(
                        isValid = false,
                        reason = "Input text exceeds limit of $MAX_INPUT_TEXT_LENGTH characters (found: ${inputText.length})",
                        errorCode = "INVALID_ACTION"
                    )
                }
                if (!accessibilityController.isAccessibilityActive()) {
                    return ValidationResult(
                        isValid = false,
                        reason = "TYPE_TEXT requires an active Accessibility Service to input text into target views",
                        missingCapability = "Accessibility Service",
                        errorCode = "INVALID_ACTION"
                    )
                }
            }

            ActionType.CLEAR_TEXT -> {
                if (!accessibilityController.isAccessibilityActive()) {
                    return ValidationResult(
                        isValid = false,
                        reason = "CLEAR_TEXT requires an active Accessibility Service to clear editable views",
                        missingCapability = "Accessibility Service",
                        errorCode = "INVALID_ACTION"
                    )
                }
            }

            ActionType.TAP, ActionType.LONG_PRESS -> {
                val targetText = action.parameters.targetText
                val viewId = action.parameters.viewId
                val xPercent = action.parameters.xPercent
                val yPercent = action.parameters.yPercent
                val xPixel = action.parameters.xPixel
                val yPixel = action.parameters.yPixel

                val hasTarget = !targetText.isNullOrBlank() ||
                        !viewId.isNullOrBlank() ||
                        (xPercent != null && yPercent != null) ||
                        (xPixel != null && yPixel != null)

                if (!hasTarget) {
                    return ValidationResult(
                        isValid = false,
                        reason = "${action.type} requires targetText, viewId, percentage coordinates, or pixel coordinates",
                        errorCode = "INVALID_ACTION"
                    )
                }

                if (xPercent != null && (xPercent < 0.0f || xPercent > 1.0f)) {
                    return ValidationResult(
                        isValid = false,
                        reason = "xPercent must be within [0.0, 1.0], found: $xPercent",
                        errorCode = "INVALID_ACTION"
                    )
                }
                if (yPercent != null && (yPercent < 0.0f || yPercent > 1.0f)) {
                    return ValidationResult(
                        isValid = false,
                        reason = "yPercent must be within [0.0, 1.0], found: $yPercent",
                        errorCode = "INVALID_ACTION"
                    )
                }
                if (xPixel != null && xPixel < 0f) {
                    return ValidationResult(
                        isValid = false,
                        reason = "xPixel coordinate cannot be negative, found: $xPixel",
                        errorCode = "INVALID_ACTION"
                    )
                }
                if (yPixel != null && yPixel < 0f) {
                    return ValidationResult(
                        isValid = false,
                        reason = "yPixel coordinate cannot be negative, found: $yPixel",
                        errorCode = "INVALID_ACTION"
                    )
                }

                if (!accessibilityController.isAccessibilityActive()) {
                    return ValidationResult(
                        isValid = false,
                        reason = "Screen interactions (${action.type}) require NEX Accessibility Service to be enabled",
                        missingCapability = "Accessibility Service",
                        errorCode = "INVALID_ACTION"
                    )
                }
            }

            ActionType.SCROLL_FORWARD, ActionType.SCROLL_BACKWARD, ActionType.SCROLL -> {
                val direction = action.parameters.direction?.uppercase()
                if (direction != null && direction !in listOf("UP", "DOWN", "LEFT", "RIGHT", "FORWARD", "BACKWARD")) {
                    return ValidationResult(
                        isValid = false,
                        reason = "Invalid scroll direction: $direction",
                        errorCode = "INVALID_ACTION"
                    )
                }
                if (!accessibilityController.isAccessibilityActive()) {
                    return ValidationResult(
                        isValid = false,
                        reason = "Scrolling requires NEX Accessibility Service to be enabled",
                        missingCapability = "Accessibility Service",
                        errorCode = "INVALID_ACTION"
                    )
                }
            }

            ActionType.SWIPE -> {
                val duration = action.parameters.durationMs ?: 300L
                if (duration <= 0 || duration > MAX_SWIPE_DURATION_MS) {
                    return ValidationResult(
                        isValid = false,
                        reason = "Swipe duration must be between 1ms and ${MAX_SWIPE_DURATION_MS}ms (found: $duration)",
                        errorCode = "INVALID_ACTION"
                    )
                }
                val direction = action.parameters.direction?.uppercase()
                if (direction != null && direction !in listOf("UP", "DOWN", "LEFT", "RIGHT")) {
                    return ValidationResult(
                        isValid = false,
                        reason = "Invalid swipe direction: $direction. Must be UP, DOWN, LEFT, or RIGHT",
                        errorCode = "INVALID_ACTION"
                    )
                }
                if (!accessibilityController.isAccessibilityActive()) {
                    return ValidationResult(
                        isValid = false,
                        reason = "Swipe gestures require NEX Accessibility Service to be enabled",
                        missingCapability = "Accessibility Service",
                        errorCode = "INVALID_ACTION"
                    )
                }
            }

            ActionType.READ_SCREEN -> {
                if (!accessibilityController.isAccessibilityActive()) {
                    return ValidationResult(
                        isValid = false,
                        reason = "READ_SCREEN requires NEX Accessibility Service to inspect the on-screen hierarchy",
                        missingCapability = "Accessibility Service",
                        errorCode = "INVALID_ACTION"
                    )
                }
            }

            ActionType.WAIT -> {
                val duration = action.parameters.durationMs ?: 1000L
                if (duration < MIN_WAIT_MS || duration > MAX_WAIT_MS) {
                    return ValidationResult(
                        isValid = false,
                        reason = "WAIT duration must be between ${MIN_WAIT_MS}ms and ${MAX_WAIT_MS}ms (found: $duration)",
                        errorCode = "INVALID_ACTION"
                    )
                }
            }

            ActionType.VERIFY -> {
                val hasTarget = !action.parameters.targetText.isNullOrBlank() ||
                        !action.parameters.packageName.isNullOrBlank() ||
                        !action.parameters.viewId.isNullOrBlank()
                if (!hasTarget) {
                    return ValidationResult(
                        isValid = false,
                        reason = "VERIFY requires targetText, packageName, or viewId to evaluate",
                        errorCode = "INVALID_ACTION"
                    )
                }
            }

            ActionType.ASK_CONFIRMATION -> {
                if (action.parameters.confirmationPrompt.isNullOrBlank()) {
                    return ValidationResult(
                        isValid = false,
                        reason = "ASK_CONFIRMATION requires a confirmationPrompt",
                        errorCode = "INVALID_ACTION"
                    )
                }
                return ValidationResult(isValid = true, requiresConfirmation = true)
            }

            ActionType.SEARCH -> {
                if (action.parameters.query.isNullOrBlank()) {
                    return ValidationResult(
                        isValid = false,
                        reason = "SEARCH requires query parameter",
                        errorCode = "INVALID_ACTION"
                    )
                }
            }

            ActionType.BACK, ActionType.HOME -> {
                // Allowed system actions
            }
        }

        return ValidationResult(
            isValid = true,
            requiresConfirmation = isSensitive
        )
    }

    fun validatePlan(actions: List<Action>): List<Pair<Action, ValidationResult>> {
        return actions.map { action ->
            action to validate(action)
        }
    }
}
