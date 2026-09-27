package com.example.diagnostics.suite

import com.example.diagnostics.model.CujTestCase
import com.example.diagnostics.model.CujTestStatus
import com.example.diagnostics.model.DiagnosticCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class InteractiveCujSuite {

    private val _testCases = MutableStateFlow<List<CujTestCase>>(getDefaultTestCases())
    val testCases: StateFlow<List<CujTestCase>> = _testCases.asStateFlow()

    fun updateTestStatus(testId: String, status: CujTestStatus, notes: String = "") {
        _testCases.value = _testCases.value.map { test ->
            if (test.id == testId) {
                test.copy(
                    status = status,
                    notes = notes,
                    executedAtTimestamp = System.currentTimeMillis()
                )
            } else {
                test
            }
        }
    }

    fun resetAllTests() {
        _testCases.value = getDefaultTestCases()
    }

    companion object {
        fun getDefaultTestCases(): List<CujTestCase> {
            return listOf(
                CujTestCase(
                    id = "cuj_01_voice_interaction",
                    title = "CUJ 1: Multilingual Voice Command Perception & TTS",
                    category = DiagnosticCategory.VOICE,
                    description = "Verify real-time microphone acoustic capture, speech recognition, and multilingual Bengali/English TTS feedback.",
                    prerequisites = listOf("Microphone Permission Granted", "Device Media Volume > 30%"),
                    steps = listOf(
                        "1. Navigate to the Home screen.",
                        "2. Tap the central glowing Orb.",
                        "3. Speak clearly: 'Hello NEX' or 'হ্যালো নেক্স'.",
                        "4. Confirm the live transcription appears in the subtitle text.",
                        "5. Confirm NEX speaks back with audible voice feedback."
                    ),
                    expectedOutcome = "Orb reacts to voice RMS audio levels, captures text transcription accurately, and provides clear spoken feedback."
                ),
                CujTestCase(
                    id = "cuj_02_owner_voice_verification",
                    title = "CUJ 2: Owner Acoustic Profile Verification",
                    category = DiagnosticCategory.OWNER_AUTH,
                    description = "Verify that the owner's enrolled voice is analyzed via MFCC/DSP features and compared against the enrolled acoustic profile.",
                    prerequisites = listOf("Owner Voice Enrolled in Settings > Voice Enrollment", "Microphone Active"),
                    steps = listOf(
                        "1. Complete the 4-phrase voice enrollment in Voice Enrollment screen if not done.",
                        "2. Speak a command into NEX.",
                        "3. Observe the Security screen recent audit log for the calculated acoustic similarity score.",
                        "4. Have a different person speak a command to verify unknown speaker score differentiation."
                    ),
                    expectedOutcome = "Owner voice scores above match threshold (> 0.80), while unknown speaker or distorted audio is flagged."
                ),
                CujTestCase(
                    id = "cuj_03_youtube_automation",
                    title = "CUJ 3: YouTube Search & Media Automation",
                    category = DiagnosticCategory.AUTOMATION,
                    description = "Verify autonomous multi-step intent execution: launch YouTube, focus search, enter query, and verify page transition.",
                    prerequisites = listOf("YouTube installed on device", "Accessibility Service enabled in Android Settings"),
                    steps = listOf(
                        "1. Ensure Accessibility Service is enabled in Settings > Accessibility.",
                        "2. Give voice command: 'Open YouTube and search for Android development'.",
                        "3. Watch NEX autonomously switch to YouTube, tap Search, type query, and submit.",
                        "4. Return to NEX and verify Task History marked as COMPLETED."
                    ),
                    expectedOutcome = "NEX smoothly orchestrates app launch, element perception, input submission, and step verification without user touch."
                ),
                CujTestCase(
                    id = "cuj_04_chrome_navigation",
                    title = "CUJ 4: Chrome Browser Navigation & Web Intent",
                    category = DiagnosticCategory.AUTOMATION,
                    description = "Verify Chrome automation provider and URL intent dispatching.",
                    prerequisites = listOf("Google Chrome installed", "Accessibility Service active"),
                    steps = listOf(
                        "1. Command NEX: 'Open Chrome and visit android.com'.",
                        "2. Verify Chrome opens with the target web page loaded.",
                        "3. Check NEX execution log for URL intent resolution."
                    ),
                    expectedOutcome = "Chrome is launched directly with the target URL via AppResolver."
                ),
                CujTestCase(
                    id = "cuj_05_sensitive_action_safeguard",
                    title = "CUJ 5: Sensitive Action Interception & Biometrics",
                    category = DiagnosticCategory.SECURITY,
                    description = "Verify zero-trust security policy intercepts destructive/high-risk commands and mandates device authentication.",
                    prerequisites = listOf("Screen Lock (PIN/Pattern/Fingerprint) set on device"),
                    steps = listOf(
                        "1. Command NEX: 'Delete all my personal memory facts'.",
                        "2. Observe NEX intercepting the action and displaying a high-risk confirmation modal.",
                        "3. Confirm with Device Biometrics or PIN prompt to authorize.",
                        "4. Verify action executes only upon successful biometric/PIN verification."
                    ),
                    expectedOutcome = "High-risk command is blocked from autonomous execution until explicit device authentication is confirmed."
                ),
                CujTestCase(
                    id = "cuj_06_memory_fact_learning",
                    title = "CUJ 6: Structured Fact Learning & Semantic Recall",
                    category = DiagnosticCategory.MEMORY_DB,
                    description = "Verify local Room SQLite entity storage, vector embedding generation, and semantic query recall.",
                    prerequisites = listOf("Database active"),
                    steps = listOf(
                        "1. Command NEX: 'Remember that my flight number is AA 100'.",
                        "2. Open the Memory tab and verify the new entry in Structured Memories.",
                        "3. Ask NEX: 'What is my flight number?'.",
                        "4. Verify NEX retrieves the context from SQLite and responds with 'AA 100'."
                    ),
                    expectedOutcome = "Memory is stored persistently on-device and retrieved instantaneously in subsequent user turns."
                ),
                CujTestCase(
                    id = "cuj_07_foreground_service_persistence",
                    title = "CUJ 7: Persistent Execution in Background & Doze",
                    category = DiagnosticCategory.SERVICES,
                    description = "Verify Android Foreground Service lifecycle and status notification.",
                    prerequisites = listOf("Notification permission granted"),
                    steps = listOf(
                        "1. Go to Settings > Toggle 'Foreground Service' ON.",
                        "2. Pull down Android notification shade to see 'NEX Active' notification.",
                        "3. Background the app or lock device screen.",
                        "4. Reopen app and confirm unbroken state."
                    ),
                    expectedOutcome = "Foreground service remains resident with active notification channel and zero crashes."
                ),
                CujTestCase(
                    id = "cuj_08_gemini_reasoning_and_fallback",
                    title = "CUJ 8: Gemini Multi-Step Planning & Fallback",
                    category = DiagnosticCategory.GEMINI,
                    description = "Verify online Gemini 2.5 Flash structured action plan synthesis and offline fallback when disconnected.",
                    prerequisites = listOf("Internet connection or Offline mode tested"),
                    steps = listOf(
                        "1. Give a complex instruction: 'Open Settings, then go to Wi-Fi settings'.",
                        "2. Observe Gemini Engine synthesizing structured ActionPlan JSON with step dependencies.",
                        "3. Turn on Airplane mode and repeat command to verify offline heuristic planner handles the request."
                    ),
                    expectedOutcome = "Online mode generates rich JSON action schemas; offline mode falls back gracefully without hanging."
                )
            )
        }
    }
}
