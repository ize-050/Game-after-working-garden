package com.littlefarm.preview

import com.littlefarm.feedback.FarmAudio
import com.littlefarm.feedback.FarmSoundtrack
import java.io.ByteArrayInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip

/** Desktop QA only: plays the same original synthesized soundtrack as the mobile adapters. */
internal class JavaSoundFarmAudio : FarmAudio {
    private var active = false
    private var musicWanted = false
    private var released = false
    private var healthy = true
    private var music: Clip? = null
    private val effects = mutableMapOf<String, Clip>()

    init {
        try {
            music = openClip(FarmSoundtrack.musicWav())
        } catch (failure: Exception) {
            healthy = false
            System.err.println("Desktop preview audio unavailable: ${failure.javaClass.simpleName}")
        }
    }

    override val available: Boolean get() = healthy && !released && music != null

    @Synchronized
    override fun setMusic(enabled: Boolean) {
        musicWanted = enabled
        audioOperation { updateMusic() }
    }

    @Synchronized
    override fun playEffect(cue: String) {
        if (!available || !active || cue !in FarmSoundtrack.effectCues) return
        audioOperation {
            val clip = effects.getOrPut(cue) { openClip(FarmSoundtrack.effectWav(cue)) }
            clip.stop()
            clip.framePosition = 0
            clip.start()
        }
    }

    @Synchronized
    override fun setActive(active: Boolean) {
        this.active = active
        audioOperation {
            if (!active) effects.values.forEach { it.stop(); it.framePosition = 0 }
            updateMusic()
        }
    }

    @Synchronized
    override fun release() {
        if (released) return
        released = true
        active = false
        closeClips()
    }

    private fun updateMusic() {
        val clip = music ?: return
        if (active && musicWanted && available) {
            if (!clip.isRunning) clip.loop(Clip.LOOP_CONTINUOUSLY)
        } else clip.stop()
    }

    private inline fun audioOperation(block: () -> Unit) {
        if (!available) return
        try {
            block()
        } catch (failure: Exception) {
            healthy = false
            closeClips()
            // The shared controller exposes audio unavailability without interrupting play.
            throw failure
        }
    }

    private fun closeClips() {
        effects.values.forEach { runCatching { it.stop(); it.close() } }
        effects.clear()
        music?.let { runCatching { it.stop(); it.close() } }
        music = null
    }

    private fun openClip(wav: ByteArray): Clip {
        val clip = AudioSystem.getClip()
        try {
            AudioSystem.getAudioInputStream(ByteArrayInputStream(wav)).use { stream -> clip.open(stream) }
            return clip
        } catch (failure: Exception) {
            clip.close()
            throw failure
        }
    }
}
