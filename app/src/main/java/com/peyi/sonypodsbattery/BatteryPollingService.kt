package com.peyi.sonypodsbattery

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.content.pm.ServiceInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class BatteryPollingService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val pollingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile
    private var pollingActive = false
    private var failures = 0
    private var lastLevels: Triple<Int?, Int?, Int?>? = null
    private var lastChangeAt = System.currentTimeMillis()

    /** Steady levels (buds idle in the case, or a long listen) don't need checking as often. */
    private fun nextInterval() =
        if (System.currentTimeMillis() - lastChangeAt > STEADY_AFTER_MS) STEADY_INTERVAL_MS else INTERVAL_MS
    private val poll: Runnable = object : Runnable {
        override fun run() {
            if (!pollingActive || EarbudConnectionStateStore.current(applicationContext) == EarbudConnectionState.DISCONNECTED) return
            pollingScope.launch {
                val state = RefreshCoordinator.awaitRefresh(
                    applicationContext,
                    startPolling = false,
                    automatic = true
                )
                if (!pollingActive || EarbudConnectionStateStore.current(applicationContext) == EarbudConnectionState.DISCONNECTED) return@launch
                failures = if (state.status.startsWith("Updated")) 0 else failures + 1
                val levels = Triple(state.left, state.right, state.case)
                if (levels != lastLevels || state.leftCharging || state.rightCharging || state.caseCharging) {
                    lastLevels = levels
                    lastChangeAt = System.currentTimeMillis()
                }
                if (failures == 0) LastSeenStore.captureWhileConnected(applicationContext)
                if (failures >= MAX_FAILURES) {
                    stopSelf()
                } else {
                    handler.postDelayed(poll, nextInterval())
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        pollingActive = true
        startAsForeground()
        AutomationEngine.start(applicationContext)
        handler.removeCallbacks(poll)
        handler.postDelayed(poll, INTERVAL_MS)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        pollingActive = false
        AutomationEngine.stop()
        handler.removeCallbacks(poll)
        pollingScope.cancel()
        RefreshCoordinator.closeSessionForServiceStop(applicationContext)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAsForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Battery Checks", NotificationManager.IMPORTANCE_LOW))
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_sunpods_mark)
            .setContentTitle("SunPods")
            .setContentText("Checking battery levels while your earbuds are connected.")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        else startForeground(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val CHANNEL = "background_refresh"
        private const val NOTIFICATION_ID = 2002
        private const val INTERVAL_MS = 5 * 60 * 1000L
        private const val STEADY_INTERVAL_MS = 15 * 60 * 1000L
        private const val STEADY_AFTER_MS = 20 * 60 * 1000L
        private const val MAX_FAILURES = 3

        fun start(context: Context) {
            try {
                val intent = Intent(context, BatteryPollingService::class.java)
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
            } catch (error: Exception) {
                DiagnosticsStore.add(context, "Background refresh could not start: ${error.javaClass.simpleName}: ${error.message}")
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BatteryPollingService::class.java))
        }
    }
}
