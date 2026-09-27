package com.example.automation.appcontrol

import android.content.Context
import com.example.accessibility.AccessibilityController
import com.example.automation.Action
import com.example.automation.ActionResult
import com.example.automation.agent.AgentTaskContext

data class ProviderExecutionContext(
    val context: Context,
    val accessibilityController: AccessibilityController,
    val appLauncher: AppLauncher,
    val taskId: String
)

interface AppAutomationProvider {
    val targetPackage: String
    val providerName: String
    val supportedCapabilities: Set<AppCapability>

    /**
     * Determines whether this provider can handle the given action within the current task context.
     */
    fun canHandle(action: Action, context: AgentTaskContext?): Boolean

    /**
     * Executes the action using app-specific verified mechanisms (intents, semantic locators, deep links).
     */
    suspend fun executeAction(
        action: Action,
        context: AgentTaskContext?,
        executionContext: ProviderExecutionContext
    ): ActionResult
}

class AppAutomationRegistry {
    private val providers = mutableMapOf<String, AppAutomationProvider>()

    fun register(provider: AppAutomationProvider) {
        providers[provider.targetPackage] = provider
    }

    fun unregister(packageName: String) {
        providers.remove(packageName)
    }

    fun getProvider(packageName: String): AppAutomationProvider? {
        return providers[packageName]
    }

    fun getAllProviders(): List<AppAutomationProvider> {
        return providers.values.toList()
    }

    fun findProviderForAction(action: Action, context: AgentTaskContext?): AppAutomationProvider? {
        // First check explicit package in parameters
        val explicitPkg = action.parameters.packageName
        if (!explicitPkg.isNullOrBlank()) {
            val provider = providers[explicitPkg]
            if (provider != null && provider.canHandle(action, context)) {
                return provider
            }
        }

        // Then check context active foreground package
        val activePkg = context?.currentPackage
        if (!activePkg.isNullOrBlank()) {
            val provider = providers[activePkg]
            if (provider != null && provider.canHandle(action, context)) {
                return provider
            }
        }

        // Check if any provider claims this action
        return providers.values.firstOrNull { it.canHandle(action, context) }
    }
}
