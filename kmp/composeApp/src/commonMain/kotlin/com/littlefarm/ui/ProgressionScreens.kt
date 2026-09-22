package com.littlefarm.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.littlefarm.game.CollectionBadge
import com.littlefarm.game.CatState
import com.littlefarm.game.CropType
import com.littlefarm.game.Decoration
import com.littlefarm.game.DecorationSlot
import com.littlefarm.game.GameState

@Composable
internal fun DecorationShopScreen(
    state: GameState,
    onBuy: (Decoration) -> Unit,
    onEquip: (Decoration) -> Unit,
    onUnequip: (DecorationSlot) -> Unit,
) {
    ProgressionPage("decorations_screen") {
        WoodenTitle("ร้านของแต่งสวน", "เก็บเหรียญ สร้างมุมโปรดของเรา", Modifier.fillMaxWidth())
        PaperCard(Modifier.fillMaxWidth(), padding = 10.dp) {
            GardenDecorScene(state, Modifier.fillMaxWidth().height(190.dp))
            GardenText("สวนของเราตอนนี้", Modifier.fillMaxWidth(), size = 13, color = GardenColors.Leaf, align = TextAlign.Center)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            GardenText("ชิ้นโปรดของเธอ", Modifier.weight(1f), size = 19, bold = true)
            CoinPill(state.coins)
        }
        GentleNote("ซื้อครั้งเดียว ใช้ได้ตลอด · แต่ละมุมวางได้ 1 ชิ้น สลับหรือเก็บของได้ฟรี")
        Decoration.entries.forEach { decor ->
            val owned = decor in state.ownedDecor
            val equipped = state.equippedDecor[decor.slot] == decor
            PaperCard(Modifier.fillMaxWidth().testTag("decor_card_${decor.name}"), padding = 14.dp,
                tint = if (equipped) Color(0xFFF2F3DE) else GardenColors.Paper) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                    DecorationArt(decor, Modifier.size(80.dp).background(Color(0xFFECE8CC), RoundedCornerShape(18.dp)))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        GardenText(decor.thaiName, size = 19, bold = true, color = GardenColors.LeafDark)
                        GardenText(decor.description(), size = 13, color = GardenColors.Muted)
                        if (equipped) Badge("กำลังแต่งสวน", symbol = FarmSymbol.CHECK)
                        else if (owned) Badge("เป็นของเราแล้ว", symbol = FarmSymbol.BAG)
                        else if (!decor.rewardOnly) GardenText("${decor.price} เหรียญ", size = 15, bold = true, color = GardenColors.Wood)
                    }
                }
                when {
                    equipped -> GardenButton("เก็บเข้ากล่อง", Modifier.fillMaxWidth().testTag("decor_remove_${decor.slot.name}"),
                        secondary = true, symbol = FarmSymbol.BAG, onClick = { onUnequip(decor.slot) })
                    owned -> GardenButton(if (decor.slot == DecorationSlot.HOUSE) "ทาสีบ้านนี้" else "วางในสวน",
                        Modifier.fillMaxWidth().testTag("decor_equip_${decor.name}"), symbol = FarmSymbol.FARM, onClick = { onEquip(decor) })
                    decor.rewardOnly -> {
                        GardenText(decor.rewardDescription(), Modifier.testTag("decor_reward_${decor.name}"), size = 13, color = GardenColors.Wood)
                        GardenButton("ของขวัญจากสมุดสะสม", Modifier.fillMaxWidth(), enabled = false, symbol = FarmSymbol.STAR, onClick = {})
                    }
                    else -> {
                        if (state.coins < decor.price) GardenText("เก็บเพิ่มอีก ${decor.price - state.coins} เหรียญ ก็เป็นของเราแล้ว", size = 13, color = GardenColors.Wood)
                        GardenButton("ซื้อ ${decor.price} เหรียญ", Modifier.fillMaxWidth().testTag("decor_buy_${decor.name}"),
                            enabled = state.coins >= decor.price, symbol = FarmSymbol.COIN, onClick = { onBuy(decor) })
                    }
                }
            }
        }
        GentleNote("ของแต่งสวนไม่มีผลกับความเร็วหรือรางวัล เลือกแบบที่ชอบได้เลย")
    }
}

@Composable
internal fun CollectionScreen(state: GameState) {
    val total = state.harvestCounts.values.sumOf { it.toLong() }
    ProgressionPage("collection_screen") {
        WoodenTitle("สมุดสะสมพืช", "ทุกผลผลิต มีเรื่องราวของเรา", Modifier.fillMaxWidth())
        PaperCard(Modifier.fillMaxWidth(), tint = Color(0xFFEEECD0)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FarmIcon(FarmSymbol.LEAF, Modifier.size(52.dp))
                Column(Modifier.weight(1f)) {
                    GardenText("เก็บเกี่ยวแล้ว $total ครั้ง", Modifier.testTag("collection_total"), size = 19, bold = true, color = GardenColors.LeafDark)
                    GardenText("ค้นพบ ${CropType.entries.count { state.harvestCount(it) > 0 }} / ${CropType.entries.size} ชนิด · ${state.earnedBadges.size} ตรารางวัล", size = 13, color = GardenColors.Leaf)
                }
            }
        }
        GentleNote("นับจากการเก็บเกี่ยวจริง ขายหรือส่งงานแล้วสถิติยังอยู่ · ครบ 10 และ 50 ครั้ง รับของแต่งสวนฟรี")
        CropType.entries.forEach { crop ->
            val count = state.harvestCount(crop)
            PaperCard(Modifier.fillMaxWidth().testTag("collection_${crop.name}"), padding = 15.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(84.dp).background(if (count > 0) Color(0xFFEAEBD2) else Color(0xFFECE7D9), RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center) {
                        CropArt(crop, Modifier.size(76.dp), sprout = count == 0)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        GardenText(crop.thaiName, size = 20, bold = true)
                        GardenText("เก็บเกี่ยว $count ครั้ง", Modifier.testTag("collection_count_${crop.name}"), size = 15, bold = true, color = GardenColors.Leaf)
                        GardenText(if (!state.isUnlocked(crop)) "เมล็ดปลดล็อกเลเวล ${crop.unlockLevel}" else if (count == 0) "ปลูกรอบแรกเพื่อเปิดภาพในสมุด" else "บันทึกความภูมิใจเล็ก ๆ ของเรา", size = 13, color = GardenColors.Muted)
                    }
                }
                CollectionBadge.forCrop(crop).forEach { badge ->
                    val earned = count >= badge.threshold
                    Column(Modifier.fillMaxWidth().testTag("collection_badge_${crop.name}_${badge.threshold}")
                        .background(if (earned) GardenColors.LeafLight else GardenColors.Cream, RoundedCornerShape(14.dp)).padding(11.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            FarmIcon(if (earned) FarmSymbol.STAR else FarmSymbol.LEAF, Modifier.size(26.dp), if (earned) GardenColors.Leaf else GardenColors.Muted)
                            GardenText(badge.thaiName, Modifier.weight(1f), size = 14, bold = true, color = if (earned) GardenColors.LeafDark else GardenColors.Wood)
                            if (earned) FarmIcon(FarmSymbol.CHECK, Modifier.size(19.dp))
                        }
                        ProgressTrack(count.toFloat() / badge.threshold)
                        GardenText(if (earned) "ได้รับตราแล้ว · ${badge.reward.thaiName}" else "${count.coerceAtMost(badge.threshold)} / ${badge.threshold} ครั้ง · ของขวัญ ${badge.reward.thaiName}", size = 13, color = GardenColors.Muted)
                        if (!earned && badge.reward in state.ownedDecor) GardenText("มีของแต่งชิ้นนี้แล้ว · ยังสะสมตราพืชนี้ต่อได้", size = 13, color = GardenColors.Leaf)
                    }
                }
            }
        }
        GentleNote("ของขวัญชนิดเดียวกันรับเข้ากล่องครั้งเดียว แต่สะสมตราของพืชแต่ละชนิดได้ครบทุกตรา")
    }
}

@Composable
internal fun CatScreen(state: GameState, nowMillis: Long, onRename: (String) -> Unit, onPet: () -> Unit) {
    var name by remember(state.cat.name) { mutableStateOf(state.cat.name) }
    val cooldown = state.cat.petCooldownRemaining(nowMillis)
    val nameValid = CatState.isValidName(name.trim()) && name.trim() != state.cat.name
    ProgressionPage("cat_screen") {
        WoodenTitle("เพื่อนตัวน้อยในสวน", "มีความสุขที่ได้อยู่ข้าง ๆ กัน", Modifier.fillMaxWidth())
        PaperCard(Modifier.fillMaxWidth(), padding = 12.dp, tint = Color(0xFFF4EAD4)) {
            Box(Modifier.fillMaxWidth().height(194.dp).clip(RoundedCornerShape(20.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFFEBEED5), Color(0xFFD8DFC0)))), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    drawOval(Color(0xFFA1B28A).copy(alpha = .4f), Offset(size.width * .16f, size.height * .68f), Size(size.width * .7f, size.height * .2f))
                    repeat(6) { i ->
                        val x = size.width * (.08f + i * .17f)
                        val y = size.height * .79f
                        drawLine(Color(0xFF879F6B), Offset(x, y), Offset(x - 4.dp.toPx(), y - 7.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
                    }
                }
                GardenCat(Modifier.size(212.dp, 159.dp), bond = state.cat.bond)
                Badge(state.cat.pose.thaiName, Modifier.align(Alignment.TopEnd).padding(10.dp), background = GardenColors.Paper)
            }
            GardenText(state.cat.name, Modifier.fillMaxWidth(), size = 28, bold = true, color = GardenColors.LeafDark, align = TextAlign.Center)
            GardenText(when {
                state.cat.bond >= 15 -> "ไว้ใจเธอที่สุดเลย ขอกลิ้งเล่นใกล้ ๆ นะ"
                state.cat.bond >= 5 -> "เสียงครางเบา ๆ บอกว่าเราเริ่มเป็นเพื่อนกันแล้ว"
                else -> "ขอนั่งเป็นเพื่อนชาวสวนคนใหม่ตรงนี้นะ"
            }, Modifier.fillMaxWidth(), size = 14, color = GardenColors.Wood, align = TextAlign.Center)
        }
        PaperCard(Modifier.fillMaxWidth()) {
            SectionLabel("ความสนิทของเรา", "ไม่ลดเมื่อไม่ได้แวะมา")
            GardenText("${state.cat.bond} แต้มความผูกพัน", Modifier.testTag("cat_bond"), size = 20, bold = true, color = GardenColors.Leaf)
            val target = if (state.cat.bond < 5) 5 else 15
            ProgressTrack(state.cat.bond.toFloat() / target)
            GardenText(if (state.cat.bond >= 15) "ปลดล็อกครบทุกท่าทางแล้ว แต่ยังมาลูบหัวกันได้เสมอ"
                else "อีก ${target - state.cat.bond} แต้ม ปลดล็อกท่า${if (target == 5) "ครางอย่างมีความสุข" else "กลิ้งเล่น"}", size = 13, color = GardenColors.Muted)
            GardenButton(if (cooldown > 0) "กำลังเคลิ้ม… ${((cooldown - 1) / 1000 + 1)} วินาที" else "ลูบหัว ${state.cat.name}",
                Modifier.fillMaxWidth().testTag("cat_pet"), enabled = state.cat.canPet(nowMillis), symbol = FarmSymbol.LEAF, onClick = onPet)
            if (cooldown > 0) GardenText("ความสนิทเพิ่มแล้ว ให้เพื่อนพักสักครู่นะ", Modifier.testTag("cat_cooldown"), size = 13, color = GardenColors.Leaf)
            GardenText("ท่าทางที่มี ${state.cat.unlockedPoses.size} / 3 · ${state.cat.unlockedPoses.joinToString(" · ") { it.thaiName }}", size = 13, color = GardenColors.Wood)
        }
        PaperCard(Modifier.fillMaxWidth()) {
            SectionLabel("เรียกเพื่อนว่าอะไรดี?")
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.filterNot(Char::isISOControl).take(CatState.MAX_NAME_LENGTH) },
                modifier = Modifier.fillMaxWidth().testTag("cat_name"),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge,
                label = { GardenText("ชื่อแมว", size = 14) },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GardenColors.Leaf, unfocusedBorderColor = GardenColors.Line,
                    focusedContainerColor = GardenColors.Paper, unfocusedContainerColor = GardenColors.Paper),
            )
            GardenText("ตั้งชื่อได้ไม่เกิน ${CatState.MAX_NAME_LENGTH} ตัวอักษร เปลี่ยนได้ฟรีเสมอ", size = 13, color = GardenColors.Muted)
            GardenButton("บันทึกชื่อ", Modifier.fillMaxWidth().testTag("cat_rename"), enabled = nameValid,
                secondary = true, symbol = FarmSymbol.SAVE, onClick = { onRename(name.trim()) })
        }
        GentleNote("ไม่ต้องให้อาหาร ไม่ต้องเช็กชื่อทุกวัน ความสนิทไม่หายไป แมวจะอยู่เป็นเพื่อนสวนเธอเสมอ")
    }
}

@Composable
private fun ProgressionPage(tag: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().testTag(tag)
        .background(Brush.verticalGradient(listOf(GardenColors.Cream, Color(0xFFF0E2C6))))
        .verticalScroll(rememberScrollState()).padding(horizontal = 17.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp), content = content)
}

@Composable
private fun ProgressTrack(progress: Float) {
    Box(Modifier.fillMaxWidth().height(8.dp).background(Color(0xFFE1D9BA), CircleShape)) {
        val fraction = progress.coerceIn(0f, 1f)
        if (fraction > 0f) Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(GardenColors.Leaf, CircleShape))
    }
}

/** A real state-driven garden vignette. Each owned object occupies one stable garden slot. */
@Composable
internal fun GardenDecorScene(state: GameState, modifier: Modifier = Modifier) {
    val description = state.equippedDecor.values.joinToString(" · ") { it.thaiName }.ifEmpty { "บ้านหลังน้อยของเรา" }
    Box(modifier.clip(RoundedCornerShape(20.dp)).testTag("decor_scene")
        .semantics { contentDescription = "สวนที่แต่งไว้: $description" }) {
        Canvas(Modifier.fillMaxSize()) {
            // The farm uses a shallow banner and the shop uses a taller preview.
            // Fit the objects uniformly so neither layout stretches the little house.
            drawRect(Color(0xFFE6ECD0))
            drawCircle(Color(0xFFFFE7AA), size.minDimension * .15f, Offset(size.width * .84f, size.height * .15f))
            drawOval(Color(0xFFC8D5AC), Offset(-size.width * .16f, size.height * .31f), Size(size.width * 1.32f, size.height * .77f))
            drawOval(Color(0xFFD6DAB0), Offset(-size.width * .13f, size.height * .54f), Size(size.width * 1.29f, size.height * .71f))
            val sceneScale = minOf(size.width / 320f, size.height / 170f)
            withTransform({
                translate((size.width - 320f * sceneScale) / 2f, (size.height - 170f * sceneScale) / 2f)
                scale(sceneScale, sceneScale, Offset.Zero)
            }) {
                drawOval(Color(0xFFFFF8DD).copy(alpha = .6f), Offset(46f, 126f), Size(225f, 30f))
                for (x in listOf(16f, 289f, 309f)) {
                    drawRoundRect(Color(0xFF9D8154), Offset(x - 3f, 55f), Size(6f, 44f), CornerRadius(3f))
                    drawCircle(Color(0xFF8DAB77), 25f, Offset(x, 48f))
                    drawCircle(Color(0xFFA4BB86), 18f, Offset(x - 7f, 40f))
                }
                state.equippedDecor[DecorationSlot.FENCE]?.let { fence ->
                    translate(left = 26f, top = 79f) { scale(1.08f, .65f, Offset.Zero) { drawDecor(fence) } }
                    translate(left = 195f, top = 79f) { scale(1.02f, .65f, Offset.Zero) { drawDecor(fence) } }
                }
                state.equippedDecor[DecorationSlot.PATH]?.let {
                    translate(left = 116f, top = 92f) { scale(.9f, .82f, Offset.Zero) { drawDecor(it) } }
                }
                val house = state.equippedDecor[DecorationSlot.HOUSE]
                translate(left = 108f, top = 23f) { scale(1.02f, 1.02f, Offset.Zero) { drawLittleHouse(house.houseTint()) } }
                state.equippedDecor[DecorationSlot.BENCH]?.let {
                    translate(left = 209f, top = 107f) { scale(.73f, .63f, Offset.Zero) { drawDecor(it) } }
                }
                state.equippedDecor[DecorationSlot.LAMP]?.let {
                    translate(left = 55f, top = 83f) { scale(.64f, .76f, Offset.Zero) { drawDecor(it) } }
                }
                repeat(10) { i ->
                    val x = 12f + i * 33f
                    val y = if (i % 2 == 0) 152f else 145f
                    drawLine(Color(0xFF9AB07D), Offset(x, y), Offset(x - 2f, y - 4f), 1.6f, StrokeCap.Round)
                    drawLine(Color(0xFF9AB07D), Offset(x, y), Offset(x + 3f, y - 5f), 1.6f, StrokeCap.Round)
                }
            }
        }
        GardenCat(Modifier.align(Alignment.BottomStart).padding(start = 14.dp, bottom = 5.dp).fillMaxHeight(.33f).aspectRatio(1.33f), bond = state.cat.bond)
    }
}

@Composable
private fun DecorationArt(decor: Decoration, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        withTransform({ scale(size.width / 100f, size.height / 100f, Offset.Zero) }) { drawDecor(decor) }
    }
}

private fun DrawScope.drawDecor(decor: Decoration) {
    val wood = Color(0xFFAC8050)
    val lightWood = Color(0xFFD4AE76)
    val ink = Color(0xFF76563A)
    fun round(x: Float, y: Float, w: Float, h: Float, color: Color, radius: Float = 3f) =
        drawRoundRect(color, Offset(x, y), Size(w, h), CornerRadius(radius))
    when (decor) {
        Decoration.WOOD_FENCE, Decoration.FLOWER_FENCE -> {
            round(8f, 43f, 84f, 8f, wood)
            round(8f, 68f, 84f, 8f, wood)
            repeat(5) { i ->
                val x = 13f + i * 17f
                val post = Path().apply { moveTo(x, 34f); lineTo(x + 6f, 25f); lineTo(x + 12f, 34f); lineTo(x + 12f, 84f); lineTo(x, 84f); close() }
                drawPath(post, if (decor == Decoration.FLOWER_FENCE) Color(0xFFEFE6C9) else lightWood)
                drawLine(wood, Offset(x + 11f, 35f), Offset(x + 11f, 83f), 1.4f)
                drawCircle(ink, 1.4f, Offset(x + 6f, 47f))
                drawCircle(ink, 1.4f, Offset(x + 6f, 72f))
                if (decor == Decoration.FLOWER_FENCE && i % 2 == 0) {
                    drawLine(Color(0xFF6F985E), Offset(x + 7f, 88f), Offset(x + 7f, 58f), 3f)
                    repeat(5) { petal -> rotate(petal * 72f, Offset(x + 7f, 58f)) {
                        drawOval(Color(0xFFDA9B88), Offset(x + 3f, 47f), Size(8f, 12f))
                    } }
                    drawCircle(Color(0xFFFFDEA0), 3f, Offset(x + 7f, 58f))
                }
            }
        }
        Decoration.STONE_PATH -> {
            listOf(Triple(37f, 21f, 25f), Triple(21f, 38f, 31f), Triple(48f, 46f, 33f), Triple(18f, 62f, 38f), Triple(51f, 75f, 36f)).forEach { (x, y, w) ->
                drawOval(Color(0xFFA69D7E), Offset(x, y + 3f), Size(w, 13f))
                drawOval(Color(0xFFD4CBB1), Offset(x, y), Size(w, 13f))
                drawLine(Color(0xFFE8DFC4), Offset(x + 7f, y + 3f), Offset(x + w - 8f, y + 3f), 2f, StrokeCap.Round)
            }
        }
        Decoration.WARM_LAMP, Decoration.STAR_LAMP -> {
            drawCircle(Color(0xFFFFE6A1).copy(alpha = .35f), 25f, Offset(50f, 34f))
            round(47f, 41f, 6f, 47f, ink)
            round(35f, 85f, 30f, 6f, wood)
            round(39f, 24f, 22f, 29f, Color(0xFFFFDC88), 5f)
            drawRoundRect(ink, Offset(39f, 24f), Size(22f, 29f), CornerRadius(5f), style = Stroke(3f))
            drawPath(Path().apply { moveTo(34f, 25f); lineTo(50f, 13f); lineTo(66f, 25f); close() }, if (decor == Decoration.STAR_LAMP) Color(0xFF68876B) else ink)
            if (decor == Decoration.STAR_LAMP) {
                drawPath(Path().apply { moveTo(50f, 28f); lineTo(53f, 35f); lineTo(60f, 36f); lineTo(54f, 41f); lineTo(56f, 47f); lineTo(50f, 43f); lineTo(44f, 47f); lineTo(46f, 41f); lineTo(40f, 36f); lineTo(47f, 35f); close() }, Color(0xFFC99342))
            } else drawLine(Color(0xFFFFF4C9), Offset(46f, 31f), Offset(46f, 43f), 4f, StrokeCap.Round)
        }
        Decoration.GARDEN_BENCH -> {
            round(21f, 45f, 7f, 39f, ink)
            round(72f, 45f, 7f, 39f, ink)
            round(16f, 26f, 68f, 13f, lightWood)
            round(16f, 42f, 68f, 13f, wood)
            round(11f, 61f, 78f, 12f, lightWood)
            round(11f, 71f, 78f, 5f, wood)
            round(17f, 76f, 7f, 13f, ink)
            round(76f, 76f, 7f, 13f, ink)
            drawLine(Color(0xFFE6C591), Offset(20f, 29f), Offset(78f, 29f), 2f)
        }
        Decoration.HOUSE_MINT, Decoration.HOUSE_PEACH -> drawLittleHouse(decor.houseTint())
    }
}

private fun DrawScope.drawLittleHouse(wall: Color) {
    drawOval(Color(0xFF6E8254).copy(alpha = .17f), Offset(11f, 86f), Size(79f, 11f))
    drawRoundRect(wall, Offset(19f, 37f), Size(64f, 50f), CornerRadius(4f))
    drawPath(Path().apply { moveTo(8f, 41f); lineTo(50f, 10f); lineTo(93f, 41f); close() }, Color(0xFF8B694A))
    drawPath(Path().apply { moveTo(12f, 38f); lineTo(50f, 11f); lineTo(89f, 38f); close() }, Color(0xFFB1875E))
    drawLine(Color(0xFFCBA16F), Offset(21f, 32f), Offset(80f, 32f), 2f, StrokeCap.Round)
    drawRoundRect(Color(0xFF7C987E), Offset(30f, 50f), Size(19f, 19f), CornerRadius(3f))
    drawRoundRect(Color(0xFFFFF4D8), Offset(30f, 50f), Size(19f, 19f), CornerRadius(3f), style = Stroke(3f))
    drawLine(Color(0xFFFFF4D8), Offset(39f, 50f), Offset(39f, 69f), 2f)
    drawLine(Color(0xFFFFF4D8), Offset(30f, 59f), Offset(49f, 59f), 2f)
    drawRoundRect(Color(0xFF997147), Offset(58f, 51f), Size(17f, 36f), CornerRadius(5f))
    drawCircle(Color(0xFFFFD58D), 1.8f, Offset(70f, 71f))
    drawRoundRect(Color(0xFFE7D7AE), Offset(54f, 85f), Size(25f, 5f), CornerRadius(2f))
    drawRoundRect(Color(0xFF8C6647), Offset(27f, 71f), Size(26f, 8f), CornerRadius(2f))
    repeat(3) { i ->
        drawCircle(Color(0xFF8DA773), 5f, Offset(32f + i * 8f, 71f))
        drawCircle(Color(0xFFE3A78A), 2.8f, Offset(31f + i * 8f, 68f))
    }
}

private fun Decoration?.houseTint(): Color = when (this) {
    Decoration.HOUSE_MINT -> Color(0xFFB9CBA6)
    Decoration.HOUSE_PEACH -> Color(0xFFE8B99D)
    else -> Color(0xFFE6D3A9)
}

private fun Decoration.description(): String = when (this) {
    Decoration.WOOD_FENCE -> "รั้วไม้ล้อมมุมสวนให้อบอุ่น"
    Decoration.STONE_PATH -> "ทางหินก้อนน้อยเดินกลับบ้าน"
    Decoration.WARM_LAMP -> "แสงนุ่ม ๆ สำหรับเย็นสบาย"
    Decoration.GARDEN_BENCH -> "ที่นั่งพักหลังดูแลผักเสร็จ"
    Decoration.HOUSE_MINT -> "เปลี่ยนบ้านเป็นสีเขียวละมุน"
    Decoration.HOUSE_PEACH -> "เปลี่ยนบ้านเป็นสีพีชอบอุ่น"
    Decoration.FLOWER_FENCE -> "รั้วดอกไม้จากการดูแลสวน"
    Decoration.STAR_LAMP -> "ดาวดวงเล็กของชาวสวนคนเก่ง"
}

private fun Decoration.rewardDescription(): String = when (this) {
    Decoration.FLOWER_FENCE -> "เก็บเกี่ยวพืชชนิดใดก็ได้ครบ 10 ครั้ง เพื่อรับรั้วนี้ฟรี"
    Decoration.STAR_LAMP -> "เก็บเกี่ยวพืชชนิดใดก็ได้ครบ 50 ครั้ง เพื่อรับโคมนี้ฟรี"
    else -> "ของขวัญจากสมุดสะสมพืช"
}

internal val CropType.produceUnit: String
    get() = when (this) {
        CropType.TOMATO, CropType.STRAWBERRY -> "ผล"
        CropType.FLOWER -> "ดอก"
        else -> "หัว"
    }
