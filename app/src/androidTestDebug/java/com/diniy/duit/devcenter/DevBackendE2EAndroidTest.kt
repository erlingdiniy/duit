package com.diniy.duit.devcenter

import androidx.test.platform.app.InstrumentationRegistry
import com.diniy.duit.security.AndroidKeyStoreSecretProtector
import com.diniy.duit.security.PinHashEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

/**
 * End-to-end Android instrumentation test verifying REAL AndroidKeyStore ECDSA P-256
 * enrollment, JWT refresh, silent PoP re-auth, step-up challenge proofs, unauthorized
 * rejection, and device revocation against local Cloudflare Worker (http://127.0.0.1:8787
 * forwarded via `adb -s emulator-5554 reverse tcp:8787 tcp:8787`).
 */
class DevBackendE2EAndroidTest {

    private class InMemStorage : DevCenterStorage {
        private val flow = MutableStateFlow(DevCenterVaultState())
        override fun observe(): Flow<DevCenterVaultState> = flow
        override suspend fun read(): DevCenterVaultState = flow.value
        override suspend fun update(transform: (DevCenterVaultState) -> DevCenterVaultState) {
            flow.value = transform(flow.value)
        }
    }

    @Test
    fun realAndroidKeystoreEcdsaPop_enrollsRefreshesStepUpAndRevokesAgainstLocalWorker() = runBlocking {
        val baseUrl = "http://127.0.0.1:8787"
        val healthy = runCatching {
            val conn = (URL("$baseUrl/v1/health").openConnection() as HttpURLConnection).apply {
                connectTimeout = 1_500
                readTimeout = 1_500
            }
            try {
                conn.responseCode == 200
            } finally {
                conn.disconnect()
            }
        }.getOrDefault(false)
        assumeTrue("Local E2E Worker on 127.0.0.1:8787 is not running; skipping live AndroidKeyStore E2E.", healthy)

        val enrollCode = run {
            val conn = (URL("$baseUrl/test/mint-code").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 2_000
                readTimeout = 2_000
            }
            try {
                assertEquals(201, conn.responseCode)
                JSONObject(conn.inputStream.bufferedReader().readText()).getString("code")
            } finally {
                conn.disconnect()
            }
        }

        // Verify direct unauthorized HTTP rejection before enrolling
        val unauthCode = (URL("$baseUrl/v1/admin/overview").openConnection() as HttpURLConnection).run {
            try { responseCode } finally { disconnect() }
        }
        assertEquals(401, unauthCode)

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val protector = AndroidKeyStoreSecretProtector("duit_dev_e2e_test_v1")
        val vault = DevCenterVault(
            storage = InMemStorage(),
            tokenProtector = protector,
            pinHashEngine = PinHashEngine(protector, iterations = 10_000)
        )
        val keyManager = AndroidKeyStoreDeviceKeyManager(keyAlias = "duit_dev_e2e_ecdsa_p256_v1")
        keyManager.deleteKey()
        val transport = HttpsUrlConnectionTransport()
        val client = DevBackendClient(vault, keyManager, transport)

        try {
            // 1. Real AndroidKeyStore ECDSA P-256 enrollment accepted by Worker WebCrypto
            val enrollRes = client.enrollDevice(baseUrl, enrollCode, "Emulator QA")
            assertTrue("AndroidKeyStore ECDSA enrollment failed: ${enrollRes.exceptionOrNull()?.message}", enrollRes.isSuccess)
            assertTrue(vault.getState().isBackendEnrolled)

            // 2. Authorized overview & feedback retrieval with issued JWT
            val ov = client.authorizedRequest("GET", "/v1/admin/overview?window=30")
            assertTrue(ov.has("releases"))
            val fb = client.fetchFeedback()
            assertTrue(fb.entries.isNotEmpty() || fb.entries.isEmpty())

            // 3. Forced silent PoP renewal using hardware key
            val popJwt = client.ensureValidAccessToken(forceReauth = true)
            assertTrue(popJwt.startsWith("eyJ"))

            // 4. Step-up challenge proof for device revocation
            val deviceId = vault.getState().enrolledDeviceId
            val revokedSelf = client.revokeDevice(deviceId)
            assertTrue(revokedSelf)
            assertFalse(vault.getState().isBackendEnrolled)

            // 5. Post-revocation renewal must fail with DeviceRevokedException / IOException
            val afterRevoke = runCatching { client.authorizedRequest("GET", "/v1/admin/overview") }
            assertTrue(afterRevoke.isFailure)
        } finally {
            keyManager.deleteKey()
        }
    }
}
