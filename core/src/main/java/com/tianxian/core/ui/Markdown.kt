package com.tianxian.core.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * Append inline Markdown — `**bold**`, `*italic*`, and `[label](url)` (kept as just the label) — to
 * this builder. Longest marker wins, so `**x**` is bold rather than two empty italics. An unpaired
 * or empty marker is left literal, so a lone `*` or a `80%` is never mangled. No nesting.
 *
 * Inline only: callers that may pass a `*`/`-` bullet line must strip the leading bullet first (see
 * [markdownNotes]), or it would be read as an emphasis marker.
 */
private fun AnnotatedString.Builder.appendInline(text: String) {
    var i = 0
    while (i < text.length) {
        val c = text[i]
        when {
            c == '*' -> {
                val marker = if (text.startsWith("**", i)) "**" else "*"
                val close = text.indexOf(marker, i + marker.length)
                if (close > i + marker.length) {                   // non-empty, properly closed
                    val style = if (marker == "**") SpanStyle(fontWeight = FontWeight.Bold)
                                else SpanStyle(fontStyle = FontStyle.Italic)
                    withStyle(style) { append(text.substring(i + marker.length, close)) }
                    i = close + marker.length
                } else { append(c); i++ }
            }
            c == '[' -> {                                          // [label](url) → label
                val endLabel = text.indexOf("](", i)
                val endUrl = if (endLabel >= 0) text.indexOf(')', endLabel + 2) else -1
                if (endUrl > endLabel) { append(text.substring(i + 1, endLabel)); i = endUrl + 1 }
                else { append(c); i++ }
            }
            else -> { append(c); i++ }
        }
    }
}

/**
 * Render the app's own prose — `**bold**` and `*italic*` — as a styled [AnnotatedString], so the
 * markers become real emphasis instead of showing as literal asterisks on screen.
 */
fun inlineMarkdown(text: String): AnnotatedString = buildAnnotatedString { appendInline(text) }

/**
 * Render GitHub release notes (the changelog in Settings → Updates) as a tidy [AnnotatedString].
 * GitHub's auto-generated notes are block Markdown — `## What's Changed`, `* commit …`,
 * `**Full Changelog**: …` — which Compose would otherwise print with the raw `#`, `*` and `**`
 * still showing. Line-aware: a heading becomes a bold line, a `*`/`-` bullet becomes "•", and only
 * the remainder of each line goes through inline emphasis (so a leading bullet isn't read as italics).
 */
fun markdownNotes(raw: String): AnnotatedString = buildAnnotatedString {
    raw.trim().lines().forEachIndexed { idx, line ->
        if (idx > 0) append('\n')
        val t = line.trim()
        when {
            t.startsWith("#") ->
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { appendInline(t.trimStart('#', ' ')) }
            t.startsWith("* ") || t.startsWith("- ") -> {
                append("•  "); appendInline(t.substring(2).trim())
            }
            else -> appendInline(line)
        }
    }
}
