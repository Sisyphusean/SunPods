package com.peyi.sonypodsbattery

/**
 * Whether a refresh should talk to the earbuds, given what Android reports about the audio link.
 * The paired entry has already been chosen (the one on A2DP wins), so this covers phones that report
 * a media or call link without listing the earbuds on A2DP at all.
 */
internal enum class LinkVerdict {
    /** The chosen entry is on A2DP. */
    USE_TARGET,
    /** Not listed, but Android says Bluetooth audio is up and the user asked: try the control channel. */
    TRY_ANYWAY,
    /** Android couldn't say. */
    UNKNOWN,
    /** Nothing is connected. */
    SKIP,
}

internal fun linkVerdict(targetOnA2dp: Boolean?, audioUp: Boolean, manual: Boolean): LinkVerdict = when {
    targetOnA2dp == true -> LinkVerdict.USE_TARGET
    manual && audioUp -> LinkVerdict.TRY_ANYWAY
    targetOnA2dp == null -> LinkVerdict.UNKNOWN
    else -> LinkVerdict.SKIP
}
