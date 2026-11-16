package com.peyi.sonypodsbattery

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/** Widget header target. Kept for existing widget instances; opens Sunpods. */
class LaunchSonyAppActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }
}
