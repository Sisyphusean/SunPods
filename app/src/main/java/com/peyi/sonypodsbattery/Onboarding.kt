package com.peyi.sonypodsbattery

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

private enum class Step { WELCOME, NEARBY, EARBUDS, UPDATES, EXTRAS, DONE }

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
    val order = Step.entries
    fun next() { step = order[(step.ordinal + 1).coerceAtMost(order.lastIndex)] }
    BackHandler(enabled = step != Step.WELCOME) { step = order[step.ordinal - 1] }
    val tick = resumeTick()

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                (slideInHorizontally { if (forward) it / 4 else -it / 4 } + fadeIn(tween(250)))
                    .togetherWith(slideOutHorizontally { if (forward) -it / 4 else it / 4 } + fadeOut(tween(200)))
            },
            label = "onboarding"
        ) { s ->
            when (s) {
                Step.WELCOME -> WelcomeStep(onStart = ::next)
                Step.NEARBY -> NearbyStep(tick, onNext = ::next)
                Step.EARBUDS -> EarbudsStep(vm, tick, onNext = ::next)
                Step.UPDATES -> UpdatesStep(tick, onNext = ::next)
                Step.EXTRAS -> ExtrasStep(tick, onNext = ::next)
                Step.DONE -> DoneStep(vm, onDone = onDone)
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
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp)) {
        if (progress != null) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().clip(CircleShape))
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (art != null) art()
            else if (icon != null) {
                Box(
                    Modifier.size(88.dp).clip(CircleShape).background((tone ?: colors.primary).copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) { Icon(icon, contentDescription = null, tint = tone ?: colors.primary, modifier = Modifier.size(40.dp)) }
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
                .onFailure { android.widget.Toast.makeText(context, it.message ?: "Couldn't read that file", android.widget.Toast.LENGTH_LONG).show() }
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
        primary = "Get started",
        onPrimary = onStart,
        secondary = "Restore from a backup",
        onSecondary = { open.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
    ) {
        Feature(Icons.Rounded.BatteryChargingFull, "Battery at a glance", "Widgets, low battery alerts and battery health")
        Feature(Icons.Rounded.GraphicEq, "Your sound, one tap away", "Noise control, EQ, touch controls and more")
        Feature(Icons.Rounded.Lock, "Private by design", "No account and no internet. Everything stays on this phone.")
    }
    pending?.let { (text, s) ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Restore this backup?") },
            text = { Text("${s.settings} settings and ${s.historyRows} battery readings from SunPods ${s.appVersion}. SunPods restarts, then you finish setup with your settings already in place.") },
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
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(16.dp))
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
            title = "Connect to your earbuds",
            body = "SunPods talks to your earbuds over Bluetooth. Android calls this \"Nearby devices\". It's the one permission SunPods can't work without.",
            primary = "Allow", onPrimary = gate.request
        )
        Perm.DENIED -> Page(
            progress = progressOf(Step.NEARBY), icon = Icons.Rounded.ErrorOutline, tone = MaterialTheme.colorScheme.error,
            title = "SunPods needs Nearby devices",
            body = "Without it, SunPods can't read battery levels or change any setting on your earbuds. It's only used to talk to your earbuds.",
            primary = "Allow", onPrimary = gate.request,
            secondary = "Open settings instead", onSecondary = { openAppSettings(context) }
        )
        Perm.BLOCKED -> Page(
            progress = progressOf(Step.NEARBY), icon = Icons.Rounded.ErrorOutline, tone = MaterialTheme.colorScheme.error,
            title = "Turn on Nearby devices in Settings",
            body = "Android won't ask again. In Settings, open Permissions, then Nearby devices, and choose Allow. SunPods carries on as soon as you come back.",
            primary = "Open settings", onPrimary = { openAppSettings(context) }
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
    /** "Pair a different pair": stay on the pairing page even though some earbuds are paired. */
    var pairNew by remember { mutableStateOf(false) }

    // Watch Bluetooth, pairing and the audio link while this page is open.
    LaunchedEffect(tick) {
        while (true) {
            val s = state
            if (s !is Buds.Searching && s !is Buds.Pairing && s !is Buds.Failed) {
                state = when {
                    !bluetoothOn(context) -> Buds.BluetoothOff
                    pairNew || bondedEarbuds(context) == null -> Buds.NotPaired
                    audioLinkUp(context) -> Buds.Connected
                    else -> Buds.Waiting
                }
            }
            if (state is Buds.Connected && !refreshed) { refreshed = true; vm.refreshAll() }
            delay(1_500)
        }
    }

    // Pairing: Android's own device picker (Companion Device Manager) finds the earbuds in
    // pairing mode without needing location or scan permissions; then we bond with them.
    val chooser = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) { state = Buds.NotPaired; return@rememberLauncherForActivityResult }
        pairNew = false
        val device = pickedDevice(context, result.data)
        if (device == null) { state = Buds.Failed("Android didn't say which earbuds you picked. Try again."); return@rememberLauncherForActivityResult }
        state = if (device.bondState == BluetoothDevice.BOND_BONDED) Buds.Waiting
        else if (device.createBond()) Buds.Pairing else Buds.Failed("Pairing didn't start. Make sure the case light is flashing blue, then try again.")
    }

    if (state is Buds.Pairing) {
        DisposableEffect(Unit) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    when (i.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)) {
                        BluetoothDevice.BOND_BONDED -> state = Buds.Waiting
                        BluetoothDevice.BOND_NONE -> state = Buds.Failed("Pairing didn't finish. Put the earbuds back in pairing mode and try again.")
                    }
                }
            }
            ContextCompat.registerReceiver(context, receiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED), ContextCompat.RECEIVER_EXPORTED)
            onDispose { runCatching { context.unregisterReceiver(receiver) } }
        }
    }

    fun search() {
        val cdm = context.getSystemService(CompanionDeviceManager::class.java)
        if (cdm == null) { state = Buds.Failed("This phone can't search from SunPods. Pair in Bluetooth settings instead."); return }
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
                state = Buds.Failed("No earbuds found. Check the case light is flashing blue and the case is close to your phone.")
            }
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= 33) cdm.associate(request, context.mainExecutor, callback)
            else @Suppress("DEPRECATION") cdm.associate(request, callback, null)
        }.onFailure { state = Buds.Failed("Couldn't start the search. Pair in Bluetooth settings instead.") }
    }

    val progress = progressOf(Step.EARBUDS)
    val battery by vm.battery.collectAsState()
    when (val s = state) {
        Buds.Checking -> Page(progress, icon = Icons.Rounded.Headphones, title = "Looking for your earbuds", body = "Just a moment.", primary = null) {
            CircularProgressIndicator()
        }
        Buds.BluetoothOff -> Page(
            progress, icon = Icons.Rounded.BluetoothDisabled,
            title = "Bluetooth is off", body = "Turn it on so SunPods can reach your earbuds.",
            primary = "Turn on Bluetooth",
            onPrimary = { runCatching { context.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }.onFailure { openBluetoothSettings(context) } },
            secondary = "Skip for now", onSecondary = onNext
        )
        Buds.NotPaired -> Page(
            progress, art = { CaseBackArt() },
            title = "Pair your earbuds",
            body = "Put both earbuds in the case and leave the lid open. Press and hold the button on the back of the case for about 5 seconds, until the light flashes blue.",
            primary = "Find my earbuds", onPrimary = ::search,
            secondary = "Skip for now", onSecondary = onNext
        )
        Buds.Searching -> Page(progress, icon = Icons.Rounded.BluetoothSearching, title = "Searching", body = "Pick your WF-1000XM5 when it appears. Keep the case open and close to your phone.", primary = null) {
            CircularProgressIndicator()
        }
        Buds.Pairing -> Page(progress, icon = Icons.Rounded.BluetoothSearching, title = "Pairing", body = "If Android asks you to confirm, tap Pair.", primary = null) {
            CircularProgressIndicator()
        }
        Buds.Waiting -> Page(
            progress, icon = Icons.Rounded.Headphones,
            title = "Open the case near your phone",
            body = "Your earbuds are paired. Take them out or open the lid and they'll connect by themselves.",
            primary = null,
            secondary = "Skip for now", onSecondary = onNext
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(24.dp))
            TextButton(onClick = { openBluetoothSettings(context) }) { Text("Still not connecting? Open Bluetooth settings") }
            TextButton(onClick = { pairNew = true; state = Buds.NotPaired }) { Text("Pair a different pair") }
        }
        Buds.Connected -> Page(
            progress, icon = Icons.Rounded.CheckCircle,
            title = "Connected",
            body = "SunPods can see your earbuds.",
            primary = "Continue", onPrimary = onNext
        ) {
            Levels(battery)
        }
        is Buds.Failed -> Page(
            progress, icon = Icons.Rounded.ErrorOutline, tone = MaterialTheme.colorScheme.error,
            title = "That didn't work", body = s.message,
            primary = "Try again", onPrimary = { state = Buds.NotPaired },
            secondary = "Open Bluetooth settings", onSecondary = { openBluetoothSettings(context) }
        )
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
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        listOf("Left" to battery.left, "Right" to battery.right, "Case" to battery.case).forEach { (label, level) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(level?.takeIf { it in 1..100 }?.let { "$it%" } ?: "--", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

// -------------------------------------------------------------------------------- 4 stay up to date

@Composable
private fun UpdatesStep(tick: Int, onNext: () -> Unit) {
    val context = LocalContext.current
    val notify = if (Build.VERSION.SDK_INT >= 33) rememberPermission(listOf(Manifest.permission.POST_NOTIFICATIONS), tick) else null
    val unrestricted = remember(tick) { BackgroundAccess.unrestricted(context) }
    val allSet = (notify == null || notify.state == Perm.GRANTED) && unrestricted
    Page(
        progress = progressOf(Step.UPDATES), icon = Icons.Rounded.Notifications,
        title = "Stay up to date",
        body = "So the widget, low battery alerts and automations keep working when SunPods is closed. SunPods only checks your earbuds every few minutes while they're connected.",
        primary = if (allSet) "Continue" else "Continue without these",
        onPrimary = onNext
    ) {
        if (notify != null) SetupRow(
            Icons.Rounded.Notifications, "Notifications", "Low battery and case alerts",
            done = notify.state == Perm.GRANTED,
            action = if (notify.state == Perm.BLOCKED) "Settings" else "Allow"
        ) { if (notify.state == Perm.BLOCKED) openNotificationSettings(context) else notify.request() }
        SetupRow(
            Icons.Rounded.BatteryChargingFull, "Keep running", "Stops your phone closing SunPods in the background",
            done = unrestricted, action = "Allow"
        ) { BackgroundAccess.request(context) }
    }
}

@Composable
private fun SetupRow(icon: ImageVector, title: String, body: String, done: Boolean, action: String, onAction: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(20.dp), color = colors.surfaceContainerHigh, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = colors.primary)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            if (done) Icon(Icons.Rounded.CheckCircle, contentDescription = "On", tint = colors.primary)
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
    Page(
        progress = progressOf(Step.EXTRAS), icon = Icons.Rounded.Tune,
        title = "Optional extras",
        body = "Turn these on now or any time later.",
        primary = "Continue", onPrimary = onNext
    ) {
        Surface(shape = RoundedCornerShape(20.dp), color = colors.surfaceContainerHigh, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = colors.primary)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Last seen", style = MaterialTheme.typography.titleSmall)
                        Text("Remembers where your earbuds disconnected, if you lose them. Saved only on this phone.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(checked = lastSeen, onCheckedChange = { on ->
                        if (!on) { LastSeenStore.setEnabled(context, false); lastSeen = false }
                        else if (location.state == Perm.GRANTED) { LastSeenStore.setEnabled(context, true); lastSeen = true }
                        else if (location.state == Perm.BLOCKED) openAppSettings(context)
                        else { wantLastSeen = true; location.request() }
                    })
                }
                if (lastSeen && background.state != Perm.GRANTED) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "To save the spot while SunPods is closed, choose \"Allow all the time\" for location.",
                        style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant
                    )
                    TextButton(onClick = { if (background.state == Perm.BLOCKED) openAppSettings(context) else background.request() }) {
                        Text("Allow all the time")
                    }
                }
            }
        }
        SetupInfo(Icons.Rounded.GraphicEq, "Automations", "Switch noise control by place, walking, calls or Focus. Set them up in Controls.")
    }
}

@Composable
private fun SetupInfo(icon: ImageVector, title: String, body: String) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(20.dp), color = colors.surfaceContainerHigh, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = colors.primary)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
        }
    }
}

// -------------------------------------------------------------------------------- 6 done

@Composable
private fun DoneStep(vm: SunpodsViewModel, onDone: () -> Unit) {
    val context = LocalContext.current
    val battery by vm.battery.collectAsState()
    val connected = battery.left != null || battery.right != null
    val widgets = remember { AppWidgetManager.getInstance(context) }
    var pinned by remember { mutableStateOf(false) }
    Page(
        progress = 1f, icon = Icons.Rounded.CheckCircle,
        title = "You're all set",
        body = if (connected) "Your earbuds are ready." else "SunPods connects as soon as your earbuds do.",
        primary = "Start using SunPods",
        onPrimary = { OnboardingStore.setDone(context, true); onDone() }
    ) {
        if (connected) { Levels(battery); Spacer(Modifier.height(24.dp)) }
        if (widgets.isRequestPinAppWidgetSupported && !pinned) {
            SetupRow(Icons.Rounded.Widgets, "Add a widget", "Battery on your home screen. More sizes in your widget list.", done = false, action = "Add") {
                pinned = widgets.requestPinAppWidget(ComponentName(context, SunpodsMediumWidgetReceiver::class.java), null, null)
            }
        }
    }
}
