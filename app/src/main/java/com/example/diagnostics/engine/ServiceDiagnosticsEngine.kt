package com.example.diagnostics.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.example.diagnostics.model.DiagnosticCategory
import com.example.diagnostics.model.DiagnosticItem
import com.example.diagnostics.model.DiagnosticStatus
import com.example.diagnostics.model.SubsystemDiagnosticResult
import com.example.services.NexForegroundService

class ServiceDiagnosticsEngine(
    private val context: Context
) {

    fun runDiagnostics(): SubsystemDiagnosticResult {
        val startTime = System.currentTimeMillis()
        val items = mutableListOf<DiagnosticItem>()

        // 1. Foreground Service State
        val isFgsRunning = NexForegroundService.isRunning.value
        items.add(
            DiagnosticItem(
                id = "srv_fgs_state",
                title = "Assistant Foreground Service",
                category = DiagnosticCategory.SERVICES,
                status = if (isFgsRunning) DiagnosticStatus.PASS else DiagnosticStatus.INFO,
                summary = if (isFgsRunning) "Active (Persistent Process)" else "Standby (Can be started via Settings or Home)",
                details = "Service: com.example.services.NexForegroundService\nType: specialUse\nIs Running: $isFgsRunning"
            )
        )

        // 2. Notification Channels Registration
        val notifManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val channel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notifManager?.getNotificationChannel(NexForegroundService.CHANNEL_ID)
        } else null

        val isChannelRegistered = channel != null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O

        items.add(
            DiagnosticItem(
                id = "srv_notif_channel",
                title = "Notification Channel Health",
                category = DiagnosticCategory.SERVICES,
                status = if (isChannelRegistered) DiagnosticStatus.PASS else DiagnosticStatus.WARN,
                summary = if (isChannelRegistered) "Channel Registered (${NexForegroundService.CHANNEL_ID})" else "Channel Missing (Will create on start)",
                details = "Channel ID: ${NexForegroundService.CHANNEL_ID}\nImportance: ${channel?.importance ?: "DEFAULT"}"
            )
        )

        val overallStatus = when {
            items.any { it.status == DiagnosticStatus.FAIL } -> DiagnosticStatus.FAIL
            items.any { it.status == DiagnosticStatus.WARN } -> DiagnosticStatus.WARN
            else -> DiagnosticStatus.PASS
        }

        return SubsystemDiagnosticResult(
            category = DiagnosticCategory.SERVICES,
            items = items,
            status = overallStatus,
            executionTimeMs = System.currentTimeMillis() - startTime
        )
    }
}
