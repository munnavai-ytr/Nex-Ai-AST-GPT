package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.accessibility.AccessibilityController
import com.example.accessibility.AndroidAccessibilityController
import com.example.accessibility.gemini.ScreenContextBuilder
import com.example.accessibility.model.AccessibilityServiceState
import com.example.accessibility.model.ForegroundAppInfo
import com.example.accessibility.model.ScreenBounds
import com.example.accessibility.model.ScreenElement
import com.example.accessibility.model.ScreenSnapshot
import com.example.accessibility.model.ScreenSnapshotResult
import com.example.accessibility.privacy.ScreenPrivacyFilter
import com.example.accessibility.provider.CurrentScreenProvider
import com.example.accessibility.service.AccessibilityStatusManager
import com.example.accessibility.tree.ElementFinder
import com.example.automation.Action
import com.example.automation.ActionExecutor
import com.example.automation.ActionParameters
import com.example.automation.ActionStatus
import com.example.automation.ActionType
import com.example.automation.ActionValidator
import com.example.automation.ExecutionLogger
import com.example.memory.MemoryRepository
import com.example.memory.NexDatabase
import com.example.permissions.PermissionManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScreenUnderstandingTest {

    private lateinit var context: Context
    private lateinit var statusManager: AccessibilityStatusManager
    private lateinit var screenProvider: CurrentScreenProvider
    private lateinit var accessibilityController: AccessibilityController
    private lateinit var permissionManager: PermissionManager
    private lateinit var actionValidator: ActionValidator
    private lateinit var actionExecutor: ActionExecutor
    private lateinit var database: NexDatabase
    private lateinit var memoryRepository: MemoryRepository
    private lateinit var executionLogger: ExecutionLogger

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = NexDatabase.getInstance(context)
        memoryRepository = MemoryRepository(database.memoryDao(), database.taskDao())
        executionLogger = ExecutionLogger(memoryRepository)
        permissionManager = PermissionManager(context)
        statusManager = AccessibilityStatusManager(context)
        screenProvider = CurrentScreenProvider(context, statusManager)
        accessibilityController = AndroidAccessibilityController(
            context = context,
            permissionManager = permissionManager,
            statusManager = statusManager,
            screenProvider = screenProvider
        )
        actionValidator = ActionValidator(accessibilityController, permissionManager)
        actionExecutor = ActionExecutor(context, accessibilityController, executionLogger)
    }

    @Test
    fun testScreenBoundsGeometry() {
        val bounds = ScreenBounds(left = 100, top = 200, right = 500, bottom = 800)
        assertEquals(400, bounds.width)
        assertEquals(600, bounds.height)
        assertEquals(300, bounds.centerX)
        assertEquals(500, bounds.centerY)

        assertTrue(bounds.contains(300, 500))
        assertTrue(bounds.contains(100, 200))
        assertFalse(bounds.contains(50, 500))
        assertFalse(bounds.contains(300, 900))
    }

    @Test
    fun testScreenElementMeaningfulLabel() {
        val elementWithText = ScreenElement(
            id = 1,
            className = "android.widget.TextView",
            text = "Search YouTube",
            contentDescription = "Search button",
            resourceId = "com.google.android.youtube:id/search_btn"
        )
        assertEquals("Search YouTube", elementWithText.meaningfulLabel)

        val elementWithDescOnly = ScreenElement(
            id = 2,
            className = "android.widget.ImageView",
            contentDescription = "Voice Search",
            resourceId = "com.google.android.youtube:id/voice_btn"
        )
        assertEquals("Voice Search", elementWithDescOnly.meaningfulLabel)

        val elementWithIdOnly = ScreenElement(
            id = 3,
            className = "android.view.View",
            resourceId = "com.example.app:id/submit_action"
        )
        assertEquals("submit_action", elementWithIdOnly.meaningfulLabel)

        val passwordElement = ScreenElement(
            id = 4,
            className = "android.widget.EditText",
            text = "mySecretPassword123",
            isPassword = true
        )
        assertEquals(ScreenPrivacyFilter.REDACTED_PLACEHOLDER, passwordElement.meaningfulLabel)
    }

    @Test
    fun testScreenPrivacyFilterRedaction() {
        val sensitiveElement1 = ScreenElement(
            id = 1,
            className = "android.widget.EditText",
            text = "secret123",
            isPassword = true
        )
        assertTrue(ScreenPrivacyFilter.isSensitiveElement(sensitiveElement1))

        val sensitiveElement2 = ScreenElement(
            id = 2,
            className = "android.widget.EditText",
            text = "987654",
            resourceId = "com.bank.app:id/pin_input"
        )
        assertTrue(ScreenPrivacyFilter.isSensitiveElement(sensitiveElement2))

        val sanitized = ScreenPrivacyFilter.sanitizeElement(sensitiveElement2)
        assertEquals(ScreenPrivacyFilter.REDACTED_PLACEHOLDER, sanitized.text)
        assertTrue(sanitized.isPassword)

        val benignElement = ScreenElement(
            id = 3,
            className = "android.widget.Button",
            text = "Subscriptions",
            clickable = true
        )
        assertFalse(ScreenPrivacyFilter.isSensitiveElement(benignElement))
    }

    @Test
    fun testElementFinderStrategies() {
        val elements = listOf(
            ScreenElement(
                id = 1,
                className = "android.widget.Button",
                text = "Subscribe",
                contentDescription = "Subscribe to channel",
                resourceId = "com.youtube:id/subscribe_button",
                clickable = true,
                bounds = ScreenBounds(100, 100, 300, 200)
            ),
            ScreenElement(
                id = 2,
                className = "android.widget.EditText",
                text = "android tutorials",
                contentDescription = "Search bar",
                resourceId = "com.youtube:id/search_query",
                editable = true,
                bounds = ScreenBounds(100, 300, 800, 400)
            ),
            ScreenElement(
                id = 3,
                className = "android.widget.ScrollView",
                scrollable = true,
                bounds = ScreenBounds(0, 400, 1080, 1920)
            )
        )

        // Exact text
        val exact = ElementFinder.findByExactText(elements, "Subscribe")
        assertEquals(1, exact.size)
        assertEquals(1, exact[0].id)

        // Partial text
        val partial = ElementFinder.findByPartialText(elements, "tutorial")
        assertEquals(1, partial.size)
        assertEquals(2, partial[0].id)

        // Resource ID
        val byRes = ElementFinder.findByResourceId(elements, "search_query")
        assertEquals(1, byRes.size)
        assertEquals(2, byRes[0].id)

        // Clickable / Editable / Scrollable
        assertEquals(1, ElementFinder.findClickable(elements).size)
        assertEquals(1, ElementFinder.findEditable(elements).size)
        assertEquals(1, ElementFinder.findScrollable(elements).size)

        // Nearest neighbor
        val nearest = ElementFinder.findNearest(elements, 120, 120)
        assertNotNull(nearest)
        assertEquals(1, nearest?.id)

        // Best match
        val bestMatch = ElementFinder.findBestMatch(elements, "subscribe")
        assertNotNull(bestMatch)
        assertEquals(1, bestMatch?.id)
    }

    @Test
    fun testScreenContextBuilderFormatting() {
        val snapshot = ScreenSnapshot(
            packageName = "com.google.android.youtube",
            displayName = "YouTube",
            screenWidth = 1080,
            screenHeight = 2400,
            elements = listOf(
                ScreenElement(
                    id = 1,
                    className = "android.widget.Button",
                    text = "Search",
                    clickable = true
                ),
                ScreenElement(
                    id = 2,
                    className = "android.widget.Button",
                    text = "Home",
                    clickable = true
                ),
                ScreenElement(
                    id = 3,
                    className = "android.widget.EditText",
                    text = "SuperSecretPassword",
                    isPassword = true,
                    editable = true
                )
            )
        )

        val contextString = ScreenContextBuilder.buildContext(snapshot)
        assertTrue(contextString.contains("CURRENT APPLICATION:"))
        assertTrue(contextString.contains("YouTube"))
        assertTrue(contextString.contains("VISIBLE UI:"))
        assertTrue(contextString.contains("text: Search"))
        assertTrue(contextString.contains("clickable: true"))
        assertTrue(contextString.contains(ScreenPrivacyFilter.REDACTED_PLACEHOLDER))
        assertFalse(contextString.contains("SuperSecretPassword"))
    }

    @Test
    fun testAccessibilityStatusManagerLifecycle() {
        statusManager.refreshState()
        // Service starts disconnected in standard test runner unless bound
        val state = statusManager.serviceConnectionState.value
        assertTrue(
            state == AccessibilityServiceState.DISABLED || state == AccessibilityServiceState.DISCONNECTED
        )

        // Test window state change event reception
        statusManager.onWindowOrContentChanged("com.android.settings")
        assertEquals("com.android.settings", statusManager.currentForegroundApp.value?.packageName)
        assertTrue(statusManager.lastScreenUpdateTime.value > 0L)

        // Element count update
        statusManager.updateObservedElementCount(42)
        assertEquals(42, statusManager.lastObservedElementCount.value)
    }

    @Test
    fun testActionValidatorCoordinateBounds() {
        val validTap = Action(
            type = ActionType.TAP,
            description = "Tap center",
            parameters = ActionParameters(xPercent = 0.5f, yPercent = 0.5f)
        )
        // Without active accessibility service, validation should detect missing capability
        val result = actionValidator.validate(validTap)
        assertFalse(result.isValid)
        assertEquals("Accessibility Service", result.missingCapability)

        // Test out of bounds coordinate
        val invalidTap = Action(
            type = ActionType.TAP,
            description = "Tap out of bounds",
            parameters = ActionParameters(xPercent = 1.5f, yPercent = 0.5f)
        )
        val invalidResult = actionValidator.validate(invalidTap)
        assertFalse(invalidResult.isValid)
        assertTrue(invalidResult.reason?.contains("xPercent must be within") == true)
    }

    @Test
    fun testActionExecutorGeneratesDetailedResult() = runBlocking {
        val tapAction = Action(
            type = ActionType.TAP,
            description = "Tap Search",
            parameters = ActionParameters(targetText = "Search")
        )

        val result = actionExecutor.execute(tapAction, "test-task-1")
        assertNotNull(result)
        assertEquals(tapAction.id, result.actionId)
        assertEquals(ActionType.TAP, result.actionType)
        // Since service is not connected in JVM test, reports actual NOT_FOUND / FAILED status
        assertTrue(result.status == ActionStatus.NOT_FOUND || result.status == ActionStatus.FAILED)
        assertNotNull(result.errorCode)
        assertEquals("Search", result.targetDescription)
    }
}
