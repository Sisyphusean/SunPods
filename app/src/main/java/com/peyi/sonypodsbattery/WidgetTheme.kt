package com.peyi.sonypodsbattery

import android.content.Context
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.glance.GlanceTheme
import androidx.glance.color.ColorProvider
import androidx.glance.material3.ColorProviders
import androidx.glance.unit.ColorProvider
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Widget surfaces that mirror the app: a tinted page with clean cards on it. */
internal data class WidgetPalette(val page: ColorProvider, val card: ColorProvider, val track: ColorProvider)

/** Same choice as the app: SunPods amber, or Material You colours when "Match wallpaper colours" is on. */
internal fun widgetUsesDynamicColor(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        context.getSharedPreferences("ui", Context.MODE_PRIVATE).getBoolean("dynamic_color", false)

@Composable
internal fun SunpodsGlanceTheme(context: Context, content: @Composable (WidgetPalette) -> Unit) {
    val dynamic = widgetUsesDynamicColor(context)
    val light = if (dynamic) dynamicLightColorScheme(context) else SolarLight
    val dark = if (dynamic) dynamicDarkColorScheme(context) else SolarDark
    GlanceTheme(colors = ColorProviders(light = light, dark = dark)) {
        content(
            WidgetPalette(
                page = ColorProvider(day = light.surfaceContainer, night = dark.surface),
                card = ColorProvider(day = light.surfaceContainerLowest, night = dark.surfaceContainerHigh),
                track = ColorProvider(day = light.surfaceContainerHighest, night = dark.surfaceContainerHighest)
            )
        )
    }
}

/**
 * Widgets can't run timers, so after a tap we schedule one redraw a little later. It clears a
 * "working" spinner if the earbuds never answered.
 */
class WidgetRedrawWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        SonyPodsBatteryWidgetUpdater.updateAll(applicationContext)
        return Result.success()
    }

    companion object {
        fun schedule(context: Context, seconds: Long) {
            WorkManager.getInstance(context).enqueue(
                OneTimeWorkRequestBuilder<WidgetRedrawWorker>().setInitialDelay(seconds, TimeUnit.SECONDS).build()
            )
        }
    }
}
