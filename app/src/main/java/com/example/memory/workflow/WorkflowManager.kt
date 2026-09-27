package com.example.memory.workflow

import com.example.automation.Action
import com.example.automation.ActionType
import com.example.memory.dao.WorkflowDao
import com.example.memory.entities.WorkflowEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class StructuredWorkflow(
    val id: Long = 0,
    val workflowId: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val triggerPhrase: String = "",
    val actions: List<Action>,
    val requiredParameters: Map<String, String> = emptyMap(),
    val preconditions: List<String> = emptyList(),
    val requiresConfirmation: Boolean = false,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

sealed class WorkflowValidationResult {
    object Valid : WorkflowValidationResult()
    data class Invalid(val errors: List<String>) : WorkflowValidationResult()
}

class WorkflowManager(
    private val workflowDao: WorkflowDao
) {
    val workflowsFlow: Flow<List<WorkflowEntity>> = workflowDao.getAllWorkflowsFlow()

    /**
     * Validates that the workflow contains legitimate supported action schemas.
     */
    fun validateWorkflow(workflow: StructuredWorkflow): WorkflowValidationResult {
        val errors = mutableListOf<String>()

        if (workflow.name.isBlank()) {
            errors.add("Workflow name cannot be blank.")
        }

        if (workflow.actions.isEmpty()) {
            errors.add("Workflow must contain at least one action.")
        }

        for ((index, action) in workflow.actions.withIndex()) {
            when (action.type) {
                ActionType.OPEN_APP -> {
                    if (action.parameters.packageName.isNullOrBlank() && action.parameters.appName.isNullOrBlank()) {
                        errors.add("Action #$index (OPEN_APP) requires either packageName or appName.")
                    }
                }
                ActionType.TYPE_TEXT -> {
                    if (action.parameters.inputText.isNullOrBlank()) {
                        errors.add("Action #$index (TYPE_TEXT) requires inputText.")
                    }
                }
                ActionType.SEARCH -> {
                    if (action.parameters.query.isNullOrBlank()) {
                        errors.add("Action #$index (SEARCH) requires a query parameter.")
                    }
                }
                else -> {
                    // Valid action type
                }
            }
        }

        return if (errors.isEmpty()) WorkflowValidationResult.Valid else WorkflowValidationResult.Invalid(errors)
    }

    /**
     * Persists a validated workflow to Room database.
     */
    suspend fun saveWorkflow(workflow: StructuredWorkflow): Long = withContext(Dispatchers.IO) {
        val actionsArray = JSONArray()
        for (action in workflow.actions) {
            val actionObj = JSONObject().apply {
                put("type", action.type.name)
                put("description", action.description)
                put("requiresConfirmation", action.requiresConfirmation)
                put("parameters", JSONObject().apply {
                    put("packageName", action.parameters.packageName)
                    put("appName", action.parameters.appName)
                    put("targetText", action.parameters.targetText)
                    put("viewId", action.parameters.viewId)
                    put("inputText", action.parameters.inputText)
                    put("direction", action.parameters.direction)
                    put("durationMs", action.parameters.durationMs)
                    put("query", action.parameters.query)
                })
            }
            actionsArray.put(actionObj)
        }

        val requiredParamsObj = JSONObject()
        workflow.requiredParameters.forEach { (k, v) -> requiredParamsObj.put(k, v) }

        val preconditionsArray = JSONArray()
        workflow.preconditions.forEach { preconditionsArray.put(it) }

        val entity = WorkflowEntity(
            id = workflow.id,
            workflowId = workflow.workflowId,
            name = workflow.name.trim(),
            description = workflow.description.trim(),
            triggerPhrase = workflow.triggerPhrase.trim(),
            actionsJson = actionsArray.toString(),
            requiredParamsJson = requiredParamsObj.toString(),
            preconditionsJson = preconditionsArray.toString(),
            requiresConfirmation = workflow.requiresConfirmation,
            isEnabled = workflow.isEnabled,
            createdAt = workflow.createdAt,
            updatedAt = System.currentTimeMillis()
        )

        workflowDao.insertWorkflow(entity)
    }

    suspend fun findByTrigger(triggerPhrase: String): StructuredWorkflow? = withContext(Dispatchers.IO) {
        val entity = workflowDao.findByTrigger(triggerPhrase.trim()) ?: return@withContext null
        parseWorkflowEntity(entity)
    }

    suspend fun deleteWorkflow(workflowId: String): Boolean = withContext(Dispatchers.IO) {
        workflowDao.deleteByWorkflowId(workflowId) > 0
    }

    private fun parseWorkflowEntity(entity: WorkflowEntity): StructuredWorkflow {
        val actions = mutableListOf<Action>()
        try {
            val array = JSONArray(entity.actionsJson)
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val typeStr = obj.optString("type")
                val actionType = try { ActionType.valueOf(typeStr) } catch (_: Exception) { continue }
                val desc = obj.optString("description", "")
                val reqConfirm = obj.optBoolean("requiresConfirmation", false)
                val paramsObj = obj.optJSONObject("parameters") ?: JSONObject()

                val params = com.example.automation.ActionParameters(
                    packageName = paramsObj.optString("packageName").takeIf { it.isNotBlank() },
                    appName = paramsObj.optString("appName").takeIf { it.isNotBlank() },
                    targetText = paramsObj.optString("targetText").takeIf { it.isNotBlank() },
                    viewId = paramsObj.optString("viewId").takeIf { it.isNotBlank() },
                    inputText = paramsObj.optString("inputText").takeIf { it.isNotBlank() },
                    direction = paramsObj.optString("direction").takeIf { it.isNotBlank() },
                    durationMs = if (paramsObj.has("durationMs")) paramsObj.optLong("durationMs") else null,
                    query = paramsObj.optString("query").takeIf { it.isNotBlank() }
                )

                actions.add(Action(type = actionType, parameters = params, description = desc, requiresConfirmation = reqConfirm))
            }
        } catch (_: Exception) {
            // Ignore parse errors
        }

        return StructuredWorkflow(
            id = entity.id,
            workflowId = entity.workflowId,
            name = entity.name,
            description = entity.description,
            triggerPhrase = entity.triggerPhrase,
            actions = actions,
            requiresConfirmation = entity.requiresConfirmation,
            isEnabled = entity.isEnabled,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }
}
