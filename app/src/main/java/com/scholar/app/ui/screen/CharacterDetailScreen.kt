package com.scholar.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scholar.app.data.content.CharInfo
import com.scholar.app.data.content.Gloss
import com.scholar.app.data.content.Sentence
import com.scholar.app.di.AppGraph
import com.scholar.app.srs.CardType
import com.scholar.app.ui.theme.SerifSC
import com.scholar.app.ui.theme.Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CharacterDetailScreen(
    graph: AppGraph, ch: String, onBack: () -> Unit,
    onPractice: (String) -> Unit, onOpenChar: (String) -> Unit,
) {
    val x = Theme.x
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<CharInfo?>(null) }
    var components by remember { mutableStateOf<List<CharInfo>>(emptyList()) }
    var hasStrokes by remember { mutableStateOf(false) }
    var examples by remember { mutableStateOf<List<Sentence>>(emptyList()) }
    var mined by remember { mutableStateOf(false) }

    LaunchedEffect(ch) {
        withContext(Dispatchers.IO) {
            info = graph.dictionary.character(ch)
            components = graph.dictionary.components(ch)
            hasStrokes = graph.dictionary.strokeData(ch) != null
            examples = graph.dictionary.examples(ch)
            mined = graph.cards.minedAmong(listOf(ch)).isNotEmpty()
        }
    }

    Column(Modifier.fillMaxSize().background(x.bg).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp)) {
        ScreenHeader("Character", onBack = onBack)

        // hero
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(110.dp).clip(RoundedCornerShape(24.dp)).background(x.surface), contentAlignment = Alignment.Center) {
                Text(ch, fontFamily = SerifSC, fontSize = 72.sp, color = x.text)
            }
            Spacer(Modifier.width(18.dp))
            Column {
                Text(info?.pinyin?.ifBlank { "—" } ?: "…", fontFamily = SerifSC, color = x.gold, fontSize = 24.sp)
                val tags = buildList {
                    info?.hsk3?.let { add("HSK $it") }
                    info?.strokeCount?.let { add("$it strokes") }
                    info?.freqRank?.let { add("#$it") }
                }.joinToString("  ·  ")
                if (tags.isNotEmpty()) Text(tags, color = x.textFaint, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Text("🔊 Hear it", color = x.jade, fontSize = 14.sp, modifier = Modifier.clickable {
                    // speak with the displayed reading (a lone polyphone is wrapped in a carrier word)
                    val py = info?.pinyin ?: ""
                    scope.launch(Dispatchers.IO) { graph.speaker.speak(graph.dictionary.audioTextFor(ch, py)) }
                })
            }
        }

        Spacer(Modifier.height(16.dp))
        info?.definition?.takeIf { it.isNotBlank() }?.let {
            // meaning-first: "surname …" / "variant of …" senses stay visible but never lead
            Text(Gloss.display(it), color = x.textSoft, fontSize = 15.sp, lineHeight = 22.sp)
            Spacer(Modifier.height(16.dp))
        }

        // components / decomposition
        if (components.isNotEmpty()) {
            SectionTitle("Built from")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                components.forEach { c ->
                    Column(Modifier.clip(RoundedCornerShape(14.dp)).background(x.surface)
                        .clickable { onOpenChar(c.char) }.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(c.char, fontFamily = SerifSC, fontSize = 34.sp, color = x.text)
                        Text(c.pinyin.take(8), color = x.gold, fontSize = 12.sp)
                        Text(Gloss.primary(c.definition, 16), color = x.textFaint, fontSize = 10.sp)
                    }
                }
            }
            info?.decomposition?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(6.dp))
                Text("Structure: $it", color = x.textFaint, fontSize = 12.sp)
            }
            Spacer(Modifier.height(16.dp))
        }

        info?.radical?.takeIf { it.isNotBlank() }?.let {
            SectionTitle("Radical")
            Text(it, fontFamily = SerifSC, fontSize = 26.sp, color = x.jade)
            Spacer(Modifier.height(16.dp))
        }

        // example sentences (Tatoeba) — seeing the character in real, translated context
        if (examples.isNotEmpty()) {
            SectionTitle("In context")
            examples.forEach { s ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(x.surface)
                    .clickable { graph.speaker.speak(s.zh) }.padding(13.dp)) {
                    SentenceLine(s.zh, ch, x.text, x.cinnabar)
                    Spacer(Modifier.height(5.dp))
                    Text(s.en, color = x.textSoft, fontSize = 13.sp, lineHeight = 18.sp)
                }
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(8.dp))
        }

        // actions
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            ActionButton(if (hasStrokes) "✍ Practise writing" else "✍ Writing (no data)", x.cinnabar, Modifier.weight(1f),
                enabled = hasStrokes) { onPractice(ch) }
            ActionButton(if (mined) "✓ In deck" else "+ Add to deck", x.surface2, Modifier.weight(1f),
                enabled = info != null) {   // wait for the gloss so the card never has an empty back
                if (!mined) scope.launch {
                    graph.cards.mine(ch, "${info?.pinyin ?: ""} · ${Gloss.display(info?.definition ?: "")}",
                        CardType.CHAR_RECOGNITION, "character")
                    mined = true
                }
            }
        }
        Spacer(Modifier.height(30.dp))
    }
}

/** The Chinese sentence with every occurrence of [target] tinted, so the eye lands on it. */
@Composable
private fun SentenceLine(zh: String, target: String, base: Color, accent: Color) {
    val text = buildAnnotatedString {
        var i = 0
        while (i < zh.length) {
            if (target.isNotEmpty() && zh.startsWith(target, i)) {
                withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) { append(target) }
                i += target.length
            } else {
                append(zh[i]); i++
            }
        }
    }
    Text(text, fontFamily = SerifSC, color = base, fontSize = 18.sp, lineHeight = 28.sp)
}

@Composable
private fun SectionTitle(t: String) {
    Text(t, fontFamily = SerifSC, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Theme.x.text)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun ActionButton(label: String, bg: Color, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val x = Theme.x
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(if (enabled) bg else x.surface)
        .clickable(enabled = enabled) { onClick() }.padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
        Text(label, color = if (enabled && bg == x.cinnabar) Color.White else x.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
