package com.example.voice

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class WakeWordAvailability(val displayName: String) {
    AVAILABLE("Available (Active Session)"),
    WAKE_WORD_UNAVAILABLE("Hardware DSP Missing / Background Restricted"),
    BACKGROUND_RESTRICTION("Android Background Mic Restricted"),
    PERMISSION_MISSING("Microphone Permission Required"),
    DISABLED_BY_USER("Disabled")
}

data class WakeWordStatus(
    val keyword: String = "Hey NEX",
    val isListening: Boolean = false,
    val availability: WakeWordAvailability = WakeWordAvailability.WAKE_WORD_UNAVAILABLE,
    val explanation: String = "Always-on keyword listening requires dedicated hardware DSP or Android Assistant Role. When active foreground service is enabled, NEX can detect wake phrases."
)

interface WakeWordManager {
    val status: StateFlow<WakeWordStatus>
    fun isSupported(): Boolean
    fun setWakePhrase(phrase: String)
    fun getWakePhrase(): String
    fun startListeningForWakeWord(onWakeWordDetected: () -> Unit): Boolean
    fun stopListeningForWakeWord()
}

class SystemWakeWordManager(
    private val context: Context,
    private val permissionManager: VoicePermissionManager
) : WakeWordManager {

    private val prefs: SharedPreferences = context.getSharedPreferences("nex_wakeword_prefs", Context.MODE_PRIVATE)

    private val _status = MutableStateFlow(
        WakeWordStatus(
            keyword = prefs.getString("wake_phrase", "Hey NEX") ?: "Hey NEX",
            isListening = false,
            availability = if (!permissionManager.checkPermission()) {
                WakeWordAvailability.PERMISSION_MISSING
            } else {
                WakeWordAvailability.WAKE_WORD_UNAVAILABLE
            },
            explanation = "Always-on keyword listening requires dedicated hardware DSP or system assistant role on modern Android. Use the primary mic button to trigger NEX voice sessions."
        )
    )
    override val status: StateFlow<WakeWordStatus> = _status.asStateFlow()

    override fun isSupported(): Boolean {
        return false // Hardware DSP hotword detector unavailable for third-party non-system apps
    }

    override fun setWakePhrase(phrase: String) {
        val trimmed = phrase.trim().ifBlank { "Hey NEX" }
        prefs.edit().putString("wake_phrase", trimmed).apply()
        _status.value = _status.value.copy(keyword = trimmed)
    }

    override fun getWakePhrase(): String {
        return prefs.getString("wake_phrase", "Hey NEX") ?: "Hey NEX"
    }

    override fun startListeningForWakeWord(onWakeWordDetected: () -> Unit): Boolean {
        if (!permissionManager.checkPermission()) {
            _status.value = _status.value.copy(
                isListening = false,
                availability = WakeWordAvailability.PERMISSION_MISSING
            )
            return false
        }

        _status.value = _status.value.copy(
            isListening = false,
            availability = WakeWordAvailability.WAKE_WORD_UNAVAILABLE
        )
        return false
    }

    override fun stopListeningForWakeWord() {
        _status.value = _status.value.copy(isListening = false)
    }
}
