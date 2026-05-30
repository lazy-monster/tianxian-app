package com.scholar.app.ui.theme

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
