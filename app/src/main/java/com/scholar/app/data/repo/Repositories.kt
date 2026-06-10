package com.scholar.app.data.repo

import android.content.Context
import android.net.Uri
import com.scholar.app.data.content.CharInfo
import com.scholar.app.data.content.ContentStore
import com.scholar.app.data.content.DictEntry
import com.scholar.app.data.content.GenreTerm
import com.scholar.app.data.content.Pinyin
import com.scholar.app.data.user.*
import com.scholar.app.model.BookDocument
import com.scholar.app.model.ReadingPosition
import com.scholar.app.reader.ingest.Ingestor
import com.scholar.app.reader.ingest.ImageStore
import com.scholar.app.srs.CardType
import com.scholar.app.srs.Fsrs6
import com.scholar.app.srs.Rating
import com.scholar.app.srs.Scheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/* ── Dictionary ─────────────────────────────────────────────────────── */
class DictionaryRepository(private val content: ContentStore) {
    fun lookup(word: String): DictEntry? = content.lookupWord(word)
    fun search(q: String) = content.search(q)
    fun character(ch: String): CharInfo? = content.character(ch)
    fun randomCharacter(): CharInfo? = content.randomCharacter()
    fun toned(numericPinyin: String) = Pinyin.toned(numericPinyin)
    fun genreTerms(): List<GenreTerm> = content.genreTerms()
    fun radicals() = content.radicals()
    fun strokeData(ch: String) = content.strokeData(ch)
    fun components(ch: String) = content.components(ch)
    fun charactersWithRadical(radical: String) = content.charactersWithRadical(radical)
    fun hskWords(levelKey: String, limit: Int = 200) = content.hskWords(levelKey, limit)
    fun examples(word: String, limit: Int = 4) = content.examples(word, limit)

    /**
     * What to feed the TTS so a *single character* is pronounced with the reading shown on screen.
     * Engines pick a lone polyphone's (多音字) default reading from no context — 还 alone says
     * "hái" even when the card teaches huán. When the displayed reading is a secondary one,
     * return a common word carrying that reading (归还) so the user hears what they see;
     * otherwise the character itself. Multi-character text passes through untouched (engines
     * disambiguate words correctly).
     */
    fun audioTextFor(text: String, displayedPinyin: String): String {
        if (text.length != 1 || text[0].code !in 0x4E00..0x9FFF) return text
        val target = firstSyllable(displayedPinyin) ?: return text
        val readings = content.readingsOf(text).map { norm(Pinyin.toned(it)) }.distinct()
        if (readings.size <= 1 || readings.first() == target) return text   // default reading is right
        if (target !in readings) return text                                // unknown reading — don't guess
        val carrier = content.wordsContaining(text).firstOrNull { e ->
            val i = e.simplified.indexOf(text)
            i >= 0 && norm(Pinyin.tonedSyllables(e.pinyin).getOrNull(i) ?: "") == target
        }
        return carrier?.simplified ?: text
    }

    private fun norm(s: String): String =
        java.text.Normalizer.normalize(s.trim().lowercase(), java.text.Normalizer.Form.NFC)

    /** First syllable of a displayed pinyin ("hái; huán" → "hái"), tolerating numeric input. */
    private fun firstSyllable(p: String): String? {
        val tok = p.split(' ', ';', ',', '/', '·').firstOrNull { it.isNotBlank() }?.trim() ?: return null
        return norm(if (tok.any { it.isDigit() }) Pinyin.toned(tok) else tok).ifBlank { null }
    }
}

/* ── Known characters (drives "Characters Known") ───────────────────── */
class KnownRepository(private val dao: KnownDao) {
    fun knownCountFlow(): Flow<Int> = dao.knownCountFlow()
    suspend fun knownSet(): Set<String> = dao.knownChars().toHashSet()

    /** Strengthen the given characters. Strength only ratchets up (a later, weaker signal must
        not erase "mark known"), and the original firstSeen timestamp is preserved. */
    suspend fun reinforce(text: String, delta: Double) {
        val now = System.currentTimeMillis()
        val d = delta.coerceIn(0.0, 1.0)
        text.forEach { c ->
            if (c.code in 0x4E00..0x9FFF) {
                val prev = dao.get(c.toString())
                dao.upsert(KnownCharEntity(c.toString(), maxOf(prev?.strength ?: 0.0, d),
                    prev?.firstSeenMillis ?: now, now))
            }
        }
    }
    suspend fun markKnown(word: String) = reinforce(word, 0.9)
}

/* ── Cards + SRS ────────────────────────────────────────────────────── */
class CardRepository(
    private val cardDao: CardDao,
    private val logDao: ReviewLogDao,
    private val known: KnownRepository,
    /** Pulls the live desired-retention from settings so the slider takes effect without a
        restart; a fresh Scheduler is cheap to build per scheduling call. */
    private val retention: () -> Double = { 0.92 },
) {
    private fun scheduler() = Scheduler(Fsrs6(desiredRetention = retention().coerceIn(0.80, 0.97)))

    fun dueCountFlow(): Flow<Int> = cardDao.dueCountFlow(System.currentTimeMillis())
    fun totalFlow(): Flow<Int> = cardDao.totalFlow()
    fun masteredCountFlow(): Flow<Int> = cardDao.masteredCountFlow()
    fun genreLearnedCountFlow(): Flow<Int> = cardDao.genreLearnedCountFlow()
    suspend fun due(limit: Int = 200): List<CardEntity> = cardDao.due(System.currentTimeMillis(), limit)

    /** The soonest-due cards even if not due yet — for an optional "review ahead" session. */
    suspend fun ahead(limit: Int = 30): List<CardEntity> = cardDao.ahead(limit)

    suspend fun dueCountNow(): Int = cardDao.dueCount(System.currentTimeMillis())

    /** Of [fronts], the subset already present in the deck — lets the level lists show what's
        been mined and figure out the "next batch". */
    suspend fun minedAmong(fronts: List<String>): Set<String> =
        if (fronts.isEmpty()) emptySet() else cardDao.existingFronts(fronts).toHashSet()

    /** One-tap mining from the reader/dictionary. */
    suspend fun mine(front: String, back: String, type: CardType, source: String?) {
        if (cardDao.find(front, type.name) != null) return
        val now = System.currentTimeMillis()
        cardDao.insert(
            CardEntity(
                type = type.name, frontRef = front, backRef = back,
                dueEpochDay = epochDay(now), dueEpochMillis = now, source = source,
            )
        )
    }

    /** A single card by id — used to re-queue a freshly graded "Again" card in-session. */
    suspend fun card(id: Long): CardEntity? = cardDao.get(id)

    /** Cards for [ids], in the same order (duplicates preserved) — restores a saved session queue. */
    suspend fun byIds(ids: List<Long>): List<CardEntity> {
        if (ids.isEmpty()) return emptyList()
        val byId = cardDao.byIds(ids.distinct()).associateBy { it.id }
        return ids.mapNotNull { byId[it] }
    }

    suspend fun project(card: CardEntity) = scheduler().project(
        card.stability, card.difficulty, elapsedDays(card), reps = card.reps,
    )

    suspend fun grade(card: CardEntity, rating: Rating) {
        val now = System.currentTimeMillis()
        val elapsed = elapsedDays(card)
        val res = scheduler().apply(card.stability, card.difficulty, elapsed, card.reps, rating)
        val dueMillis = now + (res.intervalDays * 86_400_000L).toLong()
        cardDao.update(
            card.copy(
                stability = res.stability, difficulty = res.difficulty,
                reps = card.reps + 1,
                lapses = if (rating == Rating.AGAIN) card.lapses + 1 else card.lapses,
                dueEpochMillis = dueMillis, dueEpochDay = epochDay(dueMillis),
            )
        )
        logDao.insert(ReviewLogEntity(
            cardId = card.id, rating = rating.value, reviewedAtMillis = now,
            lastStability = card.stability, lastDifficulty = card.difficulty,
            elapsedDays = elapsed,
        ))
        // recognition success strengthens the underlying character(s)
        if (rating != Rating.AGAIN) known.reinforce(card.frontRef, 0.7)
    }

    /** Days since this card was last *reviewed* (not since it came due) — what the FSRS
        forgetting curve is parameterised on. Anchored to the review log; for cards that
        predate per-card logs, reconstructed as overdue time plus the scheduled interval. */
    private suspend fun elapsedDays(card: CardEntity): Double {
        if (card.reps == 0) return 0.0
        val now = System.currentTimeMillis()
        logDao.lastReviewFor(card.id)?.let { return (now - it).coerceAtLeast(0) / 86_400_000.0 }
        val overdue = (now - card.dueEpochMillis) / 86_400_000.0
        return (overdue + scheduler().fsrs.intervalDays(card.stability)).coerceAtLeast(0.0)
    }

    private fun epochDay(millis: Long): Long =
        java.time.Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
}

/* ── Books + import ─────────────────────────────────────────────────── */
class BookRepository(
    private val context: Context,
    private val bookDao: BookDao,
    private val ingestor: Ingestor,
    private val known: KnownRepository,
) {
    fun booksFlow(): Flow<List<BookEntity>> = bookDao.allFlow()

    suspend fun import(uri: Uri, displayName: String?): Result<BookEntity> = withContext(Dispatchers.IO) {
        ingestor.ingest(uri, displayName).mapCatching { doc ->
            val cacheFile = BookCache.write(context.filesDir, doc)
            val coverage = coverage(doc, known.knownSet())
            val entity = BookEntity(
                id = doc.id, title = doc.title, author = doc.author, format = doc.format.name,
                addedAtMillis = System.currentTimeMillis(), coverage = coverage, cachePath = cacheFile.path,
            )
            bookDao.upsert(entity)
            entity
        }
    }

    suspend fun document(id: String): BookDocument? = withContext(Dispatchers.IO) {
        val e = bookDao.get(id) ?: return@withContext null
        runCatching { BookCache.read(File(e.cachePath)) }.getOrNull()
    }

    suspend fun savePosition(id: String, chapter: Int, block: Int) =
        bookDao.updatePosition(id, chapter, block)

    /** Where the reader should resume this book (defaults to the start for new/unknown books). */
    suspend fun position(id: String): ReadingPosition =
        bookDao.get(id)?.let { ReadingPosition(it.posChapter, it.posBlock) } ?: ReadingPosition(0, 0)

    /** Recompute every book's "X% readable" against what the user knows *now*. Coverage is
        cached at import time and goes stale as characters are learned; this refreshes it from
        the cached char profiles. Throttled — it re-reads each book's cache file. */
    suspend fun refreshCoverage(): Unit = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (now - lastCoverageRefresh < 60_000L) return@withContext
        lastCoverageRefresh = now
        val knownSet = known.knownSet()
        bookDao.all().forEach { e ->
            val doc = runCatching { BookCache.read(File(e.cachePath)) }.getOrNull() ?: return@forEach
            val cov = coverage(doc, knownSet)
            if (cov != e.coverage) bookDao.updateCoverage(e.id, cov)
        }
    }
    @Volatile private var lastCoverageRefresh = 0L

    /** Remove a book entirely: its row, cached text, and any page images. */
    suspend fun delete(id: String) {
        val e = bookDao.get(id) ?: return
        runCatching { File(e.cachePath).delete() }
        ImageStore.delete(context, id)
        bookDao.delete(e)
    }

    /** Fraction of Han-character occurrences in the book the user already knows. */
    private fun coverage(doc: BookDocument, knownSet: Set<String>): Float {
        var total = 0L; var knownN = 0L
        doc.charProfile.forEach { (c, n) ->
            total += n
            if (c.toString() in knownSet) knownN += n
        }
        return if (total == 0L) 0f else (knownN.toDouble() / total).toFloat()
    }
}
