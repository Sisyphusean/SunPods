package com.peyi.sonypodsbattery

import android.bluetooth.BluetoothDevice
import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
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
        onComplete: (() -> Unit)? = null
    ) {
        SonyCommandCoordinator.enqueueRefresh(context, device, startPolling, onComplete)
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

    suspend fun awaitRefresh(
        context: Context,
        device: BluetoothDevice? = null,
        startPolling: Boolean = true
    ): BatteryState = SonyCommandCoordinator.awaitRefresh(context, device, startPolling)

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
        val postInteraction: Boolean = false
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
}

private object SonyCommandCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val commands = Channel<SonyCommand>(Channel.UNLIMITED)

    @Volatile
    private var workerStarted = false

    @Volatile
    private var postInteractionRefreshQueued = false

    fun enqueueRefresh(
        context: Context,
        device: BluetoothDevice?,
        startPolling: Boolean,
        onComplete: (() -> Unit)?
    ) {
        commands.trySend(
            SonyCommand.Refresh(
                context.applicationContext,
                device,
                startPolling,
                CompletableDeferred(),
                onComplete
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
                postInteraction = true
            )
        )
        startWorker()
    }

    suspend fun awaitRefresh(
        context: Context,
        device: BluetoothDevice?,
        startPolling: Boolean
    ): BatteryState {
        val completion = CompletableDeferred<BatteryState>()
        commands.send(
            SonyCommand.Refresh(
                context.applicationContext,
                device,
                startPolling,
                completion,
                null,
                false
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
        }
    }

    private suspend fun executeRefresh(command: SonyCommand.Refresh): BatteryState {
        if (command.postInteraction) postInteractionRefreshQueued = false
        val app = command.context.applicationContext
        val previous = BatteryStore.load(app)
        val client = SonyBatteryClient(app)
        DiagnosticsStore.add(app, "Refresh started")
        val target = command.device ?: client.findEarbuds()
        val state = if (target == null) {
            previous.copy(status = "Pair your WF-1000XM5 in Android settings first")
        } else try {
            val result = client.read(target)
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
        if (command.startPolling && state.status.startsWith("Updated")) BatteryPollingService.start(app)
        command.onComplete?.invoke()
        return state
    }

    private suspend fun executeSetMode(command: SonyCommand.SetMode): BatteryState {
        val app = command.context.applicationContext
        val previous = BatteryStore.load(app)
        val client = SonyBatteryClient(app)
        DiagnosticsStore.add(app, "Listening mode change requested: ${command.mode.name}")
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
        } catch (error: Exception) {
            DiagnosticsStore.add(app, "Listening mode failed: ${error.javaClass.simpleName}: ${error.message}")
            previous.copy(status = "Could not change mode. Close Sony Sound Connect and try again.")
        }

        BatteryStore.save(app, state)
        SonyPodsBatteryWidgetUpdater.syncConfirmedState(app, state, command.requestedAtMillis)
        BatteryNotifier.show(app, state)
        return state
    }

    private suspend fun executeAmbientLevel(command: SonyCommand.SetAmbientLevel): BatteryState {
        val app = command.context.applicationContext
        val previous = BatteryStore.load(app)
        val client = SonyBatteryClient(app)
        val level = command.ambientLevel.coerceIn(1, 20)
        DiagnosticsStore.add(app, "Ambient level change requested: $level")
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
        } catch (error: Exception) {
            DiagnosticsStore.add(app, "Ambient level failed: ${error.javaClass.simpleName}: ${error.message}")
            previous.copy(status = "Could not change ambient level. Close Sony Sound Connect and try again.")
        }

        BatteryStore.save(app, state)
        SonyPodsBatteryWidgetUpdater.syncConfirmedState(app, state, command.requestedAtMillis)
        BatteryNotifier.show(app, state)
        return state
    }
}
