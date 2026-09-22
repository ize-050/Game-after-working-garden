package com.littlefarm.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.littlefarm.game.GameState

@Composable
internal fun VillageOrdersScreen(
    state: GameState,
    onDeliver: () -> Unit,
    onFarm: () -> Unit,
    onShop: () -> Unit,
) {
    val order = state.currentOrder
    val owned = state.produceCount(order.crop).coerceAtLeast(0)
    val missing = (order.quantity - owned).coerceAtLeast(0)
    val rewardFits = state.coins <= Int.MAX_VALUE - order.coins && state.xp <= Int.MAX_VALUE - order.xp
    val ready = missing == 0 && rewardFits
    val progress = (owned.toFloat() / order.quantity).coerceIn(0f, 1f)
    Column(
        Modifier.fillMaxSize().testTag("orders_screen")
            .background(Brush.verticalGradient(listOf(Color(0xFFEBEED9), Color(0xFFF4E5C7))))
            .verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        WoodenTitle("กระดานงานหมู่บ้าน", "ผักจากสวนเรา ความสุขของทุกคน", Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Badge("คำขอที่ ${state.completedOrders.toLong() + 1}", Modifier.testTag("order_number"), symbol = FarmSymbol.ORDERS)
            GardenText("ส่งแล้ว ${state.completedOrders} งาน", Modifier.testTag("orders_completed_count"), size = 13, color = GardenColors.Wood)
        }
        Box(Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(22.dp))) {
            NoticeboardTimber(Modifier.matchParentSize())
            Column(Modifier.fillMaxWidth().padding(12.dp)
                .background(Brush.verticalGradient(listOf(Color(0xFFFFFAE8), Color(0xFFF8EBCB))), RoundedCornerShape(13.dp))
                .border(1.dp, Color(0xFFE8D2A4), RoundedCornerShape(13.dp)).padding(15.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    NoticePin()
                    GardenText("คำขอจากเพื่อนบ้าน", size = 13, color = GardenColors.Wood, bold = true)
                    NoticePin()
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                    NeighborPortrait(order.npc, Modifier.size(58.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        GardenText("ถึงชาวสวนคนเก่ง", size = 16, bold = true, color = GardenColors.LeafDark)
                        GardenText("จาก ${order.npc}", size = 13, color = GardenColors.Muted)
                    }
                }
                GardenText(order.title, Modifier.testTag("order_title"), size = 25, bold = true, color = GardenColors.LeafDark)
                GardenText(
                    if (state.completedOrders == 0) "“อยากทำแกงจืดสักหม้อ ถ้ามีผักกาดสวย ๆ เอามาฝากยายหน่อยนะ”"
                    else "“ขอ${order.crop.thaiName}จากสวนเธอหน่อยนะ ว่างเมื่อไหร่ค่อยแวะมา เรารอได้เสมอ”",
                    size = 14, color = GardenColors.Wood,
                )
                Box(Modifier.fillMaxWidth().height(108.dp)
                    .background(Color(0xFFEBEED3), RoundedCornerShape(19.dp)), contentAlignment = Alignment.Center) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        repeat(order.quantity.coerceAtMost(3)) {
                            CropArt(order.crop, Modifier.size(if (order.quantity > 2) 72.dp else 82.dp))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    GardenText("${order.crop.thaiName} ${order.quantity} ${order.crop.produceUnit}", Modifier.weight(1f), size = 15, bold = true)
                    GardenText("$owned / ${order.quantity}", Modifier.testTag("order_progress"),
                        size = 15, bold = true, color = GardenColors.Leaf)
                }
                Box(Modifier.fillMaxWidth().height(9.dp).background(Color(0xFFE5D9B8), CircleShape)) {
                    if (progress > 0f) Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(GardenColors.Leaf, CircleShape))
                }
                GardenText("ของตอบแทนจาก${order.npc}", size = 13, color = GardenColors.Muted)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f).background(Color(0xFFF5E2AC), RoundedCornerShape(13.dp)).padding(9.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        FarmIcon(FarmSymbol.COIN, Modifier.size(26.dp))
                        GardenText("${order.coins} เหรียญ", size = 14, bold = true)
                    }
                    Column(Modifier.weight(1f).background(GardenColors.LeafLight, RoundedCornerShape(13.dp)).padding(9.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        FarmIcon(FarmSymbol.STAR, Modifier.size(26.dp))
                        GardenText("${order.xp} XP", size = 14, bold = true, color = GardenColors.Leaf)
                    }
                }
                when {
                    missing > 0 -> GardenText("ยังขาด${order.crop.thaiName}อีก $missing ${order.crop.produceUnit}", Modifier.testTag("order_shortfall"), size = 14, color = GardenColors.Wood)
                    !rewardFits -> GardenText("ยังรับรางวัลไม่ได้ เหรียญหรือ XP ถึงขีดจำกัดแล้ว", Modifier.testTag("order_reward_full"), size = 14, color = GardenColors.Wood)
                    else -> GardenText("ครบแล้ว พร้อมส่งให้${order.npc}", size = 14, color = GardenColors.Leaf, bold = true)
                }
                GardenButton("ส่งผักให้${order.npc}",
                    Modifier.fillMaxWidth().testTag("order_deliver"), enabled = ready, symbol = FarmSymbol.CHECK, onClick = onDeliver)
                GardenText("ไม่มีเส้นตาย ไม่ต้องรีบร้อน", Modifier.fillMaxWidth(), size = 13, color = GardenColors.Muted, align = TextAlign.Center)
            }
        }
        GentleNote("ส่งงานแล้วจะมีจดหมายฉบับใหม่ทันที · เก็บผักไว้ส่งงานหรือขายก็ได้ ไม่มีงานไหนหมดเวลา")
        GardenButton("กลับไปดูผักที่สวน", Modifier.fillMaxWidth().testTag("orders_to_farm"),
            secondary = true, symbol = FarmSymbol.FARM, onClick = onFarm)
        GardenButton("ซื้อเมล็ดสำหรับงานนี้", Modifier.fillMaxWidth().testTag("orders_to_shop"),
            secondary = true, symbol = FarmSymbol.SEED, onClick = onShop)
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun NoticePin() {
    Box(Modifier.padding(top = 3.dp).size(10.dp).shadow(1.dp, CircleShape)
        .background(Color(0xFFB26B45), CircleShape).border(1.dp, Color(0xFF89513A), CircleShape))
}

@Composable
private fun NoticeboardTimber(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFF8F6240), Color(0xFFB78853), Color(0xFF8F6240))),
            cornerRadius = CornerRadius(22.dp.toPx()))
        drawRoundRect(Color(0xFF68462E), cornerRadius = CornerRadius(22.dp.toPx()), style = Stroke(2.dp.toPx()))
        val inset = 6.dp.toPx()
        for (y in listOf(inset, size.height - inset)) {
            drawLine(Color(0xFFDCB17A), Offset(18.dp.toPx(), y), Offset(size.width - 18.dp.toPx(), y), 1.dp.toPx())
        }
        for (x in listOf(inset, size.width - inset)) {
            drawLine(Color(0xFF6E4A31).copy(alpha = .6f), Offset(x, 20.dp.toPx()), Offset(x, size.height - 20.dp.toPx()), 1.dp.toPx())
        }
    }
}

@Composable
private fun NeighborPortrait(npc: String, modifier: Modifier = Modifier) {
    if (npc != "คุณยาย") {
        Canvas(modifier.background(Color(0xFFE5D8B9), CircleShape)) {
            val s = size.minDimension / 100f
            fun point(x: Float, y: Float) = Offset(x * s, y * s)
            val chef = npc.contains("เชฟ") || npc.contains("ร้าน")
            drawOval(if (chef) Color(0xFFFFF6DA) else Color(0xFF8DA07B), point(17f, 66f), Size(66f * s, 33f * s))
            drawCircle(Color(0xFF73533A), 30f * s, point(50f, 42f))
            drawCircle(Color(0xFFF1C393), 27f * s, point(50f, 50f))
            if (chef) {
                drawRoundRect(Color(0xFFFFFBED), point(25f, 17f), Size(50f * s, 18f * s), CornerRadius(5f * s))
                repeat(3) { drawCircle(Color(0xFFFFFBED), 12f * s, point(32f + it * 18f, 18f)) }
            } else {
                drawArc(Color(0xFF73533A), 180f, 170f, false, point(22f, 21f), Size(56f * s, 40f * s), style = Stroke(9f * s))
            }
            drawCircle(GardenColors.Ink, 2.2f * s, point(39f, 50f))
            drawCircle(GardenColors.Ink, 2.2f * s, point(62f, 50f))
            drawArc(Color(0xFFA15C43), 0f, 180f, false, point(41f, 58f), Size(18f * s, 11f * s), style = Stroke(2f * s))
            drawCircle(Color(0xFFE59B80).copy(alpha = .5f), 5f * s, point(31f, 57f))
            drawCircle(Color(0xFFE59B80).copy(alpha = .5f), 5f * s, point(70f, 57f))
        }
        return
    }
    Canvas(modifier.background(Color(0xFFE5D8B9), CircleShape)) {
        val s = size.minDimension / 100f
        fun point(x: Float, y: Float) = Offset(x * s, y * s)
        drawCircle(Color(0xFFB8B5A9), 20f * s, point(50f, 26f))
        drawOval(Color(0xFF8DA07B), point(17f, 67f), Size(66f * s, 32f * s))
        drawCircle(Color(0xFFF1C393), 29f * s, point(50f, 50f))
        drawArc(Color(0xFFC7C5B7), 180f, 180f, false, point(20f, 19f), Size(60f * s, 45f * s), style = Stroke(9f * s))
        drawCircle(Color(0xFF8A725C), 9f * s, point(37f, 48f), style = Stroke(1.6f * s))
        drawCircle(Color(0xFF8A725C), 9f * s, point(63f, 48f), style = Stroke(1.6f * s))
        drawLine(Color(0xFF8A725C), point(46f, 47f), point(54f, 47f), 1.6f * s)
        drawCircle(GardenColors.Ink, 2f * s, point(37f, 49f))
        drawCircle(GardenColors.Ink, 2f * s, point(63f, 49f))
        drawArc(Color(0xFFA15C43), 0f, 180f, false, point(41f, 58f), Size(18f * s, 12f * s), style = Stroke(2f * s))
    }
}
