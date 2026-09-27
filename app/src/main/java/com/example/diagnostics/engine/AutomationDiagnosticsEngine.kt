package com.example.diagnostics.engine

import com.example.accessibility.AccessibilityController
import com.example.accessibility.inspector.UIHierarchyInspector
import com.example.accessibility.model.ScreenSnapshotResult
import com.example.automation.appcontrol.AppAutomationRegistry
import com.example.automation.appcontrol.InstalledAppRegistry
import com.example.diagnostics.model.DiagnosticCategory
import com.example.diagnostics.model.DiagnosticItem
import com.example.diagnostics.model.DiagnosticStatus
import com.example.diagnostics.model.SubsystemDiagnosticResult

class AutomationDiagnosticsEngine(
    private val accessibilityController: AccessibilityController,
    private val uiInspector: UIHierarchyInspector,
    private val installedAppRegistry: InstalledAppRegistry,
    private val appAutomationRegistry: AppAutomationRegistry
) {

    fun runDiagnostics(): SubsystemDiagnosticResult {
        val startTime = System.currentTimeMillis()
        val items = mutableListOf<DiagnosticItem>()

        // 1. Accessibility Service Connectivity
        val isA11yActive = accessibilityController.isAccessibilityActive()
        val serviceState = accessibilityController.getServiceState()
        items.add(
            DiagnosticItem(
                id = "auto_a11y_service",
                title = "Accessibility Service State",
                category = DiagnosticCategory.AUTOMATION,
                status = if (isA11yActive) DiagnosticStatus.PASS else DiagnosticStatus.NOT_CONFIGURED,
                summary = "Service State: $serviceState (${if (isA11yActive) "Active" else "Disabled in Android Settings"})",
                details = "Package: com.example\nClass: com.example.accessibility.NexAccessibilityService\nActive: $isA11yActive",
                actionLabel = if (!isA11yActive) "Enable in Settings" else null,
                isActionable = !isA11yActive
            )
        )

        // 2. Foreground Screen & Perception Engine
        val currentApp = accessibilityController.getCurrentForegroundApp()
        val snapshot = accessibilityController.captureScreenSnapshot(filterSensitive = true)

        val (snapshotStatus, snapshotSummary, snapshotDetails) = when {
            !isA11yActive -> {
                Triple(
                    DiagnosticStatus.NOT_CONFIGURED,
                    "Screen Perception Unavailable (Accessibility Service Disabled)",
                    "Screen inspection requires the Accessibility Service to be granted in Android Settings."
                )
            }
            snapshot is ScreenSnapshotResult.Success -> {
                val elementCount = snapshot.snapshot.elementCount
                if (elementCount > 0) {
                    Triple(
                        DiagnosticStatus.PASS,
                        "Perceived $elementCount UI elements on ${currentApp?.displayName ?: "screen"}",
                        "Foreground Package: ${currentApp?.packageName ?: "Unknown"}\nElement Count: $elementCount\nInteractive Nodes: ${snapshot.snapshot.interactiveElementCount}"
                    )
                } else {
                    Triple(
                        DiagnosticStatus.NOT_VERIFIED,
                        "Empty Screen Content Perceived (0 nodes)",
                        "Accessibility snapshot succeeded but returned zero perceived hierarchy nodes."
                    )
                }
            }
            snapshot is ScreenSnapshotResult.Error -> {
                Triple(
                    DiagnosticStatus.FAIL,
                    "Screen Inspection Error: ${snapshot.message}",
                    "Code: ${snapshot.errorCode}\nReason: ${snapshot.message}"
                )
            }
            else -> {
                Triple(
                    DiagnosticStatus.NOT_VERIFIED,
                    "Snapshot Inconclusive",
                    "Snapshot returned status: ${snapshot::class.simpleName}"
                )
            }
        }

        items.add(
            DiagnosticItem(
                id = "auto_screen_perception",
                title = "Screen Perception & UI Hierarchy Inspector",
                category = DiagnosticCategory.AUTOMATION,
                status = snapshotStatus,
                summary = snapshotSummary,
                details = snapshotDetails,
                actionLabel = if (!isA11yActive) "Open Settings" else null,
                isActionable = !isA11yActive
            )
        )

        // 3. Installed App Registry & Resolver
        val appCount = installedAppRegistry.getInstalledApps().size
        items.add(
            DiagnosticItem(
                id = "auto_app_registry",
                title = "Installed Applications & Voice Aliases",
                category = DiagnosticCategory.AUTOMATION,
                status = if (appCount > 0) DiagnosticStatus.PASS else DiagnosticStatus.NOT_CONFIGURED,
                summary = if (appCount > 0) "$appCount system & user apps indexed" else "No applications indexed",
                details = "Indexed Apps: $appCount\nVoice aliases: YouTube, Chrome, Settings, Gmail, WhatsApp, Camera, Maps"
            )
        )

        // 4. App Automation Providers
        val providers = appAutomationRegistry.getAllProviders()
        val providerNames = providers.joinToString(", ") { it.providerName }
        items.add(
            DiagnosticItem(
                id = "auto_providers",
                title = "Modular Automation Providers",
                category = DiagnosticCategory.AUTOMATION,
                status = if (providers.isNotEmpty()) DiagnosticStatus.PASS else DiagnosticStatus.NOT_CONFIGURED,
                summary = "${providers.size} Specialized Providers Registered",
                details = "Active Providers: $providerNames\nCapabilities: Media search & play, Web URL navigation, Settings toggles"
            )
        )

        val overallStatus = when {
            items.any { it.status == DiagnosticStatus.FAIL } -> DiagnosticStatus.FAIL
            items.any { it.status == DiagnosticStatus.NOT_CONFIGURED } -> DiagnosticStatus.NOT_CONFIGURED
            items.any { it.status == DiagnosticStatus.NOT_VERIFIED } -> DiagnosticStatus.WARN
            items.any { it.status == DiagnosticStatus.WARN } -> DiagnosticStatus.WARN
            else -> DiagnosticStatus.PASS
        }

        return SubsystemDiagnosticResult(
            category = DiagnosticCategory.AUTOMATION,
            items = items,
            status = overallStatus,
            executionTimeMs = System.currentTimeMillis() - startTime
        )
    }
}
