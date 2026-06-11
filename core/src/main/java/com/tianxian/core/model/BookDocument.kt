package com.tianxian.core.model

/* The reader's pop-up dictionary needs *extractable text*, not just rendered
   pages. So every imported file — whatever its format — is normalised into this
   single structure: a book is an ordered list of chapters, each a list of text
   blocks. The segmenter then turns each block's text into tappable words.        */

enum class BookFormat { EPUB, PDF_TEXT, PDF_SCANNED, MOBI, AZW3, TXT, IMAGES, CBZ, UNKNOWN }

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
    val blocks: List<Block>,
)

/**
 * A unit of a chapter. Books mix prose and pictures (illustrated novels, scanned pages, comics),
 * so a chapter is an ordered list of these: [TextBlock] for prose, [ImageBlock] for a picture.
 */
sealed interface Block

/** A paragraph (or OCR line group). Plain text; styling is the reader's job. */
data class TextBlock(val text: String) : Block

/** A picture saved on disk. [file] is the filename within the book's image dir (see ImageStore). */
data class ImageBlock(val file: String, val caption: String? = null) : Block

/** Reading position, persisted per book so the user resumes exactly where they left off. */
data class ReadingPosition(val chapter: Int, val block: Int, val charOffset: Int = 0)
