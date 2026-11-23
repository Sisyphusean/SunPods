package com.peyi.sonypodsbattery

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolCaptureTest {
    private fun bytes(vararg head: Int, text: String = "") = ByteArray(head.size) { head[it].toByte() } + text.toByteArray()

    @Test fun `addresses are masked and personal replies skipped`() {
        ProtocolCapture.record(SonyProtocol.DATA_MDR, bytes(0x41, 0x00, text = "AA:BB:CC:11:22:33C0:FF:EE:44:55:66"))
        ProtocolCapture.record(SonyProtocol.DATA_MDR_NO2, bytes(0x37, 0x00, 0x01, text = "Someone's Phone"))
        ProtocolCapture.record(SonyProtocol.DATA_MDR, bytes(0xa7, 0x01, 0x02, text = "A Song Title"))
        ProtocolCapture.record(SonyProtocol.DATA_MDR, bytes(0x05, 0x01, 0x08, text = "WF-C710N"))
        val text = ProtocolCapture.text()
        val decoded = text.lines().drop(1).joinToString("\n") { line ->
            line.split(' ').drop(1).map { it.toInt(16).toChar() }.joinToString("")
        }
        assertFalse(decoded.contains("AA:BB:CC"))
        assertFalse(decoded.contains("C0:FF:EE"))
        assertTrue(decoded.contains("02:00:00:00:00:01"))
        assertFalse(decoded.contains("Phone"))
        assertFalse(decoded.contains("Song"))
        assertTrue(decoded.contains("WF-C710N"))
        // The report loads straight into simulated earbuds.
        val profile = SimProfile(EarbudModel.WF_C710N, text.lines().drop(1).joinToString("\n"))
        assertTrue(profile.initial.isNotEmpty())
    }
}
