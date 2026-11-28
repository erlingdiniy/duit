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
class DevCenterRemoteConfigClientTest {

    @Test
    fun fetchesTemplateVersionsPublishesWithEtagAndRollsBack() = runTest {
        val calls = mutableListOf<Triple<String, String, String?>>()
        val client = DevCenterRemoteConfigClient(object : DevHttpTransport {
            override suspend fun request(
                method: String,
                url: String,
                headers: Map<String, String>,
                body: String?
            ): DevHttpResponse {
                calls += Triple(method, url, body)
                return when {
                    method == "GET" && url.endsWith("/remoteConfig") -> DevHttpResponse(
                        200,
                        """
                        {
                          "parameters": {
                            "maintenance_banner_enabled": {"defaultValue":{"value":"false"},"valueType":"BOOLEAN"},
                            "gradual_rollout_percent": {"defaultValue":{"value":"25"},"valueType":"NUMBER"}
                          },
                          "conditions": [{"name":"Beta Users","expression":"app.id == 'com.diniy.duit.beta'"}],
                          "version": {"versionNumber":"7"}
                        }
                        """.trimIndent(),
                        mapOf("etag" to "etag-v7")
                    )
                    method == "GET" && url.contains(":listVersions") -> DevHttpResponse(
                        200,
                        """{"versions":[{"versionNumber":"7","updateTime":"2026-11-27T08:00:00Z","updateOrigin":"REST_API"}]}"""
                    )
                    method == "PUT" && url.endsWith("/remoteConfig") -> {
                        assertEquals("etag-v7", headers["If-Match"])
                        DevHttpResponse(
                            200,
                            """{"parameters":{"maintenance_banner_enabled":{"defaultValue":{"value":"true"},"valueType":"BOOLEAN"}},"version":{"versionNumber":"8"}}""",
                            mapOf("etag" to "etag-v8")
                        )
                    }
                    method == "POST" && url.endsWith(":rollback") -> DevHttpResponse(
                        200,
                        """{"parameters":{"maintenance_banner_enabled":{"defaultValue":{"value":"false"},"valueType":"BOOLEAN"}},"version":{"versionNumber":"9"}}""",
                        mapOf("etag" to "etag-v9")
                    )
                    else -> DevHttpResponse(404, "{}")
                }
            }
        })

        val fetched = client.fetchTemplateAndVersions("duit-beta", "2026-11-27T10:00:00Z", true) { "ya29.rcToken" }
        assertTrue(fetched is RemoteConfigSourceState.Ready)
        val ready = fetched as RemoteConfigSourceState.Ready
        assertEquals("etag-v7", ready.etag)
        assertEquals("7", ready.versionNumber)
        assertEquals(2, ready.parameters.size)
        assertEquals(1, ready.versions.size)

        val published = client.publishTemplate(
            projectId = "duit-beta",
            etag = ready.etag,
            parameters = listOf(RemoteConfigParam("maintenance_banner_enabled", "BOOLEAN", "true")),
            conditions = ready.conditions,
            description = "Enable notice",
            syncedAtIso = "2026-11-27T10:05:00Z"
        ) { "ya29.rcToken" }
        assertTrue(published.isSuccess)
        assertEquals("8", published.getOrThrow().versionNumber)

        val rolled = client.rollbackToVersion("duit-beta", "7", "etag-v8", "2026-11-27T10:06:00Z") { "ya29.rcToken" }
        assertTrue(rolled.isSuccess)
        assertEquals("9", rolled.getOrThrow().versionNumber)
    }

    @Test
    fun blocksUnsafeFinancialKeysAndSupportsEmergencyDisable() = runTest {
        val client = DevCenterRemoteConfigClient(object : DevHttpTransport {
            override suspend fun request(method: String, url: String, headers: Map<String, String>, body: String?) =
                DevHttpResponse(200, "{}")
        })

        val blocked = runCatching {
            client.publishTemplate(
                projectId = "duit-beta",
                etag = "e1",
                parameters = listOf(RemoteConfigParam("disable_wallets_access", "BOOLEAN", "true")),
                conditions = emptyList(),
                description = "bad",
                syncedAtIso = "2026-11-27T10:00:00Z"
            ) { "ya29.rcToken" }
        }
        assertTrue(blocked.exceptionOrNull() is SecurityException)

        val disabled = client.buildEmergencyDisableParameters(emptyList())
        assertEquals("false", disabled.first { it.key == "maintenance_banner_enabled" }.defaultValue)
        assertEquals("0", disabled.first { it.key == "gradual_rollout_percent" }.defaultValue)
    }
}
