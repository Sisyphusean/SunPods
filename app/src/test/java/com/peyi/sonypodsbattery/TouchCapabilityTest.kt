package com.peyi.sonypodsbattery

import org.junit.Assert.assertEquals
import org.junit.Test

class TouchCapabilityTest {
    // WF-1000XM5 firmware 6.1.0, read over LE Audio.
    private val xm5 = ("f1 03 02 00 00 00 03 00 02 01 03 24 22 10 00 02 04 01 02 03 04 20 04 00 00 20 01 21 02 22 03 24 ff 01 00 00 00 " +
        "01 00 20 03 00 02 01 03 23 22 10 00 02 04 01 02 03 04 20 04 00 00 20 01 21 02 22 03 23 ff 01 00 00 00")
        .split(" ").map { it.toInt(16).toByte() }.toByteArray()

    @Test fun xm5OffersNoVolumeControl() {
        val caps = TouchCapability.parse(xm5)!!
        val expected = listOf(TouchFunction.AMBIENT_SOUND_CONTROL, TouchFunction.PLAYBACK_CONTROL, TouchFunction.NONE)
        assertEquals(expected, caps[0]!!.map { it.function })
        assertEquals(expected, caps[1]!!.map { it.function })
    }

    @Test fun describesGestures() {
        val caps = TouchCapability.parse(xm5)!!
        assertEquals("Tap: change noise mode. Tap repeatedly: volume down. Hold: Quick Attention.", TouchCapability.describe(caps[0]!![0]))
        assertEquals("Tap: play or pause. Double-tap: next track. Triple-tap: previous track. Tap repeatedly: volume up.", TouchCapability.describe(caps[1]!![1]))
        assertEquals("Touch controls are off on this side.", TouchCapability.describe(caps[1]!![2]))
    }

    @Test fun badReplyGivesNull() {
        assertEquals(null, TouchCapability.parse(byteArrayOf(0xf1.toByte(), 0x03, 0x02, 0x00)))
    }

    // The same earbuds on Classic: Noise Control carries the two Quick Access slots.
    @Test fun classicNoiseControlHasQuickAccess() {
        val classic = ("f1 03 02 00 00 35 03 35 04 01 01 43 02 44 22 10 03 24 00 02 04 01 02 03 04 20 04 00 00 20 01 21 02 22 03 24 ff 01 00 00 00 " +
            "01 00 20 03 35 04 01 01 43 02 44 22 10 03 23 00 02 04 01 02 03 04 20 04 00 00 20 01 21 02 22 03 23 ff 01 00 00 00")
            .split(" ").map { it.toInt(16).toByte() }.toByteArray()
        val left = TouchCapability.parse(classic)!![0]!!
        assertEquals(listOf(TouchFunction.AMBIENT_SOUND_CONTROL, TouchFunction.PLAYBACK_CONTROL, TouchFunction.NONE), left.map { it.function })
        assertEquals(
            "Tap: change noise mode. Double-tap: Quick Access (set up in Spotify or Endel). Triple-tap: Quick Access (set up in Spotify or Endel). Tap repeatedly: volume down. Hold: Quick Attention.",
            TouchCapability.describe(left[0])
        )
    }
}
