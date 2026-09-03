package com.peyi.sonypodsbattery

import android.bluetooth.BluetoothDevice
import android.content.Context

object RefreshCoordinator {
    fun refresh(context: Context, device: BluetoothDevice? = null, startPolling: Boolean = true): BatteryState {
        val app = context.applicationContext
        val client = SonyBatteryClient(app)
        DiagnosticsStore.add(app, "Refresh started")
        val target = device ?: client.findEarbuds()
        val state = if (target == null) {
            BatteryStore.load(app).copy(status = "Pair your WF-1000XM5 in Android settings first")
        } else try {
            val result = client.read(target)
            DiagnosticsStore.add(app, "Refresh succeeded; case reported=${result.case != null}")
            BatteryState(
                result.left, result.right, result.case,
                result.leftCharging, result.rightCharging, result.caseCharging,
                BatteryStore.load(app).listeningMode,
                BatteryStore.load(app).ambientLevel,
                BatteryStore.load(app).focusOnVoice,
                System.currentTimeMillis(),
                if (result.case == null) "Updated. Open the case to refresh its battery." else "Updated"
            )
        } catch (error: SecurityException) {
            DiagnosticsStore.add(app, "Refresh blocked: ${error.message ?: error.javaClass.simpleName}")
            BatteryStore.load(app).copy(status = "Android blocked a Bluetooth action. Share diagnostics.")
        } catch (error: Exception) {
            DiagnosticsStore.add(app, "Refresh failed: ${error.javaClass.simpleName}: ${error.message}")
            BatteryStore.load(app).copy(status = "Could not connect. Close Sony Sound Connect and try again.")
        }
        BatteryStore.save(app, state)
        SonyPodsBatteryWidgetUpdater.updateAll(app)
        BatteryNotifier.show(app, state)
        if (startPolling && state.status.startsWith("Updated")) BatteryPollingService.start(app)
        return state
    }

    fun setListeningMode(context: Context, mode: ListeningMode, ambientLevel: Int? = null, focusOnVoice: Boolean? = null): BatteryState {
        val app = context.applicationContext
        val client = SonyBatteryClient(app)
        DiagnosticsStore.add(app, "Listening mode change requested: ${mode.name}")
        val target = client.findEarbuds()
        val state = if (target == null) {
            BatteryStore.load(app).copy(status = "Pair your WF-1000XM5 in Android settings first")
        } else try {
            val previous = BatteryStore.load(app)
            val level = (ambientLevel ?: previous.ambientLevel).coerceIn(1, 20)
            val focus = focusOnVoice ?: previous.focusOnVoice
            client.setListeningMode(target, mode, level, focus)
            DiagnosticsStore.add(app, "Listening mode changed: ${mode.name}")
            previous.copy(listeningMode = mode, ambientLevel = level, focusOnVoice = focus, status = "${mode.label} enabled", updatedAt = System.currentTimeMillis())
        } catch (error: Exception) {
            DiagnosticsStore.add(app, "Listening mode failed: ${error.javaClass.simpleName}: ${error.message}")
            BatteryStore.load(app).copy(status = "Could not change mode. Close Sony Sound Connect and try again.")
        }
        BatteryStore.save(app, state)
        SonyPodsBatteryWidgetUpdater.updateAll(app)
        BatteryNotifier.show(app, state)
        return state
    }
}
