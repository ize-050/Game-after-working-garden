package com.littlefarm.ui

import com.littlefarm.game.PlotStage
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FarmMotionTest {
    @Test fun plotActionsOnlyDecorateTheTargetPlot() {
        listOf("till", "plant", "water", "harvest").forEach { kind ->
            val event = FarmMotionEvent(1, kind, plotIndex = 2)
            assertTrue(farmEventTargetsPlot(event, 2, PlotStage.TILLED))
            assertFalse(farmEventTargetsPlot(event, 1, PlotStage.TILLED))
            assertFalse(farmEventTargetsPlot(event, null, PlotStage.TILLED))
        }
    }

    @Test fun waterAllOnlyDecoratesGrowingPlots() {
        val event = FarmMotionEvent(2, "waterAll")
        assertTrue(farmEventTargetsPlot(event, 0, PlotStage.GROWING))
        assertFalse(farmEventTargetsPlot(event, 0, PlotStage.UNTILLED))
        assertFalse(farmEventTargetsPlot(event, 0, PlotStage.TILLED))
        assertFalse(farmEventTargetsPlot(event, null, PlotStage.GROWING))
    }

    @Test fun otherOrMissingEventsNeverDecoratePlots() {
        assertFalse(farmEventTargetsPlot(null, 0, PlotStage.GROWING))
        listOf("coins", "order", "upgrade", "unknown").forEach { kind ->
            assertFalse(farmEventTargetsPlot(FarmMotionEvent(3, kind, 0), 0, PlotStage.GROWING))
        }
    }
}
