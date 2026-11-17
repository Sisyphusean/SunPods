package com.peyi.sonypodsbattery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Settings change drain: ambient x0.95, off x0.67, LDAC x1.15, DSEE x1.08, Speak-to-Chat x1.05,
 * volume x(1 + 0.8 (v - 0.5)). Health must be read with those taken out, and changing a
 * setting (even all at once, halfway through) must not read as wear.
 */
class BatteryHealthFactorsTest {
    private val day = 86_400_000L
    private val min = 60_000L

    data class Config(val mode: Char, val volume: Int, val codec: Char, val dsee: Boolean, val s2c: Boolean)

    private fun factor(c: Config): Double =
        (when (c.mode) { 'A' -> 0.95; 'O' -> 0.67; else -> 1.0 }) *
            (if (c.codec == 'L') 1.15 else 1.0) * (if (c.dsee) 1.08 else 1.0) * (if (c.s2c) 1.05 else 1.0) *
            (1 + 0.8 * (c.volume / 100.0 - 0.5))

    /** [base] is the true NC/AAC/50% rate on a given day (wear makes it rise). */
    private fun simulate(days: Int, seed: Int, base: (Int) -> Double, config: (Int, Random) -> Config): List<BatterySample> {
        val rnd = Random(seed)
        val out = mutableListOf<BatterySample>()
        var l = 100.0; var r = 100.0
        for (d in 0 until days) {
            var t = d * day + 8 * 3_600_000L
            repeat(1 + rnd.nextInt(3)) {
                val c = config(d, rnd)
                val target = 60.0 + rnd.nextDouble() * 40
                l = maxOf(l, target); r = maxOf(r, target)
                out += BatterySample(t, l.toInt(), r.toInt(), 80, true, true, false, c.mode, false, c.volume, c.codec, c.dsee, c.s2c)
                t += 20 * min
                val end = t + (40 + rnd.nextInt(140)) * min
                while (t < end && l > 3 && r > 3) {
                    val step = if (rnd.nextDouble() < 0.1) 15 * min else 5 * min
                    val h = step / 3_600_000.0
                    val rate = base(d) * factor(c)
                    l -= rate * h * (1 + (rnd.nextDouble() - 0.5) * 0.4)
                    r -= rate * h * (1 + (rnd.nextDouble() - 0.5) * 0.4)
                    t += step
                    if (rnd.nextDouble() < 0.05) continue
                    // Volume wobbles a little within a session, as it does.
                    val vol = (c.volume + rnd.nextInt(-3, 4)).coerceIn(0, 100)
                    out += BatterySample(t, l.toInt(), r.toInt(), 80, false, false, false, c.mode, true, vol, c.codec, c.dsee, c.s2c)
                }
                t += 60 * min
            }
        }
        return out
    }

    private fun report(samples: List<BatterySample>, now: Long) =
        BatteryHealth.estimate(BatteryHealth.stretches(samples), BatteryHealth.paired(samples), now)!!

    private fun randomConfig(rnd: Random) = Config(
        mode = rnd.nextDouble().let { if (it < 0.6) 'N' else if (it < 0.85) 'A' else 'O' },
        volume = 30 + rnd.nextInt(50),
        codec = if (rnd.nextBoolean()) 'L' else 'A',
        dsee = rnd.nextDouble() < 0.4,
        s2c = rnd.nextDouble() < 0.3
    )

    @Test
    fun readsHealthAtYourUsualSetupAndLearnsCosts() {
        val r = report(simulate(45, 21, { 12.0 }) { _, rnd -> randomConfig(rnd) }, 45 * day)
        val s = r.setup!!
        val truth = 100 / (12.0 * factor(Config(s.mode, s.volume.toInt(), s.codec, s.dsee, s.speakToChat)))
        println("setup $s truth $truth h, left ${r.left!!.hoursPerCharge} right ${r.right!!.hoursPerCharge}, costs ${r.costs}")
        assertEquals(truth, r.left!!.hoursPerCharge, truth * 0.06)
        assertEquals(truth, r.right!!.hoursPerCharge, truth * 0.06)
        val (_, lo, hi) = r.imbalance!!
        assertTrue(lo <= 1.08 && hi >= 0.92)
        val ldac = r.costs.first { it.label.startsWith("LDAC") }.change
        val dsee = r.costs.first { it.label.startsWith("DSEE") }.change
        assertEquals(0.15, ldac, 0.05)
        assertEquals(0.08, dsee, 0.05)
    }

    @Test
    fun changingSettingsHalfwayIsNotWear() {
        // Two months on AAC, 40% volume, DSEE off; then LDAC, 70% volume, DSEE on. No wear at all.
        val samples = simulate(120, 22, { 12.0 }) { d, _ ->
            if (d < 60) Config('N', 40, 'A', false, false) else Config('N', 70, 'L', true, false)
        }
        val r = report(samples, 120 * day)
        println("switch, no wear: sinceFirst ${r.sinceFirst}")
        assertNotNull(r.sinceFirst)
        assertTrue("no wear must read as about the same, got ${r.sinceFirst}", kotlin.math.abs(r.sinceFirst!!) < 0.04)
    }

    @Test
    fun changingSettingsHalfwayStillShowsRealWear() {
        // Same switch, plus 10% real wear over four months.
        val samples = simulate(120, 23, { d -> 12.0 * (1 + 0.10 * d / 119) }) { d, _ ->
            if (d < 60) Config('N', 40, 'A', false, false) else Config('N', 70, 'L', true, false)
        }
        val r = report(samples, 120 * day)
        println("switch + wear: sinceFirst ${r.sinceFirst}")
        assertTrue("expected about 8% less per charge, got ${r.sinceFirst}", r.sinceFirst!! in -0.14..-0.04)
    }

    @Test
    fun steadySettingsCostNothing() {
        val r = report(simulate(30, 24, { 12.0 }) { _, _ -> Config('N', 50, 'L', true, false) }, 30 * day)
        println("steady: ${r.costs} left ${r.left?.hoursPerCharge}")
        assertTrue(r.costs.isEmpty())
        assertEquals(100 / (12.0 * 1.15 * 1.08), r.left!!.hoursPerCharge, 0.4)
    }
}
