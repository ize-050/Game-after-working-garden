package com.littlefarm.ui

import com.littlefarm.game.CropType
import com.littlefarm.game.GameState

/** Presentation-only quotes. GameEngine must still authorize each committed transaction. */
internal data class TradeQuote(
    val totalCoins: Long,
    val missingCoins: Long = 0,
    val maximumQuantity: Int,
    val blocker: TradeBlocker? = null,
) {
    val canTrade: Boolean get() = blocker == null
}

internal enum class TradeBlocker {
    INVALID_QUANTITY, NOT_ENOUGH_COINS, INVENTORY_FULL, NOT_ENOUGH_PRODUCE, WALLET_FULL,
}

internal fun seedTradeQuote(state: GameState, crop: CropType, quantity: Int): TradeQuote {
    val total = crop.seedPrice.toLong() * quantity.coerceAtLeast(0)
    val capacity = (Int.MAX_VALUE.toLong() - state.seedCount(crop)).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
    val shortfall = (total - state.coins.toLong()).coerceAtLeast(0)
    val blocker = when {
        quantity <= 0 -> TradeBlocker.INVALID_QUANTITY
        shortfall > 0 -> TradeBlocker.NOT_ENOUGH_COINS
        quantity > capacity -> TradeBlocker.INVENTORY_FULL
        else -> null
    }
    return TradeQuote(total, shortfall, capacity, blocker)
}

internal fun produceTradeQuote(state: GameState, crop: CropType, quantity: Int): TradeQuote {
    val total = crop.sellPrice.toLong() * quantity.coerceAtLeast(0)
    val available = state.produceCount(crop).coerceAtLeast(0)
    val blocker = when {
        quantity <= 0 -> TradeBlocker.INVALID_QUANTITY
        quantity > available -> TradeBlocker.NOT_ENOUGH_PRODUCE
        state.coins.toLong() + total > Int.MAX_VALUE -> TradeBlocker.WALLET_FULL
        else -> null
    }
    return TradeQuote(totalCoins = total, maximumQuantity = available, blocker = blocker)
}
