package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.navigation.NexNavDestination
import com.example.ui.screens.AboutScreen
import com.example.ui.screens.AccessibilityControlCenterScreen
import com.example.ui.screens.AccessibilityDebugScreen
import com.example.ui.screens.ActivityLogsScreen
import com.example.ui.screens.AppsControlScreen
import com.example.ui.screens.AssistantScreen
import com.example.ui.screens.AutomationScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MemoryScreen
import com.example.ui.screens.PermissionsScreen
import com.example.ui.screens.SecurityScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.VoiceEnrollmentScreen
import com.example.ui.screens.VoiceSettingsScreen
import com.example.ui.theme.NexBorder
import com.example.ui.theme.NexCyanLight
import com.example.ui.theme.NexIndigoLight
import com.example.ui.theme.NexObsidian
import com.example.ui.theme.NexSurface
import com.example.ui.theme.NexTextMuted
import com.example.ui.theme.NexTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            NexTheme {
                NexAppRoot()
            }
        }
    }
}

@Composable
fun NexAppRoot() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val bottomNavItems = NexNavDestination.bottomNavItems
    val showBottomBar = bottomNavItems.any { it.route == currentRoute }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(NexObsidian),
        containerColor = NexObsidian,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = NexSurface,
                    contentColor = NexTextMuted,
                    tonalElevation = 0.dp,
                    modifier = Modifier.testTag("nex_bottom_navigation")
                ) {
                    bottomNavItems.forEach { destination ->
                        val selected = currentRoute == destination.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (currentRoute != destination.route) {
                                    navController.navigate(destination.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = destination.title,
                                    modifier = Modifier.size(22.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = destination.title,
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = NexCyanLight,
                                selectedTextColor = NexCyanLight,
                                unselectedIconColor = NexTextMuted,
                                unselectedTextColor = NexTextMuted,
                                indicatorColor = NexIndigoLight.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier.testTag("nav_item_${destination.route}")
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = NexNavDestination.Home.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            composable(NexNavDestination.Home.route) {
                HomeScreen(navController = navController)
            }
            composable(NexNavDestination.Assistant.route) {
                AssistantScreen(navController = navController)
            }
            composable(NexNavDestination.Permissions.route) {
                PermissionsScreen(navController = navController)
            }
            composable(NexNavDestination.Memory.route) {
                MemoryScreen(navController = navController)
            }
            composable(NexNavDestination.Settings.route) {
                SettingsScreen(navController = navController)
            }
            composable(NexNavDestination.AppsControl.route) {
                AppsControlScreen(navController = navController)
            }
            composable(NexNavDestination.Automation.route) {
                AutomationScreen(navController = navController)
            }
            composable(NexNavDestination.Security.route) {
                SecurityScreen(navController = navController)
            }
            composable(NexNavDestination.VoiceEnrollment.route) {
                VoiceEnrollmentScreen(navController = navController)
            }
            composable(NexNavDestination.VoiceSettings.route) {
                VoiceSettingsScreen(navController = navController)
            }
            composable(NexNavDestination.AccessibilityControl.route) {
                AccessibilityControlCenterScreen(navController = navController)
            }
            composable(NexNavDestination.AccessibilityDebug.route) {
                AccessibilityDebugScreen(navController = navController)
            }
            composable(NexNavDestination.ActivityLogs.route) {
                ActivityLogsScreen(navController = navController)
            }
            composable(NexNavDestination.PrivacyCenter.route) {
                com.example.ui.screens.PrivacyCenterScreen(navController = navController)
            }
            composable(NexNavDestination.DeepTesting.route) {
                com.example.ui.screens.DeepTestingScreen(navController = navController)
            }
            composable(NexNavDestination.About.route) {
                AboutScreen(navController = navController)
            }
        }
    }
}
