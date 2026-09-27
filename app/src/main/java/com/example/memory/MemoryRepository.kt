package com.example.memory

import com.example.memory.backup.MemoryBackupManager
import com.example.memory.dao.MemoryDao
import com.example.memory.dao.MemoryHistoryDao
import com.example.memory.dao.StructuredMemoryDao
import com.example.memory.dao.TaskDao
import com.example.memory.dao.WorkflowDao
import com.example.memory.entities.CommandAliasEntity
import com.example.memory.entities.ExecutionLogEntity
import com.example.memory.entities.MemoryChangeHistoryEntity
import com.example.memory.entities.MemoryItemEntity
import com.example.memory.entities.RoutineEntity
import com.example.memory.entities.StructuredMemoryEntity
import com.example.memory.entities.TaskHistoryEntity
import com.example.memory.entities.UserPreferenceEntity
import com.example.memory.model.MemoryCandidate
import com.example.memory.model.MemoryCategory
import com.example.memory.model.MemoryConsentStatus
import com.example.memory.model.MemoryRetentionPolicy
import com.example.memory.model.MemorySearchResult
import com.example.memory.model.MemorySensitivity
import com.example.memory.model.MemorySource
import com.example.memory.model.MemoryWriteResult
import com.example.memory.model.StructuredMemory
import com.example.memory.policy.MemoryPolicy
import com.example.memory.policy.MemoryRetentionManager
import com.example.memory.policy.PolicyEvaluationResult
import com.example.memory.search.MemorySearchEngine
import com.example.memory.security.MemoryEncryptionManager
import com.example.memory.workflow.WorkflowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class MemoryRepository(
    val structuredMemoryDao: StructuredMemoryDao,
    val workflowDao: WorkflowDao,
    val historyDao: MemoryHistoryDao,
    val memoryDao: MemoryDao,
    val taskDao: TaskDao,
    val encryptionManager: MemoryEncryptionManager = MemoryEncryptionManager(),
    val policy: MemoryPolicy = MemoryPolicy()
) {
    /**
     * Backwards-compatible secondary constructor for testing and legacy invocations.
     */
    constructor(memoryDao: MemoryDao, taskDao: TaskDao) : this(
        structuredMemoryDao = object : StructuredMemoryDao {
            private val list = mutableListOf<com.example.memory.entities.StructuredMemoryEntity>()
            override fun getAllMemoriesFlow() = kotlinx.coroutines.flow.flowOf(list.toList())
            override suspend fun getAllMemories() = list.toList()
            override fun getMemoriesByCategoryFlow(category: String) = kotlinx.coroutines.flow.flowOf(list.filter { it.category == category })
            override suspend fun getMemoriesByCategory(category: String) = list.filter { it.category == category }
            override suspend fun findByKey(key: String) = list.find { it.key == key }
            override suspend fun findByCategoryAndKey(category: String, key: String) = list.find { it.category == category && it.key == key }
            override suspend fun searchMemories(query: String) = list.filter { it.key.contains(query, ignoreCase = true) || it.value.contains(query, ignoreCase = true) }
            override suspend fun getApprovedContextMemories(limit: Int) = list.take(limit)
            override suspend fun insertMemory(memory: com.example.memory.entities.StructuredMemoryEntity): Long {
                list.removeAll { it.key == memory.key && it.category == memory.category }
                list.add(memory)
                return 1L
            }
            override suspend fun updateMemory(memory: com.example.memory.entities.StructuredMemoryEntity) {
                insertMemory(memory)
            }
            override suspend fun deleteMemory(memory: com.example.memory.entities.StructuredMemoryEntity) {
                list.removeIf { it.id == memory.id || (it.key == memory.key && it.category == memory.category) }
            }
            override suspend fun deleteById(id: Long): Int {
                val before = list.size
                list.removeIf { it.id == id }
                return before - list.size
            }
            override suspend fun deleteByKey(key: String): Int {
                val before = list.size
                list.removeIf { it.key == key }
                return before - list.size
            }
            override suspend fun deleteByCategoryAndKey(category: String, key: String): Int {
                val before = list.size
                list.removeIf { it.category == category && it.key == key }
                return before - list.size
            }
            override suspend fun deleteByCategory(category: String): Int {
                val before = list.size
                list.removeIf { it.category == category }
                return before - list.size
            }
            override suspend fun clearAll(): Int {
                val count = list.size
                list.clear()
                return count
            }
            override suspend fun getExpiredMemories(currentTime: Long) = list.filter { it.expiration != null && it.expiration <= currentTime }
            override suspend fun deleteExpiredMemories(currentTime: Long): Int {
                val before = list.size
                list.removeIf { it.expiration != null && it.expiration <= currentTime }
                return before - list.size
            }
            override suspend fun updateLastAccessed(id: Long, time: Long) {}
        },
        workflowDao = object : WorkflowDao {
            private val workflows = mutableListOf<com.example.memory.entities.WorkflowEntity>()
            override fun getAllWorkflowsFlow() = kotlinx.coroutines.flow.flowOf(workflows.toList())
            override suspend fun getEnabledWorkflows() = workflows.filter { it.isEnabled }
            override suspend fun getWorkflowById(workflowId: String) = workflows.find { it.workflowId == workflowId }
            override suspend fun findByTrigger(triggerPhrase: String) = workflows.find { it.triggerPhrase.equals(triggerPhrase, ignoreCase = true) }
            override suspend fun insertWorkflow(workflow: com.example.memory.entities.WorkflowEntity): Long {
                workflows.add(workflow)
                return 1L
            }
            override suspend fun updateWorkflow(workflow: com.example.memory.entities.WorkflowEntity) {}
            override suspend fun deleteWorkflow(workflow: com.example.memory.entities.WorkflowEntity) { workflows.remove(workflow) }
            override suspend fun deleteByWorkflowId(workflowId: String): Int {
                val before = workflows.size
                workflows.removeIf { it.workflowId == workflowId }
                return before - workflows.size
            }
        },
        historyDao = object : MemoryHistoryDao {
            private val histories = mutableListOf<MemoryChangeHistoryEntity>()
            override fun getRecentHistoryFlow(limit: Int) = kotlinx.coroutines.flow.flowOf(histories.take(limit))
            override suspend fun insertHistory(history: MemoryChangeHistoryEntity): Long {
                histories.add(0, history)
                return 1L
            }
            override suspend fun clearHistory() { histories.clear() }
        },
        memoryDao = memoryDao,
        taskDao = taskDao
    )
    val retentionManager = MemoryRetentionManager(structuredMemoryDao)
    val searchEngine = MemorySearchEngine(structuredMemoryDao, encryptionManager)
    val backupManager = MemoryBackupManager(structuredMemoryDao, encryptionManager)
    val workflowManager = WorkflowManager(workflowDao)

    // Reactive streams
    val allStructuredMemories: Flow<List<StructuredMemory>> = structuredMemoryDao.getAllMemoriesFlow().map { list ->
        list.map { entity ->
            val isConfidential = entity.sensitivity == MemorySensitivity.STRICTLY_CONFIDENTIAL.name
            val displayValue = if (isConfidential) {
                "•••••••• [L2 Biometric Required]"
            } else if (entity.isEncrypted) {
                try { encryptionManager.decrypt(entity.value) } catch (_: Exception) { "[Encrypted Data]" }
            } else {
                entity.value
            }
            entity.toDomain(displayValue)
        }
    }

    val memoryChangeHistory: Flow<List<MemoryChangeHistoryEntity>> = historyDao.getRecentHistoryFlow()

    // Legacy flows preserved for backwards compatibility
    val preferences: Flow<List<UserPreferenceEntity>> = memoryDao.getAllPreferences()
    val memories: Flow<List<MemoryItemEntity>> = memoryDao.getAllMemories()
    val aliases: Flow<List<CommandAliasEntity>> = memoryDao.getAllAliases()
    val routines: Flow<List<RoutineEntity>> = memoryDao.getAllRoutines()
    val recentHistory: Flow<List<TaskHistoryEntity>> = taskDao.getRecentHistory()
    val recentLogs: Flow<List<ExecutionLogEntity>> = taskDao.getRecentLogs()

    /**
     * Executes the secure write pipeline for a memory candidate:
     * Candidate -> Policy/Schema Validation -> Consent Check -> Sensitivity/Encryption -> Room DB -> Verify -> History Audit
     */
    suspend fun saveCandidate(
        candidate: MemoryCandidate,
        explicitConsentGiven: Boolean = true,
        retentionPolicy: MemoryRetentionPolicy = MemoryRetentionPolicy.PERMANENT
    ): MemoryWriteResult = withContext(Dispatchers.IO) {
        // 1. Policy & Schema Evaluation
        val evalResult = policy.evaluateCandidate(candidate, explicitConsentGiven)
        when (evalResult) {
            is PolicyEvaluationResult.Rejected -> return@withContext MemoryWriteResult.Rejected(evalResult.reason)
            is PolicyEvaluationResult.RequiresExplicitConsent -> return@withContext MemoryWriteResult.Rejected(evalResult.reason)
            is PolicyEvaluationResult.Allowed -> {}
        }

        // 2. Sensitivity & Expiration
        val sensitivity = if (candidate.sensitivity != MemorySensitivity.NORMAL) {
            candidate.sensitivity
        } else {
            policy.classifySensitivity(candidate.key, candidate.value, candidate.category)
        }

        val isEncrypted = sensitivity != MemorySensitivity.NORMAL
        val storedValue = if (isEncrypted) {
            try {
                encryptionManager.encrypt(candidate.value)
            } catch (e: Exception) {
                return@withContext MemoryWriteResult.Error("Encryption failed: ${e.message}", e)
            }
        } else {
            candidate.value
        }

        val expirationTime = retentionManager.calculateExpiration(retentionPolicy)

        // 3. Conflict Detection
        val existing = structuredMemoryDao.findByCategoryAndKey(candidate.category.name, candidate.key)
        if (existing != null) {
            val existingDomain = existing.toDomain()
            if (policy.hasConflict(existingDomain, candidate) && !explicitConsentGiven) {
                return@withContext MemoryWriteResult.Conflict(
                    existingMemory = existingDomain,
                    candidate = candidate,
                    message = "A conflicting memory for '${candidate.key}' already exists."
                )
            }
        }

        val entity = StructuredMemoryEntity(
            id = existing?.id ?: 0,
            category = candidate.category.name,
            key = candidate.key.trim(),
            value = storedValue,
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            source = MemorySource.USER_MANUAL_ENTRY.name,
            consentStatus = MemoryConsentStatus.EXPLICIT_USER_CONSENT.name,
            sensitivity = sensitivity.name,
            expiration = expirationTime,
            lastAccessedAt = System.currentTimeMillis(),
            isEncrypted = isEncrypted
        )

        // 4. Persistence
        val rowId = structuredMemoryDao.insertMemory(entity)
        if (rowId <= 0 && existing == null) {
            return@withContext MemoryWriteResult.Error("Database insertion failed to return a valid row ID.")
        }

        // Also update legacy alias / preference tables if applicable for full compatibility
        if (candidate.category == MemoryCategory.APP_ALIASES) {
            memoryDao.insertAlias(CommandAliasEntity(alias = candidate.key.trim(), expansion = candidate.value.trim()))
        } else if (candidate.category == MemoryCategory.USER_PREFERENCES) {
            memoryDao.setPreference(UserPreferenceEntity(key = candidate.key.trim(), value = candidate.value.trim()))
        }

        // 5. Audit History Log
        val actionType = if (existing != null) "UPDATE" else "INSERT"
        historyDao.insertHistory(
            MemoryChangeHistoryEntity(
                memoryKey = candidate.key,
                category = candidate.category.name,
                action = actionType,
                changeSummary = "Saved '${candidate.key}' (${candidate.category.displayName})"
            )
        )

        val savedMemory = entity.copy(id = if (existing != null) existing.id else rowId).toDomain(candidate.value)
        MemoryWriteResult.Success(
            memory = savedMemory,
            message = "Memory '${candidate.key}' successfully saved."
        )
    }

    suspend fun getMemoryById(id: Long, includeConfidential: Boolean = false): StructuredMemory? = withContext(Dispatchers.IO) {
        val entity = structuredMemoryDao.getAllMemories().firstOrNull { it.id == id } ?: return@withContext null
        val domain = entity.toDomain()
        if (domain.sensitivity == MemorySensitivity.STRICTLY_CONFIDENTIAL && !includeConfidential) {
            return@withContext domain.copy(value = "•••••••• [L2 Biometric Required]")
        }
        val decrypted = if (domain.isEncrypted) {
            try { encryptionManager.decrypt(domain.value) } catch (_: Exception) { "[Encrypted Data]" }
        } else {
            domain.value
        }
        domain.copy(value = decrypted)
    }

    suspend fun deleteMemory(memoryId: Long): Boolean = withContext(Dispatchers.IO) {
        val entity = structuredMemoryDao.getAllMemories().firstOrNull { it.id == memoryId }
        val rows = structuredMemoryDao.deleteById(memoryId)
        if (rows > 0 && entity != null) {
            historyDao.insertHistory(
                MemoryChangeHistoryEntity(
                    memoryKey = entity.key,
                    category = entity.category,
                    action = "DELETE",
                    changeSummary = "Deleted memory '${entity.key}'"
                )
            )
            // Cleanup legacy alias if needed
            if (entity.category == MemoryCategory.APP_ALIASES.name) {
                val legacy = memoryDao.findAlias(entity.key)
                if (legacy != null) memoryDao.deleteAlias(legacy)
            }
        }
        rows > 0
    }

    suspend fun deleteByCategory(category: MemoryCategory): Int = withContext(Dispatchers.IO) {
        val rows = structuredMemoryDao.deleteByCategory(category.name)
        if (rows > 0) {
            historyDao.insertHistory(
                MemoryChangeHistoryEntity(
                    memoryKey = "*",
                    category = category.name,
                    action = "DELETE_CATEGORY",
                    changeSummary = "Deleted all $rows memories in ${category.displayName}"
                )
            )
        }
        rows
    }

    suspend fun clearAllMemories(): Int = withContext(Dispatchers.IO) {
        val rows = structuredMemoryDao.clearAll()
        historyDao.insertHistory(
            MemoryChangeHistoryEntity(
                memoryKey = "*",
                category = "ALL",
                action = "CLEAR_ALL",
                changeSummary = "Cleared entire persistent memory database ($rows items removed)"
            )
        )
        rows
    }

    suspend fun searchMemories(query: String, category: MemoryCategory? = null, includeConfidential: Boolean = false): List<MemorySearchResult> {
        return searchEngine.search(query, category, includeConfidential)
    }

    // Legacy bridge methods
    suspend fun getPreference(key: String): String? = memoryDao.getPreference(key)?.value

    suspend fun setPreference(key: String, value: String, category: String = "general") {
        memoryDao.setPreference(UserPreferenceEntity(key, value, category, System.currentTimeMillis()))
        structuredMemoryDao.insertMemory(
            StructuredMemoryEntity(
                category = MemoryCategory.USER_PREFERENCES.name,
                key = key,
                value = value,
                source = MemorySource.SYSTEM_DEFAULT.name,
                consentStatus = MemoryConsentStatus.EXPLICIT_USER_CONSENT.name,
                sensitivity = MemorySensitivity.NORMAL.name
            )
        )
    }

    suspend fun saveMemory(title: String, content: String, category: String = "user_fact"): Long {
        saveCandidate(
            MemoryCandidate(
                action = com.example.memory.model.MemoryCandidateAction.SAVE,
                category = MemoryCategory.USER_PROVIDED_FACTS,
                key = title,
                value = content,
                reason = "Direct user save"
            )
        )
        return memoryDao.insertMemory(MemoryItemEntity(title = title, content = content, category = category))
    }

    suspend fun deleteMemory(item: MemoryItemEntity) {
        memoryDao.deleteMemory(item)
    }

    suspend fun getAlias(alias: String): CommandAliasEntity? = memoryDao.findAlias(alias)

    suspend fun saveAlias(alias: String, expansion: String, description: String = ""): Long {
        saveCandidate(
            MemoryCandidate(
                action = com.example.memory.model.MemoryCandidateAction.SAVE,
                category = MemoryCategory.APP_ALIASES,
                key = alias,
                value = expansion,
                reason = "User defined app alias"
            )
        )
        return memoryDao.insertAlias(CommandAliasEntity(alias = alias.trim(), expansion = expansion.trim(), description = description))
    }

    suspend fun deleteAlias(alias: CommandAliasEntity) {
        memoryDao.deleteAlias(alias)
        structuredMemoryDao.deleteByCategoryAndKey(MemoryCategory.APP_ALIASES.name, alias.alias)
    }

    suspend fun incrementAliasUsage(alias: CommandAliasEntity) {
        memoryDao.updateAlias(alias.copy(usageCount = alias.usageCount + 1))
    }

    suspend fun saveRoutine(name: String, triggerPhrase: String, description: String, actionsJson: String): Long {
        return memoryDao.insertRoutine(RoutineEntity(name = name, triggerPhrase = triggerPhrase, description = description, actionsJson = actionsJson))
    }

    suspend fun toggleRoutine(routine: RoutineEntity, isEnabled: Boolean) {
        memoryDao.updateRoutine(routine.copy(isEnabled = isEnabled))
    }

    suspend fun deleteRoutine(routine: RoutineEntity) {
        memoryDao.deleteRoutine(routine)
    }

    suspend fun recordTaskHistory(
        taskId: String,
        prompt: String,
        status: String,
        actionsCount: Int,
        executionTimeMs: Long,
        summary: String
    ): Long {
        return taskDao.insertHistory(
            TaskHistoryEntity(
                taskId = taskId,
                prompt = prompt,
                status = status,
                actionsCount = actionsCount,
                executionTimeMs = executionTimeMs,
                summary = summary
            )
        )
    }

    suspend fun clearHistory() {
        taskDao.clearHistory()
    }

    suspend fun recordLog(
        taskId: String,
        actionType: String,
        status: String,
        errorMessage: String? = null,
        durationMs: Long = 0L,
        details: String = ""
    ): Long {
        return taskDao.insertLog(
            ExecutionLogEntity(
                taskId = taskId,
                actionType = actionType,
                status = status,
                errorMessage = errorMessage,
                durationMs = durationMs,
                details = details
            )
        )
    }

    suspend fun getLogsForTask(taskId: String): List<ExecutionLogEntity> {
        return taskDao.getLogsForTask(taskId)
    }

    suspend fun clearLogs() {
        taskDao.clearLogs()
    }
}
