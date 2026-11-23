package com.peyi.sonypodsbattery

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * When a refresh talks to the earbuds. The reported case: a Pixel 10 on an Android 17 beta, a
 * WF-1000XM5 on Classic and playing, yet every manual refresh said "A2DP disconnected; refresh
 * skipped". The paired entry on A2DP now wins the pick (SonyBatteryClient.pickEarbuds); this covers
 * phones that report Bluetooth audio without listing the earbuds on A2DP.
 */
class LinkVerdictTest {
    @Test fun `the picked entry on A2DP is used`() {
        assertEquals(LinkVerdict.USE_TARGET, linkVerdict(true, audioUp = true, manual = false))
        assertEquals(LinkVerdict.USE_TARGET, linkVerdict(true, audioUp = false, manual = true))
    }

    @Test fun `a manual refresh tries when audio is up but the earbuds aren't listed`() {
        assertEquals(LinkVerdict.TRY_ANYWAY, linkVerdict(false, audioUp = true, manual = true))
        assertEquals(LinkVerdict.TRY_ANYWAY, linkVerdict(null, audioUp = true, manual = true))
    }

    @Test fun `automatic refreshes stay cautious`() {
        assertEquals(LinkVerdict.SKIP, linkVerdict(false, audioUp = true, manual = false))
        assertEquals(LinkVerdict.UNKNOWN, linkVerdict(null, audioUp = true, manual = false))
    }

    @Test fun `nothing connected is skipped`() {
        assertEquals(LinkVerdict.SKIP, linkVerdict(false, audioUp = false, manual = true))
        assertEquals(LinkVerdict.UNKNOWN, linkVerdict(null, audioUp = false, manual = true))
    }
}
