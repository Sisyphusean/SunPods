package com.peyi.sonypodsbattery

import android.content.Context
import org.json.JSONObject
import java.io.IOException

/*
 * WF-1000XM5 settings over Sony's MDR v2 ("table 1") protocol.
 *
 * Command bytes come from Sony's own command table (Command / *InquiredType enums in
 * Sound Connect 13.2.1). Every feature follows the same shape:
 *   GET_PARAM = c, RET_PARAM = c + 1, SET_PARAM = c + 2, NTFY_PARAM = c + 3
 *   payload  = [command, inquiredType, fields...]
 * Setters echo the layout the earbuds reported (when we have it) so unknown
 * fields are preserved, then read the value back so the UI always shows what
 * the earbuds actually applied.
 */

enum class EqPreset(val code: Int, val label: String) {
    OFF(0x00, "Off"),
    BRIGHT(0x10, "Bright"),
    EXCITED(0x11, "Excited"),
    MELLOW(0x12, "Mellow"),
    RELAXED(0x13, "Relaxed"),
    VOCAL(0x14, "Vocal"),
    TREBLE(0x15, "Treble boost"),
    BASS(0x16, "Bass boost"),
    SPEECH(0x17, "Speech"),
    MANUAL(0xa0, "Manual"),
    CUSTOM_1(0xa1, "Custom 1"),
    CUSTOM_2(0xa2, "Custom 2");

    val isCustomisable: Boolean get() = code >= 0xa0

    companion object {
        fun from(code: Int?): EqPreset? = entries.firstOrNull { it.code == code }
    }
}

enum class TouchFunction(val code: Int, val label: String, val short: String) {
    AMBIENT_SOUND_CONTROL(0x00, "Ambient sound control", "Ambient"),
    PLAYBACK_CONTROL(0x20, "Playback control", "Playback"),
    VOLUME_CONTROL(0x10, "Volume control", "Volume"),
    NONE(0xff, "Not assigned", "None");

    companion object {
        fun from(code: Int?): TouchFunction? = entries.firstOrNull { it.code == code }
    }
}

enum class SpeakToChatSensitivity(val code: Int, val label: String) {
    AUTO(0x00, "Auto"), HIGH(0x01, "High"), LOW(0x02, "Low");

    companion object {
        fun from(code: Int?): SpeakToChatSensitivity? = entries.firstOrNull { it.code == code }
    }
}

enum class SpeakToChatTimeout(val code: Int, val label: String) {
    SHORT(0x00, "Short (~5 s)"),
    STANDARD(0x01, "Standard (~15 s)"),
    LONG(0x02, "Long (~30 s)"),
    NEVER(0x03, "Don't end automatically");

    companion object {
        fun from(code: Int?): SpeakToChatTimeout? = entries.firstOrNull { it.code == code }
    }
}

enum class ConnectionPriority(val code: Int, val label: String, val detail: String) {
    SOUND_QUALITY(0x00, "Prioritise sound quality", "Uses LDAC when your phone supports it"),
    STABLE_CONNECTION(0x01, "Prioritise stable connection", "Better in crowded places, uses SBC or AAC");

    companion object {
        fun from(code: Int?): ConnectionPriority? = entries.firstOrNull { it.code == code }
    }
}

enum class NoiseCycle(val code: Int, val label: String) {
    NC_AMBIENT_OFF(0x01, "Noise cancelling, Ambient, Off"),
    NC_AMBIENT(0x02, "Noise cancelling, Ambient"),
    NC_OFF(0x03, "Noise cancelling, Off"),
    AMBIENT_OFF(0x04, "Ambient, Off");

    companion object {
        fun from(code: Int?): NoiseCycle? = entries.firstOrNull { it.code == code }
    }
}

data class DeviceSettings(
    val modelName: String? = null,
    val firmware: String? = null,
    val codec: String? = null,
    val eqPresetCode: Int? = null,
    val clearBass: Int? = null,
    val bands: List<Int>? = null,
    val dseeOn: Boolean? = null,
    val dseeType: Int = 0,
    val priority: ConnectionPriority? = null,
    val speakToChat: Boolean? = null,
    val speakToChatSensitivity: SpeakToChatSensitivity? = null,
    val speakToChatTimeout: SpeakToChatTimeout? = null,
    val pauseWhenRemoved: Boolean? = null,
    val autoOffWhenRemoved: Boolean? = null,
    val touchLeft: TouchFunction? = null,
    val touchRight: TouchFunction? = null,
    val noiseCycle: NoiseCycle? = null,
    val readAt: Long = 0L
) {
    val eqPreset: EqPreset? get() = EqPreset.from(eqPresetCode)
}

internal object SonyFeatures {
    // Command table (Sound Connect table1/Command)
    private const val CONNECT_GET_DEVICE_INFO = 0x04
    private const val COMMON_GET_STATUS = 0x12
    private const val POWER_GET_PARAM = 0x26
    private const val EQEBB_GET_PARAM = 0x56
    private const val AUDIO_GET_PARAM = 0xe6
    private const val SYSTEM_GET_PARAM = 0xf6
    private const val SYSTEM_GET_EXT_PARAM = 0xfa

    // Inquired types
    private const val INFO_MODEL = 0x01
    private const val INFO_FIRMWARE = 0x02
    private const val COMMON_AUDIO_CODEC = 0x02
    private const val POWER_AUTO_OFF_WEARING = 0x05
    private const val EQ_PRESET = 0x00
    private const val AUDIO_CONNECTION_MODE = 0x00
    private const val AUDIO_UPSCALING = 0x01
    private const val AUDIO_CONNECTION_MODE_ALT = 0x02
    private const val DSEE_SHORT_LAYOUT = -1

    @Volatile private var prioritySub: Int? = null
    private const val SYSTEM_PLAYBACK_BY_WEARING = 0x01
    private const val SYSTEM_ASSIGNABLE = 0x03
    private const val SYSTEM_SPEAK_TO_CHAT = 0x0c

    private const val AUTO_OFF_WHEN_REMOVED = 0x10
    private const val AUTO_OFF_DISABLED = 0x11
    private const val EQ_BAND_COUNT = 6
    private const val EQ_OFFSET = 10

    fun readAll(s: SonyBatteryClient.SonySession, previous: DeviceSettings): DeviceSettings {
        var r = previous
        r = r.copy(modelName = readString(s, INFO_MODEL) ?: r.modelName)
        r = r.copy(firmware = readString(s, INFO_FIRMWARE) ?: r.firmware)
        r = readCodec(s, r)
        r = readEq(s, r)
        r = readDsee(s, r)
        r = readPriority(s, r)
        r = readSpeakToChat(s, r)
        r = readPause(s, r)
        r = readAutoOff(s, r)
        r = readTouch(s, r)
        r = readNoiseCycle(s, r)
        return r.copy(readAt = System.currentTimeMillis())
    }

    // ---- readers ----

    private fun readString(s: SonyBatteryClient.SonySession, type: Int): String? {
        val p = s.request(bytes(CONNECT_GET_DEVICE_INFO, type)) ?: return null
        if (p.size < 4) return null
        val length = u(p[2]).coerceAtMost(p.size - 3)
        return String(p, 3, length, Charsets.US_ASCII).trim().ifBlank { null }
    }

    private fun readCodec(s: SonyBatteryClient.SonySession, r: DeviceSettings): DeviceSettings {
        val p = s.request(bytes(COMMON_GET_STATUS, COMMON_AUDIO_CODEC)) ?: return r
        if (p.size < 3) return r
        val name = when (u(p[2])) {
            0x01 -> "SBC"
            0x02 -> "AAC"
            0x10 -> "LDAC"
            0x20 -> "aptX"
            0x21 -> "aptX HD"
            0x30 -> "LC3"
            0x00 -> null
            else -> "Other"
        }
        return r.copy(codec = name)
    }

    private fun readEq(s: SonyBatteryClient.SonySession, r: DeviceSettings): DeviceSettings {
        val p = s.request(bytes(EQEBB_GET_PARAM, EQ_PRESET)) ?: return r
        if (p.size < 3) return r
        val preset = u(p[2])
        val count = if (p.size > 3) u(p[3]) else 0
        if (count >= EQ_BAND_COUNT && p.size >= 4 + EQ_BAND_COUNT) {
            val values = (0 until EQ_BAND_COUNT).map { (u(p[4 + it]) - EQ_OFFSET).coerceIn(-10, 10) }
            return r.copy(eqPresetCode = preset, clearBass = values[0], bands = values.drop(1))
        }
        return r.copy(eqPresetCode = preset)
    }

    private fun readDsee(s: SonyBatteryClient.SonySession, r: DeviceSettings): DeviceSettings {
        val p = s.request(bytes(AUDIO_GET_PARAM, AUDIO_UPSCALING)) ?: return r
        // Two layouts exist in the wild: [cmd, 01, on] and [cmd, 01, type, on].
        return when {
            p.size == 3 -> r.copy(dseeType = DSEE_SHORT_LAYOUT, dseeOn = u(p[2]) == 1)
            p.size >= 4 -> r.copy(dseeType = u(p[2]), dseeOn = u(p[3]) == 1)
            else -> r
        }
    }

    private fun readPriority(s: SonyBatteryClient.SonySession, r: DeviceSettings): DeviceSettings {
        // Sony's table names this CONNECTION_MODE (0x00); Gadgetbridge drives it as 0x02.
        // Use whichever the earbuds answer and remember it for writes.
        val candidates = prioritySub?.let { listOf(it) } ?: listOf(AUDIO_CONNECTION_MODE, AUDIO_CONNECTION_MODE_ALT)
        for (sub in candidates) {
            val p = s.request(bytes(AUDIO_GET_PARAM, sub), 1_000) ?: continue
            val value = ConnectionPriority.from(u(p[p.size - 1])) ?: continue
            if (p.size < 3) continue
            prioritySub = sub
            return r.copy(priority = value)
        }
        return r
    }

    private fun readSpeakToChat(s: SonyBatteryClient.SonySession, r: DeviceSettings): DeviceSettings {
        var next = r
        s.request(bytes(SYSTEM_GET_PARAM, SYSTEM_SPEAK_TO_CHAT))?.let { p ->
            if (p.size >= 3) next = next.copy(speakToChat = u(p[2]) == 0)
        }
        s.request(bytes(SYSTEM_GET_EXT_PARAM, SYSTEM_SPEAK_TO_CHAT))?.let { p ->
            if (p.size >= 4) next = next.copy(
                speakToChatSensitivity = SpeakToChatSensitivity.from(u(p[p.size - 2])),
                speakToChatTimeout = SpeakToChatTimeout.from(u(p[p.size - 1]))
            )
        }
        return next
    }

    private fun readPause(s: SonyBatteryClient.SonySession, r: DeviceSettings): DeviceSettings {
        val p = s.request(bytes(SYSTEM_GET_PARAM, SYSTEM_PLAYBACK_BY_WEARING)) ?: return r
        if (p.size < 3) return r
        return r.copy(pauseWhenRemoved = u(p[2]) == 0)
    }

    private fun readAutoOff(s: SonyBatteryClient.SonySession, r: DeviceSettings): DeviceSettings {
        val p = s.request(bytes(POWER_GET_PARAM, POWER_AUTO_OFF_WEARING)) ?: return r
        if (p.size < 3) return r
        return r.copy(autoOffWhenRemoved = u(p[2]) == AUTO_OFF_WHEN_REMOVED)
    }

    private fun readTouch(s: SonyBatteryClient.SonySession, r: DeviceSettings): DeviceSettings {
        val p = s.request(bytes(SYSTEM_GET_PARAM, SYSTEM_ASSIGNABLE)) ?: return r
        if (p.size < 5) return r
        return r.copy(touchLeft = TouchFunction.from(u(p[3])), touchRight = TouchFunction.from(u(p[4])))
    }

    // Which modes a tap on a "noise control" earbud cycles through: [fb, 03, 01, 00, 01, 00, mode]
    private fun readNoiseCycle(s: SonyBatteryClient.SonySession, r: DeviceSettings): DeviceSettings {
        val p = s.request(bytes(SYSTEM_GET_EXT_PARAM, SYSTEM_ASSIGNABLE)) ?: return r
        if (p.size != 7) return r
        return r.copy(noiseCycle = NoiseCycle.from(u(p[6])) ?: r.noiseCycle)
    }

    fun setNoiseCycle(s: SonyBatteryClient.SonySession, r: DeviceSettings, cycle: NoiseCycle): DeviceSettings {
        s.command(bytes(SYSTEM_GET_EXT_PARAM + 2, SYSTEM_ASSIGNABLE, 0x01, 0x00, 0x01, 0x00, cycle.code))
        return readNoiseCycle(s, r)
    }

    // ---- writers (each returns settings re-read from the earbuds) ----

    fun setEqPreset(s: SonyBatteryClient.SonySession, r: DeviceSettings, preset: EqPreset): DeviceSettings {
        s.command(bytes(EQEBB_GET_PARAM + 2, EQ_PRESET, preset.code, 0x00))
        return readEq(s, r)
    }

    fun setEqBands(s: SonyBatteryClient.SonySession, r: DeviceSettings, clearBass: Int, bands: List<Int>): DeviceSettings {
        require(bands.size == 5) { "WF-1000XM5 has five EQ bands" }
        val target = r.eqPreset?.takeIf { it.isCustomisable } ?: EqPreset.MANUAL
        val values = (listOf(clearBass) + bands).map { it.coerceIn(-10, 10) + EQ_OFFSET }
        s.command(byteArrayOf(
            (EQEBB_GET_PARAM + 2).toByte(), EQ_PRESET.toByte(), target.code.toByte(), EQ_BAND_COUNT.toByte()
        ) + values.map { it.toByte() }.toByteArray())
        return readEq(s, r)
    }

    fun setDsee(s: SonyBatteryClient.SonySession, r: DeviceSettings, on: Boolean): DeviceSettings {
        val value = if (on) 1 else 0
        s.command(
            if (r.dseeType == DSEE_SHORT_LAYOUT) bytes(AUDIO_GET_PARAM + 2, AUDIO_UPSCALING, value)
            else bytes(AUDIO_GET_PARAM + 2, AUDIO_UPSCALING, r.dseeType, value)
        )
        return readDsee(s, r)
    }

    fun setPriority(s: SonyBatteryClient.SonySession, r: DeviceSettings, priority: ConnectionPriority): DeviceSettings {
        s.command(bytes(AUDIO_GET_PARAM + 2, prioritySub ?: AUDIO_CONNECTION_MODE_ALT, priority.code))
        return readPriority(s, r)
    }

    fun setSpeakToChat(s: SonyBatteryClient.SonySession, r: DeviceSettings, on: Boolean): DeviceSettings {
        s.command(bytes(SYSTEM_GET_PARAM + 2, SYSTEM_SPEAK_TO_CHAT, if (on) 0x00 else 0x01, 0x01))
        return readSpeakToChat(s, r)
    }

    fun setSpeakToChatConfig(
        s: SonyBatteryClient.SonySession,
        r: DeviceSettings,
        sensitivity: SpeakToChatSensitivity,
        timeout: SpeakToChatTimeout
    ): DeviceSettings {
        // Echo whatever prefix the earbuds report and replace the two trailing fields.
        val current = s.request(bytes(SYSTEM_GET_EXT_PARAM, SYSTEM_SPEAK_TO_CHAT))
        val payload = if (current != null && current.size >= 4) current.copyOf().also {
            it[0] = (SYSTEM_GET_EXT_PARAM + 2).toByte()
            it[it.size - 2] = sensitivity.code.toByte()
            it[it.size - 1] = timeout.code.toByte()
        } else bytes(SYSTEM_GET_EXT_PARAM + 2, SYSTEM_SPEAK_TO_CHAT, sensitivity.code, timeout.code)
        s.command(payload)
        return readSpeakToChat(s, r)
    }

    fun setPauseWhenRemoved(s: SonyBatteryClient.SonySession, r: DeviceSettings, on: Boolean): DeviceSettings {
        s.command(bytes(SYSTEM_GET_PARAM + 2, SYSTEM_PLAYBACK_BY_WEARING, if (on) 0x00 else 0x01))
        return readPause(s, r)
    }

    fun setAutoOff(s: SonyBatteryClient.SonySession, r: DeviceSettings, whenRemoved: Boolean): DeviceSettings {
        s.command(bytes(
            POWER_GET_PARAM + 2, POWER_AUTO_OFF_WEARING,
            if (whenRemoved) AUTO_OFF_WHEN_REMOVED else AUTO_OFF_DISABLED, 0x00
        ))
        return readAutoOff(s, r)
    }

    fun setTouch(s: SonyBatteryClient.SonySession, r: DeviceSettings, left: TouchFunction, right: TouchFunction): DeviceSettings {
        s.command(bytes(SYSTEM_GET_PARAM + 2, SYSTEM_ASSIGNABLE, 0x02, left.code, right.code))
        return readTouch(s, r)
    }

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
    private fun u(value: Byte) = value.toInt() and 0xff
}

object SettingsStore {
    private const val PREFS = "device_settings"
    private const val KEY = "json"

    fun load(context: Context): DeviceSettings {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return DeviceSettings()
        return try {
            val o = JSONObject(raw)
            fun str(k: String) = if (o.has(k)) o.getString(k) else null
            fun int(k: String) = if (o.has(k)) o.getInt(k) else null
            fun bool(k: String) = if (o.has(k)) o.getBoolean(k) else null
            DeviceSettings(
                modelName = str("model"), firmware = str("fw"), codec = str("codec"),
                eqPresetCode = int("eq"), clearBass = int("cb"),
                bands = if (o.has("bands")) o.getJSONArray("bands").let { a -> (0 until a.length()).map(a::getInt) } else null,
                dseeOn = bool("dsee"), dseeType = int("dseeType") ?: 0,
                priority = ConnectionPriority.from(int("prio")),
                speakToChat = bool("s2c"),
                speakToChatSensitivity = SpeakToChatSensitivity.from(int("s2cSens")),
                speakToChatTimeout = SpeakToChatTimeout.from(int("s2cTime")),
                pauseWhenRemoved = bool("pause"), autoOffWhenRemoved = bool("autoOff"),
                touchLeft = TouchFunction.from(int("tl")), touchRight = TouchFunction.from(int("tr")),
                noiseCycle = NoiseCycle.from(int("cycle")),
                readAt = if (o.has("at")) o.getLong("at") else 0L
            )
        } catch (_: Exception) {
            DeviceSettings()
        }
    }

    fun save(context: Context, s: DeviceSettings) {
        val o = JSONObject()
        s.modelName?.let { o.put("model", it) }
        s.firmware?.let { o.put("fw", it) }
        s.codec?.let { o.put("codec", it) }
        s.eqPresetCode?.let { o.put("eq", it) }
        s.clearBass?.let { o.put("cb", it) }
        s.bands?.let { o.put("bands", org.json.JSONArray(it)) }
        s.dseeOn?.let { o.put("dsee", it) }
        o.put("dseeType", s.dseeType)
        s.priority?.let { o.put("prio", it.code) }
        s.speakToChat?.let { o.put("s2c", it) }
        s.speakToChatSensitivity?.let { o.put("s2cSens", it.code) }
        s.speakToChatTimeout?.let { o.put("s2cTime", it.code) }
        s.pauseWhenRemoved?.let { o.put("pause", it) }
        s.autoOffWhenRemoved?.let { o.put("autoOff", it) }
        s.touchLeft?.let { o.put("tl", it.code) }
        s.touchRight?.let { o.put("tr", it.code) }
        s.noiseCycle?.let { o.put("cycle", it.code) }
        o.put("at", s.readAt)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, o.toString()).apply()
    }
}

class EarbudsUnavailableException(message: String) : IOException(message)
