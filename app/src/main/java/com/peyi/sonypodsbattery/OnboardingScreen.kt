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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Help
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.foundation.shape.RoundedCornerShape

private const val ONBOARDING_STEPS = 7f
private val OnboardingPillShape = RoundedCornerShape(percent = 50)
private val OnboardingCardShape = RoundedCornerShape(20.dp)
private val OnboardingErrorCardColor = Color(0xFF2B1B1D)
private val OnboardingErrorTextColor = Color(0xFFFFB4AB)
private val OnboardingWarningCardColor = Color(0xFF2B2519)
private val OnboardingWarningTextColor = Color(0xFFF2D38A)

@Composable
private fun OnboardingTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicDarkColorScheme(context)
        else -> darkColorScheme(
            primary = Color(0xFFD0BCFF),
            onPrimary = Color(0xFF381E72),
            primaryContainer = Color(0xFF4F378B),
            onPrimaryContainer = Color(0xFFEADDFF),
            background = Color(0xFF100D13),
            onBackground = Color(0xFFE7E0E8),
            surface = Color(0xFF100D13),
            onSurface = Color(0xFFE7E0E8),
            surfaceVariant = Color(0xFF49454F),
            onSurfaceVariant = Color(0xFFCAC4D0),
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
    denied: Boolean,
    actionLabel: String,
    actionEnabled: Boolean,
    onAction: () -> Unit,
    onOpenSettings: () -> Unit,
    onExit: (() -> Unit)? = null,
) {
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
    val icon = if (denied && nearby) Icons.Rounded.BluetoothDisabled
    else if (denied) Icons.Rounded.NotificationsOff
    else if (nearby) Icons.Rounded.BluetoothSearching
    else Icons.Rounded.Notifications
    val nextPermission = if (nearby) "Nearby devices" else "notification"
    OnboardingTheme {
        OnboardingPage(
            title = title,
            subtitle = subtitle,
            progress = if (nearby) 2f / ONBOARDING_STEPS else 3f / ONBOARDING_STEPS,
            content = {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (denied) OnboardingErrorCardColor
                        else MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    shape = OnboardingCardShape,
                ) {
                    Column(Modifier.padding(18.dp)) {
                        IconTile(icon, denied)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (denied) OnboardingErrorTextColor
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
            },
            actionLabel = actionLabel,
            actionEnabled = actionEnabled,
            onAction = onAction,
            secondaryActionLabel = if (denied) "Open settings instead" else null,
            secondaryActionIcon = if (denied) Icons.Rounded.Settings else null,
            onSecondaryAction = if (denied) onOpenSettings else null,
            tertiaryActionLabel = if (denied && nearby) "Exit onboarding" else null,
            onTertiaryAction = onExit,
        )
    }
}

@Composable
fun OnboardingPermissionSettingsScreen(
    nearby: Boolean,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    OnboardingTheme {
        OnboardingPage(
            title = if (nearby) "Enable nearby devices in Settings" else "Enable notifications in Settings",
            subtitle = if (nearby) {
                "Android is no longer showing the permission prompt."
            } else {
                "Android may no longer show the notifications prompt."
            },
            progress = if (nearby) 2f / ONBOARDING_STEPS else 3f / ONBOARDING_STEPS,
            content = {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = OnboardingCardShape,
                ) {
                    Column(Modifier.padding(18.dp)) {
                        IconTile(Icons.Rounded.Settings, false)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (nearby) {
                                "Open App info, choose Permissions, then enable Nearby devices for SONPODS."
                            } else {
                                "Open App info, choose Notifications, then allow notifications for SONPODS."
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            secondaryActionLabel = "Back",
            onSecondaryAction = onBack,
            actionLabel = "Open Settings",
            onAction = onOpenSettings,
        )
    }
}

@Composable
fun OnboardingPairingScreen(
    message: String?,
    actionEnabled: Boolean,
    onHelp: () -> Unit,
    onAction: () -> Unit,
    mockEnabled: Boolean = false,
    onMockDevice: (() -> Unit)? = null,
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
                    Card(colors = CardDefaults.cardColors(containerColor = OnboardingErrorCardColor), shape = OnboardingCardShape) {
                        Text(
                            message,
                            modifier = Modifier.padding(16.dp),
                            color = OnboardingErrorTextColor,
                        )
                    }
                }
            },
            actionLabel = "I'm ready",
            actionEnabled = actionEnabled,
            onAction = onAction,
            secondaryActionLabel = "Need help",
            secondaryActionIcon = Icons.Rounded.Help,
            onSecondaryAction = onHelp,
            tertiaryActionLabel = if (mockEnabled) "Use emulator mock" else null,
            onTertiaryAction = onMockDevice,
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
            },
            actionLabel = "Got it",
            onAction = onGotIt,
            secondaryActionLabel = "Back",
            onSecondaryAction = onBack,
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
                    containerColor = OnboardingWarningCardColor,
                ), shape = OnboardingCardShape) {
                    Column(Modifier.padding(18.dp)) {
                        IconTile(Icons.Rounded.BluetoothDisabled, false)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Open Android Bluetooth settings, enable Bluetooth, then return here.",
                            color = OnboardingWarningTextColor,
                        )
                    }
                }
            },
            actionLabel = "Turn on Bluetooth",
            onAction = onOpenSettings,
            secondaryActionLabel = "Retry",
            secondaryActionIcon = Icons.Rounded.Refresh,
            onSecondaryAction = onRetry,
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
                    containerColor = OnboardingErrorCardColor,
                ), shape = OnboardingCardShape) {
                    Column(Modifier.padding(18.dp)) {
                        IconTile(Icons.Rounded.SearchOff, true)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Check that the case is open and the rear button was held until the indicator started flashing twice.",
                            color = OnboardingErrorTextColor,
                        )
                    }
                }
            },
            actionLabel = "Retry search",
            onAction = onRetry,
            secondaryActionLabel = "Need help",
            secondaryActionIcon = Icons.Rounded.Help,
            onSecondaryAction = onHelp,
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
            },
            actionLabel = "Connect",
            onAction = onConnect,
            secondaryActionLabel = "Search again",
            secondaryActionIcon = Icons.Rounded.Refresh,
            onSecondaryAction = onSearchAgain,
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
                    containerColor = OnboardingErrorCardColor,
                ), shape = OnboardingCardShape) {
                    Column(Modifier.padding(18.dp)) {
                        IconTile(Icons.Rounded.LinkOff, true)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Make sure the earbuds are still in pairing mode, then try again.",
                            color = OnboardingErrorTextColor,
                        )
                    }
                }
            },
            actionLabel = "Try again",
            onAction = onRetry,
            secondaryActionLabel = "Open Bluetooth settings",
            secondaryActionIcon = Icons.Rounded.Settings,
            onSecondaryAction = onOpenSettings,
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
    secondaryActionLabel: String? = null,
    secondaryActionIcon: ImageVector? = null,
    onSecondaryAction: (() -> Unit)? = null,
    tertiaryActionLabel: String? = null,
    onTertiaryAction: (() -> Unit)? = null,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "SONPODS",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .padding(top = 12.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .18f),
                drawStopIndicator = {},
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
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
                Spacer(Modifier.height(24.dp))
            }
            Column(modifier = Modifier.fillMaxWidth()) {
                if (secondaryActionLabel != null && onSecondaryAction != null) {
                    OutlinedButton(
                        onClick = onSecondaryAction,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = OnboardingPillShape,
                    ) {
                        Text(secondaryActionLabel)
                        if (secondaryActionIcon != null) {
                            Spacer(Modifier.size(8.dp))
                            Icon(secondaryActionIcon, contentDescription = null)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                if (actionFilled) {
                    Button(
                        onClick = onAction,
                        enabled = actionEnabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = OnboardingPillShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
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
                        shape = OnboardingPillShape,
                    ) {
                        Text(actionLabel)
                    }
                }
                if (tertiaryActionLabel != null && onTertiaryAction != null) {
                    TextButton(
                        onClick = onTertiaryAction,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(tertiaryActionLabel)
                    }
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
    val tileColor = if (error) OnboardingErrorCardColor
    else MaterialTheme.colorScheme.primaryContainer
    val iconColor = if (error) OnboardingErrorTextColor
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
