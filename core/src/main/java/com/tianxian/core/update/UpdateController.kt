package com.tianxian.core.update

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * App-scoped state holder for the in-app updater so a check or download **survives leaving the
 * Settings screen.** Previously every bit of update state lived in the composable and the download
 * ran on the screen's `rememberCoroutineScope()` — navigating to any other screen disposed the
 * composition, which cancelled the download and reset the UI back to "Update", so the next visit
 * restarted the transfer from zero and burned the user's data.
 *
 * Here the work runs on [scope] (the application scope, which outlives any screen) and progress is
 * published through [phase] / [message]; the screen only observes them. An in-flight download is
 * never restarted while it's still running.
 */
class UpdateController(
    private val updater: Updater,
    private val scope: CoroutineScope,
    private val appName: String,
) {
    /** Installed (versionName, versionCode), read from the package manager. */
    val installed: Pair<String, Long> get() = updater.current()

    /** Where the update flow currently is. The screen renders straight off this. */
    sealed interface Phase {
        data object Idle : Phase
        data object Checking : Phase
        data class Available(val update: Update) : Phase
        data class Downloading(val update: Update, val percent: Int) : Phase
        data object Installing : Phase
    }

    private val _phase = MutableStateFlow<Phase>(Phase.Idle)
    val phase: StateFlow<Phase> = _phase.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var downloadJob: Job? = null

    /** Ask GitHub whether a newer release exists. No-op while already checking or downloading. */
    fun check() {
        when (_phase.value) {
            is Phase.Checking, is Phase.Downloading, is Phase.Installing -> return
            else -> {}
        }
        _phase.value = Phase.Checking
        _message.value = null
        scope.launch {
            runCatching { updater.check() }
                .onSuccess { found ->
                    if (found != null) _phase.value = Phase.Available(found)
                    else {
                        _phase.value = Phase.Idle
                        _message.value = "You're on the latest version (${installed.first})."
                    }
                }
                .onFailure {
                    _phase.value = Phase.Idle
                    _message.value = "Couldn't check for updates: ${it.message}"
                }
        }
    }

    /** Download [update] on the app scope and hand it to the installer. Idempotent: a call while the
        same download is already running is ignored, so re-entering Settings never restarts it. */
    fun download(update: Update) {
        if (downloadJob?.isActive == true) return
        if (!updater.canInstall()) {
            updater.requestInstallPermission()
            _message.value = "Allow $appName to install apps, then tap Update again."
            return
        }
        _message.value = null
        _phase.value = Phase.Downloading(update, 0)
        downloadJob = scope.launch {
            runCatching {
                val apk = updater.download(update) { pct -> _phase.value = Phase.Downloading(update, pct) }
                _phase.value = Phase.Installing
                updater.install(apk)
            }.onSuccess {
                _message.value = "Opening the installer…"
            }.onFailure {
                _phase.value = Phase.Available(update)   // let the user retry
                _message.value = "Download failed: ${it.message}"
            }
        }
    }
}
