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
        val reversedModelCode = ((data[2].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
        val modelInfo = MODEL_INFOS[modelCode] ?: MODEL_INFOS[reversedModelCode] ?: resolveBySingleMarker(data)
            ?: return null

        return if (modelInfo.singleBattery) {
            AppleContinuityBattery(
                modelName = modelInfo.name,
                modelCode = modelCode,
                batteryLevel = decodeNibbleBattery(data[4].toInt() and 0x0F),
                leftLevel = null,
                rightLevel = null,
                caseLevel = null,
            )
        } else {
            val rawLeft = decodeNibbleBattery((data[4].toInt() ushr 4) and 0x0F)
            val rawRight = decodeNibbleBattery(data[4].toInt() and 0x0F)
            val flip = isFlipped(data)
            AppleContinuityBattery(
                modelName = modelInfo.name,
                modelCode = modelCode,
                batteryLevel = null,
                leftLevel = if (flip) rawRight else rawLeft,
                rightLevel = if (flip) rawLeft else rawRight,
                caseLevel = decodeNibbleBattery(data[5].toInt() and 0x0F),
            )
        }
    }

    private fun isFlipped(data: ByteArray): Boolean {
        // Mirrors OpenPods heuristic: if bit1 of nibble #10 is 0, L/R nibbles are flipped.
        // In this parser's payload, nibble #10 is the high nibble of data[3].
        val nibble10 = (data[3].toInt() ushr 4) and 0x0F
        return (nibble10 and 0x02) == 0
    }

    private fun resolveBySingleMarker(data: ByteArray): ModelInfo? {
        // OpenPods uses the 7th hex character (low nibble of model byte) as a model-family marker.
        val marker = data[1].toInt() and 0x0F
        return SINGLE_MARKER_MODEL_INFOS[marker]
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

    private data class ModelInfo(
        val name: String,
        val singleBattery: Boolean,
    )

    private val MODEL_INFOS = mapOf(
        // OpenPods ids (idFull, hex chars 6..9)
        0x0220 to ModelInfo(name = "AirPods 1", singleBattery = false),
        0x0F20 to ModelInfo(name = "AirPods 2", singleBattery = false),
        0x1320 to ModelInfo(name = "AirPods 3", singleBattery = false),
        0x0E20 to ModelInfo(name = "AirPods Pro", singleBattery = false),
        0x1420 to ModelInfo(name = "AirPods Pro 2", singleBattery = false),
        0x2420 to ModelInfo(name = "AirPods Pro 2", singleBattery = false),
        0x2720 to ModelInfo(name = "AirPods Pro 3", singleBattery = false),
        0x0520 to ModelInfo(name = "Beats X", singleBattery = true),
        0x1020 to ModelInfo(name = "Beats Flex", singleBattery = true),
        0x0620 to ModelInfo(name = "Beats Solo 3", singleBattery = true),
        0x0320 to ModelInfo(name = "Powerbeats 3", singleBattery = true),

        // Existing known ids seen in other dumps/firmwares.
        0x1003 to ModelInfo(name = "Beats X", singleBattery = true),
        0x1004 to ModelInfo(name = "Powerbeats 3", singleBattery = true),
        0x1009 to ModelInfo(name = "Beats Solo 3", singleBattery = true),
        0x100D to ModelInfo(name = "Beats Studio 3", singleBattery = true),
        0x100F to ModelInfo(name = "Beats Solo Pro", singleBattery = true),
        0x101B to ModelInfo(name = "Powerbeats 4", singleBattery = true),
        0x1026 to ModelInfo(name = "Beats Studio Pro", singleBattery = true),
        0x1027 to ModelInfo(name = "Beats Solo 4", singleBattery = true),
        0x2002 to ModelInfo(name = "Powerbeats Pro", singleBattery = false),
        0x200E to ModelInfo(name = "Beats Fit Pro", singleBattery = false),
        0x2010 to ModelInfo(name = "Beats Studio Buds", singleBattery = false),
        0x2013 to ModelInfo(name = "Beats Studio Buds+", singleBattery = false),
        0x2014 to ModelInfo(name = "Beats Solo Buds", singleBattery = false),
        0x2017 to ModelInfo(name = "Powerbeats Pro 2", singleBattery = false),
    )

    private val SINGLE_MARKER_MODEL_INFOS = mapOf(
        0x0A to ModelInfo(name = "AirPods Max", singleBattery = true), // marker 'A'
        0x0B to ModelInfo(name = "Powerbeats Pro", singleBattery = false), // marker 'B'
        0x09 to ModelInfo(name = "Beats Studio 3", singleBattery = true), // marker '9'
    )
}

