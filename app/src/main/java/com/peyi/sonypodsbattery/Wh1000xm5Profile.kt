package com.peyi.sonypodsbattery

/**
 * Simulated WH-1000XM5 headphones, reconstructed (docs/protocol/wh1000xm5_profile.md section 7): the
 * handshake, the battery layout, noise control and the playback status are from other projects'
 * captures; the rest follows Sound Connect's layouts with the help guide's defaults. A tester's
 * diagnostics report (ProtocolCapture) has the same format and replaces this.
 *
 * What differs from the earbuds: one battery and no case (22 00), the NC/AMB button's cycle on
 * NcAsm type 30, a touch panel on/off switch in general setting d1 instead of per-side touch, no
 * LE Audio, fit test, head gestures or sidetone, and Safe Listening on the headband type (02).
 */
internal object Wh1000xm5Profile {
    /** [color]: Sony's colour code (0x01 Black, 0x03 Platinum Silver, 0x05 Midnight Blue, 0x06 Smoky Pink). */
    fun build(color: Int = 0x01): SimProfile = SimProfile(
        EarbudModel.WH_1000XM5,
        recorded = """
            12 01 00 03 00 20 16 00 00
            12 05 01 0a 57 48 2d 31 30 30 30 58 4d 35
            12 05 02 05 32 2e 35 2e 31
            12 05 03 30 ${"%02x".format(color)}
            12 07 00 1e 10 ff 12 ff 13 ff 14 ff 15 ff 16 ff 17 ff 20 ff 23 ff 25 36 27 ff 34 3f 50 11 69 ff 6b 08 70 01 90 ff a1 12 b2 43 b8 1f d1 3b d2 20 e2 1b e3 1a f1 38 f4 23 f5 24 f9 41 fc 0d fd 2b
            14 07 00 05 41 3d 32 ff 52 ff f2 ff f8 ff
            12 13 02 10
            12 13 03 02 00
            12 23 00 46 00
            12 25 00 45 00
            12 21 05 02 10 11
            12 23 05 00
            12 27 05 10 00
            12 61 17 02 00 01 14 01 01 01 14 01
            12 63 17 00
            12 67 17 01 01 00 00 14
            12 61 30 04
            12 63 30 00
            12 67 30 02
            12 71 00 03 06 10 20
            12 51 00 06 15 0c 00 00 10 00 11 00 12 00 13 00 14 00 15 00 16 00 17 00 a0 00 a1 00 a2 00
            12 5b 00 06 10 00 01 01 01 90 01 03 e8 01 09 c4 01 18 9c 01 3e 80
            12 53 00 00
            12 57 00 00 06 0a 0a 0a 0a 0a 0a
            12 e1 01 02
            12 e3 01 00
            12 e7 01 00
            12 e3 02 00 00
            12 e7 02 00
            12 f3 01 00
            12 f7 01 00
            12 f3 0c 00 00
            12 f7 0c 01 01
            12 fb 0c 00 01
            12 f1 04 01 04 30 31 32 ff
            12 f3 04 00
            12 f7 04 30
            12 f7 05 01
            12 f1 0d 01 01 02 01 00 00 02 00 00
            12 f3 0d 00
            12 f7 0d 02 00 00
            12 b1 10 00
            12 b3 10 00
            12 b7 10 00
            12 d1 d1 00 01 13 54 4f 55 43 48 5f 50 41 4e 45 4c 5f 53 45 54 54 49 4e 47 00
            12 d3 d1 00
            12 d7 d1 00 00
            12 d1 d2 00 01 12 4d 55 4c 54 49 50 4f 49 4e 54 5f 53 45 54 54 49 4e 47 00
            12 d3 d2 00
            12 d7 d2 00 01
            12 a1 01 1f 10
            12 a3 01 00 02 00
            12 a5 01 00 02 01
            12 a7 20 06
            12 a7 21 05
            14 41 01 03 00 01 00 01 0f 01 02 03 04 05 06 07 08 09 0a 0b 0d 0f 10 f0
            14 43 01 00 00
            14 47 01 00 01
            14 31 00 08 02 00
            14 31 02 08 02 00
            14 33 00 00 00
            14 37 00 01 41 41 3a 42 42 3a 43 43 3a 44 44 3a 45 45 3a 30 31 01 05 50 69 78 65 6c 01
            14 51 02 3c 38 6d 43 80 01 0f
            14 57 02 00
            14 5b 02 31 03
            14 f3 0a 00
            14 f7 0a 01 00 00 30 32 3a 30 30 3a 30 30 3a 30 30 3a 30 30 3a 31 34
        """,
        confirm = { _, table, p ->
            when {
                table != SonyProtocol.DATA_MDR || p.size < 2 -> null
                // Connect to Two Devices reconnects the headphones, so they ask first.
                SimProfiles.u(p[0]) == 0xd8 && SimProfiles.u(p[1]) == 0xd2 -> 0x07
                else -> null
            }
        },
        onSet = { state, table, p -> SimProfiles.eqSet(state, table, p) }
    )
}
