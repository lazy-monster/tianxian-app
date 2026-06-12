package com.tianxian.core.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
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
 *  due, characters known, and a rotating "character of the moment". The whole tile opens reviews;
 *  the character opens to study it; ↻ rerolls it (it also refreshes on every periodic update). */
private data class WidgetData(
    val known: Int, val due: Int, val mastered: Int, val nextDue: Long?,
    val char: String, val pinyin: String, val gloss: String,
)

/**
 * The single home-screen widget. Re-skins to the user's widget theme (or follows the app theme).
 *
 * **One layout shows everything, at every size.** Home-screen widgets don't scroll, so the design
 * is sized to fit the tile's minimum (see status_widget_info.xml) and never hides or reveals parts.
 * Making the tile larger simply *scales it up* — fonts and spacing grow with the tile via [scaleFor]
 * — rather than disclosing more content.
 */
class StatusWidget : GlanceAppWidget() {
    // Exact so the layout re-measures for the actual placed size (drives the scale factor).
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
                    gloss = Gloss.primary(ci?.definition.orEmpty(), 28),
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
        val toNext = ((rank.stageEnd ?: rank.score) - rank.score).coerceAtLeast(0)
        val note = when {
            rank.isPeak -> "${d.known} known · Great Perfection"
            rank.nextRealm != null -> "${d.known} known · $toNext 修为 → ${rank.nextRealm!!.name}"
            else -> "${d.known} known · $toNext to next stage"
        }
        provideContent {
            val size = LocalSize.current
            WidgetBody(context, x, rank, d, dueValue, dueLabel, dueDanger, note, scaleFor(size.width.value, size.height.value))
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

    /** Scale the whole tile up for larger placements (down to the design's natural size at the
        widget minimum). Tracks the smaller dimension so a wider *or* taller tile enlarges without
        overflowing the other axis; floored at 1f so it never shrinks below what the min size fits. */
    private fun scaleFor(widthDp: Float, heightDp: Float): Float =
        minOf(widthDp / 250f, heightDp / 175f).coerceIn(1f, 1.5f)
}

@androidx.compose.runtime.Composable
private fun WidgetBody(
    context: Context, x: XColors, rank: Cultivation.Rank, d: WidgetData,
    dueValue: String, dueLabel: String, dueDanger: Boolean, note: String, s: Float,
) {
    Column(
        modifier = GlanceModifier.fillMaxSize().background(ColorProvider(x.surface)).padding((13 * s).dp)
            .clickable(actionStartActivity(openRoute(context, "review"))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Header: rank glyph + realm/stage on the left, reviews due on the right.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
            Text(rank.realm.hanzi, style = TextStyle(color = ColorProvider(x.gold), fontSize = (29 * s).sp, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.width((10 * s).dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(rank.realm.name, maxLines = 1,
                    style = TextStyle(color = ColorProvider(x.text), fontSize = (14 * s).sp, fontWeight = FontWeight.Medium))
                Text(rank.stageLabel, maxLines = 1,
                    style = TextStyle(color = ColorProvider(x.cinnabar), fontSize = (11 * s).sp))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(dueValue, style = TextStyle(
                    color = ColorProvider(if (dueDanger) x.cinnabar else x.jade), fontSize = (23 * s).sp, fontWeight = FontWeight.Bold))
                Text(dueLabel, maxLines = 1, style = TextStyle(color = ColorProvider(x.textFaint), fontSize = (10 * s).sp))
            }
        }

        Spacer(GlanceModifier.height((9 * s).dp))
        LinearProgressIndicator(
            progress = rank.progress,
            modifier = GlanceModifier.fillMaxWidth().height((6 * s).dp).cornerRadius((3 * s).dp),
            color = ColorProvider(x.jade),
            backgroundColor = ColorProvider(x.surface2),
        )
        Spacer(GlanceModifier.height((5 * s).dp))
        Text("修为 ${rank.score} · $note", maxLines = 1,
            style = TextStyle(color = ColorProvider(x.textFaint), fontSize = (11 * s).sp))

        // Character of the moment. Its own tap targets (study / ↻) sit over the whole-tile review tap.
        Spacer(GlanceModifier.height((10 * s).dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = GlanceModifier.fillMaxWidth().background(ColorProvider(x.surface2)).cornerRadius((14 * s).dp)
                .padding(horizontal = (12 * s).dp, vertical = (8 * s).dp)
                .clickable(actionStartActivity(openRoute(context, "char/${d.char}"))),
        ) {
            Text(d.char, style = TextStyle(color = ColorProvider(x.text), fontSize = (27 * s).sp, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.width((12 * s).dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                if (d.pinyin.isNotBlank())
                    Text(d.pinyin, maxLines = 1,
                        style = TextStyle(color = ColorProvider(x.gold), fontSize = (14 * s).sp, fontWeight = FontWeight.Medium))
                if (d.gloss.isNotBlank())
                    Text(d.gloss, maxLines = 1, style = TextStyle(color = ColorProvider(x.textSoft), fontSize = (12 * s).sp))
            }
            Spacer(GlanceModifier.width((10 * s).dp))
            Text("↻", modifier = GlanceModifier.clickable(actionRunCallback<RerollCharacterAction>()),
                style = TextStyle(color = ColorProvider(x.textFaint), fontSize = (20 * s).sp))
        }
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
