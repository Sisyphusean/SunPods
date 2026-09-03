package com.peyi.sonypodsbattery

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.glance.LocalSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.DynamicThemeColorProviders
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.Spacer
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

enum class AudioMode {
    NC,
    AMBIENT,
    OFF;

    companion object {
        fun fromStored(value: String?): AudioMode = entries.firstOrNull { it.name == value } ?: NC
        fun fromListeningMode(value: ListeningMode?): AudioMode = when (value) {
            ListeningMode.NOISE_CANCELLING -> NC
            ListeningMode.AMBIENT -> AMBIENT
            ListeningMode.OFF -> OFF
            null -> NC
        }
    }

    fun toListeningMode(): ListeningMode = when (this) {
        NC -> ListeningMode.NOISE_CANCELLING
        AMBIENT -> ListeningMode.AMBIENT
        OFF -> ListeningMode.OFF
    }
}

private val CURRENT_AUDIO_MODE_KEY = stringPreferencesKey("current_audio_mode")
private val LAST_USER_ACTION_TIME = longPreferencesKey("last_user_action_time")
private val AudioModeKey = ActionParameters.Key<String>("audio_mode_action")
private const val MODE_SYNC_GRACE_PERIOD_MS = 4_000L

class SonyPodsBatteryWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme(colors = DynamicThemeColorProviders) {
                val storedState = BatteryStore.load(context)
                val currentMode = currentState<Preferences>()[CURRENT_AUDIO_MODE_KEY]
                    ?: AudioMode.NC.name
                WidgetContent(storedState, AudioMode.fromStored(currentMode))
            }
        }
    }
}

class SonyPodsBatteryWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SonyPodsBatteryWidget()
}

object SonyPodsBatteryWidgetUpdater {
    fun updateAll(context: Context) {
        CoroutineScope(Dispatchers.Default).launch {
            SonyPodsBatteryWidget().updateAll(context)
        }
    }

    suspend fun syncReportedMode(context: Context, reportedMode: AudioMode) {
        val manager = GlanceAppWidgetManager(context)
        val widget = SonyPodsBatteryWidget()
        val glanceIds = manager.getGlanceIds(SonyPodsBatteryWidget::class.java)
        for (glanceId in glanceIds) {
            val preferences: Preferences = getAppWidgetState(
                context,
                PreferencesGlanceStateDefinition,
                glanceId
            )
            val lastTap = preferences[LAST_USER_ACTION_TIME] ?: 0L
            if (System.currentTimeMillis() - lastTap > MODE_SYNC_GRACE_PERIOD_MS) {
                updateAppWidgetState(context, glanceId) { state ->
                    state[CURRENT_AUDIO_MODE_KEY] = reportedMode.name
                }
                widget.update(context, glanceId)
            }
        }
    }
}

private val labelText = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal)
private val bodyText = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium)
private val statusTitleText = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold)
private val statusSubtitleText = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal)
private val valueText = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold)

@Composable
private fun WidgetContent(state: BatteryState, currentMode: AudioMode) {
    val size = LocalSize.current
    val compactWidth = size.width < 320.dp
    val compactHeight = size.height < 190.dp
    val horizontalPadding = if (compactWidth) 12.dp else 16.dp
    val verticalPadding = if (compactHeight) 10.dp else 14.dp
    val bodyGap = if (compactWidth) 6.dp else 8.dp
    val batteryColumnWidth = (size.width * if (compactWidth) 0.40f else 0.35f)
        .coerceIn(105.dp, 145.dp)

    Box(
        modifier = GlanceModifier.fillMaxWidth()
            .fillMaxHeight()
            .background(GlanceTheme.colors.surface)
            .cornerRadius(28.dp)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding)
    ) {
        Column(modifier = GlanceModifier.fillMaxWidth().fillMaxHeight()) {
            Header(state, compactWidth)
            Spacer(modifier = GlanceModifier.height(bodyGap))
            Row(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = GlanceModifier.width(batteryColumnWidth).fillMaxHeight()
                ) {
                    BatteryCard(
                        label = "Left",
                        level = state.left,
                        charging = state.leftCharging,
                        icon = R.drawable.ic_earbud_left,
                        compactWidth = compactWidth,
                        compactHeight = compactHeight,
                        modifier = GlanceModifier.defaultWeight()
                    )
                    Spacer(modifier = GlanceModifier.height(bodyGap))
                    BatteryCard(
                        label = "Right",
                        level = state.right,
                        charging = state.rightCharging,
                        icon = R.drawable.ic_earbud_right,
                        compactWidth = compactWidth,
                        compactHeight = compactHeight,
                        modifier = GlanceModifier.defaultWeight()
                    )
                    Spacer(modifier = GlanceModifier.height(bodyGap))
                    BatteryCard(
                        label = "Case",
                        level = state.case,
                        charging = state.caseCharging,
                        icon = R.drawable.ic_case,
                        compactWidth = compactWidth,
                        compactHeight = compactHeight,
                        modifier = GlanceModifier.defaultWeight()
                    )
                }
                Column(
                    modifier = GlanceModifier.defaultWeight()
                        .fillMaxHeight()
                        .padding(start = bodyGap)
                ) {
                    ModeTrack(currentMode)
                    Spacer(modifier = GlanceModifier.height(bodyGap))
                    ContextCard(
                        state = state,
                        currentMode = currentMode,
                        compactWidth = compactWidth,
                        compactHeight = compactHeight,
                        modifier = GlanceModifier.fillMaxWidth().defaultWeight()
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(state: BatteryState, compactWidth: Boolean) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = GlanceModifier.size(48.dp)
                .clickable(actionStartActivity<LaunchSonyAppActivity>()),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_sony_wave),
                contentDescription = "Sony Logo",
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
                modifier = GlanceModifier.size(if (compactWidth) 20.dp else 22.dp)
            )
        }
        Spacer(modifier = GlanceModifier.defaultWeight())
        Text(
            text = if (state.updatedAt == 0L) "Not updated" else "Updated ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(state.updatedAt))}",
            style = labelText.copy(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = if (compactWidth) 11.sp else 13.sp
            )
        )
        Spacer(modifier = GlanceModifier.defaultWeight())
        Box(
            modifier = GlanceModifier.size(48.dp)
                .clickable(actionRunCallback<RefreshWidgetAction>()),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_refresh),
                contentDescription = "Refresh",
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
                modifier = GlanceModifier.size(if (compactWidth) 20.dp else 22.dp)
            )
        }
    }
}

@Composable
private fun BatteryCard(
    label: String,
    level: Int?,
    charging: Boolean,
    icon: Int,
    compactWidth: Boolean,
    compactHeight: Boolean,
    modifier: GlanceModifier
) {
    val isDimmed = level == null || level == 0
    val subduedColor = GlanceTheme.colors.onSurfaceVariant
    val labelColor = if (isDimmed) GlanceTheme.colors.outline else subduedColor
    val valueColor = if (isDimmed) GlanceTheme.colors.outline else GlanceTheme.colors.onSurface
    Row(
        modifier = modifier.fillMaxWidth()
            .background(GlanceTheme.colors.surfaceVariant)
            .cornerRadius(16.dp)
            .padding(
                horizontal = if (compactWidth) 8.dp else 12.dp,
                vertical = if (compactHeight) 4.dp else 8.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = label,
            colorFilter = ColorFilter.tint(labelColor),
            modifier = GlanceModifier.size(if (compactWidth) 16.dp else 18.dp)
        )
        Text(
            text = label,
            style = labelText.copy(
                color = labelColor,
                fontSize = if (compactWidth) 11.sp else 12.sp
            ),
            modifier = GlanceModifier.padding(start = if (compactWidth) 5.dp else 8.dp)
        )
        Spacer(modifier = GlanceModifier.defaultWeight())
        Text(
            text = (level?.let { "$it%" } ?: "--") + if (charging) " ⚡" else "",
            style = valueText.copy(
                color = valueColor,
                fontSize = if (compactWidth) 13.sp else 14.sp
            )
        )
    }
}

@Composable
private fun ModeTrack(activeMode: AudioMode) {
    Row(
        modifier = GlanceModifier.fillMaxWidth()
            .background(GlanceTheme.colors.surfaceVariant)
            .cornerRadius(22.dp)
            .height(44.dp)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ModeButton("NC", AudioMode.NC, activeMode, GlanceModifier.defaultWeight())
        ModeButton("Ambient", AudioMode.AMBIENT, activeMode, GlanceModifier.defaultWeight())
        ModeButton("Off", AudioMode.OFF, activeMode, GlanceModifier.defaultWeight())
    }
}

@Composable
private fun ModeButton(label: String, mode: AudioMode, activeMode: AudioMode, modifier: GlanceModifier) {
    val isActive = mode == activeMode
    val colors = GlanceTheme.colors
    Box(
        modifier = modifier
            .cornerRadius(18.dp)
            .background(if (isActive) colors.primary else colors.surfaceVariant)
            .fillMaxHeight()
            .clickable(actionRunCallback<SetListeningModeAction>(actionParametersOf(AudioModeKey.to(mode.name)))),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = labelText.copy(color = if (isActive) colors.onPrimary else colors.onSurfaceVariant)
        )
    }
}

@Composable
private fun ContextCard(
    state: BatteryState,
    currentMode: AudioMode,
    compactWidth: Boolean,
    compactHeight: Boolean,
    modifier: GlanceModifier
) {
    Box(
        modifier = modifier
            .background(GlanceTheme.colors.secondaryContainer)
            .cornerRadius(16.dp)
            .padding(
                horizontal = if (compactWidth) 8.dp else 12.dp,
                vertical = if (compactHeight) 4.dp else 10.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        when (currentMode) {
            AudioMode.AMBIENT -> AmbientControls(state, compactWidth, compactHeight)
            AudioMode.NC -> StatusContent(
                R.drawable.ic_noise_cancelling,
                "Noise Cancelling",
                "Active",
                compactWidth
            )
            AudioMode.OFF -> StatusContent(
                R.drawable.ic_sound_off,
                "Sound Control Off",
                "Passive Isolation",
                compactWidth
            )
        }
    }
}

@Composable
private fun AmbientControls(state: BatteryState, compactWidth: Boolean, compactHeight: Boolean) {
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Ambient level",
                style = labelText.copy(
                    color = GlanceTheme.colors.onSecondaryContainer,
                    fontSize = if (compactWidth) 11.sp else 13.sp
                ),
                modifier = GlanceModifier.defaultWeight()
            )
            Text(
                text = "${state.ambientLevel}",
                style = bodyText.copy(
                    color = GlanceTheme.colors.onSecondaryContainer,
                    fontSize = if (compactWidth) 12.sp else 13.sp
                )
            )
        }
        Row(
            modifier = GlanceModifier.fillMaxWidth()
                .padding(top = if (compactHeight) 0.dp else 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StepButton("−", -1)
            AmbientSteps(state.ambientLevel, compactWidth, GlanceModifier.defaultWeight())
            StepButton("+", 1)
        }
    }
}

@Composable
private fun StepButton(symbol: String, delta: Int) {
    Box(
        modifier = GlanceModifier.size(48.dp)
            .cornerRadius(16.dp)
            .clickable(actionRunCallback<AdjustAmbientAction>(actionParametersOf(AdjustAmbientAction.deltaKey.to(delta)))),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = symbol,
            style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium, color = GlanceTheme.colors.onSurfaceVariant)
        )
    }
}

@Composable
private fun AmbientSteps(level: Int, compactWidth: Boolean, modifier: GlanceModifier) {
    Row(
        modifier = modifier.padding(horizontal = if (compactWidth) 2.dp else 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (step in 1..20) {
            Box(
                modifier = GlanceModifier.defaultWeight().height(3.dp)
                    .cornerRadius(2.dp)
                    .then(if (step <= level) GlanceModifier.background(GlanceTheme.colors.primaryContainer) else GlanceModifier.background(GlanceTheme.colors.onSurfaceVariant))
            ) {}
        }
    }
}

@Composable
private fun StatusContent(icon: Int, title: String, subtitle: String, compactWidth: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = title,
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onSecondaryContainer),
            modifier = GlanceModifier.size(if (compactWidth) 20.dp else 24.dp)
        )
        Column(modifier = GlanceModifier.padding(start = 8.dp)) {
            Text(
                text = title,
                style = statusTitleText.copy(
                    color = GlanceTheme.colors.onSecondaryContainer,
                    fontSize = if (compactWidth) 13.sp else 14.sp
                )
            )
            Text(
                text = subtitle,
                style = statusSubtitleText.copy(
                    color = GlanceTheme.colors.onSecondaryContainer,
                    fontSize = if (compactWidth) 11.sp else 12.sp
                ),
                modifier = GlanceModifier.padding(top = 2.dp)
            )
        }
    }
}

class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        RefreshCoordinator.refresh(context.applicationContext)
        SonyPodsBatteryWidget().update(context, glanceId)
    }
}

class SetListeningModeAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val mode = parameters[AudioModeKey]?.let(AudioMode::fromStored) ?: return
        updateAppWidgetState(context, glanceId) { preferences ->
            preferences[CURRENT_AUDIO_MODE_KEY] = mode.name
            preferences[LAST_USER_ACTION_TIME] = System.currentTimeMillis()
        }
        SonyPodsBatteryWidget().update(context, glanceId)

        val current = BatteryStore.load(context)
        RefreshCoordinator.setListeningMode(context.applicationContext, mode.toListeningMode(), current.ambientLevel, current.focusOnVoice)
        SonyPodsBatteryWidget().update(context, glanceId)
    }
}

class AdjustAmbientAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val delta = parameters[deltaKey] ?: return
        val current = BatteryStore.load(context)
        RefreshCoordinator.setListeningMode(
            context.applicationContext,
            ListeningMode.AMBIENT,
            (current.ambientLevel + delta).coerceIn(1, 20),
            current.focusOnVoice
        )
        SonyPodsBatteryWidget().update(context, glanceId)
    }

    companion object {
        val deltaKey = ActionParameters.Key<Int>("ambient_delta")
    }
}

class ToggleFocusOnVoiceAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val current = BatteryStore.load(context)
        RefreshCoordinator.setListeningMode(
            context.applicationContext,
            ListeningMode.AMBIENT,
            current.ambientLevel,
            !current.focusOnVoice
        )
        SonyPodsBatteryWidget().update(context, glanceId)
    }
}
