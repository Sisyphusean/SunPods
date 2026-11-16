package com.peyi.sonypodsbattery

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context

/*
 * The earbuds' own list of paired devices, as Sound Connect manages it (MDR v2 table 2,
 * "peripheral" commands, sent as frame type 0x0e):
 *
 *   PERI_GET_PARAM          0x36 [inq]                 -> RET 0x37 [inq, n, n x device, playing]
 *       device (inq 0x00):  17 ASCII address, connected order (0 = not connected), name len, name
 *       device (inq 0x02):  the same with a 3 byte class of device after the connected order
 *   PERI_SET_EXTENDED_PARAM 0x3c [inq, action, address] -> NTFY 0x3d [inq, action, result, address]
 *       action: 0 disconnect, 1 connect, 2 forget (unpair)
 *   PERI_SET_EXTENDED_PARAM 0x3c [0x01, address]        -> NTFY 0x3d [0x01, result, address]
 *       move audio playback to that device
 *   PERI_SET_STATUS         0x34 [inq, mode, 0]         mode 1 = discoverable for a new device, 0 = normal
 */

data class PairedDevice(val address: String, val name: String, val connectedOrder: Int) {
    val connected: Boolean get() = connectedOrder > 0
}

data class PairedDevices(val list: List<PairedDevice>, val playingOrder: Int) {
    fun playing(device: PairedDevice) = device.connected && device.connectedOrder == playingOrder
}

enum class DeviceAction(val code: Int) { DISCONNECT(0), CONNECT(1), FORGET(2) }

internal object Multipoint {
    private const val TABLE2 = 0x0e
    private const val GET_PARAM = 0x36
    private const val SET_STATUS = 0x34
    private const val SET_EXTENDED = 0x3c
    private const val NTFY_EXTENDED = 0x3d
    private const val CLASSIC = 0x00
    private const val WITH_CLASS = 0x02
    private const val SOURCE_SWITCH = 0x01

    // Results Sony reports when an action lands (see PeripheralResult).
    private val DONE = setOf(0x00, 0x01, 0x03, 0x10, 0x11, 0x13, 0x20, 0x21, 0x23)
    private val OK = setOf(0x00, 0x10, 0x20)

    @Volatile private var inquired: Int? = null

    /** Reads the list, or null when these earbuds don't answer (no multipoint management). */
    fun read(s: SonyBatteryClient.SonySession): PairedDevices? {
        for (inq in listOfNotNull(inquired) + listOf(CLASSIC, WITH_CLASS).filter { it != inquired }) {
            val p = s.request(byteArrayOf(GET_PARAM.toByte(), inq.toByte()), timeoutMs = 1_200, type = TABLE2) ?: continue
            val parsed = parse(p, inq == WITH_CLASS) ?: continue
            inquired = inq
            return parsed
        }
        return null
    }

    private fun parse(p: ByteArray, withClass: Boolean): PairedDevices? = runCatching {
        val count = u(p[2])
        var i = 3
        val list = (0 until count).map {
            val address = String(p, i, 17, Charsets.US_ASCII)
            val order = u(p[i + 17])
            val nameAt = i + if (withClass) 21 else 18
            val len = u(p[nameAt])
            val name = String(p, nameAt + 1, len, Charsets.UTF_8).trim()
            i = nameAt + 1 + len
            PairedDevice(address, name.ifBlank { address }, order)
        }
        PairedDevices(list, if (i < p.size) u(p[i]) else 0)
    }.getOrNull()

    /** Connect, disconnect or forget; waits for the earbuds to say how it went. */
    fun act(s: SonyBatteryClient.SonySession, device: PairedDevice, action: DeviceAction): Boolean {
        val inq = inquired ?: CLASSIC
        s.send(byteArrayOf(SET_EXTENDED.toByte(), inq.toByte(), action.code.toByte()) + device.address.toByteArray(Charsets.US_ASCII), TABLE2)
        val reply = s.waitFor(15_000) { f ->
            f.type == TABLE2 && f.payload.size >= 4 && u(f.payload[0]) == NTFY_EXTENDED &&
                u(f.payload[1]) == inq && u(f.payload[3]) in DONE
        } ?: return false
        return u(reply.payload[3]) in OK
    }

    /** Moves music playback to [device]. */
    fun playOn(s: SonyBatteryClient.SonySession, device: PairedDevice): Boolean {
        s.send(byteArrayOf(SET_EXTENDED.toByte(), SOURCE_SWITCH.toByte()) + device.address.toByteArray(Charsets.US_ASCII), TABLE2)
        val reply = s.waitFor(8_000) { f ->
            f.type == TABLE2 && f.payload.size >= 3 && u(f.payload[0]) == NTFY_EXTENDED && u(f.payload[1]) == SOURCE_SWITCH
        } ?: return false
        return u(reply.payload[2]) == 0
    }

    /** Makes the earbuds discoverable so another device can pair, or stops that. */
    fun setPairingMode(s: SonyBatteryClient.SonySession, on: Boolean): Boolean {
        val inq = inquired ?: CLASSIC
        return s.command(byteArrayOf(SET_STATUS.toByte(), inq.toByte(), if (on) 0x01 else 0x00, 0x00), type = TABLE2)
    }

    /** This phone's Bluetooth name, to tell which entry in the list is us. */
    @SuppressLint("MissingPermission")
    fun phoneName(context: Context): String? = runCatching {
        context.getSystemService(BluetoothManager::class.java)?.adapter?.name
    }.getOrNull()

    private fun u(b: Byte) = b.toInt() and 0xff
}
