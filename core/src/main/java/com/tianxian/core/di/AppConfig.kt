package com.tianxian.core.di

/** Which language an app variant teaches. Reserved for the language-specific layer (Phase 4:
 *  dictionary schema, glossing, segmentation); shared code branches on this where unavoidable. */
enum class Lang { ZH, JA }

/**
 * Per-app identity injected by each app module's Application into the shared [AppGraph].
 * Everything that differs between the Chinese app (Tianxian) and the Japanese app (Tensen) —
 * branding, the in-app updater's release channel, and backup tagging — is funnelled through
 * here so the shared `:core` code carries no hardcoded app name or repo.
 *
 * The launcher label (天仙) is a string resource per app module; [appName] is the
 * English-appropriate romanization used in body copy ("Tianxian" / "Tensen").
 */
data class AppConfig(
    val language: Lang,
    /** English-appropriate name shown in prose: "Tianxian" / "Tensen". */
    val appName: String,
    /** Lowercase slug for generated file names: "tianxian" / "tensen". */
    val slug: String,
    /** GitHub "owner/repo" the in-app updater checks for new releases. */
    val updateRepo: String,
    /** User-Agent sent to the GitHub API (it rejects requests with none). */
    val userAgent: String,
    /** Identifier written into exported backups so a file announces which app produced it. */
    val backupId: String,
    /** Backup ids accepted on import. The Chinese app also accepts the legacy "scholar" id so
     *  backups made before the rebrand still restore. */
    val acceptedBackupIds: Set<String>,
)
