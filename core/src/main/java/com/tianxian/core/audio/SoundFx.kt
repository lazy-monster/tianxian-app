package com.tianxian.core.audio

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper

/**
 * Tiny interactive sound cues for the trials — a soft "right", a flatter "wrong", a celebratory
 * flourish on breakthrough, and a click on tap. Built on the platform [ToneGenerator] so it needs
 * **no audio assets** and works fully offline. Every cue is gated by [enabled] (the user's setting),
 * so muting is instant.
 *
 * Tones are deliberately short and quiet (system "music" stream, modest volume) — the goal is a
 * gentle rhythm that keeps a study session moving, not a noisy arcade. A single generator is reused
 * for the app's lifetime and recreated lazily if the platform ever drops it.
 */
class SoundFx(private val enabled: () -> Boolean) {
    private val main = Handler(Looper.getMainLooper())

    // ToneGenerator can throw on some OEM builds when audio resources are exhausted; never let a
    // sound cue crash a trial, so construction and playback are both guarded.
    @Volatile private var gen: ToneGenerator? = null
    private fun generator(): ToneGenerator? {
        gen?.let { return it }
        return runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, VOLUME) }.getOrNull()?.also { gen = it }
    }

    private fun play(tone: Int, durationMs: Int) {
        if (!enabled()) return
        runCatching { generator()?.startTone(tone, durationMs) }
    }

    /** A bright, affirming two-pip — answered correctly. */
    fun correct() = play(ToneGenerator.TONE_PROP_ACK, 150)

    /** A flat single tone — answered wrong. Short, never harsh. */
    fun wrong() = play(ToneGenerator.TONE_PROP_NACK, 200)

    /** A soft click for ordinary taps (advancing a question, etc.). */
    fun tap() = play(ToneGenerator.TONE_PROP_BEEP, 40)

    /** A short ascending flourish for a realm/stage breakthrough. */
    fun breakthrough() {
        if (!enabled()) return
        val steps = listOf(
            0L to ToneGenerator.TONE_PROP_BEEP,
            130L to ToneGenerator.TONE_PROP_ACK,
            300L to ToneGenerator.TONE_PROP_BEEP2,
        )
        steps.forEach { (delay, tone) ->
            main.postDelayed({ runCatching { generator()?.startTone(tone, 160) } }, delay)
        }
    }

    fun release() {
        runCatching { gen?.release() }
        gen = null
    }

    private companion object {
        /** 0–100; kept low so cues sit under, not over, the TTS read-aloud. */
        const val VOLUME = 70
    }
}
