package com.diniy.duit.devcenter

import android.net.Uri
import com.diniy.duit.security.SecretProtector
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.MessageDigest
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DevCenterAnalyticsOAuthTest {

    private val sampleClientId = "96681551048-sampleclient12345.apps.googleusercontent.com"

    private class FakeStorage : DevCenterStorage {
        private val flow = MutableStateFlow(DevCenterVaultState())
        override fun observe(): Flow<DevCenterVaultState> = flow
        override suspend fun read(): DevCenterVaultState = flow.value
        override suspend fun update(transform: (DevCenterVaultState) -> DevCenterVaultState) {
            flow.value = transform(flow.value)
        }
    }

    private class FakeProtector : SecretProtector {
        override fun seal(plaintext: String): String = "ENC[$plaintext]"
        override fun unseal(ciphertext: String): String? =
            ciphertext.takeIf { it.startsWith("ENC[") && it.endsWith("]") }
                ?.removePrefix("ENC[")
                ?.removeSuffix("]")
    }

    @Test
    fun generatesValidPkceS256SessionMultiScopeAndCustomSchemeRedirect() {
        val oauth = DevCenterAnalyticsOAuth(object : DevHttpTransport {
            override suspend fun request(
                method: String,
                url: String,
                headers: Map<String, String>,
                body: String?
            ) = DevHttpResponse(200, "{}")
        })

        val session = oauth.generatePkceSession(sampleClientId, nowMs = 1_700_000_000_000L)
        assertEquals(
            "com.googleusercontent.apps.96681551048-sampleclient12345:/oauth2redirect",
            session.redirectUri
        )
        val expectedChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(session.codeVerifier.toByteArray(Charsets.US_ASCII))
        )
        assertEquals(expectedChallenge, session.codeChallenge)

        val authUri = Uri.parse(oauth.buildAuthorizationUrl(session))
        assertEquals("https", authUri.scheme)
        assertEquals("accounts.google.com", authUri.host)
        assertEquals(DevCenterAnalyticsOAuth.ANALYTICS_READONLY_SCOPE, authUri.getQueryParameter("scope"))
        assertEquals("S256", authUri.getQueryParameter("code_challenge_method"))
        assertEquals("offline", authUri.getQueryParameter("access_type"))
        assertEquals("consent", authUri.getQueryParameter("prompt"))
        assertNull(authUri.getQueryParameter("client_secret"))

        val multiSession = oauth.generatePkceSession(
            sampleClientId,
            scopes = DevCenterAnalyticsOAuth.DEFAULT_MISSION_CONTROL_SCOPES,
            nowMs = 1_700_000_000_000L
        )
        val multiUri = Uri.parse(oauth.buildAuthorizationUrl(multiSession))
        val scopeStr = multiUri.getQueryParameter("scope").orEmpty()
        assertTrue(scopeStr.contains(DevCenterAnalyticsOAuth.ANALYTICS_READONLY_SCOPE))
        assertTrue(scopeStr.contains(DevCenterAnalyticsOAuth.SHEETS_READONLY_SCOPE))
        assertTrue(scopeStr.contains(DevCenterAnalyticsOAuth.REMOTE_CONFIG_SCOPE))
    }

    @Test
    fun revokesActiveOAuthToken() = runBlocking {
        var calledUrl = ""
        var calledBody = ""
        val oauth = DevCenterAnalyticsOAuth(object : DevHttpTransport {
            override suspend fun request(
                method: String,
                url: String,
                headers: Map<String, String>,
                body: String?
            ): DevHttpResponse {
                calledUrl = url
                calledBody = body.orEmpty()
                return DevHttpResponse(200, "{}")
            }
        })
        assertTrue(oauth.revokeToken("ya29.tokenToRevoke"))
        assertEquals(DevCenterAnalyticsOAuth.GOOGLE_REVOKE_ENDPOINT, calledUrl)
        assertTrue(calledBody.contains("token="))
    }

    @Test
    fun parsesCallbackAndRejectsStateMismatch() {
        val oauth = DevCenterAnalyticsOAuth(object : DevHttpTransport {
            override suspend fun request(
                method: String,
                url: String,
                headers: Map<String, String>,
                body: String?
            ) = DevHttpResponse(200, "{}")
        })

        val badState = oauth.parseCallbackUri(
            "com.googleusercontent.apps.test:/oauth2redirect?state=wrong&code=abc",
            expectedState = "expected-state"
        )
        assertTrue(badState is OAuthCallbackOutcome.Error)

        val cancelled = oauth.parseCallbackUri(
            "com.googleusercontent.apps.test:/oauth2redirect?error=access_denied",
            expectedState = "expected-state"
        )
        assertTrue(cancelled is OAuthCallbackOutcome.Error)

        val ok = oauth.parseCallbackUri(
            "com.googleusercontent.apps.test:/oauth2redirect?state=expected-state&code=4/authcode999",
            expectedState = "expected-state"
        )
        assertEquals(OAuthCallbackOutcome.AuthorizationCode("4/authcode999"), ok)
    }

    @Test
    fun exchangesAndRefreshesTokensWithoutClientSecretAndRetriesHttp401Once() = runBlocking {
        val recordedBodies = mutableListOf<String>()
        var apiCalls = 0
        val transport = object : DevHttpTransport {
            override suspend fun request(
                method: String,
                url: String,
                headers: Map<String, String>,
                body: String?
            ): DevHttpResponse {
                if (url == DevCenterAnalyticsOAuth.GOOGLE_TOKEN_ENDPOINT) {
                    recordedBodies += body.orEmpty()
                    return if (body.orEmpty().contains("grant_type=authorization_code")) {
                        DevHttpResponse(
                            200,
                            """{"access_token":"ya29.initialToken","refresh_token":"1//refresh999","expires_in":3600}"""
                        )
                    } else {
                        DevHttpResponse(
                            200,
                            """{"access_token":"ya29.refreshedToken","expires_in":3600}"""
                        )
                    }
                }
                apiCalls += 1
                val auth = headers["Authorization"].orEmpty()
                if (auth == "Bearer ya29.expiredToken") {
                    return DevHttpResponse(401, """{"error":"UNAUTHENTICATED"}""")
                }
                return when {
                    url.endsWith(":runRealtimeReport") ->
                        DevHttpResponse(200, """{"rows":[{"dimensionValues":[{"value":"feature_used"}],"metricValues":[{"value":"3"}]}]}""")
                    url.endsWith(":batchRunReports") ->
                        DevHttpResponse(200, """{"reports":[{},{"rows":[{"metricValues":[{"value":"5"},{"value":"5"},{"value":"1"},{"value":"8"}]}]},{},{},{}]}""")
                    else -> DevHttpResponse(200, "{}")
                }
            }
        }

        val oauth = DevCenterAnalyticsOAuth(transport)
        val exchanged = oauth.exchangeAuthorizationCode(
            clientId = sampleClientId,
            code = "4/code123",
            codeVerifier = "verifier123",
            redirectUri = DevCenterAnalyticsOAuth.redirectUriForClientId(sampleClientId)
        )
        assertEquals("ya29.initialToken", exchanged.accessToken)
        assertEquals("1//refresh999", exchanged.refreshToken)

        val refreshed = oauth.refreshAccessToken(sampleClientId, "1//refresh999")
        assertEquals("ya29.refreshedToken", refreshed.accessToken)
        assertNull(refreshed.refreshToken)

        recordedBodies.forEach { form ->
            assertFalse("Token request must never contain client_secret", form.contains("client_secret"))
        }

        var refreshInvocations = 0
        val analyticsClient = DevCenterAnalyticsClient(transport)
        val result = analyticsClient.loadAnalytics(
            propertyId = "565972794",
            analyticsAccessToken = null,
            dateRange = AnalyticsDateRange.SEVEN_DAYS,
            syncedAtIso = "2026-11-27T05:00:00Z",
            hasSavedCredentials = true,
            canSignInWithGoogle = true,
            tokenResolver = { force ->
                if (force) {
                    refreshInvocations += 1
                    "ya29.refreshedToken"
                } else {
                    "ya29.expiredToken"
                }
            }
        )
        assertEquals(1, refreshInvocations)
        assertTrue(apiCalls >= 2)
        assertTrue(result is AnalyticsSourceState.Ready)
        val snap = (result as AnalyticsSourceState.Ready).snapshot
        assertEquals(1, snap.realtimeEvents.size)
        assertEquals("feature_used", snap.realtimeEvents.first().label)
    }

    @Test
    fun vaultSealsAndClearsAnalyticsOAuthAndRefreshTokensSafely() = runBlocking {
        val vault = DevCenterVault(FakeStorage(), FakeProtector())
        vault.saveAnalyticsOAuthSession(
            oauthClientId = sampleClientId,
            accessToken = "ya29.accessSecretValue",
            refreshToken = "1//refreshSecretValue",
            expiresAtMs = 1_800_000_000_000L,
            propertyId = "565972794",
            grantedScopes = DevCenterAnalyticsOAuth.DEFAULT_MISSION_CONTROL_SCOPES.toSet()
        )
        val saved = vault.getState()
        assertEquals("oauth", saved.analyticsAuthMode)
        assertEquals("565972794", saved.ga4PropertyId)
        assertEquals(sampleClientId, saved.ga4OAuthClientId)
        assertEquals(3, saved.googleGrantedScopes.size)
        assertEquals("ya29.accessSecretValue", vault.readAnalyticsToken())
        assertEquals("1//refreshSecretValue", vault.readAnalyticsRefreshToken())
        assertNotNull(saved.encryptedAnalyticsRefreshToken)
        assertFalse(saved.encryptedAnalyticsRefreshToken == "1//refreshSecretValue")

        vault.updateRefreshedAnalyticsToken("ya29.newAccessToken", null, 1_800_003_600_000L)
        assertEquals("ya29.newAccessToken", vault.readAnalyticsToken())
        assertEquals("1//refreshSecretValue", vault.readAnalyticsRefreshToken())

        vault.clearAnalyticsSource()
        assertNull(vault.readAnalyticsToken())
        assertNull(vault.readAnalyticsRefreshToken())
        assertTrue(vault.getState().googleGrantedScopes.isEmpty())
        assertEquals("none", vault.getState().analyticsAuthMode)
    }

    @Test
    fun ga4ApiIsEnabledForMissionControl() {
        assertTrue(EXPERIMENTAL_GA4_API_ENABLED)
    }
}
