package com.scholar.app.widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.scholar.app.data.Cultivation
import com.scholar.app.data.user.UserDatabase
import com.scholar.app.ui.theme.Ink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class StatusData(val known: Int, val due: Int, val mastered: Int, val nextDue: Long?)

/** Cultivation-status widget: your rank, characters known, and reviews due (or time to the next).
 *  Tap opens the review screen. */
class StatusWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val now = System.currentTimeMillis()
        val settings = com.scholar.app.data.SettingsStore(context)
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
        val (dueValue, dueLabel, dueDanger) = when {
            d.due > 0 -> Triple("${d.due}", "reviews due", true)
            d.nextDue != null -> Triple(untilLabel(d.nextDue - now), "till next", false)
            else -> Triple("—", "no cards", false)
        }
        provideContent {
            Column(
                modifier = GlanceModifier.fillMaxSize().background(ColorProvider(Ink.surface)).padding(14.dp)
                    .clickable(actionStartActivity(openRoute(context, "review"))),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(rank.realm.hanzi, style = TextStyle(color = ColorProvider(Ink.gold), fontSize = 22.sp, fontWeight = FontWeight.Bold))
                Text("${rank.realm.name} · ${rank.stageLabel}", maxLines = 1,
                    style = TextStyle(color = ColorProvider(Ink.textSoft), fontSize = 12.sp))
                Spacer(GlanceModifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("${d.known}", style = TextStyle(color = ColorProvider(Ink.text), fontSize = 24.sp, fontWeight = FontWeight.Bold))
                        Text("known", style = TextStyle(color = ColorProvider(Ink.textFaint), fontSize = 11.sp))
                    }
                    Spacer(GlanceModifier.width(20.dp))
                    Column {
                        Text(dueValue, style = TextStyle(color = ColorProvider(if (dueDanger) Ink.cinnabar else Ink.jade), fontSize = 24.sp, fontWeight = FontWeight.Bold))
                        Text(dueLabel, style = TextStyle(color = ColorProvider(Ink.textFaint), fontSize = 11.sp))
                    }
                }
            }
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

class StatusWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StatusWidget()
}
