package com.example.ui.screens

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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.NexApplication
import com.example.core.TaskState
import com.example.navigation.NexNavDestination
import com.example.services.NexForegroundService
import com.example.ui.components.NexBadge
import com.example.ui.components.NexCard
import com.example.ui.components.NexOrb
import com.example.ui.components.TaskExecutionPanel
import com.example.ui.components.TaskStateCard
import com.example.ui.theme.NexAmber
import com.example.ui.theme.NexBorder
import com.example.ui.theme.NexBorderLight
import com.example.ui.theme.NexCyanLight
import com.example.ui.theme.NexEmerald
import com.example.ui.theme.NexIndigo
import com.example.ui.theme.NexIndigoLight
import com.example.ui.theme.NexObsidian
import com.example.ui.theme.NexRose
import com.example.ui.theme.NexSurfaceCard
import com.example.ui.theme.NexSurfaceElevated
import com.example.ui.theme.NexTextMuted
import com.example.ui.theme.NexTextPrimary
import com.example.ui.theme.NexTextSecondary
import com.example.voice.VoiceState

@Composable
fun HomeScreen(
    navController: NavController,
    app: NexApplication = NexApplication.instance
) {
    val taskExecutionState by app.taskStateManager.executionState.collectAsStateWithLifecycle()
    val queueProgress by app.actionQueue.progress.collectAsStateWithLifecycle()
    val agentContext by app.agentOrchestrator.taskContext.collectAsStateWithLifecycle()
    val permissionState by app.permissionManager.permissionState.collectAsStateWithLifecycle()
    val voiceState by app.voiceController.voiceState.collectAsStateWithLifecycle()
    val recentHistory by app.memoryRepository.recentHistory.collectAsStateWithLifecycle(initialValue = emptyList())
    val isFgsRunning by NexForegroundService.isRunning.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    val micLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        app.voiceController.permissionManager.checkPermission()
        app.permissionManager.refreshPermissions()
        if (granted) {
            app.voiceController.startListening()
        }
    }

    val rmsDb = when (val vs = voiceState) {
        is VoiceState.Listening -> vs.rmsDb
        else -> 0f
    }

    val isGeminiConfigured = app.geminiConfig.isApiKeyConfigured()
    val isAccessibilityActive = app.accessibilityController.isAccessibilityActive()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(NexObsidian)
            .padding(horizontal = 20.dp)
            .testTag("home_screen")
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))

            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = "NEX",
                        color = NexTextPrimary,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "PERSONAL AI OPERATING LAYER",
                        color = NexIndigoLight,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                }

                NexBadge(
                    text = if (isAccessibilityActive) "AUTONOMY ACTIVE" else "BASIC MODE",
                    color = if (isAccessibilityActive) NexEmerald else NexAmber
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Central Interaction Orb
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    NexOrb(
                        taskState = taskExecutionState.state,
                        rmsDb = rmsDb,
                        size = 140.dp,
                        onClick = {
                            if (voiceState is VoiceState.Listening) {
                                app.voiceController.stopListening()
                            } else if (voiceState is VoiceState.Speaking) {
                                app.voiceController.stopSpeech()
                            } else {
                                if (!app.voiceController.permissionManager.checkPermission()) {
                                    micLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                } else {
                                    app.voiceController.startListening()
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = when (val vs = voiceState) {
                            is VoiceState.Listening -> if (vs.partialText.isNotBlank()) "\"${vs.partialText}\"" else "Listening... Speak naturally"
                            is VoiceState.Processing -> "Perceiving & synthesizing speech..."
                            is VoiceState.Speaking -> vs.text.ifBlank { "NEX is speaking..." }
                            is VoiceState.Error -> "Error: ${vs.message}"
                            is VoiceState.PermissionRequired -> "Microphone permission required. Tap to grant."
                            is VoiceState.Unavailable -> "Speech recognizer unavailable on device"
                            else -> when (taskExecutionState.state) {
                                TaskState.IDLE -> "Tap orb to speak to NEX"
                                TaskState.PROCESSING, TaskState.PLANNING -> "Synthesizing action plan..."
                                TaskState.EXECUTING -> "Executing device task..."
                                TaskState.WAITING_FOR_USER -> "Awaiting user confirmation"
                                TaskState.COMPLETED -> "Task finished successfully"
                                TaskState.FAILED -> "Task could not be completed"
                                else -> "Ready for command"
                            }
                        },
                        color = if (voiceState is VoiceState.Listening) NexCyanLight else NexTextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Active Task Panel if not idle
            if (taskExecutionState.state != TaskState.IDLE || (agentContext != null && !agentContext!!.isTerminal)) {
                TaskExecutionPanel(
                    state = taskExecutionState,
                    progress = queueProgress,
                    agentContext = agentContext,
                    onConfirm = { 
                        coroutineScope.launch {
                            app.commandProcessor.confirmPendingAction(context as? androidx.fragment.app.FragmentActivity)
                        }
                    },
                    onDeny = { app.commandProcessor.cancelPendingAction() },
                    onCancelTask = { app.commandProcessor.cancelPendingAction() },
                    onDismiss = {
                        app.agentOrchestrator.cancelTask("Dismissed", informUser = false)
                        app.taskStateManager.resetToIdle()
                    },
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            // Real System Diagnostic Badges
            Text(
                text = "SYSTEM STATUS",
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
                StatusPill(
                    label = "MIC",
                    active = permissionState.isMicrophoneGranted,
                    activeColor = NexEmerald,
                    inactiveColor = NexRose,
                    onClick = { navController.navigate(NexNavDestination.Permissions.route) },
                    modifier = Modifier.weight(1f)
                )
                StatusPill(
                    label = "ACCESSIBILITY",
                    active = isAccessibilityActive,
                    activeColor = NexEmerald,
                    inactiveColor = NexAmber,
                    onClick = { navController.navigate(NexNavDestination.AccessibilityControl.route) },
                    modifier = Modifier.weight(1.3f)
                )
                StatusPill(
                    label = "GEMINI",
                    active = isGeminiConfigured,
                    activeColor = NexCyanLight,
                    inactiveColor = NexTextMuted,
                    onClick = { navController.navigate(NexNavDestination.Settings.route) },
                    modifier = Modifier.weight(1.1f)
                )
                StatusPill(
                    label = "SERVICE",
                    active = isFgsRunning,
                    activeColor = NexEmerald,
                    inactiveColor = NexTextMuted,
                    onClick = {
                        if (isFgsRunning) {
                            NexForegroundService.stopService(app)
                        } else {
                            NexForegroundService.startService(app)
                        }
                    },
                    modifier = Modifier.weight(1.1f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Quick Architecture Hub Cards
            Text(
                text = "SYSTEM MODULES",
                color = NexTextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                ModuleCard(
                    title = "Apps & Control",
                    subtitle = "App Layer",
                    icon = Icons.Default.Apps,
                    color = NexCyanLight,
                    modifier = Modifier.weight(1f),
                    onClick = { navController.navigate(NexNavDestination.AppsControl.route) }
                )
                ModuleCard(
                    title = "Automation",
                    subtitle = "Action Engine",
                    icon = Icons.Default.AutoAwesome,
                    color = NexIndigoLight,
                    modifier = Modifier.weight(1f),
                    onClick = { navController.navigate(NexNavDestination.Automation.route) }
                )
                ModuleCard(
                    title = "Security",
                    subtitle = "Biometrics & PIN",
                    icon = Icons.Default.Security,
                    color = NexEmerald,
                    modifier = Modifier.weight(1f),
                    onClick = { navController.navigate(NexNavDestination.Security.route) }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            NexCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = NexSurfaceElevated,
                borderColor = NexBorderLight,
                onClick = { navController.navigate(NexNavDestination.DeepTesting.route) }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = null,
                            tint = NexCyanLight,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Deep Testing & Release Diagnostics",
                                color = NexTextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Validate all 11 subsystems, hardware health & run CUJs",
                                color = NexTextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = NexTextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Recent Real Task History
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "TASK HISTORY",
                    color = NexTextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "${recentHistory.size} recorded",
                    color = NexTextMuted,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        if (recentHistory.isEmpty()) {
            item {
                NexCard(
                    backgroundColor = NexSurfaceElevated,
                    borderColor = NexBorder,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        Text(
                            text = "No tasks executed yet",
                            color = NexTextSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Speak a command or use the Assistant tab to plan and run real tasks.",
                            color = NexTextMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        } else {
            items(recentHistory.take(5)) { item ->
                HistoryRow(
                    prompt = item.prompt,
                    status = item.status,
                    summary = item.summary,
                    durationMs = item.executionTimeMs,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatusPill(
    label: String,
    active: Boolean,
    activeColor: Color,
    inactiveColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = if (active) activeColor else inactiveColor
    Surface(
        color = NexSurfaceElevated,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.3f)),
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(color, CircleShape)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                color = color,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
private fun ModuleCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    NexCard(
        modifier = modifier,
        backgroundColor = NexSurfaceElevated,
        borderColor = NexBorder,
        onClick = onClick
    ) {
        Column {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                color = NexTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                color = NexTextMuted,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun HistoryRow(
    prompt: String,
    status: String,
    summary: String,
    durationMs: Long,
    modifier: Modifier = Modifier
) {
    NexCard(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = NexSurfaceElevated,
        borderColor = NexBorder
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "\"$prompt\"",
                    color = NexTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = summary,
                    color = NexTextMuted,
                    fontSize = 11.sp
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                NexBadge(
                    text = status,
                    color = if (status == "COMPLETED") NexEmerald else NexRose
                )
                if (durationMs > 0) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${durationMs}ms",
                        color = NexTextMuted,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}
