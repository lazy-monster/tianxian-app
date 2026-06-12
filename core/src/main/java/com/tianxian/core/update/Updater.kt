package com.tianxian.core.update

import android.content.Context
import com.tianxian.core.di.AppConfig
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
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
class Updater(private val context: Context, private val config: AppConfig) {

    /** Installed (versionName, versionCode), read from the package — no BuildConfig needed. */
    fun current(): Pair<String, Long> {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode
                   else @Suppress("DEPRECATION") info.versionCode.toLong()
        return (info.versionName ?: "") to code
    }

    /** The latest published release for THIS app, or null if none is newer than what's installed.
     *
     *  Lists releases rather than asking for `/releases/latest`: one repo may host more than one app
     *  (Tianxian + Tensen, see AppConfig.updateRepo), so the newest release overall can belong to the
     *  other app. This app's release is the newest one carrying an APK named "${slug}-…apk". */
    suspend fun check(): Update? = withContext(Dispatchers.IO) {
        val arr = JSONArray(httpGet("https://api.github.com/repos/${config.updateRepo}/releases?per_page=30"))
        var best: Update? = null
        for (i in 0 until arr.length()) {
            val rel = arr.getJSONObject(i)
            if (rel.optBoolean("draft") || rel.optBoolean("prerelease")) continue
            // "v0.11.2" / "zh-v0.11.2" / "ja-v0.1.0" → "0.11.2" / "0.11.2" / "0.1.0"
            val version = rel.optString("tag_name").substringAfterLast('-').removePrefix("v")
            if (version.isBlank()) continue
            val apk = appApk(rel.optJSONArray("assets")) ?: continue
            val url = apk.optString("browser_download_url")
            if (url.isBlank()) continue
            val cand = Update(version, rel.optString("body").trim(), url, apk.optLong("size"))
            if (best == null || isNewer(cand.versionName, best!!.versionName)) best = cand
        }
        best?.takeIf { isNewer(it.versionName, current().first) }
    }

    /** This app's APK among a release's assets — named "${slug}-…apk" by the release workflow. A
        per-app prefix (never a bare ".apk") keeps a shared repo from offering one app the other's
        build. Null when the release has no matching APK (i.e. it's the other app's release). */
    private fun appApk(assets: JSONArray?): JSONObject? {
        if (assets == null) return null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            val name = a.optString("name")
            if (name.endsWith(".apk", ignoreCase = true) && name.startsWith("${config.slug}-", ignoreCase = true)) return a
        }
        return null
    }

    /** Download the APK into the cache, reporting 0..100 progress. Returns the saved file. */
    suspend fun download(update: Update, onProgress: (Int) -> Unit = {}): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val out = File(dir, "${config.slug}-${update.versionName}.apk")
        val conn = (URL(update.apkUrl).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15_000; readTimeout = 30_000
            setRequestProperty("User-Agent", config.userAgent)
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

    /** Open the system screen where the user grants "install unknown apps" for this app. */
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
            setRequestProperty("User-Agent", config.userAgent)
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        return try { conn.inputStream.bufferedReader().use { it.readText() } } finally { conn.disconnect() }
    }
}
