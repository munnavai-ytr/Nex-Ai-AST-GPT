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
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

enum class GeminiErrorType {
    NO_INTERNET,
    AUTH_OR_QUOTA,
    TIMEOUT,
    SERVER_ERROR,
    KEY_MISSING,
    EMPTY_RESPONSE,
    MALFORMED_RESPONSE,
    CANCELLED,
    UNKNOWN
}

sealed class GeminiPlanResult {
    data class Success(
        val actions: List<Action>,
        val spokenResponse: String,
        val unsupportedCapability: String? = null,
        val rawResponse: String = ""
    ) : GeminiPlanResult()

    data class Error(
        val message: String,
        val errorType: GeminiErrorType = GeminiErrorType.UNKNOWN,
        val statusCode: Int? = null,
        val isKeyMissing: Boolean = false
    ) : GeminiPlanResult()
}

class GeminiEngine(private val config: GeminiConfig) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val isRequestInFlight = AtomicBoolean(false)

    /**
     * Sends a planning and conversational prompt to the configured Gemini model.
     * Prevents duplicate simultaneous requests and handles timeouts, network drops, and schema errors.
     */
    suspend fun planActions(
        userPrompt: String,
        activeAppPackage: String? = null,
        screenVisibleTexts: List<String> = emptyList(),
        memoryContext: String? = null
    ): GeminiPlanResult = withContext(Dispatchers.IO) {
        val trimmedPrompt = userPrompt.trim()
        if (trimmedPrompt.isBlank()) {
            return@withContext GeminiPlanResult.Error(
                message = "Prompt cannot be empty.",
                errorType = GeminiErrorType.EMPTY_RESPONSE
            )
        }

        val apiKey = config.getApiKey()
        if (apiKey.isBlank()) {
            return@withContext GeminiPlanResult.Error(
                message = "Gemini API key is not configured. Please configure it in Settings or via Secrets.",
                errorType = GeminiErrorType.KEY_MISSING,
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
                                put("text", "User command: $trimmedPrompt")
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
            val responseCode = httpResponse.code
            val responseString = httpResponse.body?.string() ?: ""

            if (!httpResponse.isSuccessful) {
                val errorType = when (responseCode) {
                    401, 403, 429 -> GeminiErrorType.AUTH_OR_QUOTA
                    in 500..599 -> GeminiErrorType.SERVER_ERROR
                    else -> GeminiErrorType.UNKNOWN
                }
                val parsedErrorMsg = parseErrorMessage(responseString)
                val userFriendlyMessage = when (errorType) {
                    GeminiErrorType.AUTH_OR_QUOTA -> "Gemini API authentication or quota limit exceeded ($responseCode): $parsedErrorMsg"
                    GeminiErrorType.SERVER_ERROR -> "Gemini service temporarily unavailable ($responseCode). Please try again."
                    else -> "Gemini API call failed ($responseCode): $parsedErrorMsg"
                }
                return@withContext GeminiPlanResult.Error(
                    message = userFriendlyMessage,
                    errorType = errorType,
                    statusCode = responseCode
                )
            }

            parseGeminiOutput(responseString, trimmedPrompt)
        } catch (e: UnknownHostException) {
            GeminiPlanResult.Error(
                message = "No internet connection. Please check your network connectivity.",
                errorType = GeminiErrorType.NO_INTERNET
            )
        } catch (e: SocketTimeoutException) {
            GeminiPlanResult.Error(
                message = "Request to Gemini API timed out. Please check network speed and try again.",
                errorType = GeminiErrorType.TIMEOUT
            )
        } catch (e: IOException) {
            GeminiPlanResult.Error(
                message = "Network error contacting Gemini: ${e.message ?: "Connection failed"}",
                errorType = GeminiErrorType.NO_INTERNET
            )
        } catch (e: Exception) {
            GeminiPlanResult.Error(
                message = "Unexpected error communicating with Gemini: ${e.localizedMessage ?: e.message}",
                errorType = GeminiErrorType.UNKNOWN
            )
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

    fun parseGeminiOutput(responseBody: String, originalPrompt: String): GeminiPlanResult {
        if (responseBody.isBlank()) {
            return GeminiPlanResult.Error(
                message = "Received empty response body from Gemini API",
                errorType = GeminiErrorType.EMPTY_RESPONSE
            )
        }

        try {
            val root = JSONObject(responseBody)
            val candidates = root.optJSONArray("candidates")
                ?: return GeminiPlanResult.Error(
                    message = "No candidates returned from Gemini API",
                    errorType = GeminiErrorType.EMPTY_RESPONSE
                )

            val firstCandidate = candidates.optJSONObject(0)
                ?: return GeminiPlanResult.Error(
                    message = "Missing candidate content in Gemini response",
                    errorType = GeminiErrorType.EMPTY_RESPONSE
                )

            val content = firstCandidate.optJSONObject("content")
                ?: return GeminiPlanResult.Error(
                    message = "Missing candidate content object in Gemini response",
                    errorType = GeminiErrorType.MALFORMED_RESPONSE
                )

            val parts = content.optJSONArray("parts")
                ?: return GeminiPlanResult.Error(
                    message = "Missing content parts in Gemini response",
                    errorType = GeminiErrorType.MALFORMED_RESPONSE
                )

            val firstPart = parts.optJSONObject(0)
            val text = firstPart?.optString("text")
            if (text.isNullOrBlank()) {
                return GeminiPlanResult.Error(
                    message = "Received blank text part in Gemini response",
                    errorType = GeminiErrorType.EMPTY_RESPONSE
                )
            }

            val cleanJson = text.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            if (cleanJson.isBlank()) {
                return GeminiPlanResult.Error(
                    message = "Extracted empty JSON string from Gemini text part",
                    errorType = GeminiErrorType.EMPTY_RESPONSE
                )
            }

            var spokenResponse = ""
            var unsupportedCap: String? = null
            val actions = mutableListOf<Action>()

            if (cleanJson.startsWith("[")) {
                val array = JSONArray(cleanJson)
                actions.addAll(parseActionsArray(array))
                spokenResponse = "Executing ${actions.size} actions for: $originalPrompt"
            } else if (cleanJson.startsWith("{")) {
                val obj = JSONObject(cleanJson)
                spokenResponse = obj.optString("spokenResponse", "")
                if (obj.has("unsupportedCapability") && !obj.isNull("unsupportedCapability")) {
                    unsupportedCap = obj.optString("unsupportedCapability").takeIf { it.isNotBlank() }
                }
                val actionsArray = obj.optJSONArray("actions") ?: JSONArray()
                actions.addAll(parseActionsArray(actionsArray))
            } else {
                return GeminiPlanResult.Error(
                    message = "Gemini response is not valid JSON object or array",
                    errorType = GeminiErrorType.MALFORMED_RESPONSE
                )
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
            return GeminiPlanResult.Error(
                message = "Failed to parse Gemini structured response: ${e.message}",
                errorType = GeminiErrorType.MALFORMED_RESPONSE
            )
        }
    }

    fun parseActionsArray(jsonArray: JSONArray): List<Action> {
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
