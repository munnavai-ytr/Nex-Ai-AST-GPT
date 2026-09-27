package com.example.automation.appcontrol

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import com.example.accessibility.AccessibilityController
import kotlinx.coroutines.delay

class AppLauncher(
    private val context: Context,
    private val accessibilityController: AccessibilityController
) {

    suspend fun openApplication(
        packageName: String,
        appLabel: String? = null,
        verifyForeground: Boolean = true
    ): AppLaunchResult {
        val pm = context.packageManager
        val targetPkg = packageName.trim()

        // 1. Verify installation
        val isInstalled = try {
            pm.getPackageInfo(targetPkg, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }

        if (!isInstalled) {
            return AppLaunchResult.NotInstalled(target = targetPkg)
        }

        // 2. Resolve launch intent
        var intent = pm.getLaunchIntentForPackage(targetPkg)

        // Special handling for Settings or system apps
        if (intent == null && targetPkg == "com.android.settings") {
            intent = Intent(Settings.ACTION_SETTINGS)
        }

        if (intent == null) {
            return AppLaunchResult.NoLaunchIntent(packageName = targetPkg)
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        // 3. Dispatch launch
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            return AppLaunchResult.LaunchFailed(
                packageName = targetPkg,
                reason = e.message ?: "startActivity threw an exception"
            )
        }

        // 4. Foreground verification
        if (!verifyForeground) {
            return AppLaunchResult.Success(
                packageName = targetPkg,
                appLabel = appLabel ?: targetPkg,
                foregroundVerified = false,
                message = "Application launch intent dispatched for $targetPkg"
            )
        }

        if (!accessibilityController.isAccessibilityActive()) {
            return AppLaunchResult.Success(
                packageName = targetPkg,
                appLabel = appLabel ?: targetPkg,
                foregroundVerified = false,
                message = "Launched $targetPkg. Accessibility service is inactive, so foreground state could not be verified."
            )
        }

        // Poll foreground state up to 3 times to accommodate OS animation delay
        var verified = false
        var currentPkg: String? = null

        for (attempt in 1..4) {
            delay(attempt * 250L) // 250ms, 500ms, 750ms, 1000ms
            currentPkg = accessibilityController.getActivePackage()
            if (currentPkg.equals(targetPkg, ignoreCase = true)) {
                verified = true
                break
            }
        }

        return if (verified) {
            AppLaunchResult.Success(
                packageName = targetPkg,
                appLabel = appLabel ?: targetPkg,
                foregroundVerified = true,
                message = "Successfully opened and verified foreground application: ${appLabel ?: targetPkg}"
            )
        } else {
            AppLaunchResult.ForegroundVerificationFailed(
                expectedPackage = targetPkg,
                actualPackage = currentPkg
            )
        }
    }
}
