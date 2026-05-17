package com.example.btbattery.core

import com.example.btbattery.domain.model.BluetoothBatterySnapshot
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object FastPairEventBus {
    // replay=1 keeps the latest snapshot for UI collectors that start after service already emitted.
    private val _events = MutableSharedFlow<BluetoothBatterySnapshot>(replay = 1, extraBufferCapacity = 16)
    val events = _events.asSharedFlow()

    fun emit(snapshot: BluetoothBatterySnapshot) {
        _events.tryEmit(snapshot)
    }
}
