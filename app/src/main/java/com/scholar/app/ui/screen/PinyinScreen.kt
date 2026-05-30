package com.scholar.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scholar.app.di.AppGraph
import com.scholar.app.ui.theme.SerifSC
import com.scholar.app.ui.theme.Theme

/* Stage 0 — the sound system. Every example is tappable to hear it (TTS). The goal
   is recognition of all initials/finals and confident production of the four tones. */

private data class Tone(val mark: String, val name: String, val desc: String, val example: String, val gloss: String)

private val TONES = listOf(
    Tone("ā", "1st · high level", "Flat and high, like holding a note.", "妈", "mother (mā)"),
    Tone("á", "2nd · rising", "Rises, like a questioning \"huh?\"", "麻", "hemp (má)"),
    Tone("ǎ", "3rd · dipping", "Falls then rises, like \"weeell…\"", "马", "horse (mǎ)"),
    Tone("à", "4th · falling", "Sharp drop, like a firm \"No!\"", "骂", "to scold (mà)"),
)

private val INITIALS = listOf(
    "b","p","m","f","d","t","n","l","g","k","h","j","q","x","zh","ch","sh","r","z","c","s","y","w",
)
private val FINALS = listOf(
    "a","o","e","i","u","ü","ai","ei","ao","ou","an","en","ang","eng","ong","er",
    "ia","ie","iao","iou","ian","in","iang","ing","iong","ua","uo","uai","uei","uan","uen","uang","ueng","üe","üan","ün",
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
            FlowChips(INITIALS) { graph.speaker.speak(it + "a") }
        }
        item {
            Spacer(Modifier.height(8.dp))
            LessonLabel("Finals — the vowel ending")
            FlowChips(FINALS) { graph.speaker.speak(it) }
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

@Composable
private fun FlowChips(chips: List<String>, onTap: (String) -> Unit) {
    val x = Theme.x
    // simple wrapping rows of chips
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(chips.size) { idx ->
            val s = chips[idx]
            Box(Modifier.clip(RoundedCornerShape(12.dp)).background(x.surface)
                .clickable { onTap(s) }.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(s, color = x.text, fontSize = 16.sp)
            }
        }
    }
}

// small helper: items() for a fixed list inside LazyColumn
private fun <T> androidx.compose.foundation.lazy.LazyListScope.items4(list: List<T>, content: @Composable (T) -> Unit) {
    items(list.size) { content(list[it]) }
}
