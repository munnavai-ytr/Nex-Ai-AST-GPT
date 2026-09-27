package com.example.memory.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.memory.entities.CommandAliasEntity
import com.example.memory.entities.ExecutionLogEntity
import com.example.memory.entities.MemoryChangeHistoryEntity
import com.example.memory.entities.MemoryItemEntity
import com.example.memory.entities.RoutineEntity
import com.example.memory.entities.StructuredMemoryEntity
import com.example.memory.entities.TaskHistoryEntity
import com.example.memory.entities.UserPreferenceEntity
import com.example.memory.entities.WorkflowEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StructuredMemoryDao {

    @Query("SELECT * FROM structured_memories ORDER BY updatedAt DESC")
    fun getAllMemoriesFlow(): Flow<List<StructuredMemoryEntity>>

    @Query("SELECT * FROM structured_memories ORDER BY updatedAt DESC")
    suspend fun getAllMemories(): List<StructuredMemoryEntity>

    @Query("SELECT * FROM structured_memories WHERE category = :category ORDER BY updatedAt DESC")
    fun getMemoriesByCategoryFlow(category: String): Flow<List<StructuredMemoryEntity>>

    @Query("SELECT * FROM structured_memories WHERE category = :category ORDER BY updatedAt DESC")
    suspend fun getMemoriesByCategory(category: String): List<StructuredMemoryEntity>

    @Query("SELECT * FROM structured_memories WHERE `key` = :key LIMIT 1")
    suspend fun findByKey(key: String): StructuredMemoryEntity?

    @Query("SELECT * FROM structured_memories WHERE category = :category AND `key` = :key LIMIT 1")
    suspend fun findByCategoryAndKey(category: String, key: String): StructuredMemoryEntity?

    @Query("SELECT * FROM structured_memories WHERE `key` LIKE '%' || :query || '%' OR `value` LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    suspend fun searchMemories(query: String): List<StructuredMemoryEntity>

    @Query("SELECT * FROM structured_memories WHERE consentStatus = 'EXPLICIT_USER_CONSENT' AND sensitivity != 'STRICTLY_CONFIDENTIAL' ORDER BY lastAccessedAt DESC LIMIT :limit")
    suspend fun getApprovedContextMemories(limit: Int = 20): List<StructuredMemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: StructuredMemoryEntity): Long

    @Update
    suspend fun updateMemory(memory: StructuredMemoryEntity)

    @Delete
    suspend fun deleteMemory(memory: StructuredMemoryEntity)

    @Query("DELETE FROM structured_memories WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM structured_memories WHERE `key` = :key")
    suspend fun deleteByKey(key: String): Int

    @Query("DELETE FROM structured_memories WHERE category = :category AND `key` = :key")
    suspend fun deleteByCategoryAndKey(category: String, key: String): Int

    @Query("DELETE FROM structured_memories WHERE category = :category")
    suspend fun deleteByCategory(category: String): Int

    @Query("DELETE FROM structured_memories")
    suspend fun clearAll(): Int

    @Query("SELECT * FROM structured_memories WHERE expiration IS NOT NULL AND expiration <= :currentTime")
    suspend fun getExpiredMemories(currentTime: Long): List<StructuredMemoryEntity>

    @Query("DELETE FROM structured_memories WHERE expiration IS NOT NULL AND expiration <= :currentTime")
    suspend fun deleteExpiredMemories(currentTime: Long): Int

    @Query("UPDATE structured_memories SET lastAccessedAt = :time WHERE id = :id")
    suspend fun updateLastAccessed(id: Long, time: Long = System.currentTimeMillis())
}

@Dao
interface WorkflowDao {

    @Query("SELECT * FROM workflows ORDER BY updatedAt DESC")
    fun getAllWorkflowsFlow(): Flow<List<WorkflowEntity>>

    @Query("SELECT * FROM workflows WHERE isEnabled = 1 ORDER BY name ASC")
    suspend fun getEnabledWorkflows(): List<WorkflowEntity>

    @Query("SELECT * FROM workflows WHERE workflowId = :workflowId LIMIT 1")
    suspend fun getWorkflowById(workflowId: String): WorkflowEntity?

    @Query("SELECT * FROM workflows WHERE LOWER(triggerPhrase) = LOWER(:triggerPhrase) LIMIT 1")
    suspend fun findByTrigger(triggerPhrase: String): WorkflowEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkflow(workflow: WorkflowEntity): Long

    @Update
    suspend fun updateWorkflow(workflow: WorkflowEntity)

    @Delete
    suspend fun deleteWorkflow(workflow: WorkflowEntity)

    @Query("DELETE FROM workflows WHERE workflowId = :workflowId")
    suspend fun deleteByWorkflowId(workflowId: String): Int
}

@Dao
interface MemoryHistoryDao {

    @Query("SELECT * FROM memory_change_history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentHistoryFlow(limit: Int = 100): Flow<List<MemoryChangeHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: MemoryChangeHistoryEntity): Long

    @Query("DELETE FROM memory_change_history")
    suspend fun clearHistory()
}

// Legacy / Compatibility DAOs preserved
@Dao
interface MemoryDao {

    // User Preferences
    @Query("SELECT * FROM user_preferences")
    fun getAllPreferences(): Flow<List<UserPreferenceEntity>>

    @Query("SELECT * FROM user_preferences WHERE `key` = :key LIMIT 1")
    suspend fun getPreference(key: String): UserPreferenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setPreference(pref: UserPreferenceEntity)

    // Memory Items
    @Query("SELECT * FROM memory_items ORDER BY createdAt DESC")
    fun getAllMemories(): Flow<List<MemoryItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(item: MemoryItemEntity): Long

    @Delete
    suspend fun deleteMemory(item: MemoryItemEntity)

    // Command Aliases
    @Query("SELECT * FROM command_aliases ORDER BY usageCount DESC, alias ASC")
    fun getAllAliases(): Flow<List<CommandAliasEntity>>

    @Query("SELECT * FROM command_aliases WHERE LOWER(alias) = LOWER(:alias) LIMIT 1")
    suspend fun findAlias(alias: String): CommandAliasEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlias(alias: CommandAliasEntity): Long

    @Update
    suspend fun updateAlias(alias: CommandAliasEntity)

    @Delete
    suspend fun deleteAlias(alias: CommandAliasEntity)

    // Routines
    @Query("SELECT * FROM routines ORDER BY createdAt DESC")
    fun getAllRoutines(): Flow<List<RoutineEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutine(routine: RoutineEntity): Long

    @Update
    suspend fun updateRoutine(routine: RoutineEntity)

    @Delete
    suspend fun deleteRoutine(routine: RoutineEntity)
}

@Dao
interface TaskDao {

    // Task History
    @Query("SELECT * FROM task_history ORDER BY timestamp DESC LIMIT 50")
    fun getRecentHistory(): Flow<List<TaskHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: TaskHistoryEntity): Long

    @Query("DELETE FROM task_history")
    suspend fun clearHistory()

    // Execution Logs
    @Query("SELECT * FROM execution_logs ORDER BY timestamp DESC LIMIT 100")
    fun getRecentLogs(): Flow<List<ExecutionLogEntity>>

    @Query("SELECT * FROM execution_logs WHERE taskId = :taskId ORDER BY timestamp ASC")
    suspend fun getLogsForTask(taskId: String): List<ExecutionLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ExecutionLogEntity): Long

    @Query("DELETE FROM execution_logs")
    suspend fun clearLogs()
}
