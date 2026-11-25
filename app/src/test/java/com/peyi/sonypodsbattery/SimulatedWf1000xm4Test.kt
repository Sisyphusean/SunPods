package com.peyi.sonypodsbattery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SunPods against simulated WF-1000XM4 earbuds (recorded replies, firmware 1.4.2; see
 * Wf1000xm4Profile). The parts that differ from the WF-1000XM5: 8-byte noise control with the wind
 * switch, Speak-to-Chat type 02, touch presets with noise control on 00, voice prompts without volume.
 */
class SimulatedWf1000xm4Test {
    private fun session(color: Int = 0x01): Pair<SimulatedEarbuds, SonyBatteryClient.SonySession> {
        val sim = SimulatedEarbuds(Wf1000xm4Profile.build(color))
        return sim to SonyBatteryClient.SonySession(sim).also { it.initialize() }
    }

    private fun SimulatedEarbuds.asked(c: Int, t: Int, table: Int = SonyProtocol.DATA_MDR) =
        received.any { (tb, p) -> tb == table && (p[0].toInt() and 0xff) == c && p.size > 1 && (p[1].toInt() and 0xff) == t }

    private fun SimulatedEarbuds.sent(hex: String) = received.any { (_, p) -> hex(p) == hex }

    private fun hex(b: ByteArray) = b.joinToString(" ") { "%02x".format(it.toInt() and 0xff) }

    @Test fun `the model is recognised`() {
        assertEquals(EarbudModel.WF_1000XM4, EarbudModel.fromName("WF-1000XM4"))
        assertEquals(EarbudModel.WF_1000XM5, EarbudModel.fromName("WF-1000XM5"))
        assertFalse(EarbudModel.WF_1000XM4.headphones)
        assertTrue(EarbudModel.WF_1000XM4.namePattern.matcher("WF-1000XM4").matches())
    }

    @Test fun `battery for each earbud and the case`() {
        val (_, s) = session()
        val b = s.readBattery()
        assertEquals(97, b.left)
        assertEquals(98, b.right)
        assertEquals(83, b.case)
    }

    @Test fun `noise control reads the 8-byte type 0x15`() {
        val (_, s) = session()
        val m = s.readMode()!!
        assertEquals(ListeningMode.NOISE_CANCELLING, m.mode)
        assertEquals(15, m.ambientLevel)
        assertTrue(m.focusOnVoice)
        assertEquals(false, m.windAuto)
    }

    @Test fun `noise control changes send the wind byte back`() {
        val (sim, s) = session()
        s.readMode()
        val note = s.setAmbientSoundMode(ListeningMode.AMBIENT, 12, focusOnVoice = false)
        assertTrue(note, note.startsWith("applied"))
        assertTrue(sim.sent("68 15 01 01 01 02 00 0c"))
        val now = s.readMode()!!
        assertEquals(ListeningMode.AMBIENT, now.mode)
        assertEquals(12, now.ambientLevel)
        assertFalse(now.focusOnVoice)
    }

    @Test fun `automatic wind noise reduction`() {
        val (sim, s) = session()
        var r = SonyFeatures.readAll(s, DeviceSettings())
        assertTrue(r.has(Feature.WIND_REDUCTION))
        assertEquals(false, r.windAuto)
        r = SonyFeatures.setWindReduction(s, r, true)
        assertTrue(sim.sent("68 15 01 01 00 03 01 0f"))
        assertEquals(true, r.windAuto)
        // Fresh/reopened session (reportedMode == null): mode change reads mode first and keeps wind on.
        val s2 = SonyBatteryClient.SonySession(sim).also { it.initialize() }
        s2.setAmbientSoundMode(ListeningMode.NOISE_CANCELLING, 15, focusOnVoice = true)
        assertEquals(true, s2.readMode()!!.windAuto)
        r = SonyFeatures.setWindReduction(s2, r, false)
        assertEquals(false, r.windAuto)
    }

    @Test fun `truncated 0x15 packet is rejected by ModeReport parse`() {
        val short = byteArrayOf(0x67, 0x15, 0x01, 0x01, 0x00, 0x05, 0x0f)
        assertEquals(null, SonyBatteryClient.ModeReport.parse(short))
    }

    @Test fun `speak-to-chat on type 02 keeps voice focus`() {
        val (sim, s) = session()
        var r = SonyFeatures.readAll(s, DeviceSettings())
        assertEquals(false, r.speakToChat)
        assertEquals(SpeakToChatSensitivity.AUTO, r.speakToChatSensitivity)
        assertEquals(SpeakToChatTimeout.STANDARD, r.speakToChatTimeout)
        assertFalse(sim.asked(0xf6, 0x0c)); assertFalse(sim.asked(0xfa, 0x0c))
        r = SonyFeatures.setSpeakToChat(s, r, true)
        assertEquals(true, r.speakToChat)
        r = SonyFeatures.setSpeakToChatConfig(s, r, SpeakToChatSensitivity.HIGH, SpeakToChatTimeout.LONG)
        assertTrue(sim.sent("fc 02 01 01 02"))
        assertEquals(SpeakToChatSensitivity.HIGH, r.speakToChatSensitivity)
        assertEquals(SpeakToChatTimeout.LONG, r.speakToChatTimeout)
    }

    @Test fun `touch offers volume and writes noise control as 00`() {
        val (sim, s) = session()
        var r = SonyFeatures.readAll(s, DeviceSettings())
        assertEquals(TouchFunction.AMBIENT_SOUND_CONTROL, r.touchLeft)
        assertEquals(TouchFunction.PLAYBACK_CONTROL, r.touchRight)
        val options = r.touchOptions!!
        assertTrue(options[0]!!.any { it.function == TouchFunction.VOLUME_CONTROL })
        r = SonyFeatures.setTouch(s, r, TouchFunction.VOLUME_CONTROL, null)
        assertEquals(TouchFunction.VOLUME_CONTROL, r.touchLeft)
        // No side has noise control now: it goes back with the earbuds' own code, 00.
        r = SonyFeatures.setTouch(s, r, null, TouchFunction.AMBIENT_SOUND_CONTROL)
        assertTrue(sim.sent("f8 03 02 10 00"))
        assertEquals(TouchFunction.AMBIENT_SOUND_CONTROL, r.touchRight)
    }

    @Test fun `voice prompts without volume`() {
        val (sim, s) = session()
        var r = SonyFeatures.readAll(s, DeviceSettings())
        assertEquals(true, r.promptsOn)
        assertEquals(null, r.promptVolume)
        assertFalse(sim.asked(0x46, 0x20, SonyProtocol.DATA_MDR_NO2))
        r = SonyFeatures.setPrompts(s, r, false)
        assertTrue(sim.sent("48 01 01"))
        assertEquals(false, r.promptsOn)
    }

    @Test fun `multipoint connects two devices and lists paired devices`() {
        val (sim, s) = session()
        var r = SonyFeatures.readAll(s, DeviceSettings())
        assertEquals(setOf("MULTIPOINT_SETTING"), r.general!!.map { it.key }.toSet())
        assertEquals(false, r.general!!.first { it.key == "MULTIPOINT_SETTING" }.on)
        r = SonyFeatures.setGeneral(s, r, 0xd1, on = true)
        assertEquals(true, r.general!!.first { it.key == "MULTIPOINT_SETTING" }.on)
        assertTrue(sim.sent("98 00 07 01"))
        val paired = Multipoint.read(s)!!
        assertEquals(1, paired.list.size)
        assertEquals("Pixel", paired.list.first().name)
        assertEquals("AA:BB:CC:DD:EE:01", paired.list.first().address)
    }

    @Test fun `firmware 1_4_2 hides multipoint and re-probes on upgrade`() {
        val sim142 = SimulatedEarbuds(Wf1000xm4Profile.build())
        val t1 = SonyProtocol.DATA_MDR
        val t2 = SonyProtocol.DATA_MDR_NO2
        sim142.state[t1, 0x05, 0x02] = byteArrayOf(0x05, 0x02, 0x05, '1'.code.toByte(), '.'.code.toByte(), '4'.code.toByte(), '.'.code.toByte(), '2'.code.toByte())
        sim142.state[t1, 0x07, 0x00] = byteArrayOf(0x07, 0x00, 0x1b, 0x10, 0xff.toByte(), 0x12, 0xff.toByte(), 0x13, 0xff.toByte(), 0x14, 0xff.toByte(), 0x23, 0xff.toByte(), 0x25, 0x15, 0x11, 0xff.toByte(), 0x27, 0xff.toByte(), 0x29, 0xff.toByte(), 0x2a, 0xff.toByte(), 0x92.toByte(), 0xff.toByte(), 0x32, 0x1a, 0x50, 0x07, 0x68, 0x03, 0x70, 0x01, 0x90.toByte(), 0xff.toByte(), 0xa1.toByte(), 0x08, 0xc1.toByte(), 0xff.toByte(), 0xe1.toByte(), 0x09, 0xe2.toByte(), 0x0a, 0xf1.toByte(), 0x17, 0xf2.toByte(), 0x05, 0xf3.toByte(), 0x0f, 0xf4.toByte(), 0x0d, 0x93.toByte(), 0xff.toByte(), 0xf6.toByte(), 0x14, 0xf9.toByte(), 0x1b)
        for (ret in listOf(0xd1, 0xd3, 0xd7)) sim142.state.remove(t1, ret, 0xd1)
        sim142.state[t2, 0x07, 0x00] = byteArrayOf(0x07, 0x00, 0x01, 0x41, 0x18)
        for (ret in listOf(0x31, 0x33, 0x37)) {
            sim142.state.remove(t2, ret, 0x00)
            sim142.state.remove(t2, ret, 0x02)
        }
        val s142 = SonyBatteryClient.SonySession(sim142).also { it.initialize() }
        val r142 = SonyFeatures.readAll(s142, DeviceSettings())
        assertEquals("1.4.2", r142.firmware)
        assertEquals(emptyList<GeneralSetting>(), r142.general)
        assertEquals(null, Multipoint.read(s142))
        // Upgrading firmware to 2.1.0 resets the cached empty general list and discovers multipoint.
        val (_, s210) = session()
        val r210 = SonyFeatures.readAll(s210, r142)
        assertEquals("2.1.0", r210.firmware)
        assertEquals(setOf("MULTIPOINT_SETTING"), r210.general!!.map { it.key }.toSet())
    }

    @Test fun `full read shows what the earbuds have and nothing they lack`() {
        val (sim, s) = session(color = 0x03)
        val r = SonyFeatures.readAll(s, DeviceSettings())
        assertEquals("WF-1000XM4", r.modelName)
        assertEquals("2.1.0", r.firmware)
        assertEquals(0x03, r.colorCode)
        assertEquals("LDAC", r.codec)
        assertEquals(true, r.dseeOn)
        for (f in listOf(Feature.TOUCH, Feature.DSEE, Feature.SPEAK_TO_CHAT, Feature.PAUSE_WHEN_REMOVED, Feature.FIT_TEST, Feature.VOICE_PROMPTS, Feature.CODEC))
            assertTrue(f.name, r.has(f))
        for (f in listOf(Feature.HEAD_GESTURES, Feature.LE_AUDIO_SWITCH, Feature.SAFE_LISTENING, Feature.NC_AMB_BUTTON))
            assertFalse(f.name, r.has(f))
        assertFalse(sim.asked(0x46, 0x0c))
    }
}
