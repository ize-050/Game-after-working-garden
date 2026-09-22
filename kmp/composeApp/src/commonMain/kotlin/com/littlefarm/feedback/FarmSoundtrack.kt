package com.littlefarm.feedback

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/** Original, deterministic synthesis. No third-party recordings or downloaded assets. */
object FarmSoundtrack {
    const val sampleRate = 22_050
    val effectCues = setOf("plow", "plant", "water", "harvest", "coins", "order", "upgrade")

    /** Twelve seconds, soft pentatonic bells and a low pad; starts/ends at silence. */
    fun musicWav(): ByteArray {
        val samples = DoubleArray(sampleRate * 12)
        val melody = doubleArrayOf(
            392.00, 493.88, 587.33, 493.88, 440.00, 392.00, 329.63, 392.00,
            440.00, 587.33, 659.25, 587.33, 493.88, 440.00, 329.63, 392.00,
        )
        melody.forEachIndexed { i, frequency ->
            addBell(samples, start = i * 0.75 + 0.03, duration = 0.67, frequency = frequency, gain = 0.16)
        }
        doubleArrayOf(196.00, 164.81, 220.00, 196.00).forEachIndexed { i, frequency ->
            addBell(samples, start = i * 3.0 + 0.02, duration = 2.9, frequency = frequency, gain = 0.07)
        }
        return encodeWav(samples)
    }

    fun effectWav(cue: String): ByteArray {
        require(cue in effectCues) { "Unknown farm sound" }
        val duration = when (cue) { "water" -> 0.65; "order", "upgrade" -> 0.85; "harvest" -> 0.58; else -> 0.3 }
        val samples = DoubleArray((sampleRate * duration).toInt())
        when (cue) {
            "plow" -> {
                addBell(samples, 0.0, 0.18, 135.0, 0.24)
                addNoise(samples, gain = 0.08, watery = false)
            }
            "plant" -> {
                addBell(samples, 0.0, 0.15, 523.25, 0.20)
                addBell(samples, 0.09, 0.19, 659.25, 0.15)
            }
            "water" -> {
                addNoise(samples, gain = 0.09, watery = true)
                for (i in 0..4) addBell(samples, i * 0.105, 0.14, 900.0 + i * 85, 0.04)
            }
            "harvest" -> {
                addBell(samples, 0.0, 0.22, 392.0, 0.18)
                addBell(samples, 0.14, 0.22, 493.88, 0.16)
                addBell(samples, 0.28, 0.28, 587.33, 0.16)
            }
            "coins" -> {
                addBell(samples, 0.0, 0.18, 987.77, 0.14)
                addBell(samples, 0.09, 0.20, 1318.51, 0.10)
            }
            "order", "upgrade" -> {
                addBell(samples, 0.0, 0.32, 392.0, 0.14)
                addBell(samples, 0.16, 0.32, 493.88, 0.14)
                addBell(samples, 0.32, 0.48, 587.33, 0.12)
                addBell(samples, 0.32, 0.48, 783.99, 0.07)
            }
        }
        return encodeWav(samples)
    }

    private fun addBell(samples: DoubleArray, start: Double, duration: Double, frequency: Double, gain: Double) {
        val offset = (start * sampleRate).toInt()
        val length = min((duration * sampleRate).toInt(), samples.size - offset)
        for (i in 0 until length) {
            val t = i.toDouble() / sampleRate
            val edge = min(1.0, min(t / 0.008, (length - 1 - i).toDouble() / (sampleRate * 0.018)))
            val envelope = edge * exp(-3.2 * t / duration)
            samples[offset + i] += gain * envelope * (
                sin(2 * PI * frequency * t) + 0.16 * sin(2 * PI * frequency * 2 * t)
            )
        }
    }

    private fun addNoise(samples: DoubleArray, gain: Double, watery: Boolean) {
        var seed = 217L
        var smoothed = 0.0
        for (i in samples.indices) {
            seed = (seed * 1_664_525L + 1_013_904_223L) and 0xFFFF_FFFFL
            val raw = seed.toDouble() / 2_147_483_647.5 - 1.0
            smoothed = smoothed * 0.7 + raw * 0.3
            val progress = i.toDouble() / samples.size
            val envelope = sin(PI * progress) * if (watery) 1.0 else exp(-4 * progress)
            samples[i] += smoothed * envelope * gain
        }
        samples[samples.lastIndex] = 0.0
    }

    private fun encodeWav(samples: DoubleArray): ByteArray {
        val bytes = ByteArray(44 + samples.size * 2)
        fun ascii(offset: Int, value: String) = value.forEachIndexed { i, c -> bytes[offset + i] = c.code.toByte() }
        fun littleEndian(offset: Int, value: Int, length: Int) {
            for (i in 0 until length) bytes[offset + i] = (value ushr (i * 8)).toByte()
        }
        ascii(0, "RIFF")
        littleEndian(4, bytes.size - 8, 4)
        ascii(8, "WAVE")
        ascii(12, "fmt ")
        littleEndian(16, 16, 4)
        littleEndian(20, 1, 2) // PCM
        littleEndian(22, 1, 2) // mono
        littleEndian(24, sampleRate, 4)
        littleEndian(28, sampleRate * 2, 4)
        littleEndian(32, 2, 2)
        littleEndian(34, 16, 2)
        ascii(36, "data")
        littleEndian(40, samples.size * 2, 4)
        samples.forEachIndexed { i, value ->
            littleEndian(44 + i * 2, (value.coerceIn(-0.95, 0.95) * 32767).toInt(), 2)
        }
        return bytes
    }
}
