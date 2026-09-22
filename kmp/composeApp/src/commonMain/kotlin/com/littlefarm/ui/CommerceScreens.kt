package com.littlefarm.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.littlefarm.game.CropType
import com.littlefarm.game.GameEngine
import com.littlefarm.game.GameState

@Composable
internal fun SeedShopScreen(
    state: GameState,
    onBuy: (CropType, Int) -> Boolean,
    onRescue: () -> Unit,
    onMarket: () -> Unit,
    onReturnToPlot: (() -> Unit)? = null,
) {
    val latestBuy by rememberUpdatedState(onBuy)
    CommercePage("shop_screen") {
        SeedShopScenery()
        if (onReturnToPlot != null) {
            GardenButton("กลับไปปลูกแปลงเดิม", Modifier.fillMaxWidth().testTag("return_to_plot"), symbol = FarmSymbol.SEED, secondary = true, onClick = onReturnToPlot)
        }
        if (GameEngine.canClaimRescueSeed(state)) {
            PaperCard(tint = GardenColors.LeafLight) {
                GardenText("เริ่มปลูกใหม่ได้เสมอนะ", size = 17, bold = true)
                GardenText("ป้าพรมีเมล็ดผักกาดให้ฟรี 1 ซอง ไม่ต้องใช้เหรียญ", size = 14)
                GardenButton("รับเมล็ดผักกาดฟรี 1 ซอง", Modifier.fillMaxWidth().testTag("claim_rescue_seed"), symbol = FarmSymbol.SEED, onClick = onRescue)
            }
        }
        CropType.entries.forEach { crop ->
            var requestedQuantity by remember(crop) { mutableStateOf(1) }
            val capacity = seedTradeQuote(state, crop, 1).maximumQuantity
            val quantity = requestedQuantity.coerceIn(1, capacity.coerceAtLeast(1))
            val quote = seedTradeQuote(state, crop, quantity)
            PaperCard(Modifier.fillMaxWidth(), padding = 15.dp) {
                CommerceCropHeader(crop, "มีเมล็ด ${state.seedCount(crop)} ซอง", "ซองละ ${crop.seedPrice} เหรียญ", seedPacket = true)
                GardenText("โต ${crop.growthDurationMillis / 60_000L} นาที · ผลผลิตขายได้ ${crop.sellPrice} เหรียญ/หัว", size = 13, color = GardenColors.Muted)
                QuantityChooser(
                    crop = crop, quantity = quantity, unit = "ซอง", prefix = "buy",
                    canDecrease = quantity > 1, canIncrease = quantity < capacity,
                    onDecrease = { requestedQuantity = quantity - 1 },
                    onIncrease = { requestedQuantity = quantity + 1 },
                )
                TradeTotal("ราคารวม", quote.totalCoins, "buy_total_${crop.name}")
                when (quote.blocker) {
                    TradeBlocker.NOT_ENOUGH_COINS -> GardenText("ขาดอีก ${quote.missingCoins} เหรียญ · ลดจำนวนหรือไปขายผักได้นะ", Modifier.testTag("buy_shortfall_${crop.name}"), size = 14, color = GardenColors.Wood)
                    TradeBlocker.INVENTORY_FULL -> GardenText("กระเป๋าเมล็ดชนิดนี้เต็มแล้ว ลองนำไปปลูกก่อนนะ", size = 14, color = GardenColors.Wood)
                    else -> Unit
                }
                GardenButton(
                    "ซื้อเมล็ด $quantity ซอง",
                    Modifier.fillMaxWidth().testTag("buy_${crop.name}").semantics {
                        contentDescription = "ซื้อเมล็ด${crop.thaiName} $quantity ซอง รวม ${quote.totalCoins} เหรียญ"
                    },
                    enabled = quote.canTrade, symbol = FarmSymbol.SEED,
                ) {
                    if (latestBuy(crop, quantity)) requestedQuantity = 1
                }
            }
        }
        GardenButton("แวะขายผักหาเหรียญเพิ่ม", Modifier.fillMaxWidth().testTag("shop_to_market"), secondary = true, symbol = FarmSymbol.MARKET, onClick = onMarket)
    }
}

@Composable
internal fun ProduceMarketScreen(
    state: GameState,
    onSell: (CropType, Int) -> Boolean,
    onFarm: () -> Unit,
) {
    val latestSell by rememberUpdatedState(onSell)
    var receipt by remember { mutableStateOf<SaleReceipt?>(null) }
    val totalProduce = state.produce.values.sumOf { it.toLong() }
    CommercePage("market_screen") {
        ProduceMarketScenery(totalProduce)
        if (totalProduce == 0L) {
            PaperCard(Modifier.fillMaxWidth(), tint = GardenColors.LeafLight) {
                FarmIcon(FarmSymbol.BAG, Modifier.size(58.dp).align(Alignment.CenterHorizontally))
                GardenText("ตะกร้ายังว่างอยู่", Modifier.fillMaxWidth(), size = 21, bold = true, align = TextAlign.Center)
                GardenText("ลองเก็บผักที่โตแล้วดู แล้วค่อยเอามาแบ่งปันกันนะ", size = 14)
                GardenButton("กลับไปเก็บผัก", Modifier.fillMaxWidth().testTag("market_empty_to_farm"), symbol = FarmSymbol.FARM, onClick = onFarm)
            }
        }
        CropType.entries.forEach { crop ->
            var requestedQuantity by remember(crop) { mutableStateOf(1) }
            val available = state.produceCount(crop).coerceAtLeast(0)
            val quantity = if (available == 0) 0 else requestedQuantity.coerceIn(1, available)
            val quote = produceTradeQuote(state, crop, quantity)
            PaperCard(Modifier.fillMaxWidth(), padding = 15.dp) {
                CommerceCropHeader(crop, "ในกระเป๋า $available หัว", "หัวละ ${crop.sellPrice} เหรียญ")
                if (available == 0) {
                    GardenText("ยังไม่มี${crop.thaiName}ในตะกร้า", size = 14, color = GardenColors.Muted)
                } else {
                    QuantityChooser(
                        crop = crop, quantity = quantity, unit = "หัว", prefix = "sell",
                        canDecrease = quantity > 1, canIncrease = quantity < available,
                        onDecrease = { requestedQuantity = quantity - 1 },
                        onIncrease = { requestedQuantity = quantity + 1 },
                    )
                    TradeTotal("รับเหรียญรวม", quote.totalCoins, "sell_total_${crop.name}")
                    GardenText("ขายแล้วเหลือ ${available - quantity} หัวในกระเป๋า", size = 13, color = GardenColors.Muted)
                }
                if (quote.blocker == TradeBlocker.WALLET_FULL) {
                    GardenText("กระเป๋าเหรียญรับไม่ไหว ลองลดจำนวนหรือใช้เหรียญก่อนนะ", Modifier.testTag("sell_wallet_full_${crop.name}"), size = 14, color = GardenColors.Wood)
                }
                GardenButton(
                    if (available == 0) "ยังไม่มีผลผลิต" else "ขาย $quantity หัว",
                    Modifier.fillMaxWidth().testTag("sell_${crop.name}").semantics {
                        contentDescription = if (available == 0) "ยังไม่มี${crop.thaiName}สำหรับขาย" else "ขาย${crop.thaiName} $quantity หัว รับ ${quote.totalCoins} เหรียญ"
                    },
                    enabled = quote.canTrade, symbol = FarmSymbol.COIN,
                ) {
                    if (latestSell(crop, quantity)) {
                        receipt = SaleReceipt(crop, quantity, quote.totalCoins)
                        requestedQuantity = 1
                    }
                }
                receipt?.takeIf { it.crop == crop }?.let { sale ->
                    MarketReceipt(sale.crop, sale.quantity, sale.earnedCoins, state.coins)
                }
            }
        }
        GardenButton("กลับไปเก็บผัก", Modifier.fillMaxWidth().testTag("market_to_farm"), secondary = true, symbol = FarmSymbol.FARM, onClick = onFarm)
    }
}

@Composable
private fun CommercePage(tag: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().testTag(tag).background(Brush.verticalGradient(listOf(GardenColors.Cream, Color(0xFFF0DFC0))))
            .verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(top = 7.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp), content = content,
    )
}

@Composable
private fun CommerceCropHeader(crop: CropType, inventory: String, price: String, seedPacket: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (seedPacket) {
            SeedPacket(crop, Modifier.width(72.dp).height(94.dp))
        } else {
            Box(Modifier.size(72.dp).background(crop.commerceTint(), RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
                CropArt(crop, Modifier.size(64.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            GardenText(crop.thaiName, size = 20, bold = true)
            GardenText(inventory, size = 13, color = GardenColors.Muted)
            GardenText(price, size = 14, bold = true, color = GardenColors.Leaf)
        }
    }
}

private data class SaleReceipt(val crop: CropType, val quantity: Int, val earnedCoins: Long)

@Composable
private fun QuantityChooser(
    crop: CropType,
    quantity: Int,
    unit: String,
    prefix: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        GardenText("เลือกจำนวน", size = 14, bold = true)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuantityButton("−", "ลดจำนวน${crop.thaiName}", "${prefix}_qty_minus_${crop.name}", canDecrease, onDecrease)
            GardenText("$quantity $unit", Modifier.weight(1f).testTag("${prefix}_quantity_${crop.name}"), size = 17, bold = true, align = TextAlign.Center)
            QuantityButton("+", "เพิ่มจำนวน${crop.thaiName}", "${prefix}_qty_plus_${crop.name}", canIncrease, onIncrease)
        }
    }
}

@Composable
private fun QuantityButton(text: String, description: String, tag: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick, enabled = enabled,
        modifier = Modifier.size(52.dp).testTag(tag).semantics { contentDescription = description },
        shape = RoundedCornerShape(16.dp),
        color = if (enabled) GardenColors.LeafLight else Color(0xFFEAE5D6),
        border = BorderStroke(1.dp, GardenColors.Line),
    ) {
        Box(contentAlignment = Alignment.Center) {
            GardenText(text, size = 24, bold = true, color = if (enabled) GardenColors.LeafDark else GardenColors.Muted)
        }
    }
}

@Composable
private fun TradeTotal(label: String, total: Long, tag: String) {
    Column(Modifier.fillMaxWidth().background(GardenColors.Cream, RoundedCornerShape(15.dp)).padding(12.dp)) {
        GardenText(label, size = 13, color = GardenColors.Muted)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            FarmIcon(FarmSymbol.COIN, Modifier.size(25.dp))
            GardenText("$total เหรียญ", Modifier.weight(1f).testTag(tag), size = 20, bold = true)
        }
    }
}

private fun CropType.commerceTint(): Color = when (this) {
    CropType.LETTUCE -> Color(0xFFE5EDD3)
    CropType.RADISH -> Color(0xFFECE4DC)
    CropType.CARROT -> Color(0xFFF8E6C9)
    CropType.PUMPKIN -> Color(0xFFF7E0BC)
}
