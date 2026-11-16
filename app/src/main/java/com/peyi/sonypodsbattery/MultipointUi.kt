package com.peyi.sonypodsbattery

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Controls tab: the devices the earbuds remember, with connect, disconnect, play here and forget. */
@Composable
internal fun DevicesCard(connected: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var devices by remember { mutableStateOf(DevicesCache.last) }
    var busy by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var pairing by remember { mutableStateOf(false) }
    var forgetting by remember { mutableStateOf<PairedDevice?>(null) }
    val phone = remember { Multipoint.phoneName(context) }

    suspend fun reload() {
        RefreshCoordinator.awaitSession(context, "Paired devices") { Multipoint.read(it) }
            .onSuccess { list -> devices = list; DevicesCache.last = list }
    }

    fun run(key: String, label: String, failed: String, block: (SonyBatteryClient.SonySession) -> Boolean) {
        busy = key
        note = null
        scope.launch {
            val ok = RefreshCoordinator.awaitSession(context, label, block).getOrDefault(false)
            if (!ok) note = failed
            reload()
            busy = null
        }
    }

    LaunchedEffect(connected) {
        if (DemoMode.on(context)) {
            devices = PairedDevices(listOf(
                PairedDevice("AA:BB:CC:DD:EE:01", phone ?: "Galaxy S24 Ultra", 1),
                PairedDevice("AA:BB:CC:DD:EE:02", "Work laptop", 2),
                PairedDevice("AA:BB:CC:DD:EE:03", "iPad", 0),
                PairedDevice("AA:BB:CC:DD:EE:04", "Living room TV", 0)
            ), playingOrder = 1)
        } else if (connected) reload()
    }

    val list = devices ?: return
    Column {
        SectionHeader("Devices")
        SunCard(Modifier.animateContentSize()) {
            list.list.forEachIndexed { i, d ->
                if (i > 0) RowDivider()
                val isPhone = phone != null && d.name == phone
                DeviceRow(
                    device = d,
                    isPhone = isPhone,
                    playing = list.playing(d),
                    busy = busy == d.address,
                    enabled = busy == null,
                    onConnect = { run(d.address, "Connect ${d.name}", "${d.name} didn't connect. Make sure its Bluetooth is on and it's nearby.") { Multipoint.act(it, d, DeviceAction.CONNECT) } },
                    onDisconnect = { run(d.address, "Disconnect ${d.name}", "Couldn't disconnect ${d.name}.") { Multipoint.act(it, d, DeviceAction.DISCONNECT) } },
                    onPlayHere = { run(d.address, "Play on ${d.name}", "Couldn't move audio to ${d.name}. It may be on a call.") { Multipoint.playOn(it, d) } },
                    onForget = { forgetting = d }
                )
            }
            if (list.list.isNotEmpty()) RowDivider()
            PairRow(pairing, enabled = busy == null) { on ->
                busy = "pair"
                scope.launch {
                    val ok = RefreshCoordinator.awaitSession(context, "Pairing mode") { Multipoint.setPairingMode(it, on) }.getOrDefault(false)
                    if (ok) pairing = on else note = "The earbuds didn't switch pairing mode."
                    busy = null
                }
            }
        }
        Footer(note ?: "Turn on Connect to 2 devices to use two at once. Forgetting a device removes it from the earbuds; to use it again, pair from that device.")
    }

    forgetting?.let { d ->
        AlertDialog(
            onDismissRequest = { forgetting = null },
            title = { Text("Forget ${d.name}?") },
            text = { Text("The earbuds will no longer connect to it. To use it again, pair it from its Bluetooth settings.") },
            confirmButton = {
                TextButton(onClick = {
                    forgetting = null
                    run(d.address, "Forget ${d.name}", "Couldn't forget ${d.name}.") { Multipoint.act(it, d, DeviceAction.FORGET) }
                }) { Text("Forget") }
            },
            dismissButton = { TextButton(onClick = { forgetting = null }) { Text("Cancel") } }
        )
    }
}

/** Survives leaving the tab, so the list shows at once next time. */
private object DevicesCache {
    @Volatile var last: PairedDevices? = null
}

@Composable
private fun DeviceRow(
    device: PairedDevice,
    isPhone: Boolean,
    playing: Boolean,
    busy: Boolean,
    enabled: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onPlayHere: () -> Unit,
    onForget: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    val state = when {
        playing -> "Playing audio"
        device.connected -> "Connected"
        else -> "Not connected"
    }
    val status = if (isPhone) "This phone · $state" else state
    Row(
        Modifier.fillMaxWidth()
            .clickable(enabled = enabled && !device.connected, onClickLabel = "Connect") { onConnect() }
            .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowIcon(if (isPhone) Icons.Rounded.PhoneAndroid else Icons.Rounded.Devices, active = device.connected)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(device.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                status, style = MaterialTheme.typography.bodyMedium,
                color = if (playing) colors.primary else colors.onSurfaceVariant
            )
        }
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
            } else {
                IconButton(onClick = { menu = true }, enabled = enabled, modifier = Modifier.semantics { contentDescription = "Options for ${device.name}" }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = null)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (!device.connected) DropdownMenuItem(text = { Text("Connect") }, onClick = { menu = false; onConnect() })
                    if (device.connected && !playing) DropdownMenuItem(text = { Text("Play audio from here") }, onClick = { menu = false; onPlayHere() })
                    // Dropping or forgetting this phone would cut SunPods off; do that in Android's Bluetooth settings.
                    if (device.connected && !isPhone) DropdownMenuItem(text = { Text("Disconnect") }, onClick = { menu = false; onDisconnect() })
                    if (!isPhone) DropdownMenuItem(text = { Text("Forget", color = colors.error) }, onClick = { menu = false; onForget() })
                }
            }
        }
    }
}

@Composable
private fun PairRow(pairing: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth()
                .clickable(enabled = enabled) { onChange(!pairing) }
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RowIcon(if (pairing) Icons.Rounded.BluetoothSearching else Icons.Rounded.Add, active = pairing)
            Spacer(Modifier.width(16.dp))
            Text(if (pairing) "Stop pairing" else "Pair a new device", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        }
        AnimatedVisibility(pairing) {
            Text(
                "Your earbuds are discoverable. On the other device, open Bluetooth settings and choose your WF-1000XM5.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 76.dp, end = 20.dp, bottom = 14.dp)
            )
        }
    }
}
