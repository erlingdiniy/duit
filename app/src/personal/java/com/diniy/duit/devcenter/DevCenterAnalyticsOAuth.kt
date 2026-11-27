package com.diniy.duit.devcenter

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

data class PkceOAuthSession(
    val clientId: String,
    val redirectUri: String,
    val codeVerifier: String,
    val codeChallenge: String,
    val state: String,
    val createdAtMs: Long
)

data class OAuthTokenExchangeResult(
    val accessToken: String,
    val refreshToken: String?,
    val expiresInSeconds: Long
)

sealed interface OAuthCallbackOutcome {
    data class AuthorizationCode(val code: String) : OAuthCallbackOutcome
    data class Error(val message: String) : OAuthCallbackOutcome
}

/**
 * In-memory transient holder bridging [DevAnalyticsOAuthRedirectActivity] with [DevCenterViewModel]
 * without persisting sensitive PKCE verifiers or authorization codes to disk.
 */
object DevAnalyticsOAuthBus {
    @Volatile
    var pendingSession: PkceOAuthSession? = null

    private val _pendingCallbackUri = MutableStateFlow<String?>(null)
    val pendingCallbackUri: StateFlow<String?> = _pendingCallbackUri.asStateFlow()

    fun publishCallbackUri(uriString: String) {
        _pendingCallbackUri.value = uriString
    }

    fun consumeCallbackUri(): String? {
        val current = _pendingCallbackUri.value
        _pendingCallbackUri.value = null
        return current
    }

    fun clear() {
        pendingSession = null
        _pendingCallbackUri.value = null
    }
}

/**
 * RFC 8252 public-client OAuth 2.0 Authorization Code + PKCE (`S256`) client for Google Analytics
 * read-only reporting (`analytics.readonly`). Uses NO `client_secret` in the APK and refreshes
 * expired access tokens silently using the Keystore-sealed refresh token.
 */
class DevCenterAnalyticsOAuth(
    private val transport: DevHttpTransport,
    private val random: SecureRandom = SecureRandom()
) {
    fun generatePkceSession(
        clientId: String,
        nowMs: Long = System.currentTimeMillis()
    ): PkceOAuthSession {
        val cleanClientId = clientId.trim()
        require(DevCenterValidators.isValidGoogleOAuthClientId(cleanClientId)) {
            "Enter a valid Google Android OAuth Client ID."
        }
        val verifierBytes = ByteArray(32).also { random.nextBytes(it) }
        val verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(verifierBytes)
        val challengeDigest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(challengeDigest)
        val stateBytes = ByteArray(24).also { random.nextBytes(it) }
        val state = Base64.getUrlEncoder().withoutPadding().encodeToString(stateBytes)

        return PkceOAuthSession(
            clientId = cleanClientId,
            redirectUri = redirectUriForClientId(cleanClientId),
            codeVerifier = verifier,
            codeChallenge = challenge,
            state = state,
            createdAtMs = nowMs
        )
    }

    fun buildAuthorizationUrl(session: PkceOAuthSession): String {
        val params = listOf(
            "client_id" to session.clientId,
            "redirect_uri" to session.redirectUri,
            "response_type" to "code",
            "scope" to ANALYTICS_READONLY_SCOPE,
            "code_challenge" to session.codeChallenge,
            "code_challenge_method" to "S256",
            "state" to session.state,
            "access_type" to "offline",
            "prompt" to "consent"
        ).joinToString("&") { (k, v) ->
            "${urlEncode(k)}=${urlEncode(v)}"
        }
        return "$GOOGLE_AUTH_ENDPOINT?$params"
    }

    fun parseCallbackUri(rawUri: String, expectedState: String): OAuthCallbackOutcome {
        val uri = runCatching { Uri.parse(rawUri) }.getOrNull()
            ?: return OAuthCallbackOutcome.Error("Malformed OAuth redirect callback.")

        val returnedError = uri.getQueryParameter("error")
        if (!returnedError.isNullOrBlank()) {
            val detail = when (returnedError) {
                "access_denied" -> "Google Analytics sign-in was cancelled."
                else -> "Google authorization failed ($returnedError)."
            }
            return OAuthCallbackOutcome.Error(detail)
        }

        val returnedState = uri.getQueryParameter("state").orEmpty()
        if (returnedState.isBlank() || returnedState != expectedState) {
            return OAuthCallbackOutcome.Error("OAuth state validation failed. Retry sign-in.")
        }

        val code = uri.getQueryParameter("code")?.trim().orEmpty()
        if (code.isBlank()) {
            return OAuthCallbackOutcome.Error("No authorization code was returned.")
        }
        return OAuthCallbackOutcome.AuthorizationCode(code)
    }

    suspend fun exchangeAuthorizationCode(
        clientId: String,
        code: String,
        codeVerifier: String,
        redirectUri: String
    ): OAuthTokenExchangeResult {
        val formBody = buildFormBody(
            "client_id" to clientId.trim(),
            "code" to code.trim(),
            "code_verifier" to codeVerifier.trim(),
            "grant_type" to "authorization_code",
            "redirect_uri" to redirectUri.trim()
        )
        return postTokenRequest(
            formBody = formBody,
            secretsToRedact = listOf(code, codeVerifier)
        )
    }

    suspend fun refreshAccessToken(
        clientId: String,
        refreshToken: String
    ): OAuthTokenExchangeResult {
        val formBody = buildFormBody(
            "client_id" to clientId.trim(),
            "refresh_token" to refreshToken.trim(),
            "grant_type" to "refresh_token"
        )
        return postTokenRequest(
            formBody = formBody,
            secretsToRedact = listOf(refreshToken)
        )
    }

    private suspend fun postTokenRequest(
        formBody: String,
        secretsToRedact: List<String>
    ): OAuthTokenExchangeResult {
        val res = transport.request(
            method = "POST",
            url = GOOGLE_TOKEN_ENDPOINT,
            headers = mapOf(
                "Content-Type" to "application/x-www-form-urlencoded",
                "Accept" to "application/json"
            ),
            body = formBody
        )
        val json = runCatching { JSONObject(res.body) }.getOrNull()
        if (!res.isSuccess || json == null) {
            val errCode = json?.optString("error").orEmpty()
            val errDesc = json?.optString("error_description").orEmpty()
            val msg = when {
                errCode == "invalid_grant" ->
                    "Google session expired or was revoked. Sign in again."
                errCode.isNotBlank() ->
                    "Google OAuth error ($errCode): ${errDesc.ifBlank { "HTTP ${res.statusCode}" }}"
                else ->
                    "Google OAuth token request failed (HTTP ${res.statusCode})."
            }
            throw IllegalStateException(DevCenterRedactor.redact(msg, secretsToRedact))
        }

        val accessToken = json.optString("access_token").trim()
        require(accessToken.isNotEmpty()) { "Token response did not contain an access token." }
        val refreshToken = json.optString("refresh_token").trim().takeIf { it.isNotEmpty() }
        val expiresIn = json.optLong("expires_in", 3600L).coerceAtLeast(60L)
        return OAuthTokenExchangeResult(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresInSeconds = expiresIn
        )
    }

    companion object {
        const val ANALYTICS_READONLY_SCOPE = "https://www.googleapis.com/auth/analytics.readonly"
        const val GOOGLE_AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
        const val GOOGLE_TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
        const val OAUTH_REDIRECT_PATH = "/oauth2redirect"

        fun schemeForClientId(clientId: String): String {
            val clean = clientId.trim()
            if (!DevCenterValidators.isValidGoogleOAuthClientId(clean)) {
                return "duit-dev-oauth-unconfigured"
            }
            val prefix = clean.removeSuffix(".apps.googleusercontent.com")
            return "com.googleusercontent.apps.$prefix"
        }

        fun redirectUriForClientId(clientId: String): String =
            "${schemeForClientId(clientId)}:$OAUTH_REDIRECT_PATH"

        private fun buildFormBody(vararg pairs: Pair<String, String>): String =
            pairs.joinToString("&") { (k, v) -> "${urlEncode(k)}=${urlEncode(v)}" }

        private fun urlEncode(value: String): String =
            URLEncoder.encode(value, Charsets.UTF_8.name())
    }
}
