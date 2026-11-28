package com.diniy.duit.feedback

import android.content.Context
import com.diniy.duit.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import java.util.UUID
import javax.net.ssl.HttpsURLConnection

/** The three categories, with the exact values the Apps Script backend accepts. */
internal enum class FeedbackCategory(val emoji: String, val label: String, val apiValue: String) {
    BUG("🐛", "Report a Bug", "Bug Report"),
    FEATURE("💡", "Suggest a Feature", "Feature Request"),
    GENERAL("💛", "General Feedback", "General Feedback")
}

/** Outcome of one submission. Only a JSON body with "ok": true counts as success. */
internal sealed interface FeedbackResult {
    data object Sent : FeedbackResult
    data class Rejected(val code: String) : FeedbackResult
    data class Failed(val offline: Boolean, val timedOut: Boolean = false) : FeedbackResult
}

internal object FeedbackRules {
    const val MESSAGE_MIN = 10
    const val MESSAGE_MAX = 2000
    private val EMAIL = Regex("""^[^\s@<>"']+@[^\s@<>"']+\.[A-Za-z]{2,}$""")

    fun messageError(message: String): String? {
        val length = message.trim().length
        return when {
            length < MESSAGE_MIN -> "Please write at least $MESSAGE_MIN characters."
            length > MESSAGE_MAX -> "Please keep it under $MESSAGE_MAX characters."
            else -> null
        }
    }

    fun emailError(email: String): String? {
        val value = email.trim()
        return if (value.isEmpty() || (value.length <= 254 && EMAIL.matches(value))) null
        else "That email doesn't look right. You can also leave it empty."
    }
}

/**
 * Exactly the documented fields, nothing else: no balances, transactions, device details or logs.
 * "website" is the empty honeypot the backend expects.
 */
internal fun buildFeedbackPayload(
    category: FeedbackCategory,
    message: String,
    email: String,
    appVersion: String,
    installId: String
): String = JSONObject()
    .put("category", category.apiValue)
    .put("message", message.trim())
    .put("email", email.trim())
    .put("appVersion", appVersion)
    .put("installId", installId)
    .put("website", "")
    .toString()

/** HTTP 200 alone proves nothing: Apps Script always answers 200, so read the JSON. */
internal fun parseFeedbackResponse(body: String): FeedbackResult {
    val json = runCatching { JSONObject(body) }.getOrNull() ?: return FeedbackResult.Failed(offline = false)
    return if (json.optBoolean("ok", false)) FeedbackResult.Sent
    else FeedbackResult.Rejected(json.optString("error").ifBlank { "UNKNOWN" })
}

/** Apps Script answers a POST with a redirect to googleusercontent.com, which must be fetched with GET. */
internal fun isAllowedFeedbackHost(uri: URI, first: Boolean): Boolean {
    if (uri.scheme != "https" || uri.userInfo != null || uri.port !in listOf(-1, 443)) return false
    val host = uri.host ?: return false
    return if (first) host == "script.google.com" else host == "script.googleusercontent.com"
}

/** Validates a Mission Control Worker endpoint host (HTTPS only, exact configured host). */
internal fun isAllowedMissionFeedbackHost(uri: URI, configuredBackendUrl: String): Boolean {
    if (uri.scheme != "https" || uri.userInfo != null || uri.port !in listOf(-1, 443)) return false
    val expectedHost = runCatching { URI(configuredBackendUrl.trim()).host }.getOrNull() ?: return false
    return uri.host.equals(expectedHost, ignoreCase = true)
}

internal fun resolveFeedbackEndpoint(
    devBackendUrl: String = BuildConfig.DEV_BACKEND_URL,
    legacyFeedbackUrl: String = BuildConfig.FEEDBACK_URL
): Pair<String, Boolean> {
    val cleanWorker = devBackendUrl.trim().trimEnd('/')
    return if (cleanWorker.startsWith("https://")) {
        "$cleanWorker/v1/public/feedback" to true
    } else {
        legacyFeedbackUrl to false
    }
}

internal interface FeedbackSender {
    suspend fun send(category: FeedbackCategory, message: String, email: String): FeedbackResult
}

internal class AppsScriptFeedbackSender(private val context: Context) : FeedbackSender {

    override suspend fun send(category: FeedbackCategory, message: String, email: String): FeedbackResult =
        withContext(Dispatchers.IO) {
            val (endpoint, isWorker) = resolveFeedbackEndpoint()
            if (endpoint.isEmpty()) return@withContext FeedbackResult.Failed(offline = false)
            val payload = buildFeedbackPayload(category, message, email, appVersion(), installId(context))
                .toByteArray(Charsets.UTF_8)
            if (payload.size > MAX_REQUEST_BYTES) return@withContext FeedbackResult.Rejected("PAYLOAD_TOO_LARGE")
            try {
                val raw = if (isWorker) postWorkerJson(URI(endpoint), payload) else post(URI(endpoint), payload)
                parseFeedbackResponse(raw)
            } catch (error: IOException) {
                FeedbackResult.Failed(
                    offline = error is java.net.UnknownHostException || error is java.net.ConnectException,
                    timedOut = error is java.net.SocketTimeoutException
                )
            }
        }

    private fun postWorkerJson(endpoint: URI, payload: ByteArray): String {
        if (!isAllowedMissionFeedbackHost(endpoint, BuildConfig.DEV_BACKEND_URL)) {
            throw IOException("Unexpected Mission Control feedback host")
        }
        val connection = endpoint.toURL().openConnection() as HttpsURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.setFixedLengthStreamingMode(payload.size)
            connection.outputStream.use { it.write(payload) }
            val code = connection.responseCode
            val stream = if (code >= 400) connection.errorStream else connection.inputStream
            if (stream != null && code in listOf(200, 400, 413, 429)) {
                return readStreamCapped(stream)
            }
            throw IOException("Mission Control feedback failed (HTTP $code)")
        } finally {
            connection.disconnect()
        }
    }

    private fun post(endpoint: URI, payload: ByteArray): String {
        if (!isAllowedFeedbackHost(endpoint, first = true)) throw IOException("Unexpected feedback server")
        val connection = endpoint.toURL().openConnection() as HttpsURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
            connection.setFixedLengthStreamingMode(payload.size)
            connection.outputStream.use { it.write(payload) }
            return when (val code = connection.responseCode) {
                200 -> readCapped(connection)
                301, 302, 303, 307, 308 -> {
                    val location = connection.getHeaderField("Location") ?: throw IOException("Missing redirect")
                    get(endpoint.resolve(location))
                }
                else -> throw IOException("Feedback request failed (HTTP $code)")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun get(target: URI): String {
        if (!isAllowedFeedbackHost(target, first = false)) throw IOException("Unexpected feedback redirect")
        val connection = target.toURL().openConnection() as HttpsURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            if (connection.responseCode != 200) throw IOException("Feedback response failed (HTTP ${connection.responseCode})")
            return readCapped(connection)
        } finally {
            connection.disconnect()
        }
    }

    private fun readCapped(connection: HttpsURLConnection): String =
        readStreamCapped(connection.inputStream)

    private fun readStreamCapped(input: java.io.InputStream): String = input.use { stream ->
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            if (out.size() + count > MAX_RESPONSE_BYTES) throw IOException("Feedback response too large")
            out.write(buffer, 0, count)
        }
        out.toString(Charsets.UTF_8.name())
    }

    private fun appVersion(): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()

    private companion object {
        const val MAX_REQUEST_BYTES = 8 * 1024
        const val MAX_RESPONSE_BYTES = 16 * 1024
    }
}

/**
 * Random ID created once per install, used only so the backend can rate-limit floods.
 * Not derived from hardware, accounts or anything else on the device.
 */
internal fun installId(context: Context): String {
    val prefs = context.applicationContext.getSharedPreferences("duit_feedback", Context.MODE_PRIVATE)
    prefs.getString("install_id", null)?.let { return it }
    val created = UUID.randomUUID().toString()
    prefs.edit().putString("install_id", created).apply()
    return created
}
