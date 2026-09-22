@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.littlefarm.preview

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Density
import com.littlefarm.App
import com.littlefarm.account.MemoryAccountStore
import com.littlefarm.feedback.FarmAudio
import com.littlefarm.feedback.GameFeedbackController
import com.littlefarm.feedback.GamePreferences
import com.littlefarm.feedback.GamePreferencesCodec
import com.littlefarm.game.CropType
import com.littlefarm.game.GameSaveCodec
import com.littlefarm.game.GameState
import com.littlefarm.game.PlotStage
import com.littlefarm.platform.SaveStore
import kotlinx.coroutines.delay
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

private class MvpUiSave(initial: GameState) : SaveStore {
    private var json = GameSaveCodec.encode(initial)
    var writes = 0
        private set
    val state: GameState get() = checkNotNull(GameSaveCodec.decode(json))
    override fun read() = json
    override fun write(value: String) { json = value; writes++ }
}

private fun SemanticsNode.mvpDescendants(): List<SemanticsNode> = listOf(this) + children.flatMap { it.mvpDescendants() }

private class MvpFixtureAudio : FarmAudio {
    var released = false
        private set
    val musicCommands = mutableListOf<Boolean>()
    override val available: Boolean get() = !released
    override fun setMusic(enabled: Boolean) { musicCommands += enabled }
    override fun playEffect(cue: String) = Unit
    override fun setActive(active: Boolean) = Unit
    override fun release() { released = true }
}

private class MvpScene(
    initial: GameState,
    context: CoroutineContext,
    private val feedback: GameFeedbackController? = null,
) : AutoCloseable {
    val save = MvpUiSave(initial)
    // Use the narrow supported viewport, not only the roomy default preview.
    private val scene = ImageComposeScene(640, 1280, density = Density(2f), coroutineContext = context)
    private var frameNanos = 0L

    suspend fun start(page: String) {
        scene.setContent { App(save, initialPage = page, feedback = feedback) }
        settle()
    }

    suspend fun settle() {
        repeat(5) {
            frameNanos += 16_666_667L
            scene.render(frameNanos).close()
            delay(25)
        }
    }

    private fun nodes() = scene.semanticsOwners.flatMap { it.rootSemanticsNode.mvpDescendants() }

    fun tagged(tag: String): SemanticsNode = nodes().firstOrNull {
        it.config.getOrNull(SemanticsProperties.TestTag) == tag
    } ?: error("Missing MVP UI tag: $tag")

    fun exists(tag: String): Boolean = nodes().any { it.config.getOrNull(SemanticsProperties.TestTag) == tag }

    fun disabled(tag: String, expected: Boolean = true) {
        val actual = tagged(tag).config.contains(SemanticsProperties.Disabled)
        check(actual == expected) { "$tag disabled=$actual; expected $expected" }
    }

    fun checked(tag: String, expected: Boolean) {
        val actual = tagged(tag).config.getOrNull(SemanticsProperties.ToggleableState)
        check(actual == if (expected) ToggleableState.On else ToggleableState.Off) {
            "$tag toggle state=$actual; expected checked=$expected"
        }
    }

    fun textContains(tag: String, expected: String) {
        val text = tagged(tag).mvpDescendants().flatMap {
            it.config.getOrNull(SemanticsProperties.Text)?.map { annotated -> annotated.text }.orEmpty()
        }.joinToString(" ")
        check(expected in text) { "$tag did not contain '$expected': $text" }
    }

    suspend fun click(tag: String) {
        val node = tagged(tag)
        check(!node.config.contains(SemanticsProperties.Disabled)) { "MVP control unexpectedly disabled: $tag" }
        check(node.config.getOrNull(SemanticsActions.OnClick)?.action?.invoke() == true) { "MVP control not clickable: $tag" }
        settle()
        println("MVP fixture clicked $tag")
    }

    fun clickSameFrame(vararg tags: String) {
        // Capture both callbacks before the first mutation; no render or coroutine yield between taps.
        val actions = tags.map { tag ->
            val node = tagged(tag)
            check(!node.config.contains(SemanticsProperties.Disabled)) { "MVP control unexpectedly disabled: $tag" }
            tag to checkNotNull(node.config.getOrNull(SemanticsActions.OnClick)?.action) { "MVP control not clickable: $tag" }
        }
        actions.forEach { (tag, action) ->
            check(action.invoke()) { "MVP same-frame click failed: $tag" }
            println("MVP fixture clicked $tag without a new frame")
        }
    }

    override fun close() = scene.close()
}

/** Actual Compose semantics + local saves. Not native touch, audible playback, or device lifecycle QA. */
public suspend fun runMvpUiSmoke() {
    val now = System.currentTimeMillis()
    val context = coroutineContext

    MvpScene(GameState.initial(now), context).use { fixture ->
        fixture.start("FARM")
        fixture.click("plot_2")
        fixture.disabled("seed_CARROT")
        fixture.textContains("seed_CARROT", "ขายได้ 70")
        fixture.click("seed_shop")
        val beforeSelection = fixture.save.state
        val writesBeforeSelection = fixture.save.writes
        fixture.click("buy_qty_plus_CARROT")
        fixture.textContains("buy_quantity_CARROT", "2 ซอง")
        fixture.textContains("buy_total_CARROT", "70 เหรียญ")
        check(fixture.save.state == beforeSelection && fixture.save.writes == writesBeforeSelection) {
            "Choosing quantity must not spend coins or write a game save"
        }
        fixture.click("buy_CARROT")
        check(fixture.save.state.coins == 50)
        check(fixture.save.state.seedCount(CropType.CARROT) == 2)
        fixture.textContains("buy_quantity_CARROT", "1 ซอง")
        fixture.click("return_to_plot")
        fixture.click("seed_CARROT")
        val planted = fixture.save.state
        check(planted.plots[2].stage == PlotStage.PLANTED && planted.plots[2].crop == CropType.CARROT) {
            "Returning from shop must plant in the originally selected plot"
        }
        check(planted.seedCount(CropType.CARROT) == 1 && planted.coins == 50)
        check(planted.plots[0].stage == PlotStage.UNTILLED)
    }

    val stocked = GameState.initial(now).let { it.copy(produce = it.produce + (CropType.CARROT to 3)) }
    MvpScene(stocked, context).use { fixture ->
        fixture.start("MARKET")
        check(!fixture.exists("sale_receipt")) { "Opening the market must not invent a completed sale" }
        val writesBeforeSelection = fixture.save.writes
        fixture.click("sell_qty_plus_CARROT")
        fixture.textContains("sell_quantity_CARROT", "2 หัว")
        fixture.textContains("sell_total_CARROT", "140 เหรียญ")
        check(fixture.save.state == stocked && fixture.save.writes == writesBeforeSelection)
        fixture.click("sell_qty_minus_CARROT")
        fixture.textContains("sell_quantity_CARROT", "1 หัว")
        fixture.disabled("sell_qty_minus_CARROT")
        check(fixture.save.state == stocked && fixture.save.writes == writesBeforeSelection)
        fixture.click("sell_qty_plus_CARROT")
        fixture.click("sell_CARROT")
        check(fixture.save.state.coins == 260 && fixture.save.state.produceCount(CropType.CARROT) == 1)
        check(fixture.exists("sale_receipt"))
        fixture.textContains("sale_receipt_detail", "แครอต 2 หัว · รับ 140 เหรียญ")
        fixture.textContains("sale_receipt_balance", "เหรียญตอนนี้ 260")
        fixture.textContains("sell_quantity_CARROT", "1 หัว")
        fixture.disabled("sell_qty_plus_CARROT")
        fixture.disabled("sell_qty_minus_CARROT")
        fixture.disabled("sell_LETTUCE")
        fixture.click("sell_CARROT")
        check(fixture.save.state.coins == 330 && fixture.save.state.produceCount(CropType.CARROT) == 0)
        fixture.textContains("sale_receipt_detail", "แครอต 1 หัว · รับ 70 เหรียญ")
        fixture.textContains("sale_receipt_balance", "เหรียญตอนนี้ 330")
        fixture.disabled("sell_CARROT")
        check(fixture.exists("market_empty_to_farm"))
        fixture.click("market_empty_to_farm")
        check(fixture.exists("plot_2"))
    }

    val poor = GameState.initial(now).copy(coins = 15)
    MvpScene(poor, context).use { fixture ->
        fixture.start("SHOP")
        fixture.disabled("buy_LETTUCE", expected = false)
        fixture.disabled("buy_qty_minus_LETTUCE")
        val writesBeforeSelection = fixture.save.writes
        fixture.click("buy_qty_plus_LETTUCE")
        fixture.textContains("buy_total_LETTUCE", "20 เหรียญ")
        fixture.textContains("buy_shortfall_LETTUCE", "ขาดอีก 5 เหรียญ")
        fixture.disabled("buy_LETTUCE")
        check(fixture.save.state == poor && fixture.save.writes == writesBeforeSelection)
        fixture.click("buy_qty_minus_LETTUCE")
        fixture.disabled("buy_LETTUCE", expected = false)
        fixture.disabled("buy_qty_minus_LETTUCE")
        check(!fixture.exists("buy_shortfall_LETTUCE"))
        check(fixture.save.state == poor && fixture.save.writes == writesBeforeSelection)
    }

    MvpScene(GameState.initial(now), context).use { fixture ->
        fixture.start("FARM")
        val earliestReady = System.currentTimeMillis() + CropType.LETTUCE.growthDurationMillis
        fixture.click("plot_3")
        val watered = fixture.save.state
        val readyAt = checkNotNull(watered.plots[3].readyAtMillis)
        val latestReady = System.currentTimeMillis() + CropType.LETTUCE.growthDurationMillis
        check(watered.plots[3].stage == PlotStage.GROWING)
        check(readyAt in earliestReady..latestReady) { "Direct watering must start the real crop duration" }
        check(!fixture.exists("action_water")) { "A dry planted plot should water directly without opening the water dialog" }
        val writesAfterWater = fixture.save.writes
        fixture.click("plot_3")
        check(fixture.save.state.plots[3].readyAtMillis == readyAt && fixture.save.writes == writesAfterWater) {
            "Opening a growing plot must not restart its clock or write its save"
        }
        check(!fixture.exists("action_water"))
    }

    MvpScene(GameState.initial(now), context).use { fixture ->
        fixture.start("SETTINGS")
        val before = fixture.save.state
        val writesBefore = fixture.save.writes
        fixture.clickSameFrame("settings_demo_time", "settings_save")
        val after = fixture.save.state
        check(after.demoOffsetMillis == before.demoOffsetMillis + 5 * 60_000L) {
            "Manual save must not overwrite a same-frame demo action with the preceding UI snapshot"
        }
        check(after.coins == before.coins && after.seeds == before.seeds)
        check(after.copy(demoOffsetMillis = before.demoOffsetMillis) == before)
        check(fixture.save.writes == writesBefore + 2) { "Both demo action and explicit save must commit" }
        fixture.settle()
        check(fixture.save.state == after)
    }

    val preferencesStore = MemoryAccountStore()
    val fakeAudio = MvpFixtureAudio()
    val feedback = GameFeedbackController(preferencesStore, fakeAudio)
    val preferences = GamePreferences(musicEnabled = false, soundEnabled = false, reducedMotion = true)
    try {
        MvpScene(GameState.initial(now), context, feedback).use { fixture ->
            fixture.start("SETTINGS")
            fixture.checked("setting_music", expected = true)
            fixture.checked("setting_sound", expected = true)
            fixture.checked("setting_reduced_motion", expected = false)
            val untouchedGame = fixture.save.state
            val initialWrites = fixture.save.writes
            fixture.click("setting_music")
            fixture.click("setting_sound")
            fixture.click("setting_reduced_motion")
            check(feedback.snapshot.value.preferences == preferences)
            check(fakeAudio.musicCommands.first() && !fakeAudio.musicCommands.last())
            val stored = checkNotNull(preferencesStore.read(GameFeedbackController.PREFERENCE_KEY))
            check(GamePreferencesCodec.decode(stored) == preferences)
            check(fixture.save.state == untouchedGame && fixture.save.writes == initialWrites) {
                "Device preferences must not rewrite the account's farm save"
            }
            fixture.click("nav_FARM")
            fixture.click("header_settings")
            fixture.checked("setting_music", expected = false)
            fixture.checked("setting_sound", expected = false)
            fixture.checked("setting_reduced_motion", expected = true)
        }
    } finally { feedback.close() }
    check(fakeAudio.released)
    val restoredAudio = MvpFixtureAudio()
    val restoredFeedback = GameFeedbackController(preferencesStore, restoredAudio)
    try {
        check(restoredFeedback.snapshot.value.preferences == preferences)
        MvpScene(GameState.initial(now), context, restoredFeedback).use { fixture ->
            fixture.start("SETTINGS")
            fixture.checked("setting_music", expected = false)
            fixture.checked("setting_sound", expected = false)
            fixture.checked("setting_reduced_motion", expected = true)
        }
    } finally { restoredFeedback.close() }

    println("MVP UI SMOKE PASSED: 320dp quantity buy/sell totals, no writes on +/- selection, exact insufficient-coin feedback, disabled stock bounds, empty basket, seed resale price, original-plot return, direct watering without timer reset, same-frame demo/manual-save freshness, settings toggles and independent persisted preferences. Shared Compose semantics; audio uses a fake boundary, not audible-device QA.")
}
