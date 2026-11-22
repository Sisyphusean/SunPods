package com.peyi.sonypodsbattery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private fun modeName(mode: Char) = when (mode) {
    'N' -> "Noise Cancelling"; 'A' -> "Ambient Sound"; 'O' -> "Noise Control Off"; else -> "your usual mode"
}

private fun codecName(c: Char) = when (c) { 'L' -> "LDAC"; 'A' -> "AAC"; 'S' -> "SBC"; 'C' -> "LC3"; else -> null }

/** "noise cancelling at 40% volume with LDAC". Settings are taken out, so numbers mean your usual setup. */
private fun setupText(r: BatteryHealth.Report): String {
    val s = r.setup ?: return modeName(r.mode)
    return buildString {
        append(modeName(s.mode))
        append(", ${s.volume.roundToInt()}% volume")
        codecName(s.codec)?.let { append(", $it") }
    }
}

private fun hm(hours: Double) = BatteryInsights.formatMinutes((hours * 60).roundToInt())

@Composable
internal fun BatteryHealthCard(refreshKey: Long) {
    val context = LocalContext.current
    val report by produceState<BatteryHealth.Report?>(null, refreshKey) {
        value = withContext(Dispatchers.IO) {
            runCatching { BatteryHealth.update(context); BatteryHealth.report(context) }.getOrNull()
        }
    }
    val colors = MaterialTheme.colorScheme
    Column {
        SunCard {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                val r = report
                val left = r?.left
                val right = r?.right
                if (r == null || (left == null && right == null)) {
                    Text("Still Learning", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text("Your results appear after about 2½ hours of listening on each earbud. Short listens count.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    return@Column
                }
                Text("PER FULL CHARGE", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    BudHealth("Left", left, Modifier.weight(1f))
                    BudHealth("Right", right, Modifier.weight(1f))
                }
                BatteryHealth.ratedHours(r.mode)?.let { rated ->
                    val best = listOfNotNull(left, right).minOf { it.hoursPerCharge }
                    val pct = (best / rated * 100).roundToInt().coerceAtMost(100)
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("$pct% of Sony's ${hm(rated)}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(6.dp))
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { pct / 100f },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        drawStopIndicator = {}
                    )
                }

                Spacer(Modifier.height(16.dp))
                Text(balanceText(r.imbalance), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Text(trendText(r), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)

                if (r.costs.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text("WHAT USES THE MOST", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    r.costs.take(4).forEach { c ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(c.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            val pct = (c.change * 100).roundToInt()
                            Text(
                                if (pct > 0) "+$pct%" else "$pct%",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (pct > 0) colors.error else colors.primary
                            )
                        }
                    }
                }
            }
        }
        val listened = listOfNotNull(report?.left?.listened, report?.right?.listened).maxOrNull()
        Footer(
            if (listened != null) "From ${hm(listened)} of listening in ${report!!.windowDays} days, at your usual settings (${setupText(report!!)})."
            else "From how fast each earbud drains while you listen."
        )
    }
}

@Composable
private fun BudHealth(side: String, bud: BatteryHealth.Bud?, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.semantics { contentDescription = "$side earbud: ${bud?.let { hm(it.hoursPerCharge) + " per charge" } ?: "still learning"}" }) {
        Text(side, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        if (bud == null) {
            Text("Learning", style = MaterialTheme.typography.titleLarge, color = colors.onSurfaceVariant)
        } else {
            Text(hm(bud.hoursPerCharge), style = MaterialTheme.typography.titleLarge)
            Text("${hm(bud.low)} to ${hm(bud.high)}", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            // Alone, a bud carries the phone link by itself and drains a little faster.
            if (bud.solo) Text("Used on its own", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

private fun balanceText(imb: Triple<Double, Double, Double>?): String {
    if (imb == null) return "Left and right are compared after a few hours with both."
    val (ratio, lo, hi) = imb
    return when {
        lo > 1.08 -> "Right drains ${((ratio - 1) * 100).roundToInt()}% faster, so it has likely worn more."
        hi < 0.92 -> "Left drains ${((1 / ratio - 1) * 100).roundToInt()}% faster, so it has likely worn more."
        else -> "Both earbuds are wearing evenly."
    }
}

private fun trendText(r: BatteryHealth.Report): String {
    val fmt = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    val change = r.sinceFirst
    if (change != null && r.firstMonth != null) {
        val pct = (abs(change) * 100).roundToInt()
        val since = fmt.format(Date(r.firstMonth))
        return when {
            pct < 3 -> "About the same as your first month ($since)."
            change < 0 -> "Lasts about $pct% less per charge than in $since."
            else -> "Lasts about $pct% longer per charge than in $since, probably from lighter use."
        }
    }
    val start = r.trackingSince?.let { SimpleDateFormat("d MMMM", Locale.getDefault()).format(Date(it)) }
    return if (start != null) "Tracking since $start. Your trend appears after two months." else "Your trend appears after two months."
}
