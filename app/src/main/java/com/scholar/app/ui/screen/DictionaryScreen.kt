package com.scholar.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scholar.app.data.content.DictEntry
import com.scholar.app.di.AppGraph
import com.scholar.app.ui.theme.SerifSC
import com.scholar.app.ui.theme.Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun DictionaryScreen(graph: AppGraph, onOpenChar: (String) -> Unit) {
    val x = Theme.x
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<DictEntry>>(emptyList()) }

    // debounce search
    LaunchedEffect(query) {
        if (query.isBlank()) { results = emptyList(); return@LaunchedEffect }
        delay(180)
        results = withContext(Dispatchers.IO) { graph.dictionary.search(query.trim()) }
    }

    Column(Modifier.fillMaxSize().background(x.bg).padding(horizontal = 22.dp)) {
        Spacer(Modifier.height(14.dp))
        Text("The Lexicon · 字典", color = x.gold, fontSize = 14.sp)
        Text("Dictionary", fontFamily = SerifSC, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, color = x.text)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            placeholder = { Text("汉字 · pīnyīn · English", color = x.textFaint) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = x.cinnabar, unfocusedBorderColor = x.line,
                focusedTextColor = x.text, unfocusedTextColor = x.text, cursorColor = x.cinnabar,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        if (query.isNotBlank() && results.isEmpty()) {
            Text("No matches.", color = x.textSoft, fontSize = 13.sp, modifier = Modifier.padding(8.dp))
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items(results) { e -> EntryRow(e, graph, onOpenChar) }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun EntryRow(e: DictEntry, graph: AppGraph, onOpenChar: (String) -> Unit) {
    val x = Theme.x
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(x.surface).padding(14.dp),
        verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(e.simplified, fontFamily = SerifSC, fontWeight = FontWeight.SemiBold, fontSize = 26.sp,
                    color = if (e.simplified.length == 1) x.cinnabar else x.text,
                    modifier = if (e.simplified.length == 1) Modifier.clickable { onOpenChar(e.simplified) } else Modifier)
                if (e.traditional.isNotEmpty() && e.traditional != e.simplified) {
                    Spacer(Modifier.width(8.dp))
                    Text(e.traditional, color = x.textFaint, fontSize = 16.sp,
                        modifier = Modifier.padding(bottom = 3.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(graph.dictionary.toned(e.pinyin), color = x.gold, fontSize = 16.sp,
                    modifier = Modifier.padding(bottom = 3.dp))
            }
            Spacer(Modifier.height(4.dp))
            Text(e.gloss, color = x.textSoft, fontSize = 14.sp, lineHeight = 20.sp)
        }
        Text("🔊", fontSize = 22.sp, modifier = Modifier.clickable { graph.speaker.speak(e.simplified) }
            .padding(start = 8.dp, top = 4.dp))
    }
}
