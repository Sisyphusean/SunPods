package com.peyi.sonypodsbattery

import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

object A2dpConnectionHelper {
    @SuppressLint("MissingPermission")
    suspend fun isConnected(context: Context, device: BluetoothDevice): Boolean? {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return null
        val serviceResult = CompletableDeferred<BluetoothA2dp?>()
        val serviceReference = AtomicReference<BluetoothA2dp?>(null)
        val listener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, service: BluetoothProfile) {
                val a2dp = service as? BluetoothA2dp
                serviceReference.set(a2dp)
                if (!serviceResult.complete(a2dp) && a2dp != null) {
                    closeProxy(adapter, a2dp)
                }
            }

            override fun onServiceDisconnected(profile: Int) {
                serviceResult.complete(null)
            }
        }

        val requested = try {
            adapter.getProfileProxy(context, listener, BluetoothProfile.A2DP)
        } catch (_: SecurityException) {
            return null
        } catch (_: IllegalArgumentException) {
            return null
        }
        if (!requested) return null

        return try {
            val service = withTimeoutOrNull(PROFILE_QUERY_TIMEOUT_MS) { serviceResult.await() }
            if (service == null) return null
            try {
                service.connectedDevices.any { connectedDevice ->
                    connectedDevice.address == device.address
                }
            } catch (_: SecurityException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        } finally {
            serviceReference.get()?.let { closeProxy(adapter, it) }
        }
    }

    @SuppressLint("MissingPermission")
    private fun closeProxy(adapter: BluetoothAdapter, service: BluetoothA2dp) {
        try {
            adapter.closeProfileProxy(BluetoothProfile.A2DP, service)
        } catch (_: SecurityException) {
            // Cleanup is best effort when Bluetooth permission changes.
        } catch (_: IllegalArgumentException) {
            // Some platform implementations reject an already closed proxy.
        }
    }

    private const val PROFILE_QUERY_TIMEOUT_MS = 800L
}
