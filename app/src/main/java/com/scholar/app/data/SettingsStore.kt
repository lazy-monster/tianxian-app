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

    /** How many HSK words the "add next batch" button mines into the review deck at once. */
    var hskBatchSize: Int
        get() = prefs.getInt("hsk_batch_size", 40)
        set(v) = prefs.edit().putInt("hsk_batch_size", v.coerceIn(5, 100)).apply()

    // ── Radical cultivation track ──────────────────────────────────────────
    /** Radicals per batch on the gated track. Changing it re-forms batches, so progress resets. */
    var radicalBatchSize: Int
        get() = prefs.getInt("radical_batch_size", 20)
        set(v) {
            val c = v.coerceIn(5, 40)
            if (c != prefs.getInt("radical_batch_size", 20)) {
                resetRadicalProgress()
                prefs.edit().putInt("radical_batch_size", c).apply()
            }
        }

    /** Highest batch index the user has unlocked (0 = only the first batch is open). */
    var radicalUnlocked: Int
        get() = prefs.getInt("radical_unlocked", 0)
        set(v) = prefs.edit().putInt("radical_unlocked", v.coerceAtLeast(0)).apply()

    /** Best trial score (0–100) recorded for a batch. */
    fun radicalBestScore(batch: Int): Int = prefs.getInt("radical_best_$batch", 0)

    /** Record a trial score, keeping the best seen. Returns the stored (best) score. */
    fun setRadicalBestScore(batch: Int, score: Int): Int {
        val best = maxOf(score, radicalBestScore(batch))
        prefs.edit().putInt("radical_best_$batch", best).apply()
        return best
    }

    private fun resetRadicalProgress() {
        val e = prefs.edit()
        prefs.all.keys.filter { it.startsWith("radical_best_") }.forEach { e.remove(it) }
        e.putInt("radical_unlocked", 0).apply()
    }

    /** Whether daily review reminders are scheduled. */
    var remindersEnabled: Boolean
        get() = prefs.getBoolean("reminders_enabled", true)
        set(v) = prefs.edit().putBoolean("reminders_enabled", v).apply()

    // ── Reader preferences (global defaults applied in the reader) ──────────
    /** Reader font family key: serif | sans | kai | mono. */
    var readerFontKey: String
        get() = prefs.getString("reader_font", "serif") ?: "serif"
        set(v) = prefs.edit().putString("reader_font", v).apply()

    var readerFontSizeSp: Int
        get() = prefs.getInt("reader_font_size", 21)
        set(v) = prefs.edit().putInt("reader_font_size", v.coerceIn(16, 34)).apply()

    /** Line-height as a multiple of the font size. */
    var readerLineHeight: Float
        get() = prefs.getFloat("reader_line_height", 1.9f)
        set(v) = prefs.edit().putFloat("reader_line_height", v.coerceIn(1.4f, 2.4f)).apply()

    /** Reader colour theme key: follow (app theme) | ink | paper | sepia | oled. */
    var readerThemeKey: String
        get() = prefs.getString("reader_theme", "follow") ?: "follow"
        set(v) = prefs.edit().putString("reader_theme", v).apply()

    /** Read-aloud speech rate (1.0 = normal). */
    var readerTtsRate: Float
        get() = prefs.getFloat("reader_tts_rate", 1.0f)
        set(v) = prefs.edit().putFloat("reader_tts_rate", v.coerceIn(0.5f, 1.6f)).apply()

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
