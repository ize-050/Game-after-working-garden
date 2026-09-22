@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.littlefarm.platformfeedback

import com.littlefarm.account.AccountKeyValueStore
import com.littlefarm.feedback.FarmAudio
import com.littlefarm.feedback.FarmSoundtrack
import com.littlefarm.feedback.GameFeedbackController
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryAmbient
import platform.AVFAudio.AVAudioSessionInterruptionNotification
import platform.AVFAudio.AVAudioSessionInterruptionTypeKey
import platform.AVFAudio.AVAudioSessionInterruptionOptionKey
import platform.AVFAudio.AVAudioSessionInterruptionOptionShouldResume
import platform.Foundation.NSData
import platform.Foundation.NSNumber
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSUserDefaults
import platform.Foundation.create
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationState
import platform.UIKit.UIApplicationWillResignActiveNotification
import platform.darwin.NSObjectProtocol

private class IosFeedbackStore : AccountKeyValueStore {
    private val defaults = NSUserDefaults.standardUserDefaults
    private fun namespaced(value: String) = "little_farm_feedback_v1.$value"
    override fun read(key: String): String? = defaults.stringForKey(namespaced(key))
    override fun write(key: String, value: String) { defaults.setObject(value, forKey = namespaced(key)) }
    override fun remove(key: String) { defaults.removeObjectForKey(namespaced(key)) }
}

fun createIosFeedback(): GameFeedbackController = GameFeedbackController(IosFeedbackStore(), IosFarmAudio())

/** Ambient respects the hardware silent switch and mixes with the user's own music. */
private class IosFarmAudio : FarmAudio {
    private val players = mutableMapOf<String, AVAudioPlayer>()
    private val observers = mutableListOf<NSObjectProtocol>()
    private var healthy = true
    private var released = false
    private var active = UIApplication.sharedApplication.applicationState == UIApplicationState.UIApplicationStateActive
    private var musicEnabled = true
    private var interrupted = false
    override val available: Boolean get() = healthy && !released

    init {
        runCatching {
            check(AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryAmbient, error = null))
            fun prepare(key: String, bytes: ByteArray, volume: Float) {
                val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
                val player = AVAudioPlayer(data = data, error = null)
                player.volume = volume
                player.numberOfLoops = if (key == MUSIC) -1 else 0
                check(player.prepareToPlay())
                players[key] = player
            }
            prepare(MUSIC, FarmSoundtrack.musicWav(), 0.30f)
            FarmSoundtrack.effectCues.forEach { prepare(it, FarmSoundtrack.effectWav(it), 0.60f) }
        }.onFailure { healthy = false; pauseAll() }
        val center = NSNotificationCenter.defaultCenter
        observers += center.addObserverForName(UIApplicationWillResignActiveNotification, null, NSOperationQueue.mainQueue) {
            setActive(false)
        }
        observers += center.addObserverForName(UIApplicationDidBecomeActiveNotification, null, NSOperationQueue.mainQueue) {
            setActive(true)
        }
        observers += center.addObserverForName(AVAudioSessionInterruptionNotification, null, NSOperationQueue.mainQueue) { notification ->
            val kind = (notification?.userInfo?.get(AVAudioSessionInterruptionTypeKey) as? NSNumber)?.unsignedIntegerValue
            if (kind == 1uL) {
                interrupted = true
                pauseAll()
            } else if (kind == 0uL) {
                interrupted = false
                val options = (notification.userInfo?.get(AVAudioSessionInterruptionOptionKey) as? NSNumber)?.unsignedIntegerValue ?: 0uL
                if (options and AVAudioSessionInterruptionOptionShouldResume != 0uL) setMusic(musicEnabled)
            }
        }
    }

    override fun setMusic(enabled: Boolean) {
        musicEnabled = enabled
        attempt {
            val music = players[MUSIC] ?: return@attempt
            if (active && !interrupted && enabled) {
                if (!music.playing) check(music.play())
            } else music.pause()
        }
    }

    override fun playEffect(cue: String) {
        if (!active || interrupted) return
        attempt {
            players[cue]?.let { player ->
                player.currentTime = 0.0
                check(player.play())
            }
        }
    }

    override fun setActive(active: Boolean) {
        this.active = active
        if (!active) pauseAll() else setMusic(musicEnabled)
    }

    private fun pauseAll() { players.values.forEach { it.pause() } }

    private inline fun attempt(block: () -> Unit) {
        if (!available) return
        runCatching(block).onFailure { healthy = false; pauseAll() }
    }

    override fun release() {
        if (released) return
        released = true
        observers.forEach { NSNotificationCenter.defaultCenter.removeObserver(it) }
        observers.clear()
        players.values.forEach { it.stop() }
        players.clear()
    }

    companion object { private const val MUSIC = "music" }
}
