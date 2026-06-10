package com.scholar.app.data.content

/** Converts CC-CEDICT numeric pinyin ("xiu1 lian4", "lu:3") to tone-marked pinyin
 *  ("xiūliàn", "lǚ"). Pure function, no dependencies. */
object Pinyin {

    private val TONE = mapOf(
        'a' to "āáǎà", 'e' to "ēéěè", 'i' to "īíǐì",
        'o' to "ōóǒò", 'u' to "ūúǔù", 'v' to "ǖǘǚǜ",   // v / u: = ü
    )

    fun toned(numeric: String): String =
        numeric.trim().split(' ').joinToString("") { tonedSyllable(it) }

    /** Toned syllables of a numeric pinyin string, one entry per syllable
        ("huan2 gei3" → ["huán","gěi"]) — for aligning a word's syllables to its characters. */
    fun tonedSyllables(numeric: String): List<String> =
        numeric.trim().split(' ').filter { it.isNotBlank() }.map { tonedSyllable(it) }

    private fun tonedSyllable(raw: String): String {
        if (raw.isEmpty()) return raw
        var s = raw.lowercase().replace("u:", "v").replace("ü", "v")
        val tone = s.last().digitToIntOrNull()
        if (tone == null || tone !in 1..5) return raw.replace("u:", "ü")
        s = s.dropLast(1)
        if (tone == 5) return s.replace('v', 'ü')          // neutral tone, no mark
        val idx = vowelToMark(s)
        if (idx < 0) return s.replace('v', 'ü')
        val ch = s[idx]
        val marked = TONE[ch]!![tone - 1]
        return (s.substring(0, idx) + marked + s.substring(idx + 1)).replace('v', 'ü')
    }

    /** Standard tone-placement rule: a/e win; else o in 'ou'; else the last vowel. */
    private fun vowelToMark(s: String): Int {
        s.indexOf('a').let { if (it >= 0) return it }
        s.indexOf('e').let { if (it >= 0) return it }
        val ou = s.indexOf("ou"); if (ou >= 0) return ou
        for (i in s.indices.reversed()) if (s[i] in "aeiouv") return i
        return -1
    }
}
