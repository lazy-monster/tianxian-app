package com.scholar.app.data

/**
 * Maps real learning progress onto the xianxia cultivation ladder, so the genre page
 * becomes a living rank rather than a glossary.
 *
 * Cultivation base (修为) blends four signals:
 *   - characters known           (the spine, weight 1.0 — earned through review/marking)
 *   - words mastered via FSRS     (review depth, weight 0.2)
 *   - radicals cultivated         (foundation cleared on the radical track, weight 0.5)
 *   - words sealed on the track   (study credit for passing character trials, weight 0.4)
 *
 * Crucially, **both review and the gated study tracks move the rank**: the tracks grant credit
 * the moment you break through a trial, so a study-only day (no reviews) still advances you;
 * reviews then deepen it as the same characters become "known" and eventually "mastered".
 *
 * The ladder is tuned as a long climb: comfortable native-novel reading lands around the
 * Tribulation realm, and Great Perfection sits well beyond it.
 */
object Cultivation {

    data class Realm(val index: Int, val hanzi: String, val name: String, val entryScore: Int, val note: String)

    val REALMS = listOf(
        Realm(0, "炼气", "Qi Refining", 0, "Drawing qi into the body. The long first climb — pinyin and your first few hundred characters."),
        Realm(1, "筑基", "Foundation Establishment", 150, "A true cultivator. The foundation laid here decides how high you can ever climb."),
        Realm(2, "金丹", "Golden Core", 400, "Qi condenses into a core — you can read simple, graded text without drowning."),
        Realm(3, "元婴", "Nascent Soul", 750, "A second self forms. Sect elders sit here; you can wade into a real novel with a dictionary."),
        Realm(4, "化神", "Spirit Severing", 1150, "The soul merges with heaven and earth. Reading begins to flow."),
        Realm(5, "炼虚", "Void Refinement", 1600, "You grasp the underlying laws — context carries you past unknown words."),
        Realm(6, "合体", "Body Integration", 2100, "Body and law are one. Most chapters open without a fight."),
        Realm(7, "大乘", "Great Ascension", 2600, "The peak of the mortal world, preparing to leave it behind."),
        Realm(8, "渡劫", "Tribulation Crossing", 3000, "Crossing heaven's lightning into immortality — fluent, comfortable native reading."),
    )

    private const val PEAK_CAP = 4200                      // synthetic ceiling for the top realm's sub-stages
    private val QI_LAYERS = listOf(0, 8, 18, 30, 45, 62, 82, 105, 128)   // entry score for Qi Refining layers 1..9
    private val SUBSTAGES = listOf("Early Stage", "Middle Stage", "Late Stage", "Great Perfection")

    fun score(chars: Int, wordsMastered: Int, radicalsCultivated: Int, trackWords: Int): Int =
        (chars + 0.2 * wordsMastered + 0.5 * radicalsCultivated + 0.4 * trackWords).toInt()

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

    fun rankFor(chars: Int, wordsMastered: Int, radicalsCultivated: Int, trackWords: Int): Rank {
        val p = score(chars, wordsMastered, radicalsCultivated, trackWords)
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
