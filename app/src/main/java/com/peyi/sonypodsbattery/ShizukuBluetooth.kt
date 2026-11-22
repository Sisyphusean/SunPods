package com.peyi.sonypodsbattery

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume

/**
 * Optional Shizuku support. When Shizuku is running and SunPods is allowed in it, the LE Audio
 * switch can remove the old pairing, pair the earbuds' LE side and connect them by itself. Without
 * it, the switch shows the same steps for the user to do in Bluetooth settings.
 */
internal object ShizukuBluetooth {
    private const val REQUEST_CODE = 7101
    private const val SERVICE_VERSION = 2

    enum class State { NOT_RUNNING, NEEDS_PERMISSION, READY }

    const val PACKAGE = "moe.shizuku.privileged.api"

    /** Running means Shizuku's service answers right now, not just that the app is installed. */
    fun state(): State = runCatching {
        when {
            !Shizuku.pingBinder() || Shizuku.isPreV11() || Shizuku.getVersion() < 11 -> State.NOT_RUNNING
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> State.READY
            else -> State.NEEDS_PERMISSION
        }
    }.getOrDefault(State.NOT_RUNNING)

    fun installed(context: Context): Boolean =
        runCatching { context.packageManager.getPackageInfo(PACKAGE, 0); true }.getOrDefault(false)

    /** Shizuku's page on Google Play (or the web page if the Play Store isn't there). */
    fun openStore(context: Context) {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PACKAGE")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(market) }.onFailure {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$PACKAGE")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    fun open(context: Context) {
        context.packageManager.getLaunchIntentForPackage(PACKAGE)?.let { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    }

    /** Shows Shizuku's own prompt asking to allow SunPods. */
    fun requestPermission() {
        runCatching { if (state() == State.NEEDS_PERMISSION) Shizuku.requestPermission(REQUEST_CODE) }
    }

    private var service: IShellBluetooth? = null
    private var connection: ServiceConnection? = null

    private fun args(context: Context) = Shizuku.UserServiceArgs(ComponentName(context.packageName, ShellBluetoothService::class.java.name))
        .daemon(false)
        .processNameSuffix("bluetooth")
        .debuggable(false)
        .version(SERVICE_VERSION)

    /** Runs [block] with the shell-side helper, starting it if needed. Null if Shizuku isn't ready. */
    suspend fun <T> use(context: Context, block: (IShellBluetooth) -> T): T? {
        if (state() != State.READY) return null
        val s = service ?: bind(context.applicationContext) ?: return null
        return withContext(Dispatchers.IO) {
            runCatching { block(s) }.getOrElse { service = null; null }
        }
    }

    private suspend fun bind(context: Context): IShellBluetooth? = withContext(Dispatchers.Main) {
        withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine { cont ->
                val conn = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                        val s = binder?.takeIf { it.pingBinder() }?.let { IShellBluetooth.Stub.asInterface(it) }
                        service = s
                        if (cont.isActive) cont.resume(s)
                    }
                    override fun onServiceDisconnected(name: ComponentName?) { service = null }
                }
                connection = conn
                runCatching { Shizuku.bindUserService(args(context), conn) }.onFailure { if (cont.isActive) cont.resume(null) }
            }
        }
    }

    /** Stops the helper once the switch is finished. */
    fun release(context: Context) {
        val conn = connection ?: return
        runCatching { Shizuku.unbindUserService(args(context.applicationContext), conn, true) }
        connection = null
        service = null
    }
}
