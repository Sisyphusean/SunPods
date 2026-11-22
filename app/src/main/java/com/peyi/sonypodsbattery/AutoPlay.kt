package com.peyi.sonypodsbattery

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent

/**
 * Auto Play, like Sound Connect's: when you take your earbuds out and they connect, your music
 * starts again. It presses Play for whatever you last listened to, and stays quiet if something is
 * already playing, during a call, or while SunPods is switching between Classic and LE Audio.
 */
internal object AutoPlay {
    private const val PREFS = "auto_play"
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var lastAt = 0L

    fun enabled(context: Context) = context.getSharedPreferences(PREFS, 0).getBoolean("on", false)
    fun setEnabled(context: Context, on: Boolean) = context.getSharedPreferences(PREFS, 0).edit().putBoolean("on", on).apply()

    fun onConnected(context: Context) {
        val app = context.applicationContext
        if (!enabled(app)) return
        if (SonyControlSessionManager.switchedRecently(app, 120_000)) return
        // Classic and LE Audio can both report a connection for the same arrival.
        if (SystemClock.elapsedRealtime() - lastAt < 30_000) return
        lastAt = SystemClock.elapsedRealtime()
        main.postDelayed({ play(app) }, 2_500)
    }

    private fun play(app: Context) {
        val audio = app.getSystemService(AudioManager::class.java) ?: return
        if (audio.isMusicActive) return
        if (audio.mode == AudioManager.MODE_IN_CALL || audio.mode == AudioManager.MODE_IN_COMMUNICATION) return
        val t = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY, 0))
        DiagnosticsStore.add(app, "Auto Play: pressed Play")
    }
}
