package com.peyi.sonypodsbattery

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

/*
 * Simulated Sony earbuds that speak MDR v2 over an in-memory link, so every protocol path can be
 * exercised without the earbuds: by the unit tests, and by the debug build's simulated earbuds
 * (see SimMode). A profile is the earbuds' replies, keyed by table and [command, inquired type];
 * the WF-1000XM5 one is made of replies recorded from real earbuds.
 *
 * Behaviour, as the real earbuds do it:
 *  - every data frame is ACKed (sequence = 1 - the frame's);
 *  - GET c [t ...] answers RET c+1 [t ...] from the current state; an unknown one gets only the ACK;
 *  - SET c [t ...] updates RET c-1 [t ...] (same layout unless the profile says otherwise) and
 *    announces it with NTFY c+1 [t ...];
 *  - changes the earbuds only make after confirming (LE Audio, multipoint) send alert 99 [00 code]
 *    when the app has switched alerts on (94 00 00), apply on the answer 98 [00 code 01], and are
 *    dropped without it.
 */

/** Command kinds in Sony's tables (Sound Connect Command enums), for telling GETs from SETs. */
private object SimCommands {
    private fun set(hex: String) = hex.split(' ').map { it.toInt(16) }.toSet()
    val GET1 = set("00 02 04 06 10 12 20 22 26 30 32 36 40 42 46 4a 50 52 56 5a 60 62 66 70 7a 80 82 86 90 92 a0 a2 a6 b0 b2 b6 d0 d2 d6 e0 e2 e6 f0 f2 f6 fa")
    val SET1 = set("18 24 28 34 38 48 4c 58 64 68 74 78 84 88 94 98 a4 a8 b8 c4 d8 e8 f4 f8 fc")
    val GET2 = set("06 20 22 26 30 32 36 40 42 46 4a 50 52 56 5a 60 62 66 70 72 76 f0 f2 f6 fa")
    val SET2 = set("24 28 34 38 3c 44 48 54 58 68 74 78 7c f4 f8 fc")
    val NTFY1 = set("15 19 25 29 35 39 3f 45 49 4d 55 59 65 69 75 79 85 89 95 99 a5 a9 b5 b9 c9 d5 e5 e9 f5 f9 fd")
    val NTFY2 = set("25 29 35 39 3d 45 49 55 59 65 69 75 79 f5 f9 fd")

    fun isGet(table: Int, c: Int) = c in (if (table == SonyProtocol.DATA_MDR) GET1 else GET2)
    fun isSet(table: Int, c: Int) = c in (if (table == SonyProtocol.DATA_MDR) SET1 else SET2)
    fun isNotify(table: Int, c: Int) = c in (if (table == SonyProtocol.DATA_MDR) NTFY1 else NTFY2)
}

internal class SimProfile(
    val model: EarbudModel,
    /** Replies as recorded, "table hex..." per line (table 12 or 14, i.e. 0x0c or 0x0e). */
    recorded: String,
    /** For a SET the earbuds apply only after an alert is confirmed, the alert code; null otherwise. */
    val confirm: (state: SimState, table: Int, payload: ByteArray) -> Int? = { _, _, _ -> null },
    /** True for a confirmed change after which the earbuds drop the link (a switch to or from LE Audio). */
    val dropsAfter: (table: Int, payload: ByteArray) -> Boolean = { _, _ -> false },
    /**
     * Handles a SET whose effect isn't a plain copy into the reply; returns true when handled.
     * Receives the state to change and the SET payload.
     */
    val onSet: (state: SimState, table: Int, payload: ByteArray) -> Boolean = { _, _, _ -> false },
) {
    /** Initial RET payloads by key; NTFY recordings fill in any RET that wasn't captured. */
    val initial: Map<Int, ByteArray> = buildMap {
        val lines = recorded.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        val parsed = lines.map { line ->
            val parts = line.split(Regex("\\s+"))
            val table = if (parts[0] == "14") SonyProtocol.DATA_MDR_NO2 else SonyProtocol.DATA_MDR
            table to ByteArray(parts.size - 1) { parts[it + 1].toInt(16).toByte() }
        }
        for ((table, p) in parsed) {
            if (p.size < 2) continue
            val c = p[0].toInt() and 0xff
            if (!SimCommands.isNotify(table, c)) put(SimState.key(table, c, p[1].toInt() and 0xff), p)
        }
        // A NTFY carries the same fields as its RET (c - 2): use it where the RET is missing or empty.
        for ((table, p) in parsed) {
            if (p.size < 3) continue
            val c = p[0].toInt() and 0xff
            if (!SimCommands.isNotify(table, c)) continue
            val k = SimState.key(table, c - 2, p[1].toInt() and 0xff)
            if ((get(k)?.size ?: 0) <= 2) put(k, byteArrayOf((c - 2).toByte()) + p.copyOfRange(1, p.size))
        }
    }
}

/** The simulated earbuds' current replies, which SETs change. */
internal class SimState(initial: Map<Int, ByteArray>) {
    private val replies = initial.toMutableMap()

    operator fun get(table: Int, ret: Int, inquired: Int): ByteArray? = replies[key(table, ret, inquired)]

    operator fun set(table: Int, ret: Int, inquired: Int, payload: ByteArray) {
        replies[key(table, ret, inquired)] = payload
    }

    fun remove(table: Int, ret: Int, inquired: Int) { replies.remove(key(table, ret, inquired)) }

    companion object {
        fun key(table: Int, command: Int, inquired: Int) = (table shl 16) or (command shl 8) or inquired
    }
}

internal class SimulatedEarbuds(val profile: SimProfile, val state: SimState = SimState(profile.initial)) : SonyLink {
    /** Every data payload the app sent, in order, with its table. */
    val received = mutableListOf<Pair<Int, ByteArray>>()
    /** Frames the earbuds will send by themselves on the next read (notifications). */
    private val toApp = Queue()
    private val decoder = SonyProtocol.Decoder()
    private var sequence = 0
    private var alertsOn = false
    private var pending: Pair<Int, ByteArray>? = null
    @Volatile private var connected = true
    /** Set when the app turned the earbuds off. */
    @Volatile var poweredOff = false
        private set

    override val label = "SIM"
    override val isConnected: Boolean get() = connected
    override val input: InputStream get() = toApp
    override val output: OutputStream = object : OutputStream() {
        private val pendingBytes = ByteArrayOutputStream()
        override fun write(b: Int) { pendingBytes.write(b) }
        override fun write(b: ByteArray, off: Int, len: Int) { pendingBytes.write(b, off, len) }
        override fun flush() {
            val bytes = pendingBytes.toByteArray()
            pendingBytes.reset()
            synchronized(this@SimulatedEarbuds) { decoder.feed(bytes).forEach(::onFrame) }
        }
    }

    override fun close() { connected = false; toApp.closed = true }

    /** Sends a notification as if the earbuds made a change themselves (a tap, an earbud taken out). */
    @Synchronized fun notifyApp(table: Int, payload: ByteArray) = sendData(table, payload)

    /** Drops the link, as the earbuds do when they switch mode or switch off. */
    fun drop() = close()

    private fun onFrame(frame: SonyFrame) {
        if (frame.type == SonyProtocol.ACK) return
        if (frame.type != SonyProtocol.DATA_MDR && frame.type != SonyProtocol.DATA_MDR_NO2) return
        toApp.push(SonyProtocol.encode(SonyProtocol.ACK, 1 - frame.sequence, byteArrayOf()))
        val p = frame.payload
        received += frame.type to p
        if (p.isEmpty()) return
        val table = frame.type
        val c = p[0].toInt() and 0xff
        val t = if (p.size > 1) p[1].toInt() and 0xff else 0
        when {
            table == SonyProtocol.DATA_MDR && c == 0x94 -> alertsOn = p.size >= 3 && p[2].toInt() == 0
            table == SonyProtocol.DATA_MDR && c == 0x98 -> {
                val held = pending
                pending = null
                if (held != null && p.size >= 4 && p[3].toInt() == 1) {
                    apply(held.first, held.second)
                    if (profile.dropsAfter(held.first, held.second)) drop()
                }
            }
            table == SonyProtocol.DATA_MDR && c == 0x24 && t == 0x03 -> { poweredOff = true; drop() }
            SimCommands.isGet(table, c) -> state[table, c + 1, t]?.let { sendData(table, it) }
            SimCommands.isSet(table, c) -> {
                val alert = profile.confirm(state, table, p)
                if (alert == null) apply(table, p)
                else if (alertsOn) { pending = table to p; sendData(SonyProtocol.DATA_MDR, byteArrayOf(0x99.toByte(), 0x00, alert.toByte())) }
                // Without alerts on, the earbuds ACK the change and quietly drop it.
            }
        }
    }

    private fun apply(table: Int, p: ByteArray) {
        if (profile.onSet(state, table, p)) return
        val c = p[0].toInt() and 0xff
        val t = if (p.size > 1) p[1].toInt() and 0xff else 0
        if (state[table, c - 1, t] == null) return
        val ret = byteArrayOf((c - 1).toByte()) + p.copyOfRange(1, p.size)
        state[table, c - 1, t] = ret
        sendData(table, byteArrayOf((c + 1).toByte()) + p.copyOfRange(1, p.size))
    }

    private fun sendData(table: Int, payload: ByteArray) {
        toApp.push(SonyProtocol.encode(table, sequence, payload))
        sequence = 1 - sequence
    }

    /** Bytes for the app to read, without blocking the way the session expects. */
    private class Queue : InputStream() {
        private val buf = ArrayDeque<Byte>()
        @Volatile var closed = false

        @Synchronized fun push(data: ByteArray) { data.forEach { buf.addLast(it) } }
        @Synchronized override fun available(): Int = buf.size
        @Synchronized override fun read(): Int = if (buf.isEmpty()) -1 else buf.removeFirst().toInt() and 0xff
        @Synchronized override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (buf.isEmpty()) return if (closed) -1 else 0
            val n = minOf(len, buf.size)
            repeat(n) { b[off + it] = buf.removeFirst() }
            return n
        }
    }
}

/**
 * Debug builds only: SunPods runs against simulated earbuds instead of real ones, so every screen
 * can be tried on an emulator or a phone without the earbuds. Turn on with
 *   adb shell run-as com.peyi.sonypodsbattery ... sunpods_ui.xml: <string name="sim_model">c710n</string>
 * (or the debug command receiver). Settings changes persist across reconnects for the process.
 */
internal object SimMode {
    /** A made-up address (locally administered), never a real device. */
    const val ADDRESS = "02:5E:00:00:71:0A"
    @Volatile private var state: Pair<EarbudModel, SimState>? = null

    internal fun forgetState() { state = null }

    fun model(context: android.content.Context): EarbudModel? {
        if (!BuildConfig.TOOLS) return null
        return EarbudModel.fromId(context.getSharedPreferences("sunpods_ui", android.content.Context.MODE_PRIVATE).getString("sim_model", null))
    }

    fun active(context: android.content.Context): Boolean = model(context) != null

    @android.annotation.SuppressLint("MissingPermission")
    fun device(context: android.content.Context): android.bluetooth.BluetoothDevice? {
        if (!active(context)) return null
        return runCatching { context.getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter?.getRemoteDevice(ADDRESS) }.getOrNull()
    }

    /** A fresh link to the simulated earbuds, keeping their settings from the last one. */
    fun link(context: android.content.Context): SimulatedEarbuds? {
        val model = model(context) ?: return null
        val color = context.getSharedPreferences("sunpods_ui", android.content.Context.MODE_PRIVATE).getInt("sim_color", 0x01)
        val profile = SimProfiles.forModel(model, color)
        val kept = state?.takeIf { it.first == model && it.second[SonyProtocol.DATA_MDR, 0x05, 0x03]?.getOrNull(3)?.toInt() == color }?.second
            ?: SimState(profile.initial).also { state = model to it }
        return SimulatedEarbuds(profile, kept)
    }
}

/** Debug demo (TOOLS builds): switch the simulated earbuds right now, no restart, setup stays open. */
internal fun SimMode.startDemo(context: android.content.Context, model: EarbudModel) {
    if (!BuildConfig.TOOLS) return
    context.getSharedPreferences("sunpods_ui", android.content.Context.MODE_PRIVATE).edit()
        .putString("sim_model", model.id).putInt("sim_color", 0x01).apply()
    resetDemoDevice(context)
}

/** End the demo pair and forget the pick, for "Run Setup Again". */
internal fun SimMode.stopDemo(context: android.content.Context) {
    if (!BuildConfig.TOOLS) return
    context.getSharedPreferences("sunpods_ui", android.content.Context.MODE_PRIVATE).edit()
        .remove("sim_model").remove("chosen_model").apply()
    resetDemoDevice(context)
}

private fun resetDemoDevice(context: android.content.Context) {
    resetDeviceState(context, "demo: another simulated model")
    SimMode.forgetState()
}
