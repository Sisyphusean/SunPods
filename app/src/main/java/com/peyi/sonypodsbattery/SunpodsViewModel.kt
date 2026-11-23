package com.peyi.sonypodsbattery

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

    /** Settings changes still on their way to the earbuds. */
    private val _applying = MutableStateFlow(0)
    val applying: StateFlow<Int> = _applying.asStateFlow()

    /** Counts changes the earbuds turned down, so the UI can answer with a reject haptic. */
    private val _rejected = MutableStateFlow(0)
    val rejected: StateFlow<Int> = _rejected.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val settingsLock = Mutex()

    // Background refreshes (earbuds connecting, polling, the widget) save to BatteryStore. Follow
    // those saves so the open screen updates by itself instead of waiting for a pull.
    private val batteryListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        _battery.value = BatteryStore.load(app)
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
        ShizukuWatch.start(application)
        Updates.schedule(application)
        // Opening the app also checks the connection, so a problem left from earlier gets fixed.
        ClassicRepair.check(application, "SunPods opened")
        BatteryStore.prefs(application).registerOnSharedPreferenceChangeListener(batteryListener)
        EarbudConnectionStateStore.prefs(application).registerOnSharedPreferenceChangeListener(connectionListener)
    }

    private var liveJob: Job? = null
    private val _liveAt = MutableStateFlow(0L)
    /** When a live check last reached the earbuds. */
    val liveAt: StateFlow<Long> = _liveAt.asStateFlow()

    /** While the app is on screen, check the earbuds every few seconds so it follows them live. */
    fun setLive(on: Boolean) {
        liveJob?.cancel()
        liveJob = null
        if (!on) return
        liveJob = viewModelScope.launch {
            while (true) {
                delay(3_000)
                if (_busy.value) { android.util.Log.d("SunPods", "Live check skipped: busy"); continue }
                // A live check must never take the app down; it just tries again next time.
                if (withContext(Dispatchers.IO) { runCatching { liveTick(app) }.getOrDefault(false) }) _liveAt.value = System.currentTimeMillis()
            }
        }
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
                    _message.value = "Couldn't reach your earbuds. If Sony Sound Connect is open, close it, then pull down to try again."
                }
                if (_battery.value.left != null || _battery.value.right != null) {
                    launch { LastSeenStore.captureWhileConnected(app) }
                }
                settingsLock.withLock {
                    RefreshCoordinator.awaitSession(app, "Read settings") { session ->
                        SonyFeatures.readAll(session, _settings.value)
                    }.onSuccess(::storeSettings)
                }
            } finally {
                _busy.value = false
            }
        }
    }

    fun setListeningMode(mode: ListeningMode, ambientLevel: Int, focusOnVoice: Boolean) {
        val current = _battery.value
        _battery.value = current.copy(listeningMode = mode, ambientLevel = ambientLevel, focusOnVoice = focusOnVoice)
        viewModelScope.launch {
            _battery.value = withContext(Dispatchers.IO) {
                RefreshCoordinator.setListeningMode(app, mode, ambientLevel, focusOnVoice)
            }
        }
    }

    fun setEqPreset(preset: EqPreset) = change("Equaliser", { it.copy(eqPresetCode = preset.code) }) { s, r ->
        SonyFeatures.setEqPreset(s, r, preset)
    }

    fun setEqBands(clearBass: Int, bands: List<Int>) = change(
        "Custom Equaliser",
        { it.copy(clearBass = clearBass, bands = bands) }
    ) { s, r -> SonyFeatures.setEqBands(s, r, clearBass, bands) }

    fun setDsee(on: Boolean) = change("DSEE Extreme", { it.copy(dseeOn = on) }) { s, r ->
        SonyFeatures.setDsee(s, r, on)
    }

    fun setPriority(priority: ConnectionPriority) = change("Connection Priority", { it.copy(priority = priority) }) { s, r ->
        SonyFeatures.setPriority(s, r, priority)
    }

    fun setSpeakToChat(on: Boolean) = change("Speak-to-Chat", { it.copy(speakToChat = on) }) { s, r ->
        SonyFeatures.setSpeakToChat(s, r, on)
    }

    fun setSpeakToChatConfig(sensitivity: SpeakToChatSensitivity, timeout: SpeakToChatTimeout) = change(
        "Speak-to-Chat Settings",
        { it.copy(speakToChatSensitivity = sensitivity, speakToChatTimeout = timeout) }
    ) { s, r -> SonyFeatures.setSpeakToChatConfig(s, r, sensitivity, timeout) }

    fun setPauseWhenRemoved(on: Boolean) = change("Pause When Removed", { it.copy(pauseWhenRemoved = on) }) { s, r ->
        SonyFeatures.setPauseWhenRemoved(s, r, on)
    }

    fun setAutoOff(whenRemoved: Boolean) = change("Automatic Power Off", { it.copy(autoOffWhenRemoved = whenRemoved) }) { s, r ->
        SonyFeatures.setAutoOff(s, r, whenRemoved)
    }

    fun setGeneral(slot: Int, on: Boolean) = change(
        "this setting",
        { s -> s.copy(general = s.general?.map { if (it.slot == slot) it.copy(on = on) else it }) }
    ) { s, r -> SonyFeatures.setGeneral(s, r, slot, on) }

    fun setPrompts(on: Boolean) {
        QuietHours.overrideNow(app)
        change("Voice Prompts", { it.copy(promptsOn = on) }) { s, r -> SonyFeatures.setPrompts(s, r, on) }
    }

    fun setPromptVolume(volume: Int) = change("Voice Prompt Volume", { it.copy(promptVolume = volume) }) { s, r ->
        SonyFeatures.setPromptVolume(s, r, volume)
    }

    /** Saves quiet hours and applies them at once if the window has already started or ended. */
    fun setQuietHours(on: Boolean, start: Int, end: Int) {
        QuietHours.set(app, on, start, end)
        change("Quiet Hours", { it }) { s, r ->
            QuietHours.enforce(app, s)
            r.copy(promptsOn = VoicePrompts.read(s).on ?: r.promptsOn)
        }
    }

    /** Changes one side's touch control; null leaves that side as it is on the earbuds. */
    fun setTouch(left: TouchFunction?, right: TouchFunction?) = change(
        "Touch Controls",
        { it.copy(touchLeft = left ?: it.touchLeft, touchRight = right ?: it.touchRight) }
    ) { s, r -> SonyFeatures.setTouch(s, r, left, right) }

    fun setAutoSwitch(on: Boolean) = change("Automatic Switching", { it.copy(autoSwitch = on) }) { s, r ->
        SonyFeatures.setAutoSwitch(s, r, on)
    }

    private val _safe = MutableStateFlow(SafeListening.on(application) to SafeListening.sensitive(application))
    /** Safe listening on, and volume protection set to sensitive. */
    val safe: StateFlow<Pair<Boolean, Boolean>> = _safe.asStateFlow()

    private val _update = MutableStateFlow(Updates.available(application)?.takeUnless { Updates.dismissed(application, it.version) })
    /** A newer SunPods on GitHub, if there is one. */
    internal val update: StateFlow<Updates.Release?> = _update.asStateFlow()
    private val _updateState = MutableStateFlow<SelfUpdate.State>(SelfUpdate.State.Idle)
    internal val updateState: StateFlow<SelfUpdate.State> = _updateState.asStateFlow()

    init {
        viewModelScope.launch { _update.value = Updates.checkIfDue(app)?.takeUnless { Updates.dismissed(app, it.version) } }
    }

    internal fun setAvailableUpdate(release: Updates.Release?) { _update.value = release }

    /** "Later": hide the card until a newer version than this one. */
    fun dismissUpdate() {
        _update.value?.let { Updates.dismiss(app, it.version) }
        _update.value = null
    }

    /** Downloads the new version and hands it to Android's installer. */
    fun installUpdate() {
        val release = _update.value ?: return
        val apk = release.apk
        if (apk == null) { Updates.open(app, release.url); return }
        if (!SelfUpdate.canInstall(app)) {
            _updateState.value = SelfUpdate.State.NeedsPermission
            SelfUpdate.openInstallPermission(app)
            return
        }
        if (_updateState.value is SelfUpdate.State.Downloading || _updateState.value == SelfUpdate.State.Installing) return
        viewModelScope.launch {
            _updateState.value = SelfUpdate.State.Downloading(null)
            val file = runCatching { SelfUpdate.download(app, apk) { f -> _updateState.value = SelfUpdate.State.Downloading(f) } }.getOrNull()
            if (file == null) { _updateState.value = SelfUpdate.State.Failed("Couldn't download it. Check your connection and try again."); return@launch }
            if (!SelfUpdate.isGenuine(app, file)) {
                file.delete()
                _updateState.value = SelfUpdate.State.Failed("That download isn't a genuine SunPods update, so it wasn't installed.")
                return@launch
            }
            _updateState.value = SelfUpdate.State.Installing
            runCatching { SelfUpdate.install(app, file) }
                .onFailure { _updateState.value = SelfUpdate.State.Failed("Couldn't start the install. Try again.") }
        }
    }

    private var safeJob: kotlinx.coroutines.Job? = null

    /** Runs a write with the saving bar showing, and the reject buzz and [failMessage] if it fails. */
    private fun applying(failMessage: String, revert: () -> Unit, write: suspend () -> Boolean) = viewModelScope.launch {
        _applying.value++
        try {
            if (!write()) { revert(); _rejected.value++; _message.value = failMessage }
        } finally {
            _applying.value--
        }
    }

    fun setSafeListening(on: Boolean) {
        val before = _safe.value
        _safe.value = before.copy(first = on)
        safeJob = applying("Couldn't change Safe Listening. Make sure your earbuds are connected and try again.", { _safe.value = before }) {
            RefreshCoordinator.awaitSession(app, "Safe Listening", bothEarbuds = true) { SafeListening.setOn(app, it, on) }.getOrDefault(false)
        }
    }

    private val _level = MutableStateFlow<SafeListening.Level?>(null)
    /** The live listening level while a meter is on screen; null before the first reading. */
    val level: StateFlow<SafeListening.Level?> = _level.asStateFlow()
    private var meterJob: kotlinx.coroutines.Job? = null

    /** Reads the listening level ten times a second until [stopMeter], then restores the user's setting. */
    fun startMeter() {
        meterJob?.cancel()
        meterJob = viewModelScope.launch {
            try {
                // Turning Safe Listening on writes the setting too; let that land first so it
                // doesn't switch the meter straight back off.
                safeJob?.join()
                RefreshCoordinator.awaitSession(app, "Level meter on") { SafeListening.startMeter(it) }
                var misses = 0
                while (true) {
                    if (!audioLinkUp(app)) { delay(1_000); continue }
                    val got = RefreshCoordinator.awaitSession(app, "Level") { SafeListening.readLevel(it) }.getOrNull()
                    if (got != null) { _level.value = got; misses = 0 } else misses++
                    // Smooth while it answers; ease off when it doesn't, so a dropped link isn't hammered.
                    delay(if (misses < 5) 100 else 2_000)
                }
            } finally {
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    RefreshCoordinator.awaitSession(app, "Level meter off") { SafeListening.stopMeter(it, _safe.value.first) }
                }
                _level.value = null
            }
        }
    }

    fun stopMeter() {
        meterJob?.cancel()
        meterJob = null
    }

    fun setVolumeProtection(sensitive: Boolean) {
        val before = _safe.value
        _safe.value = before.copy(second = sensitive)
        applying("Couldn't change Volume Protection. Make sure your earbuds are connected and try again.", { _safe.value = before }) {
            RefreshCoordinator.awaitSession(app, "Volume Protection", bothEarbuds = true) { SafeListening.setSensitive(app, it, sensitive) }.getOrDefault(false)
        }
    }

    fun setHeadGestures(on: Boolean) = change("Head Gestures", { it.copy(headGestures = on) }) { s, r ->
        SonyFeatures.setHeadGestures(s, r, on)
    }

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    /** Reads what's playing, if the link is free; skips quietly otherwise. */
    fun refreshNowPlaying() {
        if (!audioLinkUp(app) || SonyControlSessionManager.currentState() == SonySessionState.RECONNECT_WAIT) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val target = SonyBatteryClient(app).findEarbuds() ?: return@runCatching
                SonyControlSessionManager.withSessionIfFree(app, target) { Playback.read(it) }?.let { _nowPlaying.value = it }
            }
        }
    }

    fun playback(action: Playback.Action) {
        _nowPlaying.value = _nowPlaying.value?.let {
            when (action) {
                Playback.Action.PLAY -> it.copy(playing = true)
                Playback.Action.PAUSE -> it.copy(playing = false)
                else -> it
            }
        }
        viewModelScope.launch {
            RefreshCoordinator.awaitSession(app, "Playback") { s ->
                Playback.control(s, action)
                Thread.sleep(400)
                Playback.read(s)
            }.onSuccess { _nowPlaying.value = it }
        }
    }

    private val _assistant = MutableStateFlow<Pair<Int?, List<Int>>?>(null)
    /** The voice assistant the earbuds use, and the ones they offer; null if they have no choice. */
    val assistant: StateFlow<Pair<Int?, List<Int>>?> = _assistant.asStateFlow()

    fun loadAssistant() {
        viewModelScope.launch {
            RefreshCoordinator.awaitSession(app, "Voice Assistant") { VoiceAssistantSetting.read(it) }
                .onSuccess { _assistant.value = it }
        }
    }

    fun setAssistant(value: Int) {
        val before = _assistant.value
        _assistant.value = before?.copy(first = value)
        applying("Couldn't change your voice assistant. Make sure your earbuds are connected and try again.", { _assistant.value = before }) {
            RefreshCoordinator.awaitSession(app, "Voice Assistant", bothEarbuds = true) { s ->
                VoiceAssistantSetting.set(s, value)
                VoiceAssistantSetting.read(s)
            }.onSuccess { _assistant.value = it ?: before?.copy(first = value) }.isSuccess
        }
    }

    fun fitTips(done: (FitTest.Tips?) -> Unit) {
        viewModelScope.launch { done(RefreshCoordinator.awaitSession(app, "Fit Test") { FitTest.tips(it) }.getOrNull()) }
    }

    fun runFitTest(tips: FitTest.Tips, size: Int, done: (FitTest.Result) -> Unit) {
        viewModelScope.launch {
            done(
                RefreshCoordinator.awaitSession(app, "Fit Test") { FitTest.run(it, tips, size) }
                    .getOrElse { FitTest.Result(null, null, "Couldn't reach your earbuds. Make sure they're connected and try again.") }
            )
        }
    }

    fun resetSettings(done: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = RefreshCoordinator.awaitSession(app, "Reset Settings", bothEarbuds = true) { EarbudReset.resetSettings(it) }.getOrDefault(false)
            done(ok)
            if (ok) refreshAll()
        }
    }

    fun powerOff(done: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = RefreshCoordinator.awaitSession(app, "Power Off", bothEarbuds = true) { SonyFeatures.powerOff(it) }.isSuccess
            done(ok)
        }
    }

    fun setNoiseCycle(cycle: NoiseCycle) = change("Noise Control Cycle", { it.copy(noiseCycle = cycle) }) { s, r ->
        SonyFeatures.setNoiseCycle(s, r, cycle)
    }

    private fun change(
        label: String,
        optimistic: (DeviceSettings) -> DeviceSettings,
        action: (SonyBatteryClient.SonySession, DeviceSettings) -> DeviceSettings
    ) {
        val before = _settings.value
        _settings.value = optimistic(before)
        _applying.value++
        viewModelScope.launch {
            try {
                settingsLock.withLock {
                    RefreshCoordinator.awaitSession(app, label, bothEarbuds = true) { session -> action(session, before) }
                        .onSuccess(::storeSettings)
                        .onFailure { error ->
                            _settings.value = before
                            _rejected.value++
                            _message.value = when (error) {
                                is EarbudsUnavailableException -> error.message
                                else -> "Couldn't change $label. Make sure your earbuds are connected and Sony Sound Connect is closed."
                            }
                        }
                }
            } finally {
                _applying.value--
            }
        }
    }

    private fun storeSettings(next: DeviceSettings) {
        _settings.value = next
        SettingsStore.save(app, next)
    }
}
