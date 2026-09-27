package com.example.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.NexApplication
import com.example.navigation.NexNavDestination
import com.example.security.BiometricAuthResult
import com.example.security.BiometricAvailability
import com.example.ui.components.NexBadge
import com.example.ui.components.NexCard
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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SecurityScreen(
    navController: NavController,
    app: NexApplication = NexApplication.instance
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val securityState by app.securityManager.securityState.collectAsStateWithLifecycle()
    val wakeWordStatus by app.voiceController.wakeWordManager.status.collectAsStateWithLifecycle()

    var showWakeWordDialog by remember { mutableStateOf(false) }
    var wakePhraseInput by remember { mutableStateOf(wakeWordStatus.keyword) }
    var biometricTestResult by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        app.securityManager.refreshState()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(NexObsidian)
            .padding(horizontal = 20.dp)
            .testTag("security_screen")
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = NexTextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Text(
                        text = "OWNER SECURITY DASHBOARD",
                        color = NexTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Voice Authentication, Biometric Tiering & Keystore Security",
                        color = NexCyanLight,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 1: Voice Authentication & Speaker Profile
            Text(
                text = "OWNER VOICE AUTHENTICATION",
                color = NexTextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            NexCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                backgroundColor = NexSurfaceElevated,
                borderColor = if (securityState.isVoiceEnrolled) NexEmerald.copy(alpha = 0.5f) else NexBorder
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (securityState.isVoiceEnrolled) NexEmerald.copy(alpha = 0.15f) else NexCyanLight.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RecordVoiceOver,
                                    contentDescription = null,
                                    tint = if (securityState.isVoiceEnrolled) NexEmerald else NexCyanLight,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Voice Enrollment Status",
                                    color = NexTextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (securityState.isVoiceEnrolled) {
                                        val meta = securityState.enrollmentMetadata
                                        val date = if (meta != null && meta.enrollmentTimestamp > 0) {
                                            SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(meta.enrollmentTimestamp))
                                        } else "Active"
                                        "Enrolled on $date • ${meta?.sampleCount ?: 4} samples"
                                    } else {
                                        "Not Enrolled • Speaker verification inactive"
                                    },
                                    color = NexTextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        NexBadge(
                            text = if (securityState.isVoiceEnrolled) "ENROLLED" else "NOT ENROLLED",
                            color = if (securityState.isVoiceEnrolled) NexEmerald else NexAmber
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                val activity = context as? FragmentActivity
                                if (activity != null && securityState.isVoiceEnrolled) {
                                    coroutineScope.launch {
                                        val authed = app.securityManager.authenticateOwnerForAction(
                                            activity = activity,
                                            actionTitle = "Access Voice Enrollment"
                                        )
                                        if (authed) {
                                            navController.navigate(NexNavDestination.VoiceEnrollment.route)
                                        }
                                    }
                                } else {
                                    navController.navigate(NexNavDestination.VoiceEnrollment.route)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NexCyanLight),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("manage_voice_enrollment_button")
                        ) {
                            Text(
                                text = if (securityState.isVoiceEnrolled) "Manage / Re-enroll Voice" else "Enroll Owner Voice",
                                color = NexObsidian,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }

                        if (securityState.isVoiceEnrolled) {
                            Button(
                                onClick = {
                                    val activity = context as? FragmentActivity
                                    if (activity != null) {
                                        coroutineScope.launch {
                                            app.securityManager.deleteVoiceProfileProtected(activity)
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NexRose.copy(alpha = 0.2f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("delete_voice_profile_button")
                            ) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete Voice Profile", tint = NexRose, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Delete", color = NexRose, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Voice Auth Enforcement Toggle
            NexCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
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
                            text = "Enforce Voice Verification for Commands",
                            color = NexTextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Compares spoken voice embeddings against your enrolled acoustic profile before processing assistant commands.",
                            color = NexTextMuted,
                            fontSize = 11.sp
                        )
                    }

                    Switch(
                        checked = securityState.isVoiceAuthEnabled,
                        onCheckedChange = { targetVal ->
                            val activity = context as? FragmentActivity
                            if (activity != null) {
                                coroutineScope.launch {
                                    app.securityManager.setVoiceAuthEnabledProtected(activity, targetVal)
                                }
                            } else {
                                app.securityManager.setVoiceAuthEnabled(targetVal)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NexCyanLight,
                            checkedTrackColor = NexCyanLight.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.testTag("voice_auth_switch")
                    )
                }
            }

            // Section 2: Biometric & Device Credentials
            Text(
                text = "BIOMETRIC & DEVICE CREDENTIALS",
                color = NexTextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            NexCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                backgroundColor = NexSurfaceElevated,
                borderColor = NexBorder
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(NexIndigoLight.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Fingerprint,
                                    contentDescription = null,
                                    tint = NexIndigoLight,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Android BiometricPrompt",
                                    color = NexTextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = securityState.biometricAvailability.displayName,
                                    color = NexTextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        NexBadge(
                            text = if (securityState.isBiometricAvailable) "AVAILABLE" else "UNAVAILABLE",
                            color = if (securityState.isBiometricAvailable) NexEmerald else NexAmber
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            val activity = context as? FragmentActivity
                            if (activity != null) {
                                coroutineScope.launch {
                                    val res = app.securityManager.authenticateBiometric(
                                        activity = activity,
                                        title = "NEX Biometric Test",
                                        subtitle = "Testing AndroidX Biometric Integration",
                                        description = "Scan your fingerprint or enter device PIN to confirm security readiness."
                                    )
                                    biometricTestResult = when (res) {
                                        is BiometricAuthResult.Success -> "Biometric Verification Succeeded! Device credentials confirmed."
                                        is BiometricAuthResult.Cancelled -> "Biometric prompt was dismissed by user."
                                        is BiometricAuthResult.Error -> "Biometric error: ${res.errString} (${res.errorCode})"
                                        is BiometricAuthResult.NotAvailable -> "Biometric authentication is not configured on this device."
                                        is BiometricAuthResult.Failed -> "Biometric authentication failed."
                                    }
                                }
                            } else {
                                biometricTestResult = "Host Activity is not a FragmentActivity."
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NexIndigoLight.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("test_biometric_prompt_button")
                    ) {
                        Icon(imageVector = Icons.Default.Fingerprint, contentDescription = null, tint = NexIndigoLight, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Test Android Biometric Prompt", color = NexIndigoLight, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            // Section 3: Wake Word & Speech Engine Status
            Text(
                text = "WAKE WORD & HOTWORD ENGINE",
                color = NexTextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            NexCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
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
                            text = "Wake Phrase: \"${wakeWordStatus.keyword}\"",
                            color = NexTextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = wakeWordStatus.availability.displayName,
                            color = NexAmber,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = wakeWordStatus.explanation,
                            color = NexTextMuted,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            wakePhraseInput = wakeWordStatus.keyword
                            showWakeWordDialog = true
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("configure_wake_phrase_button")
                    ) {
                        Text("Edit", color = NexCyanLight, fontSize = 11.sp)
                    }
                }
            }

            // Section 4: Security Policy & Risk Classification
            Text(
                text = "SECURITY POLICY & RISK TIERS",
                color = NexTextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            NexCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                backgroundColor = NexSurfaceElevated,
                borderColor = NexBorder
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Authentication Level Tier Hierarchy",
                        color = NexCyanLight,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "• Level 0 (Open): Safe UI navigation, status inspection, search\n" +
                                "• Level 1 (Voice Verified): General app launch and standard voice automation\n" +
                                "• Level 2 (Device Credential): Financial actions, settings modifications, file deletion, and password entries strictly require Android BiometricPrompt or PIN.",
                        color = NexTextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }

            // Sensitive action requirement switch
            NexCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
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
                            text = "Mandatory Biometric for Level 2 Actions",
                            color = NexTextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Strictly enforces Android device biometrics before executing high-risk autonomous steps.",
                            color = NexTextMuted,
                            fontSize = 11.sp
                        )
                    }

                    Switch(
                        checked = securityState.requireConfirmationForSensitiveActions,
                        onCheckedChange = { targetVal ->
                            val activity = context as? FragmentActivity
                            if (activity != null) {
                                coroutineScope.launch {
                                    app.securityManager.setRequireConfirmationProtected(activity, targetVal)
                                }
                            } else {
                                app.securityManager.setRequireConfirmationForSensitiveActions(targetVal)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NexIndigoLight,
                            checkedTrackColor = NexIndigoLight.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.testTag("sensitive_confirm_switch")
                    )
                }
            }
        }
    }

    // Biometric Test Result Modal
    biometricTestResult?.let { msg ->
        AlertDialog(
            onDismissRequest = { biometricTestResult = null },
            containerColor = NexSurfaceElevated,
            title = {
                Text("Biometric Verification Result", color = NexTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Text(msg, color = NexTextSecondary, fontSize = 14.sp)
            },
            confirmButton = {
                Button(
                    onClick = { biometricTestResult = null },
                    colors = ButtonDefaults.buttonColors(containerColor = NexCyanLight)
                ) {
                    Text("OK", color = NexObsidian, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Configure Wake Word Dialog
    if (showWakeWordDialog) {
        AlertDialog(
            onDismissRequest = { showWakeWordDialog = false },
            containerColor = NexSurfaceElevated,
            title = {
                Text("Configure Wake Phrase", color = NexTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Set the spoken phrase that triggers NEX during active sessions:",
                        color = NexTextSecondary,
                        fontSize = 13.sp
                    )
                    OutlinedTextField(
                        value = wakePhraseInput,
                        onValueChange = { wakePhraseInput = it },
                        label = { Text("Wake Phrase") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NexCyanLight,
                            unfocusedBorderColor = NexBorder,
                            focusedTextColor = NexTextPrimary,
                            unfocusedTextColor = NexTextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("wake_phrase_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        app.voiceController.wakeWordManager.setWakePhrase(wakePhraseInput)
                        showWakeWordDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NexCyanLight),
                    modifier = Modifier.testTag("save_wake_phrase_button")
                ) {
                    Text("Save", color = NexObsidian, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWakeWordDialog = false }) {
                    Text("Cancel", color = NexTextMuted)
                }
            }
        )
    }
}
