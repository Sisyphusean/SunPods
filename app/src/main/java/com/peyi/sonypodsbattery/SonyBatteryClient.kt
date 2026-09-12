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
    NOISE_CANCELLING("Noise cancelling"),
    AMBIENT("Ambient sound"),
    OFF("Off");

    companion object {
        fun fromStored(value: String?): ListeningMode? = entries.firstOrNull { it.name == value }
    }
}

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
