package com.laker.btbudsbattery.receiver

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.laker.btbudsbattery.core.AppPreferences
import com.laker.btbudsbattery.core.RuntimePermissionGate
import com.laker.btbudsbattery.service.BluetoothBatteryService

class BluetoothConnectionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!AppPreferences(context).monitoringEnabled) {
            Log.d(LOG_TAG, "source=receiver_skip reason=monitoring_disabled action=${intent.action}")
            return
        }
        if (!RuntimePermissionGate.hasMonitoringPermissions(context)) {
            Log.d(LOG_TAG, "source=receiver_skip reason=missing_permissions action=${intent.action}")
            return
        }
        val actionName = intent.action ?: return
        if (actionName !in SUPPORTED_ACTIONS) {
            Log.d(LOG_TAG, "source=receiver_skip reason=unsupported_action action=$actionName")
            return
        }

        val action = when {
            isConnectEvent(intent) -> BluetoothBatteryService.ACTION_START_MONITORING
            else -> null
        }
        if (action == null) {
            Log.d(LOG_TAG, "source=receiver_skip reason=not_connect_event action=$actionName")
            return
        }

        val serviceIntent = Intent(context, BluetoothBatteryService::class.java).apply {
            this.action = action
        }
        runCatching {
            ContextCompat.startForegroundService(context, serviceIntent)
            Log.d(LOG_TAG, "source=receiver_start action=$actionName serviceAction=$action")
        }.onFailure { error ->
            Log.w(LOG_TAG, "source=receiver_start_failed action=$actionName error=${error.javaClass.simpleName}")
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
        private const val LOG_TAG = "BtConnReceiver"
        val SUPPORTED_ACTIONS = setOf(
            BluetoothDevice.ACTION_ACL_CONNECTED,
            BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
            BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED,
        )
    }
}

