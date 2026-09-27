package com.example.automation.appcontrol

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class InstalledAppRegistry(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val _installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val installedApps: StateFlow<List<InstalledApp>> = _installedApps.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    suspend fun refresh(): List<InstalledApp> = withContext(ioDispatcher) {
        _isRefreshing.value = true
        try {
            val pm = context.packageManager
            val discovered = mutableMapOf<String, InstalledApp>()

            // 1. Query all launcher activities
            val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = try {
                pm.queryIntentActivities(launcherIntent, 0)
            } catch (_: Exception) {
                emptyList()
            }

            for (resolveInfo in resolveInfos) {
                val pkg = resolveInfo.activityInfo?.packageName ?: continue
                if (pkg == context.packageName) continue // Don't list NEX inside NEX

                val label = try {
                    resolveInfo.loadLabel(pm).toString()
                } catch (_: Exception) {
                    pkg
                }

                val icon = try {
                    resolveInfo.loadIcon(pm)
                } catch (_: Exception) {
                    null
                }

                val isSystem = try {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                } catch (_: Exception) {
                    false
                }

                val versionName = try {
                    pm.getPackageInfo(pkg, 0).versionName
                } catch (_: Exception) {
                    null
                }

                val capabilities = detectCapabilities(pkg, label)

                discovered[pkg] = InstalledApp(
                    packageName = pkg,
                    applicationLabel = label,
                    launchable = true,
                    isSystemApp = isSystem,
                    versionName = versionName,
                    capabilities = capabilities,
                    icon = icon
                )
            }

            // 2. Query well-known system packages (e.g. Settings) if not returned by launcher intent
            val standardPackages = listOf(
                "com.android.settings" to "Settings",
                "com.google.android.youtube" to "YouTube",
                "com.android.chrome" to "Chrome"
            )

            for ((stdPkg, stdLabel) in standardPackages) {
                if (!discovered.containsKey(stdPkg)) {
                    try {
                        val appInfo = pm.getApplicationInfo(stdPkg, 0)
                        val label = try {
                            pm.getApplicationLabel(appInfo).toString()
                        } catch (_: Exception) {
                            stdLabel
                        }
                        val icon = try {
                            pm.getApplicationIcon(appInfo)
                        } catch (_: Exception) {
                            null
                        }
                        val versionName = try {
                            pm.getPackageInfo(stdPkg, 0).versionName
                        } catch (_: Exception) {
                            null
                        }
                        val isLaunchable = pm.getLaunchIntentForPackage(stdPkg) != null || stdPkg == "com.android.settings"
                        val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                        discovered[stdPkg] = InstalledApp(
                            packageName = stdPkg,
                            applicationLabel = label,
                            launchable = isLaunchable,
                            isSystemApp = isSystem,
                            versionName = versionName,
                            capabilities = detectCapabilities(stdPkg, label),
                            icon = icon
                        )
                    } catch (_: PackageManager.NameNotFoundException) {
                        // Package not installed on this device, skip
                    } catch (_: Exception) {
                        // Ignore
                    }
                }
            }

            val resultList = discovered.values.sortedBy { it.applicationLabel.lowercase() }
            _installedApps.value = resultList
            resultList
        } finally {
            _isRefreshing.value = false
        }
    }

    fun getInstalledApps(): List<InstalledApp> {
        return _installedApps.value
    }

    fun findByPackage(packageName: String): InstalledApp? {
        val trimmed = packageName.trim()
        return _installedApps.value.firstOrNull { it.packageName.equals(trimmed, ignoreCase = true) }
    }

    fun findByLabel(label: String): InstalledApp? {
        val trimmed = label.trim()
        return _installedApps.value.firstOrNull { it.applicationLabel.equals(trimmed, ignoreCase = true) }
    }

    fun isInstalled(packageName: String): Boolean {
        val trimmed = packageName.trim()
        if (_installedApps.value.any { it.packageName.equals(trimmed, ignoreCase = true) }) {
            return true
        }
        // Direct package manager check as fallback
        return try {
            context.packageManager.getPackageInfo(trimmed, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun detectCapabilities(packageName: String, label: String): Set<AppCapability> {
        val caps = mutableSetOf(
            AppCapability.CAN_LAUNCH,
            AppCapability.CAN_NAVIGATE_UI,
            AppCapability.CAN_OBSERVE_SCREEN
        )

        val pkgLower = packageName.lowercase()
        val labelLower = label.lowercase()

        // Search capabilities
        if (pkgLower.contains("youtube") || pkgLower.contains("chrome") ||
            pkgLower.contains("browser") || pkgLower.contains("settings") ||
            pkgLower.contains("maps") || pkgLower.contains("vending") ||
            labelLower.contains("search") || labelLower.contains("store")
        ) {
            caps.add(AppCapability.CAN_SEARCH)
        }

        // Media capabilities
        if (pkgLower.contains("youtube") || pkgLower.contains("music") ||
            pkgLower.contains("spotify") || pkgLower.contains("media") ||
            pkgLower.contains("player") || pkgLower.contains("video") ||
            pkgLower.contains("audio") || pkgLower.contains("podcast")
        ) {
            caps.add(AppCapability.CAN_PLAY_MEDIA)
        }

        // Settings capabilities
        if (pkgLower.contains("settings") || labelLower.contains("settings")) {
            caps.add(AppCapability.CAN_OPEN_SETTINGS)
        }

        // Text & Input
        if (!pkgLower.contains("clock") && !pkgLower.contains("calculator")) {
            caps.add(AppCapability.CAN_ACCEPT_TEXT)
        }

        // Scrolling
        caps.add(AppCapability.CAN_SCROLL)

        // Deep linking / Sharing
        if (pkgLower.contains("youtube") || pkgLower.contains("chrome") ||
            pkgLower.contains("browser") || pkgLower.contains("maps") ||
            pkgLower.contains("gmail") || pkgLower.contains("twitter") ||
            pkgLower.contains("messaging")
        ) {
            caps.add(AppCapability.CAN_SHARE)
            caps.add(AppCapability.CAN_DEEP_LINK)
        }

        return caps
    }
}
