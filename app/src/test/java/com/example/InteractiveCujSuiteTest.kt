package com.example

import com.example.diagnostics.model.CujTestStatus
import com.example.diagnostics.model.DiagnosticCategory
import com.example.diagnostics.model.DiagnosticItem
import com.example.diagnostics.model.DiagnosticStatus
import com.example.diagnostics.model.SubsystemDiagnosticResult
import com.example.diagnostics.report.DiagnosticReportExporter
import com.example.diagnostics.suite.InteractiveCujSuite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class InteractiveCujSuiteTest {

    @Test
    fun testCujSuiteLifecycle() {
        val suite = InteractiveCujSuite()
        val defaultTests = suite.testCases.value
        assertEquals(8, defaultTests.size)

        // Pass first test
        val test1 = defaultTests[0]
        suite.updateTestStatus(test1.id, CujTestStatus.PASS, "Microphone and TTS audio tested perfectly")

        val updatedTests = suite.testCases.value
        val updatedTest1 = updatedTests.find { it.id == test1.id }
        assertNotNull(updatedTest1)
        assertEquals(CujTestStatus.PASS, updatedTest1?.status)
        assertEquals("Microphone and TTS audio tested perfectly", updatedTest1?.notes)

        // Reset
        suite.resetAllTests()
        val resetTests = suite.testCases.value
        assertEquals(CujTestStatus.UNTESTED, resetTests[0].status)
    }

    @Test
    fun testDiagnosticReportExporter() {
        val sub1 = SubsystemDiagnosticResult(
            category = DiagnosticCategory.SYSTEM,
            items = listOf(
                DiagnosticItem("item1", "Test Item 1", DiagnosticCategory.SYSTEM, DiagnosticStatus.PASS, "OK"),
                DiagnosticItem("item2", "Test Item 2", DiagnosticCategory.SYSTEM, DiagnosticStatus.PASS, "OK")
            ),
            status = DiagnosticStatus.PASS
        )

        val suite = InteractiveCujSuite()
        val report = DiagnosticReportExporter.generateReport(listOf(sub1), suite.testCases.value)

        assertNotNull(report)
        assertTrue(report.readinessScore in 0..100)
        assertNotNull(report.readinessGrade)

        val md = DiagnosticReportExporter.toMarkdown(report)
        assertTrue(md.contains("# NEX RELEASE READINESS & DEEP DIAGNOSTICS REPORT"))
        assertTrue(md.contains("System & Hardware"))

        val json = DiagnosticReportExporter.toJson(report)
        assertTrue(json.contains("\"readiness\""))
        assertTrue(json.contains("\"subsystems\""))
    }
}
