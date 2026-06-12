package com.tianxian.core.di

import android.content.Context
import com.tianxian.core.audio.SoundFx
import com.tianxian.core.audio.Speaker
import com.tianxian.core.data.SettingsStore
import com.tianxian.core.data.backup.BackupManager
import com.tianxian.core.data.content.ContentStore
import com.tianxian.core.data.repo.BookRepository
import com.tianxian.core.data.repo.CardRepository
import com.tianxian.core.data.repo.DictionaryRepository
import com.tianxian.core.data.repo.KnownRepository
import com.tianxian.core.data.segment.MaxMatchSegmenter
import com.tianxian.core.data.user.UserDatabase
import com.tianxian.core.reader.ingest.Ingestor
import com.tianxian.core.update.UpdateController
import com.tianxian.core.update.Updater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Hand-rolled dependency graph (no Hilt) — fewer moving parts, so the project
 * compiles cleanly. Created once in the Application and read by ViewModels via the
 * Application context.
 */
class AppGraph(context: Context, val config: AppConfig) {
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
    val backup by lazy { BackupManager(app, userDb, settings, config) }

    /** Loaded lazily on a background thread by callers; ~107k headwords. */
    @Volatile private var _segmenter: MaxMatchSegmenter? = null
    fun segmenter(): MaxMatchSegmenter =
        _segmenter ?: synchronized(this) {
            _segmenter ?: MaxMatchSegmenter(content.loadVocabulary()).also { _segmenter = it }
        }

    val speaker by lazy { Speaker(app) }

    /** Checks GitHub Releases and installs the APK — in-app self-update for the sideloaded build. */
    val updater by lazy { Updater(app, config) }

    /** App-scoped update flow state, so a download survives the user leaving the Settings screen. */
    val updateController by lazy { UpdateController(updater, appScope, config.appName) }

    /** Short interactive trial cues, gated by the user's sound-effects setting. */
    val soundFx by lazy { SoundFx { settings.soundEffectsEnabled } }

    /** Application-lifetime scope for fire-and-forget writes that must outlive a single screen —
        e.g. sealing a freshly-learned batch into the review deck even if the user navigates away
        before the inserts finish. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
