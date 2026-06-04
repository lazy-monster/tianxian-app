package com.scholar.app.data.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.scholar.app.data.SettingsStore
import com.scholar.app.data.user.BookEntity
import com.scholar.app.data.user.CardEntity
import com.scholar.app.data.user.KnownCharEntity
import com.scholar.app.data.user.ReviewLogEntity
import com.scholar.app.data.user.UserDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Serialises all on-device progress (cards, review history, known characters, library
 * metadata and key preferences) to a single self-describing JSON document, and restores it.
 *
 * The format is plain JSON on purpose: it is human-readable, survives app reinstalls, and can
 * be moved between devices. Book *files* are not included — only their metadata — because the
 * imported ebooks live in app storage; re-importing the book re-links it by id.
 */
class BackupManager(
    private val context: Context,
    private val db: UserDatabase,
    private val settings: SettingsStore,
) {
    data class Summary(val cards: Int, val reviews: Int, val known: Int, val books: Int)

    /** Build the full backup document as a JSON string. */
    suspend fun exportJson(): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("schema", SCHEMA)
        root.put("app", "scholar")
        root.put("exportedAt", System.currentTimeMillis())

        root.put("settings", JSONObject()
            .put("darkTheme", settings.darkTheme)
            .put("desiredRetention", settings.desiredRetention.toDouble())
            .put("hskBatchSize", settings.hskBatchSize)
            .put("readerFontKey", settings.readerFontKey)
            .put("readerFontSizeSp", settings.readerFontSizeSp)
            .put("readerLineHeight", settings.readerLineHeight.toDouble())
            .put("readerThemeKey", settings.readerThemeKey)
            .put("readerTtsRate", settings.readerTtsRate.toDouble())
            .put("radicalBatchSize", settings.radicalBatchSize)
            .put("radicalUnlocked", settings.radicalUnlocked)
            .put("remindersEnabled", settings.remindersEnabled))

        root.put("cards", JSONArray().apply {
            db.cardDao().all().forEach { c ->
                put(JSONObject()
                    .put("id", c.id).put("type", c.type).put("frontRef", c.frontRef).put("backRef", c.backRef)
                    .put("stability", c.stability).put("difficulty", c.difficulty)
                    .put("dueEpochDay", c.dueEpochDay).put("dueEpochMillis", c.dueEpochMillis)
                    .put("lapses", c.lapses).put("reps", c.reps).put("suspended", c.suspended)
                    .put("source", c.source ?: JSONObject.NULL))
            }
        })
        root.put("reviewLog", JSONArray().apply {
            db.reviewLogDao().all().forEach { r ->
                put(JSONObject()
                    .put("id", r.id).put("cardId", r.cardId).put("rating", r.rating)
                    .put("reviewedAtMillis", r.reviewedAtMillis).put("lastStability", r.lastStability)
                    .put("lastDifficulty", r.lastDifficulty).put("elapsedDays", r.elapsedDays))
            }
        })
        root.put("knownChars", JSONArray().apply {
            db.knownDao().all().forEach { k ->
                put(JSONObject()
                    .put("char", k.char).put("strength", k.strength)
                    .put("firstSeenMillis", k.firstSeenMillis).put("lastSeenMillis", k.lastSeenMillis))
            }
        })
        root.put("books", JSONArray().apply {
            db.bookDao().all().forEach { b ->
                put(JSONObject()
                    .put("id", b.id).put("title", b.title).put("author", b.author ?: JSONObject.NULL)
                    .put("format", b.format).put("addedAtMillis", b.addedAtMillis)
                    .put("posChapter", b.posChapter).put("posBlock", b.posBlock)
                    .put("coverage", b.coverage.toDouble()).put("cachePath", b.cachePath))
            }
        })
        root.toString()
    }

    /** Restore a backup, replacing current progress. Returns a summary of what was loaded. */
    suspend fun importJson(json: String): Summary = withContext(Dispatchers.IO) {
        val root = JSONObject(json)
        require(root.optString("app") == "scholar") { "Not a Scholar backup file." }

        root.optJSONObject("settings")?.let { s ->
            if (s.has("darkTheme")) settings.darkTheme = s.getBoolean("darkTheme")
            if (s.has("desiredRetention")) settings.desiredRetention = s.getDouble("desiredRetention").toFloat()
            if (s.has("hskBatchSize")) settings.hskBatchSize = s.getInt("hskBatchSize")
            if (s.has("readerFontKey")) settings.readerFontKey = s.getString("readerFontKey")
            if (s.has("readerFontSizeSp")) settings.readerFontSizeSp = s.getInt("readerFontSizeSp")
            if (s.has("readerLineHeight")) settings.readerLineHeight = s.getDouble("readerLineHeight").toFloat()
            if (s.has("readerThemeKey")) settings.readerThemeKey = s.getString("readerThemeKey")
            if (s.has("readerTtsRate")) settings.readerTtsRate = s.getDouble("readerTtsRate").toFloat()
            // batch size first — its setter resets radical progress when it changes
            if (s.has("radicalBatchSize")) settings.radicalBatchSize = s.getInt("radicalBatchSize")
            if (s.has("radicalUnlocked")) settings.radicalUnlocked = s.getInt("radicalUnlocked")
            if (s.has("remindersEnabled")) settings.remindersEnabled = s.getBoolean("remindersEnabled")
        }

        val cards = root.optJSONArray("cards")?.mapObjects { o ->
            CardEntity(
                id = o.getLong("id"), type = o.getString("type"),
                frontRef = o.getString("frontRef"), backRef = o.getString("backRef"),
                stability = o.optDouble("stability", 0.0), difficulty = o.optDouble("difficulty", 0.0),
                dueEpochDay = o.getLong("dueEpochDay"), dueEpochMillis = o.getLong("dueEpochMillis"),
                lapses = o.optInt("lapses", 0), reps = o.optInt("reps", 0),
                suspended = o.optBoolean("suspended", false),
                source = if (o.isNull("source")) null else o.optString("source", null),
            )
        }.orEmpty()
        val logs = root.optJSONArray("reviewLog")?.mapObjects { o ->
            ReviewLogEntity(
                id = o.getLong("id"), cardId = o.getLong("cardId"), rating = o.getInt("rating"),
                reviewedAtMillis = o.getLong("reviewedAtMillis"),
                lastStability = o.optDouble("lastStability", 0.0),
                lastDifficulty = o.optDouble("lastDifficulty", 0.0),
                elapsedDays = o.optDouble("elapsedDays", 0.0),
            )
        }.orEmpty()
        val known = root.optJSONArray("knownChars")?.mapObjects { o ->
            KnownCharEntity(
                char = o.getString("char"), strength = o.optDouble("strength", 0.0),
                firstSeenMillis = o.optLong("firstSeenMillis", 0L),
                lastSeenMillis = o.optLong("lastSeenMillis", 0L),
            )
        }.orEmpty()
        val books = root.optJSONArray("books")?.mapObjects { o ->
            BookEntity(
                id = o.getString("id"), title = o.getString("title"),
                author = if (o.isNull("author")) null else o.optString("author", null),
                format = o.getString("format"), addedAtMillis = o.getLong("addedAtMillis"),
                posChapter = o.optInt("posChapter", 0), posBlock = o.optInt("posBlock", 0),
                coverage = o.optDouble("coverage", 0.0).toFloat(),
                cachePath = o.optString("cachePath", ""),
            )
        }.orEmpty()

        db.cardDao().clear(); db.cardDao().insertAll(cards)
        db.reviewLogDao().clear(); db.reviewLogDao().insertAll(logs)
        db.knownDao().clear(); db.knownDao().upsertAll(known)
        db.bookDao().upsertAll(books)   // keep any books already imported on this device

        Summary(cards.size, logs.size, known.size, books.size)
    }

    /**
     * Wipe on-device progress. [includeBooks] also removes the imported library (database rows
     * and their cached book files); when false, books and their reading positions are kept.
     * Irreversible — callers must confirm with the user first.
     */
    suspend fun resetProgress(includeBooks: Boolean): Unit = withContext(Dispatchers.IO) {
        db.cardDao().clear()
        db.reviewLogDao().clear()
        db.knownDao().clear()
        if (includeBooks) {
            db.bookDao().all().forEach {
                runCatching { java.io.File(it.cachePath).delete() }
                com.scholar.app.reader.ingest.ImageStore.delete(context, it.id)
            }
            db.bookDao().clear()
        }
        settings.lastBackupMillis = 0L
    }

    /** Write the backup to a user-picked file (ACTION_CREATE_DOCUMENT result uri). */
    suspend fun writeTo(uri: Uri): Summary = withContext(Dispatchers.IO) {
        val json = exportJson()
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray()) }
            ?: error("Could not open the chosen file for writing.")
        val r = JSONObject(json)
        Summary(r.getJSONArray("cards").length(), r.getJSONArray("reviewLog").length(),
            r.getJSONArray("knownChars").length(), r.getJSONArray("books").length())
    }

    /** Read and restore a backup from a user-picked file (ACTION_OPEN_DOCUMENT result uri). */
    suspend fun restoreFrom(uri: Uri): Summary = withContext(Dispatchers.IO) {
        val json = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            ?: error("Could not open the chosen file for reading.")
        importJson(json)
    }

    /**
     * If auto-backup is on and the interval has lapsed, write a timestamped backup into the
     * chosen folder. Best-effort: returns true on success, false (silently) if it couldn't.
     */
    suspend fun maybeAutoBackup(): Boolean = withContext(Dispatchers.IO) {
        if (!settings.autoBackup) return@withContext false
        val treeUriStr = settings.backupTreeUri ?: return@withContext false
        val elapsed = System.currentTimeMillis() - settings.lastBackupMillis
        if (elapsed < settings.backupIntervalHours * 3_600_000L) return@withContext false
        runCatching { writeToTree(Uri.parse(treeUriStr)) }.isSuccess
    }

    /** Create a new timestamped backup document inside a persisted tree uri and fill it. */
    suspend fun writeToTree(treeUri: Uri): Summary = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val dirUri = DocumentsContract.buildDocumentUriUsingTree(
            treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        val stamp = SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date())
        val fileUri = DocumentsContract.createDocument(
            resolver, dirUri, "application/json", "scholar-backup-$stamp.json")
            ?: error("Could not create a backup file in that folder.")
        val summary = writeTo(fileUri)
        settings.lastBackupMillis = System.currentTimeMillis()
        summary
    }

    private inline fun <T> JSONArray.mapObjects(f: (JSONObject) -> T): List<T> =
        (0 until length()).map { f(getJSONObject(it)) }

    companion object { const val SCHEMA = 1 }
}
