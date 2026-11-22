package com.peyi.sonypodsbattery

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.util.Calendar
import kotlin.math.roundToInt

data class BatterySample(
    val time: Long,
    val left: Int?,
    val right: Int?,
    val case: Int?,
    val leftCharging: Boolean,
    val rightCharging: Boolean,
    val caseCharging: Boolean,
    /** Noise mode when read: N cancelling, A ambient, O off; null in older history. */
    val mode: Char? = null,
    /** Whether the phone was playing audio (and not in a call) when read; null in older history. */
    val playing: Boolean? = null,
    // What else costs battery, as it was at the time; null in older history.
    /** Phone media volume, 0..100. */
    val volume: Int? = null,
    /** L LDAC, A AAC, S SBC, C LC3, X other. */
    val codec: Char? = null,
    val dsee: Boolean? = null,
    val speakToChat: Boolean? = null,
    /** Ambient level 1..20 (only meaningful in ambient mode). */
    val ambient: Int? = null
)

/** Append-only log of confirmed battery reads, kept on the phone only (14 days). */
object BatteryHistory {
    private const val FILE = "battery_history.csv"
    private const val KEEP_MS = 14L * 24 * 60 * 60 * 1000
    private const val SAME_STATE_GAP_MS = 10L * 60 * 1000

    private fun file(context: Context) = File(context.filesDir, FILE)

    @Synchronized
    fun record(context: Context, state: BatteryState) {
        if (state.updatedAt == 0L || (state.left == null && state.right == null)) return
        val f = file(context)
        val last = if (f.exists()) f.useLines { lines -> lines.lastOrNull() }?.let(::parse) else null
        val settings = SettingsStore.load(context)
        val sample = BatterySample(
            state.updatedAt, state.left, state.right, state.case,
            state.leftCharging, state.rightCharging, state.caseCharging,
            mode = when (state.listeningMode) {
                ListeningMode.NOISE_CANCELLING -> 'N'; ListeningMode.AMBIENT -> 'A'; ListeningMode.OFF -> 'O'; null -> null
            },
            playing = playingNow(context),
            volume = volumeNow(context),
            codec = settings.codec?.let { c ->
                when { c.startsWith("LDAC") -> 'L'; c.startsWith("AAC") -> 'A'; c.startsWith("SBC") -> 'S'; c.startsWith("LC3") -> 'C'; else -> 'X' }
            },
            dsee = settings.dseeOn,
            speakToChat = settings.speakToChat,
            ambient = state.ambientLevel.takeIf { state.listeningMode == ListeningMode.AMBIENT }
        )
        if (last != null) {
            if (sample.time <= last.time) return
            val unchanged = last.copy(time = sample.time) == sample
            if (unchanged && sample.time - last.time < SAME_STATE_GAP_MS) return
        }
        f.appendText(format(sample) + "\n")
        if (f.length() > 300_000) prune(f)
        // Fold finished clean stretches into the long-term health log now and then.
        val p = context.getSharedPreferences("battery_health", Context.MODE_PRIVATE)
        if (sample.time - p.getLong("updated", 0L) > 3 * 3_600_000L) {
            runCatching { BatteryHealth.update(context) }
            p.edit().putLong("updated", sample.time).apply()
        }
    }

    private fun volumeNow(context: Context): Int? = runCatching {
        val audio = context.getSystemService(android.media.AudioManager::class.java) ?: return null
        val max = audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).takeIf { it > 0 } ?: return null
        audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) * 100 / max
    }.getOrNull()

    private fun playingNow(context: Context): Boolean? = runCatching {
        val audio = context.getSystemService(android.media.AudioManager::class.java) ?: return null
        val call = audio.mode == android.media.AudioManager.MODE_IN_CALL || audio.mode == android.media.AudioManager.MODE_IN_COMMUNICATION
        audio.isMusicActive && !call
    }.getOrNull()

    fun load(context: Context, sinceMillis: Long = 0L): List<BatterySample> {
        val f = file(context)
        if (!f.exists()) return emptyList()
        return f.useLines { lines -> lines.mapNotNull(::parse).filter { it.time >= sinceMillis }.toList() }
    }

    private fun prune(f: File) {
        val cutoff = System.currentTimeMillis() - KEEP_MS
        val kept = f.readLines().filter { line -> parse(line)?.let { it.time >= cutoff } == true }
        f.writeText(kept.joinToString("\n", postfix = "\n"))
    }

    private fun format(s: BatterySample) = listOf(
        s.time, s.left ?: "", s.right ?: "", s.case ?: "",
        if (s.leftCharging) 1 else 0, if (s.rightCharging) 1 else 0, if (s.caseCharging) 1 else 0,
        s.mode ?: "", flag(s.playing),
        s.volume ?: "", s.codec ?: "", flag(s.dsee), flag(s.speakToChat), s.ambient ?: ""
    ).joinToString(",")

    private fun flag(b: Boolean?) = when (b) { true -> 1; false -> 0; null -> "" }
    private fun flag(s: String?) = when (s) { "1" -> true; "0" -> false; else -> null }

    private fun parse(line: String): BatterySample? {
        val p = line.split(",")
        if (p.size != 7 && p.size != 9 && p.size != 14) return null
        return BatterySample(
            p[0].toLongOrNull() ?: return null,
            p[1].toIntOrNull(), p[2].toIntOrNull(), p[3].toIntOrNull(),
            p[4] == "1", p[5] == "1", p[6] == "1",
            mode = p.getOrNull(7)?.firstOrNull(),
            playing = flag(p.getOrNull(8)),
            volume = p.getOrNull(9)?.toIntOrNull(),
            codec = p.getOrNull(10)?.firstOrNull(),
            dsee = flag(p.getOrNull(11)),
            speakToChat = flag(p.getOrNull(12)),
            ambient = p.getOrNull(13)?.toIntOrNull()
        )
    }
}

data class BatteryOutlook(
    /** Percent per hour while listening, from your own history when there is enough of it. */
    val drainPerHour: Double,
    val measured: Boolean,
    val minutesLeft: Int?,
    val listeningMinutesToday: Int,
    val caseNeedsCharge: Boolean,
    val lowestBud: String?
)

object BatteryInsights {
    /** WF-1000XM5 rating: about 8 h with noise cancelling, so ~12.5 %/h before we have data. */
    private const val RATED_DRAIN_PER_HOUR = 12.5
    private const val MAX_GAP_MS = 15L * 60 * 1000

    fun outlook(samples: List<BatterySample>, now: BatteryState): BatteryOutlook {
        val measured = measuredDrain(samples)
        val rate = measured ?: RATED_DRAIN_PER_HOUR
        val buds = listOfNotNull(
            now.left?.takeIf { it in 1..100 && !now.leftCharging }?.let { "Left" to it },
            now.right?.takeIf { it in 1..100 && !now.rightCharging }?.let { "Right" to it }
        )
        val lowest = buds.minByOrNull { it.second }
        val minutes = lowest?.let { (it.second / rate * 60).roundToInt() }
        val case = now.case
        return BatteryOutlook(
            drainPerHour = rate,
            measured = measured != null,
            minutesLeft = minutes,
            listeningMinutesToday = listeningToday(samples),
            caseNeedsCharge = case != null && case in 1..25 && !now.caseCharging,
            lowestBud = lowest?.first
        )
    }

    /** Average discharge rate across stretches where a bud was out of the case and falling. */
    fun measuredDrain(samples: List<BatterySample>): Double? = drain(samples, listOf(0, 1))

    /** Discharge rate for one bud: 0 = left, 1 = right. */
    fun measuredDrain(samples: List<BatterySample>, side: Int): Double? = drain(samples, listOf(side))

    private fun drain(samples: List<BatterySample>, sides: List<Int>): Double? {
        var drop = 0.0
        var hours = 0.0
        val pick = listOf<(BatterySample) -> Pair<Int?, Boolean>>(
            { it.left to it.leftCharging }, { it.right to it.rightCharging }
        )
        for (side in sides.map { pick[it] }) {
            samples.zipWithNext().forEach { (a, b) ->
                val (la, ca) = side(a)
                val (lb, cb) = side(b)
                if (la == null || lb == null || ca || cb) return@forEach
                if (la !in 1..100 || lb !in 1..100) return@forEach
                val gap = b.time - a.time
                if (gap <= 0 || gap > MAX_GAP_MS || lb > la) return@forEach
                drop += (la - lb)
                hours += gap / 3_600_000.0
            }
        }
        if (hours < 0.75 * sides.size || drop < 4) return null
        return (drop / hours).coerceIn(3.0, 40.0)
    }

    /** Minutes of listening on each of the last [days] days, oldest first, paired with each day's start. */
    fun dailyListening(samples: List<BatterySample>, now: Long, days: Int): List<Pair<Long, Int>> {
        val today = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        return (days - 1 downTo 0).map { back ->
            val start = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -back) }.timeInMillis
            val end = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -back + 1) }.timeInMillis
            start to listeningBetween(samples, start, end)
        }
    }

    private fun listeningBetween(samples: List<BatterySample>, start: Long, end: Long): Int {
        var ms = 0L
        samples.filter { it.time in start until end }.zipWithNext().forEach { (a, b) ->
            val out = (a.left != null && !a.leftCharging) || (a.right != null && !a.rightCharging)
            val gap = b.time - a.time
            if (out && gap in 1..MAX_GAP_MS) ms += gap
        }
        return (ms / 60_000).toInt()
    }

    /** When the buds last came out of the case after charging, if the history saw it. */
    fun lastTakenOut(samples: List<BatterySample>): Long? =
        samples.zipWithNext().lastOrNull { (a, b) ->
            (a.leftCharging || a.rightCharging) && !b.leftCharging && !b.rightCharging && (b.left != null || b.right != null)
        }?.second?.time

    /** Time the earbuds were out and connected today, from the 5 minute refresh cadence. */
    fun listeningToday(samples: List<BatterySample>): Int {
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        var ms = 0L
        samples.filter { it.time >= start }.zipWithNext().forEach { (a, b) ->
            val out = (a.left != null && !a.leftCharging) || (a.right != null && !a.rightCharging)
            val gap = b.time - a.time
            if (out && gap in 1..MAX_GAP_MS) ms += gap
        }
        return (ms / 60_000).toInt()
    }

    fun formatMinutes(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0 -> "$m min"
            m == 0 -> "$h hr"
            else -> "$h hr $m min"
        }
    }
}

/** Gentle, once-per-cycle alerts for low buds and a low case. */
object BatteryNudges {
    private const val CHANNEL = "battery_alerts"
    private const val PREFS = "battery_nudges"

    fun check(context: Context, state: BatteryState) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val outlook = BatteryInsights.outlook(BatteryHistory.load(context, System.currentTimeMillis() - 7L * 86_400_000), state)

        val threshold = budThreshold(context)
        for ((side, level, charging) in listOf(
            Triple("Left", state.left, state.leftCharging),
            Triple("Right", state.right, state.rightCharging)
        )) {
            val key = "low_${side.lowercase()}"
            if (level == null || charging || level > threshold + 15) {
                prefs.edit().remove(key).apply()
                continue
            }
            if (budAlerts(context) && level in 1..threshold && !prefs.getBoolean(key, false)) {
                val left = (level / outlook.drainPerHour * 60).roundToInt()
                notify(
                    context, 3001 + side.length,
                    "$side Earbud at $level%",
                    "About ${BatteryInsights.formatMinutes(left)} of listening left. Put it in the case to charge."
                )
                prefs.edit().putBoolean(key, true).apply()
            }
        }

        val case = state.case
        val lastCaseNudge = prefs.getLong("case_nudge", 0L)
        if (caseAlerts(context) && case != null && case in 1..20 && !state.caseCharging &&
            System.currentTimeMillis() - lastCaseNudge > 20L * 3_600_000
        ) {
            notify(context, 3010, "Case at $case%", "Charge it tonight so your earbuds are full tomorrow.")
            prefs.edit().putLong("case_nudge", System.currentTimeMillis()).apply()
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun budAlerts(context: Context) = prefs(context).getBoolean("alert_buds", true)
    fun setBudAlerts(context: Context, on: Boolean) = prefs(context).edit().putBoolean("alert_buds", on).apply()
    fun caseAlerts(context: Context) = prefs(context).getBoolean("alert_case", true)
    fun setCaseAlerts(context: Context, on: Boolean) = prefs(context).edit().putBoolean("alert_case", on).apply()
    fun budThreshold(context: Context) = prefs(context).getInt("bud_threshold", 15).coerceIn(10, 30)
    fun setBudThreshold(context: Context, percent: Int) = prefs(context).edit().putInt("bud_threshold", percent).apply()

    private fun notify(context: Context, id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Battery Alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Alerts when your earbuds or case are running low."
            }
        )
        val open = PendingIntent.getActivity(
            context, 21, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        manager.notify(
            id,
            android.app.Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_sunpods_mark)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
        )
    }
}
