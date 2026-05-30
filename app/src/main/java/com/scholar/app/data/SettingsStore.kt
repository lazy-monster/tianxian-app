package com.scholar.app.data

import android.content.Context

/** Tiny SharedPreferences wrapper for user settings (theme, onboarding, retention). */
class SettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("scholar_settings", Context.MODE_PRIVATE)

    var darkTheme: Boolean
        get() = prefs.getBoolean("dark", true)
        set(v) = prefs.edit().putBoolean("dark", v).apply()

    var onboarded: Boolean
        get() = prefs.getBoolean("onboarded", false)
        set(v) = prefs.edit().putBoolean("onboarded", v).apply()
}
