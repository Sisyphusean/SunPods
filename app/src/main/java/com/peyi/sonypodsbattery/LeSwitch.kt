package com.peyi.sonypodsbattery

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private sealed interface Switch {
    data object Sending : Switch
    data class Waiting(val note: String) : Switch
    data object Done : Switch
    data class Failed(val message: String) : Switch
}

/**
 * Switches the earbuds between Classic and LE Audio the way Sound Connect does: set the earbuds'
 * LE Audio setting (with "change the connection method"), then wait for Android to reconnect.
 * If LE Audio hasn't come up after a while, pair the earbuds' LE addresses, as Sony's app does
 * when the phone has never paired with them over LE.
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
internal fun LeSwitchSheet(on: Boolean, settings: DeviceSettings, onDone: () -> Unit) {
    val context = LocalContext.current
    var state by remember { mutableStateOf<Switch>(Switch.Sending) }
    val target = if (on) "LE Audio" else "Classic"

    LaunchedEffect(Unit) {
        fun log(m: String) = DiagnosticsStore.add(context, "Connection mode: $m")
        val sent = RefreshCoordinator.awaitSession(context, "Connection mode") { s ->
            val addresses = SonyFeatures.leAddresses(s)
            log("addresses ${addresses.joinToString()}")
            val acked = SonyFeatures.setLeAudio(s, settings, on)
            log("asked for $target, ${if (acked) "earbuds acknowledged" else "no acknowledgement"}")
            addresses
        }
        val addresses = sent.getOrElse {
            log("couldn't send: ${it.message}")
            state = Switch.Failed("Couldn't reach your earbuds. Make sure they're connected and Sony Sound Connect is closed, then try again.")
            return@LaunchedEffect
        }
        state = Switch.Waiting("Your earbuds are reconnecting.")
        val start = System.currentTimeMillis()
        var bonded = false
        while (System.currentTimeMillis() - start < 45_000) {
            delay(1_500)
            val reached = withContext(Dispatchers.IO) {
                if (on) LeAudio.anyConnected(context) else !LeAudio.anyConnected(context) && classicConnected(context)
            }
            if (reached) { log("now on $target after ${(System.currentTimeMillis() - start) / 1000} s"); state = Switch.Done; return@LaunchedEffect }
            if (on && !bonded && System.currentTimeMillis() - start > 15_000) {
                bonded = true
                val started = withContext(Dispatchers.IO) { bondLe(context, addresses) }
                log("paired LE addresses: $started")
                if (started.isNotEmpty()) state = Switch.Waiting("Pairing for LE Audio. If Android asks, tap Pair.")
            }
        }
        log("didn't reach $target in 45 s")
        if (on) {
            // Don't leave the earbuds half switched: their LE Audio setting turns off Connect to 2 devices.
            val reverted = RefreshCoordinator.awaitSession(context, "Connection mode") { s -> SonyFeatures.setLeAudio(s, settings, false) }
            log("put the earbuds back on Classic: ${reverted.getOrNull() ?: "couldn't"}")
        }
        state = Switch.Failed(
            if (on) "Your phone didn't connect using LE Audio, so your earbuds are back on Classic. Some phones only allow LE Audio with approved earbuds. On Samsung and Pixel you can allow it in Developer options: turn on Bypass LE Audio allowlist, reconnect your earbuds, then try again."
            else "Your earbuds didn't come back on Classic. Open Bluetooth settings, tap WF-1000XM5 and turn LE Audio off."
        )
    }

    ModalBottomSheet(onDismissRequest = onDone, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            Text("Switching to $target", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            when (val s = state) {
                Switch.Sending -> { Text("Telling your earbuds.", style = MaterialTheme.typography.bodyMedium); Spacer(Modifier.height(16.dp)); CircularProgressIndicator() }
                is Switch.Waiting -> { Text(s.note + " This can take up to a minute. Keep them in your ears.", style = MaterialTheme.typography.bodyMedium); Spacer(Modifier.height(16.dp)); CircularProgressIndicator() }
                Switch.Done -> {
                    Text("Your earbuds are now on $target.", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
                }
                is Switch.Failed -> {
                    Text(s.message, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) } }, modifier = Modifier.fillMaxWidth()) { Text("Open Bluetooth settings") }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Close") }
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
private fun classicConnected(context: Context): Boolean = runCatching {
    context.getSystemService(BluetoothManager::class.java)?.adapter?.getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothProfile.STATE_CONNECTED
}.getOrDefault(false)

/** Starts pairing with any of the earbuds' addresses Android doesn't know yet. Returns those started. */
@SuppressLint("MissingPermission")
private fun bondLe(context: Context, addresses: List<String>): List<String> {
    val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return emptyList()
    return addresses.mapNotNull { a ->
        runCatching {
            val d = adapter.getRemoteDevice(a.uppercase())
            if (d.bondState == BluetoothDevice.BOND_NONE && d.createBond()) a else null
        }.getOrNull()
    }
}
