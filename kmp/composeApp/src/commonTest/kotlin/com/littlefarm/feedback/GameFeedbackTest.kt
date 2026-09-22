package com.littlefarm.feedback

import com.littlefarm.account.AccountKeyValueStore
import com.littlefarm.account.MemoryAccountStore
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameFeedbackTest {
    @Test fun defaultsEnableCozyAudioWithoutStorageWarning() {
        val audio = RecordingAudio()
        val controller = GameFeedbackController(MemoryAccountStore(), audio)
        assertEquals(GamePreferences(), controller.snapshot.value.preferences)
        assertTrue(controller.snapshot.value.available)
        assertNull(controller.snapshot.value.storageWarning)
        assertEquals(listOf(true), audio.music)
    }

    @Test fun preferencesSurviveControllerRecreationAndDoNotTouchOtherKeys() {
        val store = MemoryAccountStore()
        store.write("farm", "keep my garden")
        val first = GameFeedbackController(store, RecordingAudio())
        val preferences = GamePreferences(musicEnabled = false, soundEnabled = true, reducedMotion = true)
        assertTrue(first.update(preferences))
        first.close()
        assertEquals(preferences, GameFeedbackController(store, RecordingAudio()).snapshot.value.preferences)
        assertEquals("keep my garden", store.read("farm"))
    }

    @Test fun malformedOrUnknownPreferenceSchemasFallBackWithWarning() {
        listOf("broken", "{}", """{"version":2,"musicEnabled":false,"soundEnabled":false,"reducedMotion":true}""",
            """{"version":1,"musicEnabled":"false","soundEnabled":false,"reducedMotion":true}""").forEach { value ->
            val store = MemoryAccountStore().apply { write(GameFeedbackController.PREFERENCE_KEY, value) }
            val controller = GameFeedbackController(store, RecordingAudio())
            assertEquals(GamePreferences(), controller.snapshot.value.preferences)
            assertNotNull(controller.snapshot.value.storageWarning)
            assertEquals(value, store.read(GameFeedbackController.PREFERENCE_KEY)) // No silent overwrite.
        }
    }

    @Test fun storageFailureDoesNotChangeAppliedPreferences() {
        val audio = RecordingAudio()
        val store = FailingStore()
        val controller = GameFeedbackController(store, audio)
        assertFalse(controller.update(GamePreferences(false, false, true)))
        assertEquals(GamePreferences(), controller.snapshot.value.preferences)
        assertEquals(listOf(true), audio.music)
        assertNotNull(controller.snapshot.value.storageWarning)
    }

    @Test fun failedReadIsVisibleAndCanBeReplacedBySuccessfulSave() {
        val store = object : AccountKeyValueStore {
            override fun read(key: String): String? = error("disk")
            override fun write(key: String, value: String) = Unit
            override fun remove(key: String) = Unit
        }
        val controller = GameFeedbackController(store, RecordingAudio())
        assertNotNull(controller.snapshot.value.storageWarning)
        assertTrue(controller.update(GamePreferences(false, false, true)))
        assertNull(controller.snapshot.value.storageWarning)
    }

    @Test fun musicAndEffectsAreIndependentAndUnknownCuesAreIgnored() {
        val audio = RecordingAudio()
        val controller = GameFeedbackController(MemoryAccountStore(), audio)
        controller.update(GamePreferences(false, true, false))
        controller.playEffect("harvest")
        controller.playEffect("not a cue")
        assertEquals(listOf("harvest"), audio.effects)
        controller.update(GamePreferences(true, false, true))
        controller.playEffect("water")
        assertEquals(listOf("harvest"), audio.effects)
        assertEquals(listOf(true, false, true), audio.music)
    }

    @Test fun backgroundAndClosedControllersNeverPlayEffects() {
        val audio = RecordingAudio()
        val controller = GameFeedbackController(MemoryAccountStore(), audio)
        controller.setActive(false)
        controller.playEffect("water")
        assertTrue(audio.effects.isEmpty())
        controller.setActive(true)
        controller.playEffect("water")
        controller.close()
        controller.close()
        controller.setActive(true)
        controller.playEffect("water")
        assertFalse(controller.update(GamePreferences()))
        assertEquals(listOf("water"), audio.effects)
        assertEquals(listOf(false, true), audio.activation)
        assertEquals(1, audio.releaseCount)
        assertFalse(controller.snapshot.value.available)
    }

    @Test fun headlessAndFailingAudioNeverPretendSoundIsAvailable() {
        assertFalse(GameFeedbackController(MemoryAccountStore(), SilentFarmAudio()).snapshot.value.available)
        val audio = RecordingAudio()
        val controller = GameFeedbackController(MemoryAccountStore(), audio)
        audio.fail = true
        controller.playEffect("plant")
        assertFalse(controller.snapshot.value.available)
        assertTrue(controller.update(GamePreferences(false, false, true))) // The game remains usable.
        assertFalse(controller.snapshot.value.available)
    }

    @Test fun preferenceCodecRoundTripsEveryCombination() {
        for (music in listOf(false, true)) for (sound in listOf(false, true)) for (motion in listOf(false, true)) {
            val preferences = GamePreferences(music, sound, motion)
            assertEquals(preferences, GamePreferencesCodec.decode(GamePreferencesCodec.encode(preferences)))
        }
    }

    @Test fun originalMusicAndAllEffectsAreValidBoundedPcmWavs() {
        val music = FarmSoundtrack.musicWav()
        val waves = listOf(music) + FarmSoundtrack.effectCues.map(FarmSoundtrack::effectWav)
        assertEquals(44 + FarmSoundtrack.sampleRate * 12 * 2, music.size)
        waves.forEach { bytes ->
            fun unsigned(offset: Int, count: Int): Int = (0 until count).sumOf { i ->
                (bytes[offset + i].toInt() and 255) shl (i * 8)
            }
            fun ascii(offset: Int) = bytes.copyOfRange(offset, offset + 4).decodeToString()
            assertEquals("RIFF", ascii(0))
            assertEquals("WAVE", ascii(8))
            assertEquals("fmt ", ascii(12))
            assertEquals("data", ascii(36))
            assertEquals(bytes.size - 8, unsigned(4, 4))
            assertEquals(bytes.size - 44, unsigned(40, 4))
            assertEquals(1, unsigned(20, 2))
            assertEquals(1, unsigned(22, 2))
            assertEquals(FarmSoundtrack.sampleRate, unsigned(24, 4))
            assertEquals(FarmSoundtrack.sampleRate * 2, unsigned(28, 4))
            assertEquals(16, unsigned(34, 2))
            val samples = (44 until bytes.size step 2).map { unsigned(it, 2).toShort().toInt() }
            assertTrue(samples.any { it != 0 })
            assertTrue(samples.maxOf { abs(it) } < 16_000) // No clipping or signed overflow.
            assertEquals(0, samples.first())
            assertEquals(0, samples.last())
        }
    }

    @Test fun synthesisIsDeterministic() {
        assertTrue(FarmSoundtrack.effectWav("water").contentEquals(FarmSoundtrack.effectWav("water")))
        assertTrue(FarmSoundtrack.musicWav().contentEquals(FarmSoundtrack.musicWav()))
    }

    private class RecordingAudio : FarmAudio {
        var fail = false
        val music = mutableListOf<Boolean>()
        val effects = mutableListOf<String>()
        val activation = mutableListOf<Boolean>()
        var releaseCount = 0
        override val available get() = !fail
        override fun setMusic(enabled: Boolean) { check(!fail); music += enabled }
        override fun playEffect(cue: String) { check(!fail); effects += cue }
        override fun setActive(active: Boolean) { activation += active }
        override fun release() { releaseCount++ }
    }

    private class FailingStore : AccountKeyValueStore {
        override fun read(key: String): String? = null
        override fun write(key: String, value: String) { error("disk") }
        override fun remove(key: String) = Unit
    }
}
