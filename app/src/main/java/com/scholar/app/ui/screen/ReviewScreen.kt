package com.scholar.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scholar.app.data.user.CardEntity
import com.scholar.app.di.AppGraph
import com.scholar.app.srs.GradeProjection
import com.scholar.app.srs.Rating
import com.scholar.app.ui.theme.SerifSC
import com.scholar.app.ui.theme.Theme
import kotlinx.coroutines.launch

@Composable
fun ReviewScreen(graph: AppGraph, onOpenChar: (String) -> Unit = {}) {
    val x = Theme.x
    val scope = rememberCoroutineScope()
    var queue by remember { mutableStateOf<List<CardEntity>>(emptyList()) }
    var idx by remember { mutableStateOf(0) }
    var flipped by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var reviewed by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) { queue = graph.cards.due(); loading = false }

    if (loading) {
        Box(Modifier.fillMaxSize().background(x.bg), Alignment.Center) { CircularProgressIndicator(color = x.cinnabar) }
        return
    }
    val card = queue.getOrNull(idx)
    if (card == null) {
        Box(Modifier.fillMaxSize().background(x.bg), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (reviewed > 0) "复习完毕" else "无卡片", fontFamily = SerifSC, fontSize = 30.sp, color = x.gold)
                Spacer(Modifier.height(8.dp))
                Text(if (reviewed > 0) "Reviewed $reviewed cards. Rest your eyes." else "Mine words while reading to build your deck.",
                    color = x.textSoft, fontSize = 14.sp)
            }
        }
        return
    }

    val projections = remember(card.id) { graph.cards.project(card) }
    val total = queue.size

    Column(Modifier.fillMaxSize().background(x.bg).padding(22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${idx + 1} / $total", color = x.textSoft, fontSize = 12.sp)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(6.dp)).background(x.surface)) {
                Box(Modifier.fillMaxWidth((idx).toFloat() / total).fillMaxHeight()
                    .clip(RoundedCornerShape(6.dp)).background(x.gold))
            }
            Spacer(Modifier.width(10.dp))
            Text("FSRS v6", color = x.gold, fontSize = 12.sp)
        }
        Spacer(Modifier.height(22.dp))

        // flashcard — content scrolls so long definitions never push the speak/study
        // controls out of reach. Short cards still sit centred.
        val cardScroll = rememberScrollState()
        LaunchedEffect(card.id, flipped) { cardScroll.scrollTo(0) }
        Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(x.surface2)
            .clickable { flipped = true }, contentAlignment = Alignment.Center) {
            Column(
                Modifier.verticalScroll(cardScroll).padding(vertical = 24.dp, horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(card.frontRef, fontFamily = SerifSC, fontWeight = FontWeight.SemiBold,
                    fontSize = if (card.frontRef.length <= 2) 96.sp else 56.sp, color = x.text)
                if (flipped) {
                    Spacer(Modifier.height(18.dp))
                    val parts = card.backRef.split(" · ", limit = 2)
                    Text(parts.getOrElse(0) { "" }, color = x.gold, fontSize = 24.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(parts.getOrElse(1) { card.backRef }, color = x.text, fontSize = 17.sp,
                        modifier = Modifier.padding(horizontal = 24.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("🔊", fontSize = 26.sp, modifier = Modifier.clickable { graph.speaker.speak(card.frontRef) })
                    if (card.frontRef.any { it.code in 0x4E00..0x9FFF }) {
                        Spacer(Modifier.height(14.dp))
                        Text("study character", color = x.textFaint, fontSize = 11.sp, letterSpacing = 1.sp)
                        Spacer(Modifier.height(4.dp))
                        HanziLinks(card.frontRef, onOpenChar, fontSize = 22.sp, color = x.jade)
                    }
                } else {
                    Spacer(Modifier.height(18.dp))
                    Text("tap to reveal", color = x.textFaint, fontSize = 12.sp, letterSpacing = 2.sp)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        if (flipped) {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                GradeButton(projections, Rating.AGAIN, "Again", x.cinnabar, Modifier.weight(1f)) { grade(graph, scope, card, it) { idx++; flipped = false; reviewed++ } }
                GradeButton(projections, Rating.HARD, "Hard", x.gold, Modifier.weight(1f)) { grade(graph, scope, card, it) { idx++; flipped = false; reviewed++ } }
                GradeButton(projections, Rating.GOOD, "Good", x.jade, Modifier.weight(1f)) { grade(graph, scope, card, it) { idx++; flipped = false; reviewed++ } }
                GradeButton(projections, Rating.EASY, "Easy", Color(0xFF6FA3C4), Modifier.weight(1f)) { grade(graph, scope, card, it) { idx++; flipped = false; reviewed++ } }
            }
        } else {
            Spacer(Modifier.height(58.dp))
        }
    }
}

@Composable
private fun GradeButton(projections: List<GradeProjection>, rating: Rating, label: String,
                        accent: Color, modifier: Modifier, onGrade: (Rating) -> Unit) {
    val x = Theme.x
    val interval = projections.firstOrNull { it.rating == rating }?.intervalLabel ?: ""
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(x.surface)
        .clickable { onGrade(rating) }.padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(interval, color = x.textSoft, fontSize = 10.sp)
    }
}

private fun grade(graph: AppGraph, scope: kotlinx.coroutines.CoroutineScope,
                  card: CardEntity, rating: Rating, after: () -> Unit) {
    scope.launch { graph.cards.grade(card, rating) }
    after()
}
