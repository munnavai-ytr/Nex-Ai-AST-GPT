package com.example.security

enum class AuthenticationLevel(val level: Int, val displayName: String, val description: String) {
    LEVEL_0_PUBLIC(
        0,
        "Level 0: Open Navigation",
        "Harmless UI navigation, status reads, and conversational queries"
    ),
    LEVEL_1_VOICE_VERIFIED(
        1,
        "Level 1: Owner Voice Verified",
        "Ordinary voice commands verified against enrolled acoustic speaker profile"
    ),
    LEVEL_2_DEVICE_CREDENTIAL(
        2,
        "Level 2: Biometric / Device Security",
        "Sensitive operations requiring Android BiometricPrompt or PIN/Pattern confirmation"
    );

    companion object {
        fun fromLevel(level: Int): AuthenticationLevel {
            return values().firstOrNull { it.level == level } ?: LEVEL_0_PUBLIC
        }
    }
}
