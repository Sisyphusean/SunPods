package com.peyi.sonypodsbattery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.VisibilityThreshold

/*
 * SunPods' visual language, after Apple's Human Interface Guidelines for settings:
 * each row leads with a small rounded tile whose colour says what kind of setting it is, rows
 * carry a title and their value, never a paragraph, and anything secondary lives one tap deeper
 * on its own page.
 */

/** What a setting is about; each kind has its own tile colour, the same everywhere in the app. */
internal enum class Kind(val light: Long, val dark: Long) {
    SOUND(0xFFFF2D55, 0xFFFF375F),      // pink: what you hear
    NOISE(0xFF5856D6, 0xFF5E5CE6),      // indigo: noise control and modes
    CONNECTION(0xFF007AFF, 0xFF0A84FF), // blue: Bluetooth, devices
    CALLS(0xFF34C759, 0xFF30D158),      // green: calls and the mic
    VOICE(0xFFAF52DE, 0xFFBF5AF2),      // purple: voice, prompts, quiet hours
    SAFETY(0xFFFF3B30, 0xFFFF453A),     // red: hearing and safety
    AUTO(0xFFFF9500, 0xFFFF9F0A),       // orange: automations
    PLACE(0xFF32ADE6, 0xFF64D2FF),      // teal: location and sensors
    POWER(0xFF30B0C7, 0xFF40C8E0),      // cyan: power and battery
    STYLE(0xFFFFCC00, 0xFFFFD60A),      // yellow: appearance
    SYSTEM(0xFF8E8E93, 0xFF98989D);     // grey: data, updates, help

    @Composable
    fun color(): Color = Color(if (MaterialTheme.colorScheme.isLight) light else dark)
}

/** The kind of setting an icon stands for, so every existing row gets its colour without changes. */
internal fun kindOf(icon: ImageVector): Kind {
    val n = icon.name.substringAfterLast('.')
    return when (n) {
        "AutoAwesome", "GraphicEq", "Equalizer", "Tune", "VolumeUp", "VolumeDown" -> Kind.SOUND
        "NoiseAware", "NoiseControlOff", "Headphones", "TouchApp" -> Kind.NOISE
        "Bluetooth", "BluetoothAudio", "BluetoothSearching", "Devices", "SwapHoriz", "Earbuds", "Smartphone", "Laptop", "Tablet", "Tv", "Watch", "PhoneAndroid" -> Kind.CONNECTION
        "Call", "Mic", "Gesture" -> Kind.CALLS
        "RecordVoiceOver", "Campaign", "Bedtime", "NightsStay", "WbSunny" -> Kind.VOICE
        "HealthAndSafety", "Hearing" -> Kind.SAFETY
        "AutoMode", "DirectionsWalk", "DirectionsRun", "DoNotDisturbOn", "Wifi", "PlayCircle", "Timer" -> Kind.AUTO
        "LocationOn", "LocationOff", "Sensors", "Directions" -> Kind.PLACE
        "PowerSettingsNew", "BatteryStd", "BatteryChargingFull", "BatteryAlert", "Bolt" -> Kind.POWER
        "Palette", "Widgets" -> Kind.STYLE
        else -> Kind.SYSTEM
    }
}

internal val TileSize: Dp = 38.dp

/**
 * Material 3 Expressive's "cookie": a circle with soft scallops. Gives the icons some character
 * while staying inside Material's own shape library.
 */
internal class CookieShape(private val scallops: Int = 9, private val depth: Float = 0.07f) : androidx.compose.ui.graphics.Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density
    ): androidx.compose.ui.graphics.Outline {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = minOf(cx, cy) / (1f + depth)
        val path = androidx.compose.ui.graphics.Path()
        val steps = 180
        for (i in 0..steps) {
            val t = (i.toFloat() / steps) * (2 * Math.PI).toFloat()
            val rr = r * (1f + depth * kotlin.math.cos(scallops * t))
            val x = cx + rr * kotlin.math.cos(t)
            val y = cy + rr * kotlin.math.sin(t)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return androidx.compose.ui.graphics.Outline.Generic(path)
    }
}

private val IconShape = CookieShape()

/**
 * A settings icon, Material You: the glyph in the theme's on-container colour on its tonal
 * container, so every icon follows the wallpaper (or SunPods' own palette). Dimmed when the
 * setting can't be used.
 */
@Composable
internal fun IconTile(icon: ImageVector, @Suppress("UNUSED_PARAMETER") kind: Kind = kindOf(icon), enabled: Boolean = true, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(TileSize)
            .clip(IconShape)
            .background(if (enabled) scheme.secondaryContainer else scheme.surfaceContainerHighest)
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) scheme.onSecondaryContainer else scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

/*
 * Motion. One rule: nothing animates just because a screen appeared. Numbers you came to read are
 * there at once; motion is kept for change (a new reading, a mode you picked, a section opening)
 * so it tells you what just happened instead of making you wait.
 */
internal object Motion {
    /** Material 3 emphasized decelerate: things arriving. */
    val Arrive = androidx.compose.animation.core.CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    /** Material 3 emphasized accelerate: things leaving. */
    val Leave = androidx.compose.animation.core.CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
}

/** A section opening under the control that revealed it: unfolds from its top edge, then its content fades up. */
internal fun revealIn(): androidx.compose.animation.EnterTransition =
    androidx.compose.animation.expandVertically(
        androidx.compose.animation.core.spring(
            dampingRatio = 1f,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
            visibilityThreshold = androidx.compose.ui.unit.IntSize.VisibilityThreshold
        ),
        expandFrom = Alignment.Top
    ) + androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220, delayMillis = 60))

/** The reverse of [revealIn]: content fades first, then the space folds up into the control above. */
internal fun revealOut(): androidx.compose.animation.ExitTransition =
    androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(90)) +
        androidx.compose.animation.shrinkVertically(
            androidx.compose.animation.core.tween(220, easing = Motion.Leave),
            shrinkTowards = Alignment.Top
        )

/** One state of a sheet or card giving way to the next: fade through, with the container resizing smoothly. */
internal fun <S> androidx.compose.animation.AnimatedContentTransitionScope<S>.fadeThrough(): androidx.compose.animation.ContentTransform =
    (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(210, delayMillis = 90)) +
        androidx.compose.animation.scaleIn(androidx.compose.animation.core.tween(210, delayMillis = 90, easing = Motion.Arrive), initialScale = 0.96f))
        .togetherWith(androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(90)))
        .using(androidx.compose.animation.SizeTransform(clip = false) { _, _ ->
            androidx.compose.animation.core.spring(
                stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
                visibilityThreshold = androidx.compose.ui.unit.IntSize.VisibilityThreshold
            )
        })

/** Haptics that match what happened, using the platform's own patterns where the phone has them. */
internal enum class Buzz { TICK, ON, OFF, CONFIRM, REJECT }

@Composable
internal fun rememberBuzz(): (Buzz) -> Unit {
    val view = androidx.compose.ui.platform.LocalView.current
    return androidx.compose.runtime.remember(view) {
        { b: Buzz ->
            val sdk = android.os.Build.VERSION.SDK_INT
            val c = when (b) {
                Buzz.TICK -> if (sdk >= 34) android.view.HapticFeedbackConstants.SEGMENT_TICK else android.view.HapticFeedbackConstants.CLOCK_TICK
                Buzz.ON -> if (sdk >= 34) android.view.HapticFeedbackConstants.TOGGLE_ON else android.view.HapticFeedbackConstants.CLOCK_TICK
                Buzz.OFF -> if (sdk >= 34) android.view.HapticFeedbackConstants.TOGGLE_OFF else android.view.HapticFeedbackConstants.CLOCK_TICK
                Buzz.CONFIRM -> if (sdk >= 30) android.view.HapticFeedbackConstants.CONFIRM else android.view.HapticFeedbackConstants.CONTEXT_CLICK
                Buzz.REJECT -> if (sdk >= 30) android.view.HapticFeedbackConstants.REJECT else android.view.HapticFeedbackConstants.LONG_PRESS
            }
            view.performHapticFeedback(c)
        }
    }
}

/** The earbud and case pictures in the colour of the user's own earbuds (Sony's colour codes). */
internal data class BudArt(val left: Int, val right: Int, val case: Int, val pair: Int)

internal fun budArt(colorCode: Int?): BudArt = when (colorCode) {
    0x03 -> BudArt(R.drawable.bud_left_silver, R.drawable.bud_right_silver, R.drawable.bud_case_silver, R.drawable.buds_pair_silver)
    0x06 -> BudArt(R.drawable.bud_left_pink, R.drawable.bud_right_pink, R.drawable.bud_case_pink, R.drawable.buds_pair_pink)
    else -> BudArt(R.drawable.bud_left, R.drawable.bud_right, R.drawable.bud_case, R.drawable.buds_pair)
}

/** The current earbuds' pictures; provided by the app shell from the colour the earbuds report. */
internal val LocalBudArt = staticCompositionLocalOf { budArt(null) }

/** Pages one tap deeper than a tab, so each tab stays short. */
internal enum class Page(val title: String) {
    AUTOMATIONS("Automations"),
    SPEAK_TO_CHAT("Speak-to-Chat"),
    WEARING("Wearing Detection"),
    VOICE_PROMPTS("Voice Prompts"),
    CALLS("Calls"),
    CONNECTION("Connection"),
    SAFE_LISTENING("Safe Listening"),
    FIND("Find My Earbuds"),
    APPEARANCE("Appearance"),
    DATA("Your Data"),
    UPDATES("Updates"),
    HELP("Help and About"),
    BATTERY_HEALTH("Battery Health"),
    BATTERY_WEEK("This Week"),
    BATTERY_ALERTS("Low Battery Alerts")
}

/** Opens a [Page]; provided by the app shell. */
internal val LocalOpenPage = staticCompositionLocalOf<(Page) -> Unit> { {} }
