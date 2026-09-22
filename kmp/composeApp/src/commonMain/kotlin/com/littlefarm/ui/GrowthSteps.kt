package com.littlefarm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.littlefarm.game.Plot
import com.littlefarm.game.PlotStage

/** Visual milestones only. The engine's watered-at timestamp remains the sole growth timer. */
@Composable
internal fun GrowthSteps(plot: Plot, now: Long) {
    val crop = plot.crop ?: return
    val progress = if (plot.stage == PlotStage.GROWING)
        (1f - plot.remainingMillis(now).toFloat() / crop.growthDurationMillis).coerceIn(0f, 1f) else 0f
    val current = when {
        plot.isReady(now) -> 3
        plot.stage != PlotStage.GROWING -> 0
        progress < .5f -> 1
        else -> 2
    }
    Column(Modifier.fillMaxWidth().testTag("growth_steps"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            listOf("เมล็ด", "งอก", "เติบโต", "พร้อมเก็บ").forEachIndexed { index, label ->
                val reached = index <= current
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(39.dp).background(if (reached) GardenColors.LeafLight else GardenColors.Line.copy(alpha = .4f), CircleShape),
                        contentAlignment = Alignment.Center) {
                        when (index) {
                            0 -> FarmIcon(FarmSymbol.SEED, Modifier.size(26.dp))
                            1 -> CropArt(crop, Modifier.size(28.dp), sprout = true)
                            2 -> CropArt(crop, Modifier.size(30.dp))
                            else -> FarmIcon(FarmSymbol.CHECK, Modifier.size(25.dp), if (reached) GardenColors.Leaf else GardenColors.Muted)
                        }
                    }
                    GardenText(label, size = 13, color = if (reached) GardenColors.Leaf else GardenColors.Muted,
                        bold = index == current, align = TextAlign.Center)
                }
            }
        }
        GardenText(if (plot.stage == PlotStage.GROWING) "รดน้ำแล้ว · ไม่ต้องรดซ้ำ\nปิดเกมไปพักได้ ผักจะโตต่อเอง" else "รอน้ำหยดแรกเพื่อเริ่มเติบโต",
            Modifier.fillMaxWidth(), size = 13, color = GardenColors.Leaf, align = TextAlign.Center)
    }
}
