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
        // [f1 04 keyType count value..]
        val cap = s.request(bytes(GET_CAPABILITY, TYPE), timeoutMs = 700) ?: return null
        if (cap.size < 4) return null
        val count = u(cap[3])
        val options = (0 until count).mapNotNull { i -> cap.getOrNull(4 + i)?.let(::u) }
        if (options.isEmpty()) return null
        val now = s.request(bytes(GET_PARAM, TYPE), timeoutMs = 700)?.takeIf { it.size >= 3 }?.let { u(it[2]) }
        return now to options
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

    internal fun tips(s: SonyBatteryClient.SonySession): Tips? {
        val p = s.request(bytes(0xf0, TYPE), timeoutMs = 700) ?: return null
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
            if (done == null) return Result(null, null, "The test didn't finish. Keep both earbuds in and try again.")
            if (error != 0) return Result(null, null, errorText(error))
            val r = s.request(bytes(0xfa, TYPE), timeoutMs = 2_000)
                ?: return Result(null, null, "Your earbuds didn't send a result. Try again.")
            if (r.size < 4) return Result(null, null, "Your earbuds didn't send a result. Try again.")
            return Result(u(r[2]) == 0x00, u(r[3]) == 0x00, null)
        } finally {
            runCatching { s.command(bytes(0xf4, TYPE, 0x00, 0x00)) }
        }
    }

    private fun errorText(code: Int) = when (code) {
        0x01 -> "The left earbud isn't connected. Put both earbuds in and try again."
        0x02 -> "The right earbud isn't connected. Put both earbuds in and try again."
        0x03 -> "The fit test isn't available right now. It needs both earbuds in your ears and a Classic connection."
        0x04 -> "The left earbud couldn't be measured. Push it in gently and try again."
        0x05 -> "The right earbud couldn't be measured. Push it in gently and try again."
        0x06 -> "Neither earbud could be measured. Push them in gently and try again."
        else -> "The measurement didn't work. Find a quiet spot and try again."
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
