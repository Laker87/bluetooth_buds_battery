package com.laker.btbudsbattery.data.parser.tws.realme

import android.os.ParcelUuid
import java.util.UUID

object RealmeT310FastPairSpec {
    const val PARSER_ID = "realme_t310_fast_pair"

    val SERVICE_UUID: UUID = UUID.fromString("0000fe2c-0000-1000-8000-00805f9b34fb")
    val SERVICE_PARCEL_UUID: ParcelUuid = ParcelUuid(SERVICE_UUID)

    const val LEVEL_INDEX_LEFT = 10
    const val LEVEL_INDEX_RIGHT = 11
    const val LEVEL_INDEX_CASE = 12
    val LEVEL_MARKER = byteArrayOf(0x11, 0x55, 0x33)

    val INVALID_LEVELS = setOf(0x7F, 0xFF)
}

