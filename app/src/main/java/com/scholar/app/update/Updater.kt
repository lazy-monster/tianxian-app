package com.scholar.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** A published GitHub release that is newer than the installed build. */
data class Update(val versionName: String, val notes: String, val apkUrl: String, val apkSize: Long)

/**
 * Self-update for the sideloaded APK: checks the GitHub Releases API, downloads the signed APK,
 * and hands it to the system package installer. No third-party libraries — HttpURLConnection +
 * org.json. Networking only ever runs when the user taps "check", so the app stays offline-first.
 *
 * Updating in place works only because every release is signed with the same key (see
 * release.yml); an APK signed with a different key is rejected by Android as a signature mismatch.
 */
class Updater(private val context: Context) {

    /** Installed (versionName, versionCode), read from the package — no BuildConfig needed. */
    fun current(): Pair<String, Long> {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode
                   else @Suppress("DEPRECATION") info.versionCode.toLong()
        return (info.versionName ?: "") to code
    }

    /** The latest published release, or null if there's none newer than what's installed. */
    suspend fun check(): Update? = withContext(Dispatchers.IO) {
        val obj = JSONObject(httpGet("https://api.github.com/repos/$REPO/releases/latest"))
        val tag = obj.optString("tag_name").removePrefix("v")
        if (tag.isBlank() || !isNewer(tag, current().first)) return@withContext null
        val assets = obj.optJSONArray("assets") ?: return@withContext null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                val url = a.optString("browser_download_url")
                if (url.isNotBlank()) return@withContext Update(tag, obj.optString("body").trim(), url, a.optLong("size"))
            }
        }
        null
    }

    /** Download the APK into the cache, reporting 0..100 progress. Returns the saved file. */
    suspend fun download(update: Update, onProgress: (Int) -> Unit = {}): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val out = File(dir, "scholar-${update.versionName}.apk")
        val conn = (URL(update.apkUrl).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15_000; readTimeout = 30_000
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            val total = if (update.apkSize > 0) update.apkSize else conn.contentLengthLong
            conn.inputStream.use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024); var done = 0L; var n: Int
                    while (input.read(buf).also { n = it } >= 0) {
                        output.write(buf, 0, n); done += n
                        if (total > 0) onProgress(((done * 100) / total).toInt().coerceIn(0, 100))
                    }
                }
            }
        } finally { conn.disconnect() }
        out
    }

    /** Whether this app may install APKs — Android O+ gates it behind a per-app user setting. */
    fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    /** Open the system screen where the user grants "install unknown apps" for Scholar. */
    fun requestInstallPermission() {
        if (Build.VERSION.SDK_INT >= 26) context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Hand the downloaded APK to the system installer (shows the standard install screen). */
    fun install(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    /** Compare dotted versions numerically: "0.10.5" > "0.10.4" > "0.9.0". Suffixes (-debug) ignored. */
    private fun isNewer(remote: String, local: String): Boolean {
        val r = parts(remote); val l = parts(local)
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }; val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }
    private fun parts(v: String): List<Int> =
        v.trim().substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }

    private fun httpGet(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 15_000
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        return try { conn.inputStream.bufferedReader().use { it.readText() } } finally { conn.disconnect() }
    }

    private companion object {
        const val REPO = "lazy-monster/scholar-app"
        const val USER_AGENT = "scholar-app"          // GitHub's API rejects requests with no UA
    }
}
