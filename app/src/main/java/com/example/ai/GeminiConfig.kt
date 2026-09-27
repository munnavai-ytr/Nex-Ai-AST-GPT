package com.example.ai

import android.content.Context
import com.example.BuildConfig

class GeminiConfig(private val context: Context? = null) {

    private val prefs = context?.getSharedPreferences("nex_ai_prefs", Context.MODE_PRIVATE)

    companion object {
        const val MODEL_FLASH = "gemini-3.5-flash"
        const val MODEL_PRO = "gemini-3.1-pro-preview"
        const val PREF_CUSTOM_KEY = "custom_gemini_api_key"
        const val PREF_SELECTED_MODEL = "selected_gemini_model"
    }

    fun getApiKey(): String {
        val customKey = prefs?.getString(PREF_CUSTOM_KEY, null)
        if (!customKey.isNullOrBlank()) {
            return customKey.trim()
        }
        return try {
            BuildConfig.GEMINI_API_KEY.takeIf { it.isNotBlank() && it != "MY_GEMINI_API_KEY" } ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    fun isApiKeyConfigured(): Boolean {
        return getApiKey().isNotBlank()
    }

    fun setCustomApiKey(key: String?) {
        prefs?.edit()?.putString(PREF_CUSTOM_KEY, key?.trim())?.apply()
    }

    fun getSelectedModel(): String {
        return prefs?.getString(PREF_SELECTED_MODEL, MODEL_FLASH) ?: MODEL_FLASH
    }

    fun setSelectedModel(model: String) {
        prefs?.edit()?.putString(PREF_SELECTED_MODEL, model)?.apply()
    }
}
