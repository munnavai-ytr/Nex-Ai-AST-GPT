package com.example.automation.appcontrol

sealed class AppLaunchResult {
    data class Success(
        val packageName: String,
        val appLabel: String,
        val foregroundVerified: Boolean,
        val message: String
    ) : AppLaunchResult()

    data class NotInstalled(
        val target: String,
        val message: String = "Application is not installed on this device: $target"
    ) : AppLaunchResult()

    data class NoLaunchIntent(
        val packageName: String,
        val message: String = "No launchable intent found for package: $packageName"
    ) : AppLaunchResult()

    data class LaunchFailed(
        val packageName: String,
        val reason: String,
        val message: String = "Failed to launch $packageName: $reason"
    ) : AppLaunchResult()

    data class ForegroundVerificationFailed(
        val expectedPackage: String,
        val actualPackage: String?,
        val message: String = "Application was launched, but foreground verification failed (expected: $expectedPackage, actual: ${actualPackage ?: "none"})"
    ) : AppLaunchResult()
}
