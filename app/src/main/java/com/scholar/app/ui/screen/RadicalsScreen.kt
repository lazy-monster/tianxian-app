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
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scholar.app.data.content.CharInfo
import com.scholar.app.data.content.Radical
import com.scholar.app.data.content.RadicalName
import com.scholar.app.data.content.RadicalNames
import com.scholar.app.di.AppGraph
import com.scholar.app.ui.theme.SerifSC
import com.scholar.app.ui.theme.Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TRIAL_PASS = 85   // % needed to break through a radical batch and unlock the next

private sealed interface RadMode {
    data object Browse : RadMode
    data object Cards : RadMode
    data object Track : RadMode
    data class Trial(val batch: Int) : RadMode
}

@Composable
fun RadicalsScreen(graph: AppGraph, onBack: () -> Unit, onOpenChar: (String) -> Unit) {
    var radicals by remember { mutableStateOf<List<Radical>>(emptyList()) }
    var mode by remember { mutableStateOf<RadMode>(RadMode.Browse) }

    LaunchedEffect(Unit) { radicals = withContext(Dispatchers.IO) { graph.dictionary.radicals() } }

    when (val m = mode) {
        RadMode.Browse -> RadicalsList(graph, radicals, onBack, onOpenChar,
            onFlashcards = { mode = RadMode.Cards }, onTrack = { mode = RadMode.Track })
        RadMode.Cards -> RadicalFlashcards(graph, radicals, onExit = { mode = RadMode.Browse })
        RadMode.Track -> RadicalTrackScreen(graph, radicals,
            onExit = { mode = RadMode.Browse }, onTrial = { b -> mode = RadMode.Trial(b) })
        is RadMode.Trial -> RadicalTrial(graph, radicals, m.batch, onExit = { mode = RadMode.Track })
    }
}

/** Concise meaning for quiz options — drops the parenthetical/variant notes that give answers away. */
private fun shortMeaning(r: Radical): String =
    r.meaning.substringBefore(" (").substringBefore(" / ").trim().ifEmpty { r.meaning }

/** Batches of radicals (sorted by number) at the current batch size. */
private fun batchesOf(radicals: List<Radical>, size: Int): List<List<Radical>> =
    radicals.sortedBy { it.number }.chunked(size.coerceAtLeast(1))

/** The everyday name shown to the user (common teaching name when it differs, else the reading). */
private fun displayName(r: Radical, common: RadicalName?): String =
    common?.let { "${it.name}  ${it.pinyin}" } ?: r.pinyin

/** What the TTS engine should pronounce — the common name's word, falling back to the glyph. */
private fun speakTarget(r: Radical, common: RadicalName?): String = common?.name ?: r.radical

@Composable
private fun RadicalsList(
    graph: AppGraph, radicals: List<Radical>,
    onBack: () -> Unit, onOpenChar: (String) -> Unit, onFlashcards: () -> Unit, onTrack: () -> Unit,
) {
    val x = Theme.x
    var expanded by remember { mutableStateOf<Int?>(null) }
    val examples = remember { mutableStateMapOf<String, List<CharInfo>>() }

    LazyColumn(Modifier.fillMaxSize().background(x.bg).padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            ScreenHeader("Radicals & Components", "The 214 Kangxi radicals are the semantic pieces characters are built from. Knowing them turns memorising into reading meaning. Tap one for example characters.", onBack)
            // Two study paths — both learning-only; radicals never enter your review deck.
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(x.surface2)
                    .clickable { onFlashcards() }.padding(14.dp), contentAlignment = Alignment.Center) {
                    Text("⚡ Flashcards", color = x.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
                Box(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(x.cinnabar)
                    .clickable { onTrack() }.padding(14.dp), contentAlignment = Alignment.Center) {
                    Text("🧭 Cultivation trials", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        items(radicals) { r ->
            val isOpen = expanded == r.number
            val common = RadicalNames.forNumber(r.number)
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(x.surface)
                .clickable { expanded = if (isOpen) null else r.number }.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(x.surface2), contentAlignment = Alignment.Center) {
                        Text(r.radical, fontFamily = SerifSC, fontSize = 26.sp, color = x.gold)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${r.number}. ${r.meaning}", color = x.text, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                        if (common != null) {
                            Text("${common.name}  ${common.pinyin}", color = x.gold, fontSize = 13.sp)
                            if (common.pinyin != r.pinyin)
                                Text("formal reading: ${r.pinyin}", color = x.textFaint, fontSize = 11.sp)
                        } else {
                            Text(r.pinyin, color = x.textSoft, fontSize = 13.sp)
                        }
                    }
                    Text("🔊", fontSize = 16.sp, modifier = Modifier.clickable { graph.speaker.speak(speakTarget(r, common)) })
                    Spacer(Modifier.width(12.dp))
                    Text(if (isOpen) "▾" else "▸", color = x.textFaint, fontSize = 18.sp)
                }
                if (isOpen) {
                    val ex = examples[r.radical]
                    Spacer(Modifier.height(10.dp))
                    if (ex == null) {
                        Text("Loading examples…", color = x.textFaint, fontSize = 12.sp)
                    } else if (ex.isEmpty()) {
                        Text("No common characters indexed for this radical.", color = x.textFaint, fontSize = 12.sp)
                    } else {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ex.forEach { ci ->
                                Column(Modifier.clip(RoundedCornerShape(12.dp)).background(x.surface2)
                                    .clickable { onOpenChar(ci.char) }.padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(ci.char, fontFamily = SerifSC, fontSize = 24.sp, color = x.text)
                                    Text(ci.pinyin.take(8), color = x.gold, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    // load examples when a radical is expanded
    LaunchedEffect(expanded) {
        val r = radicals.firstOrNull { it.number == expanded } ?: return@LaunchedEffect
        if (!examples.containsKey(r.radical)) {
            examples[r.radical] = withContext(Dispatchers.IO) { graph.dictionary.charactersWithRadical(r.radical) }
        }
    }
}

/**
 * A self-contained flashcard session for *learning* the radicals — glyph on the front, name,
 * meaning and pronunciation on the back. Deliberately separate from the SRS: nothing here is ever
 * added to the review deck. "Study again" simply re-queues a card later in the same session.
 */
@Composable
private fun RadicalFlashcards(graph: AppGraph, radicals: List<Radical>, onExit: () -> Unit) {
    val x = Theme.x
    var queue by remember(radicals) { mutableStateOf(radicals.shuffled()) }
    var pos by remember { mutableStateOf(0) }
    var flipped by remember { mutableStateOf(false) }
    var learned by remember { mutableStateOf(0) }

    val card = queue.getOrNull(pos)
    Column(Modifier.fillMaxSize().background(x.bg).padding(22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹ done", color = x.gold, fontSize = 14.sp, modifier = Modifier.clickable { onExit() })
            Spacer(Modifier.width(14.dp))
            if (card != null) {
                Text("${pos + 1} / ${queue.size}", color = x.textSoft, fontSize = 12.sp)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(6.dp)).background(x.surface)) {
                    Box(Modifier.fillMaxWidth(pos.toFloat() / queue.size).fillMaxHeight()
                        .clip(RoundedCornerShape(6.dp)).background(x.gold))
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(22.dp))

        if (card == null) {
            Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("学完了", fontFamily = SerifSC, fontSize = 30.sp, color = x.gold)
                    Spacer(Modifier.height(8.dp))
                    Text("Studied $learned radicals this round.", color = x.textSoft, fontSize = 14.sp)
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FlashButton("↻ Again", x.surface, x.text, Modifier) {
                            queue = radicals.shuffled(); pos = 0; flipped = false; learned = 0
                        }
                        FlashButton("Done", x.cinnabar, Color.White, Modifier) { onExit() }
                    }
                }
            }
            return@Column
        }

        val common = RadicalNames.forNumber(card.number)
        val cardScroll = rememberScrollState()
        LaunchedEffect(card.number, flipped) { cardScroll.scrollTo(0) }
        Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(x.surface2)
            .clickable { flipped = true }, contentAlignment = Alignment.Center) {
            Column(Modifier.verticalScroll(cardScroll).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(card.radical, fontFamily = SerifSC, fontWeight = FontWeight.SemiBold, fontSize = 96.sp, color = x.text)
                if (flipped) {
                    Spacer(Modifier.height(18.dp))
                    Text(displayName(card, common), color = x.gold, fontSize = 24.sp, fontWeight = FontWeight.Medium)
                    if (common != null && common.pinyin != card.pinyin) {
                        Spacer(Modifier.height(4.dp))
                        Text("formal reading: ${card.pinyin}", color = x.textFaint, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("#${card.number} · ${card.meaning}", color = x.text, fontSize = 16.sp,
                        modifier = Modifier.padding(horizontal = 16.dp))
                    Spacer(Modifier.height(14.dp))
                    Text("🔊", fontSize = 26.sp, modifier = Modifier.clickable { graph.speaker.speak(speakTarget(card, common)) })
                } else {
                    Spacer(Modifier.height(18.dp))
                    Text("tap to reveal", color = x.textFaint, fontSize = 12.sp, letterSpacing = 2.sp)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        if (flipped) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FlashButton("Study again", x.surface, x.gold, Modifier.weight(1f)) {
                    queue = queue + card; pos++; flipped = false
                }
                FlashButton("Got it", x.jade, Color.White, Modifier.weight(1f)) {
                    pos++; flipped = false; learned++
                }
            }
        } else {
            Spacer(Modifier.height(50.dp))
        }
    }
}

@Composable
private fun FlashButton(label: String, bg: Color, fg: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(bg).clickable { onClick() }
        .padding(vertical = 14.dp, horizontal = 20.dp), contentAlignment = Alignment.Center) {
        Text(label, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

/* ── Gated radical cultivation track ─────────────────────────────────────────────────────────
   Radicals are split into batches ("trials"). Each can be drilled for a score; the track gates
   progression — you only unlock the next trial by scoring ≥ TRIAL_PASS on the current frontier. */

@Composable
private fun RadicalTrackScreen(graph: AppGraph, radicals: List<Radical>, onExit: () -> Unit, onTrial: (Int) -> Unit) {
    val x = Theme.x
    val settings = graph.settings
    val batches = remember(radicals, settings.radicalBatchSize) { batchesOf(radicals, settings.radicalBatchSize) }
    val unlocked = settings.radicalUnlocked   // read live (refreshes when re-entering after a trial)

    LazyColumn(Modifier.fillMaxSize().background(x.bg).padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            ScreenHeader("Radical Cultivation", "The 214 radicals, split into trials. Score ${TRIAL_PASS}% on a trial to break through and unlock the next. Earlier trials stay open to re-drill.", onExit)
        }
        itemsIndexed(batches) { i, batch ->
            val best = settings.radicalBestScore(i)
            val locked = i > unlocked
            val passed = best >= TRIAL_PASS
            val isFrontier = i == unlocked && !passed
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(if (isFrontier) x.surface2 else x.surface)
                .clickable(enabled = !locked) { onTrial(i) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (locked) x.surface else x.bg), contentAlignment = Alignment.Center) {
                    Text(if (locked) "🔒" else "${i + 1}", fontFamily = SerifSC,
                        fontSize = if (locked) 18.sp else 20.sp, color = if (passed) x.jade else x.gold)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Trial ${i + 1}  ·  radicals ${batch.first().number}–${batch.last().number}",
                        color = if (locked) x.textFaint else x.text, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                    Text(batch.take(8).joinToString(" ") { it.radical }, fontFamily = SerifSC,
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

/** What a trial question asks. The two sound directions — hear→shape and shape→reading — are what
    make the trials genuinely teach pronunciation, not just shape↔meaning. */
private enum class RKind { GLYPH_TO_MEANING, MEANING_TO_GLYPH, SOUND_TO_GLYPH, GLYPH_TO_SOUND }

/** One multiple-choice question. [answer] is a meaning, a glyph, or a reading depending on [kind]. */
private data class RQuestion(val prompt: Radical, val kind: RKind, val options: List<String>, val answer: String)

/** The spoken reading shown as a quiz option: common name + pinyin, else the formal reading. */
private fun readingLabel(r: Radical): String =
    RadicalNames.forNumber(r.number)?.let { "${it.name} ${it.pinyin}" } ?: r.pinyin

/** Three distinct wrong options for [r], projected through [field] (meaning, glyph, or reading). */
private inline fun radDistractors(all: List<Radical>, r: Radical, field: (Radical) -> String): List<String> {
    val correct = field(r)
    return all.asSequence().filter { it.number != r.number }.map(field)
        .filter { it.isNotBlank() }.distinct().filter { it != correct }.toList().shuffled().take(3)
}

/** Every radical is tested in all four directions — shape→meaning, meaning→shape, sound→shape and
    shape→reading — so one trial drills recognition, recall and pronunciation together. */
private fun buildTrial(batch: List<Radical>, all: List<Radical>): List<RQuestion> =
    batch.flatMap { r ->
        listOf(
            RQuestion(r, RKind.GLYPH_TO_MEANING,
                (radDistractors(all, r) { shortMeaning(it) } + shortMeaning(r)).shuffled(), shortMeaning(r)),
            RQuestion(r, RKind.MEANING_TO_GLYPH,
                (radDistractors(all, r) { it.radical } + r.radical).shuffled(), r.radical),
            RQuestion(r, RKind.SOUND_TO_GLYPH,
                (radDistractors(all, r) { it.radical } + r.radical).shuffled(), r.radical),
            RQuestion(r, RKind.GLYPH_TO_SOUND,
                (radDistractors(all, r) { readingLabel(it) } + readingLabel(r)).shuffled(), readingLabel(r)),
        )
    }.shuffled()

@Composable
private fun RadicalTrial(graph: AppGraph, radicals: List<Radical>, batchIndex: Int, onExit: () -> Unit) {
    val x = Theme.x
    val settings = graph.settings
    val batches = remember(radicals, settings.radicalBatchSize) { batchesOf(radicals, settings.radicalBatchSize) }
    val batch = batches.getOrNull(batchIndex)

    if (batch == null || batch.isEmpty()) {
        Box(Modifier.fillMaxSize().background(x.bg), Alignment.Center) {
            Text("‹ back", color = x.gold, fontSize = 14.sp, modifier = Modifier.clickable { onExit() })
        }
        return
    }

    var attempt by remember { mutableStateOf(0) }
    val questions = remember(batchIndex, attempt) { buildTrial(batch, radicals) }
    var qIdx by remember(attempt) { mutableStateOf(0) }
    var selected by remember(attempt) { mutableStateOf<String?>(null) }
    var score by remember(attempt) { mutableStateOf(0) }
    var finished by remember(attempt) { mutableStateOf(false) }
    var finishedPct by remember(attempt) { mutableStateOf(0) }
    var brokeThrough by remember(attempt) { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(x.bg).padding(22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹ trials", color = x.gold, fontSize = 14.sp, modifier = Modifier.clickable { onExit() })
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
            val passed = finishedPct >= TRIAL_PASS
            Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (brokeThrough) "突破！" else if (passed) "通过" else "未过",
                        fontFamily = SerifSC, fontSize = 34.sp, color = if (passed) x.jade else x.cinnabar)
                    Spacer(Modifier.height(10.dp))
                    Text("$finishedPct%  ·  $score / ${questions.size}", color = x.text, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when {
                            brokeThrough -> "Breakthrough — the next trial is unlocked."
                            passed -> "Passed. Re-drill any time to keep it sharp."
                            else -> "Reach ${TRIAL_PASS}% to break through. Almost there — try again."
                        },
                        color = x.textSoft, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 24.dp))
                    Spacer(Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FlashButton("↻ Retry", x.surface, x.gold, Modifier) { attempt++ }
                        FlashButton("Trials", x.cinnabar, Color.White, Modifier) { onExit() }
                    }
                }
            }
            return@Column
        }

        val q = questions[qIdx]
        val common = RadicalNames.forNumber(q.prompt.number)
        val glyphOptions = q.kind == RKind.MEANING_TO_GLYPH || q.kind == RKind.SOUND_TO_GLYPH   // pick a glyph
        val optScroll = rememberScrollState()
        LaunchedEffect(qIdx) { optScroll.scrollTo(0) }
        // a sound question speaks the radical's name as it appears (and is replayable below)
        LaunchedEffect(qIdx, attempt) {
            if (q.kind == RKind.SOUND_TO_GLYPH) graph.speaker.speak(speakTarget(q.prompt, common))
        }
        // prompt + options scroll if they don't fit, so the Next button is always reachable
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(optScroll)) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(x.surface2).padding(vertical = 28.dp),
            contentAlignment = Alignment.Center) {
            when (q.kind) {
                RKind.GLYPH_TO_MEANING ->
                    Text(q.prompt.radical, fontFamily = SerifSC, fontWeight = FontWeight.SemiBold, fontSize = 88.sp, color = x.text)
                RKind.MEANING_TO_GLYPH ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("which radical means", color = x.textFaint, fontSize = 12.sp, letterSpacing = 1.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(shortMeaning(q.prompt), color = x.gold, fontSize = 26.sp, fontWeight = FontWeight.Medium)
                    }
                RKind.SOUND_TO_GLYPH ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("which radical sounds like this", color = x.textFaint, fontSize = 12.sp, letterSpacing = 1.sp)
                        Spacer(Modifier.height(14.dp))
                        Box(Modifier.size(82.dp).clip(CircleShape).background(x.cinnabar)
                            .clickable { graph.speaker.speak(speakTarget(q.prompt, common)) }, contentAlignment = Alignment.Center) {
                            Text("🔊", fontSize = 36.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("tap to replay", color = x.textFaint, fontSize = 11.sp, letterSpacing = 1.sp)
                    }
                RKind.GLYPH_TO_SOUND ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("how is this radical read?", color = x.textFaint, fontSize = 12.sp, letterSpacing = 1.sp)
                        Spacer(Modifier.height(10.dp))
                        Text(q.prompt.radical, fontFamily = SerifSC, fontWeight = FontWeight.SemiBold, fontSize = 76.sp, color = x.text)
                    }
            }
        }
        Spacer(Modifier.height(20.dp))

        // options
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
                    graph.speaker.speak(speakTarget(q.prompt, common))   // hear it the moment you answer
                }.padding(vertical = 15.dp, horizontal = 16.dp), contentAlignment = Alignment.Center) {
                Text(opt, fontFamily = SerifSC,
                    fontSize = if (glyphOptions) 30.sp else 16.sp, color = fg,
                    fontWeight = if (glyphOptions) FontWeight.SemiBold else FontWeight.Normal)
            }
        }

        // reveal card — always names the radical and lets you replay its sound, so every
        // question reinforces glyph ↔ name ↔ pronunciation regardless of what was asked.
        if (selected != null) {
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(x.surface).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(q.prompt.radical, fontFamily = SerifSC, fontSize = 30.sp, color = x.gold)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(displayName(q.prompt, common), color = x.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text("#${q.prompt.number} · ${q.prompt.meaning}", color = x.textSoft, fontSize = 12.sp, maxLines = 2)
                }
                Text("🔊", fontSize = 22.sp, modifier = Modifier.clickable { graph.speaker.speak(speakTarget(q.prompt, common)) })
            }
        }
        }   // end scrollable prompt+options column

        Spacer(Modifier.height(12.dp))
        if (selected != null) {
            FlashButton(if (qIdx < questions.lastIndex) "Next →" else "Finish", x.gold, Color.White,
                Modifier.fillMaxWidth()) {
                if (qIdx < questions.lastIndex) { qIdx++; selected = null }
                else {
                    val pct = score * 100 / questions.size
                    finishedPct = pct
                    settings.markStudiedNow()                 // a completed trial counts as studying today
                    settings.setRadicalBestScore(batchIndex, pct)
                    if (pct >= TRIAL_PASS && batchIndex == settings.radicalUnlocked) {
                        settings.radicalUnlocked = batchIndex + 1
                        brokeThrough = true
                    }
                    finished = true
                }
            }
        } else {
            Spacer(Modifier.height(52.dp))
        }
    }
}
