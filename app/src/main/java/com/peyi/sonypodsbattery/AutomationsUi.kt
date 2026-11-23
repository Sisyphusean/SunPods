package com.peyi.sonypodsbattery

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Controls tab: rules that switch noise control by themselves. */
@Composable
internal fun AutomationsCard() {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    var stepsFor by remember { mutableStateOf(Trigger.WALKING) }
    val walkLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) AutomationStore.setRule(context, stepsFor, AutomationStore.rule(context, stepsFor).copy(enabled = true))
        tick++
    }
    val placeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { tick++ }

    key(tick) {
        Column {
            SunCard {
                RuleRow(Icons.Rounded.Call, Trigger.CALL, "During Calls", "") { tick++ }
                RowDivider()
                RuleRow(Icons.Rounded.DirectionsRun, Trigger.RUNNING, "While Running", "") { on ->
                    if (on && context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
                        stepsFor = Trigger.RUNNING
                        walkLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                        false
                    } else { tick++; true }
                }
                RowDivider()
                RuleRow(Icons.Rounded.DirectionsWalk, Trigger.WALKING, "While Walking", "") { on ->
                    if (on && context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
                        stepsFor = Trigger.WALKING
                        walkLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                        false
                    } else { tick++; true }
                }
                RowDivider()
                RuleRow(Icons.Rounded.DoNotDisturbOn, Trigger.FOCUS, "In Do Not Disturb", "") { tick++ }
            }
            Footer("Noise control changes for you, and goes back when it ends.")
        }
        Column {
            SectionHeader("Places")
            SunCard {
                PlacesSection(onNeedLocation = {
                    placeLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }) { tick++ }
            }
            Footer("A mode for the Wi-Fi you're on, like home or work.")
        }
        Column {
            SunCard {
                var autoPlay by remember { mutableStateOf(AutoPlay.enabled(context)) }
                ToggleRow(Icons.Rounded.PlayCircle, "Auto Play", "", autoPlay) {
                    autoPlay = it
                    AutoPlay.setEnabled(context, it)
                }
            }
            Footer("Your music starts when you take your earbuds out.")
        }
    }
}

@Composable
private fun key(value: Int, content: @Composable () -> Unit) = androidx.compose.runtime.key(value) { content() }

/** A switchable rule with the mode it sets. [onToggle] returns false to cancel turning it on. */
@Composable
private fun RuleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    trigger: Trigger,
    title: String,
    subtitle: String,
    onToggle: (Boolean) -> Any?
) {
    val context = LocalContext.current
    var rule by remember { mutableStateOf(AutomationStore.rule(context, trigger)) }
    var pick by remember { mutableStateOf(false) }
    ToggleRow(icon, title, subtitle, rule.enabled) { on ->
        if (onToggle(on) != false) {
            rule = rule.copy(enabled = on)
            AutomationStore.setRule(context, trigger, rule)
        }
    }
    AnimatedVisibility(rule.enabled, enter = revealIn(), exit = revealOut()) {
        ActionPill(rule.action) { pick = true }
    }
    if (pick) {
        ChoiceSheet(title, AutoAction.entries.toList(), rule.action, label = { it.label }, onDismiss = { pick = false }) {
            rule = rule.copy(action = it)
            AutomationStore.setRule(context, trigger, rule)
        }
    }
}

@Composable
private fun ActionPill(action: AutoAction, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = RowTextStart, end = 16.dp, top = 2.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Switch to", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(action.short, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun PlacesSection(onNeedLocation: () -> Unit, onChange: () -> Unit) {
    val context = LocalContext.current
    var places by remember { mutableStateOf(AutomationStore.places(context)) }
    var editing by remember { mutableStateOf<Place?>(null) }
    val here = remember { currentSsid(context) }
    val hasLocation = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val hasBackground = LastSeenStore.hasBackgroundPermission(context)

    Column(Modifier.fillMaxWidth()) {
        places.forEachIndexed { i, place ->
            if (i > 0) RowDivider()
            SettingRow(Icons.Rounded.Wifi, place.ssid, modifier = Modifier.clickable { editing = place }) {
                Text(place.action.short, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(onClick = {
                    places = places - place
                    AutomationStore.setPlaces(context, places)
                    onChange()
                }) { Icon(Icons.Rounded.Close, contentDescription = "Remove ${place.ssid}", tint = MaterialTheme.colorScheme.outline) }
            }
        }
        if (places.isNotEmpty()) RowDivider()
        when {
            !hasLocation -> SettingRow(Icons.Rounded.Wifi, "Allow Location to See Wi-Fi Names", modifier = Modifier.clickable(onClick = onNeedLocation))
            here == null -> SettingRow(Icons.Rounded.Wifi, "Connect to Wi-Fi to Add It", enabled = false)
            places.none { it.ssid == here } -> SettingRow(Icons.Rounded.Add, "Add $here", modifier = Modifier.clickable { editing = Place(here, AutoAction.NC) })
            else -> {}
        }
        if (places.isNotEmpty() && !hasBackground) {
            Text(
                "Allow location all the time to use places in the background.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = RowTextStart, end = 16.dp, bottom = 12.dp)
            )
        }
    }
    editing?.let { place ->
        ChoiceSheet("At ${place.ssid}", AutoAction.entries.toList(), place.action, label = { it.label }, onDismiss = { editing = null }) { action ->
            places = places.filter { it.ssid != place.ssid } + place.copy(action = action)
            AutomationStore.setPlaces(context, places)
            onChange()
        }
    }
}
