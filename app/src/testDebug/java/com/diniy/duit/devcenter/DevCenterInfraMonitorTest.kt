package com.diniy.duit.devcenter

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DevCenterInfraMonitorTest {

    @Test
    fun distinguishesVerifiedRemoteProbesFromLocalSdkChecksAndReadsRateLimits() = runTest {
        val monitor = DevCenterInfraMonitor(object : DevHttpTransport {
            override suspend fun request(method: String, url: String, headers: Map<String, String>, body: String?): DevHttpResponse {
                return when {
                    url.endsWith("/rate_limit") -> DevHttpResponse(
                        200,
                        """{"resources":{"core":{"limit":5000,"remaining":4890,"reset":1795776000}}}"""
                    )
                    url.contains("duit-developer-console") -> DevHttpResponse(200, """{"private":true}""")
                    else -> DevHttpResponse(404, "{}")
                }
            }

            override suspend fun probeGet(url: String): DevHttpResponse =
                DevHttpResponse(200, """{"ok":true,"service":"duit-feedback","version":1}""")
        })

        val snapshot = monitor.checkInfrastructure(
            vaultState = DevCenterVaultState(),
            pat = "github_pat_fake12345",
            feedbackUrl = "https://script.google.com/macros/s/fake123/exec",
            ga4State = AnalyticsSourceState.Unavailable("Not signed in", hasPropertyId = false, hasToken = false),
            remoteConfigState = RemoteConfigSourceState.Unavailable("Not signed in", false),
            integrity = InstalledApkIntegrity("com.diniy.duit.beta", "1.3.0-dev.1", 10030001L, "e26f", true, true, "OK")
        )

        val gh = snapshot.services.first { it.id == "github" }
        val fb = snapshot.services.first { it.id == "feedback" }
        val fcm = snapshot.services.first { it.id == "fcm" }
        assertEquals(InfraProbeKind.VERIFIED_REMOTE, gh.probeKind)
        assertEquals("healthy", gh.state)
        assertEquals(4890, snapshot.githubRateRemaining)
        assertEquals(InfraProbeKind.VERIFIED_REMOTE, fb.probeKind)
        assertEquals("healthy", fb.state)
        assertEquals(InfraProbeKind.LOCAL_SDK, fcm.probeKind)
        assertTrue(snapshot.services.any { it.id == "apk_integrity" && it.state == "healthy" })
    }
}
