package com.peyi.sonypodsbattery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Developer hook for protocol work over adb: sends raw Sony command payloads on the app's own link
 * and logs every frame that comes back (tag SunPods). Only callers holding android.permission.DUMP
 * can reach it, which means adb shell, never another app.
 *
 *   adb shell am broadcast -n com.peyi.sonypodsbattery/.DebugCommandReceiver --es hex "460c" [--el wait 1500]
 *
 * Several payloads can be sent in one go, separated by spaces.
 */
class DebugCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // --es run lepair: pairs the LE side over Classic without the case button (LePairing).
        // --es run unpairle: removes the LE-only pairings (random addresses), to test lepair.
        if (intent.getStringExtra("run") == "lepair" || intent.getStringExtra("run") == "unpairle") {
            val app = context.applicationContext
            val pending = goAsync()
            val which = intent.getStringExtra("run")
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    if (which == "lepair") Log.i("SunPods", "Debug lepair: ${LePairing.pairOverClassic(app) { Log.i("SunPods", it) }}")
                    else Log.i("SunPods", "Debug unpairle: ${ShizukuBluetooth.use(app) { sh ->
                        EarbudAddresses.le(app).filter { EarbudAddresses.isRandom(it) }.map { "$it ${sh.removeBond(it)}" }
                    }}")
                } finally { pending.finish() }
            }
            return
        }
        // --es run repair: runs the Classic repair as if a switch to Classic had just been confirmed.
        if (intent.getStringExtra("run") == "repair") {
            val app = context.applicationContext
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try { Log.i("SunPods", "Debug repair: ${ClassicRepair.finish(app, justSwitched = true)}") } finally { pending.finish() }
            }
            return
        }
        val payloads = intent.getStringExtra("hex").orEmpty().trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (payloads.isEmpty()) return
        val waitMs = intent.getLongExtra("wait", 1_500L)
        // --ei type 14 sends on table 2 (devices, voice guidance); default is table 1.
        val type = intent.getIntExtra("type", SonyProtocol.DATA_MDR)
        val other = intent.getBooleanExtra("other", false)
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                for (hex in payloads) {
                    val bytes = runCatching { hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray() }.getOrNull()
                    if (bytes == null) { Log.i("SunPods", "Debug: bad hex $hex"); continue }
                    val r = RefreshCoordinator.awaitSession(app, "Debug") { s ->
                        if (other) {
                            // --ez other true: send to the other LE earbud instead of the session's one.
                            SonyControlSessionManager.onOtherEarbud(app) { o -> o.send(bytes, type); o.waitFor(waitMs) { false }; true }
                                ?: Log.i("SunPods", "Debug: no other earbud")
                        } else {
                            s.send(bytes, type)
                            s.waitFor(waitMs) { false }
                        }
                    }
                    Log.i("SunPods", "Debug sent $hex: ${if (r.isSuccess) "ok" else r.exceptionOrNull()?.message}")
                }
            } finally {
                pending.finish()
            }
        }
    }
}
