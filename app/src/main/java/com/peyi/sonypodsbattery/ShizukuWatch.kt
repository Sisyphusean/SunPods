package com.peyi.sonypodsbattery

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

/**
 * Covers Shizuku stopping (phone restart, or the phone's battery saver closing it). When a switch
 * or repair needs Shizuku and it's gone, the fix is remembered and a notification asks the user to
 * start Shizuku. As soon as Shizuku is back, SunPods finishes the fix by itself.
 */
internal object ShizukuWatch {
    private const val PREFS = "sunpods_connection"
    private const val KEY = "fix_pending"
    private const val CHANNEL = "connection_fixes"
    private const val NOTE_ID = 4711
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var started = false

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Safe to call often; listens for Shizuku coming back for as long as the app runs. */
    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        // Event driven only, no timers: Shizuku hands itself over when SunPods' process starts
        // (see SunpodsShizukuProvider and onNotificationTap), which fires this listener.
        runCatching { Shizuku.addBinderReceivedListenerSticky { onBack(app) } }
    }

    @Volatile private var finishing = false

    private const val EXTRA_AFTER_SHIZUKU = "sunpods_after_shizuku"

    /**
     * Shizuku hands itself to an app only when the app's process starts, so a SunPods that was
     * already running when Shizuku started again can't see it. Tapping the notification after
     * starting Shizuku restarts SunPods' process, which picks Shizuku up and finishes the fix.
     * True when the activity is being replaced and shouldn't set itself up.
     */
    fun onNotificationTap(activity: android.app.Activity, intent: Intent?): Boolean {
        if (intent?.getBooleanExtra(EXTRA_AFTER_SHIZUKU, false) != true) return false
        intent.removeExtra(EXTRA_AFTER_SHIZUKU)
        if (pending(activity) == null || ShizukuBluetooth.state() == ShizukuBluetooth.State.READY) return false
        DiagnosticsStore.add(activity, "Connection mode: restarting SunPods to pick up Shizuku")
        // Shizuku starts SunPods again by itself to hand over its service; the fix then runs.
        activity.finishAffinity()
        android.os.Process.killProcess(android.os.Process.myPid())
        return true
    }

    fun pending(context: Context): String? = prefs(context).getString(KEY, null)

    /** A fix that needs Shizuku couldn't run. [what] is shown in the notification. */
    fun markPending(context: Context, what: String) {
        val app = context.applicationContext
        if (pending(app) == null) DiagnosticsStore.add(app, "Connection mode: waiting for Shizuku to $what")
        prefs(app).edit().putString(KEY, what).apply()
        notify(app)
    }

    fun clear(context: Context) {
        val app = context.applicationContext
        if (pending(app) == null) return
        prefs(app).edit().remove(KEY).apply()
        runCatching { app.getSystemService(NotificationManager::class.java).cancel(NOTE_ID) }
    }

    private fun onBack(app: Context) {
        val what = pending(app) ?: return
        if (finishing) return
        finishing = true
        scope.launch {
          try {
            // The binder arrives a moment before Shizuku answers calls.
            delay(2_000)
            if (ShizukuBluetooth.state() != ShizukuBluetooth.State.READY) return@launch
            DiagnosticsStore.add(app, "Connection mode: Shizuku is back, finishing: $what")
            val done = if (SonyControlSessionManager.classicOnly(app)) {
                ClassicRepair.finish(app, justSwitched = false) && ClassicRepair.syncSetting(app)
            } else {
                ShizukuBluetooth.use(app) { sh -> EarbudAddresses.le(app).forEach { sh.connectLe(it) } }
                var w = 0
                while (w < 30_000 && !LeAudio.anyConnected(app)) { delay(1_000); w += 1_000 }
                LeAudio.anyConnected(app)
            }
            DiagnosticsStore.add(app, "Connection mode: ${if (done) "finished after Shizuku came back" else "still not finished after Shizuku came back"}")
            if (done) clear(app)
          } finally { finishing = false }
        }
    }

    private fun notify(app: Context) {
        if (Build.VERSION.SDK_INT >= 33 &&
            app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = app.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Connection Fixes", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "When your earbuds need Shizuku to finish reconnecting."
            }
        )
        val open = Intent(app, MainActivity::class.java).putExtra(EXTRA_AFTER_SHIZUKU, true)
        val tap = PendingIntent.getActivity(app, 22, open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val startShizuku = app.packageManager.getLaunchIntentForPackage(ShizukuBluetooth.PACKAGE)?.let {
            PendingIntent.getActivity(app, 24, it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        manager.notify(
            NOTE_ID,
            android.app.Notification.Builder(app, CHANNEL)
                .setSmallIcon(R.drawable.ic_sunpods_mark)
                .setContentTitle("Your earbuds need a quick fix")
                .setContentText("Start Shizuku, then tap here to finish.")
                .setContentIntent(tap)
                .apply { if (startShizuku != null) addAction(android.app.Notification.Action.Builder(null, "Open Shizuku", startShizuku).build()) }
                .setAutoCancel(true)
                .build()
        )
    }

    /** True when the phone won't close [packageName] in the background (Unrestricted battery). */
    fun runsInBackground(context: Context, packageName: String): Boolean = runCatching {
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
    }.getOrDefault(false)

    /** The app's own settings page, where Battery is set to Unrestricted. */
    fun openAppSettings(context: Context, packageName: String) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
