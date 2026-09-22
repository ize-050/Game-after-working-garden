package com.littlefarm.platformfeedback

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.littlefarm.account.AccountKeyValueStore
import com.littlefarm.feedback.FarmAudio
import com.littlefarm.feedback.FarmSoundtrack
import com.littlefarm.feedback.GameFeedbackController
import java.io.File

/** Device preferences are separate from farm resets, account logout and cloud sync. */
private class AndroidFeedbackStore(context: Context) : AccountKeyValueStore {
    private val preferences = context.getSharedPreferences("little_farm_feedback_v1", Context.MODE_PRIVATE)
    override fun read(key: String): String? = preferences.getString(key, null)
    override fun write(key: String, value: String) {
        check(preferences.edit().putString(key, value).commit()) { "Unable to save feedback preferences" }
    }
    override fun remove(key: String) {
        check(preferences.edit().remove(key).commit()) { "Unable to remove feedback preferences" }
    }
}

fun createAndroidFeedback(context: Context): GameFeedbackController = GameFeedbackController(
    AndroidFeedbackStore(context.applicationContext),
    AndroidFarmAudio(context.applicationContext),
)

/** Foreground-only audio. No service, microphone permission, or background playback. */
private class AndroidFarmAudio(context: Context) : FarmAudio {
    private val files = mutableListOf<File>()
    private val players = mutableMapOf<String, MediaPlayer>()
    private var healthy = true
    private var active = false
    private var musicEnabled = true
    private var released = false
    override val available: Boolean get() = healthy && !released

    init {
        runCatching {
            fun prepare(key: String, bytes: ByteArray, volume: Float): MediaPlayer {
                val file = File.createTempFile("little_farm_audio_", ".wav", context.cacheDir)
                files += file
                file.writeBytes(bytes)
                val player = MediaPlayer()
                players[key] = player // Retain/release even if prepare fails.
                player.setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                player.setDataSource(file.absolutePath)
                player.setVolume(volume, volume)
                player.isLooping = key == MUSIC
                player.setOnErrorListener { _, _, _ -> healthy = false; pauseAll(); true }
                player.prepare()
                return player
            }
            prepare(MUSIC, FarmSoundtrack.musicWav(), 0.30f)
            FarmSoundtrack.effectCues.forEach { prepare(it, FarmSoundtrack.effectWav(it), 0.60f) }
        }.onFailure { healthy = false; pauseAll() }
    }

    override fun setMusic(enabled: Boolean) {
        musicEnabled = enabled
        attempt {
            val music = players[MUSIC] ?: return@attempt
            if (active && enabled) music.start() else if (music.isPlaying) music.pause()
        }
    }

    override fun playEffect(cue: String) {
        if (!active) return
        attempt {
            players[cue]?.apply { seekTo(0); start() }
        }
    }

    override fun setActive(active: Boolean) {
        this.active = active
        if (!active) pauseAll() else setMusic(musicEnabled)
    }

    private fun pauseAll() {
        players.values.forEach { player -> runCatching { if (player.isPlaying) player.pause() } }
    }

    private inline fun attempt(block: () -> Unit) {
        if (!available) return
        runCatching(block).onFailure { healthy = false; pauseAll() }
    }

    override fun release() {
        if (released) return
        released = true
        players.values.forEach { runCatching { it.release() } }
        players.clear()
        files.forEach { runCatching { it.delete() } }
        files.clear()
    }

    companion object { private const val MUSIC = "music" }
}
