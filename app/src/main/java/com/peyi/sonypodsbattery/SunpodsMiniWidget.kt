package com.peyi.sonypodsbattery

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.DynamicThemeColorProviders
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle

/** Compact 2×1 battery widget. Tap a value to refresh, tap the title to open Sunpods. */
class SunpodsMiniWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme(colors = DynamicThemeColorProviders) {
                MiniContent(BatteryStore.load(context))
            }
        }
    }
}

class SunpodsMiniWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SunpodsMiniWidget()
}

@Composable
private fun MiniContent(state: BatteryState) {
    val colors = GlanceTheme.colors
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(colors.widgetBackground)
            .cornerRadius(24.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = when (state.listeningMode) {
                ListeningMode.NOISE_CANCELLING -> "Sunpods · NC"
                ListeningMode.AMBIENT -> "Sunpods · Ambient"
                ListeningMode.OFF -> "Sunpods · Off"
                null -> "Sunpods"
            },
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.onSurfaceVariant),
            modifier = GlanceModifier.clickable(actionStartActivity<MainActivity>())
        )
        Spacer(GlanceModifier.height(6.dp))
        Row(
            modifier = GlanceModifier.fillMaxWidth().clickable(actionRunCallback<RefreshWidgetAction>()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MiniLevel("L", state.left, state.leftCharging, GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.width(8.dp))
            MiniLevel("R", state.right, state.rightCharging, GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.width(8.dp))
            MiniLevel("Case", state.case, state.caseCharging, GlanceModifier.defaultWeight())
        }
    }
}

@Composable
private fun MiniLevel(label: String, level: Int?, charging: Boolean, modifier: GlanceModifier) {
    val colors = GlanceTheme.colors
    Column(modifier = modifier) {
        Text(
            text = (level?.let { "$it%" } ?: "–") + if (charging) " ⚡" else "",
            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.onSurface),
            maxLines = 1
        )
        Text(label, style = TextStyle(fontSize = 11.sp, color = colors.onSurfaceVariant), maxLines = 1)
        Spacer(GlanceModifier.height(4.dp))
        LinearProgressIndicator(
            progress = (level ?: 0) / 100f,
            modifier = GlanceModifier.fillMaxWidth().height(4.dp),
            color = if ((level ?: 100) <= 20) colors.error else colors.primary,
            backgroundColor = colors.surfaceVariant
        )
    }
}
