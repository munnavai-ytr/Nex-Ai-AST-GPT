package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.NexApplication
import com.example.navigation.NexNavDestination
import com.example.security.events.SecurityEventType
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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PrivacyCenterScreen(
    navController: NavController,
    app: NexApplication = NexApplication.instance
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val securityManager = app.securityManager
    val permissionManager = app.automationPermissionManager

    val securityState by securityManager.securityState.collectAsState()
    val permState by permissionManager.permissionState.collectAsState()
    val recentSecurityEvents by securityManager.eventLogger.recentEvents.collectAsState()

    var showActionMessage by remember { mutableStateOf<String?>(null) }
    var isConfirmingClearMemory by remember { mutableStateOf(false) }
    var isConfirmingDeleteVoice by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(NexObsidian)
            .padding(horizontal = 20.dp)
            .testTag("privacy_center_screen")
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))

            // Header
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
                        text = "PRIVACY & SECURITY CENTER",
                        color = NexTextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Owner Verification • Data Protection • Keystore Encryption",
                        color = NexTextMuted,
                        fontSize = 11.sp
                    )
                }
                IconButton(onClick = {
                    securityManager.refreshState()
                    permissionManager.refresh()
                }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = NexCyanLight
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Notification Banner if any action occurred
            AnimatedVisibility(visible = showActionMessage != null) {
                showActionMessage?.let { msg ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(NexSurfaceElevated)
                            .border(1.dp, NexCyanLight.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text(text = msg, color = NexCyanLight, fontSize = 12.sp)
                    }
                }
            }

            // 1. Core Privacy Matrix (Honest Device Signals)
            Text(
                text = "SYSTEM PRIVACY & AUTHENTICATION SIGNALS",
                color = NexTextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        item {
            PrivacySignalCard(
                icon = Icons.Default.Fingerprint,
                title = "Owner Voice Profile",
                status = if (securityState.isVoiceEnrolled) "Enrolled & Encrypted" else "Not Enrolled",
                details = if (securityState.isVoiceEnrolled) "Keystore AES-GCM protected • ${securityState.enrollmentMetadata?.sampleCount ?: 0} samples" else "Enroll voice to add speaker verification signal",
                isOk = securityState.isVoiceEnrolled,
                actionLabel = if (securityState.isVoiceEnrolled) "Manage" else "Enroll",
                onAction = { navController.navigate(NexNavDestination.VoiceEnrollment.route) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            PrivacySignalCard(
                icon = Icons.Default.Lock,
                title = "Android Biometrics & PIN",
                status = securityState.biometricAvailability.displayName,
                details = "Required for high-risk operations (messaging, payments, security modification)",
                isOk = securityState.isBiometricAvailable,
                actionLabel = "System",
                onAction = {
                    try {
                        context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
                    } catch (_: Exception) {}
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            PrivacySignalCard(
                icon = Icons.Default.Mic,
                title = "Microphone Access",
                status = if (permState.hasAudioPermission) "Granted (On-Demand)" else "Permission Denied",
                details = "Microphone is only accessed during active listening or voice enrollment",
                isOk = permState.hasAudioPermission,
                actionLabel = "Permissions",
                onAction = { navController.navigate(NexNavDestination.Permissions.route) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            PrivacySignalCard(
                icon = Icons.Default.Visibility,
                title = "Accessibility Inspection",
                status = if (permState.isAccessibilityConnected) "Active & Inspecting" else "Service Disabled",
                details = "Password & secure windows are strictly filtered; no raw credentials stored",
                isOk = permState.isAccessibilityConnected,
                actionLabel = "Settings",
                onAction = { permissionManager.openAccessibilitySettings() }
            )

            Spacer(modifier = Modifier.height(8.dp))

            PrivacySignalCard(
                icon = Icons.Default.CloudDone,
                title = "Gemini API Connection",
                status = if (app.geminiConfig.isApiKeyConfigured()) "Configured (${app.geminiConfig.getSelectedModel()})" else "API Key Not Set",
                details = "API key secured in private app preferences; memories sent only with user consent",
                isOk = app.geminiConfig.isApiKeyConfigured(),
                actionLabel = "Configure",
                onAction = { navController.navigate(NexNavDestination.Settings.route) }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 2. Policy Controls
            Text(
                text = "AUTHENTICATION & CONFIRMATION POLICIES",
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Require Voice Authentication",
                                color = NexTextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Verify speaker acoustics before executing commands",
                                color = NexTextMuted,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = securityState.isVoiceAuthEnabled,
                            onCheckedChange = { securityManager.setVoiceAuthEnabled(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = NexCyanLight, checkedTrackColor = NexIndigoLight)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Mandatory Sensitive Confirmations",
                                color = NexTextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Always prompt for explicit confirmation on high-risk actions",
                                color = NexTextMuted,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = securityState.requireConfirmationForSensitiveActions,
                            onCheckedChange = { securityManager.setRequireConfirmationForSensitiveActions(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = NexCyanLight, checkedTrackColor = NexIndigoLight)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 3. User Data & Deletion Controls
            Text(
                text = "PERSONAL DATA & RETENTION CONTROLS",
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
                    // Export Personal Data Button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Export Personal Data", color = NexTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(text = "Generate JSON bundle of approved memories and history", color = NexTextMuted, fontSize = 11.sp)
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    val count = app.memoryManager.getStructuredMemoriesCount()
                                    showActionMessage = "Export ready: $count approved memories bundled securely."
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NexCardBackground),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp), tint = NexCyanLight)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Export", fontSize = 12.sp, color = NexCyanLight)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Delete Voice Profile
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Delete Voice Profile", color = NexTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(text = "Permanently remove acoustic embeddings from Keystore", color = NexTextMuted, fontSize = 11.sp)
                        }
                        Button(
                            onClick = {
                                if (!isConfirmingDeleteVoice) {
                                    isConfirmingDeleteVoice = true
                                } else {
                                    securityManager.deleteVoiceProfile()
                                    isConfirmingDeleteVoice = false
                                    showActionMessage = "Enrolled voice profile wiped from encrypted Keystore."
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = if (isConfirmingDeleteVoice) NexRose else NexCardBackground),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = if (isConfirmingDeleteVoice) NexTextPrimary else NexRose)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isConfirmingDeleteVoice) "Confirm Delete" else "Delete", fontSize = 12.sp, color = if (isConfirmingDeleteVoice) NexTextPrimary else NexRose)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Clear Approved Memory
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Clear Approved Memories", color = NexTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(text = "Erase all saved facts, aliases, and preferences", color = NexTextMuted, fontSize = 11.sp)
                        }
                        Button(
                            onClick = {
                                if (!isConfirmingClearMemory) {
                                    isConfirmingClearMemory = true
                                } else {
                                    val activity = context as? androidx.fragment.app.FragmentActivity
                                    scope.launch {
                                        val authed = if (activity != null) {
                                            app.securityManager.authenticateOwnerForAction(
                                                activity = activity,
                                                actionTitle = "Erase Stored Personal Memories"
                                            )
                                        } else false

                                        if (authed) {
                                            app.memoryManager.clearAllMemories()
                                            isConfirmingClearMemory = false
                                            showActionMessage = "All stored memories permanently cleared."
                                        } else {
                                            isConfirmingClearMemory = false
                                            showActionMessage = "Authentication failed or cancelled. Memories were preserved."
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = if (isConfirmingClearMemory) NexRose else NexCardBackground),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(16.dp), tint = if (isConfirmingClearMemory) NexTextPrimary else NexRose)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isConfirmingClearMemory) "Confirm Clear" else "Clear", fontSize = 12.sp, color = if (isConfirmingClearMemory) NexTextPrimary else NexRose)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 4. Real Security Event Log
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "SECURITY EVENT HISTORY",
                    color = NexTextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                if (recentSecurityEvents.isNotEmpty()) {
                    Text(
                        text = "Clear Log",
                        color = NexRose,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { 
                            val activity = context as? androidx.fragment.app.FragmentActivity
                            if (activity != null) {
                                scope.launch {
                                    val authed = app.securityManager.authenticateOwnerForAction(
                                        activity = activity,
                                        actionTitle = "Clear Security Audit Log"
                                    )
                                    if (authed) {
                                        securityManager.eventLogger.clearHistory()
                                    }
                                }
                            } else {
                                securityManager.eventLogger.clearHistory()
                            }
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (recentSecurityEvents.isEmpty()) {
            item {
                Text(
                    text = "No security events logged in this session.",
                    color = NexTextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
        } else {
            items(recentSecurityEvents.take(15)) { event ->
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
                                text = event.eventType,
                                color = NexTextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            NexBadge(
                                text = event.outcome,
                                color = when (event.outcome) {
                                    "SUCCESS" -> NexEmerald
                                    "FAILED" -> NexError
                                    "CANCELLED" -> NexAmber
                                    else -> NexCyanLight
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = event.description, color = NexTextSecondary, fontSize = 11.sp)
                        val formattedTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(event.timestamp))
                        Text(text = formattedTime, color = NexTextMuted, fontSize = 10.sp)
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
private fun PrivacySignalCard(
    icon: ImageVector,
    title: String,
    status: String,
    details: String,
    isOk: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(NexCardBackground)
            .border(1.dp, if (isOk) NexBorderDark else NexWarning.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(if (isOk) NexEmerald.copy(alpha = 0.12f) else NexWarning.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isOk) NexEmerald else NexWarning,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, color = NexTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(text = status, color = if (isOk) NexEmerald else NexWarning, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(text = details, color = NexTextMuted, fontSize = 10.sp, lineHeight = 14.sp)
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = NexSurface),
                shape = RoundedCornerShape(6.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(text = actionLabel, fontSize = 11.sp, color = NexCyanLight, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
