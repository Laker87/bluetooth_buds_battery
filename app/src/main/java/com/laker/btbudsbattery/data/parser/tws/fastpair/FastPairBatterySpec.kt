package com.laker.btbudsbattery.data.parser.tws.fastpair

import android.os.ParcelUuid
import java.util.Locale
import java.util.UUID

object FastPairBatterySpec {
    const val PARSER_ID = "fast_pair_battery"

    val SERVICE_UUID: UUID = UUID.fromString("0000fe2c-0000-1000-8000-00805f9b34fb")
    val SERVICE_PARCEL_UUID: ParcelUuid = ParcelUuid(SERVICE_UUID)

    const val TYPE_SHOW_UI = 0x03
    const val TYPE_HIDE_UI = 0x04

    private val BRAND_PREFIXES = linkedMapOf(
        "google" to listOf("pixel buds", "google pixel buds"),
        "sony" to listOf("wf-", "wh-", "linkbuds"),
        "jbl" to listOf("jbl"),
        "oneplus" to listOf("oneplus", "buds pro", "buds z"),
        "oppo" to listOf("oppo enco", "enco"),
        "realme" to listOf("realme buds", "realme"),
        "nothing" to listOf("nothing ear", "cmf buds", "cmf by nothing"),
        "xiaomi" to listOf("xiaomi buds", "redmi buds"),
        "motorola" to listOf("moto buds", "motorola"),
        "huawei" to listOf("freebuds", "huawei freebuds"),
    )

    fun resolveBrandFromName(name: String?): String? {
        val normalized = name
            ?.lowercase(Locale.ROOT)
            ?.substringBefore("-gfp")
            ?.trim()
            .orEmpty()
        if (normalized.isBlank()) return null
        return BRAND_PREFIXES.entries.firstNotNullOfOrNull { (brand, prefixes) ->
            brand.takeIf { prefixes.any { normalized.startsWith(it) || normalized.contains(it) } }
        }
    }
}
