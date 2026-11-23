package com.peyi.sonypodsbattery

import android.content.Context
import java.util.Calendar

/*
 * Voice prompts ("Bluetooth connected", "Battery about 20%", mode names), from Sound Connect's
 * MDR v2 table 2, sent as frame type 0x0e. The WF-1000XM5 lists function 0x42
 * (voice guidance with language switch and volume adjustment).
 *
 *   GET_PARAM 0x46 [0x01]            -> RET 0x47 [0x01, on/off, language] on = 0, off = 1
 *   SET_PARAM 0x48 [0x01, on/off, language] (the language must be sent back, or it's ignored)
 *   GET_PARAM 0x46 [0x20]            -> RET 0x47 [0x20, volume, x]       volume -2..+2 (signed)
 *   SET_PARAM 0x48 [0x20, volume, x] (x is echoed back as the earbuds reported it)
 */
internal object VoicePrompts {
    private const val T2 = SonyProtocol.DATA_MDR_NO2
    private const val GET = 0x46
    private const val SET = 0x48
    private const val SETTING = 0x01
    private const val VOLUME = 0x20

    data class State(val on: Boolean?, val volume: Int?, val tail: Int?)

    fun read(s: SonyBatteryClient.SonySession): State {
        // Right after a change the earbuds can take over a second to answer, so wait longer and
        // ask once more before giving up.
        val raw = s.request(byteArrayOf(GET.toByte(), SETTING.toByte()), 2_000, T2)
            ?: s.request(byteArrayOf(GET.toByte(), SETTING.toByte()), 2_000, T2)
        val onOff = raw?.takeIf { it.size >= 3 }?.let { it[2].toInt() == 0 }
        val vol = s.request(byteArrayOf(GET.toByte(), VOLUME.toByte()), 2_000, T2)?.takeIf { it.size >= 3 }
        return State(onOff, vol?.get(2)?.toInt()?.coerceIn(-2, 2), vol?.getOrNull(3)?.toInt()?.and(0xff))
    }

    /**
     * On earbuds that can switch the prompt language (the WF-1000XM5), the switch carries the
     * language too: SET 0x48 [0x01, on/off, language]. Without it the earbuds ignore the change,
     * so read the current language first and send it back unchanged.
     */
    fun setOn(s: SonyBatteryClient.SonySession, on: Boolean): Boolean {
        val language = s.request(byteArrayOf(GET.toByte(), SETTING.toByte()), 800, T2)?.getOrNull(3)
        val v: Byte = if (on) 0x00 else 0x01
        val payload = if (language != null) byteArrayOf(SET.toByte(), SETTING.toByte(), v, language)
        else byteArrayOf(SET.toByte(), SETTING.toByte(), v)
        return s.command(payload, type = T2)
    }

    fun setVolume(s: SonyBatteryClient.SonySession, volume: Int, tail: Int?): Boolean {
        val x = tail ?: read(s).tail ?: 0x00
        return s.command(byteArrayOf(SET.toByte(), VOLUME.toByte(), volume.coerceIn(-2, 2).toByte(), x.toByte()), type = T2)
    }
}

/**
 * Quiet hours: prompts off overnight, back to what you had in the morning. The setting lives on
 * the earbuds, so once switched it holds even when SunPods isn't running. Checked on each refresh
 * and only sends anything at the start or end of the quiet window.
 */
internal object QuietHours {
    private const val PREFS = "quiet_hours"
    private fun p(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun enabled(context: Context) = p(context).getBoolean("on", false)
    fun startHour(context: Context) = p(context).getInt("start", 22)
    fun endHour(context: Context) = p(context).getInt("end", 7)

    fun set(context: Context, on: Boolean, start: Int = startHour(context), end: Int = endHour(context)) {
        p(context).edit().putBoolean("on", on).putInt("start", start).putInt("end", end).commit()
    }

    fun inWindow(context: Context, now: Long = System.currentTimeMillis()): Boolean {
        val h = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
        val s = startHour(context); val e = endHour(context)
        return if (s == e) false else if (s < e) h in s until e else h >= s || h < e
    }

    /** You changed prompts by hand during quiet hours: leave them alone until the window ends. */
    fun overrideNow(context: Context) {
        if (enabled(context) && inWindow(context)) p(context).edit().putBoolean("override", true).apply()
    }

    fun enforce(context: Context, s: SonyBatteryClient.SonySession) {
        val pr = p(context)
        val silenced = pr.getBoolean("silenced", false)
        val want = enabled(context) && inWindow(context)
        if (!want) pr.edit().putBoolean("override", false).apply()
        if (want && !silenced && !pr.getBoolean("override", false)) {
            val current = VoicePrompts.read(s).on ?: return
            pr.edit().putBoolean("restore", current).putBoolean("silenced", true).apply()
            if (current) { VoicePrompts.setOn(s, false); SonyControlSessionManager.onOtherEarbud(context) { VoicePrompts.setOn(it, false) } }
            DiagnosticsStore.add(context, "Quiet hours: voice prompts off")
        } else if (!want && silenced) {
            if (pr.getBoolean("restore", true)) { VoicePrompts.setOn(s, true); SonyControlSessionManager.onOtherEarbud(context) { VoicePrompts.setOn(it, true) } }
            pr.edit().putBoolean("silenced", false).apply()
            DiagnosticsStore.add(context, "Quiet hours over: voice prompts restored")
        }
    }
}
