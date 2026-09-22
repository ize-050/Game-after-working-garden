package com.littlefarm

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.littlefarm.account.*
import com.littlefarm.game.*
import com.littlefarm.feedback.*
import com.littlefarm.notifications.*
import com.littlefarm.platform.SaveStore
import com.littlefarm.platform.currentTimeMillis
import com.littlefarm.resources.Res
import com.littlefarm.resources.farm_backdrop
import com.littlefarm.resources.noto_sans_thai_regular
import com.littlefarm.resources.noto_sans_thai_semibold
import com.littlefarm.ui.*
import com.littlefarm.ui.GardenColors as C
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource

private enum class Page(val title: String) {
    WELCOME("สวนหลังเลิกงาน"), FARM("สวนของเรา"), BAG("กระเป๋าชาวสวน"),
    SHOP("ร้านเมล็ดป้าพร"), MARKET("ตลาดผักสด"), ORDERS("งานเพื่อนบ้าน"),
    UPGRADES("ร้านช่างไม้"), VILLAGE("หมู่บ้านของเรา"), SETTINGS("ดูแลสวนของเรา"), ACCOUNT("บัญชีและสวนของเรา"),
    DECORATIONS("แต่งสวนของเรา"), COLLECTION("สมุดสะสมพืช"), CAT("เพื่อนตัวน้อย")
}

/** Optional presentation-only entry points let QA inspect the real UI without altering saves. */
@Composable
fun App(saveStore: SaveStore, initialPage: String = "WELCOME", initialPlotIndex: Int? = null, previewHarvest: CropType? = null,
    session: GardenSession? = null, feedback: GameFeedbackController? = null, reminders: HarvestReminderController? = null) {
    val gardenSession = remember(saveStore, session) { session ?: GardenSession(saveStore, MemoryAccountStore(), UnavailableAccountPlatform()) }
    val feedbackController = remember(feedback) { feedback ?: GameFeedbackController(MemoryAccountStore(), SilentFarmAudio()) }
    val feedbackState by feedbackController.snapshot.collectAsState()
    val reminderController = remember(reminders) {
        reminders ?: HarvestReminderController(MemoryAccountStore(), UnavailableHarvestNotificationPlatform())
    }
    val reminderState by reminderController.snapshot.collectAsState()
    val snapshot by gardenSession.snapshot.collectAsState()
    val state = snapshot.game
    val loadWarning = snapshot.loadWarning
    val saveFailed = snapshot.saveFailed
    val scope = rememberCoroutineScope()
    var now by remember { mutableStateOf(currentTimeMillis()) }
    var page by remember { mutableStateOf(Page.entries.firstOrNull { it.name == initialPage.uppercase() } ?: Page.WELCOME) }
    var selectedPlot by remember { mutableStateOf(initialPlotIndex?.takeIf { it in state.plots.indices }) }
    var harvest by remember { mutableStateOf(previewHarvest) }
    var confirmReset by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var pendingSync by remember(gardenSession) { mutableStateOf<Job?>(null) }
    var navigationHistory by remember { mutableStateOf<List<Page>>(emptyList()) }
    var plantingTarget by remember { mutableStateOf<Int?>(null) }
    var motionEvent by remember { mutableStateOf<FarmMotionEvent?>(null) }
    var motionCounter by remember { mutableStateOf(0) }
    var previousOwner by remember(gardenSession) { mutableStateOf(snapshot.account?.uid) }

    fun persist(updated: GameState): Boolean {
        if (snapshot.busy || snapshot.conflict != null || snapshot.deletionPending) {
            notice = when {
                snapshot.deletionPending -> "กรุณาดำเนินการลบบัญชีต่อในหน้าบัญชี"
                snapshot.conflict != null -> "เลือกสวนที่จะใช้ในหน้าบัญชีก่อนนะ"
                else -> "กำลังดูแลเซฟ รอสักครู่นะ"
            }
            return false
        }
        val saved = gardenSession.save(updated)
        if (saved) {
            pendingSync?.cancel()
            // Only a new local write schedules a sync: status/errors never create retry loops.
            pendingSync = scope.launch { delay(2_000); gardenSession.sync() }
        }
        return saved
    }
    fun accountAction(action: suspend GardenSession.() -> Unit) {
        val syncToCancel = pendingSync
        syncToCancel?.cancel()
        pendingSync = null
        scope.launch { syncToCancel?.cancelAndJoin(); gardenSession.action() }
    }
    fun perform(cue: String, kind: String = cue, plotIndex: Int? = null, action: (GameState) -> GameResult): Boolean {
        // Resolve each tap against the newest state, including two taps before Compose recomposes.
        return when (val result = action(gardenSession.snapshot.value.game)) {
            is GameResult.Success -> persist(result.state).also { saved ->
                if (saved) {
                    notice = result.notice
                    feedbackController.playEffect(cue)
                    motionCounter = if (motionCounter == Int.MAX_VALUE) 1 else motionCounter + 1
                    motionEvent = FarmMotionEvent(motionCounter, kind, plotIndex)
                }
            }
            is GameResult.Failure -> { notice = errorText(result.error); false }
        }
    }
    fun navigate(target: Page) {
        if ((snapshot.conflict != null || snapshot.deletionPending) && target != Page.ACCOUNT) {
            notice = "จัดการเซฟในหน้าบัญชีก่อนกลับไปปลูกผักนะ"
            page = Page.ACCOUNT
        } else if (target != page) {
            navigationHistory = (navigationHistory + page).takeLast(12)
            page = target
        }
    }
    fun returnToPlantingPlot() {
        val index = plantingTarget
        navigate(Page.FARM)
        if (index != null && gardenSession.snapshot.value.game.plots.getOrNull(index)?.stage == PlotStage.TILLED) selectedPlot = index
        else notice = "แปลงเดิมเปลี่ยนสถานะแล้ว เลือกแปลงใหม่ได้เลย"
        plantingTarget = null
    }
    fun goBack() {
        if (snapshot.conflict != null || snapshot.deletionPending) { navigate(Page.ACCOUNT); return }
        if (page == Page.SHOP && plantingTarget != null) { returnToPlantingPlot(); return }
        page = navigationHistory.lastOrNull() ?: Page.FARM
        navigationHistory = navigationHistory.dropLast(1)
    }
    LaunchedEffect(gardenSession) { gardenSession.restoreAccount() }
    DisposableEffect(gardenSession) { onDispose { pendingSync?.cancel() } }
    DisposableEffect(feedbackController) { onDispose { if (feedback == null) feedbackController.close() } }
    DisposableEffect(reminderController) { onDispose { if (reminders == null) reminderController.close() } }
    LaunchedEffect(snapshot.account?.uid, snapshot.game, snapshot.conflict, snapshot.deletionPending,
        snapshot.loadWarning, snapshot.saveFailed, snapshot.busy) {
        reminderController.updateGarden(snapshot.account?.uid ?: "guest", snapshot.game,
            blocked = snapshot.conflict != null || snapshot.deletionPending || snapshot.loadWarning || snapshot.saveFailed || snapshot.busy)
    }
    LaunchedEffect(page) { if (page == Page.SETTINGS) reminderController.refreshPermission() }
    LaunchedEffect(snapshot.account?.uid) {
        if (previousOwner != snapshot.account?.uid) {
            selectedPlot = null; plantingTarget = null; harvest = null; motionEvent = null; navigationHistory = emptyList()
            previousOwner = snapshot.account?.uid
        }
    }
    LaunchedEffect(snapshot.conflict, snapshot.deletionPending) {
        if (snapshot.conflict != null || snapshot.deletionPending) {
            selectedPlot = null; harvest = null; confirmReset = false; page = Page.ACCOUNT
        }
    }
    LaunchedEffect(Unit) { while (true) { delay(1_000); now = currentTimeMillis() } }
    LaunchedEffect(notice) { if (notice != null) { delay(4_500); notice = null } }
    LaunchedEffect(motionEvent?.id) {
        if (motionEvent != null) { delay(650); motionEvent = null }
    }
    val effectiveNow = state.effectiveNowMillis(now)
    val thaiFont = FontFamily(Font(Res.font.noto_sans_thai_regular), Font(Res.font.noto_sans_thai_semibold, FontWeight.Bold))
    val textStyle = TextStyle(fontFamily = thaiFont, color = C.Ink, fontSize = 14.sp, lineHeight = 21.sp)
    CompositionLocalProvider(LocalReducedMotion provides feedbackState.preferences.reducedMotion, LocalFarmMotionEvent provides motionEvent) {
    MaterialTheme(
        colorScheme = lightColorScheme(primary = C.Leaf, background = C.Cream, surface = C.Paper, onSurface = C.Ink),
        typography = Typography(bodyLarge = textStyle, bodyMedium = textStyle, bodySmall = textStyle, titleLarge = textStyle,
            titleMedium = textStyle, titleSmall = textStyle, labelLarge = textStyle, labelMedium = textStyle, labelSmall = textStyle)
    ) {
        Surface(Modifier.fillMaxSize(), color = C.Cream) {
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    if (page == Page.WELCOME) {
                        WelcomeGardenScreen(Modifier.weight(1f), snapshot.account?.displayName, state.level,
                            onStart = { navigate(Page.FARM) }, onSettings = { navigate(Page.SETTINGS) }, onAccount = { navigate(Page.ACCOUNT) },
                            onNewGame = { if (!snapshot.busy && snapshot.conflict == null && !snapshot.deletionPending) confirmReset = true })
                    } else {
                        GameHeader(state, now, page, onBack = ::goBack, onSettings = { navigate(Page.SETTINGS) })
                        if (page != Page.ACCOUNT) AccountStatusStrip(snapshot) { navigate(Page.ACCOUNT) }
                        if (loadWarning || saveFailed) {
                            Row(Modifier.fillMaxWidth().background(Color(0xFFFFE3CB)).padding(horizontal = 14.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                GardenText(if (saveFailed) "บันทึกไม่สำเร็จ ความคืบหน้ายังอยู่ในหน้านี้" else "อ่านเซฟเดิมไม่ได้ กำลังใช้สวนตัวอย่างชั่วคราว",
                                    Modifier.weight(1f), size = 11, color = Color(0xFF9C442D))
                                if (saveFailed) Surface(onClick = { persist(gardenSession.snapshot.value.game) }, color = Color.Transparent) {
                                    GardenText("ลองอีกครั้ง", Modifier.padding(8.dp), size = 12, bold = true)
                                }
                            }
                        }
                        Box(Modifier.weight(1f)) {
                            when (page) {
                                Page.FARM -> FarmScreen(state, effectiveNow, onPlot = { index ->
                                    if (!snapshot.busy && snapshot.conflict == null && !snapshot.deletionPending) {
                                        if (gardenSession.snapshot.value.game.plots.getOrNull(index)?.stage == PlotStage.PLANTED) {
                                            perform("water", plotIndex = index) { GameEngine.water(it, index, currentTimeMillis()) }
                                        } else selectedPlot = index
                                    }
                                }, navigate = ::navigate,
                                    onWater = { perform("water", "waterAll") { GameEngine.waterAll(it, currentTimeMillis()) } },
                                    onDemoTime = { perform("", "demo") { GameEngine.advanceDemoTime(it) } })
                                Page.BAG -> BagScreen(state, navigate = ::navigate)
                                Page.SHOP -> SeedShopScreen(state,
                                    onBuy = { crop, quantity -> perform("coins") { GameEngine.buySeeds(it, crop, quantity) } },
                                    onRescue = { perform("plant") { GameEngine.claimRescueSeed(it) } },
                                    onMarket = { navigate(Page.MARKET) },
                                    onReturnToPlot = if (plantingTarget != null) ::returnToPlantingPlot else null)
                                Page.MARKET -> ProduceMarketScreen(state,
                                    onSell = { crop, quantity -> perform("coins") { GameEngine.sellProduce(it, crop, quantity) } },
                                    onFarm = { navigate(Page.FARM) })
                                Page.ORDERS -> VillageOrdersScreen(state, onDeliver = { perform("order") { GameEngine.fulfillOrder(it, state.completedOrders) } },
                                    onFarm = { navigate(Page.FARM) }, onShop = { navigate(Page.SHOP) })
                                Page.UPGRADES -> UpgradeShopScreen(state, onBuy = { upgrade -> perform("upgrade") { GameEngine.buyUpgrade(it, upgrade) } }, onMarket = { navigate(Page.MARKET) })
                                Page.DECORATIONS -> DecorationShopScreen(state,
                                    onBuy = { decoration -> perform("coins") { GameEngine.buyDecoration(it, decoration) } },
                                    onEquip = { decoration -> perform("upgrade") { GameEngine.equipDecoration(it, decoration) } },
                                    onUnequip = { slot -> perform("upgrade") { GameEngine.unequipDecoration(it, slot) } })
                                Page.COLLECTION -> CollectionScreen(state)
                                Page.CAT -> CatScreen(state, now,
                                    onRename = { name -> perform("plant") { GameEngine.renameCat(it, name) } },
                                    onPet = { perform("plant") { GameEngine.petCat(it, currentTimeMillis()) } })
                                Page.VILLAGE -> VillageMapScreen(onFarm = { navigate(Page.FARM) }, onShop = { navigate(Page.SHOP) },
                                    onMarket = { navigate(Page.MARKET) }, onOrders = { navigate(Page.ORDERS) }, onUpgrades = { navigate(Page.UPGRADES) },
                                    onDecorations = { navigate(Page.DECORATIONS) })
                                Page.SETTINGS -> SettingsScreen(state, snapshot, feedbackState, reminderState,
                                    onReminders = reminderController::setEnabled, onPreferences = feedbackController::update, onSave = {
                                    notice = if (persist(gardenSession.snapshot.value.game)) "บันทึกสวนในเครื่องแล้ว" else "ยังบันทึกสวนไม่ได้ ลองตรวจสถานะบัญชี"
                                }, onReset = { if (!snapshot.busy && snapshot.conflict == null && !snapshot.deletionPending) confirmReset = true },
                                    onDemoTime = { perform("", "demo") { GameEngine.advanceDemoTime(it) } }, navigate = ::navigate)
                                Page.ACCOUNT -> AccountScreen(snapshot,
                                    onSignIn = { provider -> accountAction { signIn(provider) } },
                                    onSync = { accountAction { sync() } },
                                    onSignOut = { accountAction { signOut() } },
                                    onDelete = { accountAction { deleteAccount() } },
                                    onChooseLocal = { accountAction { chooseLocal() } },
                                    onChooseCloud = { accountAction { chooseCloud() } },
                                    onContinue = { navigate(Page.FARM) }, onClearMessage = { gardenSession.clearMessage() })
                                Page.WELCOME -> Unit
                            }
                            notice?.let {
                                Surface(Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 8.dp),
                                    shape = RoundedCornerShape(16.dp), color = C.LeafDark, shadowElevation = 5.dp) {
                                    GardenText(it, Modifier.padding(horizontal = 16.dp, vertical = 11.dp), size = 12, color = C.Paper, align = TextAlign.Center)
                                }
                            }
                        }
                        BottomGardenNav(page, onNavigate = ::navigate)
                    }
                }
                selectedPlot?.let { index ->
                    state.plots.getOrNull(index)?.let { plot ->
                        PlotOverlay(index, plot, state, effectiveNow, onDismiss = { selectedPlot = null },
                            onTill = { perform("plow", "till", index) { GameEngine.till(it, index) } },
                            onPlant = { crop -> perform("plant", plotIndex = index) { GameEngine.plant(it, index, crop) } },
                            onWater = { perform("water", plotIndex = index) { GameEngine.water(it, index, currentTimeMillis()) } },
                            onHarvest = {
                                if (perform("harvest", plotIndex = index) { GameEngine.harvest(it, index, currentTimeMillis()) }) { harvest = plot.crop; selectedPlot = null }
                            }, onDemoTime = { perform("", "demo") { GameEngine.advanceDemoTime(it) } },
                            onShop = { plantingTarget = index; selectedPlot = null; navigate(Page.SHOP) })
                    }
                }
                harvest?.let { crop ->
                    HarvestOverlay(crop, state.produceCount(crop), onDismiss = { harvest = null },
                        onMarket = { harvest = null; navigate(Page.MARKET) })
                }
                if (confirmReset) GardenOverlay("เริ่มสวนใหม่ไหม?", onDismiss = { confirmReset = false }) {
                    GardenText(if (snapshot.account == null) "เซฟในเครื่องนี้จะถูกแทนที่ด้วยสวนตัวอย่าง 120 เหรียญ และ 6 แปลง ความคืบหน้าเดิมจะหายไป"
                        else "สวนของบัญชีนี้จะถูกแทนที่ด้วยสวนตัวอย่าง 120 เหรียญ และ 6 แปลง เมื่อซิงก์แล้ว เซฟบนคลาวด์จะเปลี่ยนตามด้วย", size = 14)
                    GardenButton("เก็บสวนเดิมไว้", Modifier.fillMaxWidth().testTag("reset_cancel")) { confirmReset = false }
                    GardenButton("ยืนยันเริ่มสวนใหม่", Modifier.fillMaxWidth().testTag("reset_confirm"), enabled = !snapshot.busy && snapshot.conflict == null && !snapshot.deletionPending, secondary = true) {
                        if (persist(GameState.initial(currentTimeMillis()))) {
                            selectedPlot = null; plantingTarget = null; harvest = null; confirmReset = false; page = Page.FARM
                            navigationHistory = emptyList(); motionEvent = null
                            notice = "สวนใหม่พร้อมแล้ว"
                        }
                    }
                }
                if (snapshot.busy && page != Page.ACCOUNT) {
                    Box(Modifier.fillMaxSize().background(C.LeafDark.copy(alpha = .25f))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                        .testTag("account_busy_blocker"), contentAlignment = Alignment.Center) {
                        PaperCard(Modifier.padding(28.dp)) {
                            GardenText("กำลังดูแลเซฟของเรา…", size = 20, bold = true, color = C.LeafDark)
                            GardenText("รอสักครู่ก่อนทำกิจกรรมต่อ เพื่อเก็บสวนให้ตรงกับบัญชี", size = 13, color = C.Muted)
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun GameHeader(state: GameState, now: Long, page: Page, onBack: () -> Unit, onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(C.Cream).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        if (page != Page.FARM) RoundAction(FarmSymbol.BACK, "ย้อนกลับ", Modifier.testTag("header_back"), onClick = onBack)
        Column(Modifier.weight(1f)) {
            GardenText("วันที่ ${state.dayNumber(now)} · Lv.${state.level}", Modifier.testTag("farm_day_level"), size = 13, bold = true, color = C.Leaf)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                FarmIcon(FarmSymbol.STAR, Modifier.size(14.dp))
                GardenText("${state.xpInLevel}/100 XP", Modifier.testTag("farm_xp"), size = 13, color = C.Muted)
            }
            Box(Modifier.padding(top = 3.dp).widthIn(max = 116.dp).fillMaxWidth().height(4.dp).background(C.Line, CircleShape)) {
                if (state.xpInLevel > 0) Box(Modifier.fillMaxHeight().fillMaxWidth(state.xpInLevel / 100f).background(C.Leaf, CircleShape))
            }
        }
        CoinPill(state.coins)
        RoundAction(FarmSymbol.SETTINGS, "ตั้งค่า", Modifier.testTag("header_settings"), onClick = onSettings)
    }
}

@Composable
private fun BottomGardenNav(page: Page, onNavigate: (Page) -> Unit) {
    Surface(color = C.Paper, shadowElevation = 8.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 9.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf(Triple(Page.FARM, "สวน", FarmSymbol.FARM), Triple(Page.BAG, "กระเป๋า", FarmSymbol.BAG),
                Triple(Page.VILLAGE, "หมู่บ้าน", FarmSymbol.VILLAGE), Triple(Page.ORDERS, "งาน", FarmSymbol.ORDERS)).forEach { (target, title, icon) ->
                val active = page == target || (target == Page.VILLAGE && page in listOf(Page.SHOP, Page.MARKET, Page.UPGRADES, Page.DECORATIONS)) ||
                    (target == Page.BAG && page == Page.COLLECTION) || (target == Page.FARM && page == Page.CAT)
                Column(Modifier.weight(1f).testTag("nav_${target.name}").clip(RoundedCornerShape(19.dp))
                    .background(if (active) C.LeafLight else Color.Transparent)
                    .clickable(role = Role.Tab, onClick = { onNavigate(target) }).padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    FarmIcon(icon, Modifier.size(29.dp), if (active) C.Leaf else C.Muted)
                    GardenText(title, size = 11, bold = active, color = if (active) C.LeafDark else C.Muted)
                }
            }
        }
    }
}

@Composable
private fun FarmScreen(state: GameState, now: Long, onPlot: (Int) -> Unit, navigate: (Page) -> Unit, onWater: () -> Unit, onDemoTime: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxHeight < 560.dp
        Image(painterResource(Res.drawable.farm_backdrop), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 15.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(if (compact) 35.dp else 90.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Badge("สวนเรา · ${state.plots.size} แปลง", background = C.Paper.copy(alpha = .92f))
                Spacer(Modifier.weight(1f))
                if (!compact) Box(Modifier.testTag("farm_cat_portrait").clickable(role = Role.Button) { navigate(Page.CAT) }
                    .semantics { contentDescription = "ทักทาย${state.cat.name}" }) {
                    GardenCat(Modifier.size(45.dp, 39.dp), bond = state.cat.bond)
                }
                val ready = state.plots.count { it.isReady(now) }
                if (ready > 0) Badge("พร้อมเก็บ $ready", symbol = FarmSymbol.CHECK, background = C.Butter)
            }
            if (state.equippedDecor.isNotEmpty()) {
                GardenDecorScene(state, Modifier.fillMaxWidth().height(110.dp).padding(vertical = 4.dp))
            }
            Spacer(Modifier.height(4.dp))
            state.plots.chunked(3).forEachIndexed { row, plots ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    plots.forEachIndexed { column, plot ->
                        val index = row * 3 + column
                        PlotTile(index, plot, now, Modifier.weight(1f), compact, onClick = { onPlot(index) })
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = C.Paper.copy(alpha = .96f),
                border = BorderStroke(1.dp, C.Line), shadowElevation = 3.dp) {
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp, horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    val untilled = state.plots.indexOfFirst { it.stage == PlotStage.UNTILLED }
                    val tilled = state.plots.indexOfFirst { it.stage == PlotStage.TILLED }
                    val dry = state.plots.indexOfFirst { it.stage == PlotStage.PLANTED }
                    val ready = state.plots.indexOfFirst { it.isReady(now) }
                    FarmShortcut(FarmSymbol.HAMMER, "พรวนดิน", "tool_till", untilled >= 0) { onPlot(untilled) }
                    FarmShortcut(FarmSymbol.SEED, "เมล็ด", "tool_seed", tilled >= 0) { onPlot(tilled) }
                    FarmShortcut(FarmSymbol.WATER, if (state.upgrades.largeWateringCan) "รดทุกแปลง" else "รดน้ำ", "tool_water", dry >= 0) {
                        if (state.upgrades.largeWateringCan) onWater() else onPlot(dry)
                    }
                    FarmShortcut(FarmSymbol.BAG, "เก็บผัก", "tool_harvest", ready >= 0) { onPlot(ready) }
                }
            }
            GardenText("แตะแปลง หรือเลือกเครื่องมือดูแลแปลงแรก", Modifier.padding(vertical = 5.dp)
                .background(C.Paper.copy(alpha = .94f), CircleShape).padding(horizontal = 12.dp, vertical = 4.dp), size = 12, color = C.LeafDark)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(onClick = { navigate(Page.SHOP) }, modifier = Modifier.weight(1f).testTag("farm_shop"),
                    shape = RoundedCornerShape(14.dp), color = C.Paper.copy(alpha = .96f)) {
                    GardenText("ซื้อเมล็ด", Modifier.padding(vertical = 12.dp), size = 13, bold = true, color = C.Leaf, align = TextAlign.Center)
                }
                Surface(onClick = { navigate(Page.MARKET) }, modifier = Modifier.weight(1f).testTag("farm_market"),
                    shape = RoundedCornerShape(14.dp), color = C.Paper.copy(alpha = .96f)) {
                    GardenText("ขายผัก", Modifier.padding(vertical = 12.dp), size = 13, bold = true, color = C.Leaf, align = TextAlign.Center)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 7.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(Triple(Page.DECORATIONS, "แต่งสวน", "farm_decorations"),
                    Triple(Page.COLLECTION, "สมุดพืช", "farm_collection"),
                    Triple(Page.CAT, "ทักทายแมว", "farm_cat")).forEach { (target, label, tag) ->
                    Surface(onClick = { navigate(target) }, modifier = Modifier.weight(1f).testTag(tag),
                        shape = RoundedCornerShape(14.dp), color = C.Butter.copy(alpha = .97f)) {
                        GardenText(label, Modifier.padding(vertical = 12.dp), size = 13, bold = true, color = C.LeafDark, align = TextAlign.Center)
                    }
                }
            }
            Surface(onClick = { navigate(Page.UPGRADES) }, modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp).testTag("farm_upgrades"),
                shape = RoundedCornerShape(15.dp), color = C.Wood, border = BorderStroke(1.dp, C.Butter.copy(alpha = .6f))) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FarmIcon(FarmSymbol.HAMMER, Modifier.size(24.dp), C.Cream)
                    GardenText(if (state.upgrades.expandedPlots) "ของใช้คู่สวน · ร้านช่างไม้" else "พื้นที่ใหม่ +3 แปลง · 180 เหรียญ",
                        Modifier.weight(1f), size = 13, bold = true, color = C.Cream)
                    FarmIcon(FarmSymbol.ARROW, Modifier.size(17.dp), C.Cream)
                }
            }
            Surface(onClick = onDemoTime, modifier = Modifier.testTag("farm_demo_time"), color = C.Paper.copy(alpha = .85f), shape = CircleShape) {
                Row(Modifier.padding(horizontal = 11.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    FarmIcon(FarmSymbol.CLOCK, Modifier.size(15.dp))
                    GardenText("โหมดทดสอบ · เวลา +5 นาที", size = 10, color = C.Muted)
                }
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun PlotTile(index: Int, plot: Plot, now: Long, modifier: Modifier, compact: Boolean, onClick: () -> Unit) {
    val ready = plot.isReady(now)
    Column(modifier.testTag("plot_$index").clip(RoundedCornerShape(18.dp)).clickable(role = Role.Button, onClick = onClick)
        .semantics { contentDescription = "แปลง ${index + 1} ${plot.crop?.thaiName.orEmpty()} ${plot.label(now)}" }
        .padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(if (compact) 62.dp else 82.dp), contentAlignment = Alignment.Center) {
            SoilBed(plot, now, Modifier.fillMaxSize(), plotIndex = index)
            if (ready) Box(Modifier.align(Alignment.TopEnd).size(24.dp).shadow(2.dp, CircleShape).background(C.Butter, CircleShape), contentAlignment = Alignment.Center) {
                FarmIcon(FarmSymbol.CHECK, Modifier.size(16.dp))
            }
        }
        GardenText(if (plot.crop != null) plot.crop.thaiName else "แปลง ${index + 1}", size = 12, bold = true, color = C.LeafDark)
        GardenText(plot.label(now), Modifier.background(if (ready) C.Butter else C.Paper.copy(alpha = .88f), CircleShape)
            .padding(horizontal = 4.dp, vertical = 2.dp), size = 10, color = if (ready) C.LeafDark else C.Muted)
    }
}

@Composable
private fun FarmShortcut(icon: FarmSymbol, text: String, tag: String, enabled: Boolean, onClick: () -> Unit) {
    Column(Modifier.testTag(tag).clip(RoundedCornerShape(14.dp)).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .sizeIn(minWidth = 55.dp, minHeight = 54.dp).padding(horizontal = 3.dp, vertical = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        FarmIcon(icon, Modifier.size(28.dp), if (enabled) C.Leaf else C.Muted.copy(alpha = .5f))
        GardenText(text, size = 11, bold = true, color = if (enabled) C.Leaf else C.Muted)
    }
}

@Composable
private fun ScrollPage(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(C.Cream, Color(0xFFF7EACD))))
        .verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(top = 7.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp), content = content)
}

@Composable
private fun BagScreen(state: GameState, navigate: (Page) -> Unit) {
    var showSeeds by remember { mutableStateOf(false) }
    val basketEmpty = !showSeeds && state.produce.values.all { it == 0 }
    ScrollPage {
        WoodenTitle("กระเป๋าชาวสวน", "สิ่งเล็ก ๆ ที่เก็บมาจากสวน", Modifier.align(Alignment.CenterHorizontally))
        GardenButton("เปิดสมุดสะสมพืช", Modifier.fillMaxWidth().testTag("bag_collection"), secondary = true, symbol = FarmSymbol.LEAF) {
            navigate(Page.COLLECTION)
        }
        Row(Modifier.fillMaxWidth().background(Color(0xFFE8DDBF), RoundedCornerShape(18.dp)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(false to "ผลผลิต  ${state.produce.values.sum()}", true to "เมล็ดพันธุ์  ${state.seeds.values.sum()}").forEach { (seeds, label) ->
                Surface(onClick = { showSeeds = seeds }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp),
                    color = if (showSeeds == seeds) C.Paper else Color.Transparent) {
                    GardenText(label, Modifier.padding(vertical = 11.dp), size = 13, bold = true,
                        color = if (showSeeds == seeds) C.Leaf else C.Muted, align = TextAlign.Center)
                }
            }
        }
        if (basketEmpty) PaperCard {
            FarmIcon(FarmSymbol.BAG, Modifier.size(72.dp))
            GardenText("ตะกร้ายังว่างอยู่", size = 22, bold = true)
            GardenText("ลองเก็บผักที่โตเต็มที่ แล้วค่อยนำมาขายหรือแบ่งให้เพื่อนบ้านนะ", size = 14, color = C.Muted)
        }
        else CropType.entries.chunked(2).forEach { crops ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                crops.forEach { crop ->
                    PaperCard(Modifier.weight(1f), padding = 13.dp) {
                        Box(Modifier.fillMaxWidth().height(94.dp).background(crop.cardTint(), RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
                            CropArt(crop, Modifier.size(85.dp))
                            Badge("×${if (showSeeds) state.seedCount(crop) else state.produceCount(crop)}", Modifier.align(Alignment.TopEnd).padding(5.dp), background = C.Paper)
                        }
                        GardenText(crop.thaiName, size = 17, bold = true)
                        if (showSeeds) {
                            GardenText("โต ${crop.minutes()} นาที", size = 12, color = C.Muted)
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                FarmIcon(FarmSymbol.COIN, Modifier.size(19.dp))
                                GardenText("${crop.sellPrice} / ${crop.produceUnit}", size = 12, color = C.Muted)
                            }
                        }
                    }
                }
            }
        }
        GardenButton(if (showSeeds) "เอาเมล็ดไปปลูกกัน" else if (basketEmpty) "ไปเก็บเกี่ยวกัน" else "นำผักไปขายที่ตลาด", Modifier.fillMaxWidth(), symbol = if (showSeeds) FarmSymbol.SEED else FarmSymbol.MARKET) {
            navigate(if (showSeeds || basketEmpty) Page.FARM else Page.MARKET)
        }
        GentleNote(if (showSeeds) "ปลูกแล้วรดน้ำหนึ่งครั้ง จากนั้นปล่อยให้เขาค่อย ๆ โต" else "เก็บเกี่ยวได้ผัก ขายแล้วจึงได้เหรียญ หรือเก็บไว้ส่งงานให้เพื่อนบ้านก็ได้นะ")
    }
}

@Composable
private fun SettingsScreen(state: GameState, snapshot: SessionSnapshot, feedback: FeedbackState, reminders: HarvestReminderState,
    onReminders: (Boolean) -> Unit, onPreferences: (GamePreferences) -> Boolean,
    onSave: () -> Unit, onReset: () -> Unit, onDemoTime: () -> Unit, navigate: (Page) -> Unit) {
    ScrollPage {
        WoodenTitle("ดูแลสวนของเรา", "เก็บความสุขไว้ แล้วค่อยกลับมา", Modifier.align(Alignment.CenterHorizontally))
        PaperCard {
            SectionLabel("กลับมาเมื่อพร้อม", "เตือนรวม ไม่เร่งให้เล่น")
            PreferenceToggle("เตือนเมื่อผักพร้อมเก็บ", "ขออนุญาตเฉพาะตอนเปิด และปิดได้ทุกเมื่อ", "setting_harvest_reminders",
                reminders.enabled, enabled = !reminders.busy, onChange = onReminders)
            GardenText("แจ้งครั้งเดียวเมื่อผักที่กำลังโตรอบนี้พร้อมทั้งหมด ไม่แยกเตือนทีละแปลง และผักไม่เหี่ยวแม้ยังไม่กลับมา",
                size = 13, color = C.Muted)
            if (reminders.scheduledCount > 0) Badge("รอแจ้งรวม ${reminders.scheduledCount} แปลง", symbol = FarmSymbol.CLOCK)
            reminders.message?.let { GardenText(it, Modifier.testTag("reminder_status"), size = 13, color = C.LeafDark) }
        }
        PaperCard {
            SectionLabel("เสียงและการเคลื่อนไหว", "จังหวะที่เธอชอบ")
            PreferenceToggle("ดนตรีในสวน", "ทำนองเบา ๆ หยุดเมื่อออกจากแอป", "setting_music", feedback.preferences.musicEnabled) {
                onPreferences(feedback.preferences.copy(musicEnabled = it))
            }
            PreferenceToggle("เสียงเอฟเฟกต์", "เสียงปลูก รดน้ำ เก็บเกี่ยว และซื้อขาย", "setting_sound", feedback.preferences.soundEnabled) {
                onPreferences(feedback.preferences.copy(soundEnabled = it))
            }
            PreferenceToggle("ลดการเคลื่อนไหว", "หยุดการเด้งและฉากขยับ แต่ยังเห็นสถานะครบ", "setting_reduced_motion", feedback.preferences.reducedMotion) {
                onPreferences(feedback.preferences.copy(reducedMotion = it))
            }
            if (!feedback.available) GentleNote("เสียงยังไม่พร้อมในสภาพแวดล้อมนี้ ปรับตัวเลือกได้ แต่ยังไม่ยืนยันการเล่นเสียงจริง")
            feedback.storageWarning?.let { GardenText(it, size = 13, color = C.Terra) }
            GardenText("จำการตั้งค่าในเครื่องนี้ แยกจากบัญชีและการเริ่มสวนใหม่", size = 13, color = C.Muted)
        }
        PaperCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FarmIcon(FarmSymbol.SAVE, Modifier.size(48.dp))
                Column { GardenText("บันทึกในเครื่อง", size = 21, bold = true); GardenText(if (snapshot.saveFailed) "บันทึกล่าสุดไม่สำเร็จ" else "บันทึกอัตโนมัติหลังทำกิจกรรม", size = 12, color = C.Muted) }
            }
            GardenText(if (snapshot.account == null) "สวน Guest อยู่ในอุปกรณ์นี้ เชื่อมบัญชีเพื่อสำรองและย้ายเครื่อง" else "เซฟในเครื่องแยกตามบัญชี ตรวจสถานะคลาวด์ก่อนเปลี่ยนเครื่อง", size = 13, color = C.Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Badge("${state.plots.size} แปลง", symbol = FarmSymbol.LEAF)
                Badge("${state.xp} XP", symbol = FarmSymbol.STAR)
            }
            GardenButton("บันทึกสวนตอนนี้", Modifier.fillMaxWidth().testTag("settings_save"), enabled = !snapshot.busy && snapshot.conflict == null && !snapshot.deletionPending, symbol = FarmSymbol.SAVE, onClick = onSave)
            GardenButton("บัญชีและสำรองสวน", Modifier.fillMaxWidth().testTag("settings_account"), secondary = true, symbol = FarmSymbol.SAVE) { navigate(Page.ACCOUNT) }
        }
        PaperCard {
            GardenText("จังหวะสบาย ๆ ของสวน", size = 20, bold = true)
            GardenText("ปลูก → รดน้ำหนึ่งครั้ง → รอ → เก็บเกี่ยว\nผักไม่ตาย ไม่มีพลังงาน และไม่ใช้เงินจริง", size = 13, color = C.Muted)
            GentleNote("เวลาเติบโตเดินต่อแม้ปิดแอป ไม่ต้องเฝ้าหน้าจอตลอดนะ")
        }
        PaperCard {
            SectionLabel("มุมทดลองเกม", "PROTOTYPE")
            GardenText("ข้ามเวลาเพื่อทดสอบการเติบโต ไม่เพิ่มเหรียญหรือผลผลิต", size = 12, color = C.Muted)
            GardenButton("ทดลองเวลา +5 นาที", Modifier.fillMaxWidth().testTag("settings_demo_time"), secondary = true, symbol = FarmSymbol.CLOCK, onClick = onDemoTime)
        }
        GardenButton("กลับไปเล่นต่อ", Modifier.fillMaxWidth().testTag("settings_back_game"), symbol = FarmSymbol.FARM) { navigate(Page.FARM) }
        GardenButton("กลับหน้าชื่อเกม", Modifier.fillMaxWidth(), secondary = true, symbol = FarmSymbol.FARM) { navigate(Page.WELCOME) }
        Surface(onClick = onReset, color = Color.Transparent, border = BorderStroke(1.dp, C.Terra), shape = RoundedCornerShape(18.dp)) {
            GardenText("เริ่มสวนใหม่ทั้งหมด", Modifier.fillMaxWidth().padding(14.dp), size = 13, color = Color(0xFFA45639), bold = true, align = TextAlign.Center)
        }
        GardenText("v0.2.0 · KMP / Compose Multiplatform\nสวนเติบโตไปพร้อมเรา · เล่นออฟไลน์ได้", Modifier.fillMaxWidth(), size = 13, color = C.Muted, align = TextAlign.Center)
    }
}

@Composable
private fun PreferenceToggle(title: String, description: String, tag: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f)) {
            GardenText(title, size = 16, bold = true)
            GardenText(description, size = 13, color = C.Muted)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled,
            modifier = Modifier.sizeIn(minWidth = 52.dp, minHeight = 48.dp).testTag(tag).semantics { contentDescription = title })
    }
}

@Composable
private fun PlotOverlay(index: Int, plot: Plot, state: GameState, now: Long, onDismiss: () -> Unit,
    onTill: () -> Unit, onPlant: (CropType) -> Unit, onWater: () -> Unit, onHarvest: () -> Unit, onDemoTime: () -> Unit, onShop: () -> Unit) {
    GardenOverlay(if (plot.stage == PlotStage.TILLED) "แปลง ${index + 1} · ปลูกอะไรดี?" else "แปลง ${index + 1} · ${plot.crop?.thaiName ?: "สวนเล็ก ๆ"}",
        onDismiss, bottomSheet = plot.stage == PlotStage.TILLED) {
        Column(Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (plot.stage == PlotStage.TILLED) {
                GardenText("เลือกเมล็ด 1 ซองให้แปลงนี้", size = 13, color = C.Muted)
                CropType.entries.chunked(2).forEach { crops ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        crops.forEach { crop ->
                            val enabled = state.seedCount(crop) > 0 && state.isUnlocked(crop)
                            Surface(onClick = { onPlant(crop) }, enabled = enabled, modifier = Modifier.weight(1f).testTag("seed_${crop.name}"), color = crop.cardTint(),
                                border = BorderStroke(1.dp, if (enabled) C.Line else Color(0xFFE3DFCE)), shape = RoundedCornerShape(18.dp)) {
                                Column(Modifier.padding(11.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    CropArt(crop, Modifier.size(62.dp))
                                    GardenText(crop.thaiName, size = 14, bold = true, color = if (enabled) C.Ink else C.Muted)
                                    GardenText(if (state.isUnlocked(crop)) "${crop.minutes()} นาที · มี ${state.seedCount(crop)}"
                                        else "ปลดล็อกเลเวล ${crop.unlockLevel}", size = 13, color = C.Muted)
                                    GardenText("ขายได้ ${crop.sellPrice} เหรียญ", Modifier.testTag("seed_sale_${crop.name}"), size = 13, color = C.Leaf)
                                }
                            }
                        }
                    }
                }
                GardenButton("ซื้อเมล็ดเพิ่ม", Modifier.fillMaxWidth().testTag("seed_shop"), secondary = true, symbol = FarmSymbol.SEED, onClick = onShop)
            } else {
                val growing = plot.stage == PlotStage.GROWING
                Box(Modifier.fillMaxWidth().height(if (growing) 100.dp else 155.dp).background(Color(0xFFE7EDD0), RoundedCornerShape(22.dp)), contentAlignment = Alignment.Center) {
                    SoilBed(plot, now, Modifier.size(if (growing) 160.dp else 210.dp, if (growing) 95.dp else 135.dp), plotIndex = index)
                }
                GardenText(plot.label(now), Modifier.fillMaxWidth(), size = 22, bold = true, color = C.LeafDark, align = TextAlign.Center)
                if (plot.crop != null) GrowthSteps(plot, now)
                when (plot.stage) {
                    PlotStage.UNTILLED -> {
                        GardenText("พรวนดินให้นุ่ม แล้วค่อยเลือกเมล็ดที่ชอบ ไม่เสียเหรียญ", size = 13, color = C.Muted, align = TextAlign.Center)
                        GardenButton("พรวนดินฟรี", Modifier.fillMaxWidth().testTag("action_till"), symbol = FarmSymbol.HAMMER, onClick = onTill)
                    }
                    PlotStage.PLANTED -> {
                        GardenText("น้ำหยดแรกจะเริ่มเวลาเติบโต รดครั้งเดียวก็พอ", size = 13, color = C.Muted, align = TextAlign.Center)
                        GardenButton("รดน้ำให้ผัก", Modifier.fillMaxWidth().testTag("action_water"), symbol = FarmSymbol.WATER, onClick = onWater)
                    }
                    PlotStage.GROWING -> if (plot.isReady(now)) {
                        GardenText("ผักโตเต็มที่แล้ว เก็บเข้ากระเป๋าได้เลย", size = 13, color = C.Muted, align = TextAlign.Center)
                        GardenButton("เก็บเกี่ยวกัน!", Modifier.fillMaxWidth().testTag("action_harvest"), symbol = FarmSymbol.BAG, onClick = onHarvest)
                    } else {
                        val crop = plot.crop
                        val progress = if (crop == null) 0f else (1f - plot.remainingMillis(now).toFloat() / crop.growthDurationMillis).coerceIn(0f, 1f)
                        Box(Modifier.fillMaxWidth().height(9.dp).background(C.Line, CircleShape)) {
                            if (progress > 0f) Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(C.Leaf, CircleShape))
                        }
                        GardenButton("กลับไปพักที่สวน", Modifier.fillMaxWidth(), onClick = onDismiss)
                        GardenButton("ทดสอบเวลา +5 นาที", Modifier.fillMaxWidth(), secondary = true, symbol = FarmSymbol.CLOCK, onClick = onDemoTime)
                    }
                    PlotStage.TILLED -> Unit
                }
            }
        }
    }
}

@Composable
private fun HarvestOverlay(crop: CropType, count: Int, onDismiss: () -> Unit, onMarket: () -> Unit) {
    GardenOverlay("เก็บเกี่ยวความสุข!", onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = 430.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Box(Modifier.size(138.dp).background(Brush.radialGradient(listOf(Color(0xFFFFE6A3), C.Paper)), CircleShape), contentAlignment = Alignment.Center) {
                FarmIcon(FarmSymbol.STAR, Modifier.align(Alignment.TopEnd).padding(12.dp).size(25.dp))
                FarmIcon(FarmSymbol.STAR, Modifier.align(Alignment.BottomStart).padding(15.dp).size(19.dp))
                HarvestCelebration(crop, Modifier.size(136.dp, 124.dp))
            }
            Badge("+1 ${crop.thaiName}", symbol = FarmSymbol.CHECK, background = C.Butter, color = C.LeafDark)
            Badge("+${crop.harvestXp} XP · บันทึกลงสมุดพืชแล้ว", symbol = FarmSymbol.STAR, background = C.LeafLight)
            GardenText("ความภูมิใจจากสวนเรา", size = 21, bold = true, color = C.LeafDark, align = TextAlign.Center)
            GardenText("ในกระเป๋ามี${crop.thaiName} $count ${crop.produceUnit}\nขายได้${crop.produceUnit}ละ ${crop.sellPrice} เหรียญ หรือเก็บไว้ส่งงาน", size = 13, color = C.Muted, align = TextAlign.Center)
            GardenButton("นำผักไปขาย", Modifier.fillMaxWidth(), symbol = FarmSymbol.MARKET, onClick = onMarket)
            GardenButton("กลับไปปลูกต่อ", Modifier.fillMaxWidth(), secondary = true, symbol = FarmSymbol.LEAF, onClick = onDismiss)
        }
    }
}

private fun CropType.minutes(): Long = growthDurationMillis / 60_000
private fun CropType.cardTint(): Color = when (this) {
    CropType.LETTUCE -> Color(0xFFE7EDD0); CropType.RADISH -> Color(0xFFF2E2DD)
    CropType.CARROT -> Color(0xFFF6E4CC); CropType.PUMPKIN -> Color(0xFFF6E5BB)
    CropType.TOMATO -> Color(0xFFF8DDD1); CropType.STRAWBERRY -> Color(0xFFF7DFE4)
    CropType.FLOWER -> Color(0xFFEEE1F3)
}
private fun Plot.label(now: Long): String = when (stage) {
    PlotStage.UNTILLED -> "พรวนดินกัน"
    PlotStage.TILLED -> "พร้อมปลูก"
    PlotStage.PLANTED -> "รอน้ำหยดแรก"
    PlotStage.GROWING -> if (isReady(now)) "พร้อมเก็บแล้ว!" else {
        val seconds = (remainingMillis(now) + 999) / 1_000
        "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')} นาที"
    }
}
private fun errorText(error: GameError): String = when (error) {
    GameError.NO_SEEDS -> "เมล็ดชนิดนี้หมดแล้ว แวะร้านเมล็ดกัน"
    GameError.NOT_ENOUGH_COINS -> "เหรียญยังไม่พอ ลองขายผักที่ตลาดก่อนนะ"
    GameError.CROP_NOT_READY -> "ผักยังโตไม่เต็มที่ พักแล้วค่อยกลับมาได้"
    GameError.NOT_ENOUGH_PRODUCE -> "ผลผลิตยังไม่ครบ ลองกลับไปเก็บผักก่อน"
    GameError.ORDER_ALREADY_CLAIMED -> "งานนี้ส่งเรียบร้อยแล้ว"
    GameError.ORDER_CHANGED -> "ส่งงานแล้ว ลองดูคำขอใหม่ก่อนส่งอีกครั้งนะ"
    GameError.UPGRADE_ALREADY_OWNED -> "มีอัปเกรดนี้แล้ว"
    GameError.NOTHING_TO_WATER -> "ทุกแปลงที่ปลูกมีน้ำแล้ว"
    GameError.CROP_LOCKED -> "เก็บเกี่ยวและส่งงานเพื่อเพิ่มเลเวล แล้วปลดล็อกพืชนี้นะ"
    GameError.DECORATION_ALREADY_OWNED -> "มีของแต่งชิ้นนี้แล้ว เลือกวางในสวนได้เลย"
    GameError.DECORATION_NOT_OWNED -> "ต้องซื้อหรือปลดล็อกของแต่งชิ้นนี้ก่อนนะ"
    GameError.DECORATION_REWARD_ONLY -> "ของแต่งนี้เป็นรางวัลจากสมุดสะสมพืช"
    GameError.INVALID_CAT_NAME -> "ตั้งชื่อแมว 1–${CatState.MAX_NAME_LENGTH} ตัวอักษร โดยไม่ขึ้นบรรทัดใหม่"
    GameError.CAT_NEEDS_REST -> "เพิ่งลูบหัวไป ให้เพื่อนตัวน้อยพักสักนาทีนะ ความสนิทไม่ลดลง"
    else -> "ทำรายการนี้ไม่ได้ในตอนนี้ กลับมาดูสถานะแปลงอีกครั้งนะ"
}
