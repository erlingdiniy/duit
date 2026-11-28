package com.diniy.duit.devcenter

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URI
import java.net.URL
import javax.net.ssl.HttpsURLConnection

data class DevHttpResponse(
    val statusCode: Int,
    val body: String,
    val headers: Map<String, String> = emptyMap()
) {
    val isSuccess: Boolean get() = statusCode in 200..299
}

interface DevHttpTransport {
    suspend fun request(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: String? = null
    ): DevHttpResponse

    suspend fun probeGet(url: String): DevHttpResponse = DevHttpResponse(404, "")

    suspend fun requestBackend(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: String? = null
    ): DevHttpResponse = request(method, url, headers, body)
}

/**
 * Hardened HTTPS-only transport restricted exclusively to [ALLOWED_HOSTS].
 * Blocks HTTP, custom ports, embedded credentials, and redirects outside the allowlist.
 */
class HttpsUrlConnectionTransport : DevHttpTransport {

    override suspend fun probeGet(url: String): DevHttpResponse = withContext(Dispatchers.IO) {
        validateFeedbackHealthUri(URI(url), isRedirect = false)
        var current = url
        var redirects = 0
        while (true) {
            val parsed = URL(current)
            val conn = (parsed.openConnection() as? HttpsURLConnection)
                ?: throw SecurityException("HTTPS connection required.")
            try {
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 8_000
                conn.readTimeout = 10_000
                conn.requestMethod = "GET"
                conn.useCaches = false
                conn.setRequestProperty("Accept", "application/json")
                val code = conn.responseCode
                if (code in 300..399 && redirects == 0) {
                    val location = conn.getHeaderField("Location")
                        ?: throw SecurityException("Missing feedback redirect target.")
                    val resolved = URI(current).resolve(location)
                    validateFeedbackHealthUri(resolved, isRedirect = true)
                    current = resolved.toString()
                    redirects += 1
                } else {
                    if (code in 300..399) {
                        throw SecurityException("Blocked extra redirect (HTTP $code) on feedback probe.")
                    }
                    val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                    val responseBody = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                    return@withContext DevHttpResponse(statusCode = code, body = responseBody)
                }
            } finally {
                conn.disconnect()
            }
        }
        DevHttpResponse(500, "")
    }

    override suspend fun requestBackend(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String?
    ): DevHttpResponse = withContext(Dispatchers.IO) {
        val allowLoopbackDebug = com.diniy.duit.BuildConfig.DEBUG
        validateBackendUrl(url, allowLoopbackDebug = allowLoopbackDebug)
        val parsed = URL(url)
        val conn = (parsed.openConnection() as? java.net.HttpURLConnection)
            ?: throw IOException("HTTP connection required.")
        if (parsed.protocol.equals("http", ignoreCase = true) && !allowLoopbackDebug) {
            throw SecurityException("Only HTTPS backend connections are permitted.")
        }

        try {
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 10_000
            conn.readTimeout = 20_000
            conn.requestMethod = method
            conn.useCaches = false

            for ((key, value) in headers) {
                conn.setRequestProperty(key, value)
            }
            if (body != null) {
                conn.doOutput = true
                val bytes = body.toByteArray(Charsets.UTF_8)
                conn.setFixedLengthStreamingMode(bytes.size)
                conn.outputStream.use { it.write(bytes) }
            }

            val code = conn.responseCode
            if (code in 300..399) {
                throw SecurityException("Blocked redirect (HTTP $code) on backend endpoint.")
            }

            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val responseBody = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val responseHeaders = buildMap {
                conn.headerFields.forEach { (k, vals) ->
                    if (k != null && !vals.isNullOrEmpty()) {
                        put(k.lowercase(), vals.first())
                    }
                }
            }
            DevHttpResponse(statusCode = code, body = responseBody, headers = responseHeaders)
        } catch (e: IOException) {
            throw e
        } finally {
            conn.disconnect()
        }
    }

    override suspend fun request(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String?
    ): DevHttpResponse = withContext(Dispatchers.IO) {
        validateAllowlistedHttpsUrl(url)
        val parsed = URL(url)
        val conn = (parsed.openConnection() as? HttpsURLConnection)
            ?: throw SecurityException("HTTPS connection required.")

        try {
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 8_000
            conn.readTimeout = 10_000
            conn.requestMethod = method
            conn.useCaches = false

            for ((key, value) in headers) {
                conn.setRequestProperty(key, value)
            }
            if (body != null) {
                conn.doOutput = true
                val bytes = body.toByteArray(Charsets.UTF_8)
                conn.setFixedLengthStreamingMode(bytes.size)
                conn.outputStream.use { it.write(bytes) }
            }

            val code = conn.responseCode
            if (code in 300..399) {
                throw SecurityException("Blocked redirect (HTTP $code) for security.")
            }

            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val responseBody = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val responseHeaders = buildMap {
                conn.headerFields.forEach { (k, vals) ->
                    if (k != null && !vals.isNullOrEmpty()) {
                        put(k.lowercase(), vals.first())
                    }
                }
            }
            DevHttpResponse(statusCode = code, body = responseBody, headers = responseHeaders)
        } catch (e: IOException) {
            throw e
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        val ALLOWED_HOSTS = setOf(
            "api.github.com",
            "sheets.googleapis.com",
            "analyticsdata.googleapis.com",
            "oauth2.googleapis.com",
            "firebaseremoteconfig.googleapis.com",
            "fcm.googleapis.com",
            "fcmdata.googleapis.com"
        )

        fun validateFeedbackHealthUri(uri: URI, isRedirect: Boolean) {
            if (!uri.scheme.equals("https", ignoreCase = true) ||
                !uri.userInfo.isNullOrEmpty() ||
                (uri.port != -1 && uri.port != 443)
            ) {
                throw SecurityException("Feedback health probe requires clean HTTPS URL.")
            }
            val host = uri.host?.lowercase().orEmpty()
            val expectedHost = if (isRedirect) "script.googleusercontent.com" else "script.google.com"
            if (host != expectedHost) {
                throw SecurityException("Host \"$host\" is outside the feedback health allowlist.")
            }
        }

        fun validateAllowlistedHttpsUrl(rawUrl: String) {
            val uri = runCatching { URI(rawUrl) }.getOrElse {
                throw SecurityException("Malformed API URL.")
            }
            if (!uri.scheme.equals("https", ignoreCase = true)) {
                throw SecurityException("Only HTTPS requests are permitted.")
            }
            if (!uri.userInfo.isNullOrEmpty()) {
                throw SecurityException("Embedded URL credentials are not allowed.")
            }
            if (uri.port != -1 && uri.port != 443) {
                throw SecurityException("Non-standard HTTPS ports are not allowed.")
            }
            val host = uri.host?.lowercase().orEmpty()
            if (host !in ALLOWED_HOSTS) {
                throw SecurityException("Host \"$host\" is outside the Developer Center allowlist.")
            }
        }

        fun validateBackendUrl(rawUrl: String, allowLoopbackDebug: Boolean = false) {
            val uri = runCatching { URI(rawUrl) }.getOrElse {
                throw SecurityException("Malformed Mission Control backend URL.")
            }
            if (!uri.userInfo.isNullOrEmpty() || uri.fragment != null) {
                throw SecurityException("Embedded credentials or fragments are not allowed.")
            }
            val host = uri.host?.lowercase().orEmpty()
            val isLoopback = host == "127.0.0.1" || host == "localhost"
            if (uri.scheme.equals("https", ignoreCase = true)) {
                if (host.isEmpty()) throw SecurityException("Missing backend host.")
                if (uri.port != -1 && uri.port != 443) {
                    throw SecurityException("Non-standard HTTPS port is not allowed.")
                }
            } else if (allowLoopbackDebug && uri.scheme.equals("http", ignoreCase = true) && isLoopback) {
                if (uri.port !in 1024..65535) {
                    throw SecurityException("Loopback port out of range.")
                }
            } else {
                throw SecurityException("Mission Control backend requires HTTPS.")
            }
        }
    }
}
