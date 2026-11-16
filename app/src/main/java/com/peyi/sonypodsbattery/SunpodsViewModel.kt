package com.peyi.sonypodsbattery

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
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

    fun consumeMessage() {
        _message.value = null
    }

    fun reloadCached() {
        _battery.value = BatteryStore.load(app)
        _settings.value = SettingsStore.load(app)
    }

    fun refreshAll() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                _battery.value = RefreshCoordinator.awaitRefresh(app, startPolling = true)
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

    fun setTouch(left: TouchFunction, right: TouchFunction) = change(
        "Touch controls",
        { it.copy(touchLeft = left, touchRight = right) }
    ) { s, r -> SonyFeatures.setTouch(s, r, left, right) }

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
