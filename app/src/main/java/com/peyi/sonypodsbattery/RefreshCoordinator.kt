package com.peyi.sonypodsbattery

import android.bluetooth.BluetoothDevice
import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
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

}

private object SonyCommandCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val commands = Channel<SonyCommand>(Channel.UNLIMITED)

    private var workerJob: Job? = null

    @Volatile
    private var lastWorkerContext: Context? = null

    @Volatile
    private var postInteractionRefreshQueued = false

    @Volatile
    private var a2dpRefreshQueued = false

    @Volatile
    private var automaticWorkGeneration = 0L

    private var reconnectJob: Job? = null
    private var reconnectGeneration = 0L
    private var connectionRefreshJob: Job? = null
    private var connectionRefreshGeneration = 0L

    private val reconnectDelaysMillis = longArrayOf(1_000L, 2_000L, 5_000L, 15_000L, 30_000L)
    private val connectionRefreshDelaysMillis = longArrayOf(750L, 2_000L, 5_000L)

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
        if (!a2dpRefreshQueued) scheduleConnectionRefreshLocked(context, device)
        scope.launch { SonyPodsBatteryWidgetUpdater.updateAll(context.applicationContext) }
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
        cancelConnectionRefreshLocked()
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
        if (!automatic) cancelConnectionRefresh()
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
        if (workerJob?.isActive == true) return

        val job = scope.launch(start = CoroutineStart.LAZY) {
            var deferredCommand: SonyCommand? = null
            try {
                while (isActive) {
                    val first = deferredCommand ?: commands.receive()
                    deferredCommand = null
                    lastWorkerContext = commandContext(first)
                    try {
                        if (first is SonyCommand.Refresh) {
                            val batch = coalesceRefresh(first)
                            deferredCommand = batch.deferredCommand
                            if (batch.primary.postInteraction || batch.waiters.any { it.postInteraction }) {
                                postInteractionRefreshQueued = false
                            }
                            if (batch.primary.a2dpTriggered || batch.waiters.any { it.a2dpTriggered }) {
                                a2dpRefreshQueued = false
                            }
                            executeRefreshBatch(batch)
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
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Throwable) {
                        recordWorkerFailure(commandContext(first), error)
                        failCommand(first, error)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                recordWorkerFailure(lastWorkerContext, error)
            } finally {
                synchronized(SonyCommandCoordinator) {
                    if (workerJob === kotlin.coroutines.coroutineContext[Job]) {
                        workerJob = null
                    }
                }
            }
        }
        workerJob = job
        job.start()
    }

    private fun commandContext(command: SonyCommand): Context = when (command) {
        is SonyCommand.Refresh -> command.context
        is SonyCommand.SetMode -> command.context
        is SonyCommand.SetAmbientLevel -> command.context
        is SonyCommand.Reconnect -> command.context
    }

    private fun recordWorkerFailure(context: Context?, error: Throwable) {
        context ?: return
        DiagnosticsStore.add(
            context.applicationContext,
            "Unexpected Sony command worker failure: ${error.javaClass.simpleName}: ${error.message}"
        )
    }

    private fun failCommand(command: SonyCommand, error: Throwable) {
        when (command) {
            is SonyCommand.Refresh -> {
                command.completion.completeExceptionally(error)
                runCatching { command.onComplete?.invoke() }
            }
            is SonyCommand.SetMode -> command.completion.completeExceptionally(error)
            is SonyCommand.SetAmbientLevel -> Unit
            is SonyCommand.Reconnect -> Unit
        }
    }

    private suspend fun executeRefreshBatch(batch: RefreshBatch) {
        try {
            executeRefresh(batch.primary).also { state ->
                batch.primary.completion.complete(state)
                batch.waiters.forEach { waiter ->
                    waiter.completion.complete(state)
                    waiter.onComplete?.invoke()
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            recordWorkerFailure(batch.primary.context, error)
            failCommand(batch.primary, error)
            batch.waiters.forEach { waiter -> failCommand(waiter, error) }
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
    private fun cancelConnectionRefresh() {
        cancelConnectionRefreshLocked()
    }

    private fun cancelConnectionRefreshLocked() {
        connectionRefreshGeneration += 1L
        connectionRefreshJob?.cancel()
        connectionRefreshJob = null
        a2dpRefreshQueued = false
    }

    @Synchronized
    private fun isConnectionRefreshCurrent(generation: Long, context: Context): Boolean =
        generation == connectionRefreshGeneration &&
            EarbudConnectionStateStore.current(context) !=
            EarbudConnectionState.DISCONNECTED

    private fun scheduleConnectionRefreshLocked(context: Context, device: BluetoothDevice) {
        connectionRefreshGeneration += 1L
        val generation = connectionRefreshGeneration
        connectionRefreshJob?.cancel()
        a2dpRefreshQueued = true
        val app = context.applicationContext
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                for ((attempt, delayMillis) in connectionRefreshDelaysMillis.withIndex()) {
                    delay(delayMillis)
                    if (!isConnectionRefreshCurrent(generation, app)) return@launch
                    DiagnosticsStore.add(
                        app,
                        "Connection refresh attempt ${attempt + 1}/${connectionRefreshDelaysMillis.size}"
                    )
                    val state = awaitRefresh(
                        app,
                        device,
                        startPolling = false,
                        automatic = true
                    )
                    if (state.status.startsWith("Updated")) return@launch
                }
                DiagnosticsStore.add(app, "Connection refresh exhausted retry window")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                DiagnosticsStore.add(
                    app,
                    "Connection refresh failed: ${error.javaClass.simpleName}: ${error.message}"
                )
            } finally {
                synchronized(SonyCommandCoordinator) {
                    if (connectionRefreshJob === kotlin.coroutines.coroutineContext[Job]) {
                        connectionRefreshJob = null
                        a2dpRefreshQueued = false
                    }
                }
            }
        }
        connectionRefreshJob = job
        job.start()
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
        }
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
        suspend fun finishManualRefresh(state: BatteryState): BatteryState {
            val checked = persistCheckedState(state)
            if (!command.automatic) {
                SonyPodsBatteryWidgetUpdater.syncReportedMode(
                    app,
                    AudioMode.fromListeningMode(checked.listeningMode)
                )
                SonyPodsBatteryWidgetUpdater.updateAll(app)
            }
            command.onComplete?.invoke()
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
                    return finishManualRefresh(BatteryStore.load(app))
                }
                null -> {
                    DiagnosticsStore.add(app, "A2DP state unavailable; refresh skipped")
                    return finishManualRefresh(previous.copy(status = "Could not verify the earbud connection."))
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
            return if (command.automatic) {
                command.onComplete?.invoke()
                BatteryStore.load(app)
            } else {
                finishManualRefresh(BatteryStore.load(app))
            }
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
