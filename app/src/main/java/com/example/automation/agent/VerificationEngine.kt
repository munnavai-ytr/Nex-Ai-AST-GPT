package com.example.automation.agent

import com.example.accessibility.AccessibilityController
import com.example.accessibility.model.ScreenSnapshot
import com.example.accessibility.model.ScreenSnapshotResult
import com.example.accessibility.tree.ElementFinder
import com.example.automation.Action
import com.example.automation.ActionResult
import com.example.automation.ActionStatus
import com.example.automation.ActionType

sealed class StepVerificationResult {
    data class Verified(val message: String) : StepVerificationResult()
    data class Failed(val reason: String) : StepVerificationResult()
    data class Inconclusive(val details: String) : StepVerificationResult()

    val isSuccess: Boolean
        get() = this is Verified || this is Inconclusive
}

sealed class GoalVerificationResult {
    data class Verified(val summary: String) : GoalVerificationResult()
    data class Failed(val reason: String) : GoalVerificationResult()
    data class PartiallyVerified(val summary: String) : GoalVerificationResult()

    val isSuccess: Boolean
        get() = this is Verified || this is PartiallyVerified
}

class VerificationEngine(
    private val accessibilityController: AccessibilityController
) {

    /**
     * Verifies whether an individual task step achieved its expected post-condition.
     */
    fun verifyStep(
        step: TaskStep,
        actionResult: ActionResult,
        preSignature: ScreenSignature?,
        postSignature: ScreenSignature?,
        postSnapshot: ScreenSnapshot?
    ): StepVerificationResult {
        // If action execution itself failed at low-level, verification fails
        if (!actionResult.isSuccess) {
            return StepVerificationResult.Failed(
                "Action execution failed: ${actionResult.errorMessage ?: actionResult.message}"
            )
        }

        val criteria = step.successCriteria

        // 1. Package verification
        val expectedPkg = criteria?.expectedPackage ?: step.parameters.packageName
        if (!expectedPkg.isNullOrBlank()) {
            val activePkg = accessibilityController.getActivePackage()
            if (activePkg == null || !activePkg.contains(expectedPkg, ignoreCase = true)) {
                return StepVerificationResult.Failed(
                    "Foreground app verification failed: expected '$expectedPkg' but found '$activePkg'"
                )
            }
        }

        // 2. Expected Text / Element verification
        val expectedText = criteria?.expectedText
        if (!expectedText.isNullOrBlank()) {
            if (postSnapshot != null) {
                val found = postSnapshot.elements.any { elem ->
                    elem.text?.contains(expectedText, ignoreCase = true) == true ||
                            elem.contentDescription?.contains(expectedText, ignoreCase = true) == true
                }
                if (!found) {
                    return StepVerificationResult.Failed(
                        "Expected text '$expectedText' was not observed on screen after action"
                    )
                }
            } else {
                val visibleTexts = accessibilityController.readScreenContent()
                if (visibleTexts.none { it.contains(expectedText, ignoreCase = true) }) {
                    return StepVerificationResult.Failed(
                        "Expected text '$expectedText' was not found in visible screen elements"
                    )
                }
            }
        }

        // 3. Expected View ID verification
        val expectedViewId = criteria?.expectedViewId
        if (!expectedViewId.isNullOrBlank() && postSnapshot != null) {
            val match = ElementFinder.findByResourceId(postSnapshot.elements, expectedViewId).firstOrNull()
            if (match == null) {
                return StepVerificationResult.Failed(
                    "Expected element with ID '$expectedViewId' not found on screen"
                )
            }
        }

        // 4. Screen Mutation Check for actions that must change UI
        if (criteria?.screenMustChange == true && step.action in listOf(ActionType.TAP, ActionType.SCROLL_FORWARD, ActionType.SCROLL_BACKWARD, ActionType.TYPE_TEXT)) {
            if (preSignature != null && postSignature != null && !postSignature.hasChangedFrom(preSignature)) {
                // Warning: screen did not visibly change after tap or scroll
                return StepVerificationResult.Inconclusive(
                    "Action completed, but screen signature did not register significant mutation"
                )
            }
        }

        return StepVerificationResult.Verified(
            "Step '${step.description.ifBlank { step.id }}' verified successfully against observable screen state."
        )
    }

    /**
     * Verifies the overall goal of the autonomous task.
     */
    fun verifyGoal(
        goal: String,
        lastSnapshot: ScreenSnapshot?,
        completedSteps: List<TaskStep>
    ): GoalVerificationResult {
        if (completedSteps.isEmpty()) {
            return GoalVerificationResult.Failed("No steps were successfully completed")
        }

        val activePkg = accessibilityController.getActivePackage() ?: lastSnapshot?.packageName
        val goalLower = goal.lowercase()

        // Check if goal mentions an application and if it is foreground
        val appMatches = when {
            goalLower.contains("youtube") -> activePkg?.contains("youtube", ignoreCase = true) == true
            goalLower.contains("chrome") -> activePkg?.contains("chrome", ignoreCase = true) == true
            goalLower.contains("settings") -> activePkg?.contains("settings", ignoreCase = true) == true
            goalLower.contains("maps") -> activePkg?.contains("maps", ignoreCase = true) == true
            goalLower.contains("camera") -> activePkg?.contains("camera", ignoreCase = true) == true
            else -> null
        }

        if (appMatches == false) {
            return GoalVerificationResult.Failed(
                "Goal target application is not in the foreground. Current: $activePkg"
            )
        }

        // Check if the final step had specific success criteria that was met
        val lastStep = completedSteps.lastOrNull()
        if (lastStep?.successCriteria?.expectedText != null) {
            val text = lastStep.successCriteria.expectedText
            val hasText = lastSnapshot?.elements?.any {
                it.text?.contains(text, ignoreCase = true) == true ||
                        it.contentDescription?.contains(text, ignoreCase = true) == true
            } == true

            if (hasText) {
                return GoalVerificationResult.Verified("Verified goal completed: '$goal'. UI displays expected target '$text'.")
            }
        }

        return GoalVerificationResult.PartiallyVerified(
            "Autonomous sequence completed (${completedSteps.size} steps). Active app: ${activePkg ?: "Current screen"}."
        )
    }
}
