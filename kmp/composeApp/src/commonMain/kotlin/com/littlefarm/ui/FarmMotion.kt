package com.littlefarm.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import com.littlefarm.game.CropType
import com.littlefarm.game.PlotStage
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Presentation-only settings/events: animations never update the game or save state. */
internal val LocalReducedMotion = staticCompositionLocalOf { false }
internal data class FarmMotionEvent(val id: Int, val kind: String, val plotIndex: Int? = null)
internal val LocalFarmMotionEvent = staticCompositionLocalOf<FarmMotionEvent?> { null }

internal fun farmEventTargetsPlot(event: FarmMotionEvent?, plotIndex: Int?, stage: PlotStage): Boolean {
    if (event == null || plotIndex == null) return false
    if (event.kind == "waterAll") return stage == PlotStage.GROWING
    return event.plotIndex == plotIndex && event.kind in setOf("till", "plant", "water", "harvest")
}

@Composable
internal fun rememberFarmMotionProgress(event: FarmMotionEvent?, active: Boolean): Float {
    val reducedMotion = LocalReducedMotion.current
    val progress = remember { Animatable(1f) }
    LaunchedEffect(event?.id, active, reducedMotion) {
        if (event != null && active && !reducedMotion) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(560, easing = LinearEasing))
        } else {
            progress.snapTo(1f)
        }
    }
    // Stop immediately when the preference changes, including the current frame.
    return if (reducedMotion || !active) 1f else progress.value
}

/** Drawn in SoilBed's 100 × 100 local coordinates. */
internal fun DrawScope.drawPlotAction(kind: String?, progress: Float) {
    if (kind == null || progress >= 1f) return
    val fade = (1f - progress).coerceIn(0f, 1f)
    when (kind) {
        "water", "waterAll" -> {
            repeat(7) { index ->
                val phase = ((progress * 1.35f - index * .055f) / .6f).coerceIn(0f, 1f)
                if (phase > 0f && phase < 1f) {
                    val x = 23f + index * 9f
                    val y = 15f + phase * 48f
                    drawLine(Color(0xFFB4E4EA).copy(alpha = fade), Offset(x, y - 5f), Offset(x - 2f, y), 2.8f, StrokeCap.Round)
                }
            }
            if (progress > .28f) {
                val ring = ((progress - .28f) / .72f).coerceIn(0f, 1f)
                drawOval(Color(0xFFD6F5F2).copy(alpha = fade * .7f), Offset(28f - ring * 9f, 64f - ring * 3f),
                    Size(43f + ring * 18f, 10f + ring * 6f), style = Stroke(1.3f))
            }
        }
        "till" -> repeat(7) { index ->
            val angle = (index * PI / 6).toFloat()
            val spread = sin(progress * PI).toFloat()
            drawCircle(Color(0xFFCBA374).copy(alpha = fade), 1.6f + index % 3,
                Offset(49f + cos(angle) * (17f + progress * 22f), 66f - spread * (14f + index % 3 * 5f)))
        }
        "plant", "harvest" -> repeat(5) { index ->
            val angle = (index * PI * 2 / 5 - PI / 2).toFloat()
            val radius = 16f + progress * 20f
            drawActionSparkle(Offset(50f + cos(angle) * radius, 45f + sin(angle) * radius), 2.5f * fade,
                Color(0xFFFFE8A0).copy(alpha = fade))
        }
    }
}

/** Original orange companion; friendship unlocks contented and rolling poses. */
@Composable
internal fun GardenCat(modifier: Modifier = Modifier, bond: Int = 0) {
    val reducedMotion = LocalReducedMotion.current
    val phase = if (reducedMotion) 0f else {
        val idle = rememberInfiniteTransition(label = "garden-cat-idle")
        val value by idle.animateFloat(0f, 1f,
            infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Restart), label = "cat-breathe-blink")
        value
    }
    Canvas(modifier.size(88.dp, 66.dp)) {
        withTransform({
            scale(size.width / 120f, size.height / 90f, Offset.Zero)
            if (bond >= 15) rotate(-18f, Offset(60f, 55f))
        }) {
            val orange = Color(0xFFD9914B)
            val light = Color(0xFFF2BD76)
            val ink = Color(0xFF71503B)
            val sway = if (reducedMotion) 0f else sin(phase * PI * 2).toFloat()
            drawOval(Color(0xFF385139).copy(alpha = .14f), Offset(14f, 74f), Size(92f, 10f))
            val tail = Path().apply {
                moveTo(81f, 65f)
                cubicTo(111f, 77f, 110f + sway * 3f, 42f, 93f + sway * 4f, 43f)
            }
            drawPath(tail, orange, style = Stroke(11f, cap = StrokeCap.Round))
            drawPath(tail, light, style = Stroke(4f, cap = StrokeCap.Round))
            drawOval(orange, Offset(39f, 43f - sway * .6f), Size(51f, 36f + sway * .6f))
            drawOval(light, Offset(44f, 48f), Size(36f, 28f))
            drawRoundRect(light, Offset(29f, 71f), Size(48f, 8f), CornerRadius(4f))
            drawPath(Path().apply { moveTo(27f, 39f); lineTo(26f, 17f); lineTo(43f, 28f); close() }, orange)
            drawPath(Path().apply { moveTo(55f, 28f); lineTo(72f, 17f); lineTo(73f, 41f); close() }, orange)
            drawPath(Path().apply { moveTo(30f, 31f); lineTo(30f, 23f); lineTo(39f, 30f); close() }, Color(0xFFE9A18A))
            drawPath(Path().apply { moveTo(60f, 30f); lineTo(68f, 23f); lineTo(69f, 32f); close() }, Color(0xFFE9A18A))
            drawOval(orange, Offset(24f, 27f), Size(51f, 39f))
            drawOval(light, Offset(29f, 43f), Size(40f, 22f))
            repeat(3) { index ->
                drawLine(ink.copy(alpha = .4f), Offset(43f + index * 7f, 29f), Offset(44f + index * 6f, 34f), 2.5f, StrokeCap.Round)
            }
            val blinking = bond >= 5 || (!reducedMotion && phase > .79f && phase < .83f)
            if (blinking) {
                drawLine(ink, Offset(34f, 44f), Offset(42f, 44f), 2.4f, StrokeCap.Round)
                drawLine(ink, Offset(57f, 44f), Offset(65f, 44f), 2.4f, StrokeCap.Round)
            } else {
                drawOval(ink, Offset(37f, 40f), Size(3.5f, 6f))
                drawOval(ink, Offset(59f, 40f), Size(3.5f, 6f))
            }
            drawOval(Color(0xFFCF8473), Offset(47f, 47f), Size(6f, 4f))
            drawArc(ink, 15f, 140f, false, Offset(43f, 49f), Size(7f, 6f), style = Stroke(1.3f, cap = StrokeCap.Round))
            drawArc(ink, 25f, 140f, false, Offset(50f, 49f), Size(7f, 6f), style = Stroke(1.3f, cap = StrokeCap.Round))
            drawLine(ink.copy(alpha = .6f), Offset(23f, 48f), Offset(34f, 50f), 1f, StrokeCap.Round)
            drawLine(ink.copy(alpha = .6f), Offset(65f, 50f), Offset(77f, 48f), 1f, StrokeCap.Round)
            drawLine(ink.copy(alpha = .6f), Offset(22f, 54f), Offset(34f, 54f), 1f, StrokeCap.Round)
            drawLine(ink.copy(alpha = .6f), Offset(65f, 54f), Offset(78f, 54f), 1f, StrokeCap.Round)
            if (bond >= 15) {
                drawOval(light, Offset(70f, 42f), Size(12f, 19f))
                drawOval(light, Offset(81f, 47f), Size(10f, 18f))
                drawOval(Color(0xFFE6A194), Offset(73f, 43f), Size(6f, 7f))
                drawOval(Color(0xFFE6A194), Offset(83f, 48f), Size(5f, 6f))
            }
            if (bond >= 5) {
                drawPath(Path().apply {
                    moveTo(84f, 24f); cubicTo(75f, 18f, 78f, 11f, 84f, 15f)
                    cubicTo(90f, 9f, 95f, 17f, 84f, 24f); close()
                }, Color(0xFFD99887))
            }
        }
    }
}

/** One harvested vegetable settles into a basket; reduced motion shows the final illustration. */
@Composable
internal fun HarvestCelebration(crop: CropType, modifier: Modifier = Modifier) {
    val reducedMotion = LocalReducedMotion.current
    val progress = remember(crop) { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(crop, reducedMotion) {
        if (reducedMotion) progress.snapTo(1f)
        else {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(540, easing = LinearEasing))
        }
    }
    val current = if (reducedMotion) 1f else progress.value
    Canvas(modifier.size(160.dp, 124.dp)) {
        withTransform({ scale(size.width / 160f, size.height / 124f, Offset.Zero) }) {
            drawOval(Color(0xFF385139).copy(alpha = .12f), Offset(35f, 111f), Size(92f, 9f))
            drawArc(Color(0xFFA57145), 180f, 180f, false, Offset(47f, 66f), Size(66f, 42f), style = Stroke(6f, cap = StrokeCap.Round))
            drawOval(Color(0xFF8B5F3D), Offset(42f, 81f), Size(76f, 19f))
            val fall = (current / .56f).coerceIn(0f, 1f)
            val cropY = 12f + 24f * fall * fall
            translate(50f, cropY) {
                scale(.6f, .6f, Offset.Zero) { drawCrop(crop, sprout = false) }
            }
            drawPath(Path().apply {
                moveTo(42f, 91f); lineTo(118f, 91f); lineTo(110f, 111f)
                quadraticTo(80f, 120f, 50f, 111f); close()
            }, Color(0xFFD8A568))
            repeat(5) { index ->
                drawLine(Color(0xFFB88851), Offset(55f + index * 12f, 95f), Offset(57f + index * 11f, 110f), 2f, StrokeCap.Round)
            }
            drawLine(Color(0xFFECC68B), Offset(48f, 101f), Offset(112f, 101f), 3f, StrokeCap.Round)
            drawLine(Color(0xFFECC68B), Offset(51f, 108f), Offset(108f, 108f), 2f, StrokeCap.Round)
            drawRoundRect(Color(0xFFE2B779), Offset(40f, 87f), Size(80f, 8f), CornerRadius(4f))
            val spread = if (reducedMotion) 0f else sin(current * PI).toFloat() * 10f
            val sparkleColor = Color(0xFFE3B44F)
            drawActionSparkle(Offset(29f - spread, 55f - spread), 4f, sparkleColor)
            drawActionSparkle(Offset(129f + spread, 41f - spread), 4.5f, sparkleColor)
            drawActionSparkle(Offset(119f + spread, 68f), 2.5f, sparkleColor)
        }
    }
}

private fun DrawScope.drawActionSparkle(center: Offset, radius: Float, color: Color) {
    drawLine(color, Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), 1.7f, StrokeCap.Round)
    drawLine(color, Offset(center.x, center.y - radius * 1.35f), Offset(center.x, center.y + radius * 1.35f), 1.7f, StrokeCap.Round)
}
