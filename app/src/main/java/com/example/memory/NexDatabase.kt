package com.example.memory

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
import com.example.memory.entities.WorkflowEntity
import com.example.security.events.SecurityEventDao
import com.example.security.events.SecurityEventEntity

@Database(
    entities = [
        StructuredMemoryEntity::class,
        WorkflowEntity::class,
        MemoryChangeHistoryEntity::class,
        UserPreferenceEntity::class,
        MemoryItemEntity::class,
        CommandAliasEntity::class,
        RoutineEntity::class,
        TaskHistoryEntity::class,
        ExecutionLogEntity::class,
        SecurityEventEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class NexDatabase : RoomDatabase() {

    abstract fun structuredMemoryDao(): StructuredMemoryDao
    abstract fun workflowDao(): WorkflowDao
    abstract fun memoryHistoryDao(): MemoryHistoryDao
    abstract fun memoryDao(): MemoryDao
    abstract fun taskDao(): TaskDao
    abstract fun securityEventDao(): SecurityEventDao

    companion object {
        @Volatile
        private var INSTANCE: NexDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create structured_memories table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `structured_memories` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `category` TEXT NOT NULL,
                        `key` TEXT NOT NULL,
                        `value` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `source` TEXT NOT NULL,
                        `consentStatus` TEXT NOT NULL,
                        `sensitivity` TEXT NOT NULL,
                        `expiration` INTEGER,
                        `lastAccessedAt` INTEGER NOT NULL,
                        `isEncrypted` INTEGER NOT NULL,
                        `metadataJson` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_structured_memories_category` ON `structured_memories` (`category`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_structured_memories_key` ON `structured_memories` (`key`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_structured_memories_category_key` ON `structured_memories` (`category`, `key`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_structured_memories_expiration` ON `structured_memories` (`expiration`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_structured_memories_updatedAt` ON `structured_memories` (`updatedAt`)")

                // Create workflows table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `workflows` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `workflowId` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `triggerPhrase` TEXT NOT NULL,
                        `actionsJson` TEXT NOT NULL,
                        `requiredParamsJson` TEXT NOT NULL,
                        `preconditionsJson` TEXT NOT NULL,
                        `requiresConfirmation` INTEGER NOT NULL,
                        `isEnabled` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_workflows_workflowId` ON `workflows` (`workflowId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_workflows_triggerPhrase` ON `workflows` (`triggerPhrase`)")

                // Create memory_change_history table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `memory_change_history` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `memoryKey` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `action` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `changeSummary` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_memory_change_history_memoryKey` ON `memory_change_history` (`memoryKey`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_memory_change_history_timestamp` ON `memory_change_history` (`timestamp`)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `security_events` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `eventId` TEXT NOT NULL,
                        `eventType` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `outcome` TEXT NOT NULL,
                        `details` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_security_events_eventType` ON `security_events` (`eventType`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_security_events_timestamp` ON `security_events` (`timestamp`)")
            }
        }

        fun getInstance(context: Context): NexDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NexDatabase::class.java,
                    "nex_assistant.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
