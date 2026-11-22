package com.peyi.sonypodsbattery

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * Back up everything you'd miss on a new phone to one JSON file you keep (Files, Drive, a USB
 * stick: wherever the system file picker can save), and restore it into a fresh install.
 * SunPods has no internet permission; the file only goes where you put it.
 *
 * Included: appearance, automations and their Wi-Fi places, battery alerts, quiet hours,
 * Last seen (on/off and the saved spot), the last known earbud settings, battery history and
 * the long-term battery health log.
 *
 * Left out on purpose: live state that would be wrong on another phone (connection state,
 * current levels, charge guesses, diagnostics, widget layout), demo mode, and "already asked"
 * flags so the new install asks for its permissions properly.
 */
internal object Backup {
    private const val FORMAT = "sunpods-backup"
    private const val VERSION = 1

    /** Preference files backed up, and keys inside them that stay behind. */
    private val PREFS: Map<String, Set<String>> = mapOf(
        "ui" to emptySet(),
        "sunpods_ui" to setOf("demo_connected", "asked_battery_unrestricted", "battery_card_snoozed_until", "onboarding_done"),
        "automations" to setOf("active_label"),
        "battery_nudges" to emptySet(),
        "quiet_hours" to setOf("silenced", "restore", "override"),
        "last_seen" to emptySet(),
        "device_settings" to emptySet()
    )
    private val FILES = listOf("battery_history.csv", "battery_health.csv")

    data class Summary(val exportedAt: Long, val appVersion: String, val historyRows: Int, val healthRows: Int, val settings: Int)

    // ------------------------------------------------------------------ export

    fun export(context: Context): String {
        val prefs = JSONObject()
        for ((name, skip) in PREFS) {
            val o = JSONObject()
            context.getSharedPreferences(name, Context.MODE_PRIVATE).all.forEach { (k, v) ->
                if (k in skip || v == null) return@forEach
                val typed = when (v) {
                    is Boolean -> JSONObject().put("t", "b").put("v", v)
                    is Int -> JSONObject().put("t", "i").put("v", v)
                    is Long -> JSONObject().put("t", "l").put("v", v)
                    is Float -> JSONObject().put("t", "f").put("v", v.toDouble())
                    is String -> JSONObject().put("t", "s").put("v", v)
                    is Set<*> -> JSONObject().put("t", "ss").put("v", org.json.JSONArray(v.map { it.toString() }))
                    else -> null
                }
                if (typed != null) o.put(k, typed)
            }
            if (o.length() > 0) prefs.put(name, o)
        }
        val files = JSONObject()
        for (f in FILES) File(context.filesDir, f).takeIf { it.exists() }?.let { files.put(f, it.readText()) }
        return JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("app", SunpodsInfo.VERSION)
            .put("exportedAt", System.currentTimeMillis())
            .put("prefs", prefs)
            .put("files", files)
            .toString(1)
    }

    fun suggestedName(): String = "SunPods-backup-" + SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date()) + ".json"

    fun write(context: Context, uri: Uri, text: String) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            ?: throw IllegalStateException("Couldn't save to this file. Choose another location.")
    }

    fun read(context: Context, uri: Uri): String =
        context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: throw IllegalStateException("Couldn't open this file. Try again.")

    // ------------------------------------------------------------------ import

    /** Checks the file and says what's in it, without changing anything. */
    fun inspect(text: String): Summary {
        val o = runCatching { JSONObject(text) }.getOrNull() ?: throw IllegalArgumentException("This file isn't a SunPods backup. Choose a file that starts with SunPods-backup.")
        if (o.optString("format") != FORMAT) throw IllegalArgumentException("This file isn't a SunPods backup. Choose a file that starts with SunPods-backup.")
        if (o.optInt("version") > VERSION) throw IllegalArgumentException("This backup is from a newer version of SunPods. Update SunPods, then try again.")
        val files = o.optJSONObject("files") ?: JSONObject()
        fun rows(name: String) = files.optString(name).lineSequence().count { it.isNotBlank() }
        val prefs = o.optJSONObject("prefs") ?: JSONObject()
        val settings = prefs.keys().asSequence().sumOf { prefs.getJSONObject(it).length() }
        return Summary(o.optLong("exportedAt"), o.optString("app"), rows("battery_history.csv"), rows("battery_health.csv"), settings)
    }

    /**
     * Restores settings (replacing them) and merges history (keeping anything this install
     * recorded that the backup doesn't have). Returns the summary of what was restored.
     */
    fun restore(context: Context, text: String): Summary {
        val summary = inspect(text)
        val o = JSONObject(text)
        val prefs = o.optJSONObject("prefs") ?: JSONObject()
        for ((name, skip) in PREFS) {
            val saved = prefs.optJSONObject(name) ?: continue
            val p = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            val e = p.edit()
            p.all.keys.filter { it !in skip }.forEach { e.remove(it) }
            saved.keys().forEach { k ->
                if (k in skip) return@forEach
                val item = saved.getJSONObject(k)
                when (item.optString("t")) {
                    "b" -> e.putBoolean(k, item.getBoolean("v"))
                    "i" -> e.putInt(k, item.getInt("v"))
                    "l" -> e.putLong(k, item.getLong("v"))
                    "f" -> e.putFloat(k, item.getDouble("v").toFloat())
                    "s" -> e.putString(k, item.getString("v"))
                    "ss" -> item.getJSONArray("v").let { a -> e.putStringSet(k, (0 until a.length()).map(a::getString).toSet()) }
                }
            }
            e.commit()
        }
        val files = o.optJSONObject("files") ?: JSONObject()
        for (name in FILES) {
            val incoming = files.optString(name).takeIf { it.isNotBlank() } ?: continue
            val f = File(context.filesDir, name)
            val current = if (f.exists()) f.readLines() else emptyList()
            // Union of both, in time order (first field is the time; health lines lead with a tag).
            val merged = (current + incoming.lines()).filter { it.isNotBlank() }.distinct()
                .sortedBy { line -> line.split(",").let { if (it[0].length == 1) it.getOrNull(1) else it[0] }?.toLongOrNull() ?: 0L }
            f.writeText(merged.joinToString("\n", postfix = "\n"))
        }
        // Make the health log re-read on next use.
        context.getSharedPreferences("battery_health", Context.MODE_PRIVATE).edit().putLong("updated", 0L).commit()
        DiagnosticsStore.add(context, "Restored a backup from SunPods ${summary.appVersion}")
        return summary
    }

    /** Starts SunPods afresh so every screen, widget and automation picks up the restored data. */
    fun restart(context: Context) {
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }

    // ------------------------------------------------------------------ CSV for spreadsheets

    fun historyCsv(context: Context): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
        val codec = mapOf('L' to "LDAC", 'A' to "AAC", 'S' to "SBC", 'C' to "LC3", 'X' to "Other")
        val mode = mapOf('N' to "Noise cancelling", 'A' to "Ambient", 'O' to "Off")
        fun yn(b: Boolean?) = when (b) { true -> "yes"; false -> "no"; null -> "" }
        val header = "Time,Left %,Right %,Case %,Left charging,Right charging,Case charging,Noise mode,Playing,Volume %,Codec,DSEE,Speak-to-Chat,Ambient level"
        val rows = BatteryHistory.load(context).map { s ->
            listOf(
                fmt.format(Date(s.time)), s.left ?: "", s.right ?: "", s.case ?: "",
                yn(s.leftCharging), yn(s.rightCharging), yn(s.caseCharging),
                s.mode?.let { mode[it] } ?: "", yn(s.playing), s.volume ?: "", s.codec?.let { codec[it] } ?: "",
                yn(s.dsee), yn(s.speakToChat), s.ambient ?: ""
            ).joinToString(",")
        }
        return (listOf(header) + rows).joinToString("\n", postfix = "\n")
    }
}
