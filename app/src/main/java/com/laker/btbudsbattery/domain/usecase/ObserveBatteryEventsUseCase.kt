package com.laker.btbudsbattery.domain.usecase

import com.laker.btbudsbattery.domain.model.BluetoothBatterySnapshot
import com.laker.btbudsbattery.domain.repository.BluetoothBatteryRepository
import kotlinx.coroutines.flow.Flow

class ObserveBatteryEventsUseCase(
    private val repository: BluetoothBatteryRepository,
) {
    operator fun invoke(): Flow<BluetoothBatterySnapshot> = repository.observeBatteryUpdates()
}

