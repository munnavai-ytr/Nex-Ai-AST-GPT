package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.NexApplication
import com.example.automation.appcontrol.AppCapability
import com.example.automation.appcontrol.AppLaunchResult
import com.example.automation.appcontrol.InstalledApp
import com.example.memory.entities.CommandAliasEntity
import com.example.ui.theme.NexBorder
import com.example.ui.theme.NexCyanLight
import com.example.ui.theme.NexEmerald
import com.example.ui.theme.NexIndigoLight
import com.example.ui.theme.NexObsidian
import com.example.ui.theme.NexRose
import com.example.ui.theme.NexSurface
import com.example.ui.theme.NexSurfaceElevated
import com.example.ui.theme.NexTextMuted
import com.example.ui.theme.NexTextPrimary
import com.example.ui.theme.NexTextSecondary
import com.example.ui.theme.NexAmber
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsControlScreen(
    navController: NavController,
    app: NexApplication = NexApplication.instance
) {
    val coroutineScope = rememberCoroutineScope()
    val installedApps by app.installedAppRegistry.installedApps.collectAsStateWithLifecycle()
    val isRefreshing by app.installedAppRegistry.isRefreshing.collectAsStateWithLifecycle()
    val aliases by app.memoryRepository.aliases.collectAsStateWithLifecycle(initialValue = emptyList())
    val activeProviders = remember { app.appAutomationRegistry.getAllProviders() }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var testLaunchResult by remember { mutableStateOf<AppLaunchResult?>(null) }
    var isLaunchingApp by remember { mutableStateOf(false) }

    // Dialog state for adding alias
    var showAddAliasDialog by remember { mutableStateOf(false) }
    var newAliasText by remember { mutableStateOf("") }
    var newExpansionText by remember { mutableStateOf("") }
    var newDescriptionText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (installedApps.isEmpty()) {
            app.installedAppRegistry.refresh()
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(NexObsidian),
        containerColor = NexObsidian,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Apps & Integration",
                            color = NexTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Real Application Control & Perception Layer",
                            color = NexCyanLight,
                            fontSize = 12.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.testTag("apps_control_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = NexTextPrimary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                app.installedAppRegistry.refresh()
                            }
                        },
                        enabled = !isRefreshing,
                        modifier = Modifier.testTag("refresh_apps_button")
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = NexCyanLight,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh installed apps",
                                tint = NexCyanLight
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NexSurface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Header stats panel
            StatsOverviewBar(
                totalApps = installedApps.size,
                providersCount = activeProviders.size,
                aliasesCount = aliases.size,
                isRefreshing = isRefreshing
            )

            // Tabs
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = NexSurface,
                contentColor = NexCyanLight,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = NexCyanLight
                    )
                }
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = {
                        Text(
                            "Installed Apps (${installedApps.size})",
                            fontSize = 13.sp,
                            fontWeight = if (selectedTabIndex == 0) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    modifier = Modifier.testTag("tab_installed_apps")
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = {
                        Text(
                            "App Aliases (${aliases.size})",
                            fontSize = 13.sp,
                            fontWeight = if (selectedTabIndex == 1) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    modifier = Modifier.testTag("tab_app_aliases")
                )
                Tab(
                    selected = selectedTabIndex == 2,
                    onClick = { selectedTabIndex = 2 },
                    text = {
                        Text(
                            "Providers (${activeProviders.size})",
                            fontSize = 13.sp,
                            fontWeight = if (selectedTabIndex == 2) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    modifier = Modifier.testTag("tab_active_providers")
                )
            }

            when (selectedTabIndex) {
                0 -> {
                    InstalledAppsTabContent(
                        apps = installedApps,
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        isLaunching = isLaunchingApp,
                        onTestLaunch = { appItem ->
                            isLaunchingApp = true
                            coroutineScope.launch {
                                val result = app.appLauncher.openApplication(
                                    packageName = appItem.packageName,
                                    appLabel = appItem.applicationLabel,
                                    verifyForeground = true
                                )
                                testLaunchResult = result
                                isLaunchingApp = false
                            }
                        }
                    )
                }
                1 -> {
                    AppAliasesTabContent(
                        aliases = aliases,
                        installedApps = installedApps,
                        onAddAliasClick = { showAddAliasDialog = true },
                        onDeleteAlias = { alias ->
                            coroutineScope.launch {
                                app.memoryRepository.deleteAlias(alias)
                            }
                        },
                        onQuickAdd = { alias, expansion ->
                            coroutineScope.launch {
                                app.memoryRepository.saveAlias(alias, expansion, "Configured via quick setup")
                            }
                        }
                    )
                }
                2 -> {
                    ProvidersTabContent(providers = activeProviders)
                }
            }
        }
    }

    // Modal Dialog for Launch Result
    testLaunchResult?.let { result ->
        AlertDialog(
            onDismissRequest = { testLaunchResult = null },
            containerColor = NexSurfaceElevated,
            icon = {
                when (result) {
                    is AppLaunchResult.Success -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Success",
                            tint = NexEmerald,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    is AppLaunchResult.ForegroundVerificationFailed -> {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Warning",
                            tint = NexAmber,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    else -> {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = "Error",
                            tint = NexRose,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            },
            title = {
                Text(
                    text = when (result) {
                        is AppLaunchResult.Success -> "Launch & Foreground Verified"
                        is AppLaunchResult.ForegroundVerificationFailed -> "Foreground Verification Failed"
                        is AppLaunchResult.NotInstalled -> "App Not Installed"
                        is AppLaunchResult.NoLaunchIntent -> "No Launch Intent"
                        is AppLaunchResult.LaunchFailed -> "Launch Failed"
                    },
                    color = NexTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = when (result) {
                            is AppLaunchResult.Success -> result.message
                            is AppLaunchResult.ForegroundVerificationFailed -> result.message
                            is AppLaunchResult.NotInstalled -> result.message
                            is AppLaunchResult.NoLaunchIntent -> result.message
                            is AppLaunchResult.LaunchFailed -> result.message
                        },
                        color = NexTextSecondary,
                        fontSize = 14.sp
                    )

                    if (result is AppLaunchResult.Success) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Verification status:",
                                color = NexTextMuted,
                                fontSize = 12.sp
                            )
                            Text(
                                text = if (result.foregroundVerified) "ACTIVE FOREGROUND CONFIRMED" else "INTENT DISPATCHED (ACCESSIBILITY INACTIVE)",
                                color = if (result.foregroundVerified) NexEmerald else NexCyanLight,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { testLaunchResult = null },
                    colors = ButtonDefaults.buttonColors(containerColor = NexCyanLight)
                ) {
                    Text("OK", color = NexObsidian, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Modal Dialog for Adding Alias
    if (showAddAliasDialog) {
        AlertDialog(
            onDismissRequest = { showAddAliasDialog = false },
            containerColor = NexSurfaceElevated,
            title = {
                Text(
                    "Create App Alias",
                    color = NexTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Define a shorthand or spoken voice alias for an installed application (e.g. 'yt' for YouTube).",
                        color = NexTextSecondary,
                        fontSize = 13.sp
                    )

                    OutlinedTextField(
                        value = newAliasText,
                        onValueChange = { newAliasText = it },
                        label = { Text("Alias Phrase (e.g. 'yt' or 'work')") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NexCyanLight,
                            unfocusedBorderColor = NexBorder,
                            focusedTextColor = NexTextPrimary,
                            unfocusedTextColor = NexTextPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("alias_input_field")
                    )

                    OutlinedTextField(
                        value = newExpansionText,
                        onValueChange = { newExpansionText = it },
                        label = { Text("Target Application Label or Package") },
                        placeholder = { Text("e.g. YouTube or com.android.chrome") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NexCyanLight,
                            unfocusedBorderColor = NexBorder,
                            focusedTextColor = NexTextPrimary,
                            unfocusedTextColor = NexTextPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("expansion_input_field")
                    )

                    OutlinedTextField(
                        value = newDescriptionText,
                        onValueChange = { newDescriptionText = it },
                        label = { Text("Description (Optional)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NexCyanLight,
                            unfocusedBorderColor = NexBorder,
                            focusedTextColor = NexTextPrimary,
                            unfocusedTextColor = NexTextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newAliasText.isNotBlank() && newExpansionText.isNotBlank()) {
                            coroutineScope.launch {
                                app.memoryRepository.saveAlias(
                                    alias = newAliasText.trim(),
                                    expansion = newExpansionText.trim(),
                                    description = newDescriptionText.trim()
                                )
                                showAddAliasDialog = false
                                newAliasText = ""
                                newExpansionText = ""
                                newDescriptionText = ""
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NexCyanLight),
                    enabled = newAliasText.isNotBlank() && newExpansionText.isNotBlank(),
                    modifier = Modifier.testTag("save_alias_button")
                ) {
                    Text("Save Alias", color = NexObsidian, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddAliasDialog = false }) {
                    Text("Cancel", color = NexTextMuted)
                }
            }
        )
    }
}

@Composable
private fun StatsOverviewBar(
    totalApps: Int,
    providersCount: Int,
    aliasesCount: Int,
    isRefreshing: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NexSurface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "ACTIVE CONTROL PERCEPTION",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = NexCyanLight,
                letterSpacing = 1.sp
            )
            Text(
                text = "$totalApps apps verified • $providersCount providers active",
                fontSize = 13.sp,
                color = NexTextSecondary
            )
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(NexSurfaceElevated)
                .border(1.dp, NexBorder, RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = "$aliasesCount Aliases",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = NexIndigoLight
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InstalledAppsTabContent(
    apps: List<InstalledApp>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    isLaunching: Boolean,
    onTestLaunch: (InstalledApp) -> Unit
) {
    val filteredApps = remember(apps, searchQuery) {
        if (searchQuery.isBlank()) {
            apps
        } else {
            val q = searchQuery.trim().lowercase()
            apps.filter {
                it.applicationLabel.lowercase().contains(q) ||
                        it.packageName.lowercase().contains(q)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Search installed applications...", color = NexTextMuted, fontSize = 13.sp) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = NexTextMuted
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear",
                            tint = NexTextMuted
                        )
                    }
                }
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NexCyanLight,
                unfocusedBorderColor = NexBorder,
                focusedContainerColor = NexSurfaceElevated,
                unfocusedContainerColor = NexSurface,
                focusedTextColor = NexTextPrimary,
                unfocusedTextColor = NexTextPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("app_search_field")
        )

        if (filteredApps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Apps,
                        contentDescription = "No apps",
                        tint = NexTextMuted,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (searchQuery.isBlank()) "No installed applications discovered" else "No apps matching '$searchQuery'",
                        color = NexTextSecondary,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredApps, key = { it.packageName }) { appItem ->
                    AppItemCard(
                        appItem = appItem,
                        isLaunching = isLaunching,
                        onTestLaunch = { onTestLaunch(appItem) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppItemCard(
    appItem: InstalledApp,
    isLaunching: Boolean,
    onTestLaunch: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(NexSurface)
            .border(1.dp, NexBorder, RoundedCornerShape(10.dp))
            .padding(14.dp)
            .testTag("app_card_${appItem.packageName}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App Avatar / Initial
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(NexSurfaceElevated)
                    .border(1.dp, NexCyanLight.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = appItem.applicationLabel.take(1).uppercase(),
                    color = NexCyanLight,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = appItem.applicationLabel,
                        color = NexTextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    if (appItem.isSystemApp) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(NexSurfaceElevated)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "SYSTEM",
                                color = NexTextMuted,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Text(
                    text = appItem.packageName,
                    color = NexTextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onTestLaunch,
                enabled = !isLaunching,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NexCyanLight.copy(alpha = 0.15f),
                    contentColor = NexCyanLight
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.testTag("test_launch_${appItem.packageName}")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Launch,
                    contentDescription = "Test launch",
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Launch",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Detected Capabilities Chips
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            appItem.capabilities.forEach { cap ->
                CapabilityBadge(capability = cap)
            }
        }
    }
}

@Composable
private fun CapabilityBadge(capability: AppCapability) {
    val color: Color
    val label: String
    when (capability) {
        AppCapability.CAN_LAUNCH -> {
            color = NexCyanLight
            label = "LAUNCH"
        }
        AppCapability.CAN_SEARCH -> {
            color = NexIndigoLight
            label = "SEARCH"
        }
        AppCapability.CAN_PLAY_MEDIA -> {
            color = Color(0xFFE91E63)
            label = "MEDIA"
        }
        AppCapability.CAN_OPEN_SETTINGS -> {
            color = NexAmber
            label = "SETTINGS"
        }
        AppCapability.CAN_NAVIGATE_UI -> {
            color = NexIndigoLight
            label = "NAVIGATE"
        }
        AppCapability.CAN_ACCEPT_TEXT -> {
            color = NexEmerald
            label = "TEXT INPUT"
        }
        AppCapability.CAN_SCROLL -> {
            color = Color(0xFF00BCD4)
            label = "SCROLL"
        }
        AppCapability.CAN_DEEP_LINK -> {
            color = Color(0xFF9C27B0)
            label = "DEEP LINK"
        }
        AppCapability.CAN_SHARE -> {
            color = NexAmber
            label = "SHARE"
        }
        AppCapability.CAN_OBSERVE_SCREEN -> {
            color = Color(0xFF64B5F6)
            label = "OBSERVE"
        }
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun AppAliasesTabContent(
    aliases: List<CommandAliasEntity>,
    installedApps: List<InstalledApp>,
    onAddAliasClick: () -> Unit,
    onDeleteAlias: (CommandAliasEntity) -> Unit,
    onQuickAdd: (alias: String, expansion: String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "OWNER DEFINED APP ALIASES",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = NexCyanLight,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Map custom voice phrases to installed target apps",
                        fontSize = 13.sp,
                        color = NexTextMuted
                    )
                }

                Button(
                    onClick = onAddAliasClick,
                    colors = ButtonDefaults.buttonColors(containerColor = NexCyanLight),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("create_alias_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add",
                        tint = NexObsidian,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add", color = NexObsidian, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        // Quick Preset Suggestions
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(NexSurface)
                    .border(1.dp, NexBorder, RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = "Recommended Quick Aliases",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = NexTextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val presets = listOf(
                        "yt" to "YouTube",
                        "browser" to "Chrome",
                        "settings" to "Settings"
                    )

                    presets.forEach { (alias, expansion) ->
                        val alreadyConfigured = aliases.any { it.alias.equals(alias, ignoreCase = true) }
                        Button(
                            onClick = { onQuickAdd(alias, expansion) },
                            enabled = !alreadyConfigured,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (alreadyConfigured) NexSurfaceElevated else NexIndigoLight.copy(alpha = 0.15f),
                                contentColor = if (alreadyConfigured) NexTextMuted else NexIndigoLight
                            ),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (alreadyConfigured) "✓ $alias" else "+ $alias",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        if (aliases.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No custom aliases configured yet.",
                            color = NexTextMuted,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap '+ Add' or a recommendation above to create your first alias.",
                            color = NexTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        } else {
            items(aliases, key = { it.id }) { alias ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(NexSurface)
                        .border(1.dp, NexBorder, RoundedCornerShape(8.dp))
                        .padding(14.dp)
                        .testTag("alias_item_${alias.alias}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "\"${alias.alias}\"",
                                color = NexCyanLight,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "→",
                                color = NexTextMuted,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = alias.expansion,
                                color = NexTextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }

                        if (alias.description.isNotBlank()) {
                            Text(
                                text = alias.description,
                                color = NexTextMuted,
                                fontSize = 11.sp
                            )
                        }

                        Text(
                            text = "Used ${alias.usageCount} times",
                            color = NexTextMuted,
                            fontSize = 10.sp
                        )
                    }

                    IconButton(
                        onClick = { onDeleteAlias(alias) },
                        modifier = Modifier.testTag("delete_alias_${alias.alias}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete alias",
                            tint = NexRose.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProvidersTabContent(
    providers: List<com.example.automation.appcontrol.AppAutomationProvider>
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text(
                    text = "REGISTERED AUTOMATION PROVIDERS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = NexCyanLight,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Modular providers providing native intents, semantic locators & workflows",
                    fontSize = 13.sp,
                    color = NexTextMuted
                )
            }
        }

        items(providers, key = { it.targetPackage }) { provider ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(NexSurface)
                    .border(1.dp, NexBorder, RoundedCornerShape(10.dp))
                    .padding(16.dp)
                    .testTag("provider_card_${provider.targetPackage}")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(NexCyanLight.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Extension,
                            contentDescription = null,
                            tint = NexCyanLight,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = provider.providerName,
                            color = NexTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = provider.targetPackage,
                            color = NexTextMuted,
                            fontSize = 11.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(NexEmerald.copy(alpha = 0.15f))
                            .border(1.dp, NexEmerald.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "ACTIVE",
                            color = NexEmerald,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Supported Capabilities:",
                    color = NexTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    provider.supportedCapabilities.forEach { cap ->
                        CapabilityBadge(capability = cap)
                    }
                }
            }
        }

        item {
            // Explanatory execution strategy card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(NexSurfaceElevated)
                    .border(1.dp, NexCyanLight.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    .padding(14.dp)
            ) {
                Text(
                    text = "NEX App Control Execution Tier Hierarchy",
                    color = NexCyanLight,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "1. Native Framework API (Home, Back, System)\n" +
                            "2. Android Intent Dispatch (PackageManager launch, Settings intents)\n" +
                            "3. App-Specific Supported Integration (YouTube, Chrome, Settings providers)\n" +
                            "4. Accessibility Semantic UI Interaction (ElementFinder by text/resourceId)\n" +
                            "5. Controlled Gesture Fallback (Coordinates only when semantic targets are absent)",
                    color = NexTextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
