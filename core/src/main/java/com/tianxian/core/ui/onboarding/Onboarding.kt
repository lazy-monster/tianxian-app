package com.tianxian.core.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tianxian.core.ui.inlineMarkdown
import com.tianxian.core.ui.theme.Brush
import com.tianxian.core.ui.theme.SerifSC
import com.tianxian.core.ui.theme.Theme

private data class Slide(val glyph: String, val title: String, val body: String)

private fun onboardingSlides(appName: String) = listOf(
    Slide("学", "Welcome to $appName",
        "One goal: reading real Chinese webnovels. Five tabs along the bottom — Home for today's picture, Learn for the curriculum, Read for your books, Review for your card deck, Dict to look anything up."),
    Slide("拼", "Day one: the sounds",
        "Open Learn → Pinyin & Tones and spend one afternoon there. You only need to *recognise* the four tones, not produce them — every word you study later is tappable to hear, and that sound is what makes characters stick."),
    Slide("部", "Then: trials",
        "Learn → Radicals and Learn → Vocabulary are gated trials: study a small batch, take its quiz, score high enough to break through. Passing a vocabulary trial seals those 20 words into your review deck automatically — that's how the deck grows."),
    Slide("卡", "Every day: review",
        "The Review tab is the engine. Do it daily, before anything new — ten minutes is enough. Grade yourself honestly: Again if you blanked, Good if you got it. The schedule handles all the remembering."),
    Slide("书", "Early: read anyway",
        "Don't wait to feel ready. After a few hundred words, import a novel (Read tab — EPUB, TXT, PDF, MOBI). Tap any word for its meaning, add the useful ones to your deck, mark names as known. Heavy tapping at first is the method working."),
    Slide("修", "Your rank tells the story",
        "Trials and reviews raise your cultivation rank from 炼气 to 渡劫 — it tracks real reading ability, not streaks. The full recommended path lives in Settings → $appName's Path, and Settings → Backup keeps everything in one file you own."),
)

@Composable
fun Onboarding(appName: String, onFinish: () -> Unit) {
    val x = Theme.x
    val slides = remember(appName) { onboardingSlides(appName) }
    var i by remember { mutableStateOf(0) }
    val slide = slides[i]
    Column(Modifier.fillMaxSize().background(x.bg).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(150.dp).clip(RoundedCornerShape(36.dp)).background(x.surface2),
            contentAlignment = Alignment.Center) {
            Text(slide.glyph, fontFamily = Brush, fontSize = 96.sp, color = x.gold)
        }
        Spacer(Modifier.height(36.dp))
        Text(slide.title, fontFamily = SerifSC, fontWeight = FontWeight.SemiBold, fontSize = 26.sp,
            color = x.text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(14.dp))
        Text(inlineMarkdown(slide.body), color = x.textSoft, fontSize = 15.sp, lineHeight = 23.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(34.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            slides.indices.forEach { d ->
                Box(Modifier.size(if (d == i) 22.dp else 8.dp, 8.dp).clip(RoundedCornerShape(4.dp))
                    .background(if (d == i) x.cinnabar else x.line))
            }
        }
        Spacer(Modifier.height(28.dp))
        Button(onClick = { if (i < slides.lastIndex) i++ else onFinish() },
            colors = ButtonDefaults.buttonColors(containerColor = x.cinnabar),
            modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
            Text(if (i < slides.lastIndex) "Next" else "Begin", color = Color.White,
                fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
        if (i < slides.lastIndex) {
            Spacer(Modifier.height(8.dp))
            Text("Skip", color = x.textFaint, fontSize = 14.sp,
                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                    .clickable { onFinish() }.padding(8.dp))
        }
    }
}
