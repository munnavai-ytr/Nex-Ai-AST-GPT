package com.example

import android.app.Application
import com.example.accessibility.AccessibilityController
import com.example.accessibility.AndroidAccessibilityController
import com.example.accessibility.inspector.UIHierarchyInspector
import com.example.ai.GeminiConfig
import com.example.ai.GeminiEngine
import com.example.automation.ActionExecutor
import com.example.automation.ActionValidator
import com.example.automation.CommandProcessor
import com.example.automation.ExecutionLogger
import com.example.automation.TaskPlanner
import com.example.automation.engine.DeviceActionExecutor
import com.example.automation.engine.DeviceAutomationManager
import com.example.automation.logging.AutomationExecutionLogger
import com.example.automation.permissions.AutomationPermissionManager
import com.example.automation.state.ScreenStateManager
import com.example.automation.verifier.AutomationResultVerifier
import com.example.core.TaskStateManager
import com.example.memory.MemoryManager
import com.example.memory.MemoryRepository
import com.example.memory.NexDatabase
import com.example.permissions.PermissionManager
import com.example.security.SecurityManager
import com.example.voice.VoiceController
import com.example.voice.VoicePermissionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NexApplication : Application() {

    lateinit var database: NexDatabase
        private set

    lateinit var memoryRepository: MemoryRepository
        private set

    lateinit var memoryManager: MemoryManager
        private set

    lateinit var permissionManager: PermissionManager
        private set

    lateinit var voicePermissionManager: VoicePermissionManager
        private set

    lateinit var accessibilityStatusManager: com.example.accessibility.service.AccessibilityStatusManager
        private set

    lateinit var currentScreenProvider: com.example.accessibility.provider.CurrentScreenProvider
        private set

    lateinit var uiHierarchyInspector: UIHierarchyInspector
        private set

    lateinit var screenStateManager: ScreenStateManager
        private set

    lateinit var automationPermissionManager: AutomationPermissionManager
        private set

    lateinit var automationResultVerifier: AutomationResultVerifier
        private set

    lateinit var automationExecutionLogger: AutomationExecutionLogger
        private set

    lateinit var deviceActionExecutor: DeviceActionExecutor
        private set

    lateinit var deviceAutomationManager: DeviceAutomationManager
        private set

    lateinit var accessibilityController: AccessibilityController
        private set

    lateinit var executionLogger: ExecutionLogger
        private set

    lateinit var securityManager: SecurityManager
        private set

    lateinit var geminiConfig: GeminiConfig
        private set

    lateinit var geminiEngine: GeminiEngine
        private set

    lateinit var actionValidator: ActionValidator
        private set

    lateinit var installedAppRegistry: com.example.automation.appcontrol.InstalledAppRegistry
        private set

    lateinit var appResolver: com.example.automation.appcontrol.AppResolver
        private set

    lateinit var appLauncher: com.example.automation.appcontrol.AppLauncher
        private set

    lateinit var appAutomationRegistry: com.example.automation.appcontrol.AppAutomationRegistry
        private set

    lateinit var actionExecutor: ActionExecutor
        private set

    lateinit var taskStateManager: TaskStateManager
        private set

    lateinit var confirmationManager: com.example.automation.confirmation.ConfirmationManager
        private set

    lateinit var actionQueue: com.example.automation.queue.ActionQueue
        private set

    lateinit var verificationEngine: com.example.automation.agent.VerificationEngine
        private set

    lateinit var agentOrchestrator: com.example.automation.agent.AgentOrchestrator
        private set

    lateinit var taskPlanner: TaskPlanner
        private set

    lateinit var commandProcessor: CommandProcessor
        private set

    lateinit var voiceController: VoiceController
        private set

    lateinit var diagnosticsCoordinator: com.example.diagnostics.DiagnosticsCoordinator
        private set

    companion object {
        lateinit var instance: NexApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 1. Storage & Persistence
        database = NexDatabase.getInstance(this)
        memoryRepository = MemoryRepository(
            structuredMemoryDao = database.structuredMemoryDao(),
            workflowDao = database.workflowDao(),
            historyDao = database.memoryHistoryDao(),
            memoryDao = database.memoryDao(),
            taskDao = database.taskDao()
        )
        geminiConfig = GeminiConfig(this)
        memoryManager = MemoryManager(memoryRepository, geminiConfig)

        // 2. System Perception & Permissions
        permissionManager = PermissionManager(this)
        voicePermissionManager = VoicePermissionManager(this)
        automationPermissionManager = AutomationPermissionManager(this)
        uiHierarchyInspector = UIHierarchyInspector()
        screenStateManager = ScreenStateManager(this, uiHierarchyInspector)

        accessibilityStatusManager = com.example.accessibility.service.AccessibilityStatusManager(this)
        currentScreenProvider = com.example.accessibility.provider.CurrentScreenProvider(this, accessibilityStatusManager)
        com.example.accessibility.NexAccessibilityService.statusManager = accessibilityStatusManager
        com.example.accessibility.NexAccessibilityService.screenStateManager = screenStateManager

        accessibilityController = AndroidAccessibilityController(
            context = this,
            permissionManager = permissionManager,
            statusManager = accessibilityStatusManager,
            screenProvider = currentScreenProvider
        )
        securityManager = SecurityManager(this, securityEventDao = database.securityEventDao())

        // 3. AI Planning & Automation Engine Components
        geminiConfig = GeminiConfig(this)
        geminiEngine = GeminiEngine(geminiConfig)
        executionLogger = ExecutionLogger(memoryRepository)
        automationExecutionLogger = AutomationExecutionLogger(memoryRepository)
        automationResultVerifier = AutomationResultVerifier()
        confirmationManager = com.example.automation.confirmation.ConfirmationManager()
        actionValidator = ActionValidator(accessibilityController, permissionManager, confirmationManager)

        // Real App Control & Integration Layer
        installedAppRegistry = com.example.automation.appcontrol.InstalledAppRegistry(this)
        appResolver = com.example.automation.appcontrol.AppResolver(installedAppRegistry, memoryManager)
        appLauncher = com.example.automation.appcontrol.AppLauncher(this, accessibilityController)
        appAutomationRegistry = com.example.automation.appcontrol.AppAutomationRegistry().apply {
            register(com.example.automation.appcontrol.providers.YouTubeAutomationProvider())
            register(com.example.automation.appcontrol.providers.ChromeAutomationProvider())
            register(com.example.automation.appcontrol.providers.SettingsAutomationProvider())
        }

        deviceActionExecutor = DeviceActionExecutor(
            context = this,
            accessibilityController = accessibilityController,
            uiInspector = uiHierarchyInspector,
            appResolver = appResolver,
            appLauncher = appLauncher,
            appAutomationRegistry = appAutomationRegistry
        )

        actionExecutor = ActionExecutor(
            context = this,
            accessibilityController = accessibilityController,
            executionLogger = executionLogger,
            appRegistry = installedAppRegistry,
            appResolver = appResolver,
            appLauncher = appLauncher,
            appAutomationRegistry = appAutomationRegistry
        )
        taskStateManager = TaskStateManager()
        taskPlanner = TaskPlanner(geminiEngine, memoryManager, actionValidator, accessibilityController)
        actionQueue = com.example.automation.queue.ActionQueue(
            actionValidator = actionValidator,
            actionExecutor = actionExecutor,
            taskStateManager = taskStateManager,
            confirmationManager = confirmationManager,
            memoryRepository = memoryRepository
        )
        verificationEngine = com.example.automation.agent.VerificationEngine(accessibilityController)
        agentOrchestrator = com.example.automation.agent.AgentOrchestrator(
            accessibilityController = accessibilityController,
            taskPlanner = taskPlanner,
            actionValidator = actionValidator,
            actionExecutor = actionExecutor,
            verificationEngine = verificationEngine,
            confirmationManager = confirmationManager,
            taskStateManager = taskStateManager,
            memoryRepository = memoryRepository,
            geminiEngine = geminiEngine,
            appResolver = appResolver,
            appAutomationRegistry = appAutomationRegistry,
            memoryManager = memoryManager
        )

        deviceAutomationManager = DeviceAutomationManager(
            context = this,
            accessibilityController = accessibilityController,
            uiInspector = uiHierarchyInspector,
            screenStateManager = screenStateManager,
            deviceActionExecutor = deviceActionExecutor,
            actionValidator = actionValidator,
            permissionManager = automationPermissionManager,
            resultVerifier = automationResultVerifier,
            executionLogger = automationExecutionLogger,
            taskPlanner = taskPlanner,
            agentOrchestrator = agentOrchestrator
        )

        commandProcessor = CommandProcessor(
            taskPlanner = taskPlanner,
            actionExecutor = actionExecutor,
            taskStateManager = taskStateManager,
            securityManager = securityManager,
            memoryRepository = memoryRepository,
            confirmationManager = confirmationManager,
            actionQueue = actionQueue,
            agentOrchestrator = agentOrchestrator
        )

        // 4. Real Voice Perception & Response
        voiceController = VoiceController(
            context = this,
            permissionManager = voicePermissionManager,
            onCommandReceived = { transcript ->
                commandProcessor.processCommand(transcript)
            }
        )

        // Wire CommandProcessor responses directly to VoiceController TTS & StateManager
        commandProcessor.onResponseGeneratedListener = { response ->
            voiceController.onResponseGenerated(response)
        }

        // 5. Deep Testing, Diagnostics & Release Readiness Coordinator
        diagnosticsCoordinator = com.example.diagnostics.DiagnosticsCoordinator(this)

        // Asynchronously discover installed apps on launch
        CoroutineScope(Dispatchers.IO).launch {
            try {
                installedAppRegistry.refresh()
            } catch (_: Exception) {}
        }
    }
}
