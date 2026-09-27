package com.example.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

enum class LanguageAvailabilityStatus {
    AVAILABLE,
    MISSING_DATA,
    NOT_SUPPORTED,
    ENGINE_NOT_READY
}

class TextToSpeechManager(
    private val context: Context,
    private val onSpeakingStateChanged: (Boolean, String?) -> Unit
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var pendingSpeechText: String? = null
    private var activeLanguage: Locale = Locale.US
    private var currentSpeechRate: Float = 1.0f
    private var currentPitch: Float = 1.0f

    init {
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (_: Exception) {
            isInitialized = false
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    onSpeakingStateChanged(true, utteranceId)
                }

                override fun onDone(utteranceId: String?) {
                    onSpeakingStateChanged(false, utteranceId)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    onSpeakingStateChanged(false, utteranceId)
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    onSpeakingStateChanged(false, utteranceId)
                }
            })

            // Apply current parameters
            applyRateAndPitch()

            pendingSpeechText?.let {
                speak(it)
                pendingSpeechText = null
            }
        } else {
            isInitialized = false
        }
    }

    fun isEngineReady(): Boolean = isInitialized && tts != null

    fun checkLanguageAvailability(voiceLanguage: VoiceLanguage): LanguageAvailabilityStatus {
        val ttsInstance = tts ?: return LanguageAvailabilityStatus.ENGINE_NOT_READY
        val targetLocale = toLocale(voiceLanguage)

        return try {
            val availability = ttsInstance.isLanguageAvailable(targetLocale)
            when (availability) {
                TextToSpeech.LANG_AVAILABLE,
                TextToSpeech.LANG_COUNTRY_AVAILABLE,
                TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> LanguageAvailabilityStatus.AVAILABLE
                TextToSpeech.LANG_MISSING_DATA -> LanguageAvailabilityStatus.MISSING_DATA
                TextToSpeech.LANG_NOT_SUPPORTED -> LanguageAvailabilityStatus.NOT_SUPPORTED
                else -> LanguageAvailabilityStatus.NOT_SUPPORTED
            }
        } catch (_: Exception) {
            LanguageAvailabilityStatus.NOT_SUPPORTED
        }
    }

    fun setLanguage(voiceLanguage: VoiceLanguage): Boolean {
        val targetLocale = toLocale(voiceLanguage)
        val ttsInstance = tts ?: run {
            activeLanguage = targetLocale
            return false
        }

        return try {
            val availability = ttsInstance.isLanguageAvailable(targetLocale)
            if (availability >= TextToSpeech.LANG_AVAILABLE) {
                ttsInstance.language = targetLocale
                activeLanguage = targetLocale
                true
            } else {
                // Graceful fallback to Locale.US if the requested language (e.g. Bengali) lacks engine voice data
                ttsInstance.language = Locale.US
                activeLanguage = Locale.US
                false
            }
        } catch (_: Exception) {
            activeLanguage = Locale.US
            false
        }
    }

    fun setSpeechRate(rate: Float) {
        currentSpeechRate = rate.coerceIn(0.5f, 2.0f)
        tts?.setSpeechRate(currentSpeechRate)
    }

    fun setPitch(pitch: Float) {
        currentPitch = pitch.coerceIn(0.5f, 2.0f)
        tts?.setPitch(currentPitch)
    }

    private fun applyRateAndPitch() {
        tts?.setSpeechRate(currentSpeechRate)
        tts?.setPitch(currentPitch)
    }

    fun speak(text: String, queueMode: Int = TextToSpeech.QUEUE_FLUSH) {
        val clean = text.trim()
        if (clean.isBlank()) return

        if (!isInitialized || tts == null) {
            pendingSpeechText = clean
            return
        }

        val utteranceId = UUID.randomUUID().toString()
        val params = Bundle()
        try {
            tts?.speak(clean, queueMode, params, utteranceId)
        } catch (_: Exception) {
            onSpeakingStateChanged(false, utteranceId)
        }
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (_: Exception) {}
        onSpeakingStateChanged(false, null)
    }

    fun shutdown() {
        stop()
        try {
            tts?.shutdown()
        } catch (_: Exception) {}
        tts = null
        isInitialized = false
    }

    private fun toLocale(voiceLanguage: VoiceLanguage): Locale {
        return when (voiceLanguage) {
            VoiceLanguage.ENGLISH_US -> Locale.US
            VoiceLanguage.BENGALI_BD -> Locale.forLanguageTag("bn-BD")
            VoiceLanguage.BENGALI_IN -> Locale.forLanguageTag("bn-IN")
        }
    }
}
