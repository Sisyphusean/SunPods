package com.peyi.sonypodsbattery

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

enum class EarbudConnectionState {
    CONNECTED,
    DISCONNECTED,
    UNKNOWN;

    companion object {
        fun fromStored(value: String?): EarbudConnectionState =
            entries.firstOrNull { it.name == value } ?: UNKNOWN
    }
}

object EarbudConnectionStateStore {
    private const val PREFS = "earbud_connection"
    private const val STATE_KEY = "state"
    private const val TARGET_ADDRESS_KEY = "target_address"

    fun current(context: Context): EarbudConnectionState =
        EarbudConnectionState.fromStored(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(STATE_KEY, null)
        )

    @SuppressLint("MissingPermission")
    fun mark(context: Context, state: EarbudConnectionState, device: BluetoothDevice?) {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = preferences.edit().putString(STATE_KEY, state.name)
        if (device != null) {
            try {
                editor.putString(TARGET_ADDRESS_KEY, device.address)
            } catch (_: SecurityException) {
                // The connection event is still useful even if the address is unavailable.
            }
        }
        editor.apply()
    }

    @SuppressLint("MissingPermission")
    fun markWithDiagnostics(context: Context, state: EarbudConnectionState, device: BluetoothDevice?) {
        val previous = current(context)
        mark(context, state, device)
        if (previous != state) {
            DiagnosticsStore.add(context, "Connection state changed: $state")
        }
    }

    @SuppressLint("MissingPermission")
    fun isTargetDevice(context: Context, device: BluetoothDevice?): Boolean {
        if (device == null) return false
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val knownAddress = preferences.getString(TARGET_ADDRESS_KEY, null)
        return try {
            device.address == knownAddress || SonyBatteryClient.isTargetModel(device.name.orEmpty())
        } catch (_: SecurityException) {
            false
        }
    }
}

class EarbudConnectionReceiver : BroadcastReceiver() {
    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != BluetoothDevice.ACTION_ACL_CONNECTED &&
            action != BluetoothDevice.ACTION_ACL_DISCONNECTED
        ) return

        val app = context.applicationContext
        val device = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        else @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        val name = try { device?.name.orEmpty() } catch (_: SecurityException) { "" }
        val address = try { device?.address.orEmpty() } catch (_: SecurityException) { "" }
        val actionLabel = if (action == BluetoothDevice.ACTION_ACL_CONNECTED) "ACL_CONNECTED" else "ACL_DISCONNECTED"
        DiagnosticsStore.add(app, "$actionLabel received: ${name.ifBlank { "<unknown>" }} ${address.ifBlank { "<unknown>" }}")

        if (!EarbudConnectionStateStore.isTargetDevice(app, device)) {
            DiagnosticsStore.add(app, "Ignored ACL event: ${name.ifBlank { "<unknown>" }} ${address.ifBlank { "<unknown>" }}")
            return
        }

        if (action == BluetoothDevice.ACTION_ACL_DISCONNECTED) {
            EarbudConnectionStateStore.markWithDiagnostics(app, EarbudConnectionState.DISCONNECTED, device)
            DiagnosticsStore.add(app, "Polling stopped: ACL disconnect")
            RefreshCoordinator.closeSessionForAclDisconnect(app)
            BatteryPollingService.stop(app)
            return
        }

        RefreshCoordinator.prepareSessionForAclReconnect(app)
        EarbudConnectionStateStore.markWithDiagnostics(app, EarbudConnectionState.CONNECTED, device)
        BatteryPollingService.start(app)
        val pending = goAsync()
        RefreshCoordinator.enqueueRefresh(
            context = app,
            device = device,
            startPolling = false,
            onComplete = { pending.finish() },
            automatic = true
        )
    }
}
