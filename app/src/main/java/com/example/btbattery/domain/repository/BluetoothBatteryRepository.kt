package com.example.btbattery.domain.repository

import com.example.btbattery.domain.model.BluetoothBatterySnapshot
import kotlinx.coroutines.flow.Flow

interface BluetoothBatteryRepository {
    fun observeBatteryUpdates(): Flow<BluetoothBatterySnapshot>
    suspend fun refreshConnectedDevices()
}
