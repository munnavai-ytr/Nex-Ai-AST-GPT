package com.example.automation.appcontrol

import com.example.memory.MemoryManager
import kotlinx.coroutines.flow.first

sealed class ResolutionMatchReason {
    object UserAlias : ResolutionMatchReason()
    object ExactPackage : ResolutionMatchReason()
    object ExactLabel : ResolutionMatchReason()
    object CanonicalAlias : ResolutionMatchReason()
    object FuzzyMatch : ResolutionMatchReason()
}

sealed class AppResolutionResult {
    data class Resolved(
        val app: InstalledApp,
        val matchReason: ResolutionMatchReason
    ) : AppResolutionResult()

    data class Ambiguous(
        val query: String,
        val candidates: List<InstalledApp>
    ) : AppResolutionResult()

    data class NotFound(
        val query: String,
        val suggestedAlternatives: List<InstalledApp> = emptyList()
    ) : AppResolutionResult()
}

class AppResolver(
    private val appRegistry: InstalledAppRegistry,
    private val memoryManager: MemoryManager
) {

    // Common system abbreviations mapped to canonical queries
    private val canonicalAliases = mapOf(
        "yt" to listOf("com.google.android.youtube", "youtube"),
        "browser" to listOf("com.android.chrome", "chrome", "browser", "internet"),
        "web" to listOf("com.android.chrome", "chrome", "browser"),
        "settings" to listOf("com.android.settings", "settings"),
        "music" to listOf("com.google.android.apps.youtube.music", "spotify", "music"),
        "mail" to listOf("com.google.android.gm", "gmail", "email"),
        "email" to listOf("com.google.android.gm", "gmail"),
        "camera" to listOf("com.android.camera", "com.google.android.GoogleCamera", "camera"),
        "maps" to listOf("com.google.android.apps.maps", "maps"),
        "calc" to listOf("com.google.android.calculator", "calculator"),
        "clock" to listOf("com.google.android.deskclock", "com.android.deskclock", "clock")
    )

    suspend fun resolve(rawQuery: String): AppResolutionResult {
        val query = rawQuery.trim()
        if (query.isEmpty()) return AppResolutionResult.NotFound("")

        val installedApps = appRegistry.getInstalledApps().ifEmpty {
            appRegistry.refresh()
        }

        val queryLower = query.lowercase()

        // 1. Check user-defined aliases from MemoryManager / Room repository
        try {
            val userAliases = memoryManager.repository.aliases.first()
            val matchedAlias = userAliases.firstOrNull { it.alias.trim().equals(queryLower, ignoreCase = true) }
            if (matchedAlias != null) {
                // Try resolving target expansion by package name first, then by label
                val target = matchedAlias.expansion.trim()
                val targetApp = installedApps.firstOrNull {
                    it.packageName.equals(target, ignoreCase = true) ||
                            it.applicationLabel.equals(target, ignoreCase = true)
                }
                if (targetApp != null) {
                    memoryManager.repository.incrementAliasUsage(matchedAlias)
                    return AppResolutionResult.Resolved(targetApp, ResolutionMatchReason.UserAlias)
                }
            }
        } catch (_: Exception) {
            // Memory check fallback
        }

        // 2. Exact package name match
        val exactPackage = installedApps.firstOrNull { it.packageName.equals(query, ignoreCase = true) }
        if (exactPackage != null) {
            return AppResolutionResult.Resolved(exactPackage, ResolutionMatchReason.ExactPackage)
        }

        // 3. Exact application label match (case-insensitive)
        val exactLabelMatches = installedApps.filter { it.applicationLabel.equals(query, ignoreCase = true) }
        if (exactLabelMatches.size == 1) {
            return AppResolutionResult.Resolved(exactLabelMatches.first(), ResolutionMatchReason.ExactLabel)
        } else if (exactLabelMatches.size > 1) {
            return AppResolutionResult.Ambiguous(query, exactLabelMatches)
        }

        // 4. Canonical alias fallback
        val canonicalTargets = canonicalAliases[queryLower]
        if (canonicalTargets != null) {
            for (target in canonicalTargets) {
                val candidate = installedApps.firstOrNull {
                    it.packageName.equals(target, ignoreCase = true) ||
                            it.applicationLabel.equals(target, ignoreCase = true)
                }
                if (candidate != null) {
                    return AppResolutionResult.Resolved(candidate, ResolutionMatchReason.CanonicalAlias)
                }
            }
        }

        // 5. Prefix match or contains match
        val prefixMatches = installedApps.filter {
            it.applicationLabel.lowercase().startsWith(queryLower)
        }
        if (prefixMatches.size == 1) {
            return AppResolutionResult.Resolved(prefixMatches.first(), ResolutionMatchReason.FuzzyMatch)
        } else if (prefixMatches.size > 1) {
            return AppResolutionResult.Ambiguous(query, prefixMatches)
        }

        val containsMatches = installedApps.filter {
            it.applicationLabel.lowercase().contains(queryLower) ||
                    it.packageName.lowercase().contains(queryLower)
        }
        if (containsMatches.size == 1) {
            return AppResolutionResult.Resolved(containsMatches.first(), ResolutionMatchReason.FuzzyMatch)
        } else if (containsMatches.size > 1) {
            return AppResolutionResult.Ambiguous(query, containsMatches)
        }

        // 6. Not found - provide top suggestions if any apps match loosely
        val suggestions = installedApps.take(3)
        return AppResolutionResult.NotFound(query, suggestions)
    }
}
