package com.peyi.sonypodsbattery

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/*
 * Phones (One UI especially) stop background apps to save battery. For SunPods that means a
 * stale widget, missed automations and gaps in battery history. Ask to be left unrestricted.
 */
internal object BackgroundAccess {
    private const val PREFS = "sunpods_ui"
    private const val ASKED = "asked_battery_unrestricted"
    private const val SNOOZE = "battery_card_snoozed_until"

    fun unrestricted(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) ?: true

    /** The system "Allow app to always run in background?" dialog; on Samsung this sets Unrestricted. */
    @SuppressLint("BatteryLife")
    fun request(context: Context) {
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
        runCatching { context.startActivity(direct) }.onFailure {
            runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        }
    }

    /** Asks once by itself; after that the Home card offers it. */
    fun askOnce(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (unrestricted(context) || p.getBoolean(ASKED, false)) return
        p.edit().putBoolean(ASKED, true).apply()
        request(context)
    }

    fun snoozed(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(SNOOZE, 0L) > System.currentTimeMillis()

    fun snooze(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(SNOOZE, System.currentTimeMillis() + 7L * 86_400_000).apply()
}

/** Home card shown while SunPods is still battery restricted. [check] changes on each resume. */
@Composable
internal fun KeepRunningCard(check: Int) {
    val context = LocalContext.current
    var hidden by remember { mutableStateOf(false) }
    val show = remember(check, hidden) { !hidden && !BackgroundAccess.unrestricted(context) && !BackgroundAccess.snoozed(context) }
    if (!show) return
    SunCard {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RowIcon(Icons.Rounded.BatteryAlert, active = true)
                Spacer(Modifier.width(16.dp))
                Text("Keep SunPods running", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Your phone is limiting SunPods in the background, so the widget goes stale, automations miss moments and battery history has gaps. Allow it to run unrestricted.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { BackgroundAccess.snooze(context); hidden = true }) { Text("Not now") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { BackgroundAccess.request(context) }) { Text("Allow") }
            }
        }
    }
}
