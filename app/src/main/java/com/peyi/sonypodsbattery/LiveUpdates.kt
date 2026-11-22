package com.peyi.sonypodsbattery

import android.content.Context

/**
 * Turns a reading into the state to show: charging inferred from levels (the XM5's own charging
 * byte can't be trusted), and the listening mode as the earbuds report it.
 */
internal fun stateFrom(app: Context, previous: BatteryState, result: SonyBatteryClient.Result, checkedAt: Long, live: Boolean = false): BatteryState {
    fun inferred(part: String, before: Int?, now: Int?, reported: Boolean, outOfCase: Boolean = false) =
        ChargeInference.charging(app, part, before, previous.updatedAt, now, reported, outOfCase)
    // A bud in the case drops off the link, so a bud reported while the other is missing is in an ear.
    val leftOn = (result.left ?: 0) > 0
    val rightOn = (result.right ?: 0) > 0
    val mode = result.mode
    if (mode == null) { if (!live) DiagnosticsStore.add(app, "Earbuds didn't report their listening mode") }
    else if (mode.mode != previous.listeningMode || mode.ambientLevel != previous.ambientLevel || mode.focusOnVoice != previous.focusOnVoice)
        DiagnosticsStore.add(app, "Earbuds report ${mode.mode.name} (app showed ${previous.listeningMode?.name}); ${mode.raw}")
    return BatteryState(
        result.left,
        result.right,
        result.case,
        inferred("left", previous.left, result.left, result.leftCharging, leftOn && !rightOn),
        inferred("right", previous.right, result.right, result.rightCharging, rightOn && !leftOn),
        inferred("case", previous.case, result.case, result.caseCharging),
        mode?.mode ?: previous.listeningMode,
        mode?.ambientLevel ?: previous.ambientLevel,
        mode?.focusOnVoice ?: previous.focusOnVoice,
        System.currentTimeMillis(),
        checkedAt,
        if (result.case == null) "Updated. Open the case to refresh its battery." else "Updated"
    )
}

/**
 * One live check while the app is on screen: levels, case and mode only, on the kept link. Saves
 * (and so updates the screen and widgets) only when something changed, which keeps the battery
 * history and diagnostics quiet. Returns true when the earbuds answered.
 */
internal suspend fun liveTick(context: Context): Boolean {
    val app = context.applicationContext
    fun skip(why: String): Boolean { android.util.Log.d("SunPods", "Live check skipped: $why"); return false }
    // Before the user grants Nearby devices (a fresh install), Bluetooth calls throw.
    if (android.os.Build.VERSION.SDK_INT >= 31 &&
        app.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED
    ) return skip("no Bluetooth permission")
    if (!audioLinkUp(app)) return skip("no audio link")
    if (SonyControlSessionManager.currentState() == SonySessionState.RECONNECT_WAIT) return skip("reconnect pending")
    val client = SonyBatteryClient(app)
    val target = try { client.findEarbuds() } catch (e: SecurityException) { return skip("Bluetooth blocked") } ?: return skip("earbuds not paired")
    val result = try { client.readLive(target) } catch (e: Exception) { return skip("${e.javaClass.simpleName}: ${e.message}") }
        ?: return skip("link busy")
    val previous = BatteryStore.load(app)
    val next = stateFrom(app, previous, result, System.currentTimeMillis(), live = true)
    val changed = next.left != previous.left || next.right != previous.right || next.case != previous.case ||
        next.leftCharging != previous.leftCharging || next.rightCharging != previous.rightCharging ||
        next.caseCharging != previous.caseCharging || next.listeningMode != previous.listeningMode ||
        next.ambientLevel != previous.ambientLevel || next.focusOnVoice != previous.focusOnVoice
    if (changed) {
        DiagnosticsStore.add(app, "Live update: ${result.raw}")
        BatteryStore.save(app, next)
        SonyPodsBatteryWidgetUpdater.syncReportedMode(app, AudioMode.fromListeningMode(next.listeningMode))
        SonyPodsBatteryWidgetUpdater.updateAll(app)
    }
    return true
}
