package com.peyi.sonypodsbattery

import android.content.Context
import java.io.File

/**
 * The earbuds' latest reply to each kind of question, for adding support for a new model from a
 * tester's diagnostics. Lines are in the format SimProfile loads ("12 05 01 ..."), so a tester's
 * report becomes simulated earbuds for the tests. Nothing personal goes in: paired device names,
 * now-playing titles and serial numbers are skipped, and Bluetooth addresses are masked.
 */
internal object ProtocolCapture {
    private const val FILE = "earbud_replies.txt"
    private const val MAX = 200
    private val replies = LinkedHashMap<Int, String>()
    /** The last raw payload per key, so a repeated reply (every live check) costs one comparison. */
    private val last = HashMap<Int, ByteArray>()
    @Volatile private var dir: File? = null
    private val saving = java.util.concurrent.atomic.AtomicBoolean(false)
    private val ADDRESS = Regex("([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}")
    private val T2_PERSONAL = setOf(0x37, 0x39, 0x4b)   // paired devices, serials
    private val T1_TITLES = setOf(0xa7, 0xa9)           // now playing titles

    fun attach(context: Context) {
        if (dir != null) return
        dir = context.filesDir
        synchronized(replies) {
            runCatching { File(context.filesDir, FILE).readLines() }.getOrNull()?.forEach { line ->
                val p = line.split(' ')
                if (p.size >= 3) replies[key(p[0].toInt(), p[1].toInt(16), p[2].toInt(16))] = line
            }
        }
    }

    fun record(table: Int, payload: ByteArray) {
        if (payload.size < 2 || skip(table, payload)) return
        val c = payload[0].toInt() and 0xff
        val t = payload[1].toInt() and 0xff
        val k = key(table, c, t)
        synchronized(replies) { if (last[k]?.contentEquals(payload) == true) return }
        val line = "${if (table == SonyProtocol.DATA_MDR_NO2) 14 else 12} " + mask(payload).joinToString(" ") { "%02x".format(it.toInt() and 0xff) }
        val isNew: Boolean
        synchronized(replies) {
            last[k] = payload.copyOf()
            isNew = !replies.containsKey(k)
            replies.remove(k)
            replies[k] = line
            while (replies.size > MAX) { val old = replies.keys.first(); replies.remove(old); last.remove(old) }
        }
        if (isNew) saveSoon()
    }

    fun text(): String = synchronized(replies) {
        if (replies.isEmpty()) "" else replies.values.sorted().joinToString("\n", prefix = "Earbud replies (no personal data):\n")
    }

    /** Writes the file off the earbuds' thread, once for a burst of new replies (a full read brings ~40). */
    private fun saveSoon() {
        val d = dir ?: return
        if (!saving.compareAndSet(false, true)) return
        Thread {
            Thread.sleep(2_000)
            saving.set(false)
            val body = synchronized(replies) { replies.values.joinToString("\n") }
            runCatching { File(d, FILE).writeText(body) }
        }.apply { isDaemon = true }.start()
    }

    private fun key(table: Int, c: Int, t: Int) = (table shl 16) or (c shl 8) or t

    /** Replies that carry names, titles or serial numbers. */
    private fun skip(table: Int, p: ByteArray): Boolean {
        val c = p[0].toInt() and 0xff
        return if (table == SonyProtocol.DATA_MDR_NO2) c in T2_PERSONAL
        else c in T1_TITLES && (p[1].toInt() and 0xff) < 0x20
    }

    /** Bluetooth addresses written out as text ("AA:BB:CC:..") become 02:00:00:00:00:0n. */
    private fun mask(p: ByteArray): ByteArray {
        val s = String(p, Charsets.ISO_8859_1)
        var n = 0
        val masked = ADDRESS.replace(s) { "02:00:00:00:00:%02X".format(++n) }
        return masked.toByteArray(Charsets.ISO_8859_1)
    }
}
