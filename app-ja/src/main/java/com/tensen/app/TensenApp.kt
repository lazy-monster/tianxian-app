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
            // Option B: both apps ship from one repo. Tensen polls the same releases as Tianxian and
            // picks its own build by the "tensen-…apk" asset name (see Updater.appApk). No release
            // carries a tensen APK until Phase 4, so the updater simply finds nothing until then.
            updateRepo = "lazy-monster/tianxian-app",
            userAgent = "tensen-app",
            backupId = "tensen",
            acceptedBackupIds = setOf("tensen"),
        )
    }
}
