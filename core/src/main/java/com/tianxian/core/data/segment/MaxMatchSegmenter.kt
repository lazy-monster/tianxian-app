package com.tianxian.core.data.segment

/**
 * Forward maximal-matching segmenter. Given the set of dictionary headwords
 * (from CC-CEDICT) it splits a run of Chinese text into the longest dictionary
 * words possible — the standard, dependency-free approach that makes every word
 * in an imported novel individually tappable.
 *
 * This is real working code: feed it a vocabulary and it segments. In the app the
 * vocabulary comes from the bundled dictionary; here it's injected so it's testable.
 */
class MaxMatchSegmenter(
    private val vocabulary: Set<String>,
    private val maxWordLen: Int = 8,
) {
    data class Token(val text: String, val isWord: Boolean, val start: Int)

    fun segment(text: String): List<Token> {
        val tokens = ArrayList<Token>()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (!isHan(c)) {                       // punctuation / latin / spaces pass through
                tokens.add(Token(c.toString(), isWord = false, start = i)); i++; continue
            }
            val end = minOf(i + maxWordLen, text.length)
            var matched: String? = null
            // try longest candidate first
            var len = end - i
            while (len >= 1) {
                val cand = text.substring(i, i + len)
                if (len == 1 || cand in vocabulary) { matched = cand; break }
                len--
            }
            val word = matched ?: text[i].toString()
            tokens.add(Token(word, isWord = true, start = i))
            i += word.length
        }
        return tokens
    }

    private fun isHan(c: Char): Boolean = c.code in 0x4E00..0x9FFF || c.code in 0x3400..0x4DBF
}
