package com.laker.btbudsbattery.data

import com.laker.btbudsbattery.data.parser.tws.realme.RealmeT310FastPairParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RealmeT310FastPairParserTest {
    @Test
    fun parseServiceData_parsesLegacyPayloadLayout() {
        val payload = "00504863806b4411553364647f".hexToBytes()

        val result = RealmeT310FastPairParser.parseServiceData(payload, "Realme Buds T310")

        assertEquals(100, result?.leftLevel)
        assertEquals(100, result?.rightLevel)
        assertNull(result?.caseLevel)
    }

    @Test
    fun parseServiceData_parsesUpdatedPayloadLayoutFromBtsnoop() {
        val payload = "0060ea1084392a2d115533646450".hexToBytes()

        val result = RealmeT310FastPairParser.parseServiceData(payload, "Realme Buds T310")

        assertEquals(100, result?.leftLevel)
        assertEquals(100, result?.rightLevel)
        assertEquals(80, result?.caseLevel)
    }

    private fun String.hexToBytes(): ByteArray {
        require(length % 2 == 0) { "Hex string must have even length" }
        return ByteArray(length / 2) { index ->
            substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }
}
