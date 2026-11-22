package com.peyi.sonypodsbattery

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.AttributionSource
import android.content.ContentResolver
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.os.Looper
import android.os.Process
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * Runs inside Shizuku's process as the shell user, never in the app. The shell user holds
 * BLUETOOTH_PRIVILEGED, so this can do the few pairing steps an app can't: remove an old pairing,
 * pair a device's LE side, and allow and connect LE Audio. It is the same approach that first got
 * LE Audio working on a Samsung phone over adb.
 */
@SuppressLint("MissingPermission", "NewApi", "PrivateApi", "DiscouragedPrivateApi")
class ShellBluetoothService : IShellBluetooth.Stub() {

    /**
     * Just enough of a Context for the Bluetooth classes: identity, main looper and app info.
     * A real system context isn't available here, and Samsung's builds crash building one.
     */
    private class ShellContext : ContextWrapper(null) {
        override fun getPackageName() = SHELL
        override fun getOpPackageName() = SHELL
        override fun getAttributionSource(): AttributionSource = AttributionSource.Builder(Process.myUid()).setPackageName(SHELL).build()
        override fun getApplicationContext(): Context = this
        override fun getMainLooper(): Looper = Looper.getMainLooper()
        override fun getSystemService(name: String): Any? = null
        override fun createDeviceContext(deviceId: Int): Context = this
        override fun getDeviceId() = 0
        override fun getApplicationInfo() = ApplicationInfo().apply { packageName = SHELL; uid = Process.myUid(); targetSdkVersion = 36 }
        override fun createAttributionContext(attributionTag: String?): Context = this
        override fun getContentResolver(): ContentResolver? = null
    }

    private val context: Context = ShellContext()

    // Built on the main thread, where Shizuku constructs this service.
    private val adapter: BluetoothAdapter? = runCatching {
        runCatching { Class.forName("android.app.ActivityThread").getMethod("initializeMainlineModules").invoke(null) }
        val create = BluetoothAdapter::class.java.getDeclaredMethod("createAdapter", Context::class.java)
        create.isAccessible = true
        create.invoke(null, context) as BluetoothAdapter?
    }.getOrNull()

    override fun destroy() {
        exitProcess(0)
    }

    override fun removeBond(address: String): String = report {
        val d = device(address)
        if (d.bondState == BluetoothDevice.BOND_NONE) "not paired"
        else "removeBond: ${BluetoothDevice::class.java.getMethod("removeBond").invoke(d)}"
    }

    override fun bondLe(address: String): String = report {
        val d = leDevice(address)
        if (d.bondState == BluetoothDevice.BOND_BONDED) "already paired"
        else "createBond(LE): ${BluetoothDevice::class.java.getMethod("createBond", Int::class.javaPrimitiveType).invoke(d, BluetoothDevice.TRANSPORT_LE)}"
    }

    override fun bondClassic(address: String): String = report {
        val d = device(address)
        if (d.bondState == BluetoothDevice.BOND_BONDED) "already paired"
        else "createBond(Classic): ${BluetoothDevice::class.java.getMethod("createBond", Int::class.javaPrimitiveType).invoke(d, BluetoothDevice.TRANSPORT_BREDR)}"
    }

    override fun bondState(address: String): Int =
        runCatching { device(address).bondState }.getOrDefault(BluetoothDevice.BOND_NONE)

    override fun connectLe(address: String): String = report {
        val lea = proxy(PROFILE_LE_AUDIO)
        val d = device(address)
        val policy = lea.javaClass.getMethod("setConnectionPolicy", BluetoothDevice::class.java, Int::class.javaPrimitiveType).invoke(lea, d, POLICY_ALLOWED)
        val connect = runCatching { lea.javaClass.getMethod("connect", BluetoothDevice::class.java).invoke(lea, d) }.getOrElse { "n/a" }
        "policy: $policy, connect: $connect"
    }

    override fun connectClassic(address: String): String = report {
        val d = device(address)
        listOf(PROFILE_HEADSET to "calls", PROFILE_A2DP to "media").joinToString(", ") { (profile, name) ->
            val p = proxy(profile)
            val policy = runCatching { p.javaClass.getMethod("setConnectionPolicy", BluetoothDevice::class.java, Int::class.javaPrimitiveType).invoke(p, d, POLICY_ALLOWED) }.getOrElse { "n/a" }
            val connect = runCatching { p.javaClass.getMethod("connect", BluetoothDevice::class.java).invoke(p, d) }.getOrElse { "n/a" }
            "$name policy $policy connect $connect"
        }
    }

    override fun dropLe(address: String): String = report {
        val lea = proxy(PROFILE_LE_AUDIO)
        val d = device(address)
        val policy = lea.javaClass.getMethod("setConnectionPolicy", BluetoothDevice::class.java, Int::class.javaPrimitiveType).invoke(lea, d, POLICY_FORBIDDEN)
        val disconnect = runCatching { lea.javaClass.getMethod("disconnect", BluetoothDevice::class.java).invoke(lea, d) }.getOrElse { "n/a" }
        "policy: $policy, disconnect: $disconnect"
    }

    override fun waitForAdvert(address: String, timeoutMs: Long): Boolean = runCatching {
        val seen = CountDownLatch(1)
        val callback = object : android.bluetooth.le.ScanCallback() {
            override fun onScanResult(type: Int, result: android.bluetooth.le.ScanResult) {
                if (result.device.address.equals(address, ignoreCase = true)) seen.countDown()
            }
        }
        // LE Audio earbuds announce themselves with extended adverts, which legacy scans miss.
        val settings = android.bluetooth.le.ScanSettings.Builder()
            .setScanMode(android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setLegacy(false)
            .setPhy(android.bluetooth.le.ScanSettings.PHY_LE_ALL_SUPPORTED)
            .build()
        val scanner = adapter().bluetoothLeScanner
        scanner.startScan(null, settings, callback)
        try { seen.await(timeoutMs, TimeUnit.MILLISECONDS) } finally { runCatching { scanner.stopScan(callback) } }
    }.getOrDefault(false)

    override fun bonded(): String = report {
        adapter().bondedDevices.joinToString("\n") { "${it.name}|${it.address}|${it.type}" }
    }

    private fun adapter(): BluetoothAdapter = adapter ?: error("Bluetooth isn't available to Shizuku")

    /** A paired device keeps its own address type; otherwise Classic addresses are public. */
    private fun device(address: String): BluetoothDevice =
        adapter().bondedDevices.firstOrNull { it.address.equals(address, ignoreCase = true) }
            ?: adapter().getRemoteDevice(address.uppercase())

    /** LE addresses whose top two bits are set are random static; others are public. */
    private fun leDevice(address: String): BluetoothDevice {
        adapter().bondedDevices.firstOrNull { it.address.equals(address, ignoreCase = true) }?.let { return it }
        val random = (address.substring(0, 2).toInt(16) and 0xC0) == 0xC0
        return adapter().getRemoteLeDevice(address.uppercase(), if (random) BluetoothDevice.ADDRESS_TYPE_RANDOM else BluetoothDevice.ADDRESS_TYPE_PUBLIC)
    }

    private fun proxy(profile: Int): BluetoothProfile {
        val latch = CountDownLatch(1)
        var result: BluetoothProfile? = null
        adapter().getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(p: Int, proxy: BluetoothProfile) { result = proxy; latch.countDown() }
            override fun onServiceDisconnected(p: Int) {}
        }, profile)
        latch.await(5, TimeUnit.SECONDS)
        return result ?: error("LE Audio isn't available")
    }

    private inline fun report(block: () -> String): String =
        runCatching(block).getOrElse { e -> "error: ${(e.cause ?: e).javaClass.simpleName}: ${(e.cause ?: e).message}" }

    private companion object {
        const val SHELL = "com.android.shell"
        const val PROFILE_LE_AUDIO = 22
        const val POLICY_ALLOWED = 100
        const val POLICY_FORBIDDEN = 0
        const val PROFILE_HEADSET = 1
        const val PROFILE_A2DP = 2
    }
}
