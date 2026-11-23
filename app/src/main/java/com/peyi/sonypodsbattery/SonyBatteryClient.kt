package com.peyi.sonypodsbattery

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import java.io.EOFException
import java.io.IOException
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

enum class ListeningMode(val label: String) {
    NOISE_CANCELLING("Noise Cancelling"),
    AMBIENT("Ambient Sound"),
    OFF("Off");

    companion object {
        fun fromStored(value: String?): ListeningMode? = entries.firstOrNull { it.name == value }
    }
}

/** The earbuds' confirmation alert (99 ...) and the app's answer to it (98 ...). */
private const val ALERT_NOTIFY = 0x99.toByte()
private const val ALERT_ANSWER = 0x98.toByte()

/** Which inquired type these earbuds answer noise control questions on, once known. */
@Volatile private var noiseType: Byte? = null

class SonyControlChannelUnavailableException(cause: Throwable?) :
    IOException("Sony control channel unavailable", cause)

enum class SonySessionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECT_WAIT
}

object SonyControlSessionManager {
    private val UUID_V2: UUID = UUID.fromString("956c7b26-d49a-4ba8-b03f-b17d393cb6e2")
    private val UUID_V1: UUID = UUID.fromString("96cc203e-5068-46ad-b32d-e316f5e069ba")

    private var state = SonySessionState.DISCONNECTED
    private var activeAddress: String? = null
    /** The LE earbud the control channel is open on, in LE Audio mode. */
    @Volatile private var activeLeAddress: String? = null
    private var activeSocket: SonyLink? = null
    private var connectingSocket: SonyLink? = null
    private var activeSession: SonyBatteryClient.SonySession? = null
    private const val IDLE_DROP_MS = 90_000L
    private var connectionGeneration = 0L
    @Volatile private var lastGood: Pair<java.util.UUID, Boolean>? = null

    @Synchronized
    fun currentState(): SonySessionState = state

    @SuppressLint("MissingPermission")
    /** One command at a time on the link, so live checks never interleave with other requests. */
    private val io = java.util.concurrent.locks.ReentrantLock()

    internal fun <T> withSession(
        context: Context,
        device: BluetoothDevice,
        action: (SonyBatteryClient.SonySession) -> T
    ): T {
        io.lock()
        try {
            val session = getOrOpen(context, device)
            return try {
                action(session)
            } catch (error: SonyControlChannelUnavailableException) {
                throw error
            } catch (refused: EarbudsUnavailableException) {
                // The earbuds answered, they just said no: the link is fine, so keep it and don't retry.
                throw refused
            } catch (error: Exception) {
                close(context, "I/O failure: ${error.message ?: error.javaClass.simpleName}")
                throw SonyControlChannelUnavailableException(error)
            }
        } finally {
            io.unlock()
        }
    }

    /**
     * Like [withSession], then runs the same action on the other earbud in LE Audio mode. Over LE
     * each earbud has its own control channel and keeps its own settings (a change sent to the
     * right earbud never reaches the left), so Sony's app writes to both. Classic needs nothing:
     * the earbuds share settings between themselves. The other earbud failing isn't fatal.
     */
    internal fun <T> withBothEarbuds(
        context: Context,
        device: BluetoothDevice,
        action: (SonyBatteryClient.SonySession) -> T
    ): T {
        io.lock()
        try {
            val result = withSession(context, device, action)
            onOtherEarbud(context) { action(it) }
            return result
        } finally {
            io.unlock()
        }
    }

    /**
     * The earbuds' own connection setting as last seen: true = Classic only. While they're set to
     * Classic, SunPods never opens its LE control channel. Holding that link open after a switch
     * kept the right earbud on LE Audio indefinitely, because it waits for the phone to let go.
     */
    fun setClassicOnly(context: Context, classicOnly: Boolean) {
        context.getSharedPreferences("sunpods_connection", Context.MODE_PRIVATE).edit().putBoolean("classic_only", classicOnly).apply()
    }

    fun classicOnlyKnown(context: Context): Boolean =
        context.getSharedPreferences("sunpods_connection", Context.MODE_PRIVATE).contains("classic_only")

    fun classicOnly(context: Context): Boolean {
        val p = context.getSharedPreferences("sunpods_connection", Context.MODE_PRIVATE)
        if (p.contains("classic_only")) return p.getBoolean("classic_only", false)
        // Installs from before this flag: fall back to the last reading of the earbuds' setting.
        return SettingsStore.load(context).leOn == false
    }

    /** A switch between Classic and LE Audio just started; the links are expected to be in flux. */
    fun noteSwitch(context: Context) {
        context.getSharedPreferences("sunpods_connection", Context.MODE_PRIVATE).edit().putLong("switch_at", System.currentTimeMillis()).apply()
    }

    fun switchedRecently(context: Context, withinMs: Long = 90_000): Boolean =
        System.currentTimeMillis() - context.getSharedPreferences("sunpods_connection", Context.MODE_PRIVATE).getLong("switch_at", 0) < withinMs

    /** The earbuds' Classic link dropping; a run of these with no audio means a broken pairing. */
    fun noteAclDrop(context: Context) {
        context.getSharedPreferences("sunpods_connection", Context.MODE_PRIVATE).edit().putLong("acl_drop_at", System.currentTimeMillis()).apply()
    }

    fun aclDroppedRecently(context: Context, withinMs: Long = 60_000): Boolean =
        System.currentTimeMillis() - context.getSharedPreferences("sunpods_connection", Context.MODE_PRIVATE).getLong("acl_drop_at", 0) < withinMs

    /** Drops the LE control channel so the earbuds can leave LE Audio, after a switch to Classic. */
    fun releaseLe(context: Context) {
        setClassicOnly(context, true)
        io.lock()
        try {
            if (activeSocket is GattLink || connectingSocket is GattLink) close(context, "earbuds switched to Classic")
        } finally {
            io.unlock()
        }
    }

    /** Runs [action] on a short-lived control channel to the other LE earbud, if there is one. */
    @SuppressLint("MissingPermission")
    internal fun <T> onOtherEarbud(context: Context, action: (SonyBatteryClient.SonySession) -> T): T? {
        if (activeSocket !is GattLink) return null
        val main = activeLeAddress ?: run { DiagnosticsStore.add(context, "Other earbud: skipped, no LE address for this session"); return null }
        val connected = LeAudio.connectedEarbuds(context)
        val other = connected.firstOrNull { !it.address.equals(main, true) } ?: run {
            DiagnosticsStore.add(context, "Other earbud: skipped, session on $main, LE earbuds ${connected.map { it.address }}")
            return null
        }
        io.lock()
        var link: SonyLink? = null
        return try {
            link = GattLink.open(context, other) { }
            val session = SonyBatteryClient.SonySession(link)
            session.initialize()
            action(session).also { DiagnosticsStore.add(context, "Other earbud (${other.address}): done") }
        } catch (error: Exception) {
            DiagnosticsStore.add(context, "Other earbud (${other.address}) didn't take the change: ${error.message ?: error.javaClass.simpleName}")
            null
        } finally {
            runCatching { link?.close() }
            io.unlock()
        }
    }

    /**
     * Like [withSession], but returns null if another command is still using the link after
     * [waitMs] (at once by default).
     */
    internal fun <T> withSessionIfFree(
        context: Context,
        device: BluetoothDevice,
        waitMs: Long = 0,
        action: (SonyBatteryClient.SonySession) -> T
    ): T? {
        if (!(if (waitMs > 0) io.tryLock(waitMs, java.util.concurrent.TimeUnit.MILLISECONDS) else io.tryLock())) return null
        try {
            return withSession(context, device, action)
        } finally {
            io.unlock()
        }
    }

    @SuppressLint("MissingPermission")
    fun ensureConnected(context: Context, device: BluetoothDevice) {
        getOrOpen(context, device)
    }

    fun enterReconnectWait(context: Context) {
        updateState(context, SonySessionState.RECONNECT_WAIT)
    }

    fun prepareManualReconnect(context: Context) {
        if (currentState() == SonySessionState.RECONNECT_WAIT) {
            close(context, "manual reconnect requested")
        }
    }

    fun prepareA2dpReconnect(context: Context) {
        if (currentState() == SonySessionState.RECONNECT_WAIT) {
            close(context, "A2DP connected")
        }
    }

    fun closeForA2dpDisconnect(context: Context) {
        close(context, "A2DP disconnect")
    }

    fun closeForCommandRetry(context: Context) {
        close(context, "stale session command retry")
    }

    fun closeForServiceStop(context: Context) {
        if (currentState() != SonySessionState.RECONNECT_WAIT) {
            close(context, "polling service stopped")
        } else {
            closeSocketOnly()
        }
    }

    @SuppressLint("MissingPermission")
    private fun getOrOpen(context: Context, device: BluetoothDevice): SonyBatteryClient.SonySession {
        ProtocolCapture.attach(context)
        val address = device.address
        val idle = synchronized(this) {
            val session = activeSession
            if (state == SonySessionState.CONNECTED && activeAddress == address && session != null) {
                if (System.currentTimeMillis() - session.lastHeard < IDLE_DROP_MS) return session
                // The LE channel reports a drop straight away, so a quiet one is still good: reuse it
                // instead of closing and reopening it at every check. If it has gone stale after
                // all, the command fails and the caller's retry opens a fresh one.
                if (activeSocket is GattLink && activeSocket?.isConnected == true) return session
                true
            } else false
        }
        // An idle link is usually already gone on the earbuds' side; reopening (~0.3 s) beats
        // waiting 3 s for a reply that never comes.
        if (idle) close(context, "idle over ${IDLE_DROP_MS / 1000} s, reopening")

        val generation = synchronized(this) {
            closeSocketOnly()
            connectionGeneration += 1L
            updateStateLocked(context, SonySessionState.CONNECTING)
            connectionGeneration
        }
        var lastError: Exception? = null
        // Debug builds can run against simulated earbuds (SimMode).
        SimMode.link(context)?.let { link ->
            val session = SonyBatteryClient.SonySession(link)
            session.initialize()
            synchronized(this) {
                connectingSocket = null
                activeSocket = link
                activeAddress = address
                activeSession = session
                updateStateLocked(context, SonySessionState.CONNECTED)
            }
            DiagnosticsStore.add(context, "Simulated earbuds session opened")
            return session
        }
        // In LE Audio mode there's no Classic link to open; go straight to the LE control channel.
        if (LeAudio.anyConnected(context)) {
            if (classicOnly(context)) {
                // Set to Classic but the phone is still on LE: stay off the LE link so it can drop.
                synchronized(this) { if (connectionGeneration == generation) updateStateLocked(context, SonySessionState.DISCONNECTED) }
                throw SonyControlChannelUnavailableException(IOException("Earbuds are leaving LE Audio"))
            }
            openGatt(context, device, generation)?.let { return it }
        }
        val attempts = listOf(UUID_V2, UUID_V1).flatMap { u -> listOf(true, false).map { u to it } }
        var timeouts = 0
        for ((uuid, insecure) in listOfNotNull(lastGood) + (attempts - listOfNotNull(lastGood).toSet())) {
            // Two timeouts mean the earbuds aren't answering at this address at all; the other ways in
            // would time out too, so stop instead of making the user wait another 10 s.
            if (timeouts >= 2) break
            var socket: SonyLink? = null
            try {
                val raw = if (insecure) device.createInsecureRfcommSocketToServiceRecord(uuid)
                else device.createRfcommSocketToServiceRecord(uuid)
                socket = RfcommLink(raw)
                synchronized(this) {
                    connectingSocket = socket
                }
                connectWithTimeout(raw)
                val session = SonyBatteryClient.SonySession(socket)
                session.initialize()
                synchronized(this) {
                    if (connectionGeneration != generation || state != SonySessionState.CONNECTING) {
                        throw IOException("Sony session connection was cancelled")
                    }
                    connectingSocket = null
                    activeSocket = socket
                    activeAddress = address
                    activeSession = session
                    lastGood = uuid to insecure
                    updateStateLocked(context, SonySessionState.CONNECTED)
                }
                DiagnosticsStore.add(
                    context,
                    "RFCOMM session opened using protocol ${if (uuid == UUID_V2) "v2" else "v1"}"
                )
                return session
            } catch (error: Exception) {
                lastError = error
                if ((error.message ?: "").contains("timed out", ignoreCase = true) || error is java.util.concurrent.TimeoutException) timeouts++
                DiagnosticsStore.add(
                    context,
                    "RFCOMM session failure (${if (uuid == UUID_V2) "v2" else "v1"}, ${if (insecure) "insecure" else "secure"}, …${address.takeLast(5)}): " +
                        (error.message ?: error.javaClass.simpleName)
                )
            } finally {
                synchronized(this) {
                    if (connectingSocket === socket) connectingSocket = null
                }
                val socketIsActive = synchronized(this) { activeSocket === socket }
                if (!socketIsActive) {
                    try { socket?.close() } catch (_: Exception) {}
                }
            }
        }
        synchronized(this) {
            if (connectionGeneration == generation) {
                activeAddress = null
                activeSocket = null
                activeSession = null
                updateStateLocked(context, SonySessionState.DISCONNECTED)
            }
        }
        throw SonyControlChannelUnavailableException(lastError)
    }

    /** Tries the LE control channel on each of the earbuds' LE devices, then the paired device. */
    @SuppressLint("MissingPermission")
    private fun openGatt(context: Context, device: BluetoothDevice, generation: Long): SonyBatteryClient.SonySession? {
        val candidates = (LeAudio.connectedEarbuds(context) + device).distinctBy { it.address }
        for (candidate in candidates) {
            var link: SonyLink? = null
            try {
                link = GattLink.open(context, candidate) { DiagnosticsStore.add(context, it) }
                synchronized(this) { connectingSocket = link }
                val session = SonyBatteryClient.SonySession(link)
                session.initialize()
                synchronized(this) {
                    if (connectionGeneration != generation || state != SonySessionState.CONNECTING) throw IOException("cancelled")
                    connectingSocket = null
                    activeSocket = link
                    activeAddress = device.address
                    activeLeAddress = candidate.address
                    activeSession = session
                    updateStateLocked(context, SonySessionState.CONNECTED)
                }
                DiagnosticsStore.add(context, "LE session opened on ${candidate.address}")
                return session
            } catch (error: Exception) {
                DiagnosticsStore.add(context, "LE session failure on ${candidate.address}: ${error.message ?: error.javaClass.simpleName}")
                synchronized(this) { if (connectingSocket === link) connectingSocket = null }
                runCatching { link?.close() }
            }
        }
        return null
    }

    @Synchronized
    private fun closeSocketOnly() {
        val socket = activeSocket ?: connectingSocket
        activeSocket = null
        connectingSocket = null
        activeAddress = null
        activeSession = null
        try { socket?.close() } catch (_: Exception) {}
    }

    private fun close(context: Context, reason: String) {
        synchronized(this) {
            connectionGeneration += 1L
            closeSocketOnly()
            updateStateLocked(context, SonySessionState.DISCONNECTED)
        }
        DiagnosticsStore.add(context, "RFCOMM session closed: $reason")
    }

    private fun updateState(context: Context, nextState: SonySessionState) {
        synchronized(this) {
            updateStateLocked(context, nextState)
        }
    }

    private fun updateStateLocked(context: Context, nextState: SonySessionState) {
        if (state == nextState) return
        state = nextState
        DiagnosticsStore.add(context, "RFCOMM session state changed: $nextState")
    }

    private fun connectWithTimeout(socket: BluetoothSocket) {
        val executor = Executors.newSingleThreadExecutor()
        val future = executor.submit { socket.connect() }
        try {
            future.get(7, TimeUnit.SECONDS)
        } catch (error: Exception) {
            try { socket.close() } catch (_: Exception) {}
            future.cancel(true)
            throw IOException("Connection timed out", error)
        } finally {
            executor.shutdownNow()
        }
    }
}

class SonyBatteryClient(private val context: Context) {
    companion object {
        /** The earbuds whose name last set the model. */
        @Volatile private var notedAddress: String? = null
        @Volatile private var lastPick: String? = null
        @Volatile private var lastPickAt = 0L

        fun isTargetModel(name: String): Boolean = EarbudModel.isSupported(name)

        /** A paired entry of supported earbuds; false when Android won't give its name. */
        @SuppressLint("MissingPermission")
        fun isSupportedEntry(device: BluetoothDevice): Boolean = runCatching { EarbudModel.isSupported(device.name) }.getOrDefault(false)
    }

    data class Result(
        val left: Int?, val right: Int?, val case: Int?,
        val leftCharging: Boolean, val rightCharging: Boolean, val caseCharging: Boolean,
        /** The earbuds' own bytes, for diagnostics. */
        val raw: String = "",
        /** The listening mode the earbuds say they're in, or null if they didn't answer. */
        val mode: ModeReport? = null
    )

    /** Noise control as the earbuds report it (reply 0x67 or notification 0x69, inquired type 0x15). */
    data class ModeReport(val mode: ListeningMode, val focusOnVoice: Boolean, val ambientLevel: Int, val raw: String) {
        companion object {
            fun parse(p: ByteArray): ModeReport? {
                if (p.size < 7 || (p[0] != 0x67.toByte() && p[0] != 0x69.toByte()) || (p[1] != 0x15.toByte() && p[1] != 0x17.toByte())) return null
                val mode = when {
                    p[3].toInt() == 0 -> ListeningMode.OFF
                    p[4].toInt() == 0 -> ListeningMode.NOISE_CANCELLING
                    p[4].toInt() == 1 -> ListeningMode.AMBIENT
                    else -> return null
                }
                return ModeReport(
                    mode, p[5].toInt() == 1, (p[6].toInt() and 0xff).coerceIn(1, 20),
                    p.joinToString(" ") { "%02x".format(it.toInt() and 0xff) }
                )
            }
        }
    }

    /**
     * The earbuds to talk to. Their Bluetooth name also tells which model they are, so the right
     * name and pictures show before the first full read of their settings.
     */
    @SuppressLint("MissingPermission")
    fun findEarbuds(): BluetoothDevice? {
        SimMode.device(context)?.let { device -> SimMode.model(context)?.let { EarbudModel.noteModelName(it.displayName) }; return device }
        return pickEarbuds()?.also { device ->
            if (device.address != notedAddress) { notedAddress = device.address; runCatching { EarbudModel.noteModelName(device.name) } }
        }
    }

    @SuppressLint("MissingPermission")
    private fun pickEarbuds(): BluetoothDevice? {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return null
        val bonded = adapter.bondedDevices.filter { device -> EarbudModel.isSupported(device.name) }
        if (bonded.isEmpty()) return null
        // The entry on A2DP right now is the one Android is using, so it wins over anything
        // remembered: Classic and LE Audio bond separately, and a remembered entry can go stale
        // (a WF-1000XM5 on Classic was skipped as disconnected on every refresh because of that).
        val live = A2dpConnectionHelper.connectedNow().map { it.address }.toSet()
        val pick = bonded.firstOrNull { it.address in live }
            ?: lastPick?.takeIf { System.currentTimeMillis() - lastPickAt < 30_000 }?.let { addr -> bonded.firstOrNull { it.address == addr } }
            ?: EarbudAddresses.classic(context)?.let { saved -> bonded.firstOrNull { it.address.equals(saved, true) } }
            // Not playing and nothing remembered: the Classic entry (public address), since an
            // LE-only entry never shows on A2DP and has no Classic control channel.
            ?: bonded.firstOrNull { it.type != BluetoothDevice.DEVICE_TYPE_LE && !EarbudAddresses.isRandom(it.address) }
            ?: bonded.first()
        if (bonded.size > 1 && lastPick != pick.address) DiagnosticsStore.add(
            context,
            "Earbuds: ${bonded.size} paired entries, using ${if (pick.address in live) "the one playing" else if (EarbudAddresses.isRandom(pick.address)) "an LE entry" else "the Classic entry"} (…${pick.address.takeLast(5)})"
        )
        lastPick = pick.address
        lastPickAt = System.currentTimeMillis()
        return pick
    }

    @SuppressLint("MissingPermission")
    fun read(device: BluetoothDevice): Result =
        SonyControlSessionManager.withSession(context, device) { s ->
            val result = s.readBattery()
            val mode = runCatching { s.readMode() }.getOrNull()
            runCatching { QuietHours.enforce(context, s) }
            runCatching { SonyFeatures.refreshCodec(context, s) }
            result.copy(mode = mode)
        }

    /** Levels, case and mode only, for live updates. Null if another command is using the link. */
    @SuppressLint("MissingPermission")
    /**
     * The quick check while the app is open. It waits a moment for the link rather than skipping,
     * because the now-playing read polls on the same beat and would otherwise win every time.
     */
    fun readLive(device: BluetoothDevice): Result? =
        SonyControlSessionManager.withSessionIfFree(context, device, waitMs = 1_500) { s ->
            s.readBattery().copy(mode = runCatching { s.readMode() }.getOrNull())
        }

    @SuppressLint("MissingPermission")
    fun setListeningMode(device: BluetoothDevice, mode: ListeningMode, ambientLevel: Int = 20, focusOnVoice: Boolean = false) {
        val note = SonyControlSessionManager.withBothEarbuds(context, device) {
            it.setAmbientSoundMode(mode, ambientLevel, focusOnVoice)
        }
        DiagnosticsStore.add(context, "Listening mode $mode: $note")
    }

    internal class SonySession(private val socket: SonyLink) {
        private val input = socket.input
        /** When the earbuds last sent anything; they quietly drop an idle link after a few minutes. */
        @Volatile var lastHeard = System.currentTimeMillis()
        private val output = socket.output
        private val decoder = SonyProtocol.Decoder()
        private var sequence = 0

        internal fun send(payload: ByteArray, type: Int = SonyProtocol.DATA_MDR) {
            output.write(SonyProtocol.encode(type, sequence, payload))
            output.flush()
            sequence = 1 - sequence
        }

        /**
         * Frames already read but not yet looked at: one read can carry several (an ACK and the
         * alert after it, say), and the ones after a match belong to the next wait.
         */
        private val backlog = ArrayDeque<SonyFrame>()

        internal fun waitFor(timeoutMs: Long, match: (SonyFrame) -> Boolean): SonyFrame? {
            val deadline = System.currentTimeMillis() + timeoutMs
            val buffer = ByteArray(1024)
            while (true) {
                while (backlog.isNotEmpty()) {
                    val frame = backlog.removeFirst()
                    if (match(frame)) return frame
                }
                if (System.currentTimeMillis() >= deadline) return null
                val available = input.available()
                if (available > 0) {
                    val count = input.read(buffer, 0, minOf(buffer.size, available))
                    if (count < 0) throw EOFException("Sony control channel reached EOF")
                    if (count > 0) lastHeard = System.currentTimeMillis()
                    if (count > 0) for (frame in decoder.feed(buffer.copyOf(count))) {
                        if (frame.type == SonyProtocol.ACK) sequence = frame.sequence
                        else {
                            if (BuildConfig.DEBUG) android.util.Log.d("SunPods", "rx ${frame.type}: ${hex(frame.payload)}")
                            ProtocolCapture.record(frame.type, frame.payload)
                        }
                        if (!socket.acksItself && (frame.type == SonyProtocol.DATA_MDR || frame.type == SonyProtocol.DATA_MDR_NO2)) {
                            output.write(SonyProtocol.encode(SonyProtocol.ACK, 1 - frame.sequence, byteArrayOf()))
                            output.flush()
                        }
                        // The earbuds announce mode changes they make themselves (touch, one earbud in or out).
                        if (frame.type == SonyProtocol.DATA_MDR) ModeReport.parse(frame.payload)?.let { reportedMode = it }
                        // A confirmation alert the earbuds want answered (see withConfirmation).
                        if (frame.type == SonyProtocol.DATA_MDR && frame.payload.size >= 3 && frame.payload[0] == ALERT_NOTIFY) lastAlert = frame.payload
                        backlog.addLast(frame)
                    }
                } else {
                    if (!socket.isConnected) throw EOFException("Sony control channel disconnected")
                    Thread.sleep(20)
                }
            }
        }

        @Volatile private var lastAlert: ByteArray? = null
        private var alertsOn = false

        /**
         * Runs a change the earbuds only apply after the app confirms it, such as Connect to Two
         * Devices or LE Audio. Sound Connect switches the earbuds' confirmation alerts on for every
         * connection (94 00 00); with them off, the earbuds acknowledge such a change and then drop
         * it. With them on, the earbuds send an alert (99 [kind, code, ...]) and wait for the answer
         * (98 [kind, code, 01] for OK). Returns the alert that was confirmed, or null if none came.
         */
        fun withConfirmation(timeoutMs: Long = 2_500, change: () -> Unit): ByteArray? {
            // Sent before every confirmed change, not once per session: a switch back to Classic
            // over LE Audio was seen to get no alert when this had been sent earlier on the link.
            command(byteArrayOf(0x94.toByte(), 0x00, 0x00), 1_000)
            alertsOn = true
            lastAlert = null
            change()
            val alert = lastAlert ?: waitFor(timeoutMs) { lastAlert != null }?.let { lastAlert } ?: return null
            send(byteArrayOf(ALERT_ANSWER, alert[1], alert[2], 0x01))
            return alert
        }

        fun initialize() {
            send(byteArrayOf(0x00, 0x00))
            waitFor(3_000) { it.type == SonyProtocol.DATA_MDR && it.payload.firstOrNull() == 0x01.toByte() }
                ?: throw IOException("Sony handshake did not answer")
        }

        /** What these earbuds support, asked once per connection (it can differ between Classic and LE). */
        private var supported: SupportedFunctions? = null
        private var supportedAsked = false

        fun functions(): SupportedFunctions? {
            if (!supportedAsked) { supportedAsked = true; supported = SupportedFunctions.read(this) }
            return supported
        }

        fun readBattery(): Result {
            // Both earbuds and the case, on the inquired types these earbuds list: with thresholds
            // (0x09, 0x0a; the WF-1000XM5) or without (0x01, 0x02). Same fields either way.
            val f = functions()
            val budsType: Byte = if (f != null && !f.has1(Fn.LR_BATTERY_THRESHOLD) && f.has1(Fn.LR_BATTERY)) 0x01 else 0x09
            val caseType: Byte? = when {
                f == null || f.has1(Fn.CRADLE_BATTERY_THRESHOLD) -> 0x0a
                f.has1(Fn.CRADLE_BATTERY) -> 0x02
                else -> null
            }
            send(byteArrayOf(0x22, budsType))
            val buds = waitFor(3_000) { frame ->
                frame.type == SonyProtocol.DATA_MDR && frame.payload.size >= 6 &&
                    frame.payload[0] == 0x23.toByte() && frame.payload[1] == budsType
            } ?: throw IOException("Earbud battery was not reported")
            val caseFrame = caseType?.let { t ->
                send(byteArrayOf(0x22, t))
                waitFor(700) { frame ->
                    frame.type == SonyProtocol.DATA_MDR && frame.payload.size >= 4 &&
                        frame.payload[0] == 0x23.toByte() && frame.payload[1] == t
                }
            }
            return Result(
                left = percent(buds.payload[2]), right = percent(buds.payload[4]),
                case = caseFrame?.payload?.get(2)?.let(::percent),
                // Status byte: 0 not charging, 1 charging, 2 unknown, 3 charged (full, still on power).
                leftCharging = charging(buds.payload[3]), rightCharging = charging(buds.payload[5]),
                caseCharging = caseFrame?.payload?.get(3)?.let(::charging) == true,
                raw = "buds=${hex(buds.payload)} case=${caseFrame?.payload?.let(::hex) ?: "none"}"
            )
        }

        /** Latest mode the earbuds reported on this session, from a reply or a notification. */
        @Volatile var reportedMode: ModeReport? = null

        /** Asks the earbuds which listening mode they're actually in. */
        fun readMode(): ModeReport? {
            // The type these earbuds list for noise control, else the last one that answered, else probe.
            val listed = functions()?.ncAsmType?.toByte()
            val types = listed?.let { listOf(it) } ?: noiseType?.let { listOf(it) } ?: listOf<Byte>(0x17, 0x15)
            for (t in types) {
                val p = request(byteArrayOf(0x66, t), 800) ?: continue
                android.util.Log.i("SunPods", "Noise control reply for type ${"%02x".format(t)}: ${hex(p)}")
                noiseType = t
                return ModeReport.parse(p) ?: reportedMode
            }
            return reportedMode
        }

        private fun charging(status: Byte) = status.toInt() == 1 || status.toInt() == 3
        private fun hex(b: ByteArray) = b.joinToString(" ") { "%02x".format(it.toInt() and 0xff) }

        /**
         * Sets noise control and checks the earbuds really applied it. Over LE Audio the XM5 has been
         * seen to acknowledge the change and then stay where it was, so after the ACK this reads the
         * mode back; if it didn't take, it retries once with confirmation alerts on (94 00 00) and
         * answers any alert, the way Sound Connect does. Returns a line for diagnostics.
         */
        fun setAmbientSoundMode(mode: ListeningMode, ambientLevel: Int, focusOnVoice: Boolean): String {
            val enabled = if (mode == ListeningMode.OFF) 0 else 1
            val ambient = if (mode == ListeningMode.AMBIENT) 1 else 0
            val type = functions()?.ncAsmType?.toByte()
                ?: noiseType ?: 0x17.toByte()
            // The XM5 takes noise control on type 0x17; an unknown type is acknowledged but ignored.
            val payload = byteArrayOf(0x68, type, 0x01, enabled.toByte(), ambient.toByte(), if (focusOnVoice) 0x01 else 0x00, ambientLevel.coerceIn(1, 20).toByte())
            val seen = StringBuilder()
            fun sendAndWatch() {
                send(payload)
                waitFor(2_500) { frame ->
                    if (frame.type != SonyProtocol.ACK) seen.append(" rx${frame.type}[${hex(frame.payload)}]")
                    frame.type == SonyProtocol.ACK ||
                        (frame.type == SonyProtocol.DATA_MDR && frame.payload.firstOrNull() in listOf(0x67.toByte(), 0x69.toByte()))
                } ?: throw IOException("Earbuds did not confirm the listening mode")
                // Give the earbuds a moment to announce the result (69 17) or an alert (99).
                waitFor(400) { frame ->
                    if (frame.type != SonyProtocol.ACK) seen.append(" rx${frame.type}[${hex(frame.payload)}]")
                    false
                }
            }
            fun applied(): ModeReport? = readMode()?.takeIf { it.mode == mode }

            sendAndWatch()
            applied()?.let { return "applied (sent ${hex(payload)};$seen)" }
            val first = "not applied, earbuds report ${reportedMode?.raw ?: "nothing"} (sent ${hex(payload)};$seen)"
            seen.setLength(0)
            val alert = withConfirmation(1_500) { sendAndWatch() }
            val after = applied()
            return "$first; retry with alerts${alert?.let { " answered ${hex(it)}" } ?: ""}:$seen -> " +
                if (after != null) "applied" else "still ${reportedMode?.raw ?: "unknown"}"
        }

        private fun percent(value: Byte): Int? = (value.toInt() and 0xff).takeIf { it in 0..100 }

        /** Sends a GET (payload[0] = command) and returns the matching RET payload (command + 1, same inquired type). */
        fun request(payload: ByteArray, timeoutMs: Long = 1_500, type: Int = SonyProtocol.DATA_MDR): ByteArray? {
            val retCode = ((payload[0].toInt() and 0xff) + 1).toByte()
            val inquired = payload.getOrNull(1)
            send(payload, type)
            return waitFor(timeoutMs) { frame ->
                frame.type == type && frame.payload.size >= 2 &&
                    frame.payload[0] == retCode && (inquired == null || frame.payload[1] == inquired)
            }?.payload
        }

        /** Sends a SET and waits for the earbuds to acknowledge it. */
        fun command(payload: ByteArray, timeoutMs: Long = 2_000, type: Int = SonyProtocol.DATA_MDR): Boolean {
            send(payload, type)
            return waitFor(timeoutMs) { it.type == SonyProtocol.ACK } != null
        }
    }
}
