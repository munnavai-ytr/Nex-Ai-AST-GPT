package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.NexApplication
import com.example.automation.Action
import com.example.automation.ActionParameters
import com.example.automation.ActionType
import com.example.automation.ValidationResult
import com.example.automation.engine.DeviceAutomationStatus
import com.example.automation.permissions.AccessibilityStatus
import com.example.ui.components.NexBadge
import com.example.ui.components.NexCard
import com.example.ui.theme.NexAmber
import com.example.ui.theme.NexBorder
import com.example.ui.theme.NexBorderDark
import com.example.ui.theme.NexCardBackground
import com.example.ui.theme.NexCyanLight
import com.example.ui.theme.NexEmerald
import com.example.ui.theme.NexError
import com.example.ui.theme.NexIndigo
import com.example.ui.theme.NexIndigoLight
import com.example.ui.theme.NexObsidian
import com.example.ui.theme.NexRose
import com.example.ui.theme.NexSurface
import com.example.ui.theme.NexSurfaceElevated
import com.example.ui.theme.NexTextMuted
import com.example.ui.theme.NexTextPrimary
import com.example.ui.theme.NexTextSecondary
import com.example.ui.theme.NexWarning

data class BenchmarkCommand(
    val title: String,
    val command: String,
    val language: String,
    val category: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationScreen(
    navController: NavController,
    app: NexApplication = NexApplication.instance
) {
    val automationManager = app.deviceAutomationManager
    val permissionManager = app.automationPermissionManager

    val sessionState by automationManager.sessionState.collectAsState()
    val permissionState by permissionManager.permissionState.collectAsState()
    val screenState by automationManager.screenState.collectAsState()
    val recentLogs by app.automationExecutionLogger.recentLogs.collectAsState()

    var customCommandInput by remember { mutableStateOf("") }
    var selectedActionType by remember { mutableStateOf(ActionType.OPEN_APP) }
    var expandedDropdown by remember { mutableStateOf(false) }

    var paramAppName by remember { mutableStateOf("YouTube") }
    var paramTargetText by remember { mutableStateOf("Search") }
    var paramInputText by remember { mutableStateOf("Trending tech") }
    var paramDirection by remember { mutableStateOf("DOWN") }
    var paramDurationMs by remember { mutableStateOf("1500") }

    var validationResult by remember { mutableStateOf<ValidationResult?>(null) }

    val benchmarkCommands = listOf(
        BenchmarkCommand("Open YouTube", "Hey NEX, open YouTube", "EN", "App Launch"),
        BenchmarkCommand("ইউটিউব ওপেন করো", "ইউটিউব ওপেন করো", "BN", "App Launch"),
        BenchmarkCommand("YouTube Search", "Hey NEX, open YouTube and search for a video about Android development", "EN", "Compound"),
        BenchmarkCommand("ইউটিউব ভিডিও সার্চ", "ইউটিউব খুলে অ্যান্ড্রয়েড ডেভেলপমেন্ট সার্চ করো", "BN", "Compound"),
        BenchmarkCommand("Wi-Fi Settings", "Hey NEX, open Settings and show me the Wi-Fi settings", "EN", "System"),
        BenchmarkCommand("ওয়াইফাই সেটিংস", "সেটিংস খুলে ওয়াইফাই দেখাও", "BN", "System"),
        BenchmarkCommand("Scroll Down", "Hey NEX, scroll down", "EN", "Navigation"),
        BenchmarkCommand("নিচে স্ক্রোল করো", "নিচে স্ক্রোল করো", "BN", "Navigation"),
        BenchmarkCommand("Open First Result", "Hey NEX, open the first result", "EN", "UI Action"),
        BenchmarkCommand("প্রথম রেজাল্ট খোলো", "প্রথম রেজাল্টটা খোলো", "BN", "UI Action"),
        BenchmarkCommand("Type Text", "Hey NEX, type this text into the current text field: NEX Automation Active", "EN", "Text Input"),
        BenchmarkCommand("লেখা টাইপ করো", "এই লেখাটা টাইপ করো: নেক্স অটোমেশন চালু", "BN", "Text Input"),
        BenchmarkCommand("Go Back", "Hey NEX, go back", "EN", "Navigation"),
        BenchmarkCommand("পিছনে যাও", "পিছনে যাও", "BN", "Navigation"),
        BenchmarkCommand("Open Chrome & Navigate", "Hey NEX, open Chrome and navigate to google.com", "EN", "Compound"),
        BenchmarkCommand("ক্রোম ব্রাউজ করো", "ক্রোম ওপেন করে গুগল ডট কম এ যাও", "BN", "Compound")
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(NexObsidian)
            .padding(horizontal = 20.dp)
            .testTag("automation_screen")
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))

            // Top App Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = NexTextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "DEVICE AUTOMATION ENGINE",
                        color = NexTextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Real Android Operator • English & Bengali AI Pipeline",
                        color = NexTextMuted,
                        fontSize = 11.sp
                    )
                }
                IconButton(onClick = {
                    permissionManager.refresh()
                    automationManager.inspectScreen()
                }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = NexCyanLight
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 1. Accessibility Service Status Banner
            val isAccEnabled = permissionState.accessibilityStatus == AccessibilityStatus.ENABLED
            val isConnected = permissionState.isAccessibilityConnected

            NexCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = NexSurfaceElevated,
                borderColor = if (isConnected) NexEmerald.copy(alpha = 0.5f) else NexWarning.copy(alpha = 0.5f)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(if (isConnected) NexEmerald else NexWarning, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ACCESSIBILITY SERVICE",
                                color = NexTextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }

                        NexBadge(
                            text = if (isConnected) "CONNECTED" else if (isAccEnabled) "BINDING" else "DISABLED",
                            color = if (isConnected) NexEmerald else if (isAccEnabled) NexAmber else NexError
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (isConnected) {
                            "NEX Accessibility Service is connected and actively inspecting UI hierarchy & dispatching gestures."
                        } else {
                            "Enable NEX Accessibility Service in Android Settings to allow real-time UI understanding and automation."
                        },
                        color = NexTextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )

                    if (!isConnected) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { permissionManager.openAccessibilitySettings() },
                            colors = ButtonDefaults.buttonColors(containerColor = NexIndigoLight),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("enable_accessibility_btn")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open Android Accessibility Settings", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Real-Time Command Execution Bar
            NexCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = NexSurface,
                borderColor = NexBorder
            ) {
                Column {
                    Text(
                        text = "LIVE OPERATOR COMMAND DISPATCH",
                        color = NexTextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = customCommandInput,
                        onValueChange = { customCommandInput = it },
                        placeholder = { Text("e.g., 'Open YouTube and search for Kotlin tutorials' or 'সেটিংস খুলে ওয়াইফাই দেখাও'") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = NexTextPrimary,
                            unfocusedTextColor = NexTextPrimary,
                            focusedBorderColor = NexCyanLight,
                            unfocusedBorderColor = NexBorderDark
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_command_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                if (customCommandInput.isNotBlank()) {
                                    automationManager.executeCommand(customCommandInput)
                                }
                            },
                            enabled = customCommandInput.isNotBlank() && sessionState.status != DeviceAutomationStatus.EXECUTING,
                            colors = ButtonDefaults.buttonColors(containerColor = NexCyanLight, contentColor = NexObsidian),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("dispatch_command_btn")
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Execute Plan", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        if (sessionState.status == DeviceAutomationStatus.EXECUTING || sessionState.status == DeviceAutomationStatus.PLANNING) {
                            Button(
                                onClick = { automationManager.cancelActiveAutomation() },
                                colors = ButtonDefaults.buttonColors(containerColor = NexRose),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("stop_automation_btn")
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Stop", fontSize = 12.sp)
                            }
                        }
                    }

                    // Execution status indicator
                    if (sessionState.status != DeviceAutomationStatus.IDLE) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(NexObsidian)
                                .border(1.dp, NexBorderDark, RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "STATUS: ${sessionState.status.name}",
                                        color = when (sessionState.status) {
                                            DeviceAutomationStatus.COMPLETED -> NexEmerald
                                            DeviceAutomationStatus.FAILED -> NexError
                                            DeviceAutomationStatus.EXECUTING -> NexCyanLight
                                            DeviceAutomationStatus.PLANNING -> NexIndigoLight
                                            else -> NexAmber
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )

                                    if (sessionState.totalSteps > 0) {
                                        Text(
                                            text = "Step ${sessionState.currentStepIndex} / ${sessionState.totalSteps}",
                                            color = NexTextMuted,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                sessionState.activeStepDescription?.let { desc ->
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = desc, color = NexTextPrimary, fontSize = 12.sp)
                                }

                                sessionState.spokenFeedback?.let { feedback ->
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Spoken: \"$feedback\"",
                                        color = NexTextMuted,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 3. Benchmark Commands Matrix
            Text(
                text = "PUSH 9 BENCHMARK COMMANDS (ENGLISH & BENGALI)",
                color = NexTextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Benchmark quick buttons grid
        items(benchmarkCommands) { cmd ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(NexCardBackground)
                    .border(1.dp, NexBorderDark, RoundedCornerShape(10.dp))
                    .clickable {
                        customCommandInput = cmd.command
                        automationManager.executeCommand(cmd.command)
                    }
                    .padding(12.dp)
                    .testTag("benchmark_btn_${cmd.title.replace(" ", "_")}")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = cmd.title,
                                color = NexTextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            NexBadge(text = cmd.language, color = if (cmd.language == "BN") NexAmber else NexCyanLight)
                            Spacer(modifier = Modifier.width(4.dp))
                            NexBadge(text = cmd.category, color = NexTextMuted)
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = cmd.command,
                            color = NexTextSecondary,
                            fontSize = 11.sp
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Run",
                        tint = NexCyanLight,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))

            // 4. Live Screen State Inspector
            Text(
                text = "LIVE SCREEN HIERARCHY PERCEPTION",
                color = NexTextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            NexCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = NexSurfaceElevated,
                borderColor = NexBorder
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Active App: ${screenState.activePackage ?: "Unknown/Launcher"}",
                            color = NexTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        IconButton(
                            onClick = { automationManager.inspectScreen() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Inspect", tint = NexCyanLight, modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        MetricMiniBadge("Nodes", "${screenState.elementCount}", NexCyanLight)
                        MetricMiniBadge("Clickable", "${screenState.interactiveCount}", NexEmerald)
                        MetricMiniBadge("Editable", "${screenState.editableCount}", NexAmber)
                        MetricMiniBadge("Scrollable", "${screenState.scrollableCount}", NexIndigoLight)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 5. Recent Automation Execution Logs
            Text(
                text = "EXECUTION & VERIFICATION TELEMETRY",
                color = NexTextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (recentLogs.isEmpty()) {
            item {
                Text(
                    text = "No automation actions executed yet in this session.",
                    color = NexTextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
        } else {
            items(recentLogs.take(10)) { log ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(NexCardBackground)
                        .border(1.dp, NexBorderDark, RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = log.actionType.name,
                                color = NexTextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                NexBadge(
                                    text = log.status.name,
                                    color = if (log.status.name == "SUCCESS") NexEmerald else NexRose
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                NexBadge(
                                    text = log.verificationStatus,
                                    color = if (log.verificationStatus == "VERIFIED") NexCyanLight else NexAmber
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = log.description, color = NexTextSecondary, fontSize = 11.sp)
                        if (log.details.isNotBlank()) {
                            Text(text = log.details, color = NexTextMuted, fontSize = 10.sp)
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MetricMiniBadge(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "$label: ", fontSize = 10.sp, color = NexTextMuted)
            Text(text = value, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}
