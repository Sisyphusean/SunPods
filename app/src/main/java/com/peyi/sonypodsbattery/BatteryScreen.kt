@file:OptIn(ExperimentalMaterial3Api::class)

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
import androidx.compose.material.icons.rounded.Inventory2
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
            Text("Listening left", style = MaterialTheme.typography.labelLarge, color = colors.onPrimaryContainer)
            Text(
                if (minutes == null) "--" else BatteryInsights.formatMinutes(shown.roundToInt()),
                style = MaterialTheme.typography.displayMedium,
                color = colors.onPrimaryContainer
            )
            Text(
                when {
                    minutes == null && buds.isEmpty() && (battery.leftCharging || battery.rightCharging) -> "Both buds are charging in the case."
                    minutes == null -> "Take your buds out to see an estimate."
                    outlook.measured -> "From how you listened this week. ${outlook.lowestBud} bud runs out first."
                    else -> "From Sony's rating until SunPods learns your habits."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onPrimaryContainer
            )
            Spacer(Modifier.height(20.dp))
            Text("If you listen with", style = MaterialTheme.typography.labelLarge, color = colors.onPrimaryContainer)
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

private enum class Series(val label: String) { LEFT("Left"), RIGHT("Right"), CASE("Case") }

private fun BatterySample.level(series: Series): Int? = when (series) {
    Series.LEFT -> left
    Series.RIGHT -> right
    Series.CASE -> case
}?.takeIf { it in 1..100 }

private fun BatterySample.anyBudCharging() = leftCharging || rightCharging

@Composable
private fun HistoryCard(samples: List<BatterySample>, now: Long) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    var week by rememberSaveable { mutableStateOf(false) }
    var hidden by rememberSaveable { mutableStateOf(setOf<String>()) }
    var selected by remember { mutableStateOf<BatterySample?>(null) }
    val span = if (week) 7 * DAY else DAY
    val from = now - span
    val shown = remember(samples, week) { samples.filter { it.time >= from } }
    // Right gets a cool blue so the two buds never read as one line.
    val right = if (colors.isLight) Color(0xFF3B6EA8) else Color(0xFF9CC2F2)
    val seriesColor = mapOf(Series.LEFT to colors.primary, Series.RIGHT to right, Series.CASE to colors.outline)

    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            SectionHeader("History", Modifier.weight(1f).padding(top = 12.dp))
            SingleChoiceSegmentedButtonRow(Modifier.width(180.dp)) {
                SegmentedButton(selected = !week, onClick = { week = false; selected = null }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("24 h") }
                SegmentedButton(selected = week, onClick = { week = true; selected = null }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("7 days") }
            }
        }
        Spacer(Modifier.height(4.dp))
        SunCard {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                if (shown.size < 2) {
                    Text(
                        "Your battery story starts here. SunPods notes a reading every few minutes while your buds are out, and nothing leaves your phone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                    return@Column
                }
                // Readout: the touched moment, or the latest reading.
                val point = selected ?: shown.last()
                Text(
                    if (selected == null) "Latest, ${whenLabel(context, point.time, now)}" else whenLabel(context, point.time, now),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Series.entries.forEach { s ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(seriesColor.getValue(s)))
                            Spacer(Modifier.width(6.dp))
                            Text(point.level(s)?.let { "$it%" } ?: "--", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    if (point.anyBudCharging()) {
                        Text("Charging", style = MaterialTheme.typography.labelLarge, color = colors.primary, modifier = Modifier.align(Alignment.CenterVertically))
                    }
                }
                Spacer(Modifier.height(16.dp))
                val chartDescription = "Battery history chart. Touch and drag to see readings."
                ScrubChart(
                    samples = shown,
                    from = from,
                    to = now,
                    hidden = hidden,
                    seriesColor = seriesColor,
                    selected = selected,
                    onSelect = { s ->
                        if (s != selected) {
                            selected = s
                            if (s != null) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(200.dp).semantics { contentDescription = chartDescription }
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    axisLabels(context, from, now, week).forEach {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Series.entries.forEach { s ->
                        val on = s.name !in hidden
                        FilterChip(
                            selected = on,
                            onClick = { hidden = if (on) hidden + s.name else hidden - s.name },
                            label = { Text(s.label) },
                            leadingIcon = {
                                if (on) Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize))
                                else Box(Modifier.size(8.dp).clip(CircleShape).background(seriesColor.getValue(s)))
                            }
                        )
                    }
                }
            }
        }
        Footer("Touch and drag across the chart to see any moment. Shaded parts are time in the case.")
    }
}

@Composable
private fun ScrubChart(
    samples: List<BatterySample>,
    from: Long,
    to: Long,
    hidden: Set<String>,
    seriesColor: Map<Series, Color>,
    selected: BatterySample?,
    onSelect: (BatterySample?) -> Unit,
    modifier: Modifier
) {
    val colors = MaterialTheme.colorScheme
    val grid = colors.outlineVariant
    val charging = colors.primary.copy(alpha = 0.08f)
    val low = colors.error.copy(alpha = 0.08f)
    val cursor = colors.onSurface
    val dotRing = cardColor
    val reveal = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(from / 3_600_000, hidden) {
        reveal.snapTo(0f)
        reveal.animateTo(1f, tween(800, easing = androidx.compose.animation.core.FastOutSlowInEasing))
    }
    fun nearest(x: Float, width: Float): BatterySample? {
        val t = from + (x / width).coerceIn(0f, 1f) * (to - from)
        return samples.minByOrNull { abs(it.time - t) }
    }
    Canvas(
        modifier
            .pointerInput(samples, from, to) {
                detectTapGestures(onTap = { pos ->
                    val hit = nearest(pos.x, size.width.toFloat())
                    onSelect(if (hit == selected) null else hit)
                })
            }
            .pointerInput(samples, from, to) {
                detectHorizontalDragGestures(
                    onDragStart = { pos -> onSelect(nearest(pos.x, size.width.toFloat())) },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        onSelect(nearest(change.position.x, size.width.toFloat()))
                    }
                )
            }
    ) {
        val w = size.width
        val h = size.height
        fun x(t: Long) = ((t - from).toFloat() / (to - from)) * w
        fun y(level: Int) = h - level / 100f * h
        drawRect(low, topLeft = Offset(0f, y(20)), size = Size(w, h - y(20)))
        samples.zipWithNext().forEach { (a, b) ->
            if (a.anyBudCharging() && b.time - a.time < 3 * 3_600_000L) {
                drawRect(charging, topLeft = Offset(x(a.time), 0f), size = Size(x(b.time) - x(a.time), h))
            }
        }
        for (level in listOf(0, 50, 100)) {
            drawLine(grid, Offset(0f, y(level)), Offset(w, y(level)), strokeWidth = 1.dp.toPx(),
                pathEffect = if (level == 50) PathEffect.dashPathEffect(floatArrayOf(8f, 8f)) else null)
        }
        clipRect(right = w * reveal.value) {
        for (s in Series.entries) {
            if (s.name in hidden) continue
            val path = Path()
            var drawing = false
            var lastTime = 0L
            for (p in samples) {
                val level = p.level(s)
                if (level == null || p.time - lastTime > 30L * 60 * 1000) drawing = false
                if (level != null) {
                    if (!drawing) path.moveTo(x(p.time), y(level)) else path.lineTo(x(p.time), y(level))
                    drawing = true
                }
                lastTime = p.time
            }
            drawPath(
                path, seriesColor.getValue(s),
                style = Stroke(
                    width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round,
                    pathEffect = if (s == Series.CASE) PathEffect.dashPathEffect(floatArrayOf(10f, 10f)) else null
                )
            )
        }
        }
        if (selected != null) {
            val cx = x(selected.time)
            drawLine(cursor.copy(alpha = 0.5f), Offset(cx, 0f), Offset(cx, h), strokeWidth = 1.5.dp.toPx())
            for (s in Series.entries) {
                if (s.name in hidden) continue
                val level = selected.level(s) ?: continue
                drawCircle(dotRing, 7.dp.toPx(), Offset(cx, y(level)))
                drawCircle(seriesColor.getValue(s), 5.dp.toPx(), Offset(cx, y(level)))
            }
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
        SectionHeader("Listening this week")
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
                    if (average == 0) "Listening time shows up here as you use your buds."
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
        Footer("Tap a day to see it.")
    }
}

// ---------------------------------------------------------------------- stats

private data class Stat(val label: String, val value: String, val detail: String, val explain: String)

@Composable
private fun StatsGrid(battery: BatteryState, samples: List<BatterySample>, outlook: BatteryOutlook, now: Long) {
    val leftRate = remember(samples) { BatteryInsights.measuredDrain(samples, Series.LEFT.ordinal) }
    val rightRate = remember(samples) { BatteryInsights.measuredDrain(samples, Series.RIGHT.ordinal) }
    val charged = remember(samples) { BatteryInsights.lastTakenOut(samples) }
    val case = battery.case?.takeIf { it in 1..100 }

    val stats = listOf(
        Stat(
            "Drain", "${outlook.drainPerHour.roundToInt()}% an hour",
            if (outlook.measured) "Measured this week" else "Sony's rating",
            "How fast a bud empties while you listen. SunPods measures it from your own readings once it has about an hour of listening."
        ),
        Stat(
            "Out of the case", charged?.let { relativeShort(it, now) } ?: "--",
            if (charged == null) "Not seen yet" else "Since the last charge",
            "When you last took the buds out of the case after charging."
        ),
        Stat(
            "Balance",
            when {
                leftRate == null || rightRate == null -> "--"
                abs(leftRate - rightRate) < 1.5 -> "Even"
                leftRate > rightRate -> "Left faster"
                else -> "Right faster"
            },
            if (leftRate == null || rightRate == null) "Needs more listening" else "L ${leftRate.roundToInt()}%  R ${rightRate.roundToInt()}% an hour",
            "Compares how fast each bud drains. A big gap can mean one bud handles calls or touch controls more, or its battery is ageing."
        ),
        Stat(
            "Case reserve",
            case?.let { "%.1f charges".format(it / 100.0 * 2) } ?: "--",
            case?.let { "Case at $it%" } ?: "Open the case to read it",
            "A full WF-1000XM5 case refills both buds about twice, so this is how many more top ups it holds."
        )
    )
    var open by rememberSaveable { mutableIntStateOf(-1) }
    Column {
        SectionHeader("At a glance")
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
            Text(stat.value, style = MaterialTheme.typography.titleLarge, maxLines = 1)
            Text(stat.detail, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 2)
        }
    }
}

private fun relativeShort(time: Long, now: Long): String {
    val minutes = ((now - time) / 60_000).coerceAtLeast(0)
    return when {
        minutes < 60 -> "$minutes min ago"
        minutes < 48 * 60 -> "${minutes / 60} h ago"
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
            ToggleRow(Icons.Rounded.BatteryAlert, "Low earbud", "Tells you when a bud is running out", buds) {
                buds = it; BatteryNudges.setBudAlerts(context, it)
            }
            AnimatedVisibility(buds, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(Modifier.fillMaxWidth().padding(start = 72.dp, end = 20.dp, bottom = 8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Alert at", style = MaterialTheme.typography.bodyMedium)
                        Text("${threshold.roundToInt()}%", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(
                        value = threshold,
                        onValueChange = { threshold = it },
                        onValueChangeFinished = { BatteryNudges.setBudThreshold(context, threshold.roundToInt()) },
                        valueRange = 10f..30f,
                        steps = 3,
                        colors = SliderDefaults.colors(activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent),
                        modifier = Modifier.semantics { contentDescription = "Low earbud alert level" }
                    )
                }
            }
            HorizontalDivider(Modifier.padding(start = 72.dp, end = 20.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
            ToggleRow(Icons.Rounded.Inventory2, "Low case", "A reminder to charge the case at 20%", case) {
                case = it; BatteryNudges.setCaseAlerts(context, it)
            }
        }
    }
}
