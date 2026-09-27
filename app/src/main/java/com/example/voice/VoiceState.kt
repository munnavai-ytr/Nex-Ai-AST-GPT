package com.example.voice

sealed class VoiceState {
    object Idle : VoiceState()
    data class Listening(val rmsDb: Float = 0f, val partialText: String = "") : VoiceState()
    object Processing : VoiceState()
    data class Speaking(val text: String) : VoiceState()
    data class Error(val message: String, val errorCode: Int? = null) : VoiceState()
    data class PermissionRequired(val explanation: String = "Microphone access is required for NEX to hear your voice commands.") : VoiceState()
    data class Unavailable(val reason: String = "Speech recognition service is not available on this device.") : VoiceState()
}

enum class VoiceLanguage(val code: String, val displayName: String, val speechTag: String) {
    ENGLISH_US("en-US", "English (United States)", "en-US"),
    BENGALI_BD("bn-BD", "বাংলা (Bangladesh)", "bn-BD"),
    BENGALI_IN("bn-IN", "বাংলা (India)", "bn-IN")
}
