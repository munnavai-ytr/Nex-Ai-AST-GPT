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

class SpeechRecognizerManager(
    private val context: Context,
    private val onVoiceStateChange: (VoiceState) -> Unit,
    private val onFinalResult: (String, ByteArray) -> Unit
) {

    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isCurrentlyListening = false
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
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    fun isListening(): Boolean = isCurrentlyListening

    fun startListening(language: VoiceLanguage = VoiceLanguage.ENGLISH_US) {
        mainHandler.post {
            if (!isRecognitionAvailable()) {
                onVoiceStateChange(VoiceState.Unavailable("Android SpeechRecognizer is not available on this device."))
                return@post
            }

            stopListening()
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
                            val errorMessage = when (error) {
                                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error. Check microphone."
                                SpeechRecognizer.ERROR_CLIENT -> "Client speech recognition error ($error)"
                                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                                    onVoiceStateChange(
                                        VoiceState.PermissionRequired(
                                            "Microphone permission is required to capture speech."
                                        )
                                    )
                                    return
                                }
                                SpeechRecognizer.ERROR_NETWORK -> "Network error during speech recognition"
                                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network connection timed out"
                                SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Please speak again."
                                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer engine is busy"
                                SpeechRecognizer.ERROR_SERVER -> "Recognition server error occurred"
                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected before timeout"
                                else -> "Speech recognition error ($error)"
                            }
                            onVoiceStateChange(VoiceState.Error(errorMessage, error))
                        }

                        override fun onResults(results: Bundle?) {
                            isCurrentlyListening = false
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val transcript = matches?.firstOrNull()?.trim() ?: ""
                            val audioBytes = recordedAudioStream.toByteArray()
                            if (transcript.isNotBlank()) {
                                onVoiceStateChange(VoiceState.Processing)
                                onFinalResult(transcript, audioBytes)
                            } else {
                                onVoiceStateChange(VoiceState.Error("No intelligible words captured."))
                            }
                        }

                        override fun onPartialResults(partialResults: Bundle?) {
                            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val partial = matches?.firstOrNull()?.trim() ?: ""
                            if (partial.isNotBlank()) {
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
                onVoiceStateChange(VoiceState.Error(e.message ?: "Failed to initialize SpeechRecognizer"))
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            isCurrentlyListening = false
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (_: Exception) {}
        }
    }

    fun destroy() {
        stopListening()
    }
}
