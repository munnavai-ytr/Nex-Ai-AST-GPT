package com.example.security.voice

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import kotlin.math.sqrt

sealed class EnrollmentStepState {
    object ConsentRequired : EnrollmentStepState()
    data class ReadyToRecord(val sampleIndex: Int, val totalSamples: Int, val challengePhrase: String) : EnrollmentStepState()
    data class Recording(val sampleIndex: Int, val totalSamples: Int, val challengePhrase: String, val audioLevelRms: Float) : EnrollmentStepState()
    data class SampleProcessing(val sampleIndex: Int, val totalSamples: Int) : EnrollmentStepState()
    data class SampleAccepted(val sampleIndex: Int, val totalSamples: Int, val qualityScore: Float) : EnrollmentStepState()
    data class SampleRejected(val sampleIndex: Int, val totalSamples: Int, val reason: String) : EnrollmentStepState()
    data class Completed(val totalSamples: Int, val averageQuality: Float, val timestamp: Long) : EnrollmentStepState()
    data class Error(val message: String) : EnrollmentStepState()
}

data class OwnerAuthState(
    val isEnrolled: Boolean = false,
    val isVoiceAuthEnabled: Boolean = true,
    val enrollmentMetadata: VoiceEnrollmentMetadata? = null,
    val currentStep: EnrollmentStepState = EnrollmentStepState.ConsentRequired
)

class OwnerAuthenticationManager(
    private val context: Context,
    val storage: VoiceEnrollmentStorage = VoiceEnrollmentStorage(context),
    val speakerVerificationManager: SpeakerVerificationManager = SpeakerVerificationManager(context, storage),
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {

    companion object {
        const val REQUIRED_SAMPLES_COUNT = 4
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        val CHALLENGE_PHRASES = listOf(
            "Hey NEX, activate personal assistant",
            "Confirm my voice signature for secure automation",
            "NEX, prepare autonomous task pipeline",
            "Verify owner identity for Android operations"
        )
    }

    private val _authState = MutableStateFlow(
        OwnerAuthState(
            isEnrolled = storage.isEnrolled(),
            isVoiceAuthEnabled = true,
            enrollmentMetadata = storage.getMetadata(),
            currentStep = if (storage.isEnrolled()) {
                val meta = storage.getMetadata()
                EnrollmentStepState.Completed(meta.sampleCount, meta.averageAudioQuality, meta.enrollmentTimestamp)
            } else {
                EnrollmentStepState.ConsentRequired
            }
        )
    )
    val authState: StateFlow<OwnerAuthState> = _authState.asStateFlow()

    private val collectedEmbeddings = mutableListOf<FloatArray>()
    private val collectedQualityScores = mutableListOf<Float>()
    private val collectedPhrases = mutableListOf<String>()

    private var recordingJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private val recordedAudioStream = ByteArrayOutputStream()

    fun refreshState() {
        val enrolled = storage.isEnrolled()
        val meta = storage.getMetadata()
        _authState.value = _authState.value.copy(
            isEnrolled = enrolled,
            enrollmentMetadata = meta,
            currentStep = if (enrolled) {
                EnrollmentStepState.Completed(meta.sampleCount, meta.averageAudioQuality, meta.enrollmentTimestamp)
            } else {
                _authState.value.currentStep
            }
        )
    }

    fun startEnrollment(consentGiven: Boolean) {
        if (!consentGiven) {
            _authState.value = _authState.value.copy(currentStep = EnrollmentStepState.ConsentRequired)
            return
        }

        collectedEmbeddings.clear()
        collectedQualityScores.clear()
        collectedPhrases.clear()

        _authState.value = _authState.value.copy(
            currentStep = EnrollmentStepState.ReadyToRecord(
                sampleIndex = 1,
                totalSamples = REQUIRED_SAMPLES_COUNT,
                challengePhrase = CHALLENGE_PHRASES[0]
            )
        )
    }

    @SuppressLint("MissingPermission")
    fun startSampleRecording(): Boolean {
        val currentState = _authState.value.currentStep
        val (sampleIdx, total, phrase) = when (currentState) {
            is EnrollmentStepState.ReadyToRecord -> Triple(currentState.sampleIndex, currentState.totalSamples, currentState.challengePhrase)
            is EnrollmentStepState.SampleRejected -> Triple(currentState.sampleIndex, currentState.totalSamples, CHALLENGE_PHRASES[(currentState.sampleIndex - 1) % CHALLENGE_PHRASES.size])
            is EnrollmentStepState.SampleAccepted -> {
                val nextIdx = currentState.sampleIndex + 1
                if (nextIdx > REQUIRED_SAMPLES_COUNT) return false
                Triple(nextIdx, REQUIRED_SAMPLES_COUNT, CHALLENGE_PHRASES[(nextIdx - 1) % CHALLENGE_PHRASES.size])
            }
            else -> return false
        }

        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (bufferSize <= 0) {
            _authState.value = _authState.value.copy(
                currentStep = EnrollmentStepState.Error("Invalid AudioRecord buffer size on this hardware.")
            )
            return false
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize * 2
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                _authState.value = _authState.value.copy(
                    currentStep = EnrollmentStepState.Error("AudioRecord could not be initialized.")
                )
                return false
            }

            audioRecord?.startRecording()
            recordedAudioStream.reset()

            _authState.value = _authState.value.copy(
                currentStep = EnrollmentStepState.Recording(
                    sampleIndex = sampleIdx,
                    totalSamples = total,
                    challengePhrase = phrase,
                    audioLevelRms = 0f
                )
            )

            recordingJob = coroutineScope.launch(Dispatchers.IO) {
                val buffer = ByteArray(bufferSize)
                while (isActive && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        recordedAudioStream.write(buffer, 0, read)

                        // Compute instantaneous RMS for UI visualizer
                        var sum = 0.0
                        var sampleCount = 0
                        var i = 0
                        while (i < read - 1) {
                            val b1 = buffer[i].toInt() and 0xFF
                            val b2 = buffer[i + 1].toInt()
                            val sample = (b2 shl 8) or b1
                            sum += sample * sample
                            sampleCount++
                            i += 2
                        }
                        val rms = if (sampleCount > 0) sqrt(sum / sampleCount).toFloat() / Short.MAX_VALUE else 0f

                        val activeState = _authState.value.currentStep
                        if (activeState is EnrollmentStepState.Recording) {
                            _authState.value = _authState.value.copy(
                                currentStep = activeState.copy(audioLevelRms = rms.coerceIn(0f, 1f))
                            )
                        }
                    }
                }
            }
            return true
        } catch (e: Exception) {
            _authState.value = _authState.value.copy(
                currentStep = EnrollmentStepState.Error("Microphone access failed: ${e.message}")
            )
            return false
        }
    }

    fun stopSampleRecordingAndProcess() {
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
        recordingJob?.cancel()
        recordingJob = null

        val pcmBytes = recordedAudioStream.toByteArray()
        val currentState = _authState.value.currentStep
        val sampleIdx = when (currentState) {
            is EnrollmentStepState.Recording -> currentState.sampleIndex
            else -> return
        }

        _authState.value = _authState.value.copy(
            currentStep = EnrollmentStepState.SampleProcessing(sampleIdx, REQUIRED_SAMPLES_COUNT)
        )

        coroutineScope.launch {
            val quality = AudioFeatureExtractor.analyzeAudioQuality(pcmBytes)
            if (!quality.isAcceptableQuality) {
                _authState.value = _authState.value.copy(
                    currentStep = EnrollmentStepState.SampleRejected(
                        sampleIndex = sampleIdx,
                        totalSamples = REQUIRED_SAMPLES_COUNT,
                        reason = quality.rejectionReason ?: "Audio sample failed acoustic quality check."
                    )
                )
                return@launch
            }

            val embedding = AudioFeatureExtractor.extractSpeakerEmbedding(pcmBytes)
            if (embedding == null) {
                _authState.value = _authState.value.copy(
                    currentStep = EnrollmentStepState.SampleRejected(
                        sampleIndex = sampleIdx,
                        totalSamples = REQUIRED_SAMPLES_COUNT,
                        reason = "Could not compute acoustic speaker representation from sample."
                    )
                )
                return@launch
            }

            collectedEmbeddings.add(embedding)
            collectedQualityScores.add(quality.snrDb)
            collectedPhrases.add(CHALLENGE_PHRASES[(sampleIdx - 1) % CHALLENGE_PHRASES.size])

            if (sampleIdx < REQUIRED_SAMPLES_COUNT) {
                _authState.value = _authState.value.copy(
                    currentStep = EnrollmentStepState.SampleAccepted(
                        sampleIndex = sampleIdx,
                        totalSamples = REQUIRED_SAMPLES_COUNT,
                        qualityScore = quality.snrDb
                    )
                )
            } else {
                // Finalize enrollment
                val finalized = finalizeEnrollmentInternal()
                if (finalized) {
                    val meta = storage.getMetadata()
                    _authState.value = _authState.value.copy(
                        isEnrolled = true,
                        enrollmentMetadata = meta,
                        currentStep = EnrollmentStepState.Completed(
                            totalSamples = REQUIRED_SAMPLES_COUNT,
                            averageQuality = meta.averageAudioQuality,
                            timestamp = meta.enrollmentTimestamp
                        )
                    )
                } else {
                    _authState.value = _authState.value.copy(
                        currentStep = EnrollmentStepState.Error("Failed to encrypt and save enrolled profile to Keystore.")
                    )
                }
            }
        }
    }

    private fun finalizeEnrollmentInternal(): Boolean {
        if (collectedEmbeddings.isEmpty()) return false

        // Compute centroid (mean vector) across all enrolled voice samples
        val dim = collectedEmbeddings[0].size
        val centroid = FloatArray(dim)
        for (emb in collectedEmbeddings) {
            for (i in 0 until dim) {
                centroid[i] += emb[i]
            }
        }
        for (i in 0 until dim) {
            centroid[i] /= collectedEmbeddings.size
        }

        // L2 normalize centroid
        var normSq = 0f
        for (v in centroid) normSq += v * v
        val norm = sqrt(normSq)
        if (norm > 1e-8f) {
            for (i in centroid.indices) {
                centroid[i] /= norm
            }
        }

        val avgQuality = if (collectedQualityScores.isNotEmpty()) collectedQualityScores.average().toFloat() else 10f
        return storage.saveEnrolledProfile(
            embedding = centroid,
            sampleCount = collectedEmbeddings.size,
            averageQuality = avgQuality,
            phrases = collectedPhrases.toList()
        )
    }

    fun deleteEnrollment(): Boolean {
        val deleted = storage.deleteEnrollment()
        collectedEmbeddings.clear()
        collectedQualityScores.clear()
        collectedPhrases.clear()
        _authState.value = OwnerAuthState(
            isEnrolled = false,
            isVoiceAuthEnabled = _authState.value.isVoiceAuthEnabled,
            enrollmentMetadata = null,
            currentStep = EnrollmentStepState.ConsentRequired
        )
        return deleted
    }

    fun setVoiceAuthEnabled(enabled: Boolean) {
        _authState.value = _authState.value.copy(isVoiceAuthEnabled = enabled)
    }
}
