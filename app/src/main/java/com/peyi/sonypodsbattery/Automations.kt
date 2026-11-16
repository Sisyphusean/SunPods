package com.peyi.sonypodsbattery

import android.Manifest
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject

/*
 * Automations: SunPods switches noise control for you, the way Sony's Adaptive Sound Control does,
 * but driven by things the phone already knows: a call, walking, Focus/Do Not Disturb, or the
 * Wi-Fi you're on. Everything runs on the phone while the earbuds are connected. When the
 * trigger ends, the mode you had before comes back, unless you changed it yourself meanwhile.
 */

enum class AutoAction(val label: String) {
    NC("Noise cancelling"),
    AMBIENT("Ambient sound"),
    AMBIENT_VOICE("Ambient, focus on voice"),
    OFF("Noise control off");

    val mode: ListeningMode
        get() = when (this) {
            NC -> ListeningMode.NOISE_CANCELLING
            AMBIENT, AMBIENT_VOICE -> ListeningMode.AMBIENT
            OFF -> ListeningMode.OFF
        }

    companion object {
        fun from(name: String?, fallback: AutoAction) = entries.firstOrNull { it.name == name } ?: fallback
    }
}

enum class Trigger(val label: String) { CALL("Call"), WALKING("Walking"), FOCUS("Focus"), PLACE("Place") }

data class Rule(val enabled: Boolean, val action: AutoAction)
data class Place(val ssid: String, val action: AutoAction)

object AutomationStore {
    private const val PREFS = "automations"
    private fun p(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val defaults = mapOf(
        Trigger.CALL to AutoAction.AMBIENT_VOICE,
        Trigger.WALKING to AutoAction.AMBIENT,
        Trigger.FOCUS to AutoAction.NC
    )

    fun rule(context: Context, trigger: Trigger): Rule = Rule(
        p(context).getBoolean("${trigger.name}_on", false),
        AutoAction.from(p(context).getString("${trigger.name}_action", null), defaults[trigger] ?: AutoAction.NC)
    )

    fun setRule(context: Context, trigger: Trigger, rule: Rule) {
        p(context).edit().putBoolean("${trigger.name}_on", rule.enabled).putString("${trigger.name}_action", rule.action.name).apply()
        AutomationEngine.current?.evaluate()
    }

    fun places(context: Context): List<Place> = runCatching {
        val a = JSONArray(p(context).getString("places", "[]"))
        (0 until a.length()).map { a.getJSONObject(it) }.map { Place(it.getString("ssid"), AutoAction.from(it.optString("action"), AutoAction.NC)) }
    }.getOrDefault(emptyList())

    fun setPlaces(context: Context, places: List<Place>) {
        val a = JSONArray()
        places.forEach { a.put(JSONObject().put("ssid", it.ssid).put("action", it.action.name)) }
        p(context).edit().putString("places", a.toString()).apply()
        AutomationEngine.current?.evaluate()
    }

    /** Which automation is in charge right now, for the Home screen. */
    fun active(context: Context): String? = p(context).getString("active_label", null)
    internal fun setActive(context: Context, label: String?) = p(context).edit().putString("active_label", label).apply()

    internal fun before(context: Context): Pair<ListeningMode, Boolean>? {
        val m = p(context).getString("before_mode", null) ?: return null
        return ListeningMode.fromStored(m)?.let { it to p(context).getBoolean("before_voice", false) }
    }
    internal fun setBefore(context: Context, mode: ListeningMode?, voice: Boolean) =
        p(context).edit().putString("before_mode", mode?.name).putBoolean("before_voice", voice).apply()
    internal fun applied(context: Context): String? = p(context).getString("applied", null)
    internal fun setApplied(context: Context, value: String?) = p(context).edit().putString("applied", value).apply()

    fun anyOn(context: Context) = Trigger.entries.any { it != Trigger.PLACE && rule(context, it).enabled } || places(context).isNotEmpty()
}

/** Current Wi-Fi name, or null when not on Wi-Fi or location isn't allowed. */
@Suppress("DEPRECATION")
internal fun currentSsid(context: Context): String? {
    if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
    val raw = runCatching { context.getSystemService(WifiManager::class.java)?.connectionInfo?.ssid }.getOrNull()
    return cleanSsid(raw)
}

private fun cleanSsid(raw: String?): String? =
    raw?.removePrefix("\"")?.removeSuffix("\"")?.takeIf { it.isNotBlank() && it != WifiManager.UNKNOWN_SSID && it != "<unknown ssid>" }

class AutomationEngine private constructor(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var inCall = false
    private var walking = false
    private var focus = false
    private var ssid: String? = null
    private var lastStep = 0L
    private val recentSteps = ArrayDeque<Long>()

    private val audio = context.getSystemService(AudioManager::class.java)
    private val modeListener = AudioManager.OnModeChangedListener { mode ->
        inCall = mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION
        evaluate()
    }

    private val sensors = context.getSystemService(SensorManager::class.java)
    private val stepListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val now = System.currentTimeMillis()
            lastStep = now
            recentSteps.addLast(now)
            while (recentSteps.isNotEmpty() && now - recentSteps.first() > 30_000) recentSteps.removeFirst()
            // Walking: a steady 10 or more steps within 30 seconds.
            if (!walking && recentSteps.size >= 10) {
                walking = true
                evaluate()
            }
            main.removeCallbacks(stopWalking)
            main.postDelayed(stopWalking, 60_000)
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }
    private val stopWalking = Runnable {
        if (walking && System.currentTimeMillis() - lastStep >= 59_000) {
            walking = false
            recentSteps.clear()
            evaluate()
        }
    }

    private val focusReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            focus = readFocus()
            evaluate()
        }
    }

    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val wifiCallback = object : ConnectivityManager.NetworkCallback(
        if (Build.VERSION.SDK_INT >= 31) FLAG_INCLUDE_LOCATION_INFO else 0
    ) {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            val info = if (Build.VERSION.SDK_INT >= 31) caps.transportInfo as? WifiInfo else null
            val name = cleanSsid(info?.ssid) ?: currentSsid(context)
            if (name != ssid) { ssid = name; main.post { evaluate() } }
        }
        override fun onLost(network: Network) { ssid = null; main.post { evaluate() } }
    }

    private fun readFocus(): Boolean =
        context.getSystemService(NotificationManager::class.java)?.currentInterruptionFilter
            ?.let { it != NotificationManager.INTERRUPTION_FILTER_ALL && it != NotificationManager.INTERRUPTION_FILTER_UNKNOWN } ?: false

    private fun start() {
        if (Build.VERSION.SDK_INT >= 31) runCatching { audio?.addOnModeChangedListener(context.mainExecutor, modeListener) }
        inCall = audio?.mode == AudioManager.MODE_IN_CALL || audio?.mode == AudioManager.MODE_IN_COMMUNICATION
        if (context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED) {
            sensors?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)?.let {
                sensors.registerListener(stepListener, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }
        context.registerReceiver(focusReceiver, IntentFilter(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED))
        focus = readFocus()
        runCatching {
            connectivity?.registerNetworkCallback(NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), wifiCallback)
        }
        ssid = currentSsid(context)
        evaluate()
    }

    private fun stop() {
        if (Build.VERSION.SDK_INT >= 31) runCatching { audio?.removeOnModeChangedListener(modeListener) }
        runCatching { sensors?.unregisterListener(stepListener) }
        runCatching { context.unregisterReceiver(focusReceiver) }
        runCatching { connectivity?.unregisterNetworkCallback(wifiCallback) }
        main.removeCallbacksAndMessages(null)
        AutomationStore.setActive(context, null)
    }

    /** Picks the strongest active trigger: a call beats walking beats Focus beats a place. */
    fun evaluate() {
        val rules = Trigger.entries.filter { it != Trigger.PLACE }.associateWith { AutomationStore.rule(context, it) }
        val place = ssid?.let { s -> AutomationStore.places(context).firstOrNull { it.ssid == s } }
        val winner: Pair<String, AutoAction>? = when {
            inCall && rules.getValue(Trigger.CALL).enabled -> "During a call" to rules.getValue(Trigger.CALL).action
            walking && rules.getValue(Trigger.WALKING).enabled -> "Walking" to rules.getValue(Trigger.WALKING).action
            focus && rules.getValue(Trigger.FOCUS).enabled -> "Focus is on" to rules.getValue(Trigger.FOCUS).action
            place != null -> "At ${place.ssid}" to place.action
            else -> null
        }
        val key = winner?.let { "${it.first}|${it.second.name}" }
        if (key == AutomationStore.applied(context)) return
        val now = BatteryStore.load(context)

        if (winner == null) {
            // Trigger ended: restore the earlier mode if the earbuds are still where we put them.
            val before = AutomationStore.before(context)
            val applied = AutomationStore.applied(context)?.substringAfter('|')?.let { AutoAction.from(it, AutoAction.NC) }
            if (before != null && applied != null && now.listeningMode == applied.mode) {
                RefreshCoordinator.enqueueSetListeningMode(context, before.first, now.ambientLevel, before.second, System.currentTimeMillis())
                DiagnosticsStore.add(context, "Automation ended; back to ${before.first.name}")
            }
            AutomationStore.setBefore(context, null, false)
            AutomationStore.setApplied(context, null)
            AutomationStore.setActive(context, null)
            return
        }

        if (AutomationStore.applied(context) == null) AutomationStore.setBefore(context, now.listeningMode, now.focusOnVoice)
        val (label, action) = winner
        RefreshCoordinator.enqueueSetListeningMode(
            context, action.mode, now.ambientLevel,
            if (action == AutoAction.AMBIENT_VOICE) true else if (action == AutoAction.AMBIENT) false else now.focusOnVoice,
            System.currentTimeMillis()
        )
        AutomationStore.setApplied(context, key)
        AutomationStore.setActive(context, label)
        DiagnosticsStore.add(context, "Automation: $label -> ${action.name}")
    }

    companion object {
        @Volatile var current: AutomationEngine? = null
            private set

        fun start(context: Context) {
            if (current != null) return
            current = AutomationEngine(context.applicationContext).also { it.start() }
        }

        fun stop() {
            current?.stop()
            current = null
        }
    }
}
