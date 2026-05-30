package com.scholar.app.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
