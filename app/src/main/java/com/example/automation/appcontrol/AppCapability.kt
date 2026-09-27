package com.example.automation.appcontrol

enum class AppCapability {
    CAN_LAUNCH,
    CAN_SEARCH,
    CAN_PLAY_MEDIA,
    CAN_SHARE,
    CAN_OPEN_SETTINGS,
    CAN_NAVIGATE_UI,
    CAN_ACCEPT_TEXT,
    CAN_SCROLL,
    CAN_DEEP_LINK,
    CAN_OBSERVE_SCREEN;

    val displayName: String
        get() = when (this) {
            CAN_LAUNCH -> "Launch"
            CAN_SEARCH -> "Search"
            CAN_PLAY_MEDIA -> "Play Media"
            CAN_SHARE -> "Share"
            CAN_OPEN_SETTINGS -> "Settings"
            CAN_NAVIGATE_UI -> "Navigate UI"
            CAN_ACCEPT_TEXT -> "Text Input"
            CAN_SCROLL -> "Scroll"
            CAN_DEEP_LINK -> "Deep Link"
            CAN_OBSERVE_SCREEN -> "Observe Screen"
        }
}
