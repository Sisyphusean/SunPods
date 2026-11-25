package com.peyi.sonypodsbattery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceListTest {
    private val buds = listOf(EarbudModel.WF_1000XM5, EarbudModel.WF_1000XM4, EarbudModel.WF_C710N)

    @Test fun everyModelHasASeries() {
        assertTrue(EarbudModel.entries.all { it.series.isNotBlank() })
    }

    @Test fun groupsBySeriesInDeclaredOrder() {
        val g = groupBySeries()
        assertEquals(listOf("1000X", "C"), g.map { it.series })
        assertEquals(listOf(EarbudModel.WF_1000XM5, EarbudModel.WF_1000XM4, EarbudModel.WH_1000XM5), g.first().models)
        assertEquals(EarbudModel.entries.toSet(), g.flatMap { it.models }.toSet())
    }

    @Test fun familyFilterKeepsOnlyThatKind() {
        assertEquals(buds, groupBySeries(family = false).flatMap { it.models })
        assertEquals(listOf(EarbudModel.WH_1000XM5), groupBySeries(family = true).flatMap { it.models })
    }

    @Test fun searchIgnoresCaseSpacesAndDashes() {
        assertEquals(listOf(EarbudModel.WF_1000XM5, EarbudModel.WF_1000XM4, EarbudModel.WH_1000XM5), groupBySeries("xm").flatMap { it.models })
        assertEquals(listOf(EarbudModel.WF_C710N), groupBySeries("wf c7").flatMap { it.models })
        assertEquals(listOf(EarbudModel.WF_C710N), groupBySeries("WFC710N").flatMap { it.models })
        assertEquals(listOf(EarbudModel.WH_1000XM5), groupBySeries("Headphones").flatMap { it.models })
        assertEquals(listOf(EarbudModel.WF_1000XM4), groupBySeries("xm4").flatMap { it.models })
    }

    @Test fun blankSearchShowsEverything() {
        assertEquals(EarbudModel.entries.size, groupBySeries("  ").sumOf { it.models.size })
    }
}
