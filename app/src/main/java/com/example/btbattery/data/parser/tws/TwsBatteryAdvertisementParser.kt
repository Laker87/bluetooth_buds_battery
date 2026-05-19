package com.example.btbattery.data.parser.tws

import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult

data class TwsBatteryAdvertisement(
    val parserId: String,
    val advertisedName: String?,
    val leftLevel: Int?,
    val rightLevel: Int?,
    val caseLevel: Int?,
    val rawPayloadHex: String,
) {
    val hasAnyLevel: Boolean
        get() = leftLevel != null || rightLevel != null || caseLevel != null
}

interface TwsBatteryAdvertisementParser {
    val parserId: String

    fun scanFilters(): List<ScanFilter>

    fun parse(result: ScanResult): TwsBatteryAdvertisement?
}
