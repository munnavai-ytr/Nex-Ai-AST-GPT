package com.example

import com.example.automation.Action
import com.example.automation.ActionParameters
import com.example.automation.ActionType
import com.example.memory.ai.ExtractionResult
import com.example.memory.ai.MemoryCandidateExtractor
import com.example.memory.model.MemoryCandidate
import com.example.memory.model.MemoryCandidateAction
import com.example.memory.model.MemoryCategory
import com.example.memory.model.MemoryConsentStatus
import com.example.memory.model.MemoryRetentionPolicy
import com.example.memory.model.MemorySensitivity
import com.example.memory.model.StructuredMemory
import com.example.memory.policy.MemoryPolicy
import com.example.memory.policy.PolicyEvaluationResult
import com.example.memory.security.MemoryEncryptionManager
import com.example.memory.workflow.StructuredWorkflow
import com.example.memory.workflow.WorkflowManager
import com.example.memory.workflow.WorkflowValidationResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MemorySystemTest {

    private lateinit var policy: MemoryPolicy
    private lateinit var encryptionManager: MemoryEncryptionManager

    @Before
    fun setup() {
        policy = MemoryPolicy()
        encryptionManager = MemoryEncryptionManager()
    }

    @Test
    fun testEncryptionAndDecryptionRoundtrip() {
        val plainText = "My secret home address: 123 Main Street"
        val encrypted = encryptionManager.encrypt(plainText)
        assertNotNull(encrypted)
        assertTrue(encrypted.isNotBlank())
        assertFalse(encrypted.contains("Main Street"))

        val decrypted = encryptionManager.decrypt(encrypted)
        assertEquals(plainText, decrypted)
    }

    @Test
    fun testPolicyRejectsForbiddenCredentials() {
        val pinCandidate = MemoryCandidate(
            action = MemoryCandidateAction.SAVE,
            category = MemoryCategory.USER_PROVIDED_FACTS,
            key = "bank_account_pin",
            value = "1234"
        )
        val result1 = policy.evaluateCandidate(pinCandidate, consentGiven = true)
        assertTrue(result1 is PolicyEvaluationResult.Rejected)

        val passwordCandidate = MemoryCandidate(
            action = MemoryCandidateAction.SAVE,
            category = MemoryCategory.USER_PROVIDED_FACTS,
            key = "my_email_password",
            value = "SecretPassword123"
        )
        val result2 = policy.evaluateCandidate(passwordCandidate, consentGiven = true)
        assertTrue(result2 is PolicyEvaluationResult.Rejected)
    }

    @Test
    fun testPolicyRequiresConsentWhenConsentNotGiven() {
        val candidate = MemoryCandidate(
            action = MemoryCandidateAction.SAVE,
            category = MemoryCategory.USER_PROVIDED_FACTS,
            key = "favorite_food",
            value = "Biryani"
        )
        val result = policy.evaluateCandidate(candidate, consentGiven = false)
        assertTrue(result is PolicyEvaluationResult.RequiresExplicitConsent)
    }

    @Test
    fun testPolicyAllowsValidCandidateWithConsent() {
        val candidate = MemoryCandidate(
            action = MemoryCandidateAction.SAVE,
            category = MemoryCategory.USER_PREFERENCES,
            key = "voice_language",
            value = "bn-BD"
        )
        val result = policy.evaluateCandidate(candidate, consentGiven = true)
        assertTrue(result is PolicyEvaluationResult.Allowed)
    }

    @Test
    fun testSensitivityClassification() {
        val confidential = policy.classifySensitivity("bank_salary", "Confidential monthly amount", MemoryCategory.USER_PROVIDED_FACTS)
        assertEquals(MemorySensitivity.STRICTLY_CONFIDENTIAL, confidential)

        val sensitive = policy.classifySensitivity("home_location", "Dhaka, Bangladesh", MemoryCategory.USER_PROVIDED_FACTS)
        assertEquals(MemorySensitivity.SENSITIVE, sensitive)

        val normal = policy.classifySensitivity("favorite_color", "Emerald Cyan", MemoryCategory.USER_PREFERENCES)
        assertEquals(MemorySensitivity.NORMAL, normal)
    }

    @Test
    fun testMemoryCandidateExtractorFastLocalEnglishAndBengali() = runBlocking {
        val extractor = MemoryCandidateExtractor(com.example.ai.GeminiConfig())

        // English alias
        val res1 = extractor.extractCandidate("Save this alias: YT means YouTube")
        assertTrue(res1 is ExtractionResult.CandidateFound)
        val cand1 = (res1 as ExtractionResult.CandidateFound).candidate
        assertEquals("YT", cand1.key)
        assertEquals("YouTube", cand1.value)
        assertEquals(MemoryCategory.APP_ALIASES, cand1.category)

        // English preference
        val res2 = extractor.extractCandidate("Remember that I prefer Bengali")
        assertTrue(res2 is ExtractionResult.CandidateFound)
        val cand2 = (res2 as ExtractionResult.CandidateFound).candidate
        assertEquals("voice_language", cand2.key)
        assertEquals("bn-BD", cand2.value)

        // Bengali explicit remember
        val res3 = extractor.extractCandidate("মনে রাখো: আমার অফিস হলো ধানমন্ডি")
        assertTrue(res3 is ExtractionResult.CandidateFound)
        val cand3 = (res3 as ExtractionResult.CandidateFound).candidate
        assertEquals(MemoryCategory.USER_PROVIDED_FACTS, cand3.category)

        // Forget command
        val res4 = extractor.extractCandidate("Forget my previous work schedule")
        assertTrue(res4 is ExtractionResult.CandidateFound)
        val cand4 = (res4 as ExtractionResult.CandidateFound).candidate
        assertEquals(MemoryCandidateAction.FORGET, cand4.action)
    }

    @Test
    fun testRetentionPolicyCalculation() {
        val now = 1000000L
        val perm = MemoryRetentionPolicy.PERMANENT.durationMs
        assertEquals(null, perm)

        val days7 = MemoryRetentionPolicy.DAYS_7.durationMs
        assertNotNull(days7)
        assertEquals(7L * 24 * 60 * 60 * 1000, days7)
    }

    @Test
    fun testWorkflowValidation() {
        val validWorkflow = StructuredWorkflow(
            name = "Morning News Routine",
            description = "Opens Chrome and searches today's news",
            triggerPhrase = "morning routine",
            actions = listOf(
                Action(
                    type = ActionType.OPEN_APP,
                    parameters = ActionParameters(appName = "Chrome", packageName = "com.android.chrome"),
                    description = "Open Chrome browser"
                ),
                Action(
                    type = ActionType.SEARCH,
                    parameters = ActionParameters(query = "Today's top headlines"),
                    description = "Search headlines"
                )
            )
        )

        val workflowManager = WorkflowManager(
            object : com.example.memory.dao.WorkflowDao {
                override fun getAllWorkflowsFlow() = kotlinx.coroutines.flow.flowOf(emptyList<com.example.memory.entities.WorkflowEntity>())
                override suspend fun getEnabledWorkflows() = emptyList<com.example.memory.entities.WorkflowEntity>()
                override suspend fun getWorkflowById(workflowId: String) = null
                override suspend fun findByTrigger(triggerPhrase: String) = null
                override suspend fun insertWorkflow(workflow: com.example.memory.entities.WorkflowEntity) = 1L
                override suspend fun updateWorkflow(workflow: com.example.memory.entities.WorkflowEntity) {}
                override suspend fun deleteWorkflow(workflow: com.example.memory.entities.WorkflowEntity) {}
                override suspend fun deleteByWorkflowId(workflowId: String) = 1
            }
        )

        val validation = workflowManager.validateWorkflow(validWorkflow)
        assertTrue(validation is WorkflowValidationResult.Valid)

        val invalidWorkflow = StructuredWorkflow(
            name = "",
            actions = emptyList()
        )
        val invalidValidation = workflowManager.validateWorkflow(invalidWorkflow)
        assertTrue(invalidValidation is WorkflowValidationResult.Invalid)
    }
}
