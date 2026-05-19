package com.laker.btbudsbattery.data.parser.tws.realme

import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import com.laker.btbudsbattery.data.parser.tws.TwsBatteryAdvertisement
import com.laker.btbudsbattery.data.parser.tws.TwsBatteryAdvertisementParser

object RealmeT310FastPairParser : TwsBatteryAdvertisementParser {
    override val parserId: String = RealmeT310FastPairSpec.PARSER_ID

    override fun scanFilters(): List<ScanFilter> {
        return listOf(
            ScanFilter.Builder()
                .setServiceData(
                    RealmeT310FastPairSpec.SERVICE_PARCEL_UUID,
                    byteArrayOf(),
                )
                .build(),
        )
    }

    override fun parse(result: ScanResult): TwsBatteryAdvertisement? {
        val serviceData = result.scanRecord
            ?.getServiceData(RealmeT310FastPairSpec.SERVICE_PARCEL_UUID)
            ?: return null
        if (serviceData.size <= RealmeT310FastPairSpec.LEVEL_INDEX_CASE) return null

        val left = decodeLevel(serviceData[RealmeT310FastPairSpec.LEVEL_INDEX_LEFT].toInt() and 0xFF)
        val right = decodeLevel(serviceData[RealmeT310FastPairSpec.LEVEL_INDEX_RIGHT].toInt() and 0xFF)
        val caseLevel = decodeLevel(serviceData[RealmeT310FastPairSpec.LEVEL_INDEX_CASE].toInt() and 0xFF)

        val payload = TwsBatteryAdvertisement(
            parserId = parserId,
            advertisedName = result.scanRecord?.deviceName ?: runCatching { result.device.name }.getOrNull(),
            leftLevel = left,
            rightLevel = right,
            caseLevel = caseLevel,
            rawPayloadHex = serviceData.toHexString(),
        )
        return payload.takeIf { it.hasAnyLevel }
    }

    private fun decodeLevel(raw: Int): Int? {
        if (raw in RealmeT310FastPairSpec.INVALID_LEVELS) return null
        return raw.takeIf { it in 0..100 }
    }

    private fun ByteArray.toHexString(): String {
        return joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}

