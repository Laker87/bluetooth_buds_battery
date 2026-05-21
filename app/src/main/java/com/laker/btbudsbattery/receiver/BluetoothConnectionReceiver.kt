package com.laker.btbudsbattery.receiver

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.laker.btbudsbattery.core.AppPreferences
import com.laker.btbudsbattery.core.RuntimePermissionGate
import com.laker.btbudsbattery.service.BluetoothBatteryService

class BluetoothConnectionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!AppPreferences(context).monitoringEnabled) return
        if (!RuntimePermissionGate.hasAllRequiredPermissions(context)) return
        val actionName = intent.action ?: return
        if (actionName !in SUPPORTED_ACTIONS) return

        val action = when {
            isConnectEvent(intent) -> BluetoothBatteryService.ACTION_START_MONITORING
            else -> null
        }
        if (action == null) return

        val serviceIntent = Intent(context, BluetoothBatteryService::class.java).apply {
            this.action = action
        }
        runCatching {
            ContextCompat.startForegroundService(context, serviceIntent)
        }
    }

    private fun isConnectEvent(intent: Intent): Boolean {
        return when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> true
            BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
            BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1) ==
                    BluetoothProfile.STATE_CONNECTED
            }
            else -> false
        }
    }

    private companion object {
        val SUPPORTED_ACTIONS = setOf(
            BluetoothDevice.ACTION_ACL_CONNECTED,
            BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
            BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED,
        )
    }
}

