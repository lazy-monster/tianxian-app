package com.scholar.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scholar.app.di.AppGraph
import com.scholar.app.ui.theme.SerifSC
import com.scholar.app.ui.theme.Theme
import com.scholar.app.ui.theme.glyphTint

private data class Section(
    val route: String, val glyph: String, val title: String, val subtitle: String, val stage: String,
)

private val SECTIONS = listOf(
    Section("learn/pinyin", "拼", "Pinyin & Tones", "The sound system — start here", "Stage 0"),
    Section("learn/radicals", "部", "Radicals & Components", "Cultivation trials drill the 214 building blocks — shape, meaning & sound", "Stage 1"),
    Section("learn/levels", "级", "Vocabulary by Level", "Guided groups of 20 — learn sounds & meanings, gated by trials", "Stage 2"),
    Section("learn/writing", "写", "Handwriting", "Stroke order & tracing practice", "Anytime"),
    Section("learn/cultivation", "修", "Cultivation", "Your rank on the realm ladder, plus genre vocabulary", "Anytime"),
)

@Composable
fun LearnScreen(graph: AppGraph, onOpen: (String) -> Unit) {
    val x = Theme.x
    LazyColumn(Modifier.fillMaxSize().background(x.bg).padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Spacer(Modifier.height(16.dp))
            Text("Curriculum", fontFamily = SerifSC, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, color = x.text)
            Text("A path from zero to reading native novels. Work top to bottom, or dip in anywhere.",
                color = x.textSoft, fontSize = 14.sp, lineHeight = 20.sp)
            Spacer(Modifier.height(6.dp))
        }
        itemsIndexed(SECTIONS) { i, s ->
            // each stage gets its own accent so the curriculum reads as a colourful path, not a list
            val accent = listOf(x.gold, x.cinnabar, x.jade, x.gold, x.cinnabar)[i % 5]
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(x.surface)
                .clickable { onOpen(s.route) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(58.dp).clip(RoundedCornerShape(16.dp)).background(x.glyphTint(accent)),
                    contentAlignment = Alignment.Center) {
                    Text(s.glyph, fontFamily = SerifSC, fontSize = 32.sp, color = accent)
                }
                Spacer(Modifier.width(15.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.stage, color = x.cinnabar, fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp)
                    Text(s.title, fontFamily = SerifSC, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = x.text)
                    Text(s.subtitle, color = x.textSoft, fontSize = 13.sp, lineHeight = 18.sp)
                }
                Text("›", color = x.textFaint, fontSize = 26.sp)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
