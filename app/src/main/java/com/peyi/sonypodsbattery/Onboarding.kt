package com.peyi.sonypodsbattery

import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.clickable
import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
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
import java.util.regex.Pattern

/** Whether first-run setup has been completed. */
internal object OnboardingStore {
    private const val KEY = "onboarding_done"
    private fun p(c: Context) = c.getSharedPreferences("sunpods_ui", Context.MODE_PRIVATE)

    fun done(c: Context): Boolean {
        val p = p(c)
        if (p.contains(KEY)) return p.getBoolean(KEY, false)
        // People who already use SunPods skip it; Device > Help > Set up again brings it back.
        val existing = hasNearby(c) && File(c.filesDir, "battery_history.csv").exists()
        p.edit().putBoolean(KEY, existing).apply()
        return existing
    }

    fun setDone(c: Context, done: Boolean) = p(c).edit().putBoolean(KEY, done).commit()

    fun hasNearby(c: Context) = Build.VERSION.SDK_INT < 31 ||
        c.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
}

private enum class Step { WELCOME, NEARBY, EARBUDS, LEAUDIO, UPDATES, EXTRAS, DONE }

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
    return null
}

/** Ticks every time the screen comes back (from a system dialog or Settings), so checks re-run. */
@Composable
private fun resumeTick(): Int {
    var tick by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
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
    var step by rememberSaveable { mutableStateOf(Step.WELCOME) }
    val context = LocalContext.current
    // The LE Audio page only shows on phones that can use it.
    // Unknown (Bluetooth was off) still shows the step; the step checks properly and skips itself.
    val leAudio = remember { LeAudio.support(context) != LeAudio.Support.NOT_SUPPORTED }
    val order = Step.entries.filter { it != Step.LEAUDIO || leAudio }
    fun next() { step = order[(order.indexOf(step) + 1).coerceAtMost(order.lastIndex)] }
    // Material predictive back: the step shrinks toward the finger, and letting go returns to it.
    var backProgress by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var backCommitted by remember { mutableStateOf(false) }
    androidx.activity.compose.PredictiveBackHandler(enabled = step != Step.WELCOME) { events ->
        try {
            events.collect { backProgress = it.progress }
            backCommitted = true
            step = order[(order.indexOf(step) - 1).coerceAtLeast(0)]
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            androidx.compose.animation.core.animate(backProgress, 0f, animationSpec = androidx.compose.animation.core.spring(stiffness = 500f)) { v, _ -> backProgress = v }
        }
    }
    val tick = resumeTick()
    LaunchedEffect(step, tick) {
        if (step.ordinal > Step.NEARBY.ordinal && !OnboardingStore.hasNearby(context)) step = Step.NEARBY
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
      Column(Modifier.fillMaxSize().systemBarsPadding()) {
        // One bar for the whole setup, so it fills smoothly from step to step.
        val fraction by androidx.compose.animation.core.animateFloatAsState(
            order.indexOf(step).coerceAtLeast(0) / (order.size - 1).toFloat(),
            androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessLow),
            label = "progress"
        )
        androidx.compose.animation.AnimatedVisibility(step != Step.WELCOME, enter = fadeIn(), exit = fadeOut()) {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 16.dp).clip(CircleShape)
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
                Step.WELCOME -> WelcomeStep(onStart = ::next)
                Step.NEARBY -> NearbyStep(tick, onNext = ::next)
                Step.EARBUDS -> EarbudsStep(vm, tick, onNext = ::next)
                Step.LEAUDIO -> LeAudioStep(vm, onNext = ::next)
                Step.UPDATES -> UpdatesStep(tick, onNext = ::next)
                Step.EXTRAS -> ExtrasStep(tick, onNext = ::next)
                Step.DONE -> DoneStep(vm, onDone = onDone)
            }
          }
        }
      }
    }
}

/** One page: progress at the top, a picture or icon, a title, a short explanation, actions at the bottom. */
@Composable
private fun Page(
    progress: Float?,
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
    @Suppress("UNUSED_VARIABLE") val unusedProgress = progress // the bar lives in the setup shell now
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
        }
        if (primary != null) {
            Button(onClick = onPrimary, enabled = primaryEnabled, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(primary) }
        }
        if (secondary != null) {
            TextButton(onClick = onSecondary, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text(secondary) }
        } else Spacer(Modifier.height(8.dp))
    }
}

private fun progressOf(step: Step) = step.ordinal / (Step.entries.size - 1).toFloat()

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
        progress = null,
        art = {
            Image(
                painter = painterResource(R.drawable.buds_pair),
                contentDescription = "WF-1000XM5 earbuds",
                modifier = Modifier.fillMaxWidth(0.8f).height(200.dp).drawBehind {
                    drawCircle(
                        Brush.radialGradient(listOf(colors.primary.copy(alpha = 0.25f), Color.Transparent), center, size.height / 1.6f),
                        radius = size.height / 1.6f
                    )
                }
            )
        },
        title = "Welcome to SunPods",
        body = "Everything your WF-1000XM5 can do, without the clutter.",
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

// -------------------------------------------------------------------------------- 2 nearby devices

@Composable
private fun NearbyStep(tick: Int, onNext: () -> Unit) {
    val context = LocalContext.current
    if (Build.VERSION.SDK_INT < 31) { LaunchedEffect(Unit) { onNext() }; return }
    val gate = rememberPermission(listOf(Manifest.permission.BLUETOOTH_CONNECT), tick)
    LaunchedEffect(gate.state) { if (gate.state == Perm.GRANTED) onNext() }
    when (gate.state) {
        Perm.ASK, Perm.GRANTED -> Page(
            progress = progressOf(Step.NEARBY), icon = Icons.Rounded.Bluetooth,
            title = "Connect to Your Earbuds",
            body = "SunPods uses Bluetooth to talk to your earbuds. Android calls this Nearby devices.",
            primary = "Allow", onPrimary = gate.request
        )
        Perm.DENIED -> Page(
            progress = progressOf(Step.NEARBY), icon = Icons.Rounded.ErrorOutline, tone = MaterialTheme.colorScheme.error,
            title = "SunPods Needs Nearby Devices",
            body = "Without it, SunPods can't show battery levels or change settings. It's only used to connect to your earbuds.",
            primary = "Allow", onPrimary = gate.request,
            secondary = "Open Settings", onSecondary = { openAppSettings(context) }
        )
        Perm.BLOCKED -> Page(
            progress = progressOf(Step.NEARBY), icon = Icons.Rounded.ErrorOutline, tone = MaterialTheme.colorScheme.error,
            title = "Turn On Nearby Devices in Settings",
            body = "Android won't ask again. In Settings, tap Permissions, then Nearby devices, then Allow. SunPods continues when you return.",
            primary = "Open Settings", onPrimary = { openAppSettings(context) }
        )
    }
}

// -------------------------------------------------------------------------------- 3 earbuds

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

@SuppressLint("MissingPermission")
private fun bondedEarbuds(context: Context): BluetoothDevice? = runCatching { SonyBatteryClient(context).findEarbuds() }.getOrNull()

private fun bluetoothOn(context: Context) =
    context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true

@SuppressLint("MissingPermission")
@Composable
private fun EarbudsStep(vm: SunpodsViewModel, tick: Int, onNext: () -> Unit) {
    val context = LocalContext.current
    var state by remember { mutableStateOf<Buds>(Buds.Checking) }
    var refreshed by remember { mutableStateOf(false) }
    /** "Pair different earbuds": stay on the pairing page even though some earbuds are paired. */
    var pairNew by remember { mutableStateOf(false) }
    /** A short line under the pairing steps, e.g. after the search was cancelled. */
    var note by remember { mutableStateOf<String?>(null) }
    /** When the paired earbuds started "waiting to connect", to offer help if it drags on. */
    var waitingSince by remember { mutableStateOf(0L) }
    val busy by vm.busy.collectAsState()

    // Watch Bluetooth, pairing and the audio link while this page is open.
    LaunchedEffect(tick) {
        while (true) {
            val s = state
            // Paired some other way while searching, such as through Google's Fast Pair popup.
            if ((s is Buds.Searching || s is Buds.Pairing) && bondedEarbuds(context) != null && audioLinkUp(context)) {
                pairNew = false
                state = Buds.Connected
            }
            if (s !is Buds.Searching && s !is Buds.Pairing && s !is Buds.Failed) {
                state = when {
                    !bluetoothOn(context) -> Buds.BluetoothOff
                    pairNew || bondedEarbuds(context) == null -> Buds.NotPaired
                    audioLinkUp(context) -> Buds.Connected
                    else -> Buds.Waiting
                }
            }
            if (state is Buds.Connected && !refreshed) { refreshed = true; vm.refreshAll() }
            if (state is Buds.Waiting) { if (waitingSince == 0L) waitingSince = System.currentTimeMillis() } else waitingSince = 0L
            delay(1_500)
        }
    }

    // Pairing: Android's own device picker (Companion Device Manager) finds the earbuds in
    // pairing mode without needing location or scan permissions; then we bond with them.
    val chooser = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            // Closing the picker after pairing through Google's own popup is fine.
            if (bondedEarbuds(context) != null && audioLinkUp(context)) {
                note = null; pairNew = false; state = Buds.Connected
                return@rememberLauncherForActivityResult
            }
            note = "Search stopped. When the case light flashes blue, tap Find My Earbuds."
            state = Buds.NotPaired
            return@rememberLauncherForActivityResult
        }
        note = null
        pairNew = false
        val device = pickedDevice(context, result.data)
        if (device == null) { state = Buds.Failed("Couldn't recognise those earbuds. Try again."); return@rememberLauncherForActivityResult }
        state = if (device.bondState == BluetoothDevice.BOND_BONDED) Buds.Waiting
        else if (device.createBond()) Buds.Pairing else Buds.Failed("Pairing didn't start. Make sure the case light is flashing blue, then try again.")
    }

    val leScope = rememberCoroutineScope()
    if (state is Buds.Pairing) {
        DisposableEffect(Unit) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    when (i.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)) {
                        BluetoothDevice.BOND_BONDED -> {
                            state = Buds.Waiting
                            // Same press, both sides: the LE Audio side too, if Shizuku is set up.
                            leScope.launch { LePairing.alsoPairLe(context.applicationContext) { DiagnosticsStore.add(context, "Setup: $it") } }
                        }
                        BluetoothDevice.BOND_NONE -> state = Buds.Failed("Pairing didn't finish. Put the earbuds back in pairing mode and try again.")
                    }
                }
            }
            ContextCompat.registerReceiver(context, receiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED), ContextCompat.RECEIVER_EXPORTED)
            onDispose { runCatching { context.unregisterReceiver(receiver) } }
        }
    }

    // Some phones never answer a search or a pairing request; don't leave a spinner forever.
    LaunchedEffect(state) {
        when (state) {
            Buds.Searching -> { delay(60_000); if (state is Buds.Searching) state = Buds.Failed("No earbuds found. Make sure the case light is flashing blue and the case is next to your phone.") }
            Buds.Pairing -> { delay(45_000); if (state is Buds.Pairing) state = Buds.Failed("Pairing took too long. Put the earbuds back in pairing mode and try again.") }
            else -> Unit
        }
    }

    fun search() {
        note = null
        val cdm = context.getSystemService(CompanionDeviceManager::class.java)
        if (cdm == null) { state = Buds.Failed("SunPods can't search for earbuds on this phone. Pair them in Bluetooth settings instead."); return }
        state = Buds.Searching
        val filter = BluetoothDeviceFilter.Builder()
            .setNamePattern(Pattern.compile(".*WF[- ]?1000XM5.*", Pattern.CASE_INSENSITIVE))
            .build()
        val request = AssociationRequest.Builder().addDeviceFilter(filter).setSingleDevice(false).build()
        val callback = object : CompanionDeviceManager.Callback() {
            @Deprecated("Replaced by onAssociationPending on Android 13, which calls this by default")
            override fun onDeviceFound(chooserLauncher: IntentSender) {
                chooser.launch(IntentSenderRequest.Builder(chooserLauncher).build())
            }
            override fun onFailure(error: CharSequence?) {
                state = Buds.Failed("No earbuds found. Make sure the case light is flashing blue and the case is next to your phone.")
            }
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= 33) cdm.associate(request, context.mainExecutor, callback)
            else @Suppress("DEPRECATION") cdm.associate(request, callback, null)
        }.onFailure { state = Buds.Failed("Couldn't start searching. Pair your earbuds in Bluetooth settings instead.") }
    }

    val progress = progressOf(Step.EARBUDS)
    val battery by vm.battery.collectAsState()
    androidx.compose.animation.AnimatedContent(
        targetState = state,
        contentKey = { it::class },
        transitionSpec = { fadeThrough() },
        label = "earbuds"
    ) { s ->
    when (s) {
        Buds.Checking -> Page(progress, icon = Icons.Rounded.Headphones, title = "Looking for Your Earbuds", body = "One moment.", primary = null) {
            CircularProgressIndicator()
        }
        Buds.BluetoothOff -> Page(
            progress, icon = Icons.Rounded.BluetoothDisabled,
            title = "Bluetooth Is Off", body = "Turn on Bluetooth to connect to your earbuds.",
            primary = "Turn On Bluetooth",
            onPrimary = { runCatching { context.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }.onFailure { openBluetoothSettings(context) } },
            secondary = "Not Now", onSecondary = onNext
        )
        Buds.NotPaired -> Page(
            progress, art = {
                RenderClip(
                    R.raw.anim_pair,
                    "The case turns round, the button on the back is held down, then the light on the front flashes blue.",
                    Modifier.fillMaxWidth().height(260.dp)
                )
            },
            title = "Pair Your Earbuds",
            body = "Get them ready, then tap Find My Earbuds.",
            primary = "Find My Earbuds", onPrimary = ::search,
            secondary = "Not Now", onSecondary = onNext
        ) {
            SunCard {
                HowToRow(1, "Put both earbuds in the case and keep the lid open.")
                RowDivider()
                HowToRow(2, "Hold the button on the back for 5 seconds, until the light flashes blue.")
            }
            note?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
            }
        }
        Buds.Searching -> Page(progress, icon = Icons.Rounded.BluetoothSearching, title = "Searching for Earbuds", body = "Choose WF-1000XM5 when it appears. Keep the case open, near your phone.", primary = null) {
            CircularProgressIndicator()
        }
        Buds.Pairing -> Page(progress, icon = Icons.Rounded.BluetoothSearching, title = "Pairing Your Earbuds", body = "If Android asks you to confirm, tap Pair.", primary = null) {
            CircularProgressIndicator()
        }
        Buds.Waiting -> Page(
            progress, art = {
                RenderClip(R.raw.anim_open, "The case opens and both earbuds lift out.", Modifier.fillMaxWidth().height(260.dp))
            },
            title = "Open the Case Near Your Phone",
            body = "Your earbuds are paired. Open the lid or take them out, and they'll connect.",
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
                    "Make sure your earbuds have some charge. If they're connected to another phone or computer, disconnect them there."
                )
                Spacer(Modifier.height(8.dp))
            }
            TextButton(onClick = { openBluetoothSettings(context) }) { Text("Connect in Bluetooth Settings") }
            TextButton(onClick = { pairNew = true; state = Buds.NotPaired }) { Text("Pair Different Earbuds") }
        }
        Buds.Connected -> {
            val read = battery.left != null || battery.right != null
            if (!read && refreshed && !busy) Page(
                // Linked over Bluetooth, but the control channel didn't answer (Sound Connect holds it, usually).
                progress, icon = Icons.Rounded.ErrorOutline, tone = MaterialTheme.colorScheme.error,
                title = "Can't Reach Your Earbuds",
                body = "Your earbuds are connected, but another app may be using them. Close Sony Sound Connect if it's open, then try again.",
                primary = "Try Again", onPrimary = { vm.refreshAll() },
                secondary = "Continue Anyway", onSecondary = onNext
            ) else Page(
                progress, icon = Icons.Rounded.CheckCircle,
                title = "Connected",
                body = if (read) "Here's your battery, straight from your earbuds." else "Checking battery levels.",
                primary = "Continue", onPrimary = onNext
            ) {
                if (read) Levels(battery) else CircularProgressIndicator()
            }
        }
        is Buds.Failed -> Page(
            progress, icon = Icons.Rounded.ErrorOutline, tone = MaterialTheme.colorScheme.error,
            title = "Couldn't Pair Your Earbuds", body = s.message,
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
private fun Levels(battery: BatteryState) {
    SunCard {
    Row(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        listOf("Left" to battery.left, "Right" to battery.right, "Case" to battery.case).forEach { (label, level) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(level?.takeIf { it in 1..100 }?.let { "$it%" } ?: (if (label == "Case") "Unknown" else "In Case"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    }
}

/** The back of the case: USB-C port and, next to it, the pairing button (pulsing). */
@Composable
private fun CaseBackArt() {
    val colors = MaterialTheme.colorScheme
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Restart), label = "ring"
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.width(220.dp).height(150.dp)) {
            val w = size.width; val h = size.height
            val body = Size(w * 0.9f, h * 0.8f)
            val topLeft = Offset((w - body.width) / 2, (h - body.height) / 2)
            drawRoundRect(colors.surfaceVariant, topLeft, body, CornerRadius(body.height / 2.2f))
            drawRoundRect(colors.outline, topLeft, body, CornerRadius(body.height / 2.2f), style = Stroke(2.dp.toPx()))
            // Lid seam.
            drawLine(colors.outline, Offset(topLeft.x + body.width * 0.08f, topLeft.y + body.height * 0.42f),
                Offset(topLeft.x + body.width * 0.92f, topLeft.y + body.height * 0.42f), 1.5.dp.toPx())
            // USB-C port.
            val portW = body.width * 0.16f; val portH = body.height * 0.1f
            val portC = Offset(w / 2, topLeft.y + body.height * 0.72f)
            drawRoundRect(colors.onSurfaceVariant, Offset(portC.x - portW / 2, portC.y - portH / 2), Size(portW, portH), CornerRadius(portH / 2))
            // Pairing button, with a pulse.
            val btn = Offset(portC.x - body.width * 0.2f, portC.y)
            val r = body.height * 0.06f
            drawCircle(colors.primary.copy(alpha = (1 - pulse) * 0.5f), radius = r * (1 + 2.2f * pulse), center = btn)
            drawCircle(colors.primary, radius = r, center = btn)
        }
        Text("Back of the case", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
    }
}

// -------------------------------------------------------------------------------- 3b LE Audio

/** Offers LE Audio right after pairing, using the same switch as Controls (Shizuku first). */
@Composable
private fun LeAudioStep(vm: SunpodsViewModel, onNext: () -> Unit) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsState()
    var switching by remember { mutableStateOf(false) }
    var on by remember { mutableStateOf(LeAudio.anyConnected(context)) }
    // The full check (Bluetooth stack plus its LE Audio service) and the earbuds' own answer.
    // If either says no, this step isn't for this phone: move on without showing it.
    LaunchedEffect(Unit) {
        val phone = withContext(Dispatchers.IO) { LeAudio.verify(context) }
        if (phone == LeAudio.Support.NOT_SUPPORTED || settings.leModeSupported == false) {
            DiagnosticsStore.add(context, "Onboarding: LE Audio step skipped (phone $phone, earbuds ${settings.leModeSupported})")
            onNext()
        }
    }
    Page(
        progress = progressOf(Step.LEAUDIO), icon = androidx.compose.material.icons.Icons.Rounded.Call,
        title = if (on) "You're on LE Audio" else "Clearer Calls with LE Audio",
        body = if (on) "Your earbuds are connected with LE Audio. You can switch back to Classic anytime in Controls > Calls."
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
private fun UpdatesStep(tick: Int, onNext: () -> Unit) {
    val context = LocalContext.current
    val notify = if (Build.VERSION.SDK_INT >= 33) rememberPermission(listOf(Manifest.permission.POST_NOTIFICATIONS), tick) else null
    val unrestricted = remember(tick) { BackgroundAccess.unrestricted(context) }
    val allSet = (notify == null || notify.state == Perm.GRANTED) && unrestricted
    Page(
        progress = progressOf(Step.UPDATES), icon = Icons.Rounded.Notifications,
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
                if (allSet) "SunPods checks your earbuds every few minutes, only while they're connected."
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
private fun ExtrasStep(tick: Int, onNext: () -> Unit) {
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
        progress = progressOf(Step.EXTRAS), icon = Icons.Rounded.Tune,
        title = "Optional Extras",
        body = "Turn these on now or later.",
        primary = "Continue", onPrimary = onNext
    ) {
        Column(Modifier.fillMaxWidth()) {
            SunCard {
                SettingRow(
                    Icons.Rounded.LocationOn, "Last Seen",
                    note = "Where your earbuds last disconnected",
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
private fun DoneStep(vm: SunpodsViewModel, onDone: () -> Unit) {
    val context = LocalContext.current
    val battery by vm.battery.collectAsState()
    val connected = battery.left != null || battery.right != null
    var pinned by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    Page(
        progress = 1f, icon = Icons.Rounded.CheckCircle,
        title = "You're All Set",
        body = if (connected) "Your earbuds are ready." else "SunPods connects as soon as your earbuds do.",
        primary = "Start Using SunPods",
        onPrimary = { OnboardingStore.setDone(context, true); onDone() }
    ) {
        if (connected) { Levels(battery); Spacer(Modifier.height(24.dp)) }
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
