package com.peyi.sonypodsbattery

/** Tools builds (debug, benchmark): timing marks along app start, tag SunPodsStart. Never in releases. */
internal object StartupLog {
    private fun since() = android.os.SystemClock.uptimeMillis() - android.os.Process.getStartUptimeMillis()

    fun mark(what: String) {
        if (!BuildConfig.TOOLS) return
        android.util.Log.d("SunPodsStart", "$what +${since()} ms on ${Thread.currentThread().name}")
    }

    /** Logs every main-thread task over 50 ms during the first 15 s, to find what holds the UI. */
    fun watchMainThread() {
        if (!BuildConfig.TOOLS) return
        var started = 0L
        var what = ""
        android.os.Looper.getMainLooper().setMessageLogging { line ->
            if (since() > 15_000) { android.os.Looper.getMainLooper().setMessageLogging(null); return@setMessageLogging }
            if (line.startsWith(">>>>>")) { started = android.os.SystemClock.uptimeMillis(); what = line }
            else if (line.startsWith("<<<<<")) {
                val took = android.os.SystemClock.uptimeMillis() - started
                if (took > 50) android.util.Log.d("SunPodsStart", "main busy ${took} ms at +${since()} ms: ${what.substringAfter("Dispatching to ").take(160)}")
            }
        }
    }
}
