package com.example.diagnostics.report

import android.os.Build
import com.example.diagnostics.model.CujTestCase
import com.example.diagnostics.model.CujTestStatus
import com.example.diagnostics.model.DiagnosticCategory
import com.example.diagnostics.model.DiagnosticStatus
import com.example.diagnostics.model.ReadinessAuditReport
import com.example.diagnostics.model.SubsystemDiagnosticResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DiagnosticReportExporter {

    fun generateReport(
        subsystemResults: List<SubsystemDiagnosticResult>,
        cujResults: List<CujTestCase>
    ): ReadinessAuditReport {
        var totalItems = 0
        var passedItems = 0
        var warnItems = 0
        var failedItems = 0

        subsystemResults.forEach { sub ->
            sub.items.forEach { item ->
                totalItems++
                when (item.status) {
                    DiagnosticStatus.PASS -> passedItems++
                    DiagnosticStatus.WARN -> warnItems++
                    DiagnosticStatus.FAIL -> failedItems++
                    else -> {}
                }
            }
        }

        val cujPassed = cujResults.count { it.status == CujTestStatus.PASS }
        val cujTotal = cujResults.size

        // Weighted readiness calculation:
        // Subsystem checks: 65% weight
        // CUJ real-device passes: 35% weight
        val subsystemRatio = if (totalItems > 0) {
            ((passedItems * 1.0 + warnItems * 0.5) / totalItems.toDouble()).coerceIn(0.0, 1.0)
        } else 0.5

        val cujRatio = if (cujTotal > 0) {
            (cujPassed.toDouble() / cujTotal.toDouble()).coerceIn(0.0, 1.0)
        } else 0.0

        val score = ((subsystemRatio * 65.0) + (cujRatio * 35.0)).toInt().coerceIn(0, 100)

        val grade = when {
            score >= 90 -> "A+ (CERTIFIED PRODUCTION READY)"
            score >= 80 -> "A (PRODUCTION READY)"
            score >= 70 -> "B (FIELD TEST READY)"
            score >= 50 -> "C (NEEDS CONFIGURATION)"
            else -> "F (BLOCKED / CRITICAL ISSUES)"
        }

        val summary = when {
            score >= 85 -> "NEX is fully configured and ready for daily driver use on this device."
            score >= 70 -> "Core automation and intelligence operational. Minor permissions or settings pending."
            else -> "Key subsystems require configuration or permissions before full autonomous operation."
        }

        val recommendations = mutableListOf<String>()
        subsystemResults.forEach { sub ->
            sub.items.filter { 
                it.status == DiagnosticStatus.FAIL || 
                it.status == DiagnosticStatus.WARN ||
                it.status == DiagnosticStatus.NOT_CONFIGURED ||
                it.status == DiagnosticStatus.NOT_VERIFIED ||
                it.status == DiagnosticStatus.PERMISSION_REQUIRED
            }.forEach { item ->
                recommendations.add("[${sub.category.title}] ${item.title}: ${item.summary}")
            }
        }

        if (cujPassed < cujTotal) {
            recommendations.add("Execute remaining ${cujTotal - cujPassed} interactive CUJ real-device validations.")
        }

        return ReadinessAuditReport(
            timestamp = System.currentTimeMillis(),
            deviceModel = Build.MODEL ?: "Android Device",
            manufacturer = Build.MANUFACTURER ?: "Google",
            androidVersion = Build.VERSION.RELEASE ?: "15",
            sdkInt = if (Build.VERSION.SDK_INT > 0) Build.VERSION.SDK_INT else 36,
            appVersion = "1.0",
            readinessScore = score,
            readinessGrade = grade,
            readinessSummary = summary,
            subsystemResults = subsystemResults,
            cujResults = cujResults,
            recommendations = recommendations
        )
    }

    fun toMarkdown(report: ReadinessAuditReport): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US)
        val formattedDate = sdf.format(Date(report.timestamp))

        val sb = StringBuilder()
        sb.append("# NEX RELEASE READINESS & DEEP DIAGNOSTICS REPORT\n\n")
        sb.append("**Generated:** $formattedDate  \n")
        sb.append("**Target Device:** ${report.manufacturer} ${report.deviceModel} (Android ${report.androidVersion}, API ${report.sdkInt})  \n")
        sb.append("**App Version:** ${report.appVersion}  \n\n")

        sb.append("## Overall Readiness Assessment\n\n")
        sb.append("- **Readiness Score:** ${report.readinessScore} / 100\n")
        sb.append("- **Certification Grade:** `${report.readinessGrade}`\n")
        sb.append("- **Verdict:** ${report.readinessSummary}\n\n")

        sb.append("## Subsystem Diagnostic Audit\n\n")
        sb.append("| Subsystem | Status | Passed | Warn | Failed |\n")
        sb.append("| :--- | :---: | :---: | :---: | :---: |\n")

        report.subsystemResults.forEach { sub ->
            val statusEmoji = when (sub.status) {
                DiagnosticStatus.PASS -> "✅ PASS"
                DiagnosticStatus.WARN -> "⚠️ WARN"
                DiagnosticStatus.FAIL -> "❌ FAIL"
                else -> "ℹ️ INFO"
            }
            sb.append("| ${sub.category.title} | $statusEmoji | ${sub.passCount} | ${sub.warnCount} | ${sub.failCount} |\n")
        }

        sb.append("\n### Detailed Check Findings\n\n")
        report.subsystemResults.forEach { sub ->
            sb.append("#### ${sub.category.title}\n")
            sub.items.forEach { item ->
                val badge = when (item.status) {
                    DiagnosticStatus.PASS -> "[PASS]"
                    DiagnosticStatus.WARN -> "[WARN]"
                    DiagnosticStatus.FAIL -> "[FAIL]"
                    else -> "[INFO]"
                }
                sb.append("- **$badge ${item.title}**: ${item.summary}\n")
                if (!item.details.isNullOrBlank()) {
                    sb.append("  ```\n  ${item.details.replace("\n", "\n  ")}\n  ```\n")
                }
            }
            sb.append("\n")
        }

        sb.append("## Interactive Real-Device CUJ Validations\n\n")
        sb.append("| CUJ Test | Category | Status | Notes |\n")
        sb.append("| :--- | :--- | :---: | :--- |\n")
        report.cujResults.forEach { cuj ->
            val cujBadge = when (cuj.status) {
                CujTestStatus.PASS -> "✅ PASS"
                CujTestStatus.FAIL -> "❌ FAIL"
                CujTestStatus.SKIP -> "⏭️ SKIP"
                CujTestStatus.RUNNING -> "🔄 RUNNING"
                CujTestStatus.UNTESTED -> "⏳ UNTESTED"
            }
            sb.append("| ${cuj.title} | ${cuj.category.title} | $cujBadge | ${cuj.notes.ifBlank { "-" }} |\n")
        }

        if (report.recommendations.isNotEmpty()) {
            sb.append("\n## Action Items & Recommendations\n\n")
            report.recommendations.forEachIndexed { idx, rec ->
                sb.append("${idx + 1}. $rec\n")
            }
        }

        sb.append("\n---\n*Report generated natively by NEX Operating System Layer Diagnostics Engine.*")
        return sb.toString()
    }

    fun toJson(report: ReadinessAuditReport): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"timestamp\": ${report.timestamp},\n")
        sb.append("  \"device\": {\n")
        sb.append("    \"manufacturer\": \"${escape(report.manufacturer)}\",\n")
        sb.append("    \"model\": \"${escape(report.deviceModel)}\",\n")
        sb.append("    \"androidVersion\": \"${escape(report.androidVersion)}\",\n")
        sb.append("    \"sdkInt\": ${report.sdkInt}\n")
        sb.append("  },\n")
        sb.append("  \"readiness\": {\n")
        sb.append("    \"score\": ${report.readinessScore},\n")
        sb.append("    \"grade\": \"${escape(report.readinessGrade)}\",\n")
        sb.append("    \"summary\": \"${escape(report.readinessSummary)}\"\n")
        sb.append("  },\n")
        sb.append("  \"subsystems\": [\n")

        val subJsonList = report.subsystemResults.map { sub ->
            val itemsJson = sub.items.map { item ->
                """      {
        "id": "${escape(item.id)}",
        "title": "${escape(item.title)}",
        "status": "${item.status.name}",
        "summary": "${escape(item.summary)}"
      }"""
            }.joinToString(",\n")

            """    {
      "category": "${sub.category.name}",
      "title": "${escape(sub.category.title)}",
      "status": "${sub.status.name}",
      "passCount": ${sub.passCount},
      "warnCount": ${sub.warnCount},
      "failCount": ${sub.failCount},
      "items": [
$itemsJson
      ]
    }"""
        }.joinToString(",\n")

        sb.append(subJsonList)
        sb.append("\n  ],\n")

        sb.append("  \"cujTests\": [\n")
        val cujJsonList = report.cujResults.map { cuj ->
            """    {
      "id": "${escape(cuj.id)}",
      "title": "${escape(cuj.title)}",
      "status": "${cuj.status.name}",
      "notes": "${escape(cuj.notes)}"
    }"""
        }.joinToString(",\n")
        sb.append(cujJsonList)
        sb.append("\n  ]\n")
        sb.append("}")
        return sb.toString()
    }

    private fun escape(s: String): String {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
