@file:OptIn(ExperimentalMaterial3Api::class)

package com.peyi.sonypodsbattery

import android.os.Build
import android.text.format.DateUtils
import androidx.compose.material3.Button
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import kotlinx.coroutines.launch
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.produceState
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.LocationOff
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Earbuds
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.BluetoothAudio
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
import androidx.compose.material.icons.rounded.Devices
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

// Spacing on the 4/8dp grid. Generous on purpose: one idea per card, air between cards.
private val Gutter = 20.dp
private val GroupGap = 32.dp
private val CardShape = RoundedCornerShape(28.dp)

// Page and card surfaces: a quiet tinted page with clean cards on top (light), or deep page
// with raised cards (dark). Works with the Solar palette and with Material You colours.
internal val ColorScheme.isLight: Boolean get() = surface.luminance() > 0.5f
internal val pageColor: Color @Composable get() =
    MaterialTheme.colorScheme.let { if (it.isLight) it.surfaceContainer else it.surface }
internal val cardColor: Color @Composable get() =
    MaterialTheme.colorScheme.let { if (it.isLight) it.surfaceContainerLowest else it.surfaceContainerHigh }
private val dividerColor: Color @Composable get() = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)

/** Quiet section label above a card. */
@Composable
internal fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp).semantics { heading() }
    )
}

/** Small explanatory text under a card. */
@Composable
internal fun Footer(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp)
    )
}

@Composable
internal fun SunCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = cardColor, shape = CardShape, modifier = modifier.fillMaxWidth()) {
        Column(content = content)
    }
}

@Composable
internal fun RowDivider(inset: Dp = 72.dp) {
    HorizontalDivider(Modifier.padding(start = inset, end = 20.dp), thickness = 1.dp, color = dividerColor)
}

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
    val liveBattery by vm.battery.collectAsState()
    val liveSettings by vm.settings.collectAsState()
    val demoContext = LocalContext.current
    val demo = remember { DemoMode.on(demoContext) }
    val battery = if (demo) DemoMode.battery else liveBattery
    val settings = if (demo) DemoMode.settings else liveSettings
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
        containerColor = pageColor,
        topBar = {
            LargeTopAppBar(
                title = { Text(if (tab == Tab.HOME) "SunPods" else tab.label) },
                actions = {
                    IconButton(onClick = refresh, enabled = !busy) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh from earbuds")
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = pageColor,
                    scrolledContainerColor = pageColor
                ),
                scrollBehavior = scroll
            )
        },
        bottomBar = {
            NavigationBar(containerColor = pageColor) {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) }
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
                    .verticalScroll(remember(tab) { androidx.compose.foundation.ScrollState(0) })
                    .padding(horizontal = Gutter)
                    .padding(top = 8.dp, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(GroupGap)
            ) {
                if (!hasPermission) PermissionBanner(onRequestPermission)
                // Pages fade and rise slightly into place, so a tab change feels like one motion.
                androidx.compose.animation.AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        (fadeIn(androidx.compose.animation.core.tween(220, delayMillis = 60)) +
                            androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(280)) { it / 24 })
                            .togetherWith(fadeOut(androidx.compose.animation.core.tween(120)))
                    },
                    label = "tab"
                ) { page ->
                    Column(verticalArrangement = Arrangement.spacedBy(GroupGap)) {
                        when (page) {
                            Tab.HOME -> HomeScreen(battery, settings, vm, busy)
                            Tab.SOUND -> SoundScreen(settings, vm)
                            Tab.CONTROLS -> ControlsScreen(settings, vm)
                            Tab.BATTERY -> BatteryInsightsScreen(battery)
                            Tab.DEVICE -> DeviceScreen(battery, settings, onShareDiagnostics, onOpenBluetoothSettings, dynamicColor, onDynamicColorChange)
                        }
                    }
                }
            }
        }
    }
}

// ======================================================================
// Building blocks: Android 16 style grouped lists
// ======================================================================

/** A titled group of rows in one card, separated by hairlines. */
@Composable
private fun Group(title: String?, footer: String? = null, inset: Dp = 72.dp, rows: List<@Composable () -> Unit>) {
    Column {
        if (title != null) SectionHeader(title)
        SunCard {
            rows.forEachIndexed { index, row ->
                if (index > 0) RowDivider(inset)
                row()
            }
        }
        if (footer != null) Footer(footer)
    }
}

@Composable
internal fun RowIcon(icon: ImageVector, active: Boolean = true) {
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
internal fun ToggleRow(icon: ImageVector?, title: String, subtitle: String, checked: Boolean?, onChange: (Boolean) -> Unit) {
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
        leadingContent = icon?.let { { RowIcon(it, active = checked == true) } },
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
internal fun <T> ChoiceSheet(
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

/**
 * Home adapts to the connection. Connected: hero, battery, noise control, quick settings.
 * Disconnected: the controls that can't do anything are tucked away; what is left is the last
 * reading and where the earbuds were last seen.
 */
@Composable
private fun HomeScreen(battery: BatteryState, settings: DeviceSettings, vm: SunpodsViewModel, busy: Boolean) {
    val context = LocalContext.current
    var resumes by remember { mutableIntStateOf(0) }
    @Suppress("DEPRECATION")
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) resumes++
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    // Saved levels alone are not proof of a connection (a missed disconnect leaves them behind),
    // so also require a live Bluetooth audio link.
    val demo = remember { DemoMode.on(context) }
    val link by vm.link.collectAsState()
    val linkUp = remember(battery, resumes, link) { demo || audioLinkUp(context) }
    val connected = (battery.left != null || battery.right != null) && linkUp
    Column(verticalArrangement = Arrangement.spacedBy(GroupGap)) {
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            val windHero by vm.autoWind.collectAsState()
            SolarHero(
                windNow = windHero.enabled && windHero.windNow,
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
            androidx.compose.animation.AnimatedContent(
                targetState = connected,
                transitionSpec = {
                    (fadeIn(androidx.compose.animation.core.tween(260, delayMillis = 80)))
                        .togetherWith(fadeOut(androidx.compose.animation.core.tween(160)))
                        .using(androidx.compose.animation.SizeTransform(clip = false))
                },
                label = "battery"
            ) { on ->
                if (on) BatteryCard(battery, busy) else LastReading(battery)
            }
        }
        KeepRunningCard(resumes)
        AnimatedVisibility(
            visible = connected,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(GroupGap)) {
                NoiseControl(battery, vm)
                Group(
                    "Quick settings",
                    rows = listOf(
                        { ToggleRow(Icons.Rounded.AutoAwesome, "DSEE Extreme", "Restores detail in compressed music", settings.dseeOn, vm::setDsee) },
                        { ToggleRow(Icons.Rounded.RecordVoiceOver, "Speak-to-Chat", "Talk to pause music", settings.speakToChat, vm::setSpeakToChat) },
                        { ToggleRow(Icons.Rounded.Sensors, "Pause when removed", "Resumes when you put them back", settings.pauseWhenRemoved, vm::setPauseWhenRemoved) }
                    )
                )
            }
        }
        AnimatedVisibility(
            visible = !connected,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            LastSeenCard()
        }
    }
}

/** Left, right and case as rings around the rendered parts. */
@Composable
private fun BatteryCard(battery: BatteryState, busy: Boolean) {
    val context = LocalContext.current
    val outlook = remember(battery.updatedAt) {
        BatteryInsights.outlook(BatteryHistory.load(context, System.currentTimeMillis() - 7L * 86_400_000), battery)
    }
    Column {
        SunCard {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 24.dp)) {
                BatteryRing("Left", battery.left?.takeIf { it in 1..100 }, battery.leftCharging, R.drawable.bud_left, Modifier.weight(1f))
                BatteryRing("Right", battery.right?.takeIf { it in 1..100 }, battery.rightCharging, R.drawable.bud_right, Modifier.weight(1f))
                BatteryRing("Case", battery.case?.takeIf { it in 1..100 }, battery.caseCharging, R.drawable.bud_case, Modifier.weight(1f))
            }
        }
        Footer(
            when {
                busy -> "Checking your earbuds…"
                battery.updatedAt == 0L -> "Pull down to read your earbuds."
                outlook.caseNeedsCharge -> "Case is low. Charge it tonight."
                outlook.minutesLeft != null -> "About ${BatteryInsights.formatMinutes(outlook.minutesLeft)} of listening left. Updated ${relative(battery.updatedAt)}."
                else -> "Updated ${relative(battery.updatedAt)}."
            }
        )
    }
}

@Composable
private fun BatteryRing(label: String, level: Int?, charging: Boolean, art: Int, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val low = level != null && level <= 20 && !charging
    // Rings fill from empty and the number counts up with them; later readings glide from the old value.
    val fill = remember { androidx.compose.animation.core.Animatable(0f) }
    val pop = remember { androidx.compose.animation.core.Animatable(1f) }
    var seen by remember { mutableStateOf(false) }
    LaunchedEffect(level) {
        if (seen && level != null) {
            launch { pop.snapTo(1.12f); pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 300f)) }
        }
        seen = true
        fill.animateTo((level ?: 0) / 100f, androidx.compose.animation.core.tween(900, easing = androidx.compose.animation.core.FastOutSlowInEasing))
    }
    val sweep = fill.value
    // Charging gently breathes; a low bud doesn't, so the red reads as a calm warning.
    val breathe = if (charging) {
        rememberInfiniteTransition(label = "charge").animateFloat(
            0.45f, 1f,
            infiniteRepeatable(androidx.compose.animation.core.tween(1100, easing = androidx.compose.animation.core.FastOutSlowInEasing), RepeatMode.Reverse),
            label = "breathe"
        ).value
    } else 1f
    val ring = (if (low) colors.error else colors.primary).copy(alpha = breathe)
    val track = colors.surfaceContainerHighest
    val description = when {
        level == null -> "$label battery unknown"
        charging -> "$label battery $level percent, charging"
        else -> "$label battery $level percent"
    }
    Column(
        modifier.semantics(mergeDescendants = true) { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 7.dp.toPx()
                val topLeft = Offset(stroke / 2, stroke / 2)
                val arc = Size(size.width - stroke, size.height - stroke)
                drawArc(track, 0f, 360f, false, topLeft, arc, style = Stroke(stroke))
                if (sweep > 0f) {
                    drawArc(ring, -90f, 360f * sweep, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
                }
            }
            Image(
                painter = painterResource(art),
                contentDescription = null,
                alpha = if (level == null) 0.4f else 1f,
                modifier = Modifier.size(52.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value }
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value }) {
            Text(
                if (level == null) "--" else (sweep * 100).roundToInt().toString(),
                style = MaterialTheme.typography.titleLarge,
                color = if (low) colors.error else colors.onSurface
            )
            if (level != null) {
                Text("%", style = MaterialTheme.typography.titleSmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(bottom = 2.dp, start = 1.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (charging) {
                Icon(Icons.Rounded.Bolt, contentDescription = null, tint = colors.primary.copy(alpha = breathe), modifier = Modifier.size(16.dp))
            }
            Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
    }
}

/** While the earbuds are away: the last known levels as slim bars. */
@Composable
private fun LastReading(battery: BatteryState) {
    val context = LocalContext.current
    val last = remember(battery.updatedAt) {
        val logged = BatteryHistory.load(context, System.currentTimeMillis() - 14L * 86_400_000).lastOrNull()
        val saved = if (battery.updatedAt > 0 && (battery.left != null || battery.right != null))
            BatterySample(battery.updatedAt, battery.left, battery.right, battery.case, false, false, false) else null
        listOfNotNull(logged, saved).maxByOrNull { it.time }
    }
    if (last == null) return
    Column {
        SectionHeader("Last reading")
        SunCard {
            Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                LevelBar("Left", last.left, Modifier.weight(1f))
                LevelBar("Right", last.right, Modifier.weight(1f))
                LevelBar("Case", last.case, Modifier.weight(1f))
            }
        }
        Footer("Updated ${relative(last.time)}.")
    }
}

@Composable
private fun LevelBar(label: String, level: Int?, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val value = level?.takeIf { it in 1..100 }
    Column(
        modifier.semantics(mergeDescendants = true) {
            contentDescription = if (value == null) "$label unknown" else "$label $value percent"
        },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            Text(value?.let { "$it%" } ?: "--", style = MaterialTheme.typography.labelLarge)
        }
        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(colors.surfaceContainerHighest)) {
            Box(Modifier.fillMaxWidth((value ?: 0) / 100f).fillMaxHeight().clip(CircleShape).background(colors.outline))
        }
    }
}

/**
 * Anti-lost: where the earbuds were when they disconnected. Opt-in, saved only on this phone.
 * Shows an invitation until turned on (or dismissed), then the last spot with map buttons.
 */
@Composable
private fun LastSeenCard() {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    var tick by remember { mutableIntStateOf(0) }
    @Suppress("DEPRECATION")
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) tick++
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val enabled = remember(tick) { LastSeenStore.enabled(context) }
    val hasPermission = remember(tick) { LastSeenStore.hasLocationPermission(context) }
    val hasBackground = remember(tick) { LastSeenStore.hasBackgroundPermission(context) }
    val dismissed = remember(tick) { LastSeenStore.promptDismissed(context) }
    val spot = remember(tick) { LastSeenStore.load(context) }

    val backgroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val foregroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (LastSeenStore.hasLocationPermission(context)) LastSeenStore.setEnabled(context, true)
        tick++
    }
    fun turnOn() = foregroundLauncher.launch(
        arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION)
    )

    if (!(enabled && hasPermission)) {
        if (dismissed && !enabled) return
        Column {
            SectionHeader("Last seen")
            SunCard {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RowIcon(Icons.Rounded.LocationOn)
                        Spacer(Modifier.width(16.dp))
                        Text("Remember where you left them", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        if (enabled) "Location is off for SunPods, so the last spot can't be saved."
                        else "SunPods can save the spot where your earbuds disconnect, so you know where to look. It stays on this phone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                        Button(onClick = ::turnOn) { Text("Turn on") }
                        if (!enabled) {
                            TextButton(onClick = { LastSeenStore.dismissPrompt(context); tick++ }) { Text("Not now") }
                        }
                    }
                }
            }
        }
        return
    }

    val place by produceState<String?>(null, spot) {
        value = spot?.let { s ->
            withContext(Dispatchers.IO) {
                runCatching {
                    if (!android.location.Geocoder.isPresent()) return@runCatching null
                    @Suppress("DEPRECATION")
                    android.location.Geocoder(context).getFromLocation(s.lat, s.lng, 1)?.firstOrNull()?.let { a ->
                        val street = listOfNotNull(a.subThoroughfare, a.thoroughfare).joinToString(" ").ifBlank { null }
                        listOfNotNull(street ?: a.featureName, a.locality).distinct().joinToString(", ").ifBlank { null }
                    }
                }.getOrNull()
            }
        }
    }
    val findHub = remember { context.packageManager.getLaunchIntentForPackage("com.google.android.apps.adm") }

    Column {
        SectionHeader("Last seen")
        SunCard {
            if (spot == null) {
                Text(
                    "The spot will be saved the next time your earbuds disconnect.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp)
                )
            } else {
                val openMaps = {
                    val uri = android.net.Uri.parse("geo:${spot.lat},${spot.lng}?q=${spot.lat},${spot.lng}(${android.net.Uri.encode("Earbuds last seen")})")
                    runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri)) }
                    Unit
                }
                val directions = {
                    val nav = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("google.navigation:q=${spot.lat},${spot.lng}&mode=w"))
                    if (runCatching { context.startActivity(nav) }.isFailure) openMaps()
                }
                LastSeenMap(
                    Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clickable(onClickLabel = "Open in Maps", onClick = openMaps)
                )
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        place ?: "%.5f, %.5f".format(java.util.Locale.ROOT, spot.lat, spot.lng),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        buildString {
                            append(if (spot.atDisconnect) "Disconnected here " else "Last connected here ")
                            append(relative(spot.seenUntil))
                            if (spot.accuracyM > 0f) append(", within ${spot.accuracyM.roundToInt()} m")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 16.dp)) {
                        Button(onClick = directions, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.Directions, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Directions")
                        }
                        FilledTonalButton(onClick = openMaps, modifier = Modifier.weight(1f)) { Text("Open in Maps") }
                    }
                    if (findHub != null) {
                        TextButton(
                            onClick = { runCatching { context.startActivity(findHub) } },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) { Text("Find with Find Hub") }
                    }
                }
            }
            if (!hasBackground) {
                RowDivider(inset = 20.dp)
                Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp)) {
                    Text(
                        "Allow location all the time so the spot is saved even when SunPods is closed.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                    TextButton(
                        onClick = { backgroundLauncher.launch(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION) },
                        contentPadding = PaddingValues(horizontal = 0.dp)
                    ) { Text("Allow all the time") }
                }
            }
        }
        Footer("Saved only on this phone. SunPods has no internet access.")
    }
}

/** Decorative map tile with a pin. The real place is the text under it; tapping opens Maps. */
@Composable
private fun LastSeenMap(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val light = colors.isLight
    val land = colors.surfaceContainerHighest
    val block = colors.outlineVariant.copy(alpha = 0.45f)
    val road = if (light) Color.White else Color.White.copy(alpha = 0.10f)
    val water = if (light) Color(0xFFC6DFF0) else Color(0xFF233646)
    val park = if (light) Color(0xFFD6E8CC) else Color(0xFF26352A)
    val pin = colors.primary
    val glyph = colors.onPrimary
    val ring = cardColor
    val ping by rememberInfiniteTransition(label = "ping").animateFloat(
        0f, 1f, infiniteRepeatable(androidx.compose.animation.core.tween(2200, easing = androidx.compose.animation.core.LinearOutSlowInEasing)), label = "ping"
    )
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val r = 8.dp.toPx()
        drawRect(land)
        drawPath(
            Path().apply {
                moveTo(0f, h * 0.80f)
                cubicTo(w * 0.22f, h * 0.70f, w * 0.42f, h * 0.92f, w * 0.62f, h * 0.82f)
                cubicTo(w * 0.80f, h * 0.73f, w * 0.90f, h * 0.76f, w, h * 0.72f)
                lineTo(w, h); lineTo(0f, h); close()
            },
            water
        )
        drawRoundRect(park, Offset(w * 0.66f, h * 0.06f), Size(w * 0.27f, h * 0.30f), CornerRadius(r))
        drawRoundRect(block, Offset(w * 0.05f, h * 0.10f), Size(w * 0.18f, h * 0.26f), CornerRadius(r))
        drawRoundRect(block, Offset(w * 0.30f, h * 0.12f), Size(w * 0.16f, h * 0.24f), CornerRadius(r))
        drawRoundRect(block, Offset(w * 0.60f, h * 0.56f), Size(w * 0.14f, h * 0.12f), CornerRadius(r))
        drawLine(road, Offset(0f, h * 0.48f), Offset(w, h * 0.48f), 12.dp.toPx(), StrokeCap.Round)
        drawLine(road, Offset(w * 0.53f, -10f), Offset(w * 0.53f, h * 0.74f), 10.dp.toPx(), StrokeCap.Round)
        drawLine(road, Offset(w * 0.12f, -10f), Offset(w * 0.27f, h * 0.70f), 7.dp.toPx(), StrokeCap.Round)
        drawLine(road, Offset(w * 0.53f, h * 0.24f), Offset(w, h * 0.24f), 7.dp.toPx(), StrokeCap.Round)
        val c = Offset(w * 0.53f, h * 0.48f)
        drawCircle(pin.copy(alpha = 0.16f), 36.dp.toPx(), c)
        drawCircle(pin.copy(alpha = 0.35f * (1f - ping)), (20 + 34 * ping).dp.toPx(), c, style = Stroke(2.dp.toPx()))
        drawCircle(ring, 20.dp.toPx(), c)
        drawCircle(pin, 17.dp.toPx(), c)
        val u = 1.dp.toPx()
        for (side in listOf(-1f, 1f)) {
            val head = Offset(c.x + side * 4.6f * u, c.y - 4f * u)
            drawCircle(glyph, 3.8f * u, head)
            val stemX = if (side < 0) head.x + 0.2f * u else head.x - 2.2f * u
            drawRoundRect(glyph, Offset(stemX, head.y + 1.5f * u), Size(2f * u, 7.5f * u), CornerRadius(u))
        }
    }
}

@Composable
private fun NoiseControl(battery: BatteryState, vm: SunpodsViewModel) {
    var level by remember(battery.ambientLevel) { mutableFloatStateOf(battery.ambientLevel.toFloat()) }
    Column {
        SectionHeader("Noise control")
        SunCard {
            Row(
                Modifier.fillMaxWidth().padding(8.dp).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ModeButton(Icons.Rounded.NoiseAware, "Noise cancelling", battery.listeningMode == ListeningMode.NOISE_CANCELLING, Modifier.weight(1f)) {
                    vm.setListeningMode(ListeningMode.NOISE_CANCELLING, level.roundToInt(), battery.focusOnVoice)
                }
                ModeButton(Icons.Rounded.Hearing, "Ambient", battery.listeningMode == ListeningMode.AMBIENT, Modifier.weight(1f)) {
                    vm.setListeningMode(ListeningMode.AMBIENT, level.roundToInt(), battery.focusOnVoice)
                }
                ModeButton(Icons.Rounded.NoiseControlOff, "Off", battery.listeningMode == ListeningMode.OFF, Modifier.weight(1f)) {
                    vm.setListeningMode(ListeningMode.OFF, level.roundToInt(), battery.focusOnVoice)
                }
            }
            AnimatedVisibility(
                visible = battery.listeningMode == ListeningMode.AMBIENT,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    RowDivider(inset = 20.dp)
                    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
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
                    RowDivider(inset = 20.dp)
                    ToggleRow(null, "Focus on voice", "Let voices through more clearly", battery.focusOnVoice) {
                        vm.setListeningMode(ListeningMode.AMBIENT, level.roundToInt(), it)
                    }
                }
            }
            val wind by vm.autoWind.collectAsState()
            RowDivider(inset = 20.dp)
            if (wind.supported != false) {
                ToggleRow(
                    Icons.Rounded.Air, "Automatic wind reduction",
                    when {
                        !wind.enabled -> "Off. Noise cancelling stays on full even in wind."
                        wind.windNow -> "Wind detected. The earbuds have switched to wind reduction."
                        wind.supported == null -> "Checks your earbuds on the next update"
                        else -> "The earbuds switch to wind reduction by themselves when it gets windy, then back"
                    },
                    wind.enabled
                ) { vm.setAutoWind(it) }
            } else {
                val windy by vm.windy.collectAsState()
                ToggleRow(
                    Icons.Rounded.Air, "Windy",
                    if (windy) "Noise control is off so wind isn't amplified. Turn off to go back." else "Your earbuds can't detect wind themselves. Turn on to stop wind roar.",
                    windy
                ) { vm.setWindy(it) }
            }
        }
        val context = LocalContext.current
        val automation = remember(battery.listeningMode, battery.updatedAt) { AutomationStore.active(context) }
        Footer(automation?.let { "Set by an automation: $it. Pick a mode to take over." } ?: "You can also tap the earbuds above to switch.")
    }
}

/** One option of the mode switch. The chosen one fills and its corners round out (expressive shape change). */
@Composable
private fun ModeButton(icon: ImageVector, label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val corner by animateDpAsState(if (selected) 28.dp else 16.dp, spring(dampingRatio = 0.6f, stiffness = 400f), label = "corner")
    val container by animateColorAsState(if (selected) colors.primaryContainer else Color.Transparent, label = "container")
    val iconTint by animateColorAsState(if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant, label = "icon")
    val text by animateColorAsState(if (selected) colors.onPrimaryContainer else colors.onSurface, label = "text")
    val hop = remember { androidx.compose.animation.core.Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(selected) {
        if (selected && !first) {
            hop.snapTo(0.7f)
            hop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 500f))
        }
        first = false
    }
    Column(
        modifier = modifier
            .height(92.dp)
            .clip(RoundedCornerShape(corner))
            .background(container)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp).graphicsLayer { scaleX = hop.value; scaleY = hop.value })
        Text(label, style = MaterialTheme.typography.labelLarge, color = text, textAlign = TextAlign.Center, maxLines = 2)
    }
}

private fun relative(time: Long): String =
    DateUtils.getRelativeTimeSpanString(time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString().lowercase()

// ======================================================================
// Sound
// ======================================================================

private val bandLabels = listOf("Bass", "400", "1k", "2.5k", "6.3k", "16k")

@Composable
private fun SoundScreen(settings: DeviceSettings, vm: SunpodsViewModel) {
    EqPanel(settings, vm)

    val presets = EqPreset.entries.filter { it != EqPreset.MANUAL || settings.eqPreset == EqPreset.MANUAL }
    Column {
        SectionHeader("Presets")
        SunCard(Modifier.selectableGroup()) {
            presets.forEachIndexed { i, preset ->
                if (i > 0) RowDivider(inset = 20.dp)
                val selected = settings.eqPreset == preset
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(selected = selected, role = Role.RadioButton) { vm.setEqPreset(preset) }
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(preset.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    if (preset.isCustomisable) {
                        Text("Editable", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                    }
                    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                        if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        Footer("Custom 1 and 2 keep your own shape. Tap Customise to start editing.")
    }

    Group(
        "Audio quality",
        footer = settings.codec?.let { "Playing over $it right now. Changing priority makes the earbuds reconnect briefly." },
        rows = listOf(
            { ToggleRow(Icons.Rounded.AutoAwesome, "DSEE Extreme", "AI upscaling for compressed music. Uses a little more battery.", settings.dseeOn, vm::setDsee) },
            {
                var open by remember { mutableStateOf(false) }
                NavRow(Icons.Rounded.BluetoothAudio, "Connection priority", settings.priority?.label ?: "Not read yet") { open = true }
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

/** The sound as a live curve, with sliders under it when the preset can be edited. */
@Composable
private fun EqPanel(settings: DeviceSettings, vm: SunpodsViewModel) {
    val colors = MaterialTheme.colorScheme
    val source = listOf(settings.clearBass ?: 0) + (settings.bands ?: List(5) { 0 })
    var values by remember(settings.clearBass, settings.bands) { mutableStateOf(source) }
    val editable = settings.eqPreset?.isCustomisable == true
    val commit = { vm.setEqBands(values[0], values.drop(1)) }

    Column {
        SectionHeader("Equaliser")
        SunCard {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Text(settings.eqPreset?.label ?: "Not read yet", style = MaterialTheme.typography.headlineSmall)
                Text(
                    if (editable) "Drag the sliders to shape your sound." else "A fixed Sony preset.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )
                Spacer(Modifier.height(20.dp))
                EqCurve(values, Modifier.fillMaxWidth().height(140.dp))
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    bandLabels.forEachIndexed { i, label ->
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (i == 0) colors.primary else colors.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                AnimatedVisibility(editable, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Column(Modifier.padding(top = 16.dp)) {
                        values.forEachIndexed { i, v ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (i == 0) "Clear Bass" else "${bandLabels[i]} Hz",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.width(84.dp)
                                )
                                Slider(
                                    value = v.toFloat(),
                                    onValueChange = { new -> values = values.toMutableList().also { it[i] = new.roundToInt() } },
                                    onValueChangeFinished = commit,
                                    valueRange = -10f..10f,
                                    steps = 19,
                                    colors = quietSlider(),
                                    modifier = Modifier.weight(1f).semantics { contentDescription = "${bandLabels[i]} band" }
                                )
                                Text(
                                    signed(v),
                                    style = MaterialTheme.typography.labelLarge,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.width(36.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (editable) {
                        TextButton(onClick = {
                            values = List(6) { 0 }
                            vm.setEqBands(0, List(5) { 0 })
                        }) { Text("Flatten") }
                    } else {
                        FilledTonalButton(onClick = { vm.setEqPreset(EqPreset.CUSTOM_1) }) {
                            Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Customise")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EqCurve(values: List<Int>, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val line = colors.primary
    val fill = colors.primary.copy(alpha = 0.12f)
    val grid = colors.outlineVariant
    val dot = cardColor
    val animated = values.mapIndexed { i, v ->
        animateFloatAsState(v.toFloat(), spring(stiffness = 300f), label = "band$i").value
    }
    Canvas(modifier.semantics { contentDescription = "Equaliser curve" }) {
        val w = size.width
        val h = size.height
        val step = w / animated.size
        fun x(i: Int) = step * i + step / 2
        fun y(v: Float) = h / 2 - v / 10f * (h / 2 - 8.dp.toPx())
        drawLine(grid, Offset(0f, h / 2), Offset(w, h / 2), 1.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
        val pts = listOf(Offset(0f, y(animated.first()))) + animated.indices.map { Offset(x(it), y(animated[it])) } + Offset(w, y(animated.last()))
        val path = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 0 until pts.size - 1) {
                val p0 = pts.getOrElse(i - 1) { pts[i] }
                val p1 = pts[i]
                val p2 = pts[i + 1]
                val p3 = pts.getOrElse(i + 2) { p2 }
                cubicTo(
                    p1.x + (p2.x - p0.x) / 6, p1.y + (p2.y - p0.y) / 6,
                    p2.x - (p3.x - p1.x) / 6, p2.y - (p3.y - p1.y) / 6,
                    p2.x, p2.y
                )
            }
        }
        val area = Path().apply { addPath(path); lineTo(w, h); lineTo(0f, h); close() }
        drawPath(area, fill)
        drawPath(path, line, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        animated.indices.forEach {
            drawCircle(dot, 6.dp.toPx(), Offset(x(it), y(animated[it])))
            drawCircle(line, 4.dp.toPx(), Offset(x(it), y(animated[it])))
        }
    }
}

// ======================================================================
// Controls
// ======================================================================

@Composable
private fun ControlsScreen(settings: DeviceSettings, vm: SunpodsViewModel) {
    val sensitivity = settings.speakToChatSensitivity ?: SpeakToChatSensitivity.AUTO
    val timeout = settings.speakToChatTimeout ?: SpeakToChatTimeout.STANDARD
    val left = settings.touchLeft
    val right = settings.touchRight

    Column {
        SectionHeader("Touch controls")
        SunCard {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TouchSide("Left", R.drawable.bud_left, left, Modifier.weight(1f)) { picked -> right?.let { vm.setTouch(picked, it) } }
                TouchSide("Right", R.drawable.bud_right, right, Modifier.weight(1f)) { picked -> left?.let { vm.setTouch(it, picked) } }
            }
            if (left == TouchFunction.AMBIENT_SOUND_CONTROL || right == TouchFunction.AMBIENT_SOUND_CONTROL) {
                RowDivider(inset = 20.dp)
                var open by remember { mutableStateOf(false) }
                NavRow(Icons.Rounded.NoiseAware, "A tap cycles through", settings.noiseCycle?.label ?: "Not read yet") { open = true }
                if (open) {
                    ChoiceSheet("A tap cycles through", NoiseCycle.entries.toList(), settings.noiseCycle,
                        label = { it.label }, onDismiss = { open = false }, onPick = vm::setNoiseCycle)
                }
            }
        }
        Footer("Tap an earbud to choose what its touch sensor does.")
    }

    AutomationsCard()

    val chatRows = buildList<@Composable () -> Unit> {
        add { ToggleRow(Icons.Rounded.RecordVoiceOver, "Speak-to-Chat", "Start talking and music pauses, ambient sound comes in", settings.speakToChat, vm::setSpeakToChat) }
        if (settings.speakToChat == true) {
            add {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Text("Voice detection", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(12.dp))
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
                NavRow(Icons.Rounded.Timer, "End chat mode", settings.speakToChatTimeout?.label ?: "Not read yet") { open = true }
                if (open) {
                    ChoiceSheet("End chat mode after", SpeakToChatTimeout.entries.toList(), settings.speakToChatTimeout,
                        label = { it.label }, onDismiss = { open = false }) { vm.setSpeakToChatConfig(sensitivity, it) }
                }
            }
        }
    }
    Group("Speak-to-Chat", rows = chatRows)

    Group(
        "Wearing detection",
        rows = listOf(
            { ToggleRow(Icons.Rounded.Sensors, "Pause when removed", "Music pauses when you take an earbud out", settings.pauseWhenRemoved, vm::setPauseWhenRemoved) },
            { ToggleRow(Icons.Rounded.PowerSettingsNew, "Power off when removed", "Saves battery when both earbuds are out", settings.autoOffWhenRemoved, vm::setAutoOff) }
        )
    )

    // Only what these earbuds say they support, named the way they name it.
    val general = settings.general.orEmpty()
    if (general.isNotEmpty()) {
        Group(
            "Connection and calls",
            rows = general.map { g ->
                {
                    ToggleRow(generalIcon(g.key), g.title, g.subtitle, g.on) { on -> vm.setGeneral(g.slot, on) }
                }
            }
        )
    }

    val live = vm.battery.collectAsState().value
    DevicesCard(connected = live.left != null || live.right != null)
}

private fun generalIcon(key: String) = when {
    key.startsWith("MULTIPOINT") -> Icons.Rounded.Devices
    key.startsWith("SIDETONE") -> Icons.Rounded.Mic
    key.startsWith("TWS_ONE_SIDE") -> Icons.Rounded.Earbuds
    else -> Icons.Rounded.Tune
}

/** One earbud: its render, its side and what a touch does. Tap to change. */
@Composable
private fun TouchSide(side: String, art: Int, current: TouchFunction?, modifier: Modifier = Modifier, onPick: (TouchFunction) -> Unit) {
    val colors = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(enabled = current != null, onClickLabel = "Change $side earbud touch") { open = true }
            .padding(vertical = 16.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(painterResource(art), contentDescription = null, modifier = Modifier.size(width = 120.dp, height = 100.dp))
        Spacer(Modifier.height(12.dp))
        Text(side, style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Surface(color = colors.secondaryContainer, shape = CircleShape) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(current?.label ?: "Not read yet", style = MaterialTheme.typography.labelLarge, color = colors.onSecondaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = colors.onSecondaryContainer, modifier = Modifier.size(18.dp))
            }
        }
        if (current != null) {
            Spacer(Modifier.height(8.dp))
            Text(touchHint(current), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, textAlign = TextAlign.Center, minLines = 2)
        }
    }
    if (open) {
        ChoiceSheet("$side earbud", TouchFunction.entries.toList(), current,
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
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            painter = painterResource(R.drawable.buds_pair),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(200.dp)
                .drawBehind {
                    val r = size.height / 2
                    scale(scaleX = size.width / size.height * 0.9f, scaleY = 1f, pivot = center) {
                        drawCircle(
                            androidx.compose.ui.graphics.Brush.radialGradient(listOf(colors.primary.copy(alpha = 0.22f), Color.Transparent), center, r),
                            r, center
                        )
                    }
                }
        )
        Spacer(Modifier.height(8.dp))
        Text(settings.modelName ?: "WF-1000XM5", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text("Sony wireless earbuds", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
    }

    Group(
        "About",
        inset = 20.dp,
        rows = listOf(
            { InfoRow("Firmware", settings.firmware ?: "Not read yet") },
            { InfoRow("Codec", settings.codec ?: "Not read yet") },
            { InfoRow("Battery read", if (battery.updatedAt == 0L) "Never" else relative(battery.updatedAt)) },
            { InfoRow("Settings read", if (settings.readAt == 0L) "Never" else relative(settings.readAt)) }
        )
    )

    LastSeenSettings()

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

/** Device tab: on/off for "Last seen" and a way to forget the saved spot. */
@Composable
private fun LastSeenSettings() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(LastSeenStore.enabled(context) && LastSeenStore.hasLocationPermission(context)) }
    var hasSpot by remember { mutableStateOf(LastSeenStore.load(context) != null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (LastSeenStore.hasLocationPermission(context)) {
            LastSeenStore.setEnabled(context, true); enabled = true
        }
    }
    Group(
        "Find my earbuds",
        rows = buildList {
            add {
                ToggleRow(
                    Icons.Rounded.LocationOn, "Remember where you left them",
                    "Saves the spot where they disconnect, on this phone only", enabled
                ) { on ->
                    if (on && !LastSeenStore.hasLocationPermission(context)) {
                        launcher.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION))
                    } else {
                        LastSeenStore.setEnabled(context, on); enabled = on
                        if (!on) hasSpot = false
                    }
                }
            }
            if (enabled && hasSpot) {
                add { NavRow(Icons.Rounded.LocationOff, "Forget saved location", null) { LastSeenStore.clear(context); hasSpot = false } }
            }
        }
    )
}

/**
 * Debug builds only: shows sample levels and settings so the connected layout can be checked on an
 * emulator, which has no Bluetooth audio. Off unless the "demo_connected" flag is written with run-as.
 */
internal object DemoMode {
    fun on(context: android.content.Context): Boolean =
        (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0 &&
            context.getSharedPreferences("sunpods_ui", android.content.Context.MODE_PRIVATE).getBoolean("demo_connected", false)

    val battery = BatteryState(
        left = 82, right = 76, case = 15, caseCharging = true,
        listeningMode = ListeningMode.AMBIENT, ambientLevel = 14,
        updatedAt = System.currentTimeMillis() - 120_000
    )
    val settings = DeviceSettings(
        modelName = "WF-1000XM5", firmware = "2.1.0", codec = "LDAC", eqPresetCode = 0x10,
        clearBass = 3, bands = listOf(3, 0, 1, 3, 2), dseeOn = true, speakToChat = false, pauseWhenRemoved = true,
        autoOffWhenRemoved = false, touchLeft = TouchFunction.AMBIENT_SOUND_CONTROL, touchRight = TouchFunction.PLAYBACK_CONTROL,
        priority = ConnectionPriority.entries.first(), noiseCycle = NoiseCycle.entries.first(), readAt = System.currentTimeMillis() - 120_000
    )
}

object SunpodsInfo {
    const val VERSION = "2.15.1"
}
