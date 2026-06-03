package com.scholar.app.ui.screen

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scholar.app.di.AppGraph
import com.scholar.app.model.Chapter
import com.scholar.app.model.ImageBlock
import com.scholar.app.model.TextBlock
import com.scholar.app.reader.ingest.ImageStore
import com.scholar.app.srs.CardType
import com.scholar.app.ui.theme.ReaderColors
import com.scholar.app.ui.theme.SerifSC
import com.scholar.app.ui.theme.Theme
import com.scholar.app.ui.theme.readerFont
import com.scholar.app.ui.theme.readerPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private data class Tok(val text: String, val isWord: Boolean, val start: Int, val end: Int)
private data class Popup(val word: String, val pinyin: String, val gloss: String,
                         val tags: List<String>, val examples: List<com.scholar.app.data.content.Sentence>)
/** One spoken unit: which text block, the char range within it, and the words to read. */
private data class Utt(val block: Int, val range: IntRange, val text: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(graph: AppGraph, bookId: String, onBack: () -> Unit, onOpenChar: (String) -> Unit) {
    val x = Theme.x
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = graph.settings

    var loading by remember { mutableStateOf(true) }
    var title by remember { mutableStateOf("") }
    var chapters by remember { mutableStateOf<List<Chapter>>(emptyList()) }
    var chapterIdx by remember { mutableStateOf(0) }
    var known by remember { mutableStateOf<Set<String>>(emptySet()) }
    var popup by remember { mutableStateOf<Popup?>(null) }
    var showControls by remember { mutableStateOf(false) }

    // live reader preferences (write-through to settings so changes persist + recompose immediately)
    var fontKey by remember { mutableStateOf(settings.readerFontKey) }
    var fontSize by remember { mutableStateOf(settings.readerFontSizeSp) }
    var lineMul by remember { mutableStateOf(settings.readerLineHeight) }
    var themeKey by remember { mutableStateOf(settings.readerThemeKey) }
    var ttsRate by remember { mutableStateOf(settings.readerTtsRate) }
    val palette = readerPalette(themeKey, x)
    val fontFamily = readerFont(fontKey)
    val imgDir = remember(bookId) { ImageStore.dir(context, bookId) }

    // read-aloud state
    var speaking by remember { mutableStateOf(false) }
    var speakBlock by remember { mutableStateOf(-1) }
    var speakRange by remember { mutableStateOf<IntRange?>(null) }
    var pendingAutoplay by remember { mutableStateOf(false) }
    var restoreBlock by remember { mutableStateOf(-1) }   // scroll target from the saved position, once

    // load document + known set off the main thread, then restore the saved position
    LaunchedEffect(bookId) {
        val doc = withContext(Dispatchers.IO) { graph.books.document(bookId) }
        val knownSet = withContext(Dispatchers.IO) { graph.known.knownSet() }
        val pos = withContext(Dispatchers.IO) { graph.books.position(bookId) }
        title = doc?.title ?: "Reader"
        chapters = doc?.chapters ?: emptyList()
        chapterIdx = pos.chapter.coerceIn(0, (chapters.size - 1).coerceAtLeast(0))
        restoreBlock = pos.block          // restore scroll within the chapter on first layout
        known = knownSet
        loading = false
    }

    // stop any read-aloud when leaving the reader
    DisposableEffect(Unit) { onDispose { graph.speaker.stop() } }

    if (loading) {
        Box(Modifier.fillMaxSize().background(palette.bg), Alignment.Center) { CircularProgressIndicator(color = x.cinnabar) }
        return
    }
    if (chapters.isEmpty()) {
        Box(Modifier.fillMaxSize().background(palette.bg), Alignment.Center) {
            Text("No readable content in this book.", color = palette.textSoft)
        }
        return
    }

    val chapter = chapters[chapterIdx.coerceIn(0, chapters.lastIndex)]

    // segment each text block into tappable tokens (offsets are relative to that block's text)
    var blockToks by remember(chapter) { mutableStateOf<Map<Int, List<Tok>>>(emptyMap()) }
    LaunchedEffect(chapter) {
        blockToks = withContext(Dispatchers.IO) {
            chapter.blocks.mapIndexedNotNull { i, b ->
                if (b is TextBlock) i to graph.segmenter().segment(b.text)
                    .map { Tok(it.text, it.isWord, it.start, it.start + it.text.length) } else null
            }.toMap()
        }
    }

    // sentences to read aloud, in order, carrying their block + char range for highlighting
    val utts = remember(chapter) {
        buildList {
            chapter.blocks.forEachIndexed { i, b ->
                if (b is TextBlock) sentencesWithRanges(b.text).forEach { (r, s) -> add(Utt(i, r, s)) }
            }
        }
    }

    // chapter coverage (known Han / total Han across text blocks)
    val coverage = remember(chapter, known) {
        var han = 0; var knownHan = 0
        chapter.blocks.forEach { b ->
            if (b is TextBlock) b.text.forEach { c ->
                if (c.code in 0x4E00..0x9FFF) { han++; if (c.toString() in known) knownHan++ }
            }
        }
        if (han == 0) 0f else knownHan.toFloat() / han
    }

    val lazyState = rememberLazyListState()
    val hi = x.gold.copy(alpha = 0.25f)

    fun stopPlay() { graph.speaker.stop(); speaking = false; speakBlock = -1; speakRange = null }

    fun play(from: Int) {
        if (utts.isEmpty()) {
            // image-only chapter: just advance if there's more, else nothing to read
            if (chapterIdx < chapters.lastIndex) { chapterIdx++; pendingAutoplay = true }
            return
        }
        speaking = true
        graph.speaker.setRate(ttsRate)
        graph.speaker.speakSequence(
            utts.drop(from).map { it.text },
            onIndex = { i ->
                val u = utts[from + i]
                speakBlock = u.block; speakRange = u.range
                scope.launch { runCatching { lazyState.animateScrollToItem(u.block + 1) } }
            },
            onDone = {
                speaking = false; speakBlock = -1; speakRange = null
                if (chapterIdx < chapters.lastIndex) { chapterIdx++; pendingAutoplay = true }
            },
        )
    }

    // continue read-aloud into a freshly auto-advanced chapter
    LaunchedEffect(chapterIdx) {
        if (pendingAutoplay) { pendingAutoplay = false; play(0) }
    }

    // one-time scroll to the saved position within the restored chapter (item 0 is the header)
    LaunchedEffect(chapter) {
        val rb = restoreBlock
        if (rb > 0) {
            runCatching { lazyState.scrollToItem((rb + 1).coerceAtMost(chapter.blocks.size)) }
            restoreBlock = -1
        }
    }

    // persist reading position as the user scrolls (block ≈ first visible item, minus the header)
    LaunchedEffect(chapter) {
        snapshotFlow { lazyState.firstVisibleItemIndex }.distinctUntilChanged().collect { idx ->
            graph.books.savePosition(bookId, chapterIdx, (idx - 1).coerceAtLeast(0))
        }
    }

    Box(Modifier.fillMaxSize().background(palette.bg)) {
        LazyColumn(state = lazyState, modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp),
            contentPadding = PaddingValues(top = 14.dp, bottom = 96.dp)) {

            item {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text("‹ back", color = x.gold, fontSize = 14.sp, modifier = Modifier.clickable { stopPlay(); onBack() })
                    Text(title, fontFamily = SerifSC, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                        color = palette.text, maxLines = 1)
                }
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(4.dp)).background(x.surface)) {
                    Box(Modifier.fillMaxWidth(coverage).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(x.jade))
                }
                Spacer(Modifier.height(6.dp))
                Text("${(coverage * 100).toInt()}% of characters here are yours · tap any word",
                    color = palette.textSoft, fontSize = 12.sp)
                Spacer(Modifier.height(18.dp))
                chapter.title?.let {
                    Text(it, fontFamily = fontFamily, fontWeight = FontWeight.Bold, fontSize = (fontSize + 2).sp, color = palette.text)
                    Spacer(Modifier.height(16.dp))
                }
            }

            itemsIndexed(chapter.blocks) { i, block ->
                when (block) {
                    is TextBlock -> TextBlockView(
                        toks = blockToks[i] ?: emptyList(), raw = block.text, known = known, palette = palette,
                        fontFamily = fontFamily, fontSize = fontSize, lineMul = lineMul,
                        highlight = if (i == speakBlock) speakRange else null, highlightColor = hi,
                        onWord = { w -> scope.launch { popup = buildPopup(graph, w) } },
                    )
                    is ImageBlock -> BookImage(File(imgDir, block.file), block.caption, palette)
                }
                Spacer(Modifier.height(if (block is ImageBlock) 6.dp else 2.dp))
            }

            item {
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    NavBtn("‹ prev", enabled = chapterIdx > 0) {
                        stopPlay(); chapterIdx--; scope.launch { graph.books.savePosition(bookId, chapterIdx, 0); lazyState.scrollToItem(0) }
                    }
                    Text("${chapterIdx + 1} / ${chapters.size}", color = palette.textSoft, fontSize = 13.sp,
                        modifier = Modifier.align(Alignment.CenterVertically))
                    NavBtn("next ›", enabled = chapterIdx < chapters.lastIndex) {
                        stopPlay(); chapterIdx++; scope.launch { graph.books.savePosition(bookId, chapterIdx, 0); lazyState.scrollToItem(0) }
                    }
                }
            }
        }

        // floating controls: read-aloud + typography
        Column(Modifier.align(Alignment.BottomEnd).padding(18.dp), horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            RoundBtn("Aa", x.surface2, x.text) { showControls = true }
            RoundBtn(if (speaking) "❚❚" else "▶", x.cinnabar, Color.White) {
                if (speaking) stopPlay() else play(0)
            }
        }

        popup?.let { p -> WordSheet(graph, p, title, onOpenChar, onDismiss = { popup = null },
            onMarkKnown = { scope.launch { graph.known.markKnown(p.word); known = graph.known.knownSet(); popup = null } }) }

        if (showControls) ReaderControls(
            x = x, fontKey = fontKey, fontSize = fontSize, lineMul = lineMul, themeKey = themeKey, ttsRate = ttsRate,
            onFont = { fontKey = it; settings.readerFontKey = it },
            onSize = { fontSize = it; settings.readerFontSizeSp = it },
            onLine = { lineMul = it; settings.readerLineHeight = it },
            onTheme = { themeKey = it; settings.readerThemeKey = it },
            onRate = { ttsRate = it; settings.readerTtsRate = it; if (speaking) graph.speaker.setRate(it) },
            onDismiss = { showControls = false },
        )
    }
}

/** A paragraph rendered as individually tappable words, with the read-aloud sentence highlighted. */
@Composable
private fun TextBlockView(
    toks: List<Tok>, raw: String, known: Set<String>, palette: ReaderColors,
    fontFamily: FontFamily, fontSize: Int, lineMul: Float,
    highlight: IntRange?, highlightColor: Color, onWord: (String) -> Unit,
) {
    val annotated = remember(toks, known, highlight, palette) {
        buildAnnotatedString {
            if (toks.isEmpty()) { append(raw); return@buildAnnotatedString }
            toks.forEach { t ->
                val lit = highlight != null && t.start <= highlight.last && t.end > highlight.first
                val style = when {
                    lit -> SpanStyle(color = palette.text, background = highlightColor, fontWeight = FontWeight.Medium)
                    !t.isWord -> SpanStyle(color = palette.text)
                    t.text.all { it.toString() in known } -> SpanStyle(color = palette.textFaint)
                    else -> SpanStyle(color = palette.text, fontWeight = FontWeight.Medium)
                }
                if (t.isWord) pushStringAnnotation("w", t.text)
                withStyle(style) { append(t.text) }
                if (t.isWord) pop()
            }
        }
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = annotated, color = palette.text, fontFamily = fontFamily,
        fontSize = fontSize.sp, lineHeight = (fontSize * lineMul).sp,
        onTextLayout = { layout = it },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).pointerInput(annotated) {
            detectTapGestures { pos ->
                val off = layout?.getOffsetForPosition(pos) ?: return@detectTapGestures
                annotated.getStringAnnotations("w", off, off).firstOrNull()?.let { onWord(it.item) }
            }
        },
    )
}

/** A page image, decoded + downsampled off the main thread to avoid OOM on large scans/comics. */
@Composable
private fun BookImage(file: File, caption: String?, palette: ReaderColors) {
    var bmp by remember(file.path) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(file.path) { bmp = withContext(Dispatchers.IO) { decodeDownsampled(file, 1440) } }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        val b = bmp
        if (b != null) {
            Image(bitmap = b, contentDescription = caption, contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)))
        } else {
            Box(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(8.dp)).background(palette.textFaint.copy(alpha = 0.08f)),
                Alignment.Center) { CircularProgressIndicator(color = palette.textFaint, strokeWidth = 2.dp) }
        }
        caption?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, color = palette.textFaint, fontSize = 12.sp)
        }
    }
}

private fun decodeDownsampled(file: File, reqWidth: Int): ImageBitmap? {
    if (!file.exists()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= reqWidth) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return runCatching { BitmapFactory.decodeFile(file.path, opts)?.asImageBitmap() }.getOrNull()
}

/** Split text into sentences, returning each with its char range in [text] (for highlighting). */
private fun sentencesWithRanges(text: String): List<Pair<IntRange, String>> {
    val enders = "。！？!?；;\n".toSet()
    val out = ArrayList<Pair<IntRange, String>>()
    var start = 0
    for (i in text.indices) {
        if (text[i] in enders) {
            val s = text.substring(start, i + 1).trim()
            if (s.isNotEmpty()) out.add((start..i) to s)
            start = i + 1
        }
    }
    if (start < text.length) {
        val s = text.substring(start).trim()
        if (s.isNotEmpty()) out.add((start until text.length).let { start..(text.length - 1) } to s)
    }
    return out
}

@Composable
private fun RoundBtn(label: String, bg: Color, fg: Color, onClick: () -> Unit) {
    Box(Modifier.size(52.dp).clip(CircleShape).background(bg).clickable { onClick() }, Alignment.Center) {
        Text(label, color = fg, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun NavBtn(label: String, enabled: Boolean, onClick: () -> Unit) {
    val x = Theme.x
    Text(label, color = if (enabled) x.gold else x.textFaint, fontSize = 15.sp,
        modifier = Modifier.clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onClick() }.padding(horizontal = 14.dp, vertical = 8.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderControls(
    x: com.scholar.app.ui.theme.XColors, fontKey: String, fontSize: Int, lineMul: Float, themeKey: String, ttsRate: Float,
    onFont: (String) -> Unit, onSize: (Int) -> Unit, onLine: (Float) -> Unit, onTheme: (String) -> Unit,
    onRate: (Float) -> Unit, onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = x.surface2) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 28.dp)) {
            Text("Reading", fontFamily = SerifSC, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = x.text)
            Spacer(Modifier.height(14.dp))

            ControlLabel("Font")
            ChipRow(listOf("serif" to "Serif", "sans" to "Sans", "kai" to "Kai 楷", "mono" to "Mono"), fontKey, onFont)
            Spacer(Modifier.height(16.dp))

            ControlLabel("Text size")
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepBtn("A−") { onSize((fontSize - 1).coerceAtLeast(16)) }
                Spacer(Modifier.width(14.dp))
                Text("$fontSize sp", color = x.gold, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(14.dp))
                StepBtn("A+") { onSize((fontSize + 1).coerceAtMost(34)) }
            }
            Spacer(Modifier.height(16.dp))

            ControlLabel("Line spacing")
            Slider(value = lineMul, onValueChange = onLine, valueRange = 1.4f..2.4f,
                colors = SliderDefaults.colors(thumbColor = x.cinnabar, activeTrackColor = x.cinnabar, inactiveTrackColor = x.surface))
            Spacer(Modifier.height(8.dp))

            ControlLabel("Theme")
            ChipRow(listOf("follow" to "App", "ink" to "Ink", "paper" to "Paper", "sepia" to "Sepia", "oled" to "Black"), themeKey, onTheme)
            Spacer(Modifier.height(16.dp))

            ControlLabel("Read-aloud speed  ·  ${"%.2f".format(ttsRate)}×")
            Slider(value = ttsRate, onValueChange = onRate, valueRange = 0.5f..1.6f,
                colors = SliderDefaults.colors(thumbColor = x.cinnabar, activeTrackColor = x.cinnabar, inactiveTrackColor = x.surface))
        }
    }
}

@Composable
private fun ControlLabel(t: String) {
    Text(t, color = Theme.x.textSoft, fontSize = 12.sp, letterSpacing = 1.sp)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun ChipRow(options: List<Pair<String, String>>, selected: String, onPick: (String) -> Unit) {
    val x = Theme.x
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (key, label) ->
            val sel = key == selected
            Box(Modifier.clip(RoundedCornerShape(12.dp)).background(if (sel) x.cinnabar else x.surface)
                .clickable { onPick(key) }.padding(horizontal = 16.dp, vertical = 9.dp)) {
                Text(label, color = if (sel) Color.White else x.textSoft, fontSize = 14.sp,
                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun StepBtn(label: String, onClick: () -> Unit) {
    val x = Theme.x
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(x.surface).clickable { onClick() }, Alignment.Center) {
        Text(label, color = x.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WordSheet(graph: AppGraph, p: Popup, source: String, onOpenChar: (String) -> Unit,
                      onDismiss: () -> Unit, onMarkKnown: () -> Unit) {
    val x = Theme.x
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = x.surface2) {
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
            if (p.examples.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text("IN CONTEXT", color = x.textFaint, fontSize = 11.sp, letterSpacing = 2.sp)
                Spacer(Modifier.height(6.dp))
                p.examples.forEach { s ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(x.surface)
                        .clickable { graph.speaker.speak(s.zh) }.padding(11.dp)) {
                        Text(s.zh, fontFamily = SerifSC, color = x.text, fontSize = 16.sp, lineHeight = 24.sp)
                        Spacer(Modifier.height(3.dp))
                        Text(s.en, color = x.textSoft, fontSize = 12.sp, lineHeight = 17.sp)
                    }
                    Spacer(Modifier.height(7.dp))
                }
            }
            if (p.word.length == 1) {
                Spacer(Modifier.height(10.dp))
                Text("View character: components & writing ›", color = x.jade, fontSize = 14.sp,
                    modifier = Modifier.clickable { val c = p.word; onDismiss(); onOpenChar(c) })
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
                            if (p.word.length == 1) CardType.CHAR_RECOGNITION else CardType.WORD_RECOGNITION, source)
                        onDismiss()
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = x.cinnabar),
                    modifier = Modifier.weight(1f)) { Text("+ Add to deck", color = Color.White) }
                OutlinedButton(onClick = onMarkKnown, modifier = Modifier.weight(1f)) { Text("Mark known", color = x.text) }
            }
        }
    }
}

private suspend fun buildPopup(graph: AppGraph, word: String): Popup = withContext(Dispatchers.IO) {
    val examples = graph.dictionary.examples(word, 3)
    val entry = graph.dictionary.lookup(word)
    if (entry != null) {
        val tags = buildList {
            entry.freqRank?.let { add("freq #$it") }
            if (word.length == 1) add("char")
        }
        Popup(entry.simplified, graph.dictionary.toned(entry.pinyin), entry.gloss, tags, examples)
    } else {
        val ci = graph.dictionary.character(word)
        Popup(word, ci?.pinyin ?: "", ci?.definition ?: "(no entry)",
            buildList { ci?.radical?.takeIf { it.isNotEmpty() }?.let { add("radical $it") } }, examples)
    }
}
