package com.peyi.sonypodsbattery

import android.content.Context

/**
 * The Sony earbuds SunPods supports, and what differs between them that the earbuds can't tell us
 * themselves (ratings, names, art). What each pair can do comes from the earbuds' own list of
 * supported functions ([SupportedFunctions]), the way Sound Connect builds its screens.
 */
enum class EarbudModel(
    val id: String,
    val displayName: String,
    private val pattern: Regex,
    /** Sony's rated listening time per charge, hours: noise cancelling on, and off. */
    val ratedHoursNc: Double,
    val ratedHoursOff: Double,
    /** Roughly how many full earbud charges a full case holds (Sony's total minus one charge); 0 without a charging case. */
    val caseCharges: Double,
    /** Over-ear headphones: one battery, no charging case, buttons and a touch panel instead of per-side touch. */
    val headphones: Boolean = false,
    /** The product line it belongs to, for grouping in the device picker: "1000X", "C", "LinkBuds", "ULT"... */
    val series: String = "",
) {
    WF_1000XM5("xm5", "WF-1000XM5", Regex("WF[- ]?1000XM5", RegexOption.IGNORE_CASE), 8.0, 12.0, 2.0, series = "1000X"),
    // Sony: 8.5 h NC on / 12 h off, about 30 h in total with the case (21.5 h in the case).
    WF_C710N("c710n", "WF-C710N", Regex("WF[- ]?C710N", RegexOption.IGNORE_CASE), 8.5, 12.0, 2.5, series = "C"),
    // Sony: 30 h with noise cancelling, 40 h without; no charging case.
    WH_1000XM5("wh1000xm5", "WH-1000XM5", Regex("WH[- ]?1000XM5", RegexOption.IGNORE_CASE), 30.0, 40.0, 0.0, headphones = true, series = "1000X");

    /** What to call them in copy: "earbuds" or "headphones". */
    val noun: String get() = if (headphones) "headphones" else "earbuds"
    /** "Earbuds" or "Headphones", for titles. */
    val titleNoun: String get() = if (headphones) "Headphones" else "Earbuds"
    /** The series as shown in lists: "1000X Series", "C Series". */
    val seriesName: String get() = "$series Series"
    /** How to get them to connect: "Open the case near your phone" or "Turn them on near your phone". */
    val wakeHint: String get() = if (headphones) "Turn them on near your phone" else "Open the case near your phone"

    /** The numbered steps for putting them in pairing mode. */
    val pairSteps: List<String> get() = if (headphones) listOf(
        "Turn the headphones off.",
        "Press and hold the power button for about 5 seconds, until the light flashes blue."
    ) else listOf(
        "Put both earbuds in the case and keep the lid open.",
        "Hold the button on the back for 5 seconds, until the light flashes blue."
    )
    /** What the pairing clip shows, for screen readers. */
    val pairClipDescription: String get() = if (headphones) "The power button is held down, then the light flashes blue."
        else "The case turns round, the button on the back is held down, then the light on the front flashes blue."
    /** What to check when searching or pairing doesn't work. */
    val lightHint: String get() = if (headphones) "the light is flashing blue" else "the case light is flashing blue"

    /** Only this model, for Android's device picker. */
    val namePattern: java.util.regex.Pattern get() =
        java.util.regex.Pattern.compile(".*(" + pattern.pattern + ").*", java.util.regex.Pattern.CASE_INSENSITIVE)

    /** The batteries these report, in the order they're shown: left, right and case, or one for headphones. */
    val batteries: List<BatteryPart> get() = if (headphones) HEADPHONE_BATTERIES else EARBUD_BATTERIES
    /** The batteries you listen on (no case). */
    val buds: List<BatteryPart> get() = if (headphones) HEADPHONE_BATTERIES else EARBUD_BUDS

    fun matches(name: String?): Boolean = name != null && pattern.containsMatchIn(name)

    /** Android's name for the LE Audio side of these earbuds ("LE_WF-1000XM5"). */
    fun matchesLe(name: String?): Boolean = name != null && name.startsWith("LE_") && matches(name)

    companion object {
        fun fromName(name: String?): EarbudModel? = entries.firstOrNull { it.matches(name) }

        fun fromId(id: String?): EarbudModel? = entries.firstOrNull { it.id == id }

        /** Any pair SunPods supports. */
        fun isSupported(name: String?): Boolean = fromName(name) != null

        /**
         * The pair SunPods is set up for, as last read from the earbuds (their own model name), so
         * code without a Context, like the battery maths, uses the right ratings. WF-1000XM5 until
         * the earbuds have said otherwise.
         */
        @Volatile var active: EarbudModel = WF_1000XM5
            private set

        fun noteModelName(name: String?) {
            fromName(name)?.let { active = it }
        }

        fun current(context: Context): EarbudModel {
            val reported = fromName(SettingsStore.modelName(context))
            if (reported != null) active = reported else chosen(context)?.let { active = it }
            return active
        }

        private const val PREFS = "sunpods_ui"
        private const val CHOSEN = "chosen_model"

        /** The model picked in setup, if any. */
        fun chosen(context: Context): EarbudModel? =
            fromId(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(CHOSEN, null))

        /** Remember the pick from setup; until a device reports its own model, everything follows it. */
        fun choose(context: Context, model: EarbudModel) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(CHOSEN, model.id).apply()
            active = model
        }

        /** The outcome of weighing what someone picked against what a connected device says it is. */
        data class Resolution(val model: EarbudModel, val switchedFrom: EarbudModel?)

        /** The device knows best: a different detected model wins, and says what it replaced. */
        fun resolve(chosen: EarbudModel?, detected: EarbudModel?): Resolution = when {
            detected == null -> Resolution(chosen ?: WF_1000XM5, null)
            chosen == null || chosen == detected -> Resolution(detected, null)
            else -> Resolution(detected, chosen)
        }

        /** Names to look for when scanning for or picking earbuds to set up. */
        val scanPattern: java.util.regex.Pattern = java.util.regex.Pattern.compile(
            ".*(" + entries.joinToString("|") { it.pattern.pattern } + ").*", java.util.regex.Pattern.CASE_INSENSITIVE)

    }
}

/**
 * One battery a model reports. Headphones have one, MAIN, which travels in the left slot of
 * BatteryState and BatterySample (the Sony reply has one level where earbuds have two).
 */
enum class BatteryPart(val label: String, val short: String, val missing: String) {
    LEFT("Left", "L", "In Case"), RIGHT("Right", "R", "In Case"), CASE("Case", "Case", "Unknown"), MAIN("Battery", "Battery", "Unknown");

    fun level(s: BatteryState): Int? = when (this) { LEFT, MAIN -> s.left; RIGHT -> s.right; CASE -> s.case }
    fun charging(s: BatteryState): Boolean = when (this) { LEFT, MAIN -> s.leftCharging; RIGHT -> s.rightCharging; CASE -> s.caseCharging }
    fun level(s: BatterySample): Int? = when (this) { LEFT, MAIN -> s.left; RIGHT -> s.right; CASE -> s.case }
}

private val EARBUD_BATTERIES = listOf(BatteryPart.LEFT, BatteryPart.RIGHT, BatteryPart.CASE)
private val EARBUD_BUDS = listOf(BatteryPart.LEFT, BatteryPart.RIGHT)
private val HEADPHONE_BATTERIES = listOf(BatteryPart.MAIN)

/** One series in the device picker's full list. */
internal data class SeriesGroup(val series: String, val models: List<EarbudModel>) {
    val title: String get() = models.first().seriesName
}

/** Whether [model] matches what was typed: name, series or kind, ignoring case, spaces and dashes ("xm4", "wf c7", "headphones"). */
internal fun EarbudModel.matchesQuery(query: String): Boolean {
    fun flat(s: String) = s.filter { it.isLetterOrDigit() }.lowercase()
    val q = flat(query)
    return q.isEmpty() || listOf(displayName, series, titleNoun).any { flat(it).contains(q) }
}

/**
 * Every model that matches [query], grouped by series in the order the models are declared, so a
 * new model needs no list code. [family]: null for all, true for headphones only, false for earbuds only.
 */
internal fun groupBySeries(query: String = "", family: Boolean? = null, models: List<EarbudModel> = EarbudModel.entries): List<SeriesGroup> =
    models.filter { (family == null || it.headphones == family) && it.matchesQuery(query) }
        .groupBy { it.series }
        .map { (s, m) -> SeriesGroup(s, m) }
