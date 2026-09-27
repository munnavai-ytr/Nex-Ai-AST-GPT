package com.example.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.ui.graphics.vector.ImageVector

sealed class NexNavDestination(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val isBottomBarItem: Boolean = false
) {
    object Home : NexNavDestination("home", "Home", Icons.Default.Dashboard, isBottomBarItem = true)
    object Assistant : NexNavDestination("assistant", "Assistant", Icons.Default.Mic, isBottomBarItem = true)
    object Permissions : NexNavDestination("permissions", "Permissions", Icons.Default.CheckCircle, isBottomBarItem = true)
    object Memory : NexNavDestination("memory", "Memory", Icons.Default.Memory, isBottomBarItem = true)
    object Settings : NexNavDestination("settings", "Settings", Icons.Default.Settings, isBottomBarItem = true)

    object AppsControl : NexNavDestination("apps_control", "Apps & Control", Icons.Default.Apps)
    object Automation : NexNavDestination("automation", "Automation", Icons.Default.AutoAwesome)
    object Security : NexNavDestination("security", "Security", Icons.Default.Security)
    object VoiceEnrollment : NexNavDestination("voice_enrollment", "Voice Enrollment", Icons.Default.Mic)
    object VoiceSettings : NexNavDestination("voice_settings", "Voice Settings", Icons.Default.Mic)
    object AccessibilityControl : NexNavDestination("accessibility_control", "Accessibility", Icons.Default.Visibility)
    object AccessibilityDebug : NexNavDestination("accessibility_debug", "Screen Debugger", Icons.Default.BugReport)
    object ActivityLogs : NexNavDestination("activity_logs", "Logs", Icons.Default.History)
    object PrivacyCenter : NexNavDestination("privacy_center", "Privacy Center", Icons.Default.Security)
    object DeepTesting : NexNavDestination("deep_testing", "Testing & Diagnostics", Icons.Default.BugReport)
    object About : NexNavDestination("about", "About NEX", Icons.Default.Info)

    companion object {
        val bottomNavItems = listOf(Home, Assistant, Permissions, Memory, Settings)
    }
}
