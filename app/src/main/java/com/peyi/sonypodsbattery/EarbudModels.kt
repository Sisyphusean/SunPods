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
) {
    WF_1000XM5("xm5", "WF-1000XM5", Regex("WF[- ]?1000XM5", RegexOption.IGNORE_CASE), 8.0, 12.0, 2.0),
    // Sony: 8.5 h NC on / 12 h off, about 30 h in total with the case (21.5 h in the case).
    WF_C710N("c710n", "WF-C710N", Regex("WF[- ]?C710N", RegexOption.IGNORE_CASE), 8.5, 12.0, 2.5),
    // Sony: 30 h with noise cancelling, 40 h without; no charging case.
    WH_1000XM5("wh1000xm5", "WH-1000XM5", Regex("WH[- ]?1000XM5", RegexOption.IGNORE_CASE), 30.0, 40.0, 0.0, headphones = true);

    /** What to call them in copy: "earbuds" or "headphones". */
    val noun: String get() = if (headphones) "headphones" else "earbuds"
    /** "Earbuds" or "Headphones", for titles. */
    val titleNoun: String get() = if (headphones) "Headphones" else "Earbuds"
    /** How to get them to connect: "Open the case near your phone" or "Turn them on near your phone". */
    val wakeHint: String get() = if (headphones) "Turn them on near your phone" else "Open the case near your phone"

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
            fromName(SettingsStore.modelName(context))?.let { active = it }
            return active
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
