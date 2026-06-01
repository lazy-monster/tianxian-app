package com.scholar.app.data

import android.content.Context

/** Tiny SharedPreferences wrapper for user settings (theme, onboarding, retention, backup). */
class SettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("scholar_settings", Context.MODE_PRIVATE)

    var darkTheme: Boolean
        get() = prefs.getBoolean("dark", true)
        set(v) = prefs.edit().putBoolean("dark", v).apply()

    var onboarded: Boolean
        get() = prefs.getBoolean("onboarded", false)
        set(v) = prefs.edit().putBoolean("onboarded", v).apply()

    /** FSRS desired retention. Higher = shorter intervals / more reviews. */
    var desiredRetention: Float
        get() = prefs.getFloat("desired_retention", 0.92f)
        set(v) = prefs.edit().putFloat("desired_retention", v.coerceIn(0.80f, 0.97f)).apply()

    /** Persisted SAF tree-uri of the chosen auto-backup folder, or null if unset. */
    var backupTreeUri: String?
        get() = prefs.getString("backup_tree_uri", null)
        set(v) = prefs.edit().putString("backup_tree_uri", v).apply()

    /** When true, a fresh backup is written to [backupTreeUri] on launch once the interval lapses. */
    var autoBackup: Boolean
        get() = prefs.getBoolean("auto_backup", false)
        set(v) = prefs.edit().putBoolean("auto_backup", v).apply()

    /** Minimum gap between automatic backups. */
    var backupIntervalHours: Int
        get() = prefs.getInt("backup_interval_hours", 24)
        set(v) = prefs.edit().putInt("backup_interval_hours", v).apply()

    var lastBackupMillis: Long
        get() = prefs.getLong("last_backup_millis", 0L)
        set(v) = prefs.edit().putLong("last_backup_millis", v).apply()
}
