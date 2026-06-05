package com.scholar.app.widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.scholar.app.data.Cultivation
import com.scholar.app.data.SettingsStore
import com.scholar.app.data.user.UserDatabase
import com.scholar.app.ui.theme.XColors
import com.scholar.app.ui.theme.themeById
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class StatusData(val known: Int, val due: Int, val mastered: Int, val nextDue: Long?)

/** Cultivation-status widget. Small: rank glyph + known/due. Enlarged: a majestic 修为 panel with a
 *  grand realm glyph, the stage, and a progress bar to the next breakthrough — meant to inspire.
 *  Tap opens the review screen. Re-skins to the user's widget theme (or follows the app theme). */
class StatusWidget : GlanceAppWidget() {
    // Exact so the layout re-renders for the actual placed size — that's what lets the enlarged
    // form expand into a grand panel instead of staying a tiny tile.
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val now = System.currentTimeMillis()
        val settings = SettingsStore(context)
        val d = withContext(Dispatchers.IO) {
            runCatching {
                val db = UserDatabase.get(context)
                StatusData(
                    known = db.knownDao().knownCount(),
                    due = db.cardDao().dueCount(now),
                    mastered = db.cardDao().masteredCount(),
                    nextDue = db.cardDao().nextDueMillis(now),
                )
            }.getOrDefault(StatusData(0, 0, 0, null))
        }
        val rank = Cultivation.rankFor(d.known, d.mastered,
            settings.radicalsCultivated(), settings.trackWordsCultivated())
        val x = resolveWidgetColors(settings)
        val (dueValue, dueLabel, dueDanger) = when {
            d.due > 0 -> Triple("${d.due}", "reviews due", true)
            d.nextDue != null -> Triple(untilLabel(d.nextDue - now), "till next", false)
            else -> Triple("—", "no cards", false)
        }
        provideContent {
            val size = LocalSize.current
            val grand = size.height >= 200.dp && size.width >= 220.dp
            if (grand) GrandStatus(context, x, rank, d, dueValue, dueLabel, dueDanger)
            else CompactStatus(context, x, rank, d, dueValue, dueLabel, dueDanger)
        }
    }

    private fun untilLabel(ms: Long): String {
        val h = ms / 3_600_000L
        return when {
            h < 1 -> "<1h"
            h < 24 -> "${h}h"
            else -> "${h / 24}d"
        }
    }
}

/** Original compact tile, now palette-aware. */
@androidx.compose.runtime.Composable
private fun CompactStatus(
    context: Context, x: XColors, rank: Cultivation.Rank, d: StatusData,
    dueValue: String, dueLabel: String, dueDanger: Boolean,
) {
    Column(
        modifier = GlanceModifier.fillMaxSize().background(ColorProvider(x.surface)).padding(14.dp)
            .clickable(actionStartActivity(openRoute(context, "review"))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(rank.realm.hanzi, style = TextStyle(color = ColorProvider(x.gold), fontSize = 22.sp, fontWeight = FontWeight.Bold))
        Text("${rank.realm.name} · ${rank.stageLabel}", maxLines = 1,
            style = TextStyle(color = ColorProvider(x.textSoft), fontSize = 12.sp))
        Spacer(GlanceModifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("${d.known}", style = TextStyle(color = ColorProvider(x.text), fontSize = 24.sp, fontWeight = FontWeight.Bold))
                Text("known", style = TextStyle(color = ColorProvider(x.textFaint), fontSize = 11.sp))
            }
            Spacer(GlanceModifier.width(20.dp))
            Column {
                Text(dueValue, style = TextStyle(color = ColorProvider(if (dueDanger) x.cinnabar else x.jade), fontSize = 24.sp, fontWeight = FontWeight.Bold))
                Text(dueLabel, style = TextStyle(color = ColorProvider(x.textFaint), fontSize = 11.sp))
            }
        }
    }
}

/** Enlarged "majestic" panel: a grand realm glyph framed in 鎏金, the stage, a breakthrough bar,
 *  the 修为 score, and the known/due stats — built to feel like a cultivation tablet, not a tile. */
@androidx.compose.runtime.Composable
private fun GrandStatus(
    context: Context, x: XColors, rank: Cultivation.Rank, d: StatusData,
    dueValue: String, dueLabel: String, dueDanger: Boolean,
) {
    val toNext = ((rank.stageEnd ?: rank.score) - rank.score).coerceAtLeast(0)
    val nextNote = when {
        rank.isPeak -> "Great Perfection · 大圆满"
        rank.nextRealm != null -> "$toNext 修为 → ${rank.nextRealm!!.name}"
        else -> "$toNext to the next stage"
    }
    Column(
        modifier = GlanceModifier.fillMaxSize().background(ColorProvider(x.bg)).padding(16.dp)
            .clickable(actionStartActivity(openRoute(context, "review"))),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("修为 · CULTIVATION", style = TextStyle(color = ColorProvider(x.textSoft),
            fontSize = 11.sp, fontWeight = FontWeight.Medium))
        Spacer(GlanceModifier.height(2.dp))
        Box(GlanceModifier.width(40.dp).height(2.dp).background(ColorProvider(x.gold)).cornerRadius(2.dp)) {}
        Spacer(GlanceModifier.height(10.dp))

        // The grand glyph, framed on a deep gilt panel.
        Box(
            GlanceModifier.background(ColorProvider(x.surface2)).cornerRadius(20.dp)
                .padding(horizontal = 26.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(rank.realm.hanzi, style = TextStyle(color = ColorProvider(x.gold),
                fontSize = 60.sp, fontWeight = FontWeight.Bold))
        }
        Spacer(GlanceModifier.height(10.dp))
        Text(rank.realm.name, style = TextStyle(color = ColorProvider(x.text),
            fontSize = 19.sp, fontWeight = FontWeight.Bold))
        Text(rank.stageLabel, style = TextStyle(color = ColorProvider(x.cinnabar),
            fontSize = 13.sp, fontWeight = FontWeight.Medium))
        Spacer(GlanceModifier.height(12.dp))

        // Breakthrough progress within the current stage.
        LinearProgressIndicator(
            progress = rank.progress,
            modifier = GlanceModifier.fillMaxWidth().height(8.dp).cornerRadius(4.dp),
            color = ColorProvider(x.jade),
            backgroundColor = ColorProvider(x.surface2),
        )
        Spacer(GlanceModifier.height(6.dp))
        Text("修为 ${rank.score}  ·  $nextNote", maxLines = 1,
            style = TextStyle(color = ColorProvider(x.textFaint), fontSize = 12.sp, textAlign = TextAlign.Center))
        Spacer(GlanceModifier.height(14.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            GrandStat(x, "${d.known}", "characters known", x.gold)
            Spacer(GlanceModifier.width(14.dp))
            GrandStat(x, dueValue, dueLabel, if (dueDanger) x.cinnabar else x.jade)
        }
    }
}

@androidx.compose.runtime.Composable
private fun GrandStat(x: XColors, value: String, label: String, accent: androidx.compose.ui.graphics.Color) {
    Column(
        GlanceModifier.background(ColorProvider(x.surface)).cornerRadius(14.dp)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = TextStyle(color = ColorProvider(accent), fontSize = 22.sp, fontWeight = FontWeight.Bold))
        Text(label, style = TextStyle(color = ColorProvider(x.textFaint), fontSize = 11.sp))
    }
}

/** Resolve the widget's palette: a fixed skin, or "follow" the app's current theme. */
internal fun resolveWidgetColors(settings: SettingsStore): XColors {
    val key = settings.widgetThemeKey
    val id = if (key == "follow") settings.themeId else key
    return themeById(id).colors
}

class StatusWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StatusWidget()
}
