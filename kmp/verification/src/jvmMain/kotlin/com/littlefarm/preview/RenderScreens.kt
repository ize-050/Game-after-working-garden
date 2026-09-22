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
import com.littlefarm.game.GameEngine
import com.littlefarm.game.GameResult
import com.littlefarm.platform.SaveStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

/** A development-only renderer. It never reads or writes a player's device save. */
private class PreviewSave(initial: GameState) : SaveStore {
    private var value = GameSaveCodec.encode(initial)
    override fun read() = value
    override fun write(value: String) { this.value = value }
}

private data class Preview(val name: String, val page: String, val plot: Int? = null, val harvest: CropType? = null)

private fun SemanticsNode.descendants(): List<SemanticsNode> = listOf(this) + children.flatMap { it.descendants() }

fun main(args: Array<String>) = runBlocking {
    val output = File(args.firstOrNull() ?: "build/screenshots").apply { mkdirs() }
    val previews = listOf(
        Preview("01-welcome", "WELCOME"), Preview("02-farm", "FARM"),
        Preview("03-seeds", "FARM", plot = 2), Preview("04-growth", "FARM", plot = 4),
        Preview("05-harvest", "FARM", harvest = CropType.LETTUCE),
        Preview("06-bag", "BAG"), Preview("07-shop", "SHOP"), Preview("08-market", "MARKET"),
        Preview("09-orders", "ORDERS"), Preview("10-upgrades", "UPGRADES"),
        Preview("11-village", "VILLAGE"), Preview("12-settings", "SETTINGS"),
        Preview("13-account", "ACCOUNT")
    )
    for ((width, height) in listOf(393 to 852, 320 to 640)) {
        for (preview in previews) {
            val now = System.currentTimeMillis()
            val initial = GameState.initial(now)
            // Show the real post-harvest state, not a success message with zero inventory.
            val fixture = if (preview.harvest != null || preview.page == "MARKET") {
                (GameEngine.harvest(initial, 5, now) as GameResult.Success).state
            } else initial
            val store = PreviewSave(fixture)
            val scene = ImageComposeScene(width * 2, height * 2, density = Density(2f), coroutineContext = coroutineContext)
            try {
                scene.setContent { App(store, preview.page, preview.plot, preview.harvest) }
                // Give bundled resource loading and recomposition time to settle.
                repeat(48) { frame ->
                    scene.render(frame * 16_666_667L).close()
                    delay(25)
                }
                val name = "${preview.name}-${width}x${height}"
                scene.render(800_000_000L).use { image ->
                    image.encodeToData(EncodedImageFormat.PNG)!!.use { data ->
                        File(output, "$name.png").writeBytes(data.bytes)
                    }
                }
                val semantics = scene.semanticsOwners.flatMap { it.rootSemanticsNode.descendants() }
                File(output, "$name.txt").writeText(semantics.joinToString("\n") { node ->
                    val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty()
                    val description = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString().orEmpty()
                    val tag = node.config.getOrNull(SemanticsProperties.TestTag).orEmpty()
                    val clickable = node.config.getOrNull(SemanticsActions.OnClick) != null
                    "tag=$tag clickable=$clickable bounds=${node.boundsInRoot} text=$text description=$description"
                })
                println("Rendered $name")
                if (preview.page in listOf("SHOP", "MARKET", "ORDERS", "UPGRADES", "SETTINGS")) {
                    val scroll = semantics.firstOrNull { it.config.getOrNull(SemanticsActions.ScrollBy) != null }
                    check(scroll?.config?.getOrNull(SemanticsActions.ScrollBy)?.action?.invoke(0f, 10_000f) == true) {
                        "Long page ${preview.page} must be scrollable to its final controls"
                    }
                    repeat(8) { frame -> scene.render(900_000_000L + frame * 16_666_667L).close(); delay(25) }
                    val endName = "${preview.name}-end-${width}x${height}"
                    scene.render(1_100_000_000L).use { image ->
                        image.encodeToData(EncodedImageFormat.PNG)!!.use { data -> File(output, "$endName.png").writeBytes(data.bytes) }
                    }
                    println("Rendered $endName after real Compose scroll")
                }
            } finally {
                scene.close()
            }
        }
    }
    println("Shared Compose screenshots: ${output.absolutePath}")
}
