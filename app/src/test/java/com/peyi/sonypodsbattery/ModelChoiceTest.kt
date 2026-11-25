package com.peyi.sonypodsbattery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelChoiceTest {
    @Test fun pickerPatternMatchesOnlyThatModel() {
        for (m in EarbudModel.entries) for (o in EarbudModel.entries)
            assertEquals("${m.displayName} vs ${o.displayName}", m == o, m.namePattern.matcher(o.displayName).matches())
        assertTrue(EarbudModel.WH_1000XM5.namePattern.matcher("WH-1000XM5").matches())
    }
    @Test fun modelSpecificPairingSteps() {
        assertTrue(EarbudModel.WH_1000XM5.pairSteps.any { it.contains("power button") })
        assertTrue(EarbudModel.WF_1000XM5.pairSteps.any { it.contains("case") })
        assertTrue(EarbudModel.WF_1000XM4.pairSteps.any { it.contains("touch sensors") })
    }
}
