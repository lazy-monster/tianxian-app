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
import com.scholar.app.reader.ingest.Ingestor
import com.scholar.app.srs.CardType
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
    fun toned(numericPinyin: String) = Pinyin.toned(numericPinyin)
    fun genreTerms(): List<GenreTerm> = content.genreTerms()
    fun radicals() = content.radicals()
    fun strokeData(ch: String) = content.strokeData(ch)
    fun components(ch: String) = content.components(ch)
    fun charactersWithRadical(radical: String) = content.charactersWithRadical(radical)
    fun hskWords(levelKey: String, limit: Int = 200) = content.hskWords(levelKey, limit)
}

/* ── Known characters (drives "Characters Known") ───────────────────── */
class KnownRepository(private val dao: KnownDao) {
    fun knownCountFlow(): Flow<Int> = dao.knownCountFlow()
    suspend fun knownSet(): Set<String> = dao.knownChars().toHashSet()

    suspend fun reinforce(text: String, delta: Double) {
        val now = System.currentTimeMillis()
        text.forEach { c ->
            if (c.code in 0x4E00..0x9FFF) {
                dao.upsert(KnownCharEntity(c.toString(), delta.coerceIn(0.0, 1.0), now, now))
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
    private val scheduler: Scheduler = Scheduler(),
) {
    fun dueCountFlow(): Flow<Int> = cardDao.dueCountFlow(System.currentTimeMillis())
    fun totalFlow(): Flow<Int> = cardDao.totalFlow()
    fun masteredCountFlow(): Flow<Int> = cardDao.masteredCountFlow()
    fun genreLearnedCountFlow(): Flow<Int> = cardDao.genreLearnedCountFlow()
    suspend fun due(limit: Int = 200): List<CardEntity> = cardDao.due(System.currentTimeMillis(), limit)

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

    fun project(card: CardEntity) = scheduler.project(
        card.stability, card.difficulty, elapsedDays(card), isNew = card.reps == 0,
    )

    suspend fun grade(card: CardEntity, rating: Rating) {
        val now = System.currentTimeMillis()
        val res = scheduler.apply(card.stability, card.difficulty, elapsedDays(card), card.reps == 0, rating)
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
            elapsedDays = elapsedDays(card),
        ))
        // recognition success strengthens the underlying character(s)
        if (rating != Rating.AGAIN) known.reinforce(card.frontRef, 0.7)
    }

    private fun elapsedDays(card: CardEntity): Double =
        if (card.reps == 0) 0.0
        else ((System.currentTimeMillis() - card.dueEpochMillis).coerceAtLeast(0)) / 86_400_000.0

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

    suspend fun savePosition(id: String, chapter: Int, block: Int) {
        bookDao.get(id)?.let { bookDao.upsert(it.copy(posChapter = chapter, posBlock = block)) }
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
