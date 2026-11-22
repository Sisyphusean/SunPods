package com.peyi.sonypodsbattery

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * True when the phone has a live Bluetooth audio link and the earbuds were not last marked as
 * disconnected. If Bluetooth permission is missing we can't tell, so we trust the saved state.
 */
@SuppressLint("MissingPermission")
internal fun audioLinkUp(context: Context): Boolean {
    val adapter = context.getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter ?: return false
    return try {
        if (!adapter.isEnabled) return false
        // On LE Audio there's no A2DP at all, and the A2DP state saved while switching reads "disconnected".
        if (LeAudio.anyConnected(context)) return true
        val audio = adapter.getProfileConnectionState(android.bluetooth.BluetoothProfile.A2DP) == android.bluetooth.BluetoothProfile.STATE_CONNECTED ||
            adapter.getProfileConnectionState(android.bluetooth.BluetoothProfile.HEADSET) == android.bluetooth.BluetoothProfile.STATE_CONNECTED
        audio && EarbudConnectionStateStore.a2dpState(context) != A2dpConnectionState.DISCONNECTED
    } catch (_: SecurityException) {
        true
    }
}

/** Where the phone was the last time the earbuds were connected. Kept only on this phone. */
data class LastSeen(
    val lat: Double,
    val lng: Double,
    val accuracyM: Float,
    /** When the location itself was taken. */
    val time: Long,
    /** The last moment the earbuds were known to be connected here; what "Last connected" shows. */
    val seenUntil: Long = time,
    /** True when captured at the moment the earbuds disconnected; false for a "still connected here" fix. */
    val atDisconnect: Boolean
)

object LastSeenStore {
    private const val PREFS = "last_seen"
    private const val FRESH_MS = 2L * 60 * 1000
    private const val CONNECTED_THROTTLE_MS = 30L * 60 * 1000

    fun enabled(context: Context): Boolean = prefs(context).getBoolean("enabled", false)
    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("enabled", on).apply()
        if (!on) clear(context)
    }

    /** The opt-in card on Home can be dismissed; it then stays out of the way. */
    fun promptDismissed(context: Context): Boolean = prefs(context).getBoolean("prompt_dismissed", false)
    fun dismissPrompt(context: Context) = prefs(context).edit().putBoolean("prompt_dismissed", true).apply()

    fun load(context: Context): LastSeen? {
        val p = prefs(context)
        if (!p.contains("time")) return null
        return LastSeen(
            lat = p.getString("lat", null)?.toDoubleOrNull() ?: return null,
            lng = p.getString("lng", null)?.toDoubleOrNull() ?: return null,
            accuracyM = p.getFloat("accuracy", 0f),
            time = p.getLong("time", 0L),
            seenUntil = maxOf(p.getLong("seen_until", 0L), p.getLong("time", 0L)),
            atDisconnect = p.getBoolean("at_disconnect", false)
        )
    }

    fun clear(context: Context) {
        prefs(context).edit().remove("lat").remove("lng").remove("accuracy").remove("time").remove("seen_until").remove("at_disconnect").apply()
    }

    fun hasLocationPermission(context: Context): Boolean =
        granted(context, Manifest.permission.ACCESS_FINE_LOCATION) || granted(context, Manifest.permission.ACCESS_COARSE_LOCATION)

    /** "Allow all the time": needed to save the spot when SunPods is closed. */
    fun hasBackgroundPermission(context: Context): Boolean =
        granted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    /** Saves the spot where the earbuds just disconnected. */
    suspend fun captureAtDisconnect(context: Context) {
        stamp(context)
        val current = load(context)
        if (current != null && current.atDisconnect && System.currentTimeMillis() - current.time < 60_000) return
        capture(context, atDisconnect = true)
    }

    /**
     * While connected, refreshes the "last place they were with you" at most every 30 minutes, and
     * only from a location the phone already worked out for something else. No GPS is switched on.
     */
    suspend fun captureWhileConnected(context: Context) {
        stamp(context)
        val current = load(context)
        if (current != null && System.currentTimeMillis() - current.time < CONNECTED_THROTTLE_MS) return
        capture(context, atDisconnect = false)
    }

    /**
     * Notes "still with you" without touching location, so the time stays right even when the
     * place is only refreshed every 30 minutes or a fix at disconnect fails.
     */
    private fun stamp(context: Context) {
        if (!enabled(context) || !prefs(context).contains("time")) return
        prefs(context).edit().putLong("seen_until", System.currentTimeMillis()).apply()
    }

    @SuppressLint("MissingPermission")
    private suspend fun capture(context: Context, atDisconnect: Boolean) {
        if (!enabled(context) || !hasLocationPermission(context)) return
        val fix = try {
            if (atDisconnect) bestFix(context) else passiveFix(context)
        } catch (e: SecurityException) {
            DiagnosticsStore.add(context, "Last seen: location not allowed right now")
            null
        } ?: return
        prefs(context).edit()
            .putString("lat", fix.latitude.toString())
            .putString("lng", fix.longitude.toString())
            .putFloat("accuracy", if (fix.hasAccuracy()) fix.accuracy else 0f)
            .putLong("time", System.currentTimeMillis())
            .putLong("seen_until", System.currentTimeMillis())
            .putBoolean("at_disconnect", atDisconnect)
            .apply()
        DiagnosticsStore.add(context, "Last seen saved (${if (atDisconnect) "disconnect" else "connected"}), ±${fix.accuracy.toInt()} m")
    }

    /** A recent location the phone already has; costs nothing. */
    @SuppressLint("MissingPermission")
    private fun passiveFix(context: Context): Location? {
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        return lm.getProviders(true).mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
            ?.takeIf { System.currentTimeMillis() - it.time < CONNECTED_THROTTLE_MS }
    }

    @SuppressLint("MissingPermission")
    private suspend fun bestFix(context: Context): Location? {
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = lm.getProviders(true)
        val now = System.currentTimeMillis()
        val lastKnown = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        if (lastKnown != null && now - lastKnown.time < FRESH_MS) return lastKnown
        if (Build.VERSION.SDK_INT >= 30) {
            val provider = when {
                Build.VERSION.SDK_INT >= 31 && LocationManager.FUSED_PROVIDER in providers -> LocationManager.FUSED_PROVIDER
                LocationManager.NETWORK_PROVIDER in providers -> LocationManager.NETWORK_PROVIDER
                LocationManager.GPS_PROVIDER in providers -> LocationManager.GPS_PROVIDER
                else -> null
            }
            if (provider != null) {
                val fresh = withTimeoutOrNull(8_000) {
                    suspendCancellableCoroutine<Location?> { cont ->
                        val signal = CancellationSignal()
                        cont.invokeOnCancellation { signal.cancel() }
                        lm.getCurrentLocation(provider, signal, context.mainExecutor) { loc -> if (cont.isActive) cont.resume(loc) }
                    }
                }
                if (fresh != null) return fresh
            }
        }
        return lastKnown?.takeIf { now - it.time < 60L * 60 * 1000 }
    }

    private fun granted(context: Context, permission: String) =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
