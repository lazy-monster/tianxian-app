package com.scholar.app.di

import android.content.Context
import com.scholar.app.audio.Speaker
import com.scholar.app.data.SettingsStore
import com.scholar.app.data.backup.BackupManager
import com.scholar.app.data.content.ContentStore
import com.scholar.app.data.repo.BookRepository
import com.scholar.app.data.repo.CardRepository
import com.scholar.app.data.repo.DictionaryRepository
import com.scholar.app.data.repo.KnownRepository
import com.scholar.app.data.segment.MaxMatchSegmenter
import com.scholar.app.data.user.UserDatabase
import com.scholar.app.reader.ingest.Ingestor

/**
 * Hand-rolled dependency graph (no Hilt) — fewer moving parts, so the project
 * compiles cleanly. Created once in ScholarApp and read by ViewModels via the
 * Application context.
 */
class AppGraph(context: Context) {
    private val app = context.applicationContext

    val content: ContentStore by lazy { ContentStore.get(app) }
    private val userDb by lazy { UserDatabase.get(app) }

    val settings by lazy { SettingsStore(app) }
    val dictionary by lazy { DictionaryRepository(content) }
    val known by lazy { KnownRepository(userDb.knownDao()) }
    val cards by lazy {
        CardRepository(userDb.cardDao(), userDb.reviewLogDao(), known,
            retention = { settings.desiredRetention.toDouble() })
    }
    val books by lazy { BookRepository(app, userDb.bookDao(), Ingestor(app), known) }
    val backup by lazy { BackupManager(app, userDb, settings) }

    /** Loaded lazily on a background thread by callers; ~107k headwords. */
    @Volatile private var _segmenter: MaxMatchSegmenter? = null
    fun segmenter(): MaxMatchSegmenter =
        _segmenter ?: synchronized(this) {
            _segmenter ?: MaxMatchSegmenter(content.loadVocabulary()).also { _segmenter = it }
        }

    val speaker by lazy { Speaker(app) }
}
