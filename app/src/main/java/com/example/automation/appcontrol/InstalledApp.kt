package com.example.automation.appcontrol

import android.graphics.drawable.Drawable

data class InstalledApp(
    val packageName: String,
    val applicationLabel: String,
    val launchable: Boolean,
    val isSystemApp: Boolean = false,
    val versionName: String? = null,
    val capabilities: Set<AppCapability> = setOf(
        AppCapability.CAN_LAUNCH,
        AppCapability.CAN_NAVIGATE_UI,
        AppCapability.CAN_OBSERVE_SCREEN
    ),
    val icon: Drawable? = null
)
