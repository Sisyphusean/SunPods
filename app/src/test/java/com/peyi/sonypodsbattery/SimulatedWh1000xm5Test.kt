package com.peyi.sonypodsbattery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SunPods against simulated WH-1000XM5 headphones (reconstructed from Sound Connect's layouts and
 * other projects' captures; see Wh1000xm5Profile). One battery, no case, the NC/AMB button instead of
 * per-side touch, a touch panel switch, and nothing the headphones lack is asked for.
 */
class SimulatedWh1000xm5Test {
    private fun session(color: Int = 0x01): Pair<SimulatedEarbuds, SonyBatteryClient.SonySession> {
        val sim = SimulatedEarbuds(Wh1000xm5Profile.build(color))
        return sim to SonyBatteryClient.SonySession(sim).also { it.initialize() }
    }

    private fun SimulatedEarbuds.asked(c: Int, t: Int, table: Int = SonyProtocol.DATA_MDR) =
        received.any { (tb, p) -> tb == table && (p[0].toInt() and 0xff) == c && p.size > 1 && (p[1].toInt() and 0xff) == t }

    private fun hex(b: ByteArray) = b.joinToString(" ") { "%02x".format(it.toInt() and 0xff) }

    @Test fun `the model is recognised as headphones`() {
        assertEquals(EarbudModel.WH_1000XM5, EarbudModel.fromName("WH-1000XM5"))
        assertEquals(EarbudModel.WH_1000XM5, EarbudModel.fromName("LE_WH-1000XM5"))
        assertEquals(EarbudModel.WF_1000XM5, EarbudModel.fromName("WF-1000XM5"))
        assertTrue(EarbudModel.WH_1000XM5.headphones)
        assertEquals("headphones", EarbudModel.WH_1000XM5.noun)
        assertTrue(EarbudModel.scanPattern.matcher("WH-1000XM5").matches())
    }

    @Test fun `one battery, no case`() {
        val (sim, s) = session()
        val b = s.readBattery()
        assertEquals(70, b.left)
        assertNull(b.right)
        assertNull(b.case)
        assertFalse(b.leftCharging)
        assertTrue(sim.asked(0x22, 0x00))
        assertFalse(sim.asked(0x22, 0x09))
        assertFalse(sim.asked(0x22, 0x0a))
    }

    @Test fun `noise control on type 0x17`() {
        val (_, s) = session()
        assertEquals(ListeningMode.NOISE_CANCELLING, s.readMode()!!.mode)
        val note = s.setAmbientSoundMode(ListeningMode.AMBIENT, 10, focusOnVoice = true)
        assertTrue(note, note.startsWith("applied"))
        val now = s.readMode()!!
        assertEquals(ListeningMode.AMBIENT, now.mode)
        assertTrue(now.focusOnVoice)
        assertEquals(10, now.ambientLevel)
    }

    @Test fun `full read shows what the headphones have and nothing they lack`() {
        val (sim, s) = session(color = 0x06)
        val r = SonyFeatures.readAll(s, DeviceSettings())
        assertEquals("WH-1000XM5", r.modelName)
        assertEquals("2.5.1", r.firmware)
        assertEquals(0x06, r.colorCode)
        assertEquals("LDAC", r.codec)
        assertEquals(false, r.leModeSupported)
        assertEquals(NoiseCycle.NC_AMBIENT, r.noiseCycle)
        assertEquals(setOf("TOUCH_PANEL_SETTING", "MULTIPOINT_SETTING"), r.general!!.map { it.key }.toSet())
        assertEquals(true, r.general!!.first { it.key == "TOUCH_PANEL_SETTING" }.on)
        assertEquals(true, r.pauseWhenRemoved)
        for (f in listOf(Feature.NC_AMB_BUTTON, Feature.DSEE, Feature.SPEAK_TO_CHAT, Feature.PAUSE_WHEN_REMOVED, Feature.CODEC, Feature.VOICE_PROMPTS, Feature.SAFE_LISTENING))
            assertTrue(f.name, r.has(f))
        for (f in listOf(Feature.TOUCH, Feature.HEAD_GESTURES, Feature.FIT_TEST, Feature.LE_AUDIO_SWITCH))
            assertFalse(f.name, r.has(f))
        // The per-side touch read and LE Audio were never asked for.
        assertFalse(sim.asked(0xf6, 0x03)); assertFalse(sim.asked(0xfa, 0x03))
        assertFalse(sim.asked(0x46, 0x0c))
        assertTrue(sim.asked(0x66, 0x30))
    }

    @Test fun `the NC-AMB button cycle changes on type 30`() {
        val (sim, s) = session()
        val r = SonyFeatures.readAll(s, DeviceSettings())
        val after = SonyFeatures.setNoiseCycle(s, r, NoiseCycle.NC_AMBIENT_OFF)
        assertEquals(NoiseCycle.NC_AMBIENT_OFF, after.noiseCycle)
        assertEquals("67 30 01", hex(sim.state[SonyProtocol.DATA_MDR, 0x67, 0x30]!!))
        assertFalse(sim.asked(0xfc, 0x03))
    }

    @Test fun `touch panel switch and two devices`() {
        val (sim, s) = session()
        var r = SonyFeatures.readAll(s, DeviceSettings())
        r = SonyFeatures.setGeneral(s, r, 0xd1, on = false)
        assertEquals(false, r.general!!.first { it.key == "TOUCH_PANEL_SETTING" }.on)
        r = SonyFeatures.setGeneral(s, r, 0xd2, on = true)
        assertEquals(true, r.general!!.first { it.key == "MULTIPOINT_SETTING" }.on)
        assertTrue(sim.received.any { (_, p) -> hex(p) == "98 00 07 01" })
    }

    @Test fun `equaliser, DSEE Extreme and power off`() {
        val (sim, s) = session()
        var r = SonyFeatures.readAll(s, DeviceSettings())
        r = SonyFeatures.setEqPreset(s, r, EqPreset.BASS)
        assertEquals(EqPreset.BASS.code, r.eqPresetCode)
        r = SonyFeatures.setDsee(s, r, true)
        assertEquals(true, r.dseeOn)
        SonyFeatures.powerOff(s)
        assertTrue(sim.poweredOff)
    }
}
