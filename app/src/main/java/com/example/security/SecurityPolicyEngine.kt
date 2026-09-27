package com.example.security

import com.example.automation.Action
import com.example.automation.ActionType
import com.example.automation.confirmation.ConfirmationManager
import com.example.automation.confirmation.SensitiveCategory

object SecurityPolicyEngine {

    /**
     * Determines the mandatory AuthenticationLevel for an action based on its operational risk.
     */
    fun evaluateRequiredLevel(action: Action, confirmationManager: ConfirmationManager? = null): AuthenticationLevel {
        // Level 2 Sensitive Checks
        val isSensitive = (confirmationManager?.isActionSensitive(action) == true) || action.requiresConfirmation

        if (isSensitive) {
            return AuthenticationLevel.LEVEL_2_DEVICE_CREDENTIAL
        }

        val category = confirmationManager?.detectCategory(action)
        if (category != null) {
            return when (category) {
                SensitiveCategory.FINANCIAL,
                SensitiveCategory.PURCHASE,
                SensitiveCategory.SECURITY_CHANGE,
                SensitiveCategory.FACTORY_RESET,
                SensitiveCategory.FILE_DELETION,
                SensitiveCategory.APP_MANAGEMENT -> AuthenticationLevel.LEVEL_2_DEVICE_CREDENTIAL

                SensitiveCategory.MESSAGING -> AuthenticationLevel.LEVEL_2_DEVICE_CREDENTIAL
            }
        }

        // Action-specific level evaluation
        return when (action.type) {
            ActionType.HOME,
            ActionType.BACK,
            ActionType.WAIT,
            ActionType.READ_SCREEN,
            ActionType.VERIFY -> AuthenticationLevel.LEVEL_0_PUBLIC

            ActionType.OPEN_APP -> {
                val pkg = (action.parameters.packageName ?: "").lowercase()
                val app = (action.parameters.appName ?: "").lowercase()
                if (pkg.contains("bank") || app.contains("bank") ||
                    pkg.contains("pay") || app.contains("pay") ||
                    pkg.contains("wallet") || app.contains("wallet") ||
                    pkg.contains("crypto") || pkg.contains("settings")
                ) {
                    AuthenticationLevel.LEVEL_2_DEVICE_CREDENTIAL
                } else {
                    AuthenticationLevel.LEVEL_1_VOICE_VERIFIED
                }
            }

            ActionType.TYPE_TEXT -> {
                val text = (action.parameters.inputText ?: "").lowercase()
                val target = (action.parameters.targetText ?: "").lowercase()
                if (target.contains("pin") || target.contains("password") ||
                    target.contains("cvv") || text.contains("password")
                ) {
                    AuthenticationLevel.LEVEL_2_DEVICE_CREDENTIAL
                } else {
                    AuthenticationLevel.LEVEL_1_VOICE_VERIFIED
                }
            }

            ActionType.TAP,
            ActionType.LONG_PRESS,
            ActionType.CLEAR_TEXT,
            ActionType.SCROLL_FORWARD,
            ActionType.SCROLL_BACKWARD,
            ActionType.SCROLL,
            ActionType.SWIPE,
            ActionType.SEARCH -> AuthenticationLevel.LEVEL_1_VOICE_VERIFIED

            ActionType.ASK_CONFIRMATION -> AuthenticationLevel.LEVEL_2_DEVICE_CREDENTIAL
        }
    }
}
