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
}

/**
 * Hardened HTTPS-only transport restricted exclusively to [ALLOWED_HOSTS].
 * Blocks HTTP, custom ports, embedded credentials, and redirects outside the allowlist.
 */
class HttpsUrlConnectionTransport : DevHttpTransport {

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
        val ALLOWED_HOSTS = setOf("api.github.com", "sheets.googleapis.com", "analyticsdata.googleapis.com")

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
    }
}
