package com.peyi.sonypodsbattery

import android.content.Context
import java.io.File
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
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
 *    music playing, no call, the same noise mode, codec, DSEE and Speak-to-Chat throughout,
 *    the level only falling.
 *  - The misleading ends are dropped: above 95% (batteries sit at "100%" after a charge)
 *    and below 10% (the last few percent fall fast).
 *  - A stretch counts once it covers at least 30 min and an 8 point drop; its rate is a
 *    least squares fit over every reading, so 1% rounding barely matters.
 *  - Lopsided use: a bud working alone (the other charging, or out but not worn) drains faster
 *    because it carries the phone link by itself. A bud draining under 5%/h with music playing
 *    isn't in an ear (worn, the XM5 never drains that slowly), so those stretches are dropped.
 *
 * What you listen with changes drain too: noise mode, volume, codec, DSEE, Speak-to-Chat,
 * ambient level, solo use. A drain model learns what each costs *from your own listening*:
 *
 *     ln(rate) = bud + mode + volume + codec + DSEE + Speak-to-Chat + ambient level + solo + trend
 *
 * fitted by robust (Huber) weighted least squares, weighted by hours, with a light ridge so a
 * setting you never change simply costs nothing. Every stretch is then translated to your usual
 * setup (your most common mode, codec and volume) and health is read from those, so switching
 * to LDAC or turning the volume up reads as a setting, not as wear. The trend term soaks up slow
 * wear so a setting change made months later isn't blamed for it.
 *
 *  - The estimate is a time-weighted median of translated rates over the last 30 days (wider if
 *    there's too little), with a bootstrap range, so one odd day can't move it.
 *  - Left against right uses only time when both buds were out together, hour by hour, and only
 *    hours where both were clearly worn. Settings and temperature were identical, so a steady
 *    gap is wear, not usage.
 *
 * Stretches are kept for two years in battery_health.csv, far longer than the raw history,
 * so the trend against your own first month builds up over time.
 */
internal object BatteryHealth {
    private const val FILE = "battery_health.csv"
    private const val MAX_GAP_MS = 20L * 60 * 1000
    private const val KEEP_MS = 730L * 86_400_000
    private const val DAY = 86_400_000L
    private const val YEAR = 365.0 * DAY
    private const val TOP = 95
    private const val BOTTOM = 10
    private const val MIN_HOURS = 0.5
    private const val MIN_DROP = 8.0
    private const val MAX_RATE = 60.0
    /** Hours of clean listening per bud before a number is shown. */
    private const val ENOUGH_HOURS = 2.5
    /** Below this with music playing, a bud is out of the case but not being worn. */
    private const val MIN_WORN_RATE = 5.0
    /** Shared time is compared in chunks of up to an hour. */
    private const val CHUNK_HOURS = 1.0

    /** Sony's ratings for these earbuds; ambient isn't rated separately. */
    fun ratedHours(mode: Char): Double? = when (mode) { 'N' -> EarbudModel.active.ratedHoursNc; 'O' -> EarbudModel.active.ratedHoursOff; else -> null }

    data class Stretch(
        val start: Long, val end: Long, val side: Int, val mode: Char, val hours: Double, val drop: Double, val rate: Double,
        /** The other bud was charging, missing or not worn for most of it. */
        val solo: Boolean = false,
        /** Mean phone media volume 0..100. */
        val volume: Double? = null,
        val codec: Char? = null,
        val dsee: Boolean? = null,
        val speakToChat: Boolean? = null,
        /** Mean ambient level 1..20 (ambient mode only). */
        val ambient: Double? = null
    )
    data class Paired(val start: Long, val end: Long, val hours: Double, val dropL: Double, val dropR: Double)

    data class Bud(val hoursPerCharge: Double, val low: Double, val high: Double, val listened: Double, val solo: Boolean = false)

    /** Your usual setup, which every stretch is translated to. */
    data class Setup(val mode: Char, val volume: Double, val codec: Char, val dsee: Boolean, val speakToChat: Boolean, val ambient: Double)

    /** What a setting costs in battery, learned from your listening (+0.14 = 14% faster drain). */
    data class Cost(val label: String, val change: Double)

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
        val windowDays: Int,
        val setup: Setup? = null,
        val costs: List<Cost> = emptyList()
    )

    // ------------------------------------------------------------------ extraction

    private fun side(s: BatterySample, side: Int) = if (side == 0) s.left to s.leftCharging else s.right to s.rightCharging

    private fun <T> differ(a: T?, b: T?) = a != null && b != null && a != b

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
        if (differ(a.mode, b.mode) || differ(a.codec, b.codec) || differ(a.dsee, b.dsee) || differ(a.speakToChat, b.speakToChat)) return false
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

    private fun <T> modal(values: List<T?>): T? = values.filterNotNull().groupingBy { it }.eachCount().maxByOrNull { it.value }?.key

    fun stretches(samples: List<BatterySample>): List<Stretch> = (0..1).flatMap { sd ->
        runs(samples, sd).mapNotNull { run ->
            val t0 = run.first().time
            val hours = (run.last().time - t0) / 3_600_000.0
            val first = side(run.first(), sd).first!!
            val last = side(run.last(), sd).first!!
            val drop = (first - last).toDouble()
            if (hours < MIN_HOURS || drop < MIN_DROP) return@mapNotNull null
            val rate = slope(run.map { (it.time - t0) / 3_600_000.0 to side(it, sd).first!!.toDouble() })
            if (rate < MIN_WORN_RATE || rate > MAX_RATE) return@mapNotNull null
            Stretch(
                t0, run.last().time, sd, run.first().mode ?: '?', hours, drop, rate,
                solo = otherAbsent(run, 1 - sd),
                volume = run.mapNotNull { it.volume }.takeIf { it.isNotEmpty() }?.average(),
                codec = modal(run.map { it.codec }),
                dsee = modal(run.map { it.dsee }),
                speakToChat = modal(run.map { it.speakToChat }),
                ambient = run.mapNotNull { it.ambient }.takeIf { it.isNotEmpty() }?.average()
            )
        }
    }

    /**
     * Whether the other bud was absent for most of [run]: charging in the case, not reported, or
     * out of the case but draining so slowly it can't have been in an ear.
     */
    private fun otherAbsent(run: List<BatterySample>, other: Int): Boolean {
        val present = run.filter { s -> side(s, other).let { (lvl, ch) -> lvl != null && !ch } }
        if (present.size * 2 < run.size) return true
        val hours = (present.last().time - present.first().time) / 3_600_000.0
        if (hours < 0.25) return false
        val rate = slope(present.map { (it.time - present.first().time) / 3_600_000.0 to side(it, other).first!!.toDouble() })
        return rate < MIN_WORN_RATE
    }

    /**
     * Shared time when both buds were out, clean and clearly worn, in chunks of up to an hour,
     * with each bud's drop over exactly that time.
     */
    fun paired(samples: List<BatterySample>): List<Paired> {
        val out = mutableListOf<Paired>()
        var start = -1L; var hours = 0.0; var dl = 0.0; var dr = 0.0
        fun close(end: Long) {
            val worn = hours > 0 && dl / hours >= MIN_WORN_RATE && dr / hours >= MIN_WORN_RATE
            // A 2x gap within one hour isn't wear; it's one bud not being worn for part of it.
            val plausible = dl > 0 && dr / dl in 0.5..2.0
            if (start >= 0 && hours >= 0.4 && dl >= 4 && dr >= 4 && worn && plausible) out.add(Paired(start, end, hours, dl, dr))
            start = -1L; hours = 0.0; dl = 0.0; dr = 0.0
        }
        for (i in 1 until samples.size) {
            val a = samples[i - 1]; val b = samples[i]
            if (clean(a, b, 0) && clean(a, b, 1)) {
                if (start < 0) start = a.time
                hours += (b.time - a.time) / 3_600_000.0
                dl += a.left!! - b.left!!
                dr += a.right!! - b.right!!
                if (hours >= CHUNK_HOURS) close(b.time)
            } else close(a.time)
        }
        close(samples.lastOrNull()?.time ?: 0L)
        return out
    }

    // ------------------------------------------------------------------ storage

    private fun file(context: Context) = File(context.filesDir, FILE)

    private fun fmt(d: Double) = "%.4f".format(java.util.Locale.ROOT, d)
    private fun flag(b: Boolean?) = when (b) { true -> "1"; false -> "0"; null -> "" }
    private fun flag(s: String?) = when (s) { "1" -> true; "0" -> false; else -> null }

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
            listOf(
                "S", it.start, it.end, it.side, it.mode, fmt(it.hours), it.drop, fmt(it.rate), flag(it.solo),
                it.volume?.let(::fmt) ?: "", it.codec ?: "", flag(it.dsee), flag(it.speakToChat), it.ambient?.let(::fmt) ?: ""
            ).joinToString(",")
        } + (oldP + addP).filter { it.end >= cutoff }.sortedBy { it.start }.map {
            "P,${it.start},${it.end},${fmt(it.hours)},${it.dropL},${it.dropR}"
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
                    "S" -> s += Stretch(
                        x[1].toLong(), x[2].toLong(), x[3].toInt(), x[4][0], x[5].toDouble(), x[6].toDouble(), x[7].toDouble(),
                        solo = x.getOrNull(8) == "1",
                        volume = x.getOrNull(9)?.toDoubleOrNull(),
                        codec = x.getOrNull(10)?.firstOrNull(),
                        dsee = flag(x.getOrNull(11)),
                        speakToChat = flag(x.getOrNull(12)),
                        ambient = x.getOrNull(13)?.toDoubleOrNull()
                    )
                    "P" -> p += Paired(x[1].toLong(), x[2].toLong(), x[3].toDouble(), x[4].toDouble(), x[5].toDouble())
                }
            }
        }
        return s to p
    }

    // ------------------------------------------------------------------ drain model

    /**
     * Learned drain model. Features (all 0 at the reference: noise cancelling, AAC, half volume,
     * no DSEE, no Speak-to-Chat, paired): ambient, off, unknown mode, volume, LDAC, SBC, LC3,
     * other codec, DSEE, Speak-to-Chat, ambient level, solo. Plus one intercept per bud and a
     * time trend in years.
     */
    private class Model(val beta: DoubleArray, val names: List<String>) {
        fun effect(x: DoubleArray) = x.indices.sumOf { beta[it] * x[it] }
    }

    private const val NF = 12

    /** Fills gaps in older stretches with your usual values so they still count. */
    private class Fill(val volume: Double, val codec: Char, val dsee: Boolean, val speakToChat: Boolean, val ambient: Double)

    private fun features(s: Stretch, f: Fill): DoubleArray {
        val mode = s.mode
        // Scaled to -1..1 so the ridge treats it like the on/off settings.
        val vol = ((s.volume ?: f.volume) / 100.0 - 0.5) * 2
        val codec = s.codec ?: f.codec
        val amb = if (mode == 'A') (s.ambient ?: f.ambient) / 20.0 - 0.5 else 0.0
        return doubleArrayOf(
            if (mode == 'A') 1.0 else 0.0,
            if (mode == 'O') 1.0 else 0.0,
            if (mode != 'N' && mode != 'A' && mode != 'O') 1.0 else 0.0,
            vol,
            if (codec == 'L') 1.0 else 0.0,
            if (codec == 'S') 1.0 else 0.0,
            if (codec == 'C') 1.0 else 0.0,
            if (codec == 'X') 1.0 else 0.0,
            if (s.dsee ?: f.dsee) 1.0 else 0.0,
            if (s.speakToChat ?: f.speakToChat) 1.0 else 0.0,
            amb,
            if (s.solo) 1.0 else 0.0
        )
    }

    private fun featuresOf(setup: Setup): DoubleArray = features(
        Stretch(0, 0, 0, setup.mode, 1.0, 10.0, 10.0, false, setup.volume, setup.codec, setup.dsee, setup.speakToChat, setup.ambient),
        Fill(setup.volume, setup.codec, setup.dsee, setup.speakToChat, setup.ambient)
    )

    /** Solves A x = b by Gaussian elimination with partial pivoting (A is small and ridge-regularised). */
    private fun solve(a: Array<DoubleArray>, b: DoubleArray): DoubleArray {
        val n = b.size
        val m = Array(n) { i -> DoubleArray(n + 1) { j -> if (j < n) a[i][j] else b[i] } }
        for (c in 0 until n) {
            val p = (c until n).maxByOrNull { abs(m[it][c]) }!!
            val tmp = m[c]; m[c] = m[p]; m[p] = tmp
            val d = m[c][c].takeIf { abs(it) > 1e-12 } ?: continue
            for (r in 0 until n) if (r != c) {
                val k = m[r][c] / d
                if (k != 0.0) for (j in c..n) m[r][j] -= k * m[c][j]
            }
        }
        return DoubleArray(n) { i -> if (abs(m[i][i]) > 1e-12) m[i][n] / m[i][i] else 0.0 }
    }

    /**
     * Robust ridge regression of ln(rate) on the features, weighted by hours, with Huber
     * re-weighting so a few odd stretches can't pull it. Settings that never change, or barely
     * appear, are held near zero cost by the ridge.
     */
    private fun fit(list: List<Stretch>, fill: Fill, t0: Long): Model {
        val p = NF + 3 // features, two bud intercepts, trend
        val xs = list.map { s ->
            val f = features(s, fill)
            DoubleArray(p) { j ->
                when {
                    j < NF -> f[j]
                    j == NF -> if (s.side == 0) 1.0 else 0.0
                    j == NF + 1 -> if (s.side == 1) 1.0 else 0.0
                    else -> (s.start - t0) / YEAR
                }
            }
        }
        val y = list.map { ln(it.rate) }
        val base = list.map { it.hours }
        // Ridge: settings need a few hours of evidence to earn a cost; intercepts are free.
        val ridge = DoubleArray(p) { j -> when { j < NF -> 1.5; j < NF + 2 -> 1e-6; else -> 0.5 } }
        var w = base.toDoubleArray()
        var theta = DoubleArray(p)
        repeat(8) {
            val a = Array(p) { DoubleArray(p) }
            val b = DoubleArray(p)
            for (i in xs.indices) {
                val x = xs[i]; val wi = w[i]
                for (r in 0 until p) {
                    if (x[r] == 0.0) continue
                    b[r] += wi * x[r] * y[i]
                    for (c in 0 until p) a[r][c] += wi * x[r] * x[c]
                }
            }
            for (j in 0 until p) a[j][j] += ridge[j]
            theta = solve(a, b)
            // Huber weights from the residuals.
            val res = xs.indices.map { i -> y[i] - xs[i].indices.sumOf { j -> xs[i][j] * theta[j] } }
            val mad = res.map { abs(it) }.sorted().let { it[it.size / 2] }.coerceAtLeast(0.02)
            val k = 1.345 * mad / 0.6745
            w = DoubleArray(xs.size) { i -> base[i] * (if (abs(res[i]) <= k) 1.0 else k / abs(res[i])) }
        }
        return Model(theta.copyOf(NF), listOf("ambient", "off", "unknown", "volume", "LDAC", "SBC", "LC3", "codec", "DSEE", "Speak-to-Chat", "ambient level", "solo"))
    }

    /** What the settings you actually changed cost, when there's enough of each to tell. */
    private fun costs(model: Model, list: List<Stretch>): List<Cost> {
        fun hoursWhere(p: (Stretch) -> Boolean) = list.filter(p).sumOf { it.hours }
        val out = mutableListOf<Cost>()
        fun binary(label: String, idx: Int, on: (Stretch) -> Boolean) {
            if (hoursWhere(on) >= 2 && hoursWhere { !on(it) } >= 2) out += Cost(label, exp(model.beta[idx]) - 1)
        }
        binary("LDAC instead of AAC", 4) { it.codec == 'L' }
        binary("DSEE Extreme", 8) { it.dsee == true }
        binary("Speak-to-Chat", 9) { it.speakToChat == true }
        binary("Ambient Sound instead of Noise Cancelling", 0) { it.mode == 'A' }
        binary("Noise Control off instead of Noise Cancelling", 1) { it.mode == 'O' }
        val vols = list.mapNotNull { it.volume }
        if (vols.isNotEmpty() && vols.max() - vols.min() >= 20) out += Cost("Every 10% more volume", exp(model.beta[3] * 0.2) - 1)
        return out.filter { abs(it.change) >= 0.03 }.sortedByDescending { abs(it.change) }
    }

    // ------------------------------------------------------------------ estimate

    private fun weightedMedian(items: List<Pair<Double, Double>>): Double {
        val sorted = items.sortedBy { it.first }
        val half = sorted.sumOf { it.second } / 2
        var acc = 0.0
        for ((v, w) in sorted) { acc += w; if (acc >= half) return v }
        return sorted.last().first
    }

    private fun weightedMedianOf(values: List<Pair<Double, Double>>, default: Double) =
        if (values.isEmpty()) default else weightedMedian(values)

    /** Bootstrap 10th to 90th percentile of the weighted median. */
    private fun range(items: List<Pair<Double, Double>>, rnd: Random): Pair<Double, Double> {
        val meds = List(300) { weightedMedian(List(items.size) { items[rnd.nextInt(items.size)] }) }.sorted()
        return meds[30] to meds[269]
    }

    private fun enough(list: List<Stretch>) = list.size >= 3 && list.sumOf { it.hours } >= ENOUGH_HOURS

    /** Paired listening when there's enough of it, else solo listening (flagged as such). */
    private fun bud(all: List<Stretch>, rate: (Stretch) -> Double, rnd: Random): Bud? {
        val paired = all.filter { !it.solo }
        val solo = paired.isEmpty() || !enough(paired)
        val list = if (solo) all.filter { it.solo } else paired
        if (!enough(list)) return null
        val items = list.map { rate(it) to it.hours }
        val med = weightedMedian(items)
        val (lo, hi) = range(items, rnd)
        val v = 100 / med
        // The bootstrap only sees the spread in your data; real life (cold weather) adds some.
        return Bud(v, minOf(100 / hi, v * 0.95), maxOf(100 / lo, v * 1.05), list.sumOf { it.hours }, solo)
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
        } ?: listOf(30, 60, 90, 180).firstOrNull { d ->
            // Only one bud gets used: size the window for that one.
            all.filter { it.end >= now - d * DAY }.sumOf { it.hours } >= ENOUGH_HOURS
        } ?: 180
        val recent = all.filter { it.end >= now - windowDays * DAY }.ifEmpty { all }

        // Your usual setup, from recent listening.
        fun hoursBy(key: (Stretch) -> Any?) = recent.groupBy(key).mapValues { (_, v) -> v.sumOf { it.hours } }
        val setup = Setup(
            mode = hoursBy { it.mode }.maxByOrNull { it.value }!!.key as Char,
            volume = weightedMedianOf(recent.mapNotNull { s -> s.volume?.let { it to s.hours } }, 50.0),
            codec = (hoursBy { it.codec }.filterKeys { it != null }.maxByOrNull { it.value }?.key as Char?) ?: 'A',
            dsee = (hoursBy { it.dsee }.filterKeys { it != null }.maxByOrNull { it.value }?.key as Boolean?) ?: false,
            speakToChat = (hoursBy { it.speakToChat }.filterKeys { it != null }.maxByOrNull { it.value }?.key as Boolean?) ?: false,
            ambient = weightedMedianOf(recent.mapNotNull { s -> s.ambient?.let { it to s.hours } }, 10.0)
        )
        val fill = Fill(setup.volume, setup.codec, setup.dsee, setup.speakToChat, setup.ambient)

        // Learn what each setting costs from everything, then translate each stretch to your setup.
        val t0 = all.minOf { it.start }
        val model = fit(all, fill, t0)
        val ref = featuresOf(setup)
        val translated = HashMap<Stretch, Double>()
        fun std(s: Stretch) = translated.getOrPut(s) { s.rate * exp(model.effect(ref) - model.effect(features(s, fill))) }

        val left = bud(recent.filter { it.side == 0 }, ::std, rnd)
        val right = bud(recent.filter { it.side == 1 }, ::std, rnd)

        val recentPairs = pairs.filter { it.end >= now - windowDays * DAY }
        val imbalance = if (recentPairs.sumOf { it.hours } >= ENOUGH_HOURS && recentPairs.size >= 3) {
            fun ratio(l: List<Paired>) = l.sumOf { it.dropR } / max(1.0, l.sumOf { it.dropL })
            val boots = List(300) { ratio(List(recentPairs.size) { recentPairs[rnd.nextInt(recentPairs.size)] }) }.sorted()
            Triple(ratio(recentPairs), boots[30], boots[269])
        } else null

        // Your own first month against now, both translated to your current setup.
        var sinceFirst: Double? = null
        if (now - t0 > 60 * DAY) {
            var first = all.filter { it.start < t0 + 30 * DAY }
            var later = recent.filter { it.start >= t0 + 30 * DAY }
            if (first.filter { !it.solo }.sumOf { it.hours } >= 2 * ENOUGH_HOURS && later.filter { !it.solo }.sumOf { it.hours } >= 2 * ENOUGH_HOURS) {
                first = first.filter { !it.solo }; later = later.filter { !it.solo }
            }
            if (first.sumOf { it.hours } >= 2 * ENOUGH_HOURS && later.sumOf { it.hours } >= 2 * ENOUGH_HOURS) {
                val r0 = weightedMedian(first.map { std(it) to it.hours })
                val r1 = weightedMedian(later.map { std(it) to it.hours })
                sinceFirst = r0 / r1 - 1
            }
        }
        return Report(setup.mode, left, right, imbalance, sinceFirst, t0, t0, windowDays, setup, costs(model, all))
    }
}
