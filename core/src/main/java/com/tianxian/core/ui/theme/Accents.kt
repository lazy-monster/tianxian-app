package com.tianxian.core.ui.theme

import androidx.compose.ui.graphics.Brush as GradientBrush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

/* Subtle colour washes derived from each theme's own accents, so every skin gains warmth and
   hierarchy without new palette tokens or loud gradients. Cards keep their flat token colour as
   the dominant hue; the wash is a quiet tint at one edge. */

/** Hero panels: a gold dawn fading through the surface into a faint cinnabar ember. */
fun XColors.heroWash(): GradientBrush = GradientBrush.linearGradient(
    listOf(
        gold.copy(alpha = 0.16f).compositeOver(surface2),
        surface2,
        cinnabar.copy(alpha = 0.10f).compositeOver(surface2),
    )
)

/** A faint accent tint over a card surface — colour-codes the card without shouting. */
fun XColors.accentWash(accent: Color): GradientBrush = GradientBrush.verticalGradient(
    listOf(accent.copy(alpha = 0.12f).compositeOver(surface), surface)
)

/** Same wash on the raised surface, for flashcards and trial prompt panels. */
fun XColors.promptWash(accent: Color): GradientBrush = GradientBrush.verticalGradient(
    listOf(accent.copy(alpha = 0.10f).compositeOver(surface2), surface2)
)

/** Tile tint for glyph squares — a touch more saturated than the washes. */
fun XColors.glyphTint(accent: Color): Color = accent.copy(alpha = 0.14f).compositeOver(surface2)
