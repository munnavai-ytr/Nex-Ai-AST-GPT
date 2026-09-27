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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.example.NexApplication
import com.example.accessibility.model.AccessibilityServiceState
import com.example.accessibility.model.ScreenSnapshotResult
import com.example.navigation.NexNavDestination
import com.example.ui.theme.NexBorderDark
import com.example.ui.theme.NexCardBackground
import com.example.ui.theme.NexCyanLight
import com.example.ui.theme.NexEmerald
import com.example.ui.theme.NexError
import com.example.ui.theme.NexIndigoLight
import com.example.ui.theme.NexObsidian
import com.example.ui.theme.NexSurface
import com.example.ui.theme.NexTextMuted
import com.example.ui.theme.NexTextPrimary
import com.example.ui.theme.NexTextSecondary
import com.example.ui.theme.NexWarning
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AccessibilityControlCenterScreen(navController: NavController) {
    val app = NexApplication.instance
    val statusManager = app.accessibilityStatusManager
    val screenProvider = app.currentScreenProvider

    val isEnabled by statusManager.isAccessibilityEnabled.collectAsState()
    val serviceState by statusManager.serviceConnectionState.collectAsState()
    val currentApp by statusManager.currentForegroundApp.collectAsState()
    val lastUpdateTime by statusManager.lastScreenUpdateTime.collectAsState()
    val elementCount by statusManager.lastObservedElementCount.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                statusManager.refreshState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val coroutineScope = rememberCoroutineScope()
    var isCapturing by remember { mutableStateOf(false) }
    var captureMessage by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(NexObsidian)
            .padding(horizontal = 20.dp)
            .testTag("accessibility_control_center_screen")
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))

            // Header Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.testTag("accessibility_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = NexTextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Accessibility Center",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = NexTextPrimary
                    )
                    Text(
                        text = "Real Android screen understanding & automation",
                        fontSize = 12.sp,
                        color = NexTextMuted
                    )
                }
                IconButton(
                    onClick = { statusManager.refreshState() },
                    modifier = Modifier.testTag("accessibility_refresh_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Status",
                        tint = NexCyanLight
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        // 1. Primary Accessibility Status Card
        item {
            val statusColor = when (serviceState) {
                AccessibilityServiceState.CONNECTED -> NexEmerald
                AccessibilityServiceState.DISCONNECTED -> NexWarning
                AccessibilityServiceState.DISABLED -> NexError
            }

            val statusText = when (serviceState) {
                AccessibilityServiceState.CONNECTED -> "Enabled & Connected"
                AccessibilityServiceState.DISCONNECTED -> "Enabled (Service Binding)"
                AccessibilityServiceState.DISABLED -> "Disabled in System Settings"
            }

            val isConnected = serviceState == AccessibilityServiceState.CONNECTED

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(NexSurface)
                    .border(1.dp, NexBorderDark, RoundedCornerShape(16.dp))
                    .padding(20.dp)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Accessibility Service",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = NexTextPrimary
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(statusColor, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isEnabled) "● Enabled" else "○ Disabled",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = statusColor,
                                modifier = Modifier.testTag("accessibility_service_status_label")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = statusText,
                        fontSize = 12.sp,
                        color = NexTextSecondary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (!isConnected) {
                        Button(
                            onClick = { statusManager.openAccessibilitySettings() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NexIndigoLight.copy(alpha = 0.85f),
                                contentColor = NexTextPrimary
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("open_accessibility_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Open Android Accessibility Settings",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Enable 'NEX Accessibility Service' to grant autonomous perception and interaction capabilities.",
                            fontSize = 11.sp,
                            color = NexTextMuted,
                            lineHeight = 15.sp
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = NexEmerald,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Service active. Screen events and nodes are observable.",
                                fontSize = 12.sp,
                                color = NexEmerald
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // 2. Metrics & Live State Cards
        item {
            Text(
                text = "SYSTEM OBSERVATIONS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = NexTextMuted,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Current Foreground App Card
            ObservationMetricCard(
                icon = Icons.Default.PhoneAndroid,
                title = "Current Application",
                value = currentApp?.displayName ?: currentApp?.packageName ?: "No foreground app detected",
                secondaryValue = currentApp?.packageName?.takeIf { it != currentApp?.displayName },
                badge = if (currentApp != null) "Foreground" else "Idle",
                badgeColor = if (currentApp != null) NexCyanLight else NexTextMuted
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Screen Access & UI Elements Card
            val isScreenAvailable = serviceState == AccessibilityServiceState.CONNECTED
            ObservationMetricCard(
                icon = Icons.Default.Layers,
                title = "Screen Perception",
                value = if (isScreenAvailable) "Available" else "Unavailable",
                secondaryValue = if (elementCount > 0) "$elementCount UI elements registered" else "0 elements (refresh to inspect)",
                badge = if (isScreenAvailable) "$elementCount Nodes" else "Offline",
                badgeColor = if (isScreenAvailable) NexEmerald else NexWarning
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Last Observation Timestamp Card
            val formattedTime = if (lastUpdateTime > 0L) {
                SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(lastUpdateTime))
            } else {
                "No observation recorded"
            }

            ObservationMetricCard(
                icon = Icons.Default.Visibility,
                title = "Last Screen Observation",
                value = formattedTime,
                secondaryValue = if (lastUpdateTime > 0L) "Updated via OS AccessibilityEvent" else "Awaiting window change",
                badge = if (lastUpdateTime > 0L) "Live" else "None",
                badgeColor = if (lastUpdateTime > 0L) NexCyanLight else NexTextMuted
            )

            Spacer(modifier = Modifier.height(20.dp))
        }

        // 3. Actions & Tools
        item {
            Text(
                text = "DIAGNOSTICS & CONTROLS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = NexTextMuted,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Capture Live Screen Button
            Button(
                onClick = {
                    coroutineScope.launch {
                        isCapturing = true
                        captureMessage = null
                        val result = screenProvider.getCurrentScreenSnapshot(filterSensitive = true)
                        isCapturing = false
                        captureMessage = when (result) {
                            is ScreenSnapshotResult.Success ->
                                "Captured ${result.snapshot.elementCount} elements (${result.snapshot.displayName ?: result.snapshot.packageName})"
                            is ScreenSnapshotResult.Error ->
                                "Failed: ${result.message}"
                        }
                    }
                },
                enabled = !isCapturing && serviceState == AccessibilityServiceState.CONNECTED,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NexCardBackground,
                    contentColor = NexTextPrimary,
                    disabledContainerColor = NexSurface.copy(alpha = 0.5f),
                    disabledContentColor = NexTextMuted
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, NexBorderDark, RoundedCornerShape(12.dp))
                    .testTag("capture_screen_snapshot_button")
            ) {
                if (isCapturing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = NexCyanLight,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Reading Active Window Tree...", fontSize = 13.sp)
                } else {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = NexCyanLight,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Inspect Active Screen Hierarchy", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            AnimatedVisibility(visible = captureMessage != null) {
                captureMessage?.let { msg ->
                    Text(
                        text = msg,
                        fontSize = 12.sp,
                        color = if (msg.startsWith("Captured")) NexEmerald else NexError,
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Open Screen Debugger Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(NexCardBackground)
                    .border(1.dp, NexBorderDark, RoundedCornerShape(12.dp))
                    .clickable {
                        navController.navigate(NexNavDestination.AccessibilityDebug.route)
                    }
                    .padding(16.dp)
                    .testTag("open_screen_debugger_card")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(NexIndigoLight.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = null,
                            tint = NexIndigoLight,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Developer Screen Debugger",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = NexTextPrimary
                        )
                        Text(
                            text = "View live node hierarchy, bounds & test action executor",
                            fontSize = 11.sp,
                            color = NexTextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ObservationMetricCard(
    icon: ImageVector,
    title: String,
    value: String,
    secondaryValue: String? = null,
    badge: String,
    badgeColor: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(NexCardBackground)
            .border(1.dp, NexBorderDark, RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(NexSurface, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = NexCyanLight,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 11.sp,
                    color = NexTextMuted,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = value,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = NexTextPrimary,
                    maxLines = 1
                )
                if (secondaryValue != null) {
                    Text(
                        text = secondaryValue,
                        fontSize = 11.sp,
                        color = NexTextSecondary,
                        maxLines = 1
                    )
                }
            }

            Box(
                modifier = Modifier
                    .background(badgeColor.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = badge,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = badgeColor
                )
            }
        }
    }
}
