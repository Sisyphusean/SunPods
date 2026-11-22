package com.peyi.sonypodsbattery

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.TextButton
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

    Column {
        SectionHeader("Automations")
        SunCard {
            key(tick) {
                RuleRow(Icons.Rounded.Call, Trigger.CALL, "During Calls", "Hear yourself and the room while you talk") { tick++ }
                RowDivider()
                RuleRow(Icons.Rounded.DirectionsRun, Trigger.RUNNING, "While Running", "Stay aware of what's around you on a run") { on ->
                    if (on && context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
                        stepsFor = Trigger.RUNNING
                        walkLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                        false
                    } else { tick++; true }
                }
                RowDivider()
                RuleRow(Icons.Rounded.DirectionsWalk, Trigger.WALKING, "While Walking", "Hear traffic and people around you while you walk") { on ->
                    if (on && context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
                        stepsFor = Trigger.WALKING
                        walkLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                        false
                    } else { tick++; true }
                }
                RowDivider()
                RuleRow(Icons.Rounded.DoNotDisturbOn, Trigger.FOCUS, "In Focus or Do Not Disturb", "Cut out noise while you focus") { tick++ }
                RowDivider()
                var autoPlay by remember { mutableStateOf(AutoPlay.enabled(context)) }
                ToggleRow(Icons.Rounded.PlayCircle, "Auto Play", "Your music starts when you take your earbuds out", autoPlay) {
                    autoPlay = it
                    AutoPlay.setEnabled(context, it)
                }
                RowDivider()
                PlacesSection(onNeedLocation = {
                    placeLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }) { tick++ }
            }
        }
        Footer("Works while your earbuds are connected. When an automation ends, your previous mode returns unless you changed it.")
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
    AnimatedVisibility(rule.enabled, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
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
        Modifier.fillMaxWidth().padding(start = 72.dp, end = 20.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Mode", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
        Spacer(Modifier.width(16.dp))
        TextButton(onClick = onClick, modifier = Modifier.weight(1f)) {
            Text(
                action.label, textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.fillMaxWidth(),
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
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

    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RowIcon(Icons.Rounded.Wifi, active = places.isNotEmpty())
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("At a Place", style = MaterialTheme.typography.bodyLarge)
                Text("Set a mode for a Wi-Fi network, like home or work", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        places.forEach { place ->
            Row(Modifier.fillMaxWidth().padding(start = 56.dp, top = 8.dp).clickable { editing = place }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(place.ssid, style = MaterialTheme.typography.bodyLarge)
                    Text(place.action.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = {
                    places = places - place
                    AutomationStore.setPlaces(context, places)
                    onChange()
                }) { Icon(Icons.Rounded.Close, contentDescription = "Remove ${place.ssid}") }
            }
        }
        Row(Modifier.padding(start = 44.dp, top = 4.dp)) {
            when {
                !hasLocation -> TextButton(onClick = onNeedLocation) { Text("Allow Location to See Wi-Fi Names") }
                here == null -> Text("Connect to a Wi-Fi network to add it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp))
                places.none { it.ssid == here } -> TextButton(onClick = { editing = Place(here, AutoAction.NC) }) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add $here")
                }
            }
        }
        if (places.isNotEmpty() && !hasBackground) {
            Text(
                "To use places in the background, allow location all the time.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 56.dp, top = 4.dp)
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
