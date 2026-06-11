package com.tianxian.core.ui.theme

import androidx.compose.ui.graphics.Color

/* ── 水墨 · Ink & Jade palette ───────────────────────────────────────
   Mirrors the design tokens in the HTML prototype 1:1 so the app and
   the spec never drift. Dark ("墨" ink-wash) is the default.            */

object Ink {
    // dark · 水墨
    val bg          = Color(0xFF141109)
    val bg2         = Color(0xFF1B160D)
    val surface     = Color(0xFF221C12)
    val surface2    = Color(0xFF2B2417)
    val line        = Color(0xFF3A3120)
    val text        = Color(0xFFECE1C8)
    val textSoft    = Color(0xFFB8AC8E)
    val textFaint   = Color(0xFF7C7257)   // also: known-word dim in reader
    val cinnabar    = Color(0xFFD96A52)   // primary accent · 朱砂
    val cinnabarDeep= Color(0xFFB8442F)
    val gold        = Color(0xFFD8B46B)   // 鎏金
    val jade        = Color(0xFF7FAE90)   // mastered / success · 青翠
    val jadeDeep    = Color(0xFF4F7A64)
}

object Paper {
    // light · 宣纸
    val bg          = Color(0xFFE7DCC4)
    val bg2         = Color(0xFFEFE6D2)
    val surface     = Color(0xFFF6EFDD)
    val surface2    = Color(0xFFFBF6E9)
    val line        = Color(0xFFD8CBA9)
    val text        = Color(0xFF2B2419)
    val textSoft    = Color(0xFF6C6049)
    val textFaint   = Color(0xFFA99C7D)
    val cinnabar    = Color(0xFFB43F2C)
    val cinnabarDeep= Color(0xFF9A3322)
    val gold        = Color(0xFFA9812F)
    val jade        = Color(0xFF4F7A64)
    val jadeDeep    = Color(0xFF3C6650)
}

/* ── Extra palettes ──────────────────────────────────────────────────
   Same token set as Ink/Paper so every palette is interchangeable in
   XColors. Each is hand-tuned for contrast; the cinnabar/gold/jade
   accents keep their meaning (primary / 鎏金 / success) in every skin.  */

object Jade {
    // dark · 青冥 — a jade-green twilight
    val bg          = Color(0xFF0B1410)
    val bg2         = Color(0xFF101C16)
    val surface     = Color(0xFF15251D)
    val surface2    = Color(0xFF1D3127)
    val line        = Color(0xFF294035)
    val text        = Color(0xFFDCEBDF)
    val textSoft    = Color(0xFFA0BCAA)
    val textFaint   = Color(0xFF6C8A78)
    val cinnabar    = Color(0xFFE08A66)
    val cinnabarDeep= Color(0xFFBE5E3D)
    val gold        = Color(0xFFD7BE7C)
    val jade        = Color(0xFF74C8A0)
    val jadeDeep    = Color(0xFF45906C)
}

object Vermilion {
    // dark · 丹霞 — cinnabar dusk, warm and smouldering
    val bg          = Color(0xFF160B0A)
    val bg2         = Color(0xFF1E110E)
    val surface     = Color(0xFF281614)
    val surface2    = Color(0xFF351E1A)
    val line        = Color(0xFF492821)
    val text        = Color(0xFFF1DDD0)
    val textSoft    = Color(0xFFC6A597)
    val textFaint   = Color(0xFF8E6C5F)
    val cinnabar    = Color(0xFFE9745C)
    val cinnabarDeep= Color(0xFFC74A33)
    val gold        = Color(0xFFE0B873)
    val jade        = Color(0xFF8FB98F)
    val jadeDeep    = Color(0xFF5C8463)
}

object Azure {
    // dark · 远黛 — distant blue mountains, ink and indigo
    val bg          = Color(0xFF0A0F18)
    val bg2         = Color(0xFF0F1622)
    val surface     = Color(0xFF151E2E)
    val surface2    = Color(0xFF1E283B)
    val line        = Color(0xFF2C384F)
    val text        = Color(0xFFDDE4F1)
    val textSoft    = Color(0xFF9EAAC3)
    val textFaint   = Color(0xFF697795)
    val cinnabar    = Color(0xFFDD8270)
    val cinnabarDeep= Color(0xFFBB5746)
    val gold        = Color(0xFFCDB583)
    val jade        = Color(0xFF6FB6C6)
    val jadeDeep    = Color(0xFF457E92)
}

object Bamboo {
    // light · 竹简 — pale bamboo slips with a green cast
    val bg          = Color(0xFFDCE3C9)
    val bg2         = Color(0xFFE5EBD5)
    val surface     = Color(0xFFEEF2E2)
    val surface2    = Color(0xFFF5F8EC)
    val line        = Color(0xFFCAD3B0)
    val text        = Color(0xFF28311E)
    val textSoft    = Color(0xFF566346)
    val textFaint   = Color(0xFF879478)
    val cinnabar    = Color(0xFFB1492B)
    val cinnabarDeep= Color(0xFF8E3621)
    val gold        = Color(0xFF94762B)
    val jade        = Color(0xFF477852)
    val jadeDeep    = Color(0xFF345D3D)
}
