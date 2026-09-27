package com.example.diagnostics.engine

import android.app.ActivityManager
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.security.keystore.KeyProperties
import com.example.diagnostics.model.DiagnosticCategory
import com.example.diagnostics.model.DiagnosticItem
import com.example.diagnostics.model.DiagnosticStatus
import com.example.diagnostics.model.SubsystemDiagnosticResult
import java.io.File
import java.security.KeyStore

class SystemDiagnosticsEngine(private val context: Context) {

    fun runDiagnostics(): SubsystemDiagnosticResult {
        val startTime = System.currentTimeMillis()
        val items = mutableListOf<DiagnosticItem>()

        // 1. OS & Device Profile
        items.add(checkDeviceInfo())

        // 2. RAM & Memory Info
        items.add(checkRamUsage())

        // 3. Storage Space
        items.add(checkStorageSpace())

        // 4. Battery & Power Status
        items.add(checkBatteryStatus())

        // 5. Network Connectivity
        items.add(checkNetworkConnectivity())

        // 6. Keystore & Hardware Security
        items.add(checkKeystoreSecurity())

        val overallStatus = when {
            items.any { it.status == DiagnosticStatus.FAIL } -> DiagnosticStatus.FAIL
            items.any { it.status == DiagnosticStatus.WARN } -> DiagnosticStatus.WARN
            else -> DiagnosticStatus.PASS
        }

        return SubsystemDiagnosticResult(
            category = DiagnosticCategory.SYSTEM,
            items = items,
            status = overallStatus,
            executionTimeMs = System.currentTimeMillis() - startTime
        )
    }

    private fun checkDeviceInfo(): DiagnosticItem {
        val model = Build.MODEL
        val manufacturer = Build.MANUFACTURER
        val brand = Build.BRAND
        val androidVer = Build.VERSION.RELEASE
        val sdkInt = Build.VERSION.SDK_INT
        val supportedAbis = Build.SUPPORTED_ABIS.joinToString(", ")

        val isModern = sdkInt >= 26
        return DiagnosticItem(
            id = "sys_device_info",
            title = "Device & Android OS Profile",
            category = DiagnosticCategory.SYSTEM,
            status = if (isModern) DiagnosticStatus.PASS else DiagnosticStatus.WARN,
            summary = "$manufacturer $model (Android $androidVer, API $sdkInt)",
            details = "Brand: $brand\nModel: $model\nOS Version: Android $androidVer (API $sdkInt)\nABIs: $supportedAbis\nFingerprint: ${Build.FINGERPRINT}"
        )
    }

    private fun checkRamUsage(): DiagnosticItem {
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(memInfo)

        val totalMemMb = memInfo.totalMem / (1024 * 1024)
        val availMemMb = memInfo.availMem / (1024 * 1024)
        val usedMemMb = totalMemMb - availMemMb
        val isLowMem = memInfo.lowMemory

        val status = if (isLowMem || (totalMemMb > 0 && availMemMb < 200)) {
            DiagnosticStatus.WARN
        } else {
            DiagnosticStatus.PASS
        }

        return DiagnosticItem(
            id = "sys_ram",
            title = "RAM & Memory Availability",
            category = DiagnosticCategory.SYSTEM,
            status = status,
            summary = "Avail: ${availMemMb}MB / Total: ${totalMemMb}MB (Used: ${usedMemMb}MB)",
            details = "Total RAM: ${totalMemMb}MB\nAvailable RAM: ${availMemMb}MB\nLow Memory Flag: $isLowMem\nThreshold: ${memInfo.threshold / (1024 * 1024)}MB"
        )
    }

    private fun checkStorageSpace(): DiagnosticItem {
        return try {
            val internalPath = Environment.getDataDirectory()
            val stat = StatFs(internalPath.path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong

            val totalBytes = totalBlocks * blockSize
            val freeBytes = availableBlocks * blockSize

            val totalGb = String.format("%.1f GB", totalBytes.toDouble() / (1024 * 1024 * 1024))
            val freeGb = String.format("%.1f GB", freeBytes.toDouble() / (1024 * 1024 * 1024))
            val isLowStorage = freeBytes < (500L * 1024 * 1024) // < 500 MB

            DiagnosticItem(
                id = "sys_storage",
                title = "Internal Storage Health",
                category = DiagnosticCategory.SYSTEM,
                status = if (isLowStorage) DiagnosticStatus.WARN else DiagnosticStatus.PASS,
                summary = "Free: $freeGb / Total: $totalGb",
                details = "Internal Storage Path: ${internalPath.absolutePath}\nTotal Space: $totalGb\nFree Space: $freeGb"
            )
        } catch (e: Exception) {
            DiagnosticItem(
                id = "sys_storage",
                title = "Internal Storage Health",
                category = DiagnosticCategory.SYSTEM,
                status = DiagnosticStatus.INFO,
                summary = "Storage checked",
                details = "Details: ${e.message}"
            )
        }
    }

    private fun checkBatteryStatus(): DiagnosticItem {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            context.registerReceiver(null, ifilter)
        }

        val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct: Float = if (level >= 0 && scale > 0) (level * 100 / scale.toFloat()) else -1f

        val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging: Boolean = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val chargePlug: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        val isUsbCharge: Boolean = chargePlug == BatteryManager.BATTERY_PLUGGED_USB
        val isAcCharge: Boolean = chargePlug == BatteryManager.BATTERY_PLUGGED_AC

        val source = when {
            isAcCharge -> "AC Charger"
            isUsbCharge -> "USB"
            isCharging -> "Wireless/Other"
            else -> "Battery Power"
        }

        return DiagnosticItem(
            id = "sys_battery",
            title = "Battery & Power Management",
            category = DiagnosticCategory.SYSTEM,
            status = if (batteryPct > 15f || isCharging) DiagnosticStatus.PASS else DiagnosticStatus.WARN,
            summary = "Level: ${batteryPct.toInt()}% (${if (isCharging) "Charging via $source" else "Discharging"})",
            details = "Battery Level: ${batteryPct.toInt()}%\nCharging: $isCharging\nPower Source: $source\nHealth: ${batteryStatus?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)}"
        )
    }

    private fun checkNetworkConnectivity(): DiagnosticItem {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNetwork = cm?.activeNetwork
        val caps = cm?.getNetworkCapabilities(activeNetwork)

        val hasInternet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val hasValidated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val isCellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        val isVpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true

        val type = when {
            isWifi -> "Wi-Fi"
            isCellular -> "Cellular Data"
            isVpn -> "VPN Network"
            activeNetwork != null -> "Connected (Ethernet/Other)"
            else -> "Disconnected"
        }

        val status = if (hasInternet) DiagnosticStatus.PASS else DiagnosticStatus.WARN

        return DiagnosticItem(
            id = "sys_network",
            title = "Network Connectivity & Internet",
            category = DiagnosticCategory.SYSTEM,
            status = status,
            summary = "$type (${if (hasInternet) "Internet Available" else "No Internet"})",
            details = "Active Network: $type\nInternet Validated: $hasValidated\nWi-Fi: $isWifi\nCellular: $isCellular\nVPN Active: $isVpn"
        )
    }

    private fun checkKeystoreSecurity(): DiagnosticItem {
        var hasAndroidKeystore = false
        var isDeviceSecure = false

        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            hasAndroidKeystore = true
        } catch (_: Exception) {}

        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        isDeviceSecure = keyguardManager?.isDeviceSecure == true

        val status = if (hasAndroidKeystore && isDeviceSecure) {
            DiagnosticStatus.PASS
        } else if (hasAndroidKeystore) {
            DiagnosticStatus.WARN
        } else {
            DiagnosticStatus.FAIL
        }

        return DiagnosticItem(
            id = "sys_keystore",
            title = "Hardware Keystore & Security Layer",
            category = DiagnosticCategory.SYSTEM,
            status = status,
            summary = "Keystore: ${if (hasAndroidKeystore) "Hardware-Backed" else "Unavailable"} | Lock Screen: ${if (isDeviceSecure) "Secured" else "Unsecured"}",
            details = "AndroidKeyStore Provider: $hasAndroidKeystore\nDevice Screen Lock (PIN/Pattern/Pass): $isDeviceSecure\nKeyguard Locked: ${keyguardManager?.isKeyguardLocked}"
        )
    }
}
