package com.example

import com.example.accessibility.AccessibilityController
import com.example.accessibility.inspector.UIHierarchyInspector
import com.example.accessibility.model.AccessibilityServiceState
import com.example.accessibility.model.ForegroundAppInfo
import com.example.accessibility.model.ScreenSnapshotResult
import com.example.automation.Action
import com.example.automation.ActionParameters
import com.example.automation.ActionType
import com.example.automation.appcontrol.AppAutomationRegistry
import com.example.automation.appcontrol.InstalledAppRegistry
import com.example.diagnostics.engine.AutomationDiagnosticsEngine
import com.example.diagnostics.model.DiagnosticStatus
import com.example.security.AuthenticationLevel
import com.example.security.BiometricAuthResult
import com.example.security.BiometricAvailability
import com.example.security.SecurityPolicyEngine
import com.example.security.events.SecurityEventLogger
import com.example.security.events.SecurityEventType
import com.example.security.voice.AcousticSpeakerVerificationAdapter
import com.example.security.voice.AudioFeatureExtractor
import com.example.security.voice.OwnerCommandPolicy
import com.example.security.voice.SpeakerVerificationResult
import com.example.security.voice.SpeakerVerificationStatus
import com.example.security.voice.VoiceAuthStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OwnerSecurityTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun testLowRiskActionAllowedWithVoiceMatch() {
        val action = Action(
            type = ActionType.OPEN_APP,
            parameters = ActionParameters(appName = "YouTube"),
            description = "Open YouTube"
        )
        val verifyResult = SpeakerVerificationResult(
            status = SpeakerVerificationStatus.VERIFIED,
            similarityScore = 0.88f,
            threshold = 0.80f,
            message = "Voice matched"
        )

        val decision = OwnerCommandPolicy.evaluate(
            action = action,
            isVoiceEnrolled = true,
            isVoiceAuthEnabled = true,
            verificationResult = verifyResult,
            isDeviceAuthenticated = false
        )

        assertEquals(VoiceAuthStatus.VOICE_MATCH, decision.status)
        assertTrue(decision.isAllowedToExecute)
        assertFalse(decision.requiresBiometric)
    }

    @Test
    fun testHighRiskActionRequiresDeviceAuthEvenWithVoiceMatch() {
        val action = Action(
            type = ActionType.OPEN_APP,
            parameters = ActionParameters(targetText = "delete personal account"),
            description = "Delete personal account data"
        )
        val verifyResult = SpeakerVerificationResult(
            status = SpeakerVerificationStatus.VERIFIED,
            similarityScore = 0.95f,
            threshold = 0.80f,
            message = "Voice matched"
        )

        val decision = OwnerCommandPolicy.evaluate(
            action = action,
            isVoiceEnrolled = true,
            isVoiceAuthEnabled = true,
            verificationResult = verifyResult,
            isDeviceAuthenticated = false
        )

        assertEquals(VoiceAuthStatus.DEVICE_AUTH_REQUIRED, decision.status)
        assertFalse(decision.isAllowedToExecute)
        assertTrue(decision.requiresBiometric)
        assertTrue(decision.requiresExplicitConfirmation)
    }

    @Test
    fun testHighRiskActionAllowedWhenDeviceAuthenticated() {
        val action = Action(
            type = ActionType.OPEN_APP,
            parameters = ActionParameters(targetText = "delete personal account"),
            description = "Delete personal account data"
        )
        val verifyResult = SpeakerVerificationResult(
            status = SpeakerVerificationStatus.VERIFIED,
            similarityScore = 0.95f,
            threshold = 0.80f,
            message = "Voice matched"
        )

        val decision = OwnerCommandPolicy.evaluate(
            action = action,
            isVoiceEnrolled = true,
            isVoiceAuthEnabled = true,
            verificationResult = verifyResult,
            isDeviceAuthenticated = true
        )

        assertEquals(VoiceAuthStatus.OWNER_VERIFIED, decision.status)
        assertTrue(decision.isAllowedToExecute)
        assertFalse(decision.requiresBiometric)
        assertTrue(decision.requiresExplicitConfirmation)
    }

    @Test
    fun testVoiceMismatchBlocksExecution() {
        val action = Action(
            type = ActionType.TYPE_TEXT,
            parameters = ActionParameters(inputText = "Hello"),
            description = "Type text"
        )
        val verifyResult = SpeakerVerificationResult(
            status = SpeakerVerificationStatus.REJECTED,
            similarityScore = 0.45f,
            threshold = 0.80f,
            message = "Speaker mismatch"
        )

        val decision = OwnerCommandPolicy.evaluate(
            action = action,
            isVoiceEnrolled = true,
            isVoiceAuthEnabled = true,
            verificationResult = verifyResult,
            isDeviceAuthenticated = false
        )

        assertEquals(VoiceAuthStatus.VOICE_NO_MATCH, decision.status)
        assertFalse(decision.isAllowedToExecute)
        assertTrue(decision.requiresBiometric)
    }

    @Test
    fun testFailedBiometricAuthNeverAuthorizesAction() {
        val failedResult: BiometricAuthResult = BiometricAuthResult.Failed
        val errorResult: BiometricAuthResult = BiometricAuthResult.Error(1, "Hardware locked")

        assertFalse(failedResult.isSuccess)
        assertFalse(errorResult.isSuccess)

        val sensitiveAction = Action(
            type = ActionType.ASK_CONFIRMATION,
            description = "Wipe personal data"
        )

        val decision = OwnerCommandPolicy.evaluate(
            action = sensitiveAction,
            isVoiceEnrolled = true,
            isVoiceAuthEnabled = true,
            verificationResult = null,
            isDeviceAuthenticated = failedResult.isSuccess
        )

        assertFalse("Failed biometric must not authorize sensitive action", decision.isAllowedToExecute)
        assertTrue(decision.requiresBiometric)
        assertEquals(VoiceAuthStatus.DEVICE_AUTH_REQUIRED, decision.status)
    }

    @Test
    fun testCancelledBiometricAuthNeverAuthorizesAction() {
        val cancelledResult: BiometricAuthResult = BiometricAuthResult.Cancelled
        assertFalse(cancelledResult.isSuccess)

        val deleteAction = Action(
            type = ActionType.OPEN_APP,
            parameters = ActionParameters(appName = "Settings", targetText = "factory reset"),
            description = "Execute factory reset"
        )

        val decision = OwnerCommandPolicy.evaluate(
            action = deleteAction,
            isVoiceEnrolled = true,
            isVoiceAuthEnabled = true,
            verificationResult = null,
            isDeviceAuthenticated = cancelledResult.isSuccess
        )

        assertFalse("Cancelled biometric auth must never authorize execution", decision.isAllowedToExecute)
        assertTrue(decision.requiresBiometric)
    }

    @Test
    fun testMissingBiometricHardwareHandledSafely() {
        val unavailableHardware: BiometricAvailability = BiometricAvailability.UnsupportedHardware
        assertFalse(unavailableHardware.isAvailable)

        val notAvailableResult: BiometricAuthResult = BiometricAuthResult.NotAvailable(unavailableHardware.displayName)
        assertFalse(notAvailableResult.isSuccess)

        val sensitiveAction = Action(
            type = ActionType.TYPE_TEXT,
            parameters = ActionParameters(inputText = "4455", targetText = "Enter PIN"),
            description = "Enter Banking PIN"
        )

        val decision = OwnerCommandPolicy.evaluate(
            action = sensitiveAction,
            isVoiceEnrolled = true,
            isVoiceAuthEnabled = true,
            verificationResult = null,
            isDeviceAuthenticated = notAvailableResult.isSuccess
        )

        assertFalse("Missing biometric hardware must keep sensitive action blocked", decision.isAllowedToExecute)
        assertTrue(decision.requiresBiometric)
    }

    @Test
    fun testEmptyOrSyntheticAudioCannotProveOwnerIdentity() {
        val syntheticSilence = ByteArray(16000 * 2) { 0 }

        // 1. Audio quality evaluation must reject silence
        val quality = AudioFeatureExtractor.analyzeAudioQuality(syntheticSilence)
        assertFalse("Synthetic silence must not pass audio quality checks", quality.isAcceptableQuality)
        assertNotNull(quality.rejectionReason)

        // 2. Feature extractor must refuse to extract embedding from silence
        val embedding = AudioFeatureExtractor.extractSpeakerEmbedding(syntheticSilence)
        assertNull("Feature extractor must return null for silence", embedding)

        // 3. Adapter verification must reject synthetic silence
        val adapter = AcousticSpeakerVerificationAdapter(context)
        val dummyProfile = FloatArray(64) { 0.2f }
        val verification = adapter.verify(syntheticSilence, dummyProfile)

        assertEquals(SpeakerVerificationStatus.LOW_QUALITY_AUDIO, verification.status)
        assertFalse("Synthetic audio must never verify speaker identity", verification.isVerified)
        assertFalse("Acoustic DSP adapter must not be certified", verification.isBiometricallyCertified)
    }

    @Test
    fun testUncertifiedSpeakerModelNeverBypassesBiometricPrompt() {
        val adapter = AcousticSpeakerVerificationAdapter(context)
        assertFalse("Acoustic DSP must be explicitly marked uncertified", adapter.isBiometricallyCertified)

        // Even if an acoustic verification result has similarity 0.99f:
        val mockVoiceMatch = SpeakerVerificationResult(
            status = SpeakerVerificationStatus.VERIFIED,
            similarityScore = 0.99f,
            threshold = 0.80f,
            message = "Heuristic match",
            isBiometricallyCertified = false
        )

        val financialAction = Action(
            type = ActionType.OPEN_APP,
            parameters = ActionParameters(appName = "Wallet", packageName = "com.google.android.apps.walletnfcrel"),
            description = "Open Google Wallet"
        )

        val decision = OwnerCommandPolicy.evaluate(
            action = financialAction,
            isVoiceEnrolled = true,
            isVoiceAuthEnabled = true,
            verificationResult = mockVoiceMatch,
            isDeviceAuthenticated = false
        )

        assertEquals("Voice match must never bypass device auth for financial apps", VoiceAuthStatus.DEVICE_AUTH_REQUIRED, decision.status)
        assertFalse(decision.isAllowedToExecute)
        assertTrue(decision.requiresBiometric)
    }

    @Test
    fun testDiagnosticFailuresNeverBecomePass() {
        // Stub accessibility controller that is disabled
        val disabledController = object : AccessibilityController {
            override fun isAccessibilityActive(): Boolean = false
            override fun getActivePackage(): String? = null
            override fun performHome(): Boolean = false
            override fun performBack(): Boolean = false
            override fun performClickOnText(text: String): Boolean = false
            override fun performClickById(viewId: String): Boolean = false
            override fun performInputText(targetText: String?, text: String): Boolean = false
            override fun performClearText(targetText: String?, viewId: String?): Boolean = false
            override fun performScroll(forward: Boolean): Boolean = false
            override fun performScrollForward(viewId: String?): Boolean = false
            override fun performScrollBackward(viewId: String?): Boolean = false
            override fun readScreenContent(): List<String> = emptyList()
            override fun openAccessibilitySettings() {}
            override fun getServiceState(): AccessibilityServiceState = AccessibilityServiceState.DISABLED
            override fun getCurrentForegroundApp(): ForegroundAppInfo? = null
            override fun captureScreenSnapshot(filterSensitive: Boolean): ScreenSnapshotResult =
                ScreenSnapshotResult.Error("DISABLED", "Service disabled")
            override suspend fun performTapCoordinates(x: Float, y: Float): Boolean = false
            override suspend fun performSwipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long): Boolean = false
            override suspend fun performLongPress(x: Float, y: Float, durationMs: Long): Boolean = false
        }

        val inspector = UIHierarchyInspector()
        val appRegistry = InstalledAppRegistry(context)
        val autoRegistry = AppAutomationRegistry()

        val engine = AutomationDiagnosticsEngine(disabledController, inspector, appRegistry, autoRegistry)
        val result = engine.runDiagnostics()

        assertFalse("Disabled accessibility must NOT report PASS", result.status == DiagnosticStatus.PASS)
        assertTrue("Disabled accessibility must report NOT_CONFIGURED or FAIL", 
            result.status == DiagnosticStatus.NOT_CONFIGURED || result.status == DiagnosticStatus.FAIL)
    }

    @Test
    fun testOrdinaryNonSensitiveAssistantFunctionalityRemainsAvailable() {
        // Ordinary assistant actions: Home, Back, Wait, Read Screen, general search
        val harmlessActions = listOf(
            Action(type = ActionType.HOME, description = "Go home"),
            Action(type = ActionType.BACK, description = "Go back"),
            Action(type = ActionType.WAIT, parameters = ActionParameters(durationMs = 500), description = "Wait"),
            Action(type = ActionType.READ_SCREEN, description = "Read screen"),
            Action(type = ActionType.SEARCH, parameters = ActionParameters(query = "Weather in Tokyo"), description = "Search web"),
            Action(type = ActionType.OPEN_APP, parameters = ActionParameters(appName = "YouTube"), description = "Open YouTube")
        )

        for (action in harmlessActions) {
            val level = SecurityPolicyEngine.evaluateRequiredLevel(action)
            assertTrue("Harmless action must not require Level 2 Device Credential", level != AuthenticationLevel.LEVEL_2_DEVICE_CREDENTIAL)

            val decision = OwnerCommandPolicy.evaluate(
                action = action,
                isVoiceEnrolled = false,
                isVoiceAuthEnabled = false,
                verificationResult = null,
                isDeviceAuthenticated = false
            )

            assertTrue("Non-sensitive assistant action must be executable without forcing biometrics", decision.isAllowedToExecute)
            assertFalse("Non-sensitive assistant action must not require biometric authentication", decision.requiresBiometric)
        }
    }

    @Test
    fun testSecurityEventLoggerInMemory() {
        val logger = SecurityEventLogger(securityEventDao = null)
        logger.logEvent(
            eventType = SecurityEventType.OWNER_ENROLLMENT_SUCCESS,
            description = "Enrolled 4 voice samples",
            outcome = "SUCCESS"
        )

        val events = logger.recentEvents.value
        assertEquals(1, events.size)
        assertEquals("OWNER_ENROLLMENT_SUCCESS", events[0].eventType)
        assertEquals("SUCCESS", events[0].outcome)

        logger.clearHistory()
        assertTrue(logger.recentEvents.value.isEmpty())
    }
}
