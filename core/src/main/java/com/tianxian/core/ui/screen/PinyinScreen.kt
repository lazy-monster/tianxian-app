package com.tianxian.core.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tianxian.core.di.AppGraph
import com.tianxian.core.ui.theme.SerifSC
import com.tianxian.core.ui.theme.Theme

/* Stage 0 — the sound system. Every example is tappable to hear it (TTS). The goal
   is recognition of all initials/finals and confident production of the four tones. */

private data class Tone(val mark: String, val name: String, val desc: String, val example: String, val gloss: String)

private val TONES = listOf(
    Tone("ā", "1st · high level", "Flat and high, like holding a note.", "妈", "mother (mā)"),
    Tone("á", "2nd · rising", "Rises, like a questioning \"huh?\"", "麻", "hemp (má)"),
    Tone("ǎ", "3rd · dipping", "Falls then rises, like \"weeell…\"", "马", "horse (mǎ)"),
    Tone("à", "4th · falling", "Sharp drop, like a firm \"No!\"", "骂", "to scold (mà)"),
)

/** A sound chip: the pinyin symbol plus a common exemplar character that carries it. Tapping
    speaks the *hanzi*, so the zh-CN TTS engine always pronounces real Mandarin — feeding it raw
    latin like "ang" makes many engines spell out letters instead. */
private data class Sound(val symbol: String, val hanzi: String, val pinyin: String)

private val INITIALS = listOf(
    Sound("b", "八", "bā"), Sound("p", "怕", "pà"), Sound("m", "妈", "mā"), Sound("f", "发", "fā"),
    Sound("d", "大", "dà"), Sound("t", "他", "tā"), Sound("n", "拿", "ná"), Sound("l", "拉", "lā"),
    Sound("g", "哥", "gē"), Sound("k", "卡", "kǎ"), Sound("h", "喝", "hē"), Sound("j", "鸡", "jī"),
    Sound("q", "七", "qī"), Sound("x", "西", "xī"), Sound("zh", "知", "zhī"), Sound("ch", "吃", "chī"),
    Sound("sh", "是", "shì"), Sound("r", "日", "rì"), Sound("z", "字", "zì"), Sound("c", "词", "cí"),
    Sound("s", "四", "sì"), Sound("y", "一", "yī"), Sound("w", "我", "wǒ"),
)
private val FINALS = listOf(
    Sound("a", "啊", "ā"), Sound("o", "哦", "ó"), Sound("e", "饿", "è"), Sound("i", "一", "yī"),
    Sound("u", "五", "wǔ"), Sound("ü", "鱼", "yú"), Sound("ai", "爱", "ài"), Sound("ei", "诶", "ēi"),
    Sound("ao", "奥", "ào"), Sound("ou", "偶", "ǒu"), Sound("an", "安", "ān"), Sound("en", "恩", "ēn"),
    Sound("ang", "昂", "áng"), Sound("eng", "风", "fēng"), Sound("ong", "红", "hóng"), Sound("er", "二", "èr"),
    Sound("ia", "牙", "yá"), Sound("ie", "叶", "yè"), Sound("iao", "要", "yào"), Sound("iou", "有", "yǒu"),
    Sound("ian", "言", "yán"), Sound("in", "音", "yīn"), Sound("iang", "羊", "yáng"), Sound("ing", "英", "yīng"),
    Sound("iong", "用", "yòng"), Sound("ua", "蛙", "wā"), Sound("uo", "我", "wǒ"), Sound("uai", "外", "wài"),
    Sound("uei", "为", "wèi"), Sound("uan", "完", "wán"), Sound("uen", "文", "wén"), Sound("uang", "王", "wáng"),
    Sound("ueng", "翁", "wēng"), Sound("üe", "月", "yuè"), Sound("üan", "元", "yuán"), Sound("ün", "云", "yún"),
)

@Composable
fun PinyinScreen(graph: AppGraph, onBack: () -> Unit) {
    val x = Theme.x
    LazyColumn(Modifier.fillMaxSize().background(x.bg).padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { ScreenHeader("Pinyin & Tones", "Pinyin spells the sounds of Mandarin in the Latin alphabet. Learn the four tones first — they change meaning. Tap anything to hear it.", onBack) }

        item { LessonLabel("The four tones (+ a neutral tone)") }
        items4(TONES) { t ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(x.surface)
                .clickable { graph.speaker.speak(t.example) }.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(x.surface2), contentAlignment = Alignment.Center) {
                    Text(t.mark, fontFamily = SerifSC, fontSize = 30.sp, color = x.gold)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.name, color = x.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(t.desc, color = x.textSoft, fontSize = 13.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(t.example, fontFamily = SerifSC, fontSize = 24.sp, color = x.text)
                    Text(t.gloss, color = x.textFaint, fontSize = 11.sp)
                }
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            LessonLabel("Initials — the consonant a syllable starts with")
            FlowChips(INITIALS) { graph.speaker.speak(it.hanzi) }
        }
        item {
            Spacer(Modifier.height(8.dp))
            LessonLabel("Finals — the vowel ending")
            FlowChips(FINALS) { graph.speaker.speak(it.hanzi) }
        }
        item {
            Spacer(Modifier.height(10.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(x.surface2).padding(16.dp)) {
                Text("Tip", color = x.cinnabar, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text("A syllable = initial + final + tone. Practise saying the same syllable in all four tones until the contour feels automatic; tones carry meaning just like the consonants and vowels do.",
                    color = x.textSoft, fontSize = 14.sp, lineHeight = 21.sp)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LessonLabel(text: String) {
    Text(text, fontFamily = SerifSC, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Theme.x.text)
    Spacer(Modifier.height(8.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowChips(chips: List<Sound>, onTap: (Sound) -> Unit) {
    val x = Theme.x
    // wrapping rows, so all the sounds are visible at once instead of hiding in a long side-scroll
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        chips.forEach { s ->
            Column(Modifier.clip(RoundedCornerShape(12.dp)).background(x.surface)
                .clickable { onTap(s) }.padding(horizontal = 13.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(s.symbol, color = x.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text("${s.hanzi} ${s.pinyin}", fontFamily = SerifSC, color = x.gold, fontSize = 11.sp)
            }
        }
    }
}

// small helper: items() for a fixed list inside LazyColumn
private fun <T> androidx.compose.foundation.lazy.LazyListScope.items4(list: List<T>, content: @Composable (T) -> Unit) {
    items(list.size) { content(list[it]) }
}
