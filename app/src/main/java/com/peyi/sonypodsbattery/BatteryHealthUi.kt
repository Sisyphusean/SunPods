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
    'N' -> "noise cancelling"; 'A' -> "ambient sound"; 'O' -> "noise control off"; else -> "your usual mode"
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
        SectionHeader("Battery health")
        SunCard {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                val r = report
                val left = r?.left
                val right = r?.right
                if (r == null || (left == null && right == null)) {
                    Text("Learning your batteries", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Needs about 2½ hours of listening per earbud with music playing. Partial charges are fine: any stretch between 95% and 10% counts.",
                        style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant
                    )
                    return@Column
                }
                Text("Per full charge, ${modeName(r.mode)}", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
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
                        "Sony rates ${hm(rated)}. Your weaker bud manages about $pct% of that at your volume and codec.",
                        style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(16.dp))
                Text(balanceText(r.imbalance), style = MaterialTheme.typography.bodyMedium)

                Spacer(Modifier.height(8.dp))
                Text(trendText(r), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
        }
        val listened = listOfNotNull(report?.left?.listened, report?.right?.listened).maxOrNull()
        Footer(
            if (listened != null) "From ${hm(listened)} of clean listening in the last ${report!!.windowDays} days. Only time with music playing counts; the first and last few percent are left out because batteries report them unevenly."
            else "Health is read from how fast each bud drains while you listen, so you never need to run them flat."
        )
    }
}

@Composable
private fun BudHealth(side: String, bud: BatteryHealth.Bud?, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.semantics { contentDescription = "$side earbud: ${bud?.let { hm(it.hoursPerCharge) } ?: "not enough data yet"} per charge" }) {
        Text(side, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        if (bud == null) {
            Text("Learning", style = MaterialTheme.typography.titleLarge)
            Text("Needs more listening", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        } else {
            Text(hm(bud.hoursPerCharge), style = MaterialTheme.typography.titleLarge)
            Text("${hm(bud.low)} to ${hm(bud.high)}", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            // Alone, a bud carries the phone link by itself and drains a little faster.
            if (bud.solo) Text("From use on its own", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

private fun balanceText(imb: Triple<Double, Double, Double>?): String {
    if (imb == null) return "Left and right are compared once both have been out together for a few hours."
    val (ratio, lo, hi) = imb
    return when {
        lo > 1.08 -> "Right runs down ${((ratio - 1) * 100).roundToInt()}% faster than left. A steady gap like this usually means the right battery has worn more."
        hi < 0.92 -> "Left runs down ${((1 / ratio - 1) * 100).roundToInt()}% faster than right. A steady gap like this usually means the left battery has worn more."
        else -> "Both buds are wearing evenly."
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
            else -> "Lasts about $pct% longer per charge than in $since, likely lighter use."
        }
    }
    val start = r.trackingSince?.let { SimpleDateFormat("d MMMM", Locale.getDefault()).format(Date(it)) }
    return if (start != null) "Tracking since $start. Your trend appears after two months." else "Your trend appears after two months."
}
