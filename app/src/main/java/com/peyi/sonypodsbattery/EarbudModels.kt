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
    /** Roughly how many full earbud charges a full case holds (Sony's total minus one charge). */
    val caseCharges: Double,
) {
    WF_1000XM5("xm5", "WF-1000XM5", Regex("WF[- ]?1000XM5", RegexOption.IGNORE_CASE), 8.0, 12.0, 2.0),
    // Sony: 8.5 h NC on / 12 h off, about 30 h in total with the case (21.5 h in the case).
    WF_C710N("c710n", "WF-C710N", Regex("WF[- ]?C710N", RegexOption.IGNORE_CASE), 8.5, 12.0, 2.5);

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
            fromName(SettingsStore.load(context).modelName)?.let { active = it }
            return active
        }

        /** Names to look for when scanning for or picking earbuds to set up. */
        val scanPattern: java.util.regex.Pattern =
            java.util.regex.Pattern.compile(".*WF[- ]?(1000XM5|C710N).*", java.util.regex.Pattern.CASE_INSENSITIVE)

        /** "WF-1000XM5 or WF-C710N", for copy written before a pair is known. */
        val anyName: String get() = entries.joinToString(" or ") { it.displayName }
    }
}
