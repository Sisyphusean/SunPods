package com.peyi.sonypodsbattery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Decoders for the parity features, against replies from a WF-1000XM5 on firmware 6.1.0. */
class EarbudExtrasTest {
    private fun hex(s: String) = s.split(" ").map { it.toInt(16).toByte() }.toByteArray()

    @Test fun nowPlayingTitle() {
        val reply = hex("a7 01 02 05 48 65 6c 6c 6f 01 00 01 00 01 00")
        assertEquals("Hello", Playback.title(reply))
        assertNull(Playback.title(hex("a7 01 01 00")))
    }

    @Test fun voiceAssistantChoices() {
        assertEquals(listOf(0x31, 0xff), VoiceAssistantSetting.options(hex("f1 04 03 02 31 ff")))
        assertNull(VoiceAssistantSetting.options(hex("f1 04 03 00")))
    }

    @Test fun fitTestTips() {
        val tips = FitTest.tips(hex("f1 06 07 01 01 04 00 01 02 03"))!!
        assertEquals(1, tips.series)
        assertEquals(listOf(0, 1, 2, 3), tips.sizes)
        assertEquals("SS", FitTest.sizeLabel(0))
        assertEquals("L", FitTest.sizeLabel(3))
    }
}
