package com.littlefarm.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.littlefarm.resources.Res
import com.littlefarm.resources.stitch_welcome
import org.jetbrains.compose.resources.painterResource

/** Uses the original Stitch cover; all controls and player data remain real Compose UI. */
@Composable
internal fun WelcomeGardenScreen(
    modifier: Modifier, playerName: String?, level: Int,
    onStart: () -> Unit, onNewGame: () -> Unit, onSettings: () -> Unit, onAccount: () -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize().testTag("welcome_screen")) {
        val compact = maxHeight < 720.dp
        Image(painterResource(Res.drawable.stitch_welcome), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        // The source illustration includes an English sample logo. Cover that sample with our
        // native title treatment so the player sees one game name, not two competing logos.
        Box(Modifier.fillMaxWidth().height(if (compact) 180.dp else 210.dp).background(Brush.verticalGradient(
            0f to Color(0xFFFCE8A2), .66f to Color(0xFFFCE8A2), 1f to Color.Transparent)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, GardenColors.Cream.copy(alpha = .94f)))))
        // Only the content column scrolls; the settings control is drawn after it, never covered.
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(if (compact) 77.dp else 99.dp))
            WoodenTitle("สวนหลังเลิกงาน", large = true)
            GardenText("• Little Farm •", Modifier.background(GardenColors.Wood, RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp))
                .padding(horizontal = 22.dp, vertical = 4.dp), size = 14, color = GardenColors.Cream, bold = true)
            Spacer(Modifier.height(if (compact) 158.dp else 272.dp))
            Badge("เหมียว~ แปลงผักรอเธออยู่นะ!", symbol = FarmSymbol.LEAF, background = GardenColors.Paper.copy(alpha = .94f))
            Spacer(Modifier.height(14.dp))
            GardenButton("เล่นต่อ", Modifier.fillMaxWidth().testTag("welcome_play"), symbol = FarmSymbol.LEAF, onClick = onStart)
            Spacer(Modifier.height(7.dp))
            GardenButton("เริ่มสวนใหม่", Modifier.fillMaxWidth().testTag("welcome_new_game"), secondary = true, symbol = FarmSymbol.SEED, onClick = onNewGame)
            Spacer(Modifier.height(8.dp))
            Surface(onClick = onAccount, modifier = Modifier.fillMaxWidth().testTag("welcome_account"),
                color = GardenColors.Paper.copy(alpha = .94f), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, GardenColors.Line)) {
                GardenText(if (playerName == null) "เล่นแบบ Guest · บัญชีและเซฟ" else "${playerName.ifBlank { "ชาวสวน" }} · บัญชีและเซฟ",
                    Modifier.padding(horizontal = 10.dp, vertical = 13.dp), size = 13, bold = true,
                    color = GardenColors.LeafDark, align = TextAlign.Center)
            }
            GardenText("สวนเล็ก ๆ ในจังหวะของเธอ", Modifier.padding(top = 9.dp, bottom = 16.dp), size = 13, color = GardenColors.LeafDark)
        }
        Badge("Lv.$level", Modifier.align(Alignment.TopStart).padding(16.dp), symbol = FarmSymbol.LEAF, background = GardenColors.Paper)
        RoundAction(FarmSymbol.SETTINGS, "ตั้งค่า", Modifier.align(Alignment.TopEnd).padding(16.dp).testTag("welcome_settings"), onClick = onSettings)
    }
}
