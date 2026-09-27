package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.NexApplication
import com.example.memory.entities.MemoryChangeHistoryEntity
import com.example.memory.model.MemoryCandidate
import com.example.memory.model.MemoryCandidateAction
import com.example.memory.model.MemoryCategory
import com.example.memory.model.MemoryConsentStatus
import com.example.memory.model.MemoryRetentionPolicy
import com.example.memory.model.MemorySensitivity
import com.example.memory.model.MemorySource
import com.example.memory.model.MemoryWriteResult
import com.example.memory.model.StructuredMemory
import com.example.security.BiometricAuthResult
import com.example.ui.components.NexBadge
import com.example.ui.components.NexCard
import com.example.ui.theme.NexAmber
import com.example.ui.theme.NexBorder
import com.example.ui.theme.NexBorderLight
import com.example.ui.theme.NexCyanLight
import com.example.ui.theme.NexEmerald
import com.example.ui.theme.NexIndigoLight
import com.example.ui.theme.NexObsidian
import com.example.ui.theme.NexRose
import com.example.ui.theme.NexSurface
import com.example.ui.theme.NexSurfaceCard
import com.example.ui.theme.NexSurfaceElevated
import com.example.ui.theme.NexTextMuted
import com.example.ui.theme.NexTextPrimary
import com.example.ui.theme.NexTextSecondary
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(
    navController: NavController,
    app: NexApplication = NexApplication.instance
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val allMemories by app.memoryRepository.allStructuredMemories.collectAsStateWithLifecycle(initialValue = emptyList())
    val changeHistory by app.memoryRepository.memoryChangeHistory.collectAsStateWithLifecycle(initialValue = emptyList())
    val installedApps by app.installedAppRegistry.installedApps.collectAsStateWithLifecycle(initialValue = emptyList())

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryIndex by remember { mutableIntStateOf(0) } // 0 = All
    var selectedMainTab by remember { mutableIntStateOf(0) } // 0 = Memories, 1 = Audit History

    // Dialog States
    var showAddDialog by remember { mutableStateOf(false) }
    var showAddAliasDialog by remember { mutableStateOf(false) }
    var editingMemory by remember { mutableStateOf<StructuredMemory?>(null) }
    var viewingMemory by remember { mutableStateOf<StructuredMemory?>(null) }
    var unlockedConfidentialValue by remember { mutableStateOf<String?>(null) }
    var showClearAllDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var exportedJsonContent by remember { mutableStateOf<String?>(null) }
    var showRetentionDialog by remember { mutableStateOf(false) }
    var retentionFeedbackMessage by remember { mutableStateOf<String?>(null) }

    val categories = remember {
        listOf(null) + MemoryCategory.entries
    }

    val filteredMemories = remember(allMemories, searchQuery, selectedCategoryIndex) {
        val cat = categories[selectedCategoryIndex]
        allMemories.filter { mem ->
            val matchesCategory = (cat == null || mem.category == cat)
            val matchesSearch = searchQuery.isBlank() ||
                    mem.key.contains(searchQuery, ignoreCase = true) ||
                    mem.value.contains(searchQuery, ignoreCase = true) ||
                    mem.category.displayName.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NexObsidian)
            .padding(horizontal = 20.dp)
            .testTag("memory_screen")
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text(
                    text = "MEMORY CENTER",
                    color = NexTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "${allMemories.size} Encrypted On-Device Records",
                    color = NexTextMuted,
                    fontSize = 11.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = {
                        scope.launch {
                            val export = app.memoryRepository.backupManager.exportMemories()
                            exportedJsonContent = export.jsonString
                            showExportDialog = true
                        }
                    },
                    modifier = Modifier.size(36.dp).testTag("export_memories_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Export Memories",
                        tint = NexCyanLight,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = { showRetentionDialog = true },
                    modifier = Modifier.size(36.dp).testTag("retention_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = "Retention Policy",
                        tint = NexIndigoLight,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = { showClearAllDialog = true },
                    modifier = Modifier.size(36.dp).testTag("clear_all_memories_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Clear All Memories",
                        tint = NexRose,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search memories, facts, aliases...", color = NexTextMuted, fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = NexCyanLight, modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search", tint = NexTextMuted, modifier = Modifier.size(16.dp))
                    }
                }
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = NexSurfaceElevated,
                unfocusedContainerColor = NexSurfaceElevated,
                focusedBorderColor = NexCyanLight,
                unfocusedBorderColor = NexBorder,
                focusedTextColor = NexTextPrimary,
                unfocusedTextColor = NexTextPrimary
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("memory_search_input")
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Main View Switcher: Memories vs Change History
        TabRow(
            selectedTabIndex = selectedMainTab,
            containerColor = NexSurface,
            contentColor = NexCyanLight,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedMainTab]),
                    color = NexCyanLight,
                    height = 2.dp
                )
            },
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
        ) {
            Tab(
                selected = selectedMainTab == 0,
                onClick = { selectedMainTab = 0 },
                text = { Text("Active Memories (${filteredMemories.size})", fontSize = 12.sp, fontWeight = if (selectedMainTab == 0) FontWeight.Bold else FontWeight.Normal) },
                modifier = Modifier.testTag("active_memories_tab")
            )
            Tab(
                selected = selectedMainTab == 1,
                onClick = { selectedMainTab = 1 },
                text = { Text("Audit History (${changeHistory.size})", fontSize = 12.sp, fontWeight = if (selectedMainTab == 1) FontWeight.Bold else FontWeight.Normal) },
                modifier = Modifier.testTag("audit_history_tab")
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (selectedMainTab == 0) {
            // Category Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEachIndexed { index, cat ->
                    val isSelected = selectedCategoryIndex == index
                    val label = cat?.displayName ?: "All Categories"
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) NexCyanLight.copy(alpha = 0.2f) else NexSurfaceElevated)
                            .border(1.dp, if (isSelected) NexCyanLight else NexBorder, RoundedCornerShape(20.dp))
                            .clickable { selectedCategoryIndex = index }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("category_chip_${cat?.name ?: "ALL"}")
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) NexCyanLight else NexTextMuted,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons: Add Memory / Add Alias
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { showAddDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = NexCyanLight),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).testTag("add_memory_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Memory", tint = NexObsidian, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Memory", color = NexObsidian, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = { showAddAliasDialog = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NexIndigoLight),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NexIndigoLight),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).testTag("add_alias_button")
                ) {
                    Icon(Icons.Default.Apps, contentDescription = "Add App Alias", tint = NexIndigoLight, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add App Alias", color = NexIndigoLight, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Memories List
            if (filteredMemories.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = null,
                            tint = NexTextMuted.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isBlank() && selectedCategoryIndex == 0) "No Saved Memories" else "No Matching Memories Found",
                            color = NexTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (searchQuery.isBlank() && selectedCategoryIndex == 0) {
                                "NEX only remembers facts, preferences, and aliases when you explicitly command it.\nSay: 'Hey NEX, remember that I prefer Bengali' or tap 'Add Memory'."
                            } else {
                                "Try searching with different keywords or switch categories."
                            },
                            color = NexTextMuted,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f).testTag("memories_list")
                ) {
                    items(filteredMemories, key = { it.id }) { memory ->
                        MemoryItemCard(
                            memory = memory,
                            onView = {
                                viewingMemory = memory
                                unlockedConfidentialValue = null
                            },
                            onEdit = { editingMemory = memory },
                            onDelete = {
                                scope.launch {
                                    app.memoryRepository.deleteMemory(memory.id)
                                    Toast.makeText(context, "Memory '${memory.key}' deleted", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }
        } else {
            // Audit History Tab
            if (changeHistory.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.History, contentDescription = null, tint = NexTextMuted.copy(alpha = 0.5f), modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No Audit History Yet", color = NexTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("All memory insertions, updates, and deletions will be logged here.", color = NexTextMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f).testTag("audit_history_list")
                ) {
                    items(changeHistory, key = { it.id }) { history ->
                        AuditHistoryCard(history)
                    }
                }
            }
        }
    }

    // ==========================================
    // DIALOGS & OVERLAYS
    // ==========================================

    // 1. Add / Edit Memory Dialog
    if (showAddDialog || editingMemory != null) {
        AddEditMemoryDialog(
            existing = editingMemory,
            onDismiss = {
                showAddDialog = false
                editingMemory = null
            },
            onSave = { candidate, retention ->
                scope.launch {
                    val result = app.memoryRepository.saveCandidate(
                        candidate = candidate,
                        explicitConsentGiven = true,
                        retentionPolicy = retention
                    )
                    when (result) {
                        is MemoryWriteResult.Success -> {
                            Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                        }
                        is MemoryWriteResult.Rejected -> {
                            Toast.makeText(context, "Rejected: ${result.reason}", Toast.LENGTH_LONG).show()
                        }
                        is MemoryWriteResult.Error -> {
                            Toast.makeText(context, "Error: ${result.message}", Toast.LENGTH_LONG).show()
                        }
                        is MemoryWriteResult.Conflict -> {
                            Toast.makeText(context, "Conflict: ${result.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                    showAddDialog = false
                    editingMemory = null
                }
            }
        )
    }

    // 2. Add App Alias Dialog (Installed Apps Selector)
    if (showAddAliasDialog) {
        AddAppAliasDialog(
            installedApps = installedApps,
            onDismiss = { showAddAliasDialog = false },
            onSave = { alias, expansion ->
                scope.launch {
                    val candidate = MemoryCandidate(
                        action = MemoryCandidateAction.SAVE,
                        category = MemoryCategory.APP_ALIASES,
                        key = alias,
                        value = expansion,
                        sensitivity = MemorySensitivity.NORMAL,
                        reason = "User created app alias"
                    )
                    app.memoryRepository.saveCandidate(candidate, explicitConsentGiven = true)
                    Toast.makeText(context, "Alias '$alias' -> '$expansion' saved", Toast.LENGTH_SHORT).show()
                    showAddAliasDialog = false
                }
            }
        )
    }

    // 3. View Memory Details Dialog (with Biometric Level 2 Unlock)
    if (viewingMemory != null) {
        val mem = viewingMemory!!
        MemoryDetailDialog(
            memory = mem,
            unlockedValue = unlockedConfidentialValue,
            onRequestUnlock = {
                val activity = (context as? androidx.fragment.app.FragmentActivity)
                if (activity != null) {
                    scope.launch {
                        val authResult = app.securityManager.biometricAuthManager.authenticate(
                            activity = activity,
                            title = "Unlock Confidential Memory",
                            subtitle = "Owner Identity Verification",
                            description = "Biometric confirmation is required to inspect confidential memory '${mem.key}'."
                        )
                        if (authResult is BiometricAuthResult.Success) {
                            val fullMem = app.memoryRepository.getMemoryById(mem.id, includeConfidential = true)
                            unlockedConfidentialValue = fullMem?.value
                        } else {
                            Toast.makeText(context, "Biometric authentication failed or cancelled", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(context, "Host activity not found for biometric prompt", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = {
                viewingMemory = null
                unlockedConfidentialValue = null
            }
        )
    }

    // 4. Clear All Memories Confirmation Dialog
    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("Clear All Memories?", color = NexTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This will permanently delete all encrypted user facts, preferences, and custom aliases from your device. This operation cannot be undone.",
                    color = NexTextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val count = app.memoryRepository.clearAllMemories()
                            Toast.makeText(context, "Cleared $count memories", Toast.LENGTH_SHORT).show()
                            showClearAllDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NexRose),
                    modifier = Modifier.testTag("confirm_clear_all_button")
                ) {
                    Text("Clear All", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("Cancel", color = NexTextMuted)
                }
            },
            containerColor = NexSurfaceElevated
        )
    }

    // 5. Retention Policy Dialog
    if (showRetentionDialog) {
        AlertDialog(
            onDismissRequest = { showRetentionDialog = false },
            title = { Text("Memory Retention & Cleanup", color = NexTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "NEX automatically preserves permanent memories while enforcing expiration on temporary task contexts and scheduled records.",
                        color = NexTextSecondary,
                        fontSize = 12.sp
                    )
                    if (retentionFeedbackMessage != null) {
                        Text(
                            retentionFeedbackMessage!!,
                            color = NexEmerald,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val result = app.memoryRepository.retentionManager.cleanupExpiredMemories()
                            retentionFeedbackMessage = if (result.deletedCount > 0) {
                                "Cleanup complete: ${result.deletedCount} expired memories purged from Room DB."
                            } else {
                                "Database healthy: No expired records found."
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NexIndigoLight),
                    modifier = Modifier.testTag("trigger_retention_cleanup_button")
                ) {
                    Text("Purge Expired Memories Now", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRetentionDialog = false
                    retentionFeedbackMessage = null
                }) {
                    Text("Close", color = NexTextMuted)
                }
            },
            containerColor = NexSurfaceElevated
        )
    }

    // 6. Export Dialog
    if (showExportDialog && exportedJsonContent != null) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export Approved Memories", color = NexTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Encrypted JSON Backup Generated:", color = NexTextSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .background(NexObsidian, RoundedCornerShape(8.dp))
                            .border(1.dp, NexBorder, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = exportedJsonContent!!,
                            color = NexCyanLight,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            modifier = Modifier.verticalScroll(rememberScrollState())
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showExportDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = NexCyanLight)
                ) {
                    Text("Done", color = NexObsidian, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = NexSurfaceElevated
        )
    }
}

// ==========================================
// SUB-COMPONENTS & CARDS
// ==========================================

@Composable
fun MemoryItemCard(
    memory: StructuredMemory,
    onView: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }

    NexCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onView() }
            .testTag("memory_card_${memory.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (memory.sensitivity == MemorySensitivity.STRICTLY_CONFIDENTIAL) {
                        Icon(Icons.Default.Lock, contentDescription = "Confidential", tint = NexRose, modifier = Modifier.size(14.dp))
                    } else if (memory.isEncrypted) {
                        Icon(Icons.Default.Security, contentDescription = "Encrypted", tint = NexCyanLight, modifier = Modifier.size(14.dp))
                    }
                    Text(
                        text = memory.key,
                        color = NexTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                NexBadge(
                    text = memory.category.displayName,
                    color = when (memory.category) {
                        MemoryCategory.USER_PREFERENCES -> NexCyanLight
                        MemoryCategory.APP_ALIASES -> NexIndigoLight
                        MemoryCategory.USER_PROVIDED_FACTS -> NexEmerald
                        MemoryCategory.WORKFLOWS -> NexAmber
                        else -> NexTextMuted
                    }
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = memory.value,
                color = NexTextSecondary,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Updated ${dateFormat.format(Date(memory.updatedAt))}",
                    color = NexTextMuted,
                    fontSize = 10.sp
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(28.dp).testTag("edit_memory_${memory.id}")) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = NexCyanLight, modifier = Modifier.size(15.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp).testTag("delete_memory_${memory.id}")) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = NexRose, modifier = Modifier.size(15.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun AuditHistoryCard(history: MemoryChangeHistoryEntity) {
    val dateFormat = remember { SimpleDateFormat("MMM d, h:mm:ss a", Locale.getDefault()) }

    NexCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(12.dp).fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    NexBadge(
                        text = history.action,
                        color = when (history.action) {
                            "INSERT" -> NexEmerald
                            "UPDATE" -> NexCyanLight
                            "DELETE", "DELETE_CATEGORY", "CLEAR_ALL" -> NexRose
                            else -> NexAmber
                        }
                    )
                    Text(
                        text = history.memoryKey,
                        color = NexTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = history.changeSummary,
                    color = NexTextSecondary,
                    fontSize = 11.sp
                )
            }
            Text(
                text = dateFormat.format(Date(history.timestamp)),
                color = NexTextMuted,
                fontSize = 10.sp
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditMemoryDialog(
    existing: StructuredMemory?,
    onDismiss: () -> Unit,
    onSave: (MemoryCandidate, MemoryRetentionPolicy) -> Unit
) {
    var key by remember { mutableStateOf(existing?.key ?: "") }
    var value by remember { mutableStateOf(existing?.value ?: "") }
    var selectedCategory by remember { mutableStateOf(existing?.category ?: MemoryCategory.USER_PROVIDED_FACTS) }
    var selectedSensitivity by remember { mutableStateOf(existing?.sensitivity ?: MemorySensitivity.NORMAL) }
    var selectedRetention by remember { mutableStateOf(MemoryRetentionPolicy.PERMANENT) }

    var categoryExpanded by remember { mutableStateOf(false) }
    var sensitivityExpanded by remember { mutableStateOf(false) }
    var retentionExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (existing != null) "Edit Memory Record" else "Add Memory Record",
                color = NexTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                // Key Input
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("Memory Key / Topic", color = NexTextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = NexSurface,
                        unfocusedContainerColor = NexSurface,
                        focusedBorderColor = NexCyanLight,
                        unfocusedBorderColor = NexBorder,
                        focusedTextColor = NexTextPrimary,
                        unfocusedTextColor = NexTextPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().testTag("memory_key_input")
                )

                // Value Input
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("Memory Content / Fact", color = NexTextMuted) },
                    minLines = 2,
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = NexSurface,
                        unfocusedContainerColor = NexSurface,
                        focusedBorderColor = NexCyanLight,
                        unfocusedBorderColor = NexBorder,
                        focusedTextColor = NexTextPrimary,
                        unfocusedTextColor = NexTextPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().testTag("memory_value_input")
                )

                // Category Dropdown
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = !categoryExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedCategory.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category", color = NexTextMuted) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = NexSurface,
                            unfocusedContainerColor = NexSurface,
                            focusedBorderColor = NexCyanLight,
                            unfocusedBorderColor = NexBorder,
                            focusedTextColor = NexTextPrimary,
                            unfocusedTextColor = NexTextPrimary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false },
                        containerColor = NexSurfaceElevated
                    ) {
                        MemoryCategory.entries.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.displayName, color = NexTextPrimary, fontSize = 13.sp) },
                                onClick = {
                                    selectedCategory = cat
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }

                // Sensitivity Dropdown
                ExposedDropdownMenuBox(
                    expanded = sensitivityExpanded,
                    onExpandedChange = { sensitivityExpanded = !sensitivityExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedSensitivity.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Sensitivity & Encryption", color = NexTextMuted) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sensitivityExpanded) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = NexSurface,
                            unfocusedContainerColor = NexSurface,
                            focusedBorderColor = NexCyanLight,
                            unfocusedBorderColor = NexBorder,
                            focusedTextColor = NexTextPrimary,
                            unfocusedTextColor = NexTextPrimary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = sensitivityExpanded,
                        onDismissRequest = { sensitivityExpanded = false },
                        containerColor = NexSurfaceElevated
                    ) {
                        MemorySensitivity.entries.forEach { sens ->
                            DropdownMenuItem(
                                text = { Text(sens.displayName, color = NexTextPrimary, fontSize = 13.sp) },
                                onClick = {
                                    selectedSensitivity = sens
                                    sensitivityExpanded = false
                                }
                            )
                        }
                    }
                }

                // Retention Duration Dropdown
                ExposedDropdownMenuBox(
                    expanded = retentionExpanded,
                    onExpandedChange = { retentionExpanded = !retentionExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedRetention.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Retention Duration", color = NexTextMuted) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = retentionExpanded) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = NexSurface,
                            unfocusedContainerColor = NexSurface,
                            focusedBorderColor = NexCyanLight,
                            unfocusedBorderColor = NexBorder,
                            focusedTextColor = NexTextPrimary,
                            unfocusedTextColor = NexTextPrimary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = retentionExpanded,
                        onDismissRequest = { retentionExpanded = false },
                        containerColor = NexSurfaceElevated
                    ) {
                        MemoryRetentionPolicy.entries.forEach { policy ->
                            DropdownMenuItem(
                                text = { Text(policy.displayName, color = NexTextPrimary, fontSize = 13.sp) },
                                onClick = {
                                    selectedRetention = policy
                                    retentionExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (key.isNotBlank() && value.isNotBlank()) {
                        val candidate = MemoryCandidate(
                            action = if (existing != null) MemoryCandidateAction.UPDATE else MemoryCandidateAction.SAVE,
                            category = selectedCategory,
                            key = key.trim(),
                            value = value.trim(),
                            sensitivity = selectedSensitivity,
                            reason = "Manual user input via Memory Center"
                        )
                        onSave(candidate, selectedRetention)
                    }
                },
                enabled = key.isNotBlank() && value.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = NexCyanLight),
                modifier = Modifier.testTag("save_memory_confirm_button")
            ) {
                Text("Save to Memory", color = NexObsidian, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = NexTextMuted)
            }
        },
        containerColor = NexSurfaceElevated
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAppAliasDialog(
    installedApps: List<com.example.automation.appcontrol.InstalledApp>,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var alias by remember { mutableStateOf("") }
    var selectedApp by remember { mutableStateOf<com.example.automation.appcontrol.InstalledApp?>(null) }
    var appDropdownExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Create App Alias", color = NexTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Define a custom spoken shorthand for an installed app on your device (e.g. 'YT' for 'YouTube').",
                    color = NexTextSecondary,
                    fontSize = 12.sp
                )

                OutlinedTextField(
                    value = alias,
                    onValueChange = { alias = it },
                    label = { Text("Spoken Alias (e.g. YT, Work, Browser)", color = NexTextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = NexSurface,
                        unfocusedContainerColor = NexSurface,
                        focusedBorderColor = NexCyanLight,
                        unfocusedBorderColor = NexBorder,
                        focusedTextColor = NexTextPrimary,
                        unfocusedTextColor = NexTextPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().testTag("alias_key_input")
                )

                ExposedDropdownMenuBox(
                    expanded = appDropdownExpanded,
                    onExpandedChange = { appDropdownExpanded = !appDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedApp?.applicationLabel ?: "Select Target Installed App",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Target Application", color = NexTextMuted) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = appDropdownExpanded) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = NexSurface,
                            unfocusedContainerColor = NexSurface,
                            focusedBorderColor = NexCyanLight,
                            unfocusedBorderColor = NexBorder,
                            focusedTextColor = NexTextPrimary,
                            unfocusedTextColor = NexTextPrimary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = appDropdownExpanded,
                        onDismissRequest = { appDropdownExpanded = false },
                        containerColor = NexSurfaceElevated
                    ) {
                        installedApps.forEach { app ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(app.applicationLabel, color = NexTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(app.packageName, color = NexTextMuted, fontSize = 10.sp)
                                    }
                                },
                                onClick = {
                                    selectedApp = app
                                    appDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (alias.isNotBlank() && selectedApp != null) {
                        onSave(alias.trim(), selectedApp!!.applicationLabel)
                    }
                },
                enabled = alias.isNotBlank() && selectedApp != null,
                colors = ButtonDefaults.buttonColors(containerColor = NexIndigoLight),
                modifier = Modifier.testTag("confirm_save_alias_button")
            ) {
                Text("Save Alias", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = NexTextMuted)
            }
        },
        containerColor = NexSurfaceElevated
    )
}

@Composable
fun MemoryDetailDialog(
    memory: StructuredMemory,
    unlockedValue: String?,
    onRequestUnlock: () -> Unit,
    onDismiss: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    val displayValue = unlockedValue ?: memory.value

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Info, contentDescription = null, tint = NexCyanLight)
                Text("Memory Record Details", color = NexTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                DetailRow("Key / Topic", memory.key)
                DetailRow("Category", memory.category.displayName)
                DetailRow("Sensitivity", memory.sensitivity.displayName)
                DetailRow("Encrypted", if (memory.isEncrypted) "Yes (AES-256-GCM)" else "No")
                DetailRow("Consent Status", memory.consentStatus.name)
                DetailRow("Source", memory.source.name)
                DetailRow("Created", dateFormat.format(Date(memory.createdAt)))
                DetailRow("Last Updated", dateFormat.format(Date(memory.updatedAt)))

                Spacer(modifier = Modifier.height(6.dp))
                Text("Content / Value:", color = NexTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NexObsidian, RoundedCornerShape(8.dp))
                        .border(1.dp, NexBorder, RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = displayValue,
                        color = NexTextPrimary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                if (memory.sensitivity == MemorySensitivity.STRICTLY_CONFIDENTIAL && unlockedValue == null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = onRequestUnlock,
                        colors = ButtonDefaults.buttonColors(containerColor = NexIndigoLight),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().testTag("unlock_confidential_button")
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Authenticate Biometric to Reveal", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = NexCyanLight)
            ) {
                Text("Close", color = NexObsidian, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = NexSurfaceElevated
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = NexTextMuted, fontSize = 11.sp)
        Text(value, color = NexTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
