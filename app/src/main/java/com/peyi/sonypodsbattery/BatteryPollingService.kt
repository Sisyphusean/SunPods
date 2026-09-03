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
import kotlinx.coroutines.runBlocking

class BatteryPollingService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var failures = 0
    private val poll = object : Runnable {
        override fun run() {
            Thread {
                val state = RefreshCoordinator.refresh(applicationContext, startPolling = false)
                runBlocking {
                    SonyPodsBatteryWidgetUpdater.syncReportedMode(
                        applicationContext,
                        AudioMode.fromListeningMode(state.listeningMode)
                    )
                }
                failures = if (state.status.startsWith("Updated")) 0 else failures + 1
                if (failures >= MAX_FAILURES) stopSelf() else handler.postDelayed(this, INTERVAL_MS)
            }.start()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        handler.removeCallbacks(poll)
        handler.postDelayed(poll, INTERVAL_MS)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(poll)
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
    }
}
