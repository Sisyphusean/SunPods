package com.peyi.sonypodsbattery

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.provider.Settings
import android.widget.ImageView
import androidx.annotation.RawRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Plays one of the rendered 3D clips (an animated WebP in res/raw) on a loop, like the setup
 * animations on Apple devices. With animations turned off on the phone it shows the first frame.
 */
@Composable
internal fun RenderClip(@RawRes clip: Int, description: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val drawable = remember(clip) {
        runCatching { ImageDecoder.decodeDrawable(ImageDecoder.createSource(context.resources, clip)) }.getOrNull()
    }
    val animate = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    DisposableEffect(drawable) {
        val anim = drawable as? AnimatedImageDrawable
        anim?.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
        if (animate) anim?.start()
        onDispose { anim?.stop() }
    }
    AndroidView(
        factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.FIT_CENTER; importantForAccessibility = ImageView.IMPORTANT_FOR_ACCESSIBILITY_NO } },
        update = { it.setImageDrawable(drawable) },
        modifier = modifier.semantics { contentDescription = description }
    )
}

/**
 * Shows the first frame of a clip, and plays it through once when [play] turns true, holding the
 * last frame and then calling [onEnd]. Used by the Home hero for the case opening on connect.
 */
@Composable
internal fun RenderClipOnce(@RawRes clip: Int, play: Boolean, onEnd: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val drawable = remember(clip) {
        runCatching { ImageDecoder.decodeDrawable(ImageDecoder.createSource(context.resources, clip)) }.getOrNull()
    }
    val end = androidx.compose.runtime.rememberUpdatedState(onEnd)
    DisposableEffect(drawable, play) {
        val anim = drawable as? AnimatedImageDrawable
        if (anim == null) {
            if (play) end.value()
            return@DisposableEffect onDispose { }
        }
        anim.repeatCount = 0
        val callback = object : android.graphics.drawable.Animatable2.AnimationCallback() {
            override fun onAnimationEnd(d: android.graphics.drawable.Drawable?) { end.value() }
        }
        if (play) { anim.registerAnimationCallback(callback); anim.start() }
        onDispose { anim.unregisterAnimationCallback(callback); anim.stop() }
    }
    AndroidView(
        factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.FIT_CENTER; importantForAccessibility = ImageView.IMPORTANT_FOR_ACCESSIBILITY_NO } },
        update = { it.setImageDrawable(drawable) },
        modifier = modifier
    )
}
