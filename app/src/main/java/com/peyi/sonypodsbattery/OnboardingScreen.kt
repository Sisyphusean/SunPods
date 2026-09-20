package com.peyi.sonypodsbattery

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.isSystemInDarkTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
private fun OnboardingTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isDark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        isDark -> darkColorScheme(
            primary = Color(0xFF8DEEB7),
            onPrimary = Color(0xFF00391E),
            background = Color(0xFF0E1417),
            surface = Color(0xFF151D20),
        )
        else -> lightColorScheme(
            primary = Color(0xFF006D3C),
            onPrimary = Color.White,
        )
    }
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
fun OnboardingWelcomeScreen(onContinue: () -> Unit) {
    OnboardingTheme {
        OnboardingPage(
            title = "Control your earbuds without the clutter",
            subtitle = "Everything you use most, without digging through menus.",
            icon = Icons.Rounded.Headphones,
            progress = null,
            imageRes = R.drawable.sonypods_earbuds,
            imageDescription = "Sony WF-1000XM5 earbuds",
            content = {
                InfoCard(
                    title = "Your WF-1000XM5, at a glance",
                    body = "See battery levels, switch listening modes, and keep the connection status close at hand.",
                )
            },
            actionLabel = "Get started",
            onAction = onContinue,
        )
    }
}

@Composable
fun OnboardingPermissionScreen(
    nearby: Boolean,
    message: String?,
    actionLabel: String,
    actionEnabled: Boolean,
    onAction: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val title = if (nearby) "Find your earbuds" else "Allow notifications"
    val subtitle = if (nearby) {
        "Allow access to nearby devices so SONPODS can connect to your earbuds."
    } else {
        "Notifications are required for the connection state and battery alert experience in this MVP."
    }
    val body = if (nearby) {
        "Nearby devices are required for discovery, live battery, listening modes, and Bluetooth controls."
    } else {
        "SONPODS uses notifications to keep you informed when your earbuds connect and when battery refreshes complete."
    }
    OnboardingTheme {
        OnboardingPage(
            title = title,
            subtitle = subtitle,
            icon = if (nearby) Icons.Rounded.BluetoothSearching else Icons.Rounded.Notifications,
            progress = if (nearby) 0.25f else 0.5f,
            content = {
                InfoCard(title = if (nearby) "Nearby devices" else "Notifications", body = body)
                if (message != null) {
                    Spacer(Modifier.height(12.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(
                            text = message,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Settings, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Open app settings")
                    }
                }
            },
            actionLabel = actionLabel,
            actionEnabled = actionEnabled,
            onAction = onAction,
        )
    }
}

@Composable
fun OnboardingPairingScreen(
    message: String?,
    actionEnabled: Boolean,
    onAction: () -> Unit,
) {
    var showHelp by remember { mutableStateOf(false) }
    OnboardingTheme {
        OnboardingPage(
            title = "Put your earbuds in pairing mode",
            subtitle = "Keep both earbuds in the case and leave the lid open.",
            icon = Icons.Rounded.BluetoothSearching,
            progress = 0.75f,
            imageRes = R.drawable.sonypods_pairing_case,
            imageDescription = "Rear view of the Sony WF-1000XM5 charging case showing the pairing button",
            imageBeforeTitle = false,
            content = {
                InfoCard(
                    title = "Press and hold the pairing button on the back",
                    body = "Keep the lid open, then hold the rear button for about 5 seconds until the blue indicator repeatedly flashes twice.",
                )
                TextButton(onClick = { showHelp = !showHelp }) {
                    Text(if (showHelp) "Hide pairing help" else "Need help?")
                }
                if (showHelp) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Pairing checklist", fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "1. Open the case lid\n2. Keep both earbuds inside\n3. Hold the rear button for 5+ seconds\n4. Wait for the blue light to flash twice repeatedly",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (message != null) {
                    Spacer(Modifier.height(12.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(
                            text = message,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            },
            actionLabel = "I'm ready",
            actionEnabled = actionEnabled,
            onAction = onAction,
        )
    }
}

@Composable
fun OnboardingSearchingScreen(
    message: String?,
    onCancel: () -> Unit,
    onBluetoothOff: () -> Unit,
    onNotFound: () -> Unit,
) {
    OnboardingTheme {
        OnboardingPage(
            title = "Looking for your earbuds",
            subtitle = "Keep the case open and nearby while SONPODS searches.",
            icon = Icons.Rounded.BluetoothSearching,
            progress = 0.85f,
            content = {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text("Searching for WF-1000XM5", style = MaterialTheme.typography.titleMedium)
                    }
                }
                if (message != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onBluetoothOff) { Text("Bluetooth is off") }
                TextButton(onClick = onNotFound) { Text("Can't find earbuds") }
            },
            actionLabel = "Cancel",
            onAction = onCancel,
        )
    }
}

@Composable
fun OnboardingBluetoothOffScreen(
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    OnboardingTheme {
        OnboardingPage(
            title = "Turn on Bluetooth",
            subtitle = "Bluetooth needs to be on before SONPODS can find your earbuds.",
            icon = Icons.Rounded.BluetoothDisabled,
            progress = 0.85f,
            content = {
                InfoCard(
                    title = "Bluetooth is currently off",
                    body = "Turn Bluetooth on, then return here and retry the search.",
                )
                OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Settings, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Open Bluetooth settings")
                }
            },
            actionLabel = "Retry",
            onAction = onRetry,
        )
    }
}

@Composable
fun OnboardingNotFoundScreen(
    onRetry: () -> Unit,
    onHelp: () -> Unit,
) {
    OnboardingTheme {
        OnboardingPage(
            title = "We couldn't find your earbuds",
            subtitle = "Make sure the case is open and the blue light is flashing.",
            icon = Icons.Rounded.SearchOff,
            progress = 0.85f,
            content = {
                InfoCard(
                    title = "Try bringing them closer",
                    body = "Keep both earbuds in the open case, hold the rear pairing button for at least 5 seconds, and search again.",
                )
                OutlinedButton(onClick = onHelp, modifier = Modifier.fillMaxWidth()) {
                    Text("Need pairing help?")
                }
            },
            actionLabel = "Retry search",
            onAction = onRetry,
        )
    }
}

@Composable
fun OnboardingFoundScreen(
    onConnect: () -> Unit,
    onSearchAgain: () -> Unit,
) {
    OnboardingTheme {
        OnboardingPage(
            title = "Earbuds found",
            subtitle = "SONPODS found a nearby WF-1000XM5.",
            icon = Icons.Rounded.Headphones,
            progress = 0.92f,
            content = {
                InfoCard(
                    title = "WF-1000XM5",
                    body = "Available now • Ready to connect",
                )
                OutlinedButton(onClick = onSearchAgain, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Search again")
                }
            },
            actionLabel = "Connect",
            onAction = onConnect,
        )
    }
}

@Composable
fun OnboardingPairFailedScreen(
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    OnboardingTheme {
        OnboardingPage(
            title = "Pairing didn't finish",
            subtitle = "The earbuds were found, but Android couldn't complete the connection.",
            icon = Icons.Rounded.LinkOff,
            progress = 0.92f,
            content = {
                InfoCard(
                    title = "Try pairing again",
                    body = "Keep the case open, confirm the blue light is flashing, and make sure another device is not already connected.",
                )
                OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Settings, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Open Bluetooth settings")
                }
            },
            actionLabel = "Try again",
            onAction = onRetry,
        )
    }
}

@Composable
fun OnboardingConnectedScreen(onContinue: () -> Unit) {
    OnboardingTheme {
        OnboardingPage(
            title = "You're connected",
            subtitle = "Your WF-1000XM5 is ready for battery and listening controls.",
            icon = Icons.Rounded.CheckCircle,
            progress = 1f,
            content = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BatteryTile("Left", "82%", Modifier.weight(1f))
                    BatteryTile("Right", "76%", Modifier.weight(1f))
                    BatteryTile("Case", "64%", Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                InfoCard(
                    title = "Automatic background refresh enabled",
                    body = "SONPODS can now refresh battery information without keeping the control app open.",
                )
            },
            actionLabel = "Continue to home",
            onAction = onContinue,
        )
    }
}

@Composable
private fun OnboardingPage(
    title: String,
    subtitle: String,
    icon: ImageVector,
    progress: Float?,
    imageRes: Int? = null,
    imageDescription: String? = null,
    imageBeforeTitle: Boolean = true,
    content: @Composable () -> Unit,
    actionLabel: String,
    actionEnabled: Boolean = true,
    onAction: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
        ) {
            Text(
                text = "SONPODS",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(20.dp))
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(28.dp))
            } else {
                Spacer(Modifier.height(8.dp))
            }
            Card(
                modifier = Modifier.size(72.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            if (imageRes != null && imageBeforeTitle) {
                Spacer(Modifier.height(20.dp))
                PrototypeImage(imageRes, imageDescription)
            }
            Spacer(Modifier.height(24.dp))
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (imageRes != null && !imageBeforeTitle) {
                Spacer(Modifier.height(20.dp))
                PrototypeImage(imageRes, imageDescription)
            }
            Spacer(Modifier.height(24.dp))
            content()
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onAction,
                enabled = actionEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun PrototypeImage(imageRes: Int, imageDescription: String?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Image(
            painter = painterResource(imageRes),
            contentDescription = imageDescription,
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun BatteryTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
