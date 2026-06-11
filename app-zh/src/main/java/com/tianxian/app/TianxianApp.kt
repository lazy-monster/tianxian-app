package com.tianxian.app

import android.app.Application
import com.tianxian.core.di.AppConfig
import com.tianxian.core.di.AppGraph
import com.tianxian.core.di.GraphHolder
import com.tianxian.core.di.Lang
import com.tianxian.core.notify.Reminders

/** Tianxian (天仙) — the Chinese reading app. Builds the shared graph with the Chinese identity. */
class TianxianApp : Application(), GraphHolder {
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
            language = Lang.ZH,
            appName = "Tianxian",
            slug = "tianxian",
            updateRepo = "lazy-monster/tianxian-app",   // in-app updater polls this repo's latest release
            userAgent = "tianxian-app",
            backupId = "tianxian",
            // Accept the legacy "scholar" id so backups made before the rebrand still restore.
            acceptedBackupIds = setOf("tianxian", "scholar"),
        )
    }
}
