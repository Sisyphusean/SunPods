@file:OptIn(ExperimentalMaterial3Api::class)

package com.peyi.sonypodsbattery

import androidx.compose.ui.draw.drawWithContent
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.AutoMode
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Shapes
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
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
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

// Spacing on the 4/8dp grid, after HIG inset grouped lists: 16dp margins, cards with a modest
// corner, and room between groups so each reads as one idea.
private val Gutter = 16.dp
private val GroupGap = 28.dp
private val CardShape = RoundedCornerShape(16.dp)
/** Where a row's text starts: margin, icon tile, gap. Dividers start here, as in iOS lists. */
internal val RowTextStart = 16.dp + TileSize + 14.dp

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
        title.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = androidx.compose.ui.unit.TextUnit(0.8f, androidx.compose.ui.unit.TextUnitType.Sp)),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp).semantics { heading() }
    )
}

/** Small explanatory text under a card. */
@Composable
internal fun RollingFooter(text: String) {
    androidx.compose.animation.AnimatedContent(
        targetState = text,
        transitionSpec = {
            (fadeIn(tween(220, delayMillis = 60)) + androidx.compose.animation.slideInVertically(tween(260, easing = Motion.Arrive)) { it / 2 })
                .togetherWith(fadeOut(tween(120)) + androidx.compose.animation.slideOutVertically(tween(180, easing = Motion.Leave)) { -it / 2 })
                .using(androidx.compose.animation.SizeTransform(clip = false))
        },
        label = "footer"
    ) { Footer(it) }
}

@Composable
internal fun Footer(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
    )
}

@Composable
internal fun SunCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = cardColor, shape = CardShape, modifier = modifier.fillMaxWidth()) {
        Column(content = content)
    }
}

@Composable
internal fun RowDivider(inset: Dp = RowTextStart) {
    HorizontalDivider(Modifier.padding(start = inset), thickness = 0.5.dp, color = dividerColor)
}

private enum class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    HOME("Home", androidx.compose.material.icons.Icons.Outlined.Headphones, Icons.Rounded.Headphones),
    SOUND("Sound", androidx.compose.material.icons.Icons.Outlined.Equalizer, Icons.Rounded.Equalizer),
    CONTROLS("Controls", androidx.compose.material.icons.Icons.Outlined.TouchApp, Icons.Rounded.TouchApp),
    BATTERY("Battery", androidx.compose.material.icons.Icons.Outlined.BatteryStd, Icons.Rounded.BatteryFull),
    DEVICE("Device", androidx.compose.material.icons.Icons.Outlined.Info, Icons.Rounded.Info)
}

/** Height of the floating tab bar, and the gap under it; content leaves room for both. */
internal val TabBarHeight = 64.dp
internal val TabBarGap = 12.dp

/**
 * The tab bar, after One UI 8 (Samsung Members): a floating capsule over the content, the chosen
 * tab sitting in a lighter pill that springs across, its icon filling in. Wider than Members' so
 * five tabs breathe. A light tick on each switch.
 */
@Composable
private fun SunTabBar(selected: Tab, modifier: Modifier = Modifier, onSelect: (Tab) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val buzz = rememberBuzz()
    androidx.compose.material3.Surface(
        modifier = modifier.padding(horizontal = 16.dp).widthIn(max = 520.dp).fillMaxWidth().height(TabBarHeight),
        shape = CircleShape,
        color = colors.surfaceContainerHigh.copy(alpha = 0.96f),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, colors.outlineVariant.copy(alpha = 0.5f)),
        shadowElevation = 8.dp
    ) {
        androidx.compose.foundation.layout.BoxWithConstraints(
            Modifier.fillMaxSize().padding(5.dp).semantics { this.contentDescription = "Tabs" }
        ) {
            val itemW = maxWidth / Tab.entries.size
            val x by androidx.compose.animation.core.animateDpAsState(
                itemW * selected.ordinal,
                spring(dampingRatio = 0.78f, stiffness = 420f),
                label = "pill"
            )
            Box(
                Modifier
                    .offset(x = x)
                    .width(itemW)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(colors.onSurface.copy(alpha = 0.13f))
            )
            Row(Modifier.fillMaxSize()) {
                Tab.entries.forEach { t ->
                    val on = t == selected
                    val tint by animateColorAsState(if (on) colors.onSurface else colors.onSurfaceVariant, label = "tint")
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .selectable(
                                selected = on,
                                role = Role.Tab,
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) {
                                if (!on) buzz(Buzz.TICK)
                                onSelect(t)
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        androidx.compose.animation.Crossfade(on, animationSpec = tween(160), label = "icon") { filled ->
                            Icon(if (filled) t.selectedIcon else t.icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            t.label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (on) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Medium
                            ),
                            color = tint,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SunpodsApp(
    vm: SunpodsViewModel,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onShareDiagnostics: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    dynamicColor: Boolean = false,
    onDynamicColorChange: (Boolean) -> Unit = {},
    animateEntrance: Boolean = false,
    onEntrancePlayed: () -> Unit = {},
    onRunSetupAgain: () -> Unit = {},
) {
    val liveBattery by vm.battery.collectAsState()
    val liveSettings by vm.settings.collectAsState()
    val demoContext = LocalContext.current
    remember { EarbudModel.current(demoContext) }
    val demo = remember { DemoMode.on(demoContext) }
    val battery = if (demo) DemoMode.battery else liveBattery
    // Demo screenshots in any colour: the "demo_color" flag (Sony's code, e.g. 6 for pink).
    val settings = if (demo) DemoMode.settings.copy(colorCode = DemoMode.color(demoContext)) else liveSettings
    val busy by vm.busy.collectAsState()
    val message by vm.message.collectAsState()
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var page by rememberSaveable { mutableStateOf<Page?>(null) }
    val snackbar = remember { SnackbarHostState() }
    // Navigation is one seekable transition, so a back swipe can drag it. It follows One UI's
    // Settings: a page slides in from the right edge over the one before, which drifts left and
    // dims; a back swipe drags the page off under your finger, title and all, and lets it go on
    // release. Every page carries its own title bar so the title moves with it.
    val navScope = rememberCoroutineScope()
    val nav = remember { androidx.compose.animation.core.SeekableTransitionState(tab to page) }
    val navTransition = androidx.compose.animation.core.rememberTransition(nav, label = "nav")
    var gestureBack by remember { mutableStateOf(false) }
    // Each tab keeps its scroll position and title bar while you're elsewhere; pages start fresh.
    val tabScroll = remember { mutableMapOf<Tab, androidx.compose.foundation.ScrollState>() }
    val tabBars = remember { mutableMapOf<Tab, androidx.compose.material3.TopAppBarState>() }
    fun scrollOf(t: Tab) = tabScroll.getOrPut(t) { androidx.compose.foundation.ScrollState(0) }
    fun barOf(t: Tab) = tabBars.getOrPut(t) { androidx.compose.material3.TopAppBarState(-Float.MAX_VALUE, 0f, 0f) }
    // Like Samsung Members, the tab bar slips away while you scroll down and returns when you scroll up.
    var barHidden by remember { mutableStateOf(false) }
    val barScroll = remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPostScroll(
                consumed: androidx.compose.ui.geometry.Offset,
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource
            ): androidx.compose.ui.geometry.Offset {
                if (consumed.y < -4f) barHidden = true
                else if (consumed.y > 4f || available.y > 4f) barHidden = false
                return androidx.compose.ui.geometry.Offset.Zero
            }
        }
    }
    fun go(toTab: Tab, toPage: Page?) {
        barHidden = false
        tab = toTab
        page = toPage
        gestureBack = false
        navScope.launch { nav.animateTo(toTab to toPage) }
    }
    val screenWidth = with(androidx.compose.ui.platform.LocalDensity.current) {
        androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp.toPx()
    }
    androidx.activity.compose.PredictiveBackHandler(enabled = page != null) { events ->
        val from = tab to page
        val to = tab to null
        gestureBack = true
        var committed = false
        try {
            nav.seekTo(0f, targetState = to)
            events.collect { e ->
                // The page's left edge stays under your finger.
                val x = if (e.swipeEdge == androidx.activity.BackEventCompat.EDGE_LEFT) e.touchX else screenWidth - e.touchX
                nav.seekTo((x / screenWidth).coerceIn(0f, 1f), targetState = to)
            }
            committed = true
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            committed = false
        }
        // Finish outside the handler: clearing the page disables the handler and cancels its coroutine.
        navScope.launch {
            if (committed) {
                page = null
                nav.animateTo(to, tween(280, easing = Motion.Arrive))
            } else {
                // Let go early: the page slides back into place.
                val start = nav.fraction
                val t0 = androidx.compose.runtime.withFrameNanos { it }
                while (true) {
                    val t = androidx.compose.runtime.withFrameNanos { it }
                    val p = ((t - t0) / 240_000_000f).coerceAtMost(1f)
                    nav.seekTo(start * (1f - Motion.Arrive.transform(p)), targetState = to)
                    if (p >= 1f) break
                }
                nav.snapTo(from)
            }
            gestureBack = false
        }
    }
    // Back from another tab's top goes to Home first, like other Android apps with a tab bar.
    androidx.activity.compose.BackHandler(enabled = page == null && tab != Tab.HOME) { go(Tab.HOME, null) }

    val buzz = rememberBuzz()
    val refresh = { if (hasPermission) vm.refreshAll() else onRequestPermission() }
    // The refresh arrow turns while SunPods talks to the earbuds, then eases into place.
    val spin = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(busy) {
        if (busy) {
            while (true) spin.animateTo(spin.value + 360f, tween(850, easing = androidx.compose.animation.core.LinearEasing))
        } else if (spin.value != 0f) {
            spin.animateTo(kotlin.math.ceil(spin.value / 360f) * 360f, spring(dampingRatio = 0.7f, stiffness = 260f))
            spin.snapTo(0f)
        }
    }
    // A thin bar while a change is on its way, only if it takes long enough to notice.
    val applying by vm.applying.collectAsState()
    var showApplying by remember { mutableStateOf(false) }
    LaunchedEffect(applying > 0) {
        if (applying > 0) { delay(250); showApplying = true } else showApplying = false
    }
    val rejected by vm.rejected.collectAsState()
    LaunchedEffect(rejected) { if (rejected > 0) buzz(Buzz.REJECT) }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    Scaffold(
        containerColor = pageColor,
        // Messages sit just above the floating tab bar.
        snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = TabBarHeight + TabBarGap)) },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets.statusBars
    ) { padding ->
      val barHide by animateFloatAsState(
          if (barHidden) 1f else 0f,
          tween(if (barHidden) 220 else 300, easing = if (barHidden) Motion.Leave else Motion.Arrive),
          label = "barHide"
      )
      Box(Modifier.fillMaxSize().nestedScroll(barScroll)) {
        PullToRefreshBox(
            isRefreshing = busy,
            onRefresh = refresh,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                LocalOpenPage provides { go(tab, it) },
                LocalBudArt provides budArt(settings)
            ) {
                navTransition.AnimatedContent(
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        val pushing = initialState.first == targetState.first && targetState.second != null && initialState.second == null
                        val popping = initialState.first == targetState.first && targetState.second == null && initialState.second != null
                        val behind = { w: Int -> -(w * 0.28f).toInt() }
                        when {
                            // In: from the right edge, slowing hard at the end; the page behind drifts left.
                            pushing -> androidx.compose.animation.slideInHorizontally(tween(420, easing = Motion.Arrive)) { it }
                                .togetherWith(androidx.compose.animation.slideOutHorizontally(tween(420, easing = Motion.Arrive), behind))
                                .apply { targetContentZIndex = 1f }
                            // Back swipe: the page tracks the finger exactly; the one behind holds
                            // back until late, then settles as the page leaves.
                            popping && gestureBack -> androidx.compose.animation.slideInHorizontally(tween(400, easing = Motion.Leave), behind)
                                .togetherWith(androidx.compose.animation.slideOutHorizontally(tween(400, easing = androidx.compose.animation.core.LinearEasing)) { it })
                                .apply { targetContentZIndex = -1f }
                            // Back button or arrow: the push in reverse.
                            popping -> androidx.compose.animation.slideInHorizontally(tween(380, easing = Motion.Arrive), behind)
                                .togetherWith(androidx.compose.animation.slideOutHorizontally(tween(380, easing = Motion.Arrive)) { it })
                                .apply { targetContentZIndex = -1f }
                            // Between tabs: Material fade through.
                            else -> (fadeIn(tween(210, delayMillis = 90)) + androidx.compose.animation.scaleIn(tween(210, delayMillis = 90, easing = Motion.Arrive), initialScale = 0.94f))
                                .togetherWith(fadeOut(tween(90)))
                        }
                    }
                ) { (shownTab, shownPage) ->
                    val listState = if (shownPage == null) scrollOf(shownTab) else androidx.compose.foundation.rememberScrollState()
                    val barState = if (shownPage == null) barOf(shownTab) else remember { androidx.compose.material3.TopAppBarState(-Float.MAX_VALUE, 0f, 0f) }
                    val bar = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(barState)
                    // Moving between a tab and one of its pages (not between tabs): the tab dims
                    // as the page covers it, and the page casts a soft shadow on its left edge.
                    val inPageMove = navTransition.currentState.first == navTransition.targetState.first &&
                        (navTransition.currentState.second == null) != (navTransition.targetState.second == null)
                    val scrim by transition.animateFloat(
                        transitionSpec = { tween(400, easing = if (gestureBack) Motion.Leave else Motion.Arrive) },
                        label = "scrim"
                    ) { if (it == androidx.compose.animation.EnterExitState.Visible) 0f else 1f }
                    val dimHere = inPageMove && shownPage == null
                    val shadeHere = inPageMove && shownPage != null
                    Column(
                        Modifier
                            .fillMaxSize()
                            .drawBehind {
                                if (shadeHere) {
                                    val w = 24.dp.toPx()
                                    drawRect(
                                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.28f)), startX = -w, endX = 0f
                                        ),
                                        topLeft = androidx.compose.ui.geometry.Offset(-w, 0f),
                                        size = androidx.compose.ui.geometry.Size(w, size.height)
                                    )
                                }
                            }
                            .background(pageColor)
                            .drawWithContent {
                                drawContent()
                                if (dimHere && scrim > 0f) drawRect(Color.Black.copy(alpha = 0.32f * scrim))
                            }
                            .nestedScroll(bar.nestedScrollConnection)
                    ) {
                        LargeTopAppBar(
                            title = {
                                Text(
                                    shownPage?.displayTitle() ?: if (shownTab == Tab.HOME) "SunPods" else shownTab.label,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            },
                            navigationIcon = {
                                if (shownPage != null) IconButton(onClick = { go(shownTab, null) }) {
                                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to ${shownTab.label}")
                                }
                            },
                            actions = {
                                IconButton(onClick = { if (!busy) { buzz(Buzz.TICK); refresh() } }) {
                                    Icon(
                                        Icons.Rounded.Refresh,
                                        contentDescription = if (busy) "Refreshing" else "Refresh battery and settings",
                                        modifier = Modifier.graphicsLayer { rotationZ = spin.value }
                                    )
                                }
                            },
                            windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
                            colors = TopAppBarDefaults.largeTopAppBarColors(containerColor = pageColor, scrolledContainerColor = pageColor),
                            scrollBehavior = bar
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(listState)
                                .padding(horizontal = Gutter)
                                .padding(top = 8.dp, bottom = TabBarHeight + TabBarGap + 32.dp)
                                .navigationBarsPadding(),
                            verticalArrangement = Arrangement.spacedBy(GroupGap)
                        ) {
                            if (!hasPermission) PermissionBanner(onRequestPermission)
                            if (shownPage != null) {
                                PageContent(shownPage, settings, vm, onShareDiagnostics, onOpenBluetoothSettings, dynamicColor, onDynamicColorChange, onRunSetupAgain)
                            } else when (shownTab) {
                                Tab.HOME -> HomeScreen(battery, settings, vm, busy, animateEntrance, onEntrancePlayed)
                                Tab.SOUND -> SoundScreen(settings, vm)
                                Tab.CONTROLS -> ControlsScreen(settings, vm)
                                Tab.BATTERY -> BatteryInsightsScreen(battery)
                                Tab.DEVICE -> DeviceScreen(settings)
                            }
                        }
                    }
                }
            }
            androidx.compose.animation.AnimatedVisibility(
                showApplying,
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(300))
            ) {
                LinearProgressIndicator(
                    Modifier.fillMaxWidth().height(3.dp).semantics { contentDescription = "Saving to your ${EarbudModel.active.noun}" },
                    trackColor = Color.Transparent
                )
            }
        }
        SunTabBar(
            tab,
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = TabBarGap)
                .graphicsLayer {
                    translationY = barHide * (size.height + 56.dp.toPx())
                    alpha = 1f - 0.4f * barHide
                }
        ) { t ->
            barHidden = false
            if (t == tab && page == null) navScope.launch {
                scrollOf(tab).animateScrollTo(0, tween(420, easing = Motion.Arrive))
                barOf(tab).heightOffset = 0f
            } else go(t, null)
        }
      }
    }
}

// ======================================================================
// Building blocks: Android 16 style grouped lists
// ======================================================================

/** A titled group of rows in one card, separated by hairlines. */
@Composable
private fun Group(title: String?, footer: String? = null, inset: Dp = RowTextStart, rows: List<@Composable () -> Unit>) {
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
internal fun RowIcon(icon: ImageVector, active: Boolean = true) = IconTile(icon, enabled = active)

/** The shape every settings row shares: icon tile, title (and a short note), then whatever trails. */
@Composable
internal fun SettingRow(
    icon: ImageVector?,
    title: String,
    modifier: Modifier = Modifier,
    note: String? = null,
    enabled: Boolean = true,
    trailing: @Composable () -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().heightIn(min = 52.dp).padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            IconTile(icon, enabled = enabled)
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title, style = MaterialTheme.typography.bodyLarge, color = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = 0.38f),
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (note != null) Text(note, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

@Composable
internal fun ToggleRow(icon: ImageVector?, title: String, subtitle: String, checked: Boolean?, onChange: (Boolean) -> Unit) =
    ToggleRow(icon, title, subtitle, checked, null, onChange)

/**
 * A switch row. [subtitle] isn't shown (rows carry a title, not a paragraph); explanations live in
 * the group's footer. [offNote]: the setting can't be used now; shown off and dimmed, with the reason.
 */
@Composable
internal fun ToggleRow(icon: ImageVector?, title: String, @Suppress("UNUSED_PARAMETER") subtitle: String, checked: Boolean?, offNote: String?, onChange: (Boolean) -> Unit) {
    val known = checked != null
    val off = offNote != null
    val on = checked == true && !off
    val buzz = rememberBuzz()
    SettingRow(
        icon, title,
        modifier = Modifier.toggleable(value = on, enabled = known && !off, role = Role.Switch) {
            buzz(if (it) Buzz.ON else Buzz.OFF)
            onChange(it)
        },
        note = offNote,
        enabled = !off
    ) {
        Switch(checked = on, enabled = known && !off, onCheckedChange = null)
    }
}

private val TransparentItem @Composable get() =
    ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)

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

/** A row that opens something: its current value on the right, then a chevron. */
@Composable
private fun NavRow(icon: ImageVector?, title: String, value: String?, note: String? = null, onClick: () -> Unit) {
    SettingRow(icon, title, modifier = Modifier.clickable(onClick = onClick), note = note) {
        if (value != null) {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(max = 160.dp)
            )
            Spacer(Modifier.width(4.dp))
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
    }
}

private fun Page.displayTitle(): String = if (this == Page.FIND && EarbudModel.active.headphones) "Find My Headphones" else title

/** A row that opens one of the app's own pages. */
@Composable
private fun PageRow(icon: ImageVector, page: Page, value: String? = null) {
    val open = LocalOpenPage.current
    NavRow(icon, page.displayTitle(), value) { open(page) }
}

@Composable
private fun InfoRow(title: String, value: String) {
    SettingRow(null, title) {
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
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
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val buzz = rememberBuzz()
    var chosen by remember { mutableStateOf(selected) }
    var closing by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).semantics { heading() }
            )
            options.forEach { option ->
                ListItem(
                    modifier = Modifier.selectable(
                        selected = option == chosen,
                        role = Role.RadioButton,
                        enabled = !closing,
                        onClick = {
                            // Show the choice land, then slide the sheet away.
                            closing = true
                            chosen = option
                            buzz(Buzz.TICK)
                            onPick(option)
                            scope.launch {
                                delay(180)
                                sheet.hide()
                                onDismiss()
                            }
                        }
                    ),
                    headlineContent = { Text(label(option)) },
                    supportingContent = detail(option)?.let { { Text(it) } },
                    leadingContent = { RadioButton(selected = option == chosen, onClick = null) },
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
                "SunPods needs this to connect to your ${EarbudModel.active.noun} over Bluetooth.",
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
private fun HomeScreen(
    battery: BatteryState,
    settings: DeviceSettings,
    vm: SunpodsViewModel,
    busy: Boolean,
    animateEntrance: Boolean = false,
    onEntrancePlayed: () -> Unit = {},
) {
    val update by vm.update.collectAsState()
    androidx.compose.animation.AnimatedVisibility(update != null, enter = revealIn(), exit = revealOut()) {
        update?.let { UpdateCard(it, vm) }
    }
    val homeContext = LocalContext.current
    var justUpdated by rememberSaveable { mutableStateOf(WhatsNew.shouldShow(homeContext)) }
    if (justUpdated) {
        WhatsNew.notes(homeContext, SunpodsInfo.VERSION)?.let { notes ->
            WhatsNewSheet(SunpodsInfo.VERSION, notes) { justUpdated = false }
        }
    }
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
                model = settings.modelName ?: EarbudModel.active.displayName,
                color = LocalBudArt.current.hero3d,
                device = LocalBudArt.current.device,
                animateEntrance = animateEntrance,
                onEntrancePlayed = onEntrancePlayed,
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
            enter = revealIn(),
            exit = revealOut()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(GroupGap)) {
                NoiseControl(battery, settings, vm)
                NowPlayingCard(vm)
            }
        }
        AnimatedVisibility(
            visible = !connected,
            enter = revealIn(),
            exit = revealOut()
        ) {
            LastSeenCard()
        }
    }
}

/**
 * A new SunPods is out. Plain One UI card in the app's own Material You language: the icon tile,
 * version and size, one line on what's new, then Later and Update on the right. While it works,
 * the buttons give way to a progress bar.
 */
@Composable
private fun UpdateCard(release: Updates.Release, vm: SunpodsViewModel) {
    val state by vm.updateState.collectAsState()
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    // Back from Android's "install unknown apps" setting: carry on if it's now allowed.
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val latestState by androidx.compose.runtime.rememberUpdatedState(state)
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val o = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME && latestState == SelfUpdate.State.NeedsPermission && SelfUpdate.canInstall(context)) vm.installUpdate()
        }
        lifecycle.addObserver(o)
        onDispose { lifecycle.removeObserver(o) }
    }
    var reading by remember { mutableStateOf(false) }
    val notes = release.notes ?: WhatsNew.notes(context, release.version)
    val headline = Updates.headline(notes) ?: "Improvements and fixes."
    val size = release.size?.let { android.text.format.Formatter.formatShortFileSize(context, it) }
    val busy = state is SelfUpdate.State.Downloading || state == SelfUpdate.State.Installing
    SunCard {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Rounded.SystemUpdate)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("SunPods ${release.version}", style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Text(
                        listOfNotNull("Update available", size).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(headline, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (notes != null) {
                Text(
                    "What's new",
                    style = MaterialTheme.typography.bodyMedium, color = colors.primary,
                    modifier = Modifier.padding(top = 2.dp).clip(RoundedCornerShape(6.dp)).clickable { reading = true }.padding(vertical = 4.dp)
                )
            }
            val problem = when (val st = state) {
                is SelfUpdate.State.Failed -> st.message
                SelfUpdate.State.NeedsPermission -> "Allow SunPods to install updates, then come back."
                else -> null
            }
            if (problem != null) Text(
                problem, style = MaterialTheme.typography.bodySmall,
                color = if (state is SelfUpdate.State.Failed) colors.error else colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(Modifier.height(6.dp))
            androidx.compose.animation.AnimatedContent(busy, transitionSpec = { fadeThrough() }, label = "updateActions") { working ->
                if (working) {
                    val fraction = (state as? SelfUpdate.State.Downloading)?.fraction
                    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                        if (fraction != null) {
                            val shown by animateFloatAsState(fraction, tween(300), label = "update")
                            LinearProgressIndicator(
                                progress = { shown },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                                trackColor = colors.surfaceContainerHighest
                            )
                        } else {
                            LinearProgressIndicator(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape), trackColor = colors.surfaceContainerHighest)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            when (val st = state) {
                                is SelfUpdate.State.Downloading -> st.fraction?.let { "Downloading · ${(it * 100).roundToInt()}%" } ?: "Downloading"
                                else -> "Installing"
                            },
                            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant
                        )
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { vm.dismissUpdate() }) { Text("Later") }
                        Spacer(Modifier.width(4.dp))
                        Button(onClick = { vm.installUpdate() }) {
                            Text(if (state is SelfUpdate.State.Failed) "Try Again" else "Update")
                        }
                    }
                }
            }
        }
    }
    if (reading && notes != null) WhatsNewSheet(release.version, notes, action = "Update", onAction = { vm.installUpdate() }) { reading = false }
}

/** What's playing, with playback controls through the earbuds and the media volume. */
@Composable
private fun NowPlayingCard(vm: SunpodsViewModel) {
    val context = LocalContext.current
    val now by vm.nowPlaying.collectAsState()
    var volume by remember { mutableStateOf(Playback.volume(context)) }
    val playLifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(playLifecycle) {
        playLifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            while (true) {
                vm.refreshNowPlaying()
                volume = Playback.volume(context)
                delay(3_000)
            }
        }
    }
    Column {
        SectionHeader("Now Playing")
        SunCard {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text(
                    now?.title ?: "Nothing playing",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.playback(Playback.Action.PREVIOUS) }) {
                        Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous track", modifier = Modifier.size(32.dp))
                    }
                    val playing = now?.playing == true
                    FilledIconButton(
                        onClick = { vm.playback(if (playing) Playback.Action.PAUSE else Playback.Action.PLAY) },
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (playing) "Pause" else "Play",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    IconButton(onClick = { vm.playback(Playback.Action.NEXT) }) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = "Next track", modifier = Modifier.size(32.dp))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.VolumeDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(
                        value = volume,
                        onValueChange = { volume = it; Playback.setVolume(context, it) },
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp).semantics { contentDescription = "Volume" }
                    )
                    Icon(Icons.Rounded.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Left, right and case as rings around the rendered parts. */
@Composable
private fun BatteryCard(battery: BatteryState, busy: Boolean, liveAt: Long = 0L) {
    val heard = maxOf(battery.updatedAt, liveAt)
    val context = LocalContext.current
    val caseAt = remember(battery.updatedAt) { BatteryStore.caseAt(context) }
    val caseStale = battery.case != null && !battery.caseCharging && caseAt > 0 && System.currentTimeMillis() - caseAt > 20 * 60_000L
    val outlook = remember(battery.updatedAt) {
        BatteryInsights.outlook(BatteryHistory.load(context, System.currentTimeMillis() - 7L * 86_400_000), battery)
    }
    Column {
        SunCard {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 24.dp)) {
                val art = LocalBudArt.current
                // The case reports its level only when the earbuds come out of it. An old reading
                // greys out and the status line says how old, like Sony's app explains it.
                art.model.batteries.forEach { part ->
                    BatteryRing(
                        part, part.level(battery)?.takeIf { it in 1..100 }, part.charging(battery), art.image(part), Modifier.weight(1f),
                        muted = part == BatteryPart.CASE && caseStale
                    )
                }
            }
        }
        RollingFooter(
            when {
                busy -> "Checking your ${LocalBudArt.current.model.noun}…"
                battery.updatedAt == 0L -> "Pull down to refresh."
                outlook.caseNeedsCharge -> "Case battery is low. Charge it tonight."
                caseStale -> "Updated ${relative(heard)}. Case level from ${relative(caseAt)}."
                else -> "Updated ${relative(heard)}."
            }
        )
    }
}

@Composable
private fun BatteryRing(part: BatteryPart, level: Int?, charging: Boolean, art: Int, modifier: Modifier = Modifier, muted: Boolean = false) {
    val label = part.label
    val colors = MaterialTheme.colorScheme
    val low = level != null && level <= 20 && !charging
    // The reading is there the moment the screen is: no fill-up to wait through. A new reading
    // glides from the old value and gives a small pop, so a change is easy to spot.
    val fill = remember { androidx.compose.animation.core.Animatable((level ?: 0) / 100f) }
    val pop = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(level) {
        val to = (level ?: 0) / 100f
        if (fill.value == to) return@LaunchedEffect
        if (level != null) launch { pop.snapTo(1.1f); pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 320f)) }
        fill.animateTo(to, tween(500, easing = Motion.Arrive))
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
    val ring = (if (low) colors.error else if (muted) colors.outline else colors.primary).copy(alpha = breathe)
    val track = colors.surfaceContainerHighest
    val description = when {
        level == null -> "$label battery unknown"
        charging -> "$label battery $level percent, charging"
        else -> "$label battery $level percent"
    }
    Column(
        modifier
            .semantics(mergeDescendants = true) { contentDescription = description },
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
                modifier = Modifier.size(if (part == BatteryPart.CASE) 40.dp else 52.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value }
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value }) {
            if (level == null) {
                Text(part.missing, style = MaterialTheme.typography.titleSmall, color = colors.onSurfaceVariant)
            } else {
                Text(
                    (sweep * 100).roundToInt().toString(),
                    style = MaterialTheme.typography.titleLarge,
                    color = if (low) colors.error else if (muted) colors.onSurfaceVariant else colors.onSurface
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
                LocalBudArt.current.model.batteries.forEach { LevelBar(it.label, it.level(last), Modifier.weight(1f)) }
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
                        if (enabled) "Location access is off for SunPods, so Last Seen can't save where your ${EarbudModel.active.noun} disconnect."
                        else "SunPods can save where your ${EarbudModel.active.noun} disconnect, so you know where to look. The location stays on this phone.",
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
                    "SunPods saves the location the next time your ${EarbudModel.active.noun} disconnect.",
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
        Footer("Saved only on this phone.")
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
private fun NoiseControl(battery: BatteryState, settings: DeviceSettings, vm: SunpodsViewModel) {
    val windAuto = settings.windAuto
    val hasNcAmbButton = settings.has(Feature.NC_AMB_BUTTON)
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
                visible = battery.listeningMode == ListeningMode.NOISE_CANCELLING && windAuto != null,
                enter = revealIn(),
                exit = revealOut()
            ) {
                Column {
                    RowDivider(inset = 20.dp)
                    ToggleRow(null, "Automatic Wind Noise Reduction", "Cuts wind noise outdoors. Uses a little more battery.", windAuto == true) {
                        vm.setWindReduction(it)
                    }
                }
            }
            AnimatedVisibility(
                visible = battery.listeningMode == ListeningMode.AMBIENT,
                enter = revealIn(),
                exit = revealOut()
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
                    if (settings.autoAmbient != null) {
                        RowDivider(inset = 20.dp)
                        ToggleRow(null, "Auto Ambient Sound", "Adjusts the ambient sound to the noise around you", settings.autoAmbient == true) {
                            vm.setAutoAmbient(it)
                        }
                        AnimatedVisibility(settings.autoAmbient == true, enter = revealIn(), exit = revealOut()) {
                            Column {
                                RowDivider(inset = 20.dp)
                                var open by remember { mutableStateOf(false) }
                                NavRow(null, "Detection Sensitivity", settings.autoAmbientSensitivity?.label ?: "—") { open = true }
                                if (open) {
                                    ChoiceSheet("Detection Sensitivity", AutoAmbientSensitivity.entries.toList(), settings.autoAmbientSensitivity,
                                        label = { it.label }, onDismiss = { open = false }, onPick = vm::setAutoAmbientSensitivity)
                                }
                            }
                        }
                    }
                }
            }
        }
        val context = LocalContext.current
        val automation = remember(battery.listeningMode, battery.updatedAt) { AutomationStore.active(context) }
        Footer(automation?.let { "Set automatically: $it. Choose a mode to take over." } ?: if (hasNcAmbButton) "You can also press the NC/AMB button to switch modes." else "You can also tap the earbuds to switch modes.")
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

private fun relative(time: Long): String =
    if (System.currentTimeMillis() - time < 60_000) "just now"
    else DateUtils.getRelativeTimeSpanString(time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString().lowercase()

// ======================================================================
// Sound
// ======================================================================

@Composable
private fun SoundScreen(settings: DeviceSettings, vm: SunpodsViewModel) {
    EqPanel(settings, vm)

    val safe by vm.safe.collectAsState()
    Group(
        null,
        footer = settings.codec?.let { "Playing over $it." },
        rows = buildList {
            if (settings.has(Feature.DSEE)) add { ToggleRow(Icons.Rounded.AutoAwesome, settings.dseeName, "", settings.dseeOn, leOffNote(settings), vm::setDsee) }
            add {
                var open by remember { mutableStateOf(false) }
                NavRow(Icons.Rounded.BluetoothAudio, "Connection Priority", settings.priority?.short ?: "—") { open = true }
                if (open) {
                    ChoiceSheet(
                        "Connection Priority",
                        // Low Latency moves the headphones to LE Audio and needs pairing again, so it's
                        // listed only while it's in use (set in Sony Sound Connect).
                        ConnectionPriority.entries.filter { it != ConnectionPriority.LOW_LATENCY || settings.priority == it },
                        settings.priority,
                        label = { it.label },
                        detail = { it.detailFor(EarbudModel.active) },
                        onDismiss = { open = false },
                        onPick = vm::setPriority
                    )
                }
            }
            if (settings.has(Feature.SAFE_LISTENING)) add { PageRow(Icons.Rounded.HealthAndSafety, Page.SAFE_LISTENING, if (safe.first) "On" else "Off") }
        }
    )
}

/** Safe listening and volume protection, on the earbuds themselves. */
@Composable
private fun SafeListeningGroup(vm: SunpodsViewModel) {
    val safe by vm.safe.collectAsState()
    var choosing by remember { mutableStateOf(false) }
    // The switch leads; everything else on the page belongs to it. Off stops the live meter.
    Group(
        null,
        footer = "Your ${EarbudModel.active.noun} keep track of how loud and how long you listen, following the World Health Organization's guidelines.",
        rows = listOf({ ToggleRow(Icons.Rounded.HealthAndSafety, "Safe Listening", "", safe.first, vm::setSafeListening) })
    )
    androidx.compose.animation.AnimatedVisibility(safe.first, enter = revealIn(), exit = revealOut()) {
        Column(verticalArrangement = Arrangement.spacedBy(GroupGap)) {
            LevelMeter(vm, sensitive = safe.second)
            Group(
                null,
                rows = listOf({
                    NavRow(Icons.Rounded.VolumeUp, "Volume Protection", if (safe.second) "Sensitive" else "Standard") { choosing = true }
                    if (choosing) {
                        ChoiceSheet(
                            "Volume Protection", listOf(false, true), safe.second,
                            label = { if (it) "Sensitive" else "Standard" },
                            detail = { if (it) "A lower limit, for children and sensitive ears" else "The guideline for adults" },
                            onDismiss = { choosing = false },
                            onPick = { vm.setVolumeProtection(it) }
                        )
                    }
                })
            )
        }
    }
}

/**
 * How loud it is in your ears right now, measured by the earbuds. Runs only while it's on screen;
 * the earbuds go back to normal Safe Listening logging when you leave.
 */
@Composable
private fun LevelMeter(vm: SunpodsViewModel, sensitive: Boolean) {
    val colors = MaterialTheme.colorScheme
    val meterLifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.DisposableEffect(meterLifecycle) {
        val o = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_START) vm.startMeter()
            if (e == androidx.lifecycle.Lifecycle.Event.ON_STOP) vm.stopMeter()
        }
        meterLifecycle.addObserver(o)   // replays ON_START if the screen is already visible
        onDispose { meterLifecycle.removeObserver(o); vm.stopMeter() }
    }
    val level by vm.level.collectAsState()
    val db = (level as? SafeListening.Level.Db)?.db
    // Sony's loudness guide for each Volume Protection level (from the WHO guidelines).
    val limit = if (sensitive) 76 else 81
    val shown by animateFloatAsState((db ?: 0).toFloat(), spring(dampingRatio = 0.9f, stiffness = 600f), label = "db")
    val loud = db != null && db >= limit
    val accent by animateColorAsState(
        when {
            db == null -> colors.outline
            loud -> colors.error
            db >= limit - 10 -> colors.tertiary
            else -> colors.primary
        },
        label = "accent"
    )
    Column {
        SunCard {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Text("LISTENING LEVEL", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        if (db != null) shown.roundToInt().toString() else "–",
                        style = MaterialTheme.typography.displayMedium,
                        color = accent
                    )
                    Text(" dB", style = MaterialTheme.typography.titleLarge, color = colors.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                }
                Text(
                    when (level) {
                        null -> "Listening…"
                        SafeListening.Level.NotPlaying -> "Play something to see the level"
                        SafeListening.Level.OnCall -> "Paused during calls"
                        SafeListening.Level.NotWorn -> if (EarbudModel.active.headphones) "Put your headphones on" else "Put both earbuds in"
                        is SafeListening.Level.Db -> when {
                            loud -> "Loud. Turn it down for long listens"
                            db!! >= limit - 10 -> "Moderate"
                            else -> "Comfortable"
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                // 30 to 100 dB, with a mark at the guideline.
                val track = colors.surfaceContainerHighest
                val mark = colors.onSurfaceVariant
                Canvas(Modifier.fillMaxWidth().height(10.dp).semantics { contentDescription = if (db != null) "$db decibels" else "No reading" }) {
                    val r = size.height / 2
                    drawRoundRect(track, cornerRadius = androidx.compose.ui.geometry.CornerRadius(r))
                    val f = ((shown - 30f) / 70f).coerceIn(0f, 1f)
                    if (db != null && f > 0f) drawRoundRect(accent, size = Size(size.width * f, size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r))
                    val x = size.width * (limit - 30f) / 70f
                    drawLine(mark, Offset(x, -3.dp.toPx()), Offset(x, size.height + 3.dp.toPx()), strokeWidth = 2.dp.toPx())
                }
            }
        }
        Footer("Measured live by your ${EarbudModel.active.noun}. Try to keep long listens under $limit dB.")
    }
}

/**
 * The sound as a live curve, with sliders under it when the preset can be edited. Bands, range,
 * labels and presets come from the pair ([EqLayout]): CLEAR BASS and five bands on most, ten on the
 * WH-1000XM6.
 */
@Composable
private fun EqPanel(settings: DeviceSettings, vm: SunpodsViewModel) {
    val colors = MaterialTheme.colorScheme
    val layout = remember(settings.functions1, settings.eqCap, settings.eqInfo) { settings.eqLayout }
    val source = (if (layout.clearBass) listOf(settings.clearBass ?: 0) else emptyList()) +
        (settings.bands?.takeIf { it.size == layout.bandCount } ?: List(layout.bandCount) { 0 })
    var values by remember(settings.clearBass, settings.bands, layout) { mutableStateOf(source) }
    val editable = layout.isEditable(settings.eqPresetCode)
    fun commit(all: List<Int>) =
        if (layout.clearBass) vm.setEqBands(all[0], all.drop(1)) else vm.setEqBands(null, all)
    fun isClearBass(i: Int) = layout.clearBass && i == 0

    Column {
        SunCard {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Text(layout.label(settings.eqPresetCode), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(16.dp))
                EqCurve(values, layout.max, Modifier.fillMaxWidth().height(140.dp))
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    layout.labels.forEachIndexed { i, label ->
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isClearBass(i)) colors.primary else colors.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                AnimatedVisibility(editable, enter = revealIn(), exit = revealOut()) {
                    Column(Modifier.padding(top = 16.dp)) {
                        values.forEachIndexed { i, v ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (isClearBass(i)) "Clear Bass" else "${layout.labels[i]} Hz",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.width(84.dp)
                                )
                                Slider(
                                    value = v.toFloat(),
                                    onValueChange = { new -> values = values.toMutableList().also { it[i] = new.roundToInt() } },
                                    onValueChangeFinished = { commit(values) },
                                    valueRange = layout.min.toFloat()..layout.max.toFloat(),
                                    steps = layout.steps - 2,
                                    colors = quietSlider(),
                                    modifier = Modifier.weight(1f).semantics { contentDescription = "${layout.labels[i]} band" }
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
                Spacer(Modifier.height(16.dp))
                // Only the presets these earbuds or headphones offer (the WH-1000XM6 ignores the classic ones).
                val presets = layout.shown(settings.eqPresetCode)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { (code, name) ->
                        FilterChip(
                            selected = settings.eqPresetCode == code,
                            onClick = { vm.setEqPreset(code) },
                            label = { Text(name) },
                            shape = CircleShape
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                val customise = layout.customise
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (editable) {
                        TextButton(onClick = {
                            values = List(layout.count) { 0 }
                            commit(values)
                        }) { Text("Reset to Flat") }
                    } else if (customise != null) {
                        FilledTonalButton(onClick = { vm.setEqPreset(customise) }) {
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
private fun EqCurve(values: List<Int>, max: Int, modifier: Modifier) {
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
        fun y(v: Float) = h / 2 - v / max.coerceAtLeast(1) * (h / 2 - 8.dp.toPx())
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

/** Which noise modes a tap or the NC/AMB button steps through. */
@Composable
private fun NoiseCycleRow(title: String, sheetTitle: String, settings: DeviceSettings, vm: SunpodsViewModel) {
    var open by remember { mutableStateOf(false) }
    NavRow(Icons.Rounded.NoiseAware, title, settings.noiseCycle?.short ?: "—") { open = true }
    if (open) {
        ChoiceSheet(sheetTitle, NoiseCycle.entries.toList(), settings.noiseCycle,
            label = { it.label }, onDismiss = { open = false }, onPick = vm::setNoiseCycle)
    }
}

@Composable
private fun ControlsScreen(settings: DeviceSettings, vm: SunpodsViewModel) {
    val context = LocalContext.current
    val left = settings.touchLeft
    val right = settings.touchRight

    if (!settings.has(Feature.TOUCH) && settings.has(Feature.NC_AMB_BUTTON)) Column {
        SunCard { NoiseCycleRow("NC/AMB Button", "NC/AMB Button Cycles Through", settings, vm) }
        Footer("Press the NC/AMB button on the left ear cup to switch. Tap and swipe controls are on the right ear cup.")
    } else Column {
        SunCard {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val options = settings.touchOptions
                TouchSide("Left", LocalBudArt.current.left, left, options?.get(0), Modifier.weight(1f)) { picked -> vm.setTouch(picked, null) }
                TouchSide("Right", LocalBudArt.current.right, right, options?.get(1), Modifier.weight(1f)) { picked -> vm.setTouch(null, picked) }
            }
            if (left == TouchFunction.AMBIENT_SOUND_CONTROL || right == TouchFunction.AMBIENT_SOUND_CONTROL) {
                RowDivider(inset = 16.dp)
                NoiseCycleRow("Tap Cycles Through", "Tap Cycles Through", settings, vm)
            }
        }
        Footer("Tap an earbud to change what it does.")
    }

    val automations = remember(settings.readAt) {
        Trigger.entries.count { it != Trigger.PLACE && AutomationStore.rule(context, it).enabled } + AutomationStore.places(context).size +
            (if (AutoPlay.enabled(context)) 1 else 0)
    }
    val le = rememberLeOn(settings)
    val multipoint = settings.general?.firstOrNull { it.key == "MULTIPOINT_SETTING" }?.on
    Group(
        null,
        rows = buildList {
            add { PageRow(Icons.Rounded.AutoMode, Page.AUTOMATIONS, if (automations == 0) "Off" else "$automations on") }
            if (settings.has(Feature.SPEAK_TO_CHAT)) add { PageRow(Icons.Rounded.RecordVoiceOver, Page.SPEAK_TO_CHAT, onOff(settings.speakToChat)) }
            if (settings.has(Feature.WEARING)) add { PageRow(Icons.Rounded.Sensors, Page.WEARING) }
            if (settings.promptsOn != null || settings.promptVolume != null) add { PageRow(Icons.Rounded.Campaign, Page.VOICE_PROMPTS, onOff(settings.promptsOn)) }
        }
    )
    val hasConnection = settings.general.orEmpty().isNotEmpty() || settings.autoSwitch != null || DemoMode.on(context)
    Group(
        null,
        rows = buildList {
            add { PageRow(Icons.Rounded.Call, Page.CALLS, if (settings.leSwitchOffered) (if (le) "LE Audio" else "Classic") else null) }
            if (hasConnection) {
                add { PageRow(Icons.Rounded.Bluetooth, Page.CONNECTION, when { le -> "One device"; multipoint == true -> "Two devices"; multipoint == false -> "One device"; else -> null }) }
            }
        }
    )
}

private fun onOff(v: Boolean?) = when (v) { true -> "On"; false -> "Off"; null -> null }

@Composable
private fun SpeakToChatPage(settings: DeviceSettings, vm: SunpodsViewModel) {
    val sensitivity = settings.speakToChatSensitivity ?: SpeakToChatSensitivity.AUTO
    val timeout = settings.speakToChatTimeout ?: SpeakToChatTimeout.STANDARD
    Group(
        null,
        footer = "Music pauses and outside sound comes in when you start talking.",
        rows = buildList {
            add { ToggleRow(Icons.Rounded.RecordVoiceOver, "Speak-to-Chat", "", settings.speakToChat, vm::setSpeakToChat) }
            if (settings.speakToChat == true) {
                add {
                    var open by remember { mutableStateOf(false) }
                    NavRow(Icons.Rounded.Hearing, "Voice Detection", settings.speakToChatSensitivity?.label ?: "—") { open = true }
                    if (open) {
                        ChoiceSheet("Voice Detection", SpeakToChatSensitivity.entries.toList(), settings.speakToChatSensitivity,
                            label = { it.label }, onDismiss = { open = false }) { vm.setSpeakToChatConfig(it, timeout) }
                    }
                }
                add {
                    var open by remember { mutableStateOf(false) }
                    NavRow(Icons.Rounded.Timer, "End Chat Mode", settings.speakToChatTimeout?.short ?: "—") { open = true }
                    if (open) {
                        ChoiceSheet("End Chat Mode After", SpeakToChatTimeout.entries.toList(), settings.speakToChatTimeout,
                            label = { it.label }, onDismiss = { open = false }) { vm.setSpeakToChatConfig(sensitivity, it) }
                    }
                }
            }
        }
    )
}

@Composable
private fun WearingPage(settings: DeviceSettings, vm: SunpodsViewModel) {
    val model = EarbudModel.active
    Group(
        null,
        footer = "Your ${model.noun} sense when you take them ${if (model.headphones) "off" else "out"}.",
        rows = buildList {
            if (settings.has(Feature.PAUSE_WHEN_REMOVED)) add { ToggleRow(Icons.Rounded.Sensors, "Pause When Removed", "", settings.pauseWhenRemoved, vm::setPauseWhenRemoved) }
            if (settings.has(Feature.AUTO_OFF_WHEN_REMOVED)) {
                val options = settings.autoOffOptions
                if (AutoOff.hasTimes(options)) add {
                    // Earbuds that offer times (the WF-C710N): one choice, like Sound Connect's.
                    var open by remember { mutableStateOf(false) }
                    NavRow(Icons.Rounded.PowerSettingsNew, "Turn Off Automatically", settings.autoOffCode?.let(AutoOff::short) ?: "—") { open = true }
                    if (open) {
                        ChoiceSheet("Turn Off Automatically", AutoOff.ordered(options!!), settings.autoOffCode,
                            label = { AutoOff.label(it) }, onDismiss = { open = false }, onPick = vm::setAutoOffOption)
                    }
                } else add { ToggleRow(Icons.Rounded.PowerSettingsNew, "Power Off When Removed", "", settings.autoOffWhenRemoved, vm::setAutoOff) }
            }
        }
    )
}

@Composable
private fun ConnectionPage(settings: DeviceSettings, vm: SunpodsViewModel) {
    // Only what these earbuds say they support, named the way they name it.
    val general = settings.general.orEmpty()
    if (general.isNotEmpty() || settings.autoSwitch != null) {
        val rows = mutableListOf<@Composable () -> Unit>()
        general.forEach { g ->
            rows += {
                // Two devices at once needs Classic; the earbuds turn it off on LE Audio.
                val offNote = if (g.key == "MULTIPOINT_SETTING") leOffNote(settings) else null
                ToggleRow(generalIcon(g.key), g.title, "", g.on, offNote) { on -> vm.setGeneral(g.slot, on) }
            }
        }
        settings.autoSwitch?.let { on ->
            rows += { ToggleRow(Icons.Rounded.SwapHoriz, "Switch Devices Automatically", "", on, leOffNote(settings), vm::setAutoSwitch) }
        }
        Group(null, rows = rows)
    }
    val live = vm.battery.collectAsState().value
    DevicesCard(connected = live.left != null || live.right != null, leOn = rememberLeOn(settings))
}

/** One level below a tab: the page for [page]. */
@Composable
private fun PageContent(
    page: Page,
    settings: DeviceSettings,
    vm: SunpodsViewModel,
    onShareDiagnostics: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    dynamicColor: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
    onRunSetupAgain: () -> Unit,
) {
    when (page) {
        Page.AUTOMATIONS -> AutomationsCard()
        Page.SPEAK_TO_CHAT -> SpeakToChatPage(settings, vm)
        Page.WEARING -> WearingPage(settings, vm)
        Page.VOICE_PROMPTS -> VoicePromptsGroup(settings, vm)
        Page.CALLS -> CallsGroup(settings, vm)
        Page.CONNECTION -> ConnectionPage(settings, vm)
        Page.SAFE_LISTENING -> SafeListeningGroup(vm)
        Page.FIND -> LastSeenSettings()
        Page.APPEARANCE -> AppearancePage(dynamicColor, onDynamicColorChange)
        Page.DATA -> YourDataGroup()
        Page.UPDATES -> UpdatesGroup(vm)
        Page.HELP -> HelpPage(onShareDiagnostics, onOpenBluetoothSettings, onRunSetupAgain)
        Page.BATTERY_HEALTH -> BatteryHealthCard(vm.battery.collectAsState().value.updatedAt)
        Page.BATTERY_WEEK -> BatteryWeekPage(vm.battery.collectAsState().value)
        Page.BATTERY_ALERTS -> BatteryAlertsPage()
    }
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
    // What the earbuds list for this side; a typical key only if they haven't said.
    fun hint(f: TouchFunction) = options?.firstOrNull { it.function == f }?.let(TouchCapability::describe) ?: touchHint(f)
    fun legend(f: TouchFunction) = options?.firstOrNull { it.function == f }?.let(TouchCapability::legend)?.takeIf { it.isNotEmpty() } ?: touchLegend(f)
    val colors = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClickLabel = "Change $side earbud touch control") { open = true }
            .padding(vertical = 16.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(painterResource(art), contentDescription = null, modifier = Modifier.size(width = 96.dp, height = 80.dp))
        Spacer(Modifier.height(8.dp))
        Text(side.uppercase(), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Surface(color = colors.secondaryContainer, shape = CircleShape) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(current?.label ?: "—", style = MaterialTheme.typography.labelLarge, color = colors.onSecondaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = colors.onSecondaryContainer, modifier = Modifier.size(18.dp))
            }
        }
        if (current != null) {
            Spacer(Modifier.height(12.dp))
            GestureKey(legend(current))
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

/** Gestures and what they do, as a compact two-column key instead of a sentence. */
@Composable
private fun GestureKey(rows: List<Pair<String, String>>) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
        rows.forEach { (gesture, does) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = colors.surfaceContainerHighest, shape = RoundedCornerShape(6.dp), modifier = Modifier.widthIn(min = 44.dp)) {
                    Text(
                        gesture,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(does, style = MaterialTheme.typography.bodySmall, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun touchLegend(function: TouchFunction): List<Pair<String, String>> = when (function) {
    TouchFunction.AMBIENT_SOUND_CONTROL -> listOf("1×" to "Noise mode", "Hold" to "Attention")
    TouchFunction.PLAYBACK_CONTROL -> listOf("1×" to "Play/Pause", "2×" to "Next", "3×" to "Previous")
    TouchFunction.VOLUME_CONTROL -> listOf("1×" to "Volume up", "Hold" to "Volume down")
    TouchFunction.NONE -> listOf("—" to "Nothing")
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
private fun DeviceScreen(settings: DeviceSettings) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            painter = painterResource(LocalBudArt.current.pair),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth(0.62f)
                .height(150.dp)
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
        Spacer(Modifier.height(4.dp))
        Text(settings.modelName ?: EarbudModel.active.displayName, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(2.dp))
        Text(
            listOfNotNull(settings.firmware?.let { "Firmware $it" }, settings.codec).joinToString("  ·  ").ifEmpty { "Sony wireless ${EarbudModel.active.noun}" },
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant
        )
    }

    EarbudsGroup(settings)

    val context = LocalContext.current
    val lastSeen = remember { LastSeenStore.enabled(context) && LastSeenStore.hasLocationPermission(context) }
    Group(
        null,
        rows = listOf(
            { PageRow(Icons.Rounded.LocationOn, Page.FIND, if (lastSeen) "On" else "Off") },
            { PageRow(Icons.Rounded.Palette, Page.APPEARANCE) },
            { PageRow(Icons.Rounded.SaveAlt, Page.DATA) },
            { PageRow(Icons.Rounded.SystemUpdate, Page.UPDATES) },
            { PageRow(Icons.Rounded.Info, Page.HELP, SunpodsInfo.VERSION) }
        )
    )
}

@Composable
private fun AppearancePage(dynamicColor: Boolean, onDynamicColorChange: (Boolean) -> Unit) {
    Group(
        null,
        rows = listOf(
            {
                var picking by remember { mutableStateOf(false) }
                NavRow(Icons.Rounded.Widgets, "Add a Widget", null) { picking = true }
                if (picking) WidgetPickerSheet(onDismiss = { picking = false })
            },
            { ToggleRow(Icons.Rounded.Palette, "Use Wallpaper Colours", "", dynamicColor, onDynamicColorChange) }
        )
    )
}

@Composable
private fun HelpPage(onShareDiagnostics: () -> Unit, onOpenBluetoothSettings: () -> Unit, onRunSetupAgain: () -> Unit) {
    val context = LocalContext.current
    val model = EarbudModel.active
    Group(
        null,
        rows = listOf(
            { NavRow(Icons.Rounded.Bluetooth, "Bluetooth Settings", null, onClick = onOpenBluetoothSettings) },
            { NavRow(Icons.Rounded.BugReport, "Send Diagnostics", null, onClick = onShareDiagnostics) },
            { NavRow(Icons.Rounded.RestartAlt, "Run Setup Again", null, onClick = onRunSetupAgain) }
        ),
        footer = "Your ${model.noun} talk to one app at a time, so close Sony Sound Connect while you use SunPods."
    )
    Group(
        null,
        rows = listOf(
            { InfoRow("Version", SunpodsInfo.VERSION) },
            { NavRow(null, "Source Code", "GitHub") { Updates.open(context, "https://github.com/${Updates.REPO}") } },
            {
                var reading by remember { mutableStateOf(false) }
                NavRow(null, "What's New", SunpodsInfo.VERSION) { reading = true }
                if (reading) WhatsNew.notes(context, SunpodsInfo.VERSION)?.let { WhatsNewSheet(SunpodsInfo.VERSION, it) { reading = false } }
            }
        ),
        inset = 16.dp,
        footer = "Free and open source under the GPL-3.0. No account, and nothing leaves your phone except the update check."
    )
}

/** Voice assistant, fit test, turning the earbuds off and resetting their settings. */
@Composable
private fun EarbudsGroup(settings: DeviceSettings) {
    val vm: SunpodsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val assistant by vm.assistant.collectAsState()
    val le = rememberLeOn(settings)
    LaunchedEffect(settings.readAt) { vm.loadAssistant() }
    var choosing by remember { mutableStateOf(false) }
    var fitting by remember { mutableStateOf(false) }
    var confirmOff by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    val rows = mutableListOf<@Composable () -> Unit>()
    val a = assistant
    if (a != null && settings.has(Feature.VOICE_ASSISTANT)) rows += {
        NavRow(Icons.Rounded.RecordVoiceOver, "Voice Assistant", if (le) LE_OFF_NOTE else a.first?.let(VoiceAssistantSetting::short) ?: "—") {
            if (!le) choosing = true
        }
        if (choosing) {
            ChoiceSheet("Voice Assistant", a.second, a.first,
                label = VoiceAssistantSetting::label, detail = { VoiceAssistantSetting.detail(it) },
                onDismiss = { choosing = false }, onPick = { vm.setAssistant(it) })
        }
    }
    val model = EarbudModel.active
    if (settings.has(Feature.FIT_TEST)) rows += { NavRow(Icons.Rounded.Hearing, "Ear Tip Fit Test", null) { fitting = true } }
    if (settings.has(Feature.POWER_OFF)) rows += { NavRow(Icons.Rounded.PowerSettingsNew, "Turn Off ${model.titleNoun}", null) { confirmOff = true } }
    if (settings.has(Feature.RESET)) rows += { NavRow(Icons.Rounded.RestartAlt, "Reset ${if (model.headphones) "Headphone" else "Earbud"} Settings", null) { confirmReset = true } }
    Group("Your ${model.titleNoun}", rows = rows, footer = note)

    if (fitting) FitTestSheet(vm) { fitting = false }
    if (confirmOff) {
        AlertDialog(
            onDismissRequest = { confirmOff = false },
            title = { Text("Turn off your ${model.noun}?") },
            text = { Text(if (model.headphones) "They stay off until you press the power button again." else "They stay off until you put them in the case and take them out again.") },
            confirmButton = { TextButton(onClick = { confirmOff = false; vm.powerOff { ok -> note = if (ok) null else "Couldn't turn off your ${model.noun}. Make sure they're connected." } }) { Text("Turn Off") } },
            dismissButton = { TextButton(onClick = { confirmOff = false }) { Text("Cancel") } }
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset your ${EarbudModel.active.noun}' settings?") },
            text = { Text("Equaliser, noise control, touch controls and other settings go back to their defaults. Your paired devices stay.") },
            confirmButton = { TextButton(onClick = { confirmReset = false; vm.resetSettings { ok -> note = if (ok) "Your ${EarbudModel.active.noun} are back to their original settings." else "Couldn't reset your ${EarbudModel.active.noun}. Make sure they're connected and try again." } }) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } }
        )
    }
}

/** Pick the tips you have on, then the earbuds measure the seal of each. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FitTestSheet(vm: SunpodsViewModel, onDone: () -> Unit) {
    var tips by remember { mutableStateOf<FitTest.Tips?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var size by remember { mutableStateOf<Int?>(null) }
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<FitTest.Result?>(null) }
    LaunchedEffect(Unit) { vm.fitTips { tips = it; size = it?.sizes?.getOrNull(it.sizes.size / 2); loaded = true } }
    ModalBottomSheet(onDismissRequest = { if (!running) onDone() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            Text("Ear Tip Fit Test", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            val step = when {
                !loaded -> 0
                tips == null -> 1
                running -> 2
                result != null -> 3
                else -> 4
            }
            androidx.compose.animation.AnimatedContent(step to result, transitionSpec = { fadeThrough() }, label = "fit") { (shown, res) ->
            Column(Modifier.fillMaxWidth()) {
            val t = tips
            when (shown) {
                0 -> Text("Getting your earbuds ready…", style = MaterialTheme.typography.bodyMedium)
                1 -> Text("Couldn't start the fit test. Make sure your ${EarbudModel.active.noun} are connected and in your ears.", style = MaterialTheme.typography.bodyMedium)
                2 -> {
                    Text("Stay still and quiet while your earbuds play a short tone.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                3 -> {
                    val r = res ?: return@Column
                    if (r.error != null) Text(r.error, style = MaterialTheme.typography.bodyMedium)
                    else {
                        Text("Left: ${if (r.left == true) "Good seal" else "Poor seal"}", style = MaterialTheme.typography.titleMedium)
                        Text("Right: ${if (r.right == true) "Good seal" else "Poor seal"}", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (r.left == true && r.right == true) "A great fit. Noise cancelling is working at its best."
                            else "Twist the earbud in a little further or try another tip size, then test again.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { result = null }, modifier = Modifier.fillMaxWidth()) { Text("Test Again") }
                }
                else -> {
                    if (t == null) return@Column
                    Text("Put both earbuds in and choose the tip size you're using.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        t.sizes.forEach { s ->
                            FilterChip(selected = size == s, onClick = { size = s }, label = { Text(FitTest.sizeLabel(s)) })
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { running = true; vm.runFitTest(t, size ?: t.sizes.first()) { result = it; running = false } },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Start Test") }
                }
            }
            }
            }
        }
    }
}

@Composable
private fun UpdatesGroup(updateVm: SunpodsViewModel? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var on by remember { mutableStateOf(Updates.enabled(context)) }
    var checking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var found by remember { mutableStateOf<Updates.Release?>(null) }
    Group(
        null,
        rows = listOf(
            {
                ToggleRow(Icons.Rounded.SystemUpdate, "Check for Updates", "", on) {
                    on = it
                    Updates.setEnabled(context, it)
                }
            },
            {
                val release = found
                NavRow(
                    Icons.Rounded.Refresh,
                    if (release != null) "Get SunPods ${release.version}" else "Check Now",
                    if (checking) "Checking" else result ?: SunpodsInfo.VERSION
                ) {
                    if (release != null) {
                        // Same as the card on Home: download, check, install.
                        updateVm?.let { it.setAvailableUpdate(release); it.installUpdate() } ?: Updates.open(context, release.url)
                    } else if (!checking) {
                        checking = true
                        scope.launch {
                            val latest = Updates.refresh(context)
                            found = latest?.takeIf { Updates.isNewer(it.version, SunpodsInfo.VERSION) }
                            updateVm?.setAvailableUpdate(found ?: Updates.available(context))
                            result = when {
                                latest == null -> "Couldn't reach GitHub"
                                found != null -> "${latest.version} is out"
                                else -> "Up to date"
                            }
                            checking = false
                        }
                    }
                }
            },
        ),
        footer = "Checks GitHub automatically in the background and when you open the app. Nothing else is sent."
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
        null,
        footer = "Saves where your ${EarbudModel.active.noun} disconnect, only on this phone.",
        rows = buildList {
            add {
                ToggleRow(
                    Icons.Rounded.LocationOn, "Last Seen",
                    "Saves where your ${EarbudModel.active.noun} disconnect. Stored only on this phone.", enabled
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

    fun color(context: android.content.Context): Int? =
        context.getSharedPreferences("sunpods_ui", android.content.Context.MODE_PRIVATE).getInt("demo_color", 1)

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
        ToggleRow(Icons.Rounded.Campaign, "Voice Prompts", "", settings.promptsOn) { vm.setPrompts(it) }
    }
    if (settings.promptVolume != null) rows += {
        var open by remember { mutableStateOf(false) }
        NavRow(Icons.Rounded.VolumeUp, "Volume", promptVolumeLabel(settings.promptVolume)) { open = true }
        if (open) ChoiceSheet("Prompt Volume", PROMPT_VOLUMES, settings.promptVolume, label = ::promptVolumeLabel, onDismiss = { open = false }) {
            vm.setPromptVolume(it)
        }
    }
    rows += {
        ToggleRow(Icons.Rounded.Bedtime, "Quiet Hours", "", quiet) {
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
        null,
        footer = if (quiet) "Prompts stay off from ${hourLabel(start)} to ${hourLabel(end)}, even when SunPods is closed." else "Spoken alerts like \"Battery about 20%\".",
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
            result.onSuccess { pending = it }.onFailure { toast(it.message ?: "This isn't a SunPods backup. Choose another file.") }
        }
    }

    Group(
        null,
        rows = listOf(
            { NavRow(Icons.Rounded.SaveAlt, "Back Up", null) { save.launch(Backup.suggestedName()) } },
            { NavRow(Icons.Rounded.SettingsBackupRestore, "Restore from Backup", null) { open.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) } },
            {
                NavRow(Icons.Rounded.TableChart, "Export Battery History", "CSV") {
                    csv.launch("SunPods-battery-history-" + java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).format(java.util.Date()) + ".csv")
                }
            }
        ),
        footer = "Backups hold your settings, automations, battery history and Last Seen location. Keep them somewhere safe."
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
    rows += { NavRow(Icons.Rounded.Mic, "Test Your Call Mic", null) { testing = true } }
    if (settings.headGestures != null) rows += {
        ToggleRow(Icons.Rounded.Gesture, "Head Gestures", "", settings.headGestures, vm::setHeadGestures)
    }
    val phoneLe = remember { LeAudio.phoneSupports(context) }
    val leSwitch = settings.leSwitchOffered
    val model = EarbudModel.fromName(settings.modelName) ?: EarbudModel.active
    if (leSwitch && phoneLe) rows += {
        NavRow(Icons.Rounded.BluetoothAudio, "Connection Mode", if (le) "LE Audio" else "Classic") { choosing = true }
    }
    // Devices that can do LE Audio on a phone that can't: say so instead of hiding it.
    if (leSwitch && !phoneLe) rows += {
        InfoRow("Connection Mode", "Classic")
    }
    // A switch that didn't complete can leave the device's LE Audio setting on while the phone
    // still uses Classic, which turns off Connect to 2 devices.
    // Repair rows wait until a switch has had time to settle; mid-switch the links are meant to be in flux.
    val settling = remember(settings.readAt, tick) { SonyControlSessionManager.switchedRecently(context) }
    LaunchedEffect(settling) { if (settling) { delay(30_000); tick++ } }
    // Set to Classic but not connected at all, as after a switch the phone didn't finish.
    val classicUp = remember(settings.readAt, tick) { runCatching { context.getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter?.getProfileConnectionState(android.bluetooth.BluetoothProfile.A2DP) == android.bluetooth.BluetoothProfile.STATE_CONNECTED }.getOrDefault(false) }
    if (leSwitch && !settling && !le && !classicUp) rows += {
        NavRow(Icons.Rounded.RestartAlt, "Reconnect Your ${model.titleNoun}", null, note = "Not connected") { switching = false }
    }
    // The reverse: the device is set to Classic but still holding on to LE Audio.
    if (leSwitch && !settling && le && (settings.leOn == false || SonyControlSessionManager.classicOnly(context))) rows += {
        NavRow(
            Icons.Rounded.RestartAlt,
            "Finishing the Switch to Classic",
            null,
            note = if (model.headphones) "Turn them off for 10 seconds, then back on" else "Case them for 10 seconds, then take them out"
        ) { tick++ }
    }
    if (leSwitch && !settling && settings.leOn == true && !le) rows += {
        NavRow(Icons.Rounded.RestartAlt, "Finish Switching to Classic", null, note = "LE Audio is still on") { switching = false }
    }
    Group(
        null,
        footer = when {
            settings.headGestures != null && !leSwitch -> "With head gestures, nod to answer a call or shake your head to decline."
            !leSwitch -> null
            phoneLe -> "Nod to answer a call or shake your head to decline. LE Audio makes your voice clearer on calls."
            android.os.Build.VERSION.SDK_INT < 33 -> "Your ${model.noun} support LE Audio, but it needs Android 13 or later. They use Classic on this phone."
            else -> "Your ${model.noun} support LE Audio, but this phone doesn't, or it's turned off in Developer options. They use Classic on this phone."
        },
        rows = rows
    )
    if (testing) MicTestSheet(onDismiss = { testing = false })
    if (choosing) ChoiceSheet(
        "Connection Mode", listOf(false, true), le,
        label = { if (it) "LE Audio" else "Classic" },
        detail = { if (it) "Clearer calls on phones that support it. Turns off Connect to Two Devices." else "Works with every phone and device. Connect to Two Devices is available." },
        onDismiss = { choosing = false }
    ) { pick -> if (pick != le) confirm = pick }
    confirm?.let { on ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (on) "Switch to LE Audio?" else "Switch to Classic?") },
            text = {
                Text(
                    (if (on) "Your ${model.noun} will reconnect using LE Audio. The first time, SunPods helps you pair them again. Connect to Two Devices and LDAC are off while LE Audio is on."
                    else "Your ${model.noun} will reconnect using Classic. You may need to pair them again.") +
                        "\n\n" + if (model.headphones) "Keep your headphones turned on before you switch." else "Wear both earbuds before you switch."
                )
            },
            confirmButton = { TextButton(onClick = { confirm = null; switching = on }) { Text("Switch") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } }
        )
    }
    switching?.let { on -> LeSwitchSheet(on, settings) { switching = null; tick++; vm.reloadCached() } }
}
