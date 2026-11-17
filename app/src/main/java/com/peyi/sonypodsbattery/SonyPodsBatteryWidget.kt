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
import androidx.glance.appwidget.CircularProgressIndicator
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
private val REFRESH_STARTED = longPreferencesKey("refresh_started")
internal val REDRAW_AT = longPreferencesKey("redraw_at")
private val PENDING_MODE_KEY = stringPreferencesKey("pending_mode")
private val PENDING_MODE_AT = longPreferencesKey("pending_mode_at")
private const val WORKING_TIMEOUT_MS = 15_000L
private val AudioModeKey = ActionParameters.Key<String>("audio_mode_action")
private const val MODE_SYNC_GRACE_PERIOD_MS = 4_000L

open class SonyPodsBatteryWidget : GlanceAppWidget() {
    /** Noise control buttons under the levels. The medium widget is levels only. */
    protected open val controls = true

    override val stateDefinition = PreferencesGlanceStateDefinition
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            SunpodsGlanceTheme(context) { palette ->
                val snapshot = widgetSnapshot(context)
                val storedState = snapshot.state
                val preferences = currentState<Preferences>()
                preferences[REDRAW_AT]
                val currentMode = preferences[CURRENT_AUDIO_MODE_KEY] ?: AudioMode.NC.name
                val desiredAmbientLevel = preferences[DESIRED_AMBIENT_LEVEL_KEY]
                    ?: storedState.ambientLevel
                val now = System.currentTimeMillis()
                val started = preferences[REFRESH_STARTED] ?: 0L
                val refreshing = now - started < WORKING_TIMEOUT_MS &&
                    maxOf(storedState.updatedAt, storedState.lastCheckedAt) < started
                val pending = preferences[PENDING_MODE_KEY]
                    ?.takeIf { now - (preferences[PENDING_MODE_AT] ?: 0L) < WORKING_TIMEOUT_MS }
                    ?.let(AudioMode::fromStored)
                WidgetContent(
                    context = context,
                    state = storedState,
                    currentMode = pending ?: AudioMode.fromStored(currentMode),
                    pendingMode = pending,
                    ambientLevel = desiredAmbientLevel,
                    refreshing = refreshing,
                    palette = palette,
                    snapshot = snapshot,
                    controls = controls
                )
            }
        }
    }
}

class SonyPodsBatteryWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SonyPodsBatteryWidget()
}

/** Medium: the large widget's header and levels, without the noise control row. */
class SunpodsMediumWidget : SonyPodsBatteryWidget() {
    override val controls = false
}

class SunpodsMediumWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SunpodsMediumWidget()
}

object SonyPodsBatteryWidgetUpdater {
    suspend fun updateAll(context: Context) {
        redraw(context, SonyPodsBatteryWidget::class.java) { SonyPodsBatteryWidget() }
        redraw(context, SunpodsMediumWidget::class.java) { SunpodsMediumWidget() }
        redraw(context, SunpodsMiniWidget::class.java) { SunpodsMiniWidget() }
        SunpodsTileService.requestUpdate(context)
    }

    /**
     * Glance only recomposes a running widget when its own state changes, so new battery data in
     * BatteryStore alone left the widget frozen (stuck on "Updating"). Bump a key to force it.
     */
    private suspend fun <T : GlanceAppWidget> redraw(context: Context, type: Class<T>, make: () -> T) {
        val widget = make()
        val now = System.currentTimeMillis()
        for (id in GlanceAppWidgetManager(context).getGlanceIds(type)) {
            runCatching {
                updateAppWidgetState(context, id) { it[REDRAW_AT] = now }
                widget.update(context, id)
            }
        }
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
                    state.remove(PENDING_MODE_KEY)
                    state[LAST_USER_ACTION_TIME] = 0L
                }
                widget.update(context, glanceId)
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
    pendingMode: AudioMode?,
    ambientLevel: Int,
    refreshing: Boolean,
    palette: WidgetPalette,
    snapshot: WidgetSnapshot,
    controls: Boolean = true
) {
    val size = LocalSize.current
    val wide = size.width >= 290.dp
    val tall = size.height >= 190.dp
    val colors = GlanceTheme.colors
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(palette.page)
            .cornerRadius(24.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Header(state, context, refreshing, snapshot.connected)
        if (!snapshot.connected) {
            NotConnectedBody(snapshot.last, palette, GlanceModifier.fillMaxWidth().defaultWeight())
            return@Column
        }
        Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            BatteryTile("Left", usable(state.left), state.leftCharging, palette, GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.width(6.dp))
            BatteryTile("Right", usable(state.right), state.rightCharging, palette, GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.width(6.dp))
            BatteryTile("Case", usable(state.case), state.caseCharging, palette, GlanceModifier.defaultWeight())
        }
        if (!controls) return@Column
        Spacer(GlanceModifier.height(6.dp))
        Row(modifier = GlanceModifier.fillMaxWidth().height(48.dp)) {
            ModeButton(R.drawable.ic_mode_nc, "Noise cancelling", "ANC", AudioMode.NC, currentMode, pendingMode, wide, palette, GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.width(6.dp))
            ModeButton(R.drawable.ic_mode_ambient, "Ambient sound", "Ambient", AudioMode.AMBIENT, currentMode, pendingMode, wide, palette, GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.width(6.dp))
            ModeButton(R.drawable.ic_mode_off, "Noise control off", "Off", AudioMode.OFF, currentMode, pendingMode, wide, palette, GlanceModifier.defaultWeight())
        }
        if (currentMode == AudioMode.AMBIENT && tall) {
            Spacer(GlanceModifier.height(6.dp))
            AmbientStepper(ambientLevel, palette)
        }
    }
}

/** Shown when the earbuds are away: the last reading, quietly, and how to reconnect. */
@Composable
private fun NotConnectedBody(last: BatterySample?, palette: WidgetPalette, modifier: GlanceModifier) {
    val colors = GlanceTheme.colors
    Column(
        modifier = modifier
            .background(palette.card)
            .cornerRadius(16.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Earbuds not connected", style = titleText.copy(color = colors.onSurface), maxLines = 1)
        Spacer(GlanceModifier.height(2.dp))
        Text("Open the case near your phone", style = smallText.copy(color = colors.onSurfaceVariant), maxLines = 1)
        if (last != null) {
            Spacer(GlanceModifier.height(10.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                LastLevel("L", usable(last.left), palette, GlanceModifier.defaultWeight())
                Spacer(GlanceModifier.width(10.dp))
                LastLevel("R", usable(last.right), palette, GlanceModifier.defaultWeight())
                Spacer(GlanceModifier.width(10.dp))
                LastLevel("Case", usable(last.case), palette, GlanceModifier.defaultWeight())
            }
            Spacer(GlanceModifier.height(6.dp))
            Text("Last reading ${agoShort(last.time)}", style = smallText.copy(color = colors.onSurfaceVariant), maxLines = 1)
        }
    }
}

@Composable
private fun LastLevel(label: String, level: Int?, palette: WidgetPalette, modifier: GlanceModifier) {
    val colors = GlanceTheme.colors
    Column(modifier = modifier) {
        Text(
            "$label ${level?.let { "$it%" } ?: "--"}",
            style = smallText.copy(color = colors.onSurfaceVariant),
            maxLines = 1
        )
        Spacer(GlanceModifier.height(4.dp))
        LinearProgressIndicator(
            progress = (level ?: 0) / 100f,
            modifier = GlanceModifier.fillMaxWidth().height(4.dp),
            color = colors.outline,
            backgroundColor = palette.track
        )
    }
}

@Composable
private fun Header(state: BatteryState, context: Context, refreshing: Boolean, connected: Boolean) {
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
            Text(
                text = when {
                    refreshing -> "Updating"
                    !connected -> "Not connected"
                    timestamp != 0L -> DateFormat.getTimeFormat(context).format(Date(timestamp))
                    else -> ""
                },
                style = smallText.copy(color = if (refreshing) colors.primary else colors.onSurfaceVariant)
            )
            Box(modifier = GlanceModifier.size(44.dp), contentAlignment = Alignment.Center) {
                if (refreshing) {
                    CircularProgressIndicator(modifier = GlanceModifier.size(20.dp), color = colors.primary)
                } else {
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
}

@Composable
private fun BatteryTile(label: String, level: Int?, charging: Boolean, palette: WidgetPalette, modifier: GlanceModifier) {
    val colors = GlanceTheme.colors
    val low = level != null && level <= 20 && !charging
    Column(
        modifier = modifier.fillMaxHeight()
            .background(palette.card)
            .cornerRadius(16.dp)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .clickable(actionRunCallback<RefreshWidgetAction>()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = level?.let { "$it" } ?: "--",
                style = numberText.copy(color = if (low) colors.error else colors.onSurface),
                maxLines = 1
            )
            if (level != null) {
                Text("%", style = smallText.copy(color = colors.onSurfaceVariant))
            }
        }
        Text(
            text = if (charging) "$label · charging" else label,
            style = smallText.copy(color = if (charging) colors.primary else colors.onSurfaceVariant),
            maxLines = 1
        )
        Spacer(GlanceModifier.height(6.dp))
        LinearProgressIndicator(
            progress = (level ?: 0) / 100f,
            modifier = GlanceModifier.fillMaxWidth().height(6.dp).cornerRadius(3.dp),
            color = if (low) colors.error else colors.primary,
            backgroundColor = palette.track
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
    pendingMode: AudioMode?,
    showLabel: Boolean,
    palette: WidgetPalette,
    modifier: GlanceModifier
) {
    val active = mode == activeMode
    val waiting = mode == pendingMode
    val colors = GlanceTheme.colors
    val content = when {
        waiting -> colors.onPrimaryContainer
        active -> colors.onPrimary
        else -> colors.onSurfaceVariant
    }
    Row(
        modifier = modifier.fillMaxHeight()
            .background(
                when {
                    waiting -> colors.primaryContainer
                    active -> colors.primary
                    else -> palette.card
                }
            )
            .cornerRadius(24.dp)
            .clickable(actionRunCallback<SetListeningModeAction>(actionParametersOf(AudioModeKey.to(mode.name)))),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (waiting) {
            CircularProgressIndicator(modifier = GlanceModifier.size(18.dp), color = colors.primary)
        } else {
            Image(
                provider = ImageProvider(icon),
                contentDescription = description,
                colorFilter = ColorFilter.tint(content),
                modifier = GlanceModifier.size(20.dp)
            )
        }
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
private fun AmbientStepper(ambientLevel: Int, palette: WidgetPalette) {
    val colors = GlanceTheme.colors
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(44.dp)
            .background(palette.card)
            .cornerRadius(22.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StepButton("−", -1)
        Column(modifier = GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Ambient $ambientLevel", style = smallText.copy(color = colors.onSurface))
            Spacer(GlanceModifier.height(4.dp))
            LinearProgressIndicator(
                progress = ambientLevel.coerceIn(1, 20) / 20f,
                modifier = GlanceModifier.fillMaxWidth().height(4.dp),
                color = colors.primary,
                backgroundColor = palette.track
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
            style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium, color = GlanceTheme.colors.onSurface)
        )
    }
}

class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        // Start talking to the earbuds first, then show "Updating" while that runs.
        val now = System.currentTimeMillis()
        RefreshCoordinator.enqueueRefresh(context.applicationContext)
        val manager = GlanceAppWidgetManager(context)
        for (id in manager.getGlanceIds(SonyPodsBatteryWidget::class.java)) {
            updateAppWidgetState(context, id) { it[REFRESH_STARTED] = now }
            SonyPodsBatteryWidget().update(context, id)
        }
        for (id in manager.getGlanceIds(SunpodsMediumWidget::class.java)) {
            updateAppWidgetState(context, id) { it[REFRESH_STARTED] = now }
            SunpodsMediumWidget().update(context, id)
        }
        WidgetRedrawWorker.schedule(context, WORKING_TIMEOUT_MS / 1000 + 1)
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
        // The tapped mode lights up at once with a spinner until the earbuds confirm it.
        updateAppWidgetState(context, glanceId) { preferences ->
            preferences[LAST_USER_ACTION_TIME] = actionTime
            preferences[PENDING_MODE_KEY] = mode.name
            preferences[PENDING_MODE_AT] = actionTime
        }
        SonyPodsBatteryWidget().update(context, glanceId)
        WidgetRedrawWorker.schedule(context, WORKING_TIMEOUT_MS / 1000 + 1)

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
