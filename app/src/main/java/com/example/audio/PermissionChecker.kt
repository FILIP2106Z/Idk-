package com.example.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.example.model.SafetyAndPermissionState

object PermissionChecker {

    fun checkPermissions(
        context: Context,
        hasAcceptedSafetyNotice: Boolean,
    ): SafetyAndPermissionState {
        val micGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

        val btGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // BLUETOOTH is install-time on API 29-30
        }

        val notifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val batteryIgnored = pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false

        return SafetyAndPermissionState(
            hasAcceptedSafetyNotice = hasAcceptedSafetyNotice,
            microphoneGranted = micGranted,
            bluetoothGranted = btGranted,
            notificationsGranted = notifGranted,
            batteryOptimizationIgnored = batteryIgnored,
        )
    }
}
