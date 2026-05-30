package com.scholar.app.model

/* The reader's pop-up dictionary needs *extractable text*, not just rendered
   pages. So every imported file — whatever its format — is normalised into this
   single structure: a book is an ordered list of chapters, each a list of text
   blocks. The segmenter then turns each block's text into tappable words.        */

enum class BookFormat { EPUB, PDF_TEXT, PDF_SCANNED, MOBI, AZW3, TXT, IMAGES, UNKNOWN }

data class BookDocument(
    val id: String,
    val title: String,
    val author: String?,
    val format: BookFormat,
    val chapters: List<Chapter>,
    /** Pre-computed character profile → drives the per-book "readable for you %". */
    val charProfile: Map<Char, Int> = emptyMap(),
)

data class Chapter(
    val index: Int,
    val title: String?,
    val blocks: List<TextBlock>,
)

/** A paragraph (or OCR line group). Plain text; styling is the reader's job. */
data class TextBlock(val text: String)

/** Reading position, persisted per book so the user resumes exactly where they left off. */
data class ReadingPosition(val chapter: Int, val block: Int, val charOffset: Int = 0)
