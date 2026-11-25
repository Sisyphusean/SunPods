package com.peyi.sonypodsbattery

import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.clickable
import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Whether first-run setup has been completed. */
internal object OnboardingStore {
    private const val KEY = "onboarding_done"
    private const val HERO_INTRO_KEY = "hero_intro_pending"
    private fun p(c: Context) = c.getSharedPreferences("sunpods_ui", Context.MODE_PRIVATE)

    fun done(c: Context): Boolean {
        val p = p(c)
        if (p.contains(KEY)) return p.getBoolean(KEY, false)
        // People who already use SunPods skip it; Device > Help > Set up again brings it back.
        val existing = hasNearby(c) && File(c.filesDir, "battery_history.csv").exists()
        p.edit().putBoolean(KEY, existing).apply()
        return existing
    }

    fun setDone(c: Context, done: Boolean) { p(c).edit().putBoolean(KEY, done).commit() }

    fun heroIntroPending(c: Context): Boolean = p(c).getBoolean(HERO_INTRO_KEY, false)

    fun setHeroIntroPending(c: Context, pending: Boolean) = p(c).edit().putBoolean(HERO_INTRO_KEY, pending).apply()

    fun hasNearby(c: Context) = Build.VERSION.SDK_INT < 31 ||
        c.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
}

internal enum class OnboardingStep { WELCOME, DEVICE, NEARBY, EARBUDS, LEAUDIO, UPDATES, EXTRAS, DONE }

internal fun onboardingSteps(model: EarbudModel, phoneSupportsLeAudio: Boolean): List<OnboardingStep> =
    OnboardingStep.entries.filter { it != OnboardingStep.LEAUDIO || (phoneSupportsLeAudio && model.supportsLeAudio) }

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
    return null
}

/** Ticks every time the screen comes back (from a system dialog or Settings), so checks re-run. */
@Composable
private fun resumeTick(): Int {
    var tick by remember { mutableIntStateOf(0) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val o = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) tick++ }
        lifecycle.addObserver(o)
        onDispose { lifecycle.removeObserver(o) }
    }
    return tick
}

// -------------------------------------------------------------------------------- permissions

private enum class Perm { GRANTED, ASK, DENIED, BLOCKED }

private class PermissionGate(val state: Perm, val request: () -> Unit)

/**
 * A runtime permission with its whole story: not asked yet, denied once (ask again), or
 * blocked (Android won't show the prompt any more, so Settings is the only way).
 */
@Composable
private fun rememberPermission(permissions: List<String>, tick: Int): PermissionGate {
    val context = LocalContext.current
    var denied by rememberSaveable { mutableStateOf(false) }
    var answered by remember { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { !it }) denied = true
        answered++
    }
    val granted = remember(tick, answered) {
        permissions.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    }
    val activity = context.findActivity()
    val state = when {
        granted -> Perm.GRANTED
        denied && activity != null && permissions.none { activity.shouldShowRequestPermissionRationale(it) } -> Perm.BLOCKED
        denied -> Perm.DENIED
        else -> Perm.ASK
    }
    return PermissionGate(state) { launcher.launch(permissions.toTypedArray()) }
}

private fun openAppSettings(context: Context) = runCatching {
    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
}

private fun openNotificationSettings(context: Context) = runCatching {
    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
}.recover { openAppSettings(context) }

// -------------------------------------------------------------------------------- flow

@Composable
internal fun Onboarding(vm: SunpodsViewModel, onDone: () -> Unit) {
    var step by rememberSaveable { mutableStateOf(OnboardingStep.WELCOME) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedModel by rememberSaveable { mutableStateOf(EarbudModel.chosen(context) ?: EarbudModel.WF_1000XM5) }
    var browsedModel by rememberSaveable { mutableStateOf(selectedModel) }
    var demoOn by remember { mutableStateOf(BuildConfig.TOOLS && SimMode.active(context)) }
    // The LE Audio page only shows on phones and models that can use it, so it drops out of
    // order immediately when !selectedModel.supportsLeAudio without progress-bar flicker.
    val leAudio = remember { LeAudio.support(context) != LeAudio.Support.NOT_SUPPORTED }
    val order = remember(selectedModel, leAudio) { onboardingSteps(selectedModel, leAudio) }
    fun advanceFrom(fromStep: OnboardingStep, steps: List<OnboardingStep>): OnboardingStep {
        val idx = steps.indexOf(fromStep)
        val candidate = steps[(idx + 1).coerceAtMost(steps.lastIndex)]
        return if (candidate == OnboardingStep.NEARBY && OnboardingStore.hasNearby(context)) {
            steps[(idx + 2).coerceAtMost(steps.lastIndex)]
        } else candidate
    }
    fun next() { step = advanceFrom(step, order) }
    fun syncDemoState(enable: Boolean, targetModel: EarbudModel) {
        if (!BuildConfig.TOOLS) return
        if (enable) SimMode.startDemo(context, targetModel) else SimMode.stopDemo(context)
        if (step.ordinal > OnboardingStep.DEVICE.ordinal) EarbudModel.choose(context, selectedModel)
        vm.reloadCached()
        demoOn = enable
    }
    fun selectModelAndNext(picked: EarbudModel) {
        val cachedModel = EarbudModel.fromName(SettingsStore.modelName(context))
        val previousChosen = EarbudModel.chosen(context)
        val switched = (cachedModel != null && cachedModel != picked) || previousChosen != picked
        if (BuildConfig.TOOLS && demoOn) {
            SimMode.startDemo(context, picked)
        } else if (switched) {
            resetDeviceState(context, "setup: model switched", closeSession = false)
            scope.launch(Dispatchers.IO) { SonyControlSessionManager.closeForReset(context, "setup: model switched") }
        }
        selectedModel = picked
        browsedModel = picked
        EarbudModel.choose(context, picked)
        vm.reloadCached()
        step = advanceFrom(OnboardingStep.DEVICE, onboardingSteps(picked, leAudio))
    }
    // Material predictive back: the step shrinks toward the finger, and letting go returns to it.
    var backProgress by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var backCommitted by remember { mutableStateOf(false) }
    androidx.activity.compose.PredictiveBackHandler(enabled = step != OnboardingStep.WELCOME) { events ->
        try {
            events.collect { backProgress = it.progress }
            backCommitted = true
            var prev = order[(order.indexOf(step) - 1).coerceAtLeast(0)]
            if (prev == OnboardingStep.NEARBY && OnboardingStore.hasNearby(context)) {
                prev = OnboardingStep.DEVICE
            }
            step = prev
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            androidx.compose.animation.core.animate(backProgress, 0f, animationSpec = androidx.compose.animation.core.spring(stiffness = 500f)) { v, _ -> backProgress = v }
        }
    }
    val tick = resumeTick()
    LaunchedEffect(step, tick) {
        if (step.ordinal > OnboardingStep.NEARBY.ordinal && !OnboardingStore.hasNearby(context)) step = OnboardingStep.NEARBY
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Box(Modifier.fillMaxSize()) {
      Column(Modifier.fillMaxSize().systemBarsPadding()) {
        // One bar for the whole setup, so it fills smoothly from step to step.
        val fraction by androidx.compose.animation.core.animateFloatAsState(
            order.indexOf(step).coerceAtLeast(0) / (order.size - 1).toFloat(),
            androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessLow),
            label = "progress"
        )
        androidx.compose.animation.AnimatedVisibility(step != OnboardingStep.WELCOME, enter = fadeIn(), exit = fadeOut()) {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = if (BuildConfig.TOOLS) 34.dp else 16.dp).clip(CircleShape)
            )
        }
        AnimatedContent(
            targetState = step,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                if (backCommitted) return@AnimatedContent fadeIn(tween(120)).togetherWith(fadeOut(tween(80)))
                val forward = targetState.ordinal > initialState.ordinal
                // Material shared axis X, the same motion as pages in the app.
                (slideInHorizontally(tween(320, easing = Motion.Arrive)) { if (forward) it / 4 else -it / 4 } + fadeIn(tween(200, delayMillis = 60)))
                    .togetherWith(slideOutHorizontally(tween(320, easing = Motion.Arrive)) { if (forward) -it / 4 else it / 4 } + fadeOut(tween(90)))
            },
            label = "onboarding"
        ) { s ->
          LaunchedEffect(s) { backCommitted = false; backProgress = 0f }
          Box(
            if (s == step) Modifier.graphicsLayer {
                val p = backProgress
                scaleX = 1f - 0.1f * p; scaleY = 1f - 0.1f * p
                translationX = p * 24.dp.toPx()
                alpha = 1f - 0.25f * p
                shape = RoundedCornerShape((28 * p).dp)
                clip = p > 0f
            } else Modifier
          ) {
            when (s) {
                OnboardingStep.WELCOME -> WelcomeStep(onStart = ::next)
                OnboardingStep.DEVICE -> DeviceStep(
                    initialModel = selectedModel,
                    onModelChange = { browsedModel = it },
                    onContinue = ::selectModelAndNext
                )
                OnboardingStep.NEARBY -> NearbyStep(selectedModel, tick, onNext = ::next)
                OnboardingStep.EARBUDS -> EarbudsStep(
                    vm = vm,
                    model = selectedModel,
                    demo = demoOn,
                    onEnableDemo = { syncDemoState(true, selectedModel) },
                    tick = tick,
                    onNext = ::next
                )
                OnboardingStep.LEAUDIO -> LeAudioStep(vm, selectedModel, onNext = { if (step == OnboardingStep.LEAUDIO) next() })
                OnboardingStep.UPDATES -> UpdatesStep(selectedModel, tick, onNext = ::next)
                OnboardingStep.EXTRAS -> ExtrasStep(selectedModel, tick, onNext = ::next)
                OnboardingStep.DONE -> DoneStep(vm, selectedModel, onDone = onDone)
            }
          }
        }
      }
      // Debug builds only: explicit Demo toggle pill so real Bluetooth or simulated walkthroughs can be chosen.
      if (BuildConfig.TOOLS) {
          val pillColors = MaterialTheme.colorScheme
          Surface(
              onClick = {
                  val target = if (step == OnboardingStep.DEVICE) browsedModel else selectedModel
                  syncDemoState(!demoOn, target)
              },
              shape = CircleShape,
              color = if (demoOn) pillColors.primaryContainer else pillColors.secondaryContainer,
              contentColor = if (demoOn) pillColors.onPrimaryContainer else pillColors.onSecondaryContainer,
              modifier = Modifier
                  .align(Alignment.TopEnd)
                  .systemBarsPadding()
                  .padding(top = 4.dp, end = 16.dp)
                  .semantics { contentDescription = "Demo mode ${if (demoOn) "on" else "off"}" }
          ) {
              Text(
                  if (demoOn) "Demo: On" else "Demo: Off",
                  style = MaterialTheme.typography.labelSmall,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
              )
          }
      }
    }
    }
}

/** One page: a picture or icon, a title, a short explanation, actions at the bottom. */
@Composable
private fun Page(
    icon: ImageVector? = null,
    tone: Color? = null,
    art: (@Composable () -> Unit)? = null,
    title: String,
    body: String,
    primary: String?,
    onPrimary: () -> Unit = {},
    primaryEnabled: Boolean = true,
    secondary: String? = null,
    onSecondary: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (art != null) art()
            else if (icon != null) {
                // The app's icon language, larger: a tonal cookie in the theme's colours. Problems
                // use the error container instead.
                val warn = tone != null
                Box(
                    Modifier.size(96.dp).clip(CookieShape()).background(if (warn) colors.errorContainer else colors.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) { Icon(icon, contentDescription = null, tint = if (warn) colors.onErrorContainer else colors.onSecondaryContainer, modifier = Modifier.size(44.dp)) }
            }
            Spacer(Modifier.height(28.dp))
            Text(
                title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(Modifier.height(12.dp))
            Text(body, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            content()
            Spacer(Modifier.height(16.dp))
        }
        if (primary != null) {
            Button(onClick = onPrimary, enabled = primaryEnabled, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(primary) }
        }
        if (secondary != null) {
            TextButton(onClick = onSecondary, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text(secondary) }
        } else Spacer(Modifier.height(8.dp))
    }
}

// -------------------------------------------------------------------------------- 1 welcome

@Composable
private fun WelcomeStep(onStart: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<Pair<String, Backup.Summary>?>(null) }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            withContext(Dispatchers.IO) { runCatching { Backup.read(context, uri).let { it to Backup.inspect(it) } } }
                .onSuccess { pending = it }
                .onFailure { android.widget.Toast.makeText(context, it.message ?: "Couldn't open this file. Choose a SunPods backup.", android.widget.Toast.LENGTH_LONG).show() }
        }
    }
    val colors = MaterialTheme.colorScheme
    Page(
        art = {
            // The one live 3D hero: it steps through every supported model (EarbudModel.entries, so new
            // models join by themselves), each in black, even when the user's own model is already known.
            Hero3D(
                centered = true,
                caseProgress = 0f,
                device = EarbudModel.entries.first().id,
                color = "black",
                cycleDevices = EarbudModel.entries.map { it.id },
                modifier = Modifier.fillMaxWidth(0.8f).height(200.dp)
                    .semantics { contentDescription = "Sony earbuds and headphones" }
                    .drawBehind {
                        drawCircle(
                            Brush.radialGradient(listOf(colors.primary.copy(alpha = 0.25f), Color.Transparent), center, size.height / 1.6f),
                            radius = size.height / 1.6f
                        )
                    }
            )
        },
        title = "Welcome to SunPods",
        body = "Everything your Sony earbuds and headphones can do, without the clutter.",
        primary = "Get Started",
        onPrimary = onStart,
        secondary = "Restore from Backup",
        onSecondary = { open.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
    ) {
        Feature(Icons.Rounded.BatteryChargingFull, "Battery at a Glance", "Widgets, low battery alerts and battery health")
        Feature(Icons.Rounded.GraphicEq, "Your Sound, One Tap Away", "Noise control, EQ, touch controls and more")
        Feature(Icons.Rounded.Lock, "Private by Design", "No account and no tracking. Your data stays on this phone.")
    }
    pending?.let { (text, s) ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Restore This Backup?") },
            text = { Text("This backup has ${s.settings} settings and ${s.historyRows} battery readings. SunPods will restart so you can finish setting up.") },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { runCatching { Backup.restore(context, text) }.isSuccess }
                        if (ok) Backup.restart(context)
                    }
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, body: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// -------------------------------------------------------------------------------- 2 choose your device

/**
 * Everyone picks their own device; nothing is guessed or suggested here. An Earbuds | Headphones switch
 * chooses the family, then one device at a time in the pager: swipe to change it.
 * "See All" lists every model by series, with search.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun DeviceStep(
    initialModel: EarbudModel,
    onModelChange: (EarbudModel) -> Unit,
    onContinue: (EarbudModel) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val reduceMotion = rememberReduceMotion()
    var headphones by rememberSaveable { mutableStateOf(initialModel.headphones) }
    var selectedModel by rememberSaveable { mutableStateOf(initialModel) }
    val models = remember(headphones) { EarbudModel.entries.filter { it.headphones == headphones } }
    LaunchedEffect(selectedModel) { onModelChange(selectedModel) }
    var showAll by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Spacer(Modifier.height(32.dp))
        Text(
            "Choose Your Device", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center,
            maxLines = 1, modifier = Modifier.fillMaxWidth().semantics { heading() }
        )
        Spacer(Modifier.height(16.dp))
        FamilySwitch(
            labels = listOf("Earbuds", "Headphones"), selected = if (headphones) 1 else 0, reduceMotion = reduceMotion,
            onSelect = { i ->
                val hp = i == 1
                if (headphones != hp) {
                    headphones = hp
                    selectedModel = EarbudModel.entries.first { it.headphones == hp }
                }
            }
        )
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
          val heroH = minOf(300.dp, maxHeight - 130.dp).coerceAtLeast(180.dp)
          Box(Modifier.fillMaxWidth().height(heroH + 110.dp)) {
            // Key by family so switching between Earbuds and Headphones never shrinks an active PagerState under HorizontalPager.
            androidx.compose.runtime.key(headphones) {
                val initialPage = remember(headphones) { models.indexOf(selectedModel).coerceAtLeast(0) }
                val pager = androidx.compose.foundation.pager.rememberPagerState(initialPage = initialPage) { models.size }
                LaunchedEffect(pager.currentPage, models) {
                    val m = models.getOrNull(pager.currentPage) ?: models.firstOrNull() ?: return@LaunchedEffect
                    selectedModel = m
                }
                fun goTo(i: Int) {
                    val clamped = i.coerceIn(0, models.lastIndex)
                    scope.launch { if (reduceMotion) pager.scrollToPage(clamped) else pager.animateScrollToPage(clamped) }
                }
                androidx.compose.foundation.pager.HorizontalPager(
                    state = pager, modifier = Modifier.fillMaxSize(), key = { models.getOrNull(it)?.id ?: it }
                ) { page ->
                    val m = models.getOrNull(page) ?: return@HorizontalPager
                    Column(
                        Modifier.fillMaxSize().semantics(mergeDescendants = true) {
                            contentDescription = "${m.displayName}, ${m.noun}. ${page + 1} of ${models.size}"
                            val actions = mutableListOf<CustomAccessibilityAction>()
                            if (page > 0) actions += CustomAccessibilityAction("Previous device") { goTo(page - 1); true }
                            if (page < models.size - 1) actions += CustomAccessibilityAction("Next device") { goTo(page + 1); true }
                            customActions = actions
                        },
                        verticalArrangement = Arrangement.Top, horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(budArt(null, m).pair),
                            contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth(0.86f).height(heroH)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            m.displayName, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center, maxLines = 1
                        )
                    }
                }
                // Page dots; each is a 48dp target that jumps to its device.
                if (models.size > 1) Row(Modifier.align(Alignment.BottomCenter), verticalAlignment = Alignment.CenterVertically) {
                    models.forEachIndexed { i, m ->
                        val on = i == pager.currentPage
                        val w by androidx.compose.animation.core.animateDpAsState(
                            if (on) 20.dp else 8.dp,
                            if (reduceMotion) androidx.compose.animation.core.snap() else androidx.compose.animation.core.tween(200, easing = Motion.Arrive),
                            label = "dot"
                        )
                        Box(
                            Modifier.size(48.dp)
                                .selectable(selected = on, role = Role.Tab, onClick = { goTo(i) })
                                .semantics { contentDescription = "Show ${m.displayName}" },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(Modifier.size(width = w, height = 8.dp).clip(CircleShape).background(if (on) colors.primary else colors.outlineVariant))
                        }
                    }
                }
            }
          }
        }
        Button(
            onClick = { onContinue(selectedModel) },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) { Text("Continue") }
        TextButton(onClick = { showAll = true }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("See All") }
    }
    if (showAll) AllDevicesSheet(
        onPick = { m -> showAll = false; selectedModel = m; onContinue(m) },
        onDismiss = { showAll = false }
    )
}

/** A One UI style two-way switch: a pill track with a tonal pill that slides under the chosen label. */
@Composable
private fun FamilySwitch(labels: List<String>, selected: Int, reduceMotion: Boolean, onSelect: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    androidx.compose.foundation.layout.BoxWithConstraints(
        Modifier.fillMaxWidth().height(56.dp).clip(CircleShape).background(colors.surfaceContainerHigh).padding(4.dp)
    ) {
        val seg = maxWidth / labels.size
        val x by androidx.compose.animation.core.animateDpAsState(
            seg * selected,
            if (reduceMotion) androidx.compose.animation.core.snap()
            else androidx.compose.animation.core.spring(dampingRatio = 1f, stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow),
            label = "switch"
        )
        Box(Modifier.offset(x = x).width(seg).fillMaxHeight().clip(CircleShape).background(colors.secondaryContainer))
        Row(Modifier.fillMaxSize().selectableGroup()) {
            labels.forEachIndexed { i, label ->
                val on = i == selected
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(CircleShape).selectable(selected = on, role = Role.Tab, onClick = { onSelect(i) }),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, maxLines = 1,
                        color = if (on) colors.onSecondaryContainer else colors.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** Every supported model by series, with search. Everything comes from [EarbudModel.entries]. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AllDevicesSheet(onPick: (EarbudModel) -> Unit, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    var query by rememberSaveable { mutableStateOf("") }
    val groups = remember(query) { groupBySeries(query) }
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f).imePadding().padding(horizontal = 24.dp)) {
            Text("All Devices", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(12.dp))
            androidx.compose.material3.OutlinedTextField(
                value = query, onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                singleLine = true, shape = CircleShape,
                placeholder = { Text("Search") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = if (query.isNotEmpty()) ({
                    androidx.compose.material3.IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, contentDescription = "Clear search") }
                }) else null
            )
            androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
                if (groups.isEmpty()) item {
                    Text("Nothing matches that.", style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant, modifier = Modifier.padding(vertical = 24.dp))
                }
                groups.forEach { g ->
                    item(key = "series-" + g.series) {
                        Text(
                            g.title, style = MaterialTheme.typography.titleSmall, color = colors.primary,
                            modifier = Modifier.padding(top = 20.dp, bottom = 4.dp).semantics { heading() }
                        )
                    }
                    items(g.models.size, key = { g.models[it].id }) { i ->
                        val m = g.models[i]
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 76.dp).clip(RoundedCornerShape(16.dp)).clickable(role = Role.Button) { onPick(m) }
                                .semantics(mergeDescendants = true) { contentDescription = "${m.displayName}, ${m.noun}" },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // The model's own black picture on the same tonal cookie as every other icon.
                            Box(
                                Modifier.size(64.dp).clip(CookieShape()).background(colors.surfaceContainerHighest),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painterResource(budArt(0x01, m).pair), contentDescription = null,
                                    contentScale = androidx.compose.ui.layout.ContentScale.Fit, modifier = Modifier.size(60.dp)
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(m.displayName, style = MaterialTheme.typography.titleSmall)
                                Text(m.titleNoun, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                            }
                        }
                    }
                }
                item {
                    Text(
                        "More Sony models are coming.", style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(top = 24.dp)
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------- 3 nearby devices

@Composable
private fun NearbyStep(model: EarbudModel, tick: Int, onNext: () -> Unit) {
    val context = LocalContext.current
    if (Build.VERSION.SDK_INT < 31) { LaunchedEffect(Unit) { onNext() }; return }
    val gate = rememberPermission(listOf(Manifest.permission.BLUETOOTH_CONNECT), tick)
    LaunchedEffect(gate.state) { if (gate.state == Perm.GRANTED) onNext() }
    when (gate.state) {
        Perm.ASK, Perm.GRANTED -> Page(
            icon = Icons.Rounded.Bluetooth,
            title = "Connect to Your ${model.titleNoun}",
            body = "SunPods uses Bluetooth to talk to your ${model.displayName}. Android calls this Nearby devices.",
            primary = "Allow", onPrimary = gate.request
        )
        Perm.DENIED -> Page(
            icon = Icons.Rounded.ErrorOutline, tone = MaterialTheme.colorScheme.error,
            title = "SunPods Needs Nearby Devices",
            body = "Without it, SunPods can't show battery levels or change settings. It's only used to connect to your ${model.displayName}.",
            primary = "Allow", onPrimary = gate.request,
            secondary = "Open Settings", onSecondary = { openAppSettings(context) }
        )
        Perm.BLOCKED -> Page(
            icon = Icons.Rounded.ErrorOutline, tone = MaterialTheme.colorScheme.error,
            title = "Turn On Nearby Devices in Settings",
            body = "Android won't ask again. In Settings, tap Permissions, then Nearby devices, then Allow. SunPods continues when you return.",
            primary = "Open Settings", onPrimary = { openAppSettings(context) }
        )
    }
}

// -------------------------------------------------------------------------------- 4 earbuds

private sealed interface Buds {
    data object Checking : Buds
    data object BluetoothOff : Buds
    data object NotPaired : Buds
    data object Searching : Buds
    data object Pairing : Buds
    data object Waiting : Buds
    data object Connected : Buds
    data class Failed(val message: String) : Buds
}

private fun bluetoothOn(context: Context) =
    context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true

@SuppressLint("MissingPermission")
@Composable
private fun EarbudsStep(
    vm: SunpodsViewModel,
    model: EarbudModel,
    demo: Boolean,
    onEnableDemo: () -> Unit,
    tick: Int,
    onNext: () -> Unit,
) {
    val context = LocalContext.current
    var state by remember(model) { mutableStateOf<Buds>(if (demo) Buds.NotPaired else Buds.Checking) }
    var refreshed by remember(model) { mutableStateOf(false) }
    /** "Pair different earbuds": stay on the pairing page even though this model is paired. */
    var pairNew by remember(model) { mutableStateOf(false) }
    /** A short line under the pairing steps, e.g. after the search was cancelled. */
    var note by remember(model) { mutableStateOf<String?>(null) }
    /** When the paired device started "waiting to connect", to offer help if it drags on. */
    var waitingSince by remember(model) { mutableStateOf(0L) }
    /** Address picked in CompanionDeviceManager, so unrelated bond events don't hijack pairing. */
    var pairingAddress by remember(model) { mutableStateOf<String?>(null) }
    val busy by vm.busy.collectAsState()
    val leScope = rememberCoroutineScope()

    var wasDemo by remember(model) { mutableStateOf(demo) }
    LaunchedEffect(demo, model) {
        if (demo == wasDemo) return@LaunchedEffect
        wasDemo = demo
        refreshed = false
        if (demo) {
            if (state !is Buds.Pairing && state !is Buds.Connected) state = Buds.NotPaired
        } else {
            state = Buds.Checking
        }
    }

    // Watch Bluetooth, pairing and the audio link for the chosen model only — never hijack to another model.
    LaunchedEffect(tick, demo, model) {
        while (true) {
            if (demo) {
                if (state is Buds.Connected && !refreshed) { refreshed = true; vm.refreshAll() }
                delay(1_500)
                continue
            }
            val (btOn, bonded, live) = withContext(Dispatchers.IO) {
                val bt = bluetoothOn(context)
                val b = bondedForModel(context, model)
                val l = if (bt && b.isNotEmpty()) modelAudioLinkUp(context, model, b) else false
                Triple(bt, b, l)
            }
            if (EarbudModel.active != model) EarbudModel.choose(context, model)
            val s = state
            // Paired some other way while searching, such as through Google's Fast Pair popup.
            if ((s is Buds.Searching || s is Buds.Pairing) && live) {
                pairNew = false
                state = Buds.Connected
            }
            if (s !is Buds.Searching && s !is Buds.Pairing && s !is Buds.Failed) {
                state = when {
                    !btOn -> Buds.BluetoothOff
                    pairNew || bonded.isEmpty() -> Buds.NotPaired
                    live -> Buds.Connected
                    else -> Buds.Waiting
                }
            }
            if (state is Buds.Connected && !refreshed) { refreshed = true; vm.refreshAll() }
            if (state is Buds.Waiting) { if (waitingSince == 0L) waitingSince = System.currentTimeMillis() } else waitingSince = 0L
            delay(1_500)
        }
    }

    val chooser = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            // Closing the picker after pairing through Google's own popup is fine, if THIS model connected.
            leScope.launch {
                if (modelAudioLinkUp(context, model)) {
                    note = null; pairNew = false; state = Buds.Connected
                } else {
                    note = "Search stopped. When ${model.lightHint}, tap Start Searching."
                    state = Buds.NotPaired
                }
            }
            return@rememberLauncherForActivityResult
        }
        note = null
        pairNew = false
        val device = pickedDevice(context, result.data)
        if (device == null) { state = Buds.Failed("Couldn't recognise that device. Try again."); return@rememberLauncherForActivityResult }
        pairingAddress = device.address
        state = if (device.bondState == BluetoothDevice.BOND_BONDED) Buds.Waiting
        else if (device.createBond()) Buds.Pairing else Buds.Failed("Pairing didn't start. Make sure ${model.lightHint}, then try again.")
    }

    if (state is Buds.Pairing && !demo) {
        DisposableEffect(model, pairingAddress) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    val dev = if (Build.VERSION.SDK_INT >= 33) {
                        i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    val targetAddr = pairingAddress
                    if (targetAddr != null && dev != null && !targetAddr.equals(dev.address, ignoreCase = true)) return
                    when (i.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)) {
                        BluetoothDevice.BOND_BONDED -> {
                            state = Buds.Waiting
                            // Same press, both sides: the LE Audio side too, if supported and Shizuku is set up.
                            if (model.supportsLeAudio) {
                                leScope.launch { LePairing.alsoPairLe(context.applicationContext) { DiagnosticsStore.add(context, "Setup: $it") } }
                            }
                        }
                        BluetoothDevice.BOND_NONE -> state = Buds.Failed("Pairing didn't finish. Put them back in pairing mode and try again.")
                    }
                }
            }
            ContextCompat.registerReceiver(context, receiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED), ContextCompat.RECEIVER_EXPORTED)
            onDispose { runCatching { context.unregisterReceiver(receiver) } }
        }
    }

    // Some phones never answer a search or a pairing request; don't leave a spinner forever.
    LaunchedEffect(state, demo) {
        when (state) {
            Buds.Searching -> { delay(60_000); if (state is Buds.Searching) state = Buds.Failed("Nothing found. Make sure ${model.lightHint} and your ${model.noun} are next to your phone.") }
            Buds.Pairing -> if (!demo) { delay(45_000); if (state is Buds.Pairing) state = Buds.Failed("Pairing took too long. Put them back in pairing mode and try again.") }
            Buds.Connected -> if (!refreshed) { refreshed = true; vm.refreshAll() }
            else -> Unit
        }
    }

    fun search(forceDemo: Boolean = false) {
        if (demo || forceDemo) {
            note = null
            state = Buds.Pairing
            leScope.launch {
                delay(1_500)
                if (BuildConfig.TOOLS && SimMode.active(context) && state is Buds.Pairing) state = Buds.Connected
            }
            return
        }
        note = null
        val cdm = context.getSystemService(CompanionDeviceManager::class.java)
        if (cdm == null) { state = Buds.Failed("SunPods can't search for ${model.noun} on this phone. Pair them in Bluetooth settings instead."); return }
        state = Buds.Searching
        val filter = BluetoothDeviceFilter.Builder()
            .setNamePattern(model.namePattern)
            .build()
        val request = AssociationRequest.Builder().addDeviceFilter(filter).setSingleDevice(false).build()
        val callback = object : CompanionDeviceManager.Callback() {
            @Deprecated("Replaced by onAssociationPending on Android 13, which calls this by default")
            override fun onDeviceFound(chooserLauncher: IntentSender) {
                chooser.launch(IntentSenderRequest.Builder(chooserLauncher).build())
            }
            override fun onFailure(error: CharSequence?) {
                state = Buds.Failed("Nothing found. Make sure ${model.lightHint} and your ${model.noun} are next to your phone.")
            }
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= 33) cdm.associate(request, context.mainExecutor, callback)
            else @Suppress("DEPRECATION") cdm.associate(request, callback, null)
        }.onFailure { state = Buds.Failed("Couldn't start searching. Pair them in Bluetooth settings instead.") }
    }

    val battery by vm.battery.collectAsState()
    androidx.compose.animation.AnimatedContent(
        targetState = state,
        contentKey = { it::class },
        transitionSpec = { fadeThrough() },
        label = "earbuds"
    ) { s ->
    when (s) {
        Buds.Checking -> Page(icon = Icons.Rounded.Headphones, title = "Looking for Your ${model.titleNoun}", body = "One moment.", primary = null) {
            CircularProgressIndicator()
        }
        Buds.BluetoothOff -> Page(
            icon = Icons.Rounded.BluetoothDisabled,
            title = "Bluetooth Is Off", body = "Turn on Bluetooth to connect to your ${model.noun}.",
            primary = "Turn On Bluetooth",
            onPrimary = { runCatching { context.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }.onFailure { openBluetoothSettings(context) } },
            secondary = "Not Now", onSecondary = onNext
        ) {
            if (BuildConfig.TOOLS && !demo) {
                TextButton(onClick = onEnableDemo) { Text("Try in Demo Mode") }
            }
        }
        Buds.NotPaired -> Page(
            art = {
                RenderClip(
                    remember(model) { budArt(0x01, model).pairClip },
                    model.pairClipDescription,
                    Modifier.fillMaxWidth().height(260.dp)
                )
            },
            title = "Get Ready to Pair",
            body = "Put them in pairing mode, then tap Start Searching.",
            primary = "Start Searching", onPrimary = { search() },
            secondary = "Not Now", onSecondary = onNext
        ) {
            SunCard {
                model.pairSteps.forEachIndexed { i, step ->
                    if (i > 0) RowDivider()
                    HowToRow(i + 1, step)
                }
            }
            note?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
            }
            if (BuildConfig.TOOLS && !demo && !pairNew) {
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { onEnableDemo(); search(forceDemo = true) }) { Text("Try in Demo Mode") }
            }
        }
        Buds.Searching -> Page(icon = Icons.Rounded.BluetoothSearching, title = "Searching", body = "Choose your ${model.displayName} when it appears. Keep them near your phone.", primary = null) {
            CircularProgressIndicator()
        }
        Buds.Pairing -> Page(icon = Icons.Rounded.BluetoothSearching, title = "Pairing", body = "If Android asks you to confirm, tap Pair.", primary = null) {
            CircularProgressIndicator()
        }
        Buds.Waiting -> Page(
            art = {
                RenderClip(
                    remember(model) { budArt(0x01, model).openClip },
                    if (model.headphones) "The headphones turn to face you." else "The case opens and both earbuds lift out.",
                    Modifier.fillMaxWidth().height(260.dp)
                )
            },
            title = if (model.headphones) "Turn On Your Headphones" else "Open the Case Near Your Phone",
            body = if (model.headphones) "Your ${model.noun} are paired. ${model.wakeHint}, and they'll connect."
                else "Your earbuds are paired. Open the lid or take them out, and they'll connect.",
            primary = null,
            secondary = "Not Now", onSecondary = onNext
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(24.dp))
            var now by remember { mutableStateOf(System.currentTimeMillis()) }
            LaunchedEffect(Unit) { while (true) { delay(5_000); now = System.currentTimeMillis() } }
            if (waitingSince > 0 && now - waitingSince > 30_000) {
                SetupInfo(
                    Icons.Rounded.ErrorOutline, "Taking a While?",
                    "Make sure your ${model.noun} have some charge. If they're connected to another phone or computer, disconnect them there."
                )
                Spacer(Modifier.height(8.dp))
            }
            TextButton(onClick = { openBluetoothSettings(context) }) { Text("Connect in Bluetooth Settings") }
            TextButton(onClick = { pairNew = true; state = Buds.NotPaired }) { Text("Pair Different ${model.titleNoun}") }
        }
        Buds.Connected -> {
            val read = battery.left != null || battery.right != null
            if (!read && refreshed && !busy) Page(
                // Linked over Bluetooth, but the control channel didn't answer (Sound Connect holds it, usually).
                icon = Icons.Rounded.ErrorOutline, tone = MaterialTheme.colorScheme.error,
                title = "Can't Reach Your ${model.titleNoun}",
                body = "Your ${model.noun} are connected, but another app may be using them. Close Sony Sound Connect if it's open, then try again.",
                primary = "Try Again", onPrimary = { vm.refreshAll() },
                secondary = "Continue Anyway", onSecondary = onNext
            ) else Page(
                icon = Icons.Rounded.CheckCircle,
                title = "Connected",
                body = if (read) "Here's your battery, straight from your ${model.noun}." else "Checking battery levels.",
                primary = "Continue", onPrimary = onNext
            ) {
                if (read) Levels(battery, model) else CircularProgressIndicator()
            }
        }
        is Buds.Failed -> Page(
            icon = Icons.Rounded.ErrorOutline, tone = MaterialTheme.colorScheme.error,
            title = "Couldn't Pair", body = s.message,
            primary = "Try Again", onPrimary = { state = Buds.NotPaired },
            secondary = "Open Bluetooth Settings", onSecondary = { openBluetoothSettings(context) }
        )
    }
    }
}

private fun openBluetoothSettings(context: Context) = runCatching { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }

@SuppressLint("MissingPermission")
private fun pickedDevice(context: Context, data: Intent?): BluetoothDevice? {
    data ?: return null
    @Suppress("DEPRECATION")
    val direct: BluetoothDevice? = if (Build.VERSION.SDK_INT >= 33)
        data.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE, BluetoothDevice::class.java)
    else data.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE)
    if (direct != null) return direct
    if (Build.VERSION.SDK_INT >= 33) {
        val mac = data.getParcelableExtra(CompanionDeviceManager.EXTRA_ASSOCIATION, AssociationInfo::class.java)?.deviceMacAddress
        if (mac != null) return context.getSystemService(BluetoothManager::class.java)?.adapter?.getRemoteDevice(mac.toString().uppercase())
    }
    return null
}

/** Live levels as proof it works. */
@Composable
private fun Levels(battery: BatteryState, model: EarbudModel) {
    SunCard {
    Row(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        model.batteries.forEach { part ->
            val label = part.label
            val level = part.level(battery)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(level?.takeIf { it in 1..100 }?.let { "$it%" } ?: part.missing, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    }
}

// -------------------------------------------------------------------------------- 3b LE Audio

/** Offers LE Audio right after pairing, using the same switch as Controls (Shizuku first). */
@Composable
private fun LeAudioStep(vm: SunpodsViewModel, model: EarbudModel, onNext: () -> Unit) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsState()
    var switching by remember { mutableStateOf(false) }
    var on by remember { mutableStateOf(LeAudio.anyConnected(context)) }
    // The full check (Bluetooth stack plus its LE Audio service) and the earbuds' own answer.
    // If either says no, this step isn't for this phone: move on without showing it.
    LaunchedEffect(model) {
        val phone = withContext(Dispatchers.IO) { LeAudio.verify(context) }
        if (phone == LeAudio.Support.NOT_SUPPORTED || settings.leModeSupported == false) {
            DiagnosticsStore.add(context, "Onboarding: LE Audio step skipped (model ${model.id}, phone $phone, earbuds ${settings.leModeSupported})")
            onNext()
        }
    }
    Page(
        icon = androidx.compose.material.icons.Icons.Rounded.Call,
        title = if (on) "You're on LE Audio" else "Clearer Calls with LE Audio",
        body = if (on) "Your ${model.noun} are connected with LE Audio. You can switch back to Classic anytime in Controls > Calls."
        else "Your voice sounds clearer on calls. Connect to Two Devices and LDAC turn off while it's on. Switch back anytime in Controls > Calls.",
        primary = if (on) "Continue" else "Use LE Audio",
        onPrimary = { if (on) onNext() else switching = true },
        secondary = if (on) null else "Not Now",
        onSecondary = onNext
    )
    if (switching) LeSwitchSheet(on = true, settings = settings, closeOnSuccess = true) {
        switching = false
        on = LeAudio.anyConnected(context)
    }
}

// -------------------------------------------------------------------------------- 4 stay up to date

@Composable
private fun UpdatesStep(model: EarbudModel, tick: Int, onNext: () -> Unit) {
    val context = LocalContext.current
    val notify = if (Build.VERSION.SDK_INT >= 33) rememberPermission(listOf(Manifest.permission.POST_NOTIFICATIONS), tick) else null
    val unrestricted = remember(tick) { BackgroundAccess.unrestricted(context) }
    val allSet = (notify == null || notify.state == Perm.GRANTED) && unrestricted
    Page(
        icon = Icons.Rounded.Notifications,
        title = "Stay Up to Date",
        body = "Keep the widget, alerts and automations working when SunPods is closed.",
        primary = if (allSet) "Continue" else "Continue Without These",
        onPrimary = onNext
    ) {
        Column(Modifier.fillMaxWidth()) {
            SunCard {
                if (notify != null) {
                    SetupRow(
                        Icons.Rounded.Notifications, "Notifications", "Low battery alerts",
                        done = notify.state == Perm.GRANTED,
                        action = if (notify.state == Perm.BLOCKED) "Settings" else "Allow"
                    ) { if (notify.state == Perm.BLOCKED) openNotificationSettings(context) else notify.request() }
                    RowDivider()
                }
                SetupRow(
                    Icons.Rounded.BatteryChargingFull, "Run in Background", "So your phone doesn't close SunPods",
                    done = unrestricted, action = "Allow"
                ) { BackgroundAccess.request(context) }
            }
            Footer(
                if (allSet) "SunPods checks your ${model.noun} every few minutes, only while they're connected."
                else "Without these, the widget can fall behind and alerts may not arrive."
            )
        }
    }
}

/** A row in a setup card: icon tile, title and a short note, then a button that turns into a tick once it's done. */
@Composable
private fun SetupRow(icon: ImageVector, title: String, body: String, done: Boolean, action: String, onAction: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    SettingRow(icon, title, note = body) {
        androidx.compose.animation.AnimatedContent(
            done,
            transitionSpec = {
                (fadeIn(tween(200, delayMillis = 60)) + androidx.compose.animation.scaleIn(tween(260, easing = Motion.Arrive), initialScale = 0.6f))
                    .togetherWith(fadeOut(tween(90)))
            },
            label = "setup"
        ) { d ->
            if (d) Icon(Icons.Rounded.CheckCircle, contentDescription = "On", tint = colors.primary, modifier = Modifier.size(28.dp))
            else FilledTonalButton(onClick = onAction) { Text(action) }
        }
    }
}

// -------------------------------------------------------------------------------- 5 extras

@Composable
private fun ExtrasStep(model: EarbudModel, tick: Int, onNext: () -> Unit) {
    val context = LocalContext.current
    val location = rememberPermission(listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), tick)
    val background = rememberPermission(listOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), tick)
    var lastSeen by remember { mutableStateOf(LastSeenStore.enabled(context)) }
    var wantLastSeen by remember { mutableStateOf(false) }
    LaunchedEffect(location.state, wantLastSeen) {
        if (wantLastSeen && location.state == Perm.GRANTED) { LastSeenStore.setEnabled(context, true); lastSeen = true; wantLastSeen = false }
    }
    val colors = MaterialTheme.colorScheme
    val refused = !lastSeen && (location.state == Perm.DENIED || location.state == Perm.BLOCKED)
    Page(
        icon = Icons.Rounded.Tune,
        title = "Optional Extras",
        body = "Turn these on now or later.",
        primary = "Continue", onPrimary = onNext
    ) {
        Column(Modifier.fillMaxWidth()) {
            SunCard {
                SettingRow(
                    Icons.Rounded.LocationOn, "Last Seen",
                    note = "Where your ${model.noun} last disconnected",
                    modifier = Modifier.toggleable(value = lastSeen, role = androidx.compose.ui.semantics.Role.Switch) { on ->
                        if (!on) { LastSeenStore.setEnabled(context, false); lastSeen = false }
                        else if (location.state == Perm.GRANTED) { LastSeenStore.setEnabled(context, true); lastSeen = true }
                        else if (location.state == Perm.BLOCKED) openAppSettings(context)
                        else { wantLastSeen = true; location.request() }
                    }
                ) { Switch(checked = lastSeen, onCheckedChange = null) }
                androidx.compose.animation.AnimatedVisibility(refused, enter = revealIn(), exit = revealOut()) {
                    Text(
                        if (location.state == Perm.BLOCKED) "Location is off for SunPods. Turn it on in Settings to use Last Seen."
                        else "Location wasn't allowed, so Last Seen is off.",
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(start = RowTextStart, end = 16.dp, bottom = 12.dp)
                    )
                }
                androidx.compose.animation.AnimatedVisibility(lastSeen && background.state != Perm.GRANTED, enter = revealIn(), exit = revealOut()) {
                    Column {
                        RowDivider()
                        SettingRow(
                            null, "Allow All the Time",
                            note = "Saves the spot even when SunPods is closed",
                            modifier = Modifier.clickable { if (background.state == Perm.BLOCKED) openAppSettings(context) else background.request() }
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = colors.outline)
                        }
                    }
                }
                RowDivider()
                SettingRow(Icons.Rounded.GraphicEq, "Automations", note = "Set them up later in Controls")
            }
            Footer("Locations stay on this phone. Automations change noise control on calls, while you walk or run, in Do Not Disturb, or by Wi-Fi.")
        }
    }
}

@Composable
private fun SetupInfo(icon: ImageVector, title: String, body: String) {
    SunCard { SettingRow(icon, title, note = body) }
}

/** A numbered instruction, for steps you do with your hands. */
@Composable
private fun HowToRow(n: Int, text: String) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(TileSize).clip(CookieShape()).background(colors.secondaryContainer), contentAlignment = Alignment.Center) {
            Text("$n", style = MaterialTheme.typography.titleMedium, color = colors.onSecondaryContainer)
        }
        Spacer(Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}

// -------------------------------------------------------------------------------- 6 done

@Composable
private fun DoneStep(vm: SunpodsViewModel, model: EarbudModel, onDone: () -> Unit) {
    val context = LocalContext.current
    val battery by vm.battery.collectAsState()
    val settings by vm.settings.collectAsState()
    val matchesModel = settings.modelName == null || model.matches(settings.modelName)
    val connected = matchesModel && (battery.left != null || battery.right != null) && audioLinkUp(context)
    var pinned by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    Page(
        icon = Icons.Rounded.CheckCircle,
        title = "You're All Set",
        body = if (connected) "Your ${model.noun} are ready." else "SunPods connects as soon as your ${model.noun} do.",
        primary = "Start Using SunPods",
        onPrimary = {
            OnboardingStore.setHeroIntroPending(context, connected)
            OnboardingStore.setDone(context, true)
            onDone()
        }
    ) {
        if (connected) { Levels(battery, model); Spacer(Modifier.height(24.dp)) }
        SunCard {
            SetupRow(
                Icons.Rounded.Widgets, "Add a Widget",
                if (pinned) "Added. Check your home screen" else "Your battery on the home screen",
                done = false, action = if (pinned) "Another" else "Choose"
            ) { picking = true }
        }
        if (picking) WidgetPickerSheet(onDismiss = { picking = false }, onRequested = { pinned = true })
    }
}
