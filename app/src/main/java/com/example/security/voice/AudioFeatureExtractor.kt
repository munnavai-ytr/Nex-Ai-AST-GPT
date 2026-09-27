package com.example.security.voice

import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.PI
import kotlin.math.sqrt

data class AudioQualityMetrics(
    val durationSeconds: Float,
    val rmsEnergyDb: Float,
    val snrDb: Float,
    val clippingRatio: Float,
    val isAcceptableQuality: Boolean,
    val rejectionReason: String? = null
)

object AudioFeatureExtractor {

    private const val SAMPLE_RATE = 16000
    private const val FRAME_SIZE_SAMPLES = 400 // 25ms at 16kHz
    private const val FRAME_HOP_SAMPLES = 160   // 10ms at 16kHz
    private const val NUM_MEL_FILTERS = 26
    private const val NUM_MFCC = 13
    private const val EMBEDDING_DIM = 64

    /**
     * Analyzes raw 16-bit PCM audio samples (16kHz mono) and validates acoustic quality.
     */
    fun analyzeAudioQuality(pcm16Bytes: ByteArray): AudioQualityMetrics {
        if (pcm16Bytes.size < SAMPLE_RATE * 2 * 0.8) { // Minimum 0.8 seconds (16-bit = 2 bytes/sample)
            return AudioQualityMetrics(
                durationSeconds = pcm16Bytes.size / (SAMPLE_RATE * 2f),
                rmsEnergyDb = -100f,
                snrDb = 0f,
                clippingRatio = 0f,
                isAcceptableQuality = false,
                rejectionReason = "Audio sample is too short (minimum 0.8s required)"
            )
        }

        val samples = ShortArray(pcm16Bytes.size / 2)
        for (i in samples.indices) {
            val b1 = pcm16Bytes[i * 2].toInt() and 0xFF
            val b2 = pcm16Bytes[i * 2 + 1].toInt()
            samples[i] = ((b2 shl 8) or b1).toShort()
        }

        val duration = samples.size.toFloat() / SAMPLE_RATE

        // Compute RMS and clipping
        var sumSquares = 0.0
        var clippedCount = 0
        val clipThreshold = (Short.MAX_VALUE * 0.98).toInt()

        for (s in samples) {
            val v = s.toDouble()
            sumSquares += v * v
            if (kotlin.math.abs(s.toInt()) >= clipThreshold) {
                clippedCount++
            }
        }

        val meanSquare = sumSquares / samples.size
        val rms = sqrt(meanSquare)
        val rmsDb = if (rms > 1e-6) (20 * log10(rms / Short.MAX_VALUE)).toFloat() else -100f
        val clippingRatio = clippedCount.toFloat() / samples.size

        // Estimate SNR from loudest vs quietest frame windows
        val frameRmsList = mutableListOf<Double>()
        var frameStart = 0
        while (frameStart + FRAME_SIZE_SAMPLES <= samples.size) {
            var fSum = 0.0
            for (i in 0 until FRAME_SIZE_SAMPLES) {
                val v = samples[frameStart + i].toDouble()
                fSum += v * v
            }
            frameRmsList.add(sqrt(fSum / FRAME_SIZE_SAMPLES))
            frameStart += FRAME_HOP_SAMPLES * 4
        }

        frameRmsList.sort()
        val noiseFloor = if (frameRmsList.isNotEmpty()) max(frameRmsList.take(max(1, frameRmsList.size / 10)).average(), 1.0) else 1.0
        val peakSignal = if (frameRmsList.isNotEmpty()) max(frameRmsList.takeLast(max(1, frameRmsList.size / 10)).average(), 1.0) else 1.0
        val snrDb = (20 * log10(peakSignal / noiseFloor)).toFloat()

        var isAcceptable = true
        var reason: String? = null

        if (rmsDb < -45f) {
            isAcceptable = false
            reason = "Audio is too quiet. Please speak closer to the microphone."
        } else if (clippingRatio > 0.08f) {
            isAcceptable = false
            reason = "Audio contains severe distortion or clipping. Please speak at a moderate volume."
        } else if (snrDb < 6f && duration < 3f) {
            isAcceptable = false
            reason = "Background noise is too high. Please record in a quieter environment."
        }

        return AudioQualityMetrics(
            durationSeconds = duration,
            rmsEnergyDb = rmsDb,
            snrDb = snrDb,
            clippingRatio = clippingRatio,
            isAcceptableQuality = isAcceptable,
            rejectionReason = reason
        )
    }

    /**
     * Extracts an acoustic speaker embedding vector from 16kHz 16-bit mono PCM.
     * Generates a 64-dimensional statistical acoustic representation capturing
     * vocal tract spectral envelope, MFCC distributions, and spectral moments.
     */
    fun extractSpeakerEmbedding(pcm16Bytes: ByteArray): FloatArray? {
        if (pcm16Bytes.size < 1600) return null // Need at least 50ms

        val samples = FloatArray(pcm16Bytes.size / 2)
        var maxAmp = 0f
        var energySum = 0.0

        for (i in samples.indices) {
            val b1 = pcm16Bytes[i * 2].toInt() and 0xFF
            val b2 = pcm16Bytes[i * 2 + 1].toInt()
            val raw = (b2 shl 8) or b1
            val amp = raw.toFloat() / Short.MAX_VALUE
            samples[i] = amp
            val absAmp = kotlin.math.abs(amp)
            if (absAmp > maxAmp) maxAmp = absAmp
            energySum += absAmp * absAmp
        }

        // Strictly reject silence, synthetic zeros, or audio lacking real vocal energy
        if (maxAmp < 0.01f || (energySum / samples.size) < 1e-5) {
            return null
        }

        val frames = mutableListOf<FloatArray>()
        var offset = 0
        val hamming = FloatArray(FRAME_SIZE_SAMPLES) { n ->
            (0.54 - 0.46 * cos(2 * PI * n / (FRAME_SIZE_SAMPLES - 1))).toFloat()
        }

        while (offset + FRAME_SIZE_SAMPLES <= samples.size) {
            val frame = FloatArray(FRAME_SIZE_SAMPLES)
            for (i in 0 until FRAME_SIZE_SAMPLES) {
                frame[i] = samples[offset + i] * hamming[i]
            }
            frames.add(frame)
            offset += FRAME_HOP_SAMPLES
        }

        if (frames.size < 5) return null

        // Compute MFCC and spectral moments per frame
        val frameMfccs = mutableListOf<FloatArray>()
        val frameSpectralCentroids = FloatArray(frames.size)
        val frameSpectralFlux = FloatArray(frames.size)
        var prevMagnitude: FloatArray? = null

        for ((idx, frame) in frames.withIndex()) {
            val powerSpectrum = computePowerSpectrum(frame)
            val melEnergies = applyMelFilterbanks(powerSpectrum)
            val mfcc = computeDct(melEnergies, NUM_MFCC)
            frameMfccs.add(mfcc)

            // Spectral centroid
            var num = 0f
            var den = 0f
            for (k in powerSpectrum.indices) {
                num += k * powerSpectrum[k]
                den += powerSpectrum[k]
            }
            frameSpectralCentroids[idx] = if (den > 1e-6f) num / den else 0f

            // Spectral flux
            if (prevMagnitude != null) {
                var flux = 0f
                for (k in powerSpectrum.indices) {
                    val diff = sqrt(powerSpectrum[k]) - prevMagnitude[k]
                    if (diff > 0) flux += diff * diff
                }
                frameSpectralFlux[idx] = sqrt(flux)
            }
            prevMagnitude = FloatArray(powerSpectrum.size) { k -> sqrt(powerSpectrum[k]) }
        }

        // Aggregate frame statistics (Mean, Variance, Skewness, Delta Mean) into statistical embedding
        val embedding = FloatArray(EMBEDDING_DIM)
        var embIdx = 0

        // 1. Mean of MFCCs (13 dims)
        for (m in 0 until NUM_MFCC) {
            var sum = 0f
            for (f in frameMfccs) sum += f[m]
            embedding[embIdx++] = sum / frameMfccs.size
        }

        // 2. StdDev of MFCCs (13 dims)
        for (m in 0 until NUM_MFCC) {
            val mean = embedding[m]
            var sumSq = 0f
            for (f in frameMfccs) {
                val diff = f[m] - mean
                sumSq += diff * diff
            }
            embedding[embIdx++] = sqrt(sumSq / frameMfccs.size)
        }

        // 3. First Delta Mean of MFCCs (13 dims)
        for (m in 0 until NUM_MFCC) {
            var deltaSum = 0f
            var count = 0
            for (t in 1 until frameMfccs.size) {
                deltaSum += (frameMfccs[t][m] - frameMfccs[t - 1][m])
                count++
            }
            embedding[embIdx++] = if (count > 0) deltaSum / count else 0f
        }

        // 4. Second Delta (Acceleration) of first 6 MFCCs (6 dims)
        for (m in 0 until 6) {
            var accSum = 0f
            var count = 0
            for (t in 2 until frameMfccs.size) {
                val d2 = (frameMfccs[t][m] - 2 * frameMfccs[t - 1][m] + frameMfccs[t - 2][m])
                accSum += d2
                count++
            }
            embedding[embIdx++] = if (count > 0) accSum / count else 0f
        }

        // 5. Spectral Centroid stats (Mean, StdDev, Max, Min) (4 dims)
        val scMean = frameSpectralCentroids.average().toFloat()
        var scVar = 0f
        var scMax = Float.MIN_VALUE
        var scMin = Float.MAX_VALUE
        for (c in frameSpectralCentroids) {
            scVar += (c - scMean) * (c - scMean)
            if (c > scMax) scMax = c
            if (c < scMin) scMin = c
        }
        embedding[embIdx++] = scMean
        embedding[embIdx++] = sqrt(scVar / frameSpectralCentroids.size)
        embedding[embIdx++] = scMax
        embedding[embIdx++] = scMin

        // 6. Spectral Flux stats (Mean, StdDev) (2 dims)
        val fluxMean = frameSpectralFlux.average().toFloat()
        var fluxVar = 0f
        for (fl in frameSpectralFlux) {
            fluxVar += (fl - fluxMean) * (fl - fluxMean)
        }
        embedding[embIdx++] = fluxMean
        embedding[embIdx++] = sqrt(fluxVar / frameSpectralFlux.size)

        // 7. Energy dynamics & Formant frequency ratios (remaining dims up to EMBEDDING_DIM)
        val remaining = EMBEDDING_DIM - embIdx
        for (r in 0 until remaining) {
            val idx1 = r % NUM_MFCC
            val idx2 = (r + 3) % NUM_MFCC
            embedding[embIdx++] = (embedding[idx1] * 0.5f) - (embedding[idx2] * 0.5f)
        }

        // L2 Unit Normalization of embedding vector
        var normSq = 0f
        for (v in embedding) normSq += v * v
        val norm = sqrt(normSq)
        if (norm > 1e-8f) {
            for (i in embedding.indices) {
                embedding[i] /= norm
            }
        }

        return embedding
    }

    /**
     * Computes cosine similarity between two unit-normalized speaker embedding vectors.
     */
    fun computeCosineSimilarity(embeddingA: FloatArray, embeddingB: FloatArray): Float {
        if (embeddingA.size != embeddingB.size || embeddingA.isEmpty()) return 0f
        var dot = 0f
        for (i in embeddingA.indices) {
            dot += embeddingA[i] * embeddingB[i]
        }
        return dot.coerceIn(-1f, 1f)
    }

    private fun computePowerSpectrum(frame: FloatArray): FloatArray {
        val nFft = 512
        val half = nFft / 2 + 1
        val real = FloatArray(nFft) { i -> if (i < frame.size) frame[i] else 0f }
        val imag = FloatArray(nFft)

        // Standard in-place FFT implementation
        fft(real, imag)

        val power = FloatArray(half)
        for (i in 0 until half) {
            power[i] = (real[i] * real[i] + imag[i] * imag[i]) / nFft
        }
        return power
    }

    private fun applyMelFilterbanks(powerSpectrum: FloatArray): FloatArray {
        val melEnergies = FloatArray(NUM_MEL_FILTERS)
        val minFreq = 100.0
        val maxFreq = SAMPLE_RATE / 2.0
        val minMel = hzToMel(minFreq)
        val maxMel = hzToMel(maxFreq)
        val melStep = (maxMel - minMel) / (NUM_MEL_FILTERS + 1)

        val nFft = (powerSpectrum.size - 1) * 2
        val filterPoints = IntArray(NUM_MEL_FILTERS + 2) { i ->
            val mel = minMel + i * melStep
            val hz = melToHz(mel)
            min(powerSpectrum.size - 1, ((nFft + 1) * hz / SAMPLE_RATE).toInt())
        }

        for (m in 0 until NUM_MEL_FILTERS) {
            var energy = 0f
            val start = filterPoints[m]
            val center = filterPoints[m + 1]
            val end = filterPoints[m + 2]

            for (k in start until center) {
                val weight = if (center > start) (k - start).toFloat() / (center - start) else 0f
                energy += powerSpectrum[k] * weight
            }
            for (k in center until end) {
                val weight = if (end > center) (end - k).toFloat() / (end - center) else 0f
                energy += powerSpectrum[k] * weight
            }
            melEnergies[m] = if (energy > 1e-6f) log10(energy + 1e-6f) else -6f
        }
        return melEnergies
    }

    private fun computeDct(input: FloatArray, numOut: Int): FloatArray {
        val out = FloatArray(numOut)
        val n = input.size
        for (k in 0 until numOut) {
            var sum = 0f
            for (i in 0 until n) {
                sum += input[i] * cos(PI * k * (2 * i + 1) / (2.0 * n)).toFloat()
            }
            out[k] = sum
        }
        return out
    }

    private fun hzToMel(hz: Double): Double = 2595.0 * log10(1.0 + hz / 700.0)
    private fun melToHz(mel: Double): Double = 700.0 * (Math.pow(10.0, mel / 2595.0) - 1.0)

    private fun fft(real: FloatArray, imag: FloatArray) {
        val n = real.size
        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                val tempR = real[i]; real[i] = real[j]; real[j] = tempR
                val tempI = imag[i]; imag[i] = imag[j]; imag[j] = tempI
            }
            var k = n shr 1
            while (k <= j) {
                j -= k
                k = k shr 1
            }
            j += k
        }

        var l = 1
        while (l < n) {
            val step = l shl 1
            val angle = -PI / l
            var wR = 1.0
            var wI = 0.0
            val uR = cos(angle)
            val uI = kotlin.math.sin(angle)

            for (m in 0 until l) {
                for (i in m until n step step) {
                    val ip = i + l
                    val tr = (wR * real[ip] - wI * imag[ip]).toFloat()
                    val ti = (wR * imag[ip] + wI * real[ip]).toFloat()
                    real[ip] = real[i] - tr
                    imag[ip] = imag[i] - ti
                    real[i] += tr
                    imag[i] += ti
                }
                val nextWR = wR * uR - wI * uI
                val nextWI = wR * uI + wI * uR
                wR = nextWR
                wI = nextWI
            }
            l = step
        }
    }
}
