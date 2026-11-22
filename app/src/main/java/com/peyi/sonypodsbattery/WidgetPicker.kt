package com.peyi.sonypodsbattery

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The three home screen widgets, smallest first. */
internal enum class WidgetSize(val title: String, val detail: String, val receiver: Class<*>) {
    SMALL("Small", "Battery levels in one line", SunpodsMiniWidgetReceiver::class.java),
    MEDIUM("Medium", "Left, right and case levels", SunpodsMediumWidgetReceiver::class.java),
    LARGE("Large", "Battery levels and noise control buttons", SonyPodsBatteryWidgetReceiver::class.java)
}

internal object WidgetPins {
    fun supported(context: Context) = AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported

    /** Asks the launcher to add [size]; it shows its own confirmation sheet. */
    fun request(context: Context, size: WidgetSize): Boolean = runCatching {
        AppWidgetManager.getInstance(context).requestPinAppWidget(ComponentName(context, size.receiver), null, null)
    }.getOrDefault(false)
}

/** Pick a widget size and add it straight to the home screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WidgetPickerSheet(onDismiss: () -> Unit, onRequested: (WidgetSize) -> Unit = {}) {
    val context = LocalContext.current
    val supported = remember { WidgetPins.supported(context) }
    var asked by remember { mutableStateOf<WidgetSize?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            Text("Add a Widget", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                if (supported) "Choose a size, then confirm on your home screen."
                else "Your home screen doesn't let apps add widgets. Touch and hold an empty spot, tap Widgets, then find SunPods.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            WidgetSize.entries.forEach { size ->
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(96.dp), contentAlignment = Alignment.Center) { MiniPreview(size) }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(size.title, style = MaterialTheme.typography.titleSmall)
                        Text(size.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        androidx.compose.animation.AnimatedVisibility(asked == size, enter = revealIn(), exit = revealOut()) {
                            Text("Check your home screen", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (supported) {
                        Spacer(Modifier.width(12.dp))
                        FilledTonalButton(onClick = {
                            if (WidgetPins.request(context, size)) { asked = size; onRequested(size) }
                        }) { Text("Add") }
                    }
                }
            }
        }
    }
}

/** A tiny drawing of each widget's shape, to tell the sizes apart at a glance. */
@Composable
private fun MiniPreview(size: WidgetSize) {
    val c = MaterialTheme.colorScheme
    val (w, h) = when (size) { WidgetSize.SMALL -> 64.dp to 32.dp; WidgetSize.MEDIUM -> 84.dp to 56.dp; WidgetSize.LARGE -> 96.dp to 56.dp }
    Column(
        Modifier.size(w, h).clip(RoundedCornerShape(10.dp)).background(c.surfaceContainerHighest).padding(5.dp).clearAndSetSemantics { },
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        if (size == WidgetSize.SMALL) {
            Text("80  78  64", fontSize = 9.sp, color = c.onSurface, modifier = Modifier.padding(top = 4.dp, start = 2.dp))
            return@Column
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(3) { Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(5.dp)).background(c.surface)) }
        }
        if (size == WidgetSize.LARGE) {
            Row(Modifier.fillMaxWidth().height(10.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(c.primary))
                Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(c.surface))
                Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(c.surface))
            }
        }
    }
}
