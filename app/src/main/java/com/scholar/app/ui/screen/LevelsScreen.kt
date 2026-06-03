package com.scholar.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import com.scholar.app.data.content.HskWord
import com.scholar.app.di.AppGraph
import com.scholar.app.srs.CardType
import com.scholar.app.ui.theme.Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val LEVELS = listOf(
    "new-1" to "HSK 1", "new-2" to "HSK 2", "new-3" to "HSK 3", "new-4" to "HSK 4",
    "new-5" to "HSK 5", "new-6" to "HSK 6", "new-7" to "HSK 7-9",
)

@Composable
fun LevelsScreen(graph: AppGraph, onBack: () -> Unit, onOpenChar: (String) -> Unit) {
    val x = Theme.x
    val scope = rememberCoroutineScope()
    var level by remember { mutableStateOf("new-1") }
    var words by remember { mutableStateOf<List<HskWord>>(emptyList()) }
    val mined = remember { mutableStateMapOf<String, Boolean>() }
    var adding by remember { mutableStateOf(false) }

    // (Re)load the level, then sync which of its words are already in the deck so the counts
    // and the "add next N" button survive app restarts and reflect reality.
    LaunchedEffect(level) {
        val loaded = withContext(Dispatchers.IO) { graph.dictionary.hskWords(level, 300) }
        val already = withContext(Dispatchers.IO) { graph.cards.minedAmong(loaded.map { it.word }) }
        words = loaded
        mined.clear()
        already.forEach { mined[it] = true }
    }

    suspend fun add(word: HskWord) {
        graph.cards.mine(word.word, "${word.pinyin} · ${word.meaning}",
            if (word.word.length == 1) CardType.CHAR_RECOGNITION else CardType.WORD_RECOGNITION, "HSK $level")
        mined[word.word] = true
    }

    val batch = graph.settings.hskBatchSize
    val minedCount = words.count { mined[it.word] == true }
    val remaining = words.size - minedCount
    val nextBatch = (remaining).coerceAtMost(batch)

    LazyColumn(Modifier.fillMaxSize().background(x.bg).padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            ScreenHeader("Vocabulary by Level", "Words ordered by real-world frequency. Mine a few each day; the most useful come first. Tap any character to study it in depth.", onBack)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LEVELS.forEach { (key, label) ->
                    val sel = key == level
                    Box(Modifier.clip(RoundedCornerShape(12.dp)).background(if (sel) x.cinnabar else x.surface)
                        .clickable { level = key }.padding(horizontal = 16.dp, vertical = 9.dp)) {
                        Text(label, color = if (sel) Color.White else x.textSoft, fontSize = 14.sp,
                            fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            // progress through the level's deck-able words
            if (words.isNotEmpty()) {
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(6.dp)).background(x.surface)) {
                    Box(Modifier.fillMaxWidth(minedCount.toFloat() / words.size).fillMaxHeight()
                        .clip(RoundedCornerShape(6.dp)).background(x.jade))
                }
                Spacer(Modifier.height(6.dp))
                Text("$minedCount of ${words.size} in your deck", color = x.textFaint, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
            }
            val enabled = nextBatch > 0 && !adding
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(if (enabled) x.surface2 else x.surface)
                .clickable(enabled = enabled) {
                    adding = true
                    scope.launch {
                        words.filter { mined[it.word] != true }.take(batch).forEach { add(it) }
                        adding = false
                    }
                }.padding(13.dp), contentAlignment = Alignment.Center) {
                Text(
                    when {
                        adding -> "Adding…"
                        remaining == 0 -> "✓ Whole level is in your deck"
                        minedCount == 0 -> "+ Study this level (add first $nextBatch)"
                        else -> "+ Add next $nextBatch to deck"
                    },
                    color = if (enabled) x.text else x.textSoft, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            }
            Spacer(Modifier.height(4.dp))
        }
        items(words) { w ->
            val isMined = mined[w.word] == true
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(x.surface).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                HanziLinks(w.word, onOpenChar, fontSize = 26.sp, color = x.text)
                Spacer(Modifier.width(10.dp))
                Text("🔊", fontSize = 16.sp, modifier = Modifier.clickable { graph.speaker.speak(w.word) })
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(w.pinyin, color = x.gold, fontSize = 14.sp)
                    Text(w.meaning, color = x.textSoft, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 2)
                }
                Box(Modifier.clip(RoundedCornerShape(10.dp)).background(if (isMined) x.surface2 else x.cinnabar)
                    .clickable(enabled = !isMined) { scope.launch { add(w) } }
                    .padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(if (isMined) "✓" else "+", color = if (isMined) x.jade else Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
