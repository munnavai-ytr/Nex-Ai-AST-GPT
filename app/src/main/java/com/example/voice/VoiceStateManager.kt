package com.example.voice

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class VoiceStateManager {

    private val _voiceState = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _transcripts = MutableStateFlow<List<VoiceTranscriptItem>>(emptyList())
    val transcripts: StateFlow<List<VoiceTranscriptItem>> = _transcripts.asStateFlow()

    private val _lastRecognizedText = MutableStateFlow("")
    val lastRecognizedText: StateFlow<String> = _lastRecognizedText.asStateFlow()

    private val _lastResponseText = MutableStateFlow("")
    val lastResponseText: StateFlow<String> = _lastResponseText.asStateFlow()

    fun setState(state: VoiceState) {
        _voiceState.value = state
    }

    fun addUserTranscript(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        _lastRecognizedText.value = trimmed
        val newItem = VoiceTranscriptItem(
            sender = TranscriptSender.USER,
            text = trimmed
        )
        _transcripts.value = _transcripts.value + newItem
    }

    fun addNexResponse(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        _lastResponseText.value = trimmed
        val newItem = VoiceTranscriptItem(
            sender = TranscriptSender.NEX,
            text = trimmed
        )
        _transcripts.value = _transcripts.value + newItem
    }

    fun clearTranscripts() {
        _transcripts.value = emptyList()
        _lastRecognizedText.value = ""
        _lastResponseText.value = ""
    }
}
