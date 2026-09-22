package com.littlefarm.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.littlefarm.account.SessionSnapshot
import com.littlefarm.account.SyncStatus
import com.littlefarm.game.GameState
import com.littlefarm.ui.GardenColors as C

private fun SessionSnapshot.saveTitle(): String = when {
    deletionPending -> "บัญชีกำลังรอการลบ"
    saveFailed -> "ยังบันทึกในเครื่องไม่สำเร็จ"
    busy -> "กำลังดูแลเซฟของเรา…"
    else -> when (status) {
        SyncStatus.SYNCED -> "สำรองสวนบนคลาวด์แล้ว"
        SyncStatus.PENDING -> "เซฟในเครื่อง · รอซิงก์"
        SyncStatus.SYNCING -> "กำลังสำรองสวน…"
        SyncStatus.CONFLICT -> "พบสวนสองเวอร์ชัน"
        SyncStatus.ERROR -> "ยังยืนยันเซฟบนคลาวด์ไม่ได้"
        SyncStatus.LOCAL_ONLY, SyncStatus.UNCONFIGURED -> "เซฟในเครื่องเท่านั้น"
    }
}

@Composable
internal fun AccountStatusStrip(snapshot: SessionSnapshot, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag("account_status"), color = C.LeafLight) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            FarmIcon(FarmSymbol.SAVE, Modifier.size(16.dp))
            GardenText(snapshot.saveTitle(), Modifier.weight(1f), size = 10, color = C.LeafDark)
            GardenText("บัญชี ›", size = 10, bold = true, color = C.LeafDark)
        }
    }
}

/** All actions are supplied by the real session. This UI never manufactures an account or a sync success. */
@Composable
internal fun AccountScreen(snapshot: SessionSnapshot, onSignIn: (String) -> Unit, onSync: () -> Unit,
    onSignOut: () -> Unit, onDelete: () -> Unit, onChooseLocal: () -> Unit, onChooseCloud: () -> Unit,
    onContinue: () -> Unit, onClearMessage: () -> Unit) {
    var confirmation by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(snapshot.account?.uid) { confirmation = null }
    val account = snapshot.account
    val locked = snapshot.busy || snapshot.deletionPending
    Box(Modifier.fillMaxSize().testTag("account_screen")) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            PageHeading("บัญชีและสวนของเรา", "สวนเล็ก ๆ ที่อยากเก็บไว้ด้วยกัน")
            PaperCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(58.dp).background(C.LeafLight, CircleShape), contentAlignment = Alignment.Center) {
                        FarmIcon(FarmSymbol.LEAF, Modifier.size(39.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        GardenText(account?.displayName?.ifBlank { "ชาวสวนของเรา" } ?: "ชาวสวน Guest", size = 21, bold = true)
                        GardenText(if (account == null) "ไม่ต้องสมัคร ก็ปลูกความสุขได้" else
                            "เชื่อมผ่าน ${if (account.provider == "apple") "Apple" else if (account.provider == "google") "Google" else account.provider}",
                            size = 12, color = C.Muted)
                    }
                }
                Badge(snapshot.saveTitle(), Modifier.testTag("account_sync_status"), symbol = FarmSymbol.SAVE,
                    color = if (snapshot.status == SyncStatus.ERROR || snapshot.saveFailed) Color(0xFF984B30) else C.LeafDark,
                    background = if (snapshot.status == SyncStatus.ERROR || snapshot.saveFailed) Color(0xFFFFE4CD) else C.LeafLight)
                GardenText(if (account == null) "สวนนี้อยู่ในเครื่องนี้ การลบแอปหรือย้ายเครื่องอาจทำให้เซฟหาย เชื่อมบัญชีเมื่อระบบพร้อมเพื่อสำรองสวน"
                    else "บันทึกในเครื่องหลังทำกิจกรรม และพยายามซิงก์เมื่อมีการเปลี่ยนแปลง ตรวจว่า “สำรองสวนบนคลาวด์แล้ว” ก่อนย้ายเครื่อง",
                    size = 12, color = C.Muted)
                GardenSummary(snapshot.game)
            }

            snapshot.message?.let { message ->
                PaperCard(Modifier.fillMaxWidth().testTag("account_message"), tint = Color(0xFFFFEAD0), padding = 13.dp) {
                    GardenText(message, size = 13, color = C.Ink)
                    Surface(onClick = onClearMessage, color = Color.Transparent) {
                        GardenText("รับทราบ", Modifier.padding(vertical = 3.dp), size = 12, bold = true, color = C.Leaf)
                    }
                }
            }

            if (snapshot.deletionPending) {
                PaperCard(Modifier.fillMaxWidth().testTag("account_deletion_pending"), tint = Color(0xFFFFE4CD)) {
                    GardenText("กำลังดำเนินการลบบัญชี", size = 20, bold = true, color = Color(0xFF984B30))
                    GardenText("การลบยังไม่เสร็จ กรุณาลองลบอีกครั้ง ระหว่างนี้หยุดการเล่นและซิงก์สวนของบัญชีนี้ไว้ เพื่อไม่สร้างข้อมูลที่กำลังลบกลับขึ้นมา", size = 13)
                    GardenButton("ดำเนินการลบบัญชีต่อ", Modifier.fillMaxWidth().testTag("account_delete_retry"), enabled = !snapshot.busy,
                        secondary = true, onClick = onDelete)
                }
            } else if (snapshot.conflict != null) {
                val conflict = snapshot.conflict
                PaperCard(Modifier.fillMaxWidth().testTag("account_conflict"), tint = Color(0xFFFFEAD0)) {
                    GardenText("พบสวนสองเวอร์ชัน", size = 23, bold = true, color = C.LeafDark)
                    GardenText("เลือกสวนที่จะใช้ต่อก่อนนะ เราจะไม่รวมเหรียญหรือเขียนทับสวนให้อัตโนมัติ สวนที่เลือกจะเป็นเซฟของบัญชีนี้", size = 13)
                    SaveChoice("สวนในเครื่อง", conflict.local, "เลือกสวนในเครื่อง", "account_choose_local", !locked) { confirmation = "local" }
                    SaveChoice("สวนบนคลาวด์", conflict.cloud, "เลือกสวนบนคลาวด์", "account_choose_cloud", !locked) { confirmation = "cloud" }
                }
            } else if (account == null) {
                PaperCard(Modifier.fillMaxWidth()) {
                    GardenText("พาสวนไปด้วยทุกที่", size = 22, bold = true, color = C.LeafDark)
                    GardenText("เชื่อมบัญชีเพื่อสำรองสวน แล้วใช้บัญชีเดิมเล่นต่อบนอีกเครื่อง หากมีสวนบนบัญชีแล้ว เราจะให้เลือกก่อนเสมอ", size = 13, color = C.Muted)
                    if (snapshot.configurationMessage != null || snapshot.providers.isEmpty()) {
                        Column(Modifier.fillMaxWidth().background(C.LeafLight, RoundedCornerShape(17.dp)).padding(12.dp)
                            .testTag("account_unconfigured"), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            GardenText(if (snapshot.providers.isEmpty()) "ยังไม่ได้เชื่อม Firebase" else "ตรวจความพร้อมของบัญชี", size = 15, bold = true, color = C.LeafDark)
                            GardenText(snapshot.configurationMessage ?: "กำลังเตรียมระบบบัญชี ปุ่มเข้าสู่ระบบจะพร้อมเมื่อเชื่อม Firebase และตั้งค่าผู้ให้บริการแล้ว",
                                size = 12, color = C.Leaf)
                            GardenText("ตอนนี้ยังเล่นและเซฟแบบ Guest ในเครื่องได้", size = 12, color = C.Leaf)
                        }
                    }
                    GardenButton("ดำเนินการต่อด้วย Google", Modifier.fillMaxWidth().testTag("account_google"),
                        enabled = !locked && "google" in snapshot.providers, onClick = { onSignIn("google") })
                    GardenButton("ดำเนินการต่อด้วย Apple", Modifier.fillMaxWidth().testTag("account_apple"),
                        enabled = !locked && "apple" in snapshot.providers, secondary = true, onClick = { onSignIn("apple") })
                    GardenText("บัญชี Google และ Apple ไม่ได้ใช้สวนเดียวกันโดยอัตโนมัติ เลือกใช้วิธีเดิมเมื่อเข้าสู่ระบบอีกครั้ง", size = 11, color = C.Muted)
                }
            } else {
                PaperCard(Modifier.fillMaxWidth()) {
                    GardenText("เก็บสวนไว้บนคลาวด์", size = 21, bold = true)
                    GardenText("ไม่มีอินเทอร์เน็ตก็ยังดูแลสวนได้ เมื่อกลับมาออนไลน์ให้กดซิงก์ หากอีกเครื่องมีสวนต่างกัน เราจะให้เลือกก่อน", size = 13, color = C.Muted)
                    GardenButton(if (snapshot.busy) "กำลังซิงก์…" else "ซิงก์สวนตอนนี้", Modifier.fillMaxWidth().testTag("account_sync"),
                        enabled = !locked, symbol = FarmSymbol.SAVE, onClick = onSync)
                }
            }

            GardenButton(if (account == null) "เล่นต่อแบบ Guest" else "กลับไปดูผักกัน", Modifier.fillMaxWidth().testTag("account_continue"),
                enabled = !locked && snapshot.conflict == null, symbol = FarmSymbol.LEAF, onClick = onContinue)
            if (account != null) {
                GardenButton("ออกจากบัญชี", Modifier.fillMaxWidth().testTag("account_signout"), enabled = !snapshot.busy,
                    secondary = true, onClick = { confirmation = "signout" })
                if (!snapshot.deletionPending) Surface(onClick = { confirmation = "delete" }, enabled = !snapshot.busy,
                    modifier = Modifier.fillMaxWidth().testTag("account_delete"), color = Color.Transparent,
                    shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, C.Terra)) {
                    GardenText("ลบบัญชีและเซฟบนคลาวด์", Modifier.padding(14.dp), size = 12, bold = true,
                        color = Color(0xFF984B30), align = TextAlign.Center)
                }
            }
            GardenText("เซฟนี้เป็นความคืบหน้าเกมส่วนตัว\nยังไม่มีระบบเงินซื้อจริงหรือแลกของระหว่างผู้เล่น", Modifier.fillMaxWidth(),
                size = 10, color = C.Muted, align = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
        }
        confirmation?.let { action ->
            val title = when (action) {
                "delete" -> "ลบบัญชีนี้จริงไหม?"
                "signout" -> "พักจากบัญชีนี้ไหม?"
                "local" -> "ใช้สวนในเครื่องใช่ไหม?"
                else -> "ใช้สวนบนคลาวด์ใช่ไหม?"
            }
            GardenOverlay(title, onDismiss = { confirmation = null }) {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    GardenText(when (action) {
                        "delete" -> "บัญชีนี้และเซฟบนคลาวด์จะถูกลบ ไม่สามารถย้อนกลับได้ ระบบอาจให้ยืนยันตัวตนอีกครั้ง เมื่อลบสำเร็จจะกลับไปสวน Guest ที่เก็บแยกไว้ในเครื่อง"
                        "signout" -> "จะกลับไปสวน Guest ในเครื่อง เซฟของบัญชีนี้ที่ยังไม่ได้ซิงก์จะเก็บไว้แยกต่างหากบนเครื่องนี้ แต่ยังไม่พร้อมให้โหลดบนเครื่องอื่น"
                        "local" -> "สวนบนคลาวด์ของบัญชีนี้จะถูกแทนที่ด้วยสวนในเครื่อง เหรียญและของจะไม่ถูกรวมกัน กรุณาตรวจรายละเอียดให้แน่ใจก่อน"
                        else -> "สวนที่กำลังใช้อยู่ในเครื่องสำหรับบัญชีนี้จะถูกแทนที่ด้วยสวนบนคลาวด์ ความคืบหน้าที่ต่างกันจะไม่ถูกรวมกัน"
                    }, size = 13)
                    GardenButton("กลับไปตรวจดูก่อน", Modifier.fillMaxWidth().testTag("account_confirm_cancel"), enabled = !snapshot.busy) { confirmation = null }
                    GardenButton(when (action) { "delete" -> "ยืนยันลบบัญชี"; "signout" -> "ยืนยันออกจากบัญชี"; else -> "ยืนยันใช้สวนนี้" },
                        Modifier.fillMaxWidth().testTag("account_confirm_$action"), enabled = !snapshot.busy, secondary = true) {
                        confirmation = null
                        when (action) { "delete" -> onDelete(); "signout" -> onSignOut(); "local" -> onChooseLocal(); else -> onChooseCloud() }
                    }
                }
            }
        }
    }
}

@Composable
private fun GardenSummary(game: GameState) {
    GardenText("${game.plots.size} แปลง  ·  ${game.coins} เหรียญ  ·  ${game.xp} XP\nปลูกอยู่ ${game.plots.count { it.crop != null }} แปลง  ·  ผักในกระเป๋า ${game.produce.values.sum()} ชิ้น",
        size = 12, color = C.LeafDark)
}

@Composable
private fun SaveChoice(title: String, game: GameState, button: String, tag: String, enabled: Boolean, onClick: () -> Unit) {
    PaperCard(Modifier.fillMaxWidth(), padding = 13.dp) {
        GardenText(title, size = 17, bold = true)
        GardenSummary(game)
        GardenButton(button, Modifier.fillMaxWidth().testTag(tag), enabled = enabled, secondary = true, onClick = onClick)
    }
}
