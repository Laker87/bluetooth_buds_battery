package com.laker.btbudsbattery.data

data class AppleContinuityBattery(
    val modelName: String,
    val modelCode: Int,
    val batteryLevel: Int?,
    val leftLevel: Int?,
    val rightLevel: Int?,
    val caseLevel: Int?,
)

object AppleContinuityBatteryParser {
    const val APPLE_COMPANY_ID = 0x004C

    fun parse(manufacturerData: ByteArray?): AppleContinuityBattery? {
        if (manufacturerData == null) return null

        var index = 0
        while (index + 2 <= manufacturerData.size) {
            val type = manufacturerData[index].toInt() and 0xFF
            val length = manufacturerData[index + 1].toInt() and 0xFF
            val dataStart = index + 2
            val dataEnd = dataStart + length
            if (dataEnd > manufacturerData.size) return null

            if (type == PROXIMITY_PAIRING_TYPE && length >= PUBLIC_PAYLOAD_SIZE) {
                val data = manufacturerData.copyOfRange(dataStart, dataEnd)
                parseProximityPayload(data)?.let { return it }
            }

            index = dataEnd
        }

        return null
    }

    private fun parseProximityPayload(data: ByteArray): AppleContinuityBattery? {
        val modelCode = ((data[1].toInt() and 0xFF) shl 8) or (data[2].toInt() and 0xFF)
        val modelName = MODEL_NAMES[modelCode] ?: return null

        return if (modelCode in SINGLE_BATTERY_MODELS) {
            AppleContinuityBattery(
                modelName = modelName,
                modelCode = modelCode,
                batteryLevel = decodeNibbleBattery(data[4].toInt() and 0x0F),
                leftLevel = null,
                rightLevel = null,
                caseLevel = null,
            )
        } else {
            AppleContinuityBattery(
                modelName = modelName,
                modelCode = modelCode,
                batteryLevel = null,
                leftLevel = decodeNibbleBattery((data[4].toInt() ushr 4) and 0x0F),
                rightLevel = decodeNibbleBattery(data[4].toInt() and 0x0F),
                caseLevel = decodeNibbleBattery(data[5].toInt() and 0x0F),
            )
        }
    }

    private fun decodeNibbleBattery(value: Int): Int? {
        return when (value) {
            0x0F -> null
            in 0..10 -> value * 10
            else -> 100
        }
    }

    private const val PROXIMITY_PAIRING_TYPE = 0x07
    private const val PUBLIC_PAYLOAD_SIZE = 9

    private val SINGLE_BATTERY_MODELS = setOf(
        0x1020, // Beats Flex
        0x1003, // Beats X
        0x1004, // Powerbeats 3
        0x1009, // Beats Solo 3
        0x100D, // Beats Studio 3
        0x100F, // Beats Solo Pro
        0x101B, // Powerbeats 4
        0x1026, // Beats Studio Pro
        0x1027, // Beats Solo 4
    )

    private val MODEL_NAMES = mapOf(
        0x1020 to "Beats Flex",
        0x1003 to "Beats X",
        0x1004 to "Powerbeats 3",
        0x1009 to "Beats Solo 3",
        0x100D to "Beats Studio 3",
        0x100F to "Beats Solo Pro",
        0x101B to "Powerbeats 4",
        0x1026 to "Beats Studio Pro",
        0x1027 to "Beats Solo 4",
        0x2002 to "Powerbeats Pro",
        0x200E to "Beats Fit Pro",
        0x2010 to "Beats Studio Buds",
        0x2013 to "Beats Studio Buds+",
        0x2014 to "Beats Solo Buds",
        0x2017 to "Powerbeats Pro 2",
    )
}

