package com.littlefarm.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.littlefarm.game.CropType
import com.littlefarm.resources.Res
import com.littlefarm.resources.stitch_market_scale
import com.littlefarm.resources.stitch_shopkeeper
import org.jetbrains.compose.resources.imageResource
import kotlin.math.roundToInt

/** Original Stitch art is scenery only. Prices, stock and actions remain live Compose UI. */
@Composable
internal fun SeedShopScenery() {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        WoodenTitle("ร้านเมล็ดป้าพร", "เมล็ดสำหรับรอบต่อไป", Modifier.fillMaxWidth())
        val portrait = imageResource(Res.drawable.stitch_shopkeeper)
        Box(Modifier.fillMaxWidth().height(214.dp).clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFFF9F0DA), Color(0xFFE6D4AE))))
            .border(1.dp, Color(0xFFC7A578), RoundedCornerShape(24.dp))) {
            Canvas(Modifier.fillMaxSize()) {
                // Quiet timber shelves and an awning keep this a shop, not a dashboard.
                val stripe = size.width / 10f
                repeat(10) { index ->
                    drawRect(if (index % 2 == 0) Color(0xFF63885B) else Color(0xFFFFEBC1),
                        Offset(index * stripe, 0f), Size(stripe + 1f, 26.dp.toPx()))
                    drawRoundRect(if (index % 2 == 0) Color(0xFF63885B) else Color(0xFFFFEBC1),
                        Offset(index * stripe, 18.dp.toPx()), Size(stripe + 1f, 16.dp.toPx()), CornerRadius(9.dp.toPx()))
                }
                drawRect(Color(0xFFC5A57D), Offset(0f, size.height * .67f), Size(size.width, 5.dp.toPx()))
            }
            Column(Modifier.align(Alignment.TopStart).fillMaxWidth(.56f).padding(start = 14.dp, top = 47.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)) {
                GardenText("วันนี้อยากปลูก\nอะไรดีจ๊ะ?", size = 18, bold = true, color = GardenColors.LeafDark)
                GardenText("เมล็ดดี ๆ พร้อมปลูก", size = 13, color = GardenColors.Wood)
            }
            val portraitFrame = RoundedCornerShape(topStart = 54.dp, topEnd = 54.dp, bottomStart = 13.dp, bottomEnd = 13.dp)
            Box(Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 38.dp)
                .width(104.dp).height(138.dp).clip(portraitFrame)
                .background(GardenColors.Paper).border(3.dp, Color(0xFFCEAF7D), portraitFrame).padding(4.dp)) {
                Canvas(Modifier.fillMaxSize().clip(portraitFrame)) {
                    // The framed bust excludes the source's differently named shop sign and
                    // gives its cream background a deliberate edge, instead of a floating rectangle.
                    drawImage(portrait, srcOffset = IntOffset(205, 37), srcSize = IntSize(210, 300),
                        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()))
                }
            }
            WoodenCounter(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(42.dp))
            Row(Modifier.align(Alignment.BottomStart).padding(start = 17.dp, bottom = 23.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                SeedPacket(CropType.LETTUCE, Modifier.width(39.dp).height(55.dp), compact = true)
                SeedPacket(CropType.CARROT, Modifier.width(39.dp).height(55.dp), compact = true)
                SeedPacket(CropType.RADISH, Modifier.width(39.dp).height(55.dp), compact = true)
            }
        }
        GardenText("เลือกจำนวนก่อนซื้อ · ใช้เฉพาะเหรียญในเกม", Modifier.fillMaxWidth(), size = 13, color = GardenColors.Wood)
    }
}

@Composable
internal fun ProduceMarketScenery(totalProduce: Long) {
    val scale = imageResource(Res.drawable.stitch_market_scale)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        WoodenTitle("ตลาดผักสด", "วันนี้เอาอะไรมาขายดี", Modifier.fillMaxWidth())
        Box(Modifier.fillMaxWidth().height(165.dp).clip(RoundedCornerShape(24.dp))
            .border(1.dp, Color(0xFFC7A578), RoundedCornerShape(24.dp))) {
            Canvas(Modifier.fillMaxSize()) {
                // Exclude the exported reference's top caption and surrounding page margin.
                drawImage(scale, srcOffset = IntOffset(16, 30), srcSize = IntSize(480, 232),
                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()))
            }
            GardenText("ชั่งผักสดจากสวนเรา", Modifier.align(Alignment.BottomStart).padding(10.dp)
                .background(GardenColors.Paper.copy(alpha = .94f), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 5.dp),
                size = 14, bold = true, color = GardenColors.Wood)
        }
        Row(Modifier.fillMaxWidth().background(Color(0xFFF2E4C7), RoundedCornerShape(18.dp)).padding(13.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FarmIcon(FarmSymbol.BAG, Modifier.size(40.dp))
            Column(Modifier.weight(1f)) {
                GardenText("ตะกร้าของเธอ · $totalProduce ชิ้น", size = 17, bold = true)
                GardenText("จะขายหรือเก็บส่งงานก็ได้นะ", size = 13, color = GardenColors.Muted)
            }
        }
    }
}

@Composable
internal fun SeedPacket(crop: CropType, modifier: Modifier = Modifier, compact: Boolean = false) {
    val shape = RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp, bottomStart = 11.dp, bottomEnd = 11.dp)
    Column(modifier.background(Color(0xFFFFF6DA), shape).border(1.dp, Color(0xFFCFAE73), shape)
        .padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.fillMaxWidth().height(if (compact) 4.dp else 6.dp).background(GardenColors.Leaf, RoundedCornerShape(3.dp)))
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            CropArt(crop, Modifier.fillMaxSize().padding(if (compact) 1.dp else 3.dp))
        }
        if (!compact) GardenText("เมล็ดพันธุ์", size = 13, bold = true, color = GardenColors.Wood)
    }
}

@Composable
internal fun MarketReceipt(crop: CropType, quantity: Int, earnedCoins: Long, currentCoins: Int) {
    Column(Modifier.fillMaxWidth().testTag("sale_receipt")
        .background(Color(0xFFEDF2DA), RoundedCornerShape(16.dp))
        .border(1.dp, Color(0xFFB8CD94), RoundedCornerShape(16.dp)).padding(13.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        GardenText("ขายเรียบร้อย ขอบใจนะ!", size = 17, bold = true, color = GardenColors.LeafDark)
        GardenText("${crop.thaiName} $quantity ${crop.produceUnit} · รับ $earnedCoins เหรียญ", Modifier.testTag("sale_receipt_detail"), size = 14)
        GardenText("เหรียญตอนนี้ $currentCoins", Modifier.testTag("sale_receipt_balance"), size = 15, bold = true, color = GardenColors.Leaf)
    }
}

@Composable
private fun WoodenCounter(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRect(Color(0xFF936341))
        drawRect(Color(0xFFC3945D), size = Size(size.width, 9.dp.toPx()))
        drawLine(Color(0xFFDEBA83), Offset.Zero, Offset(size.width, 0f), 2.dp.toPx())
        drawLine(Color(0xFF70472F), Offset(0f, 10.dp.toPx()), Offset(size.width, 10.dp.toPx()), 2.dp.toPx())
        repeat(4) { index ->
            val y = 18.dp.toPx() + index * 7.dp.toPx()
            drawLine(Color(0xFF795036).copy(alpha = .38f), Offset(10.dp.toPx(), y), Offset(size.width - 10.dp.toPx(), y), 1.dp.toPx())
        }
    }
}
