package com.scholar.app.data.content

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import java.io.File

/* Read-only access to the bundled content.db (CC-CEDICT + characters + strokes +
   radicals + HSK + genre). Copied out of assets on first launch, then opened directly. */

data class DictEntry(
    val simplified: String, val traditional: String,
    val pinyin: String, val gloss: String, val freqRank: Int?,
)

data class CharInfo(
    val char: String, val pinyin: String, val definition: String,
    val decomposition: String, val radical: String,
    val strokeCount: Int?, val hsk3: Int?, val freqRank: Int?,
)

data class GenreTerm(
    val word: String, val category: String, val pinyin: String,
    val gloss: String, val realmRank: Int?,
)

data class Radical(val number: Int, val radical: String, val pinyin: String, val meaning: String)

data class HskWord(val word: String, val pinyin: String, val meaning: String, val level: String)

/** Stroke-order data for one character (Make Me a Hanzi format, 1024×1024 grid, y-down). */
data class StrokeData(val strokes: List<String>, val medians: List<List<Pair<Float, Float>>>)

class ContentStore private constructor(private val db: SQLiteDatabase) {

    // ── dictionary ──────────────────────────────────────────────────────
    fun lookupWord(word: String): DictEntry? = db.rawQuery(
        "SELECT simplified,traditional,pinyin,gloss,freq_rank FROM dict_entry " +
            "WHERE simplified=? ORDER BY freq_rank IS NULL, freq_rank LIMIT 1", arrayOf(word)
    ).use { if (it.moveToFirst()) it.toEntry() else null }

    /** Crash-proof search across hanzi (prefix), pinyin, and English (FTS). */
    fun search(query: String, limit: Int = 40): List<DictEntry> = runCatching {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()

        // Chinese input → direct prefix match, no FTS (avoids tokenizer surprises)
        if (q.any { it.code in 0x4E00..0x9FFF }) {
            val like = q.replace("%", "").replace("_", "") + "%"
            return db.rawQuery(
                "SELECT simplified,traditional,pinyin,gloss,freq_rank FROM dict_entry " +
                    "WHERE simplified LIKE ? OR traditional LIKE ? " +
                    "ORDER BY freq_rank IS NULL, freq_rank LIMIT ?",
                arrayOf(like, like, limit.toString())
            ).use { c -> buildList { while (c.moveToNext()) add(c.toEntry()) } }
        }

        // pinyin / English → FTS5 with each token quoted then prefixed (safe against punctuation)
        val match = q.split(Regex("\\s+")).filter { it.isNotBlank() }
            .joinToString(" ") { "\"" + it.replace("\"", "") + "\"*" }
        if (match.isBlank()) return emptyList()
        db.rawQuery(
            "SELECT d.simplified,d.traditional,d.pinyin,d.gloss,d.freq_rank " +
                "FROM dict_fts f JOIN dict_entry d ON d.id=f.rowid WHERE dict_fts MATCH ? " +
                "ORDER BY d.freq_rank IS NULL, d.freq_rank LIMIT ?",
            arrayOf(match, limit.toString())
        ).use { c -> buildList { while (c.moveToNext()) add(c.toEntry()) } }
    }.getOrDefault(emptyList())

    // ── characters & components ─────────────────────────────────────────
    fun character(ch: String): CharInfo? = db.rawQuery(
        "SELECT char,pinyin,definition,decomposition,radical,stroke_count,hsk3,freq_rank " +
            "FROM character WHERE char=?", arrayOf(ch)
    ).use { if (it.moveToFirst()) it.toCharInfo() else null }

    /** Component characters of a character, parsed from its IDS decomposition string. */
    fun components(ch: String): List<CharInfo> {
        val decomp = character(ch)?.decomposition ?: return emptyList()
        return decomp.filter { it.code in 0x4E00..0x9FFF && it.toString() != ch }
            .toCharArray().distinct().mapNotNull { character(it.toString()) }
    }

    /** Frequent characters that use a given radical — examples for the radical lessons. */
    fun charactersWithRadical(radical: String, limit: Int = 30): List<CharInfo> = db.rawQuery(
        "SELECT char,pinyin,definition,decomposition,radical,stroke_count,hsk3,freq_rank " +
            "FROM character WHERE radical=? AND freq_rank IS NOT NULL " +
            "ORDER BY freq_rank LIMIT ?", arrayOf(radical, limit.toString())
    ).use { c -> buildList { while (c.moveToNext()) add(c.toCharInfo()) } }

    // ── stroke-order data (handwriting) ─────────────────────────────────
    fun strokeData(ch: String): StrokeData? = db.rawQuery(
        "SELECT strokes,medians FROM char_strokes WHERE char=?", arrayOf(ch)
    ).use {
        if (!it.moveToFirst()) return null
        val strokes = JSONArray(it.getString(0)).let { a -> List(a.length()) { i -> a.getString(i) } }
        val medians = JSONArray(it.getString(1)).let { a ->
            List(a.length()) { i ->
                val pts = a.getJSONArray(i)
                List(pts.length()) { j ->
                    val p = pts.getJSONArray(j); p.getInt(0).toFloat() to p.getInt(1).toFloat()
                }
            }
        }
        StrokeData(strokes, medians)
    }

    // ── radicals ────────────────────────────────────────────────────────
    fun radicals(): List<Radical> = db.rawQuery(
        "SELECT number,radical,pinyin,meaning FROM radical ORDER BY number", null
    ).use { c -> buildList { while (c.moveToNext()) add(Radical(c.getInt(0), c.getString(1), c.getString(2), c.getString(3))) } }

    // ── HSK leveled vocabulary ──────────────────────────────────────────
    fun hskWords(levelKey: String, limit: Int = 200): List<HskWord> = db.rawQuery(
        "SELECT word,pinyin,meaning,level FROM hsk_word WHERE level LIKE ? " +
            "ORDER BY freq_rank IS NULL, freq_rank LIMIT ?", arrayOf("%$levelKey%", limit.toString())
    ).use { c -> buildList { while (c.moveToNext()) add(HskWord(c.getString(0), c.getString(1) ?: "", c.getString(2) ?: "", c.getString(3) ?: "")) } }

    // ── genre module ────────────────────────────────────────────────────
    fun genreTerms(): List<GenreTerm> = db.rawQuery(
        "SELECT word,category,pinyin,gloss,realm_rank FROM genre_term", null
    ).use { c ->
        buildList {
            while (c.moveToNext()) add(GenreTerm(c.getString(0), c.getString(1), c.getString(2) ?: "",
                c.getString(3) ?: "", if (c.isNull(4)) null else c.getInt(4)))
        }
    }

    fun loadVocabulary(): Set<String> = db.rawQuery(
        "SELECT DISTINCT simplified FROM dict_entry WHERE length(simplified)>1", null
    ).use { c -> HashSet<String>(120_000).apply { while (c.moveToNext()) add(c.getString(0)) } }

    fun meta(key: String): String? = db.rawQuery(
        "SELECT value FROM meta WHERE key=?", arrayOf(key)
    ).use { if (it.moveToFirst()) it.getString(0) else null }

    private fun Cursor.toEntry() = DictEntry(
        getString(0), getString(1) ?: "", getString(2) ?: "", getString(3) ?: "",
        if (isNull(4)) null else getInt(4))

    private fun Cursor.toCharInfo() = CharInfo(
        getString(0), getString(1) ?: "", getString(2) ?: "", getString(3) ?: "",
        getString(4) ?: "", if (isNull(5)) null else getInt(5),
        if (isNull(6)) null else getInt(6), if (isNull(7)) null else getInt(7))

    companion object {
        @Volatile private var INSTANCE: ContentStore? = null
        fun get(context: Context): ContentStore = INSTANCE ?: synchronized(this) {
            INSTANCE ?: open(context.applicationContext).also { INSTANCE = it }
        }
        private const val DB_VERSION = 2   // bump whenever assets/content.db changes
        private fun open(context: Context): ContentStore {
            val out = File(context.filesDir, "content.db")
            val marker = File(context.filesDir, "content.db.version")
            val current = if (marker.exists()) marker.readText().trim() else ""
            if (!out.exists() || out.length() == 0L || current != DB_VERSION.toString()) {
                context.assets.open("content.db").use { input -> out.outputStream().use { input.copyTo(it) } }
                marker.writeText(DB_VERSION.toString())
            }
            return ContentStore(SQLiteDatabase.openDatabase(out.path, null, SQLiteDatabase.OPEN_READONLY))
        }
    }
}
