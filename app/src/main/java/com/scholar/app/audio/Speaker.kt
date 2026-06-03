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
    private var onIndex: ((Int) -> Unit)? = null
    private var onFinished: (() -> Unit)? = null
    @Volatile private var token = 0

    @Volatile
    var isSpeaking = false
        private set

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.SIMPLIFIED_CHINESE
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

    /** One word/phrase, interrupting anything currently playing. The "single" id has no token so it
        is ignored by the sequence callbacks. */
    fun speak(text: String) {
        if (ready && text.isNotBlank()) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "single")
    }

    fun setRate(rate: Float) { if (ready) tts.setSpeechRate(rate) }

    /**
     * Read [items] in order. [onIndex] fires (on the main thread) as each sentence begins — use it
     * to highlight/scroll. [onDone] fires once the last sentence finishes. No-op-safe: an empty list
     * or an unready engine calls [onDone] immediately.
     */
    fun speakSequence(items: List<String>, onIndex: (Int) -> Unit, onDone: () -> Unit) {
        if (!ready || items.isEmpty()) { onDone(); return }
        token++
        this.items = items
        this.onIndex = onIndex
        this.onFinished = onDone
        isSpeaking = true
        tts.stop()
        speakAt(0)
    }

    /** Stop read-aloud and clear sequence callbacks (does not fire onDone). */
    fun stop() {
        token++
        isSpeaking = false
        onIndex = null; onFinished = null; items = emptyList()
        if (ready) tts.stop()
    }

    private fun speakAt(i: Int) {
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
