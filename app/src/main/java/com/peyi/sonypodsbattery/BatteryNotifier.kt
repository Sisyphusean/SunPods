package com.peyi.sonypodsbattery

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

object BatteryNotifier {
    private const val CHANNEL = "battery_updates"

    fun show(context: Context, state: BatteryState) {
        if (state.left == null && state.right == null) return
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Battery updates", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Current WF-1000XM5 battery levels"
        })
        val content = listOfNotNull(
            state.left?.let { "L $it%" }, state.right?.let { "R $it%" }, state.case?.let { "Case $it%" }
        ).joinToString("   ")
        val open = PendingIntent.getActivity(context, 20, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = android.app.Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_sunpods_mark)
            .setContentTitle("WF-1000XM5")
            .setContentText(content)
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .build()
        manager.notify(2001, notification)
    }
}
