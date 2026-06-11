package com.tianxian.core.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tianxian.core.data.content.Gloss
import com.tianxian.core.data.user.CardEntity
import com.tianxian.core.di.AppGraph
import com.tianxian.core.srs.GradeProjection
import com.tianxian.core.srs.Rating
import com.tianxian.core.ui.theme.SerifSC
import com.tianxian.core.ui.theme.Theme
import com.tianxian.core.ui.theme.promptWash
import kotlinx.coroutines.launch

@Composable
fun ReviewScreen(graph: AppGraph, onOpenChar: (String) -> Unit = {}) {
    val x = Theme.x
    val scope = rememberCoroutineScope()
    var queue by remember { mutableStateOf<List<CardEntity>>(emptyList()) }
    // Session state is *saveable*: detouring to a character page (or rotating) disposes this
    // composition, so plain remember would dump the queue and counts. The queue itself is
    // restored from saved card ids on return, landing on the same card with progress intact.
    var sessionIds by rememberSaveable { mutableStateOf(longArrayOf()) }
    var idx by rememberSaveable { mutableStateOf(0) }
    var flipped by rememberSaveable { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var reviewed by rememberSaveable { mutableStateOf(0) }
    var correct by rememberSaveable { mutableStateOf(0) }      // graded better than Again
    var ahead by rememberSaveable { mutableStateOf(false) }    // this session pulled not-yet-due cards
    var deckHasCards by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (sessionIds.isNotEmpty()) {
            queue = graph.cards.byIds(sessionIds.toList())   // resume the interrupted session
            deckHasCards = true
        } else {
            val due = graph.cards.due()
            queue = due
            sessionIds = due.map { it.id }.toLongArray()
            deckHasCards = due.isNotEmpty() || graph.cards.ahead(1).isNotEmpty()
        }
        loading = false
    }

    // Start an optional "review ahead" run: drill the soonest cards even though they're not due yet.
    fun reviewAhead() {
        scope.launch {
            val q = graph.cards.ahead(20)
            queue = q; sessionIds = q.map { it.id }.toLongArray()
            idx = 0; reviewed = 0; correct = 0; flipped = false; ahead = true
        }
    }

    if (loading) {
        Box(Modifier.fillMaxSize().background(x.bg), Alignment.Center) { CircularProgressIndicator(color = x.cinnabar) }
        return
    }
    val card = queue.getOrNull(idx)
    if (card == null) {
        Box(Modifier.fillMaxSize().background(x.bg).padding(22.dp), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (reviewed > 0) "复习完毕" else "无卡片", fontFamily = SerifSC, fontSize = 30.sp, color = x.gold)
                Spacer(Modifier.height(8.dp))
                Text(
                    when {
                        reviewed > 0 -> "Reviewed $reviewed · ${(100 * correct / reviewed)}% recalled" +
                            if (ahead) " (ahead)" else ""
                        deckHasCards -> "Nothing due right now — well rested."
                        else -> "Mine words while reading to build your deck."
                    },
                    color = x.textSoft, fontSize = 14.sp,
                )
                if (deckHasCards) {
                    Spacer(Modifier.height(22.dp))
                    Box(Modifier.clip(RoundedCornerShape(16.dp)).background(x.surface)
                        .clickable { reviewAhead() }.padding(horizontal = 22.dp, vertical = 13.dp)) {
                        Text("⚡ Review ahead", color = x.gold, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Drill the next cards early — they'll reschedule as normal.",
                        color = x.textFaint, fontSize = 12.sp)
                }
            }
        }
        return
    }

    // projections hit the review log (for elapsed time), so they load off the main thread;
    // keyed on idx as well as id because an "Again" card can reappear later in the same queue
    val projections by produceState(emptyList<GradeProjection>(), card.id, idx) {
        value = graph.cards.project(card)
    }
    val total = queue.size

    fun onGrade(rating: Rating) {
        // Write on the app scope so the grade survives leaving the screen mid-session; an
        // "Again" card goes back to the end of the queue for in-session relearning.
        val write = graph.appScope.launch { graph.cards.grade(card, rating) }
        if (rating == Rating.AGAIN) scope.launch {
            write.join()
            graph.cards.card(card.id)?.let { queue = queue + it; sessionIds += card.id }
        }
        idx++; flipped = false; reviewed++
        if (rating != Rating.AGAIN) correct++
    }

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
        Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(26.dp))
            .background(x.promptWash(if (flipped) x.jade else x.gold))
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
                    Text(coreGloss(parts.getOrElse(1) { card.backRef }), color = x.text, fontSize = 17.sp,
                        modifier = Modifier.padding(horizontal = 24.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("🔊", fontSize = 26.sp, modifier = Modifier.clickable {
                        // polyphone-safe: a lone char is spoken with the reading this card shows
                        val py = parts.getOrElse(0) { "" }
                        graph.appScope.launch { graph.speaker.speak(graph.dictionary.audioTextFor(card.frontRef, py)) }
                    })
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
                GradeButton(projections, Rating.AGAIN, "Again", x.cinnabar, Modifier.weight(1f)) { onGrade(it) }
                GradeButton(projections, Rating.HARD, "Hard", x.gold, Modifier.weight(1f)) { onGrade(it) }
                GradeButton(projections, Rating.GOOD, "Good", x.jade, Modifier.weight(1f)) { onGrade(it) }
                GradeButton(projections, Rating.EASY, "Easy", Color(0xFF6FA3C4), Modifier.weight(1f)) { onGrade(it) }
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

/**
 * Reduce a full dictionary gloss to its core senses so the review card is recallable at a glance.
 * Shared logic lives in [Gloss]: metadata senses ("surname Huan", "variant of …") never lead, and
 * the character screen ("study character") still shows the full definition.
 */
private fun coreGloss(raw: String): String = if (raw.isBlank()) raw else Gloss.core(raw)
