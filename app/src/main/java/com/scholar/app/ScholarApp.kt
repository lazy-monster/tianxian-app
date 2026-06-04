package com.scholar.app

import android.app.Application
import com.scholar.app.di.AppGraph
import com.scholar.app.notify.Reminders

class ScholarApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        Reminders.ensureChannel(this)
        if (graph.settings.remindersEnabled) Reminders.schedule(this)
    }

    companion object {
        fun graph(app: Application) = (app as ScholarApp).graph
    }
}
