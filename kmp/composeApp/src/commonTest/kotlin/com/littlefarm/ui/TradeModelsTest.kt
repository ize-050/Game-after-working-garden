package com.littlefarm.ui

import com.littlefarm.game.CropType
import com.littlefarm.game.GameEngine
import com.littlefarm.game.GameResult
import com.littlefarm.game.GameState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TradeModelsTest {
    private val initial = GameState.initial(0L)

    @Test
    fun multiSeedQuoteShowsTotalWithoutChangingState() {
        val quote = seedTradeQuote(initial, CropType.CARROT, 3)
        assertEquals(105L, quote.totalCoins)
        assertEquals(0L, quote.missingCoins)
        assertTrue(quote.canTrade)
        assertEquals(120, initial.coins)
        assertEquals(0, initial.seedCount(CropType.CARROT))
    }

    @Test
    fun unaffordableSeedQuoteReportsExactShortfall() {
        val quote = seedTradeQuote(initial, CropType.CARROT, 4)
        assertEquals(140L, quote.totalCoins)
        assertEquals(20L, quote.missingCoins)
        assertEquals(TradeBlocker.NOT_ENOUGH_COINS, quote.blocker)
        assertFalse(quote.canTrade)
    }

    @Test
    fun seedTotalsDoNotOverflowAtExtremeQuantity() {
        val quote = seedTradeQuote(initial, CropType.PUMPKIN, Int.MAX_VALUE)
        assertEquals(128_849_018_820L, quote.totalCoins)
        assertEquals(128_849_018_700L, quote.missingCoins)
        assertFalse(quote.canTrade)
    }

    @Test
    fun seedCapacityPreventsInventoryOverflow() {
        val state = initial.copy(seeds = initial.seeds + (CropType.LETTUCE to Int.MAX_VALUE - 1))
        assertTrue(seedTradeQuote(state, CropType.LETTUCE, 1).canTrade)
        val quote = seedTradeQuote(state, CropType.LETTUCE, 2)
        assertEquals(1, quote.maximumQuantity)
        assertEquals(TradeBlocker.INVENTORY_FULL, quote.blocker)
    }

    @Test
    fun sellQuoteShowsWholeSelectionAndBoundsAvailableProduce() {
        val state = initial.copy(produce = initial.produce + (CropType.PUMPKIN to 3))
        val quote = produceTradeQuote(state, CropType.PUMPKIN, 3)
        assertEquals(375L, quote.totalCoins)
        assertEquals(3, quote.maximumQuantity)
        assertTrue(quote.canTrade)
        assertEquals(TradeBlocker.NOT_ENOUGH_PRODUCE, produceTradeQuote(state, CropType.PUMPKIN, 4).blocker)
    }

    @Test
    fun emptyBasketCannotBeSold() {
        val quote = produceTradeQuote(initial, CropType.LETTUCE, 1)
        assertEquals(0, quote.maximumQuantity)
        assertEquals(TradeBlocker.NOT_ENOUGH_PRODUCE, quote.blocker)
        assertFalse(quote.canTrade)
    }

    @Test
    fun sellingChecksFinalWalletNotOnlyTransactionTotal() {
        val state = initial.copy(coins = Int.MAX_VALUE - 18, produce = initial.produce + (CropType.LETTUCE to 2))
        assertTrue(produceTradeQuote(state, CropType.LETTUCE, 1).canTrade)
        assertEquals(TradeBlocker.WALLET_FULL, produceTradeQuote(state, CropType.LETTUCE, 2).blocker)
    }

    @Test
    fun invalidQuantitiesCannotProduceActionableQuotes() {
        for (quantity in listOf(0, -1, Int.MIN_VALUE)) {
            assertEquals(TradeBlocker.INVALID_QUANTITY, seedTradeQuote(initial, CropType.LETTUCE, quantity).blocker)
            assertEquals(TradeBlocker.INVALID_QUANTITY, produceTradeQuote(initial, CropType.LETTUCE, quantity).blocker)
            assertEquals(0L, produceTradeQuote(initial, CropType.LETTUCE, quantity).totalCoins)
        }
    }

    @Test
    fun quotesAndCommittedEngineTransactionsAgreeForAllCrops() {
        for (crop in CropType.entries) {
            val state = initial.copy(coins = 2_000, produce = initial.produce + (crop to 4))
            val buy = seedTradeQuote(state, crop, 3)
            val bought = assertIs<GameResult.Success>(GameEngine.buySeeds(state, crop, 3)).state
            assertTrue(buy.canTrade)
            assertEquals(state.coins.toLong() - buy.totalCoins, bought.coins.toLong())
            assertEquals(state.seedCount(crop) + 3, bought.seedCount(crop))
            val sell = produceTradeQuote(state, crop, 4)
            val sold = assertIs<GameResult.Success>(GameEngine.sellProduce(state, crop, 4)).state
            assertTrue(sell.canTrade)
            assertEquals(state.coins.toLong() + sell.totalCoins, sold.coins.toLong())
            assertEquals(0, sold.produceCount(crop))
        }
    }
}
