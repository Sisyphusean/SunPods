package com.peyi.sonypodsbattery

import android.content.Context

/** What a widget shows: live levels when the earbuds are really connected, else the last reading. */
internal data class WidgetSnapshot(val connected: Boolean, val state: BatteryState, val last: BatterySample?, val headphones: Boolean = false)

internal fun widgetSnapshot(context: Context): WidgetSnapshot {
    val state = BatteryStore.load(context)
    val hasLevels = state.left != null || state.right != null
    // Saved levels are not proof of a connection: a missed disconnect broadcast leaves them behind.
    // Ask Bluetooth directly each time the widget draws.
    val linkUp = audioLinkUp(context)
    if (hasLevels && !linkUp) {
        DiagnosticsStore.add(context, "Widget: levels saved but no audio link; showing not connected")
        BatteryStore.clearBatteryValues(context)
    }
    val connected = hasLevels && linkUp
    val last = if (connected) null else {
        val logged = BatteryHistory.load(context, System.currentTimeMillis() - 14L * 86_400_000).lastOrNull()
        val saved = if (hasLevels) BatterySample(state.updatedAt, state.left, state.right, state.case, false, false, false) else null
        listOfNotNull(logged, saved).maxByOrNull { it.time }
    }
    return WidgetSnapshot(connected, if (connected) state else BatteryStore.load(context), last, EarbudModel.current(context).headphones)
}

internal fun agoShort(time: Long): String {
    val minutes = ((System.currentTimeMillis() - time) / 60_000).coerceAtLeast(0)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 48 * 60 -> "${minutes / 60} hr ago"
        else -> "${minutes / (24 * 60)} days ago"
    }
}
