package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.automation.agent.AgentTaskContext
import com.example.automation.agent.AgentTaskState
import com.example.automation.queue.ActionQueueProgress
import com.example.core.TaskExecutionState
import com.example.core.TaskState
import com.example.ui.theme.NexAmber
import com.example.ui.theme.NexBorder
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
import java.util.Locale

@Composable
fun TaskExecutionPanel(
    state: TaskExecutionState,
    progress: ActionQueueProgress,
    agentContext: AgentTaskContext? = null,
    onConfirm: () -> Unit,
    onDeny: () -> Unit,
    onCancelTask: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (state.state == TaskState.IDLE && agentContext?.taskState == null) return

    val totalSteps = when {
        agentContext != null && agentContext.plannedActions.isNotEmpty() -> agentContext.plannedActions.size
        progress.totalActions > 0 -> progress.totalActions
        else -> state.totalActions
    }

    val currentStep = when {
        agentContext != null -> (agentContext.completedActions.size + 1).coerceAtMost(totalSteps.coerceAtLeast(1))
        progress.currentIndex > 0 -> progress.currentIndex
        else -> state.currentActionIndex
    }

    val animatedProgress by animateFloatAsState(
        targetValue = if (totalSteps > 0) {
            currentStep.toFloat() / totalSteps.toFloat()
        } else {
            state.progressPercent
        },
        animationSpec = tween(durationMillis = 300),
        label = "task_progress_animation"
    )

    val activeColor = when (agentContext?.taskState) {
        AgentTaskState.OBSERVING, AgentTaskState.VERIFYING -> NexCyan
        AgentTaskState.PLANNING -> NexIndigoLight
        AgentTaskState.EXECUTING -> NexEmerald
        AgentTaskState.RECOVERING -> NexAmber
        AgentTaskState.WAITING_FOR_CONFIRMATION, AgentTaskState.WAITING_FOR_USER_INPUT -> NexAmber
        AgentTaskState.COMPLETED -> NexEmerald
        AgentTaskState.FAILED -> NexRose
        AgentTaskState.CANCELLED -> NexTextMuted
        else -> when (state.state) {
            TaskState.IDLE -> NexTextMuted
            TaskState.LISTENING -> NexCyanLight
            TaskState.PROCESSING, TaskState.PLANNING -> NexIndigoLight
            TaskState.EXECUTING -> NexEmerald
            TaskState.WAITING_FOR_USER -> NexAmber
            TaskState.VERIFYING -> NexCyan
            TaskState.COMPLETED -> NexEmerald
            TaskState.FAILED -> NexRose
            TaskState.CANCELLED -> NexTextMuted
        }
    }

    val elapsedMs = agentContext?.durationMs ?: progress.elapsedTimeMs
    val elapsedSeconds = elapsedMs / 1000
    val elapsedMinutes = elapsedSeconds / 60
    val elapsedRemainingSeconds = elapsedSeconds % 60
    val formattedTime = String.format(Locale.US, "%02d:%02d", elapsedMinutes, elapsedRemainingSeconds)

    val isWaitingUser = state.state == TaskState.WAITING_FOR_USER ||
            agentContext?.taskState == AgentTaskState.WAITING_FOR_CONFIRMATION

    Surface(
        color = NexSurfaceElevated,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (isWaitingUser) NexAmber.copy(alpha = 0.7f) else NexBorder
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("task_execution_panel")
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Top Bar: Section Title + State Badge + Elapsed Time
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(activeColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (agentContext != null) "NEX AUTONOMOUS AGENT" else "TASK EXECUTOR",
                        color = NexTextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (elapsedMs > 0) {
                        Text(
                            text = formattedTime,
                            color = NexTextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                    NexBadge(
                        text = agentContext?.taskState?.name ?: state.state.name,
                        color = activeColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Task Goal / Prompt Title
            val title = agentContext?.goal?.ifBlank { agentContext.userRequest }
                ?: if (!progress.taskPrompt.isBlank()) progress.taskPrompt else state.prompt
            if (!title.isNullOrBlank()) {
                Text(
                    text = "\"$title\"",
                    color = NexTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("task_title_text")
                )
            }

            // Step Counter & Current Application
            if (totalSteps > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = NexIndigo.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = "Step $currentStep of $totalSteps",
                            color = NexIndigoLight,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }

                    val appName = agentContext?.currentPackage
                        ?: progress.currentAppName
                    if (!appName.isNullOrBlank()) {
                        Surface(
                            color = NexSurfaceCard,
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NexBorder)
                        ) {
                            Text(
                                text = appName.substringAfterLast("."),
                                color = NexTextSecondary,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Current Action Description & Human Status Explanation
            val currentDesc = agentContext?.conciseStatusExplanation
                ?: progress.currentAction?.description
                ?: state.currentActionDescription
            if (!currentDesc.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = currentDesc,
                    color = NexTextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.testTag("current_action_description")
                )
            }

            // Recent Observation if available
            val snapshot = agentContext?.currentScreenSnapshot
            if (snapshot != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Observation: ${snapshot.elementCount} elements on ${snapshot.packageName.substringAfterLast(".")}",
                    color = NexTextMuted,
                    fontSize = 11.sp
                )
            }

            // Progress Bar
            val isActive = state.state == TaskState.EXECUTING || state.state == TaskState.PLANNING || state.state == TaskState.VERIFYING ||
                    (agentContext != null && !agentContext.isTerminal && !isWaitingUser)
            if (isActive) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .testTag("task_progress_bar"),
                    color = NexEmerald,
                    trackColor = NexBorder
                )
            }

            // Authorization Box when Waiting for User
            if (isWaitingUser) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    color = NexAmber.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NexAmber.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = "Security Alert",
                                tint = NexAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Action Authorization Required",
                                color = NexAmber,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = state.pendingConfirmationPrompt
                                ?: agentContext?.conciseStatusExplanation
                                ?: "Allow NEX to proceed with this sensitive action?",
                            color = NexTextPrimary,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = onConfirm,
                                colors = ButtonDefaults.buttonColors(containerColor = NexEmerald),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("confirm_action_btn")
                            ) {
                                Icon(Icons.Default.Check, contentDescription = "Authorize", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Authorize", fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = onDeny,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = NexRose),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("cancel_action_btn")
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Deny", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Deny", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Results and Status Feedback
            val isCompleted = state.state == TaskState.COMPLETED || agentContext?.taskState == AgentTaskState.COMPLETED
            if (isCompleted) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Success",
                        tint = NexEmerald,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = state.resultSummary ?: agentContext?.conciseStatusExplanation ?: "Task completed.",
                        color = NexEmerald,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            val isFailed = state.state == TaskState.FAILED || agentContext?.taskState == AgentTaskState.FAILED
            if (isFailed) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Failed",
                        tint = NexRose,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = state.errorMessage ?: agentContext?.conciseStatusExplanation ?: "Task failed.",
                        color = NexRose,
                        fontSize = 13.sp
                    )
                }
            }

            val isCancelled = state.state == TaskState.CANCELLED || agentContext?.taskState == AgentTaskState.CANCELLED
            if (isCancelled) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Task was cancelled.",
                    color = NexTextMuted,
                    fontSize = 13.sp
                )
            }

            // Footer Actions: Prominent STOP TASK button when active, or Dismiss when finished
            Spacer(modifier = Modifier.height(12.dp))
            val isTaskRunning = state.state == TaskState.EXECUTING || state.state == TaskState.PLANNING ||
                    state.state == TaskState.VERIFYING || (agentContext != null && !agentContext.isTerminal)

            if (isTaskRunning) {
                Button(
                    onClick = onCancelTask,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NexRose.copy(alpha = 0.2f),
                        contentColor = NexRose
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NexRose.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("stop_task_button")
                        .testTag("task_cancel_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Stop Task",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "STOP TASK",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            } else if (isCompleted || isFailed || isCancelled) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NexTextSecondary),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NexBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("task_dismiss_button")
                ) {
                    Text(
                        text = "Dismiss",
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
