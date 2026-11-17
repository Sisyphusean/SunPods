package com.peyi.sonypodsbattery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Lopsided real life: one bud used more, one bud used alone while the other charges, and one bud
 * left out of the case on the desk (connected, not worn) while you wear the other.
 *
 * Assumptions built into the simulation: a bud working alone drains ~15% faster (it carries
 * the phone link on its own); a connected bud that isn't worn drains ~3%/h.
 */
class BatteryHealthUsageTest {
    private val day = 86_400_000L
    private val min = 60_000L

    enum class Use { BOTH, LEFT_ONLY, RIGHT_ONLY, LEFT_RIGHT_ON_DESK }

    private fun simulate(days: Int, seed: Int, leftRate: Double, rightRate: Double, pick: (Random) -> Use): List<BatterySample> {
        val rnd = Random(seed)
        val out = mutableListOf<BatterySample>()
        var l = 100.0; var r = 100.0
        for (d in 0 until days) {
            var t = d * day + 8 * 3_600_000L
            repeat(1 + rnd.nextInt(3)) {
                val use = pick(rnd)
                val target = 60.0 + rnd.nextDouble() * 40
                l = maxOf(l, target); r = maxOf(r, target)
                out += BatterySample(t, l.toInt(), r.toInt(), 80, true, true, false, 'N', false)
                t += 20 * min
                val end = t + (40 + rnd.nextInt(140)) * min
                while (t < end && l > 3 && r > 3) {
                    val step = if (rnd.nextDouble() < 0.1) 15 * min else 5 * min
                    val h = step / 3_600_000.0
                    val nl = 1 + (rnd.nextDouble() - 0.5) * 0.4
                    val nr = 1 + (rnd.nextDouble() - 0.5) * 0.4
                    // Left: in ear unless it's a right-only session (then charging in the case).
                    val leftInCase = use == Use.RIGHT_ONLY
                    val rightInCase = use == Use.LEFT_ONLY
                    val solo = use == Use.LEFT_ONLY || use == Use.RIGHT_ONLY || use == Use.LEFT_RIGHT_ON_DESK
                    if (leftInCase) l = minOf(100.0, l + 40 * h) else l -= leftRate * (if (solo) 1.15 else 1.0) * h * nl
                    if (rightInCase) r = minOf(100.0, r + 40 * h)
                    else r -= (if (use == Use.LEFT_RIGHT_ON_DESK) 3.0 else rightRate * (if (solo) 1.15 else 1.0)) * h * nr
                    t += step
                    if (rnd.nextDouble() < 0.05) continue
                    out += BatterySample(t, l.toInt(), r.toInt(), 80, leftInCase, rightInCase, false, 'N', true)
                }
                t += 60 * min
            }
        }
        return out
    }

    private fun report(samples: List<BatterySample>, now: Long) =
        BatteryHealth.estimate(BatteryHealth.stretches(samples), BatteryHealth.paired(samples), now)!!

    private fun show(name: String, r: BatteryHealth.Report) = println(
        "$name: left ${r.left?.let { "%.2f h solo=${it.solo}".format(it.hoursPerCharge) }} " +
            "right ${r.right?.let { "%.2f h solo=${it.solo}".format(it.hoursPerCharge) }} imbalance ${r.imbalance}"
    )

    @Test
    fun leftUsedMostIsNotMistakenForWear() {
        val r = report(simulate(45, 11, 12.0, 12.0) { if (it.nextDouble() < 0.6) Use.LEFT_ONLY else Use.BOTH }, 45 * day)
        show("left-heavy", r)
        assertEquals(100 / 12.0, r.left!!.hoursPerCharge, 0.6)
        assertEquals(100 / 12.0, r.right!!.hoursPerCharge, 0.6)
        val (_, lo, hi) = r.imbalance!!
        assertTrue("even buds must not be flagged", lo <= 1.08 && hi >= 0.92)
    }

    @Test
    fun budLeftOnTheDeskIsIgnored() {
        val r = report(simulate(45, 12, 12.0, 12.0) { if (it.nextBoolean()) Use.LEFT_RIGHT_ON_DESK else Use.BOTH }, 45 * day)
        show("desk", r)
        assertEquals(100 / 12.0, r.right!!.hoursPerCharge, 0.6)
        val (_, lo, hi) = r.imbalance!!
        assertTrue("an idle bud on the desk must not make the worn one look bad", lo <= 1.08 && hi >= 0.92)
    }

    @Test
    fun onlyEverOneBud() {
        val r = report(simulate(30, 13, 12.0, 12.0) { Use.LEFT_ONLY }, 30 * day)
        show("one bud", r)
        assertTrue(r.left!!.solo)
        assertEquals(100 / (12.0 * 1.15), r.left!!.hoursPerCharge, 0.5)
        assertNull(r.right)
        assertNull(r.imbalance)
    }

    @Test
    fun wornRightStillCaughtWhenLeftIsUsedMore() {
        val r = report(simulate(60, 14, 12.0, 14.0) { if (it.nextDouble() < 0.6) Use.LEFT_ONLY else Use.BOTH }, 60 * day)
        show("worn right, left-heavy", r)
        val (ratio, lo, _) = r.imbalance!!
        assertEquals(14.0 / 12.0, ratio, 0.07)
        assertTrue(lo > 1.08)
        assertEquals(100 / 12.0, r.left!!.hoursPerCharge, 0.6)
        assertEquals(100 / 14.0, r.right!!.hoursPerCharge, 0.6)
    }

    @Test
    fun mixedSoloBothWaysStaysEven() {
        val r = report(simulate(45, 15, 12.0, 12.0) {
            when (it.nextInt(4)) { 0 -> Use.LEFT_ONLY; 1 -> Use.RIGHT_ONLY; 2 -> Use.LEFT_RIGHT_ON_DESK; else -> Use.BOTH }
        }, 45 * day)
        show("mixed", r)
        assertEquals(100 / 12.0, r.left!!.hoursPerCharge, 0.6)
        assertEquals(100 / 12.0, r.right!!.hoursPerCharge, 0.6)
        val (_, lo, hi) = r.imbalance!!
        assertTrue(lo <= 1.08 && hi >= 0.92)
    }
}
