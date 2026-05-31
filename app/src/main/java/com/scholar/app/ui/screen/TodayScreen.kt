package com.scholar.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scholar.app.data.user.BookEntity
import com.scholar.app.di.AppGraph
import com.scholar.app.ui.theme.SerifSC
import com.scholar.app.ui.theme.Theme

private const val FIRST_NOVEL_TARGET = 1000

@Composable
fun TodayScreen(graph: AppGraph, onOpenReview: () -> Unit, onOpenLibrary: () -> Unit,
                onOpenLearn: () -> Unit, onOpenSettings: () -> Unit, onOpenBook: (String) -> Unit) {
    val x = Theme.x
    val known by graph.known.knownCountFlow().collectAsStateWithLifecycle(0)
    val due by graph.cards.dueCountFlow().collectAsStateWithLifecycle(0)
    val mastered by graph.cards.masteredCountFlow().collectAsStateWithLifecycle(0)
    val genreLearned by graph.cards.genreLearnedCountFlow().collectAsStateWithLifecycle(0)
    val books by graph.books.booksFlow().collectAsStateWithLifecycle(emptyList())
    val rank = com.scholar.app.data.Cultivation.rankFor(known, mastered, genreLearned)

    LazyColumn(Modifier.fillMaxSize().background(x.bg).padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {

        item { Spacer(Modifier.height(12.dp)) }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Hall of Cultivation · 今日", color = x.gold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text("⚙", color = x.textSoft, fontSize = 20.sp, modifier = Modifier.clickable { onOpenSettings() })
            }
            Spacer(Modifier.height(8.dp))
            // hero
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
                .background(x.surface2).padding(24.dp)) {
                Text("CHARACTERS KNOWN", color = x.textSoft, fontSize = 12.sp, letterSpacing = 2.sp)
                Text("$known", fontFamily = SerifSC, fontWeight = FontWeight.SemiBold,
                    fontSize = 72.sp, color = x.gold)
                val remaining = (FIRST_NOVEL_TARGET - known).coerceAtLeast(0)
                Text(if (remaining > 0) "$remaining until your first full novel" else "ready for native novels — import one",
                    color = x.textSoft, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { onOpenLearn() }) {
                    Text(rank.realm.hanzi, fontFamily = SerifSC, fontSize = 18.sp, color = x.cinnabar)
                    Spacer(Modifier.width(8.dp))
                    Text("${rank.realm.name} · ${rank.stageLabel}", color = x.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(14.dp))
                Box(Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(6.dp)).background(x.bg)) {
                    Box(Modifier.fillMaxWidth((known.toFloat() / FIRST_NOVEL_TARGET).coerceIn(0f, 1f))
                        .fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(x.jade))
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                StatCard("Reviews due", "$due", "cards", x.cinnabar, Modifier.weight(1f), onOpenReview)
                StatCard("Library", "${books.size}", "books", x.gold, Modifier.weight(1f), onOpenLibrary)
            }
        }

        item {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(x.surface)
                .clickable { onOpenLearn() }.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(x.surface2), contentAlignment = Alignment.Center) {
                    Text("学", fontFamily = SerifSC, fontSize = 26.sp, color = x.gold)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Continue learning", color = x.text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text("Pinyin · radicals · vocabulary · handwriting", color = x.textSoft, fontSize = 12.sp)
                }
                Text("›", color = x.textFaint, fontSize = 24.sp)
            }
        }

        item {
            Text("Your library", fontFamily = SerifSC, fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp, color = x.text, modifier = Modifier.padding(top = 8.dp))
        }
        if (books.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(x.surface)
                    .clickable { onOpenLibrary() }.padding(20.dp)) {
                    Text("Import an ebook to begin", color = x.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text("EPUB · PDF · MOBI · TXT · or a photo of a page", color = x.textSoft, fontSize = 12.sp)
                }
            }
        } else {
            items(books) { b -> BookRow(b) { onOpenBook(b.id) } }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun StatCard(label: String, value: String, unit: String, accent: androidx.compose.ui.graphics.Color,
                     modifier: Modifier, onClick: () -> Unit) {
    val x = Theme.x
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(x.surface).clickable { onClick() }.padding(16.dp)) {
        Text(label, color = x.textSoft, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, fontFamily = SerifSC, fontWeight = FontWeight.SemiBold, fontSize = 38.sp, color = accent)
            Spacer(Modifier.width(5.dp))
            Text(unit, color = x.textSoft, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
        }
    }
}

@Composable
fun BookRow(b: BookEntity, onClick: () -> Unit) {
    val x = Theme.x
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(x.surface)
        .clickable { onClick() }.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(54.dp, 76.dp).clip(RoundedCornerShape(10.dp)).background(x.cinnabarDeep),
            contentAlignment = Alignment.Center) {
            Text(b.title.take(2), fontFamily = SerifSC, color = androidx.compose.ui.graphics.Color(0xFFF4E4C4), fontSize = 20.sp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(b.title, fontFamily = SerifSC, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                color = x.text, maxLines = 1)
            Text("${b.format.lowercase()} · ${b.author ?: "imported"}", color = x.textSoft, fontSize = 12.sp, maxLines = 1)
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(5.dp)).background(x.bg)) {
                Box(Modifier.fillMaxWidth(b.coverage.coerceIn(0f, 1f)).fillMaxHeight()
                    .clip(RoundedCornerShape(5.dp)).background(if (b.coverage >= 0.85f) x.jade else x.gold))
            }
            Text("${(b.coverage * 100).toInt()}% readable for you", color = x.textSoft, fontSize = 11.sp,
                modifier = Modifier.padding(top = 5.dp))
        }
    }
}
