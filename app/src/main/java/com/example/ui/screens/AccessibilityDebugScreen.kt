package com.example.ui.screens

import android.view.accessibility.AccessibilityEvent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.NexApplication
import com.example.accessibility.NexAccessibilityService
import com.example.accessibility.model.AccessibilityServiceState
import com.example.accessibility.model.ScreenElement
import com.example.accessibility.model.ScreenSnapshot
import com.example.accessibility.model.ScreenSnapshotResult
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
fun AccessibilityDebugScreen(navController: NavController) {
    val app = NexApplication.instance
    val statusManager = app.accessibilityStatusManager
    val screenProvider = app.currentScreenProvider
    val actionExecutor = app.actionExecutor

    val serviceState by statusManager.serviceConnectionState.collectAsState()
    val currentApp by statusManager.currentForegroundApp.collectAsState()
    val lastUpdateTime by statusManager.lastScreenUpdateTime.collectAsState()
    val lastActionResult by statusManager.lastActionResult.collectAsState()
    val lastError by statusManager.lastError.collectAsState()

    val serviceInstance = NexAccessibilityService.getInstance()
    val lastEventType = serviceInstance?.lastEventType ?: 0
    val lastEventTime = serviceInstance?.lastEventTimestamp ?: 0L

    var latestSnapshot by remember { mutableStateOf<ScreenSnapshot?>(actionExecutor.lastObservedSnapshot) }
    var isRefreshing by remember { mutableStateOf(false) }
    var statusFeedback by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(NexObsidian)
            .padding(horizontal = 20.dp)
            .testTag("accessibility_debug_screen")
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
                    modifier = Modifier.testTag("debug_back_button")
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
                        text = "Screen Debugger",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = NexTextPrimary
                    )
                    Text(
                        text = "Real-time accessibility node inspector",
                        fontSize = 12.sp,
                        color = NexTextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Diagnostics Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(NexSurface)
                    .border(1.dp, NexBorderDark, RoundedCornerShape(14.dp))
                    .padding(16.dp)
            ) {
                Column {
                    DebugMetricRow(
                        label = "Service State",
                        value = serviceState.name,
                        valueColor = when (serviceState) {
                            AccessibilityServiceState.CONNECTED -> NexEmerald
                            AccessibilityServiceState.DISCONNECTED -> NexWarning
                            AccessibilityServiceState.DISABLED -> NexError
                        }
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    DebugMetricRow(
                        label = "Active Package",
                        value = currentApp?.packageName ?: "None"
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    val eventName = formatEventTypeName(lastEventType)
                    DebugMetricRow(
                        label = "Last Event Type",
                        value = eventName
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    val eventTimeFormatted = if (lastEventTime > 0L) {
                        SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(lastEventTime))
                    } else {
                        "Never"
                    }
                    DebugMetricRow(
                        label = "Last Event Time",
                        value = eventTimeFormatted
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    val snapshotTimeFormatted = latestSnapshot?.timestamp?.let {
                        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(it))
                    } ?: "No snapshot captured"
                    DebugMetricRow(
                        label = "Snapshot Time",
                        value = snapshotTimeFormatted
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    DebugMetricRow(
                        label = "Detected Nodes",
                        value = "${latestSnapshot?.elementCount ?: 0} elements (${latestSnapshot?.interactiveElementCount ?: 0} interactive)"
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    if (lastActionResult != null) {
                        DebugMetricRow(
                            label = "Last Action",
                            value = "${lastActionResult?.actionType} -> ${lastActionResult?.status} (${lastActionResult?.durationMs}ms)"
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    if (lastError != null) {
                        DebugMetricRow(
                            label = "Last Error",
                            value = lastError ?: "",
                            valueColor = NexError
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }

        // Live Refresh Button
        item {
            Button(
                onClick = {
                    coroutineScope.launch {
                        isRefreshing = true
                        statusFeedback = null
                        val result = screenProvider.getCurrentScreenSnapshot(filterSensitive = true)
                        isRefreshing = false
                        when (result) {
                            is ScreenSnapshotResult.Success -> {
                                latestSnapshot = result.snapshot
                                statusFeedback = "Refreshed: ${result.snapshot.elementCount} elements detected"
                            }
                            is ScreenSnapshotResult.Error -> {
                                statusFeedback = "Capture Error: ${result.message}"
                            }
                        }
                    }
                },
                enabled = !isRefreshing && serviceState == AccessibilityServiceState.CONNECTED,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NexCyanLight.copy(alpha = 0.15f),
                    contentColor = NexCyanLight,
                    disabledContainerColor = NexSurface,
                    disabledContentColor = NexTextMuted
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, NexCyanLight.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                    .testTag("refresh_live_snapshot_button")
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = NexCyanLight,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Querying Android Accessibility Tree...", fontSize = 12.sp)
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Refresh Live Screen Snapshot", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            AnimatedVisibility(visible = statusFeedback != null) {
                statusFeedback?.let { msg ->
                    Text(
                        text = msg,
                        fontSize = 11.sp,
                        color = if (msg.startsWith("Refreshed")) NexEmerald else NexError,
                        modifier = Modifier.padding(top = 6.dp, start = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Section Title: Live Elements
        item {
            Text(
                text = "INSPECTED UI NODES (${latestSnapshot?.elements?.size ?: 0})",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = NexTextMuted,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        val elements = latestSnapshot?.elements ?: emptyList()
        if (elements.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(NexCardBackground)
                        .border(1.dp, NexBorderDark, RoundedCornerShape(12.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (serviceState != AccessibilityServiceState.CONNECTED)
                                "Accessibility Service Disconnected"
                            else
                                "No screen nodes inspected yet",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = NexTextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap 'Refresh Live Screen Snapshot' above to inspect the current active window.",
                            fontSize = 11.sp,
                            color = NexTextMuted
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        } else {
            items(elements) { element ->
                ElementInspectionCard(element = element)
                Spacer(modifier = Modifier.height(8.dp))
            }
            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun DebugMetricRow(
    label: String,
    value: String,
    valueColor: Color = NexTextPrimary
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = NexTextMuted
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = valueColor,
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )
    }
}

@Composable
private fun ElementInspectionCard(element: ScreenElement) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(NexCardBackground)
            .border(1.dp, NexBorderDark, RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "#${element.id} ${element.className.substringAfterLast('.')}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = NexCyanLight,
                    fontFamily = FontFamily.Monospace
                )

                Text(
                    text = element.bounds.toString(),
                    fontSize = 10.sp,
                    color = NexTextMuted,
                    fontFamily = FontFamily.Monospace
                )
            }

            val label = element.meaningfulLabel
            if (!label.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = label,
                    fontSize = 12.sp,
                    color = if (element.isPassword) NexWarning else NexTextPrimary,
                    maxLines = 2
                )
            }

            if (!element.resourceId.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = element.resourceId.substringAfterLast(":id/"),
                    fontSize = 10.sp,
                    color = NexIndigoLight,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Badges
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (element.clickable) {
                    MiniBadge(text = "Clickable", color = NexCyanLight)
                }
                if (element.editable) {
                    MiniBadge(text = "Editable", color = NexEmerald)
                }
                if (element.scrollable) {
                    MiniBadge(text = "Scrollable", color = NexIndigoLight)
                }
                if (element.isPassword) {
                    MiniBadge(text = "Sensitive", color = NexWarning)
                }
                if (!element.enabled) {
                    MiniBadge(text = "Disabled", color = NexError)
                }
            }
        }
    }
}

@Composable
private fun MiniBadge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

private fun formatEventTypeName(eventType: Int): String {
    return when (eventType) {
        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "WINDOW_STATE_CHANGED (32)"
        AccessibilityEvent.TYPE_VIEW_CLICKED -> "VIEW_CLICKED (1)"
        AccessibilityEvent.TYPE_VIEW_FOCUSED -> "VIEW_FOCUSED (8)"
        AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> "VIEW_TEXT_CHANGED (16)"
        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "WINDOW_CONTENT_CHANGED (2048)"
        AccessibilityEvent.TYPE_VIEW_SCROLLED -> "VIEW_SCROLLED (4096)"
        AccessibilityEvent.TYPE_WINDOWS_CHANGED -> "WINDOWS_CHANGED (4194304)"
        0 -> "None recorded"
        else -> "Event ($eventType)"
    }
}
