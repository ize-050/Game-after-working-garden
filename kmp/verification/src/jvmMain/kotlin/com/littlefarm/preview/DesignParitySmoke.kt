@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.littlefarm.preview

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import com.littlefarm.App
import com.littlefarm.game.CropType
import com.littlefarm.game.GameSaveCodec
import com.littlefarm.game.GameState
import com.littlefarm.game.Plot
import com.littlefarm.game.PlotStage
import com.littlefarm.game.Upgrades
import com.littlefarm.platform.SaveStore
import kotlinx.coroutines.delay
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

private class DesignParitySave(initial: GameState) : SaveStore {
    private var json = GameSaveCodec.encode(initial)
    var writes = 0
        private set
    val state: GameState get() = checkNotNull(GameSaveCodec.decode(json))
    override fun read() = json
    override fun write(value: String) { json = value; writes++ }
}

private fun SemanticsNode.parityDescendants(): List<SemanticsNode> =
    listOf(this) + children.flatMap { it.parityDescendants() }

/** Uses the production shared composables with isolated saves at the narrow 320 dp viewport. */
private class DesignParityScene(initial: GameState, context: CoroutineContext) : AutoCloseable {
    val save = DesignParitySave(initial)
    private val scene = ImageComposeScene(640, 1280, density = Density(2f), coroutineContext = context)
    private var frameNanos = 0L

    suspend fun start(page: String) {
        scene.setContent { App(save, initialPage = page) }
        settle()
    }

    private suspend fun settle() {
        repeat(5) {
            frameNanos += 16_666_667L
            scene.render(frameNanos).close()
            delay(25)
        }
    }

    private fun nodes() = scene.semanticsOwners.flatMap { it.rootSemanticsNode.parityDescendants() }

    fun tagged(tag: String): SemanticsNode = nodes().firstOrNull {
        it.config.getOrNull(SemanticsProperties.TestTag) == tag
    } ?: error("Missing design-parity UI tag: $tag")

    fun absent(tag: String) {
        check(nodes().none { it.config.getOrNull(SemanticsProperties.TestTag) == tag }) {
            "Design-parity UI tag should be absent: $tag"
        }
    }

    fun disabled(tag: String, expected: Boolean = true) {
        val actual = tagged(tag).config.contains(SemanticsProperties.Disabled)
        check(actual == expected) { "$tag disabled=$actual; expected $expected" }
    }

    fun textContains(tag: String, expected: String) {
        val text = tagged(tag).parityDescendants().flatMap {
            it.config.getOrNull(SemanticsProperties.Text)?.map { annotated -> annotated.text }.orEmpty()
        }.joinToString(" ")
        check(expected in text) { "$tag did not contain '$expected': $text" }
    }

    fun visibleTouchTarget(tag: String) {
        val bounds = tagged(tag).boundsInRoot
        check(bounds.width >= 96f && bounds.height >= 96f) {
            "$tag must expose at least a 48 × 48 dp target at density 2: $bounds"
        }
        check(bounds.left >= 0f && bounds.top >= 0f && bounds.right <= 640f && bounds.bottom <= 1280f) {
            "$tag must be visible without scrolling on the 320 dp Welcome screen: $bounds"
        }
    }

    fun paneTitleContains(expected: String) {
        val titles = nodes().mapNotNull { it.config.getOrNull(SemanticsProperties.PaneTitle) }
        check(titles.any { expected in it }) { "No open pane title contains '$expected': $titles" }
    }

    fun starterPlotsAndToolsFit() {
        val gameTop = tagged("account_status").boundsInRoot.bottom
        val gameBottom = tagged("nav_FARM").boundsInRoot.top
        val toolBounds = listOf("tool_till", "tool_seed", "tool_water", "tool_harvest")
            .map { tag ->
                val bounds = tagged(tag).boundsInRoot
                check(bounds.width >= 96f && bounds.height >= 96f) { "$tag must be a visible 48 dp touch target: $bounds" }
                check(bounds.left >= 0f && bounds.right <= 640f && bounds.top >= gameTop && bounds.bottom <= gameBottom) {
                    "$tag must fit above bottom navigation at 320 × 640 dp: $bounds, game=[$gameTop, $gameBottom]"
                }
                bounds
            }
        val toolsTop = toolBounds.minOf { it.top }
        repeat(6) { index ->
            val bounds = tagged("plot_$index").boundsInRoot
            check(bounds.width >= 96f && bounds.height >= 96f) { "Starter plot $index must remain a visible touch target: $bounds" }
            check(bounds.left >= 0f && bounds.right <= 640f && bounds.top >= gameTop && bounds.bottom <= gameBottom) {
                "Starter plot $index must fit in the initial game viewport: $bounds, game=[$gameTop, $gameBottom]"
            }
            check(bounds.bottom <= toolsTop) { "Starter plot $index overlaps the tools: plot=$bounds, toolsTop=$toolsTop" }
        }
    }

    suspend fun click(tag: String) {
        val node = tagged(tag)
        check(!node.config.contains(SemanticsProperties.Disabled)) {
            "Design-parity control unexpectedly disabled: $tag"
        }
        check(node.config.getOrNull(SemanticsActions.OnClick)?.action?.invoke() == true) {
            "Design-parity control not clickable: $tag"
        }
        settle()
        println("Design-parity fixture clicked $tag")
    }

    override fun close() = scene.close()
}

/** Behavioral PRD coverage, not a pixel-match assertion or native-device touch validation. */
public suspend fun runDesignParitySmoke() {
    val now = System.currentTimeMillis()
    val context = coroutineContext
    val progressed = GameState.initial(now).let { initial ->
        initial.copy(
            coins = 73,
            xp = 20,
            seeds = initial.seeds + (CropType.CARROT to 4),
            produce = initial.produce + (CropType.PUMPKIN to 2),
            plots = initial.plots + List(3) { Plot() },
            upgrades = Upgrades(expandedPlots = true, largeWateringCan = true),
            orderClaimed = true,
            completedOrders = 1,
            demoOffsetMillis = 15 * 60_000L,
        )
    }

    DesignParityScene(GameState.initial(now), context).use { fixture ->
        fixture.start("FARM")
        fixture.starterPlotsAndToolsFit()
        val before = fixture.save.state
        val writesBefore = fixture.save.writes
        fixture.click("tool_till")
        fixture.paneTitleContains("แปลง 1")
        fixture.tagged("action_till")
        check(fixture.save.state == before && fixture.save.writes == writesBefore) {
            "Tilling shortcut must open the first untilled plot without spending or modifying it"
        }
        fixture.click("action_till")
        val expected = before.copy(plots = before.plots.mapIndexed { index, plot ->
            if (index == 0) Plot(stage = PlotStage.TILLED) else plot
        })
        check(fixture.save.state == expected && fixture.save.writes == writesBefore + 1) {
            "Tilling from the toolbar must change only the first untilled plot"
        }
        fixture.tagged("seed_LETTUCE")
    }

    val twoTilled = GameState.initial(now).let { initial ->
        initial.copy(plots = initial.plots.mapIndexed { index, plot ->
            if (index == 0) Plot(stage = PlotStage.TILLED) else plot
        })
    }
    DesignParityScene(twoTilled, context).use { fixture ->
        fixture.start("FARM")
        val writesBefore = fixture.save.writes
        fixture.click("tool_seed")
        fixture.paneTitleContains("แปลง 1")
        fixture.tagged("seed_LETTUCE")
        check(fixture.save.state == twoTilled && fixture.save.writes == writesBefore)
        fixture.click("seed_LETTUCE")
        val expected = twoTilled.copy(
            seeds = twoTilled.seeds + (CropType.LETTUCE to 2),
            plots = twoTilled.plots.mapIndexed { index, plot ->
                if (index == 0) Plot(stage = PlotStage.PLANTED, crop = CropType.LETTUCE) else plot
            },
        )
        check(fixture.save.state == expected && fixture.save.writes == writesBefore + 1) {
            "Seed shortcut must plant one seed in the first tilled plot and leave later tilled plots untouched"
        }
    }

    val twoDry = GameState.initial(now).let { initial ->
        initial.copy(plots = initial.plots.mapIndexed { index, plot ->
            if (index == 1) Plot(stage = PlotStage.PLANTED, crop = CropType.CARROT) else plot
        })
    }
    DesignParityScene(twoDry, context).use { fixture ->
        fixture.start("FARM")
        val writesBefore = fixture.save.writes
        val earliestWater = System.currentTimeMillis()
        fixture.click("tool_water")
        val latestWater = System.currentTimeMillis()
        val watered = fixture.save.state
        val readyAt = checkNotNull(watered.plots[1].readyAtMillis)
        check(readyAt in (earliestWater + CropType.CARROT.growthDurationMillis)..(latestWater + CropType.CARROT.growthDurationMillis))
        val expected = twoDry.copy(plots = twoDry.plots.mapIndexed { index, plot ->
            if (index == 1) plot.copy(stage = PlotStage.GROWING, readyAtMillis = readyAt) else plot
        })
        check(watered == expected && fixture.save.writes == writesBefore + 1) {
            "Regular water shortcut must water only the first dry plot, preserving all other plots and economy"
        }
        fixture.absent("action_water")
        fixture.disabled("tool_water", expected = false)
    }

    val bigCan = twoDry.copy(upgrades = Upgrades(largeWateringCan = true))
    DesignParityScene(bigCan, context).use { fixture ->
        fixture.start("FARM")
        val writesBefore = fixture.save.writes
        val earliestWater = System.currentTimeMillis()
        fixture.click("tool_water")
        val latestWater = System.currentTimeMillis()
        val watered = fixture.save.state
        val wateringTimes = bigCan.plots.withIndex().filter { it.value.stage == PlotStage.PLANTED }.map { (index, original) ->
            val readyAt = checkNotNull(watered.plots[index].readyAtMillis)
            readyAt - checkNotNull(original.crop).growthDurationMillis
        }
        check(wateringTimes.distinct().size == 1 && wateringTimes.all { it in earliestWater..latestWater }) {
            "Large watering can must start every dry crop from one shared current time"
        }
        val waterTime = wateringTimes.first()
        val expected = bigCan.copy(plots = bigCan.plots.map { plot ->
            if (plot.stage == PlotStage.PLANTED) plot.copy(
                stage = PlotStage.GROWING,
                readyAtMillis = waterTime + checkNotNull(plot.crop).growthDurationMillis,
            ) else plot
        })
        check(watered == expected && fixture.save.writes == writesBefore + 1) {
            "Large water shortcut must update dry plots only, preserve established timers, and commit once"
        }
        fixture.absent("action_water")
        fixture.disabled("tool_water")
    }

    val twoReady = GameState.initial(now).let { initial ->
        initial.copy(plots = initial.plots.mapIndexed { index, plot ->
            if (index == 1) Plot(stage = PlotStage.GROWING, crop = CropType.CARROT, readyAtMillis = now - 1) else plot
        })
    }
    DesignParityScene(twoReady, context).use { fixture ->
        fixture.start("FARM")
        val writesBefore = fixture.save.writes
        fixture.click("tool_harvest")
        fixture.paneTitleContains("แปลง 2")
        fixture.tagged("action_harvest")
        check(fixture.save.state == twoReady && fixture.save.writes == writesBefore)
        fixture.click("action_harvest")
        val expected = twoReady.copy(
            produce = twoReady.produce + (CropType.CARROT to 1),
            xp = twoReady.xp + CropType.CARROT.harvestXp,
            harvestCounts = twoReady.harvestCounts + (CropType.CARROT to 1),
            plots = twoReady.plots.mapIndexed { index, plot -> if (index == 1) Plot(stage = PlotStage.TILLED) else plot },
        )
        check(fixture.save.state == expected && fixture.save.writes == writesBefore + 1) {
            "Harvest shortcut must collect only the first ready crop, grant harvest XP/collection credit, and not grant sale coins"
        }
        fixture.paneTitleContains("เก็บเกี่ยวความสุข")
    }

    val waitingOnly = GameState.initial(now).copy(plots = List(6) {
        Plot(stage = PlotStage.GROWING, crop = CropType.PUMPKIN, readyAtMillis = now + CropType.PUMPKIN.growthDurationMillis)
    })
    DesignParityScene(waitingOnly, context).use { fixture ->
        fixture.start("FARM")
        listOf("tool_till", "tool_seed", "tool_water", "tool_harvest").forEach { fixture.disabled(it) }
        check(fixture.save.state == waitingOnly && fixture.save.writes == 0) {
            "When no plot is eligible, disabled shortcuts must leave the complete save untouched"
        }
    }

    DesignParityScene(progressed, context).use { fixture ->
        fixture.start("WELCOME")
        fixture.visibleTouchTarget("welcome_settings")
        val writesBefore = fixture.save.writes
        fixture.click("welcome_settings")
        fixture.tagged("setting_music")
        fixture.tagged("setting_sound")
        fixture.tagged("setting_reduced_motion")
        fixture.click("settings_back_game")
        fixture.tagged("plot_8")
        check(fixture.save.state == progressed && fixture.save.writes == writesBefore) {
            "Welcome settings and Back to game must preserve all farm progress"
        }
    }

    DesignParityScene(progressed, context).use { fixture ->
        fixture.start("WELCOME")
        val writesBefore = fixture.save.writes
        fixture.click("welcome_new_game")
        fixture.tagged("reset_confirm")
        check(fixture.save.state == progressed && fixture.save.writes == writesBefore) {
            "Opening New game confirmation must not reset the farm"
        }
        fixture.click("reset_cancel")
        fixture.absent("reset_confirm")
        fixture.tagged("welcome_play")
        check(fixture.save.state == progressed && fixture.save.writes == writesBefore) {
            "Canceling New game must preserve the exact save and remain on Welcome"
        }
        fixture.click("welcome_new_game")
        val earliestReset = System.currentTimeMillis()
        fixture.click("reset_confirm")
        val reset = fixture.save.state
        val resetTime = checkNotNull(reset.startedAtMillis)
        check(resetTime in earliestReset..System.currentTimeMillis())
        check(reset == GameState.initial(resetTime)) {
            "Confirmed New game must restore the entire PRD sample, including inventory, plots, upgrades, order, XP and demo clock"
        }
        check(fixture.save.writes == writesBefore + 1) { "Confirmed reset must commit exactly once" }
        fixture.absent("reset_confirm")
        fixture.tagged("plot_5")
        fixture.absent("plot_6")
    }

    DesignParityScene(GameState.initial(now), context).use { fixture ->
        fixture.start("UPGRADES")
        val before = fixture.save.state
        val writesBefore = fixture.save.writes
        fixture.tagged("upgrades_screen")
        fixture.tagged("upgrade_preview_EXTRA_PLOTS")
        fixture.tagged("upgrade_preview_LARGE_WATERING_CAN")
        fixture.textContains("upgrade_shortfall_EXTRA_PLOTS", "ขาดอีก 60 เหรียญ")
        fixture.textContains("upgrade_shortfall_LARGE_WATERING_CAN", "ขาดอีก 30 เหรียญ")
        fixture.disabled("upgrade_EXTRA_PLOTS")
        fixture.disabled("upgrade_LARGE_WATERING_CAN")
        check(fixture.save.state == before && fixture.save.writes == writesBefore) {
            "Displaying insufficient upgrade funds must not spend coins or write a save"
        }
    }

    DesignParityScene(GameState.initial(now).copy(coins = 330), context).use { fixture ->
        fixture.start("UPGRADES")
        fixture.disabled("upgrade_EXTRA_PLOTS", expected = false)
        fixture.click("upgrade_EXTRA_PLOTS")
        check(fixture.save.state.coins == 150 && fixture.save.state.plots.size == 9)
        check(fixture.save.state.upgrades.expandedPlots)
        check(fixture.save.state.plots.drop(6).all { it.stage == PlotStage.UNTILLED && it.crop == null })
        fixture.disabled("upgrade_EXTRA_PLOTS")
        fixture.absent("upgrade_shortfall_EXTRA_PLOTS")
        fixture.disabled("upgrade_LARGE_WATERING_CAN", expected = false)
        fixture.click("upgrade_LARGE_WATERING_CAN")
        check(fixture.save.state.coins == 0 && fixture.save.state.upgrades.largeWateringCan)
        fixture.disabled("upgrade_LARGE_WATERING_CAN")
        fixture.absent("upgrade_shortfall_LARGE_WATERING_CAN")
        fixture.click("nav_FARM")
        fixture.tagged("plot_8")
        fixture.absent("plot_9")
    }

    DesignParityScene(GameState.initial(now), context).use { fixture ->
        fixture.start("VILLAGE")
        val before = fixture.save.state
        val writesBefore = fixture.save.writes
        val destinations = listOf(
            "village_shop" to "shop_screen",
            "village_market" to "market_screen",
            "village_orders" to "orders_screen",
            "village_upgrades" to "upgrades_screen",
        )
        destinations.forEach { (hotspot, destination) ->
            fixture.tagged("village_screen")
            fixture.click(hotspot)
            fixture.tagged(destination)
            fixture.absent("village_screen")
            check(fixture.save.state == before && fixture.save.writes == writesBefore)
            fixture.click("header_back")
            fixture.tagged("village_screen")
            destinations.forEach { fixture.tagged(it.first) }
            fixture.absent(destination)
            check(fixture.save.state == before && fixture.save.writes == writesBefore) {
                "Village hotspot and Back navigation must not reset or mutate the farm"
            }
        }
    }

    println("DESIGN PARITY UI SMOKE PASSED: 320 dp starter plot/tool layout, all four first-eligible toolbar actions, regular/all-plot watering, disabled tools, Welcome settings, reset cancel/confirm, exact upgrade shortfalls, one-time upgrades and all four village hotspot/back routes. Behavior only; not pixel parity or native-device QA.")
}
