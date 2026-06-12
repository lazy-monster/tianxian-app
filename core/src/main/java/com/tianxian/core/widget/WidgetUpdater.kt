package com.tianxian.core.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Pushes fresh content to any placed widgets — call after a review session and on app launch so
 *  the status counts and rotating character stay current between the system's periodic updates. */
object WidgetUpdater {
    fun refresh(context: Context) {
        val app = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            runCatching { StatusWidget().updateAll(app) }
        }
    }
}
