package com.laker.btbudsbattery.data.parser.tws.fastpair

import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import com.laker.btbudsbattery.data.parser.tws.TwsBatteryAdvertisement
import com.laker.btbudsbattery.data.parser.tws.TwsBatteryAdvertisementParser

object FastPairBatteryParser : TwsBatteryAdvertisementParser {
    override val parserId: String = FastPairBatterySpec.PARSER_ID

    override fun scanFilters(): List<ScanFilter> {
        return listOf(
            ScanFilter.Builder()
                .setServiceData(FastPairBatterySpec.SERVICE_PARCEL_UUID, byteArrayOf())
                .build(),
        )
    }

    override fun parse(result: ScanResult): TwsBatteryAdvertisement? {
        val serviceData = result.scanRecord
            ?.getServiceData(FastPairBatterySpec.SERVICE_PARCEL_UUID)
            ?: return null

        val candidate = findBestCandidate(serviceData) ?: return null
        val (left, right, caseLevel) = mapComponentLevels(candidate.values)

        val brandHint = FastPairBatterySpec.resolveBrandFromName(result.scanRecord?.deviceName)
        val effectiveParserId = if (brandHint == null) parserId else "$parserId:$brandHint"

        val payload = TwsBatteryAdvertisement(
            parserId = effectiveParserId,
            advertisedName = result.scanRecord?.deviceName,
            leftLevel = left,
            rightLevel = right,
            caseLevel = caseLevel,
            rawPayloadHex = serviceData.toHexString(),
        )
        return payload.takeIf { it.hasAnyLevel }
    }

    private fun findBestCandidate(serviceData: ByteArray): BatteryCandidate? {
        val candidates = mutableListOf<BatteryCandidate>()

        for (index in serviceData.indices) {
            val lengthAndType = serviceData[index].toInt() and 0xFF
            val type = lengthAndType and 0x0F
            if (type != FastPairBatterySpec.TYPE_SHOW_UI && type != FastPairBatterySpec.TYPE_HIDE_UI) continue

            val valueCount = (lengthAndType ushr 4) and 0x0F
            if (valueCount !in 1..3) continue

            val endExclusive = index + 1 + valueCount
            if (endExclusive > serviceData.size) continue
            if (endExclusive != serviceData.size) continue

            val values = buildList {
                for (valueIndex in (index + 1) until endExclusive) {
                    add(decodeBatteryValue(serviceData[valueIndex].toInt() and 0xFF))
                }
            }
            val knownCount = values.count { it != null }
            if (knownCount == 0) continue

            candidates += BatteryCandidate(
                startIndex = index,
                valueCount = valueCount,
                knownCount = knownCount,
                values = values,
            )
        }

        return candidates.maxWithOrNull(
            compareBy<BatteryCandidate> { it.knownCount }
                .thenBy { it.valueCount }
                .thenBy { it.startIndex },
        )
    }

    private fun mapComponentLevels(values: List<Int?>): Triple<Int?, Int?, Int?> {
        return when (values.size) {
            1 -> Triple(values[0], null, null)
            2 -> Triple(values[0], values[1], null)
            else -> Triple(values.getOrNull(0), values.getOrNull(1), values.getOrNull(2))
        }
    }

    private fun decodeBatteryValue(raw: Int): Int? {
        val value = raw and 0x7F
        if (value == 0x7F) return null
        return value.takeIf { it in 0..100 }
    }

    private fun ByteArray.toHexString(): String {
        return joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private data class BatteryCandidate(
        val startIndex: Int,
        val valueCount: Int,
        val knownCount: Int,
        val values: List<Int?>,
    )
}
