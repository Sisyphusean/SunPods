package com.peyi.sonypodsbattery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.VisibilityThreshold

/*
 * SunPods' visual language: each row leads with a small tonal icon tile in the theme's colours,
 * rows carry a title and their value, never a paragraph, and anything secondary lives one tap
 * deeper on its own page.
 */

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
internal fun IconTile(icon: ImageVector, enabled: Boolean = true, modifier: Modifier = Modifier) {
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
internal data class BudArt(
    val left: Int, val right: Int, val case: Int, val pair: Int, val hero3d: String,
    /** Setup clips: the case button held until the light flashes blue, and the case opening. */
    val pairClip: Int, val openClip: Int,
    /** The 3D hero's model: "xm5" or "c710n". */
    val device: String = "xm5",
)

/**
 * The pictures for these earbuds, from their model and the colour code they report (Sony's
 * ModelColor; the "-I" codes 0x11 to 0x1e are the same colours). Unknown colours show black.
 *  WF-1000XM5: 0x01 Black, 0x03 Platinum Silver, 0x06 Smoky Pink.
 *  WF-C710N: 0x01 Black, 0x02 White, 0x05 Glass Blue, 0x06 Pink.
 */
internal fun budArt(colorCode: Int?, model: EarbudModel = EarbudModel.active): BudArt {
    val c = colorCode?.let { if (it in 0x11..0x1e) it - 0x10 else it }
    return when (model) {
        EarbudModel.WF_C710N -> c710nArt(c)
        EarbudModel.WF_1000XM5 -> when (c) {
            0x03 -> BudArt(R.drawable.bud_left_silver, R.drawable.bud_right_silver, R.drawable.bud_case_silver, R.drawable.buds_pair_silver, "silver", R.raw.anim_pair_silver, R.raw.anim_open_silver)
            0x06 -> BudArt(R.drawable.bud_left_pink, R.drawable.bud_right_pink, R.drawable.bud_case_pink, R.drawable.buds_pair_pink, "pink", R.raw.anim_pair_pink, R.raw.anim_open_pink)
            else -> BudArt(R.drawable.bud_left, R.drawable.bud_right, R.drawable.bud_case, R.drawable.buds_pair, "black", R.raw.anim_pair, R.raw.anim_open)
        }
    }
}

internal fun budArt(settings: DeviceSettings): BudArt = budArt(settings.colorCode, EarbudModel.fromName(settings.modelName) ?: EarbudModel.active)

/** WF-C710N finishes; the 3D hero names them black, white, blue and pink. */
private fun c710nArt(c: Int?): BudArt = when (c) {
    0x02 -> BudArt(R.drawable.c710n_bud_left_white, R.drawable.c710n_bud_right_white, R.drawable.c710n_bud_case_white, R.drawable.c710n_buds_pair_white, "white", R.raw.c710n_anim_pair_white, R.raw.c710n_anim_open_white, device = "c710n")
    0x05 -> BudArt(R.drawable.c710n_bud_left_blue, R.drawable.c710n_bud_right_blue, R.drawable.c710n_bud_case_blue, R.drawable.c710n_buds_pair_blue, "blue", R.raw.c710n_anim_pair_blue, R.raw.c710n_anim_open_blue, device = "c710n")
    0x06 -> BudArt(R.drawable.c710n_bud_left_pink, R.drawable.c710n_bud_right_pink, R.drawable.c710n_bud_case_pink, R.drawable.c710n_buds_pair_pink, "pink", R.raw.c710n_anim_pair_pink, R.raw.c710n_anim_open_pink, device = "c710n")
    else -> BudArt(R.drawable.c710n_bud_left_black, R.drawable.c710n_bud_right_black, R.drawable.c710n_bud_case_black, R.drawable.c710n_buds_pair_black, "black", R.raw.c710n_anim_pair_black, R.raw.c710n_anim_open_black, device = "c710n")
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
