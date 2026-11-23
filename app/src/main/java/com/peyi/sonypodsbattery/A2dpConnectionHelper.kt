package com.peyi.sonypodsbattery

import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Is this exact pair of earbuds on A2DP right now? The profile proxy is opened once and kept,
 * because binding it costs a few hundred milliseconds and every refresh asks this first.
 */
object A2dpConnectionHelper {
    @Volatile
    private var proxy: BluetoothA2dp? = null

    @SuppressLint("MissingPermission")
    suspend fun isConnected(context: Context, device: BluetoothDevice): Boolean? {
        if (SimMode.active(context)) return true
        // In LE Audio mode the earbuds aren't on A2DP at all. Look them up off the main thread:
        // binding the LE Audio profile waits for a callback that's delivered on the main thread.
        if (LeAudio.anyConnected(context)) {
            val le = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { LeAudio.connectedEarbuds(context) }
            if (le.isNotEmpty()) return true
        }
        val service = proxy ?: bind(context) ?: return null
        return try {
            service.connectedDevices.any { it.address == device.address }
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            // A dead proxy (Bluetooth restarted): drop it and bind afresh next time.
            proxy = null
            null
        }
    }

    /** Devices on A2DP right now, from the already-bound proxy; empty if it isn't bound yet. */
    fun connectedNow(): List<BluetoothDevice> = runCatching { proxy?.connectedDevices }.getOrNull().orEmpty()

    /** Binds the proxy if it isn't yet, so [connectedNow] can steer which paired entry is used. */
    suspend fun ensureBound(context: Context) {
        if (proxy == null && !SimMode.active(context)) bind(context)
    }

    /** Whether Android has any media (A2DP) and any call (HFP) audio link up, whatever the device. */
    @SuppressLint("MissingPermission")
    fun profilesUp(context: Context): Pair<Boolean, Boolean> = runCatching {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        (adapter?.getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothProfile.STATE_CONNECTED) to
            (adapter?.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothProfile.STATE_CONNECTED)
    }.getOrDefault(false to false)

    /** Whether Android has any Bluetooth media or call audio up, whatever the device. */
    fun audioUp(context: Context): Boolean = profilesUp(context).let { (media, calls) -> media || calls }

    /** One line for diagnostics: which entry was picked and what each audio profile says. */
    @SuppressLint("MissingPermission")
    fun describe(target: BluetoothDevice, onA2dp: Boolean?, media: Boolean, calls: Boolean, le: Boolean): String {
        val kind = runCatching {
            when (target.type) { BluetoothDevice.DEVICE_TYPE_CLASSIC -> "Classic"; BluetoothDevice.DEVICE_TYPE_DUAL -> "dual"; BluetoothDevice.DEVICE_TYPE_LE -> "LE only"; else -> "unknown type" }
        }.getOrDefault("type hidden")
        val listed = connectedNow().joinToString(", ") { d -> "…" + d.address.takeLast(5) + (if (SonyBatteryClient.isSupportedEntry(d)) " (Sony)" else "") }.ifEmpty { "none" }
        return "Link: using …${target.address.takeLast(5)} ($kind, ${if (EarbudAddresses.isRandom(target.address)) "random" else "public"} address), " +
            "on A2DP ${onA2dp ?: "unknown"}; A2DP lists $listed; media ${if (media) "up" else "down"}, calls ${if (calls) "up" else "down"}, " +
            "LE Audio ${if (le) "up" else "down"}"
    }

    @Volatile private var bindFailedAt = 0L

    private suspend fun bind(context: Context): BluetoothA2dp? {
        // A bind that just failed isn't retried within the same refresh (it costs up to 0.8 s).
        if (System.currentTimeMillis() - bindFailedAt < 5_000) return null
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return null
        val result = CompletableDeferred<BluetoothA2dp?>()
        val listener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, service: BluetoothProfile) {
                val a2dp = service as? BluetoothA2dp
                proxy = a2dp
                result.complete(a2dp)
            }

            override fun onServiceDisconnected(profile: Int) {
                proxy = null
                result.complete(null)
            }
        }
        val requested = try {
            adapter.getProfileProxy(context.applicationContext, listener, BluetoothProfile.A2DP)
        } catch (_: SecurityException) {
            return null
        } catch (_: IllegalArgumentException) {
            return null
        }
        if (!requested) { bindFailedAt = System.currentTimeMillis(); return null }
        return withTimeoutOrNull(PROFILE_QUERY_TIMEOUT_MS) { result.await() }.also { if (it == null) bindFailedAt = System.currentTimeMillis() }
    }

    private const val PROFILE_QUERY_TIMEOUT_MS = 800L
}
