package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.accessibility.inspector.UIHierarchyInspector
import com.example.ai.GeminiConfig
import com.example.ai.GeminiEngine
import com.example.automation.agent.AgentOrchestrator
import com.example.automation.agent.VerificationEngine
import com.example.automation.appcontrol.AppAutomationRegistry
import com.example.automation.appcontrol.InstalledAppRegistry
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
import com.example.diagnostics.model.DiagnosticStatus
import com.example.memory.MemoryManager
import com.example.memory.MemoryRepository
import com.example.memory.NexDatabase
import com.example.permissions.PermissionManager
import com.example.security.SecurityManager
import com.example.voice.VoiceController
import com.example.voice.VoicePermissionManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DiagnosticsEnginesTest {

    private lateinit var context: Context
    private lateinit var database: NexDatabase
    private lateinit var memoryRepository: MemoryRepository
    private lateinit var memoryManager: MemoryManager
    private lateinit var permissionManager: PermissionManager
    private lateinit var securityManager: SecurityManager
    private lateinit var geminiConfig: GeminiConfig
    private lateinit var geminiEngine: GeminiEngine
    private lateinit var dummyAccessibilityController: DummyAccessibilityController

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = NexDatabase.getInstance(context)
        memoryRepository = MemoryRepository(
            structuredMemoryDao = database.structuredMemoryDao(),
            workflowDao = database.workflowDao(),
            historyDao = database.memoryHistoryDao(),
            memoryDao = database.memoryDao(),
            taskDao = database.taskDao()
        )
        geminiConfig = GeminiConfig(context)
        memoryManager = MemoryManager(memoryRepository, geminiConfig)
        permissionManager = PermissionManager(context)
        securityManager = SecurityManager(context, securityEventDao = database.securityEventDao())
        geminiEngine = GeminiEngine(geminiConfig)
        dummyAccessibilityController = DummyAccessibilityController()
    }

    @Test
    fun testSystemDiagnosticsEngine() {
        val engine = SystemDiagnosticsEngine(context)
        val result = engine.runDiagnostics()

        assertEquals(DiagnosticCategory.SYSTEM, result.category)
        assertTrue(result.items.isNotEmpty())
        assertNotNull(result.items.find { it.id == "sys_device_info" })
        assertNotNull(result.items.find { it.id == "sys_ram" })
        assertNotNull(result.items.find { it.id == "sys_storage" })
        assertNotNull(result.items.find { it.id == "sys_battery" })
        assertNotNull(result.items.find { it.id == "sys_network" })
        assertNotNull(result.items.find { it.id == "sys_keystore" })
    }

    @Test
    fun testPermissionDiagnosticsEngine() {
        val engine = PermissionDiagnosticsEngine(context, permissionManager, dummyAccessibilityController)
        val result = engine.runDiagnostics()

        assertEquals(DiagnosticCategory.PERMISSIONS, result.category)
        assertTrue(result.items.isNotEmpty())
        assertNotNull(result.items.find { it.id == "perm_mic" })
        assertNotNull(result.items.find { it.id == "perm_accessibility" })
        assertNotNull(result.items.find { it.id == "perm_notifications" })
        assertNotNull(result.items.find { it.id == "perm_battery_opt" })
    }

    @Test
    fun testVoiceDiagnosticsEngine() {
        val voiceController = VoiceController(context, VoicePermissionManager(context), onCommandReceived = {})
        val engine = VoiceDiagnosticsEngine(context, voiceController)
        val result = engine.runDiagnostics()

        assertEquals(DiagnosticCategory.VOICE, result.category)
        assertTrue(result.items.isNotEmpty())
        assertNotNull(result.items.find { it.id == "voice_audio_record" })
        assertNotNull(result.items.find { it.id == "voice_stt_engine" })
        assertNotNull(result.items.find { it.id == "voice_tts_engine" })
    }

    @Test
    fun testOwnerAuthDiagnosticsEngine() {
        val engine = OwnerAuthDiagnosticsEngine(context, securityManager)
        val result = engine.runDiagnostics()

        assertEquals(DiagnosticCategory.OWNER_AUTH, result.category)
        assertTrue(result.items.isNotEmpty())
        assertNotNull(result.items.find { it.id == "auth_voice_profile" })
        assertNotNull(result.items.find { it.id == "auth_speaker_model" })
        assertNotNull(result.items.find { it.id == "auth_policy_engine" })
    }

    @Test
    fun testGeminiDiagnosticsEngine() = runTest {
        val engine = GeminiDiagnosticsEngine(geminiConfig, geminiEngine)
        val result = engine.runDiagnostics()

        assertEquals(DiagnosticCategory.GEMINI, result.category)
        assertTrue(result.items.isNotEmpty())
        assertNotNull(result.items.find { it.id == "gemini_api_key" })
        assertNotNull(result.items.find { it.id == "gemini_model_spec" })
        assertNotNull(result.items.find { it.id == "gemini_multilingual" })
    }

    @Test
    fun testAutomationDiagnosticsEngine() {
        val uiInspector = UIHierarchyInspector()
        val appRegistry = InstalledAppRegistry(context)
        val appAutomationRegistry = AppAutomationRegistry()

        val engine = AutomationDiagnosticsEngine(
            dummyAccessibilityController,
            uiInspector,
            appRegistry,
            appAutomationRegistry
        )
        val result = engine.runDiagnostics()

        assertEquals(DiagnosticCategory.AUTOMATION, result.category)
        assertTrue(result.items.isNotEmpty())
        assertNotNull(result.items.find { it.id == "auto_a11y_service" })
        assertNotNull(result.items.find { it.id == "auto_screen_perception" })
        assertNotNull(result.items.find { it.id == "auto_app_registry" })
    }

    @Test
    fun testTaskOrchestratorDiagnosticsEngine() = runTest {
        val verificationEngine = VerificationEngine(dummyAccessibilityController)
        val actionValidator = com.example.automation.ActionValidator(
            dummyAccessibilityController,
            permissionManager,
            com.example.automation.confirmation.ConfirmationManager()
        )
        val actionExecutor = com.example.automation.ActionExecutor(
            context,
            dummyAccessibilityController,
            com.example.automation.ExecutionLogger(memoryRepository)
        )
        val taskPlanner = com.example.automation.TaskPlanner(geminiEngine, memoryManager, actionValidator, dummyAccessibilityController)

        val orchestrator = AgentOrchestrator(
            accessibilityController = dummyAccessibilityController,
            taskPlanner = taskPlanner,
            actionValidator = actionValidator,
            actionExecutor = actionExecutor,
            verificationEngine = verificationEngine,
            confirmationManager = com.example.automation.confirmation.ConfirmationManager(),
            taskStateManager = com.example.core.TaskStateManager(),
            memoryRepository = memoryRepository,
            geminiEngine = geminiEngine,
            coroutineScope = this
        )

        val engine = TaskOrchestratorDiagnosticsEngine(orchestrator, verificationEngine)
        val result = engine.runDiagnostics()

        assertEquals(DiagnosticCategory.TASK_ORCHESTRATOR, result.category)
        assertTrue(result.items.isNotEmpty())
        assertNotNull(result.items.find { it.id == "task_schema_validator" })
        assertNotNull(result.items.find { it.id == "task_loop_detector" })
        assertNotNull(result.items.find { it.id == "task_retry_budget" })
    }

    @Test
    fun testMemoryDbDiagnosticsEngine() = runTest {
        val engine = MemoryDbDiagnosticsEngine(database, memoryRepository, memoryManager)
        val result = engine.runDiagnostics()

        assertEquals(DiagnosticCategory.MEMORY_DB, result.category)
        assertTrue(result.items.isNotEmpty())
        assertNotNull(result.items.find { it.id == "mem_db_integrity" })
        assertNotNull(result.items.find { it.id == "mem_entity_counts" })
        assertNotNull(result.items.find { it.id == "mem_vector_search" })
    }

    @Test
    fun testServiceAndSecurityDiagnostics() {
        val srvEngine = ServiceDiagnosticsEngine(context)
        val srvResult = srvEngine.runDiagnostics()
        assertEquals(DiagnosticCategory.SERVICES, srvResult.category)
        assertTrue(srvResult.items.isNotEmpty())

        val secEngine = SecurityPrivacyDiagnosticsEngine(securityManager)
        val secResult = secEngine.runDiagnostics()
        assertEquals(DiagnosticCategory.SECURITY, secResult.category)
        assertTrue(secResult.items.isNotEmpty())

        val perfEngine = PerformanceProfiler()
        val perfResult = perfEngine.runDiagnostics()
        assertEquals(DiagnosticCategory.PERFORMANCE, perfResult.category)
        assertTrue(perfResult.items.isNotEmpty())
    }
}
