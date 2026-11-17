package com.peyi.sonypodsbattery

import android.content.Context
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/*
 * Windy: in wind, noise cancelling and ambient sound both feed the gusts hitting the outer mics
 * into your ears. Noise control off stops that. Windy remembers what you had and puts it back.
 */
internal object WindMode {
    private const val PREFS = "wind_mode"
    /** Ignore the earbuds' older mode report for a moment after switching. */
    private const val SETTLE_MS = 15_000L

    data class Target(val mode: ListeningMode, val level: Int, val focus: Boolean)

    private fun p(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun on(context: Context) = p(context).getBoolean("on", false)

    /** Turns Windy on: remembers the current mode and returns noise control off. */
    fun start(context: Context): Target {
        val s = BatteryStore.load(context)
        p(context).edit()
            .putBoolean("on", true)
            .putString("prev", (s.listeningMode ?: ListeningMode.NOISE_CANCELLING).name)
            .putInt("level", s.ambientLevel)
            .putBoolean("focus", s.focusOnVoice)
            .putLong("at", System.currentTimeMillis())
            .commit()
        refreshSurfaces(context)
        return Target(ListeningMode.OFF, s.ambientLevel, s.focusOnVoice)
    }

    /** Turns Windy off and returns the mode to go back to. */
    fun stop(context: Context): Target {
        val pr = p(context)
        val s = BatteryStore.load(context)
        val prev = ListeningMode.entries.firstOrNull { it.name == pr.getString("prev", null) } ?: ListeningMode.NOISE_CANCELLING
        val target = Target(prev, pr.getInt("level", s.ambientLevel), pr.getBoolean("focus", s.focusOnVoice))
        clear(context)
        return target
    }

    /** Picking a mode yourself ends Windy. */
    fun clear(context: Context) {
        if (!on(context)) return
        p(context).edit().putBoolean("on", false).commit()
        refreshSurfaces(context)
    }

    /** Ends Windy if the earbuds were switched to something else (on the buds or in another app). */
    fun reconcile(context: Context, state: BatteryState) {
        if (!on(context)) return
        if (System.currentTimeMillis() - p(context).getLong("at", 0L) < SETTLE_MS) return
        val mode = state.listeningMode ?: return
        if (mode != ListeningMode.OFF) clear(context)
    }

    /** Toggle used by the widget and the tile: sends the change through the command queue. */
    fun toggle(context: Context) {
        val t = if (on(context)) stop(context) else start(context)
        RefreshCoordinator.enqueueSetListeningMode(context.applicationContext, t.mode, t.level, t.focus, System.currentTimeMillis())
    }

    private fun refreshSurfaces(context: Context) {
        WindTileService.requestUpdate(context)
    }
}

/** Quick Settings tile: one tap for Windy, another to go back. */
class WindTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        render()
    }

    override fun onClick() {
        super.onClick()
        if (android.os.Build.VERSION.SDK_INT >= 31 &&
            checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return
        WindMode.toggle(applicationContext)
        render()
    }

    private fun render() {
        val tile = qsTile ?: return
        val on = WindMode.on(this)
        tile.label = "Windy"
        tile.subtitle = if (on) "On · tap to go back" else "Stops wind roar"
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.icon = Icon.createWithResource(this, R.drawable.ic_wind)
        tile.updateTile()
    }

    companion object {
        fun requestUpdate(context: Context) {
            runCatching {
                requestListeningState(context, android.content.ComponentName(context, WindTileService::class.java))
            }
        }
    }
}
