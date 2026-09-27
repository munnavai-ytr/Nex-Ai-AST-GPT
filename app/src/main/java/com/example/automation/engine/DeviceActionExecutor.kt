package com.example.automation.engine

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import com.example.accessibility.AccessibilityController
import com.example.accessibility.NexAccessibilityService
import com.example.accessibility.inspector.UIHierarchyInspector
import com.example.accessibility.inspector.UIHierarchySnapshot
import com.example.automation.Action
import com.example.automation.ActionResult
import com.example.automation.ActionStatus
import com.example.automation.ActionType
import com.example.automation.appcontrol.AppAutomationRegistry
import com.example.automation.appcontrol.AppLauncher
import com.example.automation.appcontrol.AppResolutionResult
import com.example.automation.appcontrol.AppResolver
import com.example.automation.appcontrol.ProviderExecutionContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

class DeviceActionExecutor(
    private val context: Context,
    private val accessibilityController: AccessibilityController,
    private val uiInspector: UIHierarchyInspector,
    private val appResolver: AppResolver? = null,
    private val appLauncher: AppLauncher? = null,
    private val appAutomationRegistry: AppAutomationRegistry? = null
) {

    private val settingsMap = mapOf(
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
        "wireless" to Settings.ACTION_WIRELESS_SETTINGS,
        "battery" to Settings.ACTION_BATTERY_SAVER_SETTINGS,
        "storage" to Settings.ACTION_INTERNAL_STORAGE_SETTINGS,
        "security" to Settings.ACTION_SECURITY_SETTINGS,
        "location" to Settings.ACTION_LOCATION_SOURCE_SETTINGS,
        "date" to Settings.ACTION_DATE_SETTINGS
    )

    suspend fun executeAction(action: Action, taskId: String): ActionResult {
        val startTime = System.currentTimeMillis()
        var resultStatus = ActionStatus.SUCCESS
        var message = "Executed ${action.type}"
        val outputData = mutableMapOf<String, String>()
        var errorCode: String? = null

        val targetDescription = action.parameters.targetText
            ?: action.parameters.appName
            ?: action.parameters.packageName
            ?: action.parameters.viewId
            ?: action.parameters.query
            ?: action.parameters.direction
            ?: action.description

        try {
            when (action.type) {
                ActionType.OPEN_APP -> {
                    val appTarget = action.parameters.appName ?: action.parameters.packageName ?: ""
                    val lower = appTarget.lowercase().trim()

                    // Check if Settings category was requested directly in OPEN_APP
                    if (lower == "settings" || lower.startsWith("settings ") || lower.contains("setting") || lower.contains("wifi") || lower.contains("bluetooth")) {
                        val category = settingsMap.keys.firstOrNull { lower.contains(it) }
                        if (category != null && category != "settings") {
                            val intentAction = settingsMap[category] ?: Settings.ACTION_SETTINGS
                            val intent = Intent(intentAction).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                            context.startActivity(intent)
                            message = "Opened Android Settings ($category)"
                            outputData["category"] = category
                        } else {
                            val res = executeOpenApp(appTarget, action, taskId)
                            resultStatus = res.status
                            message = res.message
                            errorCode = res.errorCode
                        }
                    } else {
                        val res = executeOpenApp(appTarget, action, taskId)
                        resultStatus = res.status
                        message = res.message
                        errorCode = res.errorCode
                    }
                }

                ActionType.TAP -> {
                    val targetText = action.parameters.targetText
                    val viewId = action.parameters.viewId
                    val xPercent = action.parameters.xPercent
                    val yPercent = action.parameters.yPercent

                    if (xPercent != null && yPercent != null) {
                        val metrics = context.resources.displayMetrics
                        val x = (xPercent * metrics.widthPixels).coerceIn(0f, metrics.widthPixels.toFloat())
                        val y = (yPercent * metrics.heightPixels).coerceIn(0f, metrics.heightPixels.toFloat())
                        val success = accessibilityController.performTapCoordinates(x, y)
                        if (success) {
                            message = "Tapped coordinates ($x, $y)"
                        } else {
                            resultStatus = ActionStatus.FAILED
                            errorCode = "GESTURE_FAILED"
                            message = "Failed to tap coordinates"
                        }
                    } else if (!viewId.isNullOrBlank()) {
                        val success = accessibilityController.performClickById(viewId)
                        if (success) {
                            message = "Tapped element ID: \"$viewId\""
                        } else {
                            // Try locating via UIHierarchyInspector
                            val snapshot = uiInspector.inspectCurrentScreen()
                            val match = snapshot.findByResourceId(viewId).firstOrNull()
                            if (match != null) {
                                val cx = (match.bounds.left + match.bounds.right) / 2f
                                val cy = (match.bounds.top + match.bounds.bottom) / 2f
                                val tapped = accessibilityController.performTapCoordinates(cx, cy)
                                if (tapped) {
                                    message = "Tapped element ID bounds: \"$viewId\""
                                } else {
                                    resultStatus = ActionStatus.NOT_FOUND
                                    errorCode = "VIEW_ID_NOT_FOUND"
                                    message = "Could not tap element ID: \"$viewId\""
                                }
                            } else {
                                resultStatus = ActionStatus.NOT_FOUND
                                errorCode = "VIEW_ID_NOT_FOUND"
                                message = "Element ID not found: \"$viewId\""
                            }
                        }
                    } else if (!targetText.isNullOrBlank()) {
                        // Check special cases like "first result", "search", etc.
                        if (targetText.equals("first result", ignoreCase = true) || targetText.equals("open first result", ignoreCase = true) || targetText.contains("প্রথম রেজাল্ট")) {
                            val snapshot = uiInspector.inspectCurrentScreen()
                            val firstClickable = snapshot.findFirstClickableResult()
                            if (firstClickable != null) {
                                val cx = (firstClickable.bounds.left + firstClickable.bounds.right) / 2f
                                val cy = (firstClickable.bounds.top + firstClickable.bounds.bottom) / 2f
                                val success = accessibilityController.performTapCoordinates(cx, cy)
                                if (success) {
                                    message = "Tapped first result: \"${firstClickable.displayLabel}\""
                                } else {
                                    resultStatus = ActionStatus.FAILED
                                    message = "Failed to tap first result"
                                }
                            } else {
                                resultStatus = ActionStatus.NOT_FOUND
                                message = "Could not identify first result on screen"
                            }
                        } else {
                            val success = accessibilityController.performClickOnText(targetText)
                            if (success) {
                                message = "Tapped element: \"$targetText\""
                            } else {
                                // Fuzzy match via UIHierarchyInspector
                                val snapshot = uiInspector.inspectCurrentScreen()
                                val match = snapshot.findByText(targetText).firstOrNull()
                                if (match != null) {
                                    val cx = (match.bounds.left + match.bounds.right) / 2f
                                    val cy = (match.bounds.top + match.bounds.bottom) / 2f
                                    val tapped = accessibilityController.performTapCoordinates(cx, cy)
                                    if (tapped) {
                                        message = "Tapped element via spatial bounds: \"${match.displayLabel}\""
                                    } else {
                                        resultStatus = ActionStatus.NOT_FOUND
                                        errorCode = "ELEMENT_NOT_FOUND"
                                        message = "Could not tap: \"$targetText\""
                                    }
                                } else {
                                    resultStatus = ActionStatus.NOT_FOUND
                                    errorCode = "ELEMENT_NOT_FOUND"
                                    message = "Element not found on screen: \"$targetText\""
                                }
                            }
                        }
                    } else {
                        resultStatus = ActionStatus.VALIDATION_FAILED
                        errorCode = "MISSING_TARGET"
                        message = "TAP requires target text, viewId, or coordinates"
                    }
                }

                ActionType.TYPE_TEXT -> {
                    val inputText = action.parameters.inputText ?: ""
                    val targetText = action.parameters.targetText
                    val success = accessibilityController.performInputText(targetText, inputText)
                    if (success) {
                        message = "Entered text: \"$inputText\""
                    } else {
                        // Locate first editable field in inspector
                        val snapshot = uiInspector.inspectCurrentScreen()
                        val editable = snapshot.findFirstEditable()
                        if (editable != null) {
                            val cx = (editable.bounds.left + editable.bounds.right) / 2f
                            val cy = (editable.bounds.top + editable.bounds.bottom) / 2f
                            accessibilityController.performTapCoordinates(cx, cy)
                            delay(200L)
                            val retried = accessibilityController.performInputText(null, inputText)
                            if (retried) {
                                message = "Focused editable node and entered text: \"$inputText\""
                            } else {
                                resultStatus = ActionStatus.FAILED
                                errorCode = "INPUT_FAILED"
                                message = "Could not type into editable field"
                            }
                        } else {
                            resultStatus = ActionStatus.FAILED
                            errorCode = "INPUT_FAILED"
                            message = "No editable field found on current screen"
                        }
                    }
                }

                ActionType.CLEAR_TEXT -> {
                    val success = accessibilityController.performClearText(action.parameters.targetText, action.parameters.viewId)
                    if (success) {
                        message = "Cleared text field"
                    } else {
                        resultStatus = ActionStatus.FAILED
                        message = "Could not clear text field"
                    }
                }

                ActionType.SCROLL, ActionType.SCROLL_FORWARD -> {
                    val forward = action.parameters.direction?.uppercase() != "UP" &&
                            action.parameters.direction?.uppercase() != "BACKWARD"
                    val success = if (forward) {
                        accessibilityController.performScrollForward(action.parameters.viewId) || accessibilityController.performScroll(true)
                    } else {
                        accessibilityController.performScrollBackward(action.parameters.viewId) || accessibilityController.performScroll(false)
                    }
                    if (success) {
                        message = "Scrolled ${if (forward) "down" else "up"}"
                    } else {
                        resultStatus = ActionStatus.FAILED
                        message = "Failed to scroll active view"
                    }
                }

                ActionType.SCROLL_BACKWARD -> {
                    val success = accessibilityController.performScrollBackward(action.parameters.viewId) || accessibilityController.performScroll(false)
                    if (success) {
                        message = "Scrolled up"
                    } else {
                        resultStatus = ActionStatus.FAILED
                        message = "Failed to scroll active view upwards"
                    }
                }

                ActionType.SWIPE -> {
                    val direction = action.parameters.direction?.uppercase() ?: "UP"
                    val metrics = context.resources.displayMetrics
                    val w = metrics.widthPixels.toFloat()
                    val h = metrics.heightPixels.toFloat()

                    val (sx, sy, ex, ey) = when (direction) {
                        "UP" -> listOf(w / 2, h * 0.75f, w / 2, h * 0.25f)
                        "DOWN" -> listOf(w / 2, h * 0.25f, w / 2, h * 0.75f)
                        "LEFT" -> listOf(w * 0.8f, h / 2, w * 0.2f, h / 2)
                        "RIGHT" -> listOf(w * 0.2f, h / 2, w * 0.8f, h / 2)
                        else -> listOf(w / 2, h * 0.75f, w / 2, h * 0.25f)
                    }

                    val duration = (action.parameters.durationMs ?: 300L).coerceIn(100L, 2000L)
                    val success = accessibilityController.performSwipe(sx, sy, ex, ey, duration)
                    if (success) {
                        message = "Swiped $direction"
                    } else {
                        resultStatus = ActionStatus.FAILED
                        message = "Failed to perform swipe gesture"
                    }
                }

                ActionType.LONG_PRESS -> {
                    val metrics = context.resources.displayMetrics
                    val x = (action.parameters.xPercent ?: 0.5f) * metrics.widthPixels
                    val y = (action.parameters.yPercent ?: 0.5f) * metrics.heightPixels
                    val duration = (action.parameters.durationMs ?: 800L).coerceIn(400L, 5000L)
                    val success = accessibilityController.performLongPress(x, y, duration)
                    if (success) {
                        message = "Executed long press at ($x, $y)"
                    } else {
                        resultStatus = ActionStatus.FAILED
                        message = "Failed to execute long press"
                    }
                }

                ActionType.BACK -> {
                    val success = accessibilityController.performBack()
                    if (success) {
                        message = "Navigated back"
                    } else {
                        resultStatus = ActionStatus.FAILED
                        message = "Failed to navigate back (Accessibility Service required)"
                    }
                }

                ActionType.HOME -> {
                    val success = accessibilityController.performHome()
                    if (success) {
                        message = "Navigated to Home"
                    } else {
                        resultStatus = ActionStatus.FAILED
                        message = "Failed to navigate to Home"
                    }
                }

                ActionType.SEARCH -> {
                    val query = action.parameters.query ?: action.parameters.inputText ?: ""
                    val provider = appAutomationRegistry?.findProviderForAction(action, null)
                    if (provider != null) {
                        val launcher = appLauncher ?: AppLauncher(context, accessibilityController)
                        val providerResult = provider.executeAction(
                            action,
                            null,
                            ProviderExecutionContext(context, accessibilityController, launcher, taskId)
                        )
                        resultStatus = providerResult.status
                        message = providerResult.message
                        errorCode = providerResult.errorCode
                    } else {
                        val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                            putExtra(SearchManager.QUERY, query)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        try {
                            context.startActivity(intent)
                            message = "Dispatched search for: \"$query\""
                        } catch (e: Exception) {
                            resultStatus = ActionStatus.FAILED
                            message = "Could not launch web search: ${e.message}"
                        }
                    }
                }

                ActionType.READ_SCREEN -> {
                    val snapshot = uiInspector.inspectCurrentScreen()
                    outputData["totalElements"] = snapshot.totalElements.toString()
                    outputData["activePackage"] = snapshot.packageName ?: "unknown"
                    message = "Inspected screen hierarchy: ${snapshot.totalElements} elements found"
                }

                ActionType.WAIT -> {
                    val duration = (action.parameters.durationMs ?: 1000L).coerceIn(50L, 30000L)
                    delay(duration)
                    message = "Waited for ${duration}ms"
                }

                ActionType.VERIFY -> {
                    val target = action.parameters.targetText
                    val pkg = action.parameters.packageName
                    val snapshot = uiInspector.inspectCurrentScreen()

                    var verified = false
                    if (pkg != null && snapshot.packageName?.contains(pkg, ignoreCase = true) == true) {
                        verified = true
                        message = "Verified active package: $pkg"
                    } else if (target != null && snapshot.findByText(target).isNotEmpty()) {
                        verified = true
                        message = "Verified \"$target\" on screen"
                    }

                    if (!verified) {
                        resultStatus = ActionStatus.FAILED
                        message = "Verification failed for target: ${target ?: pkg}"
                    }
                }

                ActionType.ASK_CONFIRMATION -> {
                    resultStatus = ActionStatus.REQUIRES_CONFIRMATION
                    message = action.parameters.confirmationPrompt ?: "Confirmation required"
                }
            }
        } catch (e: Exception) {
            resultStatus = ActionStatus.FAILED
            errorCode = "EXECUTION_EXCEPTION"
            message = "Execution failed: ${e.message}"
        }

        val duration = System.currentTimeMillis() - startTime
        return ActionResult(
            actionId = action.id,
            actionType = action.type,
            status = resultStatus,
            message = message,
            durationMs = duration,
            outputData = outputData,
            timestamp = startTime,
            errorCode = errorCode,
            errorMessage = if (resultStatus != ActionStatus.SUCCESS) message else null,
            targetDescription = targetDescription
        )
    }

    private suspend fun executeOpenApp(appTarget: String, action: Action, taskId: String): ActionResult {
        val startTime = System.currentTimeMillis()
        val launcher = appLauncher ?: AppLauncher(context, accessibilityController)

        if (appResolver != null && appTarget.isNotBlank()) {
            when (val resolved = appResolver.resolve(appTarget)) {
                is AppResolutionResult.Resolved -> {
                    val provider = appAutomationRegistry?.getProvider(resolved.app.packageName)
                    if (provider != null && provider.canHandle(action, null)) {
                        return provider.executeAction(
                            action,
                            null,
                            ProviderExecutionContext(context, accessibilityController, launcher, taskId)
                        )
                    } else {
                        val launchResult = launcher.openApplication(
                            packageName = resolved.app.packageName,
                            appLabel = resolved.app.applicationLabel,
                            verifyForeground = true
                        )
                        return when (launchResult) {
                            is com.example.automation.appcontrol.AppLaunchResult.Success -> {
                                ActionResult(
                                    actionId = action.id,
                                    actionType = action.type,
                                    status = ActionStatus.SUCCESS,
                                    message = launchResult.message,
                                    durationMs = System.currentTimeMillis() - startTime
                                )
                            }
                            is com.example.automation.appcontrol.AppLaunchResult.NotInstalled -> {
                                ActionResult(
                                    actionId = action.id,
                                    actionType = action.type,
                                    status = ActionStatus.APP_NOT_INSTALLED,
                                    errorCode = "APP_NOT_INSTALLED",
                                    message = launchResult.message,
                                    durationMs = System.currentTimeMillis() - startTime
                                )
                            }
                            is com.example.automation.appcontrol.AppLaunchResult.NoLaunchIntent -> {
                                ActionResult(
                                    actionId = action.id,
                                    actionType = action.type,
                                    status = ActionStatus.APP_LAUNCH_FAILED,
                                    errorCode = "NO_LAUNCH_INTENT",
                                    message = launchResult.message,
                                    durationMs = System.currentTimeMillis() - startTime
                                )
                            }
                            is com.example.automation.appcontrol.AppLaunchResult.ForegroundVerificationFailed -> {
                                ActionResult(
                                    actionId = action.id,
                                    actionType = action.type,
                                    status = ActionStatus.FAILED,
                                    errorCode = "FOREGROUND_VERIFICATION_FAILED",
                                    message = launchResult.message,
                                    durationMs = System.currentTimeMillis() - startTime
                                )
                            }
                            is com.example.automation.appcontrol.AppLaunchResult.LaunchFailed -> {
                                ActionResult(
                                    actionId = action.id,
                                    actionType = action.type,
                                    status = ActionStatus.APP_LAUNCH_FAILED,
                                    errorCode = "LAUNCH_FAILED",
                                    message = launchResult.message,
                                    durationMs = System.currentTimeMillis() - startTime
                                )
                            }
                        }
                    }
                }
                is AppResolutionResult.Ambiguous -> {
                    return ActionResult(
                        actionId = action.id,
                        actionType = action.type,
                        status = ActionStatus.FAILED,
                        errorCode = "AMBIGUOUS_MATCH",
                        message = "Multiple applications match '$appTarget': ${resolved.candidates.joinToString { it.applicationLabel }}",
                        durationMs = System.currentTimeMillis() - startTime
                    )
                }
                is AppResolutionResult.NotFound -> {
                    return ActionResult(
                        actionId = action.id,
                        actionType = action.type,
                        status = ActionStatus.APP_NOT_INSTALLED,
                        errorCode = "APP_NOT_INSTALLED",
                        message = "Application '$appTarget' is not installed on this device.",
                        durationMs = System.currentTimeMillis() - startTime
                    )
                }
            }
        }

        // Direct package launch fallback
        val pm = context.packageManager
        val intent = pm.getLaunchIntentForPackage(appTarget)
        return if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                ActionResult(
                    actionId = action.id,
                    actionType = action.type,
                    status = ActionStatus.SUCCESS,
                    message = "Opened application: $appTarget",
                    durationMs = System.currentTimeMillis() - startTime
                )
            } catch (e: Exception) {
                ActionResult(
                    actionId = action.id,
                    actionType = action.type,
                    status = ActionStatus.APP_LAUNCH_FAILED,
                    errorCode = "LAUNCH_FAILED",
                    message = "Could not start activity: ${e.message}",
                    durationMs = System.currentTimeMillis() - startTime
                )
            }
        } else {
            ActionResult(
                actionId = action.id,
                actionType = action.type,
                status = ActionStatus.APP_NOT_INSTALLED,
                errorCode = "APP_NOT_INSTALLED",
                message = "Application not found: $appTarget",
                durationMs = System.currentTimeMillis() - startTime
            )
        }
    }
}
