package com.littlefarm.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ProgressionTest {
    private val now = 1_000_000L
    private val initial = GameState.initial(now)

    private fun GameResult.success(): GameState = assertIs<GameResult.Success>(this).state
    private fun assertError(error: GameError, result: GameResult) =
        assertEquals(error, assertIs<GameResult.Failure>(result).error)

    @Test fun starterCropsStayUnlockedAndNewCropLevelsAreEnforcedAtBuyAndPlant() {
        for (crop in CropType.entries.take(4)) assertTrue(initial.isUnlocked(crop))
        for (crop in CropType.entries.drop(4)) {
            assertFalse(initial.isUnlocked(crop))
            assertError(GameError.CROP_LOCKED, GameEngine.buySeeds(initial, crop))
            val stocked = initial.copy(seeds = initial.seeds + (crop to 1))
            assertError(GameError.CROP_LOCKED, GameEngine.plant(stocked, 2, crop))
            val unlocked = initial.copy(xp = (crop.unlockLevel - 1) * 100, coins = 1_000)
            val bought = GameEngine.buySeeds(unlocked, crop).success()
            val planted = GameEngine.plant(bought, 2, crop).success()
            val watered = GameEngine.water(planted, 2, now).success()
            val harvested = GameEngine.harvest(watered, 2, now + crop.growthDurationMillis).success()
            assertEquals(crop.harvestXp, harvested.xp - unlocked.xp)
            assertEquals(1, harvested.harvestCount(crop))
        }
    }

    @Test fun harvestLevelsUpAndUnlocksTomatoesWithoutRewritingTheCurrentOrder() {
        val before = initial.copy(xp = 95)
        val result = assertIs<GameResult.Success>(GameEngine.harvest(before, 5, now))
        assertEquals(103, result.state.xp)
        assertEquals(2, result.state.level)
        assertTrue(result.state.isUnlocked(CropType.TOMATO))
        assertEquals(before.currentOrder, result.state.currentOrder)
        assertTrue(result.notice.contains("มะเขือเทศ"))
    }

    @Test fun neighborRequestsContinueThroughManyCyclesAndNeverAskForALockedCrop() {
        var state = initial
        repeat(100) { index ->
            val request = state.currentOrder
            assertTrue(state.isUnlocked(request.crop), "Request $index needs ${request.crop}")
            val stocked = state.copy(produce = state.produce + (request.crop to request.quantity))
            val completed = GameEngine.fulfillOrder(stocked).success()
            assertEquals(index + 1, completed.completedOrders)
            assertEquals(state.coins + request.coins, completed.coins)
            assertEquals(state.xp + request.xp, completed.xp)
            assertEquals(0, completed.produceCount(request.crop))
            assertTrue(completed.orderClaimed)
            state = completed
        }
        assertEquals(initial.currentOrder, state.currentOrder)
        assertEquals(state.currentOrder, state.copy(xp = state.xp + 500).currentOrder)
    }

    @Test fun staleOrderTapCannotDeliverTheNextUnseenRequestEvenWithEnoughProduce() {
        val stocked = initial.copy(produce = CropType.entries.associateWith { 100 })
        val expected = stocked.completedOrders
        val delivered = GameEngine.fulfillOrder(stocked, expectedCompletedOrders = expected).success()
        val snapshot = GameSaveCodec.encode(delivered)
        assertTrue(delivered.produceCount(delivered.currentOrder.crop) >= delivered.currentOrder.quantity)
        assertError(GameError.ORDER_CHANGED, GameEngine.fulfillOrder(delivered, expectedCompletedOrders = expected))
        assertEquals(snapshot, GameSaveCodec.encode(delivered))
        assertEquals(1, delivered.completedOrders)
        assertEquals(165, delivered.coins)
        assertEquals(20, delivered.xp)
        val intentionalNext = GameEngine.fulfillOrder(delivered, expectedCompletedOrders = delivered.completedOrders).success()
        assertEquals(2, intentionalNext.completedOrders)
    }

    @Test fun collectionCountsOnlyActualHarvestsAndGrantsBadgesAndCosmeticsAtTenAndFifty() {
        var state = initial.copy(coins = 10_000)
        repeat(50) { harvestIndex ->
            state = GameEngine.harvest(state, 5, now).success()
            assertEquals(harvestIndex + 1, state.harvestCount(CropType.LETTUCE))
            if (harvestIndex == 8) assertTrue(state.earnedBadges.isEmpty())
            if (harvestIndex == 9) {
                assertEquals(listOf(10), state.earnedBadges.map { it.threshold })
                assertTrue(Decoration.FLOWER_FENCE in state.ownedDecor)
                assertFalse(Decoration.STAR_LAMP in state.ownedDecor)
            }
            if (harvestIndex < 49) state = state.copy(plots = state.plots.mapIndexed { index, plot ->
                if (index == 5) Plot(PlotStage.GROWING, CropType.LETTUCE, now) else plot
            })
        }
        assertEquals(listOf(10, 50), state.earnedBadges.map { it.threshold })
        assertEquals(setOf(Decoration.FLOWER_FENCE, Decoration.STAR_LAMP), state.ownedDecor)
        val sold = GameEngine.sellProduce(state, CropType.LETTUCE, 20).success()
        assertEquals(50, sold.harvestCount(CropType.LETTUCE))
        val delivered = GameEngine.fulfillOrder(sold).success()
        assertEquals(50, delivered.harvestCount(CropType.LETTUCE))
        assertEquals(state.earnedBadges, delivered.earnedBadges)
    }

    @Test fun decorationsCostCoinsOnceAndOnlyOwnedItemsCanBeEquippedOrReplaced() {
        val start = initial.copy(coins = 1_000)
        assertError(GameError.DECORATION_NOT_OWNED, GameEngine.equipDecoration(start, Decoration.WOOD_FENCE))
        assertError(GameError.DECORATION_REWARD_ONLY, GameEngine.buyDecoration(start, Decoration.STAR_LAMP))
        assertError(GameError.NOT_ENOUGH_COINS, GameEngine.buyDecoration(start.copy(coins = 0), Decoration.WOOD_FENCE))
        val bought = GameEngine.buyDecoration(start, Decoration.WOOD_FENCE).success()
        assertEquals(920, bought.coins)
        assertTrue(bought.equippedDecor.isEmpty())
        assertError(GameError.DECORATION_ALREADY_OWNED, GameEngine.buyDecoration(bought, Decoration.WOOD_FENCE))
        val equipped = GameEngine.equipDecoration(bought, Decoration.WOOD_FENCE).success()
        assertEquals(Decoration.WOOD_FENCE, equipped.equippedDecor[DecorationSlot.FENCE])
        val withReward = equipped.copy(ownedDecor = equipped.ownedDecor + Decoration.FLOWER_FENCE)
        val replaced = GameEngine.equipDecoration(withReward, Decoration.FLOWER_FENCE).success()
        assertEquals(1, replaced.equippedDecor.size)
        assertEquals(Decoration.FLOWER_FENCE, replaced.equippedDecor[DecorationSlot.FENCE])
        val stored = GameEngine.unequipDecoration(replaced, DecorationSlot.FENCE).success()
        assertTrue(stored.equippedDecor.isEmpty())
        assertEquals(replaced.ownedDecor, stored.ownedDecor)
        assertEquals(replaced.coins, stored.coins)
    }

    @Test fun catNameIsTrimmedAndRejectsEmptyControlOrOverlongNames() {
        assertEquals("ปุยฝ้าย", GameEngine.renameCat(initial, " ปุยฝ้าย ").success().cat.name)
        for (name in listOf("", "   ", "a\nb", "a".repeat(25))) {
            assertError(GameError.INVALID_CAT_NAME, GameEngine.renameCat(initial, name))
        }
    }

    @Test fun catAffectionPersistsWithoutDecayAndUnlocksPosesWithARealClockCooldown() {
        var state = initial
        repeat(15) { index ->
            val petAt = now + index * CatState.PET_COOLDOWN_MILLIS
            state = GameEngine.petCat(state, petAt).success()
            assertEquals(index + 1, state.cat.bond)
            assertError(GameError.CAT_NEEDS_REST, GameEngine.petCat(state, petAt))
            assertError(GameError.CAT_NEEDS_REST, GameEngine.petCat(state, petAt + 59_999L))
            assertError(GameError.CAT_NEEDS_REST, GameEngine.petCat(state, petAt - 1L))
            if (index == 3) assertEquals(CatPose.SITTING, state.cat.pose)
            if (index == 4) assertEquals(CatPose.PURRING, state.cat.pose)
        }
        assertEquals(CatPose.ROLLING, state.cat.pose)
        assertEquals(CatPose.entries.toList(), state.cat.unlockedPoses)
        val demo = GameEngine.advanceDemoTime(state).success()
        assertError(GameError.CAT_NEEDS_REST, GameEngine.petCat(demo, state.cat.lastPettedAtMillis!!))
        val muchLater = now + 365L * 86_400_000L
        assertEquals(16, GameEngine.petCat(state, muchLater).success().cat.bond)
        assertEquals(15, state.cat.bond)
    }

    @Test fun progressionOverflowFailsAtomically() {
        assertError(GameError.VALUE_OUT_OF_RANGE, GameEngine.harvest(initial.copy(xp = Int.MAX_VALUE), 5, now))
        val collectionFull = initial.copy(harvestCounts = initial.harvestCounts + (CropType.LETTUCE to Int.MAX_VALUE))
        assertError(GameError.VALUE_OUT_OF_RANGE, GameEngine.harvest(collectionFull, 5, now))
        assertTrue(collectionFull.plots[5].isReady(now))
        assertEquals(0, collectionFull.produceCount(CropType.LETTUCE))
        assertError(GameError.VALUE_OUT_OF_RANGE, GameEngine.petCat(initial.copy(cat = CatState(bond = Int.MAX_VALUE)), now))
        assertError(GameError.VALUE_OUT_OF_RANGE, GameEngine.petCat(initial, -1L))
        val ordersFull = initial.copy(completedOrders = Int.MAX_VALUE, xp = 400,
            produce = CropType.entries.associateWith { 100 })
        assertError(GameError.VALUE_OUT_OF_RANGE, GameEngine.fulfillOrder(ordersFull))
        val clockFull = initial.copy(demoOffsetMillis = Long.MAX_VALUE)
        assertEquals(Long.MAX_VALUE, clockFull.effectiveNowMillis(now))
        assertError(GameError.VALUE_OUT_OF_RANGE, GameEngine.water(clockFull, 3, now))
    }
}
