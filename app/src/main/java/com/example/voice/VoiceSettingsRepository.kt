package com.example.voice

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VoiceSettings(
    val language: VoiceLanguage = VoiceLanguage.ENGLISH_US,
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val autoSpeakResponses: Boolean = true,
    val continuousSession: Boolean = false
)

class VoiceSettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("nex_voice_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<VoiceSettings> = _settings.asStateFlow()

    private fun loadSettings(): VoiceSettings {
        val langCode = prefs.getString(KEY_LANGUAGE, VoiceLanguage.ENGLISH_US.code) ?: VoiceLanguage.ENGLISH_US.code
        val language = VoiceLanguage.values().find { it.code == langCode } ?: VoiceLanguage.ENGLISH_US
        val speechRate = prefs.getFloat(KEY_SPEECH_RATE, 1.0f)
        val pitch = prefs.getFloat(KEY_PITCH, 1.0f)
        val autoSpeak = prefs.getBoolean(KEY_AUTO_SPEAK, true)
        val continuous = prefs.getBoolean(KEY_CONTINUOUS, false)

        return VoiceSettings(
            language = language,
            speechRate = speechRate,
            pitch = pitch,
            autoSpeakResponses = autoSpeak,
            continuousSession = continuous
        )
    }

    fun setLanguage(language: VoiceLanguage) {
        prefs.edit().putString(KEY_LANGUAGE, language.code).apply()
        _settings.value = _settings.value.copy(language = language)
    }

    fun setSpeechRate(rate: Float) {
        val clamped = rate.coerceIn(0.5f, 2.0f)
        prefs.edit().putFloat(KEY_SPEECH_RATE, clamped).apply()
        _settings.value = _settings.value.copy(speechRate = clamped)
    }

    fun setPitch(pitch: Float) {
        val clamped = pitch.coerceIn(0.5f, 2.0f)
        prefs.edit().putFloat(KEY_PITCH, clamped).apply()
        _settings.value = _settings.value.copy(pitch = clamped)
    }

    fun setAutoSpeakResponses(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_SPEAK, enabled).apply()
        _settings.value = _settings.value.copy(autoSpeakResponses = enabled)
    }

    fun setContinuousSession(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CONTINUOUS, enabled).apply()
        _settings.value = _settings.value.copy(continuousSession = enabled)
    }

    companion object {
        private const val KEY_LANGUAGE = "voice_language"
        private const val KEY_SPEECH_RATE = "speech_rate"
        private const val KEY_PITCH = "pitch"
        private const val KEY_AUTO_SPEAK = "auto_speak_responses"
        private const val KEY_CONTINUOUS = "continuous_session"
    }
}
