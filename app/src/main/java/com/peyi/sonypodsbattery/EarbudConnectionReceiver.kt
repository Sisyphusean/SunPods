package com.peyi.sonypodsbattery

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class EarbudConnectionReceiver : BroadcastReceiver() {
    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BluetoothDevice.ACTION_ACL_CONNECTED) return
        val device = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        else @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        val name = try { device?.name.orEmpty() } catch (_: SecurityException) { "" }
        if (!Regex("WF[- ]?1000XM5", RegexOption.IGNORE_CASE).containsMatchIn(name)) return
        val pending = goAsync()
        Thread {
            try { RefreshCoordinator.refresh(context.applicationContext, device) }
            finally { pending.finish() }
        }.start()
    }
}
