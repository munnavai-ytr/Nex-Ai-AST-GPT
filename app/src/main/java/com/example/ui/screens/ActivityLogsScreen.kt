package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.NexApplication
import com.example.memory.entities.ExecutionLogEntity
import com.example.ui.components.NexBadge
import com.example.ui.components.NexCard
import com.example.ui.theme.NexBorder
import com.example.ui.theme.NexEmerald
import com.example.ui.theme.NexIndigoLight
import com.example.ui.theme.NexObsidian
import com.example.ui.theme.NexRose
import com.example.ui.theme.NexSurfaceElevated
import com.example.ui.theme.NexTextMuted
import com.example.ui.theme.NexTextPrimary
import com.example.ui.theme.NexTextSecondary
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ActivityLogsScreen(
    navController: NavController,
    app: NexApplication = NexApplication.instance
) {
    val logs by app.memoryRepository.recentLogs.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var filterStatus by remember { mutableStateOf("ALL") }

    val filteredLogs = when (filterStatus) {
        "SUCCESS" -> logs.filter { it.status == "SUCCESS" }
        "FAILED" -> logs.filter { it.status == "FAILED" }
        else -> logs
    }

    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(NexObsidian)
            .padding(horizontal = 20.dp)
            .testTag("activity_logs_screen")
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
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
                            text = "EXECUTION LOGS",
                            color = NexTextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Auditable Telemetry & Action Stream",
                            color = NexTextMuted,
                            fontSize = 11.sp
                        )
                    }
                }

                IconButton(
                    onClick = {
                        scope.launch { app.memoryRepository.clearLogs() }
                    },
                    modifier = Modifier.testTag("clear_logs_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Clear logs",
                        tint = NexRose
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Filter chips
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = filterStatus == "ALL",
                        onClick = { filterStatus = "ALL" },
                        label = { Text("ALL (${logs.size})") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NexIndigoLight.copy(alpha = 0.2f),
                            selectedLabelColor = NexIndigoLight
                        )
                    )
                }
                item {
                    FilterChip(
                        selected = filterStatus == "SUCCESS",
                        onClick = { filterStatus = "SUCCESS" },
                        label = { Text("SUCCESS") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NexEmerald.copy(alpha = 0.2f),
                            selectedLabelColor = NexEmerald
                        )
                    )
                }
                item {
                    FilterChip(
                        selected = filterStatus == "FAILED",
                        onClick = { filterStatus = "FAILED" },
                        label = { Text("FAILED") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NexRose.copy(alpha = 0.2f),
                            selectedLabelColor = NexRose
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        if (filteredLogs.isEmpty()) {
            item {
                NexCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = NexSurfaceElevated,
                    borderColor = NexBorder
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp)
                    ) {
                        Text(
                            text = "No execution logs recorded",
                            color = NexTextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Actions run through the Assistant or Automation sandbox will log real telemetry here.",
                            color = NexTextMuted,
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(filteredLogs) { log ->
                LogItemCard(
                    log = log,
                    formattedTime = timeFormatter.format(Date(log.timestamp)),
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
private fun LogItemCard(
    log: ExecutionLogEntity,
    formattedTime: String,
    modifier: Modifier = Modifier
) {
    NexCard(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = NexSurfaceElevated,
        borderColor = if (log.status == "FAILED") NexRose.copy(alpha = 0.3f) else NexBorder
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = log.actionType,
                        color = NexTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formattedTime,
                        color = NexTextMuted,
                        fontSize = 11.sp
                    )
                }

                NexBadge(
                    text = log.status,
                    color = if (log.status == "SUCCESS") NexEmerald else NexRose
                )
            }

            if (log.details.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = log.details,
                    color = NexTextSecondary,
                    fontSize = 12.sp
                )
            }

            if (log.errorMessage != null && log.errorMessage != log.details) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = log.errorMessage,
                    color = NexRose,
                    fontSize = 11.sp
                )
            }

            if (log.durationMs > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Latency: ${log.durationMs}ms",
                    color = NexTextMuted,
                    fontSize = 10.sp
                )
            }
        }
    }
}
