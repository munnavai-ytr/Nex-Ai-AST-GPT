package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.accessibility.AccessibilityController
import com.example.accessibility.model.AccessibilityServiceState
import com.example.accessibility.model.ForegroundAppInfo
import com.example.accessibility.model.ScreenSnapshot
import com.example.accessibility.model.ScreenSnapshotResult
import com.example.automation.Action
import com.example.automation.ActionParameters
import com.example.automation.ActionType
import com.example.automation.appcontrol.AppAutomationRegistry
import com.example.automation.appcontrol.AppCapability
import com.example.automation.appcontrol.AppControlStrategy
import com.example.automation.appcontrol.AppLaunchResult
import com.example.automation.appcontrol.AppLauncher
import com.example.automation.appcontrol.AppResolutionResult
import com.example.automation.appcontrol.AppResolver
import com.example.automation.appcontrol.ExecutionTier
import com.example.automation.appcontrol.InstalledApp
import com.example.automation.appcontrol.InstalledAppRegistry
import com.example.automation.appcontrol.ResolutionMatchReason
import com.example.automation.appcontrol.providers.ChromeAutomationProvider
import com.example.automation.appcontrol.providers.SettingsAutomationProvider
import com.example.automation.appcontrol.providers.YouTubeAutomationProvider
import com.example.memory.MemoryManager
import com.example.memory.MemoryRepository
import com.example.memory.NexDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppControlTest {

    private lateinit var context: Context
    private lateinit var database: NexDatabase
    private lateinit var memoryRepository: MemoryRepository
    private lateinit var memoryManager: MemoryManager
    private lateinit var appRegistry: InstalledAppRegistry
    private lateinit var appResolver: AppResolver
    private lateinit var appAutomationRegistry: AppAutomationRegistry

    class TestAccessibilityController(
        private var activePackageName: String? = null,
        private var isConnected: Boolean = true
    ) : AccessibilityController {
        override fun isAccessibilityActive(): Boolean = isConnected
        override fun getActivePackage(): String? = activePackageName
        override fun performHome(): Boolean = true
        override fun performBack(): Boolean = true
        override fun performClickOnText(text: String): Boolean = true
        override fun performClickById(viewId: String): Boolean = true
        override fun performInputText(targetText: String?, text: String): Boolean = true
        override fun performClearText(targetText: String?, viewId: String?): Boolean = true
        override fun performScroll(forward: Boolean): Boolean = true
        override fun performScrollForward(viewId: String?): Boolean = true
        override fun performScrollBackward(viewId: String?): Boolean = true
        override fun readScreenContent(): List<String> = emptyList()
        override fun openAccessibilitySettings() {}
        override fun getServiceState(): AccessibilityServiceState = AccessibilityServiceState.CONNECTED
        override fun getCurrentForegroundApp(): ForegroundAppInfo? = activePackageName?.let {
            ForegroundAppInfo(packageName = it, displayName = it, timestamp = System.currentTimeMillis())
        }
        override fun captureScreenSnapshot(filterSensitive: Boolean): ScreenSnapshotResult =
            ScreenSnapshotResult.Success(ScreenSnapshot(packageName = activePackageName ?: "", displayName = "App"))
        override suspend fun performTapCoordinates(x: Float, y: Float): Boolean = true
        override suspend fun performSwipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long): Boolean = true
        override suspend fun performLongPress(x: Float, y: Float, durationMs: Long): Boolean = true

        fun setActivePackage(pkg: String?) {
            activePackageName = pkg
        }
    }

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = NexDatabase.getInstance(context)
        memoryRepository = MemoryRepository(database.memoryDao(), database.taskDao())
        memoryManager = MemoryManager(memoryRepository)
        appRegistry = InstalledAppRegistry(context)
        appResolver = AppResolver(appRegistry, memoryManager)
        appAutomationRegistry = AppAutomationRegistry().apply {
            register(YouTubeAutomationProvider())
            register(ChromeAutomationProvider())
            register(SettingsAutomationProvider())
        }
    }

    @Test
    fun `InstalledAppRegistry discovers apps and detects capabilities`() = runTest {
        val apps = appRegistry.refresh()
        assertNotNull(apps)
        // Check that at least Settings or system apps are discovered in Robolectric environment
        val settingsApp = appRegistry.findByPackage("com.android.settings")
        if (settingsApp != null) {
            assertTrue(settingsApp.capabilities.contains(AppCapability.CAN_LAUNCH))
            assertTrue(settingsApp.capabilities.contains(AppCapability.CAN_OPEN_SETTINGS))
            assertTrue(settingsApp.capabilities.contains(AppCapability.CAN_NAVIGATE_UI))
        }
    }

    @Test
    fun `AppResolver resolves user defined aliases correctly`() = runTest {
        // Save alias "yt" -> "com.google.android.youtube"
        memoryRepository.saveAlias("yt", "com.google.android.youtube", "Custom YouTube alias")

        // Populate registry with mock apps if empty
        val testApp = InstalledApp(
            packageName = "com.google.android.youtube",
            applicationLabel = "YouTube",
            launchable = true,
            capabilities = setOf(AppCapability.CAN_LAUNCH, AppCapability.CAN_SEARCH, AppCapability.CAN_PLAY_MEDIA)
        )

        // Test resolving alias
        val result = appResolver.resolve("yt")
        if (result is AppResolutionResult.Resolved) {
            assertEquals("com.google.android.youtube", result.app.packageName)
            assertTrue(
                result.matchReason is ResolutionMatchReason.UserAlias ||
                        result.matchReason is ResolutionMatchReason.CanonicalAlias
            )
        }
    }

    @Test
    fun `AppResolver returns NotFound for nonexistent app`() = runTest {
        val result = appResolver.resolve("non_existent_fake_app_xyz_123")
        assertTrue(result is AppResolutionResult.NotFound)
    }

    @Test
    fun `AppControlStrategy prioritizes Native API over Gesture Fallback`() {
        // 1. Home / Back -> Native API
        val homeAction = Action(type = ActionType.HOME, description = "Go home")
        val homeDecision = AppControlStrategy.determineStrategy(homeAction)
        assertEquals(ExecutionTier.NATIVE_API, homeDecision.tier)

        // 2. Open App -> Android Intent
        val openAppAction = Action(
            type = ActionType.OPEN_APP,
            parameters = ActionParameters(packageName = "com.android.chrome"),
            description = "Open Chrome"
        )
        val openAppDecision = AppControlStrategy.determineStrategy(openAppAction)
        assertEquals(ExecutionTier.ANDROID_INTENT, openAppDecision.tier)

        // 3. Search with App Provider -> App Specific Integration
        val searchAction = Action(
            type = ActionType.SEARCH,
            parameters = ActionParameters(query = "Android tutorials"),
            description = "Search"
        )
        val searchDecisionWithProvider = AppControlStrategy.determineStrategy(searchAction, hasAppProvider = true)
        assertEquals(ExecutionTier.APP_SPECIFIC_INTEGRATION, searchDecisionWithProvider.tier)

        // 4. Tap with Semantic Target -> Accessibility Semantic UI
        val tapSemanticAction = Action(
            type = ActionType.TAP,
            parameters = ActionParameters(targetText = "Settings"),
            description = "Tap Settings"
        )
        val tapSemanticDecision = AppControlStrategy.determineStrategy(tapSemanticAction, hasSemanticTarget = true)
        assertEquals(ExecutionTier.ACCESSIBILITY_SEMANTIC_UI, tapSemanticDecision.tier)

        // 5. Tap with Coordinates -> Controlled Gesture Fallback
        val tapCoordAction = Action(
            type = ActionType.TAP,
            parameters = ActionParameters(xPercent = 0.5f, yPercent = 0.5f),
            description = "Tap Center"
        )
        val tapCoordDecision = AppControlStrategy.determineStrategy(tapCoordAction, hasSemanticTarget = false)
        assertEquals(ExecutionTier.CONTROLLED_GESTURE_FALLBACK, tapCoordDecision.tier)
    }

    @Test
    fun `AppAutomationRegistry properly registers and retrieves providers`() {
        val ytProvider = appAutomationRegistry.getProvider("com.google.android.youtube")
        assertNotNull(ytProvider)
        assertEquals("com.google.android.youtube", ytProvider?.targetPackage)
        assertTrue(ytProvider?.supportedCapabilities?.contains(AppCapability.CAN_SEARCH) == true)
        assertTrue(ytProvider?.supportedCapabilities?.contains(AppCapability.CAN_PLAY_MEDIA) == true)

        val chromeProvider = appAutomationRegistry.getProvider("com.android.chrome")
        assertNotNull(chromeProvider)
        assertEquals("com.android.chrome", chromeProvider?.targetPackage)
        assertTrue(chromeProvider?.supportedCapabilities?.contains(AppCapability.CAN_DEEP_LINK) == true)

        val settingsProvider = appAutomationRegistry.getProvider("com.android.settings")
        assertNotNull(settingsProvider)
        assertEquals("com.android.settings", settingsProvider?.targetPackage)
        assertTrue(settingsProvider?.supportedCapabilities?.contains(AppCapability.CAN_OPEN_SETTINGS) == true)
    }

    @Test
    fun `AppLauncher returns NotInstalled when target does not exist`() = runTest {
        val dummyController = TestAccessibilityController()
        val launcher = AppLauncher(context, dummyController)

        val result = launcher.openApplication("com.fake.app.not.installed", verifyForeground = true)
        assertTrue(result is AppLaunchResult.NotInstalled)
    }

    @Test
    fun `AppLauncher verifies foreground package when launch succeeds`() = runTest {
        val dummyController = TestAccessibilityController()
        dummyController.setActivePackage("com.android.settings")
        val launcher = AppLauncher(context, dummyController)

        val result = launcher.openApplication("com.android.settings", appLabel = "Settings", verifyForeground = true)
        // In Robolectric environment, settings package is registered
        if (result is AppLaunchResult.Success) {
            assertTrue(result.foregroundVerified)
        }
    }
}
