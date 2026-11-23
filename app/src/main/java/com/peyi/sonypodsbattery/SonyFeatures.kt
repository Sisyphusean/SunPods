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
    TREBLE(0x15, "Treble Boost"),
    BASS(0x16, "Bass Boost"),
    SPEECH(0x17, "Speech"),
    MANUAL(0xa0, "Manual"),
    CUSTOM_1(0xa1, "Custom 1"),
    CUSTOM_2(0xa2, "Custom 2");

    val isCustomisable: Boolean get() = code >= 0xa0

    companion object {
        fun from(code: Int?): EqPreset? = entries.firstOrNull { it.code == code }
    }
}

enum class TouchFunction(val code: Int, val label: String, val short: String, val alsoReadAs: Int? = null) {
    // The XM5 reports and expects 0x35 here; older Sony earbuds used 0x00.
    AMBIENT_SOUND_CONTROL(0x35, "Noise Control", "Ambient", alsoReadAs = 0x00),
    PLAYBACK_CONTROL(0x20, "Playback Control", "Playback"),
    VOLUME_CONTROL(0x10, "Volume Control", "Volume"),
    NONE(0xff, "Not Assigned", "None");

    companion object {
        fun from(code: Int?): TouchFunction? = entries.firstOrNull { it.code == code || (code != null && it.alsoReadAs == code) }
    }
}

/** One function the earbuds offer for a touch sensor, with what each gesture then does (action to function codes). */
data class TouchOption(val function: TouchFunction, val actions: List<Pair<Int, Int>>)

/**
 * The earbuds' own list of what each touch sensor can do (Sony's "capability", GET 0xf0 0x03), the
 * list Sound Connect builds its menu from. Layout, as Sound Connect parses it:
 *   [f1 03 nKeys {key type defaultPreset nPresets {preset nActions nCustom
 *       {action function}*nActions {action defaultFunction n function*n}*nCustom}*}*]
 * The XM5 offers Noise Control, Playback Control and Not Assigned per side; volume is part of both
 * (tap repeatedly: left down, right up), not a function of its own.
 */
/** Touch options per side (0 left, 1 right) as the earbuds list them; null if they haven't said. */
val DeviceSettings.touchOptions: Map<Int, List<TouchOption>>?
    get() = touchCap?.let { hex -> TouchCapability.parse(ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }) }

object TouchCapability {
    fun parse(p: ByteArray): Map<Int, List<TouchOption>>? = runCatching {
        fun u(i: Int) = p[i].toInt() and 0xff
        var i = 2
        val out = mutableMapOf<Int, List<TouchOption>>()
        repeat(u(i++)) {
            val key = u(i)
            i += 3 // key, key type, default preset
            val options = mutableListOf<TouchOption>()
            repeat(u(i++)) {
                val preset = u(i); val nActions = u(i + 1); val nCustom = u(i + 2)
                i += 3
                val actions = mutableListOf<Pair<Int, Int>>()
                repeat(nActions) { actions += u(i) to u(i + 1); i += 2 }
                repeat(nCustom) { actions += u(i) to u(i + 1); i += 3 + u(i + 2) }
                TouchFunction.from(preset)?.let { f -> if (options.none { it.function == f }) options += TouchOption(f, actions) }
            }
            out[key] = options
        }
        out.takeIf { it.isNotEmpty() }
    }.getOrNull()

    private fun gesture(action: Int) = when (action) {
        0x00 -> "Tap"; 0x01 -> "Double-tap"; 0x02 -> "Triple-tap"; 0x03 -> "Tap repeatedly"
        0x10 -> "Tap and hold"; 0x11 -> "Double-tap and hold"; 0x21, 0x22 -> "Hold"
        else -> null
    }

    private fun result(function: Int) = when (function) {
        in 0x01..0x04, in 0x50..0x56, 0x59 -> "change noise mode"
        0x10 -> "Quick Attention"; 0x11 -> "optimise noise cancelling"
        0x20 -> "play or pause"; 0x21 -> "next track"; 0x22 -> "previous track"
        0x23 -> "volume up"; 0x24 -> "volume down"
        in 0x30..0x3b -> "voice assistant"
        0x70 -> "mute your mic"
        else -> null
    }

    private fun glyph(action: Int) = when (action) {
        0x00 -> "1×"; 0x01 -> "2×"; 0x02 -> "3×"; 0x03 -> "Taps"
        0x10 -> "1× hold"; 0x11 -> "2× hold"; 0x21, 0x22 -> "Hold"
        else -> null
    }

    private fun short(function: Int) = when (function) {
        in 0x01..0x04, in 0x50..0x56, 0x59 -> "Noise mode"
        0x10 -> "Attention"; 0x11 -> "Optimise"
        0x20 -> "Play/Pause"; 0x21 -> "Next"; 0x22 -> "Previous"
        0x23 -> "Volume up"; 0x24 -> "Volume down"
        in 0x30..0x3b -> "Assistant"
        0x70 -> "Mute"
        else -> null
    }

    /** The gestures as a short key: ("1×", "Play/Pause"), ("2×", "Next"), ... */
    fun legend(option: TouchOption): List<Pair<String, String>> =
        option.actions.sortedBy { it.first }.mapNotNull { (a, f) -> val g = glyph(a); val r = short(f); if (g != null && r != null) g to r else null }

    /** "Tap: play or pause. Double-tap: next track. Tap repeatedly: volume down." */
    fun describe(option: TouchOption): String {
        val parts = option.actions.sortedBy { it.first }.mapNotNull { (a, f) -> val g = gesture(a); val r = result(f); if (g != null && r != null) "$g: $r" else null }
        return if (parts.isEmpty()) "Touch controls are off on this side." else parts.joinToString(". ", postfix = ".")
    }
}

enum class SpeakToChatSensitivity(val code: Int, val label: String) {
    AUTO(0x00, "Auto"), HIGH(0x01, "High"), LOW(0x02, "Low");

    companion object {
        fun from(code: Int?): SpeakToChatSensitivity? = entries.firstOrNull { it.code == code }
    }
}

enum class SpeakToChatTimeout(val code: Int, val label: String, val short: String) {
    SHORT(0x00, "Short (about 5 seconds)", "5 sec"),
    STANDARD(0x01, "Standard (about 15 seconds)", "15 sec"),
    LONG(0x02, "Long (about 30 seconds)", "30 sec"),
    NEVER(0x03, "Never", "Never");

    companion object {
        fun from(code: Int?): SpeakToChatTimeout? = entries.firstOrNull { it.code == code }
    }
}

enum class ConnectionPriority(val code: Int, val label: String, val detail: String, val short: String) {
    SOUND_QUALITY(0x00, "Prioritise Sound Quality", "Uses LDAC when your phone supports it", "Sound Quality"),
    STABLE_CONNECTION(0x01, "Prioritise Stable Connection", "Uses SBC or AAC for a steadier connection in crowded places", "Stability");

    /** The WF-C710N has no LDAC: sound quality means AAC there. */
    fun detailFor(model: EarbudModel): String = when {
        model == EarbudModel.WF_C710N && this == SOUND_QUALITY -> "Uses AAC when your phone supports it"
        model == EarbudModel.WF_C710N -> "Uses SBC for a steadier connection in crowded places"
        else -> detail
    }

    companion object {
        fun from(code: Int?): ConnectionPriority? = entries.firstOrNull { it.code == code }
    }
}

enum class NoiseCycle(val code: Int, val label: String, val short: String) {
    NC_AMBIENT_OFF(0x01, "Noise Cancelling, Ambient Sound, Off", "All Three"),
    NC_AMBIENT(0x02, "Noise Cancelling, Ambient Sound", "Cancelling, Ambient"),
    NC_OFF(0x03, "Noise Cancelling, Off", "Cancelling, Off"),
    AMBIENT_OFF(0x04, "Ambient Sound, Off", "Ambient, Off");

    companion object {
        fun from(code: Int?): NoiseCycle? = entries.firstOrNull { it.code == code }
    }
}

/**
 * A "general setting": an on/off switch the earbuds describe themselves (Sony's GS slots 1 to 4).
 * On the XM5 these carry things like multipoint and hearing your own voice on calls.
 */
data class GeneralSetting(val slot: Int, val key: String, val on: Boolean?) {
    val title: String get() = GS_TITLES[key]?.first ?: pretty(key)
    val subtitle: String get() = GS_TITLES[key]?.second?.replace("{noun}", EarbudModel.active.noun) ?: ""

    companion object {
        private val GS_TITLES = mapOf(
            "MULTIPOINT_SETTING" to ("Connect to Two Devices" to "Stay connected to your phone and another device at the same time"),
            "SIDETONE_SETTING" to ("Hear Your Voice on Calls" to "Hear yourself in your {noun} while you talk"),
            "TOUCH_PANEL_SETTING" to ("Touch Controls" to "Tap and swipe on the right ear cup to control playback and volume"),
            "TWS_ONE_SIDE_USE_NCASM_SETTING" to ("Noise Control with One Earbud" to "Keeps noise control on when you wear one earbud"),
            "FACETAP_SETTING" to ("Face Tap" to "Tap near your ear instead of on the earbud")
        )

        private fun pretty(key: String) = key.removeSuffix("_SETTING").lowercase().replace('_', ' ')
            .replaceFirstChar { it.uppercase() }
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
    /** Auto power off as the earbuds hold it: the option in force and the time last chosen ([AutoOff] codes). */
    val autoOffCode: Int? = null,
    val autoOffTime: Int? = null,
    /** The options these earbuds offer, from their capability reply; null until read. */
    val autoOffOptions: List<Int>? = null,
    val touchLeft: TouchFunction? = null,
    val touchRight: TouchFunction? = null,
    /** The earbuds' touch capability reply, as hex; see [TouchCapability]. */
    val touchCap: String? = null,
    /** Nod to answer a call, shake your head to decline. null if the earbuds don't offer it. */
    val headGestures: Boolean? = null,
    /** The earbuds switch between connected devices by themselves. null if they don't offer it. */
    val autoSwitch: Boolean? = null,
    val noiseCycle: NoiseCycle? = null,
    /** null until the slots have been probed once; empty means the earbuds offer none. */
    val general: List<GeneralSetting>? = null,
    val promptsOn: Boolean? = null,
    /** Sony's colour code: 0x01 Black, 0x03 Platinum Silver, 0x06 Smoky Pink. */
    val colorCode: Int? = null,
    /** The earbuds can switch between Classic and LE Audio. */
    val leModeSupported: Boolean? = null,
    /** The earbuds' own LE Audio setting is on. */
    val leOn: Boolean? = null,
    /** -2 (quietest) to +2 (loudest). */
    val promptVolume: Int? = null,
    val promptTail: Int? = null,
    /** The earbuds' own list of supported functions, table 1 and table 2, as hex (see [SupportedFunctions]). */
    val functions1: String? = null,
    val functions2: String? = null,
    val readAt: Long = 0L
) {
    val eqPreset: EqPreset? get() = EqPreset.from(eqPresetCode)

    /** What the earbuds said they support; null until they've been asked. */
    internal val functions: SupportedFunctions? by lazy(LazyThreadSafetyMode.PUBLICATION) {
        SupportedFunctions.decode(functions1)?.let { SupportedFunctions(it, SupportedFunctions.decode(functions2)) }
    }

    /** Sony's name for the upscaler: DSEE Extreme on the WF-1000XM5, DSEE on the WF-C710N. */
    val dseeName: String get() = if (EarbudModel.fromName(modelName) == EarbudModel.WF_C710N) "DSEE" else "DSEE Extreme"

    /** True unless the earbuds' own list says they lack it (so earbuds not yet asked keep everything). */
    internal fun has(feature: Feature): Boolean = functions?.let(feature.test) ?: true
}

/** Features SunPods shows only when the earbuds list them (Sound Connect does the same). */
internal enum class Feature(val test: (SupportedFunctions) -> Boolean) {
    SPEAK_TO_CHAT({ it.has1(Fn.SPEAK_TO_CHAT_1, Fn.SPEAK_TO_CHAT_2) }),
    CODEC({ it.has1(Fn.CODEC_INDICATOR) }),
    DSEE({ it.has1(Fn.UPSCALING, Fn.UPSCALING_AUTO_OFF, Fn.UPSCALING_WITH_REASON) }),
    TOUCH({ it.has1(Fn.ASSIGNABLE, Fn.ASSIGNABLE_LIMITED) }),
    // Automatic switching lives in table 2; the WF-1000XM5 lists only "not over LE Audio" (0x6f) on LE.
    AUTO_SWITCH({ it.has2(Fn.T2_LINK_AUTO_SWITCH, Fn.T2_LINK_AUTO_SWITCH_SPEAKER, Fn.T2_LINK_AUTO_SWITCH_NOT_LE) }),
    PAUSE_WHEN_REMOVED({ it.has1(Fn.PLAYBACK_BY_WEARING) }),
    AUTO_OFF_WHEN_REMOVED({ it.has1(Fn.AUTO_POWER_OFF_WEARING, Fn.AUTO_POWER_OFF) }),
    WEARING({ it.has1(Fn.PLAYBACK_BY_WEARING, Fn.AUTO_POWER_OFF_WEARING, Fn.AUTO_POWER_OFF) }),
    HEAD_GESTURES({ it.has1(Fn.HEAD_GESTURE) }),
    FIT_TEST({ it.has1(Fn.WEARING_STATUS) }),
    VOICE_ASSISTANT({ it.has1(Fn.VOICE_ASSISTANT) }),
    RESET({ it.has1(Fn.RESET) }),
    POWER_OFF({ it.has1(Fn.POWER_OFF) }),
    LE_AUDIO_SWITCH({ it.has1(Fn.CLASSIC_LE_SETTING) }),
    VOICE_PROMPTS({ it.has2Any(Fn.T2_VOICE_GUIDANCE) }),
    SAFE_LISTENING({ it.has2Any(Fn.T2_SAFE_LISTENING) }),
    /** Headphones: the NC/AMB button's cycle (instead of a touch control's). */
    NC_AMB_BUTTON({ it.has1(Fn.NC_AMB_SELECT) }),
    VOLUME_PROTECTION({ it.has2(Fn.T2_SAFE_VOLUME) }),
}

/**
 * Automatic power off (POWER inquired type 05 "with wearing detection", or 04 by time only).
 * RET 27 tt [active, last chosen time]; SET 28 tt [active, last chosen time]; options from
 * capability 21 tt [n, option...]. The WF-1000XM5 offers only "when removed" and off; the
 * WF-C710N is expected to offer times as well.
 */
internal object AutoOff {
    const val MIN_5 = 0x00
    const val MIN_30 = 0x01
    const val MIN_60 = 0x02
    const val MIN_180 = 0x03
    const val MIN_15 = 0x04
    const val WHEN_REMOVED = 0x10
    const val DISABLED = 0x11

    val TIMES = listOf(MIN_5, MIN_15, MIN_30, MIN_60, MIN_180)

    fun label(code: Int): String = when (code) {
        MIN_5 -> "After 5 minutes"; MIN_15 -> "After 15 minutes"; MIN_30 -> "After 30 minutes"
        MIN_60 -> "After 1 hour"; MIN_180 -> "After 3 hours"
        WHEN_REMOVED -> "When removed"; DISABLED -> "Never"
        else -> "Option %02x".format(code)
    }

    fun short(code: Int): String = when (code) {
        MIN_5 -> "5 min"; MIN_15 -> "15 min"; MIN_30 -> "30 min"; MIN_60 -> "1 hour"; MIN_180 -> "3 hours"
        WHEN_REMOVED -> "When removed"; DISABLED -> "Never"
        else -> "—"
    }

    /** Options in the order to show them: times shortest first, then "when removed", then never. */
    fun ordered(options: List<Int>): List<Int> =
        TIMES.filter { it in options } + listOf(WHEN_REMOVED, DISABLED).filter { it in options }

    /** True when the earbuds offer a choice of times, not just on or off. */
    fun hasTimes(options: List<Int>?): Boolean = options != null && options.any { it in TIMES }
}

internal object SonyFeatures {
    // Command table (Sound Connect table1/Command)
    private const val CONNECT_GET_DEVICE_INFO = 0x04
    private const val COMMON_GET_STATUS = 0x12
    private const val POWER_GET_PARAM = 0x26
    private const val EQEBB_GET_PARAM = 0x56
    private const val AUDIO_GET_PARAM = 0xe6
    private const val SYSTEM_GET_CAPABILITY = 0xf0
    private const val SYSTEM_GET_PARAM = 0xf6
    private const val SYSTEM_GET_EXT_PARAM = 0xfa
    private const val GS_GET_CAPABILITY = 0xd0
    private const val GS_GET_PARAM = 0xd6
    private val GS_SLOTS = 0xd1..0xd4
    private const val GS_BOOLEAN = 0x00
    private const val GS_ON = 0x00
    private const val GS_OFF = 0x01
    private const val LANGUAGE_ENGLISH = 0x01
    /** General settings the earbuds only change after the app confirms an alert. */
    private val GS_CONFIRMED = setOf("MULTIPOINT_SETTING")

    // Inquired types
    private const val INFO_MODEL = 0x01
    private const val INFO_FIRMWARE = 0x02
    private const val COMMON_AUDIO_CODEC = 0x02
    private const val POWER_AUTO_OFF_WEARING = 0x05
    private const val POWER_AUTO_OFF = 0x04
    private const val POWER_GET_CAPABILITY = 0x20
    private const val EQ_PRESET = 0x00
    private const val AUDIO_CONNECTION_MODE = 0x00
    private const val AUDIO_UPSCALING = 0x01
    private const val AUDIO_CONNECTION_MODE_ALT = 0x02
    private const val DSEE_SHORT_LAYOUT = -1

    @Volatile private var prioritySub: Int? = null
    private const val SYSTEM_PLAYBACK_BY_WEARING = 0x01
    private const val SYSTEM_ASSIGNABLE = 0x03
    private const val NCASM_GET_PARAM = 0x66
    private const val NCASM_BUTTON = 0x30
    private const val SYSTEM_SPEAK_TO_CHAT = 0x0c
    private const val SYSTEM_HEAD_GESTURE = 0x0f
    private const val SYSTEM_T2_AUTO_SWITCH = 0x0a

    private const val AUTO_OFF_WHEN_REMOVED = 0x10
    private const val AUTO_OFF_DISABLED = 0x11
    private const val EQ_BAND_COUNT = 6
    private const val EQ_OFFSET = 10

    fun readAll(s: SonyBatteryClient.SonySession, previous: DeviceSettings): DeviceSettings {
        val model = readString(s, INFO_MODEL)
        // Another pair of earbuds (another model): nothing saved for the last pair applies.
        var r = if (model != null && previous.modelName != null && model != previous.modelName) DeviceSettings() else previous
        r = r.copy(modelName = model ?: r.modelName)
        // Their own list of what they support, read every time: it changes with the connection.
        s.functions()?.let { f -> r = r.copy(functions1 = f.encode1(), functions2 = f.encode2() ?: r.functions2) }
        r = r.copy(firmware = readString(s, INFO_FIRMWARE) ?: r.firmware)
        // Read every time (it's one short request), so it's right from the first connection in
        // setup and follows a different pair of earbuds. The app shows black until it's known.
        r = r.copy(colorCode = readColor(s) ?: r.colorCode)
        if (r.has(Feature.CODEC)) r = readCodec(s, r)
        r = readEq(s, r)
        if (r.has(Feature.DSEE)) r = readDsee(s, r)
        r = readPriority(s, r)
        if (r.has(Feature.SPEAK_TO_CHAT)) r = readSpeakToChat(s, r)
        if (r.has(Feature.PAUSE_WHEN_REMOVED)) r = readPause(s, r)
        if (r.has(Feature.AUTO_OFF_WHEN_REMOVED)) r = readAutoOff(s, r)
        if (r.has(Feature.TOUCH)) r = readTouch(s, r)
        if (r.has(Feature.TOUCH)) r = readTouchCap(s, r)
        if (r.has(Feature.HEAD_GESTURES)) r = readHeadGestures(s, r)
        if (r.has(Feature.AUTO_SWITCH)) r = readAutoSwitch(s, r)
        if (r.has(Feature.TOUCH) || r.has(Feature.NC_AMB_BUTTON)) r = readNoiseCycle(s, r)
        r = readGeneral(s, r)
        if (r.has(Feature.VOICE_PROMPTS)) r = readPrompts(s, r)
        r = if (r.has(Feature.LE_AUDIO_SWITCH)) readLeMode(s, r) else r.copy(leModeSupported = false)
        return r.copy(readAt = System.currentTimeMillis())
    }

    private fun readPrompts(s: SonyBatteryClient.SonySession, r: DeviceSettings, patient: Boolean = false): DeviceSettings {
        val v = VoicePrompts.read(s, patient)
        return r.copy(promptsOn = v.on ?: r.promptsOn, promptVolume = v.volume ?: r.promptVolume, promptTail = v.tail ?: r.promptTail)
    }

    /*
     * Classic or LE Audio, the way Sound Connect does it (LEA table 1,
     * LEAInquiredType.CLASSIC_ONLY_LE_CLASSIC_SETTING = 0x0c):
     *   GET 0x46 [0x0c]                 -> RET 0x47 [0x0c, 0 = LE Audio on / 1 = off]
     *   SET 0x48 [0x0c, on/off, 0x00]   0x00 = change the setting and the connection method
     *   GET_CAPABILITY 0x40 [0x00]      -> RET 0x41 [0x00, 3 x 17 char addresses]: the earbuds'
     *                                      addresses, including the LE ones Android may need to pair
     */
    private const val LEA_GET_CAPABILITY = 0x40
    private const val LEA_GET_PARAM = 0x46
    private const val LEA_CLASSIC_LE = 0x0c

    private fun readLeMode(s: SonyBatteryClient.SonySession, r: DeviceSettings): DeviceSettings {
        if (r.leModeSupported == false) return r
        val p = s.request(bytes(LEA_GET_PARAM, LEA_CLASSIC_LE), timeoutMs = 700) ?: return r.copy(leModeSupported = r.leModeSupported ?: false)
        return r.copy(leModeSupported = true, leOn = if (p.size >= 3) u(p[2]) == 0 else r.leOn)
    }

    /** The earbuds' own connection setting: true = LE Audio, false = Classic only, null = no answer. */
    fun readLeOn(s: SonyBatteryClient.SonySession): Boolean? {
        val p = s.request(bytes(LEA_GET_PARAM, LEA_CLASSIC_LE), timeoutMs = 1_500) ?: return null
        return if (p.size >= 3) u(p[2]) == 0 else null
    }

    /** The earbuds' Bluetooth addresses (Classic and LE), for pairing LE Audio if Android needs to. */
    fun leAddresses(s: SonyBatteryClient.SonySession): List<String> {
        val p = s.request(bytes(LEA_GET_CAPABILITY, 0x00), timeoutMs = 1_000) ?: return emptyList()
        if (p.size < 53) return emptyList()
        return listOf(2, 19, 36).map { String(p, it, 17, Charsets.US_ASCII) }.filter { Regex("([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}").matches(it) }
    }

    /**
     * Turns the earbuds' LE Audio Priority on or off the way Sound Connect does: SET with "change the
     * setting and the connection method" when this phone can use LE Audio (the setting only
     * otherwise), then confirm the alert the earbuds send. Returns true once the earbuds have taken
     * the change; they then disconnect and come back in the new mode.
     */
    fun setLeAudio(s: SonyBatteryClient.SonySession, r: DeviceSettings, on: Boolean, phoneSupportsLe: Boolean = true): Boolean {
        // Going to Classic the earbuds answer with alert 0x2e (they'll drop the link and wait in
        // Classic pairing mode); going to LE with 0x09. Either way nothing happens until it's
        // confirmed, so try twice before giving up.
        repeat(2) { attempt ->
            val alert = s.withConfirmation(timeoutMs = 3_500) {
                s.command(bytes(LEA_GET_PARAM + 2, LEA_CLASSIC_LE, if (on) 0x00 else 0x01, if (phoneSupportsLe) 0x00 else 0x01))
            }
            if (alert != null) return true
            // No alert seen, yet the earbuds can still be holding the change until it's confirmed
            // (seen over both LE and Classic). Answering the alert they'd have sent applies it:
            // they report the new setting (0x49 0x0c), then drop the link to switch.
            android.util.Log.i("SunPods", "LE Audio switch attempt ${attempt + 1}: no alert, confirming anyway")
            val applied = runCatching {
                s.send(bytes(0x98, 0x00, if (on) 0x09 else 0x2e, 0x01))
                s.waitFor(1_500) { it.type == SonyProtocol.DATA_MDR && it.payload.size >= 3 && u(it.payload[0]) == LEA_GET_PARAM + 3 && u(it.payload[1]) == LEA_CLASSIC_LE }
                    ?.let { (u(it.payload[2]) == 0) == on }
            }.getOrElse { true } // the link dropping right away means it switched
            if (applied == true) return true
            // Still nothing: the earbuds either applied it straight away or dropped it, so ask.
            val p = runCatching { s.request(bytes(LEA_GET_PARAM, LEA_CLASSIC_LE), timeoutMs = 800) }.getOrNull()
            if (p != null && p.size >= 3 && (u(p[2]) == 0) == on) return true
            android.util.Log.i("SunPods", "LE Audio switch attempt ${attempt + 1}: no alert, earbuds report ${p?.joinToString(" ") { "%02x".format(it.toInt() and 0xff) }}")
        }
        return false
    }

    fun setPrompts(s: SonyBatteryClient.SonySession, r: DeviceSettings, on: Boolean): DeviceSettings {
        val sent = VoicePrompts.setOn(s, on)
        // If the read-back doesn't come, the earbuds acknowledged the change: show what was set.
        return readPrompts(s, if (sent) r.copy(promptsOn = on) else r, patient = true)
    }

    fun setPromptVolume(s: SonyBatteryClient.SonySession, r: DeviceSettings, volume: Int): DeviceSettings {
        val ok = VoicePrompts.setVolume(s, volume, r.promptTail)
        return readPrompts(s, if (ok) r.copy(promptVolume = volume) else r, patient = true)
    }

    /**
     * Asks each slot what it is once (unused slots stay silent, so that probe is kept short and
     * remembered), then reads the current value of the ones that are on/off switches.
     */
    private fun readGeneral(s: SonyBatteryClient.SonySession, r: DeviceSettings): DeviceSettings {
        val known = r.general ?: GS_SLOTS.mapNotNull { slot ->
            val p = s.request(bytes(GS_GET_CAPABILITY, slot, LANGUAGE_ENGLISH), timeoutMs = 600) ?: return@mapNotNull null
            // [ret, slot, type, format, titleLen, title...]
            if (p.size < 5 || u(p[2]) != GS_BOOLEAN) return@mapNotNull null
            val len = u(p[4]).coerceAtMost(p.size - 5)
            val key = String(p, 5, len, Charsets.US_ASCII).trim()
            if (key.isBlank()) null else GeneralSetting(slot, key, null)
        }
        return r.copy(general = known.map { g -> g.copy(on = readGeneralValue(s, g.slot) ?: g.on) })
    }

    private fun readGeneralValue(s: SonyBatteryClient.SonySession, slot: Int): Boolean? {
        val p = s.request(bytes(GS_GET_PARAM, slot)) ?: return null
        // [ret, slot, type, value] with 0 = on, 1 = off
        if (p.size < 4 || u(p[2]) != GS_BOOLEAN) return null
        return when (u(p[3])) { GS_ON -> true; GS_OFF -> false; else -> null }
    }

    fun setGeneral(s: SonyBatteryClient.SonySession, r: DeviceSettings, slot: Int, on: Boolean): DeviceSettings {
        val write = { s.command(bytes(GS_GET_PARAM + 2, slot, GS_BOOLEAN, if (on) GS_ON else GS_OFF)); Unit }
        // Settings that make the earbuds reconnect, like Connect to Two Devices, wait for the app to confirm.
        if (r.general?.firstOrNull { it.slot == slot }?.key in GS_CONFIRMED) s.withConfirmation { write() } else write()
        // The earbuds may drop the link to apply it, so a failed read keeps the value just set.
        val value = runCatching { readGeneralValue(s, slot) }.getOrNull()
        return r.copy(general = r.general?.map { if (it.slot == slot) it.copy(on = value ?: on) else it })
    }

    // ---- readers ----

    private fun readColor(s: SonyBatteryClient.SonySession): Int? {
        val p = s.request(bytes(CONNECT_GET_DEVICE_INFO, 0x03), timeoutMs = 700) ?: return null
        return if (p.size >= 4) u(p[3]) else null
    }

    private fun readString(s: SonyBatteryClient.SonySession, type: Int): String? {
        val p = s.request(bytes(CONNECT_GET_DEVICE_INFO, type)) ?: return null
        if (p.size < 4) return null
        val length = u(p[2]).coerceAtMost(p.size - 3)
        return String(p, 3, length, Charsets.US_ASCII).trim().ifBlank { null }
    }

    /** Re-reads just the codec now and then during background refreshes (battery health uses it). */
    fun refreshCodec(context: android.content.Context, s: SonyBatteryClient.SonySession) {
        val p = context.getSharedPreferences("battery_health", android.content.Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - p.getLong("codec_at", 0L) < 30 * 60_000L) return
        p.edit().putLong("codec_at", now).apply()
        val stored = SettingsStore.load(context)
        val next = readCodec(s, stored)
        if (next.codec != stored.codec) SettingsStore.save(context, next)
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

    /** Type 05 (with wearing detection) when listed, else 04 (time only). */
    private fun autoOffType(r: DeviceSettings): Int =
        r.functions?.let { if (!it.has1(Fn.AUTO_POWER_OFF_WEARING) && it.has1(Fn.AUTO_POWER_OFF)) POWER_AUTO_OFF else null } ?: POWER_AUTO_OFF_WEARING

    private fun readAutoOff(s: SonyBatteryClient.SonySession, r: DeviceSettings): DeviceSettings {
        val type = autoOffType(r)
        // The options never change for a pair, so ask once: [21, type, n, option...].
        val options = r.autoOffOptions ?: s.request(bytes(POWER_GET_CAPABILITY, type), timeoutMs = 700)
            ?.takeIf { it.size >= 3 && it.size >= 3 + u(it[2]) }?.let { c -> (0 until u(c[2])).map { u(c[3 + it]) } }
        val p = s.request(bytes(POWER_GET_PARAM, type)) ?: return r.copy(autoOffOptions = options)
        if (p.size < 3) return r.copy(autoOffOptions = options)
        val active = u(p[2])
        return r.copy(
            autoOffWhenRemoved = active == AUTO_OFF_WHEN_REMOVED,
            autoOffCode = active,
            autoOffTime = if (p.size >= 4) u(p[3]) else r.autoOffTime,
            autoOffOptions = options
        )
    }

    private fun readTouch(s: SonyBatteryClient.SonySession, r: DeviceSettings): DeviceSettings {
        val p = s.request(bytes(SYSTEM_GET_PARAM, SYSTEM_ASSIGNABLE)) ?: return r
        android.util.Log.i("SunPods", "Touch controls reported: " + p.joinToString(" ") { "%02x".format(it.toInt() and 0xff) })
        if (p.size < 5) return r
        // A side the earbuds report with a code we don't know keeps its last known value.
        return r.copy(
            touchLeft = TouchFunction.from(u(p[3])) ?: r.touchLeft,
            touchRight = TouchFunction.from(u(p[4])) ?: r.touchRight
        )
    }

    // Head gestures for calls: [f7, 0f, 0 on / 1 off]
    private fun readHeadGestures(s: SonyBatteryClient.SonySession, r: DeviceSettings, patient: Boolean = false): DeviceSettings {
        val p = s.request(bytes(SYSTEM_GET_PARAM, SYSTEM_HEAD_GESTURE), timeoutMs = if (patient) 2_000 else 700) ?: return r
        return if (p.size >= 3) r.copy(headGestures = u(p[2]) == 0x00) else r
    }

    fun setHeadGestures(s: SonyBatteryClient.SonySession, r: DeviceSettings, on: Boolean): DeviceSettings {
        val ok = s.command(bytes(SYSTEM_GET_PARAM + 2, SYSTEM_HEAD_GESTURE, if (on) 0x00 else 0x01))
        return readHeadGestures(s, if (ok) r.copy(headGestures = on) else r, patient = true)
    }

    // Automatic device switching, table 2: GET f6 0a -> [f7, 0a, 0 on / 1 off, 0, 0, address text]
    private fun readAutoSwitch(s: SonyBatteryClient.SonySession, r: DeviceSettings, patient: Boolean = false): DeviceSettings {
        val p = s.request(bytes(SYSTEM_GET_PARAM, SYSTEM_T2_AUTO_SWITCH), timeoutMs = if (patient) 2_000 else 700, type = SonyProtocol.DATA_MDR_NO2) ?: return r
        return if (p.size >= 3) r.copy(autoSwitch = u(p[2]) == 0x00) else r
    }

    fun setAutoSwitch(s: SonyBatteryClient.SonySession, r: DeviceSettings, on: Boolean): DeviceSettings {
        val ok = s.command(bytes(SYSTEM_GET_PARAM + 2, SYSTEM_T2_AUTO_SWITCH, if (on) 0x00 else 0x01, 0x00, 0x00), type = SonyProtocol.DATA_MDR_NO2)
        return readAutoSwitch(s, if (ok) r.copy(autoSwitch = on) else r, patient = true)
    }

    /** Turns the earbuds off, like holding both sensors: POWER_SET_STATUS [03 power off, 01 by the user]. */
    fun powerOff(s: SonyBatteryClient.SonySession) {
        s.send(bytes(0x24, 0x03, 0x01))
    }

    private fun readTouchCap(s: SonyBatteryClient.SonySession, r: DeviceSettings): DeviceSettings {
        val p = s.request(bytes(SYSTEM_GET_CAPABILITY, SYSTEM_ASSIGNABLE), timeoutMs = 700) ?: return r
        if (TouchCapability.parse(p) == null) return r
        return r.copy(touchCap = p.joinToString("") { "%02x".format(it.toInt() and 0xff) })
    }

    // Which modes a tap on a "noise control" earbud cycles through: [fb, 03, 01, 00, 01, 00, mode]
    private fun readNoiseCycle(s: SonyBatteryClient.SonySession, r: DeviceSettings): DeviceSettings {
        if (r.has(Feature.NC_AMB_BUTTON)) {
            // Headphones: the NC/AMB button's cycle, 66 30 -> 67 30 [cycle].
            val p = s.request(bytes(NCASM_GET_PARAM, NCASM_BUTTON)) ?: return r
            return if (p.size >= 3) r.copy(noiseCycle = NoiseCycle.from(u(p[2])) ?: r.noiseCycle) else r
        }
        val p = s.request(bytes(SYSTEM_GET_EXT_PARAM, SYSTEM_ASSIGNABLE)) ?: return r
        if (p.size != 7) return r
        return r.copy(noiseCycle = NoiseCycle.from(u(p[6])) ?: r.noiseCycle)
    }

    fun setNoiseCycle(s: SonyBatteryClient.SonySession, r: DeviceSettings, cycle: NoiseCycle): DeviceSettings {
        val ok = if (r.has(Feature.NC_AMB_BUTTON)) s.command(bytes(NCASM_GET_PARAM + 2, NCASM_BUTTON, cycle.code)) else {
            // Send back the preset byte the earbuds report here (the WF-1000XM5 reports 00), as Sound Connect does.
            val preset = s.request(bytes(SYSTEM_GET_EXT_PARAM, SYSTEM_ASSIGNABLE))?.takeIf { it.size == 7 }?.let { u(it[3]) }
                ?: TouchFunction.AMBIENT_SOUND_CONTROL.code
            s.command(bytes(SYSTEM_GET_EXT_PARAM + 2, SYSTEM_ASSIGNABLE, 0x01, preset, 0x01, 0x00, cycle.code))
        }
        return readNoiseCycle(s, if (ok) r.copy(noiseCycle = cycle) else r)
    }

    // ---- writers (each returns settings re-read from the earbuds) ----

    fun setEqPreset(s: SonyBatteryClient.SonySession, r: DeviceSettings, preset: EqPreset): DeviceSettings {
        val ok = s.command(bytes(EQEBB_GET_PARAM + 2, EQ_PRESET, preset.code, 0x00))
        return readEq(s, if (ok) r.copy(eqPresetCode = preset.code) else r)
    }

    fun setEqBands(s: SonyBatteryClient.SonySession, r: DeviceSettings, clearBass: Int, bands: List<Int>): DeviceSettings {
        require(bands.size == 5) { "WF-1000XM5 has five EQ bands" }
        val target = r.eqPreset?.takeIf { it.isCustomisable } ?: EqPreset.MANUAL
        val values = (listOf(clearBass) + bands).map { it.coerceIn(-10, 10) + EQ_OFFSET }
        val ok = s.command(byteArrayOf(
            (EQEBB_GET_PARAM + 2).toByte(), EQ_PRESET.toByte(), target.code.toByte(), EQ_BAND_COUNT.toByte()
        ) + values.map { it.toByte() }.toByteArray())
        return readEq(s, if (ok) r.copy(eqPresetCode = target.code, clearBass = clearBass, bands = bands) else r)
    }

    fun setDsee(s: SonyBatteryClient.SonySession, r: DeviceSettings, on: Boolean): DeviceSettings {
        val value = if (on) 1 else 0
        val ok = s.command(
            if (r.dseeType == DSEE_SHORT_LAYOUT) bytes(AUDIO_GET_PARAM + 2, AUDIO_UPSCALING, value)
            else bytes(AUDIO_GET_PARAM + 2, AUDIO_UPSCALING, r.dseeType, value)
        )
        return readDsee(s, if (ok) r.copy(dseeOn = on) else r)
    }

    fun setPriority(s: SonyBatteryClient.SonySession, r: DeviceSettings, priority: ConnectionPriority): DeviceSettings {
        // With Connect to Two Devices on, a change of codec priority can need confirming too.
        s.withConfirmation(timeoutMs = 1_200) { s.command(bytes(AUDIO_GET_PARAM + 2, prioritySub ?: AUDIO_CONNECTION_MODE_ALT, priority.code)) }
        return runCatching { readPriority(s, r) }.getOrDefault(r.copy(priority = priority))
    }

    fun setSpeakToChat(s: SonyBatteryClient.SonySession, r: DeviceSettings, on: Boolean): DeviceSettings {
        val ok = s.command(bytes(SYSTEM_GET_PARAM + 2, SYSTEM_SPEAK_TO_CHAT, if (on) 0x00 else 0x01, 0x01))
        return readSpeakToChat(s, if (ok) r.copy(speakToChat = on) else r)
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
        val ok = s.command(payload)
        return readSpeakToChat(s, if (ok) r.copy(speakToChatSensitivity = sensitivity, speakToChatTimeout = timeout) else r)
    }

    fun setPauseWhenRemoved(s: SonyBatteryClient.SonySession, r: DeviceSettings, on: Boolean): DeviceSettings {
        val ok = s.command(bytes(SYSTEM_GET_PARAM + 2, SYSTEM_PLAYBACK_BY_WEARING, if (on) 0x00 else 0x01))
        return readPause(s, if (ok) r.copy(pauseWhenRemoved = on) else r)
    }

    fun setAutoOff(s: SonyBatteryClient.SonySession, r: DeviceSettings, whenRemoved: Boolean): DeviceSettings =
        setAutoOffOption(s, r, if (whenRemoved) AUTO_OFF_WHEN_REMOVED else AUTO_OFF_DISABLED)

    /** Sets the option in force; the time last chosen goes back too, as Sound Connect sends it. */
    fun setAutoOffOption(s: SonyBatteryClient.SonySession, r: DeviceSettings, code: Int): DeviceSettings {
        val time = if (code in AutoOff.TIMES) code else r.autoOffTime ?: 0x00
        val ok = s.command(bytes(POWER_GET_PARAM + 2, autoOffType(r), code, time))
        return readAutoOff(s, if (ok) r.copy(autoOffCode = code, autoOffTime = time, autoOffWhenRemoved = code == AUTO_OFF_WHEN_REMOVED) else r)
    }

    /**
     * Changes one side's touch control (pass null for the side to leave alone). The other side is
     * sent back exactly as the earbuds report it, so it never needs to have been read beforehand.
     */
    fun setTouch(s: SonyBatteryClient.SonySession, r: DeviceSettings, left: TouchFunction?, right: TouchFunction?): DeviceSettings {
        val now = s.request(bytes(SYSTEM_GET_PARAM, SYSTEM_ASSIGNABLE))?.takeIf { it.size >= 5 }
        // Noise control has two codes (0x00 and 0x35); write it back in the form these earbuds report.
        val ambientCode = listOfNotNull(now?.let { u(it[3]) }, now?.let { u(it[4]) })
            .firstOrNull { TouchFunction.from(it) == TouchFunction.AMBIENT_SOUND_CONTROL } ?: TouchFunction.AMBIENT_SOUND_CONTROL.code
        fun code(f: TouchFunction) = if (f == TouchFunction.AMBIENT_SOUND_CONTROL) ambientCode else f.code
        val l = left?.let(::code) ?: now?.let { u(it[3]) } ?: r.touchLeft?.let(::code)
        val rt = right?.let(::code) ?: now?.let { u(it[4]) } ?: r.touchRight?.let(::code)
        if (l == null || rt == null) throw EarbudsUnavailableException("Couldn't load your touch controls. Make sure your ${EarbudModel.active.noun} are connected and try again.")
        // Moving Playback Control (it carries the voice assistant) makes the earbuds ask for a
        // confirmation first (alert 99 00 0f); without an answer they keep the old setting.
        s.withConfirmation(timeoutMs = 1_200) { s.command(bytes(SYSTEM_GET_PARAM + 2, SYSTEM_ASSIGNABLE, 0x02, l, rt)) }
        val after = readTouch(s, r)
        val side = if (left != null) "left" else "right"
        if ((left != null && after.touchLeft != left) || (right != null && after.touchRight != right)) {
            // The earbuds turn a function they don't accept into "not assigned"; put the old one back.
            if (now != null) runCatching { s.withConfirmation(timeoutMs = 1_200) { s.command(bytes(SYSTEM_GET_PARAM + 2, SYSTEM_ASSIGNABLE, 0x02, u(now[3]), u(now[4]))) } }
            val name = (left ?: right)?.label ?: "That control"
            throw EarbudsUnavailableException("$name isn't available on the $side earbud. Nothing was changed.")
        }
        return after
    }

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
    private fun u(value: Byte) = value.toInt() and 0xff
}

object SettingsStore {
    private const val PREFS = "device_settings"
    private const val KEY = "json"

    /** Just the saved model name, without building the whole settings. */
    fun modelName(context: Context): String? = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        ?.let { runCatching { org.json.JSONObject(it).optString("model").ifEmpty { null } }.getOrNull() }

    fun load(context: Context): DeviceSettings {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return DeviceSettings()
        return try {
            val o = JSONObject(raw)
            fun str(k: String) = if (o.has(k)) o.getString(k) else null
            fun int(k: String) = if (o.has(k)) o.getInt(k) else null
            fun bool(k: String) = if (o.has(k)) o.getBoolean(k) else null
            EarbudModel.noteModelName(str("model"))
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
                autoOffCode = int("aoCode"), autoOffTime = int("aoTime"),
                autoOffOptions = if (o.has("aoOpts")) o.getJSONArray("aoOpts").let { a -> (0 until a.length()).map(a::getInt) } else null,
                touchLeft = TouchFunction.from(int("tl")), touchRight = TouchFunction.from(int("tr")), touchCap = str("tcap"), headGestures = bool("hg"), autoSwitch = bool("asw"),
                noiseCycle = NoiseCycle.from(int("cycle")),
                general = if (o.has("gs")) o.getJSONArray("gs").let { a ->
                    (0 until a.length()).map { i ->
                        val g = a.getJSONObject(i)
                        GeneralSetting(g.getInt("slot"), g.getString("key"), if (g.has("on")) g.getBoolean("on") else null)
                    }
                } else null,
                promptsOn = bool("vg"), promptVolume = int("vgVol"), promptTail = int("vgTail"), colorCode = int("color"),
                leModeSupported = bool("leSup"), leOn = bool("leOn"),
                functions1 = str("fn1"), functions2 = str("fn2"),
                readAt = if (o.has("at")) o.getLong("at") else 0L
            )
        } catch (_: Exception) {
            DeviceSettings()
        }
    }

    fun save(context: Context, s: DeviceSettings) {
        // Picks up a switch made in Sony's app or by hand, not only SunPods' own.
        // Only a first guess: after that the user's own switch decides. The earbuds can report LE
        // while the phone has them on Classic, and that mustn't turn the Classic repair off.
        if (!SonyControlSessionManager.classicOnlyKnown(context)) s.leOn?.let { SonyControlSessionManager.setClassicOnly(context, !it) }
        EarbudModel.noteModelName(s.modelName)
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
        s.autoOffCode?.let { o.put("aoCode", it) }
        s.autoOffTime?.let { o.put("aoTime", it) }
        s.autoOffOptions?.let { o.put("aoOpts", org.json.JSONArray(it)) }
        s.touchLeft?.let { o.put("tl", it.code) }
        s.touchRight?.let { o.put("tr", it.code) }
        s.touchCap?.let { o.put("tcap", it) }
        s.headGestures?.let { o.put("hg", it) }
        s.autoSwitch?.let { o.put("asw", it) }
        s.noiseCycle?.let { o.put("cycle", it.code) }
        s.general?.let { list ->
            o.put("gs", org.json.JSONArray().also { a ->
                list.forEach { g -> a.put(JSONObject().put("slot", g.slot).put("key", g.key).also { j -> g.on?.let { j.put("on", it) } }) }
            })
        }
        s.promptsOn?.let { o.put("vg", it) }
        s.colorCode?.let { o.put("color", it) }
        s.leModeSupported?.let { o.put("leSup", it) }
        s.leOn?.let { o.put("leOn", it) }
        s.promptVolume?.let { o.put("vgVol", it) }
        s.promptTail?.let { o.put("vgTail", it) }
        s.functions1?.let { o.put("fn1", it) }
        s.functions2?.let { o.put("fn2", it) }
        o.put("at", s.readAt)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, o.toString()).apply()
    }
}

class EarbudsUnavailableException(message: String) : IOException(message)
