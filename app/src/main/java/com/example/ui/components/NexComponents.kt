package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.TaskExecutionState
import com.example.core.TaskState
import com.example.ui.theme.NexAmber
import com.example.ui.theme.NexBorder
import com.example.ui.theme.NexBorderLight
import com.example.ui.theme.NexCyan
import com.example.ui.theme.NexCyanLight
import com.example.ui.theme.NexEmerald
import com.example.ui.theme.NexIndigo
import com.example.ui.theme.NexIndigoLight
import com.example.ui.theme.NexRose
import com.example.ui.theme.NexSurfaceCard
import com.example.ui.theme.NexSurfaceElevated
import com.example.ui.theme.NexTextMuted
import com.example.ui.theme.NexTextPrimary
import com.example.ui.theme.NexTextSecondary

@Composable
fun NexOrb(
    taskState: TaskState,
    rmsDb: Float = 0f,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    onClick: () -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing)
        ),
        label = "rotation"
    )

    val (glowColor, coreColor) = when (taskState) {
        TaskState.IDLE -> Pair(NexIndigo.copy(alpha = 0.4f), NexIndigoLight)
        TaskState.LISTENING -> Pair(NexCyan.copy(alpha = 0.7f), NexCyanLight)
        TaskState.PROCESSING, TaskState.PLANNING -> Pair(NexIndigo.copy(alpha = 0.8f), NexCyan)
        TaskState.EXECUTING -> Pair(NexEmerald.copy(alpha = 0.8f), NexEmerald)
        TaskState.WAITING_FOR_USER -> Pair(NexAmber.copy(alpha = 0.8f), NexAmber)
        TaskState.COMPLETED -> Pair(NexEmerald.copy(alpha = 0.7f), NexEmerald)
        TaskState.FAILED -> Pair(NexRose.copy(alpha = 0.8f), NexRose)
        else -> Pair(NexIndigo.copy(alpha = 0.5f), NexIndigoLight)
    }

    val animatedGlow by animateColorAsState(glowColor, label = "glowColor")
    val animatedCore by animateColorAsState(coreColor, label = "coreColor")

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .testTag("nex_orb")
    ) {
        // Outer aura
        Canvas(modifier = Modifier.size(size)) {
            val radius = (this.size.minDimension / 2f) * if (taskState == TaskState.LISTENING) {
                (pulseScale + (rmsDb / 20f).coerceIn(0f, 0.25f))
            } else pulseScale

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(animatedGlow, animatedGlow.copy(alpha = 0.15f), Color.Transparent),
                    radius = radius
                ),
                radius = radius
            )

            // Orbital Ring
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        animatedCore.copy(alpha = 0.8f),
                        Color.Transparent,
                        animatedCore.copy(alpha = 0.4f),
                        Color.Transparent
                    )
                ),
                radius = radius * 0.82f,
                style = Stroke(width = 2.dp.toPx())
            )

            // Inner Core
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(animatedCore, animatedGlow.copy(alpha = 0.5f))
                ),
                radius = radius * 0.55f
            )
        }

        // Center Icon
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = "Microphone",
            tint = Color.White,
            modifier = Modifier.size(size * 0.3f)
        )
    }
}

@Composable
fun NexBadge(
    text: String,
    color: Color = NexIndigoLight,
    bgColor: Color = color.copy(alpha = 0.15f),
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(bgColor, RoundedCornerShape(100.dp))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(100.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
fun NexCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = NexSurfaceCard,
    borderColor: Color = NexBorder,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val clickableModifier = if (onClick != null) {
        modifier.clickable(onClick = onClick)
    } else {
        modifier
    }

    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        modifier = clickableModifier
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

@Composable
fun TaskStateCard(
    state: TaskExecutionState,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    NexCard(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = NexSurfaceElevated,
        borderColor = if (state.state == TaskState.WAITING_FOR_USER) NexAmber.copy(alpha = 0.6f) else NexBorder
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "ACTIVE TASK",
                    color = NexTextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                NexBadge(
                    text = state.state.name,
                    color = when (state.state) {
                        TaskState.IDLE -> NexTextMuted
                        TaskState.LISTENING -> NexCyanLight
                        TaskState.PROCESSING, TaskState.PLANNING -> NexIndigoLight
                        TaskState.EXECUTING -> NexEmerald
                        TaskState.WAITING_FOR_USER -> NexAmber
                        TaskState.COMPLETED -> NexEmerald
                        TaskState.FAILED -> NexRose
                        TaskState.CANCELLED -> NexTextMuted
                        TaskState.VERIFYING -> NexCyan
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (!state.prompt.isNullOrBlank()) {
                Text(
                    text = "\"${state.prompt}\"",
                    color = NexTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (!state.currentActionDescription.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = state.currentActionDescription,
                    color = NexTextSecondary,
                    fontSize = 13.sp
                )
            }

            if (state.state == TaskState.EXECUTING || state.state == TaskState.PLANNING) {
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { state.progressPercent },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = NexIndigoLight,
                    trackColor = NexBorder
                )
            }

            if (!state.resultSummary.isNullOrBlank() && state.state == TaskState.COMPLETED) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = state.resultSummary,
                    color = NexEmerald,
                    fontSize = 13.sp
                )
            }

            if (!state.errorMessage.isNullOrBlank() && state.state == TaskState.FAILED) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = state.errorMessage,
                    color = NexRose,
                    fontSize = 13.sp
                )
            }

            // Sensitive action confirmation dialog buttons
            if (state.state == TaskState.WAITING_FOR_USER && !state.pendingConfirmationPrompt.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = state.pendingConfirmationPrompt,
                    color = NexAmber,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(containerColor = NexEmerald),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("confirm_action_btn")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Confirm", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Authorize")
                    }
                    OutlinedButton(
                        onClick = onCancel,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NexRose),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("cancel_action_btn")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Deny")
                    }
                }
            }
        }
    }
}
