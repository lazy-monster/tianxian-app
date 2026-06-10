package com.scholar.app.data.content

/**
 * CC-CEDICT / Make-Me-a-Hanzi glosses are *reference* data: senses arrive in source order and
 * often lead with material a learner shouldn't study first. Two kinds:
 *
 *  - **Cross-reference noise** — "surname Du", "variant of 還|还", "see 未可厚非", "CL:個|个",
 *    "abbr. for …", "Taiwan pr. …". Never a learnable answer. If it's *all* an entry has, the
 *    caller should fall back to a better source (see [hasRealSense] / [crossRefTarget]); the
 *    hsk_word table in particular often stores only the surname/variant reading of a character.
 *  - **Grammatical descriptions** — "classifier for …", "particle indicating …", "interjection
 *    of …", a bare "(adverb of degree)". For a function word this *is* the meaning, so it's kept
 *    — only demoted below any concrete sense.
 *
 * Senses are reordered concrete → grammatical → noise. Pure functions, no dependencies.
 */
object Gloss {

    // Cross-reference noise: never shown as the primary meaning; presence alone triggers fallback.
    // "used in 恶心" / "(used in place names)" point at a compound and carry no standalone meaning,
    // so they belong here — 上 ("used in 上声") really means "above; up", recovered by fallback.
    private val NOISE_PREFIXES = listOf(
        "surname ", "variant of ", "old variant of ", "archaic variant of ", "erhua variant",
        "euphemistic variant", "see ", "also written", "abbr. for ", "cl:", "taiwan pr", "also pr",
        "used in ", "used as ",
    )

    // Grammatical / usage descriptions: valid meaning for a function word, but demoted below
    // any concrete sense. (Trailing spaces keep one-word glosses like "particle" out.)
    private val GRAMMAR_PREFIXES = listOf(
        "classifier for", "measure word", "particle ", "conjunction ", "auxiliary ", "modal particle",
        "structural particle", "aspect particle", "sentence-final", "interjection ",
        "radical ", "kangxi radical", "(old)", "(archaic)", "(literary)", "(bound form)",
        "(dialect)", "(onom.)", "(interj", "(particle", "(grammatical", "(prefix", "(suffix",
    )

    // NB: do NOT split on '|' — it joins trad|simp inside a single cross-reference (變|变[bian4]).
    /** Split a raw gloss into its senses. */
    fun senses(raw: String): List<String> =
        raw.split('/', ';', '；').map { it.trim().trim(',', '，').trim() }.filter { it.isNotEmpty() }

    /** Strip leading parenthetical groups; MAY return "" when the sense is *only* an annotation. */
    private fun unannotated(sense: String): String {
        var t = sense.trim()
        while (t.startsWith("(")) {
            val close = t.indexOf(')')
            if (close < 0) break
            t = t.substring(close + 1).trim()
        }
        return t
    }

    /** Drop leading parentheticals for display: "(conjunction used to …) but" → "but"; keeps the
        original when the sense is nothing but an annotation ("(adverb of degree)"). */
    private fun stripAnnotation(sense: String): String = unannotated(sense).ifEmpty { sense }

    /** Text used to classify a sense by prefix: what sits outside a leading "(Tw)"/"(old)", or —
        for a sense that is *only* a parenthetical — the text inside it, so "(used in place names)"
        is recognised as noise just like the bare "used in 上声". */
    private fun classifyText(sense: String): String {
        val u = unannotated(sense)
        if (u.isNotEmpty()) return u
        val s = sense.trim()
        return if (s.startsWith("(") && s.endsWith(")")) s.substring(1, s.length - 1).trim() else s
    }

    private fun isNoise(sense: String): Boolean {
        val l = classifyText(sense).lowercase()
        return NOISE_PREFIXES.any { l.startsWith(it) }
    }

    private fun isGrammar(sense: String): Boolean {
        if (isNoise(sense)) return false
        if (unannotated(sense).isEmpty()) return true     // pure parenthetical, e.g. "(adverb of degree)"
        return GRAMMAR_PREFIXES.any { classifyText(sense).lowercase().startsWith(it) }
    }

    /** Senses reordered: concrete meanings first, grammatical roles next, cross-ref noise last. */
    fun ordered(raw: String): List<String> {
        val s = senses(raw)
        val concrete = s.filter { !isNoise(it) && !isGrammar(it) }
        val grammar = s.filter { isGrammar(it) }
        val noise = s.filter { isNoise(it) }
        return concrete + grammar + noise
    }

    /** True if [raw] has at least one sense that is not pure cross-reference noise (a grammatical
        role counts). Lets a caller fall back when an entry is e.g. only "surname Du". */
    fun hasRealSense(raw: String): Boolean = senses(raw).any { !isNoise(it) }

    /** The simplified word a cross-reference points at — "variant of 擋|挡[dang3]" → "挡" — so a
        metadata-only entry can borrow its target's gloss. Null if there's no such reference. */
    fun crossRefTarget(raw: String): String? {
        for (s in senses(raw)) {
            if (!isNoise(s)) continue
            val m = CROSSREF.find(s) ?: continue
            var ref = m.groupValues[1].trim()
            if ('|' in ref) ref = ref.substringAfterLast('|')
            ref = ref.substringBefore('[').trim()
            if (ref.isNotEmpty()) return ref
        }
        return null
    }

    private val CROSSREF =
        Regex("""(?:variant of|old variant of|archaic variant of|see|abbr\. for)\s+([^,\[;/]+)""",
            RegexOption.IGNORE_CASE)

    /** Full gloss for reference surfaces (character page, dictionary, popups), meaning first. */
    fun display(raw: String, separator: String = " / "): String =
        ordered(raw).joinToString(separator).ifEmpty { raw }

    /** The single best short label — quiz options and compact rows. Never empty for non-blank input. */
    fun primary(raw: String, maxLen: Int = 40): String {
        val first = ordered(raw).firstOrNull() ?: return raw.take(maxLen)
        return stripAnnotation(first).take(maxLen).trim()
    }

    /** One or two core senses for a flashcard back — recallable at a glance. Concrete senses lead;
        cross-reference noise is never shown unless it is genuinely all the entry has. */
    fun core(raw: String): String {
        val real = ordered(raw).filterNot { isNoise(it) }.ifEmpty { ordered(raw) }
        if (real.isEmpty()) return raw
        val core = real.take(2).joinToString("; ") { sense ->
            stripAnnotation(sense).split(',', '，').map { it.trim() }
                .filter { it.isNotEmpty() }.take(3).joinToString(", ")
        }
        return core.ifBlank { real.first() }
    }
}
