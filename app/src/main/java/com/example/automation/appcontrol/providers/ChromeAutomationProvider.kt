package com.example.automation.appcontrol.providers

import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import com.example.automation.Action
import com.example.automation.ActionResult
import com.example.automation.ActionStatus
import com.example.automation.ActionType
import com.example.automation.agent.AgentTaskContext
import com.example.automation.appcontrol.AppAutomationProvider
import com.example.automation.appcontrol.AppCapability
import com.example.automation.appcontrol.ProviderExecutionContext
import kotlinx.coroutines.delay

class ChromeAutomationProvider : AppAutomationProvider {

    override val targetPackage: String = "com.android.chrome"
    override val providerName: String = "Chrome Automation Provider"

    override val supportedCapabilities: Set<AppCapability> = setOf(
        AppCapability.CAN_LAUNCH,
        AppCapability.CAN_SEARCH,
        AppCapability.CAN_NAVIGATE_UI,
        AppCapability.CAN_ACCEPT_TEXT,
        AppCapability.CAN_SCROLL,
        AppCapability.CAN_DEEP_LINK,
        AppCapability.CAN_OBSERVE_SCREEN
    )

    private val omniboxIds = listOf(
        "com.android.chrome:id/url_bar",
        "url_bar",
        "com.android.chrome:id/search_box_text",
        "search_box_text"
    )

    override fun canHandle(action: Action, context: AgentTaskContext?): Boolean {
        val pkg = action.parameters.packageName ?: context?.currentPackage
        if (pkg != targetPackage && !action.parameters.appName.equals("Chrome", ignoreCase = true)) {
            return false
        }
        return action.type in listOf(ActionType.SEARCH, ActionType.TAP, ActionType.TYPE_TEXT, ActionType.OPEN_APP)
    }

    override suspend fun executeAction(
        action: Action,
        context: AgentTaskContext?,
        executionContext: ProviderExecutionContext
    ): ActionResult {
        val startTime = System.currentTimeMillis()

        return when (action.type) {
            ActionType.OPEN_APP -> {
                val launchResult = executionContext.appLauncher.openApplication(
                    packageName = targetPackage,
                    appLabel = "Chrome",
                    verifyForeground = true
                )
                when (launchResult) {
                    is com.example.automation.appcontrol.AppLaunchResult.Success -> {
                        ActionResult(
                            actionId = action.id,
                            actionType = action.type,
                            status = ActionStatus.SUCCESS,
                            message = launchResult.message,
                            durationMs = System.currentTimeMillis() - startTime,
                            timestamp = startTime
                        )
                    }
                    is com.example.automation.appcontrol.AppLaunchResult.NotInstalled -> {
                        ActionResult(
                            actionId = action.id,
                            actionType = action.type,
                            status = ActionStatus.APP_NOT_INSTALLED,
                            message = launchResult.message,
                            durationMs = System.currentTimeMillis() - startTime,
                            timestamp = startTime,
                            errorCode = "CHROME_NOT_INSTALLED"
                        )
                    }
                    is com.example.automation.appcontrol.AppLaunchResult.ForegroundVerificationFailed -> {
                        ActionResult(
                            actionId = action.id,
                            actionType = action.type,
                            status = ActionStatus.FAILED,
                            message = launchResult.message,
                            durationMs = System.currentTimeMillis() - startTime,
                            timestamp = startTime,
                            errorCode = "FOREGROUND_VERIFICATION_FAILED"
                        )
                    }
                    else -> {
                        ActionResult(
                            actionId = action.id,
                            actionType = action.type,
                            status = ActionStatus.APP_LAUNCH_FAILED,
                            message = "Failed to launch Chrome",
                            durationMs = System.currentTimeMillis() - startTime,
                            timestamp = startTime,
                            errorCode = "LAUNCH_FAILED"
                        )
                    }
                }
            }

            ActionType.SEARCH -> {
                val rawQuery = action.parameters.query ?: action.parameters.inputText ?: ""
                val targetUri = if (rawQuery.startsWith("http://") || rawQuery.startsWith("https://")) {
                    Uri.parse(rawQuery)
                } else {
                    Uri.parse("https://www.google.com/search?q=${Uri.encode(rawQuery)}")
                }

                val intent = Intent(Intent.ACTION_VIEW, targetUri).apply {
                    setPackage(targetPackage)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }

                try {
                    executionContext.context.startActivity(intent)
                    delay(500L)
                    ActionResult(
                        actionId = action.id,
                        actionType = action.type,
                        status = ActionStatus.SUCCESS,
                        message = "Navigated Chrome to: \"$rawQuery\"",
                        durationMs = System.currentTimeMillis() - startTime,
                        timestamp = startTime,
                        outputData = mapOf("query" to rawQuery, "url" to targetUri.toString())
                    )
                } catch (e: Exception) {
                    ActionResult(
                        actionId = action.id,
                        actionType = action.type,
                        status = ActionStatus.FAILED,
                        message = "Failed to launch Chrome search: ${e.message}",
                        durationMs = System.currentTimeMillis() - startTime,
                        timestamp = startTime,
                        errorCode = "CHROME_NAVIGATION_FAILED"
                    )
                }
            }

            ActionType.TYPE_TEXT -> {
                val text = action.parameters.inputText ?: ""
                // Focus omnibox first
                var omniboxFocused = false
                for (id in omniboxIds) {
                    if (executionContext.accessibilityController.performClickById(id)) {
                        omniboxFocused = true
                        break
                    }
                }
                delay(200L)
                val typed = executionContext.accessibilityController.performInputText(
                    targetText = null,
                    text = text
                )
                ActionResult(
                    actionId = action.id,
                    actionType = action.type,
                    status = if (typed) ActionStatus.SUCCESS else ActionStatus.FAILED,
                    message = if (typed) "Entered text into Chrome address bar: \"$text\"" else "Failed to enter text into Chrome address bar",
                    durationMs = System.currentTimeMillis() - startTime,
                    timestamp = startTime
                )
            }

            else -> {
                ActionResult(
                    actionId = action.id,
                    actionType = action.type,
                    status = ActionStatus.UNSUPPORTED,
                    message = "Action ${action.type} is not handled specifically by ChromeAutomationProvider",
                    durationMs = System.currentTimeMillis() - startTime,
                    timestamp = startTime
                )
            }
        }
    }
}
