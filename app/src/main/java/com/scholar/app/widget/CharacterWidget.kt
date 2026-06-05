package com.scholar.app.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.scholar.app.MainActivity
import com.scholar.app.data.SettingsStore
import com.scholar.app.data.content.ContentStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** "Character of the moment" widget — a rotating character with reading + gloss. Tap to study it,
 *  tap ↻ to reroll. A fresh character is drawn on every update (periodic or reroll). */
class CharacterWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val ci = withContext(Dispatchers.IO) { runCatching { ContentStore.get(context).randomCharacter() }.getOrNull() }
        val ch = ci?.char ?: "学"
        val pinyin = ci?.pinyin.orEmpty()
        val gloss = ci?.definition.orEmpty().substringBefore(",").substringBefore("/").trim().take(30)
        val x = resolveWidgetColors(SettingsStore(context))
        provideContent {
            Column(
                modifier = GlanceModifier.fillMaxSize().background(ColorProvider(x.surface)).padding(12.dp)
                    .clickable(actionStartActivity(openRoute(context, "char/$ch"))),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(ch, style = TextStyle(color = ColorProvider(x.text), fontSize = 46.sp, fontWeight = FontWeight.Bold))
                if (pinyin.isNotBlank())
                    Text(pinyin, style = TextStyle(color = ColorProvider(x.gold), fontSize = 15.sp))
                if (gloss.isNotBlank())
                    Text(gloss, maxLines = 2, style = TextStyle(color = ColorProvider(x.textSoft),
                        fontSize = 12.sp, textAlign = TextAlign.Center))
                Spacer(GlanceModifier.height(6.dp))
                Text("↻", modifier = GlanceModifier.clickable(actionRunCallback<RerollCharacterAction>()),
                    style = TextStyle(color = ColorProvider(x.textFaint), fontSize = 18.sp))
            }
        }
    }
}

/** Reroll: re-run provideGlance, which draws a new random character. */
class RerollCharacterAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        CharacterWidget().update(context, glanceId)
    }
}

class CharacterWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CharacterWidget()
}

/** Explicit launch Intent into [MainActivity] for a route, made unique by a data uri so the
    PendingIntent isn't deduped across widgets/routes and the extra is delivered reliably. */
internal fun openRoute(context: Context, route: String): Intent =
    Intent(context, MainActivity::class.java).apply {
        putExtra("route", route)
        data = Uri.parse("scholar://$route")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
