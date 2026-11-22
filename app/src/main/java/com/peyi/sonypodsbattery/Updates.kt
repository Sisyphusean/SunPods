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
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * New versions on GitHub. Once a day, when there's a network, SunPods asks GitHub for the latest
 * release of this app and shows a notification when it's newer than the installed one.
 *
 * This is SunPods' only use of the internet. The request carries nothing about the user or their
 * earbuds, and it can be turned off in Settings.
 */
internal object Updates {
    const val REPO = "Sisyphusean/Sunpods"
    const val RELEASES_PAGE = "https://github.com/$REPO/releases"
    private const val PREFS = "updates"
    private const val WORK = "update_check"
    private const val CHANNEL = "app_updates"
    private const val NOTE_ID = 4801

    data class Release(val version: String, val url: String)

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun enabled(context: Context) = prefs(context).getBoolean("on", true)

    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("on", on).apply()
        schedule(context)
    }

    /** Keeps the daily check scheduled (or cancelled). Safe to call on every app start. */
    fun schedule(context: Context) {
        val work = WorkManager.getInstance(context)
        if (!enabled(context)) {
            work.cancelUniqueWork(WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<UpdateWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        work.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** The newest published release, or null if GitHub can't be reached. */
    suspend fun latest(): Release? = withContext(Dispatchers.IO) {
        runCatching {
            val c = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
            c.connectTimeout = 10_000
            c.readTimeout = 10_000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            c.setRequestProperty("User-Agent", "SunPods-Android")
            try {
                if (c.responseCode != 200) null
                else {
                    val o = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                    if (o.optBoolean("draft") || o.optBoolean("prerelease")) null
                    else Release(o.getString("tag_name").trimStart('v', 'V'), o.optString("html_url", RELEASES_PAGE))
                }
            } finally {
                c.disconnect()
            }
        }.getOrNull()
    }

    /** True when [candidate] (like "2.28.1") is a later version than [current]. */
    fun isNewer(candidate: String, current: String): Boolean {
        fun parts(v: String) = v.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val a = parts(candidate)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /**
     * Asks GitHub. Returns the release when it's newer than this app, else null. With [notify], a
     * newer release is announced once per version.
     */
    suspend fun check(context: Context, notify: Boolean): Release? {
        val release = latest() ?: return null
        if (!isNewer(release.version, BuildConfig.VERSION_NAME)) return null
        if (notify && prefs(context).getString("notified", null) != release.version) {
            prefs(context).edit().putString("notified", release.version).apply()
            notifyAvailable(context, release)
        }
        return release
    }

    fun open(context: Context, url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun notifyAvailable(context: Context, release: Release) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "App Updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "When a new version of SunPods is out."
            }
        )
        val tap = PendingIntent.getActivity(
            context, 31, Intent(Intent.ACTION_VIEW, Uri.parse(release.url)),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        manager.notify(
            NOTE_ID,
            android.app.Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_sunpods_mark)
                .setContentTitle("SunPods ${release.version} is out")
                .setContentText("Tap to see what's new and download it.")
                .setContentIntent(tap)
                .setAutoCancel(true)
                .build()
        )
    }
}

class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (Updates.enabled(applicationContext)) Updates.check(applicationContext, notify = true)
        return Result.success()
    }
}
