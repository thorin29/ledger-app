package com.budget.app.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.budget.app.BuildConfig
import com.budget.app.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** A newer release than the installed one. */
@Serializable
data class UpdateInfo(val versionCode: Int, val versionName: String, val notes: String, val apkUrl: String)

/**
 * Checks this app's public GitHub releases for a newer build, the same way the
 * Kairos app does: the latest release carries `latest.json` (versionCode,
 * versionName, notes, tag, apk) and the signed APK. Nothing about the person or their
 * server is sent; it is an anonymous read of a public page.
 *
 * The last result is kept, so the badges survive a restart and a failed check
 * (offline, rate-limited) changes nothing. Once the newer build is installed
 * the versions match and the badges clear by themselves.
 */
class UpdateChecker(private val settings: Settings) {
    private val client = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }

    private val _available = MutableStateFlow<UpdateInfo?>(null)
    val available: StateFlow<UpdateInfo?> = _available

    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError

    /** Loads the last result saved on the phone. */
    suspend fun restore() {
        val saved = settings.get(Settings.UPDATE_LATEST)?.let { runCatching { json.decodeFromString<UpdateInfo>(it) }.getOrNull() }
        _available.value = saved?.takeIf { it.versionCode > BuildConfig.VERSION_CODE }
    }

    /** Returns the newer release, or null when there is none or the check failed. */
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        _checking.value = true
        try {
            // The API is only asked when the download links have nothing (or fail).
            val first = runCatching { fromDownloads() }
            val latest = first.getOrNull() ?: runCatching { fromApi() }.getOrElse { throw first.exceptionOrNull() ?: it }
            val found = latest?.takeIf { it.versionCode > BuildConfig.VERSION_CODE }
            settings.set(Settings.UPDATE_LATEST, found?.let { json.encodeToString(UpdateInfo.serializer(), it) })
            _available.value = found
            _lastError.value = null
            found
        } catch (e: Exception) {
            _lastError.value = "Couldn't check for updates (${e.message ?: "offline"})."
            null
        } finally {
            _checking.value = false
        }
    }

    /**
     * The latest release's files, read through github.com's download links. Unlike GitHub's API,
     * these aren't limited to 60 checks an hour per address. A home network where other devices
     * or servers also check GitHub can use that up, and the check then fails with HTTP 403.
     * Null when no release has latest.json yet (404).
     */
    private fun fromDownloads(): UpdateInfo? {
        val base = "https://github.com/${BuildConfig.UPDATE_REPO}/releases"
        val latest = fetchOrNull<LatestJson>("$base/latest/download/latest.json", api = false) ?: return null
        val apk = latest.apk.ifBlank { "budget-${latest.versionName}.apk" }
        val tag = latest.tag.ifBlank { "v${latest.versionName}" }
        return UpdateInfo(latest.versionCode, latest.versionName, latest.notes, "$base/download/$tag/$apk")
    }

    /** The API, for a release older than the download-link check. */
    private fun fromApi(): UpdateInfo? {
        val release = fetchOrNull<GhRelease>("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest") ?: return null
        val meta = release.assets.firstOrNull { it.name == "latest.json" } ?: return null
        val apk = release.assets.firstOrNull { it.name.endsWith(".apk") } ?: return null
        val latest = fetch<LatestJson>(meta.url)
        return UpdateInfo(latest.versionCode, latest.versionName, latest.notes, apk.url)
    }

    /** Like [fetch], but a 404 is null. */
    private inline fun <reified T> fetchOrNull(url: String, api: Boolean = true): T? {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "Ledger-Android")
            .apply { if (api) header("Accept", "application/vnd.github+json") }
            .build()
        client.newCall(req).execute().use { resp ->
            if (resp.code == 404) return null
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            return json.decodeFromString(resp.body?.string() ?: throw IOException("empty"))
        }
    }

    private inline fun <reified T> fetch(url: String): T {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "Ledger-Android") // GitHub refuses requests without one
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            return json.decodeFromString(resp.body?.string() ?: throw IOException("empty"))
        }
    }
}

@Serializable
private data class GhRelease(val assets: List<GhAsset> = emptyList())

@Serializable
private data class GhAsset(val name: String = "", @SerialName("browser_download_url") val url: String = "")

@Serializable
private data class LatestJson(
    val versionCode: Int = 0,
    val versionName: String = "",
    val notes: String = "",
    /** The release's tag and APK file name (releases from 0.24.1 on). */
    val tag: String = "",
    val apk: String = "",
)

/**
 * Downloads the update APK and hands it to Android's installer. The person
 * confirms on the system screen (an app can't install silently) and allows
 * "install unknown apps" for Budget once.
 */
class UpdateInstaller(private val app: Context) {
    sealed interface State {
        data object Idle : State
        data object NeedsPermission : State
        data class Downloading(val pct: Int) : State
        data object Installing : State
        data class Error(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    private val client = OkHttpClient.Builder().callTimeout(5, TimeUnit.MINUTES).build()

    fun canInstall(): Boolean = app.packageManager.canRequestPackageInstalls()

    fun permissionIntent(): Intent = Intent(
        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${app.packageName}"),
    )

    fun reset() {
        _state.value = State.Idle
    }

    suspend fun downloadAndInstall(url: String) = withContext(Dispatchers.IO) {
        if (!canInstall()) {
            _state.value = State.NeedsPermission
            return@withContext
        }
        // Only ever from GitHub, over https.
        if (!url.startsWith("https://github.com/") && !url.startsWith("https://objects.githubusercontent.com/")) {
            _state.value = State.Error("Unexpected download address")
            return@withContext
        }
        try {
            _state.value = State.Downloading(0)
            val file = File(app.cacheDir, "budget-update.apk")
            if (file.exists()) file.delete()
            val req = Request.Builder().url(url).header("User-Agent", "Budget-Android").build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("Download failed (HTTP ${resp.code})")
                val body = resp.body ?: throw IOException("Empty download")
                val total = body.contentLength()
                body.byteStream().use { input ->
                    file.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            done += n
                            if (total > 0) _state.value = State.Downloading(((done * 100) / total).toInt().coerceIn(0, 100))
                        }
                    }
                }
            }
            _state.value = State.Installing
            val uri = FileProvider.getUriForFile(app, "${app.packageName}.updates", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            app.startActivity(intent)
            _state.value = State.Idle
        } catch (e: Exception) {
            _state.value = State.Error(e.message ?: "Update failed")
        }
    }
}
