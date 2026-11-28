package com.diniy.duit.devcenter

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject

/**
 * Direct mobile REST client talking strictly over HTTPS to api.github.com.
 * Requires zero local PC server and zero GCP service account keys.
 */
class DevCenterGitHubClient(
    private val transport: DevHttpTransport
) {
    private fun authHeaders(pat: String, accept: String = "application/vnd.github+json"): Map<String, String> =
        buildMap {
            put("Accept", accept)
            put("X-GitHub-Api-Version", "2022-11-28")
            put("User-Agent", "duit-developer-center-android/1.0")
            if (pat.isNotBlank()) {
                put("Authorization", "Bearer ${pat.trim()}")
            }
        }

    suspend fun checkConnectionHealth(
        pat: String,
        distributionRepo: String,
        consoleRepo: String
    ): ConnectionHealth = coroutineScope {
        val cleanPat = pat.trim()
        if (cleanPat.isEmpty()) {
            return@coroutineScope ConnectionHealth(
                connected = false,
                statusSummary = "GitHub PAT is not configured."
            )
        }

        val userDeferred = async {
            runCatching {
                transport.request("GET", "https://api.github.com/user", authHeaders(cleanPat))
            }.getOrNull()
        }
        val distDeferred = async {
            runCatching {
                transport.request("GET", "https://api.github.com/repos/$distributionRepo", authHeaders(cleanPat))
            }.getOrNull()
        }
        val consoleDeferred = async {
            runCatching {
                transport.request("GET", "https://api.github.com/repos/$consoleRepo", authHeaders(cleanPat))
            }.getOrNull()
        }
        val ledgerDeferred = async {
            runCatching {
                transport.request(
                    "GET",
                    "https://api.github.com/repos/$distributionRepo/contents/state/release-notifications.json?ref=notification-state",
                    authHeaders(cleanPat, accept = "application/vnd.github.raw+json")
                )
            }.getOrNull()
        }

        val userRes = userDeferred.await()
        val distRes = distDeferred.await()
        val consoleRes = consoleDeferred.await()
        val ledgerRes = ledgerDeferred.await()

        val login = if (userRes?.isSuccess == true) {
            runCatching { JSONObject(userRes.body).optString("login") }.getOrNull()?.takeIf { it.isNotBlank() }
        } else null
        val rateRemaining = userRes?.headers?.get("x-ratelimit-remaining")?.toIntOrNull()

        val distOk = distRes?.isSuccess == true
        val consoleOk = consoleRes?.isSuccess == true
        val ledgerOk = ledgerRes?.isSuccess == true
        val authOk = userRes?.isSuccess == true || (distOk && consoleOk)

        val summary = when {
            userRes == null && distRes == null -> "Network error reaching api.github.com."
            userRes?.statusCode == 401 -> "GitHub rejected token (HTTP 401). Replace PAT."
            authOk && distOk && consoleOk && ledgerOk -> "Connected${login?.let { " as @$it" }.orEmpty()}, repos & ledger healthy."
            distOk && consoleOk -> "Connected to $distributionRepo & $consoleRepo (ledger HTTP ${ledgerRes?.statusCode ?: 0})."
            else -> "Partial access: dist HTTP ${distRes?.statusCode ?: 0}, console HTTP ${consoleRes?.statusCode ?: 0}."
        }

        ConnectionHealth(
            connected = authOk && distOk && consoleOk,
            authenticatedUser = login,
            distributionRepoOk = distOk,
            consoleRepoOk = consoleOk,
            ledgerOk = ledgerOk,
            rateLimitRemaining = rateRemaining,
            statusSummary = DevCenterRedactor.redact(summary, listOf(cleanPat)),
            checkedAtIso = isoUtcNow()
        )
    }

    suspend fun fetchDashboardSnapshot(
        pat: String,
        distributionRepo: String,
        consoleRepo: String,
        privateUpdateRepo: String = DEFAULT_PRIVATE_UPDATE_REPO
    ): DashboardSnapshot = coroutineScope {
        val cleanPat = pat.trim()
        val relDeferred = async {
            runCatching {
                transport.request(
                    "GET",
                    "https://api.github.com/repos/$distributionRepo/releases?per_page=15",
                    authHeaders(cleanPat)
                )
            }
        }
        val privRelDeferred = async {
            if (cleanPat.isEmpty() || privateUpdateRepo.isBlank() || privateUpdateRepo == distributionRepo) null
            else runCatching {
                transport.request(
                    "GET",
                    "https://api.github.com/repos/$privateUpdateRepo/releases?per_page=15",
                    authHeaders(cleanPat)
                )
            }.getOrNull()
        }
        val ledgerDeferred = async {
            runCatching {
                transport.request(
                    "GET",
                    "https://api.github.com/repos/$distributionRepo/contents/state/release-notifications.json?ref=notification-state",
                    authHeaders(cleanPat, accept = "application/vnd.github.raw+json")
                )
            }
        }
        val distRunsDeferred = async {
            runCatching {
                transport.request(
                    "GET",
                    "https://api.github.com/repos/$distributionRepo/actions/runs?per_page=10",
                    authHeaders(cleanPat)
                )
            }
        }
        val consoleRunsDeferred = async {
            runCatching {
                transport.request(
                    "GET",
                    "https://api.github.com/repos/$consoleRepo/actions/runs?per_page=10",
                    authHeaders(cleanPat)
                )
            }
        }
        val relVarDeferred = async {
            if (cleanPat.isEmpty()) null else runCatching {
                transport.request(
                    "GET",
                    "https://api.github.com/repos/$distributionRepo/actions/variables/ENABLE_LIVE_FCM_DISPATCH",
                    authHeaders(cleanPat)
                )
            }.getOrNull()
        }
        val customVarDeferred = async {
            if (cleanPat.isEmpty()) null else runCatching {
                transport.request(
                    "GET",
                    "https://api.github.com/repos/$consoleRepo/actions/variables/ENABLE_CUSTOM_FCM_DISPATCH",
                    authHeaders(cleanPat)
                )
            }.getOrNull()
        }
        val userDeferred = async {
            if (cleanPat.isEmpty()) null else runCatching {
                transport.request("GET", "https://api.github.com/user", authHeaders(cleanPat))
            }.getOrNull()
        }

        val relRes = relDeferred.await().getOrNull()
        val privRelRes = privRelDeferred.await()
        val ledgerRes = ledgerDeferred.await().getOrNull()
        val distRunsRes = distRunsDeferred.await().getOrNull()
        val consoleRunsRes = consoleRunsDeferred.await().getOrNull()
        val relVarRes = relVarDeferred.await()
        val customVarRes = customVarDeferred.await()
        val userRes = userDeferred.await()

        val notices = mutableListOf<String>()
        val ledgerJson = if (ledgerRes?.isSuccess == true) {
            runCatching { JSONObject(ledgerRes.body) }.getOrNull()
        } else {
            notices += "Notification ledger unavailable (${ledgerRes?.statusCode ?: "offline"})."
            null
        }

        val publicReleases = if (relRes?.isSuccess == true) {
            parseReleases(relRes.body, ledgerJson).map {
                it.copy(repoSlug = distributionRepo, isPrivate = false)
            }
        } else {
            notices += "Releases unavailable (${relRes?.statusCode ?: "offline"})."
            emptyList()
        }
        val privateReleases = if (privRelRes?.isSuccess == true) {
            parseReleases(privRelRes.body, null).map {
                it.copy(
                    channel = "private_dev",
                    notificationStatus = "private_only",
                    repoSlug = privateUpdateRepo,
                    isPrivate = true
                )
            }
        } else emptyList()
        val releases = (publicReleases + privateReleases).sortedByDescending { it.publishedAt.orEmpty() }

        val distRuns = if (distRunsRes?.isSuccess == true) {
            parseWorkflowRuns(distRunsRes.body, distributionRepo)
        } else {
            if (distRunsRes != null) notices += "Distribution workflow runs unavailable (${distRunsRes.statusCode})."
            emptyList()
        }

        val consoleRuns = if (consoleRunsRes?.isSuccess == true) {
            parseWorkflowRuns(consoleRunsRes.body, consoleRepo)
        } else {
            if (cleanPat.isNotEmpty() && consoleRunsRes != null) {
                notices += "Console workflow runs unavailable (${consoleRunsRes.statusCode})."
            }
            emptyList()
        }

        val mergedRuns = (distRuns + consoleRuns)
            .sortedByDescending { it.createdAt.orEmpty() }
            .take(16)

        val releaseBroadcasts = parseLedgerBroadcasts(ledgerJson)
        val minFloor = ledgerJson?.takeIf { it.has("minExclusiveVersionCode") && !it.isNull("minExclusiveVersionCode") }
            ?.optLong("minExclusiveVersionCode")

        val liveRelEnabled = if (relVarRes?.isSuccess == true) {
            runCatching { JSONObject(relVarRes.body).optString("value") == "true" }.getOrNull()
        } else null
        val customEnabled = if (customVarRes?.isSuccess == true) {
            runCatching { JSONObject(customVarRes.body).optString("value") == "true" }.getOrNull()
        } else null

        val login = if (userRes?.isSuccess == true) {
            runCatching { JSONObject(userRes.body).optString("login") }.getOrNull()?.takeIf { it.isNotBlank() }
        } else null
        val rateRemaining = (userRes ?: relRes)?.headers?.get("x-ratelimit-remaining")?.toIntOrNull()

        val healthOk = cleanPat.isNotEmpty() &&
            userRes?.statusCode != 401 &&
            (relRes?.isSuccess == true) &&
            (consoleRunsRes?.isSuccess == true || customVarRes?.isSuccess == true)

        val healthSummary = when {
            cleanPat.isEmpty() -> "Public read-only snapshot loaded. Add fine-grained PAT for workflows, broadcasts & kill switches."
            userRes?.statusCode == 401 -> "GitHub token rejected (HTTP 401). Update PAT in Developer Settings."
            healthOk -> "Connected${login?.let { " as @$it" }.orEmpty()}, live system status active."
            else -> DevCenterRedactor.redact(notices.firstOrNull() ?: "Check repo access or network.", listOf(cleanPat))
        }

        DashboardSnapshot(
            health = ConnectionHealth(
                connected = healthOk,
                authenticatedUser = login,
                distributionRepoOk = relRes?.isSuccess == true,
                consoleRepoOk = consoleRunsRes?.isSuccess == true || customVarRes?.isSuccess == true,
                ledgerOk = ledgerJson != null,
                rateLimitRemaining = rateRemaining,
                statusSummary = healthSummary,
                checkedAtIso = isoUtcNow()
            ),
            releases = releases,
            workflowRuns = mergedRuns,
            releaseBroadcasts = releaseBroadcasts,
            killSwitches = AutomationKillSwitches(
                customBroadcastEnabled = customEnabled,
                releaseAutomationEnabled = liveRelEnabled
            ),
            minExclusiveVersionCode = minFloor,
            degradedNotice = if (notices.isNotEmpty() && cleanPat.isNotEmpty()) {
                DevCenterRedactor.redact(notices.joinToString(" "), listOf(cleanPat))
            } else null
        )
    }

    suspend fun dispatchCustomBroadcast(
        pat: String,
        consoleRepo: String,
        request: ValidatedBroadcastRequest
    ): Result<String> {
        val cleanPat = pat.trim()
        if (cleanPat.isEmpty()) {
            return Result.failure(IllegalStateException("GitHub PAT required to dispatch workflows."))
        }

        val payload = JSONObject().apply {
            put("ref", "main")
            put("inputs", JSONObject().apply {
                put("broadcast_id", request.broadcastId)
                put("type", request.category.apiValue)
                put("audience", request.audience.apiValue)
                put("route", request.route.apiValue)
                put("title", request.title)
                put("body", request.body)
                put("validate_only", request.validateOnly)
                put("confirmation_phrase", request.confirmationPhrase)
                put("reason", request.reason)
            })
        }

        return runCatching {
            val res = transport.request(
                method = "POST",
                url = "https://api.github.com/repos/$consoleRepo/actions/workflows/custom-broadcast.yml/dispatches",
                headers = authHeaders(cleanPat) + ("Content-Type" to "application/json"),
                body = payload.toString()
            )
            if (!res.isSuccess) {
                val apiMsg = runCatching { JSONObject(res.body).optString("message") }.getOrNull().orEmpty()
                val err = "GitHub workflow_dispatch failed (HTTP ${res.statusCode}): $apiMsg".trim()
                throw IllegalStateException(DevCenterRedactor.redact(err, listOf(cleanPat)))
            }
            "https://github.com/$consoleRepo/actions/workflows/custom-broadcast.yml"
        }.recoverCatching { err ->
            throw IllegalStateException(DevCenterRedactor.redact(err.message ?: "Dispatch failed.", listOf(cleanPat)))
        }
    }

    suspend fun fetchLatestCustomWorkflowRun(
        pat: String,
        consoleRepo: String
    ): WorkflowRunInfo? {
        val cleanPat = pat.trim()
        if (cleanPat.isEmpty()) return null
        val res = runCatching {
            transport.request(
                "GET",
                "https://api.github.com/repos/$consoleRepo/actions/workflows/custom-broadcast.yml/runs?per_page=3",
                authHeaders(cleanPat)
            )
        }.getOrNull() ?: return null
        if (!res.isSuccess) return null
        return parseWorkflowRuns(res.body, consoleRepo).firstOrNull()
    }

    suspend fun updateKillSwitch(
        pat: String,
        target: KillSwitchTarget,
        repoOverride: String,
        enabled: Boolean
    ): Result<Boolean> {
        val cleanPat = pat.trim()
        if (cleanPat.isEmpty()) {
            return Result.failure(IllegalStateException("GitHub PAT required to update repository variables."))
        }
        val repo = repoOverride.trim().ifBlank { target.defaultRepo }
        val payload = JSONObject().apply {
            put("name", target.variableName)
            put("value", if (enabled) "true" else "false")
        }
        return runCatching {
            val res = transport.request(
                method = "PATCH",
                url = "https://api.github.com/repos/$repo/actions/variables/${target.variableName}",
                headers = authHeaders(cleanPat) + ("Content-Type" to "application/json"),
                body = payload.toString()
            )
            if (!res.isSuccess) {
                val apiMsg = runCatching { JSONObject(res.body).optString("message") }.getOrNull().orEmpty()
                throw IllegalStateException(
                    DevCenterRedactor.redact("Failed to update ${target.variableName} (HTTP ${res.statusCode}): $apiMsg", listOf(cleanPat))
                )
            }
            enabled
        }.recoverCatching { err ->
            throw IllegalStateException(DevCenterRedactor.redact(err.message ?: "Variable update failed.", listOf(cleanPat)))
        }
    }

    internal fun parseReleases(rawJson: String, ledgerJson: JSONObject?): List<ReleaseInfo> {
        val arr = runCatching { JSONArray(rawJson) }.getOrNull() ?: return emptyList()
        val ledgerReleases = ledgerJson?.optJSONObject("releases")
        return buildList {
            for (i in 0 until arr.length()) {
                val r = arr.optJSONObject(i) ?: continue
                val tag = r.optString("tag_name")
                if (tag.isBlank()) continue
                val prerelease = r.optBoolean("prerelease", false)
                val assets = r.optJSONArray("assets") ?: JSONArray()
                var apkObj: JSONObject? = null
                for (aIdx in 0 until assets.length()) {
                    val candidate = assets.optJSONObject(aIdx) ?: continue
                    if (candidate.optString("name").endsWith(".apk", ignoreCase = true)) {
                        apkObj = candidate
                        break
                    }
                }
                val entry = ledgerReleases?.optJSONObject(tag)
                val digest = apkObj?.optString("digest").orEmpty()
                val sha256 = when {
                    digest.startsWith("sha256:") -> digest.removePrefix("sha256:")
                    entry != null -> entry.optString("assetSha256").takeIf { it.isNotBlank() }
                    else -> null
                }
                val changelog = r.optString("body")
                    .lines()
                    .map { it.trim() }
                    .filter { it.startsWith("- ") || it.startsWith("* ") }
                    .map { it.removePrefix("- ").removePrefix("* ").replace("**", "").trim() }
                    .take(8)

                add(
                    ReleaseInfo(
                        tag = tag,
                        version = tag.removePrefix("v"),
                        versionCode = entry?.takeIf { it.has("versionCode") && !it.isNull("versionCode") }?.optLong("versionCode"),
                        title = r.optString("name").ifBlank { tag },
                        channel = if (prerelease) "beta" else "stable",
                        prerelease = prerelease,
                        publishedAt = r.optString("published_at").takeIf { it.isNotBlank() },
                        htmlUrl = r.optString("html_url"),
                        assetName = apkObj?.optString("name")?.takeIf { it.isNotBlank() },
                        assetSizeBytes = apkObj?.optLong("size")?.takeIf { it > 0L },
                        downloadCount = apkObj?.optInt("download_count", 0) ?: 0,
                        assetDownloadUrl = apkObj?.optString("browser_download_url")?.takeIf { it.isNotBlank() },
                        sha256 = sha256,
                        notificationStatus = entry?.optString("status")?.takeIf { it.isNotBlank() } ?: "unrecorded",
                        changelogBullets = changelog,
                        rawBody = r.optString("body").take(3000)
                    )
                )
            }
        }
    }

    internal fun parseWorkflowRuns(rawJson: String, repo: String): List<WorkflowRunInfo> {
        val root = runCatching { JSONObject(rawJson) }.getOrNull() ?: return emptyList()
        val arr = root.optJSONArray("workflow_runs") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val w = arr.optJSONObject(i) ?: continue
                val status = w.optString("status", "unknown")
                val conclusion = w.optString("conclusion").takeIf { it.isNotBlank() && it != "null" }
                    ?: if (status == "completed") "neutral" else "in_progress"
                add(
                    WorkflowRunInfo(
                        id = w.optLong("id").toString(),
                        repo = repo,
                        workflowName = w.optString("name", "Workflow"),
                        eventTitle = w.optString("display_title").ifBlank { w.optString("event", "dispatch") },
                        status = status,
                        conclusion = conclusion,
                        createdAt = w.optString("created_at").takeIf { it.isNotBlank() },
                        htmlUrl = w.optString("html_url"),
                        branchOrTag = w.optString("head_branch").ifBlank { w.optString("event", "") }
                    )
                )
            }
        }
    }

    internal fun parseLedgerBroadcasts(ledgerJson: JSONObject?): List<BroadcastHistoryEntry> {
        val releases = ledgerJson?.optJSONObject("releases") ?: return emptyList()
        val keys = releases.keys()
        val list = mutableListOf<BroadcastHistoryEntry>()
        while (keys.hasNext()) {
            val tag = keys.next()
            val entry = releases.optJSONObject(tag) ?: continue
            val channel = if (entry.optString("channel") == "beta") "beta" else "stable"
            val audience = if (channel == "beta") "beta" else "both"
            val topicsJson = entry.optJSONObject("topics")
            val topics = mutableListOf<String>()
            if (topicsJson != null) {
                val tk = topicsJson.keys()
                while (tk.hasNext()) topics += tk.next()
            }
            if (topics.isEmpty()) {
                if (channel == "beta") topics += "duit_beta_broadcasts"
                else {
                    topics += "duit_announcements"
                    topics += "duit_beta_broadcasts"
                }
            }
            val status = entry.optString("status", "unrecorded")
            val statusLabel = when (status) {
                "preseeded_skip" -> "Baseline Skipped"
                "sent" -> "Sent via Phase 18A"
                "accepted_by_fcm" -> "Accepted by FCM"
                "validate_only" -> "Dry-Run Validated"
                "partial" -> "Partial Acceptance"
                "ambiguous" -> "Ambiguous — Verify"
                else -> status
            }
            list += BroadcastHistoryEntry(
                id = "rel-$tag",
                kind = "release",
                category = "update",
                broadcastId = entry.optString("broadcastId").ifBlank { "duit-release-${tag.removePrefix("v")}" },
                title = "duit. ${entry.optString("tag", tag)} release broadcast",
                body = entry.optString("note").ifBlank { "Phase 18A ledger record ($channel channel)." },
                audience = audience,
                topics = topics,
                route = "home",
                status = status,
                statusLabel = statusLabel,
                timestampIso = entry.optString("updatedAt").ifBlank { entry.optString("createdAt") },
                note = entry.optString("note")
            )
        }
        return list.sortedByDescending { it.timestampIso }
    }
}
