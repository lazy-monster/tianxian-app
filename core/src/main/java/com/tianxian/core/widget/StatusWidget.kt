package com.tianxian.core.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
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
import com.tianxian.core.MainActivity
import com.tianxian.core.data.Cultivation
import com.tianxian.core.data.SettingsStore
import com.tianxian.core.data.content.ContentStore
import com.tianxian.core.data.content.Gloss
import com.tianxian.core.data.user.UserDatabase
import com.tianxian.core.ui.theme.XColors
import com.tianxian.core.ui.theme.themeById
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Everything the home screen needs in one tile: cultivation rank + breakthrough progress, reviews
 *  due, and a rotating "character of the moment". The whole tile opens reviews; the character opens
 *  to study it; ↻ rerolls the character. The character of the moment also refreshes naturally on
 *  every periodic/triggered update. */
private data class WidgetData(
    val known: Int, val due: Int, val mastered: Int, val nextDue: Long?,
    val char: String, val pinyin: String, val gloss: String,
)

/**
 * The single home-screen widget. Re-skins to the user's widget theme (or follows the app theme),
 * and re-lays-out for its placed size: a small tile stays compact; an enlarged one expands into a
 * majestic 修为 panel. Tap opens reviews; tap the character to study it; tap ↻ to reroll.
 */
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
                val ci = runCatching { ContentStore.get(context).randomCharacter() }.getOrNull()
                WidgetData(
                    known = db.knownDao().knownCount(),
                    due = db.cardDao().dueCount(now),
                    mastered = db.cardDao().masteredCount(),
                    nextDue = db.cardDao().nextDueMillis(now),
                    char = ci?.char ?: "学",
                    pinyin = ci?.pinyin.orEmpty(),
                    gloss = Gloss.primary(ci?.definition.orEmpty(), 30),
                )
            }.getOrDefault(WidgetData(0, 0, 0, null, "学", "xué", "to study; to learn"))
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

/** Compact tile: rank + realm, a breakthrough bar, the due count, and a tappable character chip. */
@androidx.compose.runtime.Composable
private fun CompactStatus(
    context: Context, x: XColors, rank: Cultivation.Rank, d: WidgetData,
    dueValue: String, dueLabel: String, dueDanger: Boolean,
) {
    Column(
        modifier = GlanceModifier.fillMaxSize().background(ColorProvider(x.surface)).padding(14.dp)
            .clickable(actionStartActivity(openRoute(context, "review"))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
            Text(rank.realm.hanzi, style = TextStyle(color = ColorProvider(x.gold), fontSize = 22.sp, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.width(10.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(rank.realm.name, maxLines = 1, style = TextStyle(color = ColorProvider(x.text), fontSize = 13.sp, fontWeight = FontWeight.Medium))
                Text(rank.stageLabel, maxLines = 1, style = TextStyle(color = ColorProvider(x.textFaint), fontSize = 11.sp))
            }
            // Character of the moment — its own tap target opens the character page to study it.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = GlanceModifier.clickable(actionStartActivity(openRoute(context, "char/${d.char}"))),
            ) {
                Text(d.char, style = TextStyle(color = ColorProvider(x.text), fontSize = 22.sp, fontWeight = FontWeight.Bold))
                if (d.pinyin.isNotBlank())
                    Text(d.pinyin, maxLines = 1, style = TextStyle(color = ColorProvider(x.gold), fontSize = 10.sp))
            }
        }
        Spacer(GlanceModifier.height(10.dp))
        LinearProgressIndicator(
            progress = rank.progress,
            modifier = GlanceModifier.fillMaxWidth().height(6.dp).cornerRadius(3.dp),
            color = ColorProvider(x.jade),
            backgroundColor = ColorProvider(x.surface2),
        )
        Spacer(GlanceModifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
            Text(dueValue, style = TextStyle(color = ColorProvider(if (dueDanger) x.cinnabar else x.jade), fontSize = 22.sp, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.width(6.dp))
            Text(dueLabel, modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(color = ColorProvider(x.textFaint), fontSize = 11.sp))
            Text("↻", modifier = GlanceModifier.clickable(actionRunCallback<RerollCharacterAction>()),
                style = TextStyle(color = ColorProvider(x.textFaint), fontSize = 17.sp))
        }
    }
}

/** Enlarged "majestic" panel: a grand realm glyph framed in 鎏金, the stage, a breakthrough bar,
 *  the 修为 score, the known/due stats, and a character-of-the-moment card to study. */
@androidx.compose.runtime.Composable
private fun GrandStatus(
    context: Context, x: XColors, rank: Cultivation.Rank, d: WidgetData,
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
                fontSize = 56.sp, fontWeight = FontWeight.Bold))
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
        Spacer(GlanceModifier.height(14.dp))

        // Character of the moment — tap to study, ↻ to reroll. Its own tap targets sit on top of
        // the whole-tile "open reviews" action.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = GlanceModifier.fillMaxWidth().background(ColorProvider(x.surface)).cornerRadius(16.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .clickable(actionStartActivity(openRoute(context, "char/${d.char}"))),
        ) {
            Text(d.char, style = TextStyle(color = ColorProvider(x.text), fontSize = 30.sp, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.width(12.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                if (d.pinyin.isNotBlank())
                    Text(d.pinyin, maxLines = 1, style = TextStyle(color = ColorProvider(x.gold), fontSize = 14.sp, fontWeight = FontWeight.Medium))
                if (d.gloss.isNotBlank())
                    Text(d.gloss, maxLines = 2, style = TextStyle(color = ColorProvider(x.textSoft), fontSize = 12.sp))
            }
            Spacer(GlanceModifier.width(10.dp))
            Text("↻", modifier = GlanceModifier.clickable(actionRunCallback<RerollCharacterAction>()),
                style = TextStyle(color = ColorProvider(x.textFaint), fontSize = 20.sp))
        }
    }
}

@androidx.compose.runtime.Composable
private fun GrandStat(x: XColors, value: String, label: String, accent: Color) {
    Column(
        GlanceModifier.background(ColorProvider(x.surface)).cornerRadius(14.dp)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = TextStyle(color = ColorProvider(accent), fontSize = 22.sp, fontWeight = FontWeight.Bold))
        Text(label, style = TextStyle(color = ColorProvider(x.textFaint), fontSize = 11.sp))
    }
}

/** Reroll the character of the moment: re-run provideGlance, which draws a new random character. */
class RerollCharacterAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        StatusWidget().update(context, glanceId)
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

/** Explicit launch Intent into [MainActivity] for a route, made unique by a data uri so the
    PendingIntent isn't deduped across routes and the extra is delivered reliably. */
internal fun openRoute(context: Context, route: String): Intent =
    Intent(context, MainActivity::class.java).apply {
        putExtra("route", route)
        data = Uri.parse("app://$route")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
