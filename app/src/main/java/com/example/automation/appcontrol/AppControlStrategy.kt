package com.example.automation.appcontrol

import com.example.automation.Action
import com.example.automation.ActionType
import com.example.automation.agent.AgentTaskContext

enum class ExecutionTier(val priority: Int, val description: String) {
    NATIVE_API(1, "Native Android OS / Framework API"),
    ANDROID_INTENT(2, "Android Intent Dispatch"),
    APP_SPECIFIC_INTEGRATION(3, "App-Specific Automation Provider"),
    ACCESSIBILITY_SEMANTIC_UI(4, "Accessibility Semantic Node Interaction"),
    CONTROLLED_GESTURE_FALLBACK(5, "Controlled Screen Coordinate Gesture Fallback")
}

data class StrategyDecision(
    val tier: ExecutionTier,
    val explanation: String
)

object AppControlStrategy {

    /**
     * Determines the optimal execution strategy strictly adhering to the priority order:
     * 1. Native Android API (e.g. HOME, BACK)
     * 2. Android Intent (e.g. OPEN_APP, direct SEARCH intent, settings actions)
     * 3. App-specific supported integration (e.g. YouTubeProvider, ChromeProvider)
     * 4. Accessibility UI interaction (semantic target click, text input, scroll)
     * 5. Controlled gesture fallback (pixel/percent coordinates only when no semantic target exists)
     */
    fun determineStrategy(
        action: Action,
        taskContext: AgentTaskContext? = null,
        hasAppProvider: Boolean = false,
        hasSemanticTarget: Boolean = false
    ): StrategyDecision {
        return when (action.type) {
            ActionType.HOME, ActionType.BACK, ActionType.WAIT, ActionType.READ_SCREEN -> {
                StrategyDecision(
                    ExecutionTier.NATIVE_API,
                    "Executing via native Android system framework API (${action.type})"
                )
            }

            ActionType.OPEN_APP -> {
                StrategyDecision(
                    ExecutionTier.ANDROID_INTENT,
                    "Launching via Android PackageManager and Activity Launch Intent"
                )
            }

            ActionType.SEARCH -> {
                if (hasAppProvider) {
                    StrategyDecision(
                        ExecutionTier.APP_SPECIFIC_INTEGRATION,
                        "Routing search through verified App-Specific Automation Provider"
                    )
                } else {
                    StrategyDecision(
                        ExecutionTier.ANDROID_INTENT,
                        "Executing search via Android Intent (ACTION_WEB_SEARCH / ACTION_SEARCH)"
                    )
                }
            }

            ActionType.TAP, ActionType.LONG_PRESS, ActionType.TYPE_TEXT, ActionType.CLEAR_TEXT,
            ActionType.SCROLL_FORWARD, ActionType.SCROLL_BACKWARD, ActionType.SCROLL -> {
                if (hasAppProvider) {
                    StrategyDecision(
                        ExecutionTier.APP_SPECIFIC_INTEGRATION,
                        "Delegating to registered App-Specific Automation Provider for tailored interaction"
                    )
                } else if (hasSemanticTarget) {
                    StrategyDecision(
                        ExecutionTier.ACCESSIBILITY_SEMANTIC_UI,
                        "Interacting directly with semantic accessibility node (text or resource ID)"
                    )
                } else if (action.parameters.xPercent != null || action.parameters.xPixel != null) {
                    StrategyDecision(
                        ExecutionTier.CONTROLLED_GESTURE_FALLBACK,
                        "Falling back to controlled coordinate gesture since no semantic locator was identified"
                    )
                } else {
                    StrategyDecision(
                        ExecutionTier.ACCESSIBILITY_SEMANTIC_UI,
                        "Defaulting to Accessibility UI node search"
                    )
                }
            }

            ActionType.SWIPE -> {
                StrategyDecision(
                    ExecutionTier.CONTROLLED_GESTURE_FALLBACK,
                    "Executing controlled stroke gesture via AccessibilityService dispatchGesture"
                )
            }

            ActionType.VERIFY, ActionType.ASK_CONFIRMATION -> {
                StrategyDecision(
                    ExecutionTier.NATIVE_API,
                    "Handled within NEX security and verification runtime"
                )
            }
        }
    }
}
