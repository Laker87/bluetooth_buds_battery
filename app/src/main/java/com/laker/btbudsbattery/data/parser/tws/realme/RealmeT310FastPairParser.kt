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

        return parseServiceData(
            serviceData = serviceData,
            advertisedName = result.scanRecord?.deviceName,
        )
    }

    internal fun parseServiceData(serviceData: ByteArray, advertisedName: String?): TwsBatteryAdvertisement? {
        val (leftRaw, rightRaw, caseRaw) = extractRawLevels(serviceData) ?: return null
        val left = decodeLevel(leftRaw)
        val right = decodeLevel(rightRaw)
        val caseLevel = decodeLevel(caseRaw)
        val payload = TwsBatteryAdvertisement(
            parserId = parserId,
            advertisedName = advertisedName,
            leftLevel = left,
            rightLevel = right,
            caseLevel = caseLevel,
            rawPayloadHex = serviceData.toHexString(),
        )
        return payload.takeIf { it.hasAnyLevel }
    }

    private fun extractRawLevels(serviceData: ByteArray): Triple<Int, Int, Int>? {
        val markerIndex = findMarkerIndex(serviceData)
        if (markerIndex >= 0) {
            val firstLevelIndex = markerIndex + RealmeT310FastPairSpec.LEVEL_MARKER.size
            if (serviceData.size > firstLevelIndex + 2) {
                return Triple(
                    serviceData[firstLevelIndex].toInt() and 0xFF,
                    serviceData[firstLevelIndex + 1].toInt() and 0xFF,
                    serviceData[firstLevelIndex + 2].toInt() and 0xFF,
                )
            }
        }

        if (serviceData.size <= RealmeT310FastPairSpec.LEVEL_INDEX_CASE) return null
        return Triple(
            serviceData[RealmeT310FastPairSpec.LEVEL_INDEX_LEFT].toInt() and 0xFF,
            serviceData[RealmeT310FastPairSpec.LEVEL_INDEX_RIGHT].toInt() and 0xFF,
            serviceData[RealmeT310FastPairSpec.LEVEL_INDEX_CASE].toInt() and 0xFF,
        )
    }

    private fun findMarkerIndex(serviceData: ByteArray): Int {
        val marker = RealmeT310FastPairSpec.LEVEL_MARKER
        val lastStart = serviceData.size - marker.size
        for (index in 0..lastStart) {
            var matches = true
            for (offset in marker.indices) {
                if (serviceData[index + offset] != marker[offset]) {
                    matches = false
                    break
                }
            }
            if (matches) return index
        }
        return -1
    }

    private fun decodeLevel(raw: Int): Int? {
        if (raw in RealmeT310FastPairSpec.INVALID_LEVELS) return null
        return raw.takeIf { it in 0..100 }
    }

    private fun ByteArray.toHexString(): String {
        return joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}

