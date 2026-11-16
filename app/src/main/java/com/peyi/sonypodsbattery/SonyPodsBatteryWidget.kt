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
import androidx.glance.appwidget.LinearProgressIndicator
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
import androidx.glance.layout.fillMaxSize
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
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import android.text.format.DateFormat
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
private val DESIRED_AMBIENT_LEVEL_KEY = intPreferencesKey("desired_ambient_level")
private val PENDING_AMBIENT_LEVEL_KEY = intPreferencesKey("pending_ambient_level")
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
                val preferences = currentState<Preferences>()
                val currentMode = preferences[CURRENT_AUDIO_MODE_KEY] ?: AudioMode.NC.name
                val desiredAmbientLevel = preferences[DESIRED_AMBIENT_LEVEL_KEY]
                    ?: storedState.ambientLevel
                WidgetContent(
                    context = context,
                    state = storedState,
                    currentMode = AudioMode.fromStored(currentMode),
                    ambientLevel = desiredAmbientLevel
                )
            }
        }
    }
}

class SonyPodsBatteryWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SonyPodsBatteryWidget()
}

object SonyPodsBatteryWidgetUpdater {
    suspend fun updateAll(context: Context) {
        SonyPodsBatteryWidget().updateAll(context)
        SunpodsMiniWidget().updateAll(context)
        SunpodsTileService.requestUpdate(context)
    }

    suspend fun syncReportedMode(context: Context, reportedMode: AudioMode): Boolean {
        val manager = GlanceAppWidgetManager(context)
        val glanceIds = manager.getGlanceIds(SonyPodsBatteryWidget::class.java)
        var changed = false
        for (glanceId in glanceIds) {
            val preferences: Preferences = getAppWidgetState(
                context,
                PreferencesGlanceStateDefinition,
                glanceId
            )
            val lastTap = preferences[LAST_USER_ACTION_TIME] ?: 0L
            if (System.currentTimeMillis() - lastTap > MODE_SYNC_GRACE_PERIOD_MS) {
                val currentMode = preferences[CURRENT_AUDIO_MODE_KEY] ?: AudioMode.NC.name
                if (currentMode != reportedMode.name) {
                    updateAppWidgetState(context, glanceId) { state ->
                        state[CURRENT_AUDIO_MODE_KEY] = reportedMode.name
                    }
                    changed = true
                }
            }
        }
        return changed
    }

    suspend fun syncConfirmedState(
        context: Context,
        confirmedState: BatteryState,
        requestedAtMillis: Long
    ) {
        SunpodsMiniWidget().updateAll(context)
        SunpodsTileService.requestUpdate(context)
        val manager = GlanceAppWidgetManager(context)
        val widget = SonyPodsBatteryWidget()
        val glanceIds = manager.getGlanceIds(SonyPodsBatteryWidget::class.java)
        val confirmedMode = AudioMode.fromListeningMode(confirmedState.listeningMode).name
        for (glanceId in glanceIds) {
            val preferences: Preferences = getAppWidgetState(
                context,
                PreferencesGlanceStateDefinition,
                glanceId
            )
            val lastTap = preferences[LAST_USER_ACTION_TIME] ?: 0L
            if (lastTap > requestedAtMillis) continue

            val currentMode = preferences[CURRENT_AUDIO_MODE_KEY] ?: AudioMode.NC.name
            val currentAmbientLevel = preferences[DESIRED_AMBIENT_LEVEL_KEY]
                ?: confirmedState.ambientLevel
            val shouldRedraw = currentMode != confirmedMode ||
                currentAmbientLevel != confirmedState.ambientLevel
            if (shouldRedraw || lastTap != 0L) {
                updateAppWidgetState(context, glanceId) { state ->
                    state[CURRENT_AUDIO_MODE_KEY] = confirmedMode
                    state[DESIRED_AMBIENT_LEVEL_KEY] = confirmedState.ambientLevel
                    state.remove(PENDING_AMBIENT_LEVEL_KEY)
                    state[LAST_USER_ACTION_TIME] = 0L
                }
                if (shouldRedraw) widget.update(context, glanceId)
            }
        }
    }
}

// ---------- Layout (Android widget quality guidelines: theme colours, 48dp targets, no truncation) ----------

private val numberText = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Medium)
private val smallText = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium)
private val titleText = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium)

private fun usable(level: Int?): Int? = level?.takeIf { it in 1..100 }

@Composable
private fun WidgetContent(
    context: Context,
    state: BatteryState,
    currentMode: AudioMode,
    ambientLevel: Int
) {
    val size = LocalSize.current
    val wide = size.width >= 290.dp
    val tall = size.height >= 190.dp
    val colors = GlanceTheme.colors
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(colors.widgetBackground)
            .cornerRadius(24.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Header(state, context)
        Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            BatteryTile("Left", usable(state.left), state.leftCharging, GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.width(6.dp))
            BatteryTile("Right", usable(state.right), state.rightCharging, GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.width(6.dp))
            BatteryTile("Case", usable(state.case), state.caseCharging, GlanceModifier.defaultWeight())
        }
        Spacer(GlanceModifier.height(6.dp))
        Row(modifier = GlanceModifier.fillMaxWidth().height(48.dp)) {
            ModeButton(R.drawable.ic_mode_nc, "Noise cancelling", "ANC", AudioMode.NC, currentMode, wide, GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.width(6.dp))
            ModeButton(R.drawable.ic_mode_ambient, "Ambient sound", "Ambient", AudioMode.AMBIENT, currentMode, wide, GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.width(6.dp))
            ModeButton(R.drawable.ic_mode_off, "Noise control off", "Off", AudioMode.OFF, currentMode, wide, GlanceModifier.defaultWeight())
        }
        if (currentMode == AudioMode.AMBIENT && tall) {
            Spacer(GlanceModifier.height(6.dp))
            AmbientStepper(ambientLevel)
        }
    }
}

@Composable
private fun Header(state: BatteryState, context: Context) {
    val colors = GlanceTheme.colors
    val timestamp = state.lastCheckedAt.takeIf { it != 0L } ?: state.updatedAt
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(44.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = GlanceModifier.defaultWeight().fillMaxHeight()
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_sunpods_mark),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.primary),
                modifier = GlanceModifier.size(20.dp)
            )
            Text(
                text = "SunPods",
                style = titleText.copy(color = colors.onSurface),
                modifier = GlanceModifier.padding(start = 8.dp)
            )
        }
        Row(
            modifier = GlanceModifier.fillMaxHeight().clickable(actionRunCallback<RefreshWidgetAction>()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (timestamp != 0L) {
                Text(
                    text = DateFormat.getTimeFormat(context).format(Date(timestamp)),
                    style = smallText.copy(color = colors.onSurfaceVariant)
                )
            }
            Box(modifier = GlanceModifier.size(44.dp), contentAlignment = Alignment.Center) {
                Image(
                    provider = ImageProvider(R.drawable.ic_refresh),
                    contentDescription = "Refresh",
                    colorFilter = ColorFilter.tint(colors.onSurfaceVariant),
                    modifier = GlanceModifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun BatteryTile(label: String, level: Int?, charging: Boolean, modifier: GlanceModifier) {
    val colors = GlanceTheme.colors
    val low = level != null && level <= 20
    Column(
        modifier = modifier.fillMaxHeight()
            .background(colors.secondaryContainer)
            .cornerRadius(16.dp)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .clickable(actionRunCallback<RefreshWidgetAction>()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = level?.let { "$it" } ?: "--",
                style = numberText.copy(color = if (low) colors.error else colors.onSecondaryContainer),
                maxLines = 1
            )
            if (level != null) {
                Text("%", style = smallText.copy(color = colors.onSecondaryContainer))
            }
        }
        Text(
            text = if (charging) "$label · charging" else label,
            style = smallText.copy(color = colors.onSecondaryContainer),
            maxLines = 1
        )
        Spacer(GlanceModifier.height(6.dp))
        LinearProgressIndicator(
            progress = (level ?: 0) / 100f,
            modifier = GlanceModifier.fillMaxWidth().height(4.dp),
            color = if (low) colors.error else colors.primary,
            backgroundColor = colors.surfaceVariant
        )
    }
}

@Composable
private fun ModeButton(
    icon: Int,
    description: String,
    label: String,
    mode: AudioMode,
    activeMode: AudioMode,
    showLabel: Boolean,
    modifier: GlanceModifier
) {
    val active = mode == activeMode
    val colors = GlanceTheme.colors
    val content = if (active) colors.onPrimary else colors.onSurfaceVariant
    Row(
        modifier = modifier.fillMaxHeight()
            .background(if (active) colors.primary else colors.surfaceVariant)
            .cornerRadius(24.dp)
            .clickable(actionRunCallback<SetListeningModeAction>(actionParametersOf(AudioModeKey.to(mode.name)))),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = description,
            colorFilter = ColorFilter.tint(content),
            modifier = GlanceModifier.size(20.dp)
        )
        if (showLabel) {
            Text(
                text = label,
                style = smallText.copy(color = content),
                maxLines = 1,
                modifier = GlanceModifier.padding(start = 6.dp)
            )
        }
    }
}

@Composable
private fun AmbientStepper(ambientLevel: Int) {
    val colors = GlanceTheme.colors
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(44.dp)
            .background(colors.secondaryContainer)
            .cornerRadius(22.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StepButton("−", -1)
        Column(modifier = GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Ambient $ambientLevel", style = smallText.copy(color = colors.onSecondaryContainer))
            Spacer(GlanceModifier.height(4.dp))
            LinearProgressIndicator(
                progress = ambientLevel.coerceIn(1, 20) / 20f,
                modifier = GlanceModifier.fillMaxWidth().height(4.dp),
                color = colors.primary,
                backgroundColor = colors.surfaceVariant
            )
        }
        StepButton("+", 1)
    }
}

@Composable
private fun StepButton(symbol: String, delta: Int) {
    Box(
        modifier = GlanceModifier.size(48.dp, 44.dp)
            .clickable(actionRunCallback<AdjustAmbientAction>(actionParametersOf(AdjustAmbientAction.deltaKey.to(delta)))),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = symbol,
            style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium, color = GlanceTheme.colors.onSecondaryContainer)
        )
    }
}

class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        RefreshCoordinator.enqueueRefresh(context.applicationContext)
        SonyPodsBatteryWidget().update(context, glanceId)
    }
}

class SetListeningModeAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val mode = parameters[AudioModeKey]?.let(AudioMode::fromStored) ?: return
        val app = context.applicationContext
        val current = BatteryStore.load(app)
        val preferences = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
        val ambientLevel = preferences[PENDING_AMBIENT_LEVEL_KEY]
            ?: preferences[DESIRED_AMBIENT_LEVEL_KEY]
            ?: current.ambientLevel
        val actionTime = nextUserActionTime(preferences)
        updateAppWidgetState(context, glanceId) { preferences ->
            preferences[LAST_USER_ACTION_TIME] = actionTime
        }
        SonyPodsBatteryWidget().update(context, glanceId)

        RefreshCoordinator.enqueueSetListeningMode(
            app,
            mode.toListeningMode(),
            ambientLevel,
            current.focusOnVoice,
            actionTime
        )
    }
}

class AdjustAmbientAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val delta = parameters[deltaKey] ?: return
        val app = context.applicationContext
        val current = BatteryStore.load(app)
        val preferences = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
        val currentLevel = preferences[PENDING_AMBIENT_LEVEL_KEY]
            ?: preferences[DESIRED_AMBIENT_LEVEL_KEY]
            ?: current.ambientLevel
        val ambientLevel = (currentLevel + delta).coerceIn(1, 20)
        val actionTime = nextUserActionTime(preferences)
        updateAppWidgetState(context, glanceId) { state ->
            state[PENDING_AMBIENT_LEVEL_KEY] = ambientLevel
            state[LAST_USER_ACTION_TIME] = actionTime
        }
        SonyPodsBatteryWidget().update(context, glanceId)
        RefreshCoordinator.enqueueAmbientLevel(
            app,
            ambientLevel,
            current.focusOnVoice,
            actionTime
        )
    }

    companion object {
        val deltaKey = ActionParameters.Key<Int>("ambient_delta")
    }
}

class ToggleFocusOnVoiceAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val app = context.applicationContext
        val current = BatteryStore.load(app)
        RefreshCoordinator.enqueueSetListeningMode(
            app,
            ListeningMode.AMBIENT,
            current.ambientLevel,
            !current.focusOnVoice,
            System.currentTimeMillis()
        )
    }
}

private fun nextUserActionTime(preferences: Preferences): Long = maxOf(
    System.currentTimeMillis(),
    (preferences[LAST_USER_ACTION_TIME] ?: 0L) + 1L
)
