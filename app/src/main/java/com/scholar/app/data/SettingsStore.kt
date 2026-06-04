package com.scholar.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Tiny SharedPreferences wrapper for user settings (theme, onboarding, retention, backup). */
class SettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("scholar_settings", Context.MODE_PRIVATE)

    // Emits whenever gated-track progress that feeds 修为 changes, so observers (the rank, the
    // breakthrough watcher) can recompute live even though SharedPreferences isn't itself reactive.
    private val _studyTick = MutableStateFlow(0)
    val studyTick: StateFlow<Int> = _studyTick.asStateFlow()
    private fun bumpStudy() { _studyTick.value++ }

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

    // ── Character cultivation track (gated, grouped HSK study) ─────────────
    // Progress is scoped per HSK level. Groups are fixed-size, so the keys stay stable.
    /** Highest group index unlocked for [level] (0 = only the first group is open). */
    fun hskUnlocked(level: String): Int = prefs.getInt("hsk_unlocked_$level", 0)
    fun setHskUnlocked(level: String, v: Int) {
        prefs.edit().putInt("hsk_unlocked_$level", v.coerceAtLeast(0)).apply()
        bumpStudy()
    }

    /** Best trial score (0–100) recorded for a group within [level]. */
    fun hskBestScore(level: String, group: Int): Int = prefs.getInt("hsk_best_${level}_$group", 0)

    /** Record a group trial score, keeping the best seen. Returns the stored (best) score. */
    fun setHskBestScore(level: String, group: Int, score: Int): Int {
        val best = maxOf(score, hskBestScore(level, group))
        prefs.edit().putInt("hsk_best_${level}_$group", best).apply()
        return best
    }

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

    /** Highest batch index the user has unlocked (0 = only the first batch is open).
        Since a batch only unlocks by passing the one before it, this equals the number of
        consecutively-passed batches — i.e. the count of radical batches cleared. */
    var radicalUnlocked: Int
        get() = prefs.getInt("radical_unlocked", 0)
        set(v) { prefs.edit().putInt("radical_unlocked", v.coerceAtLeast(0)).apply(); bumpStudy() }

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
        bumpStudy()
    }

    // ── 修为 contributions from the gated tracks (read by Cultivation.rankFor) ──
    /** Radicals cleared on the gated track: passed batches × batch size, capped at the Kangxi total. */
    fun radicalsCultivated(): Int = (radicalUnlocked * radicalBatchSize).coerceAtMost(RADICAL_TOTAL)

    /** Words sealed via the character track across all HSK levels (passed groups × group size). */
    fun trackWordsCultivated(): Int = HSK_LEVELS.sumOf { hskUnlocked(it) } * STUDY_GROUP

    // ── Backup of the gated-track progress (dynamic, per-batch/level/group keys) ──
    /** All gated-track progress as a flat key→Int map, so backup/restore preserves it. Covers the
        radical unlock frontier + per-batch scores and the HSK per-level unlock frontiers + per-group
        scores. The fixed scalar settings (batch size, retention, …) are backed up separately. */
    fun exportTrackProgress(): Map<String, Int> =
        prefs.all.filter { (k, v) ->
            v is Int && (k.startsWith("radical_best_") || k == "radical_unlocked" ||
                k.startsWith("hsk_unlocked_") || k.startsWith("hsk_best_"))
        }.mapValues { it.value as Int }

    /** Restore the track-progress keys produced by [exportTrackProgress]. */
    fun importTrackProgress(progress: Map<String, Int>) {
        val e = prefs.edit()
        progress.forEach { (k, v) -> e.putInt(k, v) }
        e.apply()
        bumpStudy()
    }

    /** Wall-clock of the user's last gated-track study (a trial completed) — feeds the daily reminder
        so "you haven't studied today" counts cultivation trials, not just reviews. */
    var lastStudyMillis: Long
        get() = prefs.getLong("last_study_millis", 0L)
        set(v) = prefs.edit().putLong("last_study_millis", v).apply()
    fun markStudiedNow() { lastStudyMillis = System.currentTimeMillis() }

    // ── Breakthrough overlays: the last cultivation state the user was actually *shown* ──
    /** -1 until the first observation, so an upgrade/first-launch records silently (no popup). */
    var lastSeenRealm: Int
        get() = prefs.getInt("cult_seen_realm", -1)
        set(v) = prefs.edit().putInt("cult_seen_realm", v).apply()
    var lastSeenStage: String
        get() = prefs.getString("cult_seen_stage", "") ?: ""
        set(v) = prefs.edit().putString("cult_seen_stage", v).apply()
    var lastSeenScore: Int
        get() = prefs.getInt("cult_seen_score", 0)
        set(v) = prefs.edit().putInt("cult_seen_score", v).apply()
    fun recordCultivation(realm: Int, stage: String, score: Int) =
        prefs.edit().putInt("cult_seen_realm", realm).putString("cult_seen_stage", stage)
            .putInt("cult_seen_score", score).apply()

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

    companion object {
        /** Words per group on the character cultivation track (shared with the UI). */
        const val STUDY_GROUP = 20
        /** Kangxi radical count — the cap on radicals cultivated. */
        const val RADICAL_TOTAL = 214
        /** HSK level keys, summed when totalling track-sealed words. */
        val HSK_LEVELS = listOf("new-1", "new-2", "new-3", "new-4", "new-5", "new-6", "new-7")
    }
}
