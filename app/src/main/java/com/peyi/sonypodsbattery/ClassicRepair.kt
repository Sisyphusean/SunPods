package com.peyi.sonypodsbattery

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Gets the earbuds onto Classic and keeps them there, with Shizuku and no steps for the user.
 *
 * After a switch to Classic the earbuds drop their link and wait in Classic pairing mode. The phone
 * may still hold them on LE Audio, or keep an old pairing that it then drops every few seconds.
 * [finish] turns LE Audio off for them, asks for Classic on the existing pairing and, only if that
 * fails while they're reachable, removes the old pairings and pairs Classic again.
 *
 * [check] runs the same repair by itself from Bluetooth events: an earbud on LE Audio while they're
 * set to Classic, or a Classic link that keeps dropping with no audio.
 */
internal object ClassicRepair {
    private val lock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var lastAutoAt = 0L
    private const val AUTO_GAP_MS = 90_000L

    private fun log(context: Context, m: String) = DiagnosticsStore.add(context, "Connection mode: $m")

    fun classicConnected(context: Context): Boolean = runCatching {
        context.getSystemService(BluetoothManager::class.java)?.adapter?.getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothProfile.STATE_CONNECTED
    }.getOrDefault(false)

    private fun onClassic(context: Context) = classicConnected(context) && !LeAudio.anyConnected(context)

    private suspend fun waitUntil(ms: Long, check: () -> Boolean): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < ms) {
            if (check()) return true
            delay(1_000)
        }
        return false
    }

    /**
     * [justSwitched]: the earbuds have just confirmed a switch, so they're about to drop their link
     * and then sit in pairing mode; it's safe to pair them again.
     */
    suspend fun finish(context: Context, justSwitched: Boolean, status: (String) -> Unit = {}): Boolean = lock.withLock {
        val app = context.applicationContext
        if (justSwitched) {
            // Don't mistake the old link for the new one: wait for the earbuds to start letting go.
            // One earbud dropping is enough; the other keeps LE Audio until the phone drops it,
            // which happens next, so waiting for both only cost time.
            val leAtStart = LeAudio.connectedEarbuds(app).size
            val dropped = waitUntil(8_000) {
                val le = LeAudio.connectedEarbuds(app).size
                (le == 0 && !classicConnected(app)) || (leAtStart > 0 && le < leAtStart)
            }
            log(app, if (dropped) "earbuds started dropping their link to switch" else "earbuds kept their link through the switch")
        }
        val classic = EarbudAddresses.classic(app)
        val le = (EarbudAddresses.le(app) + LeAudio.connectedEarbuds(app).map { it.address }).distinct()
        val asked = ShizukuBluetooth.use(app) { sh ->
            le.forEach { log(app, "drop LE $it: ${sh.dropLe(it)}") }
            // Ask for Classic on the pairing the phone already has; often that's all it takes.
            if (classic != null && sh.bondState(classic) == BluetoothDevice.BOND_BONDED) log(app, "connect $classic: ${sh.connectClassic(classic)}")
            true
        }
        if (asked != true) {
            log(app, "Shizuku isn't available")
            ShizukuWatch.markPending(app, "reconnect your earbuds on Classic")
            return@withLock false
        }
        if (waitUntil(15_000) { onClassic(app) }) {
            log(app, "on Classic")
            ShizukuWatch.clear(app)
            return@withLock true
        }
        if (classic == null) { log(app, "no Classic address saved"); return@withLock false }
        // Removing pairings is only safe when the earbuds are out and reachable: right after a
        // switch (they wait in pairing mode) or while the phone keeps dropping their link. Earbuds
        // sitting in the case would otherwise lose their pairing for nothing.
        if (!justSwitched && !SonyControlSessionManager.aclDroppedRecently(app)) {
            log(app, "earbuds not reachable, keeping the pairing")
            return@withLock false
        }
        status("Pairing your earbuds")
        val bonded = withContext(Dispatchers.IO) {
            ShizukuBluetooth.use(app) { sh ->
                val old = sh.bonded().lines().map { it.split("|") }
                    .filter { it.size >= 2 && (it[0].contains("WF-1000XM5") || it[1].equals(classic, true) || le.any { a -> a.equals(it[1], true) }) }
                    .map { it[1] }
                old.forEach { log(app, "remove $it: ${sh.removeBond(it)}") }
                var waited = 0
                while (waited < 5_000 && old.any { sh.bondState(it) == BluetoothDevice.BOND_BONDED }) { Thread.sleep(250); waited += 250 }
                for (attempt in 1..4) {
                    if (sh.bondState(classic) == BluetoothDevice.BOND_BONDED) break
                    log(app, "pair $classic: ${sh.bondClassic(classic)}")
                    var w = 0
                    while (w < 15_000 && sh.bondState(classic) != BluetoothDevice.BOND_BONDED) { Thread.sleep(1_000); w += 1_000 }
                }
                val ok = sh.bondState(classic) == BluetoothDevice.BOND_BONDED
                if (ok) { status("Connecting"); log(app, "connect $classic: ${sh.connectClassic(classic)}") }
                ok
            }
        }
        val done = bonded == true && waitUntil(30_000) { onClassic(app) }
        log(app, if (done) "on Classic after pairing again" else "couldn't get back on Classic")
        done
    }

    /**
     * Once the phone is on Classic, makes sure the earbuds' own setting says Classic too. They
     * sometimes ignore the switch over LE, or get set back to LE elsewhere, and then keep coming
     * back on LE Audio. Over Classic they take it; they drop and return, which [finish] handles.
     */
    suspend fun syncSetting(context: Context): Boolean {
        val app = context.applicationContext
        val leOn = RefreshCoordinator.awaitSession(app, "Connection mode") { SonyFeatures.readLeOn(it) }.getOrNull()
        if (leOn != true) return true
        log(app, "earbuds still set to LE on Classic, switching them over Classic")
        SonyControlSessionManager.noteSwitch(app)
        val ok = RefreshCoordinator.awaitSession(app, "Connection mode") {
            SonyFeatures.setLeAudio(it, SettingsStore.load(app), false, phoneSupportsLe = LeAudio.phoneSupports(app))
        }.getOrDefault(false)
        log(app, if (ok) "earbuds set to Classic" else "earbuds didn't take Classic")
        return ok && finish(app, justSwitched = true)
    }

    /**
     * From Bluetooth events: if the earbuds are set to Classic but an earbud is on LE Audio, or the
     * Classic link keeps dropping with no audio, repair it. Waits a little first so a normal
     * reconnect can happen on its own, and never runs more than once every 90 seconds.
     */
    fun check(context: Context, reason: String) {
        val app = context.applicationContext
        ShizukuWatch.start(app)
        if (!SonyControlSessionManager.classicOnly(app)) return
        if (lock.isLocked) return
        // A switch in progress handles its own links.
        if (SonyControlSessionManager.switchedRecently(app, 60_000)) return
        if (System.currentTimeMillis() - lastAutoAt < AUTO_GAP_MS) return
        scope.launch {
            delay(10_000)
            val leUp = LeAudio.anyConnected(app)
            if (!leUp && classicConnected(app)) {
                if (!lock.isLocked && System.currentTimeMillis() - lastAutoAt >= AUTO_GAP_MS) {
                    val leOn = RefreshCoordinator.awaitSession(app, "Connection mode") { SonyFeatures.readLeOn(it) }.getOrNull()
                    if (leOn == true) {
                        if (ShizukuBluetooth.state() != ShizukuBluetooth.State.READY) {
                            log(app, "needs fixing (on Classic but earbuds set to LE) but Shizuku isn't running")
                            ShizukuWatch.markPending(app, "set your earbuds back to Classic")
                        } else {
                            lastAutoAt = System.currentTimeMillis()
                            log(app, "fixing by itself: on Classic but earbuds set to LE")
                            syncSetting(app)
                        }
                    }
                }
                return@launch
            }
            // Nothing on LE and no recent link attempts: the earbuds are most likely in the case.
            if (!leUp && !SonyControlSessionManager.aclDroppedRecently(app, 30_000)) return@launch
            if (ShizukuBluetooth.state() != ShizukuBluetooth.State.READY) {
                log(app, "needs fixing ($reason) but Shizuku isn't running")
                ShizukuWatch.markPending(app, "reconnect your earbuds on Classic")
                return@launch
            }
            if (lock.isLocked || System.currentTimeMillis() - lastAutoAt < AUTO_GAP_MS) return@launch
            lastAutoAt = System.currentTimeMillis()
            log(app, "fixing by itself: $reason")
            if (finish(app, justSwitched = false)) syncSetting(app)
        }
    }
}
