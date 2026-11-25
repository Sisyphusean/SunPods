package com.peyi.sonypodsbattery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingModelTest {
    @Test fun `supportsLeAudio is enabled only for WF-1000XM5`() {
        assertTrue(EarbudModel.WF_1000XM5.supportsLeAudio)
        assertFalse(EarbudModel.WF_1000XM4.supportsLeAudio)
        assertFalse(EarbudModel.WF_C710N.supportsLeAudio)
        assertFalse(EarbudModel.WH_1000XM5.supportsLeAudio)
    }

    @Test fun `onboarding steps include LE Audio only when both phone and model support it`() {
        val withLe = onboardingSteps(EarbudModel.WF_1000XM5, phoneSupportsLeAudio = true)
        assertEquals(
            listOf(
                OnboardingStep.WELCOME,
                OnboardingStep.DEVICE,
                OnboardingStep.NEARBY,
                OnboardingStep.EARBUDS,
                OnboardingStep.LEAUDIO,
                OnboardingStep.UPDATES,
                OnboardingStep.EXTRAS,
                OnboardingStep.DONE,
            ),
            withLe
        )

        val expectedWithoutLe = listOf(
            OnboardingStep.WELCOME,
            OnboardingStep.DEVICE,
            OnboardingStep.NEARBY,
            OnboardingStep.EARBUDS,
            OnboardingStep.UPDATES,
            OnboardingStep.EXTRAS,
            OnboardingStep.DONE,
        )
        assertEquals(expectedWithoutLe, onboardingSteps(EarbudModel.WF_1000XM5, phoneSupportsLeAudio = false))
        assertEquals(expectedWithoutLe, onboardingSteps(EarbudModel.WF_C710N, phoneSupportsLeAudio = true))
        assertEquals(expectedWithoutLe, onboardingSteps(EarbudModel.WH_1000XM5, phoneSupportsLeAudio = true))
        assertEquals(expectedWithoutLe, onboardingSteps(EarbudModel.WH_1000XM5, phoneSupportsLeAudio = false))
    }

    @Test fun `model-scoped audio matching ignores unrelated connected Sony models`() {
        val xm5Addr = "11:22:33:44:55:66"
        val whAddr = "AA:BB:CC:DD:EE:FF"

        // WF-1000XM5 playing does not satisfy WH-1000XM5 or WF-C710N even if WH-1000XM5 is bonded.
        assertFalse(matchesModelAudioDevice(EarbudModel.WH_1000XM5, "WF-1000XM5", xm5Addr, setOf(whAddr)))
        assertFalse(matchesModelAudioDevice(EarbudModel.WH_1000XM5, "LE_WF-1000XM5", xm5Addr, setOf(whAddr)))
        assertFalse(matchesModelAudioDevice(EarbudModel.WF_C710N, "WF-1000XM5", xm5Addr, emptySet()))

        // Matching name or bonded address for the chosen model succeeds.
        assertTrue(matchesModelAudioDevice(EarbudModel.WH_1000XM5, "WH-1000XM5", whAddr, setOf(whAddr)))
        assertTrue(matchesModelAudioDevice(EarbudModel.WH_1000XM5, null, whAddr, setOf(whAddr)))
        assertTrue(matchesModelAudioDevice(EarbudModel.WF_C710N, "WF-C710N", "22:33:44:55:66:77", emptySet()))
    }

    @Test fun `pickEarbuds prefers active A2DP or LE connection then chosen model`() {
        val xm5Classic = BondedDeviceInfo("11:22:33:44:55:66", EarbudModel.WF_1000XM5, isLive = false, isClassic = true)
        val xm5Live = xm5Classic.copy(isLive = true)
        val xm5Le = BondedDeviceInfo("C0:11:22:33:44:55", EarbudModel.WF_1000XM5, isLive = false, isClassic = false)
        val whClassic = BondedDeviceInfo("AA:BB:CC:DD:EE:FF", EarbudModel.WH_1000XM5, isLive = false, isClassic = true)
        val whLive = whClassic.copy(isLive = true)

        // Actively connected WF-1000XM5 wins over disconnected WH-1000XM5 even when WH-1000XM5 was chosen.
        assertEquals(
            0,
            selectBondedDeviceIndex(
                candidates = listOf(xm5Live, whClassic),
                chosen = EarbudModel.WH_1000XM5,
                lastPickAddress = whClassic.address,
                savedClassicAddress = whClassic.address,
            )
        )

        // When neither is live, chosen WH-1000XM5 wins even if WF-1000XM5 was last picked / saved.
        assertEquals(
            2,
            selectBondedDeviceIndex(
                candidates = listOf(xm5Classic, xm5Le, whClassic),
                chosen = EarbudModel.WH_1000XM5,
                lastPickAddress = xm5Classic.address,
                savedClassicAddress = xm5Classic.address,
            )
        )

        // When both models are live simultaneously, chosen WH-1000XM5 breaks the tie.
        assertEquals(
            1,
            selectBondedDeviceIndex(
                candidates = listOf(xm5Live, whLive),
                chosen = EarbudModel.WH_1000XM5,
                lastPickAddress = xm5Live.address,
                savedClassicAddress = xm5Live.address,
            )
        )

        // Within a single model when disconnected, Classic entry is preferred over random LE entry.
        assertEquals(
            1,
            selectBondedDeviceIndex(
                candidates = listOf(xm5Le, xm5Classic),
                chosen = EarbudModel.WF_1000XM5,
                lastPickAddress = null,
                savedClassicAddress = null,
            )
        )
    }

    @Test fun `wakeHint and disconnected copy match each model`() {
        assertEquals("Turn them on near your phone", EarbudModel.WH_1000XM5.wakeHint)
        assertEquals("Open the case near your phone", EarbudModel.WF_1000XM5.wakeHint)
        assertEquals("Open the case near your phone", EarbudModel.WF_1000XM4.wakeHint)
        assertEquals("Open the case near your phone", EarbudModel.WF_C710N.wakeHint)

        assertEquals("Turn them on near your phone.", disconnectedHint(EarbudModel.WH_1000XM5))
        assertEquals("Open the case near your phone.", disconnectedHint(EarbudModel.WF_1000XM5))
        assertEquals("Open the case near your phone.", disconnectedHint(EarbudModel.WF_1000XM4))
        assertEquals("Open the case near your phone.", disconnectedHint(EarbudModel.WF_C710N))
    }

    @Test fun `family model lists are non-empty when switching between earbuds and headphones`() {
        val earbuds = EarbudModel.entries.filter { !it.headphones }
        val headphones = EarbudModel.entries.filter { it.headphones }
        assertTrue(earbuds.isNotEmpty())
        assertTrue(headphones.isNotEmpty())
        assertEquals(listOf(EarbudModel.WF_1000XM5, EarbudModel.WF_1000XM4, EarbudModel.WF_C710N), earbuds)
        assertEquals(listOf(EarbudModel.WH_1000XM5), headphones)
        // Switching back to earbuds must yield valid index bounds for the previous family size.
        val prevIndexInHeadphones = 0
        assertEquals(EarbudModel.WF_1000XM5, earbuds[prevIndexInHeadphones.coerceIn(0, earbuds.lastIndex)])
    }
}
