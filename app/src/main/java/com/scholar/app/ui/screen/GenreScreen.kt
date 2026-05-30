package com.scholar.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.scholar.app.data.content.GenreTerm
import com.scholar.app.di.AppGraph
import com.scholar.app.srs.CardType
import com.scholar.app.ui.theme.SerifSC
import com.scholar.app.ui.theme.Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val CATEGORY = linkedMapOf(
    "cultivation" to ("Cultivation 修炼" to "The core verbs and nouns of the cultivation process itself."),
    "techniques" to ("Techniques & Arts 功法" to "What characters train in and fight with."),
    "items" to ("Pills & Artifacts 丹器" to "The treasures that drive plots — elixirs, weapons, spirit tools."),
    "wuxia" to ("Martial World 武侠" to "Terms shared with the grounded martial-arts (wuxia) genre."),
    "society" to ("Sects & Ranks 宗门" to "How the cultivation world is organised — sects, elders, disciples."),
    "beings" to ("Beings 众生" to "Immortals, demons, beasts, and everything in between."),
    "register" to ("Archaic Register 古语" to "Old-style pronouns and address that pervade the genre's dialogue."),
)

// Narrative context for each rung — what this stage *means* in a story.
private val REALM_NOTE = mapOf(
    1 to "Where every protagonist begins — drawing qi into the body. Mortals can't sense qi at all.",
    2 to "A true cultivator at last. Lifespan extends, and the foundation laid here caps how high one can climb.",
    3 to "Qi condenses into a core. A real power in any mortal city; can fly short distances.",
    4 to "A second self forms and survives bodily death. This is where sect elders usually sit.",
    5 to "The soul begins to merge with heaven and earth, reshaping the space nearby.",
    6 to "Starts to grasp the underlying laws of reality — often a sect's hidden ancestor.",
    7 to "Body and law become one; a single such being can threaten a whole nation.",
    8 to "The peak of the mortal world, preparing to leave it behind.",
    9 to "Heaven sends down lightning to stop the ascension. Survive it, and you transcend to immortality.",
)

@Composable
fun GenreScreen(graph: AppGraph, onBack: () -> Unit) {
    val x = Theme.x
    val scope = rememberCoroutineScope()
    var terms by remember { mutableStateOf<List<GenreTerm>>(emptyList()) }
    LaunchedEffect(Unit) { terms = withContext(Dispatchers.IO) { graph.dictionary.genreTerms() } }

    val realms = terms.filter { it.category == "realm" }.sortedBy { it.realmRank ?: 0 }
    val byCat = terms.filter { it.category != "realm" }.groupBy { it.category }

    LazyColumn(Modifier.fillMaxSize().background(x.bg).padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            ScreenHeader("Genre Path · 修仙", "Almost every xianxia story is built on a cultivation ladder — a fixed sequence of power realms. Learn the rungs and you can place any character's strength the instant their realm is named.", onBack)
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(x.surface2).padding(16.dp)) {
                Text("Why this matters", color = x.cinnabar, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text("Realm names are the genre's load-bearing vocabulary: they appear constantly, signal stakes, and gate the plot. Systems vary between novels, but the shape below is near-universal. Tap a character to hear it; tap + to start learning it.",
                    color = x.textSoft, fontSize = 14.sp, lineHeight = 21.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text("The Cultivation Ladder", fontFamily = SerifSC, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = x.text)
            Spacer(Modifier.height(4.dp))
        }

        itemsIndexed(realms) { i, r ->
            val rank = r.realmRank ?: (i + 1)
            RealmRung(rank, r, note = REALM_NOTE[rank], last = i == realms.lastIndex,
                onHear = { graph.speaker.speak(r.word) },
                onMine = { scope.launch { graph.cards.mine(r.word, "${r.pinyin} · ${r.gloss}", CardType.WORD_RECOGNITION, "Realm ladder") } })
        }

        CATEGORY.forEach { (key, pair) ->
            val list = byCat[key].orEmpty()
            if (list.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(12.dp))
                    Text(pair.first, fontFamily = SerifSC, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = x.text)
                    Text(pair.second, color = x.textFaint, fontSize = 12.sp, lineHeight = 17.sp)
                    Spacer(Modifier.height(6.dp))
                }
                items(list) { t ->
                    TermRow(t, onHear = { graph.speaker.speak(t.word) },
                        onMine = { scope.launch { graph.cards.mine(t.word, "${t.pinyin} · ${t.gloss}", CardType.WORD_RECOGNITION, "Genre: $key") } })
                }
            }
        }
        item { Spacer(Modifier.height(28.dp)) }
    }
}

@Composable
private fun RealmRung(rank: Int, r: GenreTerm, note: String?, last: Boolean, onHear: () -> Unit, onMine: () -> Unit) {
    val x = Theme.x
    Row(Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(40.dp)) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(50)).background(x.cinnabarDeep), contentAlignment = Alignment.Center) {
                Text("$rank", color = Color(0xFFF4E4C4), fontFamily = SerifSC, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            if (!last) Box(Modifier.width(2.dp).height(56.dp).background(x.line))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(x.surface).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.word, fontFamily = SerifSC, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = x.text,
                    modifier = Modifier.clickable { onHear() })
                Spacer(Modifier.width(10.dp))
                Text(r.pinyin, color = x.gold, fontSize = 14.sp)
                Spacer(Modifier.weight(1f))
                Text("+", color = x.jade, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onMine() })
            }
            Text(r.gloss, color = x.textSoft, fontSize = 13.sp)
            if (note != null) {
                Spacer(Modifier.height(4.dp))
                Text(note, color = x.textFaint, fontSize = 12.sp, lineHeight = 17.sp)
            }
        }
    }
}

@Composable
private fun TermRow(t: GenreTerm, onHear: () -> Unit, onMine: () -> Unit) {
    val x = Theme.x
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(x.surface).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(t.word, fontFamily = SerifSC, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = x.text,
            modifier = Modifier.clickable { onHear() })
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.pinyin, color = x.gold, fontSize = 14.sp)
            Text(t.gloss, color = x.textSoft, fontSize = 13.sp, lineHeight = 18.sp)
        }
        Text("+", color = x.jade, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onMine() })
    }
}
