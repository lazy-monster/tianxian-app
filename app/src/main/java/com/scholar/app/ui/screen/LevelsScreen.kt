package com.scholar.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scholar.app.data.SettingsStore
import com.scholar.app.data.content.HskWord
import com.scholar.app.di.AppGraph
import com.scholar.app.srs.CardType
import com.scholar.app.ui.theme.SerifSC
import com.scholar.app.ui.theme.Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val LEVELS = listOf(
    "new-1" to "HSK 1", "new-2" to "HSK 2", "new-3" to "HSK 3", "new-4" to "HSK 4",
    "new-5" to "HSK 5", "new-6" to "HSK 6", "new-7" to "HSK 7-9",
)

/** Words learned per group on the guided character track, and the trial score that seals a group. */
private const val STUDY_GROUP = SettingsStore.STUDY_GROUP
private const val CHAR_TRIAL_PASS = 80

private sealed interface LvMode {
    data object Browse : LvMode
    data object Track : LvMode
    data class Session(val group: Int) : LvMode
}

@Composable
fun LevelsScreen(graph: AppGraph, onBack: () -> Unit, onOpenChar: (String) -> Unit) {
    // level + mode hoisted so the chosen level survives entering/leaving the guided track
    var level by remember { mutableStateOf("new-1") }
    var mode by remember { mutableStateOf<LvMode>(LvMode.Browse) }

    when (val m = mode) {
        LvMode.Browse -> LevelsBrowse(graph, level, onLevel = { level = it }, onBack, onOpenChar,
            onTrack = { mode = LvMode.Track })
        LvMode.Track -> CharacterTrackScreen(graph, level, onOpenChar,
            onExit = { mode = LvMode.Browse }, onSession = { g -> mode = LvMode.Session(g) })
        is LvMode.Session -> CharacterSession(graph, level, m.group, onOpenChar,
            onExit = { mode = LvMode.Track })
    }
}

/* ── Browse + free mining (the original list) ─────────────────────────────────────────────── */

@Composable
private fun LevelsBrowse(
    graph: AppGraph, level: String, onLevel: (String) -> Unit,
    onBack: () -> Unit, onOpenChar: (String) -> Unit, onTrack: () -> Unit,
) {
    val x = Theme.x
    val scope = rememberCoroutineScope()
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
            ScreenHeader("Vocabulary by Level", "Words ordered by real-world frequency. Take the guided path to learn in groups, or mine freely below. Tap any character to study it in depth.", onBack)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LEVELS.forEach { (key, label) ->
                    val sel = key == level
                    Box(Modifier.clip(RoundedCornerShape(12.dp)).background(if (sel) x.cinnabar else x.surface)
                        .clickable { onLevel(key) }.padding(horizontal = 16.dp, vertical = 9.dp)) {
                        Text(label, color = if (sel) Color.White else x.textSoft, fontSize = 14.sp,
                            fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            // The recommended structured path: learn sounds + meanings in groups, gated by a trial.
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(x.surface2)
                .clickable { onTrack() }.padding(15.dp)) {
                Text("🧭 Guided cultivation", color = x.text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text("Learn $STUDY_GROUP words at a time — their sounds and meanings — then pass a trial to seal them into review and unlock the next group.",
                    color = x.textSoft, fontSize = 12.sp, lineHeight = 17.sp)
            }
            Spacer(Modifier.height(12.dp))
            Text("Or mine freely", color = x.textFaint, fontSize = 11.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(6.dp))
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
                        minedCount == 0 -> "+ Add first $nextBatch to deck"
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

/* ── Gated character cultivation track ────────────────────────────────────────────────────────
   A level's vocabulary, split into groups of STUDY_GROUP. Each group: learn the sounds + meanings,
   then pass a trial (≥ CHAR_TRIAL_PASS) to seal those cards into the review deck and unlock the
   next group. Mirrors the radical track, but here passing actually mines the cards. */

@Composable
private fun CharacterTrackScreen(
    graph: AppGraph, level: String, onOpenChar: (String) -> Unit,
    onExit: () -> Unit, onSession: (Int) -> Unit,
) {
    val x = Theme.x
    val settings = graph.settings
    var words by remember(level) { mutableStateOf<List<HskWord>>(emptyList()) }
    val mined = remember(level) { mutableStateMapOf<String, Boolean>() }
    LaunchedEffect(level) {
        val loaded = withContext(Dispatchers.IO) { graph.dictionary.hskWords(level, 600) }
        val already = withContext(Dispatchers.IO) { graph.cards.minedAmong(loaded.map { it.word }) }
        words = loaded
        mined.clear()
        already.forEach { mined[it] = true }
    }
    val groups = remember(words) { words.chunked(STUDY_GROUP) }
    val unlocked = settings.hskUnlocked(level)
    val levelLabel = LEVELS.firstOrNull { it.first == level }?.second ?: level

    LazyColumn(Modifier.fillMaxSize().background(x.bg).padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            ScreenHeader("$levelLabel · Character Cultivation",
                "Learn $STUDY_GROUP words at a time, then score ${CHAR_TRIAL_PASS}% on their trial to seal them into your review deck and unlock the next group.", onExit)
        }
        itemsIndexed(groups) { i, group ->
            val best = settings.hskBestScore(level, i)
            val locked = i > unlocked
            val passed = best >= CHAR_TRIAL_PASS
            val isFrontier = i == unlocked && !passed
            val allMined = group.isNotEmpty() && group.all { mined[it.word] == true }
            val first = i * STUDY_GROUP + 1
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(if (isFrontier) x.surface2 else x.surface)
                .clickable(enabled = !locked) { onSession(i) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (locked) x.surface else x.bg), contentAlignment = Alignment.Center) {
                    Text(if (locked) "🔒" else "${i + 1}", fontFamily = SerifSC,
                        fontSize = if (locked) 18.sp else 20.sp, color = if (passed) x.jade else x.gold)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Group ${i + 1}  ·  words $first–${first + group.size - 1}" + if (allMined) "  · in deck" else "",
                        color = if (locked) x.textFaint else x.text, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                    Text(group.take(8).joinToString(" ") { it.word }, fontFamily = SerifSC,
                        color = if (locked) x.textFaint else x.textSoft, fontSize = 15.sp, maxLines = 1)
                    if (best > 0) {
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(5.dp)).background(x.bg)) {
                            Box(Modifier.fillMaxWidth(best / 100f).fillMaxHeight()
                                .clip(RoundedCornerShape(5.dp)).background(if (passed) x.jade else x.gold))
                        }
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    when {
                        passed -> "✓ $best%"
                        locked -> ""
                        best > 0 -> "$best%"
                        isFrontier -> "begin ›"
                        else -> "›"
                    },
                    color = if (passed) x.jade else x.textSoft, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/* ── A study session: learn the group, then trial it ─────────────────────────────────────────── */

private enum class SessPhase { LEARN, TRIAL }

@Composable
private fun CharacterSession(
    graph: AppGraph, level: String, groupIdx: Int, onOpenChar: (String) -> Unit, onExit: () -> Unit,
) {
    val x = Theme.x
    var words by remember(level) { mutableStateOf<List<HskWord>>(emptyList()) }
    LaunchedEffect(level) { words = withContext(Dispatchers.IO) { graph.dictionary.hskWords(level, 600) } }
    val groups = remember(words) { words.chunked(STUDY_GROUP) }
    val group = groups.getOrNull(groupIdx)

    if (words.isEmpty()) {
        Box(Modifier.fillMaxSize().background(x.bg), Alignment.Center) { CircularProgressIndicator(color = x.cinnabar) }
        return
    }
    if (group == null) {
        Box(Modifier.fillMaxSize().background(x.bg), Alignment.Center) {
            Text("‹ back", color = x.gold, fontSize = 14.sp, modifier = Modifier.clickable { onExit() })
        }
        return
    }

    var phase by remember(groupIdx) { mutableStateOf(SessPhase.LEARN) }
    when (phase) {
        SessPhase.LEARN -> CharacterLearn(graph, group, groupIdx, onOpenChar, onExit, onBegin = { phase = SessPhase.TRIAL })
        SessPhase.TRIAL -> CharacterTrial(graph, level, groupIdx, group, words, onOpenChar, onExit)
    }
}

@Composable
private fun CharacterLearn(
    graph: AppGraph, group: List<HskWord>, groupIdx: Int, onOpenChar: (String) -> Unit,
    onExit: () -> Unit, onBegin: () -> Unit,
) {
    val x = Theme.x
    LazyColumn(Modifier.fillMaxSize().background(x.bg).padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            ScreenHeader("Group ${groupIdx + 1} · Learn",
                "Tap 🔊 to hear each word, and any character to study it. When these feel familiar, begin the trial.", onExit)
        }
        items(group) { w ->
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
            }
        }
        item {
            Spacer(Modifier.height(8.dp))
            SessionButton("Begin trial →", x.cinnabar, Color.White, Modifier.fillMaxWidth()) { onBegin() }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/* ── The trial: tests sounds and meanings; passing seals the group into review ───────────────── */

private enum class CKind { HANZI_TO_MEANING, HANZI_TO_PINYIN, SOUND_TO_HANZI, MEANING_TO_HANZI }
private data class CQuestion(val word: HskWord, val kind: CKind, val options: List<String>, val answer: String)

/** First, concise sense of a gloss — short enough to be a recognisable quiz option. */
private fun shortGloss(w: HskWord): String =
    w.meaning.split('/', ';', '；').map { it.trim() }.firstOrNull { it.isNotEmpty() }?.take(40)
        ?: w.meaning.take(40)

private inline fun distractors(pool: List<HskWord>, target: HskWord, field: (HskWord) -> String): List<String> {
    val correct = field(target)
    return pool.asSequence().filter { it.word != target.word }.map(field)
        .filter { it.isNotBlank() }.distinct().filter { it != correct }.toList().shuffled().take(3)
}

/** Every word is tested in all four directions — shape→meaning, shape→reading, sound→shape and
    meaning→shape — so a group trial drills recognition, recall and pronunciation together. */
private fun buildCharTrial(group: List<HskWord>, pool: List<HskWord>): List<CQuestion> =
    group.flatMap { w ->
        listOf(
            CQuestion(w, CKind.HANZI_TO_MEANING,
                (distractors(pool, w) { shortGloss(it) } + shortGloss(w)).shuffled(), shortGloss(w)),
            CQuestion(w, CKind.HANZI_TO_PINYIN,
                (distractors(pool, w) { it.pinyin } + w.pinyin).shuffled(), w.pinyin),
            CQuestion(w, CKind.SOUND_TO_HANZI,
                (distractors(pool, w) { it.word } + w.word).shuffled(), w.word),
            CQuestion(w, CKind.MEANING_TO_HANZI,
                (distractors(pool, w) { it.word } + w.word).shuffled(), w.word),
        )
    }.shuffled()

@Composable
private fun CharacterTrial(
    graph: AppGraph, level: String, groupIdx: Int, group: List<HskWord>, pool: List<HskWord>,
    onOpenChar: (String) -> Unit, onExit: () -> Unit,
) {
    val x = Theme.x
    val settings = graph.settings

    var attempt by remember { mutableStateOf(0) }
    val questions = remember(groupIdx, attempt) { buildCharTrial(group, pool) }
    var qIdx by remember(attempt) { mutableStateOf(0) }
    var selected by remember(attempt) { mutableStateOf<String?>(null) }
    var score by remember(attempt) { mutableStateOf(0) }
    var finished by remember(attempt) { mutableStateOf(false) }
    var finishedPct by remember(attempt) { mutableStateOf(0) }
    var brokeThrough by remember(attempt) { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(x.bg).padding(22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹ groups", color = x.gold, fontSize = 14.sp, modifier = Modifier.clickable { onExit() })
            Spacer(Modifier.width(14.dp))
            if (!finished) {
                Text("${qIdx + 1} / ${questions.size}", color = x.textSoft, fontSize = 12.sp)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(6.dp)).background(x.surface)) {
                    Box(Modifier.fillMaxWidth(qIdx.toFloat() / questions.size).fillMaxHeight()
                        .clip(RoundedCornerShape(6.dp)).background(x.gold))
                }
            } else Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(24.dp))

        if (finished) {
            val passed = finishedPct >= CHAR_TRIAL_PASS
            Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (brokeThrough) "突破！" else if (passed) "通过" else "未过",
                        fontFamily = SerifSC, fontSize = 34.sp, color = if (passed) x.jade else x.cinnabar)
                    Spacer(Modifier.height(10.dp))
                    Text("$finishedPct%  ·  $score / ${questions.size}", color = x.text, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when {
                            brokeThrough -> "Breakthrough — these ${group.size} words are now in your review deck, and the next group is unlocked."
                            passed -> "Passed. These words are in your review deck — re-drill any time."
                            else -> "Reach ${CHAR_TRIAL_PASS}% to seal this group into review. Study again and retry."
                        },
                        color = x.textSoft, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 24.dp))
                    Spacer(Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SessionButton("↻ Retry", x.surface, x.gold, Modifier) { attempt++ }
                        SessionButton("Groups", x.cinnabar, Color.White, Modifier) { onExit() }
                    }
                }
            }
            return@Column
        }

        val q = questions[qIdx]
        val hanziOptions = q.kind == CKind.SOUND_TO_HANZI || q.kind == CKind.MEANING_TO_HANZI
        val optScroll = rememberScrollState()
        LaunchedEffect(qIdx) { optScroll.scrollTo(0) }
        // a sound question speaks the word as it appears (and is replayable)
        LaunchedEffect(qIdx, attempt) {
            if (q.kind == CKind.SOUND_TO_HANZI) graph.speaker.speak(q.word.word)
        }

        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(optScroll)) {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(x.surface2).padding(vertical = 26.dp),
                contentAlignment = Alignment.Center) {
                when (q.kind) {
                    CKind.HANZI_TO_MEANING, CKind.HANZI_TO_PINYIN ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (q.kind == CKind.HANZI_TO_MEANING) "what does this mean?" else "how is this read?",
                                color = x.textFaint, fontSize = 12.sp, letterSpacing = 1.sp)
                            Spacer(Modifier.height(10.dp))
                            Text(q.word.word, fontFamily = SerifSC, fontWeight = FontWeight.SemiBold,
                                fontSize = if (q.word.word.length <= 2) 64.sp else 44.sp, color = x.text)
                            Spacer(Modifier.height(8.dp))
                            Text("🔊", fontSize = 24.sp, modifier = Modifier.clickable { graph.speaker.speak(q.word.word) })
                        }
                    CKind.MEANING_TO_HANZI ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("which word means", color = x.textFaint, fontSize = 12.sp, letterSpacing = 1.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(shortGloss(q.word), color = x.gold, fontSize = 22.sp, fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    CKind.SOUND_TO_HANZI ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("which word did you hear?", color = x.textFaint, fontSize = 12.sp, letterSpacing = 1.sp)
                            Spacer(Modifier.height(14.dp))
                            Box(Modifier.size(82.dp).clip(CircleShape).background(x.cinnabar)
                                .clickable { graph.speaker.speak(q.word.word) }, contentAlignment = Alignment.Center) {
                                Text("🔊", fontSize = 36.sp)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("tap to replay", color = x.textFaint, fontSize = 11.sp, letterSpacing = 1.sp)
                        }
                }
            }
            Spacer(Modifier.height(20.dp))

            q.options.forEach { opt ->
                val reveal = selected != null
                val isAnswer = opt == q.answer
                val bg = when {
                    reveal && isAnswer -> x.jade
                    reveal && opt == selected -> x.cinnabar
                    else -> x.surface
                }
                val fg = if (reveal && (isAnswer || opt == selected)) Color.White else x.text
                Box(Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(14.dp))
                    .background(bg).clickable(enabled = selected == null) {
                        selected = opt
                        if (opt == q.answer) score++
                        graph.speaker.speak(q.word.word)   // hear the word the moment you answer
                    }.padding(vertical = 15.dp, horizontal = 16.dp), contentAlignment = Alignment.Center) {
                    Text(opt, fontFamily = SerifSC,
                        fontSize = if (hanziOptions) 28.sp else 16.sp, color = fg,
                        fontWeight = if (hanziOptions) FontWeight.SemiBold else FontWeight.Normal)
                }
            }

            // reveal card — names the word, its sound and meaning, so every question teaches all three
            if (selected != null) {
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(x.surface).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    HanziLinks(q.word.word, onOpenChar, fontSize = 28.sp, color = x.gold)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(q.word.pinyin, color = x.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(q.word.meaning, color = x.textSoft, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2)
                    }
                    Text("🔊", fontSize = 22.sp, modifier = Modifier.clickable { graph.speaker.speak(q.word.word) })
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        if (selected != null) {
            SessionButton(if (qIdx < questions.lastIndex) "Next →" else "Finish", x.gold, Color.White,
                Modifier.fillMaxWidth()) {
                if (qIdx < questions.lastIndex) { qIdx++; selected = null }
                else {
                    val pct = score * 100 / questions.size
                    finishedPct = pct
                    settings.markStudiedNow()                 // a completed trial counts as studying today
                    settings.setHskBestScore(level, groupIdx, pct)
                    if (pct >= CHAR_TRIAL_PASS) {
                        // seal the whole group into the review deck (idempotent). Use the app scope so
                        // the inserts complete even if the user leaves the result screen immediately.
                        graph.appScope.launch {
                            group.forEach { w ->
                                graph.cards.mine(w.word, "${w.pinyin} · ${w.meaning}",
                                    if (w.word.length == 1) CardType.CHAR_RECOGNITION else CardType.WORD_RECOGNITION,
                                    "HSK $level")
                            }
                        }
                        if (groupIdx == settings.hskUnlocked(level)) {
                            settings.setHskUnlocked(level, groupIdx + 1)
                            brokeThrough = true
                        }
                    }
                    finished = true
                }
            }
        } else {
            Spacer(Modifier.height(52.dp))
        }
    }
}

@Composable
private fun SessionButton(label: String, bg: Color, fg: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(bg).clickable { onClick() }
        .padding(vertical = 14.dp, horizontal = 20.dp), contentAlignment = Alignment.Center) {
        Text(label, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}
