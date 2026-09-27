package com.example.memory.policy

import com.example.memory.dao.StructuredMemoryDao
import com.example.memory.model.MemoryRetentionPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class RetentionCleanupResult(
    val deletedCount: Int,
    val executedAt: Long = System.currentTimeMillis()
)

class MemoryRetentionManager(
    private val structuredMemoryDao: StructuredMemoryDao
) {
    /**
     * Calculates the expiration timestamp based on the provided retention policy.
     */
    fun calculateExpiration(policy: MemoryRetentionPolicy, fromTime: Long = System.currentTimeMillis()): Long? {
        val duration = policy.durationMs ?: return null
        return fromTime + duration
    }

    /**
     * Executes real database cleanup for all expired records.
     */
    suspend fun cleanupExpiredMemories(currentTime: Long = System.currentTimeMillis()): RetentionCleanupResult = withContext(Dispatchers.IO) {
        val deletedCount = structuredMemoryDao.deleteExpiredMemories(currentTime)
        RetentionCleanupResult(deletedCount = deletedCount, executedAt = currentTime)
    }
}
