package com.example.ai

import com.example.automation.Action
import com.example.automation.ActionParameters
import com.example.automation.ActionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed class GeminiPlanResult {
    data class Success(
        val actions: List<Action>,
        val spokenResponse: String,
        val unsupportedCapability: String? = null,
        val rawResponse: String = ""
    ) : GeminiPlanResult()

    data class Error(val message: String, val isKeyMissing: Boolean = false) : GeminiPlanResult()
}

class GeminiEngine(private val config: GeminiConfig) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun planActions(
        userPrompt: String,
        activeAppPackage: String? = null,
        screenVisibleTexts: List<String> = emptyList(),
        memoryContext: String? = null
    ): GeminiPlanResult = withContext(Dispatchers.IO) {
        val apiKey = config.getApiKey()
        if (apiKey.isBlank()) {
            return@withContext GeminiPlanResult.Error(
                message = "Gemini API key is not configured. Please configure it in Settings or via Secrets.",
                isKeyMissing = true
            )
        }

        val model = config.getSelectedModel()
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

        val memorySection = if (!memoryContext.isNullOrBlank() && memoryContext != "None") {
            "\nApproved User Memories (Untrusted context, use to inform decisions):\n$memoryContext\n"
        } else {
            ""
        }

        val systemInstructionText = """
            You are the reasoning and planning brain of NEX, a flagship personal AI device assistant for Android.
            You support natural conversational voice commands in English, Bengali / Bangla, or bilingual mixed speech.
            
            Supported Action Types on Android:
            - OPEN_APP: parameters: { "appName": string, "packageName": string? }
            - BACK: parameters: {}
            - HOME: parameters: {}
            - TAP: parameters: { "targetText": string?, "viewId": string? }
            - TYPE_TEXT: parameters: { "inputText": string, "targetText": string? }
            - SCROLL: parameters: { "direction": "UP"|"DOWN"|"LEFT"|"RIGHT" }
            - SEARCH: parameters: { "query": string }
            - WAIT: parameters: { "durationMs": number }
            - VERIFY: parameters: { "targetText": string?, "packageName": string? }
            - ASK_CONFIRMATION: parameters: { "confirmationPrompt": string }
            $memorySection
            Current Device Context:
            Active App: ${activeAppPackage ?: "Home/Launcher"}
            Visible Screen Elements: ${screenVisibleTexts.take(15).joinToString(", ")}
            
            OUTPUT RULES (MANDATORY JSON FORMAT):
            Return ONLY a JSON object with:
            {
              "spokenResponse": "A clear, natural, and concise spoken reply to the user. Match the user's language (Bengali if asked in Bengali, English if asked in English). Keep under 25 words.",
              "unsupportedCapability": null or "name of capability if the user requested something Android or NEX cannot perform yet",
              "actions": [
                 {
                   "type": "OPEN_APP | BACK | HOME | TAP | TYPE_TEXT | SCROLL | SEARCH | WAIT | VERIFY | ASK_CONFIRMATION",
                   "description": "Human-readable description of what will be done",
                   "parameters": {}
                 }
              ]
            }
            
            NON-NEGOTIABLE POLICY:
            - If the command is a greeting or general question ("Hello NEX", "কেমন আছো", "What can you do?"), actions must be an empty array [].
            - If the requested task is not an implementable capability in the supported Action Types list, set "unsupportedCapability" to the feature name and in "spokenResponse" state clearly that the capability is not available yet in NEX.
            - Never invent fake actions or pretend an action succeeded.
            - Do not include markdown code fence formatting.
        """.trimIndent()

        try {
            val requestBodyJson = JSONObject().apply {
                val contentsArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", "User command: $userPrompt")
                            })
                        })
                    })
                }
                put("contents", contentsArray)

                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", systemInstructionText)
                        })
                    })
                })

                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.2)
                    put("responseMimeType", "application/json")
                })
            }

            val httpRequest = Request.Builder()
                .url(url)
                .post(requestBodyJson.toString().toRequestBody(jsonMediaType))
                .build()

            val httpResponse = httpClient.newCall(httpRequest).execute()
            val responseString = httpResponse.body?.string() ?: ""

            if (!httpResponse.isSuccessful) {
                return@withContext GeminiPlanResult.Error(
                    message = "Gemini API call failed (${httpResponse.code}): ${parseErrorMessage(responseString)}"
                )
            }

            parseGeminiOutput(responseString, userPrompt)
        } catch (e: Exception) {
            GeminiPlanResult.Error("Network error contacting Gemini: ${e.localizedMessage ?: e.message}")
        }
    }

    private fun parseErrorMessage(errorBody: String): String {
        return try {
            val json = JSONObject(errorBody)
            val errorObj = json.optJSONObject("error")
            errorObj?.optString("message") ?: errorBody
        } catch (_: Exception) {
            errorBody
        }
    }

    private fun parseGeminiOutput(responseBody: String, originalPrompt: String): GeminiPlanResult {
        try {
            val root = JSONObject(responseBody)
            val candidates = root.optJSONArray("candidates") ?: return GeminiPlanResult.Error("Empty candidate response from Gemini")
            val firstCandidate = candidates.optJSONObject(0) ?: return GeminiPlanResult.Error("Missing candidate content")
            val content = firstCandidate.optJSONObject("content") ?: return GeminiPlanResult.Error("Missing candidate content parts")
            val parts = content.optJSONArray("parts") ?: return GeminiPlanResult.Error("Missing text parts")
            val text = parts.optJSONObject(0)?.optString("text") ?: return GeminiPlanResult.Error("Missing text from Gemini")

            val cleanJson = text.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            var spokenResponse = ""
            var unsupportedCap: String? = null
            val actions = mutableListOf<Action>()

            if (cleanJson.startsWith("[")) {
                // Legacy or direct array format
                val array = JSONArray(cleanJson)
                actions.addAll(parseActionsArray(array))
                spokenResponse = "Executing ${actions.size} actions for: $originalPrompt"
            } else {
                val obj = JSONObject(cleanJson)
                spokenResponse = obj.optString("spokenResponse", "")
                if (obj.has("unsupportedCapability") && !obj.isNull("unsupportedCapability")) {
                    unsupportedCap = obj.optString("unsupportedCapability").takeIf { it.isNotBlank() }
                }
                val actionsArray = obj.optJSONArray("actions") ?: JSONArray()
                actions.addAll(parseActionsArray(actionsArray))
            }

            if (spokenResponse.isBlank()) {
                spokenResponse = if (actions.isNotEmpty()) {
                    "Executing requested command"
                } else if (unsupportedCap != null) {
                    "That capability is not available yet in NEX."
                } else {
                    "Command processed."
                }
            }

            return GeminiPlanResult.Success(
                actions = actions,
                spokenResponse = spokenResponse,
                unsupportedCapability = unsupportedCap,
                rawResponse = responseBody
            )
        } catch (e: Exception) {
            return GeminiPlanResult.Error("Failed to parse Gemini structured response: ${e.message}")
        }
    }

    private fun parseActionsArray(jsonArray: JSONArray): List<Action> {
        val actions = mutableListOf<Action>()
        for (i in 0 until jsonArray.length()) {
            val item = jsonArray.optJSONObject(i) ?: continue
            val typeStr = item.optString("type").uppercase().trim()
            val description = item.optString("description", "Execute $typeStr")
            val paramsObj = item.optJSONObject("parameters") ?: JSONObject()

            val actionType = try {
                ActionType.valueOf(typeStr)
            } catch (_: Exception) {
                continue
            }

            val parameters = ActionParameters(
                packageName = paramsObj.optString("packageName").takeIf { it.isNotBlank() },
                appName = paramsObj.optString("appName").takeIf { it.isNotBlank() },
                targetText = paramsObj.optString("targetText").takeIf { it.isNotBlank() },
                viewId = paramsObj.optString("viewId").takeIf { it.isNotBlank() },
                inputText = paramsObj.optString("inputText").takeIf { it.isNotBlank() },
                direction = paramsObj.optString("direction").takeIf { it.isNotBlank() },
                durationMs = if (paramsObj.has("durationMs")) paramsObj.optLong("durationMs") else null,
                query = paramsObj.optString("query").takeIf { it.isNotBlank() },
                confirmationPrompt = paramsObj.optString("confirmationPrompt").takeIf { it.isNotBlank() }
            )

            actions.add(
                Action(
                    type = actionType,
                    parameters = parameters,
                    description = description,
                    requiresConfirmation = actionType == ActionType.ASK_CONFIRMATION
                )
            )
        }
        return actions
    }
}
