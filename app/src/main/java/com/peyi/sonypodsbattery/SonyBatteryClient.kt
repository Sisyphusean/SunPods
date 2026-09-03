package com.peyi.sonypodsbattery

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothSocket
import android.content.Context
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

class SonyBatteryClient(private val context: Context) {
    companion object {
        private val UUID_V2: UUID = UUID.fromString("956c7b26-d49a-4ba8-b03f-b17d393cb6e2")
        private val UUID_V1: UUID = UUID.fromString("96cc203e-5068-46ad-b32d-e316f5e069ba")
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
    fun read(device: BluetoothDevice): Result = withConnectedSession(device) { it.readBattery() }

    @SuppressLint("MissingPermission")
    fun setListeningMode(device: BluetoothDevice, mode: ListeningMode, ambientLevel: Int = 20, focusOnVoice: Boolean = false) {
        withConnectedSession(device) { it.setAmbientSoundMode(mode, ambientLevel, focusOnVoice) }
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

    @SuppressLint("MissingPermission")
    private fun <T> withConnectedSession(device: BluetoothDevice, action: (SonySession) -> T): T {
        var lastError: Exception? = null
        for (uuid in listOf(UUID_V2, UUID_V1)) for (insecure in listOf(true, false)) {
            var socket: BluetoothSocket? = null
            try {
                socket = if (insecure) device.createInsecureRfcommSocketToServiceRecord(uuid)
                else device.createRfcommSocketToServiceRecord(uuid)
                connectWithTimeout(socket)
                DiagnosticsStore.add(context, "RFCOMM connected using protocol ${if (uuid == UUID_V2) "v2" else "v1"}")
                return action(SonySession(socket))
            } catch (error: Exception) {
                lastError = error
            } finally {
                try { socket?.close() } catch (_: Exception) {}
            }
        }
        throw SonyControlChannelUnavailableException(lastError)
    }

    @SuppressLint("MissingPermission")
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

    private class SonySession(socket: BluetoothSocket) {
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
                    if (count > 0) for (frame in decoder.feed(buffer.copyOf(count))) {
                        if (frame.type == SonyProtocol.ACK) sequence = frame.sequence
                        if (frame.type == SonyProtocol.DATA_MDR) {
                            output.write(SonyProtocol.encode(SonyProtocol.ACK, 1 - frame.sequence, byteArrayOf()))
                            output.flush()
                        }
                        if (match(frame)) return frame
                    }
                } else Thread.sleep(20)
            }
            return null
        }

        private fun initialize() {
            send(byteArrayOf(0x00, 0x00))
            waitFor(3_000) { it.type == SonyProtocol.DATA_MDR && it.payload.firstOrNull() == 0x01.toByte() }
                ?: throw IOException("Sony handshake did not answer")
        }

        fun readBattery(): Result {
            initialize()
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
            initialize()
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
