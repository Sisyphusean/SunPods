package com.peyi.sonypodsbattery

import android.bluetooth.BluetoothDevice
import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

object RefreshCoordinator {
    fun refresh(context: Context, device: BluetoothDevice? = null, startPolling: Boolean = true): BatteryState =
        runBlocking { awaitRefresh(context, device, startPolling) }

    fun setListeningMode(
        context: Context,
        mode: ListeningMode,
        ambientLevel: Int? = null,
        focusOnVoice: Boolean? = null
    ): BatteryState = runBlocking {
        awaitSetListeningMode(context, mode, ambientLevel, focusOnVoice, System.currentTimeMillis())
    }

    fun enqueueRefresh(
        context: Context,
        device: BluetoothDevice? = null,
        startPolling: Boolean = true,
        onComplete: (() -> Unit)? = null,
        automatic: Boolean = false
    ) {
        SonyCommandCoordinator.enqueueRefresh(context, device, startPolling, onComplete, automatic)
    }

    fun enqueueSetListeningMode(
        context: Context,
        mode: ListeningMode,
        ambientLevel: Int,
        focusOnVoice: Boolean,
        requestedAtMillis: Long
    ) {
        SonyCommandCoordinator.enqueueSetListeningMode(
            context,
            mode,
            ambientLevel,
            focusOnVoice,
            requestedAtMillis
        )
    }

    fun enqueueAmbientLevel(
        context: Context,
        ambientLevel: Int,
        focusOnVoice: Boolean,
        requestedAtMillis: Long
    ) {
        SonyCommandCoordinator.enqueueAmbientLevel(
            context,
            ambientLevel,
            focusOnVoice,
            requestedAtMillis
        )
    }

    fun enqueuePostInteractionRefresh(context: Context) {
        SonyCommandCoordinator.enqueuePostInteractionRefresh(context)
    }

    fun closeSessionForAclDisconnect(context: Context) {
        SonyCommandCoordinator.closeSessionForAclDisconnect(context)
    }

    fun prepareSessionForAclReconnect(context: Context) {
        SonyCommandCoordinator.prepareSessionForAclReconnect(context)
    }

    fun handleAudioProfileEvent(
        context: Context,
        device: BluetoothDevice,
        profile: AudioProfile,
        state: AudioProfileConnectionState
    ) {
        SonyCommandCoordinator.handleAudioProfileEvent(context, device, profile, state)
    }

    fun closeSessionForServiceStop(context: Context) {
        SonyCommandCoordinator.closeSessionForServiceStop(context)
    }

    suspend fun awaitRefresh(
        context: Context,
        device: BluetoothDevice? = null,
        startPolling: Boolean = true,
        automatic: Boolean = false
    ): BatteryState = SonyCommandCoordinator.awaitRefresh(context, device, startPolling, automatic)

    private suspend fun awaitSetListeningMode(
        context: Context,
        mode: ListeningMode,
        ambientLevel: Int?,
        focusOnVoice: Boolean?,
        requestedAtMillis: Long
    ): BatteryState = SonyCommandCoordinator.awaitSetListeningMode(
        context,
        mode,
        ambientLevel,
        focusOnVoice,
        requestedAtMillis
    )
}

private sealed interface SonyCommand {
    data class Refresh(
        val context: Context,
        val device: BluetoothDevice?,
        val startPolling: Boolean,
        val completion: CompletableDeferred<BatteryState>,
        val onComplete: (() -> Unit)? = null,
        val postInteraction: Boolean = false,
        val automatic: Boolean = false,
        val profileTriggered: Boolean = false
    ) : SonyCommand

    data class SetMode(
        val context: Context,
        val mode: ListeningMode,
        val ambientLevel: Int,
        val focusOnVoice: Boolean,
        val requestedAtMillis: Long,
        val completion: CompletableDeferred<BatteryState>
    ) : SonyCommand

    data class SetAmbientLevel(
        val context: Context,
        val ambientLevel: Int,
        val focusOnVoice: Boolean,
        val requestedAtMillis: Long
    ) : SonyCommand

    data class Reconnect(
        val context: Context,
        val device: BluetoothDevice,
        val attempt: Int,
        val generation: Long
    ) : SonyCommand

    data class AudioProfileChanged(
        val context: Context,
        val device: BluetoothDevice,
        val profile: AudioProfile,
        val state: AudioProfileConnectionState
    ) : SonyCommand
}

private object SonyCommandCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val commands = Channel<SonyCommand>(Channel.UNLIMITED)

    @Volatile
    private var workerStarted = false

    @Volatile
    private var postInteractionRefreshQueued = false

    private var profileRefreshQueued = false

    private var reconnectJob: Job? = null
    private var reconnectGeneration = 0L

    private val reconnectDelaysMillis = longArrayOf(1_000L, 2_000L, 5_000L, 15_000L, 30_000L)

    fun enqueueRefresh(
        context: Context,
        device: BluetoothDevice?,
        startPolling: Boolean,
        onComplete: (() -> Unit)?,
        automatic: Boolean
    ) {
        commands.trySend(
            SonyCommand.Refresh(
                context.applicationContext,
                device,
                startPolling,
                CompletableDeferred(),
                onComplete,
                automatic = automatic
            )
        )
        startWorker()
    }

    fun enqueueSetListeningMode(
        context: Context,
        mode: ListeningMode,
        ambientLevel: Int,
        focusOnVoice: Boolean,
        requestedAtMillis: Long
    ) {
        commands.trySend(
            SonyCommand.SetMode(
                context.applicationContext,
                mode,
                ambientLevel,
                focusOnVoice,
                requestedAtMillis,
                CompletableDeferred()
            )
        )
        startWorker()
    }

    fun enqueueAmbientLevel(
        context: Context,
        ambientLevel: Int,
        focusOnVoice: Boolean,
        requestedAtMillis: Long
    ) {
        commands.trySend(
            SonyCommand.SetAmbientLevel(
                context.applicationContext,
                ambientLevel,
                focusOnVoice,
                requestedAtMillis
            )
        )
        startWorker()
    }

    @Synchronized
    fun enqueuePostInteractionRefresh(context: Context) {
        if (postInteractionRefreshQueued) return
        postInteractionRefreshQueued = true
        commands.trySend(
            SonyCommand.Refresh(
                context.applicationContext,
                null,
                false,
                CompletableDeferred(),
                postInteraction = true,
                automatic = true
            )
        )
        startWorker()
    }

    @Synchronized
    fun handleAudioProfileEvent(
        context: Context,
        device: BluetoothDevice,
        profile: AudioProfile,
        state: AudioProfileConnectionState
    ) {
        commands.trySend(
            SonyCommand.AudioProfileChanged(
                context.applicationContext,
                device,
                profile,
                state
            )
        )
        startWorker()
    }

    @Synchronized
    fun closeSessionForAclDisconnect(context: Context) {
        cancelReconnectLocked()
        SonyControlSessionManager.closeForAclDisconnect(context)
    }

    @Synchronized
    fun prepareSessionForAclReconnect(context: Context) {
        cancelReconnectLocked()
        SonyControlSessionManager.prepareAclReconnect(context)
    }

    fun closeSessionForServiceStop(context: Context) {
        SonyControlSessionManager.closeForServiceStop(context)
    }

    suspend fun awaitRefresh(
        context: Context,
        device: BluetoothDevice?,
        startPolling: Boolean,
        automatic: Boolean
    ): BatteryState {
        val completion = CompletableDeferred<BatteryState>()
        commands.send(
            SonyCommand.Refresh(
                context.applicationContext,
                device,
                startPolling,
                completion,
                null,
                false,
                automatic
            )
        )
        startWorker()
        return completion.await()
    }

    suspend fun awaitSetListeningMode(
        context: Context,
        mode: ListeningMode,
        ambientLevel: Int?,
        focusOnVoice: Boolean?,
        requestedAtMillis: Long
    ): BatteryState {
        val completion = CompletableDeferred<BatteryState>()
        val previous = BatteryStore.load(context.applicationContext)
        commands.send(
            SonyCommand.SetMode(
                context.applicationContext,
                mode,
                (ambientLevel ?: previous.ambientLevel).coerceIn(1, 20),
                focusOnVoice ?: previous.focusOnVoice,
                requestedAtMillis,
                completion
            )
        )
        startWorker()
        return completion.await()
    }

    @Synchronized
    private fun startWorker() {
        if (workerStarted) return
        workerStarted = true
        scope.launch {
            var deferredCommand: SonyCommand? = null
            while (isActive) {
                val first = deferredCommand ?: commands.receive()
                deferredCommand = null
                val command = if (first is SonyCommand.SetAmbientLevel) {
                    val coalesced = coalesceAmbient(first)
                    deferredCommand = coalesced.deferredCommand
                    coalesced.command
                } else {
                    if (first is SonyCommand.Refresh && first.postInteraction) {
                        val coalesced = coalescePostInteractionRefresh(first)
                        deferredCommand = coalesced.deferredCommand
                        coalesced.command
                    } else {
                        first
                    }
                }
                execute(command)
            }
        }
    }

    private data class AmbientCoalesced(
        val command: SonyCommand.SetAmbientLevel,
        val deferredCommand: SonyCommand?
    )

    private fun coalesceAmbient(first: SonyCommand.SetAmbientLevel): AmbientCoalesced {
        var latest = first
        while (true) {
            val next = commands.tryReceive().getOrNull() ?: return AmbientCoalesced(latest, null)
            if (next is SonyCommand.SetAmbientLevel) {
                latest = next
            } else {
                return AmbientCoalesced(latest, next)
            }
        }
    }

    private data class RefreshCoalesced(
        val command: SonyCommand.Refresh,
        val deferredCommand: SonyCommand?
    )

    private fun coalescePostInteractionRefresh(first: SonyCommand.Refresh): RefreshCoalesced {
        var latest = first
        while (true) {
            val next = commands.tryReceive().getOrNull()
                ?: return RefreshCoalesced(latest, null)
            if (next is SonyCommand.Refresh && next.postInteraction) {
                latest = next
            } else {
                return RefreshCoalesced(latest, next)
            }
        }
    }

    @Synchronized
    private fun cancelReconnect() {
        cancelReconnectLocked()
    }

    private fun cancelReconnectLocked() {
        reconnectGeneration += 1L
        reconnectJob?.cancel()
        reconnectJob = null
    }

    @Synchronized
    private fun isReconnectCurrent(generation: Long): Boolean =
        generation == reconnectGeneration

    private fun scheduleReconnect(
        context: Context,
        device: BluetoothDevice,
        attempt: Int = 0,
        waitForProfileState: Boolean = false
    ) {
        val app = context.applicationContext
        val generation: Long
        synchronized(this) {
            cancelReconnectLocked()
            generation = reconnectGeneration
            SonyControlSessionManager.enterReconnectWait(app)
            reconnectJob = scope.launch {
                val delayIndex = attempt.coerceAtMost(reconnectDelaysMillis.lastIndex)
                val delayMillis = if (waitForProfileState) {
                    PROFILE_STATE_RETRY_DELAY_MS
                } else {
                    reconnectDelaysMillis[delayIndex]
                }
                DiagnosticsStore.add(app, "RFCOMM reconnect scheduled in ${delayMillis}ms")
                delay(delayMillis)
                if (isReconnectCurrent(generation)) {
                    commands.send(SonyCommand.Reconnect(app, device, attempt, generation))
                }
            }
        }
        startWorker()
    }

    private suspend fun executeReconnect(command: SonyCommand.Reconnect) {
        if (!isReconnectCurrent(command.generation)) return
        val app = command.context.applicationContext
        DiagnosticsStore.add(app, "RFCOMM reconnect attempt: ${command.attempt + 1}")
        when (SonyAudioProfileQuery.snapshot(app, command.device).availability) {
            AudioProfileAvailability.AVAILABLE -> Unit
            AudioProfileAvailability.DISCONNECTED -> {
                handleAllAudioProfilesDisconnected(app, command.device)
                return
            }
            AudioProfileAvailability.UNKNOWN -> {
                DiagnosticsStore.add(app, "RFCOMM reconnect waiting for audio profile state")
                scheduleReconnect(app, command.device, command.attempt, waitForProfileState = true)
                return
            }
        }
        try {
            SonyControlSessionManager.ensureConnected(app, command.device)
            if (!isReconnectCurrent(command.generation)) return
            reconnectJob = null
            EarbudConnectionStateStore.markWithDiagnostics(
                app,
                EarbudConnectionState.CONNECTED,
                command.device
            )
            BatteryPollingService.start(app)
            enqueueRefresh(
                app,
                command.device,
                startPolling = false,
                onComplete = null,
                automatic = true
            )
        } catch (error: SonyControlChannelUnavailableException) {
            DiagnosticsStore.add(app, "RFCOMM reconnect failed: ${error.message}")
            scheduleReconnect(app, command.device, command.attempt + 1)
        }
    }

    private suspend fun execute(command: SonyCommand) {
        when (command) {
            is SonyCommand.Refresh -> {
                command.completion.complete(executeRefresh(command))
            }
            is SonyCommand.SetMode -> {
                command.completion.complete(executeSetMode(command))
            }
            is SonyCommand.SetAmbientLevel -> {
                executeAmbientLevel(command)
            }
            is SonyCommand.Reconnect -> {
                executeReconnect(command)
            }
            is SonyCommand.AudioProfileChanged -> {
                executeAudioProfileChanged(command)
            }
        }
    }

    private suspend fun executeRefresh(command: SonyCommand.Refresh): BatteryState {
        val app = command.context.applicationContext
        val previous = BatteryStore.load(app)
        val client = SonyBatteryClient(app)

        if (command.postInteraction) postInteractionRefreshQueued = false
        if (command.profileTriggered) profileRefreshQueued = false
        if (command.automatic && EarbudConnectionStateStore.current(app) == EarbudConnectionState.DISCONNECTED) {
            DiagnosticsStore.add(app, "Automatic refresh skipped: disconnected")
            command.onComplete?.invoke()
            return previous
        }
        if (command.automatic && SonyControlSessionManager.currentState() == SonySessionState.RECONNECT_WAIT) {
            DiagnosticsStore.add(app, "Automatic refresh skipped: reconnect pending")
            command.onComplete?.invoke()
            return previous
        }

        val manualReconnectAttempt = !command.automatic &&
            EarbudConnectionStateStore.current(app) == EarbudConnectionState.DISCONNECTED
        if (!command.automatic) {
            cancelReconnect()
            if (manualReconnectAttempt) SonyControlSessionManager.prepareManualReconnect(app)
        }
        if (manualReconnectAttempt) DiagnosticsStore.add(app, "Manual refresh attempting reconnect")

        DiagnosticsStore.add(app, "Refresh started")
        val target = command.device ?: client.findEarbuds()
        if (command.automatic && target == null) {
            command.onComplete?.invoke()
            return previous
        }
        if (command.automatic) {
            when (SonyAudioProfileQuery.snapshot(app, target!!).availability) {
                AudioProfileAvailability.AVAILABLE -> Unit
                AudioProfileAvailability.DISCONNECTED -> {
                    handleAllAudioProfilesDisconnected(app, target)
                    command.onComplete?.invoke()
                    return previous
                }
                AudioProfileAvailability.UNKNOWN -> {
                    DiagnosticsStore.add(app, "Automatic refresh skipped: audio profile state unresolved")
                    command.onComplete?.invoke()
                    return previous
                }
            }
        }
        val state = if (target == null) {
            previous.copy(status = "Pair your WF-1000XM5 in Android settings first")
        } else try {
            val result = client.read(target)
            if (!command.automatic) {
                if (manualReconnectAttempt) DiagnosticsStore.add(app, "Manual refresh restored connection")
                EarbudConnectionStateStore.markWithDiagnostics(app, EarbudConnectionState.CONNECTED, target)
            }
            DiagnosticsStore.add(app, "Refresh succeeded; case reported=${result.case != null}")
            BatteryState(
                result.left,
                result.right,
                result.case,
                result.leftCharging,
                result.rightCharging,
                result.caseCharging,
                previous.listeningMode,
                previous.ambientLevel,
                previous.focusOnVoice,
                System.currentTimeMillis(),
                if (result.case == null) "Updated. Open the case to refresh its battery." else "Updated"
            )
        } catch (error: SonyControlChannelUnavailableException) {
            markControlChannelUnavailable(app, target)
            DiagnosticsStore.add(app, "Refresh failed: ${error.javaClass.simpleName}: ${error.message}")
            previous.copy(status = "Could not connect. Close Sony Sound Connect and try again.")
        } catch (error: SecurityException) {
            DiagnosticsStore.add(app, "Refresh blocked: ${error.message ?: error.javaClass.simpleName}")
            previous.copy(status = "Android blocked a Bluetooth action. Share diagnostics.")
        } catch (error: Exception) {
            DiagnosticsStore.add(app, "Refresh failed: ${error.javaClass.simpleName}: ${error.message}")
            previous.copy(status = "Could not connect. Close Sony Sound Connect and try again.")
        }

        BatteryStore.save(app, state)
        SonyPodsBatteryWidgetUpdater.syncReportedMode(app, AudioMode.fromListeningMode(state.listeningMode))
        val batteryDataChanged = state.left != previous.left ||
            state.right != previous.right ||
            state.case != previous.case ||
            state.leftCharging != previous.leftCharging ||
            state.rightCharging != previous.rightCharging ||
            state.caseCharging != previous.caseCharging
        if (!command.postInteraction || batteryDataChanged) {
            SonyPodsBatteryWidgetUpdater.updateAll(app)
        }
        if (!command.postInteraction) BatteryNotifier.show(app, state)
        if (command.startPolling && state.status.startsWith("Updated") &&
            EarbudConnectionStateStore.current(app) != EarbudConnectionState.DISCONNECTED
        ) BatteryPollingService.start(app)
        command.onComplete?.invoke()
        return state
    }

    private suspend fun executeSetMode(command: SonyCommand.SetMode): BatteryState {
        val app = command.context.applicationContext
        val previous = BatteryStore.load(app)
        val client = SonyBatteryClient(app)
        DiagnosticsStore.add(app, "Listening mode change requested: ${command.mode.name}")
        if (EarbudConnectionStateStore.current(app) == EarbudConnectionState.DISCONNECTED) {
            DiagnosticsStore.add(app, "Control command skipped: disconnected")
            return finishControlCommand(
                app,
                previous.copy(status = "Could not change mode. Earbuds are disconnected."),
                command.requestedAtMillis
            )
        }
        val target = client.findEarbuds()
        val state = if (target == null) {
            previous.copy(status = "Pair your WF-1000XM5 in Android settings first")
        } else try {
            client.setListeningMode(target, command.mode, command.ambientLevel, command.focusOnVoice)
            SonyCommandCoordinator.enqueuePostInteractionRefresh(app)
            DiagnosticsStore.add(app, "Listening mode changed: ${command.mode.name}")
            previous.copy(
                listeningMode = command.mode,
                ambientLevel = command.ambientLevel,
                focusOnVoice = command.focusOnVoice,
                status = "${command.mode.label} enabled",
                updatedAt = System.currentTimeMillis()
            )
        } catch (error: SonyControlChannelUnavailableException) {
            markControlChannelUnavailable(app, target)
            DiagnosticsStore.add(app, "Listening mode failed: ${error.javaClass.simpleName}: ${error.message}")
            previous.copy(status = "Could not change mode. Close Sony Sound Connect and try again.")
        } catch (error: Exception) {
            DiagnosticsStore.add(app, "Listening mode failed: ${error.javaClass.simpleName}: ${error.message}")
            previous.copy(status = "Could not change mode. Close Sony Sound Connect and try again.")
        }

        return finishControlCommand(app, state, command.requestedAtMillis)
    }

    private suspend fun executeAmbientLevel(command: SonyCommand.SetAmbientLevel): BatteryState {
        val app = command.context.applicationContext
        val previous = BatteryStore.load(app)
        val client = SonyBatteryClient(app)
        val level = command.ambientLevel.coerceIn(1, 20)
        DiagnosticsStore.add(app, "Ambient level change requested: $level")
        if (EarbudConnectionStateStore.current(app) == EarbudConnectionState.DISCONNECTED) {
            DiagnosticsStore.add(app, "Control command skipped: disconnected")
            return finishControlCommand(
                app,
                previous.copy(status = "Could not change ambient level. Earbuds are disconnected."),
                command.requestedAtMillis
            )
        }
        val target = client.findEarbuds()
        val state = if (target == null) {
            previous.copy(status = "Pair your WF-1000XM5 in Android settings first")
        } else try {
            client.setListeningMode(target, ListeningMode.AMBIENT, level, command.focusOnVoice)
            SonyCommandCoordinator.enqueuePostInteractionRefresh(app)
            DiagnosticsStore.add(app, "Ambient level changed: $level")
            previous.copy(
                listeningMode = ListeningMode.AMBIENT,
                ambientLevel = level,
                focusOnVoice = command.focusOnVoice,
                status = "Ambient sound enabled",
                updatedAt = System.currentTimeMillis()
            )
        } catch (error: SonyControlChannelUnavailableException) {
            markControlChannelUnavailable(app, target)
            DiagnosticsStore.add(app, "Ambient level failed: ${error.javaClass.simpleName}: ${error.message}")
            previous.copy(status = "Could not change ambient level. Close Sony Sound Connect and try again.")
        } catch (error: Exception) {
            DiagnosticsStore.add(app, "Ambient level failed: ${error.javaClass.simpleName}: ${error.message}")
            previous.copy(status = "Could not change ambient level. Close Sony Sound Connect and try again.")
        }

        return finishControlCommand(app, state, command.requestedAtMillis)
    }

    private suspend fun executeAudioProfileChanged(command: SonyCommand.AudioProfileChanged) {
        val app = command.context.applicationContext
        val snapshot = SonyAudioProfileQuery.snapshot(app, command.device)
        val availability = if (
            (command.state == AudioProfileConnectionState.CONNECTED ||
                command.state == AudioProfileConnectionState.CONNECTING) &&
            snapshot.availability == AudioProfileAvailability.DISCONNECTED
        ) {
            AudioProfileAvailability.AVAILABLE
        } else {
            snapshot.availability
        }
        when (availability) {
            AudioProfileAvailability.DISCONNECTED -> {
                handleAllAudioProfilesDisconnected(app, command.device)
            }
            AudioProfileAvailability.UNKNOWN -> {
                if (command.state == AudioProfileConnectionState.CONNECTED) {
                    markAudioProfileAvailable(app, command.device)
                } else {
                    DiagnosticsStore.add(app, "Audio profile state unresolved; waiting for profile event")
                }
            }
            AudioProfileAvailability.AVAILABLE -> {
                markAudioProfileAvailable(app, command.device)
            }
        }
        if (command.state == AudioProfileConnectionState.CONNECTED &&
            availability != AudioProfileAvailability.DISCONNECTED
        ) {
            enqueueProfileRefresh(app, command.device)
        }
    }

    private fun enqueueProfileRefresh(context: Context, device: BluetoothDevice) {
        synchronized(this) {
            if (profileRefreshQueued) return
            profileRefreshQueued = true
            commands.trySend(
                SonyCommand.Refresh(
                    context.applicationContext,
                    device,
                    startPolling = false,
                    completion = CompletableDeferred(),
                    automatic = true,
                    profileTriggered = true
                )
            )
        }
        startWorker()
    }

    private fun markAudioProfileAvailable(context: Context, device: BluetoothDevice) {
        cancelReconnect()
        SonyControlSessionManager.prepareAclReconnect(context)
        EarbudConnectionStateStore.markWithDiagnostics(
            context,
            EarbudConnectionState.CONNECTED,
            device
        )
        BatteryPollingService.start(context)
    }

    private fun handleAllAudioProfilesDisconnected(context: Context, device: BluetoothDevice) {
        cancelReconnect()
        DiagnosticsStore.add(context, "All audio profiles disconnected; RFCOMM reconnect cancelled")
        EarbudConnectionStateStore.markWithDiagnostics(
            context,
            EarbudConnectionState.DISCONNECTED,
            device
        )
        SonyControlSessionManager.closeForAclDisconnect(context)
        BatteryPollingService.stop(context)
    }

    private fun markControlChannelUnavailable(context: Context, device: BluetoothDevice?) {
        if (EarbudConnectionStateStore.current(context) == EarbudConnectionState.DISCONNECTED) return
        DiagnosticsStore.add(context, "RFCOMM unavailable, marking disconnected")
        EarbudConnectionStateStore.markWithDiagnostics(
            context,
            EarbudConnectionState.DISCONNECTED,
            device
        )
        BatteryPollingService.stop(context)
        if (device != null) scheduleReconnect(context, device)
    }

    private suspend fun finishControlCommand(
        context: Context,
        state: BatteryState,
        requestedAtMillis: Long
    ): BatteryState {
        BatteryStore.save(context, state)
        SonyPodsBatteryWidgetUpdater.syncConfirmedState(context, state, requestedAtMillis)
        BatteryNotifier.show(context, state)
        return state
    }

    private const val PROFILE_STATE_RETRY_DELAY_MS = 1_000L
}
