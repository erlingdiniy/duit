package com.diniy.duit.devcenter

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject

/**
 * Multi-repository GitHub management client (read overview, issues, PRs, commits, workflow jobs,
 * and least-privilege issue triage/commenting). Never performs destructive repository operations.
 */
class DevCenterGitHubManager(
    private val transport: DevHttpTransport,
    private val coreClient: DevCenterGitHubClient = DevCenterGitHubClient(transport)
) {
    private fun authHeaders(pat: String): Map<String, String> = buildMap {
        put("Accept", "application/vnd.github+json")
        put("X-GitHub-Api-Version", "2022-11-28")
        put("User-Agent", "duit-developer-center-android/2.0")
        if (pat.isNotBlank()) {
            put("Authorization", "Bearer ${pat.trim()}")
        }
    }

    suspend fun fetchRepoSnapshot(pat: String, repoSlug: String): GitHubRepoSnapshot = coroutineScope {
        val cleanSlug = repoSlug.trim()
        if (!DevCenterValidators.isValidRepoSlug(cleanSlug)) {
            return@coroutineScope GitHubRepoSnapshot(repoSlug = cleanSlug, error = "Invalid repository slug.")
        }
        val cleanPat = pat.trim()
        val headers = authHeaders(cleanPat)

        val repoDef = async { runCatching { transport.request("GET", "https://api.github.com/repos/$cleanSlug", headers) }.getOrNull() }
        val issuesDef = async { runCatching { transport.request("GET", "https://api.github.com/repos/$cleanSlug/issues?state=all&per_page=30", headers) }.getOrNull() }
        val prsDef = async { runCatching { transport.request("GET", "https://api.github.com/repos/$cleanSlug/pulls?state=all&per_page=20", headers) }.getOrNull() }
        val commitsDef = async { runCatching { transport.request("GET", "https://api.github.com/repos/$cleanSlug/commits?per_page=20", headers) }.getOrNull() }
        val relDef = async { runCatching { transport.request("GET", "https://api.github.com/repos/$cleanSlug/releases?per_page=15", headers) }.getOrNull() }
        val runsDef = async { runCatching { transport.request("GET", "https://api.github.com/repos/$cleanSlug/actions/runs?per_page=15", headers) }.getOrNull() }
        val rateDef = async {
            if (cleanPat.isEmpty()) null
            else runCatching { transport.request("GET", "https://api.github.com/rate_limit", headers) }.getOrNull()
        }

        val repoRes = repoDef.await()
        val issuesRes = issuesDef.await()
        val prsRes = prsDef.await()
        val commitsRes = commitsDef.await()
        val relRes = relDef.await()
        val runsRes = runsDef.await()
        val rateRes = rateDef.await()

        val overview = if (repoRes?.isSuccess == true) parseOverview(repoRes.body, cleanSlug) else null
        val issues = if (issuesRes?.isSuccess == true) parseIssues(issuesRes.body) else emptyList()
        val prs = if (prsRes?.isSuccess == true) parsePrs(prsRes.body) else emptyList()
        val commits = if (commitsRes?.isSuccess == true) parseCommits(commitsRes.body) else emptyList()
        val releases = if (relRes?.isSuccess == true) {
            coreClient.parseReleases(relRes.body, null).map {
                it.copy(repoSlug = cleanSlug, isPrivate = overview?.isPrivate == true)
            }
        } else emptyList()
        val runs = if (runsRes?.isSuccess == true) coreClient.parseWorkflowRuns(runsRes.body, cleanSlug) else emptyList()
        val (rateLimit, rateRemaining, rateReset) = parseRateLimit(rateRes?.body)

        val err = when {
            repoRes == null -> "Network error fetching $cleanSlug."
            repoRes.statusCode == 401 -> "GitHub rejected PAT (HTTP 401)."
            repoRes.statusCode == 403 || repoRes.statusCode == 404 ->
                "Access unavailable for $cleanSlug (HTTP ${repoRes.statusCode})."
            else -> null
        }?.let { DevCenterRedactor.redact(it, listOf(cleanPat)) }

        GitHubRepoSnapshot(
            repoSlug = cleanSlug,
            overview = overview,
            issues = issues,
            pullRequests = prs,
            commits = commits,
            releases = releases,
            workflowRuns = runs,
            rateLimitLimit = rateLimit,
            rateLimitRemaining = rateRemaining,
            rateLimitResetIso = rateReset,
            syncedAtIso = isoUtcNow(),
            error = err
        )
    }

    suspend fun fetchWorkflowJobs(pat: String, repoSlug: String, runId: String): Result<List<WorkflowJobInfo>> {
        val cleanSlug = repoSlug.trim()
        val cleanRunId = runId.trim()
        if (!DevCenterValidators.isValidRepoSlug(cleanSlug) || !cleanRunId.all(Char::isDigit)) {
            return Result.failure(IllegalArgumentException("Invalid workflow run identifier."))
        }
        val cleanPat = pat.trim()
        return runCatching {
            val res = transport.request(
                method = "GET",
                url = "https://api.github.com/repos/$cleanSlug/actions/runs/$cleanRunId/jobs?per_page=20",
                headers = authHeaders(cleanPat)
            )
            if (!res.isSuccess) {
                throw IllegalStateException("Failed to fetch jobs (HTTP ${res.statusCode}).")
            }
            parseWorkflowJobs(res.body)
        }.recoverCatching { err ->
            throw IllegalStateException(DevCenterRedactor.redact(err.message ?: "Job fetch failed.", listOf(cleanPat)))
        }
    }

    suspend fun createIssue(
        pat: String,
        repoSlug: String,
        title: String,
        body: String,
        labels: List<String> = emptyList()
    ): Result<GitHubIssueItem> {
        val cleanPat = pat.trim()
        if (cleanPat.isEmpty()) return Result.failure(IllegalStateException("GitHub PAT required to create issues."))
        val cleanSlug = repoSlug.trim()
        require(DevCenterValidators.isValidRepoSlug(cleanSlug)) { "Invalid repository slug." }
        val cleanTitle = DevCenterValidators.cleanInputText(title, 140)
        require(cleanTitle.length >= 4) { "Issue title must be at least 4 characters." }

        val payload = JSONObject().apply {
            put("title", cleanTitle)
            put("body", body.trim().take(4000))
            if (labels.isNotEmpty()) put("labels", JSONArray(labels.take(5)))
        }
        return runCatching {
            val res = transport.request(
                method = "POST",
                url = "https://api.github.com/repos/$cleanSlug/issues",
                headers = authHeaders(cleanPat) + ("Content-Type" to "application/json"),
                body = payload.toString()
            )
            if (!res.isSuccess) {
                val hint = if (res.statusCode == 403) {
                    "PAT lacks Issues: Read & Write permission (HTTP 403)."
                } else {
                    "Issue creation failed (HTTP ${res.statusCode})."
                }
                throw IllegalStateException(hint)
            }
            parseSingleIssue(JSONObject(res.body))
        }.recoverCatching { err ->
            throw IllegalStateException(DevCenterRedactor.redact(err.message ?: "Create issue failed.", listOf(cleanPat)))
        }
    }

    suspend fun updateIssueState(
        pat: String,
        repoSlug: String,
        issueNumber: Int,
        open: Boolean
    ): Result<GitHubIssueItem> {
        val cleanPat = pat.trim()
        if (cleanPat.isEmpty()) return Result.failure(IllegalStateException("GitHub PAT required to update issues."))
        val cleanSlug = repoSlug.trim()
        require(DevCenterValidators.isValidRepoSlug(cleanSlug)) { "Invalid repository slug." }
        require(issueNumber > 0) { "Invalid issue number." }

        val payload = JSONObject().put("state", if (open) "open" else "closed")
        return runCatching {
            val res = transport.request(
                method = "PATCH",
                url = "https://api.github.com/repos/$cleanSlug/issues/$issueNumber",
                headers = authHeaders(cleanPat) + ("Content-Type" to "application/json"),
                body = payload.toString()
            )
            if (!res.isSuccess) {
                val hint = if (res.statusCode == 403) {
                    "PAT lacks Issues: Read & Write permission (HTTP 403)."
                } else {
                    "Issue update failed (HTTP ${res.statusCode})."
                }
                throw IllegalStateException(hint)
            }
            parseSingleIssue(JSONObject(res.body))
        }.recoverCatching { err ->
            throw IllegalStateException(DevCenterRedactor.redact(err.message ?: "Update issue failed.", listOf(cleanPat)))
        }
    }

    suspend fun addIssueComment(
        pat: String,
        repoSlug: String,
        issueNumber: Int,
        commentBody: String
    ): Result<String> {
        val cleanPat = pat.trim()
        if (cleanPat.isEmpty()) return Result.failure(IllegalStateException("GitHub PAT required to comment."))
        val cleanSlug = repoSlug.trim()
        require(DevCenterValidators.isValidRepoSlug(cleanSlug)) { "Invalid repository slug." }
        require(issueNumber > 0) { "Invalid issue number." }
        val cleanComment = commentBody.trim().take(2000)
        require(cleanComment.isNotEmpty()) { "Comment cannot be blank." }

        return runCatching {
            val res = transport.request(
                method = "POST",
                url = "https://api.github.com/repos/$cleanSlug/issues/$issueNumber/comments",
                headers = authHeaders(cleanPat) + ("Content-Type" to "application/json"),
                body = JSONObject().put("body", cleanComment).toString()
            )
            if (!res.isSuccess) {
                val hint = if (res.statusCode == 403) {
                    "PAT lacks Issues: Read & Write permission (HTTP 403)."
                } else {
                    "Comment failed (HTTP ${res.statusCode})."
                }
                throw IllegalStateException(hint)
            }
            JSONObject(res.body).optString("html_url")
        }.recoverCatching { err ->
            throw IllegalStateException(DevCenterRedactor.redact(err.message ?: "Comment failed.", listOf(cleanPat)))
        }
    }

    internal fun parseOverview(rawJson: String, fallbackSlug: String): GitHubRepoOverview? {
        val obj = runCatching { JSONObject(rawJson) }.getOrNull() ?: return null
        val fullName = obj.optString("full_name").ifBlank { fallbackSlug }
        val isPrivate = obj.optBoolean("private", false)
        val visibility = obj.optString("visibility").ifBlank { if (isPrivate) "private" else "public" }
        val alert = if (fullName.equals(DEFAULT_CONSOLE_REPO, ignoreCase = true) && !isPrivate) {
            "CRITICAL SAFETY ALERT: Private repository $fullName is reporting PUBLIC visibility!"
        } else null
        return GitHubRepoOverview(
            fullName = fullName,
            isPrivate = isPrivate,
            visibility = visibility,
            stars = obj.optInt("stargazers_count", 0),
            forks = obj.optInt("forks_count", 0),
            watchers = obj.optInt("subscribers_count", obj.optInt("watchers_count", 0)),
            openIssuesCount = obj.optInt("open_issues_count", 0),
            defaultBranch = obj.optString("default_branch", "main"),
            updatedAtIso = obj.optString("updated_at").takeIf { it.isNotBlank() },
            htmlUrl = obj.optString("html_url"),
            privacyAlert = alert
        )
    }

    internal fun parseIssues(rawJson: String): List<GitHubIssueItem> {
        val arr = runCatching { JSONArray(rawJson) }.getOrNull() ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                if (obj.has("pull_request")) continue
                add(parseSingleIssue(obj))
            }
        }
    }

    private fun parseSingleIssue(obj: JSONObject): GitHubIssueItem {
        val labelsArr = obj.optJSONArray("labels") ?: JSONArray()
        val labels = List(labelsArr.length()) { idx ->
            labelsArr.optJSONObject(idx)?.optString("name").orEmpty()
        }.filter { it.isNotBlank() }
        return GitHubIssueItem(
            number = obj.optInt("number"),
            title = obj.optString("title"),
            body = obj.optString("body"),
            state = obj.optString("state", "open"),
            author = obj.optJSONObject("user")?.optString("login").orEmpty(),
            labels = labels,
            commentsCount = obj.optInt("comments", 0),
            updatedAtIso = obj.optString("updated_at").takeIf { it.isNotBlank() },
            htmlUrl = obj.optString("html_url")
        )
    }

    internal fun parsePrs(rawJson: String): List<GitHubPrItem> {
        val arr = runCatching { JSONArray(rawJson) }.getOrNull() ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                add(
                    GitHubPrItem(
                        number = obj.optInt("number"),
                        title = obj.optString("title"),
                        state = obj.optString("state", "open"),
                        draft = obj.optBoolean("draft", false),
                        author = obj.optJSONObject("user")?.optString("login").orEmpty(),
                        updatedAtIso = obj.optString("updated_at").takeIf { it.isNotBlank() },
                        htmlUrl = obj.optString("html_url")
                    )
                )
            }
        }
    }

    internal fun parseCommits(rawJson: String): List<GitHubCommitItem> {
        val arr = runCatching { JSONArray(rawJson) }.getOrNull() ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val sha = obj.optString("sha").take(7)
                val commitObj = obj.optJSONObject("commit")
                val msg = commitObj?.optString("message")?.lines()?.firstOrNull()?.trim().orEmpty()
                val authorObj = commitObj?.optJSONObject("author")
                add(
                    GitHubCommitItem(
                        shaShort = sha,
                        messageHeadline = msg,
                        authorName = authorObj?.optString("name").orEmpty(),
                        timestampIso = authorObj?.optString("date")?.takeIf { it.isNotBlank() },
                        htmlUrl = obj.optString("html_url")
                    )
                )
            }
        }
    }

    internal fun parseWorkflowJobs(rawJson: String): List<WorkflowJobInfo> {
        val arr = runCatching { JSONObject(rawJson).optJSONArray("jobs") }.getOrNull() ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val j = arr.optJSONObject(i) ?: continue
                val stepsArr = j.optJSONArray("steps") ?: JSONArray()
                val failedSteps = buildList {
                    for (s in 0 until stepsArr.length()) {
                        val step = stepsArr.optJSONObject(s) ?: continue
                        val c = step.optString("conclusion")
                        if (c == "failure" || c == "timed_out") {
                            add(step.optString("name").ifBlank { "Step #${step.optInt("number")}" })
                        }
                    }
                }
                add(
                    WorkflowJobInfo(
                        id = j.optLong("id").toString(),
                        name = j.optString("name", "Job"),
                        status = j.optString("status", "unknown"),
                        conclusion = j.optString("conclusion").ifBlank { "in_progress" },
                        failedSteps = failedSteps,
                        htmlUrl = j.optString("html_url")
                    )
                )
            }
        }
    }

    internal fun parseRateLimit(rawJson: String?): Triple<Int?, Int?, String?> {
        if (rawJson.isNullOrBlank()) return Triple(null, null, null)
        val core = runCatching {
            JSONObject(rawJson).optJSONObject("resources")?.optJSONObject("core")
        }.getOrNull() ?: return Triple(null, null, null)
        val limit = core.optInt("limit", -1).takeIf { it > 0 }
        val remaining = core.optInt("remaining", -1).takeIf { it >= 0 }
        val resetEpochSec = core.optLong("reset", -1L).takeIf { it > 0L }
        val resetIso = resetEpochSec?.let { isoUtcNow(it * 1000L) }
        return Triple(limit, remaining, resetIso)
    }
}
