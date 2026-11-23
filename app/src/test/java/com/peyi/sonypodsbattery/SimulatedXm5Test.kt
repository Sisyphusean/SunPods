package com.peyi.sonypodsbattery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SunPods' protocol code against simulated WF-1000XM5 earbuds built from real recorded replies.
 * These pin today's working XM5 behaviour, so adding other models can't quietly break it.
 */
class SimulatedXm5Test {
    private fun session(sim: SimulatedEarbuds = SimulatedEarbuds(SimProfiles.XM5)) =
        sim to SonyBatteryClient.SonySession(sim).also { it.initialize() }

    @Test fun `handshake and battery`() {
        val (_, s) = session()
        val b = s.readBattery()
        assertEquals(0x5b, b.left)
        assertEquals(100, b.right)
        assertEquals(0x42, b.case)
    }

    @Test fun `noise control reads type 0x17`() {
        val (sim, s) = session()
        val mode = s.readMode()
        assertNotNull(mode)
        assertEquals(ListeningMode.AMBIENT, mode!!.mode)
        assertEquals(20, mode.ambientLevel)
        assertTrue(sim.received.any { (t, p) -> t == SonyProtocol.DATA_MDR && p[0] == 0x66.toByte() && p[1] == 0x17.toByte() })
    }

    @Test fun `noise control change applies and reads back`() {
        val (sim, s) = session()
        s.readMode()
        val note = s.setAmbientSoundMode(ListeningMode.NOISE_CANCELLING, 20, false)
        assertTrue(note, note.startsWith("applied"))
        assertEquals(ListeningMode.NOISE_CANCELLING, s.readMode()!!.mode)
        assertEquals(0x00, sim.state[SonyProtocol.DATA_MDR, 0x67, 0x17]!![4].toInt())
    }

    @Test fun `support function lists parse for both connections`() {
        val le = SupportedFunctions.parse(hex(XM5_LE))!!
        val classic = SupportedFunctions.parse(hex(XM5_CLASSIC))!!
        assertEquals(46, le.size)
        assertEquals(46, classic.size)
        // Everything SunPods drives on a real XM5 is listed on both connections.
        for (fns in listOf(le, classic)) {
            val f = SupportedFunctions(fns, null)
            assertTrue(f.has1(Fn.LR_BATTERY_THRESHOLD)); assertTrue(f.has1(Fn.CRADLE_BATTERY_THRESHOLD))
            assertEquals(0x17, fns.firstNotNullOf { if (it in Fn.NC_ASM) Fn.ncAsmType(it) else null })
            assertTrue(f.has1(Fn.PRESET_EQ)); assertTrue(f.has1(Fn.UPSCALING_AUTO_OFF))
            assertTrue(f.has1(Fn.PLAYBACK_BY_WEARING)); assertTrue(f.has1(Fn.AUTO_POWER_OFF_WEARING))
            assertTrue(f.has1(Fn.SPEAK_TO_CHAT_2)); assertTrue(f.has1(Fn.ASSIGNABLE))
            assertTrue(f.has1(Fn.VOICE_ASSISTANT)); assertTrue(f.has1(Fn.WEARING_STATUS))
            assertTrue(f.has1(Fn.HEAD_GESTURE)); assertTrue(f.has1(Fn.RESET)); assertTrue(f.has1(Fn.POWER_OFF))
            assertTrue(f.has1(Fn.CLASSIC_LE_SETTING))
        }
        // Multipoint (general setting 2) is only listed over Classic.
        assertFalse(0xd2 in le)
        assertTrue(0xd2 in classic)
    }

    @Test fun `support function list reads from the earbuds`() {
        val (_, s) = session()
        val f = SupportedFunctions.read(s)
        assertNotNull(f)
        assertTrue(f!!.has1(0x6b))
        assertTrue(f.has2(0x42))
        assertTrue(f.has2(0x53))
    }

    @Test fun `support function parse rejects a bad length`() {
        assertEquals(null, SupportedFunctions.parse(hex("07 00 02 29 ff")))
    }

    @Test fun `full settings read matches the recorded earbuds`() {
        val (_, s) = session()
        val r = SonyFeatures.readAll(s, DeviceSettings())
        println(r)
        assertEquals("WF-1000XM5", r.modelName)
        assertEquals("6.1.0", r.firmware)
        assertEquals(0x01, r.colorCode)
        assertEquals(0x00, r.eqPresetCode)
        assertEquals(List(5) { 0 }, r.bands)
        assertEquals(true, r.speakToChat)
        assertEquals(false, r.pauseWhenRemoved)
        assertEquals(false, r.autoOffWhenRemoved)
        assertEquals(TouchFunction.AMBIENT_SOUND_CONTROL, r.touchLeft)
        assertEquals(TouchFunction.AMBIENT_SOUND_CONTROL, r.touchRight)
        assertNotNull(r.touchOptions)
        assertEquals(true, r.headGestures)
        assertEquals(NoiseCycle.from(0x03), r.noiseCycle)
        assertEquals(listOf("SIDETONE_SETTING", "MULTIPOINT_SETTING"), r.general!!.map { it.key })
        assertEquals(true, r.leModeSupported)
        assertNotNull(r.promptsOn)
    }

    @Test fun `eq preset change keeps the bands and reads back`() {
        val (_, s) = session()
        val r = SonyFeatures.readAll(s, DeviceSettings())
        val after = SonyFeatures.setEqPreset(s, r, EqPreset.BASS)
        assertEquals(EqPreset.BASS.code, after.eqPresetCode)
        val custom = SonyFeatures.setEqBands(s, after, 3, listOf(1, 2, 3, 4, 5))
        assertEquals(listOf(1, 2, 3, 4, 5), custom.bands)
        assertEquals(3, custom.clearBass)
    }

    @Test fun `touch volume is refused and the old layout restored`() {
        val (sim, s) = session()
        val r = SonyFeatures.readAll(s, DeviceSettings())
        val error = runCatching { SonyFeatures.setTouch(s, r, TouchFunction.VOLUME_CONTROL, null) }.exceptionOrNull()
        assertTrue(error is EarbudsUnavailableException)
        val now = sim.state[SonyProtocol.DATA_MDR, 0xf7, 0x03]!!
        assertEquals(0x00, now[3].toInt() and 0xff)
    }

    @Test fun `touch change is confirmed and applied`() {
        val (_, s) = session()
        val r = SonyFeatures.readAll(s, DeviceSettings())
        val after = SonyFeatures.setTouch(s, r, null, TouchFunction.PLAYBACK_CONTROL)
        assertEquals(TouchFunction.PLAYBACK_CONTROL, after.touchRight)
    }

    @Test fun `switching to classic is confirmed then the link drops`() {
        val (sim, s) = session()
        val r = SonyFeatures.readAll(s, DeviceSettings())
        assertTrue(SonyFeatures.setLeAudio(s, r, on = false))
        assertEquals(0x01, sim.state[SonyProtocol.DATA_MDR, 0x47, 0x0c]!![2].toInt())
        assertFalse(sim.isConnected)
    }

    @Test fun `multipoint needs the alert answered`() {
        val (sim, s) = session()
        val r = SonyFeatures.readAll(s, DeviceSettings())
        val slot = r.general!!.first { it.key == "MULTIPOINT_SETTING" }.slot
        val after = SonyFeatures.setGeneral(s, r, slot, on = false)
        assertEquals(false, after.general!!.first { it.slot == slot }.on)
        assertEquals(0x01, sim.state[SonyProtocol.DATA_MDR, 0xd7, slot]!![3].toInt())
    }

    @Test fun `power off drops the link`() {
        val (sim, s) = session()
        SonyFeatures.powerOff(s)
        assertTrue(sim.poweredOff)
    }

    private fun hex(s: String) = s.split(' ').map { it.toInt(16).toByte() }.toByteArray()

    companion object {
        /** Recorded from real WF-1000XM5 earbuds (firmware 6.1.0) over LE Audio and over Classic. */
        const val XM5_LE = "07 00 2e 10 ff 12 ff 13 ff 14 ff 15 ff 16 ff 17 ff 23 ff 25 36 11 ff 27 ff 29 ff 2a ff 92 ff 40 2e 42 3a 44 ff 50 11 6b 08 70 01 90 ff a2 13 c1 ff c2 ff e2 1b f1 38 fc 0d 93 ff f6 33 f9 41 d1 3b ff 2d 94 ff f3 26 b7 15 47 46 4c 48 4b ff 48 49 4a ff 49 ff e7 1a 34 3f f4 23 4e 47 4f ff"
        const val XM5_CLASSIC = "07 00 2e 10 ff 12 ff 13 ff 14 ff 15 ff 16 ff 17 ff 23 ff 25 36 11 ff 27 ff 29 ff 2a ff 92 ff 50 11 6b 08 70 01 90 ff a1 12 c1 ff c2 ff e2 1b f1 38 fc 0d 93 ff f6 33 f9 41 d1 3b 94 ff 40 2e 42 3a 44 ff ff 2d 19 ff f3 26 e7 1a d2 20 fd 2b b6 1d b0 42 b2 43 b8 1f b7 15 34 3f f4 23 f5 24"
    }
}
