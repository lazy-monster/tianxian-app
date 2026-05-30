"""
Curated xianxia / wuxia genre vocabulary.

This is the app's reason for existing, so it is hand-curated rather than scraped:
the highest-utility cultivation & martial-arts terms a reader meets in the first
novels, tagged by category and (for realm terms) by the power-scaling ladder rank.

Format: (word, category, realm_rank)  — realm_rank None unless it is a cultivation stage.
Pinyin + gloss are filled in automatically from CC-CEDICT at build time when available,
with curated fallbacks here for terms CC-CEDICT lacks.
"""

# realm ladder (the power scale every xianxia reader must internalise)
REALMS = [
    ("炼气", 1, "liàn qì", "Qi Refining — the first cultivation stage"),
    ("筑基", 2, "zhù jī", "Foundation Establishment"),
    ("金丹", 3, "jīn dān", "Golden Core"),
    ("元婴", 4, "yuán yīng", "Nascent Soul"),
    ("化神", 5, "huà shén", "Spirit Severing / Deity Transformation"),
    ("炼虚", 6, "liàn xū", "Void Refinement"),
    ("合体", 7, "hé tǐ", "Body Integration"),
    ("大乘", 8, "dà chéng", "Great Ascension"),
    ("渡劫", 9, "dù jié", "Tribulation Crossing"),
]

# (word, category, curated_pinyin_or_None, curated_gloss_or_None)
TERMS = [
    # core cultivation
    ("修炼", "cultivation", "xiū liàn", "to cultivate / train (oneself)"),
    ("修真", "cultivation", "xiū zhēn", "to cultivate the truth; immortal cultivation"),
    ("修仙", "cultivation", "xiū xiān", "to cultivate toward immortality"),
    ("修为", "cultivation", "xiū wéi", "cultivation level / attainment"),
    ("灵气", "cultivation", "líng qì", "spiritual energy of heaven and earth"),
    ("灵力", "cultivation", "líng lì", "spiritual power"),
    ("真气", "cultivation", "zhēn qì", "true qi; cultivated internal energy"),
    ("内力", "wuxia", "nèi lì", "internal force / inner strength"),
    ("丹田", "cultivation", "dān tián", "dantian, the energy center below the navel"),
    ("经脉", "cultivation", "jīng mài", "meridians (energy channels)"),
    ("灵根", "cultivation", "líng gēn", "spiritual root (innate cultivation talent)"),
    ("神识", "cultivation", "shén shí", "spiritual sense / divine consciousness"),
    ("元神", "cultivation", "yuán shén", "primordial spirit / soul"),
    ("天劫", "cultivation", "tiān jié", "heavenly tribulation"),
    ("雷劫", "cultivation", "léi jié", "lightning tribulation"),
    ("突破", "cultivation", "tū pò", "to break through (to a new realm)"),
    ("瓶颈", "cultivation", "píng jǐng", "bottleneck (in cultivation)"),
    ("闭关", "cultivation", "bì guān", "secluded meditation / closed-door cultivation"),
    ("打坐", "cultivation", "dǎ zuò", "to sit in meditation"),
    ("吐纳", "cultivation", "tǔ nà", "breathing exercises (qi circulation)"),
    ("走火入魔", "cultivation", "zǒu huǒ rù mó", "qi deviation; to lose control while cultivating"),
    # pills / artifacts / techniques
    ("丹药", "items", "dān yào", "alchemical pill / elixir"),
    ("灵丹", "items", "líng dān", "spirit pill"),
    ("炼丹", "items", "liàn dān", "to refine pills (alchemy)"),
    ("法器", "items", "fǎ qì", "magic implement / artifact"),
    ("法宝", "items", "fǎ bǎo", "magic treasure"),
    ("灵石", "items", "líng shí", "spirit stone (cultivation currency)"),
    ("功法", "techniques", "gōng fǎ", "cultivation technique / manual"),
    ("心法", "techniques", "xīn fǎ", "core mental cultivation method"),
    ("剑诀", "techniques", "jiàn jué", "sword art / sword formula"),
    ("符箓", "techniques", "fú lù", "talisman / charm"),
    ("阵法", "techniques", "zhèn fǎ", "magic formation / array"),
    # martial / wuxia
    ("武功", "wuxia", "wǔ gōng", "martial arts skill"),
    ("剑法", "wuxia", "jiàn fǎ", "swordsmanship"),
    ("掌法", "wuxia", "zhǎng fǎ", "palm technique"),
    ("轻功", "wuxia", "qīng gōng", "lightness skill (agile movement)"),
    ("江湖", "wuxia", "jiāng hú", "the martial world / 'rivers and lakes'"),
    ("侠客", "wuxia", "xiá kè", "chivalrous swordsman / knight-errant"),
    ("剑光", "wuxia", "jiàn guāng", "the gleam/arc of a sword"),
    # sects / ranks / society
    ("宗门", "society", "zōng mén", "sect / cultivation school"),
    ("门派", "society", "mén pài", "sect / faction"),
    ("长老", "society", "zhǎng lǎo", "elder (of a sect)"),
    ("掌门", "society", "zhǎng mén", "sect leader"),
    ("弟子", "society", "dì zǐ", "disciple"),
    ("师父", "society", "shī fu", "master / teacher"),
    ("师尊", "society", "shī zūn", "honored master"),
    ("师兄", "society", "shī xiōng", "senior martial brother"),
    ("师弟", "society", "shī dì", "junior martial brother"),
    ("仙人", "beings", "xiān rén", "immortal / celestial being"),
    ("妖兽", "beings", "yāo shòu", "demon beast"),
    ("魔修", "beings", "mó xiū", "demonic cultivator"),
    ("散修", "beings", "sǎn xiū", "rogue / unaffiliated cultivator"),
    # archaic / honorific register (trips up every new reader)
    ("在下", "register", "zài xià", "'this humble one' (I/me, modest)"),
    ("阁下", "register", "gé xià", "'Your Excellency' (you, respectful)"),
    ("本座", "register", "běn zuò", "'this one / I' (used by the powerful, arrogant)"),
    ("前辈", "register", "qián bèi", "senior / elder (respectful address)"),
    ("道友", "register", "dào yǒu", "fellow cultivator ('dao friend')"),
]
