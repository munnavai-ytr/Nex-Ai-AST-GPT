package com.example.security.voice

import android.content.Context

interface SpeakerVerificationAdapter {
    val modelName: String
    val isAvailable: Boolean
    val isBiometricallyCertified: Boolean
    fun verify(pcmBytes: ByteArray, enrolledEmbedding: FloatArray): SpeakerVerificationResult
    fun extractEmbedding(pcmBytes: ByteArray): FloatArray?
    fun analyzeQuality(pcmBytes: ByteArray): AudioQualityMetrics
}

class AcousticSpeakerVerificationAdapter(
    private val context: Context,
    val verificationThreshold: Float = SpeakerVerificationManager.VERIFICATION_THRESHOLD,
    val inconclusiveThreshold: Float = SpeakerVerificationManager.INCONCLUSIVE_LOWER_THRESHOLD
) : SpeakerVerificationAdapter {

    override val modelName: String = "NEX Acoustic Heuristic DSP (Uncertified - Not Biometrically Validated)"
    override val isAvailable: Boolean = true
    override val isBiometricallyCertified: Boolean = false

    override fun extractEmbedding(pcmBytes: ByteArray): FloatArray? {
        return AudioFeatureExtractor.extractSpeakerEmbedding(pcmBytes)
    }

    override fun analyzeQuality(pcmBytes: ByteArray): AudioQualityMetrics {
        return AudioFeatureExtractor.analyzeAudioQuality(pcmBytes)
    }

    override fun verify(pcmBytes: ByteArray, enrolledEmbedding: FloatArray): SpeakerVerificationResult {
        val quality = analyzeQuality(pcmBytes)
        if (!quality.isAcceptableQuality) {
            return SpeakerVerificationResult(
                status = SpeakerVerificationStatus.LOW_QUALITY_AUDIO,
                similarityScore = 0f,
                threshold = verificationThreshold,
                message = quality.rejectionReason ?: "Audio sample failed acoustic quality check.",
                audioQualityMetrics = quality,
                isBiometricallyCertified = false,
                modelType = "HEURISTIC_ACOUSTIC_DSP"
            )
        }

        val candidateEmbedding = extractEmbedding(pcmBytes)
            ?: return SpeakerVerificationResult(
                status = SpeakerVerificationStatus.LOW_QUALITY_AUDIO,
                similarityScore = 0f,
                threshold = verificationThreshold,
                message = "Could not compute speaker feature vector (signal lacks vocal energy).",
                audioQualityMetrics = quality,
                isBiometricallyCertified = false,
                modelType = "HEURISTIC_ACOUSTIC_DSP"
            )

        val similarity = AudioFeatureExtractor.computeCosineSimilarity(enrolledEmbedding, candidateEmbedding)

        return when {
            similarity >= verificationThreshold -> {
                SpeakerVerificationResult(
                    status = SpeakerVerificationStatus.VERIFIED,
                    similarityScore = similarity,
                    threshold = verificationThreshold,
                    message = "Acoustic match detected (heuristic similarity: ${(similarity * 100).toInt()}%). Uncertified for high-risk authorization.",
                    audioQualityMetrics = quality,
                    isBiometricallyCertified = false,
                    modelType = "HEURISTIC_ACOUSTIC_DSP"
                )
            }
            similarity >= inconclusiveThreshold -> {
                SpeakerVerificationResult(
                    status = SpeakerVerificationStatus.INCONCLUSIVE,
                    similarityScore = similarity,
                    threshold = verificationThreshold,
                    message = "Acoustic sample is inconclusive (heuristic similarity: ${(similarity * 100).toInt()}%).",
                    audioQualityMetrics = quality,
                    isBiometricallyCertified = false,
                    modelType = "HEURISTIC_ACOUSTIC_DSP"
                )
            }
            else -> {
                SpeakerVerificationResult(
                    status = SpeakerVerificationStatus.REJECTED,
                    similarityScore = similarity,
                    threshold = verificationThreshold,
                    message = "Speaker mismatch: acoustic features do not match enrolled profile.",
                    audioQualityMetrics = quality,
                    isBiometricallyCertified = false,
                    modelType = "HEURISTIC_ACOUSTIC_DSP"
                )
            }
        }
    }
}
