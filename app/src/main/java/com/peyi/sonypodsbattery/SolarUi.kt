@file:OptIn(ExperimentalMaterial3Api::class)

package com.peyi.sonypodsbattery

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// ======================================================================
// Brand: amber sun on ink. Used unless "Match wallpaper colours" is on.
// ======================================================================

internal val SolarAmber = Color(0xFFFFB547)
internal val SolarInk = Color(0xFF15161A)
internal val SolarDusk = Color(0xFFFF7A45)

internal val SolarDark = darkColorScheme(
    primary = SolarAmber, onPrimary = Color(0xFF3D2300),
    primaryContainer = Color(0xFF5C3B00), onPrimaryContainer = Color(0xFFFFDDB0),
    secondary = Color(0xFFE2C29E), onSecondary = Color(0xFF3F2D17),
    secondaryContainer = Color(0xFF3A3026), onSecondaryContainer = Color(0xFFF5DEC2),
    tertiary = Color(0xFFFF9B73), onTertiary = Color(0xFF5A1B00),
    tertiaryContainer = Color(0xFF6E2A10), onTertiaryContainer = Color(0xFFFFDBCC),
    background = SolarInk, onBackground = Color(0xFFECE6DD),
    surface = SolarInk, onSurface = Color(0xFFECE6DD),
    surfaceVariant = Color(0xFF3A372F), onSurfaceVariant = Color(0xFFCFC6B8),
    surfaceContainerLowest = Color(0xFF0F1013), surfaceContainerLow = Color(0xFF1A1B20),
    surfaceContainer = Color(0xFF1F2026), surfaceContainerHigh = Color(0xFF292A30),
    surfaceContainerHighest = Color(0xFF34353B),
    outline = Color(0xFF948D80), outlineVariant = Color(0xFF47433B),
    inverseSurface = Color(0xFFECE6DD), inverseOnSurface = Color(0xFF2B2A27), inversePrimary = Color(0xFF8A5100)
)

internal val SolarLight = lightColorScheme(
    primary = Color(0xFF8A5100), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDDB3), onPrimaryContainer = Color(0xFF2C1600),
    secondary = Color(0xFF705A41), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFBDDBD), onSecondaryContainer = Color(0xFF281805),
    tertiary = Color(0xFFA33E13), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDBCF), onTertiaryContainer = Color(0xFF380D00),
    background = Color(0xFFFCFAF7), onBackground = Color(0xFF1F1B16),
    surface = Color(0xFFFCFAF7), onSurface = Color(0xFF1F1B16),
    surfaceVariant = Color(0xFFEFE6DC), onSurfaceVariant = Color(0xFF504539),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF8F5F1),
    surfaceContainer = Color(0xFFF3F0EB), surfaceContainerHigh = Color(0xFFEDE9E3),
    surfaceContainerHighest = Color(0xFFE7E2DB),
    outline = Color(0xFF827568), outlineVariant = Color(0xFFD5C4B5),
    inverseSurface = Color(0xFF35302A), inverseOnSurface = Color(0xFFF9EFE5), inversePrimary = SolarAmber
)

/** One place to swap in a bundled display face later. Heavy, tight headings carry the voice. */
internal val BrandFont: FontFamily = FontFamily.Default

internal val SolarTypography: Typography = Typography().let { t ->
    fun TextStyle.loud(weight: FontWeight, tracking: Float) =
        copy(fontFamily = BrandFont, fontWeight = weight, letterSpacing = tracking.sp)
    t.copy(
        displayLarge = t.displayLarge.loud(FontWeight.Black, -1.5f),
        displayMedium = t.displayMedium.loud(FontWeight.Black, -1.2f),
        displaySmall = t.displaySmall.loud(FontWeight.ExtraBold, -1f),
        headlineLarge = t.headlineLarge.loud(FontWeight.ExtraBold, -0.8f),
        headlineMedium = t.headlineMedium.loud(FontWeight.ExtraBold, -0.6f),
        headlineSmall = t.headlineSmall.loud(FontWeight.Bold, -0.4f),
        titleLarge = t.titleLarge.loud(FontWeight.Bold, -0.2f),
        titleSmall = t.titleSmall.loud(FontWeight.Bold, 0.1f)
    )
}

// ======================================================================
// Product hero: rendered XM5-style buds floating over a warm glow
// ======================================================================

private fun modeTitle(mode: ListeningMode?, connected: Boolean, ambient: Int): String = when {
    !connected -> "Not Connected"
    mode == ListeningMode.NOISE_CANCELLING -> "Noise Cancelling"
    mode == ListeningMode.AMBIENT -> "Ambient Sound · $ambient"
    mode == ListeningMode.OFF -> "Noise Control Off"
    else -> "Connected"
}

internal fun disconnectedHint(model: EarbudModel): String = "${model.wakeHint}."

@Composable
internal fun SolarHero(
    mode: ListeningMode?,
    ambientLevel: Int,
    connected: Boolean,
    model: String,
    color: String = "black",
    device: String = "xm5",
    animateEntrance: Boolean = false,
    onEntrancePlayed: () -> Unit = {},
    onCycle: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val glow by animateFloatAsState(
        when {
            !connected -> 0.08f
            mode == ListeningMode.NOISE_CANCELLING -> 0.22f
            mode == ListeningMode.AMBIENT -> 0.32f + ambientLevel / 80f
            mode == ListeningMode.OFF -> 0.5f
            else -> 0.2f
        },
        tween(600), label = "glow"
    )
    val motion = rememberInfiniteTransition(label = "float")
    val bob by motion.animateFloat(-1f, 1f, infiniteRepeatable(tween(2800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob")
    val tilt by motion.animateFloat(-1f, 1f, infiniteRepeatable(tween(4300, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "tilt")
    val bump = remember { Animatable(1f) }
    val firstMode = remember { booleanArrayOf(true) }
    LaunchedEffect(mode) {
        if (firstMode[0]) { firstMode[0] = false; return@LaunchedEffect }
        bump.snapTo(0.96f)
        bump.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 300f))
    }
    val title = modeTitle(mode, connected, ambientLevel)

    val reduceMotion = rememberReduceMotion()
    // Keep full opacity while playUnpack()/playPack() is in flight, and gate taps until unpacked.
    var prevConnected by remember { mutableStateOf(connected) }
    var settling by remember { mutableStateOf(connected && animateEntrance && !reduceMotion) }
    LaunchedEffect(connected, reduceMotion) {
        if (connected != prevConnected) {
            prevConnected = connected
            settling = !reduceMotion
        }
        // Fallback timeout in case the WebView pauses mid-animation before reporting unpacked()/packed().
        if (settling) {
            delay(4_200)
            settling = false
        }
    }

    val heroAlpha by animateFloatAsState(
        if (connected || settling) 1f else 0.8f,
        tween(400), label = "heroAlpha"
    )

    // No card around the hero: the buds float on the page with a soft glow that follows the mode.
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(264.dp)
                .drawBehind {
                    // A wide oval glow that fades out fully inside the hero, so it has no edges.
                    val r = size.height / 2
                    scale(scaleX = size.width / size.height * 0.92f, scaleY = 1f, pivot = center) {
                        drawCircle(
                            Brush.radialGradient(
                                listOf(colors.primary.copy(alpha = glow), Color.Transparent),
                                center = center,
                                radius = r
                            ),
                            radius = r,
                            center = center
                        )
                    }
                }
                .semantics {
                    if (connected) onClick(label = "Change Noise Control") { onCycle(); true }
                }
                .semantics { contentDescription = "$model. $title" }
        ) {
            val maxHeroH = (maxHeight - 10.dp).coerceAtLeast(180.dp)
            val heroW = minOf(maxWidth * 0.94f, maxHeroH * (1000f / 712f))
            val heroH = heroW * (712f / 1000f)
            val heroLayer = Modifier
                .align(Alignment.Center)
                .size(width = heroW, height = heroH)
                .graphicsLayer {
                    translationY = bob * 5.dp.toPx()
                    rotationZ = tilt * 1.5f
                    scaleX = bump.value
                    scaleY = bump.value
                    alpha = heroAlpha
                }
            Hero3D(
                device = device,
                color = color,
                caseProgress = if (connected) 0f else 1f,
                animateEntrance = animateEntrance,
                onEntrancePlayed = onEntrancePlayed,
                onUnpacked = { if (connected) settling = false },
                onPacked = { if (!connected) settling = false },
                onTap = if (connected && !settling) onCycle else null,
                modifier = heroLayer
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(model, style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(8.dp).clip(CircleShape)
                    .background(if (connected) colors.primary else colors.outline)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.semantics { heading() }
            )
        }
        if (!connected) {
            Spacer(Modifier.height(12.dp))
            Text(
                disconnectedHint(EarbudModel.active),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant
            )
        }
    }
}
