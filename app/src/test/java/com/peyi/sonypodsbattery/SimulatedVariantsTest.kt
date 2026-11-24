package com.peyi.sonypodsbattery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A pair that lists only a few functions and uses the battery variant without thresholds: SunPods
 * must pick the variants the earbuds list, skip what they lack (no waiting on silence), and leave
 * the missing features empty so the screens hide them.
 */
class SimulatedVariantsTest {
    private val minimal = SimProfile(
        EarbudModel.WF_C710N,
        recorded = """
            12 01 00 03 00 30 18 00 00
            12 05 01 08 57 46 2d 43 37 31 30 4e
            12 05 02 05 31 2e 30 2e 35
            12 05 03 50 05
            # support functions: L/R battery 0x21, case 0x22, power off 0x23, EQ 0x50, pause when removed 0xf1, touch 0xf3
            12 07 00 06 21 ff 22 ff 23 ff 50 ff f1 ff f3 ff
            14 07 00 00
            12 23 01 46 00 3c 01
            12 23 02 50 00
            12 57 00 00 06 0a 0a 0a 0a 0a 0a
            12 f7 01 00
            12 f7 03 02 00 20
        """
    )

    @Test fun `battery uses the variant the earbuds list`() {
        val sim = SimulatedEarbuds(minimal)
        val s = SonyBatteryClient.SonySession(sim).also { it.initialize() }
        val b = s.readBattery()
        assertEquals(70, b.left)
        assertEquals(60, b.right)
        assertTrue(b.rightCharging)
        assertEquals(80, b.case)
        assertTrue(sim.received.none { (_, p) -> p[0] == 0x22.toByte() && p[1] == 0x09.toByte() })
    }

    @Test fun `missing features are skipped and left empty`() {
        val sim = SimulatedEarbuds(minimal)
        val s = SonyBatteryClient.SonySession(sim).also { it.initialize() }
        val t0 = System.currentTimeMillis()
        val r = SonyFeatures.readAll(s, DeviceSettings())
        val took = System.currentTimeMillis() - t0
        assertEquals("WF-C710N", r.modelName)
        assertEquals(0x05, r.colorCode)
        assertEquals(true, r.pauseWhenRemoved)
        assertNull(r.speakToChat)
        assertNull(r.headGestures)
        assertEquals(false, r.leModeSupported)
        assertTrue(!r.has(Feature.SPEAK_TO_CHAT))
        assertTrue(!r.has(Feature.FIT_TEST))
        assertTrue(!r.has(Feature.SAFE_LISTENING))
        assertTrue(!r.has(Feature.VOICE_PROMPTS))
        assertTrue(r.has(Feature.PAUSE_WHEN_REMOVED))
        assertTrue(r.has(Feature.POWER_OFF))
        // No Speak-to-Chat, head gesture, voice prompt or LE questions were asked at all.
        val asked = sim.received.map { (t, p) -> Triple(t, p[0].toInt() and 0xff, p.getOrNull(1)?.toInt()?.and(0xff)) }
        assertTrue(asked.none { it.second == 0xf6 && it.third == 0x0c })
        assertTrue(asked.none { it.second == 0xf6 && it.third == 0x0f })
        assertTrue(asked.none { it.second == 0x46 && it.third == 0x0c })
        assertTrue(asked.none { it.first == SonyProtocol.DATA_MDR_NO2 && it.second == 0x46 })
        // Only features it might have are waited on; the rest are never asked.
        assertTrue("full read took $took ms", took < 12_000)
    }

    @Test fun `art follows the model and colour`() {
        val art = budArt(0x05, EarbudModel.WF_C710N)
        assertEquals("c710n", art.device)
        assertEquals("blue", art.hero3d)
        assertEquals("white", budArt(0x12, EarbudModel.WF_C710N).hero3d)
        assertEquals("xm5", budArt(0x03, EarbudModel.WF_1000XM5).device)
        assertEquals("silver", budArt(0x03, EarbudModel.WF_1000XM5).hero3d)
    }

    @Test fun `model names`() {
        assertEquals(EarbudModel.WF_C710N, EarbudModel.fromName("WF-C710N"))
        assertEquals(EarbudModel.WF_C710N, EarbudModel.fromName("LE_WF-C710N"))
        assertTrue(EarbudModel.WF_C710N.matchesLe("LE_WF-C710N"))
        assertEquals(EarbudModel.WF_1000XM5, EarbudModel.fromName("WF-1000XM5"))
        assertNull(EarbudModel.fromName("WH-CH720N"))   // not supported
        assertTrue(EarbudModel.WF_C710N.namePattern.matcher("WF-C710N").matches())
        assertTrue(EarbudModel.WF_1000XM5.namePattern.matcher("WF-1000XM5").matches())
    }
}
