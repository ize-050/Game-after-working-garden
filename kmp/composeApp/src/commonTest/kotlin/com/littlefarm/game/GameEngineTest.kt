package com.littlefarm.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GameEngineTest {
    private val now = 1_000_000L

    private fun GameResult.success(): GameState = assertIs<GameResult.Success>(this).state

    private fun assertError(expected: GameError, result: GameResult) {
        assertEquals(expected, assertIs<GameResult.Failure>(result).error)
    }

    @Test
    fun initialGardenHasTheExactStarterInventoryAndPlotStates() {
        val state = GameState.initial(now)
        assertEquals(120, state.coins)
        assertEquals(0, state.xp)
        assertEquals(listOf(3, 2, 0, 0), CropType.entries.map(state::seedCount))
        assertTrue(state.produce.values.all { it == 0 })
        assertEquals(6, state.plots.size)
        assertEquals(
            listOf(PlotStage.UNTILLED, PlotStage.UNTILLED, PlotStage.TILLED,
                PlotStage.PLANTED, PlotStage.GROWING, PlotStage.GROWING),
            state.plots.map { it.stage },
        )
        assertEquals(CropType.LETTUCE, state.plots[3].crop)
        assertEquals(null, state.plots[3].readyAtMillis)
        assertEquals(120_000L, state.plots[4].remainingMillis(now))
        assertTrue(state.plots[5].isReady(now))
    }

    @Test
    fun fullPlantingCycleConsumesOneSeedAndHarvestsOneCropWithoutCoinsOrXp() {
        val original = GameState.initial(now)
        val tilled = GameEngine.till(original, 0).success()
        val planted = GameEngine.plant(tilled, 0, CropType.LETTUCE).success()
        assertEquals(2, planted.seedCount(CropType.LETTUCE))
        assertEquals(null, planted.plots[0].readyAtMillis)
        assertFalse(planted.plots[0].isReady(now + 999_999L))

        val watered = GameEngine.water(planted, 0, now).success()
        assertEquals(now + 120_000L, watered.plots[0].readyAtMillis)
        assertError(GameError.CROP_NOT_READY, GameEngine.harvest(watered, 0, now + 119_999L))
        val harvested = GameEngine.harvest(watered, 0, now + 120_000L).success()
        assertEquals(1, harvested.produceCount(CropType.LETTUCE))
        assertEquals(Plot(stage = PlotStage.TILLED), harvested.plots[0])
        assertEquals(120, harvested.coins)
        assertEquals(0, harvested.xp)
        assertError(GameError.CROP_NOT_READY, GameEngine.harvest(harvested, 0, now + 120_000L))

        assertEquals(PlotStage.UNTILLED, original.plots[0].stage)
        assertEquals(3, original.seedCount(CropType.LETTUCE))
        assertEquals(0, original.produceCount(CropType.LETTUCE))
    }

    @Test
    fun wateringTwiceCannotRestartTheTimer() {
        val watered = GameEngine.water(GameState.initial(now), 3, now).success()
        assertError(GameError.WRONG_PLOT_STAGE, GameEngine.water(watered, 3, now + 60_000L))
        assertEquals(now + 120_000L, watered.plots[3].readyAtMillis)
    }

    @Test
    fun cropRemainsReadyAfterALongOfflinePeriod() {
        val state = GameState.initial(now)
        val muchLater = now + 30L * 24 * 60 * 60 * 1_000
        assertTrue(state.plots[4].isReady(muchLater))
        assertEquals(0L, state.plots[4].remainingMillis(muchLater))
        assertEquals(1, GameEngine.harvest(state, 4, muchLater).success().produceCount(CropType.RADISH))
    }

    @Test
    fun buyAndSellUseExactPricesAndNeverSpendAbsentInventory() {
        val start = GameState.initial(now)
        val bought = GameEngine.buySeeds(start, CropType.CARROT, 2).success()
        assertEquals(50, bought.coins)
        assertEquals(2, bought.seedCount(CropType.CARROT))
        assertError(GameError.NOT_ENOUGH_COINS, GameEngine.buySeeds(bought, CropType.PUMPKIN))
        assertError(GameError.NOT_ENOUGH_PRODUCE, GameEngine.sellProduce(bought, CropType.CARROT))
        val stocked = bought.copy(produce = bought.produce + (CropType.CARROT to 2))
        val sold = GameEngine.sellProduce(stocked, CropType.CARROT, 2).success()
        assertEquals(190, sold.coins)
        assertEquals(0, sold.produceCount(CropType.CARROT))
        assertError(GameError.NOT_ENOUGH_PRODUCE, GameEngine.sellProduce(sold, CropType.CARROT))
    }

    @Test
    fun quantitiesCannotBeZeroNegativeOrOverflowTheEconomy() {
        val state = GameState.initial(now)
        for (quantity in listOf(0, -1, Int.MIN_VALUE)) {
            assertError(GameError.INVALID_QUANTITY, GameEngine.buySeeds(state, CropType.LETTUCE, quantity))
            assertError(GameError.INVALID_QUANTITY, GameEngine.sellProduce(state, CropType.LETTUCE, quantity))
        }
        assertError(GameError.NOT_ENOUGH_COINS, GameEngine.buySeeds(state, CropType.PUMPKIN, Int.MAX_VALUE))
        val overflowing = state.copy(coins = Int.MAX_VALUE, produce = state.produce + (CropType.LETTUCE to 1))
        assertError(GameError.VALUE_OUT_OF_RANGE, GameEngine.sellProduce(overflowing, CropType.LETTUCE))
    }

    @Test
    fun orderConsumesTwoLettuceAndRewardsExactlyOnce() {
        val state = GameState.initial(now)
        assertError(GameError.NOT_ENOUGH_PRODUCE, GameEngine.fulfillOrder(state))
        val stocked = state.copy(produce = state.produce + (CropType.LETTUCE to 3))
        val claimed = GameEngine.fulfillOrder(stocked).success()
        assertEquals(165, claimed.coins)
        assertEquals(20, claimed.xp)
        assertEquals(1, claimed.produceCount(CropType.LETTUCE))
        assertTrue(claimed.orderClaimed)
        assertError(GameError.ORDER_ALREADY_CLAIMED, GameEngine.fulfillOrder(claimed))
        val sold = GameEngine.sellProduce(claimed, CropType.LETTUCE).success()
        assertEquals(183, sold.coins)
        assertEquals(20, sold.xp)
    }

    @Test
    fun plotUpgradeCosts180AndAddsExactlyThreePlotsOnce() {
        val start = GameState.initial(now)
        assertError(GameError.NOT_ENOUGH_COINS, GameEngine.buyUpgrade(start, UpgradeType.EXTRA_PLOTS))
        val expanded = GameEngine.buyUpgrade(start.copy(coins = 500), UpgradeType.EXTRA_PLOTS).success()
        assertEquals(320, expanded.coins)
        assertEquals(9, expanded.plots.size)
        assertTrue(expanded.upgrades.expandedPlots)
        assertEquals(start.plots, expanded.plots.take(6))
        assertTrue(expanded.plots.drop(6).all { it == Plot() })
        assertError(GameError.UPGRADE_ALREADY_OWNED, GameEngine.buyUpgrade(expanded, UpgradeType.EXTRA_PLOTS))
    }

    @Test
    fun wateringCanCosts150AndWatersOnlyDryPlantedCrops() {
        val start = GameState.initial(now).copy(coins = 500)
        assertError(GameError.WATERING_CAN_REQUIRED, GameEngine.waterAll(start, now))
        val upgraded = GameEngine.buyUpgrade(start, UpgradeType.LARGE_WATERING_CAN).success()
        assertEquals(350, upgraded.coins)
        assertTrue(upgraded.upgrades.largeWateringCan)
        assertError(GameError.UPGRADE_ALREADY_OWNED, GameEngine.buyUpgrade(upgraded, UpgradeType.LARGE_WATERING_CAN))
        val planted = GameEngine.plant(upgraded, 2, CropType.RADISH).success()
        val watered = GameEngine.waterAll(planted, now).success()
        assertEquals(now + 300_000L, watered.plots[2].readyAtMillis)
        assertEquals(now + 120_000L, watered.plots[3].readyAtMillis)
        for (index in listOf(0, 1, 4, 5)) assertEquals(start.plots[index], watered.plots[index])
        assertError(GameError.NOTHING_TO_WATER, GameEngine.waterAll(watered, now + 60_000L))
    }

    @Test
    fun rescueSeedRecoversTheUpgradeSpendingSoftLockAndCannotBeFarmedRepeatedly() {
        val empty = GameState.initial(now).copy(
            coins = 333,
            seeds = CropType.entries.associateWith { 0 },
            produce = CropType.entries.associateWith { 0 },
            plots = List(6) { Plot(stage = PlotStage.TILLED) },
        )
        val expanded = GameEngine.buyUpgrade(empty, UpgradeType.EXTRA_PLOTS).success()
        val broke = GameEngine.buyUpgrade(expanded, UpgradeType.LARGE_WATERING_CAN).success()
        assertEquals(3, broke.coins)
        assertTrue(GameEngine.canClaimRescueSeed(broke))
        val rescued = GameEngine.claimRescueSeed(broke).success()
        assertEquals(1, rescued.seedCount(CropType.LETTUCE))
        assertEquals(3, rescued.coins)
        assertFalse(GameEngine.canClaimRescueSeed(rescued))
        assertError(GameError.RESCUE_NOT_AVAILABLE, GameEngine.claimRescueSeed(rescued))
        val planted = GameEngine.plant(rescued, 0, CropType.LETTUCE).success()
        assertError(GameError.RESCUE_NOT_AVAILABLE, GameEngine.claimRescueSeed(planted))
    }

    @Test
    fun rescueIsUnavailableIfAnyOtherResourceCanFundProgress() {
        val broke = GameState.initial(now).copy(
            coins = 0,
            seeds = CropType.entries.associateWith { 0 },
            produce = CropType.entries.associateWith { 0 },
            plots = List(6) { Plot() },
        )
        assertFalse(GameEngine.canClaimRescueSeed(broke.copy(coins = 10)))
        assertFalse(GameEngine.canClaimRescueSeed(broke.copy(seeds = broke.seeds + (CropType.PUMPKIN to 1))))
        assertFalse(GameEngine.canClaimRescueSeed(broke.copy(produce = broke.produce + (CropType.RADISH to 1))))
        assertFalse(GameEngine.canClaimRescueSeed(GameState.initial(now)))
    }

    @Test
    fun demoTimeAdvancesAllCropsAndFuturePlantingUsesTheSameClock() {
        val start = GameState.initial(now)
        val advanced = GameEngine.advanceDemoTime(start).success()
        assertEquals(300_000L, advanced.demoOffsetMillis)
        assertTrue(advanced.plots[4].isReady(advanced.effectiveNowMillis(now)))
        assertTrue(advanced.plots[5].isReady(advanced.effectiveNowMillis(now)))
        val watered = GameEngine.water(advanced, 3, now).success()
        assertEquals(now + 420_000L, watered.plots[3].readyAtMillis)
        assertFalse(watered.plots[3].isReady(watered.effectiveNowMillis(now)))
        assertTrue(watered.plots[3].isReady(watered.effectiveNowMillis(now + 120_000L)))
        assertEquals(start.coins, watered.coins)
    }

    @Test
    fun invalidPlotsAndStagesReturnErrorsWithoutChangingState() {
        val state = GameState.initial(now)
        assertError(GameError.INVALID_PLOT, GameEngine.till(state, -1))
        assertError(GameError.INVALID_PLOT, GameEngine.plant(state, 6, CropType.LETTUCE))
        assertError(GameError.INVALID_PLOT, GameEngine.water(state, 99, now))
        assertError(GameError.INVALID_PLOT, GameEngine.harvest(state, -1, now))
        assertError(GameError.WRONG_PLOT_STAGE, GameEngine.till(state, 2))
        assertError(GameError.WRONG_PLOT_STAGE, GameEngine.plant(state, 0, CropType.LETTUCE))
        assertError(GameError.NO_SEEDS, GameEngine.plant(state, 2, CropType.CARROT))
        assertError(GameError.WRONG_PLOT_STAGE, GameEngine.water(state, 2, now))
        assertEquals(120, state.coins)
        assertEquals(3, state.seedCount(CropType.LETTUCE))
    }
}
