package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean

class SpeechRecognizerManager(
    private val context: Context,
    private val onVoiceStateChange: (VoiceState) -> Unit,
    private val onFinalResult: (String, ByteArray) -> Unit
) {

    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isCurrentlyListening = false
    private val hasDeliveredFinalResult = AtomicBoolean(false)
    private val recordedAudioStream = ByteArrayOutputStream()

    // Secondary constructor for single-parameter string callback
    constructor(
        context: Context,
        onVoiceStateChange: (VoiceState) -> Unit,
        onLegacyResult: (String) -> Unit
    ) : this(
        context = context,
        onVoiceStateChange = onVoiceStateChange,
        onFinalResult = { text, _ -> onLegacyResult(text) }
    )

    fun isRecognitionAvailable(): Boolean {
        return try {
            SpeechRecognizer.isRecognitionAvailable(context)
        } catch (_: Exception) {
            false
        }
    }

    fun isListening(): Boolean = isCurrentlyListening

    fun startListening(language: VoiceLanguage = VoiceLanguage.ENGLISH_US) {
        mainHandler.post {
            if (!isRecognitionAvailable()) {
                isCurrentlyListening = false
                onVoiceStateChange(VoiceState.Unavailable("Android SpeechRecognizer is not available on this device."))
                return@post
            }

            // Clean up any ongoing or prior session cleanly
            cleanupRecognizer()
            hasDeliveredFinalResult.set(false)
            recordedAudioStream.reset()

            try {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            isCurrentlyListening = true
                            recordedAudioStream.reset()
                            onVoiceStateChange(VoiceState.Listening(0f, ""))
                        }

                        override fun onBeginningOfSpeech() {
                            isCurrentlyListening = true
                            onVoiceStateChange(VoiceState.Listening(0f, ""))
                        }

                        override fun onRmsChanged(rmsdB: Float) {
                            if (isCurrentlyListening) {
                                onVoiceStateChange(VoiceState.Listening(rmsdB.coerceAtLeast(0f), ""))
                            }
                        }

                        override fun onBufferReceived(buffer: ByteArray?) {
                            if (buffer != null && buffer.isNotEmpty()) {
                                recordedAudioStream.write(buffer, 0, buffer.size)
                            }
                        }

                        override fun onEndOfSpeech() {
                            isCurrentlyListening = false
                            onVoiceStateChange(VoiceState.Processing)
                        }

                        override fun onError(error: Int) {
                            isCurrentlyListening = false
                            hasDeliveredFinalResult.set(true)
                            val errorMessage = when (error) {
                                SpeechRecognizer.ERROR_AUDIO -> "Microphone audio recording error. Please check your audio input."
                                SpeechRecognizer.ERROR_CLIENT -> "Speech recognition client error ($error)"
                                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                                    onVoiceStateChange(
                                        VoiceState.PermissionRequired(
                                            "Microphone permission is required to capture speech."
                                        )
                                    )
                                    return
                                }
                                SpeechRecognizer.ERROR_NETWORK -> "Network error during speech recognition. Check your internet connection."
                                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition network connection timed out."
                                SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Please speak again."
                                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognition engine is currently busy. Please retry."
                                SpeechRecognizer.ERROR_SERVER -> "Recognition server error occurred."
                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected before timeout. Please try again."
                                else -> "Speech recognition error ($error)"
                            }
                            onVoiceStateChange(VoiceState.Error(errorMessage, error))
                        }

                        override fun onResults(results: Bundle?) {
                            isCurrentlyListening = false
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val transcript = matches?.firstOrNull()?.trim() ?: ""
                            val audioBytes = recordedAudioStream.toByteArray()

                            // Guarantee exact-once delivery of recognized speech to AI
                            if (transcript.isNotBlank()) {
                                if (hasDeliveredFinalResult.compareAndSet(false, true)) {
                                    onVoiceStateChange(VoiceState.Processing)
                                    onFinalResult(transcript, audioBytes)
                                }
                            } else {
                                if (hasDeliveredFinalResult.compareAndSet(false, true)) {
                                    onVoiceStateChange(VoiceState.Error("No intelligible words captured. Please speak again.", SpeechRecognizer.ERROR_NO_MATCH))
                                }
                            }
                        }

                        override fun onPartialResults(partialResults: Bundle?) {
                            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val partial = matches?.firstOrNull()?.trim() ?: ""
                            if (partial.isNotBlank() && isCurrentlyListening) {
                                onVoiceStateChange(VoiceState.Listening(2.5f, partial))
                            }
                        }

                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.speechTag)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language.speechTag)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                }

                isCurrentlyListening = true
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                isCurrentlyListening = false
                hasDeliveredFinalResult.set(true)
                onVoiceStateChange(VoiceState.Error(e.message ?: "Failed to initialize SpeechRecognizer"))
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            isCurrentlyListening = false
            hasDeliveredFinalResult.set(true)
            try {
                speechRecognizer?.stopListening()
            } catch (_: Exception) {}
        }
    }

    private fun cleanupRecognizer() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
        isCurrentlyListening = false
    }

    fun destroy() {
        mainHandler.post {
            cleanupRecognizer()
        }
    }
}
