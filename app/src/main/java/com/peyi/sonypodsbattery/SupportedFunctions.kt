package com.peyi.sonypodsbattery

/**
 * The earbuds' own list of what they can do (Sony's "support function"), per command table.
 * Sound Connect asks for it on every connection and builds its screens from it:
 *   GET 06 00  ->  RET 07 00 n {function, x}*n     (table 1 on frame type 0x0c, table 2 on 0x0e)
 * Function codes are Sound Connect's FunctionType values; the second byte of each pair isn't used.
 * The list can differ by connection: the WF-1000XM5 lists fewer functions over LE Audio.
 */
internal data class SupportedFunctions(val table1: Set<Int>, val table2: Set<Int>?) {
    fun has1(vararg codes: Int) = codes.any { it in table1 }
    /** Table 2 features count as there when the earbuds didn't answer for table 2 (an older pair). */
    fun has2(vararg codes: Int) = table2 == null || codes.any { it in table2 }
    fun has2Any(codes: IntRange) = table2 == null || codes.any { it in table2 }
    /** The NC/ASM inquired type the listed noise control function is driven on. */
    val ncAsmType: Int? get() = table1.firstNotNullOfOrNull(Fn::ncAsmType)

    /** Sorted hex, for saving with the settings: "29 2a 6b ..." */
    fun encode1(): String = table1.sorted().joinToString(" ") { "%02x".format(it) }
    fun encode2(): String? = table2?.sorted()?.joinToString(" ") { "%02x".format(it) }

    companion object {
        private const val GET = 0x06
        private const val RET = 0x07

        /** Parses a RET 07 00 n {code x}*n; null if it isn't one, as Sound Connect checks it. */
        fun parse(p: ByteArray?): Set<Int>? {
            if (p == null || p.size < 3 || (p[0].toInt() and 0xff) != RET || p[1].toInt() != 0) return null
            val n = p[2].toInt() and 0xff
            if (p.size != n * 2 + 3) return null
            return (0 until n).map { p[3 + it * 2].toInt() and 0xff }.filter { it != 0 }.toSet()
        }

        fun decode(hex: String?): Set<Int>? = hex?.split(' ')?.filter { it.isNotBlank() }?.map { it.toInt(16) }?.toSet()

        fun read(s: SonyBatteryClient.SonySession): SupportedFunctions? {
            val t1 = parse(s.request(byteArrayOf(GET.toByte(), 0x00), timeoutMs = 1_000)) ?: return null
            val t2 = parse(s.request(byteArrayOf(GET.toByte(), 0x00), timeoutMs = 800, type = SonyProtocol.DATA_MDR_NO2))
            return SupportedFunctions(t1, t2)
        }
    }
}

/**
 * Sound Connect FunctionType codes SunPods acts on (com.sony.songpal.tandemfamily.message.mdr.v2.FunctionType).
 * Table 1 unless the name says T2.
 */
internal object Fn {
    // Battery (POWER inquired type = code - 0x20)
    const val BATTERY = 0x20
    const val LR_BATTERY = 0x21
    const val CRADLE_BATTERY = 0x22
    const val POWER_OFF = 0x23
    const val AUTO_POWER_OFF = 0x24
    const val AUTO_POWER_OFF_WEARING = 0x25
    const val BATTERY_THRESHOLD = 0x28
    const val LR_BATTERY_THRESHOLD = 0x29
    const val CRADLE_BATTERY_THRESHOLD = 0x2a

    // Noise cancelling and ambient sound: 0x61..0x6d, mapped to an NC/ASM inquired type below.
    val NC_ASM = 0x61..0x6d

    const val CLASSIC_LE_SETTING = 0x42
    const val PRESET_EQ = 0x50
    const val PRESET_EQ_NON_CUSTOMIZABLE = 0x52
    const val CUSTOM_EQ = 0x55
    const val CODEC_INDICATOR = 0x12
    const val UPSCALING = 0x13          // indicator
    const val UPSCALING_AUTO_OFF = 0xe2
    const val UPSCALING_WITH_REASON = 0xed
    const val CONNECTION_MODE_CLASSIC_LE = 0xe7
    const val PLAYBACK_BY_WEARING = 0xf1
    const val SPEAK_TO_CHAT_1 = 0xf2
    const val SPEAK_TO_CHAT_2 = 0xfc
    const val ASSIGNABLE = 0xf3
    const val ASSIGNABLE_LIMITED = 0xfe
    const val VOICE_ASSISTANT = 0xf4
    const val WEARING_STATUS = 0xf6     // earbud fit test
    const val RESET = 0xf9
    const val HEAD_GESTURE = 0xff
    const val AUTO_PLAY = 0xb1
    const val INTEGRATED_AUTO_PLAY = 0xb8

    // Table 2
    val T2_VOICE_GUIDANCE = 0x40..0x48
    val T2_SAFE_LISTENING = 0x50..0x53
    const val T2_SAFE_VOLUME = 0x54
    val T2_PAIRING_MANAGEMENT = 0x30..0x33
    const val T2_LINK_AUTO_SWITCH = 0xf8
    const val T2_LINK_AUTO_SWITCH_SPEAKER = 0xf7
    const val T2_LINK_AUTO_SWITCH_NOT_LE = 0x6f

    /**
     * Noise control function to the NC/ASM inquired type it's driven on (NcAsmInquiredType):
     * the WF-1000XM5 lists 0x6b and answers on 0x17.
     */
    fun ncAsmType(code: Int): Int? = when (code) {
        0x61 -> 0x01; 0x62 -> 0x11; 0x63 -> 0x12; 0x64 -> 0x13; 0x65 -> 0x14
        0x66 -> 0x21; 0x67 -> 0x22; 0x68 -> 0x15; 0x6a -> 0x16; 0x6b -> 0x17
        0x6c -> 0x18; 0x6d -> 0x19
        else -> null
    }
}
