package com.peyi.sonypodsbattery

import android.content.Context
import java.io.File
import kotlin.math.max
import kotlin.random.Random

/*
 * Battery health from everyday partial discharges.
 *
 * The earbuds report charge as a percentage of their *current* capacity, so as a battery wears
 * the percentage falls faster for the same listening. Health is therefore read from the drain
 * rate (% per hour of listening), measured only where it means something:
 *
 *  - Clean stretches: a bud out of the case, not charging, readings at most 20 min apart,
 *    music playing, no call, the same noise mode throughout, the level only falling.
 *  - The misleading ends are dropped: above 95% (batteries sit at "100%" after a charge)
 *    and below 10% (the last few percent fall fast).
 *  - A stretch counts once it covers at least 30 min and an 8 point drop; its rate is a
 *    least squares fit over every reading, so 1% rounding barely matters.
 *  - Each noise mode is compared only with itself (cancelling drains faster than off).
 *  - The estimate is a time-weighted median of stretch rates over the last 30 days (wider if
 *    there's too little), with a bootstrap range, so one odd day can't move it.
 *  - Left against right uses only time when both buds were out together. Volume, codec, mode
 *    and temperature were identical, so a steady gap is wear, not usage.
 *
 * Stretches are kept for two years in battery_health.csv, far longer than the raw history,
 * so the trend against your own first month builds up over time.
 */
internal object BatteryHealth {
    private const val FILE = "battery_health.csv"
    private const val MAX_GAP_MS = 20L * 60 * 1000
    private const val KEEP_MS = 730L * 86_400_000
    private const val DAY = 86_400_000L
    private const val TOP = 95
    private const val BOTTOM = 10
    private const val MIN_HOURS = 0.5
    private const val MIN_DROP = 8.0
    private const val MAX_RATE = 60.0
    /** Hours of clean listening per bud before a number is shown. */
    private const val ENOUGH_HOURS = 2.5

    /** Sony's WF-1000XM5 ratings in % per hour; ambient isn't rated separately. */
    fun ratedHours(mode: Char): Double? = when (mode) { 'N' -> 8.0; 'O' -> 12.0; else -> null }

    data class Stretch(val start: Long, val end: Long, val side: Int, val mode: Char, val hours: Double, val drop: Double, val rate: Double)
    data class Paired(val start: Long, val end: Long, val hours: Double, val dropL: Double, val dropR: Double)

    data class Bud(val hoursPerCharge: Double, val low: Double, val high: Double, val listened: Double)
    data class Report(
        val mode: Char,
        val left: Bud?,
        val right: Bud?,
        /** Right drain / left drain over shared time, with its range; null until there's enough. */
        val imbalance: Triple<Double, Double, Double>?,
        /** Change against your own first month, as a fraction (-0.06 = 6% less per charge). */
        val sinceFirst: Double?,
        val firstMonth: Long?,
        val trackingSince: Long?,
        val windowDays: Int
    )

    // ------------------------------------------------------------------ extraction

    private fun side(s: BatterySample, side: Int) = if (side == 0) s.left to s.leftCharging else s.right to s.rightCharging

    /** Whether the step a→b is clean for one bud. */
    private fun clean(a: BatterySample, b: BatterySample, sd: Int): Boolean {
        val (la, ca) = side(a, sd)
        val (lb, cb) = side(b, sd)
        if (la == null || lb == null || ca || cb) return false
        if (la !in BOTTOM..TOP || lb !in BOTTOM..TOP || lb > la) return false
        val gap = b.time - a.time
        if (gap <= 0 || gap > MAX_GAP_MS) return false
        if ((la - lb) / (gap / 3_600_000.0) > MAX_RATE) return false
        if (a.playing == false || b.playing == false) return false
        if (a.mode != null && b.mode != null && a.mode != b.mode) return false
        return true
    }

    /** Runs of consecutive clean steps for one bud. */
    private fun runs(samples: List<BatterySample>, sd: Int): List<List<BatterySample>> {
        val out = mutableListOf<List<BatterySample>>()
        var cur = mutableListOf<BatterySample>()
        for (i in 1 until samples.size) {
            if (clean(samples[i - 1], samples[i], sd)) {
                if (cur.isEmpty()) cur.add(samples[i - 1])
                cur.add(samples[i])
            } else {
                if (cur.size >= 2) out.add(cur)
                cur = mutableListOf()
            }
        }
        if (cur.size >= 2) out.add(cur)
        return out
    }

    /** Least squares slope of level against hours, as a positive drain rate. */
    private fun slope(points: List<Pair<Double, Double>>): Double {
        val n = points.size
        val mx = points.sumOf { it.first } / n
        val my = points.sumOf { it.second } / n
        val sxx = points.sumOf { (it.first - mx) * (it.first - mx) }
        if (sxx <= 0) return 0.0
        return -points.sumOf { (it.first - mx) * (it.second - my) } / sxx
    }

    fun stretches(samples: List<BatterySample>): List<Stretch> = (0..1).flatMap { sd ->
        runs(samples, sd).mapNotNull { run ->
            val t0 = run.first().time
            val hours = (run.last().time - t0) / 3_600_000.0
            val first = side(run.first(), sd).first!!
            val last = side(run.last(), sd).first!!
            val drop = (first - last).toDouble()
            if (hours < MIN_HOURS || drop < MIN_DROP) return@mapNotNull null
            val rate = slope(run.map { (it.time - t0) / 3_600_000.0 to side(it, sd).first!!.toDouble() })
            if (rate <= 0 || rate > MAX_RATE) return@mapNotNull null
            Stretch(t0, run.last().time, sd, run.first().mode ?: '?', hours, drop, rate)
        }
    }

    /** Time both buds were out and clean together, with each bud's drop over exactly that time. */
    fun paired(samples: List<BatterySample>): List<Paired> {
        val out = mutableListOf<Paired>()
        var start = -1L; var hours = 0.0; var dl = 0.0; var dr = 0.0
        fun close(end: Long) {
            if (start >= 0 && hours >= MIN_HOURS && dl >= 5 && dr >= 5) out.add(Paired(start, end, hours, dl, dr))
            start = -1L; hours = 0.0; dl = 0.0; dr = 0.0
        }
        for (i in 1 until samples.size) {
            val a = samples[i - 1]; val b = samples[i]
            if (clean(a, b, 0) && clean(a, b, 1)) {
                if (start < 0) start = a.time
                hours += (b.time - a.time) / 3_600_000.0
                dl += a.left!! - b.left!!
                dr += a.right!! - b.right!!
            } else close(a.time)
        }
        close(samples.lastOrNull()?.time ?: 0L)
        return out
    }

    // ------------------------------------------------------------------ storage

    private fun file(context: Context) = File(context.filesDir, FILE)

    /** Folds new finished stretches from the raw history into the long-term log. */
    @Synchronized
    fun update(context: Context) {
        val samples = BatteryHistory.load(context)
        if (samples.size < 3) return
        // A stretch still running at the newest reading isn't finished yet.
        val newest = samples.last().time
        val (oldS, oldP) = loadAll(context)
        val sKeys = oldS.map { it.side to it.start }.toHashSet()
        val pKeys = oldP.map { it.start }.toHashSet()
        val addS = stretches(samples).filter { it.end < newest && (it.side to it.start) !in sKeys }
        val addP = paired(samples).filter { it.end < newest && it.start !in pKeys }
        if (addS.isEmpty() && addP.isEmpty()) return
        val cutoff = System.currentTimeMillis() - KEEP_MS
        val lines = (oldS + addS).filter { it.end >= cutoff }.sortedBy { it.start }.map {
            "S,${it.start},${it.end},${it.side},${it.mode},${"%.4f".format(java.util.Locale.ROOT, it.hours)},${it.drop},${"%.4f".format(java.util.Locale.ROOT, it.rate)}"
        } + (oldP + addP).filter { it.end >= cutoff }.sortedBy { it.start }.map {
            "P,${it.start},${it.end},${"%.4f".format(java.util.Locale.ROOT, it.hours)},${it.dropL},${it.dropR}"
        }
        file(context).writeText(lines.joinToString("\n", postfix = "\n"))
    }

    private fun loadAll(context: Context): Pair<List<Stretch>, List<Paired>> {
        val f = file(context)
        if (!f.exists()) return emptyList<Stretch>() to emptyList()
        val s = mutableListOf<Stretch>(); val p = mutableListOf<Paired>()
        f.forEachLine { line ->
            val x = line.split(",")
            runCatching {
                when (x[0]) {
                    "S" -> s += Stretch(x[1].toLong(), x[2].toLong(), x[3].toInt(), x[4][0], x[5].toDouble(), x[6].toDouble(), x[7].toDouble())
                    "P" -> p += Paired(x[1].toLong(), x[2].toLong(), x[3].toDouble(), x[4].toDouble(), x[5].toDouble())
                }
            }
        }
        return s to p
    }

    // ------------------------------------------------------------------ estimate

    private fun weightedMedian(items: List<Pair<Double, Double>>): Double {
        val sorted = items.sortedBy { it.first }
        val half = sorted.sumOf { it.second } / 2
        var acc = 0.0
        for ((v, w) in sorted) { acc += w; if (acc >= half) return v }
        return sorted.last().first
    }

    /** Bootstrap 10th to 90th percentile of the weighted median. */
    private fun range(items: List<Pair<Double, Double>>, rnd: Random): Pair<Double, Double> {
        val meds = List(300) { weightedMedian(List(items.size) { items[rnd.nextInt(items.size)] }) }.sorted()
        return meds[30] to meds[269]
    }

    private fun bud(list: List<Stretch>, rnd: Random): Bud? {
        val hours = list.sumOf { it.hours }
        if (hours < ENOUGH_HOURS || list.size < 3) return null
        val items = list.map { it.rate to it.hours }
        val med = weightedMedian(items)
        val (lo, hi) = range(items, rnd)
        val v = 100 / med
        // The bootstrap only sees the spread in your data; real life (cold, volume) adds some.
        return Bud(v, minOf(100 / hi, v * 0.95), maxOf(100 / lo, v * 1.05), hours)
    }

    fun report(context: Context, now: Long = System.currentTimeMillis()): Report? {
        val (all, pairs) = loadAll(context)
        return estimate(all, pairs, now)
    }

    /** The estimate itself, kept free of storage so it can be tested. */
    fun estimate(all: List<Stretch>, pairs: List<Paired>, now: Long): Report? {
        if (all.isEmpty()) return null
        val rnd = Random(7)
        // Widen the window until each bud has enough clean listening.
        val windowDays = listOf(30, 60, 90, 180).firstOrNull { d ->
            val w = all.filter { it.end >= now - d * DAY }
            (0..1).all { sd -> w.filter { it.side == sd }.sumOf { it.hours } >= ENOUGH_HOURS }
        } ?: 180
        val recent = all.filter { it.end >= now - windowDays * DAY }
        // Report the mode you listen in most, compared only with itself.
        val mode = recent.groupBy { it.mode }.maxByOrNull { (_, v) -> v.sumOf { it.hours } }?.key ?: '?'
        val inMode = recent.filter { it.mode == mode }
        val left = bud(inMode.filter { it.side == 0 }, rnd)
        val right = bud(inMode.filter { it.side == 1 }, rnd)

        val recentPairs = pairs.filter { it.end >= now - windowDays * DAY }
        val imbalance = if (recentPairs.sumOf { it.hours } >= ENOUGH_HOURS && recentPairs.size >= 3) {
            fun ratio(l: List<Paired>) = l.sumOf { it.dropR } / max(1.0, l.sumOf { it.dropL })
            val boots = List(300) { ratio(List(recentPairs.size) { recentPairs[rnd.nextInt(recentPairs.size)] }) }.sorted()
            Triple(ratio(recentPairs), boots[30], boots[269])
        } else null

        // Your own first month in this mode, once there's a later month to compare with.
        val modeAll = all.filter { it.mode == mode }
        val firstStart = modeAll.minOfOrNull { it.start }
        var sinceFirst: Double? = null
        if (firstStart != null && now - firstStart > 60 * DAY) {
            val first = modeAll.filter { it.start < firstStart + 30 * DAY }
            val later = inMode
            if (first.sumOf { it.hours } >= 2 * ENOUGH_HOURS && later.sumOf { it.hours } >= 2 * ENOUGH_HOURS) {
                val r0 = weightedMedian(first.map { it.rate to it.hours })
                val r1 = weightedMedian(later.map { it.rate to it.hours })
                sinceFirst = r0 / r1 - 1
            }
        }
        return Report(mode, left, right, imbalance, sinceFirst, firstStart, all.minOfOrNull { it.start }, windowDays)
    }
}
