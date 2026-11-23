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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.snapshotFlow
import kotlin.math.roundToInt
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

internal const val TURNTABLE_FRAMES = 72

/** What the Home hero is showing: the closed case, the buds, or a clip between them. */
internal enum class HeroPhase { CASE, OPENING, BUDS, CLOSING }

/** Frame index (0 until TURNTABLE_FRAMES) for a spin angle in degrees; frame k = k * 5 degrees. */
internal fun turntableFrameFor(angleDeg: Float): Int =
    Math.floorMod((angleDeg / (360f / TURNTABLE_FRAMES)).roundToInt(), TURNTABLE_FRAMES)

/** Decodes turntable frames from assets/turntable on demand, keeping a small LRU of recent ones. */
internal class TurntableFrames(private val context: android.content.Context) {
    private val cache = android.util.LruCache<Int, ImageBitmap>(14)
    private fun decode(i: Int): ImageBitmap? = runCatching {
        context.assets.open("turntable/f%03d.webp".format(i)).use {
            android.graphics.BitmapFactory.decodeStream(it)?.asImageBitmap()
        }
    }.getOrNull()?.also { cache.put(i, it) }

    fun loadNow(i: Int): ImageBitmap? = cache.get(i) ?: decode(i)
    suspend fun load(i: Int): ImageBitmap? = cache.get(i) ?: withContext(Dispatchers.IO) { decode(i) }
}

@Composable
internal fun SolarHero(
    mode: ListeningMode?,
    ambientLevel: Int,
    connected: Boolean,
    model: String,
    color: String = "black",
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

    // Turntable: drag sideways to spin both buds (pre-rendered frames), fling for momentum,
    // and they ease back to the hero pose after a short idle. Tap still cycles noise mode.
    val context = LocalContext.current
    val frames = remember { TurntableFrames(context.applicationContext) }
    val angle = remember { Animatable(0f) }
    var shown by remember { mutableStateOf(frames.loadNow(0)) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val degPerPx = with(LocalDensity.current) { 360f / 320.dp.toPx() }
    var settle by remember { mutableStateOf<Job?>(null) }
    val target = turntableFrameFor(angle.value)
    LaunchedEffect(target) {
        frames.load(target)?.let { shown = it }
        for (d in intArrayOf(1, -1, 2, -2, 3, -3)) frames.load(Math.floorMod(target + d, TURNTABLE_FRAMES))
    }
    fun settleHome(velocity: Float) {
        settle?.cancel()
        settle = scope.launch {
            angle.animateDecay(velocity.coerceIn(-1440f, 1440f), exponentialDecay(frictionMultiplier = 2.2f))
            delay(2500)
            val home = (angle.value / 360f).roundToInt() * 360f
            angle.animateTo(home, spring(dampingRatio = 0.82f, stiffness = 60f))
            angle.snapTo(0f)
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("sunpods_ui", android.content.Context.MODE_PRIVATE)
        val animationsOn = android.provider.Settings.Global.getFloat(
            context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        ) > 0f
        if (animationsOn && !prefs.getBoolean("turntable_hint_shown", false)) {
            delay(900)
            angle.animateTo(-40f, tween(700, easing = FastOutSlowInEasing))
            angle.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 90f))
            prefs.edit().putBoolean("turntable_hint_shown", true).apply()
        }
    }

    // While the earbuds are away the hero shows the closed case. When they connect, the lid opens
    // and the buds fly out to the hero pose; when they disconnect, the buds turn to face front and
    // fly back in as the lid closes. Each clip starts and ends on the exact frame it replaces, so
    // the cuts between clip and turntable are invisible.
    var phase by remember { mutableStateOf(if (connected) HeroPhase.BUDS else HeroPhase.CASE) }
    LaunchedEffect(connected) {
        val animationsOn = android.provider.Settings.Global.getFloat(
            context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        ) > 0f
        // Let a clip in flight finish, so the next one starts from the frame on screen.
        if (withTimeoutOrNull(7000) { snapshotFlow { phase }.first { it == HeroPhase.CASE || it == HeroPhase.BUDS } } == null) {
            phase = if (phase == HeroPhase.OPENING) HeroPhase.BUDS else HeroPhase.CASE
        }
        if (connected && phase == HeroPhase.CASE) {
            if (animationsOn) {
                phase = HeroPhase.OPENING
                withTimeoutOrNull(6000) { snapshotFlow { phase }.first { it == HeroPhase.BUDS } }
            }
            phase = HeroPhase.BUDS
        } else if (!connected && phase == HeroPhase.BUDS) {
            settle?.cancel()
            if (animationsOn) {
                val home = (angle.value / 360f).roundToInt() * 360f
                if (angle.value != home) angle.animateTo(home, tween(380, easing = FastOutSlowInEasing))
                angle.snapTo(0f)
                shown = frames.load(0) ?: shown
                phase = HeroPhase.CLOSING
                withTimeoutOrNull(4000) { snapshotFlow { phase }.first { it == HeroPhase.CASE } }
            }
            angle.snapTo(0f)
            phase = HeroPhase.CASE
        }
    }
    val showCase = phase != HeroPhase.BUDS

    val heroAlpha by animateFloatAsState(
        when {
            connected -> 1f
            phase == HeroPhase.CLOSING -> 1f
            else -> 0.8f
        },
        tween(400), label = "heroAlpha"
    )

    // No card around the hero: the buds float on the page with a soft glow that follows the mode.
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(244.dp)
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
                .pointerInput(Unit) {
                    if (true) return@pointerInput // the live 3D view spins itself
                    val tracker = VelocityTracker()
                    detectHorizontalDragGestures(
                        onDragStart = {
                            if (!showCase) {
                                settle?.cancel()
                                tracker.resetTracking()
                                scope.launch { angle.stop() }
                            }
                        },
                        onHorizontalDrag = { change, dx ->
                            if (!showCase) {
                                change.consume()
                                tracker.addPosition(change.uptimeMillis, change.position)
                                scope.launch { angle.snapTo(angle.value + dx * degPerPx) }
                            }
                        },
                        onDragEnd = { if (!showCase) settleHome(tracker.calculateVelocity().x * degPerPx) },
                        onDragCancel = { if (!showCase) settleHome(0f) }
                    )
                }
                .semantics {
                    if (connected) onClick(label = "Change Noise Control") { onCycle(); true }
                }
                .semantics {
                    contentDescription = "$model. $title"
                    customActions = listOf(CustomAccessibilityAction("Spin earbuds") {
                        settle?.cancel()
                        settle = scope.launch {
                            angle.animateTo(angle.value + 360f, tween(1600, easing = FastOutSlowInEasing))
                            angle.snapTo(0f)
                        }
                        true
                    })
                }
        ) {
            // The connect clip ends on the hero pose itself, so the cut to the interactive buds
            // is invisible: both share one layout and the same float, tilt and bump.
            val heroLayer = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.94f)
                .aspectRatio(1000f / 712f)
                .graphicsLayer {
                    translationY = bob * 5.dp.toPx()
                    rotationZ = tilt * 1.5f
                    scaleX = bump.value
                    scaleY = bump.value
                    alpha = heroAlpha
                }
            val lid = remember { Animatable(if (phase == HeroPhase.CASE) 0f else 1f) }
            LaunchedEffect(phase) {
                when (phase) {
                    HeroPhase.OPENING -> { lid.animateTo(1f, tween(900, easing = FastOutSlowInEasing)); delay(150); phase = HeroPhase.BUDS }
                    HeroPhase.CLOSING -> { lid.snapTo(1f); lid.animateTo(0f, tween(800, easing = FastOutSlowInEasing)); phase = HeroPhase.CASE }
                    HeroPhase.CASE -> lid.snapTo(0f)
                    HeroPhase.BUDS -> lid.snapTo(1f)
                }
            }
            Hero3D(
                color = color,
                model = if (showCase) "case" else "buds",
                lid = lid.value,
                onTap = if (connected && !showCase) onCycle else null,
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
                "Open the case near your phone to connect.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant
            )
        }
    }
}
