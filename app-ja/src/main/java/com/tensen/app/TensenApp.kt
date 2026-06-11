package com.tensen.app

import android.app.Application
import com.tianxian.core.di.AppConfig
import com.tianxian.core.di.AppGraph
import com.tianxian.core.di.GraphHolder
import com.tianxian.core.di.Lang
import com.tianxian.core.notify.Reminders

/** Tensen (天仙) — the Japanese reading app. Same shared graph as Tianxian, Japanese identity.
 *  The Japanese content DB and language-specific code arrive in Phase 4; until then the bundled
 *  content.db is an empty stub. */
class TensenApp : Application(), GraphHolder {
    override lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this, CONFIG)
        Reminders.ensureChannel(this)
        if (graph.settings.remindersEnabled) Reminders.schedule(this)
    }

    private companion object {
        val CONFIG = AppConfig(
            language = Lang.JA,
            appName = "Tensen",
            slug = "tensen",
            updateRepo = "lazy-monster/tensen-app",   // create this repo when Tensen is ready to ship (Phase 4)
            userAgent = "tensen-app",
            backupId = "tensen",
            acceptedBackupIds = setOf("tensen"),
        )
    }
}
