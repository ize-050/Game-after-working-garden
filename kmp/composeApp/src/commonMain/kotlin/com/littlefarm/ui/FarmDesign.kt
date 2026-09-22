package com.littlefarm.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal object GardenColors {
    val Leaf = Color(0xFF3D6B45)
    val LeafDark = Color(0xFF284D33)
    val LeafLight = Color(0xFFE5EBCF)
    val Cream = Color(0xFFFFF4D8)
    val Paper = Color(0xFFFFFBED)
    val Butter = Color(0xFFF4C867)
    val Terra = Color(0xFFDC8A62)
    val Ink = Color(0xFF453D2D)
    val Muted = Color(0xFF80745B)
    val Line = Color(0xFFE7D8B8)
    val Wood = Color(0xFF805334)
}

@Composable
internal fun GardenText(text: String, modifier: Modifier = Modifier, size: Int = 14, color: Color = GardenColors.Ink, bold: Boolean = false, align: TextAlign? = null) {
    val readableSize = size.coerceAtLeast(13)
    Text(text, modifier, color = color, fontFamily = MaterialTheme.typography.bodyLarge.fontFamily, fontSize = readableSize.sp, lineHeight = (readableSize * 1.45f).sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, textAlign = align)
}

@Composable
internal fun PaperCard(modifier: Modifier = Modifier, tint: Color = GardenColors.Paper, padding: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.shadow(3.dp, RoundedCornerShape(24.dp), spotColor = Color(0xFFB19060)),
        shape = RoundedCornerShape(24.dp), color = tint, border = BorderStroke(1.dp, GardenColors.Line)) {
        Column(Modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
internal fun GardenButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, symbol: FarmSymbol? = null, secondary: Boolean = false, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reducedMotion = LocalReducedMotion.current
    val lift by animateFloatAsState(
        targetValue = if (pressed && enabled) 0f else -4f,
        animationSpec = if (reducedMotion) snap() else tween(120),
        label = "garden-button-press",
    )
    val shape = RoundedCornerShape(18.dp)
    val face = when { !enabled -> Color(0xFFE6E1CF); secondary -> GardenColors.Butter; else -> GardenColors.Leaf }
    val base = when { !enabled -> Color(0xFFCCC6B5); secondary -> Color(0xFFC39443); else -> GardenColors.LeafDark }
    val ink = when { !enabled -> Color(0xFF898571); secondary -> GardenColors.Ink; else -> GardenColors.Paper }
    Box(modifier.heightIn(min = 52.dp).padding(bottom = 4.dp).background(base, shape)
        .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)) {
        Row(Modifier.fillMaxWidth().offset(y = lift.dp).background(Brush.verticalGradient(listOf(face, face.copy(alpha = .96f))), shape)
            .border(1.dp, if (enabled) Color.White.copy(alpha = .22f) else Color.Transparent, shape)
            .padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center) {
            symbol?.let { FarmIcon(it, Modifier.size(23.dp), ink); Spacer(Modifier.width(7.dp)) }
            GardenText(text, size = 16, color = ink, bold = true, align = TextAlign.Center)
        }
    }
}

@Composable
internal fun RoundAction(symbol: FarmSymbol, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier.size(48.dp).semantics { contentDescription = label }, shape = CircleShape, color = GardenColors.Paper,
        border = BorderStroke(1.dp, GardenColors.Line), shadowElevation = 2.dp) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            FarmIcon(symbol, Modifier.size(23.dp))
        }
    }
}

@Composable
internal fun CoinPill(coins: Int, modifier: Modifier = Modifier) {
    Row(modifier.shadow(2.dp, CircleShape).background(GardenColors.Paper, CircleShape)
        .border(1.dp, Color(0xFFE8CF8C), CircleShape).padding(start = 5.dp, end = 14.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FarmIcon(FarmSymbol.COIN, Modifier.size(29.dp))
        GardenText(coins.toString(), size = 18, bold = true)
    }
}

@Composable
internal fun Badge(text: String, modifier: Modifier = Modifier, symbol: FarmSymbol? = null, color: Color = GardenColors.Leaf, background: Color = GardenColors.LeafLight) {
    Row(modifier.background(background, CircleShape).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        symbol?.let { FarmIcon(it, Modifier.size(16.dp), color) }
        GardenText(text, size = 11, color = color, bold = true)
    }
}

@Composable
internal fun WoodenTitle(title: String, subtitle: String? = null, modifier: Modifier = Modifier, large: Boolean = false) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.padding(horizontal = 12.dp).shadow(5.dp, RoundedCornerShape(15.dp))
            .background(Color(0xFF5F3E2B), RoundedCornerShape(15.dp)).padding(bottom = 4.dp)) {
            Row(Modifier.background(Brush.verticalGradient(listOf(Color(0xFF9B714C), GardenColors.Wood)), RoundedCornerShape(15.dp))
                .border(1.dp, Color(0xFFB98E5C), RoundedCornerShape(15.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Box(Modifier.size(5.dp).background(Color(0xFFE0B975), CircleShape))
                GardenText(title, size = if (large) 27 else 22, color = GardenColors.Cream, bold = true, align = TextAlign.Center)
                Box(Modifier.size(5.dp).background(Color(0xFFE0B975), CircleShape))
            }
        }
        subtitle?.let {
            GardenText(it, Modifier.padding(top = 8.dp).background(GardenColors.Paper.copy(alpha = .94f), CircleShape)
                .padding(horizontal = 13.dp, vertical = 5.dp), size = 12, color = GardenColors.Leaf)
        }
    }
}

@Composable
internal fun PageHeading(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(top = 5.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        GardenText(title, size = 27, bold = true, color = GardenColors.LeafDark, align = TextAlign.Center)
        GardenText(subtitle, Modifier.padding(top = 3.dp), size = 13, color = GardenColors.Muted, align = TextAlign.Center)
    }
}

@Composable
internal fun SectionLabel(title: String, trailing: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        GardenText(title, Modifier.weight(1f), size = 18, bold = true)
        trailing?.let { GardenText(it, size = 12, color = GardenColors.Muted) }
    }
}

@Composable
internal fun GentleNote(text: String, symbol: FarmSymbol = FarmSymbol.LEAF) {
    Row(Modifier.fillMaxWidth().background(GardenColors.LeafLight.copy(alpha = .7f), RoundedCornerShape(18.dp)).padding(13.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
        FarmIcon(symbol, Modifier.size(26.dp))
        GardenText(text, size = 12, color = GardenColors.Leaf)
    }
}

@Composable
internal fun GardenOverlay(title: String, onDismiss: () -> Unit, bottomSheet: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xFF253821).copy(alpha = .56f))
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
        .windowInsetsPadding(WindowInsets.safeDrawing).padding(if (bottomSheet) 12.dp else 22.dp),
        contentAlignment = if (bottomSheet) Alignment.BottomCenter else Alignment.Center) {
        PaperCard(Modifier.widthIn(max = 390.dp).fillMaxWidth().border(3.dp, GardenColors.Wood, RoundedCornerShape(24.dp)).semantics { paneTitle = title }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}), padding = 19.dp) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                GardenText(title, Modifier.weight(1f), size = 22, bold = true, color = GardenColors.LeafDark)
                RoundAction(FarmSymbol.CLOSE, "ปิด", onClick = onDismiss)
            }
            content()
        }
    }
}
