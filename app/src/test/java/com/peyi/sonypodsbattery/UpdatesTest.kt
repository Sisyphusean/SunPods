package com.peyi.sonypodsbattery

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatesTest {
    @Test fun comparesVersions() {
        assertTrue(Updates.isNewer("2.28.1", "2.28.0"))
        assertTrue(Updates.isNewer("2.30.0", "2.28.9"))
        assertTrue(Updates.isNewer("3.0", "2.99.99"))
        assertFalse(Updates.isNewer("2.28.0", "2.28.0"))
        assertFalse(Updates.isNewer("2.27.9", "2.28.0"))
        assertFalse(Updates.isNewer("2.28.0-beta", "2.28.0"))
    }
}
