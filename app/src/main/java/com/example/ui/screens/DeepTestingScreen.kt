package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.NexApplication
import com.example.diagnostics.model.CujTestCase
import com.example.diagnostics.model.CujTestStatus
import com.example.diagnostics.model.DiagnosticCategory
import com.example.diagnostics.model.DiagnosticItem
import com.example.diagnostics.model.DiagnosticStatus
import com.example.diagnostics.model.SubsystemDiagnosticResult
import com.example.diagnostics.report.DiagnosticReportExporter
import com.example.navigation.NexNavDestination
import com.example.ui.components.NexBadge
import com.example.ui.components.NexCard
import com.example.ui.theme.NexAmber
import com.example.ui.theme.NexBorder
import com.example.ui.theme.NexBorderLight
import com.example.ui.theme.NexCyan
import com.example.ui.theme.NexCyanLight
import com.example.ui.theme.NexEmerald
import com.example.ui.theme.NexEmeraldGlow
import com.example.ui.theme.NexIndigo
import com.example.ui.theme.NexIndigoLight
import com.example.ui.theme.NexObsidian
import com.example.ui.theme.NexRose
import com.example.ui.theme.NexSurfaceCard
import com.example.ui.theme.NexSurfaceElevated
import com.example.ui.theme.NexTextMuted
import com.example.ui.theme.NexTextPrimary
import com.example.ui.theme.NexTextSecondary
import kotlinx.coroutines.launch

@Composable
fun DeepTestingScreen(
    navController: NavController,
    app: NexApplication = NexApplication.instance
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val coordinator = app.diagnosticsCoordinator

    val isRunning by coordinator.isRunning.collectAsStateWithLifecycle()
    val subsystemResults by coordinator.subsystemResults.collectAsStateWithLifecycle()
    val cujTests by coordinator.cujSuite.testCases.collectAsStateWithLifecycle()
    val latestReport by coordinator.latestReport.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Subsystems", "Interactive CUJs", "Performance", "Report & Export")

    val expandedCategories = remember { mutableStateMapOf<DiagnosticCategory, Boolean>() }
    val expandedItems = remember { mutableStateMapOf<String, Boolean>() }

    // Run diagnostics automatically on first launch if empty
    LaunchedEffect(Unit) {
        if (subsystemResults.isEmpty()) {
            coordinator.runAllDiagnostics()
        }
    }

    val totalSubsystems = DiagnosticCategory.values().size
    val currentSubsystemsTested = subsystemResults.size

    val report = latestReport ?: coordinator.compileCurrentReport()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(NexObsidian)
            .padding(horizontal = 16.dp)
            .testTag("deep_testing_screen")
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))

            // Header Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = NexTextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "TESTING & DIAGNOSTICS",
                            color = NexTextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Deep Subsystem Health & Real-Device Validation",
                            color = NexTextMuted,
                            fontSize = 11.sp
                        )
                    }
                }

                if (isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = NexCyanLight,
                        strokeWidth = 2.dp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Hero Readiness Card
            ReadinessScoreCard(
                report = report,
                isRunning = isRunning,
                onRunAll = {
                    scope.launch {
                        coordinator.runAllDiagnostics()
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Navigation Tab Row
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = NexSurfaceElevated,
                contentColor = NexCyanLight,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = NexCyanLight
                    )
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .testTag("diagnostics_tab_row")
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == index) NexCyanLight else NexTextMuted
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        when (selectedTab) {
            0 -> {
                // Subsystems tab
                items(DiagnosticCategory.values()) { category ->
                    val result = subsystemResults[category]
                    val isExpanded = expandedCategories[category] ?: (category == DiagnosticCategory.SYSTEM || category == DiagnosticCategory.PERMISSIONS)

                    SubsystemAccordionCard(
                        category = category,
                        result = result,
                        isExpanded = isExpanded,
                        onToggleExpand = { expandedCategories[category] = !isExpanded },
                        onRunCategory = {
                            scope.launch {
                                coordinator.runSubsystem(category)
                            }
                        },
                        expandedItems = expandedItems,
                        onToggleItemExpand = { itemId ->
                            expandedItems[itemId] = !(expandedItems[itemId] ?: false)
                        },
                        onItemAction = { item ->
                            handleDiagnosticAction(item, context, navController, app)
                        }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
            1 -> {
                // Interactive CUJs Tab
                item {
                    Text(
                        text = "REAL-DEVICE INTERACTIVE TEST SUITE",
                        color = NexTextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                items(cujTests) { test ->
                    CujTestCard(
                        test = test,
                        onPass = { notes ->
                            coordinator.cujSuite.updateTestStatus(test.id, CujTestStatus.PASS, notes)
                            coordinator.compileCurrentReport()
                        },
                        onFail = { notes ->
                            coordinator.cujSuite.updateTestStatus(test.id, CujTestStatus.FAIL, notes)
                            coordinator.compileCurrentReport()
                        },
                        onSkip = {
                            coordinator.cujSuite.updateTestStatus(test.id, CujTestStatus.SKIP, "Skipped by tester")
                            coordinator.compileCurrentReport()
                        }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                item {
                    OutlinedButton(
                        onClick = {
                            coordinator.cujSuite.resetAllTests()
                            coordinator.compileCurrentReport()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, NexBorder)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp), tint = NexTextMuted)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reset All CUJ Test Results", color = NexTextMuted, fontSize = 12.sp)
                    }
                }
            }
            2 -> {
                // Performance Profiler Tab
                item {
                    PerformanceTabContent(app = app, report = report)
                }
            }
            3 -> {
                // Report & Export Tab
                item {
                    ReportExportTabContent(report = report, context = context)
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ReadinessScoreCard(
    report: com.example.diagnostics.model.ReadinessAuditReport,
    isRunning: Boolean,
    onRunAll: () -> Unit
) {
    NexCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = NexSurfaceElevated,
        borderColor = when {
            report.readinessScore >= 80 -> NexEmerald.copy(alpha = 0.5f)
            report.readinessScore >= 60 -> NexAmber.copy(alpha = 0.5f)
            else -> NexRose.copy(alpha = 0.5f)
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "RELEASE READINESS",
                        color = NexTextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = report.readinessGrade,
                        color = when {
                            report.readinessScore >= 80 -> NexEmerald
                            report.readinessScore >= 60 -> NexAmber
                            else -> NexRose
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                // Big Circular Badge
                Surface(
                    color = NexObsidian,
                    shape = CircleShape,
                    border = BorderStroke(
                        2.dp,
                        when {
                            report.readinessScore >= 80 -> NexEmerald
                            report.readinessScore >= 60 -> NexAmber
                            else -> NexRose
                        }
                    ),
                    modifier = Modifier.size(54.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "${report.readinessScore}%",
                            color = NexTextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LinearProgressIndicator(
                progress = { (report.readinessScore / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = when {
                    report.readinessScore >= 80 -> NexEmerald
                    report.readinessScore >= 60 -> NexAmber
                    else -> NexRose
                },
                trackColor = NexObsidian
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = report.readinessSummary,
                color = NexTextSecondary,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onRunAll,
                enabled = !isRunning,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NexIndigoLight,
                    disabledContainerColor = NexSurfaceCard
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("run_all_diagnostics_button")
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isRunning) "ANALYZING SYSTEM..." else "RUN FULL DIAGNOSTICS SUITE",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

@Composable
private fun SubsystemAccordionCard(
    category: DiagnosticCategory,
    result: SubsystemDiagnosticResult?,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onRunCategory: () -> Unit,
    expandedItems: Map<String, Boolean>,
    onToggleItemExpand: (String) -> Unit,
    onItemAction: (DiagnosticItem) -> Unit
) {
    val status = result?.status ?: DiagnosticStatus.NOT_TESTED

    NexCard(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        backgroundColor = NexSurfaceElevated,
        borderColor = when (status) {
            DiagnosticStatus.PASS -> NexEmerald.copy(alpha = 0.3f)
            DiagnosticStatus.WARN, DiagnosticStatus.NOT_CONFIGURED, DiagnosticStatus.NOT_VERIFIED -> NexAmber.copy(alpha = 0.3f)
            DiagnosticStatus.FAIL, DiagnosticStatus.PERMISSION_REQUIRED -> NexRose.copy(alpha = 0.4f)
            else -> NexBorder
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(status.color, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = category.title,
                            color = NexTextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = category.description,
                            color = NexTextMuted,
                            fontSize = 10.sp
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    NexBadge(text = status.displayLabel, color = status.color)
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Expand",
                        tint = NexTextMuted
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(color = NexBorder, thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(8.dp))

                    if (result == null || result.items.isEmpty()) {
                        Text(
                            text = "No diagnostic results yet. Tap Run Subsystem.",
                            color = NexTextMuted,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        result.items.forEach { item ->
                            val isItemDetailsExpanded = expandedItems[item.id] ?: false
                            DiagnosticItemRow(
                                item = item,
                                isDetailsExpanded = isItemDetailsExpanded,
                                onToggleDetails = { onToggleItemExpand(item.id) },
                                onAction = { onItemAction(item) }
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (result != null) {
                            Text(
                                text = "Duration: ${result.executionTimeMs}ms (${result.passCount} passed, ${result.warnCount} warn, ${result.failCount} fail)",
                                color = NexTextMuted,
                                fontSize = 10.sp
                            )
                        } else {
                            Spacer(modifier = Modifier.width(1.dp))
                        }

                        OutlinedButton(
                            onClick = onRunCategory,
                            border = BorderStroke(1.dp, NexBorderLight),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(12.dp), tint = NexCyanLight)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Re-test Subsystem", color = NexCyanLight, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticItemRow(
    item: DiagnosticItem,
    isDetailsExpanded: Boolean,
    onToggleDetails: () -> Unit,
    onAction: () -> Unit
) {
    Surface(
        color = NexObsidian,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(0.5.dp, NexBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleDetails)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        color = NexTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.summary,
                        color = NexTextSecondary,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                NexBadge(text = item.status.displayLabel, color = item.status.color)
            }

            if (item.isActionable && item.actionLabel != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onAction,
                    colors = ButtonDefaults.buttonColors(containerColor = NexIndigoLight),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text(item.actionLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            AnimatedVisibility(visible = isDetailsExpanded && !item.details.isNullOrBlank()) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Surface(
                        color = NexSurfaceElevated,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = item.details ?: "",
                            color = NexCyanLight,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CujTestCard(
    test: CujTestCase,
    onPass: (notes: String) -> Unit,
    onFail: (notes: String) -> Unit,
    onSkip: () -> Unit
) {
    var notesText by remember { mutableStateOf(test.notes) }
    var isExpanded by remember { mutableStateOf(test.status == CujTestStatus.UNTESTED) }

    NexCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = NexSurfaceElevated,
        borderColor = when (test.status) {
            CujTestStatus.PASS -> NexEmerald.copy(alpha = 0.5f)
            CujTestStatus.FAIL -> NexRose.copy(alpha = 0.5f)
            CujTestStatus.SKIP -> NexTextMuted.copy(alpha = 0.3f)
            else -> NexBorder
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = test.title,
                        color = NexTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = test.description,
                        color = NexTextMuted,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                NexBadge(
                    text = test.status.name,
                    color = when (test.status) {
                        CujTestStatus.PASS -> NexEmerald
                        CujTestStatus.FAIL -> NexRose
                        CujTestStatus.SKIP -> NexTextMuted
                        CujTestStatus.RUNNING -> NexIndigoLight
                        CujTestStatus.UNTESTED -> NexAmber
                    }
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    HorizontalDivider(color = NexBorder, thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(8.dp))

                    if (test.prerequisites.isNotEmpty()) {
                        Text(
                            text = "PREREQUISITES:",
                            color = NexTextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        test.prerequisites.forEach { req ->
                            Text(text = "• $req", color = NexTextSecondary, fontSize = 11.sp)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    Text(
                        text = "TEST EXECUTION STEPS:",
                        color = NexTextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    test.steps.forEach { step ->
                        Text(text = step, color = NexTextPrimary, fontSize = 11.sp)
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "EXPECTED OUTCOME:",
                        color = NexTextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = test.expectedOutcome,
                        color = NexCyanLight,
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = notesText,
                        onValueChange = { notesText = it },
                        label = { Text("Tester Observations / Logs") },
                        placeholder = { Text("e.g. YouTube launched cleanly in 450ms") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = NexTextPrimary,
                            unfocusedTextColor = NexTextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = { onPass(notesText) },
                            colors = ButtonDefaults.buttonColors(containerColor = NexEmerald),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("PASS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { onFail(notesText) },
                            colors = ButtonDefaults.buttonColors(containerColor = NexRose),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("FAIL", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onSkip,
                            border = BorderStroke(1.dp, NexBorder),
                            modifier = Modifier.weight(0.8f)
                        ) {
                            Text("SKIP", color = NexTextMuted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PerformanceTabContent(
    app: NexApplication,
    report: com.example.diagnostics.model.ReadinessAuditReport
) {
    val runtime = Runtime.getRuntime()
    val totalMemoryMb = runtime.totalMemory() / (1024 * 1024)
    val freeMemoryMb = runtime.freeMemory() / (1024 * 1024)
    val usedMemoryMb = totalMemoryMb - freeMemoryMb
    val maxMemoryMb = runtime.maxMemory() / (1024 * 1024)

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "RUNTIME RESOURCE PROFILER",
            color = NexTextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        NexCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = NexSurfaceElevated,
            borderColor = NexBorder
        ) {
            Column {
                Text(
                    text = "Dalvik / ART VM Heap Allocation",
                    color = NexTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Allocated: ${usedMemoryMb} MB", color = NexCyanLight, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(text = "Max Allowed: ${maxMemoryMb} MB", color = NexTextMuted, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(6.dp))

                val ratio = if (maxMemoryMb > 0) (usedMemoryMb.toFloat() / maxMemoryMb.toFloat()) else 0f
                LinearProgressIndicator(
                    progress = { ratio.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = if (ratio > 0.8f) NexRose else NexIndigoLight,
                    trackColor = NexObsidian
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    MetricStatCard(label = "Allocated", value = "${usedMemoryMb}MB", modifier = Modifier.weight(1f))
                    MetricStatCard(label = "Free Pool", value = "${freeMemoryMb}MB", modifier = Modifier.weight(1f))
                    MetricStatCard(label = "Max Limit", value = "${maxMemoryMb}MB", modifier = Modifier.weight(1f))
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        NexCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = NexSurfaceElevated,
            borderColor = NexBorder
        ) {
            Column {
                Text(
                    text = "Subsystem Latency Breakdown",
                    color = NexTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                report.subsystemResults.forEach { sub ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Text(text = sub.category.title, color = NexTextSecondary, fontSize = 12.sp)
                        Text(text = "${sub.executionTimeMs}ms", color = NexCyanLight, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricStatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        color = NexObsidian,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(0.5.dp, NexBorder),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
        ) {
            Text(text = value, color = NexTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(text = label, color = NexTextMuted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun ReportExportTabContent(
    report: com.example.diagnostics.model.ReadinessAuditReport,
    context: Context
) {
    val markdownReport = remember(report) { DiagnosticReportExporter.toMarkdown(report) }
    val jsonReport = remember(report) { DiagnosticReportExporter.toJson(report) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "CERTIFIED DIAGNOSTIC & READINESS REPORT",
            color = NexTextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("NEX Readiness Report", markdownReport)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "Markdown report copied to clipboard!", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = NexCyan),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Copy Markdown", fontSize = 12.sp)
            }

            Button(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("NEX Diagnostic JSON", jsonReport)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "JSON report copied to clipboard!", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = NexIndigoLight),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Copy JSON", fontSize = 12.sp)
            }

            IconButton(
                onClick = {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "NEX Release Readiness Report")
                        putExtra(Intent.EXTRA_TEXT, markdownReport)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share NEX Diagnostic Report"))
                }
            ) {
                Icon(Icons.Default.Share, contentDescription = "Share", tint = NexCyanLight)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        NexCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = NexSurfaceElevated,
            borderColor = NexBorder
        ) {
            Text(
                text = markdownReport,
                color = NexTextSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(4.dp)
            )
        }
    }
}

private fun handleDiagnosticAction(
    item: DiagnosticItem,
    context: Context,
    navController: NavController,
    app: NexApplication
) {
    when (item.id) {
        "perm_mic", "perm_notifications", "perm_battery_opt" -> {
            navController.navigate(NexNavDestination.Permissions.route)
        }
        "perm_accessibility", "auto_a11y_service" -> {
            app.accessibilityController.openAccessibilitySettings()
        }
        "auth_voice_profile" -> {
            navController.navigate(NexNavDestination.VoiceEnrollment.route)
        }
        "gemini_api_key" -> {
            navController.navigate(NexNavDestination.Settings.route)
        }
        "perm_biometric" -> {
            navController.navigate(NexNavDestination.Security.route)
        }
    }
}
