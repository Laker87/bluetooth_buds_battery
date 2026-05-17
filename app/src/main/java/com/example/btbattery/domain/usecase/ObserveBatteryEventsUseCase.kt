package com.example.btbattery.domain.usecase

import com.example.btbattery.domain.model.BluetoothBatterySnapshot
import com.example.btbattery.domain.repository.BluetoothBatteryRepository
import kotlinx.coroutines.flow.Flow

class ObserveBatteryEventsUseCase(
    private val repository: BluetoothBatteryRepository,
) {
    operator fun invoke(): Flow<BluetoothBatterySnapshot> = repository.observeBatteryUpdates()
}
