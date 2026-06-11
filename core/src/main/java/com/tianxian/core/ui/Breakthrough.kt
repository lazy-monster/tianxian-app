package com.tianxian.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tianxian.core.data.Cultivation
import com.tianxian.core.ui.theme.Brush
import com.tianxian.core.ui.theme.SerifSC
import com.tianxian.core.ui.theme.Theme

/** What triggered the overlay: a new realm (major) or a new sub-stage within a realm (minor). */
data class BreakthroughInfo(val rank: Cultivation.Rank, val major: Boolean)

/**
 * Full-screen celebration shown when the cultivation rank crosses a boundary. A realm breakthrough
 * (major) is grander than a sub-stage advance (minor). Driven from [AppRoot]'s rank watcher, so
 * it fires whether the gain came from reviewing or from breaking through a study trial.
 */
@Composable
fun BreakthroughOverlay(info: BreakthroughInfo, onDismiss: () -> Unit) {
    val x = Theme.x
    val rank = info.rank
    val major = info.major

    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val appear by animateFloatAsState(if (shown) 1f else 0f, tween(360), label = "breakthrough")

    // Scrim consumes touches (no-indication clickable) so the screen behind stays inert.
    val scrim = remember { MutableInteractionSource() }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.84f * appear))
            .clickable(scrim, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.scale(0.86f + 0.14f * appear).padding(36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(if (major) "境界突破" else "修为精进", fontFamily = SerifSC, color = x.cinnabar,
                fontSize = if (major) 18.sp else 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 6.sp)
            Text(if (major) "REALM BREAKTHROUGH" else "STAGE ADVANCED", color = x.textFaint,
                fontSize = 10.sp, letterSpacing = 3.sp)
            Spacer(Modifier.height(if (major) 24.dp else 18.dp))

            Text(rank.realm.hanzi, fontFamily = Brush, color = x.gold, fontSize = if (major) 104.sp else 72.sp)
            Spacer(Modifier.height(14.dp))
            Text(rank.realm.name, fontFamily = SerifSC, fontWeight = FontWeight.SemiBold,
                color = x.text, fontSize = if (major) 26.sp else 21.sp)
            Text(rank.stageLabel, color = x.cinnabar, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(16.dp))

            Text(
                if (major) rank.realm.note else "Your cultivation deepens. 修为 ${rank.score} — onward to the next stage.",
                color = x.textSoft, fontSize = 13.sp, lineHeight = 19.sp, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 14.dp),
            )
            Spacer(Modifier.height(30.dp))
            Box(
                Modifier.clip(RoundedCornerShape(16.dp)).background(x.cinnabar)
                    .clickable { onDismiss() }.padding(horizontal = 34.dp, vertical = 13.dp),
            ) {
                Text("Continue", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
        }
    }
}
