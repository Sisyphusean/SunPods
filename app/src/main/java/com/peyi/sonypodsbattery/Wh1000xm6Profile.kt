package com.peyi.sonypodsbattery

/**
 * Simulated WH-1000XM6 headphones, reconstructed (docs/protocol/wh1000xm6_profile.md section 7): the
 * handshake, noise control type 0x19, the EQ shape (10 bands, 13 steps, 9 presets), the band
 * frequencies, the general setting keys and the volume range were seen on real headphones by other
 * projects; the rest follows Sound Connect's layouts with the help guide's defaults. A tester's
 * diagnostics report (ProtocolCapture) has the same format and replaces this.
 *
 * What differs from the WH-1000XM5: noise control on type 0x19 with Auto Ambient Sound (9 bytes), a
 * 10-band EQ that lists its own presets (0x30 to 0x33 and the custom ones; the classic presets are
 * acknowledged and ignored), sound quality mode on type 05, the touch panel switch in general setting
 * d3 (d1 is sidetone), head gestures, and the LE Audio setting (0x42).
 */
internal object Wh1000xm6Profile {
    /** EQ presets the headphones act on; any other is acknowledged and ignored (seen on firmware 2.x and 3.0.0). */
    private val EQ_ACCEPTED = setOf(0x00) + (0x30..0x33) + (0xa0..0xa5)

    /**
     * [color]: Sony's colour code (0x01 Black, 0x03 Platinum Silver, 0x05 Midnight Blue, 0x06 Sand Pink;
     * Sandstone and Olive Gray unconfirmed).
     */
    fun build(color: Int = 0x01): SimProfile = SimProfile(
        EarbudModel.WH_1000XM6,
        recorded = """
            12 01 00 03 00 30 13 00 00
            12 05 01 0a 57 48 2d 31 30 30 30 58 4d 36
            12 05 02 05 33 2e 31 2e 35
            12 05 03 30 ${"%02x".format(color)}
            12 07 00 2a 10 ff 12 ff 13 ff 14 ff 15 ff 16 ff 17 ff 20 ff 23 ff 25 36 27 ff 34 3f 42 3a 57 11 69 ff 6d 08 70 01 90 ff 93 ff 94 ff a1 12 b0 42 b2 43 b6 1d b7 15 b8 1f c1 ff c2 ff d1 3b d2 20 d3 ff e2 1b e5 ff e6 ff e7 1a eb ff f1 38 f4 23 f9 41 fc 0d fd 2b ff 2d
            14 07 00 0d 32 ff 42 3d 52 ff 54 ff 60 ff 61 ff 63 ff 6f 4a f0 ff f2 ff f5 ff f8 ff f9 ff
            12 13 02 10
            12 13 03 02 00
            12 23 00 50 00
            12 21 05 02 10 11
            12 23 05 00
            12 27 05 10 00
            12 61 19 02 00 00 14 01 01 00 14 01
            12 63 19 00
            12 67 19 01 01 00 00 14 00 00
            12 61 30 04
            12 67 30 02
            12 71 00 03 08 10 20
            12 51 04 0a 0d 09 00 00 30 00 31 00 32 00 33 00 a0 00 a1 00 a2 00 a3 00
            12 5b 00 0a 01 00 1f 01 00 3f 01 00 7d 01 00 fa 01 01 f4 01 03 e8 01 07 d0 01 0f a0 01 1f 40 01 3e 80
            12 53 00 00
            12 57 00 a0 0a 08 07 06 06 06 06 06 07 08 08
            12 e1 01 02
            12 e3 01 00
            12 e7 01 00
            12 e1 05 03 00 01 02 01 00
            12 e3 05 00 00
            12 e7 05 00
            12 43 0c 00
            12 47 0c 01
            12 e1 09 01 09
            12 e3 09 00 00
            12 e7 09 01 00
            12 e3 04 00
            12 e7 04 01
            12 f3 0c 00 00
            12 f7 0c 01 01
            12 fb 0c 00 01
            12 f3 01 00
            12 f7 01 00
            12 f7 0f 01
            12 f1 04 01 03 30 31 34
            12 f3 04 00
            12 f7 04 30
            12 f1 0d 01 01 02 01 00 00 02 00 00
            12 f3 0d 00
            12 f7 0d 02 00 00
            12 b1 10 01 00
            12 b3 10 00
            12 b7 10 01
            12 d1 d1 00 01 10 53 49 44 45 54 4f 4e 45 5f 53 45 54 54 49 4e 47 18 53 49 44 45 54 4f 4e 45 5f 53 45 54 54 49 4e 47 5f 53 55 4d 4d 41 52 59
            12 d1 d2 00 01 12 4d 55 4c 54 49 50 4f 49 4e 54 5f 53 45 54 54 49 4e 47 29 4d 55 4c 54 49 50 4f 49 4e 54 5f 53 45 54 54 49 4e 47 5f 53 55 4d 4d 41 52 59 5f 4c 44 41 43 5f 41 56 41 49 4c 41 42 4c 45
            12 d1 d3 00 01 13 54 4f 55 43 48 5f 50 41 4e 45 4c 5f 53 45 54 54 49 4e 47 00
            12 d3 d1 00
            12 d3 d2 00
            12 d3 d3 00
            12 d7 d1 00 01
            12 d7 d2 00 01
            12 d7 d3 00 00
            12 a1 01 1f 10
            12 a3 01 00 01 00
            12 a7 20 0b
            12 a7 21 05
            14 41 01 03 00 00 00 01 0f 01 02 03 04 05 06 07 08 09 0a 0b 0d 0f 10 f0
            14 43 01 00 00
            14 47 01 00 01
            14 47 20 00
            14 31 00 08 02 00
            14 31 02 08 02 00
            14 33 00 00 00
            14 33 02 00 00
            14 37 00 01 41 41 3a 42 42 3a 43 43 3a 44 44 3a 45 45 3a 30 31 01 05 50 69 78 65 6c 01
            14 51 02 3c 38 6d 43 80 01 0f
            14 57 02 00
            14 5b 02 31 03
            14 f3 00 00
        """,
        confirm = { _, table, p ->
            when {
                table != SonyProtocol.DATA_MDR || p.size < 2 -> null
                // Connect to Two Devices reconnects the headphones, so they ask first.
                SimProfiles.u(p[0]) == 0xd8 && SimProfiles.u(p[1]) == 0xd2 -> 0x07
                else -> null
            }
        },
        onSet = { state, table, p ->
            when {
                // An EQ preset they don't have (the classic 0x10 to 0x17): acknowledged, nothing changes.
                table == SonyProtocol.DATA_MDR && p.size >= 3 && SimProfiles.u(p[0]) == 0x58 && SimProfiles.u(p[1]) == 0x00 &&
                    SimProfiles.u(p[2]) !in EQ_ACCEPTED -> true
                else -> SimProfiles.eqSet(state, table, p)
            }
        }
    )
}
