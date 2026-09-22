package com.littlefarm.feedback

import com.littlefarm.account.AccountKeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/** Device preferences deliberately stay outside account/cloud/farm saves. */
data class GamePreferences(
    val musicEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val reducedMotion: Boolean = false,
)

data class FeedbackState(
    val preferences: GamePreferences,
    val available: Boolean,
    val storageWarning: String? = null,
)

interface FarmAudio {
    val available: Boolean
    fun setMusic(enabled: Boolean)
    fun playEffect(cue: String)
    fun setActive(active: Boolean)
    fun release()
}

/** Rendering previews have no audio hardware; they must not claim otherwise. */
class SilentFarmAudio : FarmAudio {
    override val available = false
    override fun setMusic(enabled: Boolean) = Unit
    override fun playEffect(cue: String) = Unit
    override fun setActive(active: Boolean) = Unit
    override fun release() = Unit
}

object GamePreferencesCodec {
    fun encode(preferences: GamePreferences): String = buildJsonObject {
        put("version", 1)
        put("musicEnabled", preferences.musicEnabled)
        put("soundEnabled", preferences.soundEnabled)
        put("reducedMotion", preferences.reducedMotion)
    }.toString()

    /** Unknown or malformed schemas cannot silently turn off accessibility preferences. */
    fun decode(value: String): GamePreferences? = runCatching {
        val obj = Json.parseToJsonElement(value).jsonObject
        val version = obj["version"] as? JsonPrimitive ?: error("Missing version")
        require(!version.isString && version.intOrNull == 1)
        fun flag(key: String): Boolean {
            val primitive = obj[key] as? JsonPrimitive ?: error("Missing preference")
            require(!primitive.isString)
            return primitive.booleanOrNull ?: error("Invalid preference")
        }
        GamePreferences(flag("musicEnabled"), flag("soundEnabled"), flag("reducedMotion"))
    }.getOrNull()
}

class GameFeedbackController(
    private val store: AccountKeyValueStore,
    private val audio: FarmAudio,
) {
    private val state = MutableStateFlow(load())
    val snapshot: StateFlow<FeedbackState> = state.asStateFlow()
    private var active = true
    private var closed = false

    init { audioCall { audio.setMusic(state.value.preferences.musicEnabled) } }

    private fun load(): FeedbackState {
        val read = runCatching { store.read(PREFERENCE_KEY) }
        val raw = read.getOrNull()
        val decoded = raw?.let(GamePreferencesCodec::decode)
        val warning = when {
            read.isFailure -> "อ่านการตั้งค่าไม่ได้ — ใช้ค่าเริ่มต้นชั่วคราว"
            raw != null && decoded == null -> "การตั้งค่าเดิมอ่านไม่ได้ — ใช้ค่าเริ่มต้น กรุณาตรวจตัวเลือกอีกครั้ง"
            else -> null
        }
        return FeedbackState(decoded ?: GamePreferences(), audio.available, warning)
    }

    /** Commit before applying, so a failed write never looks like a saved preference. */
    fun update(preferences: GamePreferences): Boolean {
        if (closed) return false
        if (runCatching { store.write(PREFERENCE_KEY, GamePreferencesCodec.encode(preferences)) }.isFailure) {
            state.value = state.value.copy(storageWarning = "บันทึกการตั้งค่าไม่ได้ กรุณาลองอีกครั้ง")
            return false
        }
        state.value = state.value.copy(preferences = preferences, storageWarning = null)
        audioCall { audio.setMusic(preferences.musicEnabled) }
        return true
    }

    fun playEffect(cue: String) {
        if (!closed && active && state.value.preferences.soundEnabled && cue in FarmSoundtrack.effectCues) {
            audioCall { audio.playEffect(cue) }
        }
    }

    fun setActive(active: Boolean) {
        if (closed) return
        this.active = active
        audioCall { audio.setActive(active) }
    }

    fun close() {
        if (closed) return
        closed = true
        active = false
        runCatching { audio.release() }
        state.value = state.value.copy(available = false)
    }

    private inline fun audioCall(block: () -> Unit) {
        val succeeded = runCatching(block).isSuccess
        state.value = state.value.copy(available = succeeded && audio.available)
    }

    companion object {
        const val PREFERENCE_KEY = "device_feedback_preferences_v1"
    }
}
