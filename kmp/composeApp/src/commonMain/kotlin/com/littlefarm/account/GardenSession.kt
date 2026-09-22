package com.littlefarm.account

import com.littlefarm.game.GameSaveCodec
import com.littlefarm.game.GameState
import com.littlefarm.platform.SaveStore
import com.littlefarm.platform.currentTimeMillis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import kotlin.coroutines.resume

data class PlayerAccount(val uid: String, val displayName: String, val provider: String)
enum class SyncStatus { LOCAL_ONLY, UNCONFIGURED, SYNCED, PENDING, SYNCING, CONFLICT, ERROR }
data class SaveConflict(val local: GameState, val cloud: GameState)
data class SessionSnapshot(
    val game: GameState,
    val account: PlayerAccount? = null,
    val busy: Boolean = false,
    val status: SyncStatus = SyncStatus.LOCAL_ONLY,
    val message: String? = null,
    val conflict: SaveConflict? = null,
    val loadWarning: Boolean = false,
    val saveFailed: Boolean = false,
    val providers: List<String> = emptyList(),
    val configurationMessage: String? = null,
    val deletionPending: Boolean = false,
)

private data class CloudSave(val payload: String, val revision: Int, val game: GameState)
private data class LocalSave(
    val payload: String,
    val baseRevision: Int = 0,
    val basePayload: String? = null,
    val hasLocalProgress: Boolean = false,
    val deletionPending: Boolean = false,
) {
    val dirty: Boolean get() = payload != basePayload
}
private class AccountFailure(val code: String) : Exception(code)

/**
 * Offline-first, single active owner. Call from the UI/main dispatcher, like the native adapters.
 * Mutations are serialized with account operations. Each account's unsynced save survives logout.
 * This is personal cloud backup, NOT an authoritative economy or anti-cheat implementation.
 */
class GardenSession(
    private val guestStore: SaveStore,
    private val accountStore: AccountKeyValueStore,
    private val platform: AccountPlatform,
    private val clock: () -> Long = ::currentTimeMillis,
) {
    private var guestHasSave = false
    private var guestWarning = false
    private var guestGame = readGuest()
    private var activeLocal: LocalSave? = null
    private var conflictingCloud: CloudSave? = null
    private var restored = false
    private val availability = runCatching { Json.parseToJsonElement(platform.availability()).jsonObject }.getOrNull()
    private val providers = (availability?.get("providers") as? JsonArray)?.mapNotNull {
        (it as? JsonPrimitive)?.contentOrNull?.takeIf { id -> id in setOf("google", "apple") }
    }?.distinct().orEmpty()
    private val configurationMessage = availability?.string("message")
        ?: if (providers.isEmpty()) "ยังไม่ได้เชื่อม Firebase — บันทึกสวนในเครื่องเท่านั้น" else null
    private val mutable = MutableStateFlow(SessionSnapshot(
        game = guestGame, loadWarning = guestWarning, providers = providers,
        configurationMessage = configurationMessage,
        status = if (providers.isEmpty()) SyncStatus.UNCONFIGURED else SyncStatus.LOCAL_ONLY,
    ))
    val snapshot: StateFlow<SessionSnapshot> = mutable.asStateFlow()

    fun clearMessage() { mutable.value = mutable.value.copy(message = null) }

    /** Returns false when blocked or storage failed; never announces a successful save in that case. */
    fun save(updated: GameState): Boolean {
        val game = if (updated.startedAtMillis == null) updated.copy(startedAtMillis = clock().coerceAtLeast(0)) else updated
        val current = mutable.value
        if (current.busy || current.conflict != null || current.deletionPending) return false
        if (current.account != null && activeLocal == null) {
            mutable.value = current.copy(message = "ยังโหลดสวนของบัญชีไม่สำเร็จ กรุณาออกจากบัญชีและตรวจเซฟก่อนเล่นต่อ")
            return false
        }
        val payload = GameSaveCodec.encode(game)
        return try {
            validatePayload(payload)
            val account = current.account
            if (account == null) {
                // Preserve malformed legacy data before a new game can overwrite it.
                if (guestWarning) guestStore.read()?.let { accountStore.write("recovery_guest_v1", it) }
                guestStore.write(payload)
                guestGame = game
                guestHasSave = true
                guestWarning = false
            } else {
                val next = (activeLocal ?: throw AccountFailure("invalid_data"))
                    .copy(payload = payload, hasLocalProgress = true)
                writeLocal(account.uid, next)
                activeLocal = next
            }
            mutable.value = current.copy(game = game, loadWarning = false, saveFailed = false,
                status = if (account == null) guestStatus() else if (activeLocal?.dirty == true) SyncStatus.PENDING else SyncStatus.SYNCED)
            true
        } catch (_: Exception) {
            // Keep the in-memory change so a storage retry does not lose the latest move.
            if (current.account == null) guestGame = game
            else activeLocal = activeLocal?.copy(payload = payload, hasLocalProgress = true)
            mutable.value = current.copy(game = game, saveFailed = true, status = SyncStatus.ERROR,
                message = "บันทึกในเครื่องไม่สำเร็จ กรุณาลองบันทึกอีกครั้งก่อนออกจากเกม")
            false
        }
    }

    suspend fun restoreAccount() {
        if (restored) return
        restored = true
        if (providers.isEmpty()) return
        operation {
            val user = readUser(call("restore")) ?: return@operation
            activate(user, importGuest = false)
            if (!mutable.value.deletionPending) synchronize()
        }
    }

    suspend fun signIn(provider: String) {
        if (mutable.value.account != null) return
        if (provider !in providers) {
            mutable.value = mutable.value.copy(message = configurationMessage ?: "ยังไม่ได้เปิดผู้ให้บริการนี้")
            return
        }
        if (mutable.value.saveFailed || mutable.value.loadWarning) {
            mutable.value = mutable.value.copy(message = "กรุณาบันทึกหรือกู้คืนสวนในเครื่องให้เรียบร้อยก่อนเชื่อมบัญชี")
            return
        }
        operation {
            // Recover an SDK login that completed after a timeout or a local activation failure.
            val user = readUser(call("restore"))
                ?: readUser(call("signIn", buildJsonObject { put("provider", provider) }))
                ?: throw AccountFailure("unauthenticated")
            activate(user, importGuest = true)
            if (!mutable.value.deletionPending) synchronize()
        }
    }

    suspend fun sync() {
        if (mutable.value.account == null || mutable.value.conflict != null || mutable.value.deletionPending) return
        operation { synchronize() }
    }

    suspend fun chooseLocal() {
        val expected = conflictingCloud ?: return
        operation {
            try { upload(expected.revision) }
            catch (e: AccountFailure) {
                if (e.code != "conflict") throw e
                synchronize()
                mutable.value = mutable.value.copy(message = "สวนบนคลาวด์เปลี่ยนอีกครั้ง กรุณาตรวจและเลือกใหม่")
            }
        }
    }

    suspend fun chooseCloud() {
        val expected = conflictingCloud ?: return
        operation {
            val current = fetchCloud() ?: throw AccountFailure("remote_missing")
            if (current.revision != expected.revision || current.payload != expected.payload) {
                showConflict(current)
                mutable.value = mutable.value.copy(message = "สวนบนคลาวด์เปลี่ยนอีกครั้ง กรุณาตรวจและเลือกใหม่")
            } else acceptCloud(current, "เลือกสวนจากคลาวด์แล้ว")
        }
    }

    suspend fun signOut() {
        val account = mutable.value.account ?: return
        if (mutable.value.saveFailed && activeLocal != null) {
            mutable.value = mutable.value.copy(message = "ยังบันทึกในเครื่องไม่สำเร็จ กรุณาลองบันทึกก่อนออกจากบัญชี")
            return
        }
        operation {
            try { call("signOut", ownerPayload(account.uid)) }
            catch (e: AccountFailure) {
                if (e.code != "unauthenticated") throw e
                val native = readUser(call("restore"))
                if (native?.uid == account.uid) throw e
                // Detach the old local owner; never sign out a different SDK user behind their back.
                returnToGuest("บัญชีบนอุปกรณ์เปลี่ยนแล้ว กลับสู่ Guest โดยเก็บสวนเดิมแยกไว้")
                return@operation
            }
            returnToGuest("ออกจากบัญชีแล้ว สวนของบัญชีนี้ยังเก็บแยกไว้ในเครื่อง")
        }
    }

    /** UI must ask for destructive confirmation first. Native side performs fresh reauthentication. */
    suspend fun deleteAccount() {
        val account = mutable.value.account ?: return
        operation {
            val local = activeLocal ?: throw AccountFailure("invalid_data")
            val alreadyPending = local.deletionPending
            setDeletionPending(true)
            try {
                call("deleteAccount", ownerPayload(account.uid))
            } catch (e: AccountFailure) {
                // These codes mean native reauthentication stopped BEFORE a destructive operation.
                if (!alreadyPending && e.code in setOf("cancelled", "reauth_required", "unavailable")) setDeletionPending(false)
                throw e
            }
            // Remote deletion is confirmed: a local cleanup error must not strand a deleted identity.
            val localRemoved = runCatching { accountStore.remove(localKey(account.uid)) }.isSuccess
            val markerRemoved = if (localRemoved) runCatching { accountStore.remove(deletionKey(account.uid)) }.isSuccess else false
            returnToGuest(if (localRemoved && markerRemoved)
                "ลบบัญชีและสวนในคลาวด์แล้ว กลับสู่สวน Guest ที่เก็บแยกไว้"
            else "ลบบัญชีและสวนในคลาวด์แล้ว แต่ล้างสำเนาในเครื่องไม่ครบ กรุณาตรวจพื้นที่จัดเก็บ สำเนานี้จะไม่ถูกซิงก์กลับ")
        }
    }

    private fun readGuest(): GameState {
        val raw = try { guestStore.read() } catch (_: Exception) { guestWarning = true; null }
        val decoded = raw?.let(GameSaveCodec::decode)
        guestHasSave = raw != null && decoded != null
        guestWarning = guestWarning || (raw != null && decoded == null)
        return decoded ?: GameState.initial(clock())
    }

    private fun activate(user: PlayerAccount, importGuest: Boolean) {
        // Show the authenticated identity even if its local cache fails: logout must remain reachable.
        activeLocal = null
        conflictingCloud = null
        mutable.value = mutable.value.copy(account = user, game = GameState.initial(clock()),
            conflict = null, status = SyncStatus.ERROR, deletionPending = false, loadWarning = true)
        // Do not use email as identity. An existing account cache always owns its own progress.
        val raw = accountStore.read(localKey(user.uid))
        val record = if (raw == null) LocalSave(
            payload = GameSaveCodec.encode(if (importGuest) guestGame else GameState.initial(clock())),
            hasLocalProgress = importGuest && guestHasSave,
        ) else decodeLocal(raw) ?: throw AccountFailure("invalid_data")
        val pending = record.deletionPending || accountStore.read(deletionKey(user.uid)) != null
        val next = record.copy(deletionPending = pending)
        writeLocal(user.uid, next)
        activeLocal = next
        conflictingCloud = null
        mutable.value = mutable.value.copy(account = user, game = validatePayload(next.payload),
            conflict = null, saveFailed = false, loadWarning = false, deletionPending = pending,
            status = if (pending) SyncStatus.ERROR else SyncStatus.PENDING,
            message = if (pending) errorText("deletion_incomplete") else null)
    }

    private suspend fun synchronize() {
        val local = activeLocal ?: throw AccountFailure("invalid_data")
        if (local.deletionPending) throw AccountFailure("deletion_incomplete")
        if (mutable.value.saveFailed) throw AccountFailure("local_storage")
        mutable.value = mutable.value.copy(status = SyncStatus.SYNCING)
        val remote = fetchCloud()
        if (remote == null) {
            // A previously existing cloud save disappearing is not consent to recreate it.
            if (local.baseRevision > 0) throw AccountFailure("remote_missing")
            try { upload(0) } catch (e: AccountFailure) {
                if (e.code != "conflict") throw e
                fetchCloud()?.let(::showConflict) ?: throw AccountFailure("remote_missing")
            }
        } else {
            when {
                remote.revision < local.baseRevision -> throw AccountFailure("invalid_data")
                remote.payload == local.payload -> acceptCloud(remote, "สวนตรงกับคลาวด์แล้ว")
                !local.hasLocalProgress || (!local.dirty && local.baseRevision > 0) -> acceptCloud(remote, "โหลดสวนจากคลาวด์แล้ว")
                remote.revision == local.baseRevision && remote.payload == local.basePayload -> {
                    try { upload(remote.revision) } catch (e: AccountFailure) {
                        if (e.code != "conflict") throw e
                        fetchCloud()?.let(::showConflict) ?: throw AccountFailure("remote_missing")
                    }
                }
                else -> showConflict(remote)
            }
        }
    }

    private suspend fun fetchCloud(): CloudSave? {
        val uid = mutable.value.account?.uid ?: throw AccountFailure("unauthenticated")
        val result = call("loadSave", ownerPayload(uid))
        val value = result["save"] ?: throw AccountFailure("invalid_data")
        return if (value == JsonNull) null else decodeCloud(value)
    }

    private suspend fun upload(expectedRevision: Int) {
        if (mutable.value.deletionPending) throw AccountFailure("deletion_incomplete")
        val uid = mutable.value.account?.uid ?: throw AccountFailure("unauthenticated")
        val local = activeLocal ?: throw AccountFailure("invalid_data")
        if (expectedRevision >= MAX_REVISION) throw AccountFailure("invalid_data")
        val reply = call("commitSave", buildJsonObject {
            put("uid", uid); put("expectedRevision", expectedRevision); put("payload", local.payload)
        })
        val saved = decodeCloud(reply["save"] ?: throw AccountFailure("invalid_data"))
        if (saved.revision != expectedRevision + 1 || saved.payload != local.payload) throw AccountFailure("invalid_data")
        acceptCloud(saved, "สำรองสวนบนคลาวด์แล้ว")
    }

    private fun acceptCloud(remote: CloudSave, message: String) {
        val uid = mutable.value.account?.uid ?: throw AccountFailure("unauthenticated")
        val record = LocalSave(remote.payload, remote.revision, remote.payload, hasLocalProgress = true)
        // Only advance the base after local durable persistence succeeds.
        writeLocal(uid, record)
        activeLocal = record
        conflictingCloud = null
        mutable.value = mutable.value.copy(game = remote.game, conflict = null, status = SyncStatus.SYNCED,
            saveFailed = false, message = message)
    }

    private fun showConflict(remote: CloudSave) {
        conflictingCloud = remote
        mutable.value = mutable.value.copy(conflict = SaveConflict(mutable.value.game, remote.game),
            status = SyncStatus.CONFLICT, message = "พบสวนสองเวอร์ชัน กรุณาเลือกก่อนเล่นต่อ ไม่มีการรวมเหรียญหรือเขียนทับอัตโนมัติ")
    }

    private fun setDeletionPending(value: Boolean) {
        val uid = mutable.value.account?.uid ?: throw AccountFailure("unauthenticated")
        val record = (activeLocal ?: throw AccountFailure("invalid_data")).copy(deletionPending = value)
        if (value) accountStore.write(deletionKey(uid), "pending") else accountStore.remove(deletionKey(uid))
        writeLocal(uid, record)
        activeLocal = record
        mutable.value = mutable.value.copy(deletionPending = value)
    }

    private fun returnToGuest(message: String) {
        activeLocal = null
        conflictingCloud = null
        mutable.value = mutable.value.copy(account = null, game = guestGame, conflict = null,
            status = guestStatus(), message = message, deletionPending = false,
            loadWarning = guestWarning, saveFailed = false)
    }

    private suspend fun operation(action: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true, message = null)
        try { action() }
        catch (e: TimeoutCancellationException) {
            mutable.value = mutable.value.copy(status = SyncStatus.ERROR, message = errorText("network"))
        } catch (e: CancellationException) {
            if (mutable.value.status == SyncStatus.SYNCING) {
                mutable.value = mutable.value.copy(status = SyncStatus.PENDING,
                    message = "การซิงก์ถูกพักไว้ กดซิงก์เพื่อตรวจสถานะบนคลาวด์อีกครั้ง")
            }
            throw e
        }
        catch (e: Exception) {
            val code = (e as? AccountFailure)?.code ?: "unknown"
            if (code == "deletion_incomplete" && mutable.value.account != null) {
                runCatching { setDeletionPending(true) }
                mutable.value = mutable.value.copy(deletionPending = true)
            }
            mutable.value = mutable.value.copy(
                status = if (mutable.value.conflict != null) SyncStatus.CONFLICT else SyncStatus.ERROR,
                message = errorText(code), saveFailed = mutable.value.saveFailed || code == "local_storage")
        } finally {
            mutable.value = mutable.value.copy(busy = false)
        }
    }

    private suspend fun call(operation: String, payload: JsonObject = buildJsonObject {}): JsonObject {
        val raw = withTimeout(if (operation == "signIn" || operation == "deleteAccount") 180_000L else 45_000L) {
            suspendCancellableCoroutine<String> { continuation ->
                try {
                    platform.request(operation, payload.toString()) { response ->
                        if (continuation.isActive) continuation.resume(response)
                    }
                } catch (_: Exception) {
                    if (continuation.isActive) continuation.resume("""{"ok":false,"code":"unknown"}""")
                }
            }
        }
        if (raw.length > 450_000) throw AccountFailure("invalid_data")
        val reply = runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull()
            ?: throw AccountFailure("invalid_data")
        if ((reply["ok"] as? JsonPrimitive)?.booleanOrNull != true) throw AccountFailure(reply.string("code") ?: "unknown")
        return reply
    }

    private fun readUser(reply: JsonObject): PlayerAccount? {
        val value = reply["user"] ?: throw AccountFailure("invalid_data")
        if (value == JsonNull) return null
        val user = value as? JsonObject ?: throw AccountFailure("invalid_data")
        val uid = user.string("uid")?.takeIf { it.isNotBlank() && it.length <= 128 && '/' !in it }
            ?: throw AccountFailure("invalid_data")
        val provider = user.string("provider")?.takeIf { it in setOf("google", "apple") }
            ?: throw AccountFailure("invalid_data")
        return PlayerAccount(uid, user.string("displayName")?.take(100)?.ifBlank { "ชาวสวน" } ?: "ชาวสวน", provider)
    }

    private fun decodeCloud(value: JsonElement): CloudSave {
        val obj = value as? JsonObject ?: throw AccountFailure("invalid_data")
        val payload = obj.string("payload") ?: throw AccountFailure("invalid_data")
        val revision = (obj["revision"] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
            ?.takeIf { it in 1..MAX_REVISION } ?: throw AccountFailure("invalid_data")
        return CloudSave(payload, revision, validatePayload(payload))
    }

    private fun writeLocal(uid: String, local: LocalSave) {
        try { accountStore.write(localKey(uid), encodeLocal(local)) }
        catch (_: Exception) { throw AccountFailure("local_storage") }
    }

    private fun guestStatus() = if (providers.isEmpty()) SyncStatus.UNCONFIGURED else SyncStatus.LOCAL_ONLY
    private fun localKey(uid: String) = "account_save_v1_$uid"
    private fun deletionKey(uid: String) = "account_delete_v1_$uid"
    private fun ownerPayload(uid: String) = buildJsonObject { put("uid", uid) }

    private fun encodeLocal(local: LocalSave) = buildJsonObject {
        put("schema", 1); put("payload", local.payload); put("baseRevision", local.baseRevision)
        put("basePayload", local.basePayload?.let(::JsonPrimitive) ?: JsonNull)
        put("hasLocalProgress", local.hasLocalProgress); put("deletionPending", local.deletionPending)
    }.toString()

    private fun decodeLocal(raw: String): LocalSave? = runCatching {
        if (raw.length > 850_000) throw AccountFailure("invalid_data")
        val obj = Json.parseToJsonElement(raw).jsonObject
        check((obj["schema"] as? JsonPrimitive)?.intOrNull == 1)
        val payload = obj.string("payload") ?: error("payload")
        validatePayload(payload)
        val revision = (obj["baseRevision"] as? JsonPrimitive)?.intOrNull?.takeIf { it in 0..MAX_REVISION } ?: error("revision")
        val base = obj.string("basePayload")
        base?.let(::validatePayload)
        check((revision == 0) == (base == null))
        LocalSave(payload, revision, base,
            (obj["hasLocalProgress"] as? JsonPrimitive)?.booleanOrNull ?: error("progress"),
            (obj["deletionPending"] as? JsonPrimitive)?.booleanOrNull ?: error("deletion"))
    }.getOrNull()

    private fun validatePayload(payload: String): GameState {
        if (payload.encodeToByteArray().size > 200_000) throw AccountFailure("invalid_data")
        return GameSaveCodec.decode(payload) ?: throw AccountFailure("invalid_data")
    }

    private fun JsonObject.string(key: String) = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun errorText(code: String): String = when (code) {
        "unavailable" -> configurationMessage ?: "ยังไม่ได้ตั้งค่าบริการบัญชี"
        "cancelled" -> "ยกเลิกการยืนยันบัญชีแล้ว"
        "network" -> "ยังเชื่อมต่อคลาวด์ไม่ได้ สวนในเครื่องยังอยู่ กดซิงก์อีกครั้งเมื่อพร้อม"
        "unauthenticated" -> "เซสชันบัญชีหมดอายุหรือบัญชีเปลี่ยน กรุณาออกจากบัญชีแล้วเข้าใหม่"
        "permission" -> "ยังเข้าถึงเซฟไม่ได้ กรุณาตรวจการตั้งค่า Firebase และสิทธิ์ของบัญชี"
        "reauth_required" -> "กรุณายืนยันบัญชีอีกครั้งก่อนลบบัญชี"
        "deletion_incomplete" -> "กำลังดำเนินการลบบัญชี กรุณาลองลบอีกครั้ง ระบบหยุดซิงก์เพื่อไม่ให้สวนที่ลบกลับมา"
        "remote_missing" -> "ไม่พบเซฟคลาวด์ที่เคยเชื่อมไว้ ระบบเก็บสวนในเครื่องไว้และยังไม่อัปโหลดทับ"
        "invalid_data" -> "ข้อมูลเซฟหรือบัญชีไม่ถูกต้อง ระบบยังไม่เขียนทับสวน กรุณาตรวจการตั้งค่า"
        "local_storage" -> "บันทึกในเครื่องไม่สำเร็จ กรุณาลองบันทึกใหม่ก่อนออกจากบัญชี"
        "conflict" -> "สวนบนคลาวด์เปลี่ยนระหว่างบันทึก กรุณาซิงก์และเลือกเวอร์ชันอีกครั้ง"
        else -> "ดำเนินการไม่สำเร็จ สวนในเครื่องยังอยู่ กรุณาลองอีกครั้ง"
    }

    private companion object { const val MAX_REVISION = 2_000_000_000 }
}
