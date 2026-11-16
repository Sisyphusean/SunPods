package com.peyi.sonypodsbattery

import android.content.Context

data class BatteryState(
    val left: Int? = null,
    val right: Int? = null,
    val case: Int? = null,
    val leftCharging: Boolean = false,
    val rightCharging: Boolean = false,
    val caseCharging: Boolean = false,
    val listeningMode: ListeningMode? = null,
    val ambientLevel: Int = 20,
    val focusOnVoice: Boolean = false,
    val updatedAt: Long = 0L,
    val lastCheckedAt: Long = 0L,
    val status: String = "Ready"
)

object BatteryStore {
    private const val PREFS = "battery_state"

    /** The saved battery values; listen to it to see refreshes made anywhere in the app. */
    fun prefs(context: Context): android.content.SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): BatteryState {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        fun level(key: String) = p.getInt(key, -1).takeIf { it in 0..100 }
        return BatteryState(
            left = level("left"), right = level("right"), case = level("case"),
            leftCharging = p.getBoolean("left_charging", false),
            rightCharging = p.getBoolean("right_charging", false),
            caseCharging = p.getBoolean("case_charging", false),
            listeningMode = ListeningMode.fromStored(p.getString("listening_mode", null)),
            ambientLevel = p.getInt("ambient_level", 20).coerceIn(1, 20),
            focusOnVoice = p.getBoolean("focus_on_voice", false),
            updatedAt = p.getLong("updated_at", 0L),
            lastCheckedAt = p.getLong("last_checked_at", 0L),
            status = p.getString("status", "Ready") ?: "Ready"
        )
    }

    fun save(context: Context, state: BatteryState) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            fun putLevel(key: String, value: Int?) {
                if (value == null) remove(key) else putInt(key, value)
            }
            putLevel("left", state.left); putLevel("right", state.right); putLevel("case", state.case)
            putBoolean("left_charging", state.leftCharging)
            putBoolean("right_charging", state.rightCharging)
            putBoolean("case_charging", state.caseCharging)
            if (state.listeningMode == null) remove("listening_mode") else putString("listening_mode", state.listeningMode.name)
            putInt("ambient_level", state.ambientLevel)
            putBoolean("focus_on_voice", state.focusOnVoice)
            putLong("updated_at", state.updatedAt)
            putLong("last_checked_at", state.lastCheckedAt)
            putString("status", state.status)
        }.apply()
        BatteryHistory.record(context, state)
    }

    fun clearBatteryValues(context: Context, status: String = "Earbuds disconnected") {
        save(
            context,
            load(context).copy(
                left = null,
                right = null,
                case = null,
                leftCharging = false,
                rightCharging = false,
                caseCharging = false,
                status = status
            )
        )
    }
}

object DiagnosticsStore {
    private const val PREFS = "diagnostics"
    private const val KEY = "events"

    fun add(context: Context, message: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = prefs.getString(KEY, "").orEmpty().lineSequence().filter { it.isNotBlank() }.toMutableList()
        current += "${System.currentTimeMillis()}: $message"
        prefs.edit().putString(KEY, current.takeLast(40).joinToString("\n")).apply()
    }

    fun text(context: Context): String = buildString {
        appendLine("SunPods diagnostics")
        appendLine("Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
        appendLine("App ${SunpodsInfo.VERSION}")
        append(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "No events yet"))
    }
}
