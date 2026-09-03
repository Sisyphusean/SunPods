package com.peyi.sonypodsbattery

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Build
import java.io.EOFException
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

enum class ListeningMode(val label: String) {
    NOISE_CANCELLING("Noise cancelling"),
    AMBIENT("Ambient sound"),
    OFF("Off");

    companion object {
        fun fromStored(value: String?): ListeningMode? = entries.firstOrNull { it.name == value }
    }
}

class SonyControlChannelUnavailableException(cause: Throwable?) :
    IOException("Sony control channel unavailable", cause)

enum class AudioProfileAvailability {
    AVAILABLE,
    DISCONNECTED,
    UNKNOWN
}

data class AudioProfileSnapshot(
    val states: Map<AudioProfile, AudioProfileConnectionState>,
    val unresolved: Boolean
) {
    val availability: AudioProfileAvailability
        get() = when {
            states.values.any {
                it == AudioProfileConnectionState.CONNECTED ||
                    it == AudioProfileConnectionState.CONNECTING
            } -> AudioProfileAvailability.AVAILABLE
            unresolved -> AudioProfileAvailability.UNKNOWN
            else -> AudioProfileAvailability.DISCONNECTED
        }
}

object SonyAudioProfileQuery {
    @SuppressLint("MissingPermission")
    suspend fun snapshot(context: Context, device: BluetoothDevice): AudioProfileSnapshot {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: return AudioProfileSnapshot(emptyMap(), unresolved = true)
        val profiles = mutableListOf(
            AudioProfile.A2DP to BluetoothProfile.A2DP,
            AudioProfile.HEADSET to BluetoothProfile.HEADSET
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            profiles += AudioProfile.LE_AUDIO to BluetoothProfile.LE_AUDIO
        }

        val states = linkedMapOf<AudioProfile, AudioProfileConnectionState>()
        var unresolved = false
        for ((profile, profileId) in profiles) {
            val result = queryProfile(context, adapter, profileId, device)
            if (!result.available) continue
            val state = result.state
            if (state == null || state == AudioProfileConnectionState.UNKNOWN) {
                unresolved = true
            } else {
                states[profile] = state
                EarbudConnectionStateStore.markProfile(context, profile, state, device)
            }
        }
        return AudioProfileSnapshot(states, unresolved)
    }

    @SuppressLint("MissingPermission")
    private suspend fun queryProfile(
        context: Context,
        adapter: BluetoothAdapter,
        profileId: Int,
        device: BluetoothDevice
    ): ProfileQueryResult {
        val result = CompletableDeferred<BluetoothProfile?>()
        val proxyReference = AtomicReference<BluetoothProfile?>(null)
        val listener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, service: BluetoothProfile) {
                proxyReference.set(service)
                if (!result.complete(service)) {
                    closeProfileProxy(adapter, profile, service)
                }
            }

            override fun onServiceDisconnected(profile: Int) {
                result.complete(null)
            }
        }

        val requested = try {
            adapter.getProfileProxy(context, listener, profileId)
        } catch (_: SecurityException) {
            return ProfileQueryResult(available = true, state = null)
        } catch (_: IllegalArgumentException) {
            return ProfileQueryResult(available = false, state = null)
        }
        if (!requested) return ProfileQueryResult(available = false, state = null)

        return try {
            val service = withTimeoutOrNull(PROFILE_QUERY_TIMEOUT_MS) { result.await() }
            if (service == null) return ProfileQueryResult(available = true, state = null)
            val connected = try {
                service.connectedDevices.any { connectedDevice ->
                    connectedDevice.address == device.address
                }
            } catch (_: SecurityException) {
                false
            } catch (_: IllegalArgumentException) {
                false
            }
            val state = try {
                if (connected) BluetoothProfile.STATE_CONNECTED else service.getConnectionState(device)
            } catch (_: SecurityException) {
                return ProfileQueryResult(available = true, state = null)
            } catch (_: IllegalArgumentException) {
                return ProfileQueryResult(available = true, state = null)
            }
            val connectionState = when (state) {
                BluetoothProfile.STATE_CONNECTED -> AudioProfileConnectionState.CONNECTED
                BluetoothProfile.STATE_CONNECTING -> AudioProfileConnectionState.CONNECTING
                BluetoothProfile.STATE_DISCONNECTED,
                BluetoothProfile.STATE_DISCONNECTING -> AudioProfileConnectionState.DISCONNECTED
                else -> AudioProfileConnectionState.UNKNOWN
            }
            ProfileQueryResult(available = true, state = connectionState)
        } finally {
            proxyReference.get()?.let { service -> closeProfileProxy(adapter, profileId, service) }
        }
    }

    @SuppressLint("MissingPermission")
    private fun closeProfileProxy(adapter: BluetoothAdapter, profileId: Int, service: BluetoothProfile) {
        try {
            adapter.closeProfileProxy(profileId, service)
        } catch (_: SecurityException) {
            // Profile cleanup is best effort when Bluetooth permission changes.
        } catch (_: IllegalArgumentException) {
            // Some platform implementations reject an already closed proxy.
        }
    }

    private data class ProfileQueryResult(
        val available: Boolean,
        val state: AudioProfileConnectionState?
    )

    private const val PROFILE_QUERY_TIMEOUT_MS = 800L
}

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
    private var activeSocket: BluetoothSocket? = null
    private var connectingSocket: BluetoothSocket? = null
    private var activeSession: SonyBatteryClient.SonySession? = null
    private var connectionGeneration = 0L

    @Synchronized
    fun currentState(): SonySessionState = state

    @SuppressLint("MissingPermission")
    internal fun <T> withSession(
        context: Context,
        device: BluetoothDevice,
        action: (SonyBatteryClient.SonySession) -> T
    ): T {
        val session = getOrOpen(context, device)
        return try {
            action(session)
        } catch (error: SonyControlChannelUnavailableException) {
            throw error
        } catch (error: Exception) {
            close(context, "I/O failure: ${error.message ?: error.javaClass.simpleName}")
            throw SonyControlChannelUnavailableException(error)
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

    fun prepareAclReconnect(context: Context) {
        if (currentState() == SonySessionState.RECONNECT_WAIT) {
            close(context, "ACL connected")
        }
    }

    fun closeForAclDisconnect(context: Context) {
        close(context, "ACL disconnect")
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
        val address = device.address
        synchronized(this) {
            if (state == SonySessionState.CONNECTED && activeAddress == address) {
                return activeSession ?: error("Connected Sony session is missing")
            }
        }

        val generation = synchronized(this) {
            closeSocketOnly()
            connectionGeneration += 1L
            updateStateLocked(context, SonySessionState.CONNECTING)
            connectionGeneration
        }
        var lastError: Exception? = null
        for (uuid in listOf(UUID_V2, UUID_V1)) for (insecure in listOf(true, false)) {
            var socket: BluetoothSocket? = null
            try {
                socket = if (insecure) device.createInsecureRfcommSocketToServiceRecord(uuid)
                else device.createRfcommSocketToServiceRecord(uuid)
                synchronized(this) {
                    connectingSocket = socket
                }
                connectWithTimeout(socket)
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
                    updateStateLocked(context, SonySessionState.CONNECTED)
                }
                DiagnosticsStore.add(
                    context,
                    "RFCOMM session opened using protocol ${if (uuid == UUID_V2) "v2" else "v1"}"
                )
                return session
            } catch (error: Exception) {
                lastError = error
                DiagnosticsStore.add(context, "RFCOMM session failure: ${error.message ?: error.javaClass.simpleName}")
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
        private val MODEL = Regex("WF[- ]?1000XM5", RegexOption.IGNORE_CASE)

        fun isTargetModel(name: String): Boolean = MODEL.containsMatchIn(name)
    }

    data class Result(
        val left: Int?, val right: Int?, val case: Int?,
        val leftCharging: Boolean, val rightCharging: Boolean, val caseCharging: Boolean
    )

    @SuppressLint("MissingPermission")
    fun findEarbuds(): BluetoothDevice? {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return null
        return adapter.bondedDevices.firstOrNull { device -> MODEL.containsMatchIn(device.name.orEmpty()) }
    }

    @SuppressLint("MissingPermission")
    fun read(device: BluetoothDevice): Result =
        SonyControlSessionManager.withSession(context, device) { it.readBattery() }

    @SuppressLint("MissingPermission")
    fun setListeningMode(device: BluetoothDevice, mode: ListeningMode, ambientLevel: Int = 20, focusOnVoice: Boolean = false) {
        SonyControlSessionManager.withSession(context, device) {
            it.setAmbientSoundMode(mode, ambientLevel, focusOnVoice)
        }
    }

    @SuppressLint("MissingPermission")
    fun hasConnectedBluetoothProfile(device: BluetoothDevice): Boolean? {
        val manager = context.getSystemService(BluetoothManager::class.java) ?: return null
        return try {
            listOf(
                BluetoothProfile.GATT,
                BluetoothProfile.GATT_SERVER
            ).any { profile ->
                manager.getConnectionState(device, profile) == BluetoothProfile.STATE_CONNECTED
            }
        } catch (_: SecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    internal class SonySession(private val socket: BluetoothSocket) {
        private val input = socket.inputStream
        private val output = socket.outputStream
        private val decoder = SonyProtocol.Decoder()
        private var sequence = 0

        private fun send(payload: ByteArray) {
            output.write(SonyProtocol.encode(SonyProtocol.DATA_MDR, sequence, payload))
            output.flush()
            sequence = 1 - sequence
        }

        private fun waitFor(timeoutMs: Long, match: (SonyFrame) -> Boolean): SonyFrame? {
            val deadline = System.currentTimeMillis() + timeoutMs
            val buffer = ByteArray(1024)
            while (System.currentTimeMillis() < deadline) {
                val available = input.available()
                if (available > 0) {
                    val count = input.read(buffer, 0, minOf(buffer.size, available))
                    if (count < 0) throw EOFException("Sony control channel reached EOF")
                    if (count > 0) for (frame in decoder.feed(buffer.copyOf(count))) {
                        if (frame.type == SonyProtocol.ACK) sequence = frame.sequence
                        if (frame.type == SonyProtocol.DATA_MDR) {
                            output.write(SonyProtocol.encode(SonyProtocol.ACK, 1 - frame.sequence, byteArrayOf()))
                            output.flush()
                        }
                        if (match(frame)) return frame
                    }
                } else {
                    if (!socket.isConnected) throw EOFException("Sony control channel disconnected")
                    Thread.sleep(20)
                }
            }
            return null
        }

        fun initialize() {
            send(byteArrayOf(0x00, 0x00))
            waitFor(3_000) { it.type == SonyProtocol.DATA_MDR && it.payload.firstOrNull() == 0x01.toByte() }
                ?: throw IOException("Sony handshake did not answer")
        }

        fun readBattery(): Result {
            send(byteArrayOf(0x22, 0x09))
            val buds = waitFor(3_000) { frame ->
                frame.type == SonyProtocol.DATA_MDR && frame.payload.size >= 6 &&
                    frame.payload[0] == 0x23.toByte() && frame.payload[1] == 0x09.toByte()
            } ?: throw IOException("Earbud battery was not reported")
            send(byteArrayOf(0x22, 0x0a))
            val caseFrame = waitFor(1_800) { frame ->
                frame.type == SonyProtocol.DATA_MDR && frame.payload.size >= 4 &&
                    frame.payload[0] == 0x23.toByte() && frame.payload[1] == 0x0a.toByte()
            }
            return Result(
                left = percent(buds.payload[2]), right = percent(buds.payload[4]),
                case = caseFrame?.payload?.get(2)?.let(::percent),
                leftCharging = buds.payload[3].toInt() != 0, rightCharging = buds.payload[5].toInt() != 0,
                caseCharging = caseFrame?.payload?.get(3)?.let { it.toInt() != 0 } == true
            )
        }

        fun setAmbientSoundMode(mode: ListeningMode, ambientLevel: Int, focusOnVoice: Boolean) {
            val enabled = if (mode == ListeningMode.OFF) 0 else 1
            val ambient = if (mode == ListeningMode.AMBIENT) 1 else 0
            send(byteArrayOf(0x68, 0x15, 0x01, enabled.toByte(), ambient.toByte(), if (focusOnVoice) 0x01 else 0x00, ambientLevel.coerceIn(1, 20).toByte()))
            waitFor(2_500) { frame ->
                frame.type == SonyProtocol.ACK ||
                    (frame.type == SonyProtocol.DATA_MDR && frame.payload.firstOrNull() in listOf(0x67.toByte(), 0x69.toByte()))
            } ?: throw IOException("Earbuds did not confirm the listening mode")
        }

        private fun percent(value: Byte): Int? = (value.toInt() and 0xff).takeIf { it in 0..100 }
    }
}
