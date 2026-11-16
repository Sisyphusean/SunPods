package com.peyi.sonypodsbattery

import android.content.Context

/*
 * The WF-1000XM5 reports "not charging" for the case (and the buds) even on a charger, so the
 * status byte can't be trusted. Instead: a level that went up between two recent readings means
 * it's charging, and it stays "charging" for a while after the last rise, or while it sits at 100%.
 */
internal object ChargeInference {
    private const val PREFS = "charge_inference"
    private const val RECENT_MS = 60L * 60_000
    private const val HOLD_MS = 20L * 60_000

    fun charging(context: Context, part: String, previous: Int?, previousAt: Long, now: Int?, reported: Boolean): Boolean {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val t = System.currentTimeMillis()
        val recent = previous != null && t - previousAt < RECENT_MS
        when {
            now == null -> { p.edit().remove(part).apply(); return reported }
            recent && now > previous!! -> p.edit().putLong(part, t).apply()
            recent && now < previous!! -> p.edit().remove(part).apply()
        }
        if (reported) return true
        val roseAt = p.getLong(part, 0L)
        if (roseAt == 0L) return false
        return t - roseAt < HOLD_MS || (now >= 100 && t - roseAt < 3 * HOLD_MS)
    }
}
