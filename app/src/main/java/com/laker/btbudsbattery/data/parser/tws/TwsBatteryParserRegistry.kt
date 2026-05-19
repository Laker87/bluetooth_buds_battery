package com.laker.btbudsbattery.data.parser.tws

import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import com.laker.btbudsbattery.data.parser.tws.realme.RealmeT310FastPairParser

object TwsBatteryParserRegistry {
    private val parsers: List<TwsBatteryAdvertisementParser> = listOf(
        RealmeT310FastPairParser,
    )

    val scanFilters: List<ScanFilter> by lazy {
        parsers.flatMap { it.scanFilters() }
    }

    fun parse(result: ScanResult): TwsBatteryAdvertisement? {
        return parsers.asSequence()
            .mapNotNull { parser -> parser.parse(result) }
            .firstOrNull()
    }
}

