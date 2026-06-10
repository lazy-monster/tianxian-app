package com.scholar.app.data.user

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

/* All mutable user state lives here, separate from the read-only content DB so
   content can be updated without touching progress. */

@Entity(tableName = "cards", indices = [Index(value = ["frontRef", "type"], unique = true)])
data class CardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,                 // CardType name
    val frontRef: String,             // hanzi / word / sentence id
    val backRef: String,              // gloss / pinyin
    val stability: Double = 0.0,
    val difficulty: Double = 0.0,
    val dueEpochDay: Long,            // due date as epoch-day for cheap "due today" queries
    val dueEpochMillis: Long,
    val lapses: Int = 0,
    val reps: Int = 0,
    val suspended: Boolean = false,
    val source: String? = null,
)

@Entity(tableName = "review_log")
data class ReviewLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardId: Long,
    val rating: Int,
    val reviewedAtMillis: Long,
    val lastStability: Double,
    val lastDifficulty: Double,
    val elapsedDays: Double,
)

@Entity(tableName = "known_chars")
data class KnownCharEntity(
    @PrimaryKey val char: String,
    val strength: Double,             // ~ recall probability proxy
    val firstSeenMillis: Long,
    val lastSeenMillis: Long,
)

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String?,
    val format: String,
    val addedAtMillis: Long,
    val posChapter: Int = 0,
    val posBlock: Int = 0,
    val coverage: Float = 0f,         // cached known-char ratio
    val cachePath: String,            // normalized BookDocument JSON on disk
)

@Dao
interface CardDao {
    @Query("SELECT * FROM cards WHERE suspended=0 AND dueEpochMillis<=:now ORDER BY dueEpochMillis LIMIT :limit")
    suspend fun due(now: Long, limit: Int = 200): List<CardEntity>

    /** Soonest-due cards regardless of whether they're due yet — powers "review ahead". */
    @Query("SELECT * FROM cards WHERE suspended=0 ORDER BY dueEpochMillis LIMIT :limit")
    suspend fun ahead(limit: Int = 30): List<CardEntity>

    @Query("SELECT COUNT(*) FROM cards WHERE suspended=0 AND dueEpochMillis<=:now")
    fun dueCountFlow(now: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM cards WHERE suspended=0 AND dueEpochMillis<=:now")
    suspend fun dueCount(now: Long): Int

    /** Soonest future due time after [now], for a widget "next review in …" countdown. */
    @Query("SELECT MIN(dueEpochMillis) FROM cards WHERE suspended=0 AND dueEpochMillis>:now")
    suspend fun nextDueMillis(now: Long): Long?

    @Query("SELECT COUNT(*) FROM cards WHERE suspended=0 AND stability>=:minStability")
    suspend fun masteredCount(minStability: Double = 21.0): Int

    @Query("SELECT COUNT(*) FROM cards WHERE suspended=0 AND stability>=:minStability AND (source LIKE 'Genre%' OR source LIKE 'Realm%')")
    suspend fun genreLearnedCount(minStability: Double = 7.0): Int

    @Query("SELECT * FROM cards WHERE frontRef=:front AND type=:type LIMIT 1")
    suspend fun find(front: String, type: String): CardEntity?

    @Query("SELECT * FROM cards WHERE id=:id")
    suspend fun get(id: Long): CardEntity?

    @Query("SELECT * FROM cards WHERE id IN (:ids)")
    suspend fun byIds(ids: List<Long>): List<CardEntity>

    @Query("SELECT frontRef FROM cards WHERE frontRef IN (:fronts)")
    suspend fun existingFronts(fronts: List<String>): List<String>

    @Query("SELECT COUNT(*) FROM cards")
    fun totalFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM cards WHERE suspended=0 AND stability>=:minStability")
    fun masteredCountFlow(minStability: Double = 21.0): Flow<Int>

    @Query("SELECT COUNT(*) FROM cards WHERE suspended=0 AND stability>=:minStability AND (source LIKE 'Genre%' OR source LIKE 'Realm%')")
    fun genreLearnedCountFlow(minStability: Double = 7.0): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(card: CardEntity): Long
    @Update suspend fun update(card: CardEntity)

    // backup / restore
    @Query("SELECT * FROM cards") suspend fun all(): List<CardEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(cards: List<CardEntity>)
    @Query("DELETE FROM cards") suspend fun clear()
}

@Dao
interface ReviewLogDao {
    @Insert suspend fun insert(log: ReviewLogEntity)
    @Query("SELECT COUNT(*) FROM review_log WHERE reviewedAtMillis>=:since") suspend fun countSince(since: Long): Int
    @Query("SELECT MAX(reviewedAtMillis) FROM review_log") suspend fun lastReviewMillis(): Long?

    /** When this card was last reviewed — the elapsed-time anchor FSRS needs. */
    @Query("SELECT MAX(reviewedAtMillis) FROM review_log WHERE cardId=:cardId")
    suspend fun lastReviewFor(cardId: Long): Long?

    // backup / restore
    @Query("SELECT * FROM review_log") suspend fun all(): List<ReviewLogEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(logs: List<ReviewLogEntity>)
    @Query("DELETE FROM review_log") suspend fun clear()
}

@Dao
interface KnownDao {
    @Query("SELECT COUNT(*) FROM known_chars WHERE strength>=:threshold")
    fun knownCountFlow(threshold: Double = 0.6): Flow<Int>

    @Query("SELECT char FROM known_chars WHERE strength>=:threshold")
    suspend fun knownChars(threshold: Double = 0.6): List<String>

    @Query("SELECT COUNT(*) FROM known_chars WHERE strength>=:threshold")
    suspend fun knownCount(threshold: Double = 0.6): Int

    // the parameter must not be named `char`: Room emits *Java*, where char is a keyword
    @Query("SELECT * FROM known_chars WHERE char=:ch")
    suspend fun get(ch: String): KnownCharEntity?

    @Upsert suspend fun upsert(known: KnownCharEntity)

    // backup / restore
    @Query("SELECT * FROM known_chars") suspend fun all(): List<KnownCharEntity>
    @Upsert suspend fun upsertAll(chars: List<KnownCharEntity>)
    @Query("DELETE FROM known_chars") suspend fun clear()
}

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY addedAtMillis DESC") fun allFlow(): Flow<List<BookEntity>>
    @Query("SELECT * FROM books WHERE id=:id") suspend fun get(id: String): BookEntity?
    @Query("UPDATE books SET posChapter=:chapter, posBlock=:block WHERE id=:id")
    suspend fun updatePosition(id: String, chapter: Int, block: Int)
    @Query("UPDATE books SET coverage=:coverage WHERE id=:id")
    suspend fun updateCoverage(id: String, coverage: Float)
    @Upsert suspend fun upsert(book: BookEntity)
    @Delete suspend fun delete(book: BookEntity)

    // backup / restore (metadata only — book files are device-local)
    @Query("SELECT * FROM books") suspend fun all(): List<BookEntity>
    @Upsert suspend fun upsertAll(books: List<BookEntity>)
    @Query("DELETE FROM books") suspend fun clear()
}

@Database(
    entities = [CardEntity::class, ReviewLogEntity::class, KnownCharEntity::class, BookEntity::class],
    version = 1, exportSchema = false,
)
abstract class UserDatabase : RoomDatabase() {
    abstract fun cardDao(): CardDao
    abstract fun reviewLogDao(): ReviewLogDao
    abstract fun knownDao(): KnownDao
    abstract fun bookDao(): BookDao

    companion object {
        @Volatile private var INSTANCE: UserDatabase? = null
        fun get(context: Context): UserDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext, UserDatabase::class.java, "user.db"
            ).build().also { INSTANCE = it }
        }
    }
}
