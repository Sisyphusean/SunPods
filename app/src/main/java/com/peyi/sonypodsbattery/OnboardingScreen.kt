package com.peyi.sonypodsbattery

import android.os.Build
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
import androidx.compose.material.icons.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.platform.LocalContext
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
private fun OnboardingPage(
    title: String,
    subtitle: String,
    icon: ImageVector,
    progress: Float?,
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
            Spacer(Modifier.height(24.dp))
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
