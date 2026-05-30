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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scholar.app.data.content.CharInfo
import com.scholar.app.data.content.Radical
import com.scholar.app.di.AppGraph
import com.scholar.app.ui.theme.SerifSC
import com.scholar.app.ui.theme.Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun RadicalsScreen(graph: AppGraph, onBack: () -> Unit, onOpenChar: (String) -> Unit) {
    val x = Theme.x
    var radicals by remember { mutableStateOf<List<Radical>>(emptyList()) }
    var expanded by remember { mutableStateOf<Int?>(null) }
    val examples = remember { mutableStateMapOf<String, List<CharInfo>>() }

    LaunchedEffect(Unit) { radicals = withContext(Dispatchers.IO) { graph.dictionary.radicals() } }

    LazyColumn(Modifier.fillMaxSize().background(x.bg).padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { ScreenHeader("Radicals & Components", "The 214 Kangxi radicals are the semantic pieces characters are built from. Knowing them turns memorising into reading meaning. Tap one for example characters.", onBack) }
        items(radicals) { r ->
            val isOpen = expanded == r.number
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(x.surface)
                .clickable {
                    expanded = if (isOpen) null else r.number
                    if (!examples.containsKey(r.radical)) {
                        // load examples lazily; collected below in LaunchedEffect keyed by expanded
                    }
                }.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(x.surface2), contentAlignment = Alignment.Center) {
                        Text(r.radical, fontFamily = SerifSC, fontSize = 26.sp, color = x.gold)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${r.number}. ${r.meaning}", color = x.text, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                        Text(r.pinyin, color = x.textSoft, fontSize = 13.sp)
                    }
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
