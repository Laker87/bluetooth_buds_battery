package com.laker.btbudsbattery.receiver

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.laker.btbudsbattery.core.AppPreferences
import com.laker.btbudsbattery.core.RuntimePermissionGate
import com.laker.btbudsbattery.service.BluetoothBatteryService

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!AppPreferences(context).monitoringEnabled) return
        if (!RuntimePermissionGate.hasMonitoringPermissions(context)) return
        if (!hasAnyActiveBluetoothAudioConnection(context)) return

        val startIntent = Intent(context, BluetoothBatteryService::class.java).apply {
            action = BluetoothBatteryService.ACTION_BOOT_RESTORE_MONITORING
        }
        runCatching {
            ContextCompat.startForegroundService(context, startIntent)
        }
    }

    @SuppressLint("MissingPermission")
    private fun hasAnyActiveBluetoothAudioConnection(context: Context): Boolean {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return false
        val a2dpConnected = runCatching {
            adapter.getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothProfile.STATE_CONNECTED
        }.getOrDefault(false)
        val headsetConnected = runCatching {
            adapter.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothProfile.STATE_CONNECTED
        }.getOrDefault(false)
        return a2dpConnected || headsetConnected
    }
}
