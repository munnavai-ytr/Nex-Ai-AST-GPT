package com.example.automation

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.example.accessibility.AccessibilityController
import com.example.accessibility.model.ScreenSnapshot
import com.example.accessibility.model.ScreenSnapshotResult
import com.example.accessibility.tree.ElementFinder
import com.example.automation.appcontrol.AppAutomationRegistry
import com.example.automation.appcontrol.AppLauncher
import com.example.automation.appcontrol.AppResolutionResult
import com.example.automation.appcontrol.AppResolver
import com.example.automation.appcontrol.InstalledAppRegistry
import com.example.automation.appcontrol.ProviderExecutionContext
import kotlinx.coroutines.delay

class ActionExecutor(
    private val context: Context,
    private val accessibilityController: AccessibilityController,
    private val executionLogger: ExecutionLogger,
    private val appRegistry: InstalledAppRegistry? = null,
    private val appResolver: AppResolver? = null,
    private val appLauncher: AppLauncher? = null,
    private val appAutomationRegistry: AppAutomationRegistry? = null
) {

    var lastObservedSnapshot: ScreenSnapshot? = null
        private set

    sealed class AppLaunchResult {
        object Success : AppLaunchResult()
        object NotInstalled : AppLaunchResult()
        object LaunchFailed : AppLaunchResult()
    }

    suspend fun execute(action: Action, taskId: String): ActionResult {
        val startTime = System.currentTimeMillis()
        var resultStatus = ActionStatus.SUCCESS
        var message = "Action executed successfully"
        val outputData = mutableMapOf<String, String>()
        var errorCode: String? = null
        var shouldObserveScreenAfter = false

        val targetDescription = action.parameters.targetText
            ?: action.parameters.appName
            ?: action.parameters.packageName
            ?: action.parameters.viewId
            ?: action.parameters.direction
            ?: action.description

        try {
            when (action.type) {
                ActionType.OPEN_APP -> {
                    val targetQuery = action.parameters.packageName ?: action.parameters.appName ?: ""
                    if (appResolver != null && targetQuery.isNotBlank()) {
                        when (val resolved = appResolver.resolve(targetQuery)) {
                            is AppResolutionResult.Resolved -> {
                                val launcher = appLauncher ?: AppLauncher(context, accessibilityController)
                                val provider = appAutomationRegistry?.getProvider(resolved.app.packageName)
                                if (provider != null && provider.canHandle(action, null)) {
                                    val providerResult = provider.executeAction(
                                        action,
                                        null,
                                        ProviderExecutionContext(context, accessibilityController, launcher, taskId)
                                    )
                                    resultStatus = providerResult.status
                                    message = providerResult.message
                                    errorCode = providerResult.errorCode
                                    shouldObserveScreenAfter = (resultStatus == ActionStatus.SUCCESS)
                                } else {
                                    val launchResult = launcher.openApplication(
                                        resolved.app.packageName,
                                        resolved.app.applicationLabel,
                                        verifyForeground = true
                                    )
                                    when (launchResult) {
                                        is com.example.automation.appcontrol.AppLaunchResult.Success -> {
                                            message = launchResult.message
                                            shouldObserveScreenAfter = true
                                        }
                                        is com.example.automation.appcontrol.AppLaunchResult.NotInstalled -> {
                                            resultStatus = ActionStatus.APP_NOT_INSTALLED
                                            errorCode = "APP_NOT_INSTALLED"
                                            message = launchResult.message
                                        }
                                        is com.example.automation.appcontrol.AppLaunchResult.ForegroundVerificationFailed -> {
                                            resultStatus = ActionStatus.FAILED
                                            errorCode = "FOREGROUND_VERIFICATION_FAILED"
                                            message = launchResult.message
                                        }
                                        is com.example.automation.appcontrol.AppLaunchResult.NoLaunchIntent -> {
                                            resultStatus = ActionStatus.APP_LAUNCH_FAILED
                                            errorCode = "NO_LAUNCH_INTENT"
                                            message = launchResult.message
                                        }
                                        is com.example.automation.appcontrol.AppLaunchResult.LaunchFailed -> {
                                            resultStatus = ActionStatus.APP_LAUNCH_FAILED
                                            errorCode = "APP_LAUNCH_FAILED"
                                            message = launchResult.message
                                        }
                                    }
                                }
                            }
                            is AppResolutionResult.Ambiguous -> {
                                resultStatus = ActionStatus.FAILED
                                errorCode = "AMBIGUOUS_APP_MATCH"
                                message = "Multiple applications match '$targetQuery': ${resolved.candidates.joinToString { it.applicationLabel }}. Please specify which app you want to open."
                            }
                            is AppResolutionResult.NotFound -> {
                                resultStatus = ActionStatus.APP_NOT_INSTALLED
                                errorCode = "APP_NOT_INSTALLED"
                                message = "Application not installed on this device: $targetQuery"
                            }
                        }
                    } else {
                        when (resolveAndLaunchApp(action.parameters.packageName, action.parameters.appName)) {
                            AppLaunchResult.Success -> {
                                message = "Opened application: ${action.parameters.appName ?: action.parameters.packageName}"
                                shouldObserveScreenAfter = true
                            }
                            AppLaunchResult.NotInstalled -> {
                                resultStatus = ActionStatus.APP_NOT_INSTALLED
                                errorCode = "APP_NOT_INSTALLED"
                                message = "Application not installed on this device: ${action.parameters.appName ?: action.parameters.packageName}"
                            }
                            AppLaunchResult.LaunchFailed -> {
                                resultStatus = ActionStatus.APP_LAUNCH_FAILED
                                errorCode = "APP_LAUNCH_FAILED"
                                message = "Could not launch application: ${action.parameters.appName ?: action.parameters.packageName}"
                            }
                        }
                    }
                }

                ActionType.HOME -> {
                    val success = accessibilityController.performHome()
                    if (!success) {
                        resultStatus = ActionStatus.FAILED
                        errorCode = "NAVIGATION_FAILED"
                        message = "Failed to navigate to Home screen"
                    } else {
                        message = "Navigated to Home screen"
                        shouldObserveScreenAfter = true
                    }
                }

                ActionType.BACK -> {
                    val success = accessibilityController.performBack()
                    if (!success) {
                        resultStatus = ActionStatus.FAILED
                        errorCode = "ACCESSIBILITY_REQUIRED"
                        message = "Failed to perform Back navigation (Accessibility Service required)"
                    } else {
                        message = "Performed Back navigation"
                        shouldObserveScreenAfter = true
                    }
                }

                ActionType.SEARCH -> {
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
                        shouldObserveScreenAfter = (resultStatus == ActionStatus.SUCCESS)
                    } else {
                        val query = action.parameters.query ?: ""
                        val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                            putExtra(SearchManager.QUERY, query)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        try {
                            context.startActivity(intent)
                            message = "Launched search for: \"$query\""
                            shouldObserveScreenAfter = true
                        } catch (e: Exception) {
                            resultStatus = ActionStatus.FAILED
                            errorCode = "SEARCH_LAUNCH_FAILED"
                            message = "Search launch failed: ${e.message}"
                        }
                    }
                }

                ActionType.WAIT -> {
                    val duration = (action.parameters.durationMs ?: 1000L).coerceIn(50L, 30000L)
                    delay(duration)
                    message = "Waited for ${duration}ms"
                }

                ActionType.TAP -> {
                    val xPercent = action.parameters.xPercent
                    val yPercent = action.parameters.yPercent
                    val xPixel = action.parameters.xPixel
                    val yPixel = action.parameters.yPixel

                    if (xPercent != null && yPercent != null) {
                        val metrics = context.resources.displayMetrics
                        val x = (xPercent * metrics.widthPixels).coerceIn(0f, metrics.widthPixels.toFloat())
                        val y = (yPercent * metrics.heightPixels).coerceIn(0f, metrics.heightPixels.toFloat())
                        val success = accessibilityController.performTapCoordinates(x, y)
                        if (!success) {
                            resultStatus = ActionStatus.FAILED
                            errorCode = "GESTURE_FAILED"
                            message = "Failed to tap coordinates ($x, $y)"
                        } else {
                            message = "Tapped coordinates ($x, $y)"
                            shouldObserveScreenAfter = true
                        }
                    } else if (xPixel != null && yPixel != null) {
                        val success = accessibilityController.performTapCoordinates(xPixel, yPixel)
                        if (!success) {
                            resultStatus = ActionStatus.FAILED
                            errorCode = "GESTURE_FAILED"
                            message = "Failed to tap pixel coordinates ($xPixel, $yPixel)"
                        } else {
                            message = "Tapped pixel coordinates ($xPixel, $yPixel)"
                            shouldObserveScreenAfter = true
                        }
                    } else if (!action.parameters.viewId.isNullOrBlank()) {
                        val viewId = action.parameters.viewId
                        var success = accessibilityController.performClickById(viewId)

                        // Deterministic recovery: refresh snapshot and try finding node via ElementFinder
                        if (!success) {
                            val snapshotResult = accessibilityController.captureScreenSnapshot(filterSensitive = false)
                            if (snapshotResult is ScreenSnapshotResult.Success) {
                                val match = ElementFinder.findByResourceId(snapshotResult.snapshot.elements, viewId).firstOrNull()
                                if (match != null && match.clickable) {
                                    val bounds = match.bounds
                                    val centerX = (bounds.left + bounds.right) / 2f
                                    val centerY = (bounds.top + bounds.bottom) / 2f
                                    success = accessibilityController.performTapCoordinates(centerX, centerY)
                                }
                            }
                        }

                        if (!success) {
                            resultStatus = ActionStatus.NOT_FOUND
                            errorCode = "VIEW_ID_NOT_FOUND"
                            message = "Could not find or tap element with ID: \"$viewId\""
                        } else {
                            message = "Tapped element ID: \"$viewId\""
                            shouldObserveScreenAfter = true
                        }
                    } else {
                        val text = action.parameters.targetText ?: ""
                        var success = accessibilityController.performClickOnText(text)

                        // Deterministic recovery: refresh snapshot and try finding node via ElementFinder
                        if (!success) {
                            val snapshotResult = accessibilityController.captureScreenSnapshot(filterSensitive = false)
                            if (snapshotResult is ScreenSnapshotResult.Success) {
                                val match = ElementFinder.findBestMatch(snapshotResult.snapshot.elements, text)
                                if (match != null) {
                                    val bounds = match.bounds
                                    val centerX = (bounds.left + bounds.right) / 2f
                                    val centerY = (bounds.top + bounds.bottom) / 2f
                                    success = accessibilityController.performTapCoordinates(centerX, centerY)
                                }
                            }
                        }

                        if (!success) {
                            resultStatus = ActionStatus.NOT_FOUND
                            errorCode = "ELEMENT_NOT_FOUND"
                            message = "Could not find or tap element with text: \"$text\""
                        } else {
                            message = "Tapped element: \"$text\""
                            shouldObserveScreenAfter = true
                        }
                    }
                }

                ActionType.LONG_PRESS -> {
                    val metrics = context.resources.displayMetrics
                    val x = (action.parameters.xPercent ?: 0.5f) * metrics.widthPixels
                    val y = (action.parameters.yPercent ?: 0.5f) * metrics.heightPixels
                    val duration = (action.parameters.durationMs ?: 800L).coerceIn(400L, 5000L)
                    val success = accessibilityController.performLongPress(x, y, duration)
                    if (!success) {
                        resultStatus = ActionStatus.FAILED
                        errorCode = "LONG_PRESS_FAILED"
                        message = "Failed to execute long press at ($x, $y)"
                    } else {
                        message = "Executed long press at ($x, $y)"
                        shouldObserveScreenAfter = true
                    }
                }

                ActionType.TYPE_TEXT -> {
                    val input = action.parameters.inputText ?: ""
                    val target = action.parameters.targetText
                    val success = accessibilityController.performInputText(target, input)
                    if (!success) {
                        resultStatus = ActionStatus.FAILED
                        errorCode = "INPUT_FAILED"
                        message = "Could not type text into target field (Accessibility Service required or field not editable)"
                    } else {
                        message = "Entered text into field"
                        shouldObserveScreenAfter = true
                    }
                }

                ActionType.CLEAR_TEXT -> {
                    val target = action.parameters.targetText
                    val viewId = action.parameters.viewId
                    val success = accessibilityController.performClearText(target, viewId)
                    if (!success) {
                        resultStatus = ActionStatus.FAILED
                        errorCode = "CLEAR_TEXT_FAILED"
                        message = "Could not clear text from target field"
                    } else {
                        message = "Cleared text from field"
                        shouldObserveScreenAfter = true
                    }
                }

                ActionType.SCROLL_FORWARD -> {
                    val success = accessibilityController.performScrollForward(action.parameters.viewId)
                    if (!success) {
                        resultStatus = ActionStatus.FAILED
                        errorCode = "SCROLL_FAILED"
                        message = "Failed to scroll forward (Accessibility Service required or not scrollable)"
                    } else {
                        message = "Scrolled forward"
                        shouldObserveScreenAfter = true
                    }
                }

                ActionType.SCROLL_BACKWARD -> {
                    val success = accessibilityController.performScrollBackward(action.parameters.viewId)
                    if (!success) {
                        resultStatus = ActionStatus.FAILED
                        errorCode = "SCROLL_FAILED"
                        message = "Failed to scroll backward (Accessibility Service required or not scrollable)"
                    } else {
                        message = "Scrolled backward"
                        shouldObserveScreenAfter = true
                    }
                }

                ActionType.SCROLL -> {
                    val forward = action.parameters.direction?.uppercase() != "UP" &&
                            action.parameters.direction?.uppercase() != "BACKWARD"
                    val success = accessibilityController.performScroll(forward)
                    if (!success) {
                        resultStatus = ActionStatus.FAILED
                        errorCode = "SCROLL_FAILED"
                        message = "Failed to scroll screen (Accessibility Service required)"
                    } else {
                        message = "Scrolled ${if (forward) "down" else "up"}"
                        shouldObserveScreenAfter = true
                    }
                }

                ActionType.SWIPE -> {
                    val metrics = context.resources.displayMetrics
                    val width = metrics.widthPixels.toFloat()
                    val height = metrics.heightPixels.toFloat()
                    val direction = action.parameters.direction?.uppercase() ?: "UP"

                    val (startX, startY, endX, endY) = when (direction) {
                        "UP" -> listOf(width / 2, height * 0.75f, width / 2, height * 0.25f)
                        "DOWN" -> listOf(width / 2, height * 0.25f, width / 2, height * 0.75f)
                        "LEFT" -> listOf(width * 0.8f, height / 2, width * 0.2f, height / 2)
                        "RIGHT" -> listOf(width * 0.2f, height / 2, width * 0.8f, height / 2)
                        else -> listOf(width / 2, height * 0.75f, width / 2, height * 0.25f)
                    }

                    val duration = (action.parameters.durationMs ?: 300L).coerceIn(100L, 3000L)
                    val success = accessibilityController.performSwipe(startX, startY, endX, endY, duration)
                    if (!success) {
                        resultStatus = ActionStatus.FAILED
                        errorCode = "SWIPE_FAILED"
                        message = "Failed to perform swipe $direction (Accessibility Service required)"
                    } else {
                        message = "Swiped $direction"
                        shouldObserveScreenAfter = true
                    }
                }

                ActionType.READ_SCREEN -> {
                    val snapshotResult = accessibilityController.captureScreenSnapshot(filterSensitive = true)
                    when (snapshotResult) {
                        is ScreenSnapshotResult.Success -> {
                            lastObservedSnapshot = snapshotResult.snapshot
                            outputData["elements_count"] = snapshotResult.snapshot.elementCount.toString()
                            outputData["active_package"] = snapshotResult.snapshot.packageName
                            message = "Captured screen snapshot with ${snapshotResult.snapshot.elementCount} elements"
                        }
                        is ScreenSnapshotResult.Error -> {
                            if (!accessibilityController.isAccessibilityActive()) {
                                resultStatus = ActionStatus.FAILED
                                errorCode = "ACCESSIBILITY_NOT_CONNECTED"
                                message = "Accessibility Service is not connected"
                            } else {
                                val texts = accessibilityController.readScreenContent()
                                outputData["texts"] = texts.joinToString(" | ")
                                message = "Read ${texts.size} visible screen elements"
                            }
                        }
                    }
                }

                ActionType.VERIFY -> {
                    val target = action.parameters.targetText
                    val pkg = action.parameters.packageName
                    val viewId = action.parameters.viewId

                    var verified = false
                    if (!pkg.isNullOrBlank()) {
                        val activePkg = accessibilityController.getActivePackage()
                        if (activePkg?.contains(pkg, ignoreCase = true) == true) {
                            verified = true
                            message = "Verified active package is $pkg"
                        } else {
                            errorCode = "VERIFY_PACKAGE_MISMATCH"
                            message = "Active package is $activePkg, expected $pkg"
                        }
                    } else if (!viewId.isNullOrBlank()) {
                        val snapshotResult = accessibilityController.captureScreenSnapshot(filterSensitive = false)
                        if (snapshotResult is ScreenSnapshotResult.Success) {
                            val match = ElementFinder.findByResourceId(snapshotResult.snapshot.elements, viewId).firstOrNull()
                            if (match != null) {
                                verified = true
                                message = "Verified element ID \"$viewId\" is present on screen"
                            } else {
                                errorCode = "VERIFY_VIEW_ID_MISSING"
                                message = "Element ID \"$viewId\" not found on screen"
                            }
                        }
                    } else if (!target.isNullOrBlank()) {
                        val texts = accessibilityController.readScreenContent()
                        val found = texts.any { it.contains(target, ignoreCase = true) }
                        if (found) {
                            verified = true
                            message = "Verified \"$target\" is visible on screen"
                        } else {
                            errorCode = "VERIFY_ELEMENT_MISSING"
                            message = "Could not verify \"$target\" on screen"
                        }
                    }

                    if (!verified) {
                        resultStatus = ActionStatus.FAILED
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

        // Observe screen state after action execution if action mutates UI
        if (shouldObserveScreenAfter && resultStatus == ActionStatus.SUCCESS) {
            try {
                delay(300L) // Allow OS to render UI changes
                val freshSnapshot = accessibilityController.captureScreenSnapshot(filterSensitive = true)
                if (freshSnapshot is ScreenSnapshotResult.Success) {
                    lastObservedSnapshot = freshSnapshot.snapshot
                    outputData["post_action_elements"] = freshSnapshot.snapshot.elementCount.toString()
                }
            } catch (_: Exception) {
                // Secondary observation failure should not fail the primary action
            }
        }

        val durationMs = System.currentTimeMillis() - startTime
        val result = ActionResult(
            actionId = action.id,
            actionType = action.type,
            status = resultStatus,
            message = message,
            durationMs = durationMs,
            outputData = outputData,
            timestamp = startTime,
            errorCode = errorCode,
            errorMessage = if (resultStatus != ActionStatus.SUCCESS) message else null,
            targetDescription = targetDescription
        )

        executionLogger.logAction(
            taskId = taskId,
            actionType = action.type,
            status = resultStatus,
            durationMs = durationMs,
            errorMessage = if (resultStatus == ActionStatus.FAILED) message else null,
            details = message
        )

        return result
    }

    private fun resolveAndLaunchApp(packageName: String?, appName: String?): AppLaunchResult {
        val pm = context.packageManager

        // 1. Direct package launch
        if (!packageName.isNullOrBlank()) {
            val isInstalled = try {
                pm.getPackageInfo(packageName, 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            } catch (_: Exception) {
                false
            }

            if (isInstalled) {
                val intent = pm.getLaunchIntentForPackage(packageName)
                return if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        context.startActivity(intent)
                        AppLaunchResult.Success
                    } catch (_: Exception) {
                        AppLaunchResult.LaunchFailed
                    }
                } else {
                    AppLaunchResult.LaunchFailed
                }
            }
        }

        // 2. Search installed applications by display label
        if (!appName.isNullOrBlank()) {
            val targetLower = appName.trim().lowercase()

            val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = pm.queryIntentActivities(launcherIntent, 0)

            // Exact match on application label
            var matchedPkg: String? = resolveInfos.firstOrNull {
                it.loadLabel(pm).toString().trim().equals(targetLower, ignoreCase = true)
            }?.activityInfo?.packageName

            // Partial match on application label
            if (matchedPkg == null) {
                matchedPkg = resolveInfos.firstOrNull {
                    val label = it.loadLabel(pm).toString().lowercase()
                    label.contains(targetLower) || targetLower.contains(label)
                }?.activityInfo?.packageName
            }

            // Fallback for standard known applications if package exists on device
            if (matchedPkg == null) {
                val candidates = when (targetLower) {
                    "youtube" -> listOf("com.google.android.youtube")
                    "chrome", "google chrome" -> listOf("com.android.chrome")
                    "settings", "android settings" -> listOf("com.android.settings")
                    "camera" -> listOf("com.android.camera", "com.google.android.GoogleCamera")
                    "clock", "alarm" -> listOf("com.google.android.deskclock", "com.android.deskclock")
                    "calculator" -> listOf("com.google.android.calculator", "com.android.calculator2")
                    "maps", "google maps" -> listOf("com.google.android.apps.maps")
                    "play store" -> listOf("com.android.vending")
                    "gmail" -> listOf("com.google.android.gm")
                    else -> emptyList()
                }

                for (candidate in candidates) {
                    val exists = try {
                        pm.getPackageInfo(candidate, 0)
                        true
                    } catch (_: Exception) {
                        false
                    }
                    if (exists) {
                        matchedPkg = candidate
                        break
                    }
                }
            }

            if (matchedPkg != null) {
                val intent = pm.getLaunchIntentForPackage(matchedPkg)
                return if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        context.startActivity(intent)
                        AppLaunchResult.Success
                    } catch (_: Exception) {
                        AppLaunchResult.LaunchFailed
                    }
                } else {
                    AppLaunchResult.LaunchFailed
                }
            }
        }

        return AppLaunchResult.NotInstalled
    }
}
