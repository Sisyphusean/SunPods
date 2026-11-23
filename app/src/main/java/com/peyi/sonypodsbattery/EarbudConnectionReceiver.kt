package com.peyi.sonypodsbattery

import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

enum class EarbudConnectionState {
    CONNECTED,
    DISCONNECTED,
    UNKNOWN;

    companion object {
        fun fromStored(value: String?): EarbudConnectionState =
            entries.firstOrNull { it.name == value } ?: UNKNOWN
    }
}

enum class A2dpConnectionState {
    CONNECTED,
    DISCONNECTED,
    UNKNOWN
}

object EarbudConnectionStateStore {
    private const val PREFS = "earbud_connection"
    private const val STATE_KEY = "state"
    private const val A2DP_STATE_KEY = "a2dp_state"
    private const val TARGET_ADDRESS_KEY = "target_address"

    fun prefs(context: Context): android.content.SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun current(context: Context): EarbudConnectionState =
        EarbudConnectionState.fromStored(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(STATE_KEY, null)
        )

    fun a2dpState(context: Context): A2dpConnectionState =
        A2dpConnectionState.entries.firstOrNull {
            it.name == context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(A2DP_STATE_KEY, null)
        } ?: A2dpConnectionState.UNKNOWN

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
    fun markA2dp(context: Context, state: A2dpConnectionState, device: BluetoothDevice) {
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(A2DP_STATE_KEY, state.name)
        try {
            editor.putString(TARGET_ADDRESS_KEY, device.address)
        } catch (_: SecurityException) {
            return
        }
        editor.apply()
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

/** BluetoothLeAudio.ACTION_LE_AUDIO_CONNECTION_STATE_CHANGED (Android 13), same extras as A2DP. */
internal const val LE_AUDIO_CONNECTION_STATE_CHANGED = "android.bluetooth.action.LE_AUDIO_CONNECTION_STATE_CHANGED"

class EarbudConnectionReceiver : BroadcastReceiver() {
    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED &&
            action != LE_AUDIO_CONNECTION_STATE_CHANGED &&
            action != BluetoothDevice.ACTION_ACL_DISCONNECTED
        ) return

        val app = context.applicationContext
        val device = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
        val name = try { device?.name.orEmpty() } catch (_: SecurityException) { "" }
        val address = try { device?.address.orEmpty() } catch (_: SecurityException) { "" }
        val deviceLabel = "${name.ifBlank { "<unknown>" }} ${address.ifBlank { "<unknown>" }}"
        if (!EarbudConnectionStateStore.isTargetDevice(context.applicationContext, device)) {
            DiagnosticsStore.add(context.applicationContext, "Ignored Bluetooth event: $deviceLabel")
            return
        }

        if (action == LE_AUDIO_CONNECTION_STATE_CHANGED &&
            intent.getIntExtra(android.bluetooth.BluetoothProfile.EXTRA_STATE, -1) == android.bluetooth.BluetoothProfile.STATE_CONNECTED
        ) {
            ClassicRepair.check(app, "LE Audio connected while set to Classic")
            AutoPlay.onConnected(app)
        }

        if (action == BluetoothDevice.ACTION_ACL_DISCONNECTED) {
            val target = device ?: return
            DiagnosticsStore.add(context.applicationContext, "ACL disconnected fallback: $deviceLabel")
            SonyControlSessionManager.noteAclDrop(context.applicationContext)
            ClassicRepair.check(context.applicationContext, "Classic link dropped")
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    if (stillConnected(context.applicationContext)) {
                        DiagnosticsStore.add(context.applicationContext, "Ignored disconnect, still connected by another link: $deviceLabel")
                        return@launch
                    }
                    val spot = launch { LastSeenStore.captureAtDisconnect(context.applicationContext) }
                    RefreshCoordinator.handleDeviceDisconnected(context.applicationContext, target)
                    spot.join()
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }

        val state = intent.getIntExtra(
            BluetoothProfile.EXTRA_STATE,
            BluetoothProfile.STATE_DISCONNECTED
        )

        val a2dpState = when (state) {
            BluetoothProfile.STATE_CONNECTED -> A2dpConnectionState.CONNECTED
            BluetoothProfile.STATE_DISCONNECTED -> A2dpConnectionState.DISCONNECTED
            BluetoothProfile.STATE_CONNECTING,
            BluetoothProfile.STATE_DISCONNECTING -> return
            else -> return
        }
        val stateLabel = a2dpState.name
        DiagnosticsStore.add(app, "A2DP state: $stateLabel $deviceLabel")
        val target = device ?: return
        if (a2dpState == A2dpConnectionState.CONNECTED) {
            EarbudConnectionStateStore.markA2dp(app, a2dpState, target)
            BatteryStore.markCaseFresh(app)
            RefreshCoordinator.handleA2dpConnected(app, target)
            AutoPlay.onConnected(app)
        } else {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    if (stillConnected(app)) {
                        DiagnosticsStore.add(app, "Ignored disconnect, still connected by another link: $deviceLabel")
                        return@launch
                    }
                    EarbudConnectionStateStore.markA2dp(app, a2dpState, target)
                    val spot = launch { LastSeenStore.captureAtDisconnect(app) }
                    RefreshCoordinator.handleDeviceDisconnected(app, target)
                    spot.join()
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}

/**
 * True while these earbuds still have an audio link of either kind. Switching between Classic and
 * LE Audio drops one link while the other carries on, which isn't the earbuds going away.
 */
private suspend fun stillConnected(context: Context): Boolean = runCatching {
    if (LeAudio.connectedEarbuds(context).isNotEmpty()) return@runCatching true
    val earbuds = SonyBatteryClient(context).findEarbuds() ?: return@runCatching false
    A2dpConnectionHelper.isConnected(context, earbuds) == true
}.getOrDefault(false)
