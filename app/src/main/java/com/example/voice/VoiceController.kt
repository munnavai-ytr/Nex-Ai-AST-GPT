package com.example.voice

import android.content.Context
import com.example.security.SecurityManager
import com.example.security.voice.SpeakerVerificationStatus
import kotlinx.coroutines.flow.StateFlow

class VoiceController(
    private val context: Context,
    val permissionManager: VoicePermissionManager,
    val voiceStateManager: VoiceStateManager = VoiceStateManager(),
    val settingsRepository: VoiceSettingsRepository = VoiceSettingsRepository(context),
    var securityManager: SecurityManager? = null,
    private val onCommandReceived: (String) -> Unit
) {

    val voiceState: StateFlow<VoiceState> = voiceStateManager.voiceState
    val transcripts: StateFlow<List<VoiceTranscriptItem>> = voiceStateManager.transcripts
    val settings: StateFlow<VoiceSettings> = settingsRepository.settings

    val wakeWordManager: WakeWordManager = SystemWakeWordManager(context, permissionManager)

    val speechRecognizerManager: SpeechRecognizerManager = SpeechRecognizerManager(
        context = context,
        onVoiceStateChange = { state ->
            voiceStateManager.setState(state)
        },
        onFinalResult = { rawTranscript, audioBytes ->
            // Clean wake-word prefixes if user said "Hey NEX", "হেই নেক্স", "NEX", etc.
            val cleanedTranscript = stripWakePrefix(rawTranscript)
            voiceStateManager.addUserTranscript(rawTranscript)
            voiceStateManager.setState(VoiceState.Processing)

            // Owner Voice Verification Pipeline
            val secMgr = securityManager
            if (secMgr != null && secMgr.securityState.value.isVoiceAuthEnabled &&
                secMgr.securityState.value.isVoiceEnrolled && audioBytes.size >= 1600
            ) {
                val verificationResult = secMgr.verifySpeakerVoice(audioBytes)
                if (verificationResult.status == SpeakerVerificationStatus.REJECTED) {
                    val rejectionMsg = "Voice authentication rejected: speaker mismatch. Not recognized as owner."
                    voiceStateManager.setState(VoiceState.Error(rejectionMsg))
                    speak("Voice authentication failed. Speaker characteristics do not match the enrolled owner profile.")
                    return@SpeechRecognizerManager
                }
            }

            onCommandReceived(cleanedTranscript)
        }
    )

    val ttsManager: TextToSpeechManager = TextToSpeechManager(
        context = context,
        onSpeakingStateChanged = { isSpeaking, _ ->
            if (isSpeaking) {
                if (voiceStateManager.voiceState.value !is VoiceState.Listening) {
                    val currentNexResponse = voiceStateManager.lastResponseText.value
                    voiceStateManager.setState(
                        VoiceState.Speaking(currentNexResponse.ifBlank { "NEX is speaking" })
                    )
                }
            } else {
                if (voiceStateManager.voiceState.value is VoiceState.Speaking) {
                    voiceStateManager.setState(VoiceState.Idle)
                }
            }
        }
    )

    init {
        // Sync initial persisted TTS configuration
        val initialSettings = settingsRepository.settings.value
        ttsManager.setLanguage(initialSettings.language)
        ttsManager.setSpeechRate(initialSettings.speechRate)
        ttsManager.setPitch(initialSettings.pitch)
    }

    fun startListening(): Boolean {
        if (!permissionManager.checkPermission()) {
            voiceStateManager.setState(
                VoiceState.PermissionRequired(permissionManager.rationaleExplanation)
            )
            return false
        }

        if (!speechRecognizerManager.isRecognitionAvailable()) {
            voiceStateManager.setState(
                VoiceState.Unavailable("Android SpeechRecognizer is not available on this device.")
            )
            return false
        }

        ttsManager.stop()
        val currentLang = settingsRepository.settings.value.language
        speechRecognizerManager.startListening(currentLang)
        return true
    }

    fun stopListening() {
        speechRecognizerManager.stopListening()
        if (voiceStateManager.voiceState.value is VoiceState.Listening) {
            voiceStateManager.setState(VoiceState.Idle)
        }
    }

    fun speak(text: String, force: Boolean = false) {
        if (force || settingsRepository.settings.value.autoSpeakResponses) {
            ttsManager.speak(text)
        }
    }

    fun stopSpeech() {
        ttsManager.stop()
    }

    fun onResponseGenerated(spokenResponse: String) {
        val clean = spokenResponse.trim()
        if (clean.isNotBlank()) {
            voiceStateManager.addNexResponse(clean)
            speak(clean)
        }
    }

    fun setLanguage(language: VoiceLanguage) {
        settingsRepository.setLanguage(language)
        ttsManager.setLanguage(language)
    }

    fun setSpeechRate(rate: Float) {
        settingsRepository.setSpeechRate(rate)
        ttsManager.setSpeechRate(rate)
    }

    fun setPitch(pitch: Float) {
        settingsRepository.setPitch(pitch)
        ttsManager.setPitch(pitch)
    }

    fun setAutoSpeakResponses(autoSpeak: Boolean) {
        settingsRepository.setAutoSpeakResponses(autoSpeak)
    }

    fun destroy() {
        stopListening()
        stopSpeech()
        speechRecognizerManager.destroy()
        ttsManager.shutdown()
        wakeWordManager.stopListeningForWakeWord()
    }

    private fun stripWakePrefix(raw: String): String {
        var text = raw.trim()
        val prefixes = listOf(
            "hey nex", "hey nex,", "nex", "nex,",
            "হেই নেক্স", "হেই নেক্স,", "নেক্স", "নেক্স,"
        )
        for (prefix in prefixes) {
            if (text.startsWith(prefix, ignoreCase = true)) {
                text = text.substring(prefix.length).trim().removePrefix(",").trim()
                break
            }
        }
        return if (text.isBlank()) raw else text
    }
}
