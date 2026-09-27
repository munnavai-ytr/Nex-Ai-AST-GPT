package com.example.voice

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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

enum class TTSInitStatus {
    INITIALIZING,
    READY,
    FAILED,
    SHUTDOWN
}

class TextToSpeechManager(
    private val context: Context,
    private val onSpeakingStateChanged: (Boolean, String?) -> Unit
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var initStatus: TTSInitStatus = TTSInitStatus.INITIALIZING
    private var pendingSpeechText: String? = null
    private var activeLanguage: Locale = Locale.US
    private var currentSpeechRate: Float = 1.0f
    private var currentPitch: Float = 1.0f
    private var currentActiveUtteranceId: String? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (_: Exception) {
            initStatus = TTSInitStatus.FAILED
        }
    }

    override fun onInit(status: Int) {
        mainHandler.post {
            if (status == TextToSpeech.SUCCESS) {
                initStatus = TTSInitStatus.READY
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        currentActiveUtteranceId = utteranceId
                        mainHandler.post {
                            onSpeakingStateChanged(true, utteranceId)
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        if (currentActiveUtteranceId == utteranceId) {
                            currentActiveUtteranceId = null
                        }
                        mainHandler.post {
                            onSpeakingStateChanged(false, utteranceId)
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        if (currentActiveUtteranceId == utteranceId) {
                            currentActiveUtteranceId = null
                        }
                        mainHandler.post {
                            onSpeakingStateChanged(false, utteranceId)
                        }
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        if (currentActiveUtteranceId == utteranceId) {
                            currentActiveUtteranceId = null
                        }
                        mainHandler.post {
                            onSpeakingStateChanged(false, utteranceId)
                        }
                    }
                })

                // Apply parameters
                applyRateAndPitch()

                val textToSpeak = pendingSpeechText
                pendingSpeechText = null
                if (!textToSpeak.isNullOrBlank()) {
                    speak(textToSpeak)
                }
            } else {
                initStatus = TTSInitStatus.FAILED
                pendingSpeechText = null
                onSpeakingStateChanged(false, null)
            }
        }
    }

    fun isEngineReady(): Boolean = initStatus == TTSInitStatus.READY && tts != null

    fun getInitStatus(): TTSInitStatus = initStatus

    fun checkLanguageAvailability(voiceLanguage: VoiceLanguage): LanguageAvailabilityStatus {
        val ttsInstance = tts ?: return LanguageAvailabilityStatus.ENGINE_NOT_READY
        if (initStatus != TTSInitStatus.READY) return LanguageAvailabilityStatus.ENGINE_NOT_READY
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

        if (initStatus != TTSInitStatus.READY) {
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
                // Graceful fallback to Locale.US if the requested language lacks voice data
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
        if (initStatus == TTSInitStatus.READY) {
            tts?.setSpeechRate(currentSpeechRate)
        }
    }

    fun setPitch(pitch: Float) {
        currentPitch = pitch.coerceIn(0.5f, 2.0f)
        if (initStatus == TTSInitStatus.READY) {
            tts?.setPitch(currentPitch)
        }
    }

    private fun applyRateAndPitch() {
        tts?.setSpeechRate(currentSpeechRate)
        tts?.setPitch(currentPitch)
    }

    /**
     * Speaks the provided text using QUEUE_FLUSH to cancel any overlapping prior speech.
     * Buffers latest response if initialization is still in progress.
     */
    fun speak(text: String, queueMode: Int = TextToSpeech.QUEUE_FLUSH): Boolean {
        val clean = text.trim()
        if (clean.isBlank()) return false

        if (initStatus == TTSInitStatus.INITIALIZING) {
            // Buffer the single latest response to speak once ready
            pendingSpeechText = clean
            return true
        }

        if (initStatus != TTSInitStatus.READY || tts == null) {
            return false
        }

        val utteranceId = UUID.randomUUID().toString()
        val params = Bundle()
        return try {
            val result = tts?.speak(clean, queueMode, params, utteranceId)
            if (result == TextToSpeech.SUCCESS) {
                currentActiveUtteranceId = utteranceId
                true
            } else {
                onSpeakingStateChanged(false, utteranceId)
                false
            }
        } catch (_: Exception) {
            onSpeakingStateChanged(false, utteranceId)
            false
        }
    }

    fun stop() {
        pendingSpeechText = null
        try {
            tts?.stop()
        } catch (_: Exception) {}
        currentActiveUtteranceId = null
        onSpeakingStateChanged(false, null)
    }

    fun shutdown() {
        stop()
        initStatus = TTSInitStatus.SHUTDOWN
        try {
            tts?.shutdown()
        } catch (_: Exception) {}
        tts = null
    }

    private fun toLocale(voiceLanguage: VoiceLanguage): Locale {
        return when (voiceLanguage) {
            VoiceLanguage.ENGLISH_US -> Locale.US
            VoiceLanguage.BENGALI_BD -> Locale.forLanguageTag("bn-BD")
            VoiceLanguage.BENGALI_IN -> Locale.forLanguageTag("bn-IN")
        }
    }
}
