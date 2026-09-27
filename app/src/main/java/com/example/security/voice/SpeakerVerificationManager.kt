package com.example.security.voice

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SpeakerVerificationStatus {
    VERIFIED,
    REJECTED,
    INCONCLUSIVE,
    NOT_ENROLLED,
    MODEL_UNAVAILABLE,
    LOW_QUALITY_AUDIO,
    ERROR
}

data class SpeakerVerificationResult(
    val status: SpeakerVerificationStatus,
    val similarityScore: Float,
    val threshold: Float,
    val message: String,
    val audioQualityMetrics: AudioQualityMetrics? = null,
    val isBiometricallyCertified: Boolean = false,
    val modelType: String = "HEURISTIC_ACOUSTIC_DSP"
) {
    val isVerified: Boolean get() = status == SpeakerVerificationStatus.VERIFIED
}

class SpeakerVerificationManager(
    private val context: Context,
    private val enrollmentStorage: VoiceEnrollmentStorage
) {

    companion object {
        const val VERIFICATION_THRESHOLD = 0.80f
        const val INCONCLUSIVE_LOWER_THRESHOLD = 0.68f
        const val MODEL_NAME = "NEX Acoustic Speaker Embedder v1.0"
    }

    private val _isModelAvailable = MutableStateFlow(true)
    val isModelAvailable: StateFlow<Boolean> = _isModelAvailable.asStateFlow()

    fun isSpeakerEnrolled(): Boolean {
        return enrollmentStorage.isEnrolled()
    }

    fun verifySpeaker(pcm16Bytes: ByteArray): SpeakerVerificationResult {
        if (!_isModelAvailable.value) {
            return SpeakerVerificationResult(
                status = SpeakerVerificationStatus.MODEL_UNAVAILABLE,
                similarityScore = 0f,
                threshold = VERIFICATION_THRESHOLD,
                message = "Speaker verification model is unavailable on this device configuration."
            )
        }

        if (!enrollmentStorage.isEnrolled()) {
            return SpeakerVerificationResult(
                status = SpeakerVerificationStatus.NOT_ENROLLED,
                similarityScore = 0f,
                threshold = VERIFICATION_THRESHOLD,
                message = "Owner voice has not been enrolled yet. Please complete voice enrollment in Security settings."
            )
        }

        val quality = AudioFeatureExtractor.analyzeAudioQuality(pcm16Bytes)
        if (!quality.isAcceptableQuality) {
            return SpeakerVerificationResult(
                status = SpeakerVerificationStatus.LOW_QUALITY_AUDIO,
                similarityScore = 0f,
                threshold = VERIFICATION_THRESHOLD,
                message = quality.rejectionReason ?: "Audio quality is insufficient for speaker verification.",
                audioQualityMetrics = quality
            )
        }

        val enrolledEmbedding = enrollmentStorage.loadEnrolledEmbedding()
        if (enrolledEmbedding == null) {
            return SpeakerVerificationResult(
                status = SpeakerVerificationStatus.ERROR,
                similarityScore = 0f,
                threshold = VERIFICATION_THRESHOLD,
                message = "Failed to load enrolled speaker embedding from encrypted Keystore storage."
            )
        }

        val candidateEmbedding = AudioFeatureExtractor.extractSpeakerEmbedding(pcm16Bytes)
        if (candidateEmbedding == null) {
            return SpeakerVerificationResult(
                status = SpeakerVerificationStatus.LOW_QUALITY_AUDIO,
                similarityScore = 0f,
                threshold = VERIFICATION_THRESHOLD,
                message = "Could not extract acoustic speaker characteristics from audio sample.",
                audioQualityMetrics = quality
            )
        }

        val similarity = AudioFeatureExtractor.computeCosineSimilarity(enrolledEmbedding, candidateEmbedding)

        return when {
            similarity >= VERIFICATION_THRESHOLD -> {
                SpeakerVerificationResult(
                    status = SpeakerVerificationStatus.VERIFIED,
                    similarityScore = similarity,
                    threshold = VERIFICATION_THRESHOLD,
                    message = "Owner voice verified (acoustic similarity: ${(similarity * 100).toInt()}%)",
                    audioQualityMetrics = quality
                )
            }
            similarity >= INCONCLUSIVE_LOWER_THRESHOLD -> {
                SpeakerVerificationResult(
                    status = SpeakerVerificationStatus.INCONCLUSIVE,
                    similarityScore = similarity,
                    threshold = VERIFICATION_THRESHOLD,
                    message = "Voice sample is inconclusive (similarity: ${(similarity * 100).toInt()}%, threshold: ${(VERIFICATION_THRESHOLD * 100).toInt()}%).",
                    audioQualityMetrics = quality
                )
            }
            else -> {
                SpeakerVerificationResult(
                    status = SpeakerVerificationStatus.REJECTED,
                    similarityScore = similarity,
                    threshold = VERIFICATION_THRESHOLD,
                    message = "Speaker mismatch: voice characteristics do not match enrolled owner profile.",
                    audioQualityMetrics = quality
                )
            }
        }
    }
}
