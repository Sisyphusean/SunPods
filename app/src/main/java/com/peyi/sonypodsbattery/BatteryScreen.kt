@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.peyi.sonypodsbattery

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.drawText
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.roundToInt

private const val DAY = 86_400_000L

// ======================================================================
// Battery tab: estimate, scrubbable history, week of listening, stats, alerts
// ======================================================================

@Composable
internal fun BatteryInsightsScreen(battery: BatteryState) {
    val context = LocalContext.current
    val now = remember(battery.updatedAt) { System.currentTimeMillis() }
    val samples = remember(battery.updatedAt) { BatteryHistory.load(context, now - 7 * DAY) }
    val outlook = remember(samples, battery) { BatteryInsights.outlook(samples, battery) }

    ListeningLeftCard(battery, outlook)
    HistoryCard(samples, now)
    StatsGrid(battery, samples, outlook, now)
    BatteryMoreGroup()
}

/** The deeper battery views, each a tap away so the tab stays short. */
@Composable
private fun BatteryMoreGroup() {
    val context = LocalContext.current
    val open = LocalOpenPage.current
    val alerts = remember { BatteryNudges.budAlerts(context) || BatteryNudges.caseAlerts(context) }
    SunCard {
        BatteryPageRow(Icons.Rounded.HealthAndSafety, Page.BATTERY_HEALTH, null) { open(Page.BATTERY_HEALTH) }
        RowDivider()
        BatteryPageRow(Icons.Rounded.CalendarMonth, Page.BATTERY_WEEK, null) { open(Page.BATTERY_WEEK) }
        RowDivider()
        BatteryPageRow(Icons.Rounded.BatteryAlert, Page.BATTERY_ALERTS, if (alerts) "On" else "Off") { open(Page.BATTERY_ALERTS) }
    }
}

@Composable
private fun BatteryPageRow(icon: androidx.compose.ui.graphics.vector.ImageVector, page: Page, value: String?, onClick: () -> Unit) {
    SettingRow(icon, page.title, modifier = Modifier.clickable(onClick = onClick)) {
        if (value != null) {
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
    }
}

/** The week page: listening per day. */
@Composable
internal fun BatteryWeekPage(battery: BatteryState) {
    val context = LocalContext.current
    val now = remember(battery.updatedAt) { System.currentTimeMillis() }
    val samples = remember(battery.updatedAt) { BatteryHistory.load(context, now - 7 * DAY) }
    WeekCard(samples, now)
}

@Composable
internal fun BatteryAlertsPage() = AlertsCard()

// ---------------------------------------------------------------------- estimate

private enum class EstimateMode(val label: String) {
    NC("Cancelling"), AMBIENT("Ambient"), OFF("Off");

    /** Sony rates ambient like noise cancelling. */
    val ratedHours: Double get() = if (this == OFF) EarbudModel.active.ratedHoursOff else EarbudModel.active.ratedHoursNc
}

@Composable
private fun ListeningLeftCard(battery: BatteryState, outlook: BatteryOutlook) {
    val colors = MaterialTheme.colorScheme
    var mode by rememberSaveable {
        mutableStateOf(
            when (battery.listeningMode) {
                ListeningMode.AMBIENT -> EstimateMode.AMBIENT
                ListeningMode.OFF -> EstimateMode.OFF
                else -> EstimateMode.NC
            }
        )
    }
    // The measured rate reflects how you really listen; scale it by Sony's rating for the chosen mode.
    val rate = outlook.drainPerHour * (EarbudModel.active.ratedHoursNc / mode.ratedHours)
    val buds = listOfNotNull(
        battery.left?.takeIf { it in 1..100 && !battery.leftCharging },
        battery.right?.takeIf { it in 1..100 && !battery.rightCharging }
    )
    val minutes = buds.minOrNull()?.let { (it / rate * 60).roundToInt() }
    // Shown straight away; it only rolls when the estimate changes (a new reading, another mode).
    val count = remember { androidx.compose.animation.core.Animatable((minutes ?: 0).toFloat()) }
    androidx.compose.runtime.LaunchedEffect(minutes) { count.animateTo((minutes ?: 0).toFloat(), tween(420, easing = Motion.Arrive)) }
    val shown = count.value

    SunCard {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text("LISTENING TIME LEFT", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(
                if (minutes == null) { if (battery.leftCharging || battery.rightCharging) "Charging" else "—" } else BatteryInsights.formatMinutes(shown.roundToInt()),
                style = MaterialTheme.typography.displaySmall,
                color = colors.primary
            )
            Text(
                when {
                    EarbudModel.active.headphones -> when {
                        minutes == null -> "Turn your headphones on to see"
                        outlook.measured -> "Measured from how you listen"
                        else -> "Estimated from Sony's rating for now"
                    }
                    minutes == null && buds.isEmpty() && (battery.leftCharging || battery.rightCharging) -> "Both earbuds are in the case"
                    minutes == null -> "Take your earbuds out to see"
                    outlook.measured -> "${outlook.lowestBud ?: "One"} earbud runs out first"
                    else -> "Estimated from Sony's rating for now"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EstimateMode.entries.forEach { m ->
                    FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(m.label) }, shape = CircleShape)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------- history chart

/*
 * History as bars, like a phone's battery screen. Readings arrive in bursts while the buds are
 * out, so a line either breaks into dashes or invents slopes across hours nobody measured. A bar
 * per time slot shows only what was seen: an empty slot simply has no bar.
 */
private enum class Series(val label: String) { BUDS("Earbuds"), CASE("Case") }

private fun BatterySample.budLevel(): Int? = listOfNotNull(left, right).filter { it in 1..100 }.minOrNull()
private fun BatterySample.level(series: Series): Int? = when (series) {
    Series.BUDS -> budLevel()
    Series.CASE -> case?.takeIf { it in 1..100 }
}

private fun BatterySample.anyBudCharging() = leftCharging || rightCharging
private fun BatterySample.charging(series: Series) = if (series == Series.CASE) caseCharging else anyBudCharging()

/** One slot of the chart: the last reading taken inside it, if any. */
private data class Slot(val start: Long, val end: Long, val sample: BatterySample?)

private fun slots(samples: List<BatterySample>, from: Long, to: Long, count: Int): List<Slot> {
    val width = (to - from) / count
    val byIndex = samples.groupBy { ((it.time - from) / width).toInt().coerceIn(0, count - 1) }
    return (0 until count).map { i -> Slot(from + i * width, from + (i + 1) * width, byIndex[i]?.lastOrNull()) }
}

@Composable
private fun HistoryCard(samples: List<BatterySample>, now: Long) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    var week by rememberSaveable { mutableStateOf(false) }
    var seriesName by rememberSaveable { mutableStateOf(Series.BUDS.name) }
    val series = if (EarbudModel.active.headphones) Series.BUDS else Series.valueOf(seriesName)
    var picked by remember { mutableStateOf<Int?>(null) }
    val span = if (week) 7 * DAY else DAY
    val from = now - span
    val count = if (week) 28 else 24
    val shown = remember(samples, week) { samples.filter { it.time >= from } }
    val slotList = remember(shown, from, count) { slots(shown, from, now, count) }

    Column {
        SectionHeader("History")
        SunCard {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                if (shown.isEmpty()) {
                    Text(
                        if (EarbudModel.active.headphones) "Battery history appears after you listen with your headphones. It stays on this phone."
                        else "Battery history appears after you take your earbuds out of the case. It stays on this phone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                    return@Column
                }
                // Readout: the touched slot, or the newest reading.
                val slot = picked?.let { slotList.getOrNull(it) }
                val point = slot?.sample ?: shown.last()
                val clock = DateFormat.getTimeFormat(context)
                Text(
                    when {
                        slot == null -> whenLabel(context, point.time, now)
                        slot.sample == null -> "${clock.format(java.util.Date(slot.start))} to ${clock.format(java.util.Date(slot.end))}, no data"
                        else -> whenLabel(context, point.time, now)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                val show = slot == null || slot.sample != null
                Text(
                    if (!show) "No reading"
                    else (if (EarbudModel.active.headphones) listOf("Battery" to point.left) else listOf("L" to point.left, "R" to point.right, "Case" to point.case))
                        .joinToString("   ") { (n, v) -> "$n ${v?.takeIf { it in 1..100 }?.let { "$it%" } ?: "—"}" } +
                        if (point.anyBudCharging()) "   Charging" else "",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(16.dp))
                Box(Modifier.fillMaxWidth()) {
                    BarChart(
                        slots = slotList,
                        series = series,
                        picked = picked,
                        onPick = { i ->
                            if (i != picked) {
                                picked = i
                                if (i != null) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(160.dp)
                            .semantics { contentDescription = "Battery history chart. Touch and drag to see each bar." }
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().padding(start = 30.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    axisLabels(context, from, now, week).forEach {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !week, onClick = { week = false; picked = null }, label = { Text("Day") }, shape = CircleShape)
                    FilterChip(selected = week, onClick = { week = true; picked = null }, label = { Text("Week") }, shape = CircleShape)
                    Spacer(Modifier.weight(1f))
                    // Headphones have one battery and no case: nothing to switch between.
                    if (!EarbudModel.active.headphones) Series.entries.forEach { s ->
                        FilterChip(selected = s == series, onClick = { seriesName = s.name; picked = null }, label = { Text(s.label) }, shape = CircleShape)
                    }
                }
            }
        }
        Footer("Touch and drag to see a reading. Green is charging.")
    }
}

@Composable
private fun chargeColor() = if (MaterialTheme.colorScheme.isLight) Color(0xFF2E7D32) else Color(0xFF7BD88F)

@Composable
private fun BarChart(
    slots: List<Slot>,
    series: Series,
    picked: Int?,
    onPick: (Int?) -> Unit,
    modifier: Modifier
) {
    val colors = MaterialTheme.colorScheme
    val grid = colors.outlineVariant.copy(alpha = 0.5f)
    val track = colors.onSurface.copy(alpha = 0.05f)
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant)
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val normal = colors.primary
    val charge = chargeColor()
    val low = colors.error
    val cursor = colors.onSurface.copy(alpha = 0.12f)
    // Drawn in full on arrival. Switching Day/Week or Earbuds/Case regrows the bars quickly, so
    // it's clear the chart now shows something else.
    val grow = remember { androidx.compose.animation.core.Animatable(1f) }
    val firstDraw = remember { booleanArrayOf(true) }
    androidx.compose.runtime.LaunchedEffect(slots.size, series) {
        if (firstDraw[0]) { firstDraw[0] = false; return@LaunchedEffect }
        grow.snapTo(0f)
        grow.animateTo(1f, tween(320, easing = Motion.Arrive))
    }
    val gutterDp = 30.dp
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    fun index(x: Float, width: Float): Int {
        val g = gutterDp.value * density
        return (((x - g) / (width - g)) * slots.size).toInt().coerceIn(0, slots.lastIndex)
    }
    Canvas(
        modifier
            .pointerInput(slots) {
                detectTapGestures(onTap = { pos ->
                    val i = index(pos.x, size.width.toFloat())
                    onPick(if (i == picked) null else i)
                })
            }
            .pointerInput(slots) {
                detectHorizontalDragGestures(
                    onDragStart = { pos -> onPick(index(pos.x, size.width.toFloat())) },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        onPick(index(change.position.x, size.width.toFloat()))
                    }
                )
            }
    ) {
        val gutter = gutterDp.toPx()
        val w = size.width - gutter
        val h = size.height
        for (level in listOf(50, 100)) {
            val y = h - level / 100f * h
            drawLine(grid, Offset(gutter, y), Offset(gutter + w, y), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f)))
        }
        val slotW = w / slots.size
        val barW = (slotW * 0.7f).coerceAtLeast(2.dp.toPx())
        val radius = androidx.compose.ui.geometry.CornerRadius(barW / 2.5f, barW / 2.5f)
        slots.forEachIndexed { i, slot ->
            val left = gutter + i * slotW + (slotW - barW) / 2
            if (i == picked) drawRoundRect(cursor, topLeft = Offset(gutter + i * slotW, 0f), size = Size(slotW, h), cornerRadius = radius)
            drawRoundRect(track, topLeft = Offset(left, 0f), size = Size(barW, h), cornerRadius = radius)
            val sample = slot.sample ?: return@forEachIndexed
            val level = sample.level(series) ?: return@forEachIndexed
            val color = when {
                sample.charging(series) -> charge
                level <= 20 -> low
                else -> normal
            }.let { if (picked != null && i != picked) it.copy(alpha = 0.45f) else it }
            val barH = (level / 100f * h * grow.value).coerceAtLeast(barW)
            drawRoundRect(color, topLeft = Offset(left, h - barH), size = Size(barW, barH), cornerRadius = radius)
        }
        // Level marks on the left, over the grid, where the eye starts reading.
        for (level in listOf(50, 100)) {
            val text = measurer.measure("$level%", labelStyle)
            val y = h - level / 100f * h
            drawText(text, topLeft = Offset(0f, (y - text.size.height / 2f).coerceIn(0f, h - text.size.height)))
        }
    }
}

private fun whenLabel(context: Context, time: Long, now: Long): String {
    val clock = DateFormat.getTimeFormat(context).format(java.util.Date(time))
    val day = Calendar.getInstance().apply { timeInMillis = time }
    val today = Calendar.getInstance().apply { timeInMillis = now }
    val sameDay = day.get(Calendar.YEAR) == today.get(Calendar.YEAR) && day.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
    today.add(Calendar.DAY_OF_YEAR, -1)
    val yesterday = day.get(Calendar.YEAR) == today.get(Calendar.YEAR) && day.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
    return when {
        sameDay -> "today at $clock"
        yesterday -> "yesterday at $clock"
        else -> java.text.SimpleDateFormat("EEEE", java.util.Locale.getDefault()).format(java.util.Date(time)) + " at $clock"
    }.replaceFirstChar { it.uppercase() }
}

private fun axisLabels(context: Context, from: Long, to: Long, week: Boolean): List<String> =
    if (week) {
        val f = java.text.SimpleDateFormat("EEE", java.util.Locale.getDefault())
        (0..6).map { i -> f.format(java.util.Date(from + i * DAY + DAY / 2)) }
    } else {
        val f = DateFormat.getTimeFormat(context)
        (0..4).map { i -> if (i == 4) "Now" else f.format(java.util.Date(from + i * (to - from) / 4)) }
    }

// ---------------------------------------------------------------------- week of listening

@Composable
private fun WeekCard(samples: List<BatterySample>, now: Long) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val days = remember(samples, now) { BatteryInsights.dailyListening(samples, now, 7) }
    var picked by rememberSaveable { mutableIntStateOf(days.lastIndex) }
    val max = (days.maxOfOrNull { it.second } ?: 0).coerceAtLeast(60)
    val heard = days.filter { it.second > 0 }
    val average = if (heard.isEmpty()) 0 else heard.sumOf { it.second } / heard.size
    val dayName = java.text.SimpleDateFormat("EEEE", java.util.Locale.getDefault())
    val shortName = java.text.SimpleDateFormat("EEEEE", java.util.Locale.getDefault())

    Column {
        SunCard {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                val (dayStart, minutes) = days[picked.coerceIn(days.indices)]
                Text(
                    if (picked == days.lastIndex) "Today" else dayName.format(java.util.Date(dayStart)),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurfaceVariant
                )
                Text(
                    if (minutes == 0) "No listening" else BatteryInsights.formatMinutes(minutes),
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    if (average == 0) "Your listening time appears here as you use your ${EarbudModel.active.noun}."
                    else "You average ${BatteryInsights.formatMinutes(average)} on days you listen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )
                Spacer(Modifier.height(20.dp))
                Row(
                    Modifier.fillMaxWidth().height(140.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    days.forEachIndexed { i, (start, mins) ->
                        val on = i == picked
                        val grow = remember { androidx.compose.animation.core.Animatable(mins.toFloat() / max) }
                        androidx.compose.runtime.LaunchedEffect(mins, max) {
                            grow.animateTo(mins.toFloat() / max, tween(350, easing = Motion.Arrive))
                        }
                        val fill = grow.value
                        val barColor by animateColorAsState(if (on) colors.primary else colors.primary.copy(alpha = 0.3f), label = "barColor")
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(MaterialTheme.shapes.medium)
                                .clickable(onClickLabel = "Show ${dayName.format(java.util.Date(start))}") {
                                    picked = i
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                .semantics { contentDescription = "${dayName.format(java.util.Date(start))}, ${if (mins == 0) "no listening" else BatteryInsights.formatMinutes(mins)}" },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height((112 * fill).coerceAtLeast(4f).dp)
                                        .clip(MaterialTheme.shapes.small)
                                        .background(if (mins == 0) colors.surfaceContainerHighest else barColor)
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                shortName.format(java.util.Date(start)),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (on) colors.primary else colors.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                val day = remember(samples, dayStart) { dayDetails(samples, dayStart) }
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(thickness = 0.5.dp, color = colors.outlineVariant.copy(alpha = 0.7f))
                val clock = DateFormat.getTimeFormat(LocalContext.current)
                listOf(
                    "Battery used" to (day.used?.let { "$it%" } ?: "—"),
                    "Charged" to when (day.charges) { 0 -> "Not charged"; 1 -> "Once"; else -> "${day.charges} times" },
                    "First out" to (day.first?.let { clock.format(java.util.Date(it)) } ?: "—"),
                    "Last heard" to (day.last?.let { clock.format(java.util.Date(it)) } ?: "—")
                ).forEach { (label, value) ->
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
                        Text(value, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        Footer("Tap a day to see it.")
    }
}

private data class DayDetails(val used: Int?, val charges: Int, val first: Long?, val last: Long?)

/** One day from the readings: battery used by the lower earbud, charges, first and last reading out of the case. */
private fun dayDetails(samples: List<BatterySample>, dayStart: Long): DayDetails {
    val day = samples.filter { it.time >= dayStart && it.time < dayStart + DAY }
    var used = 0
    var charges = 0
    day.zipWithNext().forEach { (a, b) ->
        val la = a.budLevel(); val lb = b.budLevel()
        if (la != null && lb != null && !b.anyBudCharging() && lb < la) used += la - lb
        // Charging happens in the case, out of sight, so a clear jump in level counts as a charge too.
        if ((!a.anyBudCharging() && b.anyBudCharging()) || (la != null && lb != null && lb >= la + 10)) charges++
    }
    val out = day.filter { it.budLevel() != null && !it.anyBudCharging() }
    return DayDetails(if (day.size < 2) null else used, charges, out.firstOrNull()?.time, out.lastOrNull()?.time)
}

// ---------------------------------------------------------------------- stats

private data class Stat(val label: String, val value: String, val detail: String, val explain: String)

@Composable
private fun StatsGrid(battery: BatteryState, samples: List<BatterySample>, outlook: BatteryOutlook, now: Long) {
    val leftRate = remember(samples) { BatteryInsights.measuredDrain(samples, 0) }
    val rightRate = remember(samples) { BatteryInsights.measuredDrain(samples, 1) }
    val charged = remember(samples) { BatteryInsights.lastTakenOut(samples) }
    val case = battery.case?.takeIf { it in 1..100 }

    val allStats = listOf(
        Stat(
            "Drain", "${outlook.drainPerHour.roundToInt()}% per hour",
            if (outlook.measured) "Measured this week" else "Sony's rating",
            "How fast the battery drops while you listen. SunPods measures this after about an hour of listening."
        ),
        Stat(
            "Out of the Case", charged?.let { relativeShort(it, now) } ?: "Not Yet",
            if (charged == null) "Shows after the next charge" else "Since the last charge",
            "When you last took your earbuds out of the case after charging."
        ),
        Stat(
            "Balance",
            when {
                leftRate == null || rightRate == null -> "Not Yet"
                abs(leftRate - rightRate) < 1.5 -> "Even"
                leftRate > rightRate -> "Left faster"
                else -> "Right faster"
            },
            if (leftRate == null || rightRate == null) "Available after more listening" else "L ${leftRate.roundToInt()}% · R ${rightRate.roundToInt()}% per hour",
            "Compares how fast each earbud drains. A big gap can mean one handles more calls or touch controls, or its battery is ageing."
        ),
        Stat(
            "Case Reserve",
            case?.let { "%.1f charges".format(it / 100.0 * EarbudModel.active.caseCharges) } ?: "Unknown",
            case?.let { "Case at $it%" } ?: "Open the case to check",
            "A full ${EarbudModel.active.displayName} case recharges both earbuds about ${if (EarbudModel.active.caseCharges == 2.0) "twice" else "%.1f times".format(EarbudModel.active.caseCharges)}. This shows how many charges it has left."
        )
    )
    // Headphones: one battery and no case, so no out-of-case time, left/right balance or case reserve.
    val stats = if (EarbudModel.active.headphones) allStats.filter { it.label !in setOf("Out of the Case", "Balance", "Case Reserve") } else allStats
    var open by rememberSaveable { mutableIntStateOf(-1) }
    Column {
        SectionHeader("At a Glance")
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            stats.chunked(2).forEachIndexed { row, pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEachIndexed { col, stat ->
                        val index = row * 2 + col
                        StatTile(stat, open == index, Modifier.weight(1f)) { open = if (open == index) -1 else index }
                    }
                }
            }
            val explained = stats.getOrNull(open)
            AnimatedVisibility(explained != null, enter = revealIn(), exit = revealOut()) {
                Text(
                    explained?.explain.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
        }
    }
}

@Composable
private fun StatTile(stat: Stat, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(if (selected) colors.secondaryContainer else cardColor, label = "tile")
    Surface(
        color = container,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.clip(RoundedCornerShape(16.dp)).clickable(onClickLabel = "Explain ${stat.label}", onClick = onClick)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stat.label.uppercase(), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            Text(stat.value, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}

private fun relativeShort(time: Long, now: Long): String {
    val minutes = ((now - time) / 60_000).coerceAtLeast(0)
    return when {
        minutes < 60 -> "$minutes min ago"
        minutes < 48 * 60 -> "${minutes / 60} hr ago"
        else -> "${minutes / (24 * 60)} days ago"
    }
}

// ---------------------------------------------------------------------- alerts

@Composable
private fun AlertsCard() {
    val context = LocalContext.current
    var buds by remember { mutableStateOf(BatteryNudges.budAlerts(context)) }
    var case by remember { mutableStateOf(BatteryNudges.caseAlerts(context)) }
    var threshold by remember { mutableStateOf(BatteryNudges.budThreshold(context).toFloat()) }
    Column {
        SunCard {
            ToggleRow(Icons.Rounded.BatteryAlert, EarbudModel.active.titleNoun, "", buds) {
                buds = it; BatteryNudges.setBudAlerts(context, it)
            }
            AnimatedVisibility(buds, enter = revealIn(), exit = revealOut()) {
                Column(Modifier.fillMaxWidth().padding(start = RowTextStart, end = 16.dp, bottom = 8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Alert Level", style = MaterialTheme.typography.bodyMedium)
                        Text("${threshold.roundToInt()}%", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(
                        value = threshold,
                        onValueChange = { threshold = it },
                        onValueChangeFinished = { BatteryNudges.setBudThreshold(context, threshold.roundToInt()) },
                        valueRange = 10f..30f,
                        steps = 3,
                        colors = SliderDefaults.colors(activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent),
                        modifier = Modifier.semantics { contentDescription = "Low earbud battery alert level" }
                    )
                }
            }
            if (!EarbudModel.active.headphones) {
                RowDivider()
                ToggleRow(SunIcons.Case, "Case", "", case) {
                    case = it; BatteryNudges.setCaseAlerts(context, it)
                }
            }
        }
        Footer(if (EarbudModel.active.headphones) "A notification when your headphones reach the level you set."
            else "A notification when an earbud reaches the level you set, or the case reaches 20%.")
    }
}
