package com.peyi.sonypodsbattery

/**
 * Simulated WF-1000XM4 earbuds on firmware 1.4.2, from recorded replies (Gadgetbridge issue captures;
 * docs/protocol/wf1000xm4_profile.md section 8). The earbuds' address and IDs are made up.
 *
 * What differs from the WF-1000XM5: noise control on type 0x15 with the wind byte, Speak-to-Chat on
 * type 02 with voice focus, touch presets 00 / 20 / 10 / ff (noise control is 00, and Volume Control
 * is a whole-sensor preset), voice prompts without volume (T2 0x41), sound quality mode on type 00,
 * and no LE Audio, multipoint, sidetone, head gestures or Safe Listening. No change asks for a
 * confirmation on 1.4.2.
 */
internal object Wf1000xm4Profile {
    /** [color]: Sony's colour code (0x01 Black, 0x03 Platinum Silver). */
    fun build(color: Int = 0x01): SimProfile = SimProfile(
        EarbudModel.WF_1000XM4,
        recorded = """
            12 01 00 01 00 00 00 00 00
            12 03 00 05 11 30 32 3a 30 30 3a 30 30 3a 30 30 3a 30 30 3a 34 30
            12 05 01 0a 57 46 2d 31 30 30 30 58 4d 34
            12 05 02 05 31 2e 34 2e 32
            12 05 03 30 ${"%02x".format(color)}
            12 05 04 05 10 01 40 80 60
            12 07 00 1b 10 ff 12 ff 13 ff 14 ff 23 ff 25 15 11 ff 27 ff 29 ff 2a ff 92 ff 32 1a 50 07 68 03 70 01 90 ff a1 08 c1 ff e1 09 e2 0a f1 17 f2 05 f3 0f f4 0d 93 ff f6 14 f9 1b
            14 07 00 01 41 18
            12 13 01 01 01
            12 13 02 10
            12 13 03 02 01
            12 23 09 61 00 62 00 64 64
            12 23 0a 53 00 1e
            12 21 05 02 10 11
            12 23 05 00
            12 27 05 10 00
            12 61 15 02 00 01 14 01 01 01 14 01
            12 63 15 00
            12 67 15 01 01 00 02 01 0f
            12 71 00 03 04 10 20
            12 51 00 06 15 0c 00 00 10 00 11 00 12 00 13 00 14 00 15 00 16 00 17 00 a0 00 a1 00 a2 00
            12 5b 00 06 10 00 01 01 01 90 01 03 e8 01 09 c4 01 18 9c 01 3e 80
            12 53 00 00
            12 57 00 10 06 09 0a 0f 11 11 13
            12 e1 01 02
            12 e3 01 00
            12 e7 01 01
            12 e3 00 00
            12 e7 00 00
            12 f3 01 00
            12 f7 01 00
            12 f1 02 01 05 0f 1e
            12 f3 02 00 00
            12 f7 02 01 01
            12 fb 02 00 01 01
            12 f1 03 02 00 00 00 04 00 01 01 22 10 00 02 04 01 02 03 04 20 03 00 00 20 01 21 02 22 10 02 00 00 23 22 24 ff 01 00 00 00 01 00 20 04 00 01 01 22 10 00 02 04 01 02 03 04 20 03 00 00 20 01 21 02 22 10 02 00 00 23 22 24 ff 01 00 00 00
            12 f3 03 02 00 00
            12 f7 03 02 00 20
            12 fb 03 01 00 01 00 01
            12 f1 04 03 04 30 31 32 ff
            12 f3 04 00
            12 f7 04 ff
            12 f1 06 07 01 01 03 01 02 03
            12 f3 06 00 00 00 00
            12 f7 06 00 00 00 00 ff ff
            12 f9 09 00
            12 a1 01 1f 10
            12 a3 01 00 02 00
            12 a7 20 0f
            12 a7 21 06
            12 93 03 00
            14 41 01 03 00 00 00 01 0f 01 02 03 04 05 06 07 08 09 0a 0b 0d 0f 10 f0
            14 43 01 00 00
            14 47 01 00 01
        """,
        onSet = { state, table, p ->
            // Automatic wind noise reduction: the app writes 03, the earbuds report 05 (recorded).
            if (table == SonyProtocol.DATA_MDR && p.size >= 8 && SimProfiles.u(p[0]) == 0x68 && SimProfiles.u(p[1]) == 0x15 && SimProfiles.u(p[5]) == 0x03) {
                state[SonyProtocol.DATA_MDR, 0x67, 0x15] = byteArrayOf(0x67) + p.copyOfRange(1, p.size).also { it[4] = 0x05 }
                true
            } else SimProfiles.eqSet(state, table, p)
        }
    )
}
