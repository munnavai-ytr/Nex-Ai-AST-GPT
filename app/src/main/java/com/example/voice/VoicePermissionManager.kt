package com.example.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class VoicePermissionManager(private val context: Context) {

    val permissionName: String = Manifest.permission.RECORD_AUDIO

    val rationaleExplanation: String =
        "NEX requires microphone access to perceive your natural voice commands directly on-device. " +
        "Audio is processed via Android SpeechRecognizer and never saved or transmitted without authorization."

    private val _isMicrophoneGranted = MutableStateFlow(queryPermissionGranted())
    val isMicrophoneGranted: StateFlow<Boolean> = _isMicrophoneGranted.asStateFlow()

    private fun queryPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun checkPermission(): Boolean {
        val granted = queryPermissionGranted()
        _isMicrophoneGranted.value = granted
        return granted
    }

    fun isPermissionGranted(): Boolean = checkPermission()

    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
