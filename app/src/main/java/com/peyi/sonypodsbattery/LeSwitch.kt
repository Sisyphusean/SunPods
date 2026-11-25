package com.peyi.sonypodsbattery

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.rounded.CheckCircle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface Switch {
    data class Working(val text: String) : Switch
    /** First LE Audio setup: get Shizuku ready so SunPods can do the pairing. */
    data object PrepareShizuku : Switch
    /** The earbuds took the change, but the phone needs a new pairing to use the new mode. */
    data object PairLe : Switch
    data object PairClassic : Switch
    data class Done(val message: String) : Switch
    data class Failed(val message: String, val canRevert: Boolean = false) : Switch
}

/** The earbuds' addresses as they report them: Classic first, then one LE address per earbud. */
internal object EarbudAddresses {
    private const val PREFS = "earbud_addresses"
    fun save(context: Context, addresses: List<String>) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("all", addresses.joinToString(",")).apply()
    fun load(context: Context): List<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("all", null)?.split(",")?.filter { it.isNotBlank() }.orEmpty()
    fun classic(context: Context): String? = load(context).firstOrNull()
    /** LE addresses, the random static one first: pairing that earbud makes Android pair the other. */
    fun le(context: Context): List<String> = load(context).drop(1).distinct().sortedByDescending { isRandom(it) }
    fun isRandom(address: String) = (address.take(2).toIntOrNull(16) ?: 0) and 0xC0 == 0xC0
}

/**
 * Switches the earbuds between Classic and LE Audio the way Sound Connect does: confirm the change
 * with the earbuds, let them reconnect, and if the phone needs a new pairing for the new mode, pair
 * them again. With Shizuku running, SunPods does the pairing itself; otherwise the sheet walks the
 * user through it in Bluetooth settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
internal fun LeSwitchSheet(on: Boolean, settings: DeviceSettings, closeOnSuccess: Boolean = false, onDone: () -> Unit) {
    val context = LocalContext.current
    val model = remember(settings.modelName) { EarbudModel.fromName(settings.modelName) ?: EarbudModel.current(context) }
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<Switch>(Switch.Working("Updating your ${model.noun}…")) }
    var flow by remember { mutableStateOf<Job?>(null) }
    var pairing by remember { mutableStateOf(false) }
    var shizuku by remember { mutableStateOf(ShizukuBluetooth.state()) }
    var shizukuInstalled by remember { mutableStateOf(ShizukuBluetooth.installed(context)) }
    var shizukuUnrestricted by remember { mutableStateOf(ShizukuWatch.runsInBackground(context, ShizukuBluetooth.PACKAGE)) }
    var sunpodsUnrestricted by remember { mutableStateOf(ShizukuWatch.runsInBackground(context, context.packageName)) }
    // The user chose to pair without Shizuku.
    var manual by remember { mutableStateOf(false) }
    /** Which way the switch waiting on Shizuku goes: true = LE Audio. */
    var pendingLe by remember { mutableStateOf(on) }
    var pairStatus by remember { mutableStateOf("Waiting for the light to flash blue") }
    var pairError by remember { mutableStateOf<String?>(null) }
    fun log(m: String) = DiagnosticsStore.add(context, "Connection mode: $m")

    suspend fun waitUntil(ms: Long, check: () -> Boolean): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < ms) {
            if (withContext(Dispatchers.IO) { check() }) return true
            delay(1_500)
        }
        return false
    }

    /** Tells the earbuds to switch and confirms it. False if they couldn't be reached or refused. */
    suspend fun switchEarbuds(target: Boolean): Boolean =
        RefreshCoordinator.awaitSession(context, "Connection mode") { s ->
            SonyControlSessionManager.noteSwitch(context)
            val addresses = SonyFeatures.leAddresses(s)
            if (addresses.isNotEmpty()) EarbudAddresses.save(context, addresses)
            log("addresses ${addresses.joinToString()}")
            SonyFeatures.setLeAudio(s, settings, target, phoneSupportsLe = LeAudio.phoneSupports(context))
                .also { log("asked for ${if (target) "LE Audio" else "Classic"}: ${if (it) "earbuds confirmed" else "not taken"}") }
        }.getOrDefault(false)

    /** True when the phone already has the earbuds' LE pairing, so switching won't need Shizuku. */
    fun lePairingExists(): Boolean {
        val le = EarbudAddresses.le(context)
        if (le.isEmpty()) return false
        return runCatching {
            context.getSystemService(BluetoothManager::class.java)?.adapter?.bondedDevices.orEmpty().any { d -> le.any { it.equals(d.address, true) } }
        }.getOrDefault(false)
    }

    fun readyToSwitch(): Boolean = BatteryStore.load(context).let {
        if (model.headphones) (it.left ?: 0) in 1..100
        else (it.left ?: 0) in 1..100 && (it.right ?: 0) in 1..100
    }

    fun toLe() {
        flow = scope.launch {
            when (withContext(Dispatchers.IO) { LeAudio.verify(context) }) {
                LeAudio.Support.SUPPORTED -> Unit
                LeAudio.Support.UNKNOWN -> { state = Switch.Failed("Turn on Bluetooth, then try again."); return@launch }
                LeAudio.Support.NOT_SUPPORTED -> {
                    log("phone doesn't support LE Audio (Android ${Build.VERSION.SDK_INT})")
                    state = Switch.Failed(
                        if (Build.VERSION.SDK_INT < 33) "LE Audio needs Android 13 or later. Your ${model.noun} stay on Classic."
                        else "Your phone doesn't support LE Audio, or it's turned off in Developer options. Your ${model.noun} stay on Classic."
                    )
                    return@launch
                }
            }
            // Levels only exist while connected; without a link "wear both earbuds" would be wrong.
            val connected = classicConnected(context) || LeAudio.anyConnected(context)
            if (!connected) { state = Switch.Failed("Your ${model.noun} aren't connected. Reconnect them in Controls > Calls first."); return@launch }
            if (!readyToSwitch()) {
                state = Switch.Failed(
                    if (model.headphones) "Turn on your headphones and connect them, then try again."
                    else "Wear both earbuds, then try again."
                )
                return@launch
            }
            // Switching needs the phone's pairings changed, which only Shizuku lets SunPods do.
            // Without it nothing is sent to the earbuds, so they can't be left half switched.
            if (ShizukuBluetooth.state() != ShizukuBluetooth.State.READY) {
                log("LE Audio switch needs Shizuku: ${ShizukuBluetooth.state()}, installed $shizukuInstalled")
                pendingLe = true
                state = Switch.PrepareShizuku
                return@launch
            }
            state = Switch.Working("Updating your ${model.noun}…")
            ShizukuWatch.start(context)
            // Checked again right before the earbuds change: the phone may have closed Shizuku.
            if (ShizukuBluetooth.state() != ShizukuBluetooth.State.READY) { pendingLe = true; state = Switch.PrepareShizuku; return@launch }
            SonyControlSessionManager.setClassicOnly(context, false)
            if (!switchEarbuds(true)) {
                state = Switch.Failed("Couldn't switch. If Sony Sound Connect is open, close it and try again.")
                return@launch
            }
            // A switch to Classic with Shizuku turns LE Audio off for the earbuds; allow it again.
            if (lePairingExists()) {
                val allowed = ShizukuBluetooth.use(context) { sh -> EarbudAddresses.le(context).forEach { log("allow LE $it: ${sh.connectLe(it)}") }; true }
                if (allowed != true) ShizukuWatch.markPending(context, "connect your ${model.noun} on LE Audio")
            }
            // A phone that has never paired the earbuds' LE side can't reconnect by itself, so go
            // straight to pairing instead of waiting.
            val firstTime = !lePairingExists()
            state = Switch.Working(if (firstTime) "Updating your ${model.noun}…" else "Your ${model.noun} are reconnecting. This can take up to a minute.")
            if (waitUntil(if (firstTime) 4_000 else 60_000) { LeAudio.anyConnected(context) }) {
                log("on LE Audio without pairing again")
                state = Switch.Done("Your ${model.noun} are now on LE Audio.")
                return@launch
            }
            log("LE Audio needs a new pairing (Shizuku ${ShizukuBluetooth.state()})")
            state = Switch.PairLe
            if (waitUntil(10 * 60_000L) { LeAudio.anyConnected(context) }) {
                log("on LE Audio after pairing")
                state = Switch.Done(if (closeOnSuccess) "Your ${model.noun} are now on LE Audio." else "Your ${model.noun} are now on LE Audio. Test your call mic to hear the difference.")
            } else {
                // Some phones say they support LE Audio but only accept certain earbuds over it.
                state = Switch.Failed("Your phone couldn't connect using LE Audio. It may not support LE Audio with these ${model.noun}. Switch back to Classic to keep using them.", canRevert = true)
            }
        }
    }

    /** See [ClassicRepair.finish]. */
    suspend fun finishClassicWithShizuku(justSwitched: Boolean = true): Boolean =
        ClassicRepair.finish(context, justSwitched) { pairStatus = it }

    fun toClassic() {
        flow?.cancel()
        flow = scope.launch {
            if (ShizukuBluetooth.state() != ShizukuBluetooth.State.READY) {
                log("Classic switch needs Shizuku: ${ShizukuBluetooth.state()}, installed $shizukuInstalled")
                pendingLe = false
                state = Switch.PrepareShizuku
                return@launch
            }
            state = Switch.Working("Updating your ${model.noun}…")
            ShizukuWatch.start(context)
            if (ShizukuBluetooth.state() != ShizukuBluetooth.State.READY) { pendingLe = false; state = Switch.PrepareShizuku; return@launch }
            if (!switchEarbuds(false)) {
                // Not reachable: often already set to Classic but not reconnected (an old pairing the
                // phone keeps dropping). With Shizuku, pair them again; hold the touch sensors for
                // pairing mode if they've left it.
                if (ShizukuBluetooth.state() == ShizukuBluetooth.State.READY) {
                    log("${model.noun} not reachable, pairing Classic again")
                    SonyControlSessionManager.releaseLe(context)
                    state = Switch.Working(
                        if (model.headphones) "Reconnecting… If this takes a while, turn your headphones off and hold the power button for 5 seconds."
                        else "Reconnecting… If this takes a while, put your earbuds in and hold both touch sensors for 5 seconds."
                    )
                    if (finishClassicWithShizuku(justSwitched = false)) {
                        // The phone is on Classic now, but the earbuds may still be set to LE: they
                        // sometimes ignore the switch over LE while settling in. Over Classic they
                        // take it, then drop and come back, which the repair below handles.
                        ClassicRepair.syncSetting(context)
                        state = Switch.Done("Your ${model.noun} are connected on Classic.")
                        return@launch
                    }
                    state = Switch.Failed(
                        if (model.headphones) "Couldn't reach your headphones. Turn them on and try again."
                        else "Couldn't reach your earbuds. Put them in your ears and try again."
                    )
                    return@launch
                }
                state = Switch.Failed(
                    if (model.headphones) "Couldn't switch to Classic. Keep your headphones turned on and try again."
                    else "Couldn't switch to Classic. Keep both earbuds in your ears and try again."
                )
                return@launch
            }
            // Let go of the earbuds' LE side, or they stay on LE Audio waiting for the phone.
            SonyControlSessionManager.releaseLe(context)
            state = Switch.Working("Your ${model.noun} are switching to Classic. This can take up to a minute.")
            if (ShizukuBluetooth.state() == ShizukuBluetooth.State.READY) {
                if (finishClassicWithShizuku()) {
                    state = Switch.Done("Your ${model.noun} are now on Classic.")
                    return@launch
                }
                log("Shizuku couldn't finish the switch, showing the steps")
            } else {
                if (waitUntil(25_000) { classicConnected(context) && !LeAudio.anyConnected(context) }) {
                    log("on Classic without pairing again")
                    state = Switch.Done("Your ${model.noun} are now on Classic.")
                    return@launch
                }
                if (LeAudio.anyConnected(context)) {
                    // Without Shizuku the phone can't be told to let go; restart to drop the LE link.
                    log("LE Audio still connected, asking for restart")
                    state = Switch.Working(
                        if (model.headphones) "Almost done. Turn your headphones off for 10 seconds, then turn them back on."
                        else "Almost done. Put both earbuds in the case, close the lid for 10 seconds, then take them out."
                    )
                }
                if (waitUntil(90_000) { classicConnected(context) && !LeAudio.anyConnected(context) }) {
                    log("on Classic without pairing again")
                    state = Switch.Done("Your ${model.noun} are now on Classic.")
                    return@launch
                }
            }
            log("Classic needs a new pairing")
            state = Switch.PairClassic
            if (waitUntil(10 * 60_000L) { classicConnected(context) }) {
                log("on Classic after pairing")
                state = Switch.Done("Your ${model.noun} are now on Classic.")
            } else {
                state = Switch.Failed("Your phone isn't connected to your ${model.noun} yet. Pair them in Bluetooth settings.")
            }
        }
    }

    /**
     * With Shizuku: removes the old pairings, waits for the earbuds to show up in pairing mode, then
     * pairs and connects them by itself. The user only presses the case button (and Android's own
     * Pair prompt if it shows one).
     */
    fun autoPairLe() {
        if (pairing) return
        pairing = true
        pairError = null
        scope.launch {
            val le = EarbudAddresses.le(context)
            val classic = EarbudAddresses.classic(context)
            log("pairing LE ${le.joinToString()} with Shizuku")
            val ok = ShizukuBluetooth.use(context) { sh ->
                val firstLe = le.firstOrNull() ?: return@use false
                // Wait for the earbuds to show up in pairing mode before touching any pairing, so
                // nothing that works today is removed for a pairing that never happens.
                pairStatus = "Waiting for the light to flash blue"
                if (!sh.waitForAdvert(firstLe, 5 * 60_000L)) return@use false
                // Old pairings stop the LE side pairing cleanly. Remove them and wait until they're gone.
                val old = sh.bonded().lines().map { it.split("|") }
                    .filter { it.size >= 2 && (model.matchesLe(it[0]) || it[1].equals(classic, true) || le.any { a -> a.equals(it[1], true) }) }
                    .map { it[1] }
                old.forEach { log("remove $it: ${sh.removeBond(it)}") }
                var waited = 0
                while (waited < 5_000 && old.any { sh.bondState(it) == BluetoothDevice.BOND_BONDED }) { Thread.sleep(250); waited += 250 }

                fun bond(address: String): Boolean {
                    log("pair $address: ${sh.bondLe(address)}")
                    Thread.sleep(1_000)
                    var w = 0
                    while (w < 30_000 && sh.bondState(address) == BluetoothDevice.BOND_BONDING) { Thread.sleep(500); w += 500 }
                    return sh.bondState(address) == BluetoothDevice.BOND_BONDED
                }

                var allPaired = true
                for ((i, address) in le.withIndex()) {
                    // Pairing the first earbud usually makes Android pair the other one by itself.
                    if (i > 0) {
                        var w = 0
                        while (w < 10_000 && sh.bondState(address) != BluetoothDevice.BOND_BONDED) { Thread.sleep(500); w += 500 }
                        if (sh.bondState(address) == BluetoothDevice.BOND_BONDED) continue
                    }
                    var paired = false
                    for (attempt in 1..3) {
                        pairStatus = "Waiting for the light to flash blue"
                        if (!sh.waitForAdvert(address, 5 * 60_000L)) break
                        pairStatus = "Pairing your ${model.noun}"
                        if (bond(address)) { paired = true; break }
                    }
                    if (!paired) { allPaired = false; break }
                }
                if (allPaired) {
                    pairStatus = "Connecting"
                    le.forEach { log("connect $it: ${sh.connectLe(it)}") }
                }
                allPaired
            }
            if (ok != true) {
                log("pairing with Shizuku didn't finish: $ok")
                pairError = "Couldn't pair your ${model.noun}. Put them in pairing mode again, then tap Try Again."
            }
            pairing = false
        }
    }

    /** Without Shizuku: pair the LE Audio side using Android's own prompt. */
    fun pairLe() {
        if (pairing) return
        pairing = true
        scope.launch {
            val le = EarbudAddresses.le(context)
            log("pairing LE ${le.joinToString()} with Android's prompt")
            withContext(Dispatchers.IO) {
                // Android then pairs the other earbud of the set by itself while they're in pairing mode.
                val target = le.firstOrNull { EarbudAddresses.isRandom(it) } ?: le.firstOrNull()
                val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
                if (target != null && adapter != null && Build.VERSION.SDK_INT >= 33) {
                    val addrType = if (EarbudAddresses.isRandom(target)) BluetoothDevice.ADDRESS_TYPE_RANDOM else BluetoothDevice.ADDRESS_TYPE_PUBLIC
                    val ok = runCatching { adapter.getRemoteLeDevice(target, addrType).createBond() }.getOrDefault(false)
                    log("pair $target with Android's prompt: $ok")
                }
            }
            pairing = false
        }
    }

    /** Pairs the device over Classic again. Only possible with Shizuku; otherwise the steps say how. */
    fun pairClassic() {
        if (pairing) return
        pairing = true
        scope.launch {
            if (!finishClassicWithShizuku()) pairError = "Couldn't pair your ${model.noun}. Put them in pairing mode, then tap Pair Now."
            pairing = false
        }
    }

    LaunchedEffect(Unit) { if (on) toLe() else toClassic() }

    // In onboarding the page behind says it worked, so don't make the user tap Done as well.
    // Let the success land for a moment, then slide the sheet away rather than cutting it.
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val close: () -> Unit = { scope.launch { sheet.hide(); onDone() } }
    LaunchedEffect(state) {
        if (closeOnSuccess && state is Switch.Done) { kotlinx.coroutines.delay(700); sheet.hide(); onDone() }
    }

    // Shizuku can be started or allowed while the sheet is open.
    LaunchedEffect(state) {
        while (state == Switch.PairLe || state == Switch.PairClassic || state == Switch.PrepareShizuku) {
            shizuku = ShizukuBluetooth.state()
            shizukuInstalled = ShizukuBluetooth.installed(context)
            shizukuUnrestricted = ShizukuWatch.runsInBackground(context, ShizukuBluetooth.PACKAGE)
            sunpodsUnrestricted = ShizukuWatch.runsInBackground(context, context.packageName)
            delay(1_000)
        }
    }

    // With Shizuku, pairing starts by itself as soon as the pairing step shows.
    LaunchedEffect(state, shizuku) {
        if (state == Switch.PairLe && shizuku == ShizukuBluetooth.State.READY && !manual) autoPairLe()
        if (state == Switch.PairClassic && shizuku == ShizukuBluetooth.State.READY) pairClassic()
    }

    DisposableEffect(Unit) { onDispose { ShizukuBluetooth.release(context) } }

    val openBluetooth = { runCatching { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }; Unit }
    val target = if (on) "LE Audio" else "Classic"

    ModalBottomSheet(onDismissRequest = onDone, sheetState = sheet) {
        // Each step fades through to the next and the sheet resizes smoothly. Progress text within
        // a step just updates, without replaying the transition.
        androidx.compose.animation.AnimatedContent(
            targetState = state,
            contentKey = { it::class },
            transitionSpec = { fadeThrough() },
            label = "switch"
        ) { s ->
        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding()
        ) {
            Text(
                when (s) {
                    Switch.PrepareShizuku -> "Turn On Shizuku"
                    Switch.PairLe -> "Pair Your ${model.titleNoun} for LE Audio"
                    Switch.PairClassic -> "Pair Your ${model.titleNoun} for Classic"
                    else -> "Switching to $target"
                },
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(12.dp))
            when (s) {
                is Switch.Working -> Busy(s.text)
                Switch.PrepareShizuku -> {
                    val running = shizuku != ShizukuBluetooth.State.NOT_RUNNING
                    Text(
                        "Switching between Classic and LE Audio means pairing your ${model.noun} again. Shizuku, a free helper app, lets SunPods do it for you. Nothing changes until Shizuku is running.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(16.dp))
                    Checklist("Install Shizuku", "Free on Google Play", done = shizukuInstalled || running, action = "Get") { ShizukuBluetooth.openStore(context) }
                    Checklist("Start Shizuku", "In Shizuku, choose Start via Wireless debugging. Start it again after your phone restarts", done = running, action = "Open", enabled = shizukuInstalled) { ShizukuBluetooth.open(context) }
                    Checklist("Allow SunPods", "Lets SunPods pair your ${model.noun} through Shizuku", done = shizuku == ShizukuBluetooth.State.READY, action = "Allow", enabled = running) { ShizukuBluetooth.requestPermission() }
                    Checklist("Keep Shizuku running", "In Battery, choose Unrestricted so your phone doesn't close it", done = shizukuUnrestricted, action = "Open", enabled = shizukuInstalled) { ShizukuWatch.openAppSettings(context, ShizukuBluetooth.PACKAGE) }
                    Checklist("Keep SunPods running", "In Battery, choose Unrestricted so SunPods can fix your connection by itself", done = sunpodsUnrestricted, action = "Open") { ShizukuWatch.openAppSettings(context, context.packageName) }
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = { if (pendingLe) toLe() else toClassic() }, enabled = shizuku == ShizukuBluetooth.State.READY, modifier = Modifier.fillMaxWidth()) {
                        Text(if (pendingLe) "Switch to LE Audio" else "Switch to Classic")
                    }
                }
                Switch.PairLe -> {
                    RenderClip(
                        budArt(settings).pairClip,
                        model.pairClipDescription,
                        Modifier.fillMaxWidth().height(200.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Your ${model.noun} are on LE Audio. Pair them with your phone again to use it.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(16.dp))
                    if (shizuku == ShizukuBluetooth.State.READY && !manual) {
                        model.pairSteps.forEachIndexed { idx, step ->
                            Step(idx + 1, if (idx == model.pairSteps.lastIndex) "$step SunPods pairs them as soon as it sees them." else step)
                        }
                        Step(model.pairSteps.size + 1, "If your phone asks to pair, tap Pair.")
                        val error = pairError
                        if (error != null) {
                            Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { autoPairLe() }, modifier = Modifier.fillMaxWidth()) { Text("Try Again") }
                        } else {
                            PairingStatus(pairStatus)
                        }
                    } else {
                        Step(1, "Open Bluetooth settings. Tap the settings icon next to ${model.displayName}, then tap Unpair. Unpair LE_${model.displayName} too if it's there.")
                        Step(2, model.pairSteps.joinToString(" "))
                        Step(3, "Come back here and tap Pair Now. If your phone asks to pair, tap Pair.")
                        PairingStatus(if (pairing) "Pairing your ${model.noun}" else "Waiting for LE Audio")
                        Spacer(Modifier.height(20.dp))
                        Button(onClick = { pairLe() }, enabled = !pairing, modifier = Modifier.fillMaxWidth()) { Text("Pair Now") }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = openBluetooth, modifier = Modifier.fillMaxWidth()) { Text("Open Bluetooth Settings") }
                    }
                    ShizukuNote(shizuku, model.noun)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { toClassic() }, modifier = Modifier.fillMaxWidth()) { Text("Go Back to Classic") }
                }
                Switch.PairClassic -> {
                    RenderClip(
                        budArt(settings).pairClip,
                        model.pairClipDescription,
                        Modifier.fillMaxWidth().height(200.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Your ${model.noun} are on Classic. Pair them with your phone again to use it.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(16.dp))
                    if (shizuku == ShizukuBluetooth.State.READY) {
                        Step(1, model.pairSteps.joinToString(" "))
                        Step(2, "Tap Pair Now. If your phone asks to pair, tap Pair.")
                        PairingStatus(if (pairing) "Pairing your ${model.noun}" else "Waiting for your ${model.noun}")
                        Spacer(Modifier.height(20.dp))
                        Button(onClick = { pairClassic() }, enabled = !pairing, modifier = Modifier.fillMaxWidth()) { Text("Pair Now") }
                    } else {
                        Step(1, "Open Bluetooth settings. Unpair every ${model.displayName} in the list.")
                        Step(2, model.pairSteps.joinToString(" "))
                        Step(3, "Under Available devices, tap ${model.displayName}.")
                        PairingStatus("Waiting for your ${model.noun}")
                        Spacer(Modifier.height(20.dp))
                        Button(onClick = openBluetooth, modifier = Modifier.fillMaxWidth()) { Text("Open Bluetooth Settings") }
                    }
                    ShizukuNote(shizuku, model.noun)
                }
                is Switch.Done -> {
                    Text(s.message, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = close, modifier = Modifier.fillMaxWidth()) { Text("Done") }
                }
                is Switch.Failed -> {
                    Text(s.message, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(20.dp))
                    if (s.canRevert) {
                        Button(onClick = { toClassic() }, modifier = Modifier.fillMaxWidth()) { Text("Go Back to Classic") }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { state = Switch.PairLe }, modifier = Modifier.fillMaxWidth()) { Text("Try Pairing Again") }
                    } else {
                        Button(onClick = openBluetooth, modifier = Modifier.fillMaxWidth()) { Text("Open Bluetooth Settings") }
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = close, modifier = Modifier.fillMaxWidth()) { Text("Close") }
                }
            }
        }
        }
    }
}

@Composable
private fun Checklist(title: String, body: String, done: Boolean, action: String, enabled: Boolean = true, onAction: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    androidx.compose.material3.Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp), color = colors.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            if (done) androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.CheckCircle, contentDescription = "Done", tint = colors.primary)
            else androidx.compose.material3.FilledTonalButton(onClick = onAction, enabled = enabled) { Text(action) }
        }
    }
}

@Composable
private fun PairingStatus(text: String) {
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ShizukuNote(state: ShizukuBluetooth.State, noun: String) {
    when (state) {
        ShizukuBluetooth.State.READY -> {
            Spacer(Modifier.height(12.dp))
            Text("SunPods will remove the old pairing and pair your $noun using Shizuku.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ShizukuBluetooth.State.NEEDS_PERMISSION -> {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { ShizukuBluetooth.requestPermission() }, modifier = Modifier.fillMaxWidth()) { Text("Let Shizuku Do This for You") }
        }
        ShizukuBluetooth.State.NOT_RUNNING -> {
            Spacer(Modifier.height(12.dp))
            Text("With Shizuku running, SunPods can do these steps for you.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Busy(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(16.dp))
    CircularProgressIndicator()
}

@Composable
private fun Step(n: Int, text: String) {
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("$n", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@SuppressLint("MissingPermission")
private fun classicConnected(context: Context): Boolean = runCatching {
    context.getSystemService(BluetoothManager::class.java)?.adapter?.getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothProfile.STATE_CONNECTED
}.getOrDefault(false)

/**
 * One case-button press pairs both sides. Right after SunPods pairs the earbuds, while they're
 * still in pairing mode, it also pairs their LE Audio side through Shizuku if Android didn't. Then
 * every later switch between Classic and LE Audio is a plain reconnect, with no button. It only
 * ever adds a pairing; it never removes one.
 */
internal object LePairing {
    suspend fun alsoPairLe(context: Context, log: (String) -> Unit): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (ShizukuBluetooth.state() != ShizukuBluetooth.State.READY) return@withContext false
        if (LeAudio.support(context) == LeAudio.Support.NOT_SUPPORTED) return@withContext false
        // Devices without LE Audio (like WF-C710N) have no LE side; saved addresses may be another pair's.
        if (SettingsStore.load(context).leModeSupported == false) return@withContext false
        val le = EarbudAddresses.le(context)
        if (le.isEmpty()) return@withContext false
        ShizukuBluetooth.use(context) { sh ->
            val missing = le.filter { sh.bondState(it) != BluetoothDevice.BOND_BONDED }
            if (missing.isEmpty()) return@use true
            missing.all { address ->
                // Only while the earbuds still advertise for pairing; otherwise leave it.
                if (!sh.waitForAdvert(address, 15_000)) { log("LE side not in pairing mode, left for later"); return@all false }
                log("pair LE side $address: ${sh.bondLe(address)}")
                var waited = 0
                while (waited < 20_000 && sh.bondState(address) != BluetoothDevice.BOND_BONDED) { Thread.sleep(500); waited += 500 }
                (sh.bondState(address) == BluetoothDevice.BOND_BONDED).also { log("LE side ${if (it) "paired" else "not paired"}") }
            }
        } == true
    }
}
