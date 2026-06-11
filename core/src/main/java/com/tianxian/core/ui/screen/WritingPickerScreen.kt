package com.tianxian.core.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tianxian.core.di.AppGraph
import com.tianxian.core.ui.theme.SerifSC
import com.tianxian.core.ui.theme.Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun WritingPickerScreen(graph: AppGraph, onBack: () -> Unit, onPractice: (String) -> Unit) {
    val x = Theme.x
    var chars by remember { mutableStateOf<List<String>>(emptyList()) }
    var typed by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        chars = withContext(Dispatchers.IO) {
            graph.dictionary.hskWords("new-1", 400).map { it.word }.filter { it.length == 1 }.distinct()
        }
    }

    Column(Modifier.fillMaxSize().background(x.bg).padding(horizontal = 22.dp)) {
        ScreenHeader("Handwriting", "Type any character to practise, or pick a common one below.", onBack)

        TextField(
            // keep only the last Han character — stroke data only exists for hanzi, so latin
            // keystrokes (pre-IME-commit) never land in the field as a dead "no data" target
            value = typed, onValueChange = { input ->
                typed = input.lastOrNull { it.code in 0x4E00..0x9FFF }?.toString() ?: ""
            },
            placeholder = { Text("Type a character…", color = x.textFaint) },
            singleLine = true,
            keyboardActions = KeyboardActions(onDone = { if (typed.isNotBlank()) onPractice(typed) }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = x.surface, unfocusedContainerColor = x.surface,
                focusedTextColor = x.text, unfocusedTextColor = x.text,
                cursorColor = x.cinnabar, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)),
        )
        if (typed.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(x.cinnabar)
                .clickable { onPractice(typed) }.padding(14.dp), contentAlignment = Alignment.Center) {
                Text("Practise  $typed", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Common characters (HSK 1)", fontFamily = SerifSC, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = x.text)
        Spacer(Modifier.height(10.dp))
        LazyVerticalGrid(columns = GridCells.Adaptive(64.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(chars) { c ->
                Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(14.dp)).background(x.surface)
                    .clickable { onPractice(c) }, contentAlignment = Alignment.Center) {
                    Text(c, fontFamily = SerifSC, fontSize = 30.sp, color = x.text)
                }
            }
        }
    }
}
