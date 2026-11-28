package com.diniy.duit.devcenter

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DevCenterGitHubManagerTest {

    @Test
    fun fetchesOverviewIssuesPrsCommitsJobsAndFlagsPublicPrivateRepoAlert() = runTest {
        val manager = DevCenterGitHubManager(object : DevHttpTransport {
            override suspend fun request(method: String, url: String, headers: Map<String, String>, body: String?): DevHttpResponse {
                return when {
                    url.endsWith("/repos/erlingdiniy/duit-developer-console") -> DevHttpResponse(
                        200,
                        """{"full_name":"erlingdiniy/duit-developer-console","private":false,"visibility":"public","stargazers_count":0,"forks_count":0,"subscribers_count":1,"open_issues_count":1,"default_branch":"main"}"""
                    )
                    url.contains("/issues") && method == "GET" -> DevHttpResponse(
                        200,
                        """[
                          {"number":1,"title":"Bug item","state":"open","user":{"login":"erlingdiniy"},"labels":[{"name":"bug"}],"comments":1,"html_url":"https://github.com/a/b/issues/1"},
                          {"number":2,"title":"PR disguised","state":"open","pull_request":{"url":"x"},"user":{"login":"erlingdiniy"}}
                        ]"""
                    )
                    url.contains("/pulls") -> DevHttpResponse(
                        200,
                        """[{"number":2,"title":"Sync v1.3.0","state":"closed","draft":false,"user":{"login":"erlingdiniy"},"html_url":"https://github.com/a/b/pull/2"}]"""
                    )
                    url.contains("/commits") -> DevHttpResponse(
                        200,
                        """[{"sha":"82dcabdce3c32","commit":{"message":"Release v1.3.0\n\nBody","author":{"name":"Diniy","date":"2026-11-27T09:53:00Z"}},"html_url":"https://github.com/a/b/commit/82dcabd"}]"""
                    )
                    url.endsWith("/rate_limit") -> DevHttpResponse(
                        200,
                        """{"resources":{"core":{"limit":5000,"remaining":4920,"reset":1795776000}}}"""
                    )
                    url.contains("/actions/runs/555/jobs") -> DevHttpResponse(
                        200,
                        """{"jobs":[{"id":1,"name":"build","status":"completed","conclusion":"failure","html_url":"https://github.com/j/1","steps":[{"number":3,"name":"Run tests","conclusion":"failure"}]}]}"""
                    )
                    url.endsWith("/issues") && method == "POST" -> DevHttpResponse(
                        201,
                        """{"number":15,"title":"New issue","state":"open","user":{"login":"erlingdiniy"},"labels":[{"name":"bug"}],"comments":0,"html_url":"https://github.com/erlingdiniy/duit/issues/15"}"""
                    )
                    url.endsWith("/issues/15") && method == "PATCH" -> DevHttpResponse(
                        200,
                        """{"number":15,"title":"New issue","state":"closed","user":{"login":"erlingdiniy"},"labels":[],"comments":0,"html_url":"https://github.com/erlingdiniy/duit/issues/15"}"""
                    )
                    else -> DevHttpResponse(403, """{"message":"Resource not accessible by personal access token"}""")
                }
            }
        })

        val snap = manager.fetchRepoSnapshot("github_pat_fake12345", "erlingdiniy/duit-developer-console")
        assertNotNull(snap.overview?.privacyAlert)
        assertEquals(1, snap.issues.size)
        assertEquals(1, snap.pullRequests.size)
        assertEquals("82dcabd", snap.commits.first().shaShort)
        assertEquals(4920, snap.rateLimitRemaining)

        val jobs = manager.fetchWorkflowJobs("github_pat_fake12345", "erlingdiniy/duit-beta", "555")
        assertTrue(jobs.isSuccess)
        assertEquals("Run tests", jobs.getOrThrow().first().failedSteps.single())

        val created = manager.createIssue("github_pat_fake12345", "erlingdiniy/duit", "New issue", "Body", listOf("bug"))
        assertEquals(15, created.getOrThrow().number)

        val closed = manager.updateIssueState("github_pat_fake12345", "erlingdiniy/duit", 15, open = false)
        assertEquals("closed", closed.getOrThrow().state)

        val commentForbidden = manager.addIssueComment("github_pat_fake12345", "erlingdiniy/duit", 15, "hello")
        assertTrue(commentForbidden.isFailure)
        assertTrue(commentForbidden.exceptionOrNull()!!.message!!.contains("HTTP 403"))
    }
}
