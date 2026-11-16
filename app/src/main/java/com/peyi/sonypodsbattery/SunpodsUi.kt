@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.peyi.sonypodsbattery

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

@Composable
fun SunpodsTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

private enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Rounded.Headphones),
    SOUND("Sound", Icons.Rounded.GraphicEq),
    CONTROLS("Controls", Icons.Rounded.TouchApp),
    DEVICE("Device", Icons.Rounded.Info)
}

@Composable
fun SunpodsApp(
    vm: SunpodsViewModel,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onShareDiagnostics: () -> Unit,
    onOpenBluetoothSettings: () -> Unit
) {
    val battery by vm.battery.collectAsState()
    val settings by vm.settings.collectAsState()
    val busy by vm.busy.collectAsState()
    val message by vm.message.collectAsState()
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Sunpods", fontWeight = FontWeight.SemiBold)
                        Text(
                            settings.modelName ?: "WF-1000XM5",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    if (busy) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 16.dp).size(22.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = { if (hasPermission) vm.refreshAll() else onRequestPermission() }) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label) }
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!hasPermission) PermissionCard(onRequestPermission)
            when (tab) {
                Tab.HOME -> HomeScreen(battery, settings, vm)
                Tab.SOUND -> SoundScreen(settings, vm)
                Tab.CONTROLS -> ControlsScreen(settings, vm)
                Tab.DEVICE -> DeviceScreen(battery, settings, onShareDiagnostics, onOpenBluetoothSettings)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ---------- shared pieces ----------

@Composable
private fun Section(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(Modifier.padding(vertical = 16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
            content()
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean?, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Text(if (checked == null) "Not read from earbuds yet" else subtitle.orEmpty())
        },
        trailingContent = {
            Switch(checked = checked == true, enabled = checked != null, onCheckedChange = onChange)
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    )
}

@Composable
private fun <T> Segmented(options: List<T>, selected: T?, label: (T) -> String, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
            ) { Text(label(option), maxLines = 1) }
        }
    }
}

@Composable
private fun PermissionCard(onRequest: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Nearby devices permission needed", style = MaterialTheme.typography.titleMedium)
            Text("Sunpods talks to your earbuds over Bluetooth. Nothing leaves your phone.")
            FilledTonalButton(onClick = onRequest) { Text("Allow") }
        }
    }
}

// ---------- Home ----------

@Composable
private fun HomeScreen(battery: BatteryState, settings: DeviceSettings, vm: SunpodsViewModel) {
    Card(
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                battery.status,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                BatteryRing("Left", battery.left, battery.leftCharging)
                BatteryRing("Right", battery.right, battery.rightCharging)
                BatteryRing("Case", battery.case, battery.caseCharging)
            }
            if (battery.case == null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Open the case lid to read its battery.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    NoiseControl(battery, vm)

    Section("Quick settings") {
        SwitchRow("DSEE Extreme", "Restores detail in compressed music", settings.dseeOn, vm::setDsee)
        SwitchRow("Speak-to-Chat", "Pauses music when you start talking", settings.speakToChat, vm::setSpeakToChat)
        SwitchRow("Pause when removed", "Resumes when you put them back in", settings.pauseWhenRemoved, vm::setPauseWhenRemoved)
    }
}

@Composable
private fun BatteryRing(label: String, level: Int?, charging: Boolean) {
    val colors = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(88.dp)) {
            CircularProgressIndicator(
                progress = { (level ?: 0) / 100f },
                modifier = Modifier.fillMaxSize(),
                color = if ((level ?: 100) <= 20) colors.error else colors.primary,
                trackColor = colors.surfaceVariant,
                strokeWidth = 8.dp,
                strokeCap = StrokeCap.Round
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (charging) Icon(Icons.Rounded.Bolt, contentDescription = "Charging", modifier = Modifier.size(16.dp))
                Text(
                    level?.let { "$it%" } ?: "–",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onPrimaryContainer
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = colors.onPrimaryContainer)
    }
}

@Composable
private fun NoiseControl(battery: BatteryState, vm: SunpodsViewModel) {
    var level by remember(battery.ambientLevel) { mutableFloatStateOf(battery.ambientLevel.toFloat()) }
    Section("Noise control") {
        Spacer(Modifier.height(8.dp))
        Segmented(
            options = ListeningMode.entries.toList(),
            selected = battery.listeningMode,
            label = {
                when (it) {
                    ListeningMode.NOISE_CANCELLING -> "Noise cancel"
                    ListeningMode.AMBIENT -> "Ambient"
                    ListeningMode.OFF -> "Off"
                }
            },
            onSelect = { vm.setListeningMode(it, level.roundToInt(), battery.focusOnVoice) }
        )
        AnimatedVisibility(visible = battery.listeningMode == ListeningMode.AMBIENT) {
            Column(Modifier.padding(top = 8.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Ambient level", style = MaterialTheme.typography.bodyMedium)
                    Text("${level.roundToInt()}", style = MaterialTheme.typography.bodyMedium)
                }
                Slider(
                    value = level,
                    onValueChange = { level = it },
                    valueRange = 1f..20f,
                    steps = 18,
                    onValueChangeFinished = {
                        vm.setListeningMode(ListeningMode.AMBIENT, level.roundToInt(), battery.focusOnVoice)
                    },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                SwitchRow("Focus on voice", "Lets voices through more clearly", battery.focusOnVoice) {
                    vm.setListeningMode(ListeningMode.AMBIENT, level.roundToInt(), it)
                }
            }
        }
    }
}

// ---------- Sound ----------

private val bandLabels = listOf("400", "1k", "2.5k", "6.3k", "16k")

@Composable
private fun SoundScreen(settings: DeviceSettings, vm: SunpodsViewModel) {
    Section("Equaliser") {
        FlowRow(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EqPreset.entries.forEach { preset ->
                FilterChip(
                    selected = settings.eqPreset == preset,
                    onClick = { vm.setEqPreset(preset) },
                    label = { Text(preset.label) }
                )
            }
        }
        if (settings.eqPresetCode == null) {
            Text(
                "Tap refresh to read the current equaliser.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
        EqBands(settings, vm)
    }

    Section("Audio quality") {
        SwitchRow(
            "DSEE Extreme",
            "AI upscaling for compressed music. Uses a little more battery.",
            settings.dseeOn,
            vm::setDsee
        )
        HorizontalDivider(Modifier.padding(horizontal = 20.dp))
        ConnectionPriority.entries.forEach { option ->
            ListItem(
                headlineContent = { Text(option.label) },
                supportingContent = { Text(option.detail) },
                leadingContent = {
                    RadioButton(selected = settings.priority == option, onClick = { vm.setPriority(option) })
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            )
        }
        Text(
            "Changing this makes the earbuds reconnect briefly." +
                (settings.codec?.let { " Current codec: $it." } ?: ""),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
    }
}

@Composable
private fun EqBands(settings: DeviceSettings, vm: SunpodsViewModel) {
    val sourceBands = settings.bands ?: List(5) { 0 }
    val sourceBass = settings.clearBass ?: 0
    var bands by remember(settings.bands) { mutableStateOf(sourceBands) }
    var clearBass by remember(settings.clearBass) { mutableIntStateOf(sourceBass) }
    val editable = settings.eqPreset?.isCustomisable == true
    val commit = { vm.setEqBands(clearBass, bands) }

    Column(Modifier.padding(top = 8.dp)) {
        if (!editable) {
            Text(
                "Pick Manual, Custom 1 or Custom 2 to shape the sound yourself.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
        }
        BandSlider("Clear Bass", clearBass, editable, { clearBass = it }, commit)
        bands.forEachIndexed { index, value ->
            BandSlider("${bandLabels[index]} Hz", value, editable, { v ->
                bands = bands.toMutableList().also { it[index] = v }
            }, commit)
        }
        if (editable) {
            TextButton(
                onClick = {
                    bands = List(5) { 0 }
                    clearBass = 0
                    vm.setEqBands(0, List(5) { 0 })
                },
                modifier = Modifier.padding(horizontal = 8.dp)
            ) { Text("Reset to flat") }
        }
    }
}

@Composable
private fun BandSlider(label: String, value: Int, enabled: Boolean, onChange: (Int) -> Unit, onDone: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(84.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            onValueChangeFinished = onDone,
            valueRange = -10f..10f,
            steps = 19,
            enabled = enabled,
            modifier = Modifier.weight(1f)
        )
        Text(
            if (value > 0) "+$value" else "$value",
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.End,
            modifier = Modifier.width(36.dp)
        )
    }
}

// ---------- Controls ----------

@Composable
private fun ControlsScreen(settings: DeviceSettings, vm: SunpodsViewModel) {
    Section("Speak-to-Chat") {
        SwitchRow("Speak-to-Chat", "Talk to pause music and let ambient sound in", settings.speakToChat, vm::setSpeakToChat)
        if (settings.speakToChat == true) {
            val sensitivity = settings.speakToChatSensitivity ?: SpeakToChatSensitivity.AUTO
            val timeout = settings.speakToChatTimeout ?: SpeakToChatTimeout.STANDARD
            Text(
                "Voice detection sensitivity",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            Segmented(SpeakToChatSensitivity.entries.toList(), settings.speakToChatSensitivity, { it.label }) {
                vm.setSpeakToChatConfig(it, timeout)
            }
            Text(
                "End chat mode after",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp)
            )
            SpeakToChatTimeout.entries.forEach { option ->
                ListItem(
                    headlineContent = { Text(option.label) },
                    leadingContent = {
                        RadioButton(
                            selected = settings.speakToChatTimeout == option,
                            onClick = { vm.setSpeakToChatConfig(sensitivity, option) }
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                )
            }
        }
    }

    Section("Touch controls") {
        val left = settings.touchLeft
        val right = settings.touchRight
        if (left == null || right == null) {
            Text(
                "Tap refresh to read your current touch settings.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
        } else {
            Text("Left earbud", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            Segmented(TouchFunction.entries.toList(), left, { it.short }) { vm.setTouch(it, right) }
            Text(touchHint(left), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            Text("Right earbud", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            Segmented(TouchFunction.entries.toList(), right, { it.short }) { vm.setTouch(left, it) }
            Text(touchHint(right), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            if (left == TouchFunction.AMBIENT_SOUND_CONTROL || right == TouchFunction.AMBIENT_SOUND_CONTROL) {
                Text(
                    "Noise control tap cycles through",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp)
                )
                NoiseCycle.entries.forEach { option ->
                    ListItem(
                        headlineContent = { Text(option.label) },
                        leadingContent = {
                            RadioButton(selected = settings.noiseCycle == option, onClick = { vm.setNoiseCycle(option) })
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                    )
                }
            }
        }
    }

    Section("Wearing detection") {
        SwitchRow("Pause when removed", "Music pauses when you take an earbud out", settings.pauseWhenRemoved, vm::setPauseWhenRemoved)
        SwitchRow("Power off when removed", "Turns off after both earbuds are out for a while", settings.autoOffWhenRemoved, vm::setAutoOff)
    }
}

private fun touchHint(function: TouchFunction): String = when (function) {
    TouchFunction.AMBIENT_SOUND_CONTROL -> "Tap: noise cancelling / ambient. Hold: quick attention."
    TouchFunction.PLAYBACK_CONTROL -> "Tap: play/pause. Double tap: next. Triple tap: previous. Hold: assistant."
    TouchFunction.VOLUME_CONTROL -> "Tap repeatedly: volume up. Hold: volume down."
    TouchFunction.NONE -> "Touches on this side do nothing."
}

// ---------- Device ----------

@Composable
private fun DeviceScreen(
    battery: BatteryState,
    settings: DeviceSettings,
    onShareDiagnostics: () -> Unit,
    onOpenBluetoothSettings: () -> Unit
) {
    val format = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
    Section("Earbuds") {
        InfoRow("Model", settings.modelName ?: "WF-1000XM5")
        InfoRow("Firmware", settings.firmware ?: "Unknown")
        InfoRow("Codec", settings.codec ?: "Unknown")
        InfoRow("Battery read", if (battery.updatedAt == 0L) "Never" else format.format(Date(battery.updatedAt)))
        InfoRow("Settings read", if (settings.readAt == 0L) "Never" else format.format(Date(settings.readAt)))
    }
    Section("Help") {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onOpenBluetoothSettings, modifier = Modifier.fillMaxWidth()) { Text("Bluetooth settings") }
            OutlinedButton(onClick = onShareDiagnostics, modifier = Modifier.fillMaxWidth()) { Text("Share diagnostics") }
        }
    }
    Section("About Sunpods") {
        Text(
            "No account, no sign-in, no internet permission. Sunpods only talks to your earbuds. " +
                "Sony allows one control connection at a time, so close Sound Connect before using Sunpods.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        Text(
            "Version ${SunpodsInfo.VERSION}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = { Text(value, style = MaterialTheme.typography.bodyMedium) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    )
}

object SunpodsInfo {
    const val VERSION = "2.0.0"
}
