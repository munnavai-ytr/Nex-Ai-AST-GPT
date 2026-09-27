package com.example.memory.ai

import com.example.ai.GeminiConfig
import com.example.memory.model.MemoryCandidate
import com.example.memory.model.MemoryCandidateAction
import com.example.memory.model.MemoryCategory
import com.example.memory.model.MemorySensitivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed class ExtractionResult {
    data class CandidateFound(val candidate: MemoryCandidate) : ExtractionResult()
    object NotAMemoryCommand : ExtractionResult()
    data class Error(val message: String) : ExtractionResult()
}

class MemoryCandidateExtractor(
    private val geminiConfig: GeminiConfig
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Attempts to extract a structured memory candidate from user speech or text input.
     * Uses fast local pattern matching first, then falls back to Gemini if available.
     */
    suspend fun extractCandidate(rawInput: String): ExtractionResult = withContext(Dispatchers.Default) {
        val trimmed = rawInput.trim()
        if (trimmed.isBlank()) return@withContext ExtractionResult.NotAMemoryCommand

        // 1. Fast local regex matcher for explicit commands (English + Bengali)
        val localCandidate = extractFastLocal(trimmed)
        if (localCandidate != null) {
            return@withContext ExtractionResult.CandidateFound(localCandidate)
        }

        // 2. If Gemini API key is configured, use Gemini for conversational extraction
        if (geminiConfig.isApiKeyConfigured()) {
            return@withContext extractWithGemini(trimmed)
        }

        ExtractionResult.NotAMemoryCommand
    }

    private fun extractFastLocal(input: String): MemoryCandidate? {
        val lower = input.lowercase()

        // Alias matching: "Save this alias: YT means YouTube" or "Alias YT means YouTube" or "YT মানে YouTube"
        val aliasPattern = Regex("^(?:save\\s+(?:this\\s+)?alias[:\\s]+|alias\\s+)?([a-z0-9_.-]+)\\s+(?:means|is|for|=|মানে)\\s+(.+)$", RegexOption.IGNORE_CASE)
        val aliasMatch = aliasPattern.find(input)
        if (aliasMatch != null && (lower.contains("alias") || lower.contains("means") || lower.contains("মানে"))) {
            val key = aliasMatch.groupValues[1].trim()
            val value = aliasMatch.groupValues[2].trim()
            return MemoryCandidate(
                action = MemoryCandidateAction.SAVE,
                category = MemoryCategory.APP_ALIASES,
                key = key,
                value = value,
                sensitivity = MemorySensitivity.NORMAL,
                reason = "Explicit app alias definition: $key -> $value",
                rawSource = input
            )
        }

        // Language preference: "Remember that I prefer Bengali" or "আমার ভাষা বাংলা করো"
        if (lower.contains("prefer bengali") || lower.contains("language to bengali") || lower.contains("বাংলা ভাষা পছন্দ") || lower.contains("ভাষা বাংলা")) {
            return MemoryCandidate(
                action = MemoryCandidateAction.SAVE,
                category = MemoryCategory.USER_PREFERENCES,
                key = "voice_language",
                value = "bn-BD",
                sensitivity = MemorySensitivity.NORMAL,
                reason = "Owner preferred voice language: Bengali",
                rawSource = input
            )
        }
        if (lower.contains("prefer english") || lower.contains("language to english") || lower.contains("ইংরেজি ভাষা পছন্দ")) {
            return MemoryCandidate(
                action = MemoryCandidateAction.SAVE,
                category = MemoryCategory.USER_PREFERENCES,
                key = "voice_language",
                value = "en-US",
                sensitivity = MemorySensitivity.NORMAL,
                reason = "Owner preferred voice language: English",
                rawSource = input
            )
        }

        // Explicit "Forget" / "ভুলে যাও" commands:
        val forgetPattern = Regex("^(?:forget|remove|delete|মুছে\\s+ফেলো|ভুলে\\s+যাও)\\s+(?:my\\s+|that\\s+|the\\s+|আমার\\s+)?(.+)$", RegexOption.IGNORE_CASE)
        val forgetMatch = forgetPattern.find(input)
        if (forgetMatch != null && (lower.startsWith("forget") || lower.startsWith("মুছে") || lower.startsWith("ভুলে"))) {
            val targetKey = forgetMatch.groupValues[1].trim().replace(" ", "_").lowercase()
            return MemoryCandidate(
                action = MemoryCandidateAction.FORGET,
                category = MemoryCategory.USER_PROVIDED_FACTS,
                key = targetKey,
                value = "",
                sensitivity = MemorySensitivity.NORMAL,
                reason = "User requested deletion of fact: $targetKey",
                rawSource = input
            )
        }

        // Explicit "Remember that" / "মনে রাখো" commands:
        val rememberPattern = Regex("^(?:remember\\s+(?:that|this)?[:\\s]*|save\\s+(?:that|this)?[:\\s]*|note\\s+(?:down)?[:\\s]*|মনে\\s+রাখো[:\\s]*)(.+)$", RegexOption.IGNORE_CASE)
        val rememberMatch = rememberPattern.find(input)
        if (rememberMatch != null) {
            val content = rememberMatch.groupValues[1].trim()
            // Split into key/value if colon or "is" exists
            val splitPattern = Regex("^([^:]+?)\\s*(?::|\\sis\\s|\\sহলো\\s|\\sমানে\\s)\\s*(.+)$", RegexOption.IGNORE_CASE)
            val splitMatch = splitPattern.find(content)

            val key: String
            val value: String
            if (splitMatch != null) {
                key = splitMatch.groupValues[1].trim().replace(" ", "_").lowercase()
                value = splitMatch.groupValues[2].trim()
            } else {
                key = "fact_${System.currentTimeMillis() % 10000}"
                value = content
            }

            return MemoryCandidate(
                action = MemoryCandidateAction.SAVE,
                category = MemoryCategory.USER_PROVIDED_FACTS,
                key = key,
                value = value,
                sensitivity = MemorySensitivity.NORMAL,
                reason = "Explicit user memory statement",
                rawSource = input
            )
        }

        return null
    }

    private suspend fun extractWithGemini(input: String): ExtractionResult = withContext(Dispatchers.IO) {
        val apiKey = geminiConfig.getApiKey()
        if (apiKey.isBlank()) return@withContext ExtractionResult.NotAMemoryCommand

        val model = geminiConfig.getSelectedModel()
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

        val systemPrompt = """
            You are the Memory Intent Classifier for NEX, an Android AI assistant.
            The user speaks in English, Bengali (বাংলা), or bilingual speech.
            
            Determine if the user's input is an EXPLICIT memory command (e.g. asking to remember a preference, fact, alias, routine, workflow, or forget something).
            
            Valid Categories:
            - USER_PREFERENCES (e.g. language, confirmation settings, themes)
            - APP_ALIASES (e.g. "YT means YouTube", "Browser is Chrome")
            - USER_PROVIDED_FACTS (e.g. "My work schedule is 9 to 5", "My dog's name is Rex")
            - WORKFLOWS (e.g. "Save my morning routine")
            - USER_APPROVED_CONTACT_ALIASES (e.g. "Mom means +123456789")
            
            Valid Actions: SAVE, FORGET, QUERY, UPDATE
            Valid Sensitivities: NORMAL, SENSITIVE, STRICTLY_CONFIDENTIAL
            
            OUTPUT RULES (JSON ONLY):
            If NOT an explicit memory command, return:
            { "isMemoryCommand": false }
            
            If it IS an explicit memory command, return:
            {
              "isMemoryCommand": true,
              "action": "SAVE | FORGET | QUERY | UPDATE",
              "category": "USER_PREFERENCES | APP_ALIASES | USER_PROVIDED_FACTS | WORKFLOWS | USER_APPROVED_CONTACT_ALIASES",
              "key": "canonical_short_key_in_snake_case",
              "value": "extracted_memory_value_or_content",
              "sensitivity": "NORMAL | SENSITIVE | STRICTLY_CONFIDENTIAL",
              "reason": "Short explanation of the extraction"
            }
            
            DO NOT mark general questions or ordinary task commands as memory commands.
        """.trimIndent()

        try {
            val requestBodyJson = JSONObject().apply {
                val contentsArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", "Input: $input")
                            })
                        })
                    })
                }
                put("contents", contentsArray)
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", systemPrompt)
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.1)
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
                return@withContext ExtractionResult.NotAMemoryCommand
            }

            val root = JSONObject(responseString)
            val candidates = root.optJSONArray("candidates") ?: return@withContext ExtractionResult.NotAMemoryCommand
            val firstCandidate = candidates.optJSONObject(0) ?: return@withContext ExtractionResult.NotAMemoryCommand
            val parts = firstCandidate.optJSONObject("content")?.optJSONArray("parts") ?: return@withContext ExtractionResult.NotAMemoryCommand
            val text = parts.optJSONObject(0)?.optString("text") ?: return@withContext ExtractionResult.NotAMemoryCommand

            val cleanJson = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val resultJson = JSONObject(cleanJson)

            if (!resultJson.optBoolean("isMemoryCommand", false)) {
                return@withContext ExtractionResult.NotAMemoryCommand
            }

            val actionStr = resultJson.optString("action", "SAVE").uppercase()
            val action = try { MemoryCandidateAction.valueOf(actionStr) } catch (_: Exception) { MemoryCandidateAction.SAVE }
            val category = MemoryCategory.fromString(resultJson.optString("category", "USER_PROVIDED_FACTS"))
            val key = resultJson.optString("key", "").trim()
            val value = resultJson.optString("value", "").trim()
            val sensitivity = MemorySensitivity.fromString(resultJson.optString("sensitivity", "NORMAL"))
            val reason = resultJson.optString("reason", "Extracted by Gemini")

            if (key.isBlank()) {
                return@withContext ExtractionResult.NotAMemoryCommand
            }

            ExtractionResult.CandidateFound(
                MemoryCandidate(
                    action = action,
                    category = category,
                    key = key,
                    value = value,
                    sensitivity = sensitivity,
                    reason = reason,
                    rawSource = input
                )
            )
        } catch (e: Exception) {
            ExtractionResult.Error("Failed to extract candidate via Gemini: ${e.message}")
        }
    }
}
