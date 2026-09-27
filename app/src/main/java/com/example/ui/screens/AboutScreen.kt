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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.ui.components.NexBadge
import com.example.ui.components.NexCard
import com.example.ui.theme.NexBorder
import com.example.ui.theme.NexCyanLight
import com.example.ui.theme.NexEmerald
import com.example.ui.theme.NexIndigoLight
import com.example.ui.theme.NexObsidian
import com.example.ui.theme.NexSurfaceElevated
import com.example.ui.theme.NexTextMuted
import com.example.ui.theme.NexTextPrimary
import com.example.ui.theme.NexTextSecondary

@Composable
fun AboutScreen(
    navController: NavController
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(NexObsidian)
            .padding(horizontal = 20.dp)
            .testTag("about_screen")
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
                        text = "ABOUT NEX",
                        color = NexTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Native Android Personal AI Assistant",
                        color = NexTextMuted,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // App Brand Card
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
                            text = "NEX",
                            color = NexTextPrimary,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 2.sp
                        )
                        NexBadge(text = "v1.0.0-FOUNDATION", color = NexIndigoLight)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "NEX is designed as a serious personal AI operating layer for Android. It bridges natural conversational intent with verified, autonomous device actions.",
                        color = NexTextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "CORE ARCHITECTURAL PILLARS",
                color = NexTextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            PillarCard(
                title = "1. Zero-Arbitrary Code Execution",
                description = "Gemini acts strictly as a reasoning planner returning structured JSON actions. No raw shell scripts or arbitrary code are ever executed on your phone."
            )

            PillarCard(
                title = "2. Real System Integrations",
                description = "Every interaction uses native Android APIs: SpeechRecognizer for voice perception, TextToSpeech for synthesis, AccessibilityService for screen interaction, and Room SQLite for persistence."
            )

            PillarCard(
                title = "3. Strict Action Validation",
                description = "All planned actions pass through the ActionValidator. Missing permissions, malformed parameters, and unauthorized gestures are intercepted prior to invocation."
            )

            PillarCard(
                title = "4. Local-First Encrypted Memory",
                description = "User facts, voice aliases, routines, and telemetry history remain securely persisted on device inside encrypted Room database tables."
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "SPECIFICATIONS",
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SpecRow("Framework", "Native Kotlin & Jetpack Compose")
                    SpecRow("Design System", "Material 3 Obsidian Dark")
                    SpecRow("Reasoning Engine", "Google Gemini 2.5 Flash / 3.1 Pro")
                    SpecRow("Persistence", "AndroidX Room (SQLite)")
                    SpecRow("Speech Engine", "Android SpeechRecognizer (Multilingual)")
                    SpecRow("Target Android SDK", "Android 15 (API 35)")
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
private fun PillarCard(title: String, description: String) {
    NexCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        backgroundColor = NexSurfaceElevated,
        borderColor = NexBorder
    ) {
        Column {
            Text(
                text = title,
                color = NexTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                color = NexTextMuted,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun SpecRow(label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text = label, color = NexTextMuted, fontSize = 12.sp)
        Text(text = value, color = NexTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}
