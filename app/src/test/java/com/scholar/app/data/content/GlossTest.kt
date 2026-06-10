package com.scholar.app.data.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [Gloss] — the actual shipped logic. Covers the bugs behind the reported
 * "surname Du" / "(adverb of degree)" leaks. The companion data sweep over every content.db row
 * lives in tools/validate_glosses.py (run it after editing the bundled dictionary).
 */
class GlossTest {

    // ── cross-reference noise must never lead a learner-facing label ──────────────────

    @Test fun surnameNeverLeads() {
        // 还 huán/hái — CC-CEDICT lists the surname reading first.
        val raw = "surname Huan / still; yet; even more / to pay back; to return"
        assertEquals("still", Gloss.primary(raw))
        assertFalse(Gloss.core(raw).lowercase().startsWith("surname"))
        assertTrue(Gloss.core(raw).startsWith("still"))
    }

    @Test fun variantAndClassifierNoiseDropped() {
        val raw = "to contact / connection / variant of 聯繫|联系[lian2 xi4] / CL:個|个[ge4]"
        assertEquals("to contact", Gloss.primary(raw))
        // the trad|simp pipe must NOT be split into a fake "联系[lian2 xi4]" sense
        assertFalse(Gloss.primary(raw).contains("["))
    }

    @Test fun annotationOnlyLeadingSenseIsDemoted() {
        // 很 — the bare "(adverb of degree)" annotation must not lead; concrete "quite" does.
        val raw = "(adverb of degree); quite; very; awfully"
        assertEquals("quite", Gloss.primary(raw))
        assertEquals("quite; very", Gloss.core(raw))
        assertTrue(Gloss.hasRealSense(raw))            // it has real senses → no fallback needed
    }

    @Test fun leadingParentheticalIsStrippedNotDropped() {
        // "(coll.) father; dad" → keep the meaning, just drop the register tag.
        assertEquals("father", Gloss.primary("(coll.) father; dad"))
    }

    @Test fun annotationHidingNoiseStillCountsAsNoise() {
        // "(Tw) variant of 挡, gear" — the leading (Tw) must not mask the "variant of" beneath it.
        assertFalse(Gloss.hasRealSense("(Tw) variant of 挡"))
    }

    // ── grammatical descriptions ARE the meaning for function words ───────────────────

    @Test fun classifierDescriptionIsKept() {
        val raw = "classifier for trees, cabbages, plants etc"
        assertTrue(Gloss.hasRealSense(raw))            // do NOT fall back — this is the meaning
        assertTrue(Gloss.primary(raw).startsWith("classifier for"))
    }

    @Test fun particleDescriptionIsKept() {
        assertTrue(Gloss.hasRealSense("particle indicating continuation of a state"))
    }

    // ── hasRealSense drives the data-layer fallback ───────────────────────────────────

    @Test fun metadataOnlyHasNoRealSense() {
        assertFalse(Gloss.hasRealSense("surname Du"))
        assertFalse(Gloss.hasRealSense("variant of 火爆[huo3 bao4]"))
        assertFalse(Gloss.hasRealSense("see 未可厚非[wei4 ke3 hou4 fei1]"))
        assertFalse(Gloss.hasRealSense("abbr. for 克罗地亚, Croatia"))
    }

    @Test fun realMeaningHasRealSense() {
        assertTrue(Gloss.hasRealSense("all, each, entirely; capital"))
        assertTrue(Gloss.hasRealSense("surname Du / all; entirely / capital"))  // mixed → real
    }

    // ── crossRefTarget resolves the pointer for the deepest fallback ──────────────────

    @Test fun crossRefTargetExtractsSimplified() {
        assertEquals("火爆", Gloss.crossRefTarget("variant of 火爆[huo3 bao4]"))
        assertEquals("挡", Gloss.crossRefTarget("(Tw) variant of 擋|挡[dang3], gear"))  // trad|simp → simp
        assertEquals("未可厚非", Gloss.crossRefTarget("see 未可厚非[wei4 ke3 hou4 fei1]"))
        assertEquals(null, Gloss.crossRefTarget("all; entirely"))                       // no reference
    }

    // ── degenerate input ──────────────────────────────────────────────────────────────

    @Test fun blankAndPlainInput() {
        assertEquals("", Gloss.primary(""))
        assertEquals("hello", Gloss.primary("hello"))
        assertEquals("hello", Gloss.core("hello"))
    }
}
