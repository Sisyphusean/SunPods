package com.peyi.sonypodsbattery

import android.content.Context

/*
 * Asks the earbuds what they support, once per app version, and keeps the raw answers for
 * diagnostics. Used to look for features we don't drive yet (such as wind noise reduction).
 *
 *   CONNECT_GET_SUPPORT_FUNCTION 0x06 [0x00] on table 1 (0x0c) and table 2 (0x0e)
 *   NCASM_GET_PARAM 0x66 [type] for every noise control type, to see which ones answer
 */
internal object FeatureProbe {
    private const val PREFS = "feature_probe"

    fun due(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("lines", null) == null

    fun run(s: SonyBatteryClient.SonySession): List<String> {
        val out = mutableListOf<String>()
        s.request(byteArrayOf(0x06, 0x00), 1_200, SonyProtocol.DATA_MDR)?.let { out += "T1 functions: ${hex(it)}" }
            ?: run { out += "T1 functions: no answer" }
        s.request(byteArrayOf(0x06, 0x00), 1_200, SonyProtocol.DATA_MDR_NO2)?.let { out += "T2 functions: ${hex(it)}" }
            ?: run { out += "T2 functions: no answer" }
        val answered = (0x01..0x20).mapNotNull { t ->
            s.request(byteArrayOf(0x66, t.toByte()), 350, SonyProtocol.DATA_MDR)?.let { "%02x".format(t) to hex(it) }
        }
        out += "NCASM types: " + if (answered.isEmpty()) "none" else answered.joinToString(" | ") { "${it.first}=${it.second}" }
        return out
    }

    fun save(context: Context, lines: List<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("version", SunpodsInfo.VERSION)
            .putString("lines", lines.joinToString("\n"))
            .apply()
        DiagnosticsStore.add(context, "Feature probe done (${lines.size} lines)")
    }

    fun text(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("lines", null)

    private fun hex(b: ByteArray) = b.joinToString(" ") { "%02x".format(it) }
}
