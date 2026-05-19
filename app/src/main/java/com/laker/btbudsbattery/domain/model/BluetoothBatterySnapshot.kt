package com.laker.btbudsbattery.domain.model

data class BluetoothBatterySnapshot(
    val deviceAddress: String,
    val deviceName: String,
    val batteryLevel: Int?,
    val leftLevel: Int?,
    val rightLevel: Int?,
    val caseLevel: Int?,
    val isConnected: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
) {
    val hasSplitLevels: Boolean
        get() = leftLevel != null || rightLevel != null || caseLevel != null

    val primaryLevel: Int?
        get() {
            if (batteryLevel != null) return batteryLevel
            val split = listOfNotNull(leftLevel, rightLevel, caseLevel)
            return if (split.isNotEmpty()) split.average().toInt() else null
        }
}

