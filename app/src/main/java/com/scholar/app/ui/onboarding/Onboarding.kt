package com.scholar.app.ui.onboarding

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
import com.scholar.app.ui.theme.Brush
import com.scholar.app.ui.theme.SerifSC
import com.scholar.app.ui.theme.Theme

private data class Slide(val glyph: String, val title: String, val body: String)

private val SLIDES = listOf(
    Slide("学", "Welcome to Scholar",
        "Learn to read Chinese the efficient way — pinyin and tones first, then characters, then real books. Progress is measured in characters you actually know."),
    Slide("拼", "Sound before symbol",
        "Start with the Pinyin & Tones lessons so every new word has a sound in your head. It takes an afternoon and saves months of confusion."),
    Slide("木", "Characters are built, not memorised",
        "Each character is made of components and radicals with real meanings. Scholar shows you the pieces, so 好 is 'woman + child', not random strokes."),
    Slide("书", "Then read what you love",
        "Import any ebook — including xianxia and wuxia web novels. Tap any word for its meaning, mine it into spaced repetition, and watch your reading coverage climb."),
)

@Composable
fun Onboarding(onFinish: () -> Unit) {
    val x = Theme.x
    var i by remember { mutableStateOf(0) }
    val slide = SLIDES[i]
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
        Text(slide.body, color = x.textSoft, fontSize = 15.sp, lineHeight = 23.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(34.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            SLIDES.indices.forEach { d ->
                Box(Modifier.size(if (d == i) 22.dp else 8.dp, 8.dp).clip(RoundedCornerShape(4.dp))
                    .background(if (d == i) x.cinnabar else x.line))
            }
        }
        Spacer(Modifier.height(28.dp))
        Button(onClick = { if (i < SLIDES.lastIndex) i++ else onFinish() },
            colors = ButtonDefaults.buttonColors(containerColor = x.cinnabar),
            modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
            Text(if (i < SLIDES.lastIndex) "Next" else "Begin", color = Color.White,
                fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
        if (i < SLIDES.lastIndex) {
            Spacer(Modifier.height(8.dp))
            Text("Skip", color = x.textFaint, fontSize = 14.sp,
                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                    .clickable { onFinish() }.padding(8.dp))
        }
    }
}
