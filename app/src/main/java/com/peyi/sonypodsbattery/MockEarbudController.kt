package com.peyi.sonypodsbattery

/**
 * Debug-only state provider for exercising the onboarding success path without
 * a physical Bluetooth peripheral. It is never consulted by release flows.
 */
object MockEarbudController {
    fun initialState(): BatteryState = BatteryState(
        left = 82,
        right = 76,
        case = 64,
        listeningMode = ListeningMode.NOISE_CANCELLING,
        ambientLevel = 20,
        updatedAt = System.currentTimeMillis(),
        lastCheckedAt = System.currentTimeMillis(),
        status = "Connected to WF-1000XM5 (emulator mock)",
    )

    fun refreshedState(state: BatteryState): BatteryState = state.copy(
        updatedAt = System.currentTimeMillis(),
        lastCheckedAt = System.currentTimeMillis(),
        status = "Battery refreshed (emulator mock)",
    )

    fun modeState(
        state: BatteryState,
        mode: ListeningMode,
        ambientLevel: Int,
        focusOnVoice: Boolean,
    ): BatteryState = state.copy(
        listeningMode = mode,
        ambientLevel = ambientLevel.coerceIn(1, 20),
        focusOnVoice = focusOnVoice,
        updatedAt = System.currentTimeMillis(),
        lastCheckedAt = System.currentTimeMillis(),
        status = "${mode.label} enabled (emulator mock)",
    )
}
