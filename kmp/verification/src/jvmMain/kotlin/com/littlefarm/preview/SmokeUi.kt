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
import com.littlefarm.game.PlotStage
import com.littlefarm.platform.SaveStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

private class SmokeSave : SaveStore {
    private var json = GameSaveCodec.encode(GameState.initial(System.currentTimeMillis()))
    val state: GameState get() = checkNotNull(GameSaveCodec.decode(json))
    override fun read() = json
    override fun write(value: String) { json = value }
}

private fun SemanticsNode.allNodes(): List<SemanticsNode> = listOf(this) + children.flatMap { it.allNodes() }

/** Semantics-level integration checks, not OS touch input or iPhone runtime QA. */
fun main() = runBlocking {
    val store = SmokeSave()
    val scene = ImageComposeScene(786, 1704, density = Density(2f), coroutineContext = coroutineContext)
    var nanos = 0L
    suspend fun settle() {
        repeat(5) { nanos += 16_666_667L; scene.render(nanos).close(); delay(25) }
    }
    fun nodes() = scene.semanticsOwners.flatMap { it.rootSemanticsNode.allNodes() }
    fun assertTag(tag: String) = check(nodes().any { it.config.getOrNull(SemanticsProperties.TestTag) == tag }) { "Missing UI: $tag" }
    fun assertDisabled(tag: String) {
        val node = nodes().firstOrNull { it.config.getOrNull(SemanticsProperties.TestTag) == tag }
            ?: error("Missing unavailable provider: $tag")
        check(node.config.contains(SemanticsProperties.Disabled)) { "$tag must not allow fake sign-in without Firebase" }
    }
    suspend fun click(tag: String? = null, text: String? = null) {
        val matches = nodes().filter { node ->
            node.config.getOrNull(SemanticsActions.OnClick) != null &&
                if (tag != null) node.config.getOrNull(SemanticsProperties.TestTag) == tag
                else node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == text } == true
        }
        val node = matches.minByOrNull { it.boundsInRoot.width * it.boundsInRoot.height }
            ?: error("Missing clickable control: ${tag ?: text}")
        check(!node.config.contains(SemanticsProperties.Disabled)) { "Disabled: ${tag ?: text}" }
        check(node.config.getOrNull(SemanticsActions.OnClick)?.action?.invoke() == true)
        settle()
        println("Clicked ${tag ?: text}")
    }
    try {
        scene.setContent { App(store) }
        settle()
        click(tag = "welcome_account")
        assertTag("account_screen")
        assertTag("account_unconfigured")
        assertDisabled("account_google")
        assertDisabled("account_apple")
        check(store.state.coins == 120)
        click(tag = "account_continue")
        click(tag = "header_settings")
        click(tag = "settings_account")
        assertTag("account_unconfigured")
        click(tag = "account_continue")
        click(tag = "header_settings")
        click(text = "กลับหน้าชื่อเกม")
        click(tag = "welcome_play")
        click(tag = "plot_0")
        click(tag = "action_till")
        check(store.state.plots[0].stage == PlotStage.TILLED)
        click(tag = "seed_LETTUCE")
        check(store.state.seedCount(CropType.LETTUCE) == 2)
        check(store.state.plots[0].stage == PlotStage.PLANTED)
        click(tag = "action_water")
        check(store.state.plots[0].stage == PlotStage.GROWING)
        check(store.state.plots[0].readyAtMillis != null)
        click(text = "ทดสอบเวลา +5 นาที")
        click(tag = "action_harvest")
        check(store.state.produceCount(CropType.LETTUCE) == 1)
        check(store.state.coins == 120)
        click(text = "นำผักไปขาย")
        click(tag = "sell_LETTUCE")
        check(store.state.coins == 138)
        check(store.state.produceCount(CropType.LETTUCE) == 0)
        click(tag = "nav_VILLAGE")
        click(text = "ร้านเมล็ดป้าพร")
        click(tag = "buy_LETTUCE")
        check(store.state.coins == 128)
        check(store.state.seedCount(CropType.LETTUCE) == 3)
        click(tag = "nav_BAG")
        click(tag = "nav_ORDERS")
        click(tag = "nav_FARM")
        click(tag = "plot_5")
        click(tag = "action_harvest")
        click(text = "กลับไปปลูกต่อ")
        click(tag = "plot_3")
        check(store.state.plots[3].stage == PlotStage.GROWING)
        click(tag = "farm_demo_time")
        click(tag = "plot_3")
        click(tag = "action_harvest")
        check(store.state.produceCount(CropType.LETTUCE) == 2)
        click(text = "กลับไปปลูกต่อ")
        click(tag = "nav_ORDERS")
        click(text = "ส่งผักให้คุณยาย")
        check(store.state.orderClaimed)
        check(store.state.coins == 173 && store.state.xp == 20 + 3 * CropType.LETTUCE.harvestXp)
        check(store.state.completedOrders == 1)
        check(store.state.produceCount(CropType.LETTUCE) == 0)
        click(tag = "account_status")
        assertTag("account_screen")
        assertDisabled("account_google")
        assertDisabled("account_apple")
        check(store.state.coins == 173 && store.state.xp == 20 + 3 * CropType.LETTUCE.harvestXp)
        println("UI SMOKE PASSED: navigation, till, plant, water, real game clock, harvest, sale, purchase, order, serialized saves, Guest account routes and unavailable real sign-in")
    } finally {
        scene.close()
    }
    accountUiSmoke(coroutineContext)
    runMvpUiSmoke()
    runDesignParitySmoke()
    runProgressionUiSmoke()
}
