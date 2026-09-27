package com.example.voice

import java.util.UUID

enum class TranscriptSender {
    USER,
    NEX
}

data class VoiceTranscriptItem(
    val id: String = UUID.randomUUID().toString(),
    val sender: TranscriptSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)
