package com.littlefarm.notifications

import com.littlefarm.account.AccountKeyValueStore
import com.littlefarm.game.GameState
import com.littlefarm.game.PlotStage
import com.littlefarm.platform.currentTimeMillis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.*

/** Local OS reminders only. There is no push token, network call, or notification backend. */
interface HarvestNotificationPlatform {
    /** Replies on the UI dispatcher. permission/requestPermission return a permission string. */
    fun request(operation: String, payload: String, completion: (String) -> Unit)
}

class UnavailableHarvestNotificationPlatform : HarvestNotificationPlatform {
    override fun request(operation: String, payload: String, completion: (String) -> Unit) {
        completion("""{"ok":true,"permission":"UNAVAILABLE"}""")
    }
}

enum class HarvestPermission { UNKNOWN, AUTHORIZED, DENIED, UNAVAILABLE }
data class HarvestReminderState(
    val enabled: Boolean = false,
    val permission: HarvestPermission = HarvestPermission.UNKNOWN,
    val busy: Boolean = false,
    val scheduledCount: Int = 0,
    val message: String? = null,
)
data class HarvestReminderPlan(val fireAtMillis: Long, val cropCount: Int)

object HarvestReminderPlanner {
    /** One aggregate at the latest future crop. Already-ready crops never create an immediate alert. */
    fun plan(game: GameState, nowMillis: Long): HarvestReminderPlan? {
        val pending = game.plots.mapNotNull { plot ->
            if (plot.stage != PlotStage.GROWING || plot.crop == null) return@mapNotNull null
            val ready = plot.readyAtMillis ?: return@mapNotNull null
            if (ready < game.demoOffsetMillis) return@mapNotNull null
            (ready - game.demoOffsetMillis).takeIf { it > nowMillis }
        }
        return pending.maxOrNull()?.let { HarvestReminderPlan(it, pending.size) }
    }
}

/** Main-dispatcher controller. Device opt-in is separate from garden/account saves. */
class HarvestReminderController(
    private val store: AccountKeyValueStore,
    private val platform: HarvestNotificationPlatform,
    private val clock: () -> Long = ::currentTimeMillis,
) {
    private val mutable = MutableStateFlow(load())
    val snapshot: StateFlow<HarvestReminderState> = mutable.asStateFlow()
    private var owner: String? = null
    private var game: GameState? = null
    private var blocked = false
    private var closed = false
    private var generation = 0L
    private var permissionGeneration = 0L
    private var scheduled: HarvestReminderPlan? = null

    init {
        // Never request OS authorization during startup. Remove obsolete alerts if opt-in is absent.
        if (!mutable.value.enabled) cancelPending()
        refreshPermission()
    }

    private fun load(): HarvestReminderState {
        val raw = runCatching { store.read(PREFERENCE_KEY) }
        return when {
            raw.isFailure -> HarvestReminderState(message = "อ่านการตั้งค่าแจ้งเตือนไม่สำเร็จ จึงยังปิดไว้")
            raw.getOrNull() == "true" -> HarvestReminderState(enabled = true)
            raw.getOrNull() == null || raw.getOrNull() == "false" -> HarvestReminderState()
            else -> HarvestReminderState(message = "การตั้งค่าแจ้งเตือนเดิมอ่านไม่ได้ จึงยังปิดไว้")
        }
    }

    /** Call only from an explicit settings toggle. Only this path can ask OS permission. */
    fun setEnabled(enabled: Boolean) {
        if (closed) return
        permissionGeneration++
        val requestVersion = permissionGeneration
        if (!enabled) {
            // Cancellation remains effective even if storage is temporarily full.
            cancelPending()
            val saved = runCatching { store.write(PREFERENCE_KEY, "false") }.isSuccess
            mutable.value = mutable.value.copy(enabled = false, busy = false,
                message = if (saved) null else "ปิดการแจ้งเตือนครั้งนี้แล้ว แต่บันทึกตัวเลือกไม่สำเร็จ")
            return
        }
        mutable.value = mutable.value.copy(busy = true, message = null)
        call("requestPermission") { reply ->
            if (closed || requestVersion != permissionGeneration) return@call
            val permission = permission(reply)
            if (permission != HarvestPermission.AUTHORIZED) {
                cancelPending()
                runCatching { store.write(PREFERENCE_KEY, "false") }
                mutable.value = mutable.value.copy(enabled = false, permission = permission, busy = false,
                    message = permissionMessage(permission))
            } else if (runCatching { store.write(PREFERENCE_KEY, "true") }.isFailure) {
                cancelPending()
                mutable.value = mutable.value.copy(enabled = false, permission = permission, busy = false,
                    message = "บันทึกตัวเลือกไม่ได้ จึงยังไม่เปิดการแจ้งเตือน")
            } else {
                mutable.value = mutable.value.copy(enabled = true, permission = permission, busy = false, message = null)
                reconcile(force = true)
            }
        }
    }

    /** Refresh on native foreground entry; this reads settings and cannot open a permission prompt. */
    fun refreshPermission() {
        if (closed || mutable.value.busy) return
        val requestVersion = ++permissionGeneration
        call("permission") { reply ->
            if (closed || requestVersion != permissionGeneration) return@call
            val permission = permission(reply)
            mutable.value = mutable.value.copy(permission = permission,
                message = if (mutable.value.enabled && permission != HarvestPermission.AUTHORIZED) permissionMessage(permission) else mutable.value.message)
            if (permission != HarvestPermission.AUTHORIZED) cancelPending() else reconcile(force = true)
        }
    }

    fun updateGarden(owner: String, game: GameState?, blocked: Boolean = false) {
        if (closed) return
        val changedOwner = this.owner != owner
        if (changedOwner || blocked || game == null) cancelPending()
        this.owner = owner
        this.game = game
        this.blocked = blocked
        reconcile(force = changedOwner)
    }

    /** Clear before reset/account transitions. A subsequent safe update may schedule the new garden. */
    fun clearGarden() {
        if (closed) return
        game = null
        owner = null
        cancelPending()
    }

    /** Pending OS notifications deliberately survive a normal application shutdown. */
    fun close() { closed = true; generation++; permissionGeneration++ }

    private fun reconcile(force: Boolean = false) {
        if (closed) return
        val plan = if (mutable.value.enabled && mutable.value.permission == HarvestPermission.AUTHORIZED && !blocked)
            game?.let { HarvestReminderPlanner.plan(it, clock()) } else null
        if (!force && scheduled == plan) return
        cancelPending()
        if (plan == null) return
        val requestVersion = generation
        scheduled = plan
        call("schedule", buildJsonObject {
            put("fireAtMillis", plan.fireAtMillis); put("cropCount", plan.cropCount)
        }.toString()) { reply ->
            if (closed || requestVersion != generation) return@call
            val succeeded = (reply?.get("ok") as? JsonPrimitive)?.booleanOrNull == true
            if (!succeeded) scheduled = null
            mutable.value = mutable.value.copy(scheduledCount = if (succeeded) plan.cropCount else 0,
                message = if (succeeded) null else "ยังตั้งเวลาแจ้งเตือนไม่สำเร็จ ลองเปิดแอปใหม่เพื่อตรวจอีกครั้ง")
        }
    }

    private fun cancelPending() {
        generation++
        scheduled = null
        mutable.value = mutable.value.copy(scheduledCount = 0)
        call("cancel") { }
    }

    private fun call(operation: String, payload: String = "{}", callback: (JsonObject?) -> Unit) {
        try {
            platform.request(operation, payload) { raw ->
                callback(runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull())
            }
        } catch (_: Exception) { callback(null) }
    }

    private fun permission(reply: JsonObject?): HarvestPermission = runCatching {
        HarvestPermission.valueOf(reply?.get("permission")?.jsonPrimitive?.content ?: "UNAVAILABLE")
    }.getOrDefault(HarvestPermission.UNAVAILABLE)

    private fun permissionMessage(permission: HarvestPermission) = when (permission) {
        HarvestPermission.DENIED -> "ระบบยังไม่อนุญาตแจ้งเตือน เปิดสิทธิ์ให้เกมได้ในการตั้งค่าอุปกรณ์"
        HarvestPermission.UNAVAILABLE -> "สภาพแวดล้อมนี้ยังไม่รองรับการแจ้งเตือนบนอุปกรณ์จริง"
        else -> "ยังไม่ได้รับอนุญาตแจ้งเตือน"
    }

    companion object { const val PREFERENCE_KEY = "device_harvest_reminders_v1" }
}
