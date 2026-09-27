package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.NexApplication
import com.example.security.voice.EnrollmentStepState
import com.example.security.voice.OwnerAuthenticationManager
import com.example.ui.theme.NexAmber
import com.example.ui.theme.NexBorder
import com.example.ui.theme.NexCyanLight
import com.example.ui.theme.NexEmerald
import com.example.ui.theme.NexIndigoLight
import com.example.ui.theme.NexObsidian
import com.example.ui.theme.NexRose
import com.example.ui.theme.NexSurface
import com.example.ui.theme.NexSurfaceElevated
import com.example.ui.theme.NexTextMuted
import com.example.ui.theme.NexTextPrimary
import com.example.ui.theme.NexTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceEnrollmentScreen(
    navController: NavController,
    app: NexApplication = NexApplication.instance
) {
    val ownerAuthManager = app.securityManager.ownerAuthManager
    val authState by ownerAuthManager.authState.collectAsStateWithLifecycle()
    val permissionState by app.permissionManager.permissionState.collectAsStateWithLifecycle()

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val micLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        app.permissionManager.refreshPermissions()
        if (granted) {
            ownerAuthManager.startSampleRecording()
        }
    }

    LaunchedEffect(Unit) {
        ownerAuthManager.refreshState()
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(NexObsidian),
        containerColor = NexObsidian,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Owner Voice Enrollment",
                            color = NexTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Acoustic Speaker Profile & Keystore Encryption",
                            color = NexCyanLight,
                            fontSize = 12.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.testTag("voice_enrollment_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = NexTextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NexSurface)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Privacy & Architecture Banner
            PrivacyConsentBanner()

            when (val step = authState.currentStep) {
                is EnrollmentStepState.ConsentRequired -> {
                    ConsentRequiredCard(
                        isAlreadyEnrolled = authState.isEnrolled,
                        onConsentGiven = {
                            ownerAuthManager.startEnrollment(consentGiven = true)
                        }
                    )
                }

                is EnrollmentStepState.ReadyToRecord -> {
                    SamplePromptCard(
                        sampleIndex = step.sampleIndex,
                        totalSamples = step.totalSamples,
                        challengePhrase = step.challengePhrase,
                        isRecording = false,
                        audioLevel = 0f,
                        onRecordClick = {
                            if (!permissionState.isMicrophoneGranted) {
                                micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else {
                                ownerAuthManager.startSampleRecording()
                            }
                        },
                        onStopClick = {}
                    )
                }

                is EnrollmentStepState.Recording -> {
                    SamplePromptCard(
                        sampleIndex = step.sampleIndex,
                        totalSamples = step.totalSamples,
                        challengePhrase = step.challengePhrase,
                        isRecording = true,
                        audioLevel = step.audioLevelRms,
                        onRecordClick = {},
                        onStopClick = {
                            ownerAuthManager.stopSampleRecordingAndProcess()
                        }
                    )
                }

                is EnrollmentStepState.SampleProcessing -> {
                    ProcessingSampleCard(sampleIndex = step.sampleIndex, totalSamples = step.totalSamples)
                }

                is EnrollmentStepState.SampleAccepted -> {
                    SampleAcceptedCard(
                        sampleIndex = step.sampleIndex,
                        totalSamples = step.totalSamples,
                        snrScore = step.qualityScore,
                        onNextClick = {
                            if (!permissionState.isMicrophoneGranted) {
                                micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else {
                                ownerAuthManager.startSampleRecording()
                            }
                        }
                    )
                }

                is EnrollmentStepState.SampleRejected -> {
                    SampleRejectedCard(
                        sampleIndex = step.sampleIndex,
                        totalSamples = step.totalSamples,
                        reason = step.reason,
                        onRetryClick = {
                            if (!permissionState.isMicrophoneGranted) {
                                micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else {
                                ownerAuthManager.startSampleRecording()
                            }
                        }
                    )
                }

                is EnrollmentStepState.Completed -> {
                    EnrollmentCompletedCard(
                        totalSamples = step.totalSamples,
                        avgQuality = step.averageQuality,
                        timestamp = step.timestamp,
                        onReEnrollClick = {
                            ownerAuthManager.startEnrollment(consentGiven = true)
                        },
                        onDeleteClick = {
                            showDeleteConfirmDialog = true
                        }
                    )
                }

                is EnrollmentStepState.Error -> {
                    ErrorCard(
                        errorMessage = step.message,
                        onDismiss = {
                            ownerAuthManager.startEnrollment(consentGiven = true)
                        }
                    )
                }
            }
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            containerColor = NexSurfaceElevated,
            icon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = NexRose,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    "Delete Voice Enrollment?",
                    color = NexTextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "This will permanently delete your encrypted speaker embedding and cryptographic key from Android Keystore. You will need to re-enroll to use voice authentication.",
                    color = NexTextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        ownerAuthManager.deleteEnrollment()
                        app.securityManager.refreshState()
                        showDeleteConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NexRose),
                    modifier = Modifier.testTag("confirm_delete_voice_button")
                ) {
                    Text("Delete Permanently", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel", color = NexTextMuted)
                }
            }
        )
    }
}

@Composable
private fun PrivacyConsentBanner() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(NexSurface)
            .border(1.dp, NexBorder, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = NexCyanLight,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "LOCAL ON-DEVICE PRIVACY & SECURITY",
                color = NexCyanLight,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Voice enrollment computes an acoustic speaker embedding locally on your device. Embeddings are encrypted using hardware-backed Android Keystore keys and are never uploaded to Gemini or any external cloud.",
            color = NexTextSecondary,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Notice: Voice matching is a biometric convenience layer and cannot guarantee 100% identity protection. Sensitive actions (Level 2) strictly require Android BiometricPrompt or PIN authentication.",
            color = NexAmber,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )
    }
}

@Composable
private fun ConsentRequiredCard(
    isAlreadyEnrolled: Boolean,
    onConsentGiven: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NexSurfaceElevated)
            .border(1.dp, NexBorder, RoundedCornerShape(16.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = null,
            tint = NexCyanLight,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (isAlreadyEnrolled) "Voice Profile Enrolled" else "Enroll Your Voice",
            color = NexTextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "To enable speaker verification, you will record 4 spoken challenge phrases. NEX will generate your unique acoustic profile to recognize your voice during assistant sessions.",
            color = NexTextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onConsentGiven,
            colors = ButtonDefaults.buttonColors(containerColor = NexCyanLight),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("start_enrollment_consent_button")
        ) {
            Text(
                text = if (isAlreadyEnrolled) "Re-enroll Voice Profile" else "I Agree & Start Enrollment",
                color = NexObsidian,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun SamplePromptCard(
    sampleIndex: Int,
    totalSamples: Int,
    challengePhrase: String,
    isRecording: Boolean,
    audioLevel: Float,
    onRecordClick: () -> Unit,
    onStopClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NexSurfaceElevated)
            .border(1.dp, NexBorder, RoundedCornerShape(16.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SAMPLE $sampleIndex OF $totalSamples",
                color = NexCyanLight,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Text(
                text = if (isRecording) "RECORDING ACTIVE" else "READY",
                color = if (isRecording) NexRose else NexTextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { sampleIndex.toFloat() / totalSamples },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = NexCyanLight,
            trackColor = NexBorder,
        )

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Please speak the following phrase clearly:",
            color = NexTextMuted,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(NexSurface)
                .border(1.dp, NexBorder, RoundedCornerShape(10.dp))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "\"$challengePhrase\"",
                color = NexTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(30.dp))

        // Animated Mic Visualizer
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(
                    if (isRecording) NexRose.copy(alpha = 0.15f) else NexCyanLight.copy(alpha = 0.1f)
                )
                .scale(if (isRecording) (1.0f + audioLevel * 0.4f).coerceIn(1f, 1.4f) else 1f),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(if (isRecording) NexRose else NexCyanLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isRecording) Icons.Default.Mic else Icons.Default.Mic,
                    contentDescription = null,
                    tint = NexObsidian,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (isRecording) {
            Button(
                onClick = onStopClick,
                colors = ButtonDefaults.buttonColors(containerColor = NexRose),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("stop_sample_recording_button")
            ) {
                Icon(imageVector = Icons.Default.Stop, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Stop & Process Sample", color = Color.White, fontWeight = FontWeight.Bold)
            }
        } else {
            Button(
                onClick = onRecordClick,
                colors = ButtonDefaults.buttonColors(containerColor = NexCyanLight),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("start_sample_recording_button")
            ) {
                Icon(imageVector = Icons.Default.Mic, contentDescription = null, tint = NexObsidian)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Start Recording Sample $sampleIndex", color = NexObsidian, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ProcessingSampleCard(sampleIndex: Int, totalSamples: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NexSurfaceElevated)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = NexCyanLight, modifier = Modifier.size(44.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Analyzing Acoustic Features...",
            color = NexTextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Validating SNR, energy, and extracting 64-dim MFCC speaker representation",
            color = NexTextMuted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SampleAcceptedCard(
    sampleIndex: Int,
    totalSamples: Int,
    snrScore: Float,
    onNextClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NexSurfaceElevated)
            .border(1.dp, NexEmerald.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = NexEmerald,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Sample $sampleIndex Accepted",
            color = NexTextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Acoustic quality verified • SNR: ${snrScore.toInt()} dB",
            color = NexEmerald,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onNextClick,
            colors = ButtonDefaults.buttonColors(containerColor = NexCyanLight),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("continue_to_next_sample_button")
        ) {
            Text(
                text = "Continue to Sample ${sampleIndex + 1} of $totalSamples",
                color = NexObsidian,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SampleRejectedCard(
    sampleIndex: Int,
    totalSamples: Int,
    reason: String,
    onRetryClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NexSurfaceElevated)
            .border(1.dp, NexRose.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = NexRose,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Sample $sampleIndex Quality Check Failed",
            color = NexTextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = reason,
            color = NexRose,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onRetryClick,
            colors = ButtonDefaults.buttonColors(containerColor = NexCyanLight),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("retry_sample_button")
        ) {
            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = NexObsidian)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Retry Recording Sample $sampleIndex", color = NexObsidian, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EnrollmentCompletedCard(
    totalSamples: Int,
    avgQuality: Float,
    timestamp: Long,
    onReEnrollClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val dateStr = remember(timestamp) {
        if (timestamp > 0) SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(timestamp)) else "Active"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NexSurfaceElevated)
            .border(1.dp, NexEmerald.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = NexEmerald,
            modifier = Modifier.size(52.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Voice Profile Active & Enrolled",
            color = NexTextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Enrolled $totalSamples acoustic samples on $dateStr",
            color = NexTextMuted,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(NexSurface)
                .padding(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "• Model: NEX Acoustic Speaker Embedder v1.0",
                    color = NexTextSecondary,
                    fontSize = 12.sp
                )
                Text(
                    text = "• Encryption: AES-256 GCM (Android Keystore)",
                    color = NexTextSecondary,
                    fontSize = 12.sp
                )
                Text(
                    text = "• Avg Signal-to-Noise Ratio: ${avgQuality.toInt()} dB (Optimal)",
                    color = NexEmerald,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onReEnrollClick,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("re_enroll_voice_button")
            ) {
                Text("Re-enroll Voice", color = NexCyanLight, fontSize = 13.sp)
            }

            Button(
                onClick = onDeleteClick,
                colors = ButtonDefaults.buttonColors(containerColor = NexRose.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("delete_voice_profile_button")
            ) {
                Text("Delete Profile", color = NexRose, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ErrorCard(errorMessage: String, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NexSurfaceElevated)
            .border(1.dp, NexRose, RoundedCornerShape(16.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = NexRose, modifier = Modifier.size(36.dp))
        Spacer(modifier = Modifier.height(10.dp))
        Text(text = "Enrollment Error", color = NexTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = errorMessage, color = NexRose, fontSize = 12.sp, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onDismiss,
            colors = ButtonDefaults.buttonColors(containerColor = NexCyanLight)
        ) {
            Text("Restart Enrollment", color = NexObsidian, fontWeight = FontWeight.Bold)
        }
    }
}
