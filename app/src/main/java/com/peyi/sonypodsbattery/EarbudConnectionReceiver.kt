package com.peyi.sonypodsbattery

import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothLeAudio
import android.bluetooth.BluetoothProfile
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

enum class AudioProfile {
    A2DP,
    HEADSET,
    LE_AUDIO
}

enum class AudioProfileConnectionState {
    CONNECTED,
    CONNECTING,
    DISCONNECTED,
    UNKNOWN
}

object EarbudConnectionStateStore {
    private const val PREFS = "earbud_connection"
    private const val STATE_KEY = "state"
    private const val TARGET_ADDRESS_KEY = "target_address"
    private const val A2DP_STATE_KEY = "a2dp_state"
    private const val HEADSET_STATE_KEY = "headset_state"
    private const val LE_AUDIO_STATE_KEY = "le_audio_state"

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
    fun profileState(
        context: Context,
        profile: AudioProfile,
        device: BluetoothDevice?
    ): AudioProfileConnectionState {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val storedAddress = preferences.getString(TARGET_ADDRESS_KEY, null)
        val deviceAddress = try { device?.address } catch (_: SecurityException) { null }
        if (deviceAddress == null || storedAddress != deviceAddress) return AudioProfileConnectionState.UNKNOWN
        val key = when (profile) {
            AudioProfile.A2DP -> A2DP_STATE_KEY
            AudioProfile.HEADSET -> HEADSET_STATE_KEY
            AudioProfile.LE_AUDIO -> LE_AUDIO_STATE_KEY
        }
        return AudioProfileConnectionState.entries.firstOrNull {
            it.name == preferences.getString(key, null)
        } ?: AudioProfileConnectionState.UNKNOWN
    }

    @SuppressLint("MissingPermission")
    fun markProfile(
        context: Context,
        profile: AudioProfile,
        state: AudioProfileConnectionState,
        device: BluetoothDevice
    ) {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = when (profile) {
            AudioProfile.A2DP -> A2DP_STATE_KEY
            AudioProfile.HEADSET -> HEADSET_STATE_KEY
            AudioProfile.LE_AUDIO -> LE_AUDIO_STATE_KEY
        }
        val editor = preferences.edit().putString(key, state.name)
        try {
            editor.putString(TARGET_ADDRESS_KEY, device.address)
        } catch (_: SecurityException) {
            return
        }
        editor.apply()
    }

    fun markProfilesUnknown(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(A2DP_STATE_KEY, AudioProfileConnectionState.UNKNOWN.name)
            .putString(HEADSET_STATE_KEY, AudioProfileConnectionState.UNKNOWN.name)
            .putString(LE_AUDIO_STATE_KEY, AudioProfileConnectionState.UNKNOWN.name)
            .apply()
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
        val isAclAction = action == BluetoothDevice.ACTION_ACL_CONNECTED ||
            action == BluetoothDevice.ACTION_ACL_DISCONNECTED ||
            action == BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED
        val profile = when (action) {
            BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> AudioProfile.A2DP
            BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> AudioProfile.HEADSET
            BluetoothLeAudio.ACTION_LE_AUDIO_CONNECTION_STATE_CHANGED.takeIf {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            } -> AudioProfile.LE_AUDIO
            else -> null
        }
        if (!isAclAction && profile == null) return

        val app = context.applicationContext
        val device = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        else @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        val name = try { device?.name.orEmpty() } catch (_: SecurityException) { "" }
        val address = try { device?.address.orEmpty() } catch (_: SecurityException) { "" }
        val deviceLabel = "${name.ifBlank { "<unknown>" }} ${address.ifBlank { "<unknown>" }}"
        if (isAclAction) {
            val actionLabel = when (action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> "ACL_CONNECTED"
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> "ACL_DISCONNECTED"
                else -> "ACL_DISCONNECT_REQUESTED"
            }
            DiagnosticsStore.add(app, "$actionLabel received: $deviceLabel")
        } else {
            val profileState = profileState(intent)
            DiagnosticsStore.add(app, "${profileLabel(profile!!)} state: ${profileState.name}")
        }

        if (!EarbudConnectionStateStore.isTargetDevice(app, device)) {
            DiagnosticsStore.add(app, "Ignored ACL event: $deviceLabel")
            return
        }

        if (profile != null) {
            val state = profileState(intent)
            EarbudConnectionStateStore.markProfile(app, profile, state, device!!)
            RefreshCoordinator.handleAudioProfileEvent(app, device, profile, state)
            return
        }

        if (action == BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED) {
            EarbudConnectionStateStore.markProfilesUnknown(app)
            EarbudConnectionStateStore.markWithDiagnostics(app, EarbudConnectionState.DISCONNECTED, device)
            RefreshCoordinator.closeSessionForAclDisconnect(app)
            BatteryPollingService.stop(app)
            return
        }

        if (action == BluetoothDevice.ACTION_ACL_DISCONNECTED) {
            EarbudConnectionStateStore.markProfilesUnknown(app)
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

    private fun profileState(intent: Intent): AudioProfileConnectionState {
        return when (intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED)) {
            BluetoothProfile.STATE_CONNECTED -> AudioProfileConnectionState.CONNECTED
            BluetoothProfile.STATE_CONNECTING -> AudioProfileConnectionState.CONNECTING
            BluetoothProfile.STATE_DISCONNECTED,
            BluetoothProfile.STATE_DISCONNECTING -> AudioProfileConnectionState.DISCONNECTED
            else -> AudioProfileConnectionState.UNKNOWN
        }
    }

    private fun profileLabel(profile: AudioProfile): String = when (profile) {
        AudioProfile.A2DP -> "A2DP"
        AudioProfile.HEADSET -> "HEADSET"
        AudioProfile.LE_AUDIO -> "LE_AUDIO"
    }
}
