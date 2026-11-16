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

    fun handleA2dpConnected(context: Context, device: BluetoothDevice) {
        SonyCommandCoordinator.handleA2dpConnected(context, device)
    }

    fun handleA2dpDisconnected(context: Context, device: BluetoothDevice) {
        SonyCommandCoordinator.handleA2dpDisconnected(context, device)
    }

    suspend fun handleDeviceDisconnected(context: Context, device: BluetoothDevice) {
        SonyCommandCoordinator.handleDeviceDisconnected(context, device)
    }

    fun closeSessionForServiceStop(context: Context) {
        SonyCommandCoordinator.closeSessionForServiceStop(context)
    }

    /** Runs [action] on the shared Sony session, serialized with refreshes and mode changes. */
    internal suspend fun <T> awaitSession(
        context: Context,
        label: String,
        action: (SonyBatteryClient.SonySession) -> T
    ): Result<T> = SonyCommandCoordinator.awaitCustom(context, label, action)

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
        val a2dpTriggered: Boolean = false,
        val automaticGeneration: Long = 0L
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

    class Custom(
        val context: Context,
        val label: String,
        val action: (SonyBatteryClient.SonySession) -> Any?,
        val completion: CompletableDeferred<Result<Any?>>
    ) : SonyCommand

}

private object SonyCommandCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val commands = Channel<SonyCommand>(Channel.UNLIMITED)

    @Volatile
    private var workerStarted = false

    @Volatile
    private var postInteractionRefreshQueued = false

    @Volatile
    private var a2dpRefreshQueued = false

    @Volatile
    private var automaticWorkGeneration = 0L

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
                automatic = automatic,
                automaticGeneration = currentAutomaticWorkGeneration()
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
                automatic = true,
                automaticGeneration = currentAutomaticWorkGeneration()
            )
        )
        startWorker()
    }

    @Synchronized
    fun handleA2dpConnected(context: Context, device: BluetoothDevice) {
        cancelReconnectLocked()
        SonyControlSessionManager.prepareA2dpReconnect(context)
        EarbudConnectionStateStore.markWithDiagnostics(
            context,
            EarbudConnectionState.CONNECTED,
            device
        )
        BatteryPollingService.start(context)
        if (!a2dpRefreshQueued) {
            a2dpRefreshQueued = true
            commands.trySend(
                SonyCommand.Refresh(
                    context.applicationContext,
                    device,
                    startPolling = false,
                    completion = CompletableDeferred(),
                    automatic = true,
                    a2dpTriggered = true,
                    automaticGeneration = currentAutomaticWorkGeneration()
                )
            )
        }
        startWorker()
    }

    @Synchronized
    fun handleA2dpDisconnected(context: Context, device: BluetoothDevice) {
        handleDeviceDisconnectedInternal(context, device)
        scope.launch { SonyPodsBatteryWidgetUpdater.updateAll(context.applicationContext) }
    }

    suspend fun handleDeviceDisconnected(context: Context, device: BluetoothDevice) {
        handleDeviceDisconnectedInternal(context, device)
        SonyPodsBatteryWidgetUpdater.updateAll(context.applicationContext)
    }

    @Synchronized
    private fun handleDeviceDisconnectedInternal(context: Context, device: BluetoothDevice) {
        cancelReconnectLocked()
        automaticWorkGeneration += 1L
        postInteractionRefreshQueued = false
        a2dpRefreshQueued = false
        SonyControlSessionManager.closeForA2dpDisconnect(context)
        EarbudConnectionStateStore.markWithDiagnostics(
            context,
            EarbudConnectionState.DISCONNECTED,
            device
        )
        BatteryStore.clearBatteryValues(context)
        BatteryPollingService.stop(context)
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
                automatic,
                automaticGeneration = currentAutomaticWorkGeneration()
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
                if (first is SonyCommand.Refresh) {
                    val batch = coalesceRefresh(first)
                    deferredCommand = batch.deferredCommand
                    if (batch.primary.postInteraction || batch.waiters.any { it.postInteraction }) {
                        postInteractionRefreshQueued = false
                    }
                    if (batch.primary.a2dpTriggered || batch.waiters.any { it.a2dpTriggered }) {
                        a2dpRefreshQueued = false
                    }
                    executeRefresh(batch.primary).also { state ->
                        batch.primary.completion.complete(state)
                        batch.waiters.forEach { waiter ->
                            waiter.completion.complete(state)
                            waiter.onComplete?.invoke()
                        }
                    }
                    continue
                }
                val command = if (first is SonyCommand.SetAmbientLevel) {
                    val coalesced = coalesceAmbient(first)
                    deferredCommand = coalesced.deferredCommand
                    coalesced.command
                } else {
                    first
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

    private data class RefreshBatch(
        val primary: SonyCommand.Refresh,
        val waiters: List<SonyCommand.Refresh>,
        val deferredCommand: SonyCommand?
    )

    private fun coalesceRefresh(first: SonyCommand.Refresh): RefreshBatch {
        val requests = mutableListOf(first)
        while (true) {
            val next = commands.tryReceive().getOrNull()
                ?: break
            if (next is SonyCommand.Refresh) {
                requests += next
            } else {
                val primaryRequest = requests.firstOrNull { !it.automatic } ?: first
                val primary = primaryRequest.copy(
                    context = first.context,
                    device = requests.firstNotNullOfOrNull { it.device },
                    startPolling = requests.any { it.startPolling },
                    completion = first.completion,
                    onComplete = first.onComplete,
                    postInteraction = requests.all { it.postInteraction },
                    automatic = requests.all { it.automatic },
                    a2dpTriggered = requests.any { it.a2dpTriggered },
                    automaticGeneration = primaryRequest.automaticGeneration
                )
                return RefreshBatch(primary, requests.drop(1), next)
            }
        }
        val primaryRequest = requests.firstOrNull { !it.automatic } ?: first
        val primary = primaryRequest.copy(
            context = first.context,
            device = requests.firstNotNullOfOrNull { it.device },
            startPolling = requests.any { it.startPolling },
            completion = first.completion,
            onComplete = first.onComplete,
            postInteraction = requests.all { it.postInteraction },
            automatic = requests.all { it.automatic },
            a2dpTriggered = requests.any { it.a2dpTriggered },
            automaticGeneration = primaryRequest.automaticGeneration
        )
        return RefreshBatch(primary, requests.drop(1), null)
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
                    A2DP_STATE_RETRY_DELAY_MS
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
        when (A2dpConnectionHelper.isConnected(app, command.device)) {
            true -> Unit
            false -> {
                handleA2dpDisconnected(app, command.device)
                return
            }
            null -> {
                DiagnosticsStore.add(app, "A2DP state unavailable; RFCOMM reconnect deferred")
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
            is SonyCommand.Custom -> {
                command.completion.complete(executeCustom(command))
            }
        }
    }

    private suspend fun executeCustom(command: SonyCommand.Custom): Result<Any?> {
        val app = command.context.applicationContext
        val client = SonyBatteryClient(app)
        val target = client.findEarbuds()
            ?: return Result.failure(EarbudsUnavailableException("Pair your WF-1000XM5 in Android settings first"))
        if (A2dpConnectionHelper.isConnected(app, target) == false) {
            return Result.failure(EarbudsUnavailableException("Earbuds aren't connected"))
        }
        val wasConnected = SonyControlSessionManager.currentState() == SonySessionState.CONNECTED
        return try {
            Result.success(
                try {
                    SonyControlSessionManager.withSession(app, target, command.action)
                } catch (stale: SonyControlChannelUnavailableException) {
                    if (!wasConnected) throw stale
                    // The cached socket may have died silently; reopen once before giving up.
                    DiagnosticsStore.add(app, "${command.label}: session failed, retrying once")
                    SonyControlSessionManager.prepareManualReconnect(app)
                    SonyControlSessionManager.withSession(app, target, command.action)
                }
            )
        } catch (error: Exception) {
            DiagnosticsStore.add(app, "${command.label} failed: ${error.javaClass.simpleName}: ${error.message}")
            Result.failure(error)
        }
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun <T> awaitCustom(
        context: Context,
        label: String,
        action: (SonyBatteryClient.SonySession) -> T
    ): Result<T> {
        val completion = CompletableDeferred<Result<Any?>>()
        commands.send(SonyCommand.Custom(context.applicationContext, label, { action(it) }, completion))
        startWorker()
        return completion.await().map { it as T }
    }

    private suspend fun executeConfirmedControlCommand(
        context: Context,
        device: BluetoothDevice,
        operation: String,
        action: () -> Unit
    ) {
        val app = context.applicationContext
        val stateBeforePrepare = SonyControlSessionManager.currentState()
        if (stateBeforePrepare != SonySessionState.CONNECTED) {
            DiagnosticsStore.add(
                app,
                "$operation detected stale Sony session state=$stateBeforePrepare; reconnecting"
            )
            SonyControlSessionManager.prepareManualReconnect(app)
            try {
                SonyControlSessionManager.ensureConnected(app, device)
                DiagnosticsStore.add(app, "$operation Sony control session reconnected")
            } catch (error: SonyControlChannelUnavailableException) {
                DiagnosticsStore.add(app, "$operation terminal failure during reconnect: ${error.message}")
                throw error
            }
        }

        try {
            action()
            DiagnosticsStore.add(app, "$operation Sony confirmation received")
        } catch (error: SonyControlChannelUnavailableException) {
            if (stateBeforePrepare != SonySessionState.CONNECTED) {
                DiagnosticsStore.add(app, "$operation terminal failure after reconnect: ${error.message}")
                throw error
            }

            DiagnosticsStore.add(app, "$operation detected stale connected Sony session; retry 1/1")
            SonyControlSessionManager.closeForCommandRetry(app)
            try {
                SonyControlSessionManager.prepareManualReconnect(app)
                DiagnosticsStore.add(app, "$operation recreating Sony control session")
                SonyControlSessionManager.ensureConnected(app, device)
                action()
                DiagnosticsStore.add(app, "$operation Sony confirmation received after retry")
            } catch (retryError: SonyControlChannelUnavailableException) {
                DiagnosticsStore.add(app, "$operation terminal failure after retry: ${retryError.message}")
                throw retryError
            }
        }
    }

    private suspend fun executeRefresh(command: SonyCommand.Refresh): BatteryState {
        val app = command.context.applicationContext
        val previous = BatteryStore.load(app)
        val client = SonyBatteryClient(app)
        val checkedAt = if (!command.automatic) System.currentTimeMillis() else 0L
        fun checkedState(state: BatteryState): BatteryState =
            if (checkedAt == 0L) state else state.copy(lastCheckedAt = checkedAt)
        fun persistCheckedState(state: BatteryState): BatteryState {
            val checked = checkedState(state)
            if (checkedAt != 0L) BatteryStore.save(app, checked)
            return checked
        }

        if (command.postInteraction) postInteractionRefreshQueued = false
        if (command.a2dpTriggered) a2dpRefreshQueued = false
        if (command.automatic && command.automaticGeneration != automaticWorkGeneration) {
            DiagnosticsStore.add(app, "Automatic refresh skipped: stale disconnect work")
            command.onComplete?.invoke()
            return persistCheckedState(previous)
        }

        val manualReconnectAttempt = !command.automatic &&
            EarbudConnectionStateStore.current(app) == EarbudConnectionState.DISCONNECTED
        if (!command.automatic) {
            cancelReconnect()
            if (manualReconnectAttempt) SonyControlSessionManager.prepareManualReconnect(app)
        }
        if (manualReconnectAttempt) DiagnosticsStore.add(app, "Manual refresh attempting reconnect")

        DiagnosticsStore.add(app, "Refresh started")
        val t0 = System.currentTimeMillis()
        val target = command.device ?: client.findEarbuds()
        if (target != null) {
            when (A2dpConnectionHelper.isConnected(app, target)) {
                true -> {
                    EarbudConnectionStateStore.markWithDiagnostics(
                        app,
                        EarbudConnectionState.CONNECTED,
                        target
                    )
                }
                false -> {
                    DiagnosticsStore.add(app, "A2DP disconnected; refresh skipped")
                    handleA2dpDisconnected(app, target)
                    command.onComplete?.invoke()
                    return persistCheckedState(BatteryStore.load(app))
                }
                null -> {
                    DiagnosticsStore.add(app, "A2DP state unavailable; refresh skipped")
                    command.onComplete?.invoke()
                    return persistCheckedState(previous.copy(status = "Could not verify the earbud connection."))
                }
            }
        }
        if (command.automatic && EarbudConnectionStateStore.current(app) == EarbudConnectionState.DISCONNECTED) {
            DiagnosticsStore.add(app, "Automatic refresh skipped: disconnected")
            command.onComplete?.invoke()
            return persistCheckedState(previous)
        }
        if (command.automatic && SonyControlSessionManager.currentState() == SonySessionState.RECONNECT_WAIT) {
            DiagnosticsStore.add(app, "Automatic refresh skipped: reconnect pending")
            command.onComplete?.invoke()
            return persistCheckedState(previous)
        }
        if (command.automatic && target == null) {
            command.onComplete?.invoke()
            return persistCheckedState(previous)
        }
        val state = if (target == null) {
            persistCheckedState(previous.copy(status = "Pair your WF-1000XM5 in Android settings first"))
        } else try {
            val wasConnected = SonyControlSessionManager.currentState() == SonySessionState.CONNECTED
            val tRead = System.currentTimeMillis()
            val result = try {
                client.read(target)
            } catch (stale: SonyControlChannelUnavailableException) {
                if (!wasConnected) throw stale
                // The kept socket died quietly: reopen once instead of failing the refresh.
                DiagnosticsStore.add(app, "Refresh: kept session was stale, reopening")
                SonyControlSessionManager.closeForCommandRetry(app)
                client.read(target)
            }
            DiagnosticsStore.add(app, "Refresh timing: link check ${tRead - t0} ms, read ${System.currentTimeMillis() - tRead} ms (${if (wasConnected) "kept session" else "new session"})")
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
                checkedAt,
                if (result.case == null) "Updated. Open the case to refresh its battery." else "Updated"
            )
        } catch (error: SonyControlChannelUnavailableException) {
            markControlChannelUnavailable(app, target)
            DiagnosticsStore.add(app, "Refresh failed: ${error.javaClass.simpleName}: ${error.message}")
            persistCheckedState(previous.copy(status = "Could not connect. Close Sony Sound Connect and try again."))
        } catch (error: SecurityException) {
            DiagnosticsStore.add(app, "Refresh blocked: ${error.message ?: error.javaClass.simpleName}")
            persistCheckedState(previous.copy(status = "Android blocked a Bluetooth action. Share diagnostics."))
        } catch (error: Exception) {
            DiagnosticsStore.add(app, "Refresh failed: ${error.javaClass.simpleName}: ${error.message}")
            persistCheckedState(previous.copy(status = "Could not connect. Close Sony Sound Connect and try again."))
        }

        if (EarbudConnectionStateStore.current(app) == EarbudConnectionState.DISCONNECTED &&
            EarbudConnectionStateStore.a2dpState(app) == A2dpConnectionState.DISCONNECTED
        ) {
            command.onComplete?.invoke()
            return BatteryStore.load(app)
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
        val target = client.findEarbuds()
        if (target != null) {
            when (A2dpConnectionHelper.isConnected(app, target)) {
                true -> EarbudConnectionStateStore.markWithDiagnostics(
                    app,
                    EarbudConnectionState.CONNECTED,
                    target
                )
                false -> {
                    DiagnosticsStore.add(app, "A2DP disconnected; control command skipped")
                    handleA2dpDisconnected(app, target)
                    return BatteryStore.load(app)
                }
                null -> {
                    DiagnosticsStore.add(app, "A2DP state unavailable; control command skipped")
                    return finishControlCommand(
                        app,
                        previous.copy(status = "Could not verify the earbud connection."),
                        command.requestedAtMillis
                    )
                }
            }
        }
        val state = if (target == null) {
            previous.copy(status = "Pair your WF-1000XM5 in Android settings first")
        } else try {
            executeConfirmedControlCommand(
                app,
                target,
                "Listening mode ${command.mode.name}"
            ) {
                client.setListeningMode(target, command.mode, command.ambientLevel, command.focusOnVoice)
            }
            SonyCommandCoordinator.enqueuePostInteractionRefresh(app)
            DiagnosticsStore.add(app, "Listening mode changed: ${command.mode.name}")
            previous.copy(
                listeningMode = command.mode,
                ambientLevel = command.ambientLevel,
                focusOnVoice = command.focusOnVoice,
                status = "${command.mode.label} enabled"
            )
        } catch (error: SonyControlChannelUnavailableException) {
            markControlChannelUnavailable(app, target)
            DiagnosticsStore.add(app, "Listening mode terminal failure: ${error.javaClass.simpleName}: ${error.message}")
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
        val target = client.findEarbuds()
        if (target != null) {
            when (A2dpConnectionHelper.isConnected(app, target)) {
                true -> EarbudConnectionStateStore.markWithDiagnostics(
                    app,
                    EarbudConnectionState.CONNECTED,
                    target
                )
                false -> {
                    DiagnosticsStore.add(app, "A2DP disconnected; control command skipped")
                    handleA2dpDisconnected(app, target)
                    return BatteryStore.load(app)
                }
                null -> {
                    DiagnosticsStore.add(app, "A2DP state unavailable; control command skipped")
                    return finishControlCommand(
                        app,
                        previous.copy(status = "Could not verify the earbud connection."),
                        command.requestedAtMillis
                    )
                }
            }
        }
        val state = if (target == null) {
            previous.copy(status = "Pair your WF-1000XM5 in Android settings first")
        } else try {
            executeConfirmedControlCommand(
                app,
                target,
                "Ambient level $level"
            ) {
                client.setListeningMode(target, ListeningMode.AMBIENT, level, command.focusOnVoice)
            }
            SonyCommandCoordinator.enqueuePostInteractionRefresh(app)
            DiagnosticsStore.add(app, "Ambient level changed: $level")
            previous.copy(
                listeningMode = ListeningMode.AMBIENT,
                ambientLevel = level,
                focusOnVoice = command.focusOnVoice,
                status = "Ambient sound enabled"
            )
        } catch (error: SonyControlChannelUnavailableException) {
            markControlChannelUnavailable(app, target)
            DiagnosticsStore.add(app, "Ambient level terminal failure: ${error.javaClass.simpleName}: ${error.message}")
            previous.copy(status = "Could not change ambient level. Close Sony Sound Connect and try again.")
        } catch (error: Exception) {
            DiagnosticsStore.add(app, "Ambient level failed: ${error.javaClass.simpleName}: ${error.message}")
            previous.copy(status = "Could not change ambient level. Close Sony Sound Connect and try again.")
        }

        return finishControlCommand(app, state, command.requestedAtMillis)
    }

    private suspend fun markControlChannelUnavailable(context: Context, device: BluetoothDevice?) {
        if (EarbudConnectionStateStore.current(context) == EarbudConnectionState.DISCONNECTED) return
        DiagnosticsStore.add(context, "RFCOMM unavailable, marking disconnected")
        EarbudConnectionStateStore.markWithDiagnostics(
            context,
            EarbudConnectionState.DISCONNECTED,
            device
        )
        BatteryPollingService.stop(context)
        if (device == null) return
        when (A2dpConnectionHelper.isConnected(context, device)) {
            true -> scheduleReconnect(context, device)
            false -> handleA2dpDisconnected(context, device)
            null -> scheduleReconnect(context, device, waitForProfileState = true)
        }
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

    private const val A2DP_STATE_RETRY_DELAY_MS = 1_000L

    @Synchronized
    private fun currentAutomaticWorkGeneration(): Long = automaticWorkGeneration
}
