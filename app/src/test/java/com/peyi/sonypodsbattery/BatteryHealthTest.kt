package com.peyi.sonypodsbattery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Simulated real life: partial charges, the 100% plateau after charging, paused music, a few
 * sessions with noise control off, missed and late readings, 1% rounding, and a right bud
 * that drains faster than the left. The estimate has to see through all of it.
 */
class BatteryHealthTest {
    private val day = 86_400_000L
    private val min = 60_000L

    /** [leftRate]/[rightRate] are true drain in %/h with noise cancelling, as a function of the day. */
    private fun simulate(days: Int, seed: Int, leftRate: (Int) -> Double, rightRate: (Int) -> Double): List<BatterySample> {
        val rnd = Random(seed)
        val out = mutableListOf<BatterySample>()
        var t = 0L
        var l = 100.0; var r = 100.0
        for (d in 0 until days) {
            t = d * day + 8 * 3_600_000L
            repeat(1 + rnd.nextInt(3)) {
                // Partial top-up in the case before each session (never a full drain).
                val target = 60.0 + rnd.nextDouble() * 40
                l = maxOf(l, target); r = maxOf(r, target)
                out += BatterySample(t, l.toInt(), r.toInt(), 80, true, true, false, 'N', false)
                t += 30 * min
                val full = l >= 99.5
                var plateau = if (full) 20 * min else 0L
                val mode = if (rnd.nextDouble() < 0.2) 'O' else 'N'
                val factor = if (mode == 'O') 0.66 else 1.0
                val length = (30 + rnd.nextInt(150)) * min
                val end = t + length
                var paused = false
                while (t < end && l > 3 && r > 3) {
                    val step = if (rnd.nextDouble() < 0.1) 15 * min else 5 * min
                    if (rnd.nextDouble() < 0.08) paused = !paused
                    val h = step / 3_600_000.0
                    val nl = 1 + (rnd.nextDouble() - 0.5) * 0.4
                    val nr = 1 + (rnd.nextDouble() - 0.5) * 0.4
                    val dl = (if (paused) 2.0 else leftRate(d) * factor) * h * nl
                    val dr = (if (paused) 2.0 else rightRate(d) * factor) * h * nr
                    if (plateau > 0) { plateau -= step; t += step; l -= dl * 0.2; r -= dr * 0.2 } else { l -= dl; r -= dr; t += step }
                    // A fuel gauge holds 100 for a while after charging.
                    val shownL = if (full && plateau > 0) 100 else l.toInt()
                    val shownR = if (full && plateau > 0) 100 else r.toInt()
                    if (rnd.nextDouble() < 0.05) continue // missed reading
                    out += BatterySample(t, shownL, shownR, 80, false, false, false, mode, !paused)
                }
                t += 60 * min
            }
        }
        return out
    }

    private fun run(samples: List<BatterySample>, now: Long): BatteryHealth.Report {
        val report = BatteryHealth.estimate(BatteryHealth.stretches(samples), BatteryHealth.paired(samples), now)
        assertNotNull(report)
        return report!!
    }

    @Test
    fun recoversTrueLifeFromPartialCharges() {
        val samples = simulate(45, 1, { 12.0 }, { 14.0 })
        val r = run(samples, 45 * day)
        assertEquals('N', r.mode)
        val left = r.left!!.hoursPerCharge
        val right = r.right!!.hoursPerCharge
        println("left %.2f h (%.2f..%.2f), right %.2f h (%.2f..%.2f), imbalance %s".format(
            left, r.left!!.low, r.left!!.high, right, r.right!!.low, r.right!!.high, r.imbalance))
        assertEquals(100 / 12.0, left, 0.6)
        assertEquals(100 / 14.0, right, 0.5)
        val (ratio, lo, _) = r.imbalance!!
        assertEquals(14.0 / 12.0, ratio, 0.06)
        assertTrue("worn right bud should be flagged", lo > 1.08)
    }

    @Test
    fun evenBudsAreNotFlagged() {
        val r = run(simulate(30, 2, { 12.5 }, { 12.5 }), 30 * day)
        val (_, lo, hi) = r.imbalance!!
        println("even: ${r.imbalance}")
        assertTrue(lo <= 1.08 && hi >= 0.92)
    }

    @Test
    fun seesFadeAgainstFirstMonth() {
        // 11 %/h when tracking starts, wearing to 12.5 %/h four months later.
        val samples = simulate(120, 3, { d -> 11.0 + 1.5 * d / 119 }, { d -> 11.0 + 1.5 * d / 119 })
        val r = run(samples, 120 * day)
        println("fade: sinceFirst ${r.sinceFirst}")
        val change = r.sinceFirst!!
        assertTrue("expected roughly 10% less per charge, got $change", change in -0.17..-0.05)
    }

    @Test
    fun notEnoughDataShowsNothing() {
        val samples = simulate(1, 4, { 12.0 }, { 12.0 }).take(8)
        val r = BatteryHealth.estimate(BatteryHealth.stretches(samples), BatteryHealth.paired(samples), day)
        assertTrue(r == null || (r.left == null && r.right == null))
    }
}
