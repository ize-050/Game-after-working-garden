package com.littlefarm.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.littlefarm.game.GameState
import com.littlefarm.game.UpgradeType
import com.littlefarm.resources.Res
import com.littlefarm.resources.stitch_carpenter
import org.jetbrains.compose.resources.painterResource

/** Purchase rules and persistence belong to the caller; this shop only presents the current state. */
@Composable
internal fun UpgradeShopScreen(
    state: GameState,
    onBuy: (UpgradeType) -> Unit,
    onMarket: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().testTag("upgrades_screen")
            .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        WoodenTitle("ร้านช่างไม้", "ค่อย ๆ ต่อเติมสวนของเรา", Modifier.align(Alignment.CenterHorizontally))
        CarpenterWelcome()
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                GardenText("ของใช้คู่สวน", size = 21, bold = true, color = GardenColors.LeafDark)
                GardenText("ซื้อครั้งเดียว ใช้ได้ตลอด", size = 13, color = GardenColors.Muted)
            }
            CoinPill(state.coins, Modifier.testTag("upgrade_balance"))
        }
        UpgradeType.entries.forEach { upgrade ->
            val expand = upgrade == UpgradeType.EXTRA_PLOTS
            val owned = if (expand) state.upgrades.expandedPlots else state.upgrades.largeWateringCan
            val shortfall = (upgrade.price.toLong() - state.coins.toLong()).coerceAtLeast(0L)
            PaperCard(Modifier.fillMaxWidth(), padding = 15.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Box(Modifier.size(42.dp).background(GardenColors.Cream, CircleShape), contentAlignment = Alignment.Center) {
                        FarmIcon(if (expand) FarmSymbol.FARM else FarmSymbol.WATER, Modifier.size(31.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        GardenText(if (expand) "เพิ่มที่ให้ผักอีกนิด" else "รดน้ำสะดวกขึ้น", size = 20, bold = true)
                        GardenText(if (expand) "ขยายสวน · เพิ่ม 3 แปลง" else "บัวรดน้ำใบใหญ่", size = 13, color = GardenColors.Muted)
                    }
                }
                UpgradeComparison(upgrade)
                GardenText(
                    if (expand) "จาก 6 เป็น 9 แปลง มีที่ปลูกของโปรดเพิ่มขึ้น โดยไม่เปลี่ยนผักที่ปลูกไว้"
                    else "รดทุกแปลงที่ปลูกแล้วและยังแห้งได้พร้อมกัน ไม่ลดเวลาโต ไม่เริ่มเวลาใหม่ให้ต้นที่รดแล้ว",
                    size = 14, color = GardenColors.Muted,
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    GardenText("ราคาอัปเกรด", Modifier.weight(1f), size = 14, color = GardenColors.Muted)
                    FarmIcon(FarmSymbol.COIN, Modifier.size(23.dp))
                    Spacer(Modifier.width(5.dp))
                    GardenText("${upgrade.price} เหรียญ", size = 18, bold = true)
                }
                when {
                    owned -> Row(Modifier.fillMaxWidth().background(GardenColors.LeafLight, RoundedCornerShape(12.dp)).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        FarmIcon(FarmSymbol.CHECK, Modifier.size(20.dp))
                        GardenText(if (expand) "ฟาร์มเต็มพื้นที่แล้ว · 9 แปลง" else "เป็นของสวนเราแล้ว", size = 14, color = GardenColors.Leaf, bold = true)
                    }
                    shortfall > 0L -> GardenText(
                        "ขาดอีก $shortfall เหรียญ · เก็บผักไปขายเพิ่มได้นะ",
                        Modifier.fillMaxWidth().testTag("upgrade_shortfall_${upgrade.name}")
                            .background(GardenColors.Cream, RoundedCornerShape(12.dp)).padding(10.dp),
                        size = 14, color = GardenColors.Wood,
                    )
                }
                GardenButton(
                    text = if (owned) "อัปเกรดแล้ว" else if (expand) "ขยายสวน · ${upgrade.price} เหรียญ" else "ซื้อบัวรดน้ำ · ${upgrade.price} เหรียญ",
                    modifier = Modifier.fillMaxWidth().testTag("upgrade_${upgrade.name}"),
                    enabled = !owned && shortfall == 0L,
                    symbol = if (owned) FarmSymbol.CHECK else FarmSymbol.HAMMER,
                    onClick = { onBuy(upgrade) },
                )
            }
        }
        GentleNote("ไม่ต้องรีบซื้อก็ได้นะ สวนเล็ก ๆ ก็มีความสุขได้เหมือนกัน")
        GardenButton("ไปตลาดหาเหรียญเพิ่ม", Modifier.fillMaxWidth().testTag("upgrade_to_market"),
            secondary = true, symbol = FarmSymbol.MARKET, onClick = onMarket)
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun CarpenterWelcome() {
    Surface(shape = RoundedCornerShape(22.dp), color = Color(0xFFE6C391), border = BorderStroke(2.dp, Color(0xFFB78A55))) {
        Box(Modifier.fillMaxWidth()) {
            Canvas(Modifier.matchParentSize()) {
                val plank = size.height / 4f
                repeat(4) { row ->
                    drawLine(Color(0xFFAB7C49).copy(alpha = .18f), Offset(0f, row * plank), Offset(size.width, row * plank), 1.5.dp.toPx())
                    drawLine(Color.White.copy(alpha = .12f), Offset(10.dp.toPx(), row * plank + plank / 2f),
                        Offset(size.width * .66f, row * plank + plank / 2f), 1.dp.toPx(), StrokeCap.Round)
                }
            }
            Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CarpenterPortrait(Modifier.size(80.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    GardenText("“ค่อย ๆ สร้างไปด้วยกันนะ”", size = 16, bold = true, color = GardenColors.Wood)
                    GardenText("ช่างไม้ประจำหมู่บ้าน\nต่อเติมด้วยใจ ให้สวนสบายขึ้น", size = 13, color = GardenColors.Ink)
                }
            }
        }
    }
}

@Composable
private fun UpgradeComparison(upgrade: UpgradeType) {
    val expand = upgrade == UpgradeType.EXTRA_PLOTS
    val background = if (expand) Color(0xFFF0EBCD) else Color(0xFFE8F0E9)
    Row(Modifier.fillMaxWidth().testTag("upgrade_preview_${upgrade.name}")
        .background(background, RoundedCornerShape(17.dp)).padding(horizontal = 8.dp, vertical = 11.dp)
        .semantics {
            contentDescription = if (expand) "ก่อนอัปเกรด 6 แปลง หลังอัปเกรด 9 แปลง เพิ่ม 3 แปลง"
            else "ก่อนอัปเกรด รดน้ำทีละแปลง หลังอัปเกรด รดทุกแปลงที่ยังแห้งพร้อมกัน"
        }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        UpgradeComparisonHalf(expand, upgraded = false, Modifier.weight(1f))
        FarmIcon(FarmSymbol.ARROW, Modifier.size(24.dp), GardenColors.Wood)
        UpgradeComparisonHalf(expand, upgraded = true, Modifier.weight(1f))
    }
}

@Composable
private fun UpgradeComparisonHalf(expand: Boolean, upgraded: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        GardenText(if (upgraded) "หลังอัปเกรด" else "ก่อนอัปเกรด", size = 13, color = GardenColors.Muted)
        if (expand) {
            PlotExpansionPreview(if (upgraded) 9 else 6, Modifier.fillMaxWidth().height(82.dp))
        } else {
            WateringPreview(upgraded, Modifier.fillMaxWidth().height(82.dp))
        }
        GardenText(if (expand) { if (upgraded) "9 แปลง" else "6 แปลง" } else { if (upgraded) "รดทุกแปลง" else "ทีละแปลง" },
            size = 14, color = if (upgraded) GardenColors.Leaf else GardenColors.Ink, bold = true, align = TextAlign.Center)
    }
}

@Composable
private fun PlotExpansionPreview(count: Int, modifier: Modifier) {
    Canvas(modifier) {
        val cell = minOf(size.width / 3.65f, size.height / 3.25f)
        val gap = cell * .12f
        val width = cell * 3f + gap * 2f
        val startX = (size.width - width) / 2f
        val startY = (size.height - cell * 3f - gap * 2f) / 2f
        repeat(9) { i ->
            val x = startX + (i % 3) * (cell + gap)
            val y = startY + (i / 3) * (cell + gap)
            val enabled = i < count
            drawRoundRect(if (enabled) Color(0xFF936343) else Color(0xFFD5D8B1), Offset(x, y), Size(cell, cell), CornerRadius(cell * .16f))
            if (enabled) {
                drawLine(Color(0xFFB78C60), Offset(x + cell * .17f, y + cell * .7f), Offset(x + cell * .83f, y + cell * .7f), cell * .06f, StrokeCap.Round)
                drawLine(GardenColors.LeafDark, Offset(x + cell * .5f, y + cell * .63f), Offset(x + cell * .5f, y + cell * .34f), cell * .075f, StrokeCap.Round)
                drawOval(Color(0xFF9EBD60), Offset(x + cell * .2f, y + cell * .23f), Size(cell * .31f, cell * .22f))
                drawOval(Color(0xFF6F9B4C), Offset(x + cell * .49f, y + cell * .18f), Size(cell * .31f, cell * .22f))
            } else {
                drawRoundRect(Color(0xFFB8BF8A), Offset(x + cell * .04f, y + cell * .04f), Size(cell * .92f, cell * .92f),
                    CornerRadius(cell * .16f), style = Stroke(cell * .035f))
            }
        }
    }
}

@Composable
private fun WateringPreview(upgraded: Boolean, modifier: Modifier) {
    Box(modifier) {
        FarmIcon(FarmSymbol.WATER, Modifier.size(if (upgraded) 58.dp else 46.dp).align(Alignment.TopCenter),
            if (upgraded) GardenColors.Leaf else Color(0xFF9A9E80))
        Canvas(Modifier.fillMaxSize()) {
            val width = size.width * .23f
            val startX = (size.width - width * 3f - 8.dp.toPx()) / 2f
            repeat(3) { i ->
                val x = startX + i * (width + 4.dp.toPx())
                val wet = upgraded || i == 1
                drawRoundRect(if (wet) Color(0xFF795941) else Color(0xFFB3875B), Offset(x, size.height * .79f),
                    Size(width, size.height * .16f), CornerRadius(4.dp.toPx()))
                if (wet) {
                    drawLine(Color(0xFF77ADB4), Offset(x + width / 2f, size.height * .64f), Offset(x + width / 2f, size.height * .71f),
                        3.dp.toPx(), StrokeCap.Round)
                }
            }
        }
    }
}

/** The approved Stitch art has a baked-in title; this crop exposes the character, not its old UI. */
@Composable
private fun CarpenterPortrait(modifier: Modifier) {
    Box(modifier.clip(CircleShape).background(Color(0xFFF5DDAA))) {
        Image(painterResource(Res.drawable.stitch_carpenter), null,
            Modifier.fillMaxSize().graphicsLayer { scaleX = 1.35f; scaleY = 1.35f }, contentScale = ContentScale.Crop)
    }
}
