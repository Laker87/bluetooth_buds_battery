package com.laker.btbudsbattery.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.laker.btbudsbattery.core.AppPreferences
import com.laker.btbudsbattery.core.RuntimePermissionGate
import com.laker.btbudsbattery.service.BluetoothBatteryService

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }

        val preferences = AppPreferences(context)
        if (!preferences.monitoringEnabled) return
        if (!RuntimePermissionGate.hasAllRequiredPermissions(context)) return

        val startIntent = Intent(context, BluetoothBatteryService::class.java).apply {
            action = BluetoothBatteryService.ACTION_BOOT_RESTORE_MONITORING
        }
        runCatching {
            ContextCompat.startForegroundService(context, startIntent)
        }
    }
}

