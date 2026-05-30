package com.scholar.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scholar.app.di.AppGraph
import com.scholar.app.srs.CardType
import com.scholar.app.ui.theme.SerifSC
import com.scholar.app.ui.theme.Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class Tok(val text: String, val isWord: Boolean, val start: Int, val end: Int)
private data class Popup(val word: String, val pinyin: String, val gloss: String, val tags: List<String>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(graph: AppGraph, bookId: String, onBack: () -> Unit, onOpenChar: (String) -> Unit) {
    val x = Theme.x
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var title by remember { mutableStateOf("") }
    var chapters by remember { mutableStateOf<List<com.scholar.app.model.Chapter>>(emptyList()) }
    var chapterIdx by remember { mutableStateOf(0) }
    var known by remember { mutableStateOf<Set<String>>(emptySet()) }
    var popup by remember { mutableStateOf<Popup?>(null) }

    // load document + segmenter vocabulary + known set off the main thread
    LaunchedEffect(bookId) {
        withContext(Dispatchers.IO) {
            val doc = graph.books.document(bookId)
            val knownSet = graph.known.knownSet()
            withContext(Dispatchers.Main) {
                title = doc?.title ?: "Reader"
                chapters = doc?.chapters ?: emptyList()
                chapterIdx = 0   // TODO: restore saved position from BookEntity
                known = knownSet
                loading = false
            }
        }
    }

    if (loading) {
        Box(Modifier.fillMaxSize().background(x.bg), Alignment.Center) { CircularProgressIndicator(color = x.cinnabar) }
        return
    }
    if (chapters.isEmpty()) {
        Box(Modifier.fillMaxSize().background(x.bg), Alignment.Center) {
            Text("No readable text in this book.", color = x.textSoft)
        }
        return
    }

    val chapter = chapters[chapterIdx.coerceIn(0, chapters.lastIndex)]
    val chapterText = remember(chapter) { chapter.blocks.joinToString("\n\n") { it.text } }

    // segment the chapter (segmenter vocab load is cached in AppGraph)
    var toks by remember(chapter) { mutableStateOf<List<Tok>>(emptyList()) }
    LaunchedEffect(chapter) {
        toks = withContext(Dispatchers.IO) {
            graph.segmenter().segment(chapterText).map { Tok(it.text, it.isWord, it.start, it.start + it.text.length) }
        }
    }

    // coverage of this chapter
    val coverage = remember(toks, known) {
        val han = chapterText.count { it.code in 0x4E00..0x9FFF }
        val knownHan = chapterText.count { it.code in 0x4E00..0x9FFF && it.toString() in known }
        if (han == 0) 0f else knownHan.toFloat() / han
    }

    val annotated = remember(toks, known) {
        buildAnnotatedString {
            toks.forEach { t ->
                val style = when {
                    !t.isWord -> SpanStyle(color = x.text)
                    t.text.all { it.toString() in known } -> SpanStyle(color = x.textFaint)
                    else -> SpanStyle(color = x.text, fontWeight = FontWeight.Medium)
                }
                if (t.isWord) pushStringAnnotation("w", t.text)
                withStyle(style) { append(t.text) }
                if (t.isWord) pop()
            }
        }
    }

    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }

    Box(Modifier.fillMaxSize().background(x.bg)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp).padding(top = 14.dp, bottom = 40.dp)) {

            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("‹ back", color = x.gold, fontSize = 14.sp, modifier = Modifier.clickable { onBack() })
                Text(title, fontFamily = SerifSC, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                    color = x.text, maxLines = 1)
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(4.dp)).background(x.surface)) {
                Box(Modifier.fillMaxWidth(coverage).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(x.jade))
            }
            Spacer(Modifier.height(6.dp))
            Text("${(coverage * 100).toInt()}% of characters here are yours · tap any word",
                color = x.textSoft, fontSize = 12.sp)
            Spacer(Modifier.height(18.dp))
            chapter.title?.let {
                Text(it, fontFamily = SerifSC, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = x.text)
                Spacer(Modifier.height(16.dp))
            }

            Text(
                text = annotated, color = x.text,
                fontFamily = SerifSC, fontSize = 21.sp, lineHeight = 40.sp,
                onTextLayout = { layout = it },
                modifier = Modifier.pointerInput(annotated) {
                    detectTapGestures { pos ->
                        val off = layout?.getOffsetForPosition(pos) ?: return@detectTapGestures
                        annotated.getStringAnnotations("w", off, off).firstOrNull()?.let { ann ->
                            scope.launch { popup = buildPopup(graph, ann.item) }
                        }
                    }
                },
            )

            Spacer(Modifier.height(26.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                NavBtn("‹ prev", enabled = chapterIdx > 0) {
                    chapterIdx--; scope.launch { graph.books.savePosition(bookId, chapterIdx, 0) }
                }
                Text("${chapterIdx + 1} / ${chapters.size}", color = x.textSoft, fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.CenterVertically))
                NavBtn("next ›", enabled = chapterIdx < chapters.lastIndex) {
                    chapterIdx++; scope.launch { graph.books.savePosition(bookId, chapterIdx, 0) }
                }
            }
        }

        popup?.let { p ->
            ModalBottomSheet(onDismissRequest = { popup = null }, containerColor = x.surface2) {
                Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 28.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(p.word, fontFamily = SerifSC, fontWeight = FontWeight.SemiBold, fontSize = 40.sp, color = x.text)
                        Spacer(Modifier.width(12.dp))
                        Text(p.pinyin, color = x.gold, fontSize = 20.sp)
                        Spacer(Modifier.weight(1f))
                        Text("🔊", fontSize = 24.sp, modifier = Modifier.clickable { graph.speaker.speak(p.word) })
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(p.gloss, color = x.text, fontSize = 15.sp, lineHeight = 22.sp)
                    if (p.word.length == 1) {
                        Spacer(Modifier.height(10.dp))
                        Text("View character: components & writing ›", color = x.jade, fontSize = 14.sp,
                            modifier = Modifier.clickable { val c = p.word; popup = null; onOpenChar(c) })
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        p.tags.forEach { tag ->
                            Surface(color = x.surface, shape = RoundedCornerShape(20.dp)) {
                                Text(tag, color = x.textSoft, fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = {
                            scope.launch {
                                graph.cards.mine(p.word, "${p.pinyin} · ${p.gloss}",
                                    if (p.word.length == 1) CardType.CHAR_RECOGNITION else CardType.WORD_RECOGNITION, title)
                                popup = null
                            }
                        }, colors = ButtonDefaults.buttonColors(containerColor = x.cinnabar),
                            modifier = Modifier.weight(1f)) { Text("+ Add to deck", color = Color.White) }
                        OutlinedButton(onClick = {
                            scope.launch { graph.known.markKnown(p.word); known = graph.known.knownSet(); popup = null }
                        }, modifier = Modifier.weight(1f)) { Text("Mark known", color = x.text) }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavBtn(label: String, enabled: Boolean, onClick: () -> Unit) {
    val x = Theme.x
    Text(label, color = if (enabled) x.gold else x.textFaint, fontSize = 15.sp,
        modifier = Modifier.clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onClick() }.padding(horizontal = 14.dp, vertical = 8.dp))
}

private suspend fun buildPopup(graph: AppGraph, word: String): Popup = withContext(Dispatchers.IO) {
    val entry = graph.dictionary.lookup(word)
    if (entry != null) {
        val tags = buildList {
            entry.freqRank?.let { add("freq #$it") }
            if (word.length == 1) add("char")
        }
        Popup(entry.simplified, graph.dictionary.toned(entry.pinyin), entry.gloss, tags)
    } else {
        val ci = graph.dictionary.character(word)
        Popup(word, ci?.pinyin ?: "", ci?.definition ?: "(no entry)",
            buildList { ci?.radical?.takeIf { it.isNotEmpty() }?.let { add("radical $it") } })
    }
}
