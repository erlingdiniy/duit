package com.diniy.duit.devcenter

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DevCenterAnalyticsClientTest {

    @Test
    fun returnsUnavailableWithoutFabricatingWhenCredentialsMissing() = runBlocking {
        var requests = 0
        val client = DevCenterAnalyticsClient(object : DevHttpTransport {
            override suspend fun request(
                method: String,
                url: String,
                headers: Map<String, String>,
                body: String?
            ): DevHttpResponse {
                requests += 1
                return DevHttpResponse(200, "{}")
            }
        })

        val res = client.loadAnalytics(
            propertyId = "",
            analyticsAccessToken = null,
            dateRange = AnalyticsDateRange.THIRTY_DAYS,
            syncedAtIso = "2026-11-27T04:00:00Z"
        )
        assertTrue(res is AnalyticsSourceState.Unavailable)
        assertEquals(0, requests)
    }

    @Test
    fun parsesBatchReportsCrashesThemesWidgetsAndCachesAccurately() = runBlocking {
        val urlsCalled = mutableListOf<String>()
        val client = DevCenterAnalyticsClient(object : DevHttpTransport {
            override suspend fun request(
                method: String,
                url: String,
                headers: Map<String, String>,
                body: String?
            ): DevHttpResponse {
                urlsCalled += url
                val payload = body.orEmpty()
                val json = when {
                    url.endsWith(":runRealtimeReport") && payload.contains("eventName") -> """
                        {"rows":[{"dimensionValues":[{"value":"feature_used"}],"metricValues":[{"value":"9"}]}]}
                    """.trimIndent()
                    url.endsWith(":runRealtimeReport") -> """
                        {"rows":[{"metricValues":[{"value":"4"},{"value":"12"}]}]}
                    """.trimIndent()
                    payload.contains("userEngagementDuration") -> """
                        {
                          "reports": [
                            {
                              "rows": [
                                {"dimensionValues":[{"value":"20261125"}],"metricValues":[{"value":"40"},{"value":"15"},{"value":"2400"},{"value":"60"}]},
                                {"dimensionValues":[{"value":"20261126"}],"metricValues":[{"value":"60"},{"value":"25"},{"value":"3600"},{"value":"90"}]}
                              ],
                              "propertyQuota": {
                                "tokensPerHour": {"remaining": 39950},
                                "tokensPerDay": {"remaining": 199800}
                              }
                            },
                            {"rows":[{"metricValues":[{"value":"140"},{"value":"100"},{"value":"40"},{"value":"150"}]}]},
                            {"rows":[{"metricValues":[{"value":"25"}]}]},
                            {"rows":[{"metricValues":[{"value":"65"}]}]},
                            {"rows":[{"metricValues":[{"value":"95"}]}]}
                          ]
                        }
                    """.trimIndent()
                    payload.contains("deviceModel") && !payload.contains("app_exception") -> """
                        {
                          "reports": [
                            {"rows":[{"dimensionValues":[{"value":"Malaysia"}],"metricValues":[{"value":"88"},{"value":"130"}]}]},
                            {"rows":[{"dimensionValues":[{"value":"Android 15"}],"metricValues":[{"value":"70"},{"value":"105"}]}]},
                            {"rows":[{"dimensionValues":[{"value":"Pixel 8"}],"metricValues":[{"value":"35"},{"value":"50"}]}]},
                            {"rows":[{"dimensionValues":[{"value":"1.3.0"}],"metricValues":[{"value":"82"},{"value":"120"}]}]},
                            {"rows":[{"dimensionValues":[{"value":"stable"}],"metricValues":[{"value":"85"},{"value":"125"}]}]}
                          ]
                        }
                    """.trimIndent()
                    payload.contains("theme_selected") -> """
                        {
                          "reports": [
                            {"rows":[
                              {"dimensionValues":[{"value":"home"}],"metricValues":[{"value":"120"},{"value":"80"}]},
                              {"dimensionValues":[{"value":"widgets"}],"metricValues":[{"value":"19"},{"value":"14"}]}
                            ]},
                            {"rows":[{"dimensionValues":[{"value":"liquid_glass"}],"metricValues":[{"value":"44"},{"value":"39"}]}]},
                            {"rows":[{"dimensionValues":[{"value":"app_updated"}],"metricValues":[{"value":"11"},{"value":"11"}]}]},
                            {"rows":[{"dimensionValues":[{"value":"1.3.0"}],"metricValues":[{"value":"3"},{"value":"2"}]}]},
                            {"rows":[{"dimensionValues":[{"value":"Android 15"}],"metricValues":[{"value":"3"},{"value":"2"}]}]}
                          ]
                        }
                    """.trimIndent()
                    else -> """
                        {
                          "reports": [
                            {"rows":[{"dimensionValues":[{"value":"Pixel 8"}],"metricValues":[{"value":"2"},{"value":"1"}]}]}
                          ]
                        }
                    """.trimIndent()
                }
                return DevHttpResponse(200, json)
            }
        })

        val res = client.loadAnalytics(
            propertyId = "565972794",
            analyticsAccessToken = "ya29.fake-readonly-token-12345",
            dateRange = AnalyticsDateRange.THIRTY_DAYS,
            syncedAtIso = "2026-11-27T04:00:00Z"
        )

        assertTrue(res is AnalyticsSourceState.Ready)
        val snap = (res as AnalyticsSourceState.Ready).snapshot
        assertEquals(140L, snap.totalUsers)
        assertEquals(100L, snap.activeUsers)
        assertEquals(40L, snap.newUsers)
        assertEquals(60L, snap.returningUsers)
        assertEquals(60, snap.returningRatePercent)
        assertEquals(25L, snap.dau)
        assertEquals(65L, snap.wau)
        assertEquals(95L, snap.mau)
        assertEquals(150L, snap.sessions)
        assertEquals("Malaysia", snap.countries.first().label)
        assertEquals("Android 15", snap.androidVersions.first().label)
        assertEquals("Pixel 8", snap.deviceModels.first().label)
        assertEquals("1.3.0", snap.appVersions.first().label)
        assertEquals("stable", snap.releaseChannels.first().label)
        assertEquals("liquid_glass", snap.themePopularity.first().label)
        assertEquals(19L, snap.widgetUsageCount)
        assertEquals(14L, snap.widgetUsers)
        assertEquals(11L, snap.updateAdoptions)
        assertEquals(98.0, snap.crashFreeUsersPercent!!, 0.01)
        assertEquals(98.0, snap.crashFreeSessionsPercent!!, 0.01)
        assertEquals(2L, snap.crashAffectedUsers)
        assertEquals(3L, snap.crashEventCount)
        assertEquals(39950, snap.quotaHourlyRemaining)
        assertEquals(199800, snap.quotaDailyRemaining)
        assertEquals(4, urlsCalled.count { it.endsWith(":batchRunReports") })
        assertEquals(2, urlsCalled.count { it.endsWith(":runRealtimeReport") })

        val cachedJson = DevCenterAnalyticsClient.encodeSnapshot(snap)
        val decoded = DevCenterAnalyticsClient.decodeCachedSnapshot(cachedJson)
        assertNotNull(decoded)
        assertTrue(decoded!!.isCached)
        assertEquals(snap.activeUsers, decoded.activeUsers)
        assertEquals("Malaysia", decoded.countries.first().label)
        assertEquals(98.0, decoded.crashFreeUsersPercent!!, 0.01)
    }

    @Test
    fun returnsEmptyWhenGa4HasZeroRows_andRedactsTokenOnHttpError() = runBlocking {
        val emptyClient = DevCenterAnalyticsClient(object : DevHttpTransport {
            override suspend fun request(method: String, url: String, headers: Map<String, String>, body: String?) =
                DevHttpResponse(200, """{"reports": []}""")
        })
        val emptyState = emptyClient.loadAnalytics("123456789", "ya29.token", AnalyticsDateRange.NINETY_DAYS, "2026-11-27T04:00:00Z")
        assertTrue(emptyState is AnalyticsSourceState.Empty)

        val secret = "ya29.SuperSecretTokenValue999"
        val errClient = DevCenterAnalyticsClient(object : DevHttpTransport {
            override suspend fun request(method: String, url: String, headers: Map<String, String>, body: String?) =
                throw IllegalStateException("Auth failed for Bearer $secret")
        })
        val errState = errClient.loadAnalytics("123456789", secret, AnalyticsDateRange.THIRTY_DAYS, "2026-11-27T04:00:00Z")
        assertTrue(errState is AnalyticsSourceState.Error)
        assertFalse((errState as AnalyticsSourceState.Error).message.contains(secret))
    }

    @Test
    fun validatorAndTransportHostAllowlistEnforceSecurity() {
        assertTrue(DevCenterValidators.isValidGa4PropertyId("123456789"))
        assertFalse(DevCenterValidators.isValidGa4PropertyId("12345/../etc"))
        assertFalse(DevCenterValidators.isValidGa4PropertyId("abc"))

        assertTrue("analyticsdata.googleapis.com" in HttpsUrlConnectionTransport.ALLOWED_HOSTS)
        assertTrue("firebaseremoteconfig.googleapis.com" in HttpsUrlConnectionTransport.ALLOWED_HOSTS)
        assertTrue("fcm.googleapis.com" in HttpsUrlConnectionTransport.ALLOWED_HOSTS)
        var blocked = false
        try {
            HttpsUrlConnectionTransport.validateAllowlistedHttpsUrl("https://evil.example/v1beta/properties/123:runReport")
        } catch (_: SecurityException) {
            blocked = true
        }
        assertTrue(blocked)
    }
}
