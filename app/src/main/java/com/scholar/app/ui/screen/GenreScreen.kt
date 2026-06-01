package com.scholar.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scholar.app.data.Cultivation
import com.scholar.app.data.content.GenreTerm
import com.scholar.app.di.AppGraph
import com.scholar.app.srs.CardType
import com.scholar.app.ui.theme.Brush
import com.scholar.app.ui.theme.SerifSC
import com.scholar.app.ui.theme.Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val CATEGORY = linkedMapOf(
    "cultivation" to "Cultivation 修炼",
    "techniques" to "Techniques & Arts 功法",
    "items" to "Pills & Artifacts 丹器",
    "society" to "Sects & Ranks 宗门",
    "beings" to "Beings 众生",
    "wuxia" to "Martial World 武侠",
    "register" to "Archaic Register 古语",
)

private data class Term(val t: GenreTerm, val freq: Int?)

@Composable
fun CultivationScreen(graph: AppGraph, onBack: () -> Unit, onOpenChar: (String) -> Unit = {}) {
    val x = Theme.x
    val scope = rememberCoroutineScope()

    val known by graph.known.knownCountFlow().collectAsStateWithLifecycle(0)
    val mastered by graph.cards.masteredCountFlow().collectAsStateWithLifecycle(0)
    val genreLearned by graph.cards.genreLearnedCountFlow().collectAsStateWithLifecycle(0)
    val rank = Cultivation.rankFor(known, mastered, genreLearned)

    var lexicon by remember { mutableStateOf<Map<String, List<Term>>>(emptyMap()) }
    LaunchedEffect(Unit) {
        lexicon = withContext(Dispatchers.IO) {
            graph.dictionary.genreTerms().filter { it.category != "realm" }
                .map { Term(it, graph.dictionary.lookup(it.word)?.freqRank) }
                .groupBy { it.t.category }
                .mapValues { (_, v) -> v.sortedBy { it.freq ?: Int.MAX_VALUE } }
        }
    }

    LazyColumn(Modifier.fillMaxSize().background(x.bg).padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {

        item { ScreenHeader("Cultivation · 修为", onBack = onBack) }

        // ── HERO: current rank ───────────────────────────────────────────
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(x.surface2).padding(22.dp)) {
                Text("YOUR CULTIVATION", color = x.textSoft, fontSize = 11.sp, letterSpacing = 2.sp)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(rank.realm.hanzi, fontFamily = Brush, fontSize = 64.sp, color = x.gold)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(rank.realm.name, fontFamily = SerifSC, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = x.text)
                        Text(rank.stageLabel, color = x.cinnabar, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(rank.realm.note, color = x.textSoft, fontSize = 13.sp, lineHeight = 19.sp)
                Spacer(Modifier.height(16.dp))

                // progress within current sub-stage
                if (rank.isPeak) {
                    Text("You have reached Great Perfection. The mortal world holds nothing more to read.",
                        color = x.gold, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                } else {
                    val span = (rank.stageEnd ?: rank.score) - rank.stageStart
                    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(6.dp)).background(x.bg)) {
                        Box(Modifier.fillMaxWidth(rank.progress).fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(x.jade))
                    }
                    Spacer(Modifier.height(6.dp))
                    val toStage = ((rank.stageEnd ?: rank.score) - rank.score).coerceAtLeast(0)
                    Text("修为 ${rank.score}  ·  $toStage to the next stage" +
                        (rank.nextRealm?.let { nr -> "  ·  ${rank.toNextRealm} to break through to ${nr.name}" } ?: ""),
                        color = x.textFaint, fontSize = 12.sp)
                }
            }
        }

        // ── breakdown of the blend ───────────────────────────────────────
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Contribution("Characters\nknown", known, "+$known", x.gold, Modifier.weight(1f))
                Contribution("Words\nmastered", mastered, "+${(mastered * 0.2).toInt()}", x.jade, Modifier.weight(1f))
                Contribution("Genre terms\nlearned", genreLearned, "+${(genreLearned * 0.5).toInt()}", x.cinnabar, Modifier.weight(1f))
            }
            Text("Your cultivation base blends all three — characters are the spine, mastered words add breadth, and genre terms count double toward fluency in the novels you actually read.",
                color = x.textFaint, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 8.dp))
        }

        // ── the ladder as a scoreboard ───────────────────────────────────
        item {
            Spacer(Modifier.height(6.dp))
            Text("The Ladder", fontFamily = SerifSC, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = x.text)
            Text("Nine realms from mortal to immortal. Your climb is the long one — reaching Tribulation means reading native novels with ease.",
                color = x.textFaint, fontSize = 12.sp, lineHeight = 17.sp)
            Spacer(Modifier.height(4.dp))
        }
        items(Cultivation.REALMS) { r ->
            val attained = r.index < rank.realm.index
            val isCurrent = r.index == rank.realm.index
            RealmRow(hanzi = r.hanzi, name = r.name, note = r.note, entry = r.entryScore,
                attained = attained, isCurrent = isCurrent, stageLabel = if (isCurrent) rank.stageLabel else null,
                last = r.index == Cultivation.REALMS.lastIndex)
        }

        // ── genre lexicon, ordered by difficulty ─────────────────────────
        item {
            Spacer(Modifier.height(14.dp))
            Text("Genre Lexicon", fontFamily = SerifSC, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = x.text)
            Text("The genre's signature vocabulary, easiest (most common) first. Learn these and your cultivation climbs faster. Tap to hear; + to study.",
                color = x.textFaint, fontSize = 12.sp, lineHeight = 17.sp)
            Spacer(Modifier.height(4.dp))
        }
        CATEGORY.forEach { (key, label) ->
            val list = lexicon[key].orEmpty()
            if (list.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(label, fontFamily = SerifSC, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = x.text)
                    Spacer(Modifier.height(4.dp))
                }
                items(list) { tw ->
                    TermRow(tw.t, onOpenChar = onOpenChar, onHear = { graph.speaker.speak(tw.t.word) },
                        onMine = { scope.launch { graph.cards.mine(tw.t.word, "${tw.t.pinyin} · ${tw.t.gloss}", CardType.WORD_RECOGNITION, "Genre: $key") } })
                }
            }
        }
        item { Spacer(Modifier.height(28.dp)) }
    }
}

@Composable
private fun Contribution(label: String, value: Int, contrib: String, accent: Color, modifier: Modifier) {
    val x = Theme.x
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(x.surface).padding(13.dp)) {
        Text("$value", fontFamily = SerifSC, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, color = accent)
        Text(label, color = x.textSoft, fontSize = 11.sp, lineHeight = 14.sp)
        Text("$contrib 修为", color = x.textFaint, fontSize = 10.sp)
    }
}

@Composable
private fun RealmRow(hanzi: String, name: String, note: String, entry: Int, attained: Boolean,
                     isCurrent: Boolean, stageLabel: String?, last: Boolean) {
    val x = Theme.x
    val nodeColor = when { isCurrent -> x.cinnabar; attained -> x.jadeDeep; else -> x.surface2 }
    Row(Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(40.dp)) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(50)).background(nodeColor), contentAlignment = Alignment.Center) {
                Text(if (attained) "✓" else hanzi.take(1), fontFamily = SerifSC,
                    color = if (isCurrent || attained) Color(0xFFF4E4C4) else x.textFaint,
                    fontWeight = FontWeight.Bold, fontSize = if (attained) 16.sp else 18.sp)
            }
            if (!last) Box(Modifier.width(2.dp).height(40.dp).background(if (attained) x.jadeDeep else x.line))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
            .background(if (isCurrent) x.surface2 else x.surface).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$hanzi  $name", fontFamily = SerifSC,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium, fontSize = 16.sp,
                    color = if (!attained && !isCurrent) x.textFaint else x.text)
                Spacer(Modifier.weight(1f))
                if (isCurrent && stageLabel != null) Text(stageLabel, color = x.cinnabar, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                else if (attained) Text("attained", color = x.jade, fontSize = 11.sp)
                else Text("🔒 $entry 修为", color = x.textFaint, fontSize = 11.sp)
            }
            if (isCurrent) {
                Spacer(Modifier.height(4.dp))
                Text(note, color = x.textSoft, fontSize = 12.sp, lineHeight = 17.sp)
            }
        }
    }
}

@Composable
private fun TermRow(t: GenreTerm, onOpenChar: (String) -> Unit, onHear: () -> Unit, onMine: () -> Unit) {
    val x = Theme.x
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(x.surface).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        HanziLinks(t.word, onOpenChar, fontSize = 20.sp, color = x.text, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(8.dp))
        Text("🔊", fontSize = 14.sp, modifier = Modifier.clickable { onHear() })
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(t.pinyin, color = x.gold, fontSize = 14.sp)
            Text(t.gloss, color = x.textSoft, fontSize = 13.sp, lineHeight = 18.sp)
        }
        Text("+", color = x.jade, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onMine() })
    }
}
