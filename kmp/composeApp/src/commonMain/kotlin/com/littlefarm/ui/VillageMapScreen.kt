package com.littlefarm.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.littlefarm.resources.Res
import com.littlefarm.resources.stitch_village
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun VillageMapScreen(onFarm: () -> Unit, onShop: () -> Unit, onMarket: () -> Unit,
    onOrders: () -> Unit, onUpgrades: () -> Unit) {
    Column(Modifier.fillMaxSize().testTag("village_screen").background(GardenColors.Cream).verticalScroll(rememberScrollState())) {
        // Fixed image ratio keeps each hit region anchored to its actual building on every width.
        BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(286f / 512f)) {
            Image(painterResource(Res.drawable.stitch_village), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            Surface(Modifier.align(Alignment.TopCenter).padding(top = 12.dp), shape = RoundedCornerShape(18.dp),
                color = GardenColors.Paper.copy(alpha = .94f), border = BorderStroke(1.dp, GardenColors.Line)) {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    GardenText("หมู่บ้านของเรา", size = 24, bold = true, color = GardenColors.LeafDark)
                    GardenText("แตะร้านบนแผนที่เพื่อแวะเข้าไป", size = 13, color = GardenColors.Leaf)
                }
            }
            @Composable fun place(tag: String, label: String, icon: FarmSymbol, x: Float, y: Float,
                w: Float, h: Float, topLabel: Boolean = false, action: () -> Unit) {
                Box(Modifier.offset(x = maxWidth * x, y = maxHeight * y).width(maxWidth * w).height(maxHeight * h)
                    .testTag(tag).semantics { contentDescription = "เข้าหา$label" }
                    .clickable(role = Role.Button, onClick = action)) {
                    Surface(Modifier.align(if (topLabel) Alignment.TopCenter else Alignment.BottomCenter).fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp), color = GardenColors.Wood,
                        border = BorderStroke(2.dp, Color(0xFFE2BD7C)), shadowElevation = 3.dp) {
                        Column(Modifier.padding(horizontal = 4.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            FarmIcon(icon, Modifier.size(21.dp), GardenColors.Cream)
                            GardenText(label, size = 13, color = GardenColors.Cream, bold = true, align = TextAlign.Center)
                        }
                    }
                }
            }
            place("village_shop", "ร้านเมล็ดป้าพร", FarmSymbol.SEED, .025f, .255f, .33f, .185f, action = onShop)
            place("village_market", "ตลาดผักสด", FarmSymbol.MARKET, .37f, .365f, .29f, .19f, action = onMarket)
            place("village_orders", "กระดานงาน", FarmSymbol.ORDERS, .68f, .295f, .30f, .15f, true, onOrders)
            place("village_upgrades", "ร้านช่างไม้", FarmSymbol.HAMMER, .68f, .465f, .30f, .19f, action = onUpgrades)
            place("village_farm", "กลับฟาร์ม", FarmSymbol.FARM, .035f, .57f, .35f, .19f, action = onFarm)
            GardenCat(Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 24.dp).size(60.dp, 45.dp))
        }
        GardenButton("กลับสวนของเรา", Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), symbol = FarmSymbol.FARM, onClick = onFarm)
    }
}
