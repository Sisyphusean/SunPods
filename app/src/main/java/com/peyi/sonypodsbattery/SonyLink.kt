package com.peyi.sonypodsbattery

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/** A byte pipe to the earbuds' control channel: Classic RFCOMM or, in LE Audio mode, GATT. */
internal interface SonyLink : Closeable {
    val input: InputStream
    val output: OutputStream
    val isConnected: Boolean
    val label: String
}

internal class RfcommLink(private val socket: BluetoothSocket) : SonyLink {
    override val input: InputStream get() = socket.inputStream
    override val output: OutputStream get() = socket.outputStream
    override val isConnected: Boolean get() = socket.isConnected
    override val label = "RFCOMM"
    override fun close() = socket.close()
}

/** Bytes arriving from notifications, read without blocking the way the session expects. */
private class ByteQueueInputStream : InputStream() {
    private val buf = ByteArrayOutputStream()
    private var pos = 0
    @Volatile var closed = false

    @Synchronized fun push(data: ByteArray) { buf.write(data); (this as Object).notifyAll() }

    @Synchronized override fun available(): Int = buf.size() - pos

    @Synchronized override fun read(): Int {
        while (available() == 0) { if (closed) return -1; (this as Object).wait(200) }
        return buf.toByteArray()[pos++].toInt() and 0xff
    }

    @Synchronized override fun read(b: ByteArray, off: Int, len: Int): Int {
        while (available() == 0) { if (closed) return -1; (this as Object).wait(200) }
        val all = buf.toByteArray()
        val n = minOf(len, all.size - pos)
        System.arraycopy(all, pos, b, off, n)
        pos += n
        if (pos == all.size) { buf.reset(); pos = 0 }
        return n
    }

    override fun close() { closed = true }
}

/**
 * Sony's control channel over Bluetooth LE ("Tandem" over GATT), used when the earbuds are in
 * LE Audio mode and Classic RFCOMM isn't there. From Sound Connect:
 *   service  5b833e20  TANDEM_V2_HPC_SERVICE
 *   write    5b833c60  TANDEM_HPC_TO_ACC   (one MDR frame per write)
 *   notify   5b833c61  TANDEM_HPC_FROM_ACC (bytes feed the same frame decoder)
 *   notify   5b833c93  DETERMINE_MTU       (the earbuds say when the MTU is settled)
 *   read     5b833c91  WRITABLE_VALUE_LENGTH
 */
@SuppressLint("MissingPermission")
internal class GattLink private constructor(private val gatt: BluetoothGatt, private val cb: Callback) : SonyLink {
    override val label = "GATT"
    override val input: InputStream get() = cb.incoming
    override val isConnected: Boolean get() = cb.connected

    override val output: OutputStream = object : OutputStream() {
        private val pending = ByteArrayOutputStream()
        override fun write(b: Int) { pending.write(b) }
        override fun write(b: ByteArray, off: Int, len: Int) { pending.write(b, off, len) }
        override fun flush() {
            if (pending.size() == 0) return
            val frame = pending.toByteArray(); pending.reset()
            cb.write(gatt, frame)
        }
    }

    override fun close() {
        cb.connected = false
        cb.incoming.close()
        runCatching { gatt.disconnect() }
        runCatching { gatt.close() }
    }

    private class Callback : BluetoothGattCallback() {
        val incoming = ByteQueueInputStream()
        @Volatile var connected = false
        val ready = CountDownLatch(1)
        @Volatile var services = CountDownLatch(1)
        @Volatile var op = CountDownLatch(1)
        @Volatile var mtuSettled = CountDownLatch(1)
        private val writing = Semaphore(1)
        var toAcc: BluetoothGattCharacteristic? = null

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) { connected = true; g.requestMtu(517) }
            else { connected = false; incoming.close(); services.countDown(); op.countDown(); mtuSettled.countDown() }
        }
        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) { g.discoverServices() }
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) { services.countDown() }
        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) { op.countDown() }
        @Deprecated("Android 12 and below")
        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) { op.countDown() }
        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray, status: Int) { op.countDown() }
        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) { writing.release() }
        @Deprecated("Android 12 and below")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) { changed(c.uuid, c.value ?: return) }
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) { changed(c.uuid, value) }

        private fun changed(uuid: UUID, value: ByteArray) {
            when (uuid) {
                FROM_ACC -> incoming.push(value)
                MTU -> mtuSettled.countDown()
            }
        }

        fun write(g: BluetoothGatt, frame: ByteArray) {
            val c = toAcc ?: throw IOException("Not ready")
            if (!connected) throw IOException("LE link closed")
            if (!writing.tryAcquire(2, TimeUnit.SECONDS)) writing.drainPermits()
            val ok = if (Build.VERSION.SDK_INT >= 33) {
                g.writeCharacteristic(c, frame, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == 0
            } else {
                @Suppress("DEPRECATION") run { c.value = frame; c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT; g.writeCharacteristic(c) }
            }
            if (!ok) { writing.release(); throw IOException("LE write failed") }
        }

        fun notify(g: BluetoothGatt, c: BluetoothGattCharacteristic, on: Boolean): Boolean {
            g.setCharacteristicNotification(c, on)
            val d = c.getDescriptor(CCCD) ?: return false
            val v = if (on) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            op = CountDownLatch(1)
            val started = if (Build.VERSION.SDK_INT >= 33) g.writeDescriptor(d, v) == 0
            else @Suppress("DEPRECATION") run { d.value = v; g.writeDescriptor(d) }
            return started && op.await(3, TimeUnit.SECONDS)
        }

        fun read(g: BluetoothGatt, c: BluetoothGattCharacteristic): Boolean {
            op = CountDownLatch(1)
            return g.readCharacteristic(c) && op.await(2, TimeUnit.SECONDS)
        }
    }

    companion object {
        private fun u(s: String) = UUID.fromString("$s-6bc7-4802-8e9a-723ceca4bd8f")
        val SERVICE: UUID = u("5b833e20")
        val TO_ACC: UUID = u("5b833c60")
        val FROM_ACC: UUID = u("5b833c61")
        val MTU: UUID = u("5b833c93")
        val WRITABLE_LENGTH: UUID = u("5b833c91")
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        /** Opens the LE control channel on [device], following Sound Connect's set-up order. */
        fun open(context: Context, device: BluetoothDevice, log: (String) -> Unit): GattLink {
            val cb = Callback()
            val gatt = device.connectGatt(context.applicationContext, false, cb, BluetoothDevice.TRANSPORT_LE)
                ?: throw IOException("LE connect refused")
            try {
                if (!cb.services.await(10, TimeUnit.SECONDS) || !cb.connected) throw IOException("LE connect timed out")
                val service = gatt.getService(SERVICE) ?: throw IOException("No Sony control service on ${device.address}")
                service.getCharacteristic(MTU)?.let { m ->
                    if (cb.notify(gatt, m, true)) cb.mtuSettled.await(3, TimeUnit.SECONDS)
                    cb.notify(gatt, m, false)
                }
                service.getCharacteristic(WRITABLE_LENGTH)?.let { cb.read(gatt, it) }
                val from = service.getCharacteristic(FROM_ACC) ?: throw IOException("No notify characteristic")
                cb.toAcc = service.getCharacteristic(TO_ACC) ?: throw IOException("No write characteristic")
                if (!cb.notify(gatt, from, true)) throw IOException("Couldn't subscribe to the earbuds")
                log("LE control channel ready on ${device.address}")
                return GattLink(gatt, cb)
            } catch (e: Exception) {
                runCatching { gatt.disconnect() }; runCatching { gatt.close() }
                throw e
            }
        }
    }
}

/** LE Audio state of the earbuds, from Android's own profile (Android 13 and later). */
internal object LeAudio {
    private const val PROFILE_LE_AUDIO = 22
    @Volatile private var proxy: BluetoothProfile? = null

    private fun adapter(context: Context) = context.getSystemService(BluetoothManager::class.java)?.adapter

    /** True when some LE Audio device is connected (cheap; no proxy needed). */
    @SuppressLint("MissingPermission")
    fun anyConnected(context: Context): Boolean = Build.VERSION.SDK_INT >= 33 && runCatching {
        adapter(context)?.getProfileConnectionState(PROFILE_LE_AUDIO) == BluetoothProfile.STATE_CONNECTED
    }.getOrDefault(false)

    /** The earbuds' LE Audio devices (one per earbud), when connected that way. */
    @SuppressLint("MissingPermission")
    fun connectedEarbuds(context: Context): List<BluetoothDevice> {
        if (Build.VERSION.SDK_INT < 33) return emptyList()
        val p = proxy ?: bind(context) ?: return emptyList()
        return runCatching {
            p.connectedDevices.filter { SonyBatteryClient.isTargetModel(runCatching { it.name.orEmpty() }.getOrDefault("")) }
        }.getOrElse { proxy = null; emptyList() }
    }

    private fun bind(context: Context): BluetoothProfile? {
        val latch = CountDownLatch(1)
        val ok = runCatching {
            adapter(context)?.getProfileProxy(context.applicationContext, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, service: BluetoothProfile) { proxy = service; latch.countDown() }
                override fun onServiceDisconnected(profile: Int) { proxy = null }
            }, PROFILE_LE_AUDIO) == true
        }.getOrDefault(false)
        if (ok) latch.await(800, TimeUnit.MILLISECONDS)
        return proxy
    }
}
