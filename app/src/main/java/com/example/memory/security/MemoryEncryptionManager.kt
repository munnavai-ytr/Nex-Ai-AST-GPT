package com.example.memory.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class MemoryEncryptionManager(
    private val keyAlias: String = "nex_memory_master_key"
) {
    private val androidKeyStore = "AndroidKeyStore"
    private val transformation = "AES/GCM/NoPadding"
    private val gcmTagLength = 128
    private val ivLength = 12

    // Fallback key for testing environments where AndroidKeyStore is not mocked
    private var fallbackKey: SecretKey? = null

    init {
        try {
            ensureKeyExists()
        } catch (_: Exception) {
            // Use fallback in non-AndroidKeyStore test environments
            initFallbackKey()
        }
    }

    private fun ensureKeyExists() {
        val keyStore = KeyStore.getInstance(androidKeyStore)
        keyStore.load(null)

        if (!keyStore.containsAlias(keyAlias)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                androidKeyStore
            )

            val spec = KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()

            keyGenerator.init(spec)
            keyGenerator.generateKey()
        }
    }

    private fun initFallbackKey() {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        fallbackKey = keyGen.generateKey()
    }

    private fun getSecretKey(): SecretKey {
        return try {
            val keyStore = KeyStore.getInstance(androidKeyStore)
            keyStore.load(null)
            val entry = keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry
            entry?.secretKey ?: fallbackKey ?: throw IllegalStateException("Keystore entry missing")
        } catch (_: Exception) {
            fallbackKey ?: run {
                initFallbackKey()
                fallbackKey!!
            }
        }
    }

    /**
     * Encrypts plain text using AES-256-GCM.
     * Returns a Base64-encoded string containing [IV (12 bytes) + CipherText + Tag].
     */
    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        return try {
            val secretKey = getSecretKey()
            val cipher = Cipher.getInstance(transformation)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)

            val iv = cipher.iv
            val cipherText = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))

            // Combine IV + ciphertext
            val combined = ByteArray(iv.size + cipherText.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)

            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            throw SecurityException("Failed to encrypt memory value: ${e.message}", e)
        }
    }

    /**
     * Decrypts Base64-encoded ciphertext using AES-256-GCM.
     */
    fun decrypt(encryptedBase64: String): String {
        if (encryptedBase64.isEmpty()) return ""
        return try {
            val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            if (combined.size < ivLength) {
                throw IllegalArgumentException("Invalid encrypted payload length")
            }

            val iv = ByteArray(ivLength)
            System.arraycopy(combined, 0, iv, 0, ivLength)

            val cipherTextLength = combined.size - ivLength
            val cipherText = ByteArray(cipherTextLength)
            System.arraycopy(combined, ivLength, cipherText, 0, cipherTextLength)

            val secretKey = getSecretKey()
            val cipher = Cipher.getInstance(transformation)
            val spec = GCMParameterSpec(gcmTagLength, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            val plainTextBytes = cipher.doFinal(cipherText)
            String(plainTextBytes, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            throw SecurityException("Failed to decrypt memory value: ${e.message}", e)
        }
    }
}
