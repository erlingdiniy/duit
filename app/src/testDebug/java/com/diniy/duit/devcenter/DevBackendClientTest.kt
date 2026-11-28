package com.diniy.duit.devcenter

import android.app.Application
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import com.diniy.duit.security.BiometricAuthGateway
import com.diniy.duit.security.BiometricAuthResult
import com.diniy.duit.security.BiometricAvailability
import com.diniy.duit.security.PinHashEngine
import com.diniy.duit.security.SecretProtector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DevBackendClientTest {

    private class MemoryStorage(initial: DevCenterVaultState = DevCenterVaultState()) : DevCenterStorage {
        private val flow = MutableStateFlow(initial)
        override fun observe(): Flow<DevCenterVaultState> = flow
        override suspend fun read(): DevCenterVaultState = flow.value
        override suspend fun update(transform: (DevCenterVaultState) -> DevCenterVaultState) {
            flow.value = transform(flow.value)
        }
    }

    private class FakeProtector : SecretProtector {
        override fun seal(plaintext: String): String = "sealed::$plaintext"
        override fun unseal(ciphertext: String): String? =
            if (ciphertext.startsWith("sealed::")) ciphertext.removePrefix("sealed::") else null
    }

    private class JavaEcdsaKeyManager : DeviceSigningKeyManager {
        private var kp: KeyPair? = null
        override fun hasKey(): Boolean = kp != null
        override fun ensureKeyPair(): DeviceKeyMetadata {
            val pair = kp ?: KeyPairGenerator.getInstance("EC").apply {
                initialize(ECGenParameterSpec("secp256r1"))
            }.generateKeyPair().also { kp = it }
            return DeviceKeyMetadata(
                alias = "test-p256",
                publicKeySpkiBase64 = Base64.encodeToString(pair.public.encoded, Base64.NO_WRAP),
                hardwareBacked = true,
                attestationSummary = "Hardware TEE ECDSA P-256 (test)"
            )
        }
        override fun signChallengePayload(payload: String): String {
            val pair = requireNotNull(kp)
            val sig = Signature.getInstance("SHA256withECDSA").apply {
                initSign(pair.private)
                update(payload.toByteArray(Charsets.UTF_8))
            }.sign()
            return Base64.encodeToString(sig, Base64.NO_WRAP)
        }
        override fun deleteKey() {
            kp = null
        }
        fun verifySigned(payload: String, sigB64: String): Boolean {
            val pair = requireNotNull(kp)
            val sigBytes = Base64.decode(sigB64, Base64.NO_WRAP)
            return Signature.getInstance("SHA256withECDSA").apply {
                initVerify(pair.public)
                update(payload.toByteArray(Charsets.UTF_8))
            }.verify(sigBytes)
        }
    }

    private class FakeBiometric : BiometricAuthGateway {
        override fun checkAvailability(): BiometricAvailability = BiometricAvailability.AVAILABLE
        override fun authenticate(
            activity: androidx.fragment.app.FragmentActivity,
            title: String,
            subtitle: String,
            negativeButtonText: String,
            onResult: (BiometricAuthResult) -> Unit
        ) = Unit
    }

    @Test
    fun enroll_refreshRotation_silentPop_stepUp_andRevocation() = runTest {
        val keyMgr = JavaEcdsaKeyManager()
        val vault = DevCenterVault(MemoryStorage(), FakeProtector(), PinHashEngine(FakeProtector(), iterations = 1_000))
        var now = 1_760_000_000_000L
        var forceRevokeNext = false
        var refreshShouldFail = false
        var popChallengeIssued = false
        var stepUpActionVerified = ""

        val transport = object : DevHttpTransport {
            override suspend fun request(method: String, url: String, headers: Map<String, String>, body: String?): DevHttpResponse =
                DevHttpResponse(404, "{}")

            override suspend fun requestBackend(method: String, url: String, headers: Map<String, String>, body: String?): DevHttpResponse {
                val reqJson = body?.let(::JSONObject) ?: JSONObject()
                return when {
                    url.endsWith("/v1/auth/challenge") -> {
                        val purpose = reqJson.getString("purpose")
                        if (purpose == "token") popChallengeIssued = true
                        DevHttpResponse(
                            200,
                            JSONObject()
                                .put("challengeId", "chg_$purpose")
                                .put("nonce", "nonce_123")
                                .put("purpose", purpose)
                                .put("expiresAtMs", now + 120_000L)
                                .toString()
                        )
                    }
                    url.endsWith("/v1/auth/enroll") -> {
                        val devId = reqJson.getString("deviceId")
                        val sig = reqJson.getString("signature")
                        assertTrue(keyMgr.verifySigned("enroll:chg_enroll:nonce_123:$devId", sig))
                        DevHttpResponse(
                            200,
                            JSONObject()
                                .put("ok", true)
                                .put("deviceId", devId)
                                .put("label", reqJson.getString("label"))
                                .put("accessToken", "jwt_1")
                                .put("accessTokenExpiresAtMs", now + 60_000L)
                                .put("refreshToken", "drc_ref_1")
                                .put("refreshTokenExpiresAtMs", now + 86_400_000L)
                                .toString()
                        )
                    }
                    url.endsWith("/v1/auth/refresh") -> {
                        if (refreshShouldFail) {
                            DevHttpResponse(401, """{"error":"Refresh session expired"}""")
                        } else {
                            DevHttpResponse(
                                200,
                                JSONObject()
                                    .put("ok", true)
                                    .put("accessToken", "jwt_2")
                                    .put("accessTokenExpiresAtMs", now + 900_000L)
                                    .put("refreshToken", "drc_ref_2")
                                    .put("refreshTokenExpiresAtMs", now + 86_400_000L)
                                    .toString()
                            )
                        }
                    }
                    url.endsWith("/v1/auth/token") -> {
                        val devId = reqJson.getString("deviceId")
                        val sig = reqJson.getString("signature")
                        assertTrue(keyMgr.verifySigned("token:chg_token:nonce_123:$devId", sig))
                        DevHttpResponse(
                            200,
                            JSONObject()
                                .put("ok", true)
                                .put("accessToken", "jwt_pop_3")
                                .put("accessTokenExpiresAtMs", now + 900_000L)
                                .put("refreshToken", "drc_ref_3")
                                .put("refreshTokenExpiresAtMs", now + 86_400_000L)
                                .toString()
                        )
                    }
                    url.endsWith("/v1/admin/kill-switches") -> {
                        val devId = vault.getState().enrolledDeviceId
                        val sig = reqJson.getString("signature")
                        val action = "kill_switch:${reqJson.getString("target")}:${reqJson.getBoolean("enabled")}"
                        assertTrue(keyMgr.verifySigned("step_up:chg_step_up:nonce_123:$devId:$action", sig))
                        stepUpActionVerified = action
                        DevHttpResponse(200, """{"ok":true,"killSwitches":{"customBroadcastsEnabled":false,"releaseNotificationsEnabled":true}}""")
                    }
                    url.startsWith("https://mc.example.dev/v1/admin/") -> {
                        if (forceRevokeNext) {
                            DevHttpResponse(403, """{"error":"Device access revoked."}""")
                        } else {
                            DevHttpResponse(200, """{"ok":true}""")
                        }
                    }
                    else -> DevHttpResponse(404, "{}")
                }
            }
        }

        val client = DevBackendClient(vault, keyMgr, transport, nowMs = { now })
        val enrollRes = client.enrollDevice("https://mc.example.dev", "DUIT-ABCD-EFGH", "Owner Phone")
        assertTrue(enrollRes.isSuccess)
        assertTrue(vault.getState().isBackendEnrolled)
        assertEquals("jwt_1", client.ensureValidAccessToken())

        // Advance time within refresh window -> rotates token cleanly
        now += 30_000L
        assertEquals("jwt_2", client.ensureValidAccessToken())
        assertEquals("drc_ref_2", vault.readBackendRefreshToken())

        // Force expired refresh -> silently falls back to hardware PoP challenge
        now += 900_000L
        refreshShouldFail = true
        assertEquals("jwt_pop_3", client.ensureValidAccessToken())
        assertTrue(popChallengeIssued)

        // Step-up kill switch requires fresh ECDSA signature
        val ks = client.toggleKillSwitch(KillSwitchTarget.CUSTOM_BROADCAST, enabled = false)
        assertEquals(false, ks.customBroadcastEnabled)
        assertEquals("kill_switch:custom:false", stepUpActionVerified)

        // Server revocation clears enrollment immediately
        forceRevokeNext = true
        val revokedThrown = runCatching { client.authorizedRequest("GET", "/v1/admin/devices") }.exceptionOrNull()
        assertTrue(revokedThrown is DeviceRevokedException)
        assertFalse(vault.getState().isBackendEnrolled)
    }

    @Test
    fun viewModel_zeroConfigRefreshAll_loadsAll11ModulesWithoutPatOrOAuth() = runTest {
        val keyMgr = JavaEcdsaKeyManager()
        val vault = DevCenterVault(MemoryStorage(), FakeProtector(), PinHashEngine(FakeProtector(), iterations = 1_000))
        val now = 1_760_000_000_000L

        val transport = object : DevHttpTransport {
            override suspend fun request(method: String, url: String, headers: Map<String, String>, body: String?) = DevHttpResponse(404, "{}")
            override suspend fun requestBackend(method: String, url: String, headers: Map<String, String>, body: String?): DevHttpResponse {
                return when {
                    url.endsWith("/v1/auth/challenge") -> DevHttpResponse(200, """{"challengeId":"chg_1","nonce":"n_1","expiresAtMs":${now + 60_000}}""")
                    url.endsWith("/v1/auth/enroll") -> DevHttpResponse(200, """{"ok":true,"accessToken":"jwt_x","accessTokenExpiresAtMs":${now + 900_000},"refreshToken":"drc_x","refreshTokenExpiresAtMs":${now + 86_400_000}}""")
                    url.contains("/v1/admin/overview") -> DevHttpResponse(
                        200,
                        JSONObject()
                            .put("syncedAtMs", now)
                            .put("ga4", JSONObject().put("status", "ok").put("syncedAt", now).put("payload", JSONObject().put("activeUsers", 42).put("newUsers", 12).put("dau", 9).put("wau", 24).put("mau", 42).put("growthSeries", JSONArray().put(JSONObject().put("date", "20261128").put("activeUsers", 9).put("newUsers", 2))).put("exceptions", JSONObject().put("crashFreeObservedPercent", 99.5))))
                            .put("remoteConfig", JSONObject().put("status", "ok").put("syncedAt", now).put("payload", JSONObject().put("etag", "e-1").put("versionNumber", "5").put("parameters", JSONArray().put(JSONObject().put("key", "ui_maintenance_banner_enabled").put("valueType", "BOOLEAN").put("defaultValue", "false")))))
                            .put("releases", JSONObject().put("ok", true).put("rateRemaining", 4800).put("releases", JSONArray().put(JSONObject().put("tag", "v11.0.0").put("channel", "stable").put("repository", "erlingdiniy/duit-beta").put("versionCode", 11_000_000).put("downloadCount", 55))))
                            .put("killSwitches", JSONObject().put("customBroadcastsEnabled", true).put("releaseNotificationsEnabled", true))
                            .toString()
                    )
                    url.endsWith("/v1/admin/feedback") -> DevHttpResponse(
                        200,
                        """{"items":[{"id":"fb_1","category":"Bug Report","message":"Widget text clipped","contactEmail":"u@x.dev","appVersion":"1.3.0","status":"pending","priority":"high","isUnread":true,"createdAt":$now}],"summary":{"unreadCount":1,"openBugCount":1,"openFeatureCount":0,"spamCount":0}}"""
                    )
                    url.endsWith("/v1/admin/notifications/state") -> DevHttpResponse(200, """{"templates":[],"drafts":[],"scheduledBroadcasts":[],"history":[],"workflowRuns":[],"killSwitches":{"customBroadcastsEnabled":true,"releaseNotificationsEnabled":true}}""")
                    url.contains("/v1/admin/github/overview") -> DevHttpResponse(200, """{"repository":{"full_name":"erlingdiniy/duit-beta","private":false,"stargazers_count":3,"forks_count":1,"watchers_count":3,"open_issues_count":1,"default_branch":"main","html_url":"https://github.com/erlingdiniy/duit-beta"},"issues":[],"pullRequests":[],"commits":[],"workflows":[],"rateRemaining":4800}""")
                    url.endsWith("/v1/admin/infra") -> DevHttpResponse(200, """{"checkedAtMs":$now,"githubApi":{"status":"verified_remote","authMode":"app","remaining":4800,"limit":5000},"d1Database":{"feedbackRows":1,"activeDevices":1},"ga4Sync":{"status":"cached_sync","syncedAtMs":$now},"remoteConfigSync":{"status":"cached_sync","syncedAtMs":$now}}""")
                    url.endsWith("/v1/admin/audit-logs") -> DevHttpResponse(200, """{"items":[{"id":"a1","timestamp":$now,"actor":"developer","category":"auth","action":"device.enrolled","severity":"ok","summary":"Enrolled."}]}""")
                    url.endsWith("/v1/admin/devices") -> DevHttpResponse(200, """{"currentDeviceId":"d1","devices":[{"device_id":"d1","label":"Owner Phone","key_algorithm":"ECDSA_P256_SHA256","hardware_backed":1}]}""")
                    else -> DevHttpResponse(404, "{}")
                }
            }
        }

        val backendClient = DevBackendClient(vault, keyMgr, transport, nowMs = { now })
        val enrollResult = backendClient.enrollDevice("https://mc.example.dev", "DUIT-ABCD-EFGH", "Owner Phone")
        assertTrue(enrollResult.isSuccess)

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val auth = DevCenterAuthManager(vault, FakeBiometric(), scope)
        val vm = DevCenterViewModel(
            vault = vault,
            githubClient = DevCenterGitHubClient(transport),
            feedbackClient = DevCenterFeedbackClient(transport),
            authManager = auth,
            scope = scope,
            nowMs = { now },
            backendClient = backendClient
        )

        vm.refreshAll()
        val state = vm.uiState.value
        assertEquals(ZeroConfigBackendMode.CONNECTED, state.backendMode)
        assertTrue(state.dashboard.health.connected)
        assertTrue(state.analyticsState is AnalyticsSourceState.Ready)
        assertEquals(42L, (state.analyticsState as AnalyticsSourceState.Ready).snapshot.totalUsers)
        assertTrue(state.feedbackState is FeedbackSourceState.Ready)
        assertEquals(1, state.unreadFeedbackCount)
        assertTrue(state.remoteConfigState is RemoteConfigSourceState.Ready)
        assertEquals("v11.0.0", state.dashboard.latestStable?.tag)
        assertTrue(state.infraSnapshot.services.isNotEmpty())
        assertTrue(state.enrolledDevices.isNotEmpty())
        assertTrue(state.combinedAuditLog.isNotEmpty())

        // Verify legacy migration keeps Vault clean without throwing
        val app = ApplicationProvider.getApplicationContext<Application>()
        vault.migrateLegacyPrivateEditionIfNeeded(app)
        assertTrue(vault.getState().unifiedMigrated)
    }
}
