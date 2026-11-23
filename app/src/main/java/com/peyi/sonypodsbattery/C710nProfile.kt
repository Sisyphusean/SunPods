package com.peyi.sonypodsbattery

/**
 * Simulated WF-C710N, reconstructed (docs/protocol/c710n_profile.md): nobody here has the
 * earbuds, so these replies are built from the layouts in Sound Connect's own code, with the
 * function list taken from Sony's help guide and Gadgetbridge. Values are realistic examples.
 * A tester's diagnostics report (ProtocolCapture) has the same format and replaces this.
 *
 * What differs from the WF-1000XM5: battery without thresholds (22 01), sound quality mode on
 * type 00, plain DSEE, auto power off with times, multipoint in general setting 1, and no
 * Speak-to-Chat, head gestures, fit test, voice assistant choice, Safe Listening or LE Audio.
 */
internal object C710nProfile {
    /** [color]: Sony's colour code (0x01 Black, 0x02 White, 0x05 Glass Blue, 0x06 Pink). */
    fun build(color: Int = 0x01): SimProfile = SimProfile(
        EarbudModel.WF_C710N,
        recorded = """
            12 01 00 03 00 30 18 00 00
            12 05 01 08 57 46 2d 43 37 31 30 4e
            12 05 02 05 31 2e 30 2e 35
            12 05 03 50 ${"%02x".format(color)}
            12 07 00 1d 10 ff 11 ff 12 ff 13 ff 14 ff 15 ff 16 ff 17 ff 21 ff 23 ff 25 36 27 ff 2a ff 34 3f 50 11 6b 08 70 01 90 ff a1 12 b2 43 c1 ff c2 ff d1 3b e1 1a e2 1b f1 38 f3 26 f9 41 fd 2b
            14 07 00 03 42 3d 32 ff f2 ff
            12 13 01 01 01
            12 13 02 02
            12 13 03 01 00
            12 23 01 46 00 3c 01
            12 23 0a 50 00 1e
            12 21 05 05 04 01 02 03 11
            12 23 05 00
            12 27 05 04 04
            12 61 17 02 00 00 14 01 01 00 14 01
            12 63 17 00
            12 67 17 01 01 00 00 14
            12 71 00 02 06 10
            12 57 00 10 06 0a 0a 0a 0a 0a 0a
            12 e1 01 01
            12 e3 01 00
            12 e7 01 00
            12 e3 00 00
            12 e7 00 01
            12 f3 01 00
            12 f7 01 00
            12 f1 03 02 00 00 35 03 35 04 01 01 43 02 44 22 10 03 24 00 02 04 01 02 03 04 20 05 00 00 20 01 21 02 22 21 30 03 24 ff 01 00 00 00 01 00 20 03 35 04 01 01 43 02 44 22 10 03 23 00 02 04 01 02 03 04 20 05 00 00 20 01 21 02 22 21 30 03 23 ff 01 00 00 00
            12 f7 03 02 00 20
            12 fb 03 01 00 01 00 01
            12 f1 0d 00 00 02 01 00 00 02 00 00
            12 f7 0d 02 00 00
            12 f7 09 00
            12 b1 10 01 00
            12 b3 10 00
            12 b7 10 01
            12 d1 d1 00 01 12 4d 55 4c 54 49 50 4f 49 4e 54 5f 53 45 54 54 49 4e 47 00
            12 d3 d1 00
            12 d7 d1 00 00
            12 a1 01 1f 10
            12 a3 01 00 01 00
            12 a7 01 01 00
            12 a7 20 0b
            12 a7 21 05
            14 41 01 03 00 00 00 01 0f 01 02 03 04 05 06 07 08 09 0a 0b 0d 0f 10 f0
            14 43 01 00 00
            14 47 01 00 01
            14 47 20 00
            14 31 02 08 02 00
            14 33 02 00 00
        """,
        confirm = { state, table, p ->
            when {
                table != SonyProtocol.DATA_MDR || p.size < 2 -> null
                (p[0].toInt() and 0xff) == 0xd8 && (p[1].toInt() and 0xff) == 0xd1 -> 0x07
                (p[0].toInt() and 0xff) == 0xf8 && (p[1].toInt() and 0xff) == 0x03 && p.size >= 5 -> 0x0f.takeIf { SimProfiles.movesPlayback(state, p) }
                else -> null
            }
        },
        onSet = { state, table, p -> SimProfiles.eqSet(state, table, p) }
    )
}
