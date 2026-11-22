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
import kotlinx.coroutines.delay
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.SystemUpdate
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
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Widgets
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
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh battery and settings")
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
                            Tab.DEVICE -> DeviceScreen(battery, settings, onShareDiagnostics, onOpenBluetoothSettings, dynamicColor, onDynamicColorChange, vm.liveAt.collectAsState().value)
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
internal fun ToggleRow(icon: ImageVector?, title: String, subtitle: String, checked: Boolean?, onChange: (Boolean) -> Unit) =
    ToggleRow(icon, title, subtitle, checked, null, onChange)

/** [offNote]: the setting can't be used right now; shown greyed out and off, with this reason. */
@Composable
internal fun ToggleRow(icon: ImageVector?, title: String, subtitle: String, checked: Boolean?, offNote: String?, onChange: (Boolean) -> Unit) {
    val known = checked != null
    val off = offNote != null
    val on = checked == true && !off
    val faded = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    ListItem(
        modifier = Modifier.toggleable(
            value = on,
            enabled = known && !off,
            role = Role.Switch,
            onValueChange = onChange
        ),
        headlineContent = { Text(title, color = if (off) faded else androidx.compose.ui.graphics.Color.Unspecified) },
        supportingContent = { Text(offNote ?: if (known) subtitle else "Pull down to refresh") },
        leadingContent = icon?.let { { RowIcon(it, active = on) } },
        trailingContent = { Switch(checked = on, enabled = known && !off, onCheckedChange = null) },
        colors = TransparentItem
    )
}

internal const val LE_OFF_NOTE = "Off while LE Audio is on"

/** True while the earbuds are set to LE Audio or connected over it; some features are off then. */
@Composable
internal fun rememberLeOn(settings: DeviceSettings): Boolean {
    val context = LocalContext.current
    return remember(settings.readAt, settings.leOn) { settings.leOn == true || LeAudio.anyConnected(context) }
}

/** [LE_OFF_NOTE] while LE Audio is on, else null. */
@Composable
internal fun leOffNote(settings: DeviceSettings): String? = if (rememberLeOn(settings)) LE_OFF_NOTE else null

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
            Text("Allow Nearby Devices", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            Text(
                "SunPods needs this to connect to your earbuds over Bluetooth.",
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
            androidx.compose.animation.AnimatedContent(
                targetState = connected,
                transitionSpec = {
                    (fadeIn(androidx.compose.animation.core.tween(260, delayMillis = 80)))
                        .togetherWith(fadeOut(androidx.compose.animation.core.tween(160)))
                        .using(androidx.compose.animation.SizeTransform(clip = false))
                },
                label = "battery"
            ) { on ->
                if (on) BatteryCard(battery, busy, vm.liveAt.collectAsState().value) else LastReading(battery)
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
                    "Quick Settings",
                    rows = listOf(
                        { ToggleRow(Icons.Rounded.AutoAwesome, "DSEE Extreme", "Restores detail in compressed music", settings.dseeOn, leOffNote(settings), vm::setDsee) },
                        { ToggleRow(Icons.Rounded.RecordVoiceOver, "Speak-to-Chat", "Pauses music when you start talking", settings.speakToChat, vm::setSpeakToChat) },
                        { ToggleRow(Icons.Rounded.Sensors, "Pause When Removed", "Pauses music when you take an earbud out", settings.pauseWhenRemoved, vm::setPauseWhenRemoved) }
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
private fun BatteryCard(battery: BatteryState, busy: Boolean, liveAt: Long = 0L) {
    val heard = maxOf(battery.updatedAt, liveAt)
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
                battery.updatedAt == 0L -> "Pull down to refresh."
                outlook.caseNeedsCharge -> "Case battery is low. Charge it tonight."
                else -> "Updated ${relative(heard)}."
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
                modifier = Modifier.size(if (art == R.drawable.bud_case) 40.dp else 52.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value }
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value }) {
            if (level == null) {
                Text(if (label == "Case") "Unknown" else "In Case", style = MaterialTheme.typography.titleSmall, color = colors.onSurfaceVariant)
            } else {
                Text(
                    (sweep * 100).roundToInt().toString(),
                    style = MaterialTheme.typography.titleLarge,
                    color = if (low) colors.error else colors.onSurface
                )
            }
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
        SectionHeader("Last Reading")
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
            Text(value?.let { "$it%" } ?: "Unknown", style = MaterialTheme.typography.labelLarge)
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
            SectionHeader("Last Seen")
            SunCard {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RowIcon(Icons.Rounded.LocationOn)
                        Spacer(Modifier.width(16.dp))
                        Text("Turn On Last Seen", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        if (enabled) "Location access is off for SunPods, so Last Seen can't save where your earbuds disconnect."
                        else "SunPods can save where your earbuds disconnect, so you know where to look. The location stays on this phone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                        Button(onClick = ::turnOn) { Text("Turn On") }
                        if (!enabled) {
                            TextButton(onClick = { LastSeenStore.dismissPrompt(context); tick++ }) { Text("Not Now") }
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
        SectionHeader("Last Seen")
        SunCard {
            if (spot == null) {
                Text(
                    "SunPods saves the location the next time your earbuds disconnect.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp)
                )
            } else {
                val openMaps = {
                    val uri = android.net.Uri.parse("geo:${spot.lat},${spot.lng}?q=${spot.lat},${spot.lng}(${android.net.Uri.encode("Earbuds Last Seen")})")
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
                        "To save the location when SunPods is closed, allow location access all the time.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                    TextButton(
                        onClick = { backgroundLauncher.launch(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION) },
                        contentPadding = PaddingValues(horizontal = 0.dp)
                    ) { Text("Allow All the Time") }
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
        SectionHeader("Noise Control")
        SunCard {
            Row(
                Modifier.fillMaxWidth().padding(8.dp).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ModeButton(Icons.Rounded.NoiseAware, "Noise Cancelling", battery.listeningMode == ListeningMode.NOISE_CANCELLING, Modifier.weight(1f)) {
                    vm.setListeningMode(ListeningMode.NOISE_CANCELLING, level.roundToInt(), battery.focusOnVoice)
                }
                ModeButton(Icons.Rounded.Hearing, "Ambient Sound", battery.listeningMode == ListeningMode.AMBIENT, Modifier.weight(1f)) {
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
                            Text("Ambient Sound Level", style = MaterialTheme.typography.bodyLarge)
                            Text("${level.roundToInt()}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                        }
                        Slider(
                            value = level,
                            onValueChange = { level = it },
                            valueRange = 1f..20f,
                            steps = 18,
                            colors = quietSlider(),
                            onValueChangeFinished = { vm.setListeningMode(ListeningMode.AMBIENT, level.roundToInt(), battery.focusOnVoice) },
                            modifier = Modifier.semantics { contentDescription = "Ambient Sound Level" }
                        )
                    }
                    RowDivider(inset = 20.dp)
                    ToggleRow(null, "Focus on Voice", "Lets voices through more clearly", battery.focusOnVoice) {
                        vm.setListeningMode(ListeningMode.AMBIENT, level.roundToInt(), it)
                    }
                }
            }
        }
        val context = LocalContext.current
        val automation = remember(battery.listeningMode, battery.updatedAt) { AutomationStore.active(context) }
        Footer(automation?.let { "Set by an automation: $it. Choose a mode to override it." } ?: "You can also tap the earbuds to switch modes.")
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

/** A time shown on its own, such as a row value: "Just now", "5 minutes ago". */
private fun relativeValue(time: Long): String = relative(time).replaceFirstChar { it.uppercase() }

private fun relative(time: Long): String =
    if (System.currentTimeMillis() - time < 60_000) "just now"
    else DateUtils.getRelativeTimeSpanString(time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString().lowercase()

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
        Footer("Custom 1 and 2 save your own settings. Tap Customise to edit.")
    }

    Group(
        "Audio Quality",
        footer = settings.codec?.let { "Now playing over $it. Changing Connection Priority briefly reconnects your earbuds." },
        rows = listOf(
            { ToggleRow(Icons.Rounded.AutoAwesome, "DSEE Extreme", "Restores detail in compressed music. Uses a little more battery.", settings.dseeOn, leOffNote(settings), vm::setDsee) },
            {
                var open by remember { mutableStateOf(false) }
                NavRow(Icons.Rounded.BluetoothAudio, "Connection Priority", settings.priority?.label ?: "Connect to Check") { open = true }
                if (open) {
                    ChoiceSheet(
                        "Connection Priority",
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
                Text(settings.eqPreset?.label ?: "Connect to Check", style = MaterialTheme.typography.headlineSmall)
                Text(
                    if (editable) "Drag the sliders to shape your sound." else "A Sony preset. Tap Customise to edit.",
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
                        }) { Text("Reset to Flat") }
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
        SectionHeader("Touch Controls")
        SunCard {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val options = settings.touchOptions
                TouchSide("Left", R.drawable.bud_left, left, options?.get(0), Modifier.weight(1f)) { picked -> vm.setTouch(picked, null) }
                TouchSide("Right", R.drawable.bud_right, right, options?.get(1), Modifier.weight(1f)) { picked -> vm.setTouch(null, picked) }
            }
            if (left == TouchFunction.AMBIENT_SOUND_CONTROL || right == TouchFunction.AMBIENT_SOUND_CONTROL) {
                RowDivider(inset = 20.dp)
                var open by remember { mutableStateOf(false) }
                NavRow(Icons.Rounded.NoiseAware, "Tap Cycles Through", settings.noiseCycle?.label ?: "Connect to Check") { open = true }
                if (open) {
                    ChoiceSheet("Tap Cycles Through", NoiseCycle.entries.toList(), settings.noiseCycle,
                        label = { it.label }, onDismiss = { open = false }, onPick = vm::setNoiseCycle)
                }
            }
        }
        Footer("Tap an earbud to choose what its touch control does.")
    }

    AutomationsCard()

    val chatRows = buildList<@Composable () -> Unit> {
        add { ToggleRow(Icons.Rounded.RecordVoiceOver, "Speak-to-Chat", "Pauses music and lets sound in when you talk", settings.speakToChat, vm::setSpeakToChat) }
        if (settings.speakToChat == true) {
            add {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Text("Voice Detection", style = MaterialTheme.typography.bodyLarge)
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
                NavRow(Icons.Rounded.Timer, "End Chat Mode", settings.speakToChatTimeout?.label ?: "Connect to Check") { open = true }
                if (open) {
                    ChoiceSheet("End Chat Mode After", SpeakToChatTimeout.entries.toList(), settings.speakToChatTimeout,
                        label = { it.label }, onDismiss = { open = false }) { vm.setSpeakToChatConfig(sensitivity, it) }
                }
            }
        }
    }
    Group("Speak-to-Chat", rows = chatRows)

    Group(
        "Wearing Detection",
        rows = listOf(
            { ToggleRow(Icons.Rounded.Sensors, "Pause When Removed", "Music pauses when you take an earbud out", settings.pauseWhenRemoved, vm::setPauseWhenRemoved) },
            { ToggleRow(Icons.Rounded.PowerSettingsNew, "Power Off When Removed", "Turns off when both earbuds are out, to save battery", settings.autoOffWhenRemoved, vm::setAutoOff) }
        )
    )

    if (settings.promptsOn != null || settings.promptVolume != null) VoicePromptsGroup(settings, vm)

    CallsGroup(settings, vm)

    // Only what these earbuds say they support, named the way they name it.
    val general = settings.general.orEmpty()
    if (general.isNotEmpty()) {
        Group(
            "Connection and Calls",
            rows = general.map { g ->
                {
                    // Two devices at once needs Classic; the earbuds turn it off on LE Audio.
                    val offNote = if (g.key == "MULTIPOINT_SETTING") leOffNote(settings) else null
                    ToggleRow(generalIcon(g.key), g.title, g.subtitle, g.on, offNote) { on -> vm.setGeneral(g.slot, on) }
                }
            }
        )
    }

    val live = vm.battery.collectAsState().value
    DevicesCard(connected = live.left != null || live.right != null, leOn = rememberLeOn(settings))
}

private fun generalIcon(key: String) = when {
    key.startsWith("MULTIPOINT") -> Icons.Rounded.Devices
    key.startsWith("SIDETONE") -> Icons.Rounded.Mic
    key.startsWith("TWS_ONE_SIDE") -> Icons.Rounded.Earbuds
    else -> Icons.Rounded.Tune
}

/** One earbud: its render, its side and what a touch does. Tap to change. */
@Composable
private fun TouchSide(side: String, art: Int, current: TouchFunction?, options: List<TouchOption>?, modifier: Modifier = Modifier, onPick: (TouchFunction) -> Unit) {
    // What the earbuds list for this side, in their words; the full list only if they haven't said.
    fun hint(f: TouchFunction) = options?.firstOrNull { it.function == f }?.let(TouchCapability::describe) ?: touchHint(f)
    val colors = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClickLabel = "Change $side earbud touch control") { open = true }
            .padding(vertical = 16.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(painterResource(art), contentDescription = null, modifier = Modifier.size(width = 120.dp, height = 100.dp))
        Spacer(Modifier.height(12.dp))
        Text(side, style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Surface(color = colors.secondaryContainer, shape = CircleShape) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(current?.label ?: "Connect to Check", style = MaterialTheme.typography.labelLarge, color = colors.onSecondaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = colors.onSecondaryContainer, modifier = Modifier.size(18.dp))
            }
        }
        if (current != null) {
            Spacer(Modifier.height(8.dp))
            Text(hint(current), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, textAlign = TextAlign.Center, minLines = 2)
        }
    }
    if (open) {
        ChoiceSheet("$side Earbud", options?.map { it.function }
            // Before the earbuds have said: the options every model has. Volume Control is its own
            // option only on some models (not the XM5), so it waits for their list.
            ?: TouchFunction.entries.filter { it != TouchFunction.VOLUME_CONTROL || it == current }, current,
            label = { it.label }, detail = { hint(it) }, onDismiss = { open = false }, onPick = onPick)
    }
}

private fun touchHint(function: TouchFunction): String = when (function) {
    TouchFunction.AMBIENT_SOUND_CONTROL -> "Tap to switch noise control mode. Hold for Quick Attention."
    TouchFunction.PLAYBACK_CONTROL -> "Tap to play or pause. Double-tap for next, triple-tap for previous. Hold for voice assistant."
    TouchFunction.VOLUME_CONTROL -> "Tap to turn the volume up. Hold to turn it down."
    TouchFunction.NONE -> "Touch controls are off on this side."
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
    onDynamicColorChange: (Boolean) -> Unit,
    liveAt: Long = 0L
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
        Text("Sony wireless noise cancelling earbuds", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
    }

    Group(
        "About",
        inset = 20.dp,
        rows = listOf(
            { InfoRow("Firmware Version", settings.firmware ?: "Connect to Check") },
            { InfoRow("Audio Codec", settings.codec ?: "Connect to Check") },
            { InfoRow("Battery Last Updated", if (battery.updatedAt == 0L) "Never" else relativeValue(maxOf(battery.updatedAt, liveAt))) },
            { InfoRow("Settings Last Synced", if (settings.readAt == 0L) "Never" else relativeValue(settings.readAt)) }
        )
    )

    LastSeenSettings()

    Group(
        "Appearance",
        rows = listOf(
            {
                var picking by remember { mutableStateOf(false) }
                NavRow(Icons.Rounded.Widgets, "Add a Widget", "Choose from small, medium and large sizes") { picking = true }
                if (picking) WidgetPickerSheet(onDismiss = { picking = false })
            },
            {
                ToggleRow(
                    Icons.Rounded.Palette, "Use Wallpaper Colours",
                    "Matches the app's colours to your wallpaper", dynamicColor, onDynamicColorChange
                )
            }
        )
    )

    YourDataGroup()

    UpdatesGroup()

    Group(
        "Help",
        rows = listOf(
            { NavRow(Icons.Rounded.Bluetooth, "Bluetooth Settings", "Pair, reconnect or forget your earbuds", onOpenBluetoothSettings) },
            { NavRow(Icons.Rounded.BugReport, "Send Diagnostics", "Share a technical log to help solve problems", onShareDiagnostics) },
            {
                val context = LocalContext.current
                NavRow(Icons.Rounded.RestartAlt, "Run Setup Again", "Review permissions, pairing and optional features") {
                    OnboardingStore.setDone(context, false)
                    Backup.restart(context)
                }
            }
        ),
        footer = "SunPods works without an account. Its only use of the internet is the update check, which you can turn off. Your earbuds connect to one control app at a time, so close Sony Sound Connect while you use SunPods.\n\nVersion ${SunpodsInfo.VERSION}. Free and open source under the GPL-3.0."
    )
}

@Composable
private fun UpdatesGroup() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var on by remember { mutableStateOf(Updates.enabled(context)) }
    var checking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var found by remember { mutableStateOf<Updates.Release?>(null) }
    Group(
        "Updates",
        rows = listOf(
            {
                ToggleRow(Icons.Rounded.SystemUpdate, "Check for Updates", "Once a day, SunPods asks GitHub for a new version and lets you know", on) {
                    on = it
                    Updates.setEnabled(context, it)
                }
            },
            {
                val release = found
                NavRow(
                    Icons.Rounded.Refresh,
                    if (release != null) "Get SunPods ${release.version}" else "Check Now",
                    if (checking) "Checking" else result ?: "You have version ${SunpodsInfo.VERSION}"
                ) {
                    if (release != null) {
                        Updates.open(context, release.url)
                    } else if (!checking) {
                        checking = true
                        scope.launch {
                            val latest = Updates.latest()
                            found = latest?.takeIf { Updates.isNewer(it.version, SunpodsInfo.VERSION) }
                            result = when {
                                latest == null -> "Couldn't reach GitHub. Check your connection and try again."
                                found != null -> "Version ${latest.version} is out. Tap to download it."
                                else -> "You're up to date (version ${SunpodsInfo.VERSION})"
                            }
                            checking = false
                        }
                    }
                }
            },
            { NavRow(Icons.Rounded.Code, "Source Code", "See the code, report a bug or suggest a feature on GitHub") { Updates.open(context, "https://github.com/${Updates.REPO}") } }
        )
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
        "Find My Earbuds",
        rows = buildList {
            add {
                ToggleRow(
                    Icons.Rounded.LocationOn, "Last Seen",
                    "Saves where your earbuds disconnect. Stored only on this phone.", enabled
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
                add { NavRow(Icons.Rounded.LocationOff, "Clear Saved Location", null) { LastSeenStore.clear(context); hasSpot = false } }
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
    /** From the build, so it can't go stale. */
    val VERSION: String = BuildConfig.VERSION_NAME
}

private val PROMPT_VOLUMES = listOf(-2, -1, 0, 1, 2)
private fun promptVolumeLabel(v: Int) = when (v) { -2 -> "Quietest"; -1 -> "Quieter"; 0 -> "Normal"; 1 -> "Louder"; else -> "Loudest" }
private fun hourLabel(h: Int) = "%02d:00".format(h)

@Composable
private fun VoicePromptsGroup(settings: DeviceSettings, vm: SunpodsViewModel) {
    val context = LocalContext.current
    var quiet by remember { mutableStateOf(QuietHours.enabled(context)) }
    var start by remember { mutableStateOf(QuietHours.startHour(context)) }
    var end by remember { mutableStateOf(QuietHours.endHour(context)) }
    val rows = mutableListOf<@Composable () -> Unit>()
    rows += {
        ToggleRow(Icons.Rounded.RecordVoiceOver, "Voice Prompts", "Spoken alerts such as \"Battery about 20%\"", settings.promptsOn) { vm.setPrompts(it) }
    }
    if (settings.promptVolume != null) rows += {
        var open by remember { mutableStateOf(false) }
        NavRow(Icons.Rounded.VolumeUp, "Prompt Volume", promptVolumeLabel(settings.promptVolume)) { open = true }
        if (open) ChoiceSheet("Prompt Volume", PROMPT_VOLUMES, settings.promptVolume, label = ::promptVolumeLabel, onDismiss = { open = false }) {
            vm.setPromptVolume(it)
        }
    }
    rows += {
        ToggleRow(Icons.Rounded.Bedtime, "Quiet Hours", "Turns prompts off from ${hourLabel(start)} to ${hourLabel(end)}", quiet) {
            quiet = it; vm.setQuietHours(it, start, end)
        }
    }
    if (quiet) {
        rows += {
            var open by remember { mutableStateOf(false) }
            NavRow(Icons.Rounded.NightsStay, "Starts", hourLabel(start)) { open = true }
            if (open) ChoiceSheet("Quiet Hours Start", (0..23).toList(), start, label = ::hourLabel, onDismiss = { open = false }) {
                start = it; vm.setQuietHours(true, it, end)
            }
        }
        rows += {
            var open by remember { mutableStateOf(false) }
            NavRow(Icons.Rounded.WbSunny, "Ends", hourLabel(end)) { open = true }
            if (open) ChoiceSheet("Quiet Hours End", (0..23).toList(), end, label = ::hourLabel, onDismiss = { open = false }) {
                end = it; vm.setQuietHours(true, start, it)
            }
        }
    }
    Group(
        "Voice Prompts",
        footer = if (quiet) "Quiet Hours are set on your earbuds, so they work even when SunPods is closed. The connection prompt may still play once if your earbuds connect during Quiet Hours." else null,
        rows = rows
    )
}

/** Device tab: back up to a file, restore on a new phone, or export battery history for a spreadsheet. */
@Composable
private fun YourDataGroup() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<Pair<String, Backup.Summary>?>(null) }
    fun toast(text: String) = android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_LONG).show()

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { Backup.write(context, uri, Backup.export(context)) }.isSuccess }
            toast(if (ok) "Backup saved" else "Couldn't save the backup. Try again.")
        }
    }
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { Backup.write(context, uri, Backup.historyCsv(context)) }.isSuccess }
            toast(if (ok) "Battery history exported" else "Couldn't export battery history. Try again.")
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { Backup.read(context, uri).let { it to Backup.inspect(it) } }
            }
            result.onSuccess { pending = it }.onFailure { toast(it.message ?: "This file isn't a SunPods backup. Choose another file.") }
        }
    }

    Group(
        "Your Data",
        rows = listOf(
            { NavRow(Icons.Rounded.SaveAlt, "Back Up", "Save your settings, automations, Last Seen location and battery history to a file") { save.launch(Backup.suggestedName()) } },
            { NavRow(Icons.Rounded.SettingsBackupRestore, "Restore from Backup", "Load your data from a SunPods backup file") { open.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) } },
            {
                NavRow(Icons.Rounded.TableChart, "Export Battery History", "Save as a CSV file") {
                    csv.launch("SunPods-battery-history-" + java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).format(java.util.Date()) + ".csv")
                }
            }
        ),
        footer = "Files are saved only where you choose. Backups include your Last Seen location, so store them securely."
    )

    pending?.let { (text, s) ->
        val date = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(s.exportedAt))
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Restore This Backup?") },
            text = {
                Text(
                    "Created $date in SunPods ${s.appVersion}. Includes ${s.settings} settings, ${s.historyRows} battery readings and ${s.healthRows} battery health records.\n\n" +
                        "This replaces your current settings and merges your battery history. SunPods restarts when it's done."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { runCatching { Backup.restore(context, text) }.isSuccess }
                        if (ok) Backup.restart(context) else toast("Couldn't restore this backup. Try another file.")
                    }
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } }
        )
    }
}

/** Controls tab: hear your own call mic, and switch between Classic and LE Audio. */
@Composable
private fun CallsGroup(settings: DeviceSettings, vm: SunpodsViewModel) {
    val context = LocalContext.current
    var testing by remember { mutableStateOf(false) }
    var choosing by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<Boolean?>(null) }
    var switching by remember { mutableStateOf<Boolean?>(null) }
    var tick by remember { mutableStateOf(0) }
    val le = remember(settings.readAt, tick) { LeAudio.anyConnected(context) }
    val rows = mutableListOf<@Composable () -> Unit>()
    rows += { NavRow(Icons.Rounded.Mic, "Test Your Call Mic", "Hear yourself the way callers do") { testing = true } }
    val phoneLe = remember { LeAudio.phoneSupports(context) }
    if (settings.leModeSupported == true && phoneLe) rows += {
        NavRow(Icons.Rounded.BluetoothAudio, "Connection Mode", if (le) "LE Audio" else "Classic") { choosing = true }
    }
    // Earbuds that can do LE Audio on a phone that can't: say so instead of hiding it.
    if (settings.leModeSupported == true && !phoneLe) rows += {
        InfoRow("Connection Mode", "Classic")
    }
    // A switch that didn't complete can leave the earbuds' LE Audio setting on while the phone
    // still uses Classic, which turns off Connect to 2 devices.
    // Repair rows wait until a switch has had time to settle; mid-switch the links are meant to be in flux.
    val settling = remember(settings.readAt, tick) { SonyControlSessionManager.switchedRecently(context) }
    LaunchedEffect(settling) { if (settling) { delay(30_000); tick++ } }
    // Set to Classic but not connected at all, as after a switch the phone didn't finish.
    val classicUp = remember(settings.readAt, tick) { runCatching { context.getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter?.getProfileConnectionState(android.bluetooth.BluetoothProfile.A2DP) == android.bluetooth.BluetoothProfile.STATE_CONNECTED }.getOrDefault(false) }
    if (!settling && !le && !classicUp) rows += {
        NavRow(Icons.Rounded.RestartAlt, "Reconnect Your Earbuds", "Your earbuds aren't connected. Tap to pair them again.") { switching = false }
    }
    // The reverse: the earbuds are set to Classic but one is still holding on to LE Audio.
    if (!settling && le && (settings.leOn == false || SonyControlSessionManager.classicOnly(context))) rows += {
        NavRow(Icons.Rounded.RestartAlt, "Finishing the Switch to Classic", "Put both earbuds in the case, close the lid for 10 seconds, then take them out.") { tick++ }
    }
    if (!settling && settings.leOn == true && !le) rows += {
        NavRow(Icons.Rounded.RestartAlt, "Finish Switching Back to Classic", "Your earbuds still have LE Audio on. Tap to turn it off.") { switching = false }
    }
    Group(
        "Calls",
        footer = when {
            settings.leModeSupported != true -> null
            phoneLe -> "LE Audio often makes your voice clearer on calls. It needs a phone that supports it."
            android.os.Build.VERSION.SDK_INT < 33 -> "Your earbuds support LE Audio, but it needs Android 13 or later. They use Classic on this phone."
            else -> "Your earbuds support LE Audio, but this phone doesn't, or it's turned off in Developer options. They use Classic on this phone."
        },
        rows = rows
    )
    if (testing) MicTestSheet(onDismiss = { testing = false })
    if (choosing) ChoiceSheet(
        "Connection Mode", listOf(false, true), le,
        label = { if (it) "LE Audio" else "Classic" },
        detail = { if (it) "Clearer calls on phones that support it. Turns off Connect to Two Devices." else "Works with every phone and device. Connect to Two Devices is available." },
        onDismiss = { choosing = false }
    ) { pick -> choosing = false; if (pick != le) confirm = pick }
    confirm?.let { on ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (on) "Switch to LE Audio?" else "Switch to Classic?") },
            text = {
                Text(
                    (if (on) "Your earbuds will reconnect using LE Audio. The first time, SunPods helps you pair them again. Connect to Two Devices and LDAC are off while LE Audio is on."
                    else "Your earbuds will reconnect using Classic. You may need to pair them again.") + "\n\nWear both earbuds before you switch."
                )
            },
            confirmButton = { TextButton(onClick = { confirm = null; switching = on }) { Text("Switch") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } }
        )
    }
    switching?.let { on -> LeSwitchSheet(on, settings) { switching = null; tick++; vm.reloadCached() } }
}
