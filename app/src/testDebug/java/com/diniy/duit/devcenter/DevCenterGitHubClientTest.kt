package com.diniy.duit.devcenter

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class RecordingTransport(
    private val responder: (method: String, url: String, body: String?) -> DevHttpResponse
) : DevHttpTransport {
    val requests = mutableListOf<Triple<String, String, String?>>()
    override suspend fun request(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String?
    ): DevHttpResponse {
        requests += Triple(method, url, body)
        return responder(method, url, body)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DevCenterGitHubClientTest {

    @Test
    fun httpsAllowlist_blocksCleartextForeignHostsAndNonStandardPorts() {
        val blocked = listOf(
            "http://api.github.com/user",
            "https://evil.example/api",
            "https://user:pass@api.github.com/user",
            "https://api.github.com:8443/user"
        )
        for (url in blocked) {
            val result = runCatching { HttpsUrlConnectionTransport.validateAllowlistedHttpsUrl(url) }
            assertTrue("Expected SecurityException for $url", result.exceptionOrNull() is SecurityException)
        }
        HttpsUrlConnectionTransport.validateAllowlistedHttpsUrl("https://api.github.com/repos/erlingdiniy/duit-beta/releases")
        HttpsUrlConnectionTransport.validateAllowlistedHttpsUrl("https://sheets.googleapis.com/v4/spreadsheets/id/values/A1:F10")
    }

    @Test
    fun githubClient_parsesRealDownloadCountsWorkflowsAndMutatesSafely() = runTest {
        val secretPat = "github_pat_fakeTestToken123456"
        val transport = RecordingTransport { method, url, body ->
            when {
                url.endsWith("/user") -> DevHttpResponse(200, """{"login":"erlingdiniy"}""", mapOf("x-ratelimit-remaining" to "4980"))
                url.contains("/releases") -> DevHttpResponse(
                    200,
                    """
                    [
                      {
                        "tag_name": "v1.2.0",
                        "name": "duit. v1.2.0",
                        "prerelease": false,
                        "published_at": "2026-11-25T09:24:20Z",
                        "html_url": "https://github.com/erlingdiniy/duit-beta/releases/tag/v1.2.0",
                        "body": "- **Pure Liquid Glass Theme**: Light & Dark",
                        "assets": [{
                          "name": "duit-1.2.0.apk",
                          "size": 8450511,
                          "download_count": 42,
                          "browser_download_url": "https://github.com/erlingdiniy/duit-beta/releases/download/v1.2.0/duit-1.2.0.apk",
                          "digest": "sha256:e883b223504d684a"
                        }]
                      },
                      {
                        "tag_name": "v1.2.0-beta.2",
                        "name": "duit. 1.2.0-beta.2",
                        "prerelease": true,
                        "published_at": "2026-11-23T12:32:30Z",
                        "html_url": "https://github.com/erlingdiniy/duit-beta/releases/tag/v1.2.0-beta.2",
                        "body": "- Beta candidate",
                        "assets": [{
                          "name": "duit-1.2.0-beta.2.apk",
                          "size": 8400000,
                          "download_count": 17,
                          "browser_download_url": "https://github.com/erlingdiniy/duit-beta/releases/download/v1.2.0-beta.2/duit-1.2.0-beta.2.apk"
                        }]
                      }
                    ]
                    """.trimIndent()
                )
                url.contains("release-notifications.json") -> DevHttpResponse(
                    200,
                    """
                    {
                      "minExclusiveVersionCode": 1002000,
                      "releases": {
                        "v1.2.0": {
                          "tag": "v1.2.0",
                          "versionCode": 1002000,
                          "channel": "stable",
                          "status": "preseeded_skip",
                          "updatedAt": "2026-11-25T10:05:00Z",
                          "note": "Baseline floor"
                        }
                      }
                    }
                    """.trimIndent()
                )
                url.contains("/actions/runs") -> DevHttpResponse(
                    200,
                    """
                    {
                      "workflow_runs": [
                        {
                          "id": 9991,
                          "name": "Custom Developer Broadcast",
                          "display_title": "Dry run",
                          "status": "completed",
                          "conclusion": "success",
                          "created_at": "2026-11-26T04:00:00Z",
                          "html_url": "https://github.com/erlingdiniy/duit-developer-console/actions/runs/9991",
                          "head_branch": "main"
                        }
                      ]
                    }
                    """.trimIndent()
                )
                url.endsWith("/ENABLE_LIVE_FCM_DISPATCH") && method == "GET" ->
                    DevHttpResponse(200, """{"name":"ENABLE_LIVE_FCM_DISPATCH","value":"true"}""")
                url.endsWith("/ENABLE_CUSTOM_FCM_DISPATCH") && method == "GET" ->
                    DevHttpResponse(200, """{"name":"ENABLE_CUSTOM_FCM_DISPATCH","value":"true"}""")
                url.endsWith("/ENABLE_CUSTOM_FCM_DISPATCH") && method == "PATCH" ->
                    DevHttpResponse(204, "")
                url.endsWith("/custom-broadcast.yml/dispatches") && method == "POST" ->
                    DevHttpResponse(204, "")
                else -> DevHttpResponse(404, """{"message":"Not found $secretPat"}""")
            }
        }

        val client = DevCenterGitHubClient(transport)
        val snapshot = client.fetchDashboardSnapshot(secretPat, DEFAULT_DISTRIBUTION_REPO, DEFAULT_CONSOLE_REPO)
        assertTrue(snapshot.health.connected)
        assertEquals("erlingdiniy", snapshot.health.authenticatedUser)
        assertEquals("v1.2.0", snapshot.latestStable?.tag)
        assertEquals(42, snapshot.latestStable?.downloadCount)
        assertEquals("v1.2.0-beta.2", snapshot.latestBeta?.tag)
        assertEquals(59, snapshot.totalApkDownloads)
        assertEquals(true, snapshot.killSwitches.customBroadcastEnabled)
        assertEquals(true, snapshot.killSwitches.releaseAutomationEnabled)

        val dryReq = ValidatedBroadcastRequest(
            category = BroadcastCategory.GENERAL,
            audience = BroadcastAudience.BETA,
            route = BroadcastDestination.HOME,
            title = "Dry Run Test",
            body = "Testing general category dispatch to custom-broadcast.yml",
            broadcastId = "duit-custom-test-01",
            validateOnly = true,
            confirmationPhrase = "",
            reason = "Unit test"
        )
        val dispatchRes = client.dispatchCustomBroadcast(secretPat, DEFAULT_CONSOLE_REPO, dryReq)
        assertTrue(dispatchRes.isSuccess)
        val postBody = transport.requests.last { it.first == "POST" }.third!!
        assertTrue(postBody.contains(""""type":"general""""))
        assertTrue(postBody.contains(""""validate_only":true"""))

        val ksRes = client.updateKillSwitch(secretPat, KillSwitchTarget.CUSTOM_BROADCAST, DEFAULT_CONSOLE_REPO, false)
        assertEquals(false, ksRes.getOrNull())
        val patchBody = transport.requests.last { it.first == "PATCH" }.third!!
        assertTrue(patchBody.contains(""""value":"false""""))

        // Error redaction verification
        val errKs = client.updateKillSwitch(secretPat, KillSwitchTarget.RELEASE_AUTOMATION, DEFAULT_DISTRIBUTION_REPO, false)
        assertTrue(errKs.isFailure)
        assertFalse(errKs.exceptionOrNull()!!.message!!.contains(secretPat))

        // Confirm every network call operates independently of the local desktop server.
        assertTrue(transport.requests.isNotEmpty())
        val forbiddenLocalHosts = listOf("localhost", "127.0.0.1", "10.0.2.2", ":4180")
        for ((_, reqUrl, _) in transport.requests) {
            assertTrue("Expected direct GitHub API HTTPS URL, got $reqUrl", reqUrl.startsWith("https://api.github.com/"))
            for (bad in forbiddenLocalHosts) {
                assertFalse("Mobile client must never depend on local server host '$bad' ($reqUrl)", reqUrl.contains(bad, ignoreCase = true))
            }
        }
    }
}
