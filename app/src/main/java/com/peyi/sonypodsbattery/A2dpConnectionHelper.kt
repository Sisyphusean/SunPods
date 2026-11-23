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

    private suspend fun bind(context: Context): BluetoothA2dp? {
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
        if (!requested) return null
        return withTimeoutOrNull(PROFILE_QUERY_TIMEOUT_MS) { result.await() }
    }

    private const val PROFILE_QUERY_TIMEOUT_MS = 800L
}
