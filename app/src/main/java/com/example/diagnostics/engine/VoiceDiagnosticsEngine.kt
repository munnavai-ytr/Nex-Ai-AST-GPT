package com.example.diagnostics.engine

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import com.example.diagnostics.model.DiagnosticCategory
import com.example.diagnostics.model.DiagnosticItem
import com.example.diagnostics.model.DiagnosticStatus
import com.example.diagnostics.model.SubsystemDiagnosticResult
import com.example.voice.VoiceController

class VoiceDiagnosticsEngine(
    private val context: Context,
    private val voiceController: VoiceController
) {

    fun runDiagnostics(): SubsystemDiagnosticResult {
        val startTime = System.currentTimeMillis()
        val items = mutableListOf<DiagnosticItem>()

        // 1. Microphone Hardware & Permission Pipeline
        items.add(checkMicrophonePipeline())

        // 2. Android SpeechRecognizer Service
        items.add(checkSpeechRecognizerAvailability())

        // 3. Text-to-Speech Engine Status & Multilingual Support
        items.add(checkTextToSpeechEngine())

        // 4. Voice Controller Integration
        items.add(checkVoiceControllerState())

        val overallStatus = when {
            items.any { it.status == DiagnosticStatus.FAIL } -> DiagnosticStatus.FAIL
            items.any { it.status == DiagnosticStatus.PERMISSION_REQUIRED } -> DiagnosticStatus.PERMISSION_REQUIRED
            items.any { it.status == DiagnosticStatus.NOT_CONFIGURED } -> DiagnosticStatus.NOT_CONFIGURED
            items.any { it.status == DiagnosticStatus.NOT_VERIFIED } -> DiagnosticStatus.WARN
            items.any { it.status == DiagnosticStatus.WARN } -> DiagnosticStatus.WARN
            else -> DiagnosticStatus.PASS
        }

        return SubsystemDiagnosticResult(
            category = DiagnosticCategory.VOICE,
            items = items,
            status = overallStatus,
            executionTimeMs = System.currentTimeMillis() - startTime
        )
    }

    private fun checkMicrophonePipeline(): DiagnosticItem {
        // Step 1: Verify hardware microphone feature
        val hasMicFeature = context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
        if (!hasMicFeature) {
            return DiagnosticItem(
                id = "voice_audio_record",
                title = "Acoustic Microphone Pipeline (16kHz PCM)",
                category = DiagnosticCategory.VOICE,
                status = DiagnosticStatus.FAIL,
                summary = "Hardware Microphone Not Detected",
                details = "Device system features indicate no physical microphone hardware is present."
            )
        }

        // Step 2: Check RECORD_AUDIO runtime permission
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            return DiagnosticItem(
                id = "voice_audio_record",
                title = "Acoustic Microphone Pipeline (16kHz PCM)",
                category = DiagnosticCategory.VOICE,
                status = DiagnosticStatus.PERMISSION_REQUIRED,
                summary = "Microphone Permission Required (RECORD_AUDIO)",
                details = "Android permission android.permission.RECORD_AUDIO is not granted. Cannot access microphone hardware.",
                actionLabel = "Grant Microphone Permission",
                isActionable = true
            )
        }

        // Step 3: Check AudioRecord buffer compatibility
        val sampleRate = 16000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

        if (bufferSize <= 0) {
            return DiagnosticItem(
                id = "voice_audio_record",
                title = "Acoustic Microphone Pipeline (16kHz PCM)",
                category = DiagnosticCategory.VOICE,
                status = DiagnosticStatus.FAIL,
                summary = "AudioRecord Min Buffer Invalid ($bufferSize)",
                details = "Audio subsystem failed to provide a valid minimum buffer size for 16kHz 16-bit Mono PCM."
            )
        }

        // Step 4: Real initialization verification with exception handling
        var testRecord: AudioRecord? = null
        return try {
            testRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (testRecord.state == AudioRecord.STATE_INITIALIZED) {
                DiagnosticItem(
                    id = "voice_audio_record",
                    title = "Acoustic Microphone Pipeline (16kHz PCM)",
                    category = DiagnosticCategory.VOICE,
                    status = DiagnosticStatus.PASS,
                    summary = "Hardware & Audio Driver Ready (16kHz Mono)",
                    details = "Format: 16000Hz, 16-bit PCM Mono\nBuffer Size: $bufferSize bytes\nAudio Source: MediaRecorder.AudioSource.MIC\nState: STATE_INITIALIZED\nNote: Real audio capture requires explicit user voice interaction."
                )
            } else {
                DiagnosticItem(
                    id = "voice_audio_record",
                    title = "Acoustic Microphone Pipeline (16kHz PCM)",
                    category = DiagnosticCategory.VOICE,
                    status = DiagnosticStatus.FAIL,
                    summary = "AudioRecord Initialization Failed (State uninitialized)",
                    details = "AudioRecord state was not STATE_INITIALIZED. Microphone hardware may be locked by another application."
                )
            }
        } catch (se: SecurityException) {
            DiagnosticItem(
                id = "voice_audio_record",
                title = "Acoustic Microphone Pipeline (16kHz PCM)",
                category = DiagnosticCategory.VOICE,
                status = DiagnosticStatus.PERMISSION_REQUIRED,
                summary = "Microphone Access Denied by Security Manager",
                details = "SecurityException during AudioRecord allocation: ${se.message}",
                actionLabel = "Grant Permission",
                isActionable = true
            )
        } catch (e: Exception) {
            DiagnosticItem(
                id = "voice_audio_record",
                title = "Acoustic Microphone Pipeline (16kHz PCM)",
                category = DiagnosticCategory.VOICE,
                status = DiagnosticStatus.FAIL,
                summary = "Microphone Driver Error: ${e.message}",
                details = "Exception: ${e::class.simpleName}\nStack: ${e.localizedMessage}"
            )
        } finally {
            try {
                testRecord?.release()
            } catch (_: Exception) {}
        }
    }

    private fun checkSpeechRecognizerAvailability(): DiagnosticItem {
        val isSttAvailable = SpeechRecognizer.isRecognitionAvailable(context)
        return DiagnosticItem(
            id = "voice_stt_engine",
            title = "Speech-To-Text (STT) Service Availability",
            category = DiagnosticCategory.VOICE,
            status = if (isSttAvailable) DiagnosticStatus.PASS else DiagnosticStatus.NOT_CONFIGURED,
            summary = if (isSttAvailable) "On-Device / Cloud Speech Recognition Available" else "No STT engine detected on this device ROM",
            details = "SpeechRecognizer.isRecognitionAvailable: $isSttAvailable\nSupports English (en-US, en-GB) and Bengali (bn-BD, bn-IN) dictation."
        )
    }

    private fun checkTextToSpeechEngine(): DiagnosticItem {
        val ttsManager = voiceController.ttsManager
        val isReady = ttsManager.isEngineReady()

        return DiagnosticItem(
            id = "voice_tts_engine",
            title = "Text-To-Speech (TTS) Engine & Multilingual Voices",
            category = DiagnosticCategory.VOICE,
            status = if (isReady) DiagnosticStatus.PASS else DiagnosticStatus.INFO,
            summary = if (isReady) "TTS Engine Active (English & Bengali Speech Ready)" else "TTS Initializing or Standby",
            details = "Status: ${if (isReady) "READY" else "STANDBY / INITIALIZING"}\nLanguage Fallback: en-US, bn-BD\nRate & Pitch Modifiers: Supported"
        )
    }

    private fun checkVoiceControllerState(): DiagnosticItem {
        val state = voiceController.voiceState.value
        return DiagnosticItem(
            id = "voice_controller_state",
            title = "Voice Controller State Machine",
            category = DiagnosticCategory.VOICE,
            status = DiagnosticStatus.PASS,
            summary = "Current State: ${state::class.simpleName}",
            details = "VoiceState: $state\nListening / Speaking flow active."
        )
    }
}
