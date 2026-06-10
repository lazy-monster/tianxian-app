package com.scholar.app.audio

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** Universal offline-capable audio via the platform zh-CN TTS engine (Standard Mandarin).
 *  Supports one-shot word playback ([speak]) and sequential read-aloud ([speakSequence]) that
 *  reports progress so the reader can highlight and auto-scroll the sentence being spoken.
 *
 *  Read-aloud advances one sentence at a time (speaking the next from onDone) rather than queuing a
 *  whole chapter up front — the platform TTS queue is bounded and would silently drop the tail of a
 *  long chapter, leaving playback stuck. Each utterance id is tagged with a sequence [token] so that
 *  callbacks from a stopped/replaced sequence are ignored (avoids advancing into a new, shorter list). */
class Speaker(context: Context) {
    private var ready = false
    private val main = Handler(Looper.getMainLooper())

    // read-aloud sequence state (mutated only on the main thread)
    private var items: List<String> = emptyList()
    private var current = 0                       // index of the sentence currently being spoken
    private var onIndex: ((Int) -> Unit)? = null
    private var onFinished: (() -> Unit)? = null
    @Volatile private var token = 0

    @Volatile
    var isSpeaking = false
        private set

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            // Prefer the Mainland voice; fall back to any Chinese voice rather than ending up
            // on the system default (which would read hanzi with English letter names).
            val r = tts.setLanguage(Locale.SIMPLIFIED_CHINESE)
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.setLanguage(Locale.CHINESE)
            }
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    val (tk, i) = parse(utteranceId) ?: return
                    if (tk != token) return
                    onIndex?.let { cb -> main.post { if (tk == token) cb(i) } }
                }
                override fun onDone(utteranceId: String?) {
                    val (tk, i) = parse(utteranceId) ?: return
                    main.post { if (tk == token && isSpeaking) advanceAfter(i) }
                }
                override fun onError(utteranceId: String?) {
                    val (tk, i) = parse(utteranceId) ?: return
                    main.post { if (tk == token && isSpeaking) advanceAfter(i) }   // skip a failed sentence
                }
            })
            ready = true
        }
    }

    // The user's read-aloud speed. Applied per sequence utterance, never to one-shot words —
    // otherwise a slow reader session would leave every 🔊 tap in the app playing slow too.
    private var sequenceRate = 1.0f

    /** One word/phrase at natural speed, interrupting anything currently playing. The "single"
        id has no token so it is ignored by the sequence callbacks. */
    fun speak(text: String) {
        if (!ready || text.isBlank()) return
        tts.setSpeechRate(1.0f)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "single")
    }

    /** Set the read-aloud (sequence) speed. One-shot [speak] always stays at natural speed. */
    fun setRate(rate: Float) { sequenceRate = rate }

    /**
     * Read [items] in order starting at [startAt]. [onIndex] fires (on the main thread) as each
     * sentence begins, carrying its absolute index in [items] — use it to highlight/scroll. [onDone]
     * fires once the last sentence finishes. No-op-safe: an empty list or an unready engine calls
     * [onDone] immediately.
     */
    fun speakSequence(items: List<String>, startAt: Int = 0, onIndex: (Int) -> Unit, onDone: () -> Unit) {
        if (!ready || items.isEmpty()) { onDone(); return }
        token++
        this.items = items
        this.onIndex = onIndex
        this.onFinished = onDone
        isSpeaking = true
        tts.stop()
        speakAt(startAt.coerceIn(0, items.lastIndex))
    }

    /**
     * Jump [delta] sentences within the active read-aloud (e.g. -1 to repeat/go back, +1 to skip
     * ahead) and start speaking from there. Clamped to the current sequence; no-op if not reading.
     */
    fun skipBy(delta: Int) {
        main.post {
            if (!isSpeaking || items.isEmpty()) return@post
            // New token so the interrupted utterance's onDone can't auto-advance over our jump.
            token++
            speakAt((current + delta).coerceIn(0, items.lastIndex))
        }
    }

    /** Stop read-aloud and clear sequence callbacks (does not fire onDone). */
    fun stop() {
        token++
        isSpeaking = false
        onIndex = null; onFinished = null; items = emptyList()
        if (ready) tts.stop()
    }

    private fun speakAt(i: Int) {
        current = i
        tts.setSpeechRate(sequenceRate)
        tts.speak(items[i], TextToSpeech.QUEUE_FLUSH, null, "$token:$i")
    }

    /** Main-thread: sentence [i] finished — play the next or finish the sequence. */
    private fun advanceAfter(i: Int) {
        if (i >= items.lastIndex) {
            isSpeaking = false
            val cb = onFinished
            onIndex = null; onFinished = null; items = emptyList()
            cb?.invoke()
        } else {
            speakAt(i + 1)
        }
    }

    /** Parse a "token:index" utterance id; null for one-shot ("single") or malformed ids. */
    private fun parse(utteranceId: String?): Pair<Int, Int>? {
        val parts = utteranceId?.split(':') ?: return null
        if (parts.size != 2) return null
        val tk = parts[0].toIntOrNull() ?: return null
        val i = parts[1].toIntOrNull() ?: return null
        return tk to i
    }

    fun shutdown() = tts.shutdown()
}
