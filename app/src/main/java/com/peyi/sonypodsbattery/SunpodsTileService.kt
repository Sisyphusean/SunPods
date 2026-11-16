package com.peyi.sonypodsbattery

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Quick Settings tile: tap to cycle Noise cancelling → Ambient → Off. */
class SunpodsTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        render(BatteryStore.load(this))
    }

    override fun onClick() {
        super.onClick()
        if (Build.VERSION.SDK_INT >= 31 &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) return
        val state = BatteryStore.load(this)
        val next = when (state.listeningMode) {
            ListeningMode.NOISE_CANCELLING -> ListeningMode.AMBIENT
            ListeningMode.AMBIENT -> ListeningMode.OFF
            ListeningMode.OFF, null -> ListeningMode.NOISE_CANCELLING
        }
        render(state.copy(listeningMode = next))
        RefreshCoordinator.enqueueSetListeningMode(
            applicationContext,
            next,
            state.ambientLevel,
            state.focusOnVoice,
            System.currentTimeMillis()
        )
    }

    private fun render(state: BatteryState) {
        val tile = qsTile ?: return
        val mode = state.listeningMode
        tile.label = when (mode) {
            ListeningMode.NOISE_CANCELLING -> "Noise cancelling"
            ListeningMode.AMBIENT -> "Ambient sound"
            ListeningMode.OFF -> "Noise control off"
            null -> "Sunpods"
        }
        val levels = listOfNotNull(
            state.left?.let { "L $it%" },
            state.right?.let { "R $it%" }
        ).joinToString("  ")
        tile.subtitle = levels.ifEmpty { "Tap to switch" }
        tile.state = if (mode == ListeningMode.OFF || mode == null) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
        tile.icon = Icon.createWithResource(
            this,
            if (mode == ListeningMode.AMBIENT) R.drawable.ic_waveform else R.drawable.ic_noise_cancelling
        )
        tile.updateTile()
    }

    companion object {
        fun requestUpdate(context: android.content.Context) {
            try {
                requestListeningState(
                    context,
                    android.content.ComponentName(context, SunpodsTileService::class.java)
                )
            } catch (_: Exception) {
            }
        }
    }
}
