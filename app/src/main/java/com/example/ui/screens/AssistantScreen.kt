package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.NexApplication
import com.example.core.TaskState
import com.example.navigation.NexNavDestination
import com.example.ui.components.NexBadge
import com.example.ui.components.NexCard
import com.example.ui.components.NexOrb
import com.example.ui.components.TaskStateCard
import com.example.ui.theme.NexAmber
import com.example.ui.theme.NexBorder
import com.example.ui.theme.NexBorderLight
import com.example.ui.theme.NexCyan
import com.example.ui.theme.NexCyanLight
import com.example.ui.theme.NexEmerald
import com.example.ui.theme.NexIndigo
import com.example.ui.theme.NexIndigoLight
import com.example.ui.theme.NexObsidian
import com.example.ui.theme.NexRose
import com.example.ui.theme.NexSurfaceElevated
import com.example.ui.theme.NexTextMuted
import com.example.ui.theme.NexTextPrimary
import com.example.ui.theme.NexTextSecondary
import com.example.voice.TranscriptSender
import com.example.voice.VoiceLanguage
import com.example.voice.VoiceState
import com.example.voice.VoiceTranscriptItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AssistantScreen(
    navController: NavController,
    app: NexApplication = NexApplication.instance
) {
    val voiceController = app.voiceController
    val taskState by app.taskStateManager.executionState.collectAsStateWithLifecycle()
    val voiceState by voiceController.voiceState.collectAsStateWithLifecycle()
    val transcripts by voiceController.transcripts.collectAsStateWithLifecycle()
    val voiceSettings by voiceController.settings.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    var inputPrompt by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val micLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        voiceController.permissionManager.checkPermission()
        app.permissionManager.refreshPermissions()
        if (granted) {
            voiceController.startListening()
        }
    }

    LaunchedEffect(transcripts.size) {
        if (transcripts.isNotEmpty()) {
            listState.animateScrollToItem(transcripts.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NexObsidian)
            .padding(horizontal = 20.dp)
            .testTag("assistant_screen")
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Top Navigation & Engine Selector Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text(
                    text = "ASSISTANT CONSOLE",
                    color = NexTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Bilingual Voice Engine & Task Automation",
                    color = NexTextMuted,
                    fontSize = 11.sp
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Language quick toggle
                Surface(
                    color = NexSurfaceElevated,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NexBorder),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { navController.navigate(NexNavDestination.VoiceSettings.route) }
                ) {
                    Text(
                        text = when (voiceSettings.language) {
                            VoiceLanguage.ENGLISH_US -> "EN"
                            VoiceLanguage.BENGALI_BD -> "বাংলা (BD)"
                            VoiceLanguage.BENGALI_IN -> "বাংলা (IN)"
                        },
                        color = NexCyanLight,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }

                IconButton(
                    onClick = { navController.navigate(NexNavDestination.VoiceSettings.route) },
                    modifier = Modifier.size(36.dp).testTag("assistant_voice_settings_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Voice Settings",
                        tint = NexTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Permission Required Banner
        if (voiceState is VoiceState.PermissionRequired) {
            val permState = voiceState as VoiceState.PermissionRequired
            NexCard(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                backgroundColor = NexSurfaceElevated,
                borderColor = NexRose.copy(alpha = 0.5f)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MicOff, contentDescription = null, tint = NexRose, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Microphone Permission Required", color = NexTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(permState.explanation, color = NexTextMuted, fontSize = 11.sp, lineHeight = 16.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                            colors = ButtonDefaults.buttonColors(containerColor = NexIndigo),
                            modifier = Modifier.height(34.dp).testTag("grant_mic_permission_btn")
                        ) {
                            Text("Grant Permission", fontSize = 11.sp)
                        }
                        Button(
                            onClick = { voiceController.permissionManager.openAppSettings() },
                            colors = ButtonDefaults.buttonColors(containerColor = NexSurfaceElevated),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("App Settings", color = NexTextPrimary, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Voice Unavailable Banner
        if (voiceState is VoiceState.Unavailable) {
            val unavail = voiceState as VoiceState.Unavailable
            NexCard(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                backgroundColor = NexSurfaceElevated,
                borderColor = NexAmber.copy(alpha = 0.5f)
            ) {
                Text(
                    text = unavail.reason,
                    color = NexAmber,
                    fontSize = 12.sp
                )
            }
        }

        // Active Task Card if not idle
        if (taskState.state != TaskState.IDLE) {
            TaskStateCard(
                state = taskState,
                onConfirm = { 
                    coroutineScope.launch {
                        app.commandProcessor.confirmPendingAction(context as? androidx.fragment.app.FragmentActivity)
                    }
                },
                onCancel = { app.commandProcessor.cancelPendingAction() },
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        // Main Interaction Section: Voice Orb & Transcript History
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (transcripts.isEmpty() && voiceState !is VoiceState.Listening && voiceState !is VoiceState.Processing && voiceState !is VoiceState.Speaking) {
                // Empty state for new user: No fake messages automatically inserted!
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp)
                ) {
                    NexOrb(
                        taskState = taskState.state,
                        rmsDb = 0f,
                        size = 110.dp,
                        onClick = {
                            if (!voiceController.permissionManager.checkPermission()) {
                                micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else {
                                voiceController.startListening()
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Ready to Listen",
                        color = NexTextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Tap the orb or the microphone below to speak naturally in English or Bengali.",
                        color = NexTextMuted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Central mini visualizer when active
                    item {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            val rms = when (val vs = voiceState) {
                                is VoiceState.Listening -> vs.rmsDb
                                else -> 0f
                            }

                            NexOrb(
                                taskState = taskState.state,
                                rmsDb = rms,
                                size = 80.dp,
                                onClick = {
                                    if (voiceState is VoiceState.Listening) {
                                        voiceController.stopListening()
                                    } else if (voiceState is VoiceState.Speaking) {
                                        voiceController.stopSpeech()
                                    } else {
                                        if (!voiceController.permissionManager.checkPermission()) {
                                            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        } else {
                                            voiceController.startListening()
                                        }
                                    }
                                }
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Live status label
                            val statusLabel = when (val vs = voiceState) {
                                is VoiceState.Listening -> {
                                    if (vs.partialText.isNotBlank()) "\"${vs.partialText}\"" else "Listening... (${voiceSettings.language.displayName})"
                                }
                                is VoiceState.Processing -> "Perceiving & synthesizing command..."
                                is VoiceState.Speaking -> "NEX is speaking..."
                                is VoiceState.Error -> "Error: ${vs.message}"
                                is VoiceState.PermissionRequired -> "Microphone permission required"
                                is VoiceState.Unavailable -> "Speech recognition service unavailable"
                                else -> "Ready"
                            }

                            Text(
                                text = statusLabel,
                                color = when (voiceState) {
                                    is VoiceState.Listening -> NexCyanLight
                                    is VoiceState.Processing -> NexIndigoLight
                                    is VoiceState.Speaking -> NexEmerald
                                    is VoiceState.Error -> NexRose
                                    else -> NexTextMuted
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }

                    // Transcript list: Actual recognized speech and actual responses
                    items(transcripts) { transcriptItem ->
                        TranscriptBubble(transcript = transcriptItem)
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }
        }

        // Bottom Input Console Bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            // Text command input field
            OutlinedTextField(
                value = inputPrompt,
                onValueChange = { inputPrompt = it },
                placeholder = {
                    Text(
                        text = when (voiceSettings.language) {
                            VoiceLanguage.ENGLISH_US -> "Command NEX..."
                            VoiceLanguage.BENGALI_BD, VoiceLanguage.BENGALI_IN -> "নেক্স কে নির্দেশ দিন..."
                        },
                        color = NexTextMuted,
                        fontSize = 14.sp
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = NexSurfaceElevated,
                    unfocusedContainerColor = NexSurfaceElevated,
                    focusedBorderColor = NexIndigoLight,
                    unfocusedBorderColor = NexBorder,
                    focusedTextColor = NexTextPrimary,
                    unfocusedTextColor = NexTextPrimary
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("command_input_field")
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Primary Microphone Action Button
            IconButton(
                onClick = {
                    if (voiceState is VoiceState.Listening) {
                        voiceController.stopListening()
                    } else if (voiceState is VoiceState.Speaking) {
                        voiceController.stopSpeech()
                    } else {
                        if (!voiceController.permissionManager.checkPermission()) {
                            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            voiceController.startListening()
                        }
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = when (voiceState) {
                            is VoiceState.Listening -> NexCyanLight
                            is VoiceState.Speaking -> NexEmerald
                            else -> NexIndigo
                        },
                        shape = CircleShape
                    )
                    .testTag("assistant_mic_btn")
            ) {
                Icon(
                    imageVector = when (voiceState) {
                        is VoiceState.Listening -> Icons.Default.Stop
                        is VoiceState.Speaking -> Icons.Default.Stop
                        else -> Icons.Default.Mic
                    },
                    contentDescription = "Voice Input",
                    tint = if (voiceState is VoiceState.Listening) NexObsidian else Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Send Button if text entered
            if (inputPrompt.isNotBlank()) {
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(
                    onClick = {
                        val text = inputPrompt.trim()
                        inputPrompt = ""
                        app.commandProcessor.processCommand(text)
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .background(NexIndigoLight.copy(alpha = 0.3f), CircleShape)
                        .testTag("send_command_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = NexCyanLight,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TranscriptBubble(transcript: VoiceTranscriptItem) {
    val isUser = transcript.sender == TranscriptSender.USER
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val bg = if (isUser) NexIndigo.copy(alpha = 0.35f) else NexSurfaceElevated
    val border = if (isUser) NexIndigoLight.copy(alpha = 0.4f) else NexBorder
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Column(
        horizontalAlignment = alignment,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(
                text = if (isUser) "YOU (VOICE / TEXT)" else "NEX",
                color = if (isUser) NexIndigoLight else NexCyanLight,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = timeFormat.format(Date(transcript.timestamp)),
                color = NexTextMuted,
                fontSize = 9.sp
            )
        }

        Surface(
            color = bg,
            shape = RoundedCornerShape(
                topStart = 14.dp,
                topEnd = 14.dp,
                bottomStart = if (isUser) 14.dp else 2.dp,
                bottomEnd = if (isUser) 2.dp else 14.dp
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, border),
            modifier = Modifier
                .widthIn(max = 300.dp)
                .testTag(if (isUser) "user_transcript_bubble" else "nex_transcript_bubble")
        ) {
            Text(
                text = transcript.text,
                color = NexTextPrimary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}
