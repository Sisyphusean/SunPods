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

