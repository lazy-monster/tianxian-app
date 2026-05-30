package com.scholar.app.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Universal offline-capable audio via the platform zh-CN TTS engine (Standard
 *  Mandarin). Per-syllable recorded audio can be layered on later for the
 *  pronunciation trainer; this covers every word and sentence today. */
class Speaker(context: Context) {
    private var ready = false
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.SIMPLIFIED_CHINESE
            ready = true
        }
    }

    fun speak(text: String) {
        if (ready && text.isNotBlank()) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, text.hashCode().toString())
    }

    fun shutdown() = tts.shutdown()
}
