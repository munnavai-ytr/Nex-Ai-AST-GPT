package com.example.accessibility.privacy

import com.example.accessibility.model.ScreenElement
import com.example.accessibility.model.ScreenSnapshot
import java.util.regex.Pattern

object ScreenPrivacyFilter {

    const val REDACTED_PLACEHOLDER = "[REDACTED_SENSITIVE_FIELD]"

    // Patterns for common sensitive data: standalone 4-8 digit OTPs, 13-19 digit credit cards
    private val OTP_PATTERN = Pattern.compile("(?<!\\d)\\d{4,8}(?!\\d)")
    private val CREDIT_CARD_PATTERN = Pattern.compile("\\b(?:\\d[ -]*?){13,19}\\b")

    private val SENSITIVE_ID_KEYWORDS = listOf(
        "password", "passwd", "pwd", "pin", "passcode",
        "otp", "cvv", "cvc", "secret", "token", "auth_code",
        "card_number", "ssn", "credentials"
    )

    private val SENSITIVE_CLASS_KEYWORDS = listOf(
        "password"
    )

    private val SENSITIVE_PACKAGES = setOf(
        "com.google.android.apps.authenticator2",
        "com.authy.authy",
        "com.onepassword.android",
        "com.lastpass.lpandroid",
        "com.bitwarden.mobile",
        "org.keepassdroid"
    )

    fun isSensitivePackage(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        return SENSITIVE_PACKAGES.contains(packageName.lowercase()) ||
                packageName.contains("banking", ignoreCase = true) ||
                packageName.contains("password", ignoreCase = true)
    }

    fun isSensitiveElement(element: ScreenElement): Boolean {
        if (element.isPassword) return true

        val resourceIdLower = element.resourceId?.lowercase() ?: ""
        if (SENSITIVE_ID_KEYWORDS.any { resourceIdLower.contains(it) }) {
            return true
        }

        val classNameLower = element.className.lowercase()
        if (SENSITIVE_CLASS_KEYWORDS.any { classNameLower.contains(it) }) {
            return true
        }

        // Check if visible text looks like a standalone OTP or Card Number
        val text = element.text
        if (!text.isNullOrBlank() && (element.editable || resourceIdLower.isNotEmpty())) {
            if (CREDIT_CARD_PATTERN.matcher(text).matches()) return true
            if (OTP_PATTERN.matcher(text.trim()).matches() && text.trim().length in 4..8) {
                // If it's pure digits in an input/sensitive context
                return true
            }
        }

        return false
    }

    fun sanitizeElement(element: ScreenElement): ScreenElement {
        if (!isSensitiveElement(element)) {
            return element
        }

        return element.copy(
            text = REDACTED_PLACEHOLDER,
            contentDescription = if (element.contentDescription != null) REDACTED_PLACEHOLDER else null,
            isPassword = true
        )
    }

    fun filterSnapshot(snapshot: ScreenSnapshot): ScreenSnapshot {
        val sanitizedElements = snapshot.elements.map { sanitizeElement(it) }
        return snapshot.copy(
            elements = sanitizedElements,
            isRedacted = true
        )
    }

    fun redactTextIfSensitive(text: String?): String? {
        if (text.isNullOrBlank()) return text
        if (CREDIT_CARD_PATTERN.matcher(text).matches()) return REDACTED_PLACEHOLDER
        return text
    }
}
