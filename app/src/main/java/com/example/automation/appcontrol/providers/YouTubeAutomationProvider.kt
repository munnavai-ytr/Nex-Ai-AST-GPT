package com.example.automation.appcontrol.providers

import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import com.example.accessibility.model.ScreenSnapshotResult
import com.example.accessibility.tree.ElementFinder
import com.example.automation.Action
import com.example.automation.ActionResult
import com.example.automation.ActionStatus
import com.example.automation.ActionType
import com.example.automation.agent.AgentTaskContext
import com.example.automation.appcontrol.AppAutomationProvider
import com.example.automation.appcontrol.AppCapability
import com.example.automation.appcontrol.ProviderExecutionContext
import kotlinx.coroutines.delay

class YouTubeAutomationProvider : AppAutomationProvider {

    override val targetPackage: String = "com.google.android.youtube"
    override val providerName: String = "YouTube Automation Provider"

    override val supportedCapabilities: Set<AppCapability> = setOf(
        AppCapability.CAN_LAUNCH,
        AppCapability.CAN_SEARCH,
        AppCapability.CAN_PLAY_MEDIA,
        AppCapability.CAN_NAVIGATE_UI,
        AppCapability.CAN_ACCEPT_TEXT,
        AppCapability.CAN_SCROLL,
        AppCapability.CAN_SHARE,
        AppCapability.CAN_DEEP_LINK,
        AppCapability.CAN_OBSERVE_SCREEN
    )

    private val searchButtonIds = listOf(
        "com.google.android.youtube:id/menu_item_search",
        "menu_item_search",
        "com.google.android.youtube:id/search_button",
        "search_button"
    )

    private val searchInputIds = listOf(
        "com.google.android.youtube:id/search_edit_text",
        "search_edit_text",
        "com.google.android.youtube:id/search_query",
        "search_query"
    )

    override fun canHandle(action: Action, context: AgentTaskContext?): Boolean {
        val pkg = action.parameters.packageName ?: context?.currentPackage
        if (pkg != targetPackage && !action.parameters.appName.equals("YouTube", ignoreCase = true)) {
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
                    appLabel = "YouTube",
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
                            errorCode = "YOUTUBE_NOT_INSTALLED"
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
                            message = "Failed to launch YouTube",
                            durationMs = System.currentTimeMillis() - startTime,
                            timestamp = startTime,
                            errorCode = "LAUNCH_FAILED"
                        )
                    }
                }
            }

            ActionType.SEARCH -> {
                val query = action.parameters.query ?: action.parameters.inputText ?: ""
                executeYouTubeSearch(query, action, executionContext, startTime)
            }

            ActionType.TYPE_TEXT -> {
                val text = action.parameters.inputText ?: ""
                // Semantic input: search for input box or active focus
                var clickedInput = false
                for (id in searchInputIds) {
                    if (executionContext.accessibilityController.performClickById(id)) {
                        clickedInput = true
                        break
                    }
                }
                val inputSuccess = executionContext.accessibilityController.performInputText(
                    targetText = null,
                    text = text
                )
                ActionResult(
                    actionId = action.id,
                    actionType = action.type,
                    status = if (inputSuccess) ActionStatus.SUCCESS else ActionStatus.FAILED,
                    message = if (inputSuccess) "Entered text into YouTube: \"$text\"" else "Failed to enter text into YouTube",
                    durationMs = System.currentTimeMillis() - startTime,
                    timestamp = startTime
                )
            }

            ActionType.TAP -> {
                // If tapping search
                val targetText = action.parameters.targetText
                if (targetText.equals("Search", ignoreCase = true) || targetText.equals("Search YouTube", ignoreCase = true)) {
                    val tapped = tapSearchButton(executionContext)
                    ActionResult(
                        actionId = action.id,
                        actionType = action.type,
                        status = if (tapped) ActionStatus.SUCCESS else ActionStatus.NOT_FOUND,
                        message = if (tapped) "Tapped YouTube search button" else "Could not locate YouTube search button via semantic elements",
                        durationMs = System.currentTimeMillis() - startTime,
                        timestamp = startTime
                    )
                } else {
                    // Fall back to general accessibility semantic tap
                    val success = if (!action.parameters.viewId.isNullOrBlank()) {
                        executionContext.accessibilityController.performClickById(action.parameters.viewId)
                    } else if (!action.parameters.targetText.isNullOrBlank()) {
                        executionContext.accessibilityController.performClickOnText(action.parameters.targetText)
                    } else {
                        false
                    }
                    ActionResult(
                        actionId = action.id,
                        actionType = action.type,
                        status = if (success) ActionStatus.SUCCESS else ActionStatus.NOT_FOUND,
                        message = if (success) "Tapped ${action.parameters.targetText ?: action.parameters.viewId}" else "Element not found in YouTube UI",
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
                    message = "Action ${action.type} is not handled specifically by YouTubeAutomationProvider",
                    durationMs = System.currentTimeMillis() - startTime,
                    timestamp = startTime
                )
            }
        }
    }

    private suspend fun executeYouTubeSearch(
        query: String,
        action: Action,
        executionContext: ProviderExecutionContext,
        startTime: Long
    ): ActionResult {
        if (query.isBlank()) {
            return ActionResult(
                actionId = action.id,
                actionType = action.type,
                status = ActionStatus.FAILED,
                message = "Search query cannot be empty",
                durationMs = System.currentTimeMillis() - startTime,
                timestamp = startTime
            )
        }

        // Strategy A: Try Intent-based deep search in YouTube app first
        val searchIntent = Intent(Intent.ACTION_SEARCH).apply {
            setPackage(targetPackage)
            putExtra(SearchManager.QUERY, query)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        val intentDispatched = try {
            executionContext.context.startActivity(searchIntent)
            true
        } catch (_: Exception) {
            // Intent not handled, fallback to URI intent or UI workflow
            false
        }

        if (intentDispatched) {
            delay(600L) // Wait for search view
            return ActionResult(
                actionId = action.id,
                actionType = action.type,
                status = ActionStatus.SUCCESS,
                message = "Dispatched native YouTube search intent for: \"$query\"",
                durationMs = System.currentTimeMillis() - startTime,
                timestamp = startTime,
                outputData = mapOf("query" to query, "method" to "native_intent")
            )
        }

        // Strategy B: Semantic UI workflow (Observe -> Find Search -> Tap Search -> Type Query)
        val searchButtonTapped = tapSearchButton(executionContext)
        if (searchButtonTapped) {
            delay(400L)
            val typed = executionContext.accessibilityController.performInputText(null, query)
            return ActionResult(
                actionId = action.id,
                actionType = action.type,
                status = if (typed) ActionStatus.SUCCESS else ActionStatus.FAILED,
                message = if (typed) "Submitted semantic search in YouTube for: \"$query\"" else "Tapped search button but failed to enter search query in YouTube",
                durationMs = System.currentTimeMillis() - startTime,
                timestamp = startTime,
                outputData = mapOf("query" to query, "method" to "semantic_ui")
            )
        }

        // Strategy C: Fallback to YouTube web URI search
        val uriIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}")).apply {
            setPackage(targetPackage)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            executionContext.context.startActivity(uriIntent)
            ActionResult(
                actionId = action.id,
                actionType = action.type,
                status = ActionStatus.SUCCESS,
                message = "Opened YouTube search results for: \"$query\"",
                durationMs = System.currentTimeMillis() - startTime,
                timestamp = startTime,
                outputData = mapOf("query" to query, "method" to "uri_intent")
            )
        } catch (e: Exception) {
            ActionResult(
                actionId = action.id,
                actionType = action.type,
                status = ActionStatus.FAILED,
                message = "Could not execute YouTube search: ${e.message}",
                durationMs = System.currentTimeMillis() - startTime,
                timestamp = startTime,
                errorCode = "SEARCH_FAILED"
            )
        }
    }

    private fun tapSearchButton(executionContext: ProviderExecutionContext): Boolean {
        // 1. Try known resource IDs
        for (id in searchButtonIds) {
            if (executionContext.accessibilityController.performClickById(id)) {
                return true
            }
        }

        // 2. Try text match
        if (executionContext.accessibilityController.performClickOnText("Search") ||
            executionContext.accessibilityController.performClickOnText("Search YouTube")
        ) {
            return true
        }

        // 3. Inspect ScreenSnapshot tree with ElementFinder for contentDescription or resourceId
        val snapshotResult = executionContext.accessibilityController.captureScreenSnapshot(filterSensitive = false)
        if (snapshotResult is ScreenSnapshotResult.Success) {
            val byDesc = ElementFinder.findByContentDescription(snapshotResult.snapshot.elements, "search")
            val byRes = ElementFinder.findByResourceId(snapshotResult.snapshot.elements, "search")
            val candidate = (byDesc + byRes).firstOrNull()
            if (candidate != null && !candidate.resourceId.isNullOrBlank()) {
                return executionContext.accessibilityController.performClickById(candidate.resourceId)
            }
        }

        return false
    }
}
