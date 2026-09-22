package com.littlefarm.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import com.littlefarm.game.CropType
import com.littlefarm.game.Plot
import com.littlefarm.game.PlotStage
import kotlin.math.PI
import kotlin.math.sin

internal enum class FarmSymbol {
    FARM, BAG, VILLAGE, ORDERS, SEED, MARKET, HAMMER, WATER, COIN, STAR,
    SETTINGS, BACK, CLOSE, CHECK, PLUS, CLOCK, SAVE, LEAF, ARROW,
}

private val LeafDark = Color(0xFF3D6B45)
private val LeafMid = Color(0xFF73A44D)
private val LeafLight = Color(0xFFA7C965)
private val Cream = Color(0xFFFFF4D8)
private val Gold = Color(0xFFF4C867)
private val Earth = Color(0xFF986341)

/** Small original illustrations share the same rounded, toy-like visual language. */
@Composable
internal fun FarmIcon(
    symbol: FarmSymbol,
    modifier: Modifier = Modifier,
    tint: Color = LeafDark,
) {
    Canvas(modifier.size(32.dp)) {
        withTransform({ scale(size.width / 100f, size.height / 100f, Offset.Zero) }) {
            fun line(x1: Float, y1: Float, x2: Float, y2: Float, width: Float = 7f) =
                drawLine(tint, Offset(x1, y1), Offset(x2, y2), width, StrokeCap.Round)
            fun round(x: Float, y: Float, w: Float, h: Float, color: Color = tint, r: Float = 8f) =
                drawRoundRect(color, Offset(x, y), Size(w, h), CornerRadius(r))
            when (symbol) {
                FarmSymbol.FARM -> {
                    drawOval(tint.copy(alpha = .15f), Offset(13f, 79f), Size(75f, 11f))
                    round(24f, 43f, 54f, 41f, Color(0xFFE4AD6C), 5f)
                    drawPath(polygon(13f, 46f, 49f, 16f, 86f, 46f), tint)
                    round(44f, 56f, 17f, 28f, Cream, 4f)
                    round(29f, 53f, 9f, 10f, Cream, 3f)
                    drawLine(tint.copy(alpha = .4f), Offset(25f, 81f), Offset(77f, 81f), 5f, StrokeCap.Round)
                }
                FarmSymbol.BAG -> {
                    drawOval(tint.copy(alpha = .13f), Offset(19f, 81f), Size(65f, 9f))
                    drawArc(tint, 180f, 180f, false, Offset(34f, 16f), Size(32f, 32f), style = Stroke(8f, cap = StrokeCap.Round))
                    round(21f, 35f, 58f, 50f, tint, 12f)
                    round(27f, 39f, 46f, 8f, Color.White.copy(alpha = .2f), 4f)
                    round(37f, 53f, 27f, 20f, Cream, 5f)
                    drawLeaf(50f, 65f, 15f, 22f, -22f, LeafMid)
                }
                FarmSymbol.VILLAGE -> {
                    round(12f, 52f, 31f, 32f, Color(0xFFE5B078), 3f)
                    round(54f, 39f, 34f, 45f, Color(0xFFF2D5A6), 3f)
                    drawPath(polygon(6f, 53f, 27f, 31f, 49f, 53f), tint)
                    drawPath(polygon(47f, 41f, 70f, 15f, 94f, 41f), tint)
                    round(23f, 66f, 10f, 18f, Cream, 3f)
                    round(66f, 62f, 11f, 22f, Earth, 3f)
                    round(61f, 44f, 8f, 9f, Cream, 2f)
                    round(74f, 44f, 8f, 9f, Cream, 2f)
                }
                FarmSymbol.ORDERS -> {
                    round(22f, 21f, 57f, 67f, tint, 9f)
                    round(28f, 28f, 45f, 52f, Cream, 5f)
                    round(36f, 13f, 29f, 18f, Gold, 6f)
                    line(36f, 44f, 43f, 51f, 5f); line(43f, 51f, 54f, 39f, 5f)
                    line(36f, 64f, 61f, 64f, 5f)
                }
                FarmSymbol.SEED -> {
                    round(23f, 18f, 55f, 70f, Color(0xFFF0D7A2), 8f)
                    round(25f, 19f, 51f, 13f, tint, 5f)
                    drawOval(Cream, Offset(31f, 38f), Size(39f, 39f))
                    drawLine(tint, Offset(50f, 67f), Offset(50f, 48f), 4f, StrokeCap.Round)
                    drawLeaf(49f, 55f, 14f, 22f, -50f, LeafMid)
                    drawLeaf(51f, 53f, 14f, 24f, 44f, tint)
                    line(32f, 82f, 69f, 82f, 3f)
                }
                FarmSymbol.MARKET -> {
                    round(18f, 46f, 66f, 37f, Color(0xFFE1AF77), 4f)
                    round(29f, 56f, 20f, 27f, Cream, 3f)
                    round(58f, 55f, 16f, 15f, tint, 3f)
                    drawPath(polygon(12f, 42f, 22f, 20f, 78f, 20f, 90f, 42f), Gold)
                    for (i in 0..3) {
                        round(12f + i * 20f, 38f, 20f, 14f, if (i % 2 == 0) tint else Cream, 6f)
                    }
                }
                FarmSymbol.HAMMER -> {
                    rotate(38f, Offset(50f, 50f)) {
                        round(43f, 33f, 15f, 54f, Color(0xFFD9A360), 6f)
                        round(24f, 19f, 55f, 26f, tint, 7f)
                        round(28f, 23f, 45f, 5f, Color.White.copy(alpha = .2f), 3f)
                    }
                }
                FarmSymbol.WATER -> {
                    drawArc(tint, 240f, 290f, false, Offset(57f, 28f), Size(30f, 35f), style = Stroke(8f, cap = StrokeCap.Round))
                    drawPath(polygon(27f, 47f, 10f, 37f, 8f, 46f, 29f, 70f), tint)
                    round(25f, 39f, 43f, 38f, tint, 11f)
                    round(31f, 44f, 6f, 24f, Color.White.copy(alpha = .25f), 3f)
                    drawOval(Gold, Offset(31f, 30f), Size(30f, 12f))
                    for (i in 0..2) drawCircle(Color(0xFF83B7C1), 3f, Offset(8f + i * 8f, 62f + i * 7f))
                }
                FarmSymbol.COIN -> {
                    drawCircle(Color(0xFFC28A36), 36f, Offset(51f, 54f))
                    drawCircle(Gold, 35f, Offset(49f, 48f))
                    drawCircle(Color(0xFFE2AE4B), 25f, Offset(49f, 48f), style = Stroke(4f))
                    drawLine(Cream, Offset(34f, 26f), Offset(25f, 35f), 5f, StrokeCap.Round)
                    drawPath(polygon(50f, 32f, 59f, 49f, 50f, 65f, 41f, 49f), Cream)
                }
                FarmSymbol.STAR -> {
                    drawPath(starPath(50f, 53f), Color(0xFFD39C3A))
                    translate(top = -4f) { drawPath(starPath(50f, 53f), Gold) }
                    drawLine(Cream.copy(alpha = .8f), Offset(45f, 30f), Offset(40f, 44f), 4f, StrokeCap.Round)
                }
                FarmSymbol.SETTINGS -> {
                    repeat(8) { rotate(it * 45f, Offset(50f, 50f)) { round(43f, 10f, 14f, 24f, tint, 4f) } }
                    drawCircle(tint, 30f, Offset(50f, 50f))
                    drawCircle(Cream, 15f, Offset(50f, 50f))
                    drawCircle(tint.copy(alpha = .15f), 7f, Offset(50f, 50f))
                }
                FarmSymbol.BACK -> { line(61f, 24f, 35f, 50f); line(35f, 50f, 61f, 76f) }
                FarmSymbol.ARROW -> { line(37f, 24f, 64f, 50f); line(64f, 50f, 37f, 76f) }
                FarmSymbol.CLOSE -> { line(29f, 29f, 71f, 71f); line(71f, 29f, 29f, 71f) }
                FarmSymbol.CHECK -> { line(23f, 51f, 43f, 70f, 9f); line(43f, 70f, 78f, 29f, 9f) }
                FarmSymbol.PLUS -> { line(50f, 25f, 50f, 75f); line(25f, 50f, 75f, 50f) }
                FarmSymbol.CLOCK -> {
                    drawCircle(tint, 35f, Offset(50f, 52f))
                    drawCircle(Cream, 28f, Offset(50f, 50f))
                    line(50f, 31f, 50f, 51f, 5f); line(50f, 51f, 64f, 59f, 5f)
                    drawCircle(Gold, 4f, Offset(50f, 50f))
                }
                FarmSymbol.SAVE -> {
                    round(19f, 18f, 63f, 66f, tint, 9f)
                    round(32f, 18f, 36f, 25f, Cream, 3f)
                    round(31f, 56f, 39f, 28f, Cream, 4f)
                    round(56f, 23f, 6f, 14f, Gold, 2f)
                    line(39f, 64f, 61f, 64f, 3f); line(39f, 73f, 56f, 73f, 3f)
                }
                FarmSymbol.LEAF -> {
                    drawLeaf(50f, 78f, 40f, 68f, 28f, tint)
                    drawLine(LeafLight, Offset(40f, 74f), Offset(61f, 35f), 4f, StrokeCap.Round)
                }
            }
        }
    }
}

@Composable
internal fun CropArt(crop: CropType, modifier: Modifier = Modifier, sprout: Boolean = false) {
    Canvas(modifier.size(72.dp)) {
        withTransform({ scale(size.width / 100f, size.height / 100f, Offset.Zero) }) {
            drawCrop(crop, sprout)
        }
    }
}

/** The parent owns labels and click handling; this canvas is decoration only. */
@Composable
internal fun SoilBed(plot: Plot, now: Long, modifier: Modifier = Modifier, plotIndex: Int? = null) {
    val event = LocalFarmMotionEvent.current
    val active = farmEventTargetsPlot(event, plotIndex, plot.stage)
    val progress = rememberFarmMotionProgress(event, active)
    Canvas(modifier.fillMaxSize()) {
        withTransform({ scale(size.width / 100f, size.height / 100f, Offset.Zero) }) {
            drawOval(Color(0xFF4E6C3F).copy(alpha = .13f), Offset(6f, 80f), Size(91f, 17f))
            val lowerBed = Path().apply {
                moveTo(14f, 52f); cubicTo(14f, 47f, 20f, 44f, 26f, 44f)
                lineTo(80f, 44f); cubicTo(87f, 44f, 91f, 49f, 92f, 54f)
                lineTo(96f, 79f); cubicTo(97f, 88f, 89f, 94f, 81f, 94f)
                lineTo(22f, 94f); cubicTo(12f, 94f, 5f, 88f, 8f, 79f); close()
            }
            drawPath(lowerBed, Color(0xFF765236))
            val surface = Path().apply {
                moveTo(14f, 48f); cubicTo(15f, 41f, 21f, 39f, 28f, 39f)
                lineTo(77f, 39f); cubicTo(86f, 39f, 89f, 42f, 91f, 49f)
                lineTo(96f, 73f); cubicTo(97f, 81f, 89f, 86f, 81f, 86f)
                lineTo(21f, 86f); cubicTo(12f, 86f, 5f, 81f, 8f, 73f); close()
            }
            val untilled = plot.stage == PlotStage.UNTILLED
            drawPath(surface, if (untilled) Color(0xFFB9915B) else Color(0xFFAB764D))
            drawPath(surface, Color(0xFFD5A16C).copy(alpha = .75f), style = Stroke(1.8f))
            if (untilled) {
                drawOval(Color(0xFF8F9A54), Offset(13f, 47f), Size(30f, 17f))
                drawOval(Color(0xFF98A35E), Offset(47f, 66f), Size(41f, 13f))
                listOf(Offset(26f, 57f), Offset(67f, 73f), Offset(74f, 49f)).forEach { p ->
                    drawLeaf(p.x, p.y, 5f, 15f, -25f, LeafDark)
                    drawLeaf(p.x + 1f, p.y, 5f, 13f, 30f, LeafMid)
                }
                drawOval(Color(0xFFD3C6A1), Offset(45f, 58f), Size(8f, 5f))
                drawOval(Color(0xFFD7C79E), Offset(19f, 73f), Size(6f, 4f))
            } else {
                repeat(3) { index ->
                    val y = 50f + index * 12f
                    drawLine(Color(0xFF805536), Offset(22f, y + 3f), Offset(81f, y + 3f), 5.5f, StrokeCap.Round)
                    drawLine(Color(0xFFC39262), Offset(22f, y), Offset(81f, y), 3.5f, StrokeCap.Round)
                }
                listOf(Offset(18f, 64f), Offset(84f, 72f), Offset(55f, 80f), Offset(51f, 43f)).forEach {
                    drawCircle(Color(0xFFDBAF7B), 1.2f, it)
                }
                plot.crop?.let { crop ->
                    val ready = plot.isReady(now)
                    val growing = plot.stage == PlotStage.GROWING
                    val baseScale = if (ready) .72f else if (growing) .55f else .45f
                    val bounce = if (active && event?.kind == "plant" && progress < .54f)
                        sin(progress / .54f * PI).toFloat() * .12f else 0f
                    val plantScale = baseScale * (1f + bounce)
                    // One seed produces one vegetable, so each bed shows one plant.
                    translate(51f - 50f * plantScale, 77f - 88f * plantScale) {
                        scale(plantScale, plantScale, Offset.Zero) { drawCrop(crop, sprout = !ready) }
                    }
                    if (ready) {
                        drawSparkle(17f, 29f, 4f)
                        drawSparkle(86f, 34f, 3f)
                    } else if (growing) {
                        drawCircle(Color(0xFFABCAD2), 1.9f, Offset(82f, 64f))
                        drawCircle(Color(0xFFABCAD2), 1.3f, Offset(21f, 72f))
                    }
                }
            }
            if (active) drawPlotAction(event?.kind, progress)
        }
    }
}

internal fun DrawScope.drawCrop(crop: CropType, sprout: Boolean) {
    drawOval(Color(0xFF385139).copy(alpha = .12f), Offset(19f, 80f), Size(64f, 13f))
    if (sprout) {
        drawLine(LeafDark, Offset(50f, 82f), Offset(50f, 48f), 6f, StrokeCap.Round)
        drawLeaf(49f, 65f, 23f, 39f, -55f, LeafMid)
        drawLeaf(52f, 62f, 25f, 40f, 42f, LeafLight)
        drawLeaf(50f, 50f, 18f, 29f, -5f, LeafDark)
        return
    }
    when (crop) {
        CropType.LETTUCE -> {
            drawOval(LeafDark, Offset(14f, 41f), Size(73f, 46f))
            drawLeaf(43f, 80f, 35f, 63f, -57f, LeafMid)
            drawLeaf(57f, 83f, 39f, 67f, 49f, LeafMid)
            drawLeaf(48f, 79f, 37f, 68f, -20f, LeafLight)
            drawLeaf(55f, 79f, 35f, 61f, 24f, Color(0xFF91BC58))
            drawOval(Color(0xFFB5D478), Offset(31f, 36f), Size(41f, 42f))
            drawLeaf(47f, 77f, 23f, 37f, -32f, Color(0xFFCFDF94))
            drawLeaf(57f, 76f, 25f, 38f, 35f, LeafLight)
            drawLeaf(51f, 71f, 19f, 31f, -7f, Color(0xFFD4E5A3))
        }
        CropType.RADISH -> {
            drawLeaf(50f, 47f, 20f, 38f, -47f, LeafDark)
            drawLeaf(50f, 44f, 22f, 42f, 1f, LeafMid)
            drawLeaf(54f, 47f, 21f, 37f, 48f, LeafLight)
            val body = Path().apply {
                moveTo(30f, 50f); cubicTo(31f, 36f, 70f, 34f, 73f, 52f)
                cubicTo(76f, 71f, 55f, 78f, 51f, 89f)
                cubicTo(43f, 79f, 25f, 70f, 30f, 50f); close()
            }
            drawPath(body, Color(0xFFE7DDD3))
            drawOval(Color(0xFFDB7C80), Offset(30f, 39f), Size(43f, 29f))
            drawOval(Color(0xFFEF9CA0), Offset(35f, 41f), Size(24f, 15f))
            drawPath(Path().apply { moveTo(51f, 84f); quadraticTo(47f, 91f, 54f, 93f) }, Color(0xFFC9B9A5), style = Stroke(2f, cap = StrokeCap.Round))
        }
        CropType.CARROT -> {
            drawLeaf(52f, 42f, 15f, 35f, -43f, LeafDark)
            drawLeaf(52f, 40f, 16f, 36f, 6f, LeafMid)
            drawLeaf(55f, 41f, 15f, 36f, 41f, LeafLight)
            val carrot = Path().apply {
                moveTo(30f, 44f); cubicTo(29f, 32f, 65f, 29f, 71f, 42f)
                cubicTo(72f, 53f, 47f, 80f, 35f, 89f)
                cubicTo(32f, 70f, 28f, 53f, 30f, 44f); close()
            }
            drawPath(carrot, Color(0xFFD97932))
            val highlight = Path().apply {
                moveTo(34f, 44f); cubicTo(34f, 37f, 56f, 34f, 61f, 43f)
                cubicTo(59f, 52f, 42f, 77f, 36f, 82f)
                cubicTo(34f, 64f, 32f, 53f, 34f, 44f); close()
            }
            drawPath(highlight, Color(0xFFF1A044))
            drawLine(Color(0xFFCF7131), Offset(34f, 53f), Offset(44f, 56f), 3f, StrokeCap.Round)
            drawLine(Color(0xFFCF7131), Offset(48f, 62f), Offset(55f, 63f), 3f, StrokeCap.Round)
            drawLine(Color(0xFFFFC96B), Offset(38f, 41f), Offset(50f, 39f), 3f, StrokeCap.Round)
        }
        CropType.PUMPKIN -> {
            drawLeaf(52f, 36f, 22f, 31f, 54f, LeafMid)
            drawPath(Path().apply { moveTo(51f, 41f); quadraticTo(43f, 29f, 54f, 20f) }, LeafDark, style = Stroke(8f, cap = StrokeCap.Round))
            drawOval(Color(0xFFCE813A), Offset(12f, 36f), Size(76f, 49f))
            drawOval(Color(0xFFECA648), Offset(16f, 36f), Size(40f, 45f))
            drawOval(Color(0xFFF4B34E), Offset(46f, 36f), Size(35f, 45f))
            drawOval(Color(0xFFE69939), Offset(31f, 33f), Size(40f, 52f))
            drawOval(Color(0xFFF7B94F), Offset(36f, 36f), Size(26f, 44f))
            drawOval(Color(0xFFFFD278).copy(alpha = .7f), Offset(39f, 40f), Size(10f, 23f))
        }
    }
}

private fun DrawScope.drawLeaf(x: Float, y: Float, width: Float, height: Float, angle: Float, color: Color) {
    rotate(angle, Offset(x, y)) {
        val leaf = Path().apply {
            moveTo(x, y)
            cubicTo(x - width * .73f, y - height * .32f, x - width * .47f, y - height * .86f, x, y - height)
            cubicTo(x + width * .68f, y - height * .81f, x + width * .58f, y - height * .31f, x, y)
            close()
        }
        drawPath(leaf, color)
        drawPath(
            Path().apply { moveTo(x, y - height * .12f); quadraticTo(x - width * .06f, y - height * .43f, x, y - height * .77f) },
            Color.White.copy(alpha = .2f),
            style = Stroke((width * .07f).coerceAtLeast(.7f), cap = StrokeCap.Round),
        )
    }
}

private fun DrawScope.drawSparkle(x: Float, y: Float, radius: Float) {
    drawPath(polygon(x, y - radius * 1.6f, x + radius * .5f, y - radius * .4f, x + radius * 1.2f, y,
        x + radius * .5f, y + radius * .4f, x, y + radius * 1.6f, x - radius * .5f, y + radius * .4f,
        x - radius * 1.2f, y, x - radius * .5f, y - radius * .4f), Color(0xFFFFE8A0))
}

private fun polygon(vararg coordinates: Float) = Path().apply {
    moveTo(coordinates[0], coordinates[1])
    for (index in 2 until coordinates.size step 2) lineTo(coordinates[index], coordinates[index + 1])
    close()
}

private fun starPath(x: Float, y: Float) = polygon(
    x, y - 36f, x + 11f, y - 13f, x + 36f, y - 9f,
    x + 19f, y + 10f, x + 22f, y + 35f, x, y + 24f,
    x - 22f, y + 35f, x - 19f, y + 10f, x - 36f, y - 9f, x - 11f, y - 13f,
)
