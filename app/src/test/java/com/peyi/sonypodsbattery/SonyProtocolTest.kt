package com.peyi.sonypodsbattery

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SonyProtocolTest {
    @Test fun `dual battery command matches known Sony frame`() {
        val expected = byteArrayOf(0x3e, 0x0c, 0x00, 0, 0, 0, 2, 0x22, 0x09, 0x39, 0x3c)
        assertArrayEquals(expected, SonyProtocol.encode(SonyProtocol.DATA_MDR, 0, byteArrayOf(0x22, 0x09)))
    }

    @Test fun `round trip preserves escaped protocol bytes`() {
        val payload = byteArrayOf(0x3c, 0x3d, 0x3e, 0x23)
        val encoded = SonyProtocol.encode(SonyProtocol.DATA_MDR, 1, payload)
        assertTrue(encoded.count { it == 0x3d.toByte() } >= 3)
        val result = SonyProtocol.Decoder().feed(encoded).single()
        assertEquals(SonyProtocol.DATA_MDR, result.type)
        assertEquals(1, result.sequence)
        assertArrayEquals(payload, result.payload)
    }

    @Test fun `decoder accepts a frame delivered in chunks`() {
        val encoded = SonyProtocol.encode(SonyProtocol.DATA_MDR, 0, byteArrayOf(0x23, 0x09, 80, 0, 70, 1))
        val decoder = SonyProtocol.Decoder()
        assertTrue(decoder.feed(encoded.copyOfRange(0, 4)).isEmpty())
        val result = decoder.feed(encoded.copyOfRange(4, encoded.size)).single()
        assertEquals(6, result.payload.size)
        assertEquals(80, result.payload[2].toInt())
    }

    @Test fun `decoder rejects a bad checksum`() {
        val encoded = SonyProtocol.encode(SonyProtocol.DATA_MDR, 0, byteArrayOf(0x22, 0x0a))
        encoded[encoded.lastIndex - 1] = 0
        assertTrue(SonyProtocol.Decoder().feed(encoded).isEmpty())
    }
}
