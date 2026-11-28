package com.diniy.duit.update

import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import com.diniy.duit.BuildConfig
import com.diniy.duit.devcenter.AndroidKeyStoreDeviceKeyManager
import com.diniy.duit.devcenter.DataStoreDevCenterStorage
import com.diniy.duit.devcenter.DEFAULT_PRIVATE_UPDATE_REPO
import com.diniy.duit.devcenter.DevBackendClient
import com.diniy.duit.devcenter.DevCenterRedactor
import com.diniy.duit.devcenter.DevCenterValidators
import com.diniy.duit.devcenter.DevCenterVault
import com.diniy.duit.devcenter.HttpsUrlConnectionTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.coroutineContext

internal const val PRIVATE_DEV_METADATA_KEY = "com.diniy.duit.PRIVATE_DEV_EDITION"
private const val MAX_METADATA_BYTES = 2 * 1024 * 1024
private const val MAX_APK_BYTES = 100L * 1024 * 1024
private const val MAX_NOTES_CHARS = 12_000

internal data class PrivateDevRelease(
    val versionCode: Long,
    val assetApiUrl: URI,
    val size: Long,
    val sha256: String,
    val versionName: String,
    val notes: String,
    val publishedDate: String,
    val isBackendStream: Boolean = false
)

internal sealed interface PrivateCheckResult {
    data class Available(val release: PrivateDevRelease, val repo: String) : PrivateCheckResult
    data class UpToDate(val repo: String) : PrivateCheckResult
    data object AuthRequired : PrivateCheckResult
}

internal class NotPrivateRepoException(message: String) : IOException(message)

internal class AuthRejectedException(message: String) : IOException(message)

internal data class CandidateArchiveMetadata(
    val packageName: String,
    val versionCode: Long,
    val versionName: String,
    val hasPrivateDevMetadata: Boolean,
    val signers: Set<Signature>
)

internal interface PrivateDownloadTransport {
    suspend fun getJson(url: URI, pat: String): String
    suspend fun downloadStream(
        assetApiUrl: URI,
        pat: String,
        maxBytes: Long,
        onResponse: (contentLength: Long, stream: InputStream) -> Unit
    )
}

internal class HttpsPrivateDownloadTransport : PrivateDownloadTransport {
    override suspend fun getJson(url: URI, pat: String): String = withContext(Dispatchers.IO) {
        validateApiUri(url)
        val conn = (url.toURL().openConnection() as? HttpsURLConnection)
            ?: throw IOException("HTTPS connection required")
        try {
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.useCaches = false
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            conn.setRequestProperty("User-Agent", "duit-private-updater/1.0")
            conn.setRequestProperty("Authorization", "Bearer ${pat.trim()}")

            val code = conn.responseCode
            if (code == 401 || code == 403) {
                throw AuthRejectedException("GitHub rejected authentication (HTTP $code)")
            }
            if (code !in 200..299) {
                throw IOException("GitHub update request failed (HTTP $code)")
            }
            val bytes = conn.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buf)
                    if (count < 0) break
                    if (out.size() + count > MAX_METADATA_BYTES) {
                        throw IOException("Release metadata is too large")
                    }
                    out.write(buf, 0, count)
                }
                out.toByteArray()
            }
            bytes.toString(Charsets.UTF_8)
        } finally {
            conn.disconnect()
        }
    }

    override suspend fun downloadStream(
        assetApiUrl: URI,
        pat: String,
        maxBytes: Long,
        onResponse: (contentLength: Long, stream: InputStream) -> Unit
    ) = withContext(Dispatchers.IO) {
        val isBackendOta = isBackendOtaStreamUri(assetApiUrl)
        if (!isBackendOta) {
            validateApiUri(assetApiUrl)
        }
        var currentUri = assetApiUrl
        var sendAuth = true
        repeat(4) {
            val conn = (currentUri.toURL().openConnection() as? java.net.HttpURLConnection)
                ?: throw IOException("HTTP connection required")
            if (currentUri.scheme.equals("http", ignoreCase = true) && !BuildConfig.DEBUG) {
                throw SecurityException("HTTPS connection required")
            }
            try {
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 10_000
                conn.readTimeout = 30_000
                conn.useCaches = false
                conn.setRequestProperty("User-Agent", "duit-private-updater/1.0")
                if (sendAuth) {
                    if (isBackendOta) {
                        HttpsUrlConnectionTransport.validateBackendUrl(
                            currentUri.toString(),
                            allowLoopbackDebug = BuildConfig.DEBUG
                        )
                        conn.setRequestProperty("Authorization", "Bearer ${pat.trim()}")
                        conn.setRequestProperty("Accept", "application/vnd.android.package-archive")
                    } else {
                        validateApiUri(currentUri)
                        conn.setRequestProperty("Authorization", "Bearer ${pat.trim()}")
                        conn.setRequestProperty("Accept", "application/octet-stream")
                        conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                    }
                } else {
                    validateCdnUri(currentUri)
                }
                val code = conn.responseCode
                when (code) {
                    200 -> {
                        val contentLen = conn.contentLengthLong
                        if (contentLen > maxBytes) {
                            throw IOException("APK exceeds maximum allowed size")
                        }
                        conn.inputStream.use { input ->
                            onResponse(contentLen, input)
                        }
                        return@withContext
                    }
                    301, 302, 303, 307, 308 -> {
                        if (isBackendOta) {
                            throw SecurityException("Redirects blocked on backend OTA stream")
                        }
                        val location = conn.getHeaderField("Location")
                            ?: throw IOException("Missing download redirect location")
                        val nextUri = currentUri.resolve(location)
                        validateCdnUri(nextUri)
                        currentUri = nextUri
                        // Never leak GitHub PAT outside api.github.com.
                        sendAuth = false
                    }
                    401, 403 -> throw AuthRejectedException("Private update authorization failed (HTTP $code)")
                    else -> throw IOException("Download failed (HTTP $code)")
                }
            } catch (err: Throwable) {
                val safeMsg = DevCenterRedactor.redact(err.message ?: "Download failed", listOf(pat, currentUri.toString()))
                when (err) {
                    is AuthRejectedException -> throw AuthRejectedException(safeMsg)
                    is IOException -> throw IOException(safeMsg)
                    else -> throw IOException(safeMsg)
                }
            } finally {
                conn.disconnect()
            }
        }
        throw IOException("Too many download redirects")
    }
}

internal class PrivateDevUpdates(
    private val context: Context,
    private val transport: PrivateDownloadTransport = HttpsPrivateDownloadTransport(),
    private val patProvider: suspend () -> String? = {
        DevCenterVault(DataStoreDevCenterStorage(context)).readGithubPat()
    },
    private val repoProvider: suspend () -> String = {
        DevCenterVault(DataStoreDevCenterStorage(context)).getState().privateUpdateRepo
    },
    private val installedPackageProvider: () -> PackageInfo = {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_META_DATA
        )
    },
    private val candidateArchiveProvider: (File) -> CandidateArchiveMetadata? = { apk ->
        @Suppress("DEPRECATION")
        context.packageManager.getPackageArchiveInfo(
            apk.path,
            PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_META_DATA
        )?.let { pkg ->
            CandidateArchiveMetadata(
                packageName = pkg.packageName,
                versionCode = pkg.longVersionCode,
                versionName = pkg.versionName.orEmpty(),
                hasPrivateDevMetadata = pkg.applicationInfo?.metaData?.getBoolean(PRIVATE_DEV_METADATA_KEY, false) == true,
                signers = pkg.signingInfo?.apkContentsSigners?.toSet().orEmpty()
            )
        }
    },
    private val backendClientProvider: () -> DevBackendClient? = {
        val vault = DevCenterVault(DataStoreDevCenterStorage(context))
        DevBackendClient(vault, AndroidKeyStoreDeviceKeyManager(), HttpsUrlConnectionTransport())
    },
    private val isBackendEnrolledProvider: suspend () -> Boolean = {
        DevCenterVault(DataStoreDevCenterStorage(context)).getState().isBackendEnrolled
    }
) {
    private val manager = context.packageManager

    suspend fun currentRepo(): String =
        repoProvider().trim().ifBlank { DEFAULT_PRIVATE_UPDATE_REPO }

    suspend fun check(): PrivateCheckResult = withContext(Dispatchers.IO) {
        if (runCatching { isBackendEnrolledProvider() }.getOrDefault(false)) {
            val client = backendClientProvider()
            val baseUrl = client?.resolveBaseUrl().orEmpty()
            if (client != null && baseUrl.isNotBlank()) {
                return@withContext checkViaBackend(client, baseUrl)
            }
        }
        val repo = currentRepo()
        if (!DevCenterValidators.isValidPrivateUpdateRepo(repo)) {
            throw NotPrivateRepoException("Private update channel cannot point to a public repository.")
        }
        val pat = patProvider()?.trim().orEmpty()
        if (pat.isEmpty()) {
            return@withContext PrivateCheckResult.AuthRequired
        }
        val safeSecretList = listOf(pat)
        try {
            val repoMetaRaw = transport.getJson(URI("https://api.github.com/repos/$repo"), pat)
            val repoMeta = JSONObject(repoMetaRaw)
            if (!repoMeta.optBoolean("private", false)) {
                throw NotPrivateRepoException("Repository $repo is not private. Refusing update check.")
            }
            val releasesRaw = transport.getJson(URI("https://api.github.com/repos/$repo/releases?per_page=20"), pat)
            val release = selectPrivateDevRelease(
                releases = JSONArray(releasesRaw),
                repository = repo,
                installedCode = installedVersionCode()
            )
            if (release != null) PrivateCheckResult.Available(release, repo)
            else PrivateCheckResult.UpToDate(repo)
        } catch (err: NotPrivateRepoException) {
            throw NotPrivateRepoException(DevCenterRedactor.redact(err.message, safeSecretList))
        } catch (err: AuthRejectedException) {
            throw AuthRejectedException(DevCenterRedactor.redact(err.message, safeSecretList))
        } catch (err: Exception) {
            throw IOException(DevCenterRedactor.redact(err.message ?: "Private update check failed", safeSecretList))
        }
    }

    private suspend fun checkViaBackend(client: DevBackendClient, baseUrl: String): PrivateCheckResult {
        return try {
            val ota = client.fetchLatestPrivateOta() ?: return PrivateCheckResult.UpToDate(currentRepo())
            if (!DevCenterValidators.isValidPrivateUpdateRepo(ota.repository)) {
                throw NotPrivateRepoException("Private update channel cannot point to a public repository.")
            }
            if (ota.versionCode <= installedVersionCode() || !ota.versionName.contains("-dev") || ota.versionName.contains("-beta.")) {
                return PrivateCheckResult.UpToDate(ota.repository)
            }
            val rel = PrivateDevRelease(
                versionCode = ota.versionCode,
                assetApiUrl = URI("$baseUrl/v1/admin/ota/download/${ota.assetId}"),
                size = ota.sizeBytes,
                sha256 = ota.sha256.lowercase(),
                versionName = ota.versionName,
                notes = ota.notes.take(MAX_NOTES_CHARS),
                publishedDate = ota.publishedAt.take(10).takeIf { DATE_REGEX.matches(it) }.orEmpty(),
                isBackendStream = true
            )
            PrivateCheckResult.Available(rel, ota.repository)
        } catch (err: NotPrivateRepoException) {
            throw err
        } catch (err: com.diniy.duit.devcenter.DeviceRevokedException) {
            throw AuthRejectedException(DevCenterRedactor.redact(err.message))
        } catch (err: Exception) {
            throw IOException(DevCenterRedactor.redact(err.message ?: "Private update check failed", listOf(baseUrl)))
        }
    }

    suspend fun notesForInstalled(): String? = withContext(Dispatchers.IO) {
        val repo = currentRepo()
        if (!DevCenterValidators.isValidPrivateUpdateRepo(repo)) return@withContext null
        val pat = patProvider()?.trim().orEmpty()
        if (pat.isNotEmpty()) {
            return@withContext runCatching {
                val repoMeta = JSONObject(transport.getJson(URI("https://api.github.com/repos/$repo"), pat))
                if (!repoMeta.optBoolean("private", false)) return@withContext null
                val raw = transport.getJson(URI("https://api.github.com/repos/$repo/releases?per_page=20"), pat)
                findPrivateReleaseNotes(JSONArray(raw), installedVersionCode())
            }.getOrNull()
        }
        if (runCatching { isBackendEnrolledProvider() }.getOrDefault(false)) {
            val client = backendClientProvider() ?: return@withContext null
            return@withContext runCatching {
                val ota = client.fetchLatestPrivateOta()
                if (ota != null && ota.versionCode == installedVersionCode()) {
                    ota.notes.take(MAX_NOTES_CHARS)
                } else {
                    null
                }
            }.getOrNull()
        }
        null
    }

    fun installedVersionCode(): Long = installedPackageProvider().longVersionCode

    fun installedVersionName(): String =
        runCatching { installedPackageProvider().versionName }.getOrNull().orEmpty().ifBlank { BuildConfig.VERSION_NAME }

    fun isFreshInstall(): Boolean = installedPackageProvider().let { it.firstInstallTime == it.lastUpdateTime }

    fun canInstall(): Boolean = manager.canRequestPackageInstalls()

    suspend fun downloadAndRequestInstall(
        release: PrivateDevRelease,
        onProgress: (Float) -> Unit = {},
        status: suspend (sessionId: Int) -> IntentSender
    ): Int = withContext(Dispatchers.IO) {
        check(canInstall()) { "Allow installs from duit. before continuing" }
        var authToken = ""
        if (release.isBackendStream) {
            val client = backendClientProvider()
                ?: throw AuthRejectedException("Enroll this device in Developer Center to download private updates.")
            authToken = runCatching { client.ensureValidAccessToken(forceReauth = false) }
                .getOrElse { throw AuthRejectedException(it.message ?: "Device session unavailable.") }
        } else {
            authToken = patProvider()?.trim().orEmpty()
            if (authToken.isEmpty()) {
                throw AuthRejectedException("Connect your device in Developer Center (or add GitHub PAT) to download updates.")
            }
        }
        val apk = File.createTempFile("duit-private-dev-", ".apk", context.cacheDir)
        try {
            if (release.isBackendStream) {
                try {
                    downloadAndVerifyFile(release, authToken, apk, onProgress)
                } catch (authErr: AuthRejectedException) {
                    val client = backendClientProvider() ?: throw authErr
                    val refreshed = client.ensureValidAccessToken(forceReauth = true)
                    authToken = refreshed
                    downloadAndVerifyFile(release, refreshed, apk, onProgress)
                }
            } else {
                downloadAndVerifyFile(release, authToken, apk, onProgress)
            }
            val candidate = candidateArchiveProvider(apk) ?: throw IOException("Invalid APK archive")
            val current = installedPackageProvider()
            @Suppress("DEPRECATION")
            val currentSigners = current.signingInfo?.apkContentsSigners?.toSet().orEmpty()
            verifyCandidateArchive(
                release = release,
                candidate = candidate,
                expectedPackageName = context.packageName,
                installedVersionCode = current.longVersionCode,
                installedSigners = currentSigners
            )
            coroutineContext.ensureActive()
            commit(apk, status)
        } catch (err: Exception) {
            val cleanMsg = DevCenterRedactor.redact(
                err.message ?: "Private update failed",
                listOf(authToken, release.assetApiUrl.toString())
            )
            when (err) {
                is AuthRejectedException -> throw AuthRejectedException(cleanMsg)
                is SecurityException -> throw SecurityException(cleanMsg)
                else -> throw IOException(cleanMsg)
            }
        } finally {
            apk.delete()
        }
    }

    internal suspend fun downloadAndVerifyFile(
        release: PrivateDevRelease,
        pat: String,
        apk: File,
        onProgress: (Float) -> Unit = {}
    ) {
        if (!HEX_SHA256_REGEX.matches(release.sha256)) {
            throw IOException("Missing or invalid release SHA-256 digest")
        }
        val job = coroutineContext
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        var reportedPercent = -1
        transport.downloadStream(release.assetApiUrl, pat, release.size) { _, input ->
            apk.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    job.ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > release.size) {
                        throw IOException("Downloaded APK exceeds declared release size")
                    }
                    digest.update(buffer, 0, count)
                    output.write(buffer, 0, count)
                    val percent = (total * 100 / release.size).toInt().coerceIn(0, 100)
                    if (percent != reportedPercent) {
                        reportedPercent = percent
                        onProgress(percent / 100f)
                    }
                }
            }
        }
        val actualSha = digest.digest().joinToString("") { "%02x".format(it) }
        if (total != release.size || !actualSha.equals(release.sha256, ignoreCase = true)) {
            throw IOException("APK SHA-256 integrity check failed")
        }
    }

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
                val sender = status(id)
                job.ensureActive()
                session.commit(sender)
                committed = true
            }
            return id
        } finally {
            if (!committed) runCatching { installer.abandonSession(id) }
        }
    }
}

internal fun privateDevVersionCode(
    major: Int,
    minor: Int,
    patch: Int,
    devPatch: Int = 0
): Long? {
    if (major !in 1..200 || minor !in 0..999 || patch !in 0..99 || devPatch !in 0..99) return null
    return major * 10_000_000L + minor * 10_000L + patch * 100L + devPatch
}

internal fun parsePrivateDevReleaseIdentity(release: JSONObject): Triple<Long, String, String>? {
    if (release.optBoolean("draft", true)) return null
    val tag = release.optString("tag_name").trim()
    val match = PRIVATE_DEV_TAG.matchEntire(tag) ?: return null
    val (majorStr, minorStr, patchStr, devStr) = match.destructured
    val devPatch = devStr.ifEmpty { "0" }.toIntOrNull() ?: return null
    val code = privateDevVersionCode(
        major = majorStr.toInt(),
        minor = minorStr.toInt(),
        patch = patchStr.toInt(),
        devPatch = devPatch
    ) ?: return null
    val versionName = tag.removePrefix("v")
    return Triple(code, "duit-$versionName.apk", versionName)
}

internal fun selectPrivateDevRelease(
    releases: JSONArray,
    repository: String,
    installedCode: Long
): PrivateDevRelease? {
    if (!DevCenterValidators.isValidPrivateUpdateRepo(repository)) return null
    var latest: PrivateDevRelease? = null
    for (i in 0 until releases.length()) {
        val release = releases.optJSONObject(i) ?: continue
        val (code, expectedName, versionName) = parsePrivateDevReleaseIdentity(release) ?: continue
        if (code <= installedCode || code > 2_100_000_000L || code <= (latest?.versionCode ?: 0L)) continue
        val assets = release.optJSONArray("assets") ?: continue
        val assetPrefix = "https://api.github.com/repos/$repository/releases/assets/"
        for (j in 0 until assets.length()) {
            val asset = assets.optJSONObject(j) ?: continue
            if (asset.optString("name") != expectedName || asset.optString("state") != "uploaded") continue
            val apiUrl = asset.optString("url").trim()
            if (!apiUrl.startsWith(assetPrefix)) continue
            val assetId = apiUrl.removePrefix(assetPrefix)
            if (assetId.isEmpty() || !assetId.all(Char::isDigit)) continue
            val digest = DIGEST_REGEX.matchEntire(asset.optString("digest")) ?: continue
            val size = asset.optLong("size", -1L)
            if (size !in 1L..MAX_APK_BYTES) continue
            latest = PrivateDevRelease(
                versionCode = code,
                assetApiUrl = URI(apiUrl),
                size = size,
                sha256 = digest.groupValues[1].lowercase(),
                versionName = versionName,
                notes = release.optString("body").take(MAX_NOTES_CHARS),
                publishedDate = release.optString("published_at").take(10).takeIf { DATE_REGEX.matches(it) }.orEmpty()
            )
            break
        }
    }
    return latest
}

internal fun findPrivateReleaseNotes(releases: JSONArray, installedCode: Long): String? {
    for (i in 0 until releases.length()) {
        val release = releases.optJSONObject(i) ?: continue
        val (code, _, _) = parsePrivateDevReleaseIdentity(release) ?: continue
        if (code == installedCode) {
            return release.optString("body").take(MAX_NOTES_CHARS)
        }
    }
    return null
}

internal fun verifyCandidateArchive(
    release: PrivateDevRelease,
    candidate: CandidateArchiveMetadata,
    expectedPackageName: String,
    installedVersionCode: Long,
    installedSigners: Set<Signature>
) {
    if (candidate.packageName != expectedPackageName) {
        throw SecurityException("Package ID mismatch: expected $expectedPackageName.")
    }
    if (!candidate.versionName.contains("-dev") || candidate.versionName.contains("-beta.")) {
        throw SecurityException("Candidate APK is not a private developer build.")
    }
    if (!candidate.hasPrivateDevMetadata) {
        throw SecurityException("Candidate APK lacks private developer verification metadata.")
    }
    if (candidate.versionCode != release.versionCode || candidate.versionCode <= installedVersionCode) {
        throw SecurityException("Candidate versionCode (${candidate.versionCode}) is incompatible or not newer.")
    }
    if (installedSigners.isEmpty() || installedSigners != candidate.signers) {
        throw SecurityException("Candidate APK signing certificate does not match this app.")
    }
}

private fun validateApiUri(uri: URI) {
    if (!uri.scheme.equals("https", ignoreCase = true) ||
        uri.host?.equals("api.github.com", ignoreCase = true) != true ||
        !uri.userInfo.isNullOrEmpty() ||
        uri.port !in listOf(-1, 443) ||
        uri.fragment != null
    ) {
        throw SecurityException("Unexpected GitHub API URL")
    }
}

private fun isBackendOtaStreamUri(uri: URI): Boolean =
    Regex("""/v1/admin/ota/download/\d+""").matches(uri.path.orEmpty())

private fun validateCdnUri(uri: URI) {
    val host = uri.host?.lowercase().orEmpty()
    if (!uri.scheme.equals("https", ignoreCase = true) ||
        host !in PRIVATE_CDN_HOSTS ||
        !uri.userInfo.isNullOrEmpty() ||
        uri.port !in listOf(-1, 443) ||
        uri.fragment != null
    ) {
        throw SecurityException("Unexpected download CDN host")
    }
}

internal class PrivateDevUpdateMemory(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("duit_private_dev_updates", Context.MODE_PRIVATE)

    var acknowledgedVersionCode: Long?
        get() = prefs.getLong(KEY_ACK, -1L).takeIf { it >= 0L }
        set(value) = prefs.edit().putLong(KEY_ACK, value ?: -1L).apply()

    fun cachedNotes(versionCode: Long): String? = prefs.getString("$KEY_NOTES$versionCode", null)

    fun cacheNotes(versionCode: Long, notes: String) {
        prefs.edit().putString("$KEY_NOTES$versionCode", notes).apply()
    }

    var lastCheckEpochMs: Long
        get() = prefs.getLong(KEY_CHECK_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_CHECK_AT, value).apply()

    var lastCheckResult: String
        get() = prefs.getString(KEY_CHECK_RESULT, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_CHECK_RESULT, value).apply()

    private companion object {
        const val KEY_ACK = "acknowledged_version_code"
        const val KEY_NOTES = "notes_"
        const val KEY_CHECK_AT = "last_check_at"
        const val KEY_CHECK_RESULT = "last_check_result"
    }
}

internal fun shouldShowPrivateWhatsNew(acknowledged: Long?, installedCode: Long, freshInstall: Boolean): Boolean =
    if (acknowledged == null) !freshInstall else installedCode > acknowledged

internal data class PrivateNoteItem(val text: String, val bullet: Boolean)

internal data class PrivateNotesSection(val icon: String, val title: String, val items: List<PrivateNoteItem>)

/**
 * Strips untrusted markdown/HTML/URLs/checksum lines from GitHub release bodies into safe plain text.
 */
internal fun parsePrivateReleaseNotes(raw: String): List<PrivateNotesSection> {
    val sections = mutableListOf<PrivateNotesSection>()
    var heading: String? = null
    var items = mutableListOf<PrivateNoteItem>()
    fun flush() {
        if (items.isNotEmpty()) sections += privateSectionFor(heading, items.toList())
        items = mutableListOf()
    }
    raw.take(MAX_NOTES_CHARS).replace("\r\n", "\n").lines().forEach { line ->
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return@forEach
        if (CHECKSUM_LINE.containsMatchIn(trimmed)) return@forEach
        MD_HEADING.matchEntire(trimmed)?.let {
            flush()
            heading = plainNoteText(it.groupValues[1])
            return@forEach
        }
        val bullet = MD_BULLET.matchEntire(trimmed)
        val text = plainNoteText(bullet?.groupValues?.get(1) ?: trimmed)
        if (text.isNotBlank()) items += PrivateNoteItem(text, bullet != null)
    }
    flush()
    val merged = mutableListOf<PrivateNotesSection>()
    for (section in sections) {
        val prev = merged.lastOrNull()
        if (prev != null && prev.title == section.title) {
            merged[merged.lastIndex] = prev.copy(items = prev.items + section.items)
        } else {
            merged += section
        }
    }
    return merged
}

private fun privateSectionFor(heading: String?, items: List<PrivateNoteItem>): PrivateNotesSection {
    val h = heading?.lowercase().orEmpty()
    return when {
        heading == null -> PrivateNotesSection("✨", "What's New", items)
        listOf("fix", "bug", "security").any { it in h } -> PrivateNotesSection("🛠", "Bug Fixes", items)
        listOf("improve", "enhance", "performance").any { it in h } -> PrivateNotesSection("🚀", "Improvements", items)
        listOf("new", "feature").any { it in h } -> PrivateNotesSection("✨", "What's New", items)
        else -> PrivateNotesSection("📝", heading, items)
    }
}

private fun plainNoteText(input: String): String = input
    .replace(MD_IMAGE, "")
    .replace(MD_LINK) { it.groupValues[1] }
    .replace(HTML_TAG, "")
    .replace(MD_EMPHASIS, "")
    .filter { it == '\t' || !it.isISOControl() }
    .trim()
    .take(500)

private val CHECKSUM_LINE = Regex("sha-?256", RegexOption.IGNORE_CASE)
private val MD_HEADING = Regex("""#{1,6}\s+(.+?)\s*#*""")
private val MD_BULLET = Regex("""(?:[-*+•]|\d+[.)])\s+(.+)""")
private val MD_IMAGE = Regex("""!\[[^\]]*]\([^)]*\)""")
private val MD_LINK = Regex("""\[([^\]]*)]\([^)]*\)""")
private val HTML_TAG = Regex("""<[^>]*>""")
private val MD_EMPHASIS = Regex("""\*\*|__|~~|`""")

private val PRIVATE_DEV_TAG = Regex("""v(\d{1,3})\.(\d{1,3})\.(\d{1,3})-dev(?:\.([1-9]\d?))?""")
private val DIGEST_REGEX = Regex("""sha256:([0-9a-fA-F]{64})""")
private val HEX_SHA256_REGEX = Regex("""[0-9a-fA-F]{64}""")
private val DATE_REGEX = Regex("""\d{4}-\d{2}-\d{2}""")
private val PRIVATE_CDN_HOSTS = setOf(
    "objects.githubusercontent.com",
    "release-assets.githubusercontent.com",
    "github-releases.githubusercontent.com"
)
