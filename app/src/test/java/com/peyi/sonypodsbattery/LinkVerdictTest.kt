package com.peyi.sonypodsbattery

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * When a refresh talks to the earbuds. The reported case: a Pixel 10 on an Android 17 beta, a
 * WF-1000XM5 on Classic and playing, yet every manual refresh said "A2DP disconnected; refresh
 * skipped" because the paired entry SunPods picked wasn't the one Android listed on A2DP.
 */
class LinkVerdictTest {
    @Test fun `the picked entry on A2DP is used`() =
        assertEquals(LinkVerdict.USE_TARGET, linkVerdict(true, otherEntryOnA2dp = false, mediaUp = true, callsUp = true, manual = false))

    @Test fun `another entry of the same earbuds on A2DP is used instead`() {
        assertEquals(LinkVerdict.USE_OTHER_ENTRY, linkVerdict(false, otherEntryOnA2dp = true, mediaUp = true, callsUp = false, manual = true))
        assertEquals(LinkVerdict.USE_OTHER_ENTRY, linkVerdict(false, otherEntryOnA2dp = true, mediaUp = true, callsUp = false, manual = false))
    }

    @Test fun `a manual refresh tries when audio is up but the earbuds aren't listed`() {
        assertEquals(LinkVerdict.TRY_ANYWAY, linkVerdict(false, otherEntryOnA2dp = false, mediaUp = true, callsUp = false, manual = true))
        assertEquals(LinkVerdict.TRY_ANYWAY, linkVerdict(false, otherEntryOnA2dp = false, mediaUp = false, callsUp = true, manual = true))
        assertEquals(LinkVerdict.TRY_ANYWAY, linkVerdict(null, otherEntryOnA2dp = false, mediaUp = true, callsUp = false, manual = true))
    }

    @Test fun `automatic refreshes stay cautious`() {
        assertEquals(LinkVerdict.SKIP, linkVerdict(false, otherEntryOnA2dp = false, mediaUp = true, callsUp = true, manual = false))
        assertEquals(LinkVerdict.UNKNOWN, linkVerdict(null, otherEntryOnA2dp = false, mediaUp = true, callsUp = false, manual = false))
    }

    @Test fun `nothing connected is skipped`() {
        assertEquals(LinkVerdict.SKIP, linkVerdict(false, otherEntryOnA2dp = false, mediaUp = false, callsUp = false, manual = true))
        assertEquals(LinkVerdict.UNKNOWN, linkVerdict(null, otherEntryOnA2dp = false, mediaUp = false, callsUp = false, manual = true))
    }
}
