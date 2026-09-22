package com.littlefarm.game

import kotlin.test.Test
import kotlin.test.assertEquals

class FarmProgressTest {
    private val start = 1_000_000L
    @Test fun elapsedDaysUseRealClockNotDemoOffset() {
        val state = GameState.initial(start)
        assertEquals(1L, state.dayNumber(start))
        assertEquals(1L, state.dayNumber(start + 86_399_999L))
        assertEquals(2L, state.dayNumber(start + 86_400_000L))
        assertEquals(1L, state.copy(demoOffsetMillis = 86_400_000L).dayNumber(start))
        assertEquals(1L, state.dayNumber(start - 1))
        assertEquals(1L, state.copy(startedAtMillis = null).dayNumber(start + 200_000_000L))
    }
    @Test fun xpDisplayHasStableHundredPointLevels() {
        val state = GameState.initial(start)
        assertEquals(1, state.level); assertEquals(0, state.xpInLevel)
        assertEquals(1, state.copy(xp = 20).level); assertEquals(20, state.copy(xp = 20).xpInLevel)
        assertEquals(2, state.copy(xp = 100).level); assertEquals(0, state.copy(xp = 100).xpInLevel)
        assertEquals(21474837, state.copy(xp = Int.MAX_VALUE).level)
    }
}
