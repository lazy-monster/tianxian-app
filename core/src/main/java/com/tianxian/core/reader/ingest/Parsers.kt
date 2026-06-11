package com.tianxian.core.reader.ingest

import android.content.Context
import android.util.Xml
import com.tianxian.core.model.Block
import com.tianxian.core.model.BookDocument
import com.tianxian.core.model.BookFormat
import com.tianxian.core.model.Chapter
import com.tianxian.core.model.ImageBlock
import com.tianxian.core.model.TextBlock
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile

/** EPUB = a ZIP of XHTML. Parse container.xml → OPF → spine order → split each document into
 *  ordered paragraphs and inline images. Pure Kotlin (java.util.zip + XmlPullParser). */
object EpubParser {

    fun parse(context: Context, file: File, fallbackTitle: String): BookDocument {
        val id = UUID.randomUUID().toString()
        ZipFile(file).use { zip ->
            val opfPath = rootfilePath(zip) ?: error("Not a valid EPUB (no OPF)")
            val opfText = zip.readEntry(opfPath) ?: error("OPF missing")
            val opfDir = opfPath.substringBeforeLast('/', "")
            val (title, author, hrefs) = parseOpf(opfText)

            val chapters = ArrayList<Chapter>()
            var imgN = 0
            for (href in hrefs) {
                val full = (if (opfDir.isEmpty()) href else "$opfDir/$href").removePrefix("/")
                val html = zip.readEntry(full) ?: continue
                val baseDir = full.substringBeforeLast('/', "")
                val blocks = ArrayList<Block>()
                var firstText: String? = null
                for (node in Html.toBlocks(html)) when (node) {
                    is HtmlNode.Para -> { blocks.add(TextBlock(node.text)); if (firstText == null) firstText = node.text }
                    is HtmlNode.Img -> {
                        val zpath = resolveZipPath(baseDir, node.src)
                        val bytes = zip.readBytes(zpath) ?: continue
                        val ext = zpath.substringAfterLast('.', "jpg").lowercase().take(4)
                        val name = "img_%04d.%s".format(imgN++, ext)
                        ImageStore.saveBytes(context, id, name, bytes)
                        blocks.add(ImageBlock(name))
                    }
                }
                if (blocks.isNotEmpty())
                    chapters.add(Chapter(chapters.size, firstText?.take(24), blocks))
            }
            if (chapters.isEmpty()) error("EPUB had no readable content")
            return BookDocument(id, title ?: fallbackTitle, author, BookFormat.EPUB, chapters)
        }
    }

    private fun ZipFile.readEntry(path: String): String? {
        val e = getEntry(path) ?: entries().asSequence().firstOrNull { it.name.equals(path, true) } ?: return null
        return getInputStream(e).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    /** Read an entry's raw bytes, tolerating percent-encoding and case differences in the path. */
    private fun ZipFile.readBytes(path: String): ByteArray? {
        val e = getEntry(path)
            ?: getEntry(android.net.Uri.decode(path))
            ?: entries().asSequence().firstOrNull { it.name.equals(path, true) }
            ?: return null
        return getInputStream(e).use { it.readBytes() }
    }

    /** Resolve an image src (relative to [baseDir], may contain ./ and ../) to a zip entry path. */
    private fun resolveZipPath(baseDir: String, src: String): String {
        val clean = src.substringBefore('#').substringBefore('?')
        val combined = when {
            clean.startsWith("/") -> clean.removePrefix("/")
            baseDir.isEmpty() -> clean
            else -> "$baseDir/$clean"
        }
        val parts = ArrayList<String>()
        for (seg in combined.split('/')) when (seg) {
            "", "." -> {}
            ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
            else -> parts.add(seg)
        }
        return parts.joinToString("/")
    }

    private fun rootfilePath(zip: ZipFile): String? {
        val container = zip.readEntry("META-INF/container.xml") ?: return null
        return Regex("""full-path="([^"]+)"""").find(container)?.groupValues?.get(1)
    }

    private data class Opf(val title: String?, val author: String?, val spineHrefs: List<String>)

    private fun parseOpf(xml: String): Opf {
        val manifest = HashMap<String, String>()   // id -> href
        val spine = ArrayList<String>()             // idrefs in order
        var title: String? = null
        var author: String? = null
        val p = Xml.newPullParser().apply { setInput(xml.reader()) }
        var ev = p.eventType
        var textTarget: String? = null
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                when (p.name) {
                    "item" -> {
                        val id = p.getAttributeValue(null, "id")
                        val href = p.getAttributeValue(null, "href")
                        if (id != null && href != null) manifest[id] = href
                    }
                    "itemref" -> p.getAttributeValue(null, "idref")?.let { spine.add(it) }
                    "title" -> textTarget = "title"
                    "creator" -> textTarget = "creator"
                }
            } else if (ev == XmlPullParser.TEXT && textTarget != null) {
                if (textTarget == "title" && title == null) title = p.text?.trim()
                if (textTarget == "creator" && author == null) author = p.text?.trim()
            } else if (ev == XmlPullParser.END_TAG) {
                textTarget = null
            }
            ev = p.next()
        }
        val hrefs = spine.mapNotNull { manifest[it] }
            .ifEmpty { manifest.values.filter { it.endsWith(".xhtml") || it.endsWith(".html") } }
        return Opf(title, author, hrefs)
    }
}

/** Plain text: decode (UTF-8, fallback GBK for older Chinese files), split to paragraphs. */
object TxtParser {
    fun parse(file: File, title: String): BookDocument {
        val bytes = file.readBytes()
        val text = runCatching { String(bytes, Charsets.UTF_8) }
            .getOrNull()?.takeIf { !it.contains('\uFFFD') }
            ?: runCatching { String(bytes, charset("GBK")) }.getOrNull()
            ?: String(bytes, Charsets.UTF_8)
        val paras = text.split(Regex("\\r?\\n")).map { it.trim() }.filter { it.isNotEmpty() }
        return paragraphsToDoc(title, BookFormat.TXT, paras)
    }
}

/** CBZ / comic archive = a ZIP of page images. Save each page in name order; one scrolling
 *  chapter of images. No OCR (comics have many pages; keep import fast — text isn't expected). */
object CbzParser {
    private val IMG_EXT = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")

    fun parse(context: Context, file: File, title: String): BookDocument {
        val id = UUID.randomUUID().toString()
        ZipFile(file).use { zip ->
            val pages = zip.entries().asSequence()
                .filter { !it.isDirectory && it.name.substringAfterLast('.', "").lowercase() in IMG_EXT }
                .sortedBy { it.name.lowercase() }
                .toList()
            if (pages.isEmpty()) error("No images found in this archive")
            val blocks = ArrayList<Block>(pages.size)
            pages.forEachIndexed { i, e ->
                val ext = e.name.substringAfterLast('.', "jpg").lowercase().take(4)
                val name = "page_%04d.%s".format(i, ext)
                val bytes = zip.getInputStream(e).use { it.readBytes() }
                ImageStore.saveBytes(context, id, name, bytes)
                blocks.add(ImageBlock(name))
            }
            return BookDocument(id, title, null, BookFormat.CBZ, listOf(Chapter(0, null, blocks)))
        }
    }
}
