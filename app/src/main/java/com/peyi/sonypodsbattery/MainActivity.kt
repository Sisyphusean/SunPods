package com.peyi.sonypodsbattery

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: SunpodsViewModel by viewModels()
    private var hasPermission by mutableStateOf(false)
    private var dynamicColor by mutableStateOf(false)
    private var onboarding by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasPermission = hasBluetoothPermission()
        if (hasPermission) vm.refreshAll()
        BackgroundAccess.askOnce(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        ShizukuWatch.onNotificationTap(this, intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        StartupLog.mark("activity onCreate")
        StartupLog.watchMainThread()
        if (ShizukuWatch.onNotificationTap(this, intent)) { super.onCreate(savedInstanceState); return }
        // Splash exit: the mark grows and fades as the page fades through to SunPods.
        // Fast phones are ready before the logo finishes assembling, so wait out the rest of
        // the icon animation before the exit plays.
        installSplashScreen().setOnExitAnimationListener { splash ->
            val left = runCatching {
                splash.iconAnimationStartMillis + splash.iconAnimationDurationMillis - android.os.SystemClock.uptimeMillis()
            }.getOrDefault(0L).coerceIn(0L, 1_000L)
            runCatching {
                splash.iconView.animate().scaleX(1.5f).scaleY(1.5f).alpha(0f).setStartDelay(left + 120)
                    .setDuration(300).setInterpolator(android.view.animation.AccelerateInterpolator()).start()
            }
            splash.view.animate().alpha(0f).setStartDelay(left + 180).setDuration(320)
                .withEndAction { splash.remove() }.start()
        }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        hasPermission = hasBluetoothPermission()
        dynamicColor = getSharedPreferences("ui", MODE_PRIVATE).getBoolean("dynamic_color", false)
        onboarding = !OnboardingStore.done(this)
        setContent {
            SunpodsTheme(dynamicColor = dynamicColor) {
                if (onboarding) {
                    Onboarding(vm) {
                        onboarding = false
                        hasPermission = hasBluetoothPermission()
                        if (hasPermission) vm.refreshAll()
                    }
                    return@SunpodsTheme
                }
                SunpodsApp(
                    vm = vm,
                    hasPermission = hasPermission,
                    onRequestPermission = ::askForPermissions,
                    onShareDiagnostics = ::shareDiagnostics,
                    onOpenBluetoothSettings = { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
                    dynamicColor = dynamicColor,
                    onDynamicColorChange = { on ->
                        dynamicColor = on
                        getSharedPreferences("ui", MODE_PRIVATE).edit().putBoolean("dynamic_color", on).commit()
                        // Widgets follow the same colour choice as the app.
                        lifecycleScope.launch { SonyPodsBatteryWidgetUpdater.updateAll(applicationContext) }
                    }
                )
            }
        }
        StartupLog.mark("setContent done")
        if (BuildConfig.TOOLS) androidx.core.view.OneShotPreDrawListener.add(window.decorView) { StartupLog.mark("first draw") }
        // First run: setup asks for everything with explanations, so don't fire bare prompts.
        if (!onboarding) { if (!hasPermission) askForPermissions() else BackgroundAccess.askOnce(this) }
    }

    override fun onStart() {
        super.onStart()
        vm.setLive(true)
    }

    override fun onStop() {
        vm.setLive(false)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        StartupLog.mark("resume")
        hasPermission = hasBluetoothPermission()
        vm.reloadCached()
        if (hasPermission) vm.refreshIfStale()
        StartupLog.mark("resume done")
    }

    private fun hasBluetoothPermission() = Build.VERSION.SDK_INT < 31 ||
        checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun askForPermissions() {
        val wanted = buildList {
            if (Build.VERSION.SDK_INT >= 31) add(Manifest.permission.BLUETOOTH_CONNECT)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (wanted.isEmpty()) {
            hasPermission = true
            vm.refreshAll()
        } else {
            permissionLauncher.launch(wanted.toTypedArray())
        }
    }

    private fun shareDiagnostics() {
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "SunPods Diagnostics")
            putExtra(Intent.EXTRA_TEXT, DiagnosticsStore.text(this@MainActivity))
        }, "Share Diagnostics"))
    }
}
