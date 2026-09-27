package com.example.memory.search

import com.example.memory.dao.StructuredMemoryDao
import com.example.memory.model.MemoryCategory
import com.example.memory.model.MemorySearchResult
import com.example.memory.model.MemorySensitivity
import com.example.memory.model.StructuredMemory
import com.example.memory.security.MemoryEncryptionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MemorySearchEngine(
    private val memoryDao: StructuredMemoryDao,
    private val encryptionManager: MemoryEncryptionManager
) {
    /**
     * Resolves exact key match in the database.
     */
    suspend fun getByKey(
        key: String,
        category: MemoryCategory? = null,
        includeConfidential: Boolean = false
    ): StructuredMemory? = withContext(Dispatchers.IO) {
        val entity = if (category != null) {
            memoryDao.findByCategoryAndKey(category.name, key.trim())
        } else {
            memoryDao.findByKey(key.trim())
        } ?: return@withContext null

        val memory = entity.toDomain()
        if (memory.isExpired) {
            return@withContext null
        }

        if (memory.sensitivity == MemorySensitivity.STRICTLY_CONFIDENTIAL && !includeConfidential) {
            // Conceal strictly confidential values unless authorized
            return@withContext memory.copy(value = "•••••••• [L2 Biometric Required]")
        }

        val decryptedValue = if (memory.isEncrypted) {
            try {
                encryptionManager.decrypt(memory.value)
            } catch (_: Exception) {
                "[Encrypted Data - Decryption Failed]"
            }
        } else {
            memory.value
        }

        // Update last accessed timestamp
        memoryDao.updateLastAccessed(memory.id)
        memory.copy(value = decryptedValue)
    }

    /**
     * Resolves app aliases (e.g., "YT" -> "YouTube", "Browser" -> "Chrome").
     */
    suspend fun resolveAppAlias(alias: String): String? = withContext(Dispatchers.IO) {
        val cleanAlias = alias.trim()
        val match = memoryDao.findByCategoryAndKey(MemoryCategory.APP_ALIASES.name, cleanAlias)
            ?: memoryDao.findByKey(cleanAlias)

        if (match != null) {
            val mem = match.toDomain()
            if (!mem.isExpired && mem.category == MemoryCategory.APP_ALIASES) {
                memoryDao.updateLastAccessed(mem.id)
                return@withContext if (mem.isEncrypted) {
                    encryptionManager.decrypt(mem.value)
                } else {
                    mem.value
                }
            }
        }
        null
    }

    /**
     * Searches memories using query tokens and relevance scoring.
     */
    suspend fun search(
        query: String,
        category: MemoryCategory? = null,
        includeConfidential: Boolean = false,
        limit: Int = 20
    ): List<MemorySearchResult> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim().lowercase()
        if (cleanQuery.isBlank()) return@withContext emptyList()

        val allEntities = if (category != null) {
            memoryDao.getMemoriesByCategory(category.name)
        } else {
            memoryDao.getAllMemories()
        }

        val tokens = cleanQuery.split(Regex("\\s+")).filter { it.isNotBlank() }
        val results = mutableListOf<MemorySearchResult>()

        for (entity in allEntities) {
            val domain = entity.toDomain()
            if (domain.isExpired) continue

            val decryptedValue = if (domain.isEncrypted) {
                try {
                    encryptionManager.decrypt(domain.value)
                } catch (_: Exception) {
                    ""
                }
            } else {
                domain.value
            }

            val keyLower = domain.key.lowercase()
            val valueLower = decryptedValue.lowercase()

            var score = 0.0
            var matchReason = ""

            // Exact match
            if (keyLower == cleanQuery) {
                score += 100.0
                matchReason = "Exact key match"
            } else if (keyLower.contains(cleanQuery)) {
                score += 60.0
                matchReason = "Key contains query"
            } else if (valueLower.contains(cleanQuery)) {
                score += 40.0
                matchReason = "Content contains query"
            }

            // Token overlap
            val matchedTokens = tokens.count { keyLower.contains(it) || valueLower.contains(it) }
            if (matchedTokens > 0) {
                score += (matchedTokens.toDouble() / tokens.size) * 30.0
                if (matchReason.isBlank()) matchReason = "Token match ($matchedTokens/${tokens.size})"
            }

            if (score > 0) {
                val displayValue = if (domain.sensitivity == MemorySensitivity.STRICTLY_CONFIDENTIAL && !includeConfidential) {
                    "•••••••• [L2 Biometric Required]"
                } else {
                    decryptedValue
                }

                results.add(
                    MemorySearchResult(
                        memory = domain.copy(value = displayValue),
                        relevanceScore = score,
                        matchReason = matchReason
                    )
                )
            }
        }

        results.sortedByDescending { it.relevanceScore }.take(limit)
    }

    /**
     * Retrieves approved memories for AI task planning context.
     * Excludes confidential / sensitive content.
     */
    suspend fun getApprovedContextMemories(limit: Int = 10): List<StructuredMemory> = withContext(Dispatchers.IO) {
        val entities = memoryDao.getApprovedContextMemories(limit)
        entities.mapNotNull { entity ->
            val domain = entity.toDomain()
            if (domain.isExpired) return@mapNotNull null

            val decrypted = if (domain.isEncrypted) {
                try {
                    encryptionManager.decrypt(domain.value)
                } catch (_: Exception) {
                    return@mapNotNull null
                }
            } else {
                domain.value
            }
            domain.copy(value = decrypted)
        }
    }
}
