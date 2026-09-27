package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.NexApplication
import com.example.ai.GeminiConfig
import com.example.navigation.NexNavDestination
import com.example.services.NexForegroundService
import com.example.ui.components.NexBadge
import com.example.ui.components.NexCard
import com.example.ui.theme.NexBorder
import com.example.ui.theme.NexCyanLight
import com.example.ui.theme.NexEmerald
import com.example.ui.theme.NexIndigo
import com.example.ui.theme.NexIndigoLight
import com.example.ui.theme.NexObsidian
import com.example.ui.theme.NexSurfaceElevated
import com.example.ui.theme.NexTextMuted
import com.example.ui.theme.NexTextPrimary
import com.example.ui.theme.NexTextSecondary
import com.example.voice.VoiceLanguage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    app: NexApplication = NexApplication.instance
) {
    val isFgsRunning by NexForegroundService.isRunning.collectAsStateWithLifecycle()

    var selectedModel by remember { mutableStateOf(app.geminiConfig.getSelectedModel()) }
    var customApiKey by remember { mutableStateOf(app.geminiConfig.getApiKey()) }
    var apiKeySavedFeedback by remember { mutableStateOf(false) }

    var modelDropdownExpanded by remember { mutableStateOf(false) }
    val models = listOf(GeminiConfig.MODEL_FLASH, GeminiConfig.MODEL_PRO)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(NexObsidian)
            .padding(horizontal = 20.dp)
            .testTag("settings_screen")
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))

            Column {
                Text(
                    text = "SETTINGS",
                    color = NexTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Operating Layer Configuration & AI Preferences",
                    color = NexTextMuted,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section: AI / Gemini Engine
            Text(
                text = "GEMINI REASONING ENGINE",
                color = NexTextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

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
                        Text(
                            text = "Model Architecture",
                            color = NexTextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        NexBadge(
                            text = if (app.geminiConfig.isApiKeyConfigured()) "KEY READY" else "KEY UNCONFIGURED",
                            color = if (app.geminiConfig.isApiKeyConfigured()) NexEmerald else NexCyanLight
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    ExposedDropdownMenuBox(
                        expanded = modelDropdownExpanded,
                        onExpandedChange = { modelDropdownExpanded = !modelDropdownExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedModel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Active Gemini Model") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelDropdownExpanded) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = NexTextPrimary,
                                unfocusedTextColor = NexTextPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(
                            expanded = modelDropdownExpanded,
                            onDismissRequest = { modelDropdownExpanded = false }
                        ) {
                            models.forEach { model ->
                                DropdownMenuItem(
                                    text = { Text(model) },
                                    onClick = {
                                        selectedModel = model
                                        app.geminiConfig.setSelectedModel(model)
                                        modelDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = customApiKey,
                        onValueChange = {
                            customApiKey = it
                            apiKeySavedFeedback = false
                        },
                        label = { Text("Gemini API Key") },
                        placeholder = { Text("Injected from Secrets or enter custom key") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = NexTextPrimary,
                            unfocusedTextColor = NexTextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (apiKeySavedFeedback) "Key configured successfully!" else "Secured locally on device",
                            color = if (apiKeySavedFeedback) NexEmerald else NexTextMuted,
                            fontSize = 11.sp
                        )

                        Button(
                            onClick = {
                                app.geminiConfig.setCustomApiKey(customApiKey)
                                apiKeySavedFeedback = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NexIndigoLight)
                        ) {
                            Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Update Key", fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section: Background Service
            Text(
                text = "PERSISTENT EXECUTION",
                color = NexTextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            NexCard(
                modifier = Modifier.fillMaxWidth(),
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
                            text = "Foreground Service",
                            color = NexTextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Maintains NEX in active memory to prevent OS termination during automation tasks.",
                            color = NexTextMuted,
                            fontSize = 11.sp
                        )
                    }

                    Switch(
                        checked = isFgsRunning,
                        onCheckedChange = { start ->
                            if (start) {
                                NexForegroundService.startService(app)
                            } else {
                                NexForegroundService.stopService(app)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NexIndigoLight,
                            checkedTrackColor = NexIndigoLight.copy(alpha = 0.5f)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section: Voice & Speech Engine
            Text(
                text = "SPEECH & VOICE",
                color = NexTextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            SettingsNavigationRow(
                icon = Icons.Default.Mic,
                title = "Voice Engine & Speech Settings",
                subtitle = "Language, speech rate, pitch, auto-speak, & Android engine diagnostics",
                onClick = { navController.navigate(NexNavDestination.VoiceSettings.route) }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Navigation Links
            Text(
                text = "SYSTEM CONFIGURATION",
                color = NexTextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            SettingsNavigationRow(
                icon = NexNavDestination.AppsControl.icon,
                title = "Installed Apps & Automation Providers",
                subtitle = "Discovered apps, voice aliases, capabilities, and modular providers",
                onClick = { navController.navigate(NexNavDestination.AppsControl.route) }
            )

            SettingsNavigationRow(
                icon = NexNavDestination.AccessibilityControl.icon,
                title = "Accessibility Control Center",
                subtitle = "Active service state, foreground app, and screen perception",
                onClick = { navController.navigate(NexNavDestination.AccessibilityControl.route) }
            )

            SettingsNavigationRow(
                icon = NexNavDestination.AccessibilityDebug.icon,
                title = "Screen Node Debugger",
                subtitle = "Inspect real-time hierarchy, node bounds, and raw elements",
                onClick = { navController.navigate(NexNavDestination.AccessibilityDebug.route) }
            )

            SettingsNavigationRow(
                icon = Icons.Default.AutoAwesome,
                title = "Automation & Action Validator",
                subtitle = "Test schemas, parameter bounds, and validation sandbox",
                onClick = { navController.navigate(NexNavDestination.Automation.route) }
            )

            SettingsNavigationRow(
                icon = Icons.Default.Security,
                title = "Security & Safeguards",
                subtitle = "Biometrics audit and sensitive action confirmation policies",
                onClick = { navController.navigate(NexNavDestination.Security.route) }
            )

            SettingsNavigationRow(
                icon = Icons.Default.Memory,
                title = "Local Memory & Aliases",
                subtitle = "Manage personal context, trigger expansions, and facts",
                onClick = { navController.navigate(NexNavDestination.Memory.route) }
            )

            SettingsNavigationRow(
                icon = Icons.Default.BugReport,
                title = "Deep Testing & Diagnostics",
                subtitle = "Validate all 11 subsystems, hardware health, and run interactive CUJs",
                onClick = { navController.navigate(NexNavDestination.DeepTesting.route) }
            )

            SettingsNavigationRow(
                icon = Icons.Default.Info,
                title = "About NEX",
                subtitle = "Architecture specs, zero-fake manifesto, and app version",
                onClick = { navController.navigate(NexNavDestination.About.route) }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsNavigationRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    NexCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        backgroundColor = NexSurfaceElevated,
        borderColor = NexBorder,
        onClick = onClick
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = NexIndigoLight,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        color = NexTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = subtitle,
                        color = NexTextMuted,
                        fontSize = 11.sp
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = NexTextMuted,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
