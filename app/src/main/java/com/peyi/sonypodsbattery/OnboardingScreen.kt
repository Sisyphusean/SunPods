package com.peyi.sonypodsbattery

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Help
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable

private const val ONBOARDING_STEPS = 7f

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
            progress = 1f / ONBOARDING_STEPS,
            content = {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    PrototypeImage(R.drawable.sonypods_earbuds, "WF-1000XM5 product reference render")
                }
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
    val denied = message != null
    val title = when {
        nearby && denied -> "Nearby devices is required"
        nearby -> "Find your earbuds"
        denied -> "Notifications are required"
        else -> "Allow notifications"
    }
    val subtitle = when {
        nearby && denied -> "SONPODS cannot discover or control your earbuds without this permission."
        nearby -> "Allow access to nearby devices so SONPODS can connect to your earbuds."
        denied -> "You need to allow notifications to continue onboarding."
        else -> "Notifications are required for this MVP."
    }
    val body = if (nearby) {
        if (denied) {
            "Try the permission request again. If Android stops offering the prompt, enable the permission from App info."
        } else {
            "Required for discovery, live battery, ANC, Ambient Sound, EQ, and other Bluetooth controls."
        }
    } else {
        if (denied) {
            "Without notification permission, this MVP does not meet its required battery and connection alert behavior."
        } else {
            "SONPODS needs notifications for the required connection state and battery alert experience in this MVP."
        }
    }
    val icon = if (denied) Icons.Rounded.Warning
    else if (nearby) Icons.Rounded.BluetoothSearching
    else Icons.Rounded.Notifications
    val nextPermission = if (nearby) "Nearby devices" else "notification"
    OnboardingTheme {
        OnboardingPage(
            title = title,
            subtitle = subtitle,
            progress = if (nearby) 2f / ONBOARDING_STEPS else 3f / ONBOARDING_STEPS,
            content = {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                    containerColor = if (denied) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                )) {
                    Column(Modifier.padding(18.dp)) {
                        IconTile(icon, denied)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (denied) MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (!denied) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Android will ask for " + nextPermission + " permission next.",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (denied) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Settings, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Open settings instead")
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
    onHelp: () -> Unit,
    onAction: () -> Unit,
) {
    OnboardingTheme {
        OnboardingPage(
            title = "Put your earbuds in pairing mode",
            subtitle = "Keep both earbuds in the case and leave the lid open.",
            progress = 4f / ONBOARDING_STEPS,
            content = {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                )) {
                    Column {
                        PrototypeImage(
                            R.drawable.sonypods_pairing_case,
                            "Rear view of Sony WF-1000XM5 charging case showing the pairing button and USB-C port",
                        )
                        Text(
                            "Press and hold the pairing button on the back.",
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Keep the lid open, then hold the rear button for about 5 seconds or more, until the blue indicator repeatedly flashes twice.",
                            modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 18.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (message != null) {
                    Spacer(Modifier.height(12.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(
                            message,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onHelp, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Help, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Need help")
                }
            },
            actionLabel = "I'm ready",
            actionEnabled = actionEnabled,
            onAction = onAction,
        )
    }
}

@Composable
fun OnboardingHelpScreen(
    onBack: () -> Unit,
    onGotIt: () -> Unit,
) {
    OnboardingTheme {
        OnboardingPage(
            title = "Pairing help",
            subtitle = "Use these steps if the earbuds are not entering pairing mode.",
            progress = 4f / ONBOARDING_STEPS,
            content = {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                )) {
                    Column(Modifier.padding(18.dp)) {
                        PairingStep("1", "Open the case lid")
                        PairingStep("2", "Keep both earbuds inside")
                        PairingStep("3", "Hold the rear button for 5+ seconds")
                        PairingStep("4", "Wait for the blue light to flash twice repeatedly")
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                    Text("Back")
                }
            },
            actionLabel = "Got it",
            onAction = onGotIt,
        )
    }
}

@Composable
fun OnboardingSearchingScreen(
    message: String?,
    onCancel: () -> Unit,
) {
    OnboardingTheme {
        OnboardingPage(
            title = "Looking for your earbuds",
            subtitle = "Keep the case open and nearby.",
            progress = 5f / ONBOARDING_STEPS,
            content = {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                )) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(18.dp))
                        Text("Searching for WF 1000XM5", style = MaterialTheme.typography.bodyLarge)
                    }
                }
                if (message != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            actionLabel = "Cancel",
            actionFilled = false,
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
            title = "Bluetooth is off",
            subtitle = "Turn Bluetooth on so SONPODS can search for your earbuds.",
            progress = 5f / ONBOARDING_STEPS,
            content = {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                )) {
                    Column(Modifier.padding(18.dp)) {
                        IconTile(Icons.Rounded.BluetoothDisabled, false)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Open Android Bluetooth settings, enable Bluetooth, then return here.",
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Retry")
                }
            },
            actionLabel = "Turn on Bluetooth",
            onAction = onOpenSettings,
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
            title = "No earbuds found",
            subtitle = "We could not find your earbuds yet.",
            progress = 5f / ONBOARDING_STEPS,
            content = {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                )) {
                    Column(Modifier.padding(18.dp)) {
                        IconTile(Icons.Rounded.SearchOff, true)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Check that the case is open and the rear button was held until the indicator started flashing twice.",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onHelp, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Help, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Need help")
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
            title = "Your earbuds are nearby",
            subtitle = "Choose the device you want SONPODS to connect to.",
            progress = 6f / ONBOARDING_STEPS,
            content = {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                )) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(R.drawable.sonypods_earbuds),
                                contentDescription = "WF-1000XM5 product reference render",
                                modifier = Modifier.size(56.dp),
                                contentScale = ContentScale.Fit,
                            )
                            Spacer(Modifier.size(12.dp))
                            Column {
                                Text("WF 1000XM5", fontWeight = FontWeight.SemiBold)
                                Text("Available now", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "SONPODS will request the Bluetooth connection needed for live controls.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
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
            title = "Could not connect",
            subtitle = "The pairing request did not complete.",
            progress = 6f / ONBOARDING_STEPS,
            content = {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                )) {
                    Column(Modifier.padding(18.dp)) {
                        IconTile(Icons.Rounded.LinkOff, true)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Make sure the earbuds are still in pairing mode, then try again.",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
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
            subtitle = "Live battery and listening controls are ready.",
            progress = 1f,
            content = {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                )) {
                    Column(Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            IconTile(Icons.Rounded.Check, false)
                        }
                        Spacer(Modifier.height(18.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            BatteryTile("Left", "82%", Modifier.weight(1f))
                            BatteryTile("Right", "76%", Modifier.weight(1f))
                            BatteryTile("Case", "64%", Modifier.weight(1f))
                        }
                    }
                }
            },
            actionLabel = "Continue to Home",
            onAction = onContinue,
        )
    }
}

@Composable
private fun OnboardingPage(
    title: String,
    subtitle: String,
    progress: Float,
    content: @Composable () -> Unit,
    actionLabel: String,
    actionEnabled: Boolean = true,
    actionFilled: Boolean = true,
    onAction: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "SONPODS",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = {}) {
                    Icon(Icons.Rounded.Help, contentDescription = "Help")
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            )
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
            Spacer(Modifier.height(28.dp))
            if (actionFilled) {
                Button(
                    onClick = onAction,
                    enabled = actionEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) {
                    Text(actionLabel)
                }
            } else {
                OutlinedButton(
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
}

@Composable
private fun PrototypeImage(imageRes: Int, imageDescription: String?) {
    Image(
        painter = painterResource(imageRes),
        contentDescription = imageDescription,
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .padding(12.dp),
        contentScale = ContentScale.Fit,
    )
}

@Composable
private fun IconTile(icon: ImageVector, error: Boolean) {
    val tileColor = if (error) MaterialTheme.colorScheme.errorContainer
    else MaterialTheme.colorScheme.primaryContainer
    val iconColor = if (error) MaterialTheme.colorScheme.onErrorContainer
    else MaterialTheme.colorScheme.onPrimaryContainer
    Card(
        modifier = Modifier.size(56.dp),
        colors = CardDefaults.cardColors(containerColor = tileColor),
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            tint = iconColor,
        )
    }
}

@Composable
private fun PairingStep(number: String, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Card(
            modifier = Modifier.size(32.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Text(number, modifier = Modifier.padding(8.dp), fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.size(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
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
