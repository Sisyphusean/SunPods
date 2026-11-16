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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    private val vm: SunpodsViewModel by viewModels()
    private var hasPermission by mutableStateOf(false)
    private var dynamicColor by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasPermission = hasBluetoothPermission()
        if (hasPermission) vm.refreshAll()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        hasPermission = hasBluetoothPermission()
        dynamicColor = getSharedPreferences("ui", MODE_PRIVATE).getBoolean("dynamic_color", false)
        setContent {
            SunpodsTheme(dynamicColor = dynamicColor) {
                SunpodsApp(
                    vm = vm,
                    hasPermission = hasPermission,
                    onRequestPermission = ::askForPermissions,
                    onShareDiagnostics = ::shareDiagnostics,
                    onOpenBluetoothSettings = { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
                    dynamicColor = dynamicColor,
                    onDynamicColorChange = { on ->
                        dynamicColor = on
                        getSharedPreferences("ui", MODE_PRIVATE).edit().putBoolean("dynamic_color", on).apply()
                    }
                )
            }
        }
        if (hasPermission) vm.refreshAll() else askForPermissions()
    }

    override fun onResume() {
        super.onResume()
        hasPermission = hasBluetoothPermission()
        vm.reloadCached()
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
            putExtra(Intent.EXTRA_SUBJECT, "SunPods diagnostics")
            putExtra(Intent.EXTRA_TEXT, DiagnosticsStore.text(this@MainActivity))
        }, "Share diagnostics"))
    }
}
