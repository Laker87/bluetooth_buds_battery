package com.laker.btbudsbattery.domain.repository

import com.laker.btbudsbattery.domain.model.BluetoothBatterySnapshot
import kotlinx.coroutines.flow.Flow

interface BluetoothBatteryRepository {
    fun observeBatteryUpdates(): Flow<BluetoothBatterySnapshot>
    suspend fun refreshConnectedDevices()
}

