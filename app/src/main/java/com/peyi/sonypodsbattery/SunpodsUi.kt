@file:OptIn(ExperimentalMaterial3Api::class)

package com.peyi.sonypodsbattery

import android.os.Build
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NoiseAware
import androidx.compose.material.icons.rounded.NoiseControlOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SettingsInputAntenna
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Shapes
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

// ======================================================================
// Theme: dynamic colour on Android 12+, M3 shape scale, default Roboto type
// ======================================================================

private val SunpodsShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun SunpodsTheme(dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark -> dynamicDarkColorScheme(context)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        dark -> SolarDark
        else -> SolarLight
    }
    MaterialTheme(colorScheme = scheme, shapes = SunpodsShapes, typography = SolarTypography, content = content)
}

// Spacing on the 8dp grid
private val Gutter = 16.dp
private val GroupGap = 24.dp
private val ItemGap = 2.dp
private val GroupOuter = 24.dp
private val GroupInner = 4.dp

private enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Rounded.Headphones),
    SOUND("Sound", Icons.Rounded.GraphicEq),
    CONTROLS("Controls", Icons.Rounded.TouchApp),
    BATTERY("Battery", Icons.Rounded.BatteryStd),
    DEVICE("Device", Icons.Rounded.Info)
}

@Composable
fun SunpodsApp(
    vm: SunpodsViewModel,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onShareDiagnostics: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    dynamicColor: Boolean = false,
    onDynamicColorChange: (Boolean) -> Unit = {}
) {
    val battery by vm.battery.collectAsState()
    val settings by vm.settings.collectAsState()
    val busy by vm.busy.collectAsState()
    val message by vm.message.collectAsState()
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    val snackbar = remember { SnackbarHostState() }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val refresh = { if (hasPermission) vm.refreshAll() else onRequestPermission() }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            LargeTopAppBar(
                title = { Text(if (tab == Tab.HOME) greeting() else tab.label) },
                actions = {
                    IconButton(onClick = refresh, enabled = !busy) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh from earbuds")
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                scrollBehavior = scroll
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
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
        PullToRefreshBox(
            isRefreshing = busy,
            onRefresh = refresh,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Gutter)
                    .padding(top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(GroupGap)
            ) {
                if (!hasPermission) PermissionBanner(onRequestPermission)
                when (tab) {
                    Tab.HOME -> HomeScreen(battery, settings, vm)
                    Tab.SOUND -> SoundScreen(settings, vm)
                    Tab.CONTROLS -> ControlsScreen(settings, vm)
                    Tab.BATTERY -> BatteryInsightsScreen(battery)
                    Tab.DEVICE -> DeviceScreen(battery, settings, onShareDiagnostics, onOpenBluetoothSettings, dynamicColor, onDynamicColorChange)
                }
            }
        }
    }
}

// ======================================================================
// Building blocks: Android 16 style grouped lists
// ======================================================================

private fun itemShape(index: Int, count: Int): RoundedCornerShape {
    val top = if (index == 0) GroupOuter else GroupInner
    val bottom = if (index == count - 1) GroupOuter else GroupInner
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

/** A titled group of rows. Each row gets its own tonal tile; the group reads as one rounded block. */
@Composable
private fun Group(title: String?, footer: String? = null, rows: List<@Composable () -> Unit>) {
    Column {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(start = 16.dp, bottom = 8.dp)
                    .semantics { heading() }
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(ItemGap)) {
            rows.forEachIndexed { index, row ->
                Surface(
                    shape = itemShape(index, rows.size),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) { row() }
            }
        }
        if (footer != null) {
            Text(
                footer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
            )
        }
    }
}

@Composable
private fun RowIcon(icon: ImageVector, active: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (active) colors.secondaryContainer else colors.surfaceContainerHighest)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (active) colors.onSecondaryContainer else colors.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

private val TransparentItem @Composable get() =
    ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)

@Composable
private fun ToggleRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean?, onChange: (Boolean) -> Unit) {
    val known = checked != null
    ListItem(
        modifier = Modifier.toggleable(
            value = checked == true,
            enabled = known,
            role = Role.Switch,
            onValueChange = onChange
        ),
        headlineContent = { Text(title) },
        supportingContent = { Text(if (known) subtitle else "Pull down to read from your earbuds") },
        leadingContent = { RowIcon(icon, active = checked == true) },
        trailingContent = { Switch(checked = checked == true, enabled = known, onCheckedChange = null) },
        colors = TransparentItem
    )
}

@Composable
private fun NavRow(icon: ImageVector, title: String, value: String?, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(title) },
        supportingContent = value?.let { { Text(it) } },
        leadingContent = { RowIcon(icon) },
        trailingContent = {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        colors = TransparentItem
    )
}

@Composable
private fun InfoRow(title: String, value: String) {
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = {
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        colors = TransparentItem
    )
}

/** Bottom sheet that lists options as radio rows with an optional explanation each. */
@Composable
private fun <T> ChoiceSheet(
    title: String,
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    detail: (T) -> String? = { null },
    onDismiss: () -> Unit,
    onPick: (T) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).semantics { heading() }
            )
            options.forEach { option ->
                ListItem(
                    modifier = Modifier.selectable(
                        selected = option == selected,
                        role = Role.RadioButton,
                        onClick = { onPick(option); onDismiss() }
                    ),
                    headlineContent = { Text(label(option)) },
                    supportingContent = detail(option)?.let { { Text(it) } },
                    leadingContent = { RadioButton(selected = option == selected, onClick = null) },
                    colors = TransparentItem
                )
            }
        }
    }
}

@Composable
private fun PermissionBanner(onRequest: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Allow Nearby devices", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            Text(
                "SunPods talks to your earbuds over Bluetooth. Nothing leaves your phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            FilledTonalButton(onClick = onRequest) { Text("Allow") }
        }
    }
}

@Composable
private fun quietSlider() = SliderDefaults.colors(
    activeTickColor = androidx.compose.ui.graphics.Color.Transparent,
    inactiveTickColor = androidx.compose.ui.graphics.Color.Transparent,
    disabledActiveTickColor = androidx.compose.ui.graphics.Color.Transparent,
    disabledInactiveTickColor = androidx.compose.ui.graphics.Color.Transparent
)

private fun signed(v: Int) = if (v > 0) "+$v" else "$v"

// ======================================================================
// Home
// ======================================================================

@Composable
private fun HomeScreen(battery: BatteryState, settings: DeviceSettings, vm: SunpodsViewModel) {
    val connected = battery.left != null || battery.right != null
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SolarHero(
            mode = battery.listeningMode,
            ambientLevel = battery.ambientLevel,
            connected = connected,
            model = settings.modelName ?: "WF-1000XM5"
        ) {
            val next = when (battery.listeningMode) {
                ListeningMode.NOISE_CANCELLING -> ListeningMode.AMBIENT
                ListeningMode.AMBIENT -> ListeningMode.OFF
                else -> ListeningMode.NOISE_CANCELLING
            }
            vm.setListeningMode(next, battery.ambientLevel, battery.focusOnVoice)
        }
        BatteryStrip(battery)
    }
    NoiseControl(battery, vm)
    Group(
        "Quick settings",
        rows = listOf(
            { ToggleRow(Icons.Rounded.AutoAwesome, "DSEE Extreme", "Restores detail in compressed music", settings.dseeOn, vm::setDsee) },
            { ToggleRow(Icons.Rounded.RecordVoiceOver, "Speak-to-Chat", "Talk to pause music", settings.speakToChat, vm::setSpeakToChat) },
            { ToggleRow(Icons.Rounded.Pause, "Pause when removed", "Resumes when you put them back", settings.pauseWhenRemoved, vm::setPauseWhenRemoved) }
        )
    )
}

@Composable
private fun BatteryStrip(battery: BatteryState) {
    val context = LocalContext.current
    val outlook = remember(battery.updatedAt) {
        BatteryInsights.outlook(BatteryHistory.load(context, System.currentTimeMillis() - 7L * 86_400_000), battery)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BatteryPill("Left", battery.left?.takeIf { it in 1..100 }, battery.leftCharging, Modifier.weight(1f), R.drawable.bud_left)
        BatteryPill("Right", battery.right?.takeIf { it in 1..100 }, battery.rightCharging, Modifier.weight(1f), R.drawable.bud_right)
        BatteryPill("Case", battery.case?.takeIf { it in 1..100 }, battery.caseCharging, Modifier.weight(1f), R.drawable.bud_case)
    }
    Text(
        when {
            battery.updatedAt == 0L -> "Pull down to read your earbuds."
            outlook.caseNeedsCharge -> "Case is low. Charge it tonight."
            outlook.minutesLeft != null -> "About ${BatteryInsights.formatMinutes(outlook.minutesLeft)} of listening left · updated ${relative(battery.updatedAt)}"
            else -> "Updated ${relative(battery.updatedAt)}"
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 8.dp)
    )
}

private fun relative(time: Long): String =
    DateUtils.getRelativeTimeSpanString(time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString().lowercase()

/** A tall tile that fills from the bottom like a battery. */
@Composable
private fun BatteryPill(label: String, level: Int?, charging: Boolean, modifier: Modifier = Modifier, art: Int? = null) {
    val colors = MaterialTheme.colorScheme
    val low = level != null && level <= 20
    val fraction by animateFloatAsState((level ?: 0) / 100f, spring(stiffness = 200f), label = "fill")
    val description = when {
        level == null -> "$label battery unknown"
        charging -> "$label battery $level percent, charging"
        else -> "$label battery $level percent"
    }
    Box(
        modifier = modifier
            .height(132.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(colors.surfaceContainerHighest)
            .semantics(mergeDescendants = true) { contentDescription = description }
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(fraction)
                .align(Alignment.BottomCenter)
                .background(if (low) colors.error.copy(alpha = 0.35f) else colors.primary.copy(alpha = 0.28f))
        )
        if (art != null) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(art),
                contentDescription = null,
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxWidth(0.62f).padding(end = 4.dp)
            )
        }
        Column(
            Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    level?.toString() ?: "--",
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (low) colors.onErrorContainer else colors.onSurface
                )
                if (level != null) {
                    Text("%", style = MaterialTheme.typography.titleSmall, color = colors.onSurfaceVariant)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (charging) {
                    Icon(Icons.Rounded.Bolt, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.onPrimaryContainer)
                    Spacer(Modifier.width(2.dp))
                }
                Text(label, style = MaterialTheme.typography.labelLarge, color = colors.onSurface)
            }
        }
    }
}

@Composable
private fun NoiseControl(battery: BatteryState, vm: SunpodsViewModel) {
    var level by remember(battery.ambientLevel) { mutableFloatStateOf(battery.ambientLevel.toFloat()) }
    Column {
        Text(
            "Noise control",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp).semantics { heading() }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeTile(Icons.Rounded.NoiseAware, "Noise\ncancelling", battery.listeningMode == ListeningMode.NOISE_CANCELLING, Modifier.weight(1f)) {
                vm.setListeningMode(ListeningMode.NOISE_CANCELLING, level.roundToInt(), battery.focusOnVoice)
            }
            ModeTile(Icons.Rounded.Hearing, "Ambient\nsound", battery.listeningMode == ListeningMode.AMBIENT, Modifier.weight(1f)) {
                vm.setListeningMode(ListeningMode.AMBIENT, level.roundToInt(), battery.focusOnVoice)
            }
            ModeTile(Icons.Rounded.NoiseControlOff, "Off\n", battery.listeningMode == ListeningMode.OFF, Modifier.weight(1f)) {
                vm.setListeningMode(ListeningMode.OFF, level.roundToInt(), battery.focusOnVoice)
            }
        }
        AnimatedVisibility(
            visible = battery.listeningMode == ListeningMode.AMBIENT,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(Modifier.padding(top = ItemGap), verticalArrangement = Arrangement.spacedBy(ItemGap)) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(GroupInner, GroupInner, GroupInner, GroupInner),
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Ambient level", style = MaterialTheme.typography.bodyLarge)
                            Text("${level.roundToInt()}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                        }
                        Slider(
                            value = level,
                            onValueChange = { level = it },
                            valueRange = 1f..20f,
                            steps = 18,
                            colors = quietSlider(),
                            onValueChangeFinished = { vm.setListeningMode(ListeningMode.AMBIENT, level.roundToInt(), battery.focusOnVoice) },
                            modifier = Modifier.semantics { contentDescription = "Ambient sound level" }
                        )
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(GroupInner, GroupInner, GroupOuter, GroupOuter)
                ) {
                    ToggleRow(Icons.Rounded.RecordVoiceOver, "Focus on voice", "Let voices through more clearly", battery.focusOnVoice) {
                        vm.setListeningMode(ListeningMode.AMBIENT, level.roundToInt(), it)
                    }
                }
            }
        }
    }
}

/** Selectable mode tile. Selection fills with primary and the corners round out (expressive shape change). */
@Composable
private fun ModeTile(icon: ImageVector, label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val corner by animateDpAsState(if (selected) 32.dp else 16.dp, spring(dampingRatio = 0.6f, stiffness = 400f), label = "corner")
    val container by animateColorAsState(if (selected) colors.primary else colors.surfaceContainer, label = "container")
    val content by animateColorAsState(if (selected) colors.onPrimary else colors.onSurface, label = "content")
    Surface(
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(corner),
        modifier = modifier
            .height(112.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Text(label.trimEnd(), style = MaterialTheme.typography.labelLarge, minLines = 2)
        }
    }
}

// ======================================================================
// Sound
// ======================================================================

private val bandLabels = listOf("400", "1k", "2.5k", "6.3k", "16k")

@Composable
private fun SoundScreen(settings: DeviceSettings, vm: SunpodsViewModel) {
    Column {
        Text(
            "Equaliser",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp).semantics { heading() }
        )
        val presets = EqPreset.entries.filter { it != EqPreset.MANUAL || settings.eqPreset == EqPreset.MANUAL }
        val chipState = rememberLazyListState()
        LaunchedEffect(settings.eqPreset) {
            val index = presets.indexOf(settings.eqPreset)
            if (index >= 0) chipState.animateScrollToItem(index)
        }
        LazyRow(
            state = chipState,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(presets) { preset ->
                val selected = settings.eqPreset == preset
                FilterChip(
                    selected = selected,
                    onClick = { vm.setEqPreset(preset) },
                    label = { Text(preset.label) },
                    leadingIcon = if (selected) {
                        { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                    } else null
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        EqPanel(settings, vm)
    }

    Group(
        "Audio quality",
        footer = settings.codec?.let { "Playing over $it right now. Changing priority makes the earbuds reconnect briefly." },
        rows = listOf(
            { ToggleRow(Icons.Rounded.AutoAwesome, "DSEE Extreme", "AI upscaling for compressed music. Uses a little more battery.", settings.dseeOn, vm::setDsee) },
            {
                var open by remember { mutableStateOf(false) }
                NavRow(Icons.Rounded.SettingsInputAntenna, "Connection priority", settings.priority?.label ?: "Not read yet") { open = true }
                if (open) {
                    ChoiceSheet(
                        "Connection priority",
                        ConnectionPriority.entries.toList(),
                        settings.priority,
                        label = { it.label },
                        detail = { it.detail },
                        onDismiss = { open = false },
                        onPick = vm::setPriority
                    )
                }
            }
        )
    )
}

@Composable
private fun EqPanel(settings: DeviceSettings, vm: SunpodsViewModel) {
    val sourceBands = settings.bands ?: List(5) { 0 }
    val sourceBass = settings.clearBass ?: 0
    var bands by remember(settings.bands) { mutableStateOf(sourceBands) }
    var clearBass by remember(settings.clearBass) { mutableIntStateOf(sourceBass) }
    val editable = settings.eqPreset?.isCustomisable == true
    val commit = { vm.setEqBands(clearBass, bands) }

    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.fillMaxWidth().padding(vertical = 20.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                VerticalBand("Bass", clearBass, editable, { clearBass = it }, commit, emphasised = true)
                bands.forEachIndexed { index, value ->
                    VerticalBand(bandLabels[index], value, editable, { v ->
                        bands = bands.toMutableList().also { it[index] = v }
                    }, commit)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (editable) "Drag a band to shape the sound." else "Presets are fixed. Pick Custom 1 or 2 to edit.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (editable) {
                    TextButton(onClick = {
                        bands = List(5) { 0 }
                        clearBass = 0
                        vm.setEqBands(0, List(5) { 0 })
                    }) { Text("Flatten") }
                } else {
                    FilledTonalButton(
                        onClick = { vm.setEqPreset(EqPreset.CUSTOM_1) },
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Customise")
                    }
                }
            }
        }
    }
}

@Composable
private fun VerticalBand(
    label: String,
    value: Int,
    enabled: Boolean,
    onChange: (Int) -> Unit,
    onDone: () -> Unit,
    emphasised: Boolean = false
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(48.dp)) {
        Text(
            signed(value),
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            onValueChangeFinished = onDone,
            valueRange = -10f..10f,
            steps = 19,
            enabled = enabled,
            colors = quietSlider(),
            modifier = Modifier
                .height(196.dp)
                .vertical()
                .semantics { contentDescription = "$label band" }
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (emphasised) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/** Rotates a horizontal component to run bottom (min) to top (max). */
private fun Modifier.vertical() = this
    .graphicsLayer {
        rotationZ = 270f
        transformOrigin = TransformOrigin(0f, 0f)
    }
    .layout { measurable, constraints ->
        val placeable = measurable.measure(
            Constraints(
                minWidth = constraints.minHeight,
                maxWidth = constraints.maxHeight,
                minHeight = constraints.minWidth,
                maxHeight = constraints.maxWidth
            )
        )
        layout(placeable.height, placeable.width) { placeable.place(-placeable.width, 0) }
    }

// ======================================================================
// Controls
// ======================================================================

@Composable
private fun ControlsScreen(settings: DeviceSettings, vm: SunpodsViewModel) {
    val sensitivity = settings.speakToChatSensitivity ?: SpeakToChatSensitivity.AUTO
    val timeout = settings.speakToChatTimeout ?: SpeakToChatTimeout.STANDARD

    val chatRows = buildList<@Composable () -> Unit> {
        add { ToggleRow(Icons.Rounded.RecordVoiceOver, "Speak-to-Chat", "Start talking and music pauses, ambient sound comes in", settings.speakToChat, vm::setSpeakToChat) }
        if (settings.speakToChat == true) {
            add {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text("Voice detection", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        val options = SpeakToChatSensitivity.entries
                        options.forEachIndexed { i, option ->
                            SegmentedButton(
                                selected = settings.speakToChatSensitivity == option,
                                onClick = { vm.setSpeakToChatConfig(option, timeout) },
                                shape = SegmentedButtonDefaults.itemShape(i, options.size)
                            ) { Text(option.label) }
                        }
                    }
                }
            }
            add {
                var open by remember { mutableStateOf(false) }
                NavRow(Icons.Rounded.SwapHoriz, "End chat mode", settings.speakToChatTimeout?.label ?: "Not read yet") { open = true }
                if (open) {
                    ChoiceSheet("End chat mode after", SpeakToChatTimeout.entries.toList(), settings.speakToChatTimeout,
                        label = { it.label }, onDismiss = { open = false }) { vm.setSpeakToChatConfig(sensitivity, it) }
                }
            }
        }
    }
    Group("Speak-to-Chat", rows = chatRows)

    val left = settings.touchLeft
    val right = settings.touchRight
    val touchRows = buildList<@Composable () -> Unit> {
        add { TouchRow("Left earbud", left) { picked -> right?.let { vm.setTouch(picked, it) } } }
        add { TouchRow("Right earbud", right) { picked -> left?.let { vm.setTouch(it, picked) } } }
        if (left == TouchFunction.AMBIENT_SOUND_CONTROL || right == TouchFunction.AMBIENT_SOUND_CONTROL) {
            add {
                var open by remember { mutableStateOf(false) }
                NavRow(Icons.Rounded.NoiseAware, "Noise control cycles through", settings.noiseCycle?.label ?: "Not read yet") { open = true }
                if (open) {
                    ChoiceSheet("A tap cycles through", NoiseCycle.entries.toList(), settings.noiseCycle,
                        label = { it.label }, onDismiss = { open = false }, onPick = vm::setNoiseCycle)
                }
            }
        }
    }
    Group("Touch controls", footer = "Hold either earbud for Quick Attention when it's set to noise control.", rows = touchRows)

    Group(
        "Wearing detection",
        rows = listOf(
            { ToggleRow(Icons.Rounded.Pause, "Pause when removed", "Music pauses when you take an earbud out", settings.pauseWhenRemoved, vm::setPauseWhenRemoved) },
            { ToggleRow(Icons.Rounded.PowerSettingsNew, "Power off when removed", "Saves battery when both earbuds are out", settings.autoOffWhenRemoved, vm::setAutoOff) }
        )
    )
}

@Composable
private fun TouchRow(side: String, current: TouchFunction?, onPick: (TouchFunction) -> Unit) {
    var open by remember { mutableStateOf(false) }
    NavRow(Icons.Rounded.TouchApp, side, current?.label ?: "Not read yet") { if (current != null) open = true }
    if (open) {
        ChoiceSheet("$side", TouchFunction.entries.toList(), current,
            label = { it.label }, detail = ::touchHint, onDismiss = { open = false }, onPick = onPick)
    }
}

private fun touchHint(function: TouchFunction): String = when (function) {
    TouchFunction.AMBIENT_SOUND_CONTROL -> "Tap: switch noise mode. Hold: Quick Attention."
    TouchFunction.PLAYBACK_CONTROL -> "Tap: play/pause. Double: next. Triple: previous. Hold: assistant."
    TouchFunction.VOLUME_CONTROL -> "Tap: volume up. Hold: volume down."
    TouchFunction.NONE -> "Touches on this side do nothing."
}

// ======================================================================
// Device
// ======================================================================

@Composable
private fun DeviceScreen(
    battery: BatteryState,
    settings: DeviceSettings,
    onShareDiagnostics: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    dynamicColor: Boolean,
    onDynamicColorChange: (Boolean) -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.extraLarge) {
        Row(Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.Headphones,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    settings.modelName ?: "WF-1000XM5",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    listOfNotNull(settings.firmware?.let { "Firmware $it" }, settings.codec).joinToString("  ·  ").ifEmpty { "Pull down to read device info" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }

    Group(
        "Status",
        rows = listOf(
            { InfoRow("Battery read", if (battery.updatedAt == 0L) "Never" else relative(battery.updatedAt)) },
            { InfoRow("Settings read", if (settings.readAt == 0L) "Never" else relative(settings.readAt)) }
        )
    )

    Group(
        "Appearance",
        rows = listOf(
            {
                ToggleRow(
                    Icons.Rounded.Palette, "Match wallpaper colours",
                    "Use Material You colours instead of SunPods amber", dynamicColor, onDynamicColorChange
                )
            }
        )
    )

    Group(
        "Help",
        rows = listOf(
            { NavRow(Icons.Rounded.Bluetooth, "Bluetooth settings", "Pair, forget or reconnect", onOpenBluetoothSettings) },
            { NavRow(Icons.Rounded.BugReport, "Share diagnostics", "Send the connection log", onShareDiagnostics) }
        ),
        footer = "No account, no sign-in, no internet. Sony allows one control connection at a time, so close Sound Connect while using SunPods.\n\nSunPods ${SunpodsInfo.VERSION}"
    )
}

object SunpodsInfo {
    const val VERSION = "2.3.0"
}
