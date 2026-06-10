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

/** A bilingual example sentence (Tatoeba). */
data class Sentence(val zh: String, val en: String)

class ContentStore private constructor(
    private val db: SQLiteDatabase,
    /** Optional Tatoeba example-sentence bank; null when the asset isn't bundled. */
    private val sentencesDb: SQLiteDatabase?,
) {

    // ── dictionary ──────────────────────────────────────────────────────
    fun lookupWord(word: String): DictEntry? = db.rawQuery(
        "SELECT simplified,traditional,pinyin,gloss,freq_rank FROM dict_entry " +
            "WHERE simplified=? ORDER BY freq_rank IS NULL, freq_rank LIMIT 1", arrayOf(word)
    ).use { if (it.moveToFirst()) it.toEntry() else null }

    /** Each dictionary reading of a single character (numeric pinyin), most common sense first —
        used to detect polyphones (多音字) for pronunciation-safe audio. */
    fun readingsOf(ch: String): List<String> = db.rawQuery(
        "SELECT pinyin FROM dict_entry WHERE simplified=? ORDER BY freq_rank IS NULL, freq_rank",
        arrayOf(ch)
    ).use { c -> buildList { while (c.moveToNext()) c.getString(0)?.let { add(it) } } }

    /** Frequent two-character words containing [ch] — carrier candidates so a secondary reading
        can be *heard* (the TTS reads a lone polyphone with its default reading only). */
    fun wordsContaining(ch: String, limit: Int = 30): List<DictEntry> = db.rawQuery(
        "SELECT simplified,traditional,pinyin,gloss,freq_rank FROM dict_entry " +
            "WHERE simplified LIKE ? AND length(simplified)=2 AND freq_rank IS NOT NULL " +
            "ORDER BY freq_rank LIMIT ?", arrayOf("%$ch%", limit.toString())
    ).use { c -> buildList { while (c.moveToNext()) add(c.toEntry()) } }

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

    /** A random reasonably-common character (has a frequency rank + gloss) — for the home card
        and the widget's rotating "character of the moment". */
    fun randomCharacter(): CharInfo? = db.rawQuery(
        "SELECT char,pinyin,definition,decomposition,radical,stroke_count,hsk3,freq_rank " +
            "FROM character WHERE freq_rank IS NOT NULL AND definition IS NOT NULL AND definition<>'' " +
            "ORDER BY RANDOM() LIMIT 1", null
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
    // Some hsk_word rows carry only a cross-reference gloss — the source picked the wrong
    // polyphone reading, e.g. 都 → "surname Du" instead of "all; entirely". When a row has no
    // learnable sense, fall back to the merged dictionary gloss (all readings); failing that, to
    // the gloss of the word it points at ("variant of 火爆" → 火爆's gloss). See Gloss.hasRealSense.
    fun hskWords(levelKey: String, limit: Int = 200): List<HskWord> = db.rawQuery(
        "SELECT h.word, h.pinyin, h.meaning, h.level, " +
            "(SELECT group_concat(d.gloss, ' / ') FROM dict_entry d WHERE d.simplified=h.word) AS dict_gloss " +
            "FROM hsk_word h WHERE h.level LIKE ? " +
            "ORDER BY h.freq_rank IS NULL, h.freq_rank LIMIT ?", arrayOf("%$levelKey%", limit.toString())
    ).use { c ->
        buildList {
            while (c.moveToNext()) {
                val word = c.getString(0)
                val raw = c.getString(2) ?: ""
                add(HskWord(word, c.getString(1) ?: "",
                    bestMeaning(word, raw, c.getString(4)), c.getString(3) ?: ""))
            }
        }
    }

    /** Pick the most learnable gloss for an HSK entry whose own [raw] meaning may be cross-ref
        noise: keep it if it has a real sense, else the word's [merged] dictionary gloss, else the
        gloss of the word it cross-references. Falls back to [raw] if nothing better turns up. */
    private fun bestMeaning(word: String, raw: String, merged: String?): String {
        if (Gloss.hasRealSense(raw)) return raw
        val m = merged?.takeIf { it.isNotBlank() } ?: ""
        if (Gloss.hasRealSense(m)) return m
        Gloss.crossRefTarget(m.ifBlank { raw })?.let { target ->
            mergedGloss(target).takeIf { Gloss.hasRealSense(it) }?.let { return it }
        }
        return raw
    }

    /** All dictionary readings of [word] joined into one gloss string. */
    private fun mergedGloss(word: String): String = db.rawQuery(
        "SELECT group_concat(gloss, ' / ') FROM dict_entry WHERE simplified=?", arrayOf(word)
    ).use { if (it.moveToFirst()) it.getString(0) ?: "" else "" }

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

    // ── example sentences (optional Tatoeba bank) ───────────────────────
    /** Bilingual example sentences containing [word]; shortest first. Empty if no bank. */
    fun examples(word: String, limit: Int = 4): List<Sentence> {
        val sdb = sentencesDb ?: return emptyList()
        if (word.isBlank() || word.none { it.code in 0x4E00..0x9FFF }) return emptyList()
        val like = "%" + word.replace("%", "").replace("_", "") + "%"
        return runCatching {
            sdb.rawQuery(
                "SELECT zh,en FROM sentence WHERE zh LIKE ? ORDER BY n LIMIT ?",
                arrayOf(like, limit.toString())
            ).use { c -> buildList { while (c.moveToNext()) add(Sentence(c.getString(0), c.getString(1))) } }
        }.getOrDefault(emptyList())
    }

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
        private const val DB_VERSION = 2          // bump whenever assets/content.db changes
        private const val SENTENCES_VERSION = 1   // bump whenever assets/sentences.db changes
        private fun open(context: Context): ContentStore {
            val main = copyAsset(context, "content.db", DB_VERSION)
                ?: error("content.db missing from assets")
            val sentences = copyAsset(context, "sentences.db", SENTENCES_VERSION)
            return ContentStore(
                SQLiteDatabase.openDatabase(main.path, null, SQLiteDatabase.OPEN_READONLY),
                sentences?.let {
                    runCatching { SQLiteDatabase.openDatabase(it.path, null, SQLiteDatabase.OPEN_READONLY) }.getOrNull()
                },
            )
        }

        /** Copy a bundled DB out of assets on first run / version bump. Returns null (rather
            than throwing) if the asset isn't present, so optional banks degrade gracefully. */
        private fun copyAsset(context: Context, name: String, version: Int): File? {
            val out = File(context.filesDir, name)
            val marker = File(context.filesDir, "$name.version")
            val current = if (marker.exists()) marker.readText().trim() else ""
            if (!out.exists() || out.length() == 0L || current != version.toString()) {
                return try {
                    context.assets.open(name).use { input -> out.outputStream().use { input.copyTo(it) } }
                    marker.writeText(version.toString())
                    out
                } catch (e: java.io.FileNotFoundException) {
                    null
                }
            }
            return out
        }
    }
}
