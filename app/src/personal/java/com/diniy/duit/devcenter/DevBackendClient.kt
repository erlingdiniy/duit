package com.diniy.duit.devcenter

import com.diniy.duit.config.RemoteAppConfigPolicy
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

class DeviceRevokedException(message: String) : IOException(message)

data class BackendStepUpProof(
    val challengeId: String,
    val signature: String
)

data class BackendNotificationsState(
    val templates: List<BroadcastTemplate>,
    val drafts: List<BroadcastDraft>,
    val autoScheduled: List<BackendAutoScheduledBroadcast>,
    val history: List<BroadcastHistoryEntry>,
    val workflowRuns: List<WorkflowRunInfo>,
    val killSwitches: AutomationKillSwitches
)

data class BackendFeedbackLoadResult(
    val entries: List<FeedbackEntry>,
    val triageMap: Map<String, FeedbackTriageRecord>,
    val openFeatureCount: Int,
    val spamCount: Int
)

data class BackendOverviewBundle(
    val syncedAtIso: String,
    val dashboard: DashboardSnapshot,
    val analytics: AnalyticsSourceState,
    val remoteConfig: RemoteConfigSourceState,
    val recentAudit: List<AuditLogEntry>,
    val wifSyncedAtIso: String?
)

/**
 * Zero-Configuration Mission Control Worker client.
 * Authenticates via non-exportable AndroidKeyStore ECDSA P-256 Proof-of-Possession,
 * automatically refreshes short-lived JWTs, and signs step-up proofs for privileged mutations.
 */
class DevBackendClient(
    private val vault: DevCenterVault,
    private val keyManager: DeviceSigningKeyManager,
    private val transport: DevHttpTransport,
    private val nowMs: () -> Long = System::currentTimeMillis
) {
    private val authMutex = Mutex()

    suspend fun resolveBaseUrl(): String {
        val v = vault.getState()
        return v.backendUrlOverride.trim().ifBlank { com.diniy.duit.BuildConfig.DEV_BACKEND_URL.trim() }.trimEnd('/')
    }

    suspend fun enrollDevice(
        backendUrlInput: String,
        enrollmentCode: String,
        deviceLabel: String
    ): Result<DevCenterVaultState> = runCatching {
        val baseUrl = backendUrlInput.trim().trimEnd('/')
        require(baseUrl.isNotBlank()) { "Mission Control backend URL is required." }
        HttpsUrlConnectionTransport.validateBackendUrl(
            baseUrl,
            allowLoopbackDebug = com.diniy.duit.BuildConfig.DEBUG
        )
        val cleanCode = enrollmentCode.trim().uppercase()
        require(cleanCode.length >= 6) { "Enter the enrollment code from Mission Control CLI." }
        val cleanLabel = DevCenterValidators.cleanInputText(deviceLabel, 60).ifBlank { "Primary Phone" }

        val keyMeta = keyManager.ensureKeyPair()
        val existingId = vault.getState().enrolledDeviceId.trim()
        val deviceId = existingId.ifBlank { "dev_${UUID.randomUUID().toString().replace("-", "").take(16)}" }

        val chRes = postUnauthJson(
            "$baseUrl/v1/auth/challenge",
            JSONObject().apply {
                put("purpose", "enroll")
            }
        )
        val challengeId = chRes.optString("challengeId")
        val nonce = chRes.optString("nonce")
        require(challengeId.isNotBlank() && nonce.isNotBlank()) { "Invalid challenge response from backend." }

        val sig = keyManager.signChallengePayload("enroll:$challengeId:$nonce:$deviceId")
        val enrollRes = postUnauthJson(
            "$baseUrl/v1/auth/enroll",
            JSONObject().apply {
                put("enrollmentCode", cleanCode)
                put("deviceId", deviceId)
                put("label", cleanLabel)
                put("publicKeySpki", keyMeta.publicKeySpkiBase64)
                put("challengeId", challengeId)
                put("signature", sig)
                put("hardwareBacked", keyMeta.hardwareBacked)
                put("attestationSummary", keyMeta.attestationSummary)
            }
        )

        val accessToken = enrollRes.getString("accessToken")
        val accessExp = enrollRes.getLong("accessTokenExpiresAtMs")
        val refreshToken = enrollRes.getString("refreshToken")
        val refreshExp = enrollRes.getLong("refreshTokenExpiresAtMs")

        vault.saveBackendEnrollment(
            backendUrl = baseUrl,
            deviceId = deviceId,
            label = enrollRes.optString("label", cleanLabel),
            keyAlias = keyMeta.alias,
            hardwareBacked = keyMeta.hardwareBacked,
            accessToken = accessToken,
            accessExpiresAtMs = accessExp,
            refreshToken = refreshToken,
            refreshExpiresAtMs = refreshExp
        )
        vault.appendAuditEvent(
            AuditLogEntry(
                id = "aud-${nowMs()}",
                timestampIso = isoUtc(nowMs()),
                actor = cleanLabel,
                action = "device.enrolled",
                target = deviceId,
                status = "ok",
                summary = "Zero-config hardware key enrolled (${keyMeta.attestationSummary})."
            ),
            extraSecrets = listOf(accessToken, refreshToken, cleanCode)
        )
        vault.getState()
    }

    suspend fun ensureValidAccessToken(forceReauth: Boolean = false): String = authMutex.withLock {
        val baseUrl = resolveBaseUrl()
        require(baseUrl.isNotBlank()) { "Mission Control backend URL is not configured." }
        val v = vault.getState()
        val deviceId = v.enrolledDeviceId.trim()
        require(deviceId.isNotBlank()) { "Device is not enrolled." }

        val currentAccess = vault.readBackendAccessToken()
        val now = nowMs()
        if (!forceReauth && !currentAccess.isNullOrBlank() && v.backendAccessExpiresAtMs > now + 45_000L) {
            return@withLock currentAccess
        }

        val refreshToken = vault.readBackendRefreshToken()
        if (!forceReauth && !refreshToken.isNullOrBlank() && v.backendRefreshExpiresAtMs > now + 30_000L) {
            val refreshAttempt = runCatching {
                val res = transport.requestBackend(
                    method = "POST",
                    url = "$baseUrl/v1/auth/refresh",
                    headers = mapOf("Content-Type" to "application/json", "Accept" to "application/json"),
                    body = JSONObject().apply {
                        put("deviceId", deviceId)
                        put("refreshToken", refreshToken)
                    }.toString()
                )
                handleTokenResponse(res, baseUrl, refreshToken)
            }.getOrNull()
            if (refreshAttempt != null) {
                return@withLock refreshAttempt
            }
        }

        // Silent hardware ECDSA Proof-of-Possession token issue
        val chRes = postUnauthJson(
            "$baseUrl/v1/auth/challenge",
            JSONObject().apply {
                put("purpose", "token")
                put("deviceId", deviceId)
            }
        )
        val challengeId = chRes.optString("challengeId")
        val nonce = chRes.optString("nonce")
        require(challengeId.isNotBlank() && nonce.isNotBlank()) { "Failed to obtain PoP challenge." }

        val sig = keyManager.signChallengePayload("token:$challengeId:$nonce:$deviceId")
        val tokRes = transport.requestBackend(
            method = "POST",
            url = "$baseUrl/v1/auth/token",
            headers = mapOf("Content-Type" to "application/json", "Accept" to "application/json"),
            body = JSONObject().apply {
                put("deviceId", deviceId)
                put("challengeId", challengeId)
                put("signature", sig)
            }.toString()
        )
        return@withLock handleTokenResponse(tokRes, baseUrl, refreshToken.orEmpty())
            ?: throw IOException("Failed to renew session via hardware key PoP.")
    }

    private suspend fun handleTokenResponse(
        res: DevHttpResponse,
        baseUrl: String,
        previousSecret: String
    ): String? {
        if (res.statusCode == 403) {
            val msg = extractError(res.body)
            if (msg.contains("revoked", ignoreCase = true) || msg.contains("not enrolled", ignoreCase = true)) {
                vault.clearBackendEnrollment()
                throw DeviceRevokedException(msg.ifBlank { "This device was revoked by administrator." })
            }
            throw IOException(DevCenterRedactor.redact(msg.ifBlank { "Device authentication rejected." }, listOf(previousSecret, baseUrl)))
        }
        if (!res.isSuccess) return null
        val json = JSONObject(res.body)
        val access = json.getString("accessToken")
        val accessExp = json.getLong("accessTokenExpiresAtMs")
        val refresh = json.getString("refreshToken")
        val refreshExp = json.getLong("refreshTokenExpiresAtMs")
        vault.updateBackendSessionTokens(access, accessExp, refresh, refreshExp)
        return access
    }

    suspend fun requestStepUpProof(action: String): BackendStepUpProof {
        val baseUrl = resolveBaseUrl()
        val deviceId = vault.getState().enrolledDeviceId.trim()
        require(baseUrl.isNotBlank() && deviceId.isNotBlank()) { "Device enrollment required." }
        val chRes = postUnauthJson(
            "$baseUrl/v1/auth/challenge",
            JSONObject().apply {
                put("purpose", "step_up")
                put("deviceId", deviceId)
            }
        )
        val challengeId = chRes.optString("challengeId")
        val nonce = chRes.optString("nonce")
        require(challengeId.isNotBlank() && nonce.isNotBlank()) { "Invalid step-up challenge." }
        val sig = keyManager.signChallengePayload("step_up:$challengeId:$nonce:$deviceId:$action")
        return BackendStepUpProof(challengeId = challengeId, signature = sig)
    }

    suspend fun authorizedRequest(
        method: String,
        path: String,
        body: JSONObject? = null
    ): JSONObject {
        val baseUrl = resolveBaseUrl()
        var token = ensureValidAccessToken(forceReauth = false)
        var res = transport.requestBackend(
            method = method,
            url = "$baseUrl$path",
            headers = buildHeaders(token, body != null),
            body = body?.toString()
        )
        if (res.statusCode == 401) {
            token = ensureValidAccessToken(forceReauth = true)
            res = transport.requestBackend(
                method = method,
                url = "$baseUrl$path",
                headers = buildHeaders(token, body != null),
                body = body?.toString()
            )
        }
        if (res.statusCode == 403) {
            val msg = extractError(res.body)
            if (msg.contains("revoked", ignoreCase = true) || msg.contains("not enrolled", ignoreCase = true)) {
                vault.clearBackendEnrollment()
                throw DeviceRevokedException(msg)
            }
            throw IOException(DevCenterRedactor.redact(msg.ifBlank { "Access forbidden (HTTP 403)." }, listOf(token)))
        }
        if (res.statusCode == 429) {
            val retryAfter = res.headers["retry-after"] ?: JSONObject(res.body.ifBlank { "{}" }).optString("retryAfterSeconds", "30")
            throw IOException("Rate limit exceeded. Retry in ${retryAfter}s.")
        }
        if (!res.isSuccess) {
            val msg = extractError(res.body).ifBlank { "Backend error HTTP ${res.statusCode}" }
            throw IOException(DevCenterRedactor.redact(msg, listOf(token)))
        }
        return if (res.body.isBlank()) JSONObject() else JSONObject(res.body)
    }

    private fun buildHeaders(token: String, hasBody: Boolean): Map<String, String> = buildMap {
        put("Authorization", "Bearer $token")
        put("Accept", "application/json")
        if (hasBody) put("Content-Type", "application/json; charset=UTF-8")
    }

    private suspend fun postUnauthJson(url: String, body: JSONObject): JSONObject {
        val res = transport.requestBackend(
            method = "POST",
            url = url,
            headers = mapOf("Content-Type" to "application/json; charset=UTF-8", "Accept" to "application/json"),
            body = body.toString()
        )
        if (!res.isSuccess) {
            val msg = extractError(res.body).ifBlank { "Request failed (HTTP ${res.statusCode})." }
            throw IOException(DevCenterRedactor.redact(msg))
        }
        return JSONObject(res.body)
    }

    suspend fun fetchOverviewBundle(windowDays: Int, dateRange: AnalyticsDateRange): BackendOverviewBundle {
        val json = authorizedRequest("GET", "/v1/admin/overview?window=$windowDays")
        val syncedMs = json.optLong("syncedAtMs", nowMs())
        val syncedIso = isoUtc(syncedMs)
        val relSummary = parseReleasesPayload(json.optJSONObject("releases") ?: JSONObject())
        val ksObj = json.optJSONObject("killSwitches")
        val killSwitches = AutomationKillSwitches(
            customBroadcastEnabled = ksObj?.optNullableBoolean("customBroadcastsEnabled"),
            releaseAutomationEnabled = ksObj?.optNullableBoolean("releaseNotificationsEnabled")
        )
        val gaWrap = json.optJSONObject("ga4")
        val analyticsState = parseGa4SnapshotWrapper(gaWrap, dateRange, syncedIso)
        val rcWrap = json.optJSONObject("remoteConfig")
        val remoteConfigState = parseRemoteConfigWrapper(rcWrap, syncedIso)
        val auditArr = json.optJSONArray("recentAudit") ?: JSONArray()
        val recentAudit = parseAuditArray(auditArr)
        val wifSnap = json.optJSONObject("wifHealth")
        val wifIso = wifSnap?.optLong("syncedAt", 0L)?.takeIf { it > 0L }?.let(::isoUtc)
        val v = vault.getState()

        val dashboard = DashboardSnapshot(
            health = ConnectionHealth(
                connected = true,
                authenticatedUser = "Mission Control (${v.enrolledDeviceLabel.ifBlank { "Enrolled" }})",
                distributionRepoOk = relSummary.ok,
                consoleRepoOk = relSummary.ok,
                ledgerOk = true,
                rateLimitRemaining = relSummary.rateRemaining,
                statusSummary = "Zero-config Worker connected (GitHub App / WIF server-managed).",
                checkedAtIso = syncedIso
            ),
            releases = relSummary.releases,
            workflowRuns = emptyList(),
            releaseBroadcasts = relSummary.releaseBroadcasts,
            killSwitches = killSwitches,
            degradedNotice = null
        )
        return BackendOverviewBundle(
            syncedAtIso = syncedIso,
            dashboard = dashboard,
            analytics = analyticsState,
            remoteConfig = remoteConfigState,
            recentAudit = recentAudit,
            wifSyncedAtIso = wifIso
        )
    }

    suspend fun fetchAnalytics(dateRange: AnalyticsDateRange): AnalyticsSourceState {
        val json = authorizedRequest("GET", "/v1/admin/analytics?window=${dateRange.days}")
        val available = json.optBoolean("available", false)
        val snap = json.optJSONObject("snapshot")
        if (!available && snap == null) {
            return AnalyticsSourceState.Unavailable(
                summary = json.optString("reason", "Waiting for first scheduled WIF analytics sync."),
                hasPropertyId = true,
                hasToken = true,
                canSignInWithGoogle = false
            )
        }
        return parseGa4SnapshotWrapper(snap, dateRange, isoUtc(nowMs()))
    }

    suspend fun triggerSync(): String {
        val json = authorizedRequest("POST", "/v1/admin/sync/trigger", JSONObject())
        return json.optString("message", "Background sync workflow dispatched.")
    }

    suspend fun fetchFeedback(): BackendFeedbackLoadResult {
        val json = authorizedRequest("GET", "/v1/admin/feedback")
        val itemsArr = json.optJSONArray("items") ?: JSONArray()
        val summaryObj = json.optJSONObject("summary")
        val entries = mutableListOf<FeedbackEntry>()
        val triageMap = mutableMapOf<String, FeedbackTriageRecord>()

        for (i in 0 until itemsArr.length()) {
            val o = itemsArr.optJSONObject(i) ?: continue
            val id = o.optString("id").takeIf { it.isNotBlank() } ?: continue
            val createdMs = o.optLong("createdAt", nowMs())
            val updatedMs = o.optLong("updatedAt", createdMs)
            val status = FeedbackTriageStatus.fromStored(o.optString("status"))
            entries += FeedbackEntry(
                id = id,
                timestampIso = isoUtc(createdMs),
                category = o.optString("category", "General Feedback"),
                message = o.optString("message"),
                email = o.optString("contactEmail"),
                appVersion = o.optString("appVersion"),
                status = status.label,
                sheetRowNumber = null
            )
            triageMap[id] = FeedbackTriageRecord(
                feedbackId = id,
                isRead = !o.optBoolean("isUnread", false),
                priority = parsePriorityFromBackend(o.optString("priority")),
                status = status,
                notes = o.optString("developerNotes"),
                linkedIssueNumber = o.takeIf { it.has("githubIssueNumber") && !it.isNull("githubIssueNumber") }?.optInt("githubIssueNumber"),
                linkedIssueUrl = o.optString("githubIssueUrl").takeIf { it.isNotBlank() },
                updatedAtIso = isoUtc(updatedMs)
            )
        }
        return BackendFeedbackLoadResult(
            entries = entries,
            triageMap = triageMap,
            openFeatureCount = summaryObj?.optInt("openFeatureCount", 0) ?: 0,
            spamCount = summaryObj?.optInt("spamCount", 0) ?: 0
        )
    }

    suspend fun markAllFeedbackRead() {
        authorizedRequest("PATCH", "/v1/admin/feedback/mark-all-read", JSONObject())
    }

    suspend fun updateFeedbackTriage(
        feedbackId: String,
        isUnread: Boolean,
        priority: FeedbackPriority,
        status: FeedbackTriageStatus,
        developerNotes: String
    ) {
        val body = JSONObject().apply {
            put("isUnread", isUnread)
            put("priority", priorityToBackend(priority))
            put("status", status.apiValue)
            put("developerNotes", developerNotes.take(1000))
        }
        authorizedRequest("PATCH", "/v1/admin/feedback/$feedbackId", body)
    }

    suspend fun createIssueFromFeedback(feedbackId: String, repoSlug: String): Pair<Int, String> {
        val json = authorizedRequest(
            "POST",
            "/v1/admin/feedback/$feedbackId/github-issue",
            JSONObject().apply { put("repo", repoSlug) }
        )
        return json.optInt("issueNumber", 0) to json.optString("issueUrl", "")
    }

    suspend fun fetchNotificationsState(): BackendNotificationsState {
        val json = authorizedRequest("GET", "/v1/admin/notifications/state")
        val templates = parseTemplates(json.optJSONArray("templates") ?: JSONArray())
        val drafts = parseDrafts(json.optJSONArray("drafts") ?: JSONArray())
        val autoScheduled = parseAutoScheduled(json.optJSONArray("scheduledBroadcasts") ?: JSONArray())
        val history = parseBackendHistory(json.optJSONArray("history") ?: JSONArray())
        val runs = parseWorkflowRuns(json.optJSONArray("workflowRuns") ?: JSONArray(), vault.getState().consoleRepo)
        val ksObj = json.optJSONObject("killSwitches")
        val killSwitches = AutomationKillSwitches(
            customBroadcastEnabled = ksObj?.optNullableBoolean("customBroadcastsEnabled"),
            releaseAutomationEnabled = ksObj?.optNullableBoolean("releaseNotificationsEnabled")
        )
        return BackendNotificationsState(
            templates = templates,
            drafts = drafts,
            autoScheduled = autoScheduled,
            history = history,
            workflowRuns = runs,
            killSwitches = killSwitches
        )
    }

    suspend fun dispatchBroadcast(
        mode: String,
        req: ValidatedBroadcastRequest,
        testDeviceToken: String = ""
    ): JSONObject {
        val payload = JSONObject().apply {
            put("mode", mode)
            put("broadcastId", req.broadcastId)
            put("type", req.category.apiValue)
            put("audience", req.audience.apiValue)
            put("route", req.route.apiValue)
            put("title", req.title)
            put("body", req.body)
            put("confirmationPhrase", req.confirmationPhrase)
            put("reason", req.reason)
            if (testDeviceToken.isNotBlank()) put("testDeviceToken", testDeviceToken.trim())
        }
        if (mode == "public_live") {
            val stepUp = requestStepUpProof("broadcast_live:${req.broadcastId}")
            payload.put("challengeId", stepUp.challengeId)
            payload.put("signature", stepUp.signature)
        }
        return authorizedRequest("POST", "/v1/admin/notifications/dispatch", payload)
    }

    suspend fun scheduleAutoBroadcast(
        req: ValidatedBroadcastRequest,
        scheduledAtMs: Long
    ): JSONObject {
        val stepUp = requestStepUpProof("schedule_live:${req.broadcastId}")
        val payload = JSONObject().apply {
            put("broadcastId", req.broadcastId)
            put("type", req.category.apiValue)
            put("audience", req.audience.apiValue)
            put("route", req.route.apiValue)
            put("title", req.title)
            put("body", req.body)
            put("scheduledAtMs", scheduledAtMs)
            put("confirmationPhrase", req.confirmationPhrase)
            put("reason", req.reason)
            put("challengeId", stepUp.challengeId)
            put("signature", stepUp.signature)
        }
        return authorizedRequest("POST", "/v1/admin/notifications/scheduled", payload)
    }

    suspend fun cancelAutoScheduledBroadcast(id: String) {
        authorizedRequest("DELETE", "/v1/admin/notifications/scheduled/$id")
    }

    suspend fun saveRemoteDraft(draft: BroadcastDraft) {
        authorizedRequest(
            "POST",
            "/v1/admin/notifications/drafts",
            JSONObject().apply {
                put("id", draft.id)
                put("category", draft.category.apiValue)
                put("audience", draft.audience.apiValue)
                put("route", draft.route.apiValue)
                put("title", draft.title)
                put("body", draft.body)
            }
        )
    }

    suspend fun deleteRemoteDraft(id: String) {
        authorizedRequest("DELETE", "/v1/admin/notifications/drafts/$id")
    }

    suspend fun fetchLatestPrivateOta(): BackendPrivateOtaRelease? {
        val json = authorizedRequest("GET", "/v1/admin/ota/latest")
        val rel = json.optJSONObject("release") ?: return null
        return BackendPrivateOtaRelease(
            repository = rel.optString("repository"),
            tag = rel.optString("tag"),
            versionName = rel.optString("versionName"),
            versionCode = rel.optLong("versionCode"),
            assetId = rel.optLong("assetId"),
            assetName = rel.optString("assetName"),
            sizeBytes = rel.optLong("sizeBytes"),
            sha256 = rel.optString("sha256"),
            publishedAt = rel.optString("publishedAt"),
            notes = rel.optString("notes")
        )
    }

    suspend fun fetchRemoteConfig(): RemoteConfigSourceState {
        val json = authorizedRequest("GET", "/v1/admin/remote-config")
        return parseRemoteConfigWrapper(json.optJSONObject("snapshot"), isoUtc(nowMs()))
    }

    suspend fun publishRemoteConfig(
        etag: String,
        parameters: List<RemoteConfigParam>
    ): String {
        for (p in parameters) {
            if (!RemoteAppConfigPolicy.isSafeParameterKey(p.key)) {
                throw SecurityException("Blocked unsafe Remote Config key '${p.key}'.")
            }
        }
        val stepUp = requestStepUpProof("rc_publish")
        val paramsObj = JSONObject()
        for (p in parameters) {
            paramsObj.put(
                p.key,
                JSONObject().apply {
                    put("defaultValue", JSONObject().apply { put("value", p.defaultValue) })
                    put("valueType", p.valueType)
                    if (p.description.isNotBlank()) put("description", p.description)
                }
            )
        }
        authorizedRequest(
            "POST",
            "/v1/admin/remote-config/publish",
            JSONObject().apply {
                put("etag", etag.ifBlank { "*" })
                put("confirmationPhrase", "PUBLISH CONFIG")
                put("parameters", paramsObj)
                put("challengeId", stepUp.challengeId)
                put("signature", stepUp.signature)
            }
        )
        return "Remote Config publish workflow dispatched via keyless WIF."
    }

    suspend fun rollbackRemoteConfig(versionNumber: String): String {
        val stepUp = requestStepUpProof("rc_rollback:$versionNumber")
        authorizedRequest(
            "POST",
            "/v1/admin/remote-config/rollback",
            JSONObject().apply {
                put("versionNumber", versionNumber.toLong())
                put("challengeId", stepUp.challengeId)
                put("signature", stepUp.signature)
            }
        )
        return "Remote Config rollback to v$versionNumber dispatched."
    }

    suspend fun fetchGitHubOverview(repoSlug: String): GitHubRepoSnapshot {
        val json = authorizedRequest("GET", "/v1/admin/github/overview?repo=$repoSlug")
        val repoObj = json.optJSONObject("repository")
        val isPrivate = repoObj?.optBoolean("private", false) ?: false
        val visibility = repoObj?.optString("visibility", if (isPrivate) "private" else "public") ?: "unknown"
        val isConsoleRepo = repoSlug.equals(DEFAULT_CONSOLE_REPO, ignoreCase = true)
        val privacyAlert = if (isConsoleRepo && !isPrivate) {
            "CRITICAL: $repoSlug is NOT marked private!"
        } else null
        val overview = repoObj?.let {
            GitHubRepoOverview(
                fullName = it.optString("full_name", repoSlug),
                isPrivate = isPrivate,
                visibility = visibility,
                stars = it.optInt("stargazers_count", 0),
                forks = it.optInt("forks_count", 0),
                watchers = it.optInt("watchers_count", 0),
                openIssuesCount = it.optInt("open_issues_count", 0),
                defaultBranch = it.optString("default_branch", "main"),
                updatedAtIso = it.optString("pushed_at").takeIf { s -> s.isNotBlank() },
                htmlUrl = it.optString("html_url"),
                privacyAlert = privacyAlert
            )
        }
        return GitHubRepoSnapshot(
            repoSlug = repoSlug,
            overview = overview,
            issues = parseGitHubIssues(json.optJSONArray("issues") ?: JSONArray()),
            pullRequests = parseGitHubPrs(json.optJSONArray("pullRequests") ?: JSONArray()),
            commits = parseGitHubCommits(json.optJSONArray("commits") ?: JSONArray()),
            workflowRuns = parseWorkflowRuns(json.optJSONArray("workflows") ?: JSONArray(), repoSlug),
            rateLimitRemaining = json.takeIf { it.has("rateRemaining") && !it.isNull("rateRemaining") }?.optInt("rateRemaining"),
            syncedAtIso = isoUtc(nowMs())
        )
    }

    suspend fun fetchWorkflowRunJobs(repoSlug: String, runId: String): List<WorkflowJobInfo> {
        val json = authorizedRequest("GET", "/v1/admin/github/runs/$runId/jobs?repo=$repoSlug")
        val arr = json.optJSONArray("jobs") ?: JSONArray()
        return buildList {
            for (i in 0 until arr.length()) {
                val j = arr.optJSONObject(i) ?: continue
                val steps = j.optJSONArray("steps") ?: JSONArray()
                val failedSteps = buildList {
                    for (k in 0 until steps.length()) {
                        val st = steps.optJSONObject(k) ?: continue
                        if (st.optString("conclusion") == "failure") add(st.optString("name"))
                    }
                }
                add(
                    WorkflowJobInfo(
                        id = j.optLong("id").toString(),
                        name = j.optString("name"),
                        status = j.optString("status"),
                        conclusion = j.optString("conclusion").ifBlank { j.optString("status") },
                        failedSteps = failedSteps,
                        htmlUrl = j.optString("html_url")
                    )
                )
            }
        }
    }

    suspend fun createGitHubIssue(repoSlug: String, title: String, body: String): Int {
        val res = authorizedRequest(
            "POST",
            "/v1/admin/github/issues",
            JSONObject().apply {
                put("repo", repoSlug)
                put("title", title)
                put("body", body)
            }
        )
        return res.optJSONObject("data")?.optInt("number", 0) ?: 0
    }

    suspend fun updateGitHubIssueState(repoSlug: String, issueNumber: Int, open: Boolean) {
        authorizedRequest(
            "PATCH",
            "/v1/admin/github/issues/$issueNumber",
            JSONObject().apply {
                put("repo", repoSlug)
                put("state", if (open) "open" else "closed")
            }
        )
    }

    suspend fun commentOnGitHubIssue(repoSlug: String, issueNumber: Int, body: String) {
        authorizedRequest(
            "POST",
            "/v1/admin/github/issues/$issueNumber/comments",
            JSONObject().apply {
                put("repo", repoSlug)
                put("body", body)
            }
        )
    }

    suspend fun fetchInfraSnapshot(
        integrity: InstalledApkIntegrity?,
        fcmInitialized: Boolean,
        crashlyticsOptedIn: Boolean,
        analyticsOptedIn: Boolean
    ): InfraMonitorSnapshot {
        val json = authorizedRequest("GET", "/v1/admin/infra")
        val checkedIso = isoUtc(json.optLong("checkedAtMs", nowMs()))
        val ghObj = json.optJSONObject("githubApi")
        val d1Obj = json.optJSONObject("d1Database")
        val gaObj = json.optJSONObject("ga4Sync")
        val rcObj = json.optJSONObject("remoteConfigSync")
        val remaining = ghObj?.takeIf { it.has("remaining") && !it.isNull("remaining") }?.optInt("remaining")
        val limit = ghObj?.takeIf { it.has("limit") && !it.isNull("limit") }?.optInt("limit")

        val services = buildList {
            add(
                InfraServiceStatus(
                    id = "worker",
                    name = "Cloudflare Worker Mission Control",
                    state = "healthy",
                    probeKind = InfraProbeKind.VERIFIED_REMOTE,
                    summary = "Zero-config HTTPS API connected (${d1Obj?.optInt("activeDevices", 1)} enrolled device)",
                    detail = "D1 SQLite feedback rows: ${d1Obj?.optInt("feedbackRows", 0)}. Free tier (100k req/day).",
                    checkedAtIso = checkedIso
                )
            )
            add(
                InfraServiceStatus(
                    id = "github",
                    name = "GitHub Server Auth (${ghObj?.optString("authMode", "app")})",
                    state = if (ghObj?.optString("status") == "verified_remote") "healthy" else "warning",
                    probeKind = InfraProbeKind.VERIFIED_REMOTE,
                    summary = "Authenticated server-side; zero PAT on device",
                    quotaText = if (remaining != null && limit != null) "$remaining / $limit remaining" else null,
                    checkedAtIso = checkedIso
                )
            )
            add(
                InfraServiceStatus(
                    id = "ga4",
                    name = "GA4 Analytics (Keyless WIF)",
                    state = if (gaObj?.optString("status") == "cached_sync") "healthy" else "warning",
                    probeKind = InfraProbeKind.VERIFIED_REMOTE,
                    summary = if (gaObj?.optString("status") == "cached_sync") {
                        "Synced every 3h via GitHub Actions WIF"
                    } else {
                        "Awaiting first WIF sync"
                    },
                    checkedAtIso = gaObj?.optLong("syncedAtMs", 0L)?.takeIf { it > 0L }?.let(::isoUtc)
                )
            )
            add(
                InfraServiceStatus(
                    id = "remote_config",
                    name = "Firebase Remote Config",
                    state = if (rcObj?.optString("status") == "cached_sync") "healthy" else "warning",
                    probeKind = InfraProbeKind.VERIFIED_REMOTE,
                    summary = if (rcObj?.optString("status") == "cached_sync") {
                        "Template cached; publish/rollback WIF ready"
                    } else {
                        "Awaiting template sync"
                    },
                    checkedAtIso = rcObj?.optLong("syncedAtMs", 0L)?.takeIf { it > 0L }?.let(::isoUtc)
                )
            )
            add(
                InfraServiceStatus(
                    id = "fcm",
                    name = "Firebase Messaging & Crashlytics SDK",
                    state = if (fcmInitialized) "healthy" else "warning",
                    probeKind = InfraProbeKind.LOCAL_SDK,
                    summary = "On-device SDK initialized; crash opt-in=$crashlyticsOptedIn, analytics opt-in=$analyticsOptedIn",
                    detail = json.optString("crashlyticsNote"),
                    checkedAtIso = checkedIso
                )
            )
            integrity?.let {
                add(
                    InfraServiceStatus(
                        id = "apk_integrity",
                        name = "Signing Key & Binary Integrity",
                        state = if (it.matchesOriginalCert) "healthy" else "error",
                        probeKind = InfraProbeKind.LOCAL_SDK,
                        summary = it.statusSummary,
                        checkedAtIso = checkedIso
                    )
                )
            }
        }
        return InfraMonitorSnapshot(
            services = services,
            githubRateLimit = limit,
            githubRateRemaining = remaining,
            oauthAutoRefreshReady = true,
            lastSyncIso = checkedIso
        )
    }

    suspend fun fetchAuditLogs(): List<AuditLogEntry> {
        val json = authorizedRequest("GET", "/v1/admin/audit-logs")
        return parseAuditArray(json.optJSONArray("items") ?: JSONArray())
    }

    suspend fun fetchEnrolledDevices(): Pair<String, List<EnrolledDeviceInfo>> {
        val json = authorizedRequest("GET", "/v1/admin/devices")
        val currentId = json.optString("currentDeviceId")
        val arr = json.optJSONArray("devices") ?: JSONArray()
        val list = buildList {
            for (i in 0 until arr.length()) {
                val d = arr.optJSONObject(i) ?: continue
                val id = d.optString("device_id")
                add(
                    EnrolledDeviceInfo(
                        deviceId = id,
                        label = d.optString("label", "Device"),
                        keyAlgorithm = d.optString("key_algorithm", "ECDSA_P256_SHA256"),
                        hardwareBacked = d.optInt("hardware_backed", 0) == 1,
                        attestationSummary = d.optString("attestation_summary"),
                        createdAtMs = d.optLong("created_at", 0L),
                        lastSeenAtMs = d.optLong("last_seen_at", 0L),
                        revokedAtMs = d.takeIf { it.has("revoked_at") && !it.isNull("revoked_at") }?.optLong("revoked_at"),
                        isCurrentDevice = id == currentId
                    )
                )
            }
        }
        return currentId to list
    }

    suspend fun revokeDevice(targetDeviceId: String): Boolean {
        val stepUp = requestStepUpProof("revoke_device:$targetDeviceId")
        authorizedRequest(
            "POST",
            "/v1/admin/devices/$targetDeviceId/revoke",
            JSONObject().apply {
                put("challengeId", stepUp.challengeId)
                put("signature", stepUp.signature)
            }
        )
        if (targetDeviceId == vault.getState().enrolledDeviceId) {
            vault.clearBackendEnrollment()
            return true
        }
        return false
    }

    suspend fun toggleKillSwitch(target: KillSwitchTarget, enabled: Boolean): AutomationKillSwitches {
        val targetKey = if (target == KillSwitchTarget.CUSTOM_BROADCAST) "custom" else "release"
        val stepUp = requestStepUpProof("kill_switch:$targetKey:$enabled")
        val json = authorizedRequest(
            "POST",
            "/v1/admin/kill-switches",
            JSONObject().apply {
                put("target", targetKey)
                put("enabled", enabled)
                put("challengeId", stepUp.challengeId)
                put("signature", stepUp.signature)
            }
        )
        val ksObj = json.optJSONObject("killSwitches")
        return AutomationKillSwitches(
            customBroadcastEnabled = ksObj?.optNullableBoolean("customBroadcastsEnabled"),
            releaseAutomationEnabled = ksObj?.optNullableBoolean("releaseNotificationsEnabled")
        )
    }

    private fun parseGa4SnapshotWrapper(
        wrapper: JSONObject?,
        dateRange: AnalyticsDateRange,
        fallbackIso: String
    ): AnalyticsSourceState {
        if (wrapper == null) {
            return AnalyticsSourceState.Unavailable(
                summary = "Waiting for scheduled keyless WIF analytics sync.",
                hasPropertyId = true,
                hasToken = true,
                canSignInWithGoogle = false
            )
        }
        val status = wrapper.optString("status")
        val syncedMs = wrapper.optLong("syncedAt", 0L)
        val syncedIso = if (syncedMs > 0L) isoUtc(syncedMs) else fallbackIso
        if (status == "error") {
            return AnalyticsSourceState.Error(
                message = wrapper.optString("errorMessage").ifBlank { "Analytics sync reported an error." },
                syncedAtIso = syncedIso
            )
        }
        val p = wrapper.optJSONObject("payload") ?: return AnalyticsSourceState.Empty(dateRange, syncedIso)
        val activeUsers = p.optLong("activeUsers", 0L)
        val newUsers = p.optLong("newUsers", 0L)
        if (activeUsers == 0L && newUsers == 0L) {
            return AnalyticsSourceState.Empty(dateRange, syncedIso)
        }
        val growthSeries = buildList {
            val arr = p.optJSONArray("growthSeries") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val pt = arr.optJSONObject(i) ?: continue
                add(
                    AnalyticsDailyPoint(
                        dateLabel = pt.optString("date"),
                        activeUsers = pt.optLong("activeUsers", 0L),
                        newUsers = pt.optLong("newUsers", 0L)
                    )
                )
            }
        }
        val dau = p.optLong("dau", 0L)
        val wau = p.optLong("wau", 0L)
        val mau = p.optLong("mau", activeUsers)
        val returningUsers = p.optLong("returningUsers", (activeUsers - newUsers).coerceAtLeast(0L))
        val exc = p.optJSONObject("exceptions")
        val breakdownArr = exc?.optJSONArray("breakdown") ?: JSONArray()
        val crashesByVersion = buildList {
            for (i in 0 until breakdownArr.length()) {
                val r = breakdownArr.optJSONObject(i) ?: continue
                add(AnalyticsRankedItem(r.optString("appVersion"), r.optLong("exceptionCount"), r.optLong("affectedUsers")))
            }
        }
        val crashesByOs = buildList {
            for (i in 0 until breakdownArr.length()) {
                val r = breakdownArr.optJSONObject(i) ?: continue
                add(AnalyticsRankedItem(r.optString("osVersion"), r.optLong("exceptionCount"), r.optLong("affectedUsers")))
            }
        }
        val widgetsList = parseRankedCountList(p.optJSONArray("widgets"))
        return AnalyticsSourceState.Ready(
            AnalyticsDashboardSnapshot(
                dateRange = dateRange,
                activeUsers = activeUsers,
                newUsers = newUsers,
                returningRatePercent = p.optDouble("retentionRatePercent", 0.0).toInt(),
                avgEngagementSeconds = p.optLong("avgEngagementSeconds", 0L),
                growthTrend = growthSeries,
                popularFeatures = parseRankedCountList(p.optJSONArray("features")),
                appVersions = parseRankedCountList(p.optJSONArray("appVersions")),
                updateAdoptions = 0L,
                syncedAtIso = syncedIso,
                totalUsers = activeUsers,
                dau = dau,
                wau = wau,
                mau = mau,
                sessions = p.optLong("sessions", 0L),
                returningUsers = returningUsers,
                dauPerMauPercent = if (mau > 0L) ((dau * 100L) / mau).toInt() else 0,
                wauPerMauPercent = if (mau > 0L) ((wau * 100L) / mau).toInt() else 0,
                countries = parseRankedCountList(p.optJSONArray("countries")),
                androidVersions = parseRankedCountList(p.optJSONArray("androidVersions")),
                deviceModels = parseRankedCountList(p.optJSONArray("deviceModels")),
                themePopularity = parseRankedCountList(p.optJSONArray("themes")),
                widgetUsageCount = widgetsList.sumOf { it.count },
                widgetUsers = widgetsList.sumOf { it.users },
                releaseChannels = parseRankedCountList(p.optJSONArray("channels")),
                crashFreeUsersPercent = exc?.takeIf { it.has("crashFreeObservedPercent") && !it.isNull("crashFreeObservedPercent") }?.optDouble("crashFreeObservedPercent"),
                crashAffectedUsers = exc?.optLong("affectedUsers", 0L) ?: 0L,
                crashEventCount = exc?.optLong("exceptionEvents", 0L) ?: 0L,
                crashesByAppVersion = crashesByVersion,
                crashesByOsVersion = crashesByOs,
                quotaHourlyRemaining = p.takeIf { it.has("quotaRemainingTokensPerHour") && !it.isNull("quotaRemainingTokensPerHour") }?.optInt("quotaRemainingTokensPerHour"),
                quotaDailyRemaining = p.takeIf { it.has("quotaRemainingTokensPerDay") && !it.isNull("quotaRemainingTokensPerDay") }?.optInt("quotaRemainingTokensPerDay"),
                isCached = false
            )
        )
    }

    private fun parseRemoteConfigWrapper(wrapper: JSONObject?, fallbackIso: String): RemoteConfigSourceState {
        if (wrapper == null) {
            return RemoteConfigSourceState.Unavailable(
                reason = "Remote Config snapshot will populate on the next WIF sync.",
                canSignInWithGoogle = false
            )
        }
        val syncedMs = wrapper.optLong("syncedAt", 0L)
        val syncedIso = if (syncedMs > 0L) isoUtc(syncedMs) else fallbackIso
        if (wrapper.optString("status") == "error") {
            return RemoteConfigSourceState.Error(wrapper.optString("errorMessage"), syncedIso)
        }
        val p = wrapper.optJSONObject("payload") ?: return RemoteConfigSourceState.Error("Empty Remote Config snapshot", syncedIso)
        val paramsArr = p.optJSONArray("parameters") ?: JSONArray()
        val params = buildList {
            for (i in 0 until paramsArr.length()) {
                val item = paramsArr.optJSONObject(i) ?: continue
                val key = item.optString("key")
                if (key.isNotBlank()) {
                    add(
                        RemoteConfigParam(
                            key = key,
                            valueType = item.optString("valueType", "STRING"),
                            defaultValue = item.optString("defaultValue"),
                            description = item.optString("description"),
                            isSafeKey = RemoteAppConfigPolicy.isSafeParameterKey(key)
                        )
                    )
                }
            }
        }
        val versArr = p.optJSONArray("versions") ?: JSONArray()
        val versions = buildList {
            for (i in 0 until versArr.length()) {
                val v = versArr.optJSONObject(i) ?: continue
                add(
                    RemoteConfigVersionInfo(
                        versionNumber = v.optString("versionNumber"),
                        updateTimeIso = v.optString("updateTime"),
                        updateOrigin = v.optString("updateOrigin"),
                        description = v.optString("description")
                    )
                )
            }
        }
        return RemoteConfigSourceState.Ready(
            etag = p.optString("etag", "*"),
            versionNumber = p.optString("versionNumber", "1"),
            parameters = params,
            conditions = emptyList(),
            versions = versions,
            syncedAtIso = syncedIso
        )
    }

    private data class ParsedReleasesPayload(
        val ok: Boolean,
        val rateRemaining: Int?,
        val releases: List<ReleaseInfo>,
        val releaseBroadcasts: List<BroadcastHistoryEntry>
    )

    private fun parseReleasesPayload(json: JSONObject): ParsedReleasesPayload {
        val arr = json.optJSONArray("releases") ?: JSONArray()
        val list = buildList {
            for (i in 0 until arr.length()) {
                val r = arr.optJSONObject(i) ?: continue
                val channel = r.optString("channel", "stable")
                val isPrivate = channel == "private_dev"
                val tag = r.optString("tag")
                val bullets = r.optString("notes")
                    .lines()
                    .map { it.trim() }
                    .filter { it.startsWith("- ") || it.startsWith("* ") }
                    .map { it.removePrefix("- ").removePrefix("* ").trim() }
                    .take(6)
                add(
                    ReleaseInfo(
                        tag = tag,
                        version = tag.removePrefix("v"),
                        versionCode = r.optLong("versionCode", 0L).takeIf { it > 0L },
                        title = r.optString("name", tag),
                        channel = when (channel) {
                            "private_dev" -> "Private Dev"
                            "beta" -> "Beta"
                            else -> "Stable"
                        },
                        prerelease = r.optBoolean("isPrerelease", false),
                        publishedAt = r.optString("publishedAt").takeIf { it.isNotBlank() },
                        htmlUrl = r.optString("htmlUrl"),
                        assetName = r.optString("apkName").takeIf { it.isNotBlank() },
                        assetSizeBytes = r.optLong("apkSizeBytes", 0L).takeIf { it > 0L },
                        downloadCount = r.optInt("downloadCount", 0),
                        assetDownloadUrl = null,
                        sha256 = r.optString("apkSha256").takeIf { it.isNotBlank() },
                        notificationStatus = r.optString("ledgerStatus"),
                        changelogBullets = bullets,
                        repoSlug = r.optString("repository", DEFAULT_DISTRIBUTION_REPO),
                        isPrivate = isPrivate,
                        rawBody = r.optString("notes")
                    )
                )
            }
        }
        return ParsedReleasesPayload(
            ok = json.optBoolean("ok", true),
            rateRemaining = json.takeIf { it.has("rateRemaining") && !it.isNull("rateRemaining") }?.optInt("rateRemaining"),
            releases = list,
            releaseBroadcasts = emptyList()
        )
    }

    private fun parseRankedCountList(arr: JSONArray?): List<AnalyticsRankedItem> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val c = o.optLong("count", 0L)
                add(AnalyticsRankedItem(o.optString("label"), c, c))
            }
        }
    }

    private fun parseAuditArray(arr: JSONArray): List<AuditLogEntry> = buildList {
        for (i in 0 until arr.length()) {
            val a = arr.optJSONObject(i) ?: continue
            val ts = a.optLong("timestamp", 0L)
            add(
                AuditLogEntry(
                    id = a.optString("id"),
                    timestampIso = if (ts > 0L) isoUtc(ts) else a.optString("timestampIso"),
                    actor = a.optString("actor", "backend"),
                    action = a.optString("action"),
                    target = a.optString("category"),
                    status = a.optString("severity", "ok"),
                    summary = a.optString("summary")
                )
            )
        }
    }

    private fun parseTemplates(arr: JSONArray): List<BroadcastTemplate> = buildList {
        for (i in 0 until arr.length()) {
            val t = arr.optJSONObject(i) ?: continue
            add(
                BroadcastTemplate(
                    id = t.optString("id"),
                    name = t.optString("name"),
                    category = BroadcastCategory.fromApiValue(t.optString("category")) ?: BroadcastCategory.ANNOUNCEMENT,
                    audience = BroadcastAudience.fromApiValue(t.optString("audience")) ?: BroadcastAudience.BETA,
                    route = BroadcastDestination.fromApiValue(t.optString("route")) ?: BroadcastDestination.HOME,
                    title = t.optString("title"),
                    body = t.optString("body")
                )
            )
        }
    }

    private fun parseDrafts(arr: JSONArray): List<BroadcastDraft> = buildList {
        for (i in 0 until arr.length()) {
            val d = arr.optJSONObject(i) ?: continue
            val ts = d.optLong("updated_at", 0L)
            add(
                BroadcastDraft(
                    id = d.optString("id"),
                    category = BroadcastCategory.fromApiValue(d.optString("category")) ?: BroadcastCategory.ANNOUNCEMENT,
                    audience = BroadcastAudience.fromApiValue(d.optString("audience")) ?: BroadcastAudience.BETA,
                    route = BroadcastDestination.fromApiValue(d.optString("route")) ?: BroadcastDestination.HOME,
                    title = d.optString("title"),
                    body = d.optString("body"),
                    updatedAtIso = if (ts > 0L) isoUtc(ts) else ""
                )
            )
        }
    }

    private fun parseAutoScheduled(arr: JSONArray): List<BackendAutoScheduledBroadcast> = buildList {
        for (i in 0 until arr.length()) {
            val s = arr.optJSONObject(i) ?: continue
            add(
                BackendAutoScheduledBroadcast(
                    id = s.optString("id"),
                    broadcastId = s.optString("broadcast_id"),
                    title = s.optString("title"),
                    body = s.optString("body"),
                    category = BroadcastCategory.fromApiValue(s.optString("category")) ?: BroadcastCategory.ANNOUNCEMENT,
                    audience = BroadcastAudience.fromApiValue(s.optString("audience")) ?: BroadcastAudience.BETA,
                    route = BroadcastDestination.fromApiValue(s.optString("route")) ?: BroadcastDestination.HOME,
                    scheduledAtMs = s.optLong("scheduled_at"),
                    status = s.optString("status", "scheduled"),
                    reason = s.optString("reason"),
                    dispatchedAtMs = s.takeIf { it.has("dispatched_at") && !it.isNull("dispatched_at") }?.optLong("dispatched_at"),
                    workflowUrl = s.optString("workflow_url").takeIf { it.isNotBlank() },
                    errorMessage = s.optString("error_message").takeIf { it.isNotBlank() }
                )
            )
        }
    }

    private fun parseBackendHistory(arr: JSONArray): List<BroadcastHistoryEntry> = buildList {
        for (i in 0 until arr.length()) {
            val h = arr.optJSONObject(i) ?: continue
            val aud = BroadcastAudience.fromApiValue(h.optString("audience")) ?: BroadcastAudience.BETA
            add(
                BroadcastHistoryEntry(
                    id = h.optString("id"),
                    kind = h.optString("kind", "custom"),
                    category = h.optString("category", "general"),
                    broadcastId = h.optString("broadcast_id"),
                    title = h.optString("title"),
                    body = h.optString("body"),
                    audience = h.optString("audience", "beta"),
                    topics = aud.topics,
                    route = h.optString("route", "home"),
                    status = h.optString("status", "unknown"),
                    statusLabel = h.optString("status_label", h.optString("status")),
                    timestampIso = isoUtc(h.optLong("created_at", nowMs())),
                    note = h.optString("note"),
                    workflowUrl = h.optString("workflow_url").takeIf { it.isNotBlank() }
                )
            )
        }
    }

    private fun parseWorkflowRuns(arr: JSONArray, repoSlug: String): List<WorkflowRunInfo> = buildList {
        for (i in 0 until arr.length()) {
            val r = arr.optJSONObject(i) ?: continue
            add(
                WorkflowRunInfo(
                    id = r.optLong("id").toString(),
                    repo = repoSlug,
                    workflowName = r.optString("name"),
                    eventTitle = r.optString("display_title", r.optString("name")),
                    status = r.optString("status"),
                    conclusion = r.optString("conclusion").ifBlank { r.optString("status") },
                    createdAt = r.optString("created_at").takeIf { it.isNotBlank() },
                    htmlUrl = r.optString("html_url"),
                    branchOrTag = r.optString("head_branch", "main")
                )
            )
        }
    }

    private fun parseGitHubIssues(arr: JSONArray): List<GitHubIssueItem> = buildList {
        for (i in 0 until arr.length()) {
            val it = arr.optJSONObject(i) ?: continue
            val labelsArr = it.optJSONArray("labels") ?: JSONArray()
            val labels = buildList {
                for (k in 0 until labelsArr.length()) {
                    val l = labelsArr.optJSONObject(k)?.optString("name").orEmpty()
                    if (l.isNotBlank()) add(l)
                }
            }
            add(
                GitHubIssueItem(
                    number = it.optInt("number"),
                    title = it.optString("title"),
                    body = it.optString("body"),
                    state = it.optString("state", "open"),
                    author = it.optJSONObject("user")?.optString("login") ?: "unknown",
                    labels = labels,
                    commentsCount = it.optInt("comments", 0),
                    updatedAtIso = it.optString("updated_at").takeIf { s -> s.isNotBlank() },
                    htmlUrl = it.optString("html_url")
                )
            )
        }
    }

    private fun parseGitHubPrs(arr: JSONArray): List<GitHubPrItem> = buildList {
        for (i in 0 until arr.length()) {
            val pr = arr.optJSONObject(i) ?: continue
            add(
                GitHubPrItem(
                    number = pr.optInt("number"),
                    title = pr.optString("title"),
                    state = pr.optString("state", "open"),
                    draft = pr.optBoolean("draft", false),
                    author = pr.optJSONObject("user")?.optString("login") ?: "unknown",
                    updatedAtIso = pr.optString("updated_at").takeIf { s -> s.isNotBlank() },
                    htmlUrl = pr.optString("html_url")
                )
            )
        }
    }

    private fun parseGitHubCommits(arr: JSONArray): List<GitHubCommitItem> = buildList {
        for (i in 0 until arr.length()) {
            val c = arr.optJSONObject(i) ?: continue
            val commitObj = c.optJSONObject("commit")
            val msg = commitObj?.optString("message").orEmpty().lineSequence().firstOrNull().orEmpty()
            add(
                GitHubCommitItem(
                    shaShort = c.optString("sha").take(7),
                    messageHeadline = msg,
                    authorName = commitObj?.optJSONObject("author")?.optString("name") ?: "developer",
                    timestampIso = commitObj?.optJSONObject("author")?.optString("date"),
                    htmlUrl = c.optString("html_url")
                )
            )
        }
    }

    private fun parsePriorityFromBackend(raw: String): FeedbackPriority = when (raw.trim().lowercase()) {
        "critical" -> FeedbackPriority.CRITICAL
        "high" -> FeedbackPriority.HIGH
        "low" -> FeedbackPriority.LOW
        else -> FeedbackPriority.NORMAL
    }

    private fun priorityToBackend(priority: FeedbackPriority): String = when (priority) {
        FeedbackPriority.CRITICAL -> "critical"
        FeedbackPriority.HIGH -> "high"
        FeedbackPriority.NORMAL -> "medium"
        FeedbackPriority.LOW -> "low"
    }

    private fun extractError(rawBody: String): String {
        if (rawBody.isBlank()) return ""
        return runCatching {
            val obj = JSONObject(rawBody)
            obj.optString("error").ifBlank { obj.optString("message") }
        }.getOrDefault("")
    }

    private fun JSONObject.optNullableBoolean(key: String): Boolean? =
        if (has(key) && !isNull(key)) optBoolean(key) else null

    private fun isoUtc(epochMs: Long): String =
        DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC).format(Instant.ofEpochMilli(epochMs))
}
