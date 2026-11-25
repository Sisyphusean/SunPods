package com.peyi.sonypodsbattery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SunPods against simulated WH-1000XM6 headphones (reconstructed from other projects' captures and
 * Sound Connect's layouts; see Wh1000xm6Profile). One battery, noise control type 0x19 with Auto
 * Ambient Sound, a 10-band EQ with its own presets, sound quality mode on type 05, the touch panel
 * switch in slot d3, and nothing the headphones lack is asked for.
 */
class SimulatedWh1000xm6Test {
    private fun session(color: Int = 0x01): Pair<SimulatedEarbuds, SonyBatteryClient.SonySession> {
        val sim = SimulatedEarbuds(Wh1000xm6Profile.build(color))
        return sim to SonyBatteryClient.SonySession(sim).also { it.initialize() }
    }

    private fun SimulatedEarbuds.asked(c: Int, t: Int, table: Int = SonyProtocol.DATA_MDR) =
        received.any { (tb, p) -> tb == table && (p[0].toInt() and 0xff) == c && p.size > 1 && (p[1].toInt() and 0xff) == t }

    private fun SimulatedEarbuds.sent(hex: String) = received.any { (_, p) -> hex(p) == hex }

    private fun hex(b: ByteArray) = b.joinToString(" ") { "%02x".format(it.toInt() and 0xff) }

    @Test fun `the model is recognised as headphones, on Classic and LE Audio`() {
        assertEquals(EarbudModel.WH_1000XM6, EarbudModel.fromName("WH-1000XM6"))
        assertEquals(EarbudModel.WH_1000XM6, EarbudModel.fromName("LE_WH-1000XM6"))
        assertTrue(EarbudModel.WH_1000XM6.matchesLe("LE_WH-1000XM6"))
        assertTrue(SonyBatteryClient.isTargetModel("LE_WH-1000XM6"))
        assertEquals(EarbudModel.WH_1000XM5, EarbudModel.fromName("WH-1000XM5"))
        assertNull(EarbudModel.fromName("WF-1000XM6"))
        assertTrue(EarbudModel.WH_1000XM6.headphones)
        assertEquals("headphones", EarbudModel.WH_1000XM6.noun)
        assertEquals(0.0, EarbudModel.WH_1000XM6.caseCharges, 0.0)
        assertTrue(EarbudModel.WH_1000XM6.namePattern.matcher("WH-1000XM6").matches())
    }

    @Test fun `one battery, no case`() {
        val (sim, s) = session()
        val b = s.readBattery()
        assertEquals(80, b.left)
        assertNull(b.right)
        assertNull(b.case)
        assertFalse(b.leftCharging)
        assertTrue(sim.asked(0x22, 0x00))
        assertFalse(sim.asked(0x22, 0x09))
        assertFalse(sim.asked(0x22, 0x0a))
    }

    @Test fun `noise control reads the 9-byte type 0x19`() {
        val (sim, s) = session()
        val m = s.readMode()!!
        assertEquals(ListeningMode.NOISE_CANCELLING, m.mode)
        assertEquals(20, m.ambientLevel)
        assertFalse(m.focusOnVoice)
        assertEquals(false, m.autoAmbient)
        assertEquals(0x00, m.naSens)
        assertNull(m.windAuto)
        assertTrue(sim.asked(0x66, 0x19))
        assertFalse(sim.asked(0x66, 0x17))
    }

    @Test fun `noise control changes send 9 bytes with Auto Ambient Sound back`() {
        val (sim, s) = session()
        val note = s.setAmbientSoundMode(ListeningMode.AMBIENT, 10, focusOnVoice = false)
        assertTrue(note, note.startsWith("applied"))
        assertTrue(sim.sent("68 19 01 01 01 00 0a 00 00"))
        val now = s.readMode()!!
        assertEquals(ListeningMode.AMBIENT, now.mode)
        assertEquals(10, now.ambientLevel)
        // Off keeps the reported Auto Ambient Sound bytes.
        s.setAmbientSoundMode(ListeningMode.OFF, 10, focusOnVoice = false)
        assertTrue(sim.sent("68 19 01 00 00 00 0a 00 00"))
        assertEquals(ListeningMode.OFF, s.readMode()!!.mode)
    }

    @Test fun `auto ambient sound and its sensitivity`() {
        val (sim, s) = session()
        var r = SonyFeatures.readAll(s, DeviceSettings())
        assertTrue(r.has(Feature.AUTO_AMBIENT))
        assertEquals(false, r.autoAmbient)
        assertEquals(AutoAmbientSensitivity.STANDARD, r.autoAmbientSensitivity)
        s.setAmbientSoundMode(ListeningMode.AMBIENT, 10, focusOnVoice = false)
        r = SonyFeatures.setAutoAmbient(s, r, true)
        assertTrue(sim.sent("68 19 01 01 01 00 0a 01 00"))
        assertEquals(true, r.autoAmbient)
        r = SonyFeatures.setAutoAmbientSensitivity(s, r, AutoAmbientSensitivity.HIGH)
        assertTrue(sim.sent("68 19 01 01 01 00 0a 01 01"))
        assertEquals(AutoAmbientSensitivity.HIGH, r.autoAmbientSensitivity)
        // A later mode change on a fresh/reopened session (reportedMode == null) reads mode first and keeps both.
        val s2 = SonyBatteryClient.SonySession(sim).also { it.initialize() }
        s2.setAmbientSoundMode(ListeningMode.AMBIENT, 12, focusOnVoice = true)
        assertTrue(sim.sent("68 19 01 01 01 01 0c 01 01"))
        val now = s2.readMode()!!
        assertEquals(true, now.autoAmbient)
        assertEquals(0x01, now.naSens)
        r = SonyFeatures.setAutoAmbient(s2, r, false)
        assertEquals(false, r.autoAmbient)
    }

    @Test fun `truncated 0x19 packet is rejected by ModeReport parse`() {
        val short = byteArrayOf(0x67, 0x19, 0x01, 0x01, 0x00, 0x00, 0x14, 0x00)
        assertNull(SonyBatteryClient.ModeReport.parse(short))
    }

    @Test fun `ten-band EQ read from the headphones' own layout`() {
        val (sim, s) = session()
        val r = SonyFeatures.readAll(s, DeviceSettings())
        assertTrue(sim.sent("50 04 01"))
        assertTrue(sim.asked(0x5a, 0x00))
        val layout = r.eqLayout
        assertTrue(layout.described)
        assertEquals(10, layout.count)
        assertEquals(13, layout.steps)
        assertEquals(-6, layout.min)
        assertEquals(6, layout.max)
        assertFalse(layout.clearBass)
        assertEquals(listOf("31", "63", "125", "250", "500", "1k", "2k", "4k", "8k", "16k"), layout.labels)
        assertEquals(0xa0, r.eqPresetCode)
        assertEquals("Custom", layout.label(r.eqPresetCode))
        assertNull(r.clearBass)
        assertEquals(listOf(2, 1, 0, 0, 0, 0, 0, 1, 2, 2), r.bands)
        // Only the presets the headphones list; never the classic ones they ignore.
        val codes = layout.shown(r.eqPresetCode).map { it.first }
        assertEquals(listOf(0x00, 0x30, 0x31, 0x32, 0x33, 0xa0, 0xa1, 0xa2, 0xa3), codes)
        assertTrue(codes.none { it in 0x10..0x17 })
        assertEquals(listOf("Off", "Heavy", "Clear", "Hard", "Soft", "Custom", "User 1", "User 2", "User 3"), layout.shown(r.eqPresetCode).map { it.second })
        assertEquals(0xa0, layout.customise)
    }

    @Test fun `EQ preset and ten custom bands, byte for byte`() {
        val (sim, s) = session()
        var r = SonyFeatures.readAll(s, DeviceSettings())
        r = SonyFeatures.setEqPreset(s, r, 0x31)
        assertTrue(sim.sent("58 00 31 00"))
        assertEquals(0x31, r.eqPresetCode)
        assertEquals("Clear", r.eqLayout.label(r.eqPresetCode))
        assertFalse(r.eqLayout.isEditable(r.eqPresetCode))
        // From a fixed preset, bands go to Custom (0xa0); dB + 6 on the wire, clamped to -6..+6.
        r = SonyFeatures.setEqBands(s, r, null, listOf(3, 2, 1, 0, -1, -2, 0, 1, 2, 9))
        assertTrue(sim.sent("58 00 a0 0a 09 08 07 06 05 04 06 07 08 0c"))
        assertEquals(0xa0, r.eqPresetCode)
        assertEquals(listOf(3, 2, 1, 0, -1, -2, 0, 1, 2, 6), r.bands)
        assertNull(r.clearBass)
        // A user preset keeps its own slot.
        r = SonyFeatures.setEqPreset(s, r, 0xa2)
        r = SonyFeatures.setEqBands(s, r, null, List(10) { -6 })
        assertTrue(sim.sent("58 00 a2 0a 00 00 00 00 00 00 00 00 00 00"))
        assertEquals(List(10) { -6 }, r.bands)
    }

    @Test fun `a classic preset is acknowledged and ignored`() {
        val (sim, s) = session()
        var r = SonyFeatures.readAll(s, DeviceSettings())
        r = SonyFeatures.setEqPreset(s, r, EqPreset.BRIGHT)
        assertTrue(sim.sent("58 00 10 00"))
        assertEquals(0xa0, r.eqPresetCode)
    }

    @Test fun `sound quality mode on type 05`() {
        val (sim, s) = session()
        var r = SonyFeatures.readAll(s, DeviceSettings())
        assertEquals(ConnectionPriority.SOUND_QUALITY, r.priority)
        assertTrue(sim.asked(0xe6, 0x05))
        assertFalse(sim.asked(0xe6, 0x00)); assertFalse(sim.asked(0xe6, 0x02))
        r = SonyFeatures.setPriority(s, r, ConnectionPriority.STABLE_CONNECTION)
        assertTrue(sim.sent("e8 05 01 00"))
        assertEquals(ConnectionPriority.STABLE_CONNECTION, r.priority)
        // Low latency (LE Audio) is shown when the headphones report it.
        sim.state[SonyProtocol.DATA_MDR, 0xe7, 0x05] = byteArrayOf(0xe7.toByte(), 0x05, 0x02)
        r = SonyFeatures.readAll(s, r)
        assertEquals(ConnectionPriority.LOW_LATENCY, r.priority)
    }

    @Test fun `touch panel switch in slot d3 and two devices`() {
        val (sim, s) = session()
        var r = SonyFeatures.readAll(s, DeviceSettings())
        val touch = r.general!!.first { it.key == "TOUCH_PANEL_SETTING" }
        assertEquals(0xd3, touch.slot)
        assertEquals("Touch Controls", touch.title)
        assertEquals(true, touch.on)
        r = SonyFeatures.setGeneral(s, r, 0xd3, on = false)
        assertTrue(sim.sent("d8 d3 00 01"))
        assertEquals(false, r.general!!.first { it.key == "TOUCH_PANEL_SETTING" }.on)
        r = SonyFeatures.setGeneral(s, r, 0xd2, on = true)
        assertEquals(true, r.general!!.first { it.key == "MULTIPOINT_SETTING" }.on)
        assertTrue(sim.sent("98 00 07 01"))
        val paired = Multipoint.read(s)!!
        assertEquals(1, paired.list.size)
        assertEquals("Pixel", paired.list.first().name)
        assertEquals(SafeListening.Level.Db(49), SafeListening.readLevel(s))
    }

    @Test fun `full read shows what the headphones have and nothing they lack`() {
        val (sim, s) = session(color = 0x05)
        val r = SonyFeatures.readAll(s, DeviceSettings())
        assertEquals("WH-1000XM6", r.modelName)
        assertEquals("3.1.5", r.firmware)
        assertEquals(0x05, r.colorCode)
        assertEquals("LDAC", r.codec)
        assertEquals(NoiseCycle.NC_AMBIENT, r.noiseCycle)
        assertEquals(setOf("SIDETONE_SETTING", "MULTIPOINT_SETTING", "TOUCH_PANEL_SETTING"), r.general!!.map { it.key }.toSet())
        assertEquals(true, r.pauseWhenRemoved)
        assertEquals(false, r.headGestures)
        assertEquals(false, r.speakToChat)
        assertEquals(false, r.dseeOn)
        assertEquals(true, r.promptsOn)
        for (f in listOf(
            Feature.NC_AMB_BUTTON, Feature.AUTO_AMBIENT, Feature.EQ_DESCRIBED, Feature.DSEE, Feature.SPEAK_TO_CHAT,
            Feature.PAUSE_WHEN_REMOVED, Feature.AUTO_OFF_WHEN_REMOVED, Feature.HEAD_GESTURES, Feature.CODEC,
            Feature.VOICE_PROMPTS, Feature.SAFE_LISTENING, Feature.LE_AUDIO_SWITCH, Feature.POWER_OFF, Feature.RESET
        )) assertTrue(f.name, r.has(f))
        for (f in listOf(Feature.TOUCH, Feature.FIT_TEST, Feature.WIND_REDUCTION))
            assertFalse(f.name, r.has(f))
        assertNull(r.windAuto)
        // Every LE-capable device offers SunPods' LE Audio switch and onboarding flow.
        assertEquals(true, r.leModeSupported)
        assertTrue(r.leSwitchOffered)
        // The per-side touch read and the earbud noise cycle were never asked for.
        assertFalse(sim.asked(0xf6, 0x03)); assertFalse(sim.asked(0xfa, 0x03))
        assertTrue(sim.asked(0x66, 0x30))
    }

    @Test fun `leAddresses parses both 2-address headphones and 3-address earbuds`() {
        val mac1 = "11:22:33:44:55:66"
        val mac2 = "AA:BB:CC:DD:EE:FF"
        val mac3 = "C0:11:22:33:44:55"
        val reply2 = (byteArrayOf(0x41, 0x00) + "$mac1$mac2".toByteArray(Charsets.US_ASCII))
            .joinToString(" ") { "%02x".format(it.toInt() and 0xff) }
        val reply3 = (byteArrayOf(0x41, 0x00) + "$mac1$mac2$mac3".toByteArray(Charsets.US_ASCII))
            .joinToString(" ") { "%02x".format(it.toInt() and 0xff) }
        val base = "12 01 00 03 00 30 13 00 00\n12 05 01 0a 57 48 2d 31 30 30 30 58 4d 36"

        val s2 = SonyBatteryClient.SonySession(SimulatedEarbuds(SimProfile(EarbudModel.WH_1000XM6, "$base\n12 $reply2")))
            .also { it.initialize() }
        assertEquals(listOf(mac1, mac2), SonyFeatures.leAddresses(s2))

        val s3 = SonyBatteryClient.SonySession(SimulatedEarbuds(SimProfile(EarbudModel.WH_1000XM6, "$base\n12 $reply3")))
            .also { it.initialize() }
        assertEquals(listOf(mac1, mac2, mac3), SonyFeatures.leAddresses(s3))
    }

    @Test fun `art and simulator entry`() {
        val art = budArt(0x05, EarbudModel.WH_1000XM6)
        assertEquals("wh1000xm6", art.device)
        assertEquals("blue", art.hero3d)
        assertEquals("black", budArt(0x01, EarbudModel.WH_1000XM6).hero3d)
        assertEquals("silver", budArt(0x13, EarbudModel.WH_1000XM6).hero3d)
        assertEquals("pink", budArt(0x06, EarbudModel.WH_1000XM6).hero3d)
        assertEquals(EarbudModel.WH_1000XM6, SimProfiles.forModel(EarbudModel.WH_1000XM6, 0x01).model)
    }
}
