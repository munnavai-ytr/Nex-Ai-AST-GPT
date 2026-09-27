package com.example.diagnostics.model

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.NexAmber
import com.example.ui.theme.NexCyanLight
import com.example.ui.theme.NexEmerald
import com.example.ui.theme.NexIndigoLight
import com.example.ui.theme.NexRose
import com.example.ui.theme.NexTextMuted

enum class DiagnosticStatus {
    NOT_TESTED,
    RUNNING,
    PASS,
    WARN,
    FAIL,
    INFO,
    NOT_CONFIGURED,
    NOT_VERIFIED,
    PERMISSION_REQUIRED;

    val displayLabel: String
        get() = when (this) {
            NOT_TESTED -> "NOT TESTED"
            RUNNING -> "RUNNING..."
            PASS -> "PASSED"
            WARN -> "WARNING"
            FAIL -> "FAILED"
            INFO -> "INFO"
            NOT_CONFIGURED -> "NOT CONFIGURED"
            NOT_VERIFIED -> "NOT VERIFIED"
            PERMISSION_REQUIRED -> "PERMISSION REQUIRED"
        }

    val color: Color
        get() = when (this) {
            NOT_TESTED -> NexTextMuted
            RUNNING -> NexIndigoLight
            PASS -> NexEmerald
            WARN -> NexAmber
            FAIL -> NexRose
            INFO -> NexCyanLight
            NOT_CONFIGURED -> NexAmber
            NOT_VERIFIED -> NexAmber
            PERMISSION_REQUIRED -> NexRose
        }
}

enum class DiagnosticCategory(val title: String, val description: String) {
    SYSTEM("System & Hardware", "OS, SoC, memory, storage, battery, keystore"),
    PERMISSIONS("Permissions & Access", "Normal, runtime, and special access rights"),
    VOICE("Voice & Speech Engine", "Microphone, STT, TTS, Bengali & English synthesis"),
    OWNER_AUTH("Owner Voice & Biometrics", "Voice profile, acoustic confidence, PIN/Biometrics"),
    GEMINI("Gemini AI Engine", "API key, endpoint reachability, prompt comprehension"),
    AUTOMATION("Device Automation", "Accessibility service, UI hierarchy perception, safe actions"),
    TASK_ORCHESTRATOR("Task Orchestration", "Planning, multi-step execution, retries, cancel"),
    MEMORY_DB("Memory & Database", "Room DB integrity, CRUD speed, vector similarity search"),
    SERVICES("Background Services", "Foreground service, notifications, persistence"),
    SECURITY("Security & Privacy", "Sensitive redaction, policy engine, audit log"),
    PERFORMANCE("Performance & Latency", "Heap memory, sub-system latencies, execution profile")
}

data class DiagnosticItem(
    val id: String,
    val title: String,
    val category: DiagnosticCategory,
    val status: DiagnosticStatus = DiagnosticStatus.NOT_TESTED,
    val summary: String = "Ready for execution",
    val details: String? = null,
    val latencyMs: Long = 0L,
    val actionLabel: String? = null,
    val isActionable: Boolean = false
)

data class SubsystemDiagnosticResult(
    val category: DiagnosticCategory,
    val items: List<DiagnosticItem>,
    val status: DiagnosticStatus = DiagnosticStatus.NOT_TESTED,
    val executionTimeMs: Long = 0L
) {
    val passCount: Int get() = items.count { it.status == DiagnosticStatus.PASS }
    val warnCount: Int get() = items.count { it.status == DiagnosticStatus.WARN }
    val failCount: Int get() = items.count { it.status == DiagnosticStatus.FAIL }
    val notConfiguredCount: Int get() = items.count { it.status == DiagnosticStatus.NOT_CONFIGURED }
    val notVerifiedCount: Int get() = items.count { it.status == DiagnosticStatus.NOT_VERIFIED }
    val permissionRequiredCount: Int get() = items.count { it.status == DiagnosticStatus.PERMISSION_REQUIRED }
    val totalCount: Int get() = items.size
}

enum class CujTestStatus {
    UNTESTED,
    RUNNING,
    PASS,
    FAIL,
    SKIP
}

data class CujTestCase(
    val id: String,
    val title: String,
    val category: DiagnosticCategory,
    val description: String,
    val prerequisites: List<String> = emptyList(),
    val steps: List<String>,
    val expectedOutcome: String,
    val status: CujTestStatus = CujTestStatus.UNTESTED,
    val notes: String = "",
    val executedAtTimestamp: Long? = null
)

data class ReadinessAuditReport(
    val timestamp: Long = System.currentTimeMillis(),
    val deviceModel: String,
    val manufacturer: String,
    val androidVersion: String,
    val sdkInt: Int,
    val appVersion: String,
    val readinessScore: Int, // 0 - 100
    val readinessGrade: String, // A+, A, B, C, F
    val readinessSummary: String,
    val subsystemResults: List<SubsystemDiagnosticResult>,
    val cujResults: List<CujTestCase>,
    val recommendations: List<String>
)
