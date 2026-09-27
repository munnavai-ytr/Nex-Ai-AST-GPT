package com.example.automation.confirmation

import com.example.automation.Action
import com.example.automation.ActionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

enum class SensitiveCategory(val displayName: String, val description: String) {
    MESSAGING("Sending Messages", "Action sends SMS or external communications"),
    FILE_DELETION("File Deletion", "Action modifies or permanently deletes device files"),
    PURCHASE("Purchases & Payments", "Action authorizes purchase or payment transaction"),
    SECURITY_CHANGE("Security Settings", "Action modifies device security, PIN, or credentials"),
    APP_MANAGEMENT("Install / Uninstall App", "Action installs, updates, or uninstalls applications"),
    FACTORY_RESET("Factory Reset / Wipe", "Action initiates device wipe or factory reset"),
    FINANCIAL("Financial Transactions", "Action accesses banking, funds, or wallet services")
}

data class ConfirmationRequest(
    val requestId: String = UUID.randomUUID().toString(),
    val action: Action,
    val category: SensitiveCategory,
    val prompt: String,
    val timestamp: Long = System.currentTimeMillis()
)

class ConfirmationManager {

    private val _pendingConfirmation = MutableStateFlow<ConfirmationRequest?>(null)
    val pendingConfirmation: StateFlow<ConfirmationRequest?> = _pendingConfirmation.asStateFlow()

    private val pendingCompletions = mutableMapOf<String, (Boolean) -> Unit>()

    fun isActionSensitive(action: Action): Boolean {
        if (action.requiresConfirmation) return true
        return detectCategory(action) != null
    }

    fun detectCategory(action: Action): SensitiveCategory? {
        val desc = action.description.lowercase()
        val target = (action.parameters.targetText ?: "").lowercase()
        val pkg = (action.parameters.packageName ?: "").lowercase()
        val app = (action.parameters.appName ?: "").lowercase()
        val input = (action.parameters.inputText ?: "").lowercase()
        val query = (action.parameters.query ?: "").lowercase()
        val allText = "$desc $target $pkg $app $input $query"

        return when {
            allText.contains("factory reset") || allText.contains("wipe device") ->
                SensitiveCategory.FACTORY_RESET

            allText.contains("delete file") || allText.contains("delete photo") || allText.contains("erase") ||
            (allText.contains("delete") && !allText.contains("cancel")) ->
                SensitiveCategory.FILE_DELETION

            allText.contains("uninstall") || allText.contains("install apk") || allText.contains("install app") ->
                SensitiveCategory.APP_MANAGEMENT

            allText.contains("buy") || allText.contains("purchase") || allText.contains("checkout") || allText.contains("pay now") ->
                SensitiveCategory.PURCHASE

            allText.contains("transfer money") || allText.contains("send money") ||
            pkg.contains("bank") || app.contains("bank") || pkg.contains("wallet") || app.contains("wallet") ||
            pkg.contains("pay") || app.contains("pay") ->
                SensitiveCategory.FINANCIAL

            allText.contains("send message") || allText.contains("send sms") || allText.contains("send email") ||
            (target.contains("send") && (app.contains("message") || app.contains("whatsapp") || app.contains("mail"))) ->
                SensitiveCategory.MESSAGING

            allText.contains("change password") || allText.contains("reset pin") || allText.contains("disable lock") ||
            target.contains("password") || target.contains("pin") || target.contains("cvv") ->
                SensitiveCategory.SECURITY_CHANGE

            action.type == ActionType.ASK_CONFIRMATION ->
                SensitiveCategory.SECURITY_CHANGE

            else -> null
        }
    }

    fun requestConfirmation(action: Action, onResult: (Boolean) -> Unit): ConfirmationRequest {
        val category = detectCategory(action) ?: SensitiveCategory.SECURITY_CHANGE
        val prompt = action.parameters.confirmationPrompt
            ?: "Allow NEX to execute: ${action.description} (${category.displayName})?"

        val request = ConfirmationRequest(
            action = action,
            category = category,
            prompt = prompt
        )
        pendingCompletions[request.requestId] = onResult
        _pendingConfirmation.value = request
        return request
    }

    fun respond(requestId: String, approved: Boolean) {
        val completion = pendingCompletions.remove(requestId)
        if (_pendingConfirmation.value?.requestId == requestId) {
            _pendingConfirmation.value = null
        }
        completion?.invoke(approved)
    }

    fun clearPending() {
        val completions = pendingCompletions.values.toList()
        pendingCompletions.clear()
        _pendingConfirmation.value = null
        completions.forEach { it(false) }
    }
}
