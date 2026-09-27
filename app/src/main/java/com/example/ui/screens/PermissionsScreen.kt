package com.example.ui.screens

import android.Manifest
import android.os.Build
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.NexApplication
import com.example.permissions.PermissionItem
import com.example.ui.components.NexBadge
import com.example.ui.components.NexCard
import com.example.ui.theme.NexAmber
import com.example.ui.theme.NexBorder
import com.example.ui.theme.NexEmerald
import com.example.ui.theme.NexIndigo
import com.example.ui.theme.NexIndigoLight
import com.example.ui.theme.NexObsidian
import com.example.ui.theme.NexRose
import com.example.ui.theme.NexSurfaceElevated
import com.example.ui.theme.NexTextMuted
import com.example.ui.theme.NexTextPrimary
import com.example.ui.theme.NexTextSecondary

@Composable
fun PermissionsScreen(
    navController: NavController,
    app: NexApplication = NexApplication.instance
) {
    val permissionState by app.permissionManager.permissionState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Refresh permissions on resume when returning from Android Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                app.permissionManager.refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val micLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        app.permissionManager.refreshPermissions()
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        app.permissionManager.refreshPermissions()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(NexObsidian)
            .padding(horizontal = 20.dp)
            .testTag("permissions_screen")
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = "CAPABILITY AUDIT",
                        color = NexTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Real System Permissions & Autonomy Access",
                        color = NexTextMuted,
                        fontSize = 11.sp
                    )
                }

                IconButton(
                    onClick = { app.permissionManager.refreshPermissions() },
                    modifier = Modifier.testTag("refresh_permissions_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = NexIndigoLight
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Autonomy Capability Summary Card
            NexCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = NexSurfaceElevated,
                borderColor = if (permissionState.allRequiredGranted) NexEmerald.copy(alpha = 0.5f) else NexAmber.copy(alpha = 0.5f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = if (permissionState.allRequiredGranted) NexEmerald else NexAmber,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (permissionState.allRequiredGranted) {
                                "Full Autonomy Engine Enabled"
                            } else {
                                "Limited Capabilities Active"
                            },
                            color = NexTextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (permissionState.allRequiredGranted) {
                                "NEX can listen, perceive active applications, and perform authorized device actions."
                            } else {
                                "Enable the Accessibility Service and Microphone below to unlock autonomous execution."
                            },
                            color = NexTextMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "PERMISSIONS & CAPABILITIES",
                color = NexTextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        items(permissionState.items) { item ->
            PermissionCard(
                item = item,
                onGrantClick = {
                    when (item.id) {
                        "microphone" -> micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        "accessibility" -> app.permissionManager.openAccessibilitySettings()
                        "notification" -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                app.permissionManager.openNotificationSettings()
                            }
                        }
                    }
                },
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))

            // System App Info Button
            OutlinedButton(
                onClick = { app.permissionManager.openAppSettings() },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NexTextSecondary),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("open_app_settings_btn")
            ) {
                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open Android App Details & Permissions")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PermissionCard(
    item: PermissionItem,
    onGrantClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    NexCard(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = NexSurfaceElevated,
        borderColor = if (item.isGranted) NexBorder else NexAmber.copy(alpha = 0.3f)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = item.name,
                    color = NexTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                NexBadge(
                    text = if (item.isGranted) "GRANTED" else "NOT GRANTED",
                    color = if (item.isGranted) NexEmerald else NexRose
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = item.description,
                color = NexTextMuted,
                fontSize = 12.sp
            )

            if (!item.isGranted) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onGrantClick,
                    colors = ButtonDefaults.buttonColors(containerColor = NexIndigo),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("enable_${item.id}_btn")
                ) {
                    Text(
                        text = if (item.requiresSystemSettings) {
                            "Open Android Accessibility Settings"
                        } else {
                            "Grant Permission"
                        },
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
