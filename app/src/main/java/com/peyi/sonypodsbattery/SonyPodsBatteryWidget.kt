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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
private val refreshActionMutex = Mutex()
private val widgetRenderMutex = Mutex()

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
                val isDisconnected = EarbudConnectionStateStore.current(context) ==
                    EarbudConnectionState.DISCONNECTED
                WidgetContent(
                    context = context,
                    state = storedState,
                    currentMode = AudioMode.fromStored(currentMode),
                    ambientLevel = desiredAmbientLevel,
                    isDisconnected = isDisconnected,
                    controlsEnabled = !isDisconnected
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
        // Connection callbacks and refresh completions can arrive out of order. Keep
        // Glance/RemoteViews renders serialized so the final render observes the newest
        // persisted battery and connection state instead of overwriting it with a stale one.
        widgetRenderMutex.withLock {
            SonyPodsBatteryWidget().updateAll(context.applicationContext)
        }
    }

    suspend fun update(context: Context, glanceId: GlanceId) {
        widgetRenderMutex.withLock {
            SonyPodsBatteryWidget().update(context.applicationContext, glanceId)
        }
    }

    suspend fun syncReportedMode(context: Context, reportedMode: AudioMode): Boolean {
        return widgetRenderMutex.withLock {
            val manager = GlanceAppWidgetManager(context.applicationContext)
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
            changed
        }
    }

    suspend fun syncConfirmedState(
        context: Context,
        confirmedState: BatteryState,
        requestedAtMillis: Long
    ) {
        widgetRenderMutex.withLock {
            val manager = GlanceAppWidgetManager(context.applicationContext)
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
                    if (shouldRedraw) widget.update(context.applicationContext, glanceId)
                }
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
private fun WidgetContent(
    context: Context,
    state: BatteryState,
    currentMode: AudioMode,
    ambientLevel: Int,
    isDisconnected: Boolean,
    controlsEnabled: Boolean
) {
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
            Header(state, compactWidth, context)
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
                    ModeTrack(
                        activeMode = currentMode,
                        isDisconnected = isDisconnected,
                        controlsEnabled = controlsEnabled
                    )
                    Spacer(modifier = GlanceModifier.height(bodyGap))
                    ContextCard(
                        state = state,
                        currentMode = currentMode,
                        ambientLevel = ambientLevel,
                        compactWidth = compactWidth,
                        compactHeight = compactHeight,
                        isDisconnected = isDisconnected,
                        controlsEnabled = controlsEnabled,
                        modifier = GlanceModifier.fillMaxWidth().defaultWeight()
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(
    state: BatteryState,
    compactWidth: Boolean,
    context: Context
) {
    val timestamp = state.lastCheckedAt.takeIf { it != 0L } ?: state.updatedAt
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
            text = if (timestamp == 0L) "Not updated"
            else "Updated ${DateFormat.getTimeFormat(context).format(Date(timestamp))}",
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
private fun ModeTrack(
    activeMode: AudioMode,
    isDisconnected: Boolean,
    controlsEnabled: Boolean
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth()
            .background(GlanceTheme.colors.surfaceVariant)
            .cornerRadius(22.dp)
            .height(44.dp)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ModeButton("NC", AudioMode.NC, activeMode, isDisconnected, controlsEnabled, GlanceModifier.defaultWeight())
        ModeButton("Ambient", AudioMode.AMBIENT, activeMode, isDisconnected, controlsEnabled, GlanceModifier.defaultWeight())
        ModeButton("Off", AudioMode.OFF, activeMode, isDisconnected, controlsEnabled, GlanceModifier.defaultWeight())
    }
}

@Composable
private fun ModeButton(
    label: String,
    mode: AudioMode,
    activeMode: AudioMode,
    isDisconnected: Boolean,
    controlsEnabled: Boolean,
    modifier: GlanceModifier
) {
    val isActive = controlsEnabled && mode == activeMode
    val colors = GlanceTheme.colors
    Box(
        modifier = modifier
            .cornerRadius(18.dp)
            .background(if (isActive) colors.primary else colors.surfaceVariant)
            .fillMaxHeight()
            .let {
                if (controlsEnabled) {
                    it.clickable(actionRunCallback<SetListeningModeAction>(actionParametersOf(AudioModeKey.to(mode.name))))
                } else {
                    it
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = labelText.copy(
                color = when {
                    isActive -> colors.onPrimary
                    isDisconnected -> colors.outline
                    else -> colors.onSurfaceVariant
                }
            )
        )
    }
}

@Composable
private fun ContextCard(
    state: BatteryState,
    currentMode: AudioMode,
    ambientLevel: Int,
    compactWidth: Boolean,
    compactHeight: Boolean,
    isDisconnected: Boolean,
    controlsEnabled: Boolean,
    modifier: GlanceModifier
) {
    val contentColor = if (isDisconnected) {
        GlanceTheme.colors.outline
    } else {
        GlanceTheme.colors.onSecondaryContainer
    }
    Box(
        modifier = modifier
            .background(
                if (isDisconnected) GlanceTheme.colors.surfaceVariant
                else GlanceTheme.colors.secondaryContainer
            )
            .cornerRadius(16.dp)
            .padding(
                horizontal = if (compactWidth) 8.dp else 12.dp,
                vertical = if (compactHeight) 4.dp else 10.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        when (currentMode) {
            AudioMode.AMBIENT -> AmbientControls(
                ambientLevel = ambientLevel,
                compactWidth = compactWidth,
                compactHeight = compactHeight,
                isDisconnected = isDisconnected,
                controlsEnabled = controlsEnabled,
                contentColor = contentColor
            )
            AudioMode.NC -> StatusContent(
                R.drawable.ic_noise_cancelling,
                "Noise Cancelling",
                "Active",
                compactWidth,
                isDisconnected,
                contentColor
            )
            AudioMode.OFF -> StatusContent(
                R.drawable.ic_sound_off,
                "Sound Control Off",
                "Passive Isolation",
                compactWidth,
                isDisconnected,
                contentColor
            )
        }
    }
}

@Composable
private fun AmbientControls(
    ambientLevel: Int,
    compactWidth: Boolean,
    compactHeight: Boolean,
    isDisconnected: Boolean,
    controlsEnabled: Boolean,
    contentColor: androidx.glance.unit.ColorProvider
) {
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Ambient level",
                style = labelText.copy(
                    color = contentColor,
                    fontSize = if (compactWidth) 11.sp else 13.sp
                ),
                modifier = GlanceModifier.defaultWeight()
            )
            Text(
                text = "$ambientLevel",
                style = bodyText.copy(
                    color = contentColor,
                    fontSize = if (compactWidth) 12.sp else 13.sp
                )
            )
        }
        Row(
            modifier = GlanceModifier.fillMaxWidth()
                .padding(top = if (compactHeight) 0.dp else 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StepButton("−", -1, controlsEnabled, isDisconnected)
            AmbientProgress(ambientLevel, GlanceModifier.defaultWeight(), isDisconnected)
            StepButton("+", 1, controlsEnabled, isDisconnected)
        }
    }
}

@Composable
private fun StepButton(symbol: String, delta: Int, controlsEnabled: Boolean, isDisconnected: Boolean) {
    Box(
        modifier = GlanceModifier.size(48.dp)
            .cornerRadius(16.dp)
            .let {
                if (controlsEnabled) {
                    it.clickable(actionRunCallback<AdjustAmbientAction>(actionParametersOf(AdjustAmbientAction.deltaKey.to(delta))))
                } else {
                    it
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = symbol,
            style = TextStyle(
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = if (isDisconnected) GlanceTheme.colors.outline else GlanceTheme.colors.onSurfaceVariant
            )
        )
    }
}

@Composable
private fun AmbientProgress(level: Int, modifier: GlanceModifier, isDisconnected: Boolean) {
    LinearProgressIndicator(
        progress = level.coerceIn(1, 20) / 20f,
        modifier = modifier.height(3.dp),
        color = if (isDisconnected) GlanceTheme.colors.outline else GlanceTheme.colors.primaryContainer,
        backgroundColor = if (isDisconnected) GlanceTheme.colors.outline else GlanceTheme.colors.onSurfaceVariant
    )
}

@Composable
private fun StatusContent(
    icon: Int,
    title: String,
    subtitle: String,
    compactWidth: Boolean,
    isDisconnected: Boolean,
    contentColor: androidx.glance.unit.ColorProvider
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = title,
            colorFilter = ColorFilter.tint(contentColor),
            modifier = GlanceModifier.size(if (compactWidth) 20.dp else 24.dp)
        )
        Column(modifier = GlanceModifier.padding(start = 8.dp)) {
            Text(
                text = title,
                style = statusTitleText.copy(
                    color = contentColor,
                    fontSize = if (compactWidth) 13.sp else 14.sp
                )
            )
            Text(
                text = subtitle,
                style = statusSubtitleText.copy(
                    color = contentColor,
                    fontSize = if (compactWidth) 11.sp else 12.sp
                ),
                modifier = GlanceModifier.padding(top = 2.dp)
            )
        }
    }
}

class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        if (!refreshActionMutex.tryLock()) return

        val app = context.applicationContext
        try {
            try {
                RefreshCoordinator.awaitRefresh(app, startPolling = true, automatic = false)
            } finally {
                SonyPodsBatteryWidgetUpdater.updateAll(app)
            }
        } finally {
            refreshActionMutex.unlock()
        }
    }
}

class SetListeningModeAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val app = context.applicationContext
        if (EarbudConnectionStateStore.current(app) == EarbudConnectionState.DISCONNECTED) return
        val mode = parameters[AudioModeKey]?.let(AudioMode::fromStored) ?: return
        val current = BatteryStore.load(app)
        val preferences = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
        val ambientLevel = preferences[PENDING_AMBIENT_LEVEL_KEY]
            ?: preferences[DESIRED_AMBIENT_LEVEL_KEY]
            ?: current.ambientLevel
        val actionTime = nextUserActionTime(preferences)
        updateAppWidgetState(context, glanceId) { preferences ->
            preferences[LAST_USER_ACTION_TIME] = actionTime
        }
        SonyPodsBatteryWidgetUpdater.update(context, glanceId)

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
        if (EarbudConnectionStateStore.current(app) == EarbudConnectionState.DISCONNECTED) return
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
        SonyPodsBatteryWidgetUpdater.update(context, glanceId)
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
        if (EarbudConnectionStateStore.current(app) == EarbudConnectionState.DISCONNECTED) return
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
