package com.example.security.events

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

enum class SecurityEventType {
    OWNER_ENROLLMENT_SUCCESS,
    OWNER_ENROLLMENT_FAILED,
    VOICE_VERIFICATION_MATCH,
    VOICE_VERIFICATION_MISMATCH,
    VOICE_VERIFICATION_INCONCLUSIVE,
    BIOMETRIC_AUTH_SUCCESS,
    BIOMETRIC_AUTH_FAILED,
    BIOMETRIC_AUTH_CANCELLED,
    VOICE_PROFILE_DELETED,
    PERMISSION_GRANTED,
    PERMISSION_REVOKED,
    SENSITIVE_ACTION_CONFIRMED,
    SENSITIVE_ACTION_DENIED,
    CONFIG_CHANGED,
    SECURITY_LOCKOUT
}

@Entity(
    tableName = "security_events",
    indices = [
        Index(value = ["eventType"]),
        Index(value = ["timestamp"])
    ]
)
data class SecurityEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventId: String = UUID.randomUUID().toString(),
    val eventType: String,
    val description: String,
    val outcome: String, // SUCCESS, FAILED, CANCELLED, INFO
    val details: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface SecurityEventDao {

    @Query("SELECT * FROM security_events ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentEventsFlow(limit: Int = 100): Flow<List<SecurityEventEntity>>

    @Query("SELECT * FROM security_events ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentEvents(limit: Int = 100): List<SecurityEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: SecurityEventEntity): Long

    @Query("DELETE FROM security_events")
    suspend fun clearAllEvents(): Int
}

class SecurityEventLogger(
    private val securityEventDao: SecurityEventDao? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val _recentEvents = MutableStateFlow<List<SecurityEventEntity>>(emptyList())
    val recentEvents: StateFlow<List<SecurityEventEntity>> = _recentEvents.asStateFlow()

    fun logEvent(
        eventType: SecurityEventType,
        description: String,
        outcome: String = "SUCCESS",
        details: String = ""
    ) {
        val event = SecurityEventEntity(
            eventType = eventType.name,
            description = description,
            outcome = outcome,
            details = details,
            timestamp = System.currentTimeMillis()
        )

        _recentEvents.value = listOf(event) + _recentEvents.value.take(49)

        if (securityEventDao != null) {
            scope.launch {
                try {
                    securityEventDao.insertEvent(event)
                } catch (_: Exception) {}
            }
        }
    }

    fun clearHistory() {
        _recentEvents.value = emptyList()
        if (securityEventDao != null) {
            scope.launch {
                try {
                    securityEventDao.clearAllEvents()
                } catch (_: Exception) {}
            }
        }
    }
}
