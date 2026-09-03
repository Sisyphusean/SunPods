package com.peyi.sonypodsbattery

import android.app.Activity
import android.content.Intent
import android.os.Bundle

class LaunchSonyAppActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sonyIntent = listOf(
            "com.sony.songpal.mdr",
            "com.sony.songpal.mdrapp"
        ).asSequence()
            .mapNotNull(packageManager::getLaunchIntentForPackage)
            .firstOrNull()

        startActivity(sonyIntent ?: Intent(this, MainActivity::class.java))
        finish()
    }
}
