package com.scholar.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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

@Composable
fun RadicalsScreen(graph: AppGraph, onBack: () -> Unit, onOpenChar: (String) -> Unit) {
    var radicals by remember { mutableStateOf<List<Radical>>(emptyList()) }
    var studying by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { radicals = withContext(Dispatchers.IO) { graph.dictionary.radicals() } }

    if (studying) {
        RadicalFlashcards(graph, radicals, onExit = { studying = false })
    } else {
        RadicalsList(graph, radicals, onBack, onOpenChar, onStudy = { studying = true })
    }
}

/** The everyday name shown to the user (common teaching name when it differs, else the reading). */
private fun displayName(r: Radical, common: RadicalName?): String =
    common?.let { "${it.name}  ${it.pinyin}" } ?: r.pinyin

/** What the TTS engine should pronounce — the common name's word, falling back to the glyph. */
private fun speakTarget(r: Radical, common: RadicalName?): String = common?.name ?: r.radical

@Composable
private fun RadicalsList(
    graph: AppGraph, radicals: List<Radical>,
    onBack: () -> Unit, onOpenChar: (String) -> Unit, onStudy: () -> Unit,
) {
    val x = Theme.x
    var expanded by remember { mutableStateOf<Int?>(null) }
    val examples = remember { mutableStateMapOf<String, List<CharInfo>>() }

    LazyColumn(Modifier.fillMaxSize().background(x.bg).padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            ScreenHeader("Radicals & Components", "The 214 Kangxi radicals are the semantic pieces characters are built from. Knowing them turns memorising into reading meaning. Tap one for example characters.", onBack)
            // Flashcard study — learning only; these never enter your review deck.
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(x.cinnabar)
                .clickable { onStudy() }.padding(14.dp), contentAlignment = Alignment.Center) {
                Text("⚡ Study with flashcards", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
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
