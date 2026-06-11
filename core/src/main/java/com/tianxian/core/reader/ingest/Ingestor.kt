package com.tianxian.core.reader.ingest

import android.content.Context
import android.net.Uri
import com.tianxian.core.model.BookDocument
import com.tianxian.core.model.BookFormat
import com.tianxian.core.model.Chapter
import com.tianxian.core.model.TextBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * "User supplied a file → give me a readable BookDocument."
 * EPUB / TXT / MOBI are parsed in pure Kotlin (see *Parser files); PDF uses PDFBox
 * for its text layer and falls back to ML Kit OCR when the PDF is scanned images.
 * Everything funnels into one BookDocument so the reader never cares about format.
 */
class Ingestor(private val context: Context) {

    suspend fun ingest(uri: Uri, displayName: String?): Result<BookDocument> =
        withContext(Dispatchers.IO) {
            runCatching {
                val name = displayName ?: uri.lastPathSegment ?: "imported"
                val title = name.substringBeforeLast('.').ifBlank { "Imported text" }
                val tmp = copyToCache(uri, name)
                try {
                    val doc = when (FormatDetector.detect(tmp, name)) {
                        BookFormat.EPUB -> EpubParser.parse(context, tmp, title)
                        BookFormat.TXT -> TxtParser.parse(tmp, title)
                        BookFormat.MOBI, BookFormat.AZW3 -> MobiParser.parse(tmp, title)
                        BookFormat.PDF_TEXT, BookFormat.PDF_SCANNED -> PdfExtractor(context).parse(tmp, title)
                        BookFormat.IMAGES -> Ocr(context).parseImage(tmp, title)
                        BookFormat.CBZ -> CbzParser.parse(context, tmp, title)
                        BookFormat.UNKNOWN -> error("Unsupported file. Use EPUB, PDF, MOBI, TXT, CBZ, or a page photo.")
                    }
                    doc.withCharProfile()
                } finally {
                    tmp.delete()   // the parsed content is cached separately; don't pile imports up in cacheDir
                }
            }
        }

    private fun copyToCache(uri: Uri, name: String): File {
        val out = File(context.cacheDir, "import_${System.currentTimeMillis()}_$name")
        context.contentResolver.openInputStream(uri)?.use { input ->
            out.outputStream().use { input.copyTo(it) }
        } ?: error("Could not open file")
        return out
    }
}

object FormatDetector {
    private val IMG_EXT = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")

    fun detect(file: File, name: String): BookFormat {
        when (name.substringAfterLast('.', "").lowercase()) {
            "epub" -> return BookFormat.EPUB
            "txt" -> return BookFormat.TXT
            "mobi", "prc" -> return BookFormat.MOBI
            "azw", "azw3" -> return BookFormat.AZW3
            "png", "jpg", "jpeg", "webp" -> return BookFormat.IMAGES
            "pdf" -> return BookFormat.PDF_TEXT
            "cbz", "cbr", "cb7", "zip" -> return sniffZip(file) ?: BookFormat.CBZ
        }
        // sniff magic bytes when the extension is missing/wrong
        file.inputStream().use { s ->
            val h = ByteArray(8); val n = s.read(h)
            if (n >= 4) {
                if (h[0] == 0x50.toByte() && h[1] == 0x4B.toByte()) return sniffZip(file) ?: BookFormat.EPUB   // PK zip
                if (h[0] == '%'.code.toByte() && h[1] == 'P'.code.toByte()) return BookFormat.PDF_TEXT
            }
        }
        return BookFormat.UNKNOWN
    }

    /** A PK zip is either an EPUB (has container.xml) or a comic archive (mostly images). */
    private fun sniffZip(file: File): BookFormat? = runCatching {
        java.util.zip.ZipFile(file).use { zip ->
            val names = zip.entries().asSequence().filter { !it.isDirectory }.toList()
            when {
                names.any { it.name.equals("META-INF/container.xml", true) } -> BookFormat.EPUB
                else -> {
                    val imgs = names.count { it.name.substringAfterLast('.', "").lowercase() in IMG_EXT }
                    if (imgs > 0 && imgs >= names.size / 2) BookFormat.CBZ else BookFormat.EPUB
                }
            }
        }
    }.getOrNull()
}

/** Tally Han-character frequencies at import so per-book coverage % is instant later. */
internal fun BookDocument.withCharProfile(): BookDocument {
    val counts = HashMap<Char, Int>()
    chapters.forEach { ch -> ch.blocks.forEach { b ->
        if (b is TextBlock) b.text.forEach { c -> if (c.code in 0x4E00..0x9FFF) counts.merge(c, 1, Int::plus) }
    } }
    return copy(charProfile = counts)
}

/** Build chapters out of a flat list of paragraph strings (used by TXT/MOBI). */
internal fun paragraphsToDoc(title: String, format: BookFormat, paras: List<String>): BookDocument {
    // crude chapter splitting on common heading markers (第..章 / Chapter)
    val chapterRegex = Regex("""^\s*(第\s*[0-9零一二三四五六七八九十百千]+\s*[章回节卷]|Chapter\s+\d+)""")
    val chapters = ArrayList<Chapter>()
    var blocks = ArrayList<TextBlock>()
    var chTitle: String? = null
    fun flush() {
        if (blocks.isNotEmpty()) { chapters.add(Chapter(chapters.size, chTitle, blocks)); blocks = ArrayList() }
    }
    for (p in paras) {
        if (chapterRegex.containsMatchIn(p) && p.length < 30) { flush(); chTitle = p.trim() }
        else if (p.isNotBlank()) blocks.add(TextBlock(p.trim()))
    }
    flush()
    if (chapters.isEmpty()) chapters.add(Chapter(0, null, listOf(TextBlock(paras.joinToString("\n")))))
    return BookDocument(UUID.randomUUID().toString(), title, null, format, chapters)
}

/** One ordered piece of an HTML document: a text paragraph, or an embedded image reference. */
internal sealed interface HtmlNode {
    data class Para(val text: String) : HtmlNode
    data class Img(val src: String) : HtmlNode
}

/** Minimal, dependency-free HTML → ordered text + image refs (good enough for EPUB/MOBI XHTML). */
internal object Html {
    private val SCRIPT = Regex("(?is)<(script|style)[^>]*>.*?</\\1>")
    private val IMG = Regex("(?is)<(img|image)\\b[^>]*>")
    private val SRC = Regex("(?i)(?:xlink:href|href|src)\\s*=\\s*[\"']([^\"']+)[\"']")
    private val BLOCK = Regex("(?i)</(p|div|h[1-6]|li|br|tr)\\s*>|<br\\s*/?>")
    private val TAG = Regex("(?s)<[^>]+>")
    private const val IMG_MARK = '\u0001'   // sentinel that survives tag-stripping

    /** Text paragraphs only (TXT/MOBI, and anywhere images aren't wanted). */
    fun toParagraphs(html: String): List<String> =
        toBlocks(html).filterIsInstance<HtmlNode.Para>().map { it.text }

    /** Paragraphs and `<img>` (or SVG `<image>`) references, in document order. */
    fun toBlocks(html: String): List<HtmlNode> {
        var s = SCRIPT.replace(html, " ")
        // mark each image inline (carrying its src) so its position relative to text is preserved
        s = IMG.replace(s) { m -> "\n$IMG_MARK${SRC.find(m.value)?.groupValues?.get(1).orEmpty()}\n" }
        s = BLOCK.replace(s, "\n")
        s = TAG.replace(s, "")
        s = decode(s)
        val out = ArrayList<HtmlNode>()
        for (raw in s.split('\n')) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line[0] == IMG_MARK) {
                val src = line.drop(1).trim()
                if (src.isNotEmpty()) out.add(HtmlNode.Img(src))
            } else out.add(HtmlNode.Para(line))
        }
        return out
    }

    private fun decode(s: String): String = s
        .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<")
        .replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
        .replace(Regex("&#(\\d+);")) { it.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: "" }
}
