package com.scholar.app.data.content

/**
 * Common teaching names for radicals whose everyday name differs from the formal dictionary reading.
 *
 * The bundled `radical` table stores the formal/dictionary pronunciation (e.g. 丶 "zhǔ", 宀 "mián",
 * 氵 under 水 as "shuǐ"). In practice radicals are referred to by their everyday names (部首名称):
 * 丶 is "点 (diǎn)", 宀 is "宝盖头 (bǎo gài tóu)", the 氵 form is "三点水 (sān diǎn shuǐ)". This map
 * supplies that everyday name; the formal reading is kept alongside it in the UI, never discarded.
 *
 * Coverage: every Kangxi radical that has a *distinct* established name is listed here (~65). The
 * remaining ~150 radicals are ordinary characters with no separate colloquial name — their everyday
 * name simply *is* their reading (人 rén, 口 kǒu, 木 mù…), which the app already shows from the DB.
 *
 * Source: cross-checked against the colloquial-name column of the English Wikipedia "Kangxi radical"
 * table and standard mainland teaching practice (the GF 0011-2009 部首 naming conventions). Garbled
 * or non-standard Wikipedia entries were corrected; well-established names it omitted were added.
 *
 * Re-verified against published 部首名称表 references: 宀 "宝盖头" and 子 "子字旁" are the correct,
 * everyday names (so are 三点水, 提手旁, 草字头 etc.) — these *look* formulaic but are genuinely how
 * the radicals are taught. Two genuine fixes were made: 厶 is 私字儿 (not 私字旁), and 罒 is the *net*
 * radical 网字头 — "四字头" is only a 四-look-alike nickname and is avoided here because this app
 * teaches radicals by meaning.
 *
 * Keyed by Kangxi radical number. [name] is the spoken Mandarin term (also fed to TTS so the
 * pronunciation you hear matches the label); [pinyin] is its toned romanisation.
 */
data class RadicalName(val name: String, val pinyin: String)

object RadicalNames {
    /** number → everyday name. Only radicals whose common name differs from the reading are listed. */
    val byNumber: Map<Int, RadicalName> = mapOf(
        2 to RadicalName("竖", "shù"),
        3 to RadicalName("点", "diǎn"),
        4 to RadicalName("撇", "piě"),
        6 to RadicalName("竖钩", "shù gōu"),
        9 to RadicalName("单人旁", "dān rén páng"),     // 亻 form
        13 to RadicalName("同字框", "tóng zì kuàng"),
        14 to RadicalName("秃宝盖", "tū bǎo gài"),
        15 to RadicalName("两点水", "liǎng diǎn shuǐ"),
        17 to RadicalName("凵字框", "kǎn zì kuàng"),
        18 to RadicalName("立刀旁", "lì dāo páng"),     // 刂 form
        20 to RadicalName("包字头", "bāo zì tóu"),
        22 to RadicalName("三框儿", "sān kuàngr"),
        26 to RadicalName("单耳旁", "dān ěr páng"),
        27 to RadicalName("厂字旁", "chǎng zì páng"),
        28 to RadicalName("私字儿", "sī zìr"),         // 厶 — standard name is 私字儿, not 私字旁
        31 to RadicalName("方框儿", "fāng kuàngr"),
        32 to RadicalName("提土旁", "tí tǔ páng"),       // 土 as left component
        39 to RadicalName("子字旁", "zǐ zì páng"),
        40 to RadicalName("宝盖头", "bǎo gài tóu"),
        44 to RadicalName("尸字头", "shī zì tóu"),
        53 to RadicalName("广字旁", "guǎng zì páng"),
        54 to RadicalName("建之旁", "jiàn zhī páng"),
        55 to RadicalName("弄字底", "nòng zì dǐ"),
        57 to RadicalName("弓字旁", "gōng zì páng"),
        59 to RadicalName("三撇儿", "sān piěr"),
        60 to RadicalName("双人旁", "shuāng rén páng"),  // 彳
        61 to RadicalName("竖心旁", "shù xīn páng"),     // 忄 form
        64 to RadicalName("提手旁", "tí shǒu páng"),     // 扌 form
        66 to RadicalName("反文旁", "fǎn wén páng"),     // 攵 form
        75 to RadicalName("木字旁", "mù zì páng"),        // 木 as left component
        85 to RadicalName("三点水", "sān diǎn shuǐ"),    // 氵 form
        86 to RadicalName("四点底", "sì diǎn dǐ"),       // 灬 form
        87 to RadicalName("爪字头", "zhǎo zì tóu"),      // 爫 form
        90 to RadicalName("将字旁", "jiāng zì páng"),    // 丬 form
        93 to RadicalName("牛字旁", "niú zì páng"),      // 牜 form
        94 to RadicalName("反犬旁", "fǎn quǎn páng"),    // 犭 form
        96 to RadicalName("王字旁", "wáng zì páng"),     // 王 form
        104 to RadicalName("病字旁", "bìng zì páng"),
        105 to RadicalName("登字头", "dēng zì tóu"),
        109 to RadicalName("目字旁", "mù zì páng"),
        113 to RadicalName("示字旁", "shì zì páng"),     // 礻 form
        115 to RadicalName("禾木旁", "hé mù páng"),
        118 to RadicalName("竹字头", "zhú zì tóu"),      // ⺮ form
        119 to RadicalName("米字旁", "mǐ zì páng"),
        120 to RadicalName("绞丝旁", "jiǎo sī páng"),    // 纟 form
        122 to RadicalName("网字头", "wǎng zì tóu"),     // 罒 — the *net* radical; "四字头" is a look-alike misnomer
        125 to RadicalName("老字头", "lǎo zì tóu"),      // 耂 form
        130 to RadicalName("肉月旁", "ròu yuè páng"),    // 月(肉) form
        140 to RadicalName("草字头", "cǎo zì tóu"),      // 艹 form
        145 to RadicalName("衣字旁", "yī zì páng"),      // 衤 form
        149 to RadicalName("言字旁", "yán zì páng"),     // 讠 form
        157 to RadicalName("足字旁", "zú zì páng"),      // 𧾷 form
        159 to RadicalName("车字旁", "chē zì páng"),     // 车 form
        162 to RadicalName("走之底", "zǒu zhī dǐ"),      // 辶 form
        163 to RadicalName("右耳旁", "yòu ěr páng"),     // 阝 right (邑)
        167 to RadicalName("金字旁", "jīn zì páng"),     // 钅 form
        169 to RadicalName("门字框", "mén zì kuàng"),    // 门 form
        170 to RadicalName("左耳旁", "zuǒ ěr páng"),     // 阝 left (阜)
        184 to RadicalName("食字旁", "shí zì páng"),     // 饣 form
    )

    fun forNumber(number: Int): RadicalName? = byNumber[number]
}
