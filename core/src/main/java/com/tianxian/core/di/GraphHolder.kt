package com.tianxian.core.di

/**
 * Implemented by each app module's [android.app.Application] so shared code (e.g. [com.tianxian.core.MainActivity])
 * can reach the [AppGraph] without knowing the concrete Application subclass. The Chinese app
 * (TianxianApp) and the Japanese app (TensenApp) each build their own graph from an [AppConfig].
 */
interface GraphHolder {
    val graph: AppGraph
}
