package com.scholar.app.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scholar.app.ui.theme.SerifSC
import com.scholar.app.ui.theme.Theme
import androidx.compose.ui.text.font.FontWeight

@Composable
fun ScreenHeader(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null) {
    val x = Theme.x
    Column {
        Spacer(Modifier.height(14.dp))
        if (onBack != null) {
            Text("‹ back", color = x.gold, fontSize = 14.sp, modifier = Modifier.clickable { onBack() })
            Spacer(Modifier.height(6.dp))
        }
        Text(title, fontFamily = SerifSC, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, color = x.text)
        if (subtitle != null) Text(subtitle, color = x.textSoft, fontSize = 14.sp, lineHeight = 20.sp)
        Spacer(Modifier.height(10.dp))
    }
}

private fun Char.isHan() = code in 0x4E00..0x9FFF

/**
 * Renders [word] so that every Han character is individually tappable, opening its
 * character-detail page. Single characters become one link; multi-character words let you
 * drill into any of their components. Non-Han glyphs (punctuation, latin) render inert.
 * This is the shared entry point that puts the character page "everywhere relevant".
 */
@Composable
fun HanziLinks(
    word: String,
    onOpenChar: (String) -> Unit,
    fontSize: TextUnit,
    color: Color = Theme.x.text,
    fontFamily: FontFamily = SerifSC,
    fontWeight: FontWeight = FontWeight.Normal,
) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        word.forEach { c ->
            if (c.isHan()) {
                Text(c.toString(), fontFamily = fontFamily, fontSize = fontSize, color = color,
                    fontWeight = fontWeight, modifier = Modifier.clickable { onOpenChar(c.toString()) })
            } else {
                Text(c.toString(), fontFamily = fontFamily, fontSize = fontSize, color = color, fontWeight = fontWeight)
            }
        }
    }
}
