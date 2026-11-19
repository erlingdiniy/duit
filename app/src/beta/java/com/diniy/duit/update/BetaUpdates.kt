package com.diniy.duit.update

import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import com.diniy.duit.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.URI
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.coroutineContext

internal data class BetaRelease(
    val versionCode: Long,
    val download: URI,
    val size: Long,
    val sha256: String,
    /** Display-only metadata from the same validated GitHub release; never used for trust decisions. */
    val versionName: String = "",
    val notes: String = "",
    val publishedDate: String = ""
)

internal class BetaUpdates(private val context: Context) {
    private val repository = BuildConfig.BETA_REPOSITORY
    private val manager = context.packageManager

    suspend fun check(): BetaRelease? = withContext(Dispatchers.IO) {
        if (repository.isEmpty()) return@withContext null
        selectBetaRelease(fetchReleases(), repository, installed().longVersionCode)
    }

    /** Notes of the release whose tag matches this exact installed build, or null if it has none. */
    suspend fun notesForInstalled(): String? = withContext(Dispatchers.IO) {
        if (repository.isEmpty()) return@withContext null
        findReleaseNotes(fetchReleases(), installed().longVersionCode)
    }

    fun installedVersionCode(): Long = installed().longVersionCode

    /** True right after a fresh install (never updated), so What's New is not shown to new users. */
    fun isFreshInstall(): Boolean = installed().let { it.firstInstallTime == it.lastUpdateTime }

    private suspend fun fetchReleases(): JSONArray = withContext(Dispatchers.IO) {
        val url = URI("https://api.github.com/repos/$repository/releases?per_page=30")
        request(url, setOf("api.github.com")) { connection ->
            val bytes = connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > MAX_METADATA_BYTES) throw IOException("Release metadata is too large")
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            JSONArray(bytes.toString(Charsets.UTF_8))
        }
    }

    fun canInstall(): Boolean = manager.canRequestPackageInstalls()

    fun installedVersionName(): String = runCatching { installed().versionName }.getOrNull().orEmpty()

    // The caller owns the PackageInstaller status callback and must surface Android's confirmation
    // while the activity is visible. Commit alone does not report an installed update.
    suspend fun downloadAndRequestInstall(
        release: BetaRelease,
        onProgress: (Float) -> Unit = {},
        status: suspend (sessionId: Int) -> IntentSender
    ): Int =
        withContext(Dispatchers.IO) {
            check(canInstall()) { "Allow installs from duit. Beta before continuing" }
            val apk = File.createTempFile("duit-beta-", ".apk", context.cacheDir)
            try {
                download(release, apk, onProgress)
                verifyApk(release, apk)
                coroutineContext.ensureActive()
                commit(apk, status)
            } finally {
                apk.delete()
            }
        }

    private suspend fun download(release: BetaRelease, apk: File, onProgress: (Float) -> Unit) {
        val job = coroutineContext
        val hash = MessageDigest.getInstance("SHA-256")
        var total = 0L
        var reportedPercent = -1
        request(release.download, DOWNLOAD_HOSTS) { connection ->
            connection.inputStream.use { input ->
                apk.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        job.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > release.size) throw IOException("APK exceeds its declared size")
                        hash.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                        // Report whole-percent steps only, so the UI recomposes at most 100 times.
                        val percent = (total * 100 / release.size).toInt()
                        if (percent != reportedPercent) {
                            reportedPercent = percent
                            onProgress(percent / 100f)
                        }
                    }
                }
            }
        }
        val actual = hash.digest().joinToString("") { "%02x".format(it) }
        if (total != release.size || actual != release.sha256) throw IOException("APK integrity check failed")
    }

    private fun verifyApk(release: BetaRelease, apk: File) {
        @Suppress("DEPRECATION")
        val candidate = manager.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: throw IOException("Invalid APK")
        val current = installed()
        val currentSigners = current.signingInfo?.apkContentsSigners?.toSet().orEmpty()
        val candidateSigners = candidate.signingInfo?.apkContentsSigners?.toSet().orEmpty()
        if (candidate.packageName != context.packageName ||
            candidate.longVersionCode != release.versionCode ||
            candidate.longVersionCode <= current.longVersionCode ||
            currentSigners.isEmpty() || currentSigners != candidateSigners
        ) {
            throw IOException("APK package, version or signer does not match this beta update")
        }
    }

    @Suppress("DEPRECATION")
    private fun installed(): PackageInfo =
        manager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)

    private suspend fun commit(apk: File, status: suspend (Int) -> IntentSender): Int {
        val installer = manager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= 31) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            }
        }
        val id = installer.createSession(params)
        var committed = false
        try {
            val job = coroutineContext
            installer.openSession(id).use { session ->
                session.openWrite("base.apk", 0, apk.length()).use { output ->
                    apk.inputStream().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            job.ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                    }
                    session.fsync(output)
                }
                val statusSender = status(id)
                job.ensureActive()
                session.commit(statusSender)
                committed = true
            }
            return id
        } finally {
            if (!committed) runCatching { installer.abandonSession(id) }
        }
    }
}

/** Matches by exact tag (beta-N), never by "latest", so notes always belong to the installed build. */
internal fun findReleaseNotes(releases: JSONArray, installedCode: Long): String? {
    for (i in 0 until releases.length()) {
        val release = releases.optJSONObject(i) ?: continue
        if (release.optBoolean("draft", true)) continue
        if (release.optString("tag_name") != "beta-$installedCode") continue
        return release.optString("body").take(MAX_NOTES_CHARS)
    }
    return null
}

internal fun selectBetaRelease(releases: JSONArray, repository: String, installedCode: Long): BetaRelease? {
    var latest: BetaRelease? = null
    for (i in 0 until releases.length()) {
        val release = releases.optJSONObject(i) ?: continue
        if (release.optBoolean("draft", true) || !release.optBoolean("prerelease")) continue
        val tag = release.optString("tag_name")
        val code = TAG.matchEntire(tag)?.groupValues?.get(1)?.toLongOrNull() ?: continue
        if (code <= installedCode || code > 2_100_000_000L || code <= (latest?.versionCode ?: 0)) continue
        val name = "duit-beta-$code.apk"
        val expected = "https://github.com/$repository/releases/download/$tag/$name"
        val assets = release.optJSONArray("assets") ?: continue
        for (j in 0 until assets.length()) {
            val asset = assets.optJSONObject(j) ?: continue
            if (asset.optString("name") != name || asset.optString("state") != "uploaded") continue
            if (asset.optString("browser_download_url") != expected) continue
            val digest = DIGEST.matchEntire(asset.optString("digest")) ?: continue
            val size = asset.optLong("size", -1)
            if (size !in 1..MAX_APK_BYTES) continue
            latest = BetaRelease(
                code, URI(expected), size, digest.groupValues[1].lowercase(),
                versionName = VERSION_NAME.find(release.optString("name"))?.value.orEmpty(),
                notes = release.optString("body").take(MAX_NOTES_CHARS),
                publishedDate = release.optString("published_at").take(10).takeIf { DATE.matches(it) }.orEmpty()
            )
            break
        }
    }
    return latest
}

private inline fun <T> request(start: URI, hosts: Set<String>, read: (HttpsURLConnection) -> T): T {
    var url = start
    repeat(6) {
        if (url.scheme != "https" || url.host !in hosts || url.userInfo != null ||
            url.port !in listOf(-1, 443) || url.fragment != null
        ) throw IOException("Unexpected update server")

        val connection = url.toURL().openConnection() as HttpsURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", if (url.host == "api.github.com") "application/vnd.github+json" else "application/octet-stream")
            when (val code = connection.responseCode) {
                200 -> return read(connection)
                301, 302, 303, 307, 308 -> {
                    val location = connection.getHeaderField("Location") ?: throw IOException("Missing redirect")
                    url = url.resolve(location)
                }
                else -> throw IOException("GitHub update request failed (HTTP $code)")
            }
        } finally {
            connection.disconnect()
        }
    }
    throw IOException("Too many update redirects")
}

private val DOWNLOAD_HOSTS = setOf("github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")
private val TAG = Regex("beta-([1-9][0-9]{0,9})")
private val DIGEST = Regex("sha256:([0-9a-fA-F]{64})")
private const val MAX_METADATA_BYTES = 2 * 1024 * 1024
private const val MAX_APK_BYTES = 100L * 1024 * 1024
private const val MAX_NOTES_CHARS = 12_000
private val VERSION_NAME = Regex("""\d+\.\d+\.\d+(?:-[0-9A-Za-z.]+)?""")
private val DATE = Regex("""\d{4}-\d{2}-\d{2}""")
