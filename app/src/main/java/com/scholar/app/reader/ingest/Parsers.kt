package com.scholar.app.reader.ingest

import android.util.Xml
import com.scholar.app.model.BookDocument
import com.scholar.app.model.BookFormat
import com.scholar.app.model.Chapter
import com.scholar.app.model.TextBlock
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile

/** EPUB = a ZIP of XHTML. Parse container.xml → OPF → spine order → strip each
 *  document to paragraphs. Pure Kotlin (java.util.zip + XmlPullParser). */
object EpubParser {

    fun parse(file: File, fallbackTitle: String): BookDocument {
        ZipFile(file).use { zip ->
            val opfPath = rootfilePath(zip) ?: error("Not a valid EPUB (no OPF)")
            val opfText = zip.readEntry(opfPath) ?: error("OPF missing")
            val opfDir = opfPath.substringBeforeLast('/', "")
            val (title, author, hrefs) = parseOpf(opfText)

            val chapters = ArrayList<Chapter>()
            for (href in hrefs) {
                val full = if (opfDir.isEmpty()) href else "$opfDir/$href"
                val html = zip.readEntry(full.removePrefix("/")) ?: continue
                val paras = Html.toParagraphs(html)
                if (paras.isNotEmpty())
                    chapters.add(Chapter(chapters.size, paras.firstOrNull()?.take(24), paras.map { TextBlock(it) }))
            }
            if (chapters.isEmpty()) error("EPUB had no readable text")
            return BookDocument(UUID.randomUUID().toString(), title ?: fallbackTitle,
                author, BookFormat.EPUB, chapters)
        }
    }

    private fun ZipFile.readEntry(path: String): String? {
        val e = getEntry(path) ?: entries().asSequence().firstOrNull { it.name.equals(path, true) } ?: return null
        return getInputStream(e).bufferedReader(Charsets.UTF_8).use { it.readText() }
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
