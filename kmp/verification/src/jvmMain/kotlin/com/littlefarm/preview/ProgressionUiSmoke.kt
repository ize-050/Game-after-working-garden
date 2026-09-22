@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.littlefarm.preview

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import com.littlefarm.App
import com.littlefarm.game.*
import com.littlefarm.platform.SaveStore
import kotlinx.coroutines.delay
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

private class ProgressionSave(initial: GameState) : SaveStore {
    private var json = GameSaveCodec.encode(initial)
    val state: GameState get() = checkNotNull(GameSaveCodec.decode(json))
    override fun read() = json
    override fun write(value: String) { json = value }
}

private fun SemanticsNode.progressionNodes(): List<SemanticsNode> = listOf(this) + children.flatMap { it.progressionNodes() }

private class ProgressionScene(initial: GameState, context: CoroutineContext) : AutoCloseable {
    val save = ProgressionSave(initial)
    private val scene = ImageComposeScene(640, 1280, density = Density(2f), coroutineContext = context)
    private var nanos = 0L
    suspend fun start(page: String) {
        scene.setContent { App(save, initialPage = page) }
        settle()
    }
    private suspend fun settle() {
        repeat(5) { nanos += 16_666_667L; scene.render(nanos).close(); delay(25) }
    }
    fun tagged(tag: String): SemanticsNode = scene.semanticsOwners.flatMap { it.rootSemanticsNode.progressionNodes() }
        .firstOrNull { it.config.getOrNull(SemanticsProperties.TestTag) == tag }
        ?: error("Missing progression UI: $tag")
    fun disabled(tag: String, expected: Boolean = true) {
        check(tagged(tag).config.contains(SemanticsProperties.Disabled) == expected) { "Unexpected enabled state: $tag" }
    }
    suspend fun click(tag: String) {
        disabled(tag, false)
        check(tagged(tag).config.getOrNull(SemanticsActions.OnClick)?.action?.invoke() == true) { "Cannot click $tag" }
        settle()
        println("Progression fixture clicked $tag")
    }
    suspend fun doubleTapWithoutRecompose(tag: String) {
        disabled(tag, false)
        val action = checkNotNull(tagged(tag).config.getOrNull(SemanticsActions.OnClick)?.action)
        check(action.invoke())
        check(action.invoke())
        settle()
    }
    suspend fun enterName(value: String) {
        check(tagged("cat_name").config.getOrNull(SemanticsActions.SetText)?.action?.invoke(AnnotatedString(value)) == true)
        settle()
    }
    override fun close() = scene.close()
}

/** Shared production UI and serialized saves; not a substitute for native OS notification delivery QA. */
suspend fun runProgressionUiSmoke() {
    val now = System.currentTimeMillis()
    val context = coroutineContext
    val initial = GameState.initial(now)

    ProgressionScene(initial.copy(coins = 1_000), context).use { fixture ->
        fixture.start("FARM")
        fixture.click("farm_decorations")
        fixture.tagged("decorations_screen")
        fixture.click("decor_buy_WOOD_FENCE")
        check(Decoration.WOOD_FENCE in fixture.save.state.ownedDecor)
        check(fixture.save.state.coins == 1_000 - Decoration.WOOD_FENCE.price)
        fixture.click("decor_equip_WOOD_FENCE")
        check(fixture.save.state.equippedDecor[DecorationSlot.FENCE] == Decoration.WOOD_FENCE)
        fixture.click("nav_FARM")
        fixture.tagged("decor_scene")
        fixture.click("farm_decorations")
        fixture.click("decor_remove_FENCE")
        check(DecorationSlot.FENCE !in fixture.save.state.equippedDecor)
        check(Decoration.WOOD_FENCE in fixture.save.state.ownedDecor)
        check(fixture.save.state.coins == 1_000 - Decoration.WOOD_FENCE.price)
    }

    ProgressionScene(initial, context).use { fixture ->
        fixture.start("SHOP")
        fixture.disabled("buy_TOMATO")
        fixture.disabled("buy_STRAWBERRY")
        fixture.disabled("buy_FLOWER")
        check(fixture.save.state == initial)
    }
    ProgressionScene(initial.copy(xp = 99, harvestCounts = initial.harvestCounts + (CropType.LETTUCE to 49)), context).use { fixture ->
        fixture.start("FARM")
        fixture.click("plot_5")
        fixture.click("action_harvest")
        check(fixture.save.state.level == 2)
        check(fixture.save.state.harvestCounts[CropType.LETTUCE] == 50)
        check(Decoration.STAR_LAMP in fixture.save.state.ownedDecor)
        // A separate reopening verifies that both the level and collection survived serialization.
        val afterHarvest = fixture.save.state
        ProgressionScene(afterHarvest, context).use { reopened ->
            reopened.start("COLLECTION")
            reopened.tagged("collection_badge_LETTUCE_50")
            check(reopened.save.state == afterHarvest)
            reopened.click("nav_VILLAGE")
            reopened.click("village_shop")
            reopened.disabled("buy_TOMATO", false)
            reopened.click("buy_TOMATO")
            check(reopened.save.state.seedCount(CropType.TOMATO) == 1)
        }
    }

    val orderStock = initial.copy(produce = initial.produce + (CropType.LETTUCE to 2) + (CropType.CARROT to 2))
    ProgressionScene(orderStock, context).use { fixture ->
        fixture.start("ORDERS")
        fixture.doubleTapWithoutRecompose("order_deliver")
        check(fixture.save.state.completedOrders == 1 && fixture.save.state.currentOrder.crop == CropType.CARROT)
        fixture.click("order_deliver")
        check(fixture.save.state.completedOrders == 2 && fixture.save.state.currentOrder.crop == CropType.RADISH)
        check(fixture.save.state.coins == initial.coins + 45 + 170)
        fixture.disabled("order_deliver")
    }

    ProgressionScene(initial, context).use { fixture ->
        fixture.start("FARM")
        fixture.click("farm_cat")
        fixture.tagged("cat_screen")
        fixture.enterName("โมจิ")
        fixture.click("cat_rename")
        check(fixture.save.state.cat.name == "โมจิ")
        fixture.click("cat_pet")
        check(fixture.save.state.cat.bond == 1)
        fixture.disabled("cat_pet")
        fixture.tagged("cat_cooldown")
        val after = fixture.save.state
        ProgressionScene(after, context).use { reopened ->
            reopened.start("CAT")
            check(reopened.save.state.cat.name == "โมจิ" && reopened.save.state.cat.bond == 1)
            reopened.disabled("cat_pet")
        }
    }
    ProgressionScene(initial, context).use { fixture ->
        fixture.start("BAG")
        fixture.click("bag_collection")
        fixture.tagged("collection_screen")
        fixture.click("header_settings")
        fixture.tagged("setting_harvest_reminders")
    }
    println("PROGRESSION UI SMOKE PASSED: decoration purchase/equip/remove, locked crops, harvest XP/level/unlock, collection50 reward, renewed orders, cat rename/pet/cooldown/reopen, collection and reminder routes at 320dp.")
}
