package com.scholar.app

import android.app.Application
import com.scholar.app.di.AppGraph

class ScholarApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }

    companion object {
        fun graph(app: Application) = (app as ScholarApp).graph
    }
}
