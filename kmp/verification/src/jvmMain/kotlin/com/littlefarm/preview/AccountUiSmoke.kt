@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.littlefarm.preview

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import com.littlefarm.App
import com.littlefarm.account.AccountPlatform
import com.littlefarm.account.GardenSession
import com.littlefarm.account.MemoryAccountStore
import com.littlefarm.account.SyncStatus
import com.littlefarm.game.GameSaveCodec
import com.littlefarm.game.GameState
import com.littlefarm.platform.SaveStore
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*
import kotlin.coroutines.CoroutineContext

/** Verification-only in-memory server. This does not validate OAuth, Firebase SDKs, or real cloud access. */
private class AccountUiFixturePlatform(now: Long) : AccountPlatform {
    var cloud = GameState.initial(now).copy(coins = 300)
    var revision = 1
    var signedIn = false
    var deleted = false
    val commits = mutableListOf<String>()
    override fun availability() = """{"providers":["google"]}"""
    override fun request(operation: String, payload: String, completion: (String) -> Unit) {
        val input = Json.parseToJsonElement(payload).jsonObject
        val reply = buildJsonObject {
            put("ok", true)
            when (operation) {
                "restore", "signIn" -> {
                    if (operation == "signIn") signedIn = true
                    put("user", if (!signedIn) JsonNull else buildJsonObject {
                        put("uid", "ui-fixture-player"); put("displayName", "ชาวสวนทดสอบ"); put("provider", "google")
                    })
                }
                "loadSave" -> put("save", buildJsonObject {
                    put("revision", revision); put("payload", GameSaveCodec.encode(cloud))
                })
                "commitSave" -> {
                    check(input.getValue("expectedRevision").jsonPrimitive.int == revision)
                    val save = input.getValue("payload").jsonPrimitive.content
                    cloud = checkNotNull(GameSaveCodec.decode(save)); revision++; commits += save
                    put("save", buildJsonObject { put("revision", revision); put("payload", save) })
                }
                "signOut" -> signedIn = false
                "deleteAccount" -> { deleted = true; signedIn = false }
                else -> error("Unsupported UI fixture operation: $operation")
            }
        }
        completion(reply.toString())
    }
}

private class AccountUiFixtureSave(now: Long) : SaveStore {
    private var json = GameSaveCodec.encode(GameState.initial(now))
    override fun read() = json
    override fun write(value: String) { json = value }
}

private fun SemanticsNode.accountNodes(): List<SemanticsNode> = listOf(this) + children.flatMap { it.accountNodes() }

/** Covers real shared UI + session with an explicitly fake, isolated native boundary. */
internal suspend fun accountUiSmoke(context: CoroutineContext) {
    val now = System.currentTimeMillis()
    val guest = AccountUiFixtureSave(now)
    val platform = AccountUiFixturePlatform(now)
    val session = GardenSession(guest, MemoryAccountStore(), platform, clock = { now })
    session.signIn("google")
    check(session.snapshot.value.conflict != null)
    val scene = ImageComposeScene(786, 1704, density = Density(2f), coroutineContext = context)
    var frame = 0L
    suspend fun settle() { repeat(5) { frame += 16_666_667L; scene.render(frame).close(); delay(25) } }
    fun tagged(tag: String): SemanticsNode = scene.semanticsOwners.flatMap { it.rootSemanticsNode.accountNodes() }
        .firstOrNull { it.config.getOrNull(SemanticsProperties.TestTag) == tag } ?: error("Missing account UI tag: $tag")
    suspend fun click(tag: String) {
        val node = tagged(tag)
        check(!node.config.contains(SemanticsProperties.Disabled)) { "Unexpected disabled account control: $tag" }
        check(node.config.getOrNull(SemanticsActions.OnClick)?.action?.invoke() == true) { "Not clickable: $tag" }
        settle()
        println("Account fixture clicked $tag")
    }
    try {
        scene.setContent { App(guest, initialPage = "ACCOUNT", session = session) }
        settle()
        tagged("account_conflict")
        check(tagged("account_continue").config.contains(SemanticsProperties.Disabled))
        click("nav_FARM")
        tagged("account_conflict")
        click("account_choose_cloud")
        check(session.snapshot.value.game.coins == 120 && platform.commits.isEmpty())
        click("account_confirm_cancel")
        check(session.snapshot.value.conflict != null)
        click("account_choose_local")
        check(platform.commits.isEmpty())
        click("account_confirm_local")
        check(session.snapshot.value.conflict == null && session.snapshot.value.status == SyncStatus.SYNCED)
        check(platform.cloud.coins == 120 && platform.commits.size == 1)

        check(session.save(session.snapshot.value.game.copy(coins = 130)))
        settle()
        click("account_signout")
        check(session.snapshot.value.account != null)
        click("account_confirm_signout")
        check(session.snapshot.value.account == null && session.snapshot.value.game.coins == 120)
        session.signIn("google")
        settle()
        check(session.snapshot.value.game.coins == 130 && platform.cloud.coins == 130)

        click("account_delete")
        check(!platform.deleted)
        click("account_confirm_cancel")
        check(!platform.deleted)
        click("account_delete")
        check(!platform.deleted)
        click("account_confirm_delete")
        check(platform.deleted && session.snapshot.value.account == null)
        check(session.snapshot.value.game.coins == 120)
        println("ACCOUNT UI FIXTURE PASSED: conflict lock, explicit replace consent, logout cache isolation, delete confirmation and Guest recovery. No real OAuth/Firebase was used.")
    } finally { scene.close() }
}
