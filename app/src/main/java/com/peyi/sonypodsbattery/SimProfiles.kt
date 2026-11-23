package com.peyi.sonypodsbattery

/*
 * Profiles for [SimulatedEarbuds].
 *
 * WF-1000XM5: replies recorded from real earbuds (firmware 6.1.0) in SunPods' debug logs, over
 * Classic and LE Audio. Bluetooth addresses are replaced with made-up ones; the paired-device list,
 * now-playing titles and serial numbers are left out.
 */
internal object SimProfiles {
    private const val T1 = SonyProtocol.DATA_MDR
    private const val T2 = SonyProtocol.DATA_MDR_NO2

    internal fun u(b: Byte) = b.toInt() and 0xff

    /** EQ: a preset-only SET (58 00 preset 00) keeps the bands the earbuds hold. */
    internal fun eqSet(state: SimState, table: Int, p: ByteArray): Boolean {
        if (table != T1 || u(p[0]) != 0x58 || p.size < 4) return false
        val now = state[T1, 0x57, u(p[1])] ?: return false
        if (u(p[3]) != 0) return false // full form: same layout as the reply
        state[T1, 0x57, u(p[1])] = now.copyOf().also { it[2] = p[2] }
        return true
    }

    /** True when a side that has Playback Control (0x20) is given something else. */
    internal fun movesPlayback(state: SimState, p: ByteArray): Boolean {
        val now = state[T1, 0xf7, 0x03]?.takeIf { it.size >= 5 } ?: return false
        return (u(now[3]) == 0x20 && u(p[3]) != 0x20) || (u(now[4]) == 0x20 && u(p[4]) != 0x20)
    }

    fun forModel(model: EarbudModel, color: Int): SimProfile = when (model) {
        EarbudModel.WF_1000XM5 -> XM5
        EarbudModel.WF_C710N -> C710nProfile.build(color)
        EarbudModel.WH_1000XM5 -> Wh1000xm5Profile.build(color)
    }

    val XM5 = SimProfile(
        EarbudModel.WF_1000XM5,
        recorded = """
        12 01 00 03 00 30 18 00 00
        12 05 03 30 01
        12 05 01 0a 57 46 2d 31 30 30 30 58 4d 35
        12 05 02 05 36 2e 31 2e 30
        12 07 00 2e 10 ff 12 ff 13 ff 14 ff 15 ff 16 ff 17 ff 23 ff 25 36 11 ff 27 ff 29 ff 2a ff 92 ff 40 2e 42 3a 44 ff 50 11 6b 08 70 01 90 ff a2 13 c1 ff c2 ff e2 1b f1 38 fc 0d 93 ff f6 33 f9 41 d1 3b ff 2d 94 ff f3 26 b7 15 47 46 4c 48 4b ff 48 49 4a ff 49 ff e7 1a 34 3f f4 23 4e 47 4f ff
        12 13 02 00
        12 15 03 02 00
        12 15 01 01 00
        12 21 04 05 04 01 02 03 11
        12 21 05 02 10 11
        12 23 09 5b 00 64 00 64 64
        12 23 0a 42 00 1e
        12 25 09 52 00 64 00 64 64
        12 27 04 10 00
        12 27 05 11 00
        12 29 05 11 00
        12 41 00 30 32 3a 30 30 3a 30 30 3a 30 30 3a 30 30 3a 31 30 30 32 3a 30 30 3a 30 30 3a 30 30 3a 30 30 3a 31 31 30 32 3a 30 30 3a 30 30 3a
        12 41 01 30 32 3a 30 30 3a 30 30 3a 30 30 3a 30 30 3a 31 30 30 32 3a 30 30 3a 30 30 3a 30 30 3a 30 30 3a 31 30
        12 43 01 00 02
        12 43 00 00 02 02
        12 43 0c 00
        12 47 01 01
        12 47 0c
        12 47 00 01
        12 49 0c
        12 49 0d
        12 57 00 00 06 0a 0a 0a 0a 0a 0a
        12 59 00 14 06 0a 10 0e 0c 0d 09
        12 67 17 01 01 01 00 14
        12 69 17 01 01 00 01 14
        12 71 00 03 06 10 20
        12 99 00 2e
        12 99 06 0d 09 18 0f 0b 09 0c 17 15 19 12 01
        12 a1 01 1f 10
        12 a3 01 00 01 00
        12 a7 01 01 00
        12 a5 01 00 01 01
        12 a7 21 05
        12 a7 31 05 01
        12 a7 30 0b 01
        12 a7 20 0b
        12 a9 20 09
        12 a9 30 07 01
        12 b1 10 00
        12 b1 00 00
        12 b1 01 00
        12 b7 10 00
        12 b7 20 06
        12 b7 21 06
        12 d1 d2 00 01 12 4d 55 4c 54 49 50 4f 49 4e 54 5f 53 45 54 54 49 4e 47 29 4d 55 4c 54 49 50 4f 49 4e 54 5f 53 45 54 54 49 4e 47 5f 53 55 4d 4d 41 52 59 5f 4c 44 41 43 5f 41 56 41 49 4c 41 42 4c
        12 d1 d1 00 01 10 53 49 44 45 54 4f 4e 45 5f 53 45 54 54 49 4e 47 18 53 49 44 45 54 4f 4e 45 5f 53 45 54 54 49 4e 47 5f 53 55 4d 4d 41 52 59
        12 d7 d1 00 01
        12 d7 d2 00 01
        12 d9 d1 00 01
        12 d9 d2 00 01
        12 e1 01 02
        12 e3 01 00
        12 e7 00 02
        12 e7 02 02
        12 e7 01 00
        12 e9 01 01
        12 e9 05 01 00
        12 f1 03 02 00 00 35 03 35 04 01 01 43 02 44 22 10 03 24 00 02 04 01 02 03 04 20 05 00 00 20 01 21 02 22 21 30 03 24 ff 01 00 00 00 01 00 20 03 35 04 01 01 43 02 44 22 10 03 23 00 02 04 01 02 03 04 20 05 00 00 20 01 21 02 22 21 30 03 23 ff 01 00 00 00
        12 f1 04 03 02 31 ff
        12 f1 06 07 01 01 04 00 01 02 03
        12 f1 0d 00 00 02 01 00 00 02 00 00
        12 f3 03 02 00 00
        12 f3 04 00
        12 f3 0d 00
        12 f3 0f 00
        12 f5 0c 00 01
        12 f5 06 00 00 00 00
        12 f5 10 01 01
        12 f5 05 02
        12 f7 02 01 01
        12 f7 03
        12 f7 06 00 00 00 00 ff ff
        12 f7 09 00
        12 f7 01 01
        12 f7 04
        12 f7 0d 02 00 00
        12 f7 0f 00
        12 f7 0c 00 01
        12 f9 01 01
        12 f9 03 02 00 00
        12 f9 06 00 03 00 00 01 02
        12 f9 09 00
        12 f9 0f 00
        12 f9 04 30
        12 f9 0c 00 01
        12 fb 06 01 01 ff ff ff ff
        12 fb 03 01 00 01 00 03
        12 fb 0c 00 01
        12 fd 03 01 00 01 00 03
        12 fd 0c 00 00
        14 07 00 09 42 3d 53 ff 54 ff f1 ff 60 ff 61 ff f2 ff 63 ff 6f 4a
        14 31 00 08 02 00
        14 31 02 08 02 00
        14 33 00 00 00
        14 41 01 03 00 00 00 01 0f 01 02 03 04 05 06 07 08 09 0a 0b 0d 0f 10 f0
        14 47 20 00
        14 47 01 01 01
        14 49 20 00
        14 49 01 01
        14 51 03 3c 38 6d 43 80 01 0f
        14 51 04 3c 38 6d 43 80 01 0f
        14 53 03 00 00 01 02 9c 4b 34 00 01 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
        14 55 04 00
        14 57 00 00
        14 57 01 00
        14 57 04 00
        14 57 03 00
        14 59 03 01 00
        14 5b 03 31 03
        14 f1 01 02 00 00 01 00
        14 f3 0a 00
        14 f7 0a 00 00 00 30 32 3a 30 30 3a 30 30 3a 30 30 3a 30 30 3a 31 34
        14 f9 0a
        """,
        confirm = { state, table, p ->
            when {
                table != T1 || p.size < 2 -> null
                // LE Audio on (0x09, "will reconnect over LE") or off (0x2e, "will wait in Classic pairing").
                u(p[0]) == 0x48 && u(p[1]) == 0x0c -> if (p.size > 2 && u(p[2]) == 0) 0x09 else 0x2e
                // Connect to Two Devices (DISCONNECT_CAUSED_BY_CHANGING_MULTIPOINT).
                u(p[0]) == 0xd8 && u(p[1]) == 0xd2 -> 0x07
                // Moving Playback Control off a side (it carries the voice assistant).
                u(p[0]) == 0xf8 && u(p[1]) == 0x03 && p.size >= 5 -> 0x0f.takeIf { movesPlayback(state, p) }
                else -> null
            }
        },
        dropsAfter = { table, p -> table == T1 && u(p[0]) == 0x48 && u(p[1]) == 0x0c },
        onSet = { state, table, p ->
            when {
                eqSet(state, table, p) -> true
                // Touch: the XM5 refuses Volume Control (0x10) on either side and turns that side off.
                table == T1 && u(p[0]) == 0xf8 && u(p[1]) == 0x03 && p.size >= 5 -> {
                    val l = if (u(p[3]) == 0x10) 0xff else u(p[3])
                    val r = if (u(p[4]) == 0x10) 0xff else u(p[4])
                    state[T1, 0xf7, 0x03] = byteArrayOf(0xf7.toByte(), 0x03, p[2], l.toByte(), r.toByte())
                    true
                }
                else -> false
            }
        }
    )

}
