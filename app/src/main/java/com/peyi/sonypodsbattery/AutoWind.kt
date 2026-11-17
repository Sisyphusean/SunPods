package com.peyi.sonypodsbattery

import android.content.Context

/*
 * Automatic wind noise reduction, done by the earbuds themselves (no phone mic, works with
 * SunPods closed). From Sony Sound Connect: noise control type 0x15
 * (MODE_NC_ASM_AUTO_NC_MODE_SWITCH_AND_ASM_SEAMLESS, NoiseCancellingType.DUAL_AUTO):
 *
 *   NCASM_SET_PARAM 0x68 [0x15, change 1, on/off, mode (0 NC, 1 ambient), nc value, voice, level]
 *   NCASM_GET_PARAM 0x66 [0x15] -> RET 0x67 with the same 8 byte layout; NTFY 0x69 likewise
 *
 *   nc value: 2 normal noise cancelling (both mics), 1 wind noise reduction (one mic),
 *             3 auto; while auto the earbuds report 4 (wind detected, one mic) or 5 (calm, both).
 *
 * Sony shows "Auto Wind Noise Reduction" only for earbuds that answer this type with 8 bytes,
 * so SunPods asks once and only uses the 8 byte form when the earbuds do.
 */
internal object AutoWind {
    private const val PREFS = "auto_wind"
    private const val TYPE = 0x15
    @Volatile var appContext: Context? = null

    private fun p(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** true: supported, false: these earbuds answered differently, null: not checked yet. */
    fun supported(context: Context): Boolean? =
        if (p(context).contains("supported")) p(context).getBoolean("supported", false) else null

    /** On unless turned off: wind roar is never wanted. */
    fun enabled(context: Context) = p(context).getBoolean("enabled", true)

    fun setEnabled(context: Context, on: Boolean) = p(context).edit().putBoolean("enabled", on).commit()

    /** The earbuds reported wind within the last few minutes (they switched to one mic). */
    fun windNow(context: Context): Boolean {
        val pr = p(context)
        return pr.getBoolean("wind", false) && System.currentTimeMillis() - pr.getLong("wind_at", 0L) < 10 * 60_000L
    }

    /** The nc value byte to send, or null to keep the older 7 byte command. */
    fun ncValue(context: Context): Int? = if (supported(context) == true) (if (enabled(context)) 3 else 2) else null

    /**
     * Reads type 0x15 from the earbuds. Learns whether it's supported, notes wind, and if auto
     * wind is wanted but not set on the earbuds, sets it while keeping their current mode.
     */
    fun sync(context: Context, s: SonyBatteryClient.SonySession) {
        if (supported(context) == false) return
        val reply = s.request(byteArrayOf(0x66, TYPE.toByte()), 800)
        if (reply == null) {
            // The WF-1000XM5 doesn't answer this type at all (it only offers 0x17, dual NC without wind modes).
            p(context).edit().putBoolean("supported", false).apply()
            DiagnosticsStore.add(context, "Auto wind: not offered by these earbuds")
            return
        }
        val ok = reply.size >= 8 && u(reply[1]) == TYPE && u(reply[4]) <= 1 && u(reply[5]) in 0..5
        if (supported(context) == null) {
            p(context).edit().putBoolean("supported", ok).apply()
            DiagnosticsStore.add(context, "Auto wind: ${if (ok) "supported" else "not offered"} (${hex(reply)})")
        }
        if (!ok) return
        note(context, u(reply[5]))
        val nc = u(reply[5])
        val autoSet = nc >= 3
        if (enabled(context) != autoSet) {
            s.send(byteArrayOf(0x68, TYPE.toByte(), 0x01, reply[3], reply[4], (if (enabled(context)) 3 else 2).toByte(), reply[6], reply[7]))
            s.waitFor(1_500) { it.type == SonyProtocol.ACK }
            DiagnosticsStore.add(context, "Auto wind ${if (enabled(context)) "switched on" else "switched off"} on the earbuds")
        }
    }

    /** Called for every noise control notification the earbuds push while a session is open. */
    fun onNotify(payload: ByteArray) {
        val context = appContext ?: return
        DiagnosticsStore.add(context, "Noise control notify: ${hex(payload)}")
        if (payload.size >= 8 && u(payload[1]) == TYPE) note(context, u(payload[5]))
    }

    private fun note(context: Context, nc: Int) {
        val wind = nc == 4 || nc == 1
        val pr = p(context)
        if (pr.getBoolean("wind", false) != wind) DiagnosticsStore.add(context, if (wind) "Earbuds: wind detected" else "Earbuds: wind gone")
        pr.edit().putBoolean("wind", wind).putLong("wind_at", System.currentTimeMillis()).apply()
    }

    private fun u(b: Byte) = b.toInt() and 0xff
    private fun hex(b: ByteArray) = b.joinToString(" ") { "%02x".format(it) }
}

/** What the screen shows about automatic wind reduction. */
data class AutoWindUi(val supported: Boolean?, val enabled: Boolean, val windNow: Boolean) {
    companion object {
        fun read(context: Context) = AutoWindUi(AutoWind.supported(context), AutoWind.enabled(context), AutoWind.windNow(context))
    }
}
