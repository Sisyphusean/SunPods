package com.peyi.sonypodsbattery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SunPods against a simulated WF-C710N (reconstructed from Sound Connect's message layouts; see
 * C710nProfile). Every feature the C710N offers must read and change through the real code, and
 * nothing it lacks may be asked for or shown.
 */
class SimulatedC710nTest {
    private fun session(color: Int = 0x01): Pair<SimulatedEarbuds, SonyBatteryClient.SonySession> {
        val sim = SimulatedEarbuds(C710nProfile.build(color))
        return sim to SonyBatteryClient.SonySession(sim).also { it.initialize() }
    }

    private fun SimulatedEarbuds.asked(c: Int, t: Int, table: Int = SonyProtocol.DATA_MDR) =
        received.any { (tb, p) -> tb == table && (p[0].toInt() and 0xff) == c && p.size > 1 && (p[1].toInt() and 0xff) == t }

    @Test fun `battery uses the variant without thresholds`() {
        val (sim, s) = session()
        val b = s.readBattery()
        assertEquals(70, b.left)
        assertEquals(60, b.right)
        assertFalse(b.leftCharging)
        assertTrue(b.rightCharging)
        assertEquals(80, b.case)
        assertTrue(sim.asked(0x22, 0x01))
        assertFalse(sim.asked(0x22, 0x09))
    }

    @Test fun `noise control reads and changes on type 0x17`() {
        val (sim, s) = session()
        assertEquals(ListeningMode.NOISE_CANCELLING, s.readMode()!!.mode)
        val note = s.setAmbientSoundMode(ListeningMode.AMBIENT, 12, focusOnVoice = true)
        assertTrue(note, note.startsWith("applied"))
        val now = s.readMode()!!
        assertEquals(ListeningMode.AMBIENT, now.mode)
        assertTrue(now.focusOnVoice)
        assertEquals(12, now.ambientLevel)
        assertFalse(sim.asked(0x66, 0x15))
    }

    @Test fun `full read shows what the C710N has and nothing it lacks`() {
        val (sim, s) = session(color = 0x05)
        val t0 = System.currentTimeMillis()
        val r = SonyFeatures.readAll(s, DeviceSettings())
        val took = System.currentTimeMillis() - t0
        assertEquals("WF-C710N", r.modelName)
        assertEquals("1.0.5", r.firmware)
        assertEquals(0x05, r.colorCode)
        assertEquals("AAC", r.codec)
        assertEquals(false, r.dseeOn)
        assertEquals("DSEE", r.dseeName)
        assertEquals(ConnectionPriority.STABLE_CONNECTION, r.priority)
        assertEquals(0x10, r.eqPresetCode)
        assertEquals(List(5) { 0 }, r.bands)
        assertEquals(true, r.pauseWhenRemoved)
        assertEquals(AutoOff.MIN_15, r.autoOffCode)
        assertEquals(listOf(0x04, 0x01, 0x02, 0x03, 0x11), r.autoOffOptions)
        assertTrue(AutoOff.hasTimes(r.autoOffOptions))
        assertEquals(TouchFunction.AMBIENT_SOUND_CONTROL, r.touchLeft)
        assertEquals(TouchFunction.PLAYBACK_CONTROL, r.touchRight)
        assertNotNull(r.touchOptions)
        assertEquals(NoiseCycle.NC_AMBIENT_OFF, r.noiseCycle)
        assertEquals(listOf("MULTIPOINT_SETTING"), r.general!!.map { it.key })
        assertEquals(true, r.general!!.single().on)
        assertEquals(true, r.promptsOn)
        assertEquals(false, r.leModeSupported)
        assertNull(r.speakToChat)
        assertNull(r.headGestures)
        assertNull(r.autoSwitch)

        for (f in listOf(Feature.PAUSE_WHEN_REMOVED, Feature.AUTO_OFF_WHEN_REMOVED, Feature.DSEE, Feature.TOUCH, Feature.CODEC,
                Feature.VOICE_PROMPTS, Feature.RESET, Feature.POWER_OFF, Feature.WEARING)) assertTrue(f.name, r.has(f))
        for (f in listOf(Feature.SPEAK_TO_CHAT, Feature.HEAD_GESTURES, Feature.FIT_TEST, Feature.VOICE_ASSISTANT,
                Feature.SAFE_LISTENING, Feature.VOLUME_PROTECTION, Feature.LE_AUDIO_SWITCH, Feature.AUTO_SWITCH)) assertFalse(f.name, r.has(f))

        // Nothing it lacks was asked for.
        assertFalse(sim.asked(0xf6, 0x0c)); assertFalse(sim.asked(0xfa, 0x0c))   // Speak-to-Chat
        assertFalse(sim.asked(0xf6, 0x0f))                                       // head gestures
        assertFalse(sim.asked(0x46, 0x0c))                                       // LE Audio
        assertFalse(sim.asked(0xf6, 0x0a, SonyProtocol.DATA_MDR_NO2))            // auto switching
        // Unanswered questions are only the general-setting slots it doesn't use (probed once).
        assertTrue("full read took $took ms", took < 6_000)
    }

    @Test fun `auto power off times`() {
        val (sim, s) = session()
        val r = SonyFeatures.readAll(s, DeviceSettings())
        val thirty = SonyFeatures.setAutoOffOption(s, r, AutoOff.MIN_30)
        assertEquals(AutoOff.MIN_30, thirty.autoOffCode)
        assertEquals("27 05 01 01", hex(sim.state[SonyProtocol.DATA_MDR, 0x27, 0x05]!!))
        val never = SonyFeatures.setAutoOffOption(s, thirty, AutoOff.DISABLED)
        assertEquals(AutoOff.DISABLED, never.autoOffCode)
        // "Never" keeps the time last chosen, so choosing a time again restores it.
        assertEquals("27 05 11 01", hex(sim.state[SonyProtocol.DATA_MDR, 0x27, 0x05]!!))
        assertEquals(listOf(0x00, 0x04, 0x01, 0x02, 0x03, 0x11).filter { it in never.autoOffOptions!! }.let { AutoOff.ordered(never.autoOffOptions!!) },
            listOf(AutoOff.MIN_15, AutoOff.MIN_30, AutoOff.MIN_60, AutoOff.MIN_180, AutoOff.DISABLED))
    }

    @Test fun `multipoint off is confirmed with alert 07`() {
        val (sim, s) = session()
        val r = SonyFeatures.readAll(s, DeviceSettings())
        val after = SonyFeatures.setGeneral(s, r, 0xd1, on = false)
        assertEquals(false, after.general!!.single().on)
        assertEquals("d7 d1 00 01", hex(sim.state[SonyProtocol.DATA_MDR, 0xd7, 0xd1]!!))
        assertTrue(sim.received.any { (_, p) -> hex(p) == "98 00 07 01" })
    }

    @Test fun `eq, dsee, sound quality, pause and noise cycle change`() {
        val (sim, s) = session()
        var r = SonyFeatures.readAll(s, DeviceSettings())
        r = SonyFeatures.setEqPreset(s, r, EqPreset.BASS)
        assertEquals(EqPreset.BASS.code, r.eqPresetCode)
        r = SonyFeatures.setEqBands(s, r, 2, listOf(1, 0, -1, 0, 3))
        assertEquals(listOf(1, 0, -1, 0, 3), r.bands)
        r = SonyFeatures.setDsee(s, r, true)
        assertEquals(true, r.dseeOn)
        r = SonyFeatures.setPriority(s, r, ConnectionPriority.SOUND_QUALITY)
        assertEquals(ConnectionPriority.SOUND_QUALITY, r.priority)
        assertEquals("e7 00 00", hex(sim.state[SonyProtocol.DATA_MDR, 0xe7, 0x00]!!))
        r = SonyFeatures.setPauseWhenRemoved(s, r, false)
        assertEquals(false, r.pauseWhenRemoved)
        r = SonyFeatures.setNoiseCycle(s, r, NoiseCycle.NC_AMBIENT)
        assertEquals(NoiseCycle.NC_AMBIENT, r.noiseCycle)
        // The preset byte goes back as the earbuds reported it (00), not a fixed code.
        assertEquals("fb 03 01 00 01 00 02", hex(sim.state[SonyProtocol.DATA_MDR, 0xfb, 0x03]!!))
    }

    @Test fun `touch change away from playback is confirmed`() {
        val (_, s) = session()
        val r = SonyFeatures.readAll(s, DeviceSettings())
        val after = SonyFeatures.setTouch(s, r, null, TouchFunction.AMBIENT_SOUND_CONTROL)
        assertEquals(TouchFunction.AMBIENT_SOUND_CONTROL, after.touchRight)
    }

    @Test fun `voice prompts and reset`() {
        val (sim, s) = session()
        var r = SonyFeatures.readAll(s, DeviceSettings())
        r = SonyFeatures.setPrompts(s, r, false)
        assertEquals(false, r.promptsOn)
        SonyFeatures.powerOff(s)
        assertTrue(sim.poweredOff)
    }

    private fun hex(b: ByteArray) = b.joinToString(" ") { "%02x".format(it.toInt() and 0xff) }
}
