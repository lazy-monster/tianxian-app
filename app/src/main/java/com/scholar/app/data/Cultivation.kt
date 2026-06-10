package com.scholar.app.data

/**
 * Maps real learning progress onto the xianxia cultivation ladder, so the genre page
 * becomes a living rank rather than a glossary.
 *
 * The rank is anchored to **fluency**, not exposure: the score approximates how much of a
 * fluent reader's vocabulary you have genuinely and durably acquired. Deep retention is the spine.
 *
 * Cultivation base (修为) blends four signals:
 *   - words/chars MASTERED (FSRS stability ≥ 21d)   weight 1.0 — durable recall, the true measure
 *   - characters merely KNOWN (recognition)          weight 0.3 — partial credit; recognised, not yet fluent
 *   - radicals cultivated                             weight 0.4 — foundation (caps at 214 ≈ 86 pts)
 *   - words sealed on the study track                 weight 0.2 — freshly learned; light credit until they
 *                                                                  survive review and graduate into "mastered"
 *
 * Both review and the gated tracks move the rank, but only durable mastery carries you to the top:
 * recognition and study-credit are deliberately small, so against a corpus of thousands of words the
 * high realms can only be reached by mastering the language for real — not by flipping through cards once.
 *
 * Calibrated to the bundled HSK corpus (~11,470 words / ~3,000 fluency-level characters):
 *   - entering the final realm (渡劫 Tribulation) ≈ mastering ~HSK 1–6 (≈5–6k units) — comfortable native reading;
 *   - Great Perfection sits near total mastery of the whole corpus — a near-mythical climb.
 * Realm thresholds **accelerate** (each gap wider than the last), so from a high realm the distance to
 * the peak is far greater than a linear "two realms away" suggests — exactly like cultivating at altitude.
 */
object Cultivation {

    data class Realm(val index: Int, val hanzi: String, val name: String, val entryScore: Int, val note: String)

    // Entry scores accelerate (gaps 180,270,400,550,750,1000,1300,1650): each realm costs more 修为
    // than the last. Anchored to mastered-vocabulary milestones on the bundled HSK corpus.
    val REALMS = listOf(
        Realm(0, "炼气", "Qi Refining", 0, "Drawing qi into the body. The long first climb — pinyin, the radicals, and your first hundred-odd words held firmly."),
        Realm(1, "筑基", "Foundation Establishment", 180, "A true cultivator. The foundation laid here — early HSK, mastered not merely met — decides how high you can ever climb."),
        Realm(2, "金丹", "Golden Core", 450, "Qi condenses into a core — graded and simple text opens up without drowning you."),
        Realm(3, "元婴", "Nascent Soul", 850, "A second self forms. Sect elders sit here; you can wade into a real novel with a dictionary at your side."),
        Realm(4, "化神", "Spirit Severing", 1400, "The soul merges with heaven and earth. With several thousand words held fast, reading begins to flow."),
        Realm(5, "炼虚", "Void Refinement", 2150, "You grasp the underlying laws — context now carries you past most unknown words unaided."),
        Realm(6, "合体", "Body Integration", 3150, "Body and law are one. Most chapters open without a fight; the HSK core is yours."),
        Realm(7, "大乘", "Great Ascension", 4450, "The peak of the mortal world — the advanced lexicon largely mastered, preparing to leave the dictionary behind."),
        Realm(8, "渡劫", "Tribulation Crossing", 6100, "Crossing heaven's lightning into immortality — fluent, comfortable native reading. The final realm is the longest: its summit, Great Perfection, is near-total mastery of the whole corpus."),
    )

    private const val PEAK_CAP = 12100                     // ceiling for the top realm's sub-stages ≈ total corpus mastery
    private val QI_LAYERS = listOf(0, 10, 24, 42, 65, 92, 122, 150, 165)   // entry score for Qi Refining layers 1..9
    private val SUBSTAGES = listOf("Early Stage", "Middle Stage", "Late Stage", "Great Perfection")

    // Blend weights (also shown on the Cultivation screen's contribution breakdown).
    const val W_KNOWN = 0.3
    const val W_RADICALS = 0.4
    const val W_TRACK = 0.2

    /** 修为. Durable mastery is the spine (×1.0); recognition, foundation and fresh study credit add less. */
    fun score(known: Int, mastered: Int, radicalsCultivated: Int, trackWords: Int): Int =
        (mastered + W_KNOWN * known + W_RADICALS * radicalsCultivated + W_TRACK * trackWords).toInt()

    data class Rank(
        val realm: Realm,
        val stageLabel: String,      // "Layer 7" or "Middle Stage"
        val title: String,           // "Qi Refining · Layer 7"
        val score: Int,
        val stageStart: Int,
        val stageEnd: Int?,          // null at the very peak
        val progress: Float,         // 0..1 within the current sub-stage
        val nextRealm: Realm?,
        val toNextRealm: Int?,       // score remaining to next realm
        val isPeak: Boolean,
    )

    fun rankFor(known: Int, mastered: Int, radicalsCultivated: Int, trackWords: Int): Rank {
        val p = score(known, mastered, radicalsCultivated, trackWords)
        val ri = REALMS.indexOfLast { it.entryScore <= p }.coerceAtLeast(0)
        val realm = REALMS[ri]
        val nextRealm = REALMS.getOrNull(ri + 1)
        val realmEnd = nextRealm?.entryScore ?: PEAK_CAP

        if (ri == 0) {
            val layer = QI_LAYERS.indexOfLast { it <= p }.coerceIn(0, 8)
            val start = QI_LAYERS[layer]
            val end = if (layer < 8) QI_LAYERS[layer + 1] else realmEnd
            val prog = ((p - start).toFloat() / (end - start).coerceAtLeast(1)).coerceIn(0f, 1f)
            return Rank(realm, "Layer ${layer + 1}", "${realm.name} · Layer ${layer + 1}", p,
                start, end, prog, nextRealm, (realmEnd - p).coerceAtLeast(0), false)
        }

        val width = ((realmEnd - realm.entryScore) / 4.0).coerceAtLeast(1.0)
        val sub = (((p - realm.entryScore) / width).toInt()).coerceIn(0, 3)
        val start = (realm.entryScore + sub * width).toInt()
        val end = (realm.entryScore + (sub + 1) * width).toInt()
        val isPeak = ri == REALMS.lastIndex && sub == 3
        val prog = ((p - start).toFloat() / (end - start).coerceAtLeast(1)).coerceIn(0f, 1f)
        return Rank(realm, SUBSTAGES[sub], "${realm.name} · ${SUBSTAGES[sub]}", p,
            start, if (isPeak) null else end,
            if (isPeak) 1f else prog, nextRealm,
            nextRealm?.let { (it.entryScore - p).coerceAtLeast(0) }, isPeak)
    }
}
