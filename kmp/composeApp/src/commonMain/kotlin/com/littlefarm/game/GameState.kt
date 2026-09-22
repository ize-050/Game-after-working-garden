package com.littlefarm.game

enum class CropType(
    val thaiName: String,
    val growthDurationMillis: Long,
    val seedPrice: Int,
    val sellPrice: Int,
) {
    LETTUCE("ผักกาด", 2 * 60_000L, 10, 18),
    RADISH("หัวไชเท้า", 5 * 60_000L, 20, 38),
    CARROT("แครอต", 10 * 60_000L, 35, 70),
    PUMPKIN("ฟักทอง", 20 * 60_000L, 60, 125),
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
        readyAtMillis?.let { (it - effectiveNowMillis).coerceAtLeast(0L) } ?: 0L
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
) {
    fun seedCount(crop: CropType): Int = seeds[crop] ?: 0

    fun produceCount(crop: CropType): Int = produce[crop] ?: 0

    fun effectiveNowMillis(nowMillis: Long): Long = nowMillis + demoOffsetMillis

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
    UPGRADE_ALREADY_OWNED,
    WATERING_CAN_REQUIRED,
    NOTHING_TO_WATER,
    RESCUE_NOT_AVAILABLE,
    VALUE_OUT_OF_RANGE,
}

sealed interface GameResult {
    data class Success(val state: GameState, val notice: String) : GameResult
    data class Failure(val error: GameError) : GameResult
}
