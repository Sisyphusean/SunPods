package com.peyi.sonypodsbattery

/**
 * Whether a refresh should talk to the earbuds, given what Android reports about the audio link.
 * Phones differ here: some list the earbuds on A2DP under another paired entry (Classic and LE
 * Audio bond separately), and some report a media or call link without listing the earbuds at all.
 */
internal enum class LinkVerdict {
    /** The chosen entry is on A2DP. */
    USE_TARGET,
    /** Another paired entry of supported earbuds is on A2DP: use that one. */
    USE_OTHER_ENTRY,
    /** Not listed, but Android says Bluetooth audio is up and the user asked: try the control channel. */
    TRY_ANYWAY,
    /** Android couldn't say. */
    UNKNOWN,
    /** Nothing is connected. */
    SKIP,
}

internal fun linkVerdict(
    targetOnA2dp: Boolean?,
    otherEntryOnA2dp: Boolean,
    mediaUp: Boolean,
    callsUp: Boolean,
    manual: Boolean,
): LinkVerdict = when {
    targetOnA2dp == true -> LinkVerdict.USE_TARGET
    otherEntryOnA2dp -> LinkVerdict.USE_OTHER_ENTRY
    manual && (mediaUp || callsUp) -> LinkVerdict.TRY_ANYWAY
    targetOnA2dp == null -> LinkVerdict.UNKNOWN
    else -> LinkVerdict.SKIP
}
