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
        SectionHeader("Battery Health")
        SunCard {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                val r = report
                val left = r?.left
                val right = r?.right
                if (r == null || (left == null && right == null)) {
                    Text("Learning Your Batteries", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Listen for about 2½ hours with each earbud to see battery health. Short listens count too, so there's no need to run them flat.",
                        style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant
                    )
                    return@Column
                }
                Text("Per full charge using ${setupText(r)}.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    BudHealth("Left", left, Modifier.weight(1f))
                    BudHealth("Right", right, Modifier.weight(1f))
                }
                BatteryHealth.ratedHours(r.mode)?.let { rated ->
                    val best = listOfNotNull(left, right).minOf { it.hoursPerCharge }
                    val pct = (best / rated * 100).roundToInt().coerceAtMost(100)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Sony rates them at ${hm(rated)}. Your weaker earbud lasts about $pct% of that at your usual settings.",
                        style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(16.dp))
                Text(balanceText(r.imbalance), style = MaterialTheme.typography.bodyMedium)

                Spacer(Modifier.height(8.dp))
                Text(trendText(r), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)

                if (r.costs.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text("What Uses the Most Battery", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    r.costs.take(4).forEach { c ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(c.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            val pct = (c.change * 100).roundToInt()
                            Text(
                                if (pct > 0) "+$pct% drain" else "$pct% drain",
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
            if (listened != null) "Based on ${hm(listened)} of listening in the last ${report!!.windowDays} days, adjusted to your usual settings. Changing the volume or codec won't count as wear."
            else "Battery health is based on how fast each earbud drains while you listen."
        )
    }
}

@Composable
private fun BudHealth(side: String, bud: BatteryHealth.Bud?, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.semantics { contentDescription = "$side earbud: ${bud?.let { hm(it.hoursPerCharge) + " per charge" } ?: "still learning"}" }) {
        Text(side, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        if (bud == null) {
            Text("Learning", style = MaterialTheme.typography.titleLarge)
            Text("Available after more listening", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        } else {
            Text(hm(bud.hoursPerCharge), style = MaterialTheme.typography.titleLarge)
            Text("${hm(bud.low)} to ${hm(bud.high)}", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            // Alone, a bud carries the phone link by itself and drains a little faster.
            if (bud.solo) Text("Used on its own", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

private fun balanceText(imb: Triple<Double, Double, Double>?): String {
    if (imb == null) return "Left and right are compared after you've worn both for a few hours."
    val (ratio, lo, hi) = imb
    return when {
        lo > 1.08 -> "Right drains ${((ratio - 1) * 100).roundToInt()}% faster than left. A steady gap like this usually means the right battery has worn more."
        hi < 0.92 -> "Left drains ${((1 / ratio - 1) * 100).roundToInt()}% faster than right. A steady gap like this usually means the left battery has worn more."
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
