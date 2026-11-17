package com.peyi.sonypodsbattery

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class SunpodsViewModel(application: Application) : AndroidViewModel(application) {
    private val app: Application get() = getApplication()

    private val _battery = MutableStateFlow(BatteryStore.load(application))
    val battery: StateFlow<BatteryState> = _battery.asStateFlow()

    private val _settings = MutableStateFlow(SettingsStore.load(application))
    val settings: StateFlow<DeviceSettings> = _settings.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val settingsLock = Mutex()

    // Background refreshes (earbuds connecting, polling, the widget) save to BatteryStore. Follow
    // those saves so the open screen updates by itself instead of waiting for a pull.
    private val batteryListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        val state = BatteryStore.load(app)
        WindMode.reconcile(app, state)
        _windy.value = WindMode.on(app)
        _battery.value = state
    }

    private val _windy = MutableStateFlow(WindMode.on(application))
    val windy: StateFlow<Boolean> = _windy.asStateFlow()

    /** Windy: noise control off while it's blowing, then back to what you had. */
    fun setWindy(on: Boolean) {
        val t = if (on) WindMode.start(app) else WindMode.stop(app)
        _windy.value = on
        applyMode(t.mode, t.level, t.focus)
    }

    /** Bumps whenever the earbuds connect or disconnect, so the screen re-checks the link. */
    private val _link = MutableStateFlow(0)
    val link: StateFlow<Int> = _link.asStateFlow()

    // While the app is open, a reconnect reads everything again (levels, mode, settings) with the
    // same retries as a pull to refresh, and a disconnect shows at once.
    private val connectionListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != "a2dp_state" && key != "state") return@OnSharedPreferenceChangeListener
        _link.value++
        _battery.value = BatteryStore.load(app)
        if (key == "a2dp_state" && EarbudConnectionStateStore.a2dpState(app) == A2dpConnectionState.CONNECTED) {
            viewModelScope.launch {
                delay(1_500)
                refreshAll()
            }
        }
    }

    init {
        BatteryStore.prefs(application).registerOnSharedPreferenceChangeListener(batteryListener)
        EarbudConnectionStateStore.prefs(application).registerOnSharedPreferenceChangeListener(connectionListener)
    }

    override fun onCleared() {
        BatteryStore.prefs(app).unregisterOnSharedPreferenceChangeListener(batteryListener)
        EarbudConnectionStateStore.prefs(app).unregisterOnSharedPreferenceChangeListener(connectionListener)
    }

    /** On opening the app: read the earbuds if they're connected and the numbers are over a minute old. */
    fun refreshIfStale() {
        val b = _battery.value
        val age = System.currentTimeMillis() - maxOf(b.updatedAt, b.lastCheckedAt)
        if (age > 60_000 && audioLinkUp(app)) refreshAll()
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun reloadCached() {
        _battery.value = BatteryStore.load(app)
        _settings.value = SettingsStore.load(app)
    }

    fun refreshAll() {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                // Right after the earbuds connect, the control channel often isn't ready for a second
                // or two. Retry quietly instead of stopping and showing the old numbers.
                var state = RefreshCoordinator.awaitRefresh(app, startPolling = true)
                var attempt = 0
                while (!state.status.startsWith("Updated") && attempt < 3 && audioLinkUp(app)) {
                    delay(1_500L * (attempt + 1))
                    attempt++
                    state = RefreshCoordinator.awaitRefresh(app, startPolling = true)
                }
                _battery.value = state
                if (!state.status.startsWith("Updated") && audioLinkUp(app)) {
                    _message.value = "Couldn't reach your earbuds. If Sony Sound Connect is open, close it and pull down."
                }
                if (_battery.value.left != null || _battery.value.right != null) {
                    launch { LastSeenStore.captureWhileConnected(app) }
                }
                settingsLock.withLock {
                    RefreshCoordinator.awaitSession(app, "Read settings") { session ->
                        SonyFeatures.readAll(session, _settings.value)
                    }.onSuccess(::storeSettings)
                    if (FeatureProbe.due(app)) {
                        RefreshCoordinator.awaitSession(app, "Feature probe") { FeatureProbe.run(it) }
                            .onSuccess { FeatureProbe.save(app, it) }
                    }
                }
            } finally {
                _busy.value = false
            }
        }
    }

    fun setListeningMode(mode: ListeningMode, ambientLevel: Int, focusOnVoice: Boolean) {
        WindMode.clear(app)
        _windy.value = false
        applyMode(mode, ambientLevel, focusOnVoice)
    }

    private fun applyMode(mode: ListeningMode, ambientLevel: Int, focusOnVoice: Boolean) {
        val current = _battery.value
        _battery.value = current.copy(listeningMode = mode, ambientLevel = ambientLevel, focusOnVoice = focusOnVoice)
        viewModelScope.launch {
            _battery.value = withContext(Dispatchers.IO) {
                RefreshCoordinator.setListeningMode(app, mode, ambientLevel, focusOnVoice)
            }
        }
    }

    fun setEqPreset(preset: EqPreset) = change("Equaliser preset", { it.copy(eqPresetCode = preset.code) }) { s, r ->
        SonyFeatures.setEqPreset(s, r, preset)
    }

    fun setEqBands(clearBass: Int, bands: List<Int>) = change(
        "Custom equaliser",
        { it.copy(clearBass = clearBass, bands = bands) }
    ) { s, r -> SonyFeatures.setEqBands(s, r, clearBass, bands) }

    fun setDsee(on: Boolean) = change("DSEE Extreme", { it.copy(dseeOn = on) }) { s, r ->
        SonyFeatures.setDsee(s, r, on)
    }

    fun setPriority(priority: ConnectionPriority) = change("Connection quality", { it.copy(priority = priority) }) { s, r ->
        SonyFeatures.setPriority(s, r, priority)
    }

    fun setSpeakToChat(on: Boolean) = change("Speak-to-Chat", { it.copy(speakToChat = on) }) { s, r ->
        SonyFeatures.setSpeakToChat(s, r, on)
    }

    fun setSpeakToChatConfig(sensitivity: SpeakToChatSensitivity, timeout: SpeakToChatTimeout) = change(
        "Speak-to-Chat settings",
        { it.copy(speakToChatSensitivity = sensitivity, speakToChatTimeout = timeout) }
    ) { s, r -> SonyFeatures.setSpeakToChatConfig(s, r, sensitivity, timeout) }

    fun setPauseWhenRemoved(on: Boolean) = change("Pause when removed", { it.copy(pauseWhenRemoved = on) }) { s, r ->
        SonyFeatures.setPauseWhenRemoved(s, r, on)
    }

    fun setAutoOff(whenRemoved: Boolean) = change("Automatic power off", { it.copy(autoOffWhenRemoved = whenRemoved) }) { s, r ->
        SonyFeatures.setAutoOff(s, r, whenRemoved)
    }

    fun setGeneral(slot: Int, on: Boolean) = change(
        "Device setting",
        { s -> s.copy(general = s.general?.map { if (it.slot == slot) it.copy(on = on) else it }) }
    ) { s, r -> SonyFeatures.setGeneral(s, r, slot, on) }

    fun setTouch(left: TouchFunction, right: TouchFunction) = change(
        "Touch controls",
        { it.copy(touchLeft = left, touchRight = right) }
    ) { s, r -> SonyFeatures.setTouch(s, r, left, right) }

    fun setNoiseCycle(cycle: NoiseCycle) = change("Noise control cycle", { it.copy(noiseCycle = cycle) }) { s, r ->
        SonyFeatures.setNoiseCycle(s, r, cycle)
    }

    private fun change(
        label: String,
        optimistic: (DeviceSettings) -> DeviceSettings,
        action: (SonyBatteryClient.SonySession, DeviceSettings) -> DeviceSettings
    ) {
        val before = _settings.value
        _settings.value = optimistic(before)
        viewModelScope.launch {
            settingsLock.withLock {
                RefreshCoordinator.awaitSession(app, label) { session -> action(session, before) }
                    .onSuccess(::storeSettings)
                    .onFailure { error ->
                        _settings.value = before
                        _message.value = when (error) {
                            is EarbudsUnavailableException -> error.message
                            else -> "$label didn't apply. Check the earbuds are connected and Sound Connect is closed."
                        }
                    }
            }
        }
    }

    private fun storeSettings(next: DeviceSettings) {
        _settings.value = next
        SettingsStore.save(app, next)
    }
}
