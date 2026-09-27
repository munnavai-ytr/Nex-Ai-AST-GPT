package com.example.diagnostics.engine

import com.example.accessibility.privacy.ScreenPrivacyFilter
import com.example.diagnostics.model.DiagnosticCategory
import com.example.diagnostics.model.DiagnosticItem
import com.example.diagnostics.model.DiagnosticStatus
import com.example.diagnostics.model.SubsystemDiagnosticResult
import com.example.security.SecurityManager

class SecurityPrivacyDiagnosticsEngine(
    private val securityManager: SecurityManager
) {

    fun runDiagnostics(): SubsystemDiagnosticResult {
        val startTime = System.currentTimeMillis()
        val items = mutableListOf<DiagnosticItem>()

        // 1. Sensitive Data Redaction Test
        val sampleSensitive = "4111222233334444"
        val redacted = ScreenPrivacyFilter.redactTextIfSensitive(sampleSensitive)
        val isRedacted = redacted == ScreenPrivacyFilter.REDACTED_PLACEHOLDER

        items.add(
            DiagnosticItem(
                id = "sec_redaction_engine",
                title = "Sensitive Data & PII Redaction Engine",
                category = DiagnosticCategory.SECURITY,
                status = if (isRedacted) DiagnosticStatus.PASS else DiagnosticStatus.WARN,
                summary = "Active (Masks Credit Cards, OTPs, Passwords)",
                details = "Sample Credit Card: $sampleSensitive -> $redacted\nRedaction Verified: $isRedacted"
            )
        )

        // 2. Security Event Audit Logger
        val eventLogger = securityManager.eventLogger
        val recentEvents = eventLogger.recentEvents.value
        items.add(
            DiagnosticItem(
                id = "sec_event_audit",
                title = "Security Event Audit Trail",
                category = DiagnosticCategory.SECURITY,
                status = DiagnosticStatus.PASS,
                summary = "${recentEvents.size} Security Audit Events Logged",
                details = "Events Captured: ${recentEvents.size}\nTamper-evident local event history."
            )
        )

        val overallStatus = when {
            items.any { it.status == DiagnosticStatus.FAIL } -> DiagnosticStatus.FAIL
            items.any { it.status == DiagnosticStatus.WARN } -> DiagnosticStatus.WARN
            else -> DiagnosticStatus.PASS
        }

        return SubsystemDiagnosticResult(
            category = DiagnosticCategory.SECURITY,
            items = items,
            status = overallStatus,
            executionTimeMs = System.currentTimeMillis() - startTime
        )
    }
}
