package com.littlefarm.game

/** Pure game rules. The caller owns storage and provides wall-clock time. */
object GameEngine {
    fun till(state: GameState, plotIndex: Int): GameResult {
        val plot = state.plots.getOrNull(plotIndex) ?: return fail(GameError.INVALID_PLOT)
        if (plot.stage != PlotStage.UNTILLED) return fail(GameError.WRONG_PLOT_STAGE)
        return success(
            state.withPlot(plotIndex, Plot(stage = PlotStage.TILLED)),
            "ขุดแปลงแล้ว เลือกเมล็ดมาปลูกกัน",
        )
    }

    fun plant(state: GameState, plotIndex: Int, crop: CropType): GameResult {
        val plot = state.plots.getOrNull(plotIndex) ?: return fail(GameError.INVALID_PLOT)
        if (plot.stage != PlotStage.TILLED) return fail(GameError.WRONG_PLOT_STAGE)
        if (!state.isUnlocked(crop)) return fail(GameError.CROP_LOCKED)
        if (state.seedCount(crop) < 1) return fail(GameError.NO_SEEDS)
        val planted = state.withPlot(plotIndex, Plot(stage = PlotStage.PLANTED, crop = crop))
        return success(
            planted.copy(seeds = state.seeds + (crop to state.seedCount(crop) - 1)),
            "ปลูก${crop.thaiName}แล้ว รดน้ำเพื่อเริ่มเติบโต",
        )
    }

    fun water(state: GameState, plotIndex: Int, nowMillis: Long): GameResult {
        val plot = state.plots.getOrNull(plotIndex) ?: return fail(GameError.INVALID_PLOT)
        val crop = plot.crop
        if (plot.stage != PlotStage.PLANTED || crop == null) {
            return fail(GameError.WRONG_PLOT_STAGE)
        }
        val readyAt = readyAt(state, crop, nowMillis) ?: return fail(GameError.VALUE_OUT_OF_RANGE)
        return success(
            state.withPlot(plotIndex, plot.copy(stage = PlotStage.GROWING, readyAtMillis = readyAt)),
            "รดน้ำแล้ว ให้เวลาเขาเติบโตสักนิด",
        )
    }

    fun harvest(state: GameState, plotIndex: Int, nowMillis: Long): GameResult {
        val plot = state.plots.getOrNull(plotIndex) ?: return fail(GameError.INVALID_PLOT)
        if (!plot.isReady(state.effectiveNowMillis(nowMillis))) return fail(GameError.CROP_NOT_READY)
        val crop = plot.crop ?: return fail(GameError.WRONG_PLOT_STAGE)
        if (state.produceCount(crop) == Int.MAX_VALUE || state.harvestCount(crop) == Int.MAX_VALUE ||
            state.xp > Int.MAX_VALUE - crop.harvestXp) return fail(GameError.VALUE_OUT_OF_RANGE)
        val count = state.harvestCount(crop) + 1
        val newBadges = CollectionBadge.forCrop(crop).filter { it.threshold == count }
        val newLevel = 1 + (state.xp + crop.harvestXp) / 100
        val unlocked = CropType.entries.filter { it.unlockLevel > state.level && it.unlockLevel <= newLevel }
        val harvested = state.withPlot(plotIndex, Plot(stage = PlotStage.TILLED))
        return success(
            harvested.copy(
                produce = state.produce + (crop to state.produceCount(crop) + 1),
                xp = state.xp + crop.harvestXp,
                harvestCounts = state.harvestCounts + (crop to count),
                ownedDecor = state.ownedDecor + newBadges.map { it.reward },
            ),
            buildString {
                append("เก็บ${crop.thaiName} 1 ต้น +${crop.harvestXp} XP")
                if (newBadges.isNotEmpty()) append(" · ได้ตรา${newBadges.first().thaiName}และของแต่งสวน")
                if (newLevel > state.level) append(" · เลเวล $newLevel!")
                if (unlocked.isNotEmpty()) append(" ปลดล็อก${unlocked.joinToString { it.thaiName }}")
            },
        )
    }

    fun buySeeds(state: GameState, crop: CropType, quantity: Int = 1): GameResult {
        if (quantity <= 0) return fail(GameError.INVALID_QUANTITY)
        if (!state.isUnlocked(crop)) return fail(GameError.CROP_LOCKED)
        val cost = crop.seedPrice.toLong() * quantity
        if (cost > state.coins) return fail(GameError.NOT_ENOUGH_COINS)
        val count = state.seedCount(crop).toLong() + quantity
        if (count > Int.MAX_VALUE) return fail(GameError.VALUE_OUT_OF_RANGE)
        return success(
            state.copy(
                coins = state.coins - cost.toInt(),
                seeds = state.seeds + (crop to count.toInt()),
            ),
            "ได้รับเมล็ด${crop.thaiName} $quantity เมล็ด",
        )
    }

    fun sellProduce(state: GameState, crop: CropType, quantity: Int = 1): GameResult {
        if (quantity <= 0) return fail(GameError.INVALID_QUANTITY)
        if (state.produceCount(crop) < quantity) return fail(GameError.NOT_ENOUGH_PRODUCE)
        val proceeds = crop.sellPrice.toLong() * quantity
        val newCoins = state.coins.toLong() + proceeds
        if (newCoins > Int.MAX_VALUE) return fail(GameError.VALUE_OUT_OF_RANGE)
        return success(
            state.copy(
                coins = newCoins.toInt(),
                produce = state.produce + (crop to state.produceCount(crop) - quantity),
            ),
            "ขาย${crop.thaiName}แล้ว +$proceeds เหรียญ",
        )
    }

    fun fulfillOrder(state: GameState, expectedCompletedOrders: Int = state.completedOrders): GameResult {
        if (expectedCompletedOrders != state.completedOrders) return fail(GameError.ORDER_CHANGED)
        val order = state.currentOrder
        if (!state.isUnlocked(order.crop)) return fail(GameError.CROP_LOCKED)
        if (state.produceCount(order.crop) < order.quantity) return fail(GameError.NOT_ENOUGH_PRODUCE)
        if (state.coins > Int.MAX_VALUE - order.coins || state.xp > Int.MAX_VALUE - order.xp ||
            state.completedOrders == Int.MAX_VALUE) {
            return fail(GameError.VALUE_OUT_OF_RANGE)
        }
        val nextLevel = 1 + (state.xp + order.xp) / 100
        val unlocked = CropType.entries.filter { it.unlockLevel > state.level && it.unlockLevel <= nextLevel }
        return success(
            state.copy(
                coins = state.coins + order.coins,
                xp = state.xp + order.xp,
                produce = state.produce + (order.crop to state.produceCount(order.crop) - order.quantity),
                orderClaimed = true,
                completedOrders = state.completedOrders + 1,
            ),
            buildString {
                append("${order.npc}ขอบใจนะ! +${order.coins} เหรียญ +${order.xp} XP")
                if (nextLevel > state.level) append(" · เลเวล $nextLevel!")
                if (unlocked.isNotEmpty()) append(" ปลดล็อก${unlocked.joinToString { it.thaiName }}")
                append(" · มีคำขอใหม่แล้ว ไม่ต้องรีบส่ง")
            },
        )
    }

    fun buyDecoration(state: GameState, decoration: Decoration): GameResult {
        if (decoration in state.ownedDecor) return fail(GameError.DECORATION_ALREADY_OWNED)
        if (decoration.rewardOnly) return fail(GameError.DECORATION_REWARD_ONLY)
        if (state.coins < decoration.price) return fail(GameError.NOT_ENOUGH_COINS)
        return success(
            state.copy(coins = state.coins - decoration.price, ownedDecor = state.ownedDecor + decoration),
            "ได้${decoration.thaiName}แล้ว เลือกนำไปแต่งสวนได้เลย",
        )
    }

    fun equipDecoration(state: GameState, decoration: Decoration): GameResult {
        if (decoration !in state.ownedDecor) return fail(GameError.DECORATION_NOT_OWNED)
        return success(
            state.copy(equippedDecor = state.equippedDecor + (decoration.slot to decoration)),
            "แต่งสวนด้วย${decoration.thaiName}แล้ว",
        )
    }

    fun unequipDecoration(state: GameState, slot: DecorationSlot): GameResult = success(
        state.copy(equippedDecor = state.equippedDecor - slot),
        "เก็บ${slot.thaiName}เข้าคลังแล้ว นำกลับมาใช้ได้เสมอ",
    )

    fun renameCat(state: GameState, name: String): GameResult {
        val trimmed = name.trim()
        if (!CatState.isValidName(trimmed)) return fail(GameError.INVALID_CAT_NAME)
        return success(state.copy(cat = state.cat.copy(name = trimmed)), "จากนี้เรียกฉันว่า $trimmed นะ เมี้ยว~")
    }

    fun petCat(state: GameState, nowMillis: Long): GameResult {
        if (nowMillis < 0 || state.cat.bond == Int.MAX_VALUE) return fail(GameError.VALUE_OUT_OF_RANGE)
        if (!state.cat.canPet(nowMillis)) return fail(GameError.CAT_NEEDS_REST)
        val cat = state.cat.copy(bond = state.cat.bond + 1, lastPettedAtMillis = nowMillis)
        return success(
            state.copy(cat = cat),
            "${cat.name}ชอบให้ลูบหัว · ความสนิท +1" +
                if (cat.pose != state.cat.pose) " · ท่าใหม่: ${cat.pose.thaiName}" else "",
        )
    }

    fun buyUpgrade(state: GameState, upgrade: UpgradeType): GameResult {
        val owned = when (upgrade) {
            UpgradeType.EXTRA_PLOTS -> state.upgrades.expandedPlots
            UpgradeType.LARGE_WATERING_CAN -> state.upgrades.largeWateringCan
        }
        if (owned) return fail(GameError.UPGRADE_ALREADY_OWNED)
        if (state.coins < upgrade.price) return fail(GameError.NOT_ENOUGH_COINS)
        return when (upgrade) {
            UpgradeType.EXTRA_PLOTS -> success(
                state.copy(
                    coins = state.coins - upgrade.price,
                    plots = state.plots + List(3) { Plot() },
                    upgrades = state.upgrades.copy(expandedPlots = true),
                ),
                "สวนขยายเป็น 9 แปลงแล้ว",
            )
            UpgradeType.LARGE_WATERING_CAN -> success(
                state.copy(
                    coins = state.coins - upgrade.price,
                    upgrades = state.upgrades.copy(largeWateringCan = true),
                ),
                "ได้บัวรดน้ำใบใหญ่แล้ว รดน้ำทุกแปลงได้ในครั้งเดียว",
            )
        }
    }

    fun waterAll(state: GameState, nowMillis: Long): GameResult {
        if (!state.upgrades.largeWateringCan) return fail(GameError.WATERING_CAN_REQUIRED)
        val dryPlots = state.plots.filter { it.stage == PlotStage.PLANTED && it.crop != null }
        if (dryPlots.isEmpty()) return fail(GameError.NOTHING_TO_WATER)
        if (dryPlots.any { readyAt(state, it.crop!!, nowMillis) == null }) {
            return fail(GameError.VALUE_OUT_OF_RANGE)
        }
        val wateredPlots = state.plots.map { plot ->
            if (plot.stage == PlotStage.PLANTED && plot.crop != null) {
                plot.copy(
                    stage = PlotStage.GROWING,
                    readyAtMillis = readyAt(state, plot.crop, nowMillis),
                )
            } else {
                plot
            }
        }
        return success(state.copy(plots = wateredPlots), "รดน้ำแล้ว ${dryPlots.size} แปลง")
    }

    fun canClaimRescueSeed(state: GameState): Boolean =
        state.coins < CropType.LETTUCE.seedPrice &&
            CropType.entries.all { state.seedCount(it) == 0 && state.produceCount(it) == 0 } &&
            state.plots.all { it.crop == null }

    fun claimRescueSeed(state: GameState): GameResult {
        if (!canClaimRescueSeed(state)) return fail(GameError.RESCUE_NOT_AVAILABLE)
        return success(
            state.copy(seeds = state.seeds + (CropType.LETTUCE to 1)),
            "ป้าพรให้เมล็ดผักกาด 1 เมล็ด เริ่มปลูกใหม่ได้เลย",
        )
    }

    fun advanceDemoTime(state: GameState, deltaMillis: Long = 5 * 60_000L): GameResult {
        if (deltaMillis <= 0) return fail(GameError.INVALID_QUANTITY)
        if (state.demoOffsetMillis > Long.MAX_VALUE - deltaMillis) {
            return fail(GameError.VALUE_OUT_OF_RANGE)
        }
        return success(
            state.copy(demoOffsetMillis = state.demoOffsetMillis + deltaMillis),
            "ขยับเวลาเดโมไปข้างหน้าแล้ว",
        )
    }

    private fun readyAt(state: GameState, crop: CropType, nowMillis: Long): Long? {
        if (nowMillis < 0 || nowMillis > Long.MAX_VALUE - state.demoOffsetMillis) return null
        val effectiveNow = state.effectiveNowMillis(nowMillis)
        if (effectiveNow > Long.MAX_VALUE - crop.growthDurationMillis) return null
        return effectiveNow + crop.growthDurationMillis
    }

    private fun GameState.withPlot(index: Int, plot: Plot): GameState =
        copy(plots = plots.mapIndexed { currentIndex, current -> if (currentIndex == index) plot else current })

    private fun success(state: GameState, notice: String): GameResult = GameResult.Success(state, notice)

    private fun fail(error: GameError): GameResult = GameResult.Failure(error)
}
