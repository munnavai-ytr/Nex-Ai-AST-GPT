package com.example.memory

import com.example.ai.GeminiConfig
import com.example.memory.ai.ExtractionResult
import com.example.memory.ai.MemoryCandidateExtractor
import com.example.memory.model.MemoryCandidate
import com.example.memory.model.MemoryCandidateAction
import com.example.memory.model.MemoryCategory
import com.example.memory.model.MemoryRetentionPolicy
import com.example.memory.model.MemorySearchResult
import com.example.memory.model.MemoryWriteResult
import com.example.memory.model.StructuredMemory
import kotlinx.coroutines.flow.Flow

class MemoryManager(
    val repository: MemoryRepository,
    private val geminiConfig: GeminiConfig = GeminiConfig()
) {
    val candidateExtractor = MemoryCandidateExtractor(geminiConfig)
    val allMemories: Flow<List<StructuredMemory>> = repository.allStructuredMemories

    /**
     * Attempts to process a user prompt as a memory command (e.g. "Remember that...", "Save alias...", "Forget...").
     * Returns true if the prompt was fully handled as a memory command.
     */
    suspend fun handlePotentialMemoryCommand(
        prompt: String,
        onConfirmed: suspend (String) -> Unit,
        onRequireConsent: suspend (MemoryCandidate) -> Unit
    ): Boolean {
        return when (val extraction = candidateExtractor.extractCandidate(prompt)) {
            is ExtractionResult.CandidateFound -> {
                val candidate = extraction.candidate
                when (candidate.action) {
                    MemoryCandidateAction.FORGET -> {
                        val deleted = repository.structuredMemoryDao.deleteByKey(candidate.key) +
                                repository.structuredMemoryDao.deleteByCategoryAndKey(candidate.category.name, candidate.key)
                        if (deleted > 0) {
                            onConfirmed("I have forgotten the information for '${candidate.key}'.")
                        } else {
                            onConfirmed("No memory was found matching '${candidate.key}'.")
                        }
                        true
                    }
                    MemoryCandidateAction.QUERY -> {
                        val memory = repository.searchEngine.getByKey(candidate.key)
                        if (memory != null) {
                            onConfirmed("According to my memory, ${memory.key} is: ${memory.value}")
                        } else {
                            onConfirmed("I don't have any saved memory for '${candidate.key}'.")
                        }
                        true
                    }
                    MemoryCandidateAction.SAVE, MemoryCandidateAction.UPDATE -> {
                        val writeResult = repository.saveCandidate(
                            candidate = candidate,
                            explicitConsentGiven = true,
                            retentionPolicy = MemoryRetentionPolicy.PERMANENT
                        )
                        when (writeResult) {
                            is MemoryWriteResult.Success -> {
                                onConfirmed("Saved to memory: '${candidate.key}' = '${candidate.value}'")
                                true
                            }
                            is MemoryWriteResult.Conflict -> {
                                onRequireConsent(candidate)
                                true
                            }
                            is MemoryWriteResult.Rejected -> {
                                onConfirmed("Could not save memory: ${writeResult.reason}")
                                true
                            }
                            is MemoryWriteResult.Error -> {
                                onConfirmed("Failed to save memory: ${writeResult.message}")
                                true
                            }
                        }
                    }
                }
            }
            is ExtractionResult.NotAMemoryCommand -> false
            is ExtractionResult.Error -> false
        }
    }

    /**
     * Expands user prompts with defined app and command aliases (e.g., "YT" -> "YouTube").
     */
    suspend fun expandPromptWithAliases(rawPrompt: String): String {
        var processedPrompt = rawPrompt
        try {
            // First check structured memory app aliases
            val aliasMemories = repository.structuredMemoryDao.getMemoriesByCategory(MemoryCategory.APP_ALIASES.name)
            for (entity in aliasMemories) {
                val domain = entity.toDomain()
                if (domain.isExpired) continue
                val expansion = if (domain.isEncrypted) {
                    try { repository.encryptionManager.decrypt(domain.value) } catch (_: Exception) { domain.value }
                } else {
                    domain.value
                }
                val regex = Regex("\\b${Regex.escape(domain.key)}\\b", RegexOption.IGNORE_CASE)
                if (regex.containsMatchIn(processedPrompt)) {
                    processedPrompt = regex.replace(processedPrompt, expansion)
                    repository.structuredMemoryDao.updateLastAccessed(domain.id)
                }
            }
        } catch (_: Exception) {
            // Fallback to original prompt if error
        }
        return processedPrompt
    }

    /**
     * Formats minimal approved memories for Gemini context inclusion without exposing sensitive secrets.
     */
    suspend fun formatMemoryContextForAi(limit: Int = 10): String {
        return try {
            val memories = repository.searchEngine.getApprovedContextMemories(limit)
            if (memories.isEmpty()) return "None"

            memories.joinToString("\n") { mem ->
                "- [${mem.category.displayName}] ${mem.key}: ${mem.value}"
            }
        } catch (_: Exception) {
            "None"
        }
    }

    suspend fun getPreferredLanguage(): String {
        return repository.getPreference("voice_language") ?: "en-US"
    }

    suspend fun setPreferredLanguage(language: String) {
        repository.setPreference("voice_language", language, category = "voice")
    }

    suspend fun isConfirmationRequired(): Boolean {
        return repository.getPreference("require_sensitive_confirmation")?.toBoolean() ?: true
    }

    suspend fun setConfirmationRequired(required: Boolean) {
        repository.setPreference("require_sensitive_confirmation", required.toString(), category = "security")
    }

    suspend fun getStructuredMemoriesCount(): Int {
        return repository.structuredMemoryDao.getAllMemories().size
    }

    suspend fun clearAllMemories(): Int {
        return repository.clearAllMemories()
    }
}
