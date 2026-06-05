package com.scholar.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.scholar.app.R

/* Extended palette carried alongside Material's scheme (jade/gold/cinnabar). */
@Immutable
data class XColors(
    val bg: Color, val bg2: Color, val surface: Color, val surface2: Color, val line: Color,
    val text: Color, val textSoft: Color, val textFaint: Color,
    val cinnabar: Color, val cinnabarDeep: Color, val gold: Color, val jade: Color, val jadeDeep: Color,
)

private val DarkX = XColors(
    Ink.bg, Ink.bg2, Ink.surface, Ink.surface2, Ink.line,
    Ink.text, Ink.textSoft, Ink.textFaint,
    Ink.cinnabar, Ink.cinnabarDeep, Ink.gold, Ink.jade, Ink.jadeDeep,
)
private val LightX = XColors(
    Paper.bg, Paper.bg2, Paper.surface, Paper.surface2, Paper.line,
    Paper.text, Paper.textSoft, Paper.textFaint,
    Paper.cinnabar, Paper.cinnabarDeep, Paper.gold, Paper.jade, Paper.jadeDeep,
)
private val JadeX = XColors(
    Jade.bg, Jade.bg2, Jade.surface, Jade.surface2, Jade.line,
    Jade.text, Jade.textSoft, Jade.textFaint,
    Jade.cinnabar, Jade.cinnabarDeep, Jade.gold, Jade.jade, Jade.jadeDeep,
)
private val VermilionX = XColors(
    Vermilion.bg, Vermilion.bg2, Vermilion.surface, Vermilion.surface2, Vermilion.line,
    Vermilion.text, Vermilion.textSoft, Vermilion.textFaint,
    Vermilion.cinnabar, Vermilion.cinnabarDeep, Vermilion.gold, Vermilion.jade, Vermilion.jadeDeep,
)
private val AzureX = XColors(
    Azure.bg, Azure.bg2, Azure.surface, Azure.surface2, Azure.line,
    Azure.text, Azure.textSoft, Azure.textFaint,
    Azure.cinnabar, Azure.cinnabarDeep, Azure.gold, Azure.jade, Azure.jadeDeep,
)
private val BambooX = XColors(
    Bamboo.bg, Bamboo.bg2, Bamboo.surface, Bamboo.surface2, Bamboo.line,
    Bamboo.text, Bamboo.textSoft, Bamboo.textFaint,
    Bamboo.cinnabar, Bamboo.cinnabarDeep, Bamboo.gold, Bamboo.jade, Bamboo.jadeDeep,
)

/** A selectable app skin: a palette plus the dark/light flag that drives Material's scheme. */
@Immutable
data class AppTheme(
    val id: String, val label: String, val hanzi: String, val blurb: String,
    val dark: Boolean, val colors: XColors,
)

/** All skins the user can pick from, in display order. The first is the default. */
val APP_THEMES: List<AppTheme> = listOf(
    AppTheme("ink", "Ink Wash", "墨", "Classic 水墨 dark", true, DarkX),
    AppTheme("jade", "Jade Heaven", "青", "Jade-green twilight", true, JadeX),
    AppTheme("vermilion", "Cinnabar Dusk", "丹", "Warm smouldering red", true, VermilionX),
    AppTheme("azure", "Distant Mountains", "黛", "Ink & indigo blue", true, AzureX),
    AppTheme("paper", "Rice Paper", "纸", "Bright 宣纸 light", false, LightX),
    AppTheme("bamboo", "Bamboo Slips", "竹", "Pale green light", false, BambooX),
)

/** Resolve a stored theme id to its [AppTheme], defaulting to Ink for unknown/legacy ids. */
fun themeById(id: String?): AppTheme = APP_THEMES.firstOrNull { it.id == id } ?: APP_THEMES[0]

val LocalXColors = staticCompositionLocalOf { DarkX }

/* Use the platform CJK fonts (Android ships Noto Serif/Sans CJK). The reader uses
   the serif family for a real-book feel; UI uses sans. No font assets required —
   so the project compiles and renders Chinese out of the box. Drop custom .ttf
   into res/font and swap these for FontFamily(Font(...)) to refine later. */
val SerifSC = FontFamily.Serif
val SansSC = FontFamily.SansSerif
val Brush = FontFamily.Serif   // placeholder for Ma Shan Zheng brush accent
/** Bundled LXGW WenKai — an open (OFL) Kai/楷 face, the marquee custom reading font. */
val KaiSC = FontFamily(Font(R.font.lxgw_wenkai))

/** Reader font family for a stored preference key (serif | sans | kai | mono). */
fun readerFont(key: String): FontFamily = when (key) {
    "sans" -> SansSC
    "kai" -> KaiSC
    "mono" -> FontFamily.Monospace
    else -> SerifSC
}

/** Colours for the reading surface — chosen independently of the app's dark/light theme. */
@Immutable
data class ReaderColors(val bg: Color, val text: Color, val textSoft: Color, val textFaint: Color)

/** Resolve a reader theme key to its palette; "follow" mirrors the current app theme [x]. */
fun readerPalette(key: String, x: XColors): ReaderColors = when (key) {
    "ink" -> ReaderColors(Ink.bg, Ink.text, Ink.textSoft, Ink.textFaint)
    "paper" -> ReaderColors(Paper.bg, Paper.text, Paper.textSoft, Paper.textFaint)
    "sepia" -> ReaderColors(Color(0xFFF4ECD8), Color(0xFF5B4636), Color(0xFF7A6A52), Color(0xFFA8997D))
    "oled" -> ReaderColors(Color(0xFF000000), Color(0xFFE8E2D6), Color(0xFFB9B2A4), Color(0xFF6F695C))
    else -> ReaderColors(x.bg, x.text, x.textSoft, x.textFaint)
}

private val XTypography = Typography(
    displayLarge = TextStyle(fontFamily = SerifSC, fontWeight = FontWeight.SemiBold, fontSize = 64.sp),
    headlineMedium = TextStyle(fontFamily = SerifSC, fontWeight = FontWeight.SemiBold, fontSize = 26.sp),
    titleMedium = TextStyle(fontFamily = SerifSC, fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
    bodyLarge = TextStyle(fontFamily = SerifSC, fontSize = 21.sp, lineHeight = 38.sp),
    bodyMedium = TextStyle(fontFamily = SansSC, fontSize = 15.sp, lineHeight = 22.sp),
    labelSmall = TextStyle(fontFamily = SansSC, fontSize = 11.sp),
)

@Composable
fun ScholarTheme(theme: AppTheme = APP_THEMES[0], content: @Composable () -> Unit) {
    val x = theme.colors
    val scheme = if (theme.dark)
        darkColorScheme(primary = x.cinnabar, background = x.bg, surface = x.surface,
            onBackground = x.text, onSurface = x.text)
    else
        lightColorScheme(primary = x.cinnabar, background = x.bg, surface = x.surface,
            onBackground = x.text, onSurface = x.text)
    CompositionLocalProvider(LocalXColors provides x) {
        MaterialTheme(colorScheme = scheme, typography = XTypography, content = content)
    }
}

object Theme {
    val x: XColors @Composable get() = LocalXColors.current
}
