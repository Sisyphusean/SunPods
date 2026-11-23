package com.peyi.sonypodsbattery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelChoiceTest {
    @Test fun noDeviceKeepsThePick() {
        val r = EarbudModel.resolve(EarbudModel.WH_1000XM5, null)
        assertEquals(EarbudModel.WH_1000XM5, r.model); assertNull(r.switchedFrom)
    }
    @Test fun matchingDeviceIsNotAMismatch() {
        val r = EarbudModel.resolve(EarbudModel.WF_C710N, EarbudModel.WF_C710N)
        assertEquals(EarbudModel.WF_C710N, r.model); assertNull(r.switchedFrom)
    }
    @Test fun differentDeviceWinsAndRemembersThePick() {
        val r = EarbudModel.resolve(EarbudModel.WH_1000XM5, EarbudModel.WF_1000XM5)
        assertEquals(EarbudModel.WF_1000XM5, r.model); assertEquals(EarbudModel.WH_1000XM5, r.switchedFrom)
    }
    @Test fun nothingPickedFollowsTheDevice() {
        val r = EarbudModel.resolve(null, EarbudModel.WF_C710N)
        assertEquals(EarbudModel.WF_C710N, r.model); assertNull(r.switchedFrom)
    }
    @Test fun nothingAtAllDefaults() {
        assertEquals(EarbudModel.WF_1000XM5, EarbudModel.resolve(null, null).model)
    }
    @Test fun pickerPatternMatchesOnlyThatModel() {
        for (m in EarbudModel.entries) for (o in EarbudModel.entries)
            assertEquals("${m.displayName} vs ${o.displayName}", m == o, m.namePattern.matcher(o.displayName).matches())
        assertTrue(EarbudModel.WH_1000XM5.namePattern.matcher("WH-1000XM5").matches())
        assertTrue(EarbudModel.scanPattern.matcher("WH-1000XM5").matches())
    }
    @Test fun headphonesGetPowerButtonSteps() {
        assertTrue(EarbudModel.WH_1000XM5.pairSteps.any { it.contains("power button") })
        assertTrue(EarbudModel.WF_1000XM5.pairSteps.any { it.contains("case") })
    }
}
