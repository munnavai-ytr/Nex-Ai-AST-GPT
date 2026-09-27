package com.example.automation.appcontrol.providers

import android.content.Intent
import android.provider.Settings
import com.example.automation.Action
import com.example.automation.ActionResult
import com.example.automation.ActionStatus
import com.example.automation.ActionType
import com.example.automation.agent.AgentTaskContext
import com.example.automation.appcontrol.AppAutomationProvider
import com.example.automation.appcontrol.AppCapability
import com.example.automation.appcontrol.ProviderExecutionContext
import kotlinx.coroutines.delay

class SettingsAutomationProvider : AppAutomationProvider {

    override val targetPackage: String = "com.android.settings"
    override val providerName: String = "Settings Automation Provider"

    override val supportedCapabilities: Set<AppCapability> = setOf(
        AppCapability.CAN_LAUNCH,
        AppCapability.CAN_OPEN_SETTINGS,
        AppCapability.CAN_SEARCH,
        AppCapability.CAN_NAVIGATE_UI,
        AppCapability.CAN_SCROLL,
        AppCapability.CAN_OBSERVE_SCREEN
    )

    private val settingIntentMap = mapOf(
        "wifi" to Settings.ACTION_WIFI_SETTINGS,
        "wi-fi" to Settings.ACTION_WIFI_SETTINGS,
        "bluetooth" to Settings.ACTION_BLUETOOTH_SETTINGS,
        "display" to Settings.ACTION_DISPLAY_SETTINGS,
        "screen" to Settings.ACTION_DISPLAY_SETTINGS,
        "sound" to Settings.ACTION_SOUND_SETTINGS,
        "volume" to Settings.ACTION_SOUND_SETTINGS,
        "apps" to Settings.ACTION_APPLICATION_SETTINGS,
        "applications" to Settings.ACTION_APPLICATION_SETTINGS,
        "accessibility" to Settings.ACTION_ACCESSIBILITY_SETTINGS,
        "network" to Settings.ACTION_WIRELESS_SETTINGS,
        "battery" to Settings.ACTION_BATTERY_SAVER_SETTINGS,
        "storage" to Settings.ACTION_INTERNAL_STORAGE_SETTINGS,
        "security" to Settings.ACTION_SECURITY_SETTINGS,
        "location" to Settings.ACTION_LOCATION_SOURCE_SETTINGS
    )

    override fun canHandle(action: Action, context: AgentTaskContext?): Boolean {
        val pkg = action.parameters.packageName ?: context?.currentPackage
        if (pkg != targetPackage && !action.parameters.appName.equals("Settings", ignoreCase = true)) {
            return false
        }
        return action.type in listOf(ActionType.OPEN_APP, ActionType.SEARCH, ActionType.TAP, ActionType.TYPE_TEXT)
    }

    override suspend fun executeAction(
        action: Action,
        context: AgentTaskContext?,
        executionContext: ProviderExecutionContext
    ): ActionResult {
        val startTime = System.currentTimeMillis()

        return when (action.type) {
            ActionType.OPEN_APP -> {
                // Check if specific setting category is requested in targetText or query
                val category = (action.parameters.targetText ?: action.parameters.query ?: "").lowercase()
                val targetIntentAction = settingIntentMap.entries.firstOrNull {
                    category.contains(it.key)
                }?.value ?: Settings.ACTION_SETTINGS

                val intent = Intent(targetIntentAction).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }

                try {
                    executionContext.context.startActivity(intent)
                    delay(500L)
                    val activePkg = executionContext.accessibilityController.getActivePackage()
                    val verified = activePkg.equals(targetPackage, ignoreCase = true)
                    ActionResult(
                        actionId = action.id,
                        actionType = action.type,
                        status = ActionStatus.SUCCESS,
                        message = "Opened Android Settings${if (category.isNotBlank()) " ($category)" else ""}",
                        durationMs = System.currentTimeMillis() - startTime,
                        timestamp = startTime,
                        outputData = mapOf("foregroundVerified" to verified.toString(), "action" to targetIntentAction)
                    )
                } catch (e: Exception) {
                    ActionResult(
                        actionId = action.id,
                        actionType = action.type,
                        status = ActionStatus.FAILED,
                        message = "Failed to launch Settings: ${e.message}",
                        durationMs = System.currentTimeMillis() - startTime,
                        timestamp = startTime,
                        errorCode = "SETTINGS_LAUNCH_FAILED"
                    )
                }
            }

            ActionType.SEARCH -> {
                val query = action.parameters.query ?: action.parameters.inputText ?: ""
                // Try finding Settings search bar
                val searchClicked = executionContext.accessibilityController.performClickById("com.android.settings:id/search_action_bar") ||
                        executionContext.accessibilityController.performClickOnText("Search settings") ||
                        executionContext.accessibilityController.performClickOnText("Search")

                if (searchClicked) {
                    delay(300L)
                    executionContext.accessibilityController.performInputText(null, query)
                    ActionResult(
                        actionId = action.id,
                        actionType = action.type,
                        status = ActionStatus.SUCCESS,
                        message = "Searched Settings for: \"$query\"",
                        durationMs = System.currentTimeMillis() - startTime,
                        timestamp = startTime
                    )
                } else {
                    ActionResult(
                        actionId = action.id,
                        actionType = action.type,
                        status = ActionStatus.FAILED,
                        message = "Could not locate search bar in Settings",
                        durationMs = System.currentTimeMillis() - startTime,
                        timestamp = startTime
                    )
                }
            }

            else -> {
                ActionResult(
                    actionId = action.id,
                    actionType = action.type,
                    status = ActionStatus.UNSUPPORTED,
                    message = "Action ${action.type} handled via generic accessibility UI",
                    durationMs = System.currentTimeMillis() - startTime,
                    timestamp = startTime
                )
            }
        }
    }
}
