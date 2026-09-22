package com.littlefarm.game

enum class CropType(
    val thaiName: String,
    val growthDurationMillis: Long,
    val seedPrice: Int,
    val sellPrice: Int,
    val unlockLevel: Int = 1,
    val harvestXp: Int,
) {
    LETTUCE("ผักกาด", 2 * 60_000L, 10, 18, harvestXp = 8),
    RADISH("หัวไชเท้า", 5 * 60_000L, 20, 38, harvestXp = 10),
    CARROT("แครอต", 10 * 60_000L, 35, 70, harvestXp = 12),
    PUMPKIN("ฟักทอง", 20 * 60_000L, 60, 125, harvestXp = 15),
    TOMATO("มะเขือเทศ", 15 * 60_000L, 45, 90, 2, 14),
    STRAWBERRY("สตรอว์เบอร์รี", 30 * 60_000L, 75, 160, 3, 20),
    FLOWER("ดอกไม้", 45 * 60_000L, 90, 200, 4, 25),
}

enum class PlotStage { UNTILLED, TILLED, PLANTED, GROWING }

data class Plot(
    val stage: PlotStage = PlotStage.UNTILLED,
    val crop: CropType? = null,
    val readyAtMillis: Long? = null,
) {
    fun isReady(effectiveNowMillis: Long): Boolean =
        stage == PlotStage.GROWING && crop != null &&
            readyAtMillis != null && effectiveNowMillis >= readyAtMillis

    fun remainingMillis(effectiveNowMillis: Long): Long =
        readyAtMillis?.let { readyAt ->
            if (effectiveNowMillis >= readyAt) 0L else readyAt - effectiveNowMillis.coerceAtLeast(0L)
        } ?: 0L
}

data class Upgrades(
    val expandedPlots: Boolean = false,
    val largeWateringCan: Boolean = false,
)

data class GameState(
    val coins: Int,
    val xp: Int,
    val seeds: Map<CropType, Int>,
    val produce: Map<CropType, Int>,
    val plots: List<Plot>,
    val upgrades: Upgrades = Upgrades(),
    val orderClaimed: Boolean = false,
    val demoOffsetMillis: Long = 0L,
    /** Optional v1 metadata; older saves remain valid and get an epoch on their next local write. */
    val startedAtMillis: Long? = null,
    val completedOrders: Int = if (orderClaimed) 1 else 0,
    val harvestCounts: Map<CropType, Int> = CropType.entries.associateWith { 0 },
    val ownedDecor: Set<Decoration> = emptySet(),
    val equippedDecor: Map<DecorationSlot, Decoration> = emptyMap(),
    val cat: CatState = CatState(),
) {
    fun seedCount(crop: CropType): Int = seeds[crop] ?: 0

    fun produceCount(crop: CropType): Int = produce[crop] ?: 0

    /** Saturates for rendering; actions that create timers reject an overflowing clock. */
    fun effectiveNowMillis(nowMillis: Long): Long =
        if (nowMillis > Long.MAX_VALUE - demoOffsetMillis) Long.MAX_VALUE else nowMillis + demoOffsetMillis

    fun isUnlocked(crop: CropType): Boolean = level >= crop.unlockLevel

    fun harvestCount(crop: CropType): Int = harvestCounts[crop] ?: 0

    val currentOrder: NeighborOrder get() = NeighborOrder.at(completedOrders)

    val earnedBadges: List<CollectionBadge> get() = CropType.entries.flatMap { crop ->
        CollectionBadge.forCrop(crop).filter { harvestCount(crop) >= it.threshold }
    }

    val level: Int get() = 1 + xp / 100
    val xpInLevel: Int get() = xp % 100

    /** Elapsed real 24-hour days, not a season calendar; the crop demo clock does not advance it. */
    fun dayNumber(nowMillis: Long): Long {
        val start = startedAtMillis ?: return 1L
        return if (nowMillis <= start) 1L else 1L + (nowMillis - start) / 86_400_000L
    }

    companion object {
        fun initial(nowMillis: Long): GameState = GameState(
            coins = 120,
            xp = 0,
            startedAtMillis = nowMillis,
            seeds = CropType.entries.associateWith {
                when (it) {
                    CropType.LETTUCE -> 3
                    CropType.RADISH -> 2
                    else -> 0
                }
            },
            produce = CropType.entries.associateWith { 0 },
            plots = listOf(
                Plot(),
                Plot(),
                Plot(stage = PlotStage.TILLED),
                Plot(stage = PlotStage.PLANTED, crop = CropType.LETTUCE),
                Plot(
                    stage = PlotStage.GROWING,
                    crop = CropType.RADISH,
                    readyAtMillis = nowMillis + 2 * 60_000L,
                ),
                Plot(
                    stage = PlotStage.GROWING,
                    crop = CropType.LETTUCE,
                    readyAtMillis = nowMillis,
                ),
            ),
        )
    }
}

enum class UpgradeType(val price: Int) {
    EXTRA_PLOTS(180),
    LARGE_WATERING_CAN(150),
}

enum class GameError {
    INVALID_PLOT,
    WRONG_PLOT_STAGE,
    NO_SEEDS,
    CROP_NOT_READY,
    INVALID_QUANTITY,
    NOT_ENOUGH_COINS,
    NOT_ENOUGH_PRODUCE,
    ORDER_ALREADY_CLAIMED,
    ORDER_CHANGED,
    UPGRADE_ALREADY_OWNED,
    WATERING_CAN_REQUIRED,
    NOTHING_TO_WATER,
    RESCUE_NOT_AVAILABLE,
    VALUE_OUT_OF_RANGE,
    CROP_LOCKED,
    DECORATION_ALREADY_OWNED,
    DECORATION_NOT_OWNED,
    DECORATION_REWARD_ONLY,
    INVALID_CAT_NAME,
    CAT_NEEDS_REST,
}

sealed interface GameResult {
    data class Success(val state: GameState, val notice: String) : GameResult
    data class Failure(val error: GameError) : GameResult
}
