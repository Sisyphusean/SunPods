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
                if (failures >= MAX_FAILURES) {
                    stopSelf()
                } else {
                    handler.postDelayed(poll, INTERVAL_MS)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        pollingActive = true
        startAsForeground()
        handler.removeCallbacks(poll)
        handler.postDelayed(poll, INTERVAL_MS)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        pollingActive = false
        handler.removeCallbacks(poll)
        pollingScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAsForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Background battery refresh", NotificationManager.IMPORTANCE_LOW))
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_battery)
            .setContentTitle("Sony Pods Battery")
            .setContentText("Refreshing WF-1000XM5 battery every 5 minutes while connected")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        else startForeground(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val CHANNEL = "background_refresh"
        private const val NOTIFICATION_ID = 2002
        private const val INTERVAL_MS = 5 * 60 * 1000L
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
