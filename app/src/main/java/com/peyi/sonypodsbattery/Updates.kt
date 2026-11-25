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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * New versions on GitHub. Every 4 hours in the background and on foreground opens (throttled to
 * 10 minutes with ETag validation), SunPods checks GitHub for the latest release and notifies once
 * per newer version.
 *
 * This is SunPods' only use of the internet. The request carries nothing about the user or their
 * device, and it can be turned off in Settings.
 */
internal object Updates {
    const val REPO = "Sisyphusean/SunPods"
    const val RELEASES_PAGE = "https://github.com/$REPO/releases"
    private const val PREFS = "updates"
    private const val WORK = "update_check"
    private const val CHANNEL = "app_updates"
    private const val NOTE_ID = 4801
    private const val SCHEDULE_HOURS = 4
    private val checkMutex = Mutex()

    /** A published release: its version, its page, and the APK to install, if it has one. */
    data class Release(val version: String, val url: String, val apk: String? = null, val notes: String? = null, val size: Long? = null)

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun enabled(context: Context) = prefs(context).getBoolean("on", true)

    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("on", on).apply()
        schedule(context)
    }

    /** Keeps the periodic check scheduled (or cancelled). Safe to call on every app start. */
    fun schedule(context: Context) {
        val work = WorkManager.getInstance(context)
        val p = prefs(context)
        if (!enabled(context)) {
            work.cancelUniqueWork(WORK)
            return
        }
        val policy = if (p.getInt("schedule_hours", 0) != SCHEDULE_HOURS) {
            p.edit().putInt("schedule_hours", SCHEDULE_HOURS).apply()
            ExistingPeriodicWorkPolicy.UPDATE
        } else {
            ExistingPeriodicWorkPolicy.KEEP
        }
        val request = PeriodicWorkRequestBuilder<UpdateWorker>(SCHEDULE_HOURS.toLong(), TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        work.enqueueUniquePeriodicWork(WORK, policy, request)
    }

    /** The newest published release, or null if GitHub can't be reached. */
    suspend fun latest(): Release? = fetchLatest(etag = null).first

    private suspend fun fetchLatest(etag: String?): Pair<Release?, String?> = withContext(Dispatchers.IO) {
        runCatching {
            val c = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
            c.connectTimeout = 10_000
            c.readTimeout = 10_000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            c.setRequestProperty("User-Agent", "SunPods-Android")
            if (!etag.isNullOrBlank()) c.setRequestProperty("If-None-Match", etag)
            try {
                when (c.responseCode) {
                    HttpURLConnection.HTTP_NOT_MODIFIED -> null to etag
                    200 -> {
                        val o = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                        val nextEtag = c.getHeaderField("ETag")
                        if (o.optBoolean("draft") || o.optBoolean("prerelease")) null to nextEtag
                        else {
                            val assets = o.optJSONArray("assets")
                            val asset = (0 until (assets?.length() ?: 0)).map { assets!!.getJSONObject(it) }
                                .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
                            val apk = asset?.optString("browser_download_url")?.takeIf { it.startsWith("https://github.com/$REPO/releases/download/") }
                            Release(
                                o.getString("tag_name").trimStart('v', 'V'), o.optString("html_url", RELEASES_PAGE), apk,
                                o.optString("body").takeIf { it.isNotBlank() }, asset?.optLong("size")?.takeIf { it > 0 }
                            ) to nextEtag
                        }
                    }
                    else -> null to null
                }
            } finally {
                c.disconnect()
            }
        }.getOrElse { null to null }
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

    /** Asks GitHub using ETag validation and remembers the answer; returns the newest release, newer or not. */
    suspend fun refresh(context: Context): Release? {
        val p = prefs(context)
        val cached = cachedRelease(p)
        val sentEtag = if (cached != null) p.getString("etag", null) else null
        val (release, etag) = fetchLatest(sentEtag)
        val now = System.currentTimeMillis()
        if (release == null) {
            p.edit().putLong("checked_at", now).apply()
            return if (sentEtag != null && etag == sentEtag) cached else null
        }
        p.edit()
            .putLong("checked_at", now)
            .putString("latest_version", release.version)
            .putString("latest_url", release.url)
            .putString("latest_apk", release.apk)
            .putString("latest_notes", release.notes)
            .putLong("latest_size", release.size ?: 0L)
            .putString("etag", etag)
            .apply()
        return release
    }

    private fun cachedRelease(p: android.content.SharedPreferences): Release? {
        val v = p.getString("latest_version", null) ?: return null
        return Release(
            v, p.getString("latest_url", RELEASES_PAGE) ?: RELEASES_PAGE, p.getString("latest_apk", null),
            p.getString("latest_notes", null), p.getLong("latest_size", 0L).takeIf { it > 0 }
        )
    }

    suspend fun check(context: Context, notify: Boolean): Release? {
        val release = refresh(context) ?: return null
        if (!isNewer(release.version, BuildConfig.VERSION_NAME)) return null
        if (notify && prefs(context).getString("notified", null) != release.version) {
            prefs(context).edit().putString("notified", release.version).apply()
            notifyAvailable(context, release)
        }
        return release
    }

    /** The newest release seen by any check, if it's newer than this app. No network. */
    fun available(context: Context): Release? {
        val r = cachedRelease(prefs(context)) ?: return null
        return r.takeIf { isNewer(it.version, BuildConfig.VERSION_NAME) }
    }

    /** "Later" on the Home card: hide it until a newer version than this one comes out. */
    fun dismiss(context: Context, version: String) = prefs(context).edit().putString("dismissed", version).apply()

    fun dismissed(context: Context, version: String) = prefs(context).getString("dismissed", null) == version

    /** The first plain sentence of the release notes, for a one-line summary. */
    fun headline(notes: String?): String? {
        val line = notes?.lines()?.map { it.trim() }?.firstOrNull {
            it.isNotEmpty() && !it.startsWith("#") && !(it.startsWith("**") && it.endsWith("**")) && !it.startsWith("- ") && !it.startsWith("* ")
        } ?: notes?.lines()?.map { it.trim() }?.firstOrNull { it.startsWith("- ") || it.startsWith("* ") }?.drop(2)
        val clean = line?.replace("**", "")?.replace("`", "")?.substringBefore(" (")?.trim() ?: return null
        return clean.substringBefore(". ").trimEnd('.') .takeIf { it.isNotBlank() }?.let { "$it." }
    }

    /** Checks again if the last check was over 10 minutes ago (lightweight with ETag validation, serialized). */
    suspend fun checkIfDue(context: Context): Release? {
        if (!enabled(context)) return available(context)
        return checkMutex.withLock {
            val last = prefs(context).getLong("checked_at", 0L)
            if (System.currentTimeMillis() - last < 10 * 60_000L) return@withLock available(context)
            check(context, notify = false) ?: available(context)
        }
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
        // Opens SunPods, where the update card does the rest.
        val tap = PendingIntent.getActivity(
            context, 31, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        manager.notify(
            NOTE_ID,
            android.app.Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_sunpods_mark)
                .setContentTitle("SunPods ${release.version} is out")
                .setContentText("Tap to update.")
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

/**
 * SunPods updating itself: download the release APK, make sure it's really SunPods (same package,
 * same signing key, higher version), then hand it to Android's installer. Android asks once for
 * permission to install apps, then confirms each update; on Android 12 and later, once SunPods
 * installed itself, updates can go through without that confirmation.
 */
internal object SelfUpdate {
    sealed interface State {
        data object Idle : State
        data class Downloading(val fraction: Float?) : State
        data object Installing : State
        data object NeedsPermission : State
        data class Failed(val message: String) : State
    }

    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(context: Context) {
        runCatching {
            context.startActivity(
                Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    suspend fun download(context: Context, url: String, progress: (Float?) -> Unit): java.io.File = withContext(Dispatchers.IO) {
        val dir = java.io.File(context.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val out = java.io.File(dir, "SunPods-update.apk")
        var c = URL(url).openConnection() as HttpURLConnection
        c.instanceFollowRedirects = true
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.setRequestProperty("User-Agent", "SunPods-Android")
        try {
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
            val total = c.contentLengthLong.takeIf { it > 0 }
            c.inputStream.use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        progress(total?.let { done.toFloat() / it })
                    }
                }
            }
        } finally {
            c.disconnect()
        }
        out
    }

    /** True when [apk] is SunPods, signed with the same key as this install, and newer. */
    @Suppress("DEPRECATION")
    fun isGenuine(context: Context, apk: java.io.File): Boolean {
        val pm = context.packageManager
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val incoming = pm.getPackageArchiveInfo(apk.path, flags) ?: return false
        if (incoming.packageName != context.packageName) return false
        val current = pm.getPackageInfo(context.packageName, flags)
        if (incoming.longVersionCode <= current.longVersionCode) return false
        fun keys(info: android.content.pm.PackageInfo?): Set<String> =
            info?.signingInfo?.apkContentsSigners.orEmpty().map {
                java.security.MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { b -> "%02x".format(b) }
            }.toSet()
        val mine = keys(current)
        return mine.isNotEmpty() && keys(incoming) == mine
    }

    fun install(context: Context, apk: java.io.File) {
        val installer = context.packageManager.packageInstaller
        val params = android.content.pm.PackageInstaller.SessionParams(android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(android.content.pm.PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("SunPods.apk", 0, apk.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val done = PendingIntent.getBroadcast(
                context, id, Intent(context, InstallResultReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            session.commit(done.intentSender)
        }
    }
}

/** Android's answer to an install: show its confirmation when it needs one, note failures. */
class InstallResultReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(android.content.pm.PackageInstaller.EXTRA_STATUS, -999)
        when (status) {
            android.content.pm.PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm: Intent? = if (Build.VERSION.SDK_INT >= 33)
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                else intent.getParcelableExtra(Intent.EXTRA_INTENT)
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { runCatching { context.startActivity(it) } }
            }
            android.content.pm.PackageInstaller.STATUS_SUCCESS -> Unit
            else -> {
                val msg = intent.getStringExtra(android.content.pm.PackageInstaller.EXTRA_STATUS_MESSAGE)
                DiagnosticsStore.add(context, "Update install failed: $status $msg")
                android.widget.Toast.makeText(context, "Couldn't install the update. Try again from Home.", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
}
