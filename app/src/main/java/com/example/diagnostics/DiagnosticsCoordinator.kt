package com.example.diagnostics

import android.content.Context
import com.example.NexApplication
import com.example.diagnostics.engine.AutomationDiagnosticsEngine
import com.example.diagnostics.engine.GeminiDiagnosticsEngine
import com.example.diagnostics.engine.MemoryDbDiagnosticsEngine
import com.example.diagnostics.engine.OwnerAuthDiagnosticsEngine
import com.example.diagnostics.engine.PerformanceProfiler
import com.example.diagnostics.engine.PermissionDiagnosticsEngine
import com.example.diagnostics.engine.SecurityPrivacyDiagnosticsEngine
import com.example.diagnostics.engine.ServiceDiagnosticsEngine
import com.example.diagnostics.engine.SystemDiagnosticsEngine
import com.example.diagnostics.engine.TaskOrchestratorDiagnosticsEngine
import com.example.diagnostics.engine.VoiceDiagnosticsEngine
import com.example.diagnostics.model.DiagnosticCategory
import com.example.diagnostics.model.ReadinessAuditReport
import com.example.diagnostics.model.SubsystemDiagnosticResult
import com.example.diagnostics.report.DiagnosticReportExporter
import com.example.diagnostics.suite.InteractiveCujSuite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class DiagnosticsCoordinator(private val app: NexApplication) {

    val systemEngine = SystemDiagnosticsEngine(app)
    val permissionEngine = PermissionDiagnosticsEngine(app, app.permissionManager, app.accessibilityController)
    val voiceEngine = VoiceDiagnosticsEngine(app, app.voiceController)
    val ownerAuthEngine = OwnerAuthDiagnosticsEngine(app, app.securityManager)
    val geminiEngine = GeminiDiagnosticsEngine(app.geminiConfig, app.geminiEngine)
    val automationEngine = AutomationDiagnosticsEngine(
        app.accessibilityController,
        app.uiHierarchyInspector,
        app.installedAppRegistry,
        app.appAutomationRegistry
    )
    val taskEngine = TaskOrchestratorDiagnosticsEngine(app.agentOrchestrator, app.verificationEngine)
    val memoryDbEngine = MemoryDbDiagnosticsEngine(app.database, app.memoryRepository, app.memoryManager)
    val serviceEngine = ServiceDiagnosticsEngine(app)
    val securityEngine = SecurityPrivacyDiagnosticsEngine(app.securityManager)
    val performanceProfiler = PerformanceProfiler()

    val cujSuite = InteractiveCujSuite()

    private val _subsystemResults = MutableStateFlow<Map<DiagnosticCategory, SubsystemDiagnosticResult>>(emptyMap())
    val subsystemResults: StateFlow<Map<DiagnosticCategory, SubsystemDiagnosticResult>> = _subsystemResults.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _latestReport = MutableStateFlow<ReadinessAuditReport?>(null)
    val latestReport: StateFlow<ReadinessAuditReport?> = _latestReport.asStateFlow()

    suspend fun runAllDiagnostics(): ReadinessAuditReport = withContext(Dispatchers.IO) {
        _isRunning.value = true
        val resultsMap = mutableMapOf<DiagnosticCategory, SubsystemDiagnosticResult>()

        try {
            // 1. System
            val sysRes = systemEngine.runDiagnostics()
            resultsMap[DiagnosticCategory.SYSTEM] = sysRes
            _subsystemResults.value = resultsMap.toMap()

            // 2. Permissions
            val permRes = permissionEngine.runDiagnostics()
            resultsMap[DiagnosticCategory.PERMISSIONS] = permRes
            _subsystemResults.value = resultsMap.toMap()

            // 3. Voice
            val voiceRes = voiceEngine.runDiagnostics()
            resultsMap[DiagnosticCategory.VOICE] = voiceRes
            _subsystemResults.value = resultsMap.toMap()

            // 4. Owner Auth
            val authRes = ownerAuthEngine.runDiagnostics()
            resultsMap[DiagnosticCategory.OWNER_AUTH] = authRes
            _subsystemResults.value = resultsMap.toMap()

            // 5. Gemini
            val geminiRes = geminiEngine.runDiagnostics()
            resultsMap[DiagnosticCategory.GEMINI] = geminiRes
            _subsystemResults.value = resultsMap.toMap()

            // 6. Automation
            val autoRes = automationEngine.runDiagnostics()
            resultsMap[DiagnosticCategory.AUTOMATION] = autoRes
            _subsystemResults.value = resultsMap.toMap()

            // 7. Tasks
            val taskRes = taskEngine.runDiagnostics()
            resultsMap[DiagnosticCategory.TASK_ORCHESTRATOR] = taskRes
            _subsystemResults.value = resultsMap.toMap()

            // 8. Memory & DB
            val memRes = memoryDbEngine.runDiagnostics()
            resultsMap[DiagnosticCategory.MEMORY_DB] = memRes
            _subsystemResults.value = resultsMap.toMap()

            // 9. Services
            val srvRes = serviceEngine.runDiagnostics()
            resultsMap[DiagnosticCategory.SERVICES] = srvRes
            _subsystemResults.value = resultsMap.toMap()

            // 10. Security
            val secRes = securityEngine.runDiagnostics()
            resultsMap[DiagnosticCategory.SECURITY] = secRes
            _subsystemResults.value = resultsMap.toMap()

            // 11. Performance
            val perfRes = performanceProfiler.runDiagnostics()
            resultsMap[DiagnosticCategory.PERFORMANCE] = perfRes
            _subsystemResults.value = resultsMap.toMap()

            val report = DiagnosticReportExporter.generateReport(
                subsystemResults = resultsMap.values.toList(),
                cujResults = cujSuite.testCases.value
            )
            _latestReport.value = report
            return@withContext report
        } finally {
            _isRunning.value = false
        }
    }

    suspend fun runSubsystem(category: DiagnosticCategory): SubsystemDiagnosticResult = withContext(Dispatchers.IO) {
        val res = when (category) {
            DiagnosticCategory.SYSTEM -> systemEngine.runDiagnostics()
            DiagnosticCategory.PERMISSIONS -> permissionEngine.runDiagnostics()
            DiagnosticCategory.VOICE -> voiceEngine.runDiagnostics()
            DiagnosticCategory.OWNER_AUTH -> ownerAuthEngine.runDiagnostics()
            DiagnosticCategory.GEMINI -> geminiEngine.runDiagnostics()
            DiagnosticCategory.AUTOMATION -> automationEngine.runDiagnostics()
            DiagnosticCategory.TASK_ORCHESTRATOR -> taskEngine.runDiagnostics()
            DiagnosticCategory.MEMORY_DB -> memoryDbEngine.runDiagnostics()
            DiagnosticCategory.SERVICES -> serviceEngine.runDiagnostics()
            DiagnosticCategory.SECURITY -> securityEngine.runDiagnostics()
            DiagnosticCategory.PERFORMANCE -> performanceProfiler.runDiagnostics()
        }
        val current = _subsystemResults.value.toMutableMap()
        current[category] = res
        _subsystemResults.value = current

        val report = DiagnosticReportExporter.generateReport(
            subsystemResults = current.values.toList(),
            cujResults = cujSuite.testCases.value
        )
        _latestReport.value = report
        res
    }

    fun compileCurrentReport(): ReadinessAuditReport {
        val report = DiagnosticReportExporter.generateReport(
            subsystemResults = _subsystemResults.value.values.toList(),
            cujResults = cujSuite.testCases.value
        )
        _latestReport.value = report
        return report
    }
}
