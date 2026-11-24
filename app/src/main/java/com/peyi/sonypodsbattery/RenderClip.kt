package com.peyi.sonypodsbattery

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.widget.ImageView
import androidx.annotation.RawRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
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
    val reduceMotion = rememberReduceMotion()
    var visible by remember(clip) { mutableStateOf(reduceMotion || drawable == null) }
    val alpha by androidx.compose.animation.core.animateFloatAsState(
        if (visible) 1f else 0f,
        if (reduceMotion) androidx.compose.animation.core.snap() else androidx.compose.animation.core.tween(220),
        label = "clipFade"
    )
    DisposableEffect(drawable) {
        val anim = drawable as? AnimatedImageDrawable
        anim?.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
        if (!reduceMotion) anim?.start()
        visible = true
        onDispose { anim?.stop() }
    }
    AndroidView(
        factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.FIT_CENTER; importantForAccessibility = ImageView.IMPORTANT_FOR_ACCESSIBILITY_NO } },
        update = { it.setImageDrawable(drawable) },
        modifier = modifier.graphicsLayer { this.alpha = alpha }.semantics { contentDescription = description }
    )
}

