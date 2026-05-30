package com.scholar.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scholar.app.ui.theme.SerifSC
import com.scholar.app.ui.theme.Theme

@Composable
fun SettingsScreen(dark: Boolean, onToggleTheme: () -> Unit, onBack: () -> Unit) {
    val x = Theme.x
    Column(Modifier.fillMaxSize().background(x.bg).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp)) {
        ScreenHeader("Settings", onBack = onBack)

        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(x.surface).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Dark theme", color = x.text, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                Text("Ink-wash dark, or rice-paper light", color = x.textSoft, fontSize = 13.sp)
            }
            Switch(checked = dark, onCheckedChange = { onToggleTheme() },
                colors = SwitchDefaults.colors(checkedThumbColor = x.gold, checkedTrackColor = x.cinnabarDeep,
                    uncheckedThumbColor = x.textSoft, uncheckedTrackColor = x.surface2))
        }

        Spacer(Modifier.height(20.dp))
        Text("About", fontFamily = SerifSC, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = x.text)
        Spacer(Modifier.height(8.dp))
        AboutCard("Scholar", "A free, offline-first app for learning to read Chinese web novels — pinyin first, characters by their components, frequency-ordered vocabulary, spaced repetition, and a friction-free reader.")
        Spacer(Modifier.height(10.dp))
        AboutCard("Open data", "Dictionary: CC-CEDICT (CC BY-SA). Characters & strokes: Make Me a Hanzi (Arphic). Levels: complete-hsk-vocabulary (MIT). Frequency: wordfreq (MIT). The 214 radicals are curated reference data.")
        Spacer(Modifier.height(10.dp))
        AboutCard("Your data stays yours", "No account, no ads, no tracking. Your progress and imported books live only on this device.")
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun AboutCard(title: String, body: String) {
    val x = Theme.x
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(x.surface).padding(16.dp)) {
        Text(title, color = x.gold, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Spacer(Modifier.height(4.dp))
        Text(body, color = x.textSoft, fontSize = 14.sp, lineHeight = 21.sp)
    }
}
