package com.scholar.app.data.content

/**
 * CC-CEDICT / Make-Me-a-Hanzi glosses are *reference* data: senses arrive in source order and
 * often lead with metadata — "surname Huan", "variant of 還|还", "(conjunction used to express
 * contrast with a previous clause) but; then" — rather than the meaning a learner should study.
 *
 * This reorders senses so substantive meanings lead (metadata kept, but last), and produces the
 * short labels used on cards and quiz options. Pure functions, no dependencies.
 */
object Gloss {

    // A sense starting with one of these is metadata or a grammatical description, not the
    // meaning to learn. (Trailing spaces keep one-word glosses like "particle" safe.)
    private val META_PREFIXES = listOf(
        "surname ", "variant of ", "old variant of ", "archaic variant of ", "erhua variant",
        "euphemistic variant", "used in ", "used as ", "see ", "also written", "abbr. for ",
        "cl:", "taiwan pr", "also pr", "(old)", "(archaic)", "(literary)", "(bound form)",
        "(dialect)", "(onom.)", "(interj", "(particle", "(grammatical", "(used ", "radical ",
        "kangxi radical", "(prefix", "(suffix",
        // bare grammatical descriptions, as the HSK lists phrase them
        "conjunction ", "particle ", "auxiliary ", "modal particle", "structural particle",
        "aspect particle", "sentence-final", "interjection ", "classifier for", "measure word",
    )

    /** Split a raw gloss into its senses. */
    fun senses(raw: String): List<String> =
        raw.split('/', ';', '；', '|').map { it.trim().trim(',', '，').trim() }.filter { it.isNotEmpty() }

    private fun isMeta(sense: String): Boolean {
        val l = sense.lowercase()
        return META_PREFIXES.any { l.startsWith(it) }
    }

    /** Drop leading parenthetical annotations: "(conjunction used to …) but" → "but". */
    private fun stripAnnotation(sense: String): String {
        var t = sense.trim()
        while (t.startsWith("(")) {
            val close = t.indexOf(')')
            if (close < 0) break
            t = t.substring(close + 1).trim()
        }
        return t.ifEmpty { sense }
    }

    /** All senses with substantive meanings first and metadata after. */
    fun ordered(raw: String): List<String> {
        val (meta, real) = senses(raw).partition { isMeta(it) }
        return real + meta
    }

    /** Full gloss for reference surfaces (character page, dictionary, popups), meaning first. */
    fun display(raw: String, separator: String = " / "): String =
        ordered(raw).joinToString(separator).ifEmpty { raw }

    /** The single best short label — quiz options and compact rows. Never empty for non-blank input. */
    fun primary(raw: String, maxLen: Int = 40): String {
        val first = ordered(raw).firstOrNull() ?: return raw.take(maxLen)
        return stripAnnotation(first).take(maxLen).trim()
    }

    /** One or two core senses for a flashcard back — recallable at a glance. */
    fun core(raw: String): String {
        val real = ordered(raw)
        if (real.isEmpty()) return raw
        val core = real.take(2).joinToString("; ") { sense ->
            stripAnnotation(sense).split(',', '，').map { it.trim() }
                .filter { it.isNotEmpty() }.take(3).joinToString(", ")
        }
        return core.ifBlank { real.first() }
    }
}
