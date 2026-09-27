package com.example.automation.agent

import com.example.automation.Action
import com.example.automation.ActionParameters
import com.example.automation.ActionType
import org.json.JSONArray
import org.json.JSONObject

data class TaskTarget(
    val text: String? = null,
    val viewId: String? = null,
    val contentDescription: String? = null,
    val xPercent: Float? = null,
    val yPercent: Float? = null
) {
    val description: String
        get() = text ?: viewId ?: contentDescription ?: "(${xPercent ?: 0f}, ${yPercent ?: 0f})"
}

data class StepSuccessCriteria(
    val expectedPackage: String? = null,
    val expectedText: String? = null,
    val expectedViewId: String? = null,
    val screenMustChange: Boolean = true
)

data class TaskStep(
    val id: String,
    val action: ActionType,
    val target: TaskTarget? = null,
    val parameters: ActionParameters = ActionParameters(),
    val successCriteria: StepSuccessCriteria? = null,
    val description: String = ""
) {
    fun toAction(): Action {
        val mergedParams = ActionParameters(
            packageName = parameters.packageName,
            appName = parameters.appName,
            targetText = target?.text ?: parameters.targetText,
            viewId = target?.viewId ?: parameters.viewId,
            inputText = parameters.inputText,
            direction = parameters.direction,
            durationMs = parameters.durationMs,
            query = parameters.query,
            confirmationPrompt = parameters.confirmationPrompt,
            xPercent = target?.xPercent ?: parameters.xPercent,
            yPercent = target?.yPercent ?: parameters.yPercent,
            exists = parameters.exists,
            isEditable = parameters.isEditable,
            isClickable = parameters.isClickable,
            sensitiveCategory = parameters.sensitiveCategory
        )

        return Action(
            id = id,
            type = action,
            parameters = mergedParams,
            description = if (description.isNotBlank()) description else "Execute ${action.name} on ${target?.description ?: mergedParams.appName ?: "target"}",
            requiresConfirmation = action == ActionType.ASK_CONFIRMATION
        )
    }
}

data class TaskPlan(
    val goal: String,
    val steps: List<TaskStep>,
    val spokenResponse: String = "",
    val unsupportedCapability: String? = null
) {
    companion object {
        sealed class ParseResult {
            data class Success(val plan: TaskPlan) : ParseResult()
            data class Invalid(val errors: List<String>) : ParseResult()
        }

        fun parseAndValidate(jsonString: String): ParseResult {
            val errors = mutableListOf<String>()

            val root = try {
                val clean = jsonString.trim()
                    .removePrefix("```json")
                    .removePrefix("```")
                    .removeSuffix("```")
                    .trim()
                JSONObject(clean)
            } catch (e: Exception) {
                return ParseResult.Invalid(listOf("Malformed JSON: ${e.message}"))
            }

            val goal = root.optString("goal", "").trim()
            val spokenResponse = root.optString("spokenResponse", "").trim()
            val unsupported = root.optString("unsupportedCapability").takeIf { !it.isNullOrBlank() }

            if (unsupported != null) {
                return ParseResult.Success(
                    TaskPlan(
                        goal = goal.ifBlank { "Unsupported request" },
                        steps = emptyList(),
                        spokenResponse = spokenResponse,
                        unsupportedCapability = unsupported
                    )
                )
            }

            if (goal.isBlank()) {
                errors.add("Plan schema error: 'goal' field is missing or empty")
            }

            val stepsArray = root.optJSONArray("steps")
            val stepsList = mutableListOf<TaskStep>()

            if (stepsArray != null) {
                for (i in 0 until stepsArray.length()) {
                    val stepObj = stepsArray.optJSONObject(i)
                    if (stepObj == null) {
                        errors.add("Step at index $i is not a valid JSON object")
                        continue
                    }

                    val stepId = stepObj.optString("id", "step_${i + 1}")
                    val actionStr = stepObj.optString("action", "").uppercase().trim()
                    val actionType = try {
                        ActionType.valueOf(actionStr)
                    } catch (_: Exception) {
                        errors.add("Step $stepId has unknown action type '$actionStr'")
                        null
                    }

                    val desc = stepObj.optString("description", "")

                    // Parse target
                    var target: TaskTarget? = null
                    if (stepObj.has("target")) {
                        val targetObj = stepObj.optJSONObject("target")
                        if (targetObj != null) {
                            target = TaskTarget(
                                text = targetObj.optString("text").takeIf { it.isNotBlank() },
                                viewId = targetObj.optString("viewId").takeIf { it.isNotBlank() },
                                contentDescription = targetObj.optString("contentDescription").takeIf { it.isNotBlank() },
                                xPercent = if (targetObj.has("xPercent")) targetObj.optDouble("xPercent").toFloat() else null,
                                yPercent = if (targetObj.has("yPercent")) targetObj.optDouble("yPercent").toFloat() else null
                            )
                        }
                    }

                    // Parse parameters
                    val paramsObj = stepObj.optJSONObject("parameters") ?: JSONObject()
                    val parameters = ActionParameters(
                        packageName = paramsObj.optString("packageName").takeIf { it.isNotBlank() },
                        appName = paramsObj.optString("appName").takeIf { it.isNotBlank() },
                        targetText = paramsObj.optString("targetText").takeIf { it.isNotBlank() } ?: target?.text,
                        viewId = paramsObj.optString("viewId").takeIf { it.isNotBlank() } ?: target?.viewId,
                        inputText = stepObj.optString("text").takeIf { it.isNotBlank() }
                            ?: paramsObj.optString("inputText").takeIf { it.isNotBlank() },
                        direction = paramsObj.optString("direction").takeIf { it.isNotBlank() },
                        durationMs = if (paramsObj.has("durationMs")) paramsObj.optLong("durationMs") else null,
                        query = paramsObj.optString("query").takeIf { it.isNotBlank() },
                        confirmationPrompt = paramsObj.optString("confirmationPrompt").takeIf { it.isNotBlank() }
                    )

                    // Parse success criteria
                    var criteria: StepSuccessCriteria? = null
                    if (stepObj.has("successCriteria")) {
                        val critObj = stepObj.optJSONObject("successCriteria")
                        if (critObj != null) {
                            criteria = StepSuccessCriteria(
                                expectedPackage = critObj.optString("expectedPackage").takeIf { it.isNotBlank() },
                                expectedText = critObj.optString("expectedText").takeIf { it.isNotBlank() },
                                expectedViewId = critObj.optString("expectedViewId").takeIf { it.isNotBlank() },
                                screenMustChange = critObj.optBoolean("screenMustChange", true)
                            )
                        }
                    }

                    if (actionType != null) {
                        stepsList.add(
                            TaskStep(
                                id = stepId,
                                action = actionType,
                                target = target,
                                parameters = parameters,
                                successCriteria = criteria,
                                description = desc
                            )
                        )
                    }
                }
            }

            if (errors.isNotEmpty()) {
                return ParseResult.Invalid(errors)
            }

            return ParseResult.Success(
                TaskPlan(
                    goal = goal,
                    steps = stepsList,
                    spokenResponse = spokenResponse,
                    unsupportedCapability = null
                )
            )
        }
    }
}
