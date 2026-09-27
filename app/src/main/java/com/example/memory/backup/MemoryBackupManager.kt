package com.example.memory.backup

import com.example.memory.dao.StructuredMemoryDao
import com.example.memory.entities.StructuredMemoryEntity
import com.example.memory.model.MemoryCategory
import com.example.memory.model.MemoryConsentStatus
import com.example.memory.model.MemorySensitivity
import com.example.memory.model.MemorySource
import com.example.memory.model.StructuredMemory
import com.example.memory.security.MemoryEncryptionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

data class MemoryExportResult(
    val jsonString: String,
    val exportedCount: Int,
    val checksum: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class MemoryImportResult(
    val importedCount: Int,
    val skippedCount: Int,
    val errors: List<String>
)

class MemoryBackupManager(
    private val memoryDao: StructuredMemoryDao,
    private val encryptionManager: MemoryEncryptionManager
) {

    /**
     * Exports approved memories into a secure JSON structure.
     */
    suspend fun exportMemories(
        includeSensitive: Boolean = true,
        includeConfidential: Boolean = false
    ): MemoryExportResult = withContext(Dispatchers.IO) {
        val allEntities = memoryDao.getAllMemories()
        val memoriesArray = JSONArray()

        var count = 0
        for (entity in allEntities) {
            val domain = entity.toDomain()
            if (domain.isExpired) continue

            // Filter out confidential if not authorized
            if (domain.sensitivity == MemorySensitivity.STRICTLY_CONFIDENTIAL && !includeConfidential) {
                continue
            }
            if (domain.sensitivity == MemorySensitivity.SENSITIVE && !includeSensitive) {
                continue
            }

            val decryptedValue = if (domain.isEncrypted) {
                try {
                    encryptionManager.decrypt(domain.value)
                } catch (_: Exception) {
                    domain.value
                }
            } else {
                domain.value
            }

            val memoryJson = JSONObject().apply {
                put("id", domain.id)
                put("category", domain.category.name)
                put("key", domain.key)
                put("value", decryptedValue)
                put("createdAt", domain.createdAt)
                put("updatedAt", domain.updatedAt)
                put("source", domain.source.name)
                put("consentStatus", domain.consentStatus.name)
                put("sensitivity", domain.sensitivity.name)
                put("expiration", domain.expiration ?: JSONObject.NULL)
            }
            memoriesArray.put(memoryJson)
            count++
        }

        val exportRoot = JSONObject().apply {
            put("version", 1)
            put("app", "NEX AI Assistant")
            put("exportTimestamp", System.currentTimeMillis())
            put("itemCount", count)
            put("memories", memoriesArray)
        }

        val jsonString = exportRoot.toString(2)
        val checksum = calculateChecksum(jsonString)

        MemoryExportResult(
            jsonString = jsonString,
            exportedCount = count,
            checksum = checksum
        )
    }

    /**
     * Validates and imports a JSON memory backup.
     */
    suspend fun importMemories(jsonContent: String): MemoryImportResult = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()
        var imported = 0
        var skipped = 0

        try {
            val root = JSONObject(jsonContent)
            val memoriesArray = root.optJSONArray("memories")
                ?: return@withContext MemoryImportResult(0, 0, listOf("Invalid backup file: 'memories' array missing"))

            for (i in 0 until memoriesArray.length()) {
                val item = memoriesArray.optJSONObject(i) ?: continue
                val key = item.optString("key", "").trim()
                val value = item.optString("value", "").trim()
                val categoryStr = item.optString("category", MemoryCategory.USER_PROVIDED_FACTS.name)
                val sensitivityStr = item.optString("sensitivity", MemorySensitivity.NORMAL.name)
                val category = MemoryCategory.fromString(categoryStr)
                val sensitivity = MemorySensitivity.fromString(sensitivityStr)

                if (key.isBlank() || value.isBlank()) {
                    skipped++
                    continue
                }

                val isEncrypted = sensitivity != MemorySensitivity.NORMAL
                val storedValue = if (isEncrypted) {
                    encryptionManager.encrypt(value)
                } else {
                    value
                }

                val entity = StructuredMemoryEntity(
                    category = category.name,
                    key = key,
                    value = storedValue,
                    createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = System.currentTimeMillis(),
                    source = MemorySource.USER_MANUAL_ENTRY.name,
                    consentStatus = MemoryConsentStatus.EXPLICIT_USER_CONSENT.name,
                    sensitivity = sensitivity.name,
                    expiration = if (item.has("expiration") && !item.isNull("expiration")) item.optLong("expiration") else null,
                    lastAccessedAt = System.currentTimeMillis(),
                    isEncrypted = isEncrypted
                )

                memoryDao.insertMemory(entity)
                imported++
            }
        } catch (e: Exception) {
            errors.add("Failed to parse backup content: ${e.message}")
        }

        MemoryImportResult(
            importedCount = imported,
            skippedCount = skipped,
            errors = errors
        )
    }

    private fun calculateChecksum(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }
}
