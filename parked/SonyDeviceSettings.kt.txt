package com.peyi.sonypodsbattery

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException

// Command bytes for the WF-1000XM5 (Sony MDR protocol v2), cross-checked against
// Gadgetbridge's open protocol notes. Every GET answers with code + 1 (RET) and the
// earbuds may also push code + 3 (NOTIFY) with the same layout.

enum class EqPreset(val code: Int, val label: String) {
    OFF(0x00, "Off"),
    BRIGHT(0x10, "Bright"),
    EXCITED(0x11, "Excited"),
    MELLOW(0x12, "Mellow"),
    RELAXED(0x13, "Relaxed"),
    VOCAL(0x14, "Vocal"),
    TREBLE_BOOST(0x15, "Treble boost"),
    BASS_BOOST(0x16, "Bass boost"),
    SPEECH(0x17, "Speech"),
    MANUAL(0xa0, "Manual"),
    CUSTOM_1(0xa1, "Custom 1"),
    CUSTOM_2(0xa2, "Custom 2");

    companion object {
        fun fromCode(code: Int): EqPreset? = entries.firstOrNull { it.code == code }
        val selectable = listOf(OFF, BRIGHT, EXCITED, MELLOW, RELAXED, VOCAL, TREBLE_BOOST, BASS_BOOST, SPEECH, CUSTOM_1, CUSTOM_2)
    }
}

enum class TouchFunction(val code: Int, val label: String, val detail: String) {
    AMBIENT_CONTROL(0x00, "Noise control", "Tap to cycle noise cancelling and ambient sound"),
    PLAYBACK(0x20, "Playback", "Play, pause, skip, call answer"),
    VOLUME(0x10, "Volume", "Tap to raise, hold to lower"),
    OFF(0xff, "Not assigned", "Taps do nothing on this side");

    companion object {
        fun fromCode(code: Int): TouchFunction? = entries.firstOrNull { it.code == code }
    }
}

enum class NoiseCycle(val code: Int, val label: String) {
    NC_AMBIENT_OFF(0x01, "Noise cancelling, Ambient, Off"),
    NC_AMBIENT(0x02, "Noise cancelling, Ambient"),
    NC_OFF(0x03, "Noise cancelling, Off"),
    AMBIENT_OFF(0x04, "Ambient, Off");

    companion object {
        fun fromCode(code: Int): NoiseCycle? = entries.firstOrNull { it.code == code }
    }
}

enum class ChatSensitivity(val code: Int, val label: String) {
    AUTO(0x00, "Auto"), HIGH(0x01, "High"), LOW(0x02, "Low");

    companion object {
        fun fromCode(code: Int): ChatSensitivity? = entries.firstOrNull { it.code == code }
    }
}

enum class ChatTimeout(val code: Int, val label: String) {
    SHORT(0x00, "Short (about 5 s)"),
    STANDARD(0x01, "Standard (about 15 s)"),
    LONG(0x02, "Long (about 30 s)"),
    OFF(0x03, "Stay on until I end it");

    companion object {
        fun fromCode(code: Int): ChatTimeout? = entries.firstOrNull { it.code == code }
    }
}

enum class AutoPowerOff(val a: Int, val b: Int, val label: String) {
    WHEN_TAKEN_OFF(0x10, 0x00, "When both earbuds are out"),
    OFF(0x11, 0x00, "Never");

    companion object {
        fun fromCode(a: Int, b: Int): AutoPowerOff? = entries.firstOrNull { it.a == a && it.b == b }
    }
}

data class EqualizerState(
    val preset: EqPreset = EqPreset.OFF,
    val clearBass: Int = 0,
    val bands: List<Int> = listOf(0, 0, 0, 0, 0)
) {
    companion object {
        val BAND_LABELS = listOf("400", "1k", "2.5k", "6.3k", "16k")
        const val MIN = -10
        const val MAX = 10
    }
}

data class DeviceSettings(
    val equalizer: EqualizerState? = null,
    val dseeExtreme: Boolean? = null,
    val speakToChat: Boolean? = null,
    val chatSensitivity: ChatSensitivity? = null,
    val chatTimeout: ChatTimeout? = null,
    val pauseWhenTakenOff: Boolean? = null,
    val autoPowerOff: AutoPowerOff? = null,
    val touchLeft: TouchFunction? = null,
    val touchRight: TouchFunction? = null,
    val noiseCycle: NoiseCycle? = null,
    val codec: String? = null,
    val firmware: String? = null,
    val readAt: Long = 0L
)

/** Pure decoders, kept separate so they can be unit tested without Bluetooth. */
object SonySettingsCodec {
    private fun u(b: Byte) = b.toInt() and 0xff

    fun equalizer(p: ByteArray): EqualizerState? {
        if (p.size != 10 || (u(p[0]) != 0x57 && u(p[0]) != 0x59) || u(p[1]) != 0x00) return null
        val preset = EqPreset.fromCode(u(p[2])) ?: return null
        fun level(b: Byte) = (u(b) - 10).coerceIn(EqualizerState.MIN, EqualizerState.MAX)
        return EqualizerState(preset, level(p[4]), (5..9).map { level(p[it]) })
    }

    fun dsee(p: ByteArray): Boolean? =
        if (p.size == 3 && u(p[1]) == 0x01 && u(p[2]) in 0..1) u(p[2]) == 1 else null

    /** Sony encodes several on/off switches inverted: 0x00 means on. */
    fun invertedSwitch(p: ByteArray): Boolean? = when (u(p.getOrElse(2) { 0x7f })) {
        0x00 -> true
        0x01 -> false
        else -> null
    }

    fun touch(p: ByteArray): Pair<TouchFunction, TouchFunction>? {
        if (p.size != 5 || u(p[1]) != 0x03) return null
        val left = TouchFunction.fromCode(if (u(p[3]) == 0x35) 0x00 else u(p[3])) ?: return null
        val right = TouchFunction.fromCode(if (u(p[4]) == 0x35) 0x00 else u(p[4])) ?: return null
        return left to right
    }

    fun noiseCycle(p: ByteArray): NoiseCycle? =
        if (p.size == 7 && u(p[1]) == 0x03) NoiseCycle.fromCode(u(p[6])) else null

    fun chatConfig(p: ByteArray): Pair<ChatSensitivity, ChatTimeout>? {
        if (p.size != 4 || u(p[1]) != 0x0c) return null
        val s = ChatSensitivity.fromCode(u(p[2])) ?: return null
        val t = ChatTimeout.fromCode(u(p[3])) ?: return null
        return s to t
    }

    fun autoPowerOff(p: ByteArray): AutoPowerOff? =
        if (p.size == 4 && u(p[1]) == 0x05) AutoPowerOff.fromCode(u(p[2]), u(p[3])) else null

    fun codec(p: ByteArray): String? {
        if (p.size != 3 || u(p[1]) != 0x02) return null
        return when (u(p[2])) {
            0x01 -> "SBC"
            0x02 -> "AAC"
            0x10 -> "LDAC"
            0x20 -> "aptX"
            0x21 -> "aptX HD"
            0x30 -> "LC3"
            0x00 -> null
            else -> "Codec 0x%02x".format(u(p[2]))
        }
    }

    fun firmware(p: ByteArray): String? {
        if (p.size < 4 || u(p[1]) != 0x02) return null
        val length = u(p[2])
        if (p.size < 3 + length) return null
        return String(p, 3, length, Charsets.US_ASCII).takeIf { it.isNotBlank() }
    }

    fun eqSetPreset(preset: EqPreset) = bytes(0x58, 0x00, preset.code, 0x00)

    fun eqSetBands(clearBass: Int, bands: List<Int>): ByteArray {
        require(bands.size == 5)
        fun enc(v: Int) = v.coerceIn(EqualizerState.MIN, EqualizerState.MAX) + 10
        return bytes(0x58, 0x00, 0xa0, 0x06, enc(clearBass), *bands.map(::enc).toIntArray())
    }

    fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
}

/** Reads and writes XM5 settings on an already open session. */
internal class SonySettingsSession(private val session: SonyBatteryClient.SonySession) {
    private fun u(b: Byte) = b.toInt() and 0xff

    private fun get(request: ByteArray, replyCodes: Set<Int>, sub: Int, timeoutMs: Long = 1_500): ByteArray? {
        session.send(request)
        return session.waitFor(timeoutMs) { frame ->
            frame.type == SonyProtocol.DATA_MDR && frame.payload.size >= 2 &&
                u(frame.payload[0]) in replyCodes && u(frame.payload[1]) == sub
        }?.payload
    }

    private fun set(payload: ByteArray, what: String) {
        session.send(payload)
        session.waitFor(2_500) { it.type == SonyProtocol.ACK }
            ?: throw IOException("Earbuds did not confirm $what")
    }

    private fun b(vararg v: Int) = SonySettingsCodec.bytes(*v)

    fun readAll(): DeviceSettings {
        val eq = get(b(0x56, 0x00), setOf(0x57, 0x59), 0x00)?.let(SonySettingsCodec::equalizer)
        val dsee = get(b(0xe6, 0x01), setOf(0xe7, 0xe9), 0x01)?.let(SonySettingsCodec::dsee)
        val chat = get(b(0xf6, 0x0c), setOf(0xf7, 0xf9), 0x0c)
            ?.takeIf { it.size == 4 }?.let(SonySettingsCodec::invertedSwitch)
        val chatConfig = get(b(0xfa, 0x0c), setOf(0xfb, 0xfd), 0x0c)?.let(SonySettingsCodec::chatConfig)
        val pause = (get(b(0xf6, 0x01), setOf(0xf7, 0xf9), 0x01, 1_000)
            ?: get(b(0x26, 0x01), setOf(0x27, 0x29, 0xf7, 0xf9), 0x01, 1_000))
            ?.takeIf { it.size == 3 }?.let(SonySettingsCodec::invertedSwitch)
        val power = get(b(0x26, 0x05), setOf(0x27, 0x29), 0x05)?.let(SonySettingsCodec::autoPowerOff)
        val touch = get(b(0xf6, 0x03), setOf(0xf7, 0xf9), 0x03)?.let(SonySettingsCodec::touch)
        val cycle = get(b(0xfa, 0x03), setOf(0xfb, 0xfd), 0x03)?.let(SonySettingsCodec::noiseCycle)
        val codec = get(b(0x12, 0x02), setOf(0x13, 0x15), 0x02)?.let(SonySettingsCodec::codec)
        val firmware = get(b(0x04, 0x02), setOf(0x05), 0x02)?.let(SonySettingsCodec::firmware)
        return DeviceSettings(
            equalizer = eq, dseeExtreme = dsee,
            speakToChat = chat, chatSensitivity = chatConfig?.first, chatTimeout = chatConfig?.second,
            pauseWhenTakenOff = pause, autoPowerOff = power,
            touchLeft = touch?.first, touchRight = touch?.second, noiseCycle = cycle,
            codec = codec, firmware = firmware, readAt = System.currentTimeMillis()
        )
    }

    fun setEqPreset(preset: EqPreset) = set(SonySettingsCodec.eqSetPreset(preset), "the equaliser preset")
    fun setEqBands(clearBass: Int, bands: List<Int>) = set(SonySettingsCodec.eqSetBands(clearBass, bands), "the equaliser")
    fun setDsee(on: Boolean) = set(b(0xe8, 0x01, if (on) 1 else 0), "DSEE Extreme")
    fun setSpeakToChat(on: Boolean) = set(b(0xf8, 0x0c, if (on) 0 else 1, 0x01), "Speak-to-Chat")
    fun setChatConfig(s: ChatSensitivity, t: ChatTimeout) = set(b(0xfc, 0x0c, s.code, t.code), "Speak-to-Chat settings")
    fun setPauseWhenTakenOff(on: Boolean) = set(b(0xf8, 0x01, if (on) 0 else 1), "auto pause")
    fun setAutoPowerOff(mode: AutoPowerOff) = set(b(0x28, 0x05, mode.a, mode.b), "auto power off")
    fun setTouch(left: TouchFunction, right: TouchFunction) = set(b(0xf8, 0x03, 0x02, left.code, right.code), "touch controls")
    fun setNoiseCycle(cycle: NoiseCycle) = set(b(0xfc, 0x03, 0x01, 0x00, 0x01, 0x00, cycle.code), "the noise control cycle")
    fun powerOff() = set(b(0x24, 0x03, 0x01), "power off")
}

object DeviceSettingsStore {
    private val _state = MutableStateFlow(DeviceSettings())
    val state: StateFlow<DeviceSettings> = _state.asStateFlow()
    private const val PREFS = "device_settings"
    @Volatile private var loaded = false

    fun current(context: Context): DeviceSettings {
        ensureLoaded(context)
        return _state.value
    }

    fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            _state.value = load(context)
            loaded = true
        }
    }

    fun update(context: Context, transform: (DeviceSettings) -> DeviceSettings) {
        ensureLoaded(context)
        val next = synchronized(this) { transform(_state.value).also { _state.value = it } }
        save(context, next)
    }

    /** Merge a fresh read, keeping old values for anything the earbuds did not answer. */
    fun merge(context: Context, fresh: DeviceSettings) = update(context) { old ->
        DeviceSettings(
            equalizer = fresh.equalizer ?: old.equalizer,
            dseeExtreme = fresh.dseeExtreme ?: old.dseeExtreme,
            speakToChat = fresh.speakToChat ?: old.speakToChat,
            chatSensitivity = fresh.chatSensitivity ?: old.chatSensitivity,
            chatTimeout = fresh.chatTimeout ?: old.chatTimeout,
            pauseWhenTakenOff = fresh.pauseWhenTakenOff ?: old.pauseWhenTakenOff,
            autoPowerOff = fresh.autoPowerOff ?: old.autoPowerOff,
            touchLeft = fresh.touchLeft ?: old.touchLeft,
            touchRight = fresh.touchRight ?: old.touchRight,
            noiseCycle = fresh.noiseCycle ?: old.noiseCycle,
            codec = fresh.codec ?: old.codec,
            firmware = fresh.firmware ?: old.firmware,
            readAt = fresh.readAt
        )
    }

    private fun load(context: Context): DeviceSettings {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        fun int(key: String) = if (p.contains(key)) p.getInt(key, 0) else null
        fun bool(key: String) = if (p.contains(key)) p.getBoolean(key, false) else null
        val eq = int("eq_preset")?.let(EqPreset::fromCode)?.let { preset ->
            EqualizerState(
                preset,
                p.getInt("eq_bass", 0),
                (0 until 5).map { p.getInt("eq_band_$it", 0) }
            )
        }
        return DeviceSettings(
            equalizer = eq,
            dseeExtreme = bool("dsee"),
            speakToChat = bool("chat"),
            chatSensitivity = int("chat_sens")?.let(ChatSensitivity::fromCode),
            chatTimeout = int("chat_timeout")?.let(ChatTimeout::fromCode),
            pauseWhenTakenOff = bool("pause"),
            autoPowerOff = p.getString("power_off", null)?.let { name -> AutoPowerOff.entries.firstOrNull { it.name == name } },
            touchLeft = int("touch_left")?.let(TouchFunction::fromCode),
            touchRight = int("touch_right")?.let(TouchFunction::fromCode),
            noiseCycle = int("cycle")?.let(NoiseCycle::fromCode),
            codec = p.getString("codec", null),
            firmware = p.getString("firmware", null),
            readAt = p.getLong("read_at", 0L)
        )
    }

    private fun save(context: Context, s: DeviceSettings) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            clear()
            s.equalizer?.let { eq ->
                putInt("eq_preset", eq.preset.code)
                putInt("eq_bass", eq.clearBass)
                eq.bands.forEachIndexed { i, v -> putInt("eq_band_$i", v) }
            }
            s.dseeExtreme?.let { putBoolean("dsee", it) }
            s.speakToChat?.let { putBoolean("chat", it) }
            s.chatSensitivity?.let { putInt("chat_sens", it.code) }
            s.chatTimeout?.let { putInt("chat_timeout", it.code) }
            s.pauseWhenTakenOff?.let { putBoolean("pause", it) }
            s.autoPowerOff?.let { putString("power_off", it.name) }
            s.touchLeft?.let { putInt("touch_left", it.code) }
            s.touchRight?.let { putInt("touch_right", it.code) }
            s.noiseCycle?.let { putInt("cycle", it.code) }
            s.codec?.let { putString("codec", it) }
            s.firmware?.let { putString("firmware", it) }
            putLong("read_at", s.readAt)
        }.apply()
    }
}
