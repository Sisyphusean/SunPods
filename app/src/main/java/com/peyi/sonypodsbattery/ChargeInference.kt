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

    /**
     * [outOfCase] means the part can't be charging right now: a bud the earbuds report while the
     * other bud is missing has just come out of the case (a bud in the case drops off the link).
     */
    fun charging(context: Context, part: String, previous: Int?, previousAt: Long, now: Int?, reported: Boolean, outOfCase: Boolean = false): Boolean {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val t = System.currentTimeMillis()
        // 0 means the part wasn't reporting (a bud in the case), not that it was empty, so it's no baseline.
        val before = previous?.takeIf { it > 0 }
        val level = now?.takeIf { it > 0 }
        if (level == null || outOfCase) {
            p.edit().remove(part).apply()
            return reported && !outOfCase
        }
        val recent = before != null && t - previousAt < RECENT_MS
        when {
            recent && level > before!! -> p.edit().putLong(part, t).apply()
            recent && level < before!! -> p.edit().remove(part).apply()
        }
        if (reported) return true
        val roseAt = p.getLong(part, 0L)
        if (roseAt == 0L) return false
        return t - roseAt < HOLD_MS || (level >= 100 && t - roseAt < 3 * HOLD_MS)
    }
}
