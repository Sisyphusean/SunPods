package com.peyi.sonypodsbattery

import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import java.util.Locale
import java.util.regex.Pattern

object EarbudCompanionManager {
    const val REQUEST_ASSOCIATION = 700

    private const val PREFS = "earbud_companion"
    private const val ADDRESS_KEY = "associated_address"
    private const val TARGET_NAME = "WF-1000XM5"

    fun isSupported(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP)

    fun isAssociated(context: Context): Boolean = associatedAddress(context) != null

    fun associatedAddress(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(ADDRESS_KEY, null)

    fun associate(
        activity: Activity,
        onStatus: (String) -> Unit,
        onFinished: (Boolean) -> Unit = {}
    ) {
        if (!isSupported(activity)) {
            onStatus("Companion device setup is not available on this phone")
            onFinished(false)
            return
        }

        val deviceManager = activity.getSystemService(CompanionDeviceManager::class.java)
        val filter = BluetoothDeviceFilter.Builder()
            .setNamePattern(Pattern.compile("$TARGET_NAME.*", Pattern.CASE_INSENSITIVE))
            .build()
        val request = AssociationRequest.Builder()
            .addDeviceFilter(filter)
            .setSingleDevice(true)
            .build()

        onStatus("Choose your WF-1000XM5 in the Android system dialog")
        deviceManager.associate(
            request,
            activity.mainExecutor,
            object : CompanionDeviceManager.Callback() {
                override fun onDeviceFound(chooserLauncher: IntentSender) {
                    try {
                        activity.startIntentSenderForResult(
                            chooserLauncher,
                            REQUEST_ASSOCIATION,
                            null,
                            0,
                            0,
                            0
                        )
                    } catch (error: IntentSender.SendIntentException) {
                        onStatus("Could not open the companion device picker")
                        onFinished(false)
                    }
                }

                override fun onFailure(error: CharSequence?) {
                    onStatus(error?.toString()?.ifBlank { "Companion association failed" }
                        ?: "Companion association failed")
                    onFinished(false)
                }
            }
        )
    }

    fun handleAssociationResult(
        context: Context,
        resultCode: Int,
        data: Intent?
    ): Boolean {
        if (resultCode != Activity.RESULT_OK) return false

        val device = if (Build.VERSION.SDK_INT >= 33) {
            data?.getParcelableExtra(
                CompanionDeviceManager.EXTRA_DEVICE,
                BluetoothDevice::class.java
            )
        } else {
            @Suppress("DEPRECATION")
            data?.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE)
        }
        val address = runCatching { device?.address }.getOrNull()?.uppercase(Locale.US)
            ?: return false

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(ADDRESS_KEY, address)
            .apply()
        startObservingPresence(context)
        return true
    }

    fun startObservingPresence(context: Context) {
        val address = associatedAddress(context) ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !isSupported(context)) return

        val deviceManager = context.getSystemService(CompanionDeviceManager::class.java)
        try {
            @Suppress("DEPRECATION")
            deviceManager.startObservingDevicePresence(address)
            DiagnosticsStore.add(context, "Companion presence observation enabled")
        } catch (error: Exception) {
            DiagnosticsStore.add(
                context,
                "Companion presence observation failed: ${error.javaClass.simpleName}: ${error.message}"
            )
        }
    }

    fun handleDeviceAppeared(context: Context, address: String) {
        val associated = associatedAddress(context) ?: return
        if (!associated.equals(address, ignoreCase = true)) return

        val device = runCatching {
            context.getSystemService(android.bluetooth.BluetoothManager::class.java)
                ?.adapter
                ?.getRemoteDevice(address)
        }.getOrNull() ?: return

        DiagnosticsStore.add(context, "Companion device connected")
        RefreshCoordinator.handleCompanionDeviceConnected(context, device)
    }

    fun handleDeviceDisappeared(context: Context, address: String) {
        val associated = associatedAddress(context) ?: return
        if (associated.equals(address, ignoreCase = true)) {
            DiagnosticsStore.add(context, "Companion device disconnected")
            val device = runCatching {
                context.getSystemService(android.bluetooth.BluetoothManager::class.java)
                    ?.adapter
                    ?.getRemoteDevice(address)
            }.getOrNull()
            if (device != null) {
                RefreshCoordinator.handleCompanionDeviceDisconnected(context, device)
            }
        }
    }
}
