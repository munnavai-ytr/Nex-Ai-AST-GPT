package com.example.security.voice

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class VoiceEnrollmentMetadata(
    val isEnrolled: Boolean,
    val enrollmentTimestamp: Long,
    val sampleCount: Int,
    val modelVersion: String,
    val averageAudioQuality: Float,
    val enrolledPhrases: List<String> = emptyList()
)

class VoiceEnrollmentStorage(private val context: Context) {

    companion object {
        private const val KEY_ALIAS = "nex_voice_enrollment_key"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val ENCRYPTED_FILE_NAME = "nex_voice_profile.enc"
        private const val METADATA_PREFS = "nex_voice_enrollment_prefs"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
        const val CURRENT_MODEL_VERSION = "NEX-AcousticEmbedder-v1.0"
    }

    private val prefs = context.getSharedPreferences(METADATA_PREFS, Context.MODE_PRIVATE)

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) {
            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (entry != null) {
                return entry.secretKey
            }
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val keyGenSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        keyGenerator.init(keyGenSpec)
        return keyGenerator.generateKey()
    }

    fun saveEnrolledProfile(
        embedding: FloatArray,
        sampleCount: Int,
        averageQuality: Float,
        phrases: List<String>
    ): Boolean {
        return try {
            val key = getOrCreateKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv

            val buffer = ByteBuffer.allocate(embedding.size * 4)
            for (f in embedding) {
                buffer.putFloat(f)
            }
            val plainBytes = buffer.array()
            val encryptedBytes = cipher.doFinal(plainBytes)

            val file = File(context.filesDir, ENCRYPTED_FILE_NAME)
            FileOutputStream(file).use { fos ->
                fos.write(iv)
                fos.write(encryptedBytes)
            }

            prefs.edit()
                .putBoolean("is_enrolled", true)
                .putLong("enrollment_timestamp", System.currentTimeMillis())
                .putInt("sample_count", sampleCount)
                .putString("model_version", CURRENT_MODEL_VERSION)
                .putFloat("avg_quality", averageQuality)
                .putStringSet("phrases", phrases.toSet())
                .apply()

            true
        } catch (e: Exception) {
            false
        }
    }

    fun loadEnrolledEmbedding(): FloatArray? {
        val file = File(context.filesDir, ENCRYPTED_FILE_NAME)
        if (!file.exists() || !isEnrolled()) return null

        return try {
            val fileBytes = FileInputStream(file).use { it.readBytes() }
            if (fileBytes.size <= GCM_IV_LENGTH) return null

            val iv = fileBytes.copyOfRange(0, GCM_IV_LENGTH)
            val cipherText = fileBytes.copyOfRange(GCM_IV_LENGTH, fileBytes.size)

            val key = getOrCreateKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)

            val decryptedBytes = cipher.doFinal(cipherText)
            val buffer = ByteBuffer.wrap(decryptedBytes)
            val floatCount = decryptedBytes.size / 4
            val embedding = FloatArray(floatCount)
            for (i in 0 until floatCount) {
                embedding[i] = buffer.getFloat()
            }
            embedding
        } catch (e: Exception) {
            null
        }
    }

    fun getMetadata(): VoiceEnrollmentMetadata {
        val enrolled = prefs.getBoolean("is_enrolled", false)
        val file = File(context.filesDir, ENCRYPTED_FILE_NAME)
        val actuallyEnrolled = enrolled && file.exists()

        return VoiceEnrollmentMetadata(
            isEnrolled = actuallyEnrolled,
            enrollmentTimestamp = prefs.getLong("enrollment_timestamp", 0L),
            sampleCount = prefs.getInt("sample_count", 0),
            modelVersion = prefs.getString("model_version", CURRENT_MODEL_VERSION) ?: CURRENT_MODEL_VERSION,
            averageAudioQuality = prefs.getFloat("avg_quality", 0f),
            enrolledPhrases = prefs.getStringSet("phrases", emptySet())?.toList() ?: emptyList()
        )
    }

    fun isEnrolled(): Boolean {
        return getMetadata().isEnrolled
    }

    fun deleteEnrollment(): Boolean {
        return try {
            val file = File(context.filesDir, ENCRYPTED_FILE_NAME)
            if (file.exists()) {
                file.delete()
            }

            prefs.edit().clear().apply()

            try {
                val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
                if (keyStore.containsAlias(KEY_ALIAS)) {
                    keyStore.deleteEntry(KEY_ALIAS)
                }
            } catch (_: Exception) {}

            true
        } catch (e: Exception) {
            false
        }
    }
}
