@file:OptIn(ExperimentalMaterial3Api::class)

package com.peyi.sonypodsbattery

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

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
    background = Color(0xFFFFF8F1), onBackground = Color(0xFF1F1B16),
    surface = Color(0xFFFFF8F1), onSurface = Color(0xFF1F1B16),
    surfaceVariant = Color(0xFFF0E0CF), onSurfaceVariant = Color(0xFF504539),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFBF2E7),
    surfaceContainer = Color(0xFFF6ECDF), surfaceContainerHigh = Color(0xFFF0E6D9),
    surfaceContainerHighest = Color(0xFFEAE0D3),
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

internal fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Late night listening"
}

// ======================================================================
// Product hero: rendered XM5-style buds floating over a warm glow
// ======================================================================

private fun modeTitle(mode: ListeningMode?, connected: Boolean, ambient: Int): String = when {
    !connected -> "Not connected"
    mode == ListeningMode.NOISE_CANCELLING -> "Noise cancelling"
    mode == ListeningMode.AMBIENT -> "Ambient sound · $ambient"
    mode == ListeningMode.OFF -> "Noise control off"
    else -> "Connected"
}

@Composable
internal fun SolarHero(
    mode: ListeningMode?,
    ambientLevel: Int,
    connected: Boolean,
    model: String,
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
    LaunchedEffect(mode) {
        bump.snapTo(0.96f)
        bump.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 300f))
    }
    val title = modeTitle(mode, connected, ambientLevel)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(colors.surfaceContainerLow)
            .background(
                Brush.radialGradient(
                    listOf(SolarAmber.copy(alpha = glow), Color.Transparent),
                    radius = 700f
                )
            )
            .clickable(enabled = connected, onClickLabel = "Switch noise mode", onClick = onCycle)
            .semantics { contentDescription = "$model. $title" }
    ) {
        Image(
            painter = painterResource(R.drawable.buds_pair),
            contentDescription = null,
            colorFilter = if (connected) null else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.82f)
                .graphicsLayer {
                    translationY = bob * 6.dp.toPx() - 8.dp.toPx()
                    rotationZ = tilt * 1.5f
                    scaleX = bump.value
                    scaleY = bump.value
                    alpha = if (connected) 1f else 0.55f
                }
        )
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(8.dp).clip(CircleShape)
                    .background(if (connected) SolarAmber else colors.outline)
            )
            Spacer(Modifier.width(8.dp))
            Text(model, style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
        }
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            color = colors.onSurface,
            modifier = Modifier.align(Alignment.BottomStart).padding(20.dp).semantics { heading() }
        )
    }
}

// ======================================================================
// Battery insights screen
// ======================================================================

@Composable
internal fun BatteryInsightsScreen(battery: BatteryState) {
    val context = LocalContext.current
    var week by rememberSaveable { mutableStateOf(false) }
    val samples = remember(battery.updatedAt) {
        BatteryHistory.load(context, System.currentTimeMillis() - 7L * 86_400_000)
    }
    val outlook = remember(samples, battery) { BatteryInsights.outlook(samples, battery) }
    val colors = MaterialTheme.colorScheme

    Surface(color = colors.primaryContainer, shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.fillMaxWidth().padding(24.dp)) {
            Text("Listening left", style = MaterialTheme.typography.labelLarge, color = colors.onPrimaryContainer)
            Text(
                outlook.minutesLeft?.let(BatteryInsights::formatMinutes) ?: "--",
                style = MaterialTheme.typography.displayMedium,
                color = colors.onPrimaryContainer
            )
            Text(
                when {
                    outlook.minutesLeft == null -> "Take your buds out to see an estimate."
                    outlook.measured -> "Based on how you listened this week. ${outlook.lowestBud} bud runs out first."
                    else -> "Estimated from the XM5's 8 hour rating until SunPods learns your habits."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onPrimaryContainer
            )
        }
    }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                "History",
                style = MaterialTheme.typography.titleSmall,
                color = colors.primary,
                modifier = Modifier.padding(start = 16.dp).weight(1f).semantics { heading() }
            )
            SingleChoiceSegmentedButtonRow(Modifier.width(180.dp)) {
                SegmentedButton(selected = !week, onClick = { week = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("24 h") }
                SegmentedButton(selected = week, onClick = { week = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("7 days") }
            }
        }
        Spacer(Modifier.height(8.dp))
        Surface(color = colors.surfaceContainer, shape = MaterialTheme.shapes.extraLarge) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                val span = if (week) 7L * 86_400_000 else 86_400_000L
                val now = System.currentTimeMillis()
                val shown = samples.filter { it.time >= now - span }
                if (shown.size < 2) {
                    Text(
                        "Your battery story starts here. SunPods notes a reading every few minutes while your buds are out, and nothing leaves your phone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                } else {
                    BatteryChart(shown, now - span, now, Modifier.fillMaxWidth().height(200.dp))
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Legend("Left", colors.primary, false)
                        Legend("Right", colors.tertiary, false)
                        Legend("Case", colors.outline, true)
                    }
                }
            }
        }
    }
}

@Composable
private fun Legend(label: String, color: Color, dashed: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 18.dp, height = 8.dp)) {
            drawLine(
                color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2),
                strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round,
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(6f, 6f)) else null
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BatteryChart(samples: List<BatterySample>, from: Long, to: Long, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val grid = colors.outlineVariant
    val low = colors.error.copy(alpha = 0.10f)
    val series = listOf(
        Triple({ s: BatterySample -> s.left }, colors.primary, false),
        Triple({ s: BatterySample -> s.right }, colors.tertiary, false),
        Triple({ s: BatterySample -> s.case }, colors.outline, true)
    )
    Canvas(modifier.semantics { contentDescription = "Battery history chart" }) {
        val w = size.width
        val h = size.height
        fun x(t: Long) = ((t - from).toFloat() / (to - from)) * w
        fun y(level: Int) = h - level / 100f * h
        drawRect(low, topLeft = Offset(0f, y(20)), size = androidx.compose.ui.geometry.Size(w, h - y(20)))
        for (level in listOf(25, 50, 75, 100)) {
            drawLine(grid, Offset(0f, y(level)), Offset(w, y(level)), strokeWidth = 1.dp.toPx())
        }
        for ((pick, color, dashed) in series) {
            val path = Path()
            var drawing = false
            var lastTime = 0L
            for (s in samples) {
                val level = pick(s)?.takeIf { it in 1..100 }
                if (level == null || s.time - lastTime > 30L * 60 * 1000) drawing = false
                if (level != null) {
                    if (!drawing) path.moveTo(x(s.time), y(level)) else path.lineTo(x(s.time), y(level))
                    drawing = true
                }
                lastTime = s.time
            }
            drawPath(
                path, color,
                style = Stroke(
                    width = 3.dp.toPx(), cap = StrokeCap.Round,
                    pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(10f, 10f)) else null
                )
            )
        }
    }
}
