package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.NexApplication
import com.example.ui.components.NexBadge
import com.example.ui.components.NexCard
import com.example.ui.theme.NexAmber
import com.example.ui.theme.NexBorder
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
import com.example.voice.LanguageAvailabilityStatus
import com.example.voice.VoiceLanguage
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSettingsScreen(
    navController: NavController,
    app: NexApplication = NexApplication.instance
) {
    val voiceController = app.voiceController
    val settings by voiceController.settings.collectAsStateWithLifecycle()
    val isMicGranted by voiceController.permissionManager.isMicrophoneGranted.collectAsStateWithLifecycle()
    val wakeWordStatus by voiceController.wakeWordManager.status.collectAsStateWithLifecycle()

    var languageDropdownExpanded by remember { mutableStateOf(false) }

    val micLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        voiceController.permissionManager.checkPermission()
        app.permissionManager.refreshPermissions()
    }

    val isRecognitionAvailable = voiceController.speechRecognizerManager.isRecognitionAvailable()
    val ttsLanguageStatus = voiceController.ttsManager.checkLanguageAvailability(settings.language)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(NexObsidian)
            .padding(horizontal = 20.dp)
            .testTag("voice_settings_screen")
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))

            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.testTag("voice_settings_back_btn")
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
                        text = "VOICE ENGINE",
                        color = NexTextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Android SpeechRecognizer & TTS Architecture",
                        color = NexTextMuted,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section: Language Configuration
            Text(
                text = "PRIMARY VOICE LANGUAGE",
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
                        text = "Active Recognition & TTS Locale",
                        color = NexTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Configures Android SpeechRecognizer language tag and Text-to-Speech locale.",
                        color = NexTextMuted,
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    ExposedDropdownMenuBox(
                        expanded = languageDropdownExpanded,
                        onExpandedChange = { languageDropdownExpanded = !languageDropdownExpanded }
                    ) {
                        OutlinedTextField(
                            value = settings.language.displayName,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = languageDropdownExpanded) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = NexTextPrimary,
                                unfocusedTextColor = NexTextPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .testTag("voice_language_dropdown")
                        )
                        ExposedDropdownMenu(
                            expanded = languageDropdownExpanded,
                            onDismissRequest = { languageDropdownExpanded = false }
                        ) {
                            VoiceLanguage.values().forEach { lang ->
                                DropdownMenuItem(
                                    text = { Text(lang.displayName) },
                                    onClick = {
                                        voiceController.setLanguage(lang)
                                        languageDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Engine support for ${settings.language.code}:",
                            color = NexTextSecondary,
                            fontSize = 11.sp
                        )
                        NexBadge(
                            text = when (ttsLanguageStatus) {
                                LanguageAvailabilityStatus.AVAILABLE -> "SUPPORTED"
                                LanguageAvailabilityStatus.MISSING_DATA -> "DATA MISSING (FALLBACK)"
                                LanguageAvailabilityStatus.NOT_SUPPORTED -> "NOT SUPPORTED"
                                LanguageAvailabilityStatus.ENGINE_NOT_READY -> "INITIALIZING"
                            },
                            color = when (ttsLanguageStatus) {
                                LanguageAvailabilityStatus.AVAILABLE -> NexEmerald
                                LanguageAvailabilityStatus.MISSING_DATA -> NexAmber
                                else -> NexRose
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section: Speech Synthesis (TTS) Tuning
            Text(
                text = "TEXT-TO-SPEECH SYNTHESIS",
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
                    // Speech Rate Slider
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = NexCyanLight,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Speech Rate",
                                color = NexTextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Text(
                            text = String.format(Locale.US, "%.2fx", settings.speechRate),
                            color = NexCyanLight,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Slider(
                        value = settings.speechRate,
                        onValueChange = { voiceController.setSpeechRate(it) },
                        valueRange = 0.5f..2.0f,
                        steps = 14,
                        colors = SliderDefaults.colors(
                            thumbColor = NexCyanLight,
                            activeTrackColor = NexCyanLight,
                            inactiveTrackColor = NexBorder
                        ),
                        modifier = Modifier.testTag("speech_rate_slider")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Pitch Slider
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = NexIndigoLight,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Voice Pitch",
                                color = NexTextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Text(
                            text = String.format(Locale.US, "%.2fx", settings.pitch),
                            color = NexIndigoLight,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Slider(
                        value = settings.pitch,
                        onValueChange = { voiceController.setPitch(it) },
                        valueRange = 0.5f..2.0f,
                        steps = 14,
                        colors = SliderDefaults.colors(
                            thumbColor = NexIndigoLight,
                            activeTrackColor = NexIndigoLight,
                            inactiveTrackColor = NexBorder
                        ),
                        modifier = Modifier.testTag("speech_pitch_slider")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Auto Speak Toggle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-speak Responses",
                                color = NexTextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Vocalize NEX responses automatically via Android Text-to-Speech.",
                                color = NexTextMuted,
                                fontSize = 11.sp
                            )
                        }

                        Switch(
                            checked = settings.autoSpeakResponses,
                            onCheckedChange = { voiceController.setAutoSpeakResponses(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = NexIndigoLight,
                                checkedTrackColor = NexIndigoLight.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.testTag("auto_speak_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Test Voice Button
                    Button(
                        onClick = {
                            val testPhrase = when (settings.language) {
                                VoiceLanguage.ENGLISH_US -> "NEX voice engine active. Speech synthesis is operational."
                                VoiceLanguage.BENGALI_BD, VoiceLanguage.BENGALI_IN -> "নেক্স ভয়েস ইঞ্জিন সক্রিয় আছে। আমি আপনার কথা শুনতে পাচ্ছি।"
                            }
                            voiceController.speak(testPhrase, force = true)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NexIndigo),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("test_voice_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Test Speech Synthesis Output", fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section: Real System Diagnostics & Permissions
            Text(
                text = "REAL SYSTEM HARDWARE & ENGINE STATUS",
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
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // 1. Microphone Permission
                    DiagnosticRow(
                        title = "Microphone Permission",
                        subtitle = "android.permission.RECORD_AUDIO",
                        statusText = if (isMicGranted) "GRANTED" else "NOT GRANTED",
                        statusColor = if (isMicGranted) NexEmerald else NexRose,
                        actionLabel = if (!isMicGranted) "Grant Access" else null,
                        onAction = {
                            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    )

                    // 2. SpeechRecognizer Availability
                    DiagnosticRow(
                        title = "Speech Recognition Engine",
                        subtitle = "android.speech.SpeechRecognizer",
                        statusText = if (isRecognitionAvailable) "AVAILABLE" else "UNAVAILABLE",
                        statusColor = if (isRecognitionAvailable) NexEmerald else NexRose,
                        actionLabel = if (!isRecognitionAvailable) "App Details" else null,
                        onAction = { voiceController.permissionManager.openAppSettings() }
                    )

                    // 3. Text-to-Speech Engine
                    DiagnosticRow(
                        title = "Text-to-Speech Engine",
                        subtitle = "android.speech.tts.TextToSpeech",
                        statusText = if (voiceController.ttsManager.isEngineReady()) "INITIALIZED" else "CONNECTING",
                        statusColor = if (voiceController.ttsManager.isEngineReady()) NexEmerald else NexAmber
                    )

                    // 4. Wake-Word Architecture
                    DiagnosticRow(
                        title = "Keyword Activation (Wake Word)",
                        subtitle = "\"Hey NEX\" architecture",
                        statusText = wakeWordStatus.availability.displayName,
                        statusColor = NexAmber
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Wake word limitation note
            NexCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = NexSurfaceElevated,
                borderColor = NexBorder
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = NexAmber,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = wakeWordStatus.explanation,
                        color = NexTextMuted,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DiagnosticRow(
    title: String,
    subtitle: String,
    statusText: String,
    statusColor: Color,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = NexTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                color = NexTextMuted,
                fontSize = 11.sp
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            NexBadge(
                text = statusText,
                color = statusColor
            )

            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onAction,
                    colors = ButtonDefaults.buttonColors(containerColor = NexIndigoLight),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text(actionLabel, fontSize = 11.sp)
                }
            }
        }
    }
}
