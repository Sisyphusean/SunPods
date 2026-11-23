package com.peyi.sonypodsbattery

/**
 * Earbud features from Sony's SYSTEM commands (table 1) that Sound Connect offers on the XM5:
 * the voice assistant, the earbud fit test and resetting the earbuds' settings. Value codes are
 * Sound Connect's own (VoiceAssistant, EarpieceSize, EarpieceFittingDetection*, ResetType).
 */
object VoiceAssistantSetting {
    private const val GET_CAPABILITY = 0xf0
    private const val GET_PARAM = 0xf6
    private const val SET_PARAM = 0xf8
    private const val TYPE = 0x04

    /** The current choice and the choices the earbuds offer, or null if they don't have one. */
    internal fun read(s: SonyBatteryClient.SonySession): Pair<Int?, List<Int>>? {
        val options = s.request(bytes(GET_CAPABILITY, TYPE), timeoutMs = 700)?.let(::options) ?: return null
        val now = s.request(bytes(GET_PARAM, TYPE), timeoutMs = 2_000)?.takeIf { it.size >= 3 }?.let { u(it[2]) }
        return now to options
    }

    /** The choices from a capability reply [f1 04 keyType count value..]; null when there are none. */
    fun options(cap: ByteArray): List<Int>? {
        if (cap.size < 4) return null
        val count = u(cap[3])
        return (0 until count).mapNotNull { i -> cap.getOrNull(4 + i)?.let(::u) }.ifEmpty { null }
    }

    internal fun set(s: SonyBatteryClient.SonySession, value: Int) {
        s.command(bytes(SET_PARAM, TYPE, value))
    }

    fun label(value: Int): String = when (value) {
        0x30 -> "Your Phone's Assistant"
        0x31 -> "Google Assistant"
        0x32 -> "Amazon Alexa"
        0x34 -> "Sony Voice Assistant"
        0xff -> "None"
        else -> "Option ${"%02x".format(value)}"
    }

    /** For a row's value: short enough that the title never wraps. */
    fun short(value: Int): String = when (value) {
        0x30 -> "Phone Default"
        0x31 -> "Google"
        0x32 -> "Alexa"
        0x34 -> "Sony"
        0xff -> "Off"
        else -> label(value)
    }

    fun detail(value: Int): String? = when (value) {
        0x31 -> "Talk to Google Assistant from your earbuds"
        0x32 -> "Talk to Alexa from your earbuds"
        0x30 -> "Uses the assistant your phone is set to"
        0xff -> "No voice assistant from the earbuds"
        else -> null
    }

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }
    private fun u(b: Byte) = b.toInt() and 0xff
}

/**
 * Sony's earbud fit test (WEARING_STATUS_DETECTOR, inquired type 0x06). Each earbud plays a tone and
 * measures how well its tip seals:
 *   capability f0 06 -> f1 06 ? nSeries {series nSizes size..}
 *   test mode  f4 06 [1 in / 0 out, 0]
 *   start      f8 06 [0 start, 0, series, size]
 *   progress   f9 06 [status, error, .., series, size]   status 2 done, 3 failed
 *   result     fa 06 -> fb 06 [left, right, seriesL, seriesR, sizeL, sizeR]   0 good seal, 1 poor
 */
object FitTest {
    private const val TYPE = 0x06

    data class Tips(val series: Int, val sizes: List<Int>)
    data class Result(val left: Boolean?, val right: Boolean?, val error: String?)

    fun sizeLabel(size: Int) = when (size) { 0 -> "SS"; 1 -> "S"; 2 -> "M"; 3 -> "L"; 4 -> "LL"; else -> "?" }

    internal fun tips(s: SonyBatteryClient.SonySession): Tips? = s.request(bytes(0xf0, TYPE), timeoutMs = 700)?.let(::tips)

    /** Tip sizes from a capability reply [f1 06 ? nSeries series nSizes size..]. */
    fun tips(p: ByteArray): Tips? {
        if (p.size < 6 || u(p[3]) < 1) return null
        val series = u(p[4])
        val n = u(p[5])
        val sizes = (0 until n).mapNotNull { p.getOrNull(6 + it)?.let(::u) }
        return if (sizes.isEmpty()) null else Tips(series, sizes)
    }

    /** Runs the test with the tips the user says are on. Blocking, up to about 40 seconds. */
    internal fun run(s: SonyBatteryClient.SonySession, tips: Tips, size: Int): Result {
        s.command(bytes(0xf4, TYPE, 0x01, 0x00))
        try {
            s.send(bytes(0xf8, TYPE, 0x00, 0x00, tips.series, size))
            var error = 0
            val done = s.waitFor(40_000) { f ->
                val p = f.payload
                if (p.size >= 4 && u(p[0]) == 0xf9 && u(p[1]) == TYPE) {
                    error = u(p[3])
                    error != 0 || u(p[2]) == 0x02 || u(p[2]) == 0x03
                } else false
            }
            if (done == null) return Result(null, null, "The test stopped early. Keep both earbuds in your ears and try again.")
            if (error != 0) return Result(null, null, errorText(error))
            val r = s.request(bytes(0xfa, TYPE), timeoutMs = 2_000)
                ?: return Result(null, null, "Couldn't get a result. Try the test again.")
            if (r.size < 4) return Result(null, null, "Couldn't get a result. Try the test again.")
            return Result(u(r[2]) == 0x00, u(r[3]) == 0x00, null)
        } finally {
            runCatching { s.command(bytes(0xf4, TYPE, 0x00, 0x00)) }
        }
    }

    private fun errorText(code: Int) = when (code) {
        0x01 -> "Your left earbud isn't connected. Put both in and try again."
        0x02 -> "Your right earbud isn't connected. Put both in and try again."
        0x03 -> "The fit test needs both earbuds in your ears and a Classic connection."
        0x04 -> "Couldn't measure your left earbud. Gently press it in and try again."
        0x05 -> "Couldn't measure your right earbud. Gently press it in and try again."
        0x06 -> "Couldn't measure either earbud. Gently press them in and try again."
        else -> "It was too noisy to measure. Find a quieter spot and try again."
    }

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }
    private fun u(b: Byte) = b.toInt() and 0xff
}

/** Puts the earbuds' settings back to how they came: f8 09 [0 settings only]. */
object EarbudReset {
    internal fun resetSettings(s: SonyBatteryClient.SonySession): Boolean {
        s.send(byteArrayOf(0xf8.toByte(), 0x09, 0x00))
        val reply = s.waitFor(5_000) { f -> f.payload.size >= 3 && (f.payload[0].toInt() and 0xff) == 0xf9 && f.payload[1].toInt() == 0x09 }
        return reply == null || (reply.payload[2].toInt() and 0xff) == 0x00
    }
}

/**
 * Safe listening (table 2, inquired type 0x03 for true wireless earbuds): the earbuds measure how
 * loud and how long you listen. Volume protection (type 0x04) limits loudness to the WHO guideline
 * for adults or for sensitive listeners. The earbuds don't report either setting back, so the last
 * choice is kept here, as Sound Connect does.
 *   switch      58 03 [logging, live meter]   0 on, 1 off. Sony never turns both on: the live
 *               meter ("preview") runs only while a meter is on screen, then logging comes back.
 *   live level  5a 03 -> 5b 03 [dB] [cause]: dB ff = no reading, cause 0 not playing, 1 on a call,
 *               2 earbud out, 3 none
 *   protection  54 04 [0 standard / 1 sensitive]
 */
object SafeListening {
    private const val PREFS = "safe_listening"
    private const val T2 = SonyProtocol.DATA_MDR_NO2

    fun on(context: android.content.Context) = context.getSharedPreferences(PREFS, 0).getBoolean("on", true)
    fun sensitive(context: android.content.Context) = context.getSharedPreferences(PREFS, 0).getBoolean("sensitive", false)

    internal fun setOn(context: android.content.Context, s: SonyBatteryClient.SonySession, on: Boolean): Boolean {
        // Logging on or off; the live meter stays off (Sony's app never runs both).
        val ok = s.command(byteArrayOf(0x58, 0x03, if (on) 0x00 else 0x01, 0x01), type = T2)
        if (ok) context.getSharedPreferences(PREFS, 0).edit().putBoolean("on", on).apply()
        return ok
    }

    /** What the earbuds hear right now. */
    sealed interface Level {
        data class Db(val db: Int) : Level
        data object NotPlaying : Level
        data object OnCall : Level
        data object NotWorn : Level
    }

    /** Starts the earbuds' live measurement; logging pauses while it runs. */
    internal fun startMeter(s: SonyBatteryClient.SonySession): Boolean =
        s.command(byteArrayOf(0x58, 0x03, 0x01, 0x00), type = T2)

    /** Puts the earbuds back to Safe Listening logging on or off. */
    internal fun stopMeter(s: SonyBatteryClient.SonySession, loggingOn: Boolean): Boolean =
        s.command(byteArrayOf(0x58, 0x03, if (loggingOn) 0x00 else 0x01, 0x01), type = T2)

    internal fun readLevel(s: SonyBatteryClient.SonySession): Level? {
        s.send(byteArrayOf(0x5a, 0x03), T2)
        val p = s.waitFor(1_500) { f ->
            f.type == T2 && f.payload.size >= 4 && (f.payload[0].toInt() and 0xff) == 0x5b && f.payload[1].toInt() == 0x03
        }?.payload ?: return null
        val db = p[2].toInt() and 0xff
        return when {
            db == 0xff -> when (p[3].toInt()) { 1 -> Level.OnCall; 2 -> Level.NotWorn; else -> Level.NotPlaying }
            db == 0 -> Level.NotPlaying
            else -> Level.Db(db)
        }
    }

    internal fun setSensitive(context: android.content.Context, s: SonyBatteryClient.SonySession, sensitive: Boolean): Boolean {
        val ok = s.command(byteArrayOf(0x54, 0x04, if (sensitive) 0x01 else 0x00), type = T2)
        if (ok) context.getSharedPreferences(PREFS, 0).edit().putBoolean("sensitive", sensitive).apply()
        return ok
    }
}
