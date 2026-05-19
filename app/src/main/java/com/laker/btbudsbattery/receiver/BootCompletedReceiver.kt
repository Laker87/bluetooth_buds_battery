package com.laker.btbudsbattery.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.laker.btbudsbattery.core.AppPreferences
import com.laker.btbudsbattery.service.BluetoothBatteryService

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }

        val preferences = AppPreferences(context)
        if (!preferences.monitoringEnabled) return

        val startIntent = Intent(context, BluetoothBatteryService::class.java).apply {
            action = BluetoothBatteryService.ACTION_BOOT_RESTORE_MONITORING
        }
        context.startService(startIntent)
    }
}

