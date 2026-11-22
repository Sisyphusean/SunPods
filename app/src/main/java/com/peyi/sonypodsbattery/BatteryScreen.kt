@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.peyi.sonypodsbattery

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
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
    WeekCard(samples, now)
    StatsGrid(battery, samples, outlook, now)
    BatteryHealthCard(battery.updatedAt)
    AlertsCard()
}

// ---------------------------------------------------------------------- estimate

private enum class EstimateMode(val label: String, val ratedHours: Double) {
    NC("Cancelling", 8.0), AMBIENT("Ambient", 8.0), OFF("Off", 12.0)
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
    val rate = outlook.drainPerHour * (8.0 / mode.ratedHours)
    val buds = listOfNotNull(
        battery.left?.takeIf { it in 1..100 && !battery.leftCharging },
        battery.right?.takeIf { it in 1..100 && !battery.rightCharging }
    )
    val minutes = buds.minOrNull()?.let { (it / rate * 60).roundToInt() }
    val count = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(minutes) { count.animateTo((minutes ?: 0).toFloat(), tween(if (count.value == 0f) 900 else 450)) }
    val shown = count.value

    Surface(color = colors.primaryContainer, shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.fillMaxWidth().padding(24.dp)) {
            Text("Listening Time Remaining", style = MaterialTheme.typography.labelLarge, color = colors.onPrimaryContainer)
            Text(
                if (minutes == null) { if (battery.leftCharging || battery.rightCharging) "Charging" else "Unavailable" } else BatteryInsights.formatMinutes(shown.roundToInt()),
                style = MaterialTheme.typography.displayMedium,
                color = colors.onPrimaryContainer
            )
            Text(
                when {
                    minutes == null && buds.isEmpty() && (battery.leftCharging || battery.rightCharging) -> "Both earbuds are charging in the case."
                    minutes == null -> "Take your earbuds out to see how long they'll last."
                    outlook.measured -> "Based on this week's listening. The ${outlook.lowestBud?.lowercase()} earbud runs out first."
                    else -> "Based on Sony's rating until SunPods learns how you listen."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onPrimaryContainer
            )
            Spacer(Modifier.height(20.dp))
            Text("Estimate for", style = MaterialTheme.typography.labelLarge, color = colors.onPrimaryContainer)
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                EstimateMode.entries.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = mode == m,
                        onClick = { mode = m },
                        shape = SegmentedButtonDefaults.itemShape(i, EstimateMode.entries.size),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = colors.primary,
                            activeContentColor = colors.onPrimary,
                            inactiveContainerColor = Color.Transparent,
                            inactiveContentColor = colors.onPrimaryContainer,
                            activeBorderColor = colors.onPrimaryContainer.copy(alpha = 0.3f),
                            inactiveBorderColor = colors.onPrimaryContainer.copy(alpha = 0.3f)
                        ),
                        icon = {}
                    ) { Text(m.label, maxLines = 1) }
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
    val series = Series.valueOf(seriesName)
    var picked by remember { mutableStateOf<Int?>(null) }
    val span = if (week) 7 * DAY else DAY
    val from = now - span
    val count = if (week) 56 else 48
    val shown = remember(samples, week) { samples.filter { it.time >= from } }
    val slotList = remember(shown, from, count) { slots(shown, from, now, count) }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            SectionHeader("Battery History", Modifier.weight(1f).padding(top = 12.dp))
            SingleChoiceSegmentedButtonRow(Modifier.width(180.dp)) {
                SegmentedButton(selected = !week, onClick = { week = false; picked = null }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("1 Day") }
                SegmentedButton(selected = week, onClick = { week = true; picked = null }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("7 Days") }
            }
        }
        Spacer(Modifier.height(4.dp))
        SunCard {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                if (shown.isEmpty()) {
                    Text(
                        "Battery history appears after you take your earbuds out of the case. It stays on this phone.",
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
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val show = slot == null || slot.sample != null
                    listOf("Left" to point.left, "Right" to point.right, "Case" to point.case).forEach { (name, v) ->
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(name, style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                            Spacer(Modifier.width(6.dp))
                            Text(if (show) v?.takeIf { it in 1..100 }?.let { "$it%" } ?: (if (name == "Case") "Unknown" else "In Case") else "Unknown", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    if (show && point.anyBudCharging()) {
                        Text("Charging", style = MaterialTheme.typography.labelLarge, color = chargeColor(), maxLines = 1, softWrap = false)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth()) {
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
                        modifier = Modifier.weight(1f).height(180.dp)
                            .semantics { contentDescription = "Battery history chart. Touch and drag to see each bar." }
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.height(180.dp), verticalArrangement = Arrangement.SpaceBetween) {
                        listOf("100%", "50%", "0%").forEach {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().padding(end = 40.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    axisLabels(context, from, now, week).forEach {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Series.entries.forEach { s ->
                        FilterChip(
                            selected = s == series,
                            onClick = { seriesName = s.name; picked = null },
                            label = { Text(s.label) },
                            leadingIcon = if (s == series) {
                                { Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }
                            } else null
                        )
                    }
                }
            }
        }
        Footer(
            "Each bar is ${if (week) "3 hours" else "half an hour"}" +
                (if (series == Series.BUDS) " and shows the lower earbud" else "") +
                ". Green means charging. Gaps mean no data."
        )
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
    val grid = colors.outlineVariant
    val normal = colors.primary
    val charge = chargeColor()
    val low = colors.error
    val cursor = colors.onSurface.copy(alpha = 0.12f)
    val grow = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(slots.size, series) {
        grow.snapTo(0f)
        grow.animateTo(1f, tween(700, easing = androidx.compose.animation.core.FastOutSlowInEasing))
    }
    fun index(x: Float, width: Float) = ((x / width) * slots.size).toInt().coerceIn(0, slots.lastIndex)
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
        val w = size.width
        val h = size.height
        for (level in listOf(0, 50, 100)) {
            val y = h - level / 100f * h
            drawLine(grid, Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx(),
                pathEffect = if (level == 50) PathEffect.dashPathEffect(floatArrayOf(6f, 8f)) else null)
        }
        val slotW = w / slots.size
        val barW = (slotW * 0.62f).coerceAtLeast(1.5.dp.toPx())
        val radius = androidx.compose.ui.geometry.CornerRadius(barW / 2, barW / 2)
        slots.forEachIndexed { i, slot ->
            val left = i * slotW + (slotW - barW) / 2
            if (i == picked) drawRect(cursor, topLeft = Offset(i * slotW, 0f), size = Size(slotW, h))
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
        SectionHeader("Listening This Week")
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
                    if (average == 0) "Your listening time appears here as you use your earbuds."
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
                        val grow = remember { androidx.compose.animation.core.Animatable(0f) }
                        androidx.compose.runtime.LaunchedEffect(mins, max) {
                            grow.animateTo(mins.toFloat() / max, tween(600, delayMillis = if (grow.value == 0f) i * 50 else 0, easing = androidx.compose.animation.core.FastOutSlowInEasing))
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
            }
        }
        Footer("Tap a day for details.")
    }
}

// ---------------------------------------------------------------------- stats

private data class Stat(val label: String, val value: String, val detail: String, val explain: String)

@Composable
private fun StatsGrid(battery: BatteryState, samples: List<BatterySample>, outlook: BatteryOutlook, now: Long) {
    val leftRate = remember(samples) { BatteryInsights.measuredDrain(samples, 0) }
    val rightRate = remember(samples) { BatteryInsights.measuredDrain(samples, 1) }
    val charged = remember(samples) { BatteryInsights.lastTakenOut(samples) }
    val case = battery.case?.takeIf { it in 1..100 }

    val stats = listOf(
        Stat(
            "Drain", "${outlook.drainPerHour.roundToInt()}% per hour",
            if (outlook.measured) "Measured this week" else "Sony's rating",
            "How fast an earbud's battery drops while you listen. SunPods measures this after about an hour of listening."
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
            case?.let { "%.1f charges".format(it / 100.0 * 2) } ?: "Unknown",
            case?.let { "Case at $it%" } ?: "Open the case to check",
            "A full WF-1000XM5 case recharges both earbuds about twice. This shows how many charges it has left."
        )
    )
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
            AnimatedVisibility(explained != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
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
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier.clip(MaterialTheme.shapes.extraLarge).clickable(onClickLabel = "Explain ${stat.label}", onClick = onClick)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stat.label, style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
            Text(stat.value, style = MaterialTheme.typography.titleLarge, maxLines = 2)
            Text(stat.detail, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 2)
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
        SectionHeader("Alerts")
        SunCard {
            ToggleRow(Icons.Rounded.BatteryAlert, "Low Earbud Battery", "Notifies you when an earbud runs low", buds) {
                buds = it; BatteryNudges.setBudAlerts(context, it)
            }
            AnimatedVisibility(buds, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(Modifier.fillMaxWidth().padding(start = 72.dp, end = 20.dp, bottom = 8.dp)) {
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
            HorizontalDivider(Modifier.padding(start = 72.dp, end = 20.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
            ToggleRow(SunIcons.Case, "Low Case Battery", "Reminds you to charge the case at 20%", case) {
                case = it; BatteryNudges.setCaseAlerts(context, it)
            }
        }
    }
}
