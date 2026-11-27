package com.diniy.duit.devcenter

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun parsesGa4RunReportResponsesAccurately() = runBlocking {
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
                    payload.contains("userEngagementDuration") -> """
                        {
                          "rows": [
                            {
                              "dimensionValues": [{"value": "20261125"}],
                              "metricValues": [{"value": "10"}, {"value": "4"}, {"value": "600"}]
                            },
                            {
                              "dimensionValues": [{"value": "20261126"}],
                              "metricValues": [{"value": "20"}, {"value": "6"}, {"value": "1200"}]
                            }
                          ]
                        }
                    """.trimIndent()
                    payload.contains("customEvent:feature") -> """
                        {
                          "rows": [
                            {
                              "dimensionValues": [{"value": "home"}],
                              "metricValues": [{"value": "42"}, {"value": "18"}]
                            },
                            {
                              "dimensionValues": [{"value": "wallets"}],
                              "metricValues": [{"value": "21"}, {"value": "14"}]
                            }
                          ]
                        }
                    """.trimIndent()
                    payload.contains("appVersion") -> """
                        {
                          "rows": [
                            {
                              "dimensionValues": [{"value": "1.2.0"}],
                              "metricValues": [{"value": "22"}, {"value": "40"}]
                            }
                          ]
                        }
                    """.trimIndent()
                    else -> """
                        {
                          "rows": [
                            {
                              "dimensionValues": [{"value": "app_updated"}],
                              "metricValues": [{"value": "7"}, {"value": "7"}]
                            }
                          ]
                        }
                    """.trimIndent()
                }
                return DevHttpResponse(200, json)
            }
        })

        val res = client.loadAnalytics(
            propertyId = "123456789",
            analyticsAccessToken = "ya29.fake-readonly-token-12345",
            dateRange = AnalyticsDateRange.SEVEN_DAYS,
            syncedAtIso = "2026-11-27T04:00:00Z"
        )

        assertTrue(res is AnalyticsSourceState.Ready)
        val snap = (res as AnalyticsSourceState.Ready).snapshot
        assertEquals(30L, snap.activeUsers)
        assertEquals(10L, snap.newUsers)
        assertEquals(66, snap.returningRatePercent)
        assertEquals(60L, snap.avgEngagementSeconds)
        assertEquals(2, snap.growthTrend.size)
        assertEquals("2026-11-25", snap.growthTrend.first().dateLabel)
        assertEquals("home", snap.popularFeatures.first().label)
        assertEquals(42L, snap.popularFeatures.first().count)
        assertEquals("1.2.0", snap.appVersions.first().label)
        assertEquals(7L, snap.updateAdoptions)
        assertEquals(4, urlsCalled.size)
        assertTrue(urlsCalled.all { it == "https://analyticsdata.googleapis.com/v1beta/properties/123456789:runReport" })
    }

    @Test
    fun returnsEmptyWhenGa4HasZeroRows_andRedactsTokenOnHttpError() = runBlocking {
        val emptyClient = DevCenterAnalyticsClient(object : DevHttpTransport {
            override suspend fun request(method: String, url: String, headers: Map<String, String>, body: String?) =
                DevHttpResponse(200, """{"rows": []}""")
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
        var blocked = false
        try {
            HttpsUrlConnectionTransport.validateAllowlistedHttpsUrl("https://evil.example/v1beta/properties/123:runReport")
        } catch (_: SecurityException) {
            blocked = true
        }
        assertTrue(blocked)
    }
}
