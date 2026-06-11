package com.tianxian.core.data.repo

import com.tianxian.core.model.Block
import com.tianxian.core.model.BookDocument
import com.tianxian.core.model.Chapter
import com.tianxian.core.model.BookFormat
import com.tianxian.core.model.ImageBlock
import com.tianxian.core.model.TextBlock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Serialises a normalised BookDocument to/from JSON on disk. Important for OCR'd
 *  books, where re-processing on every open would be slow. */
object BookCache {

    fun write(dir: File, doc: BookDocument): File {
        val f = File(dir, "book_${doc.id}.json")
        val root = JSONObject()
            .put("id", doc.id).put("title", doc.title).put("author", doc.author ?: JSONObject.NULL)
            .put("format", doc.format.name)
        val chapters = JSONArray()
        doc.chapters.forEach { ch ->
            val blocks = JSONArray()
            ch.blocks.forEach { b ->
                when (b) {
                    is TextBlock -> blocks.put(JSONObject().put("k", "t").put("v", b.text))
                    is ImageBlock -> blocks.put(JSONObject().put("k", "i").put("v", b.file)
                        .put("c", b.caption ?: JSONObject.NULL))
                }
            }
            chapters.put(JSONObject().put("i", ch.index).put("t", ch.title ?: JSONObject.NULL).put("b", blocks))
        }
        root.put("chapters", chapters)
        val profile = JSONObject()
        doc.charProfile.forEach { (c, n) -> profile.put(c.toString(), n) }
        root.put("profile", profile)
        f.writeText(root.toString())
        return f
    }

    fun read(file: File): BookDocument {
        val root = JSONObject(file.readText())
        val chapters = ArrayList<Chapter>()
        val arr = root.getJSONArray("chapters")
        for (i in 0 until arr.length()) {
            val c = arr.getJSONObject(i)
            val b = c.getJSONArray("b")
            val blocks = ArrayList<Block>(b.length())
            for (j in 0 until b.length()) {
                // new format: {k,v[,c]}; legacy format: a bare string == a text block
                val obj = b.optJSONObject(j)
                if (obj == null) {
                    blocks.add(TextBlock(b.getString(j)))
                } else if (obj.optString("k") == "i") {
                    blocks.add(ImageBlock(obj.getString("v"), obj.optString("c").takeIf { it.isNotEmpty() }))
                } else {
                    blocks.add(TextBlock(obj.optString("v")))
                }
            }
            chapters.add(Chapter(c.getInt("i"), c.optString("t").takeIf { it.isNotEmpty() }, blocks))
        }
        val profile = HashMap<Char, Int>()
        val p = root.getJSONObject("profile")
        p.keys().forEach { k -> if (k.isNotEmpty()) profile[k[0]] = p.getInt(k) }
        return BookDocument(
            root.getString("id"), root.getString("title"),
            root.optString("author").takeIf { it.isNotEmpty() },
            BookFormat.valueOf(root.getString("format")), chapters, profile,
        )
    }
}
