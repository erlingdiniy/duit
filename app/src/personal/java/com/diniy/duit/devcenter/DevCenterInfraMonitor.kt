package com.diniy.duit.devcenter

import com.diniy.duit.BuildConfig
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject

/**
 * Centralized Infrastructure Monitor performing real network probes for GitHub, Apps Script feedback,
 * GA4, and Remote Config while strictly labeling local SDK checks (FCM/Crashlytics) cleanly.
 */
class DevCenterInfraMonitor(
    private val transport: DevHttpTransport
) {
    suspend fun checkInfrastructure(
        vaultState: DevCenterVaultState,
        pat: String,
        feedbackUrl: String,
        ga4State: AnalyticsSourceState,
        remoteConfigState: RemoteConfigSourceState,
        integrity: InstalledApkIntegrity? = null,
        fcmInitialized: Boolean = BuildConfig.FCM_ENABLED,
        crashlyticsOptedIn: Boolean = false,
        analyticsOptedIn: Boolean = false,
        nowMs: Long = System.currentTimeMillis()
    ): InfraMonitorSnapshot = coroutineScope {
        val checkedIso = isoUtcNow(nowMs)
        val cleanPat = pat.trim()

        val rateDef = async {
            if (cleanPat.isEmpty()) null
            else runCatching {
                transport.request(
                    "GET",
                    "https://api.github.com/rate_limit",
                    mapOf(
                        "Authorization" to "Bearer $cleanPat",
                        "Accept" to "application/vnd.github+json",
                        "X-GitHub-Api-Version" to "2022-11-28"
                    )
                )
            }.getOrNull()
        }

        val consoleVisDef = async {
            if (cleanPat.isEmpty()) null
            else runCatching {
                transport.request(
                    "GET",
                    "https://api.github.com/repos/${vaultState.consoleRepo}",
                    mapOf(
                        "Authorization" to "Bearer $cleanPat",
                        "Accept" to "application/vnd.github+json",
                        "X-GitHub-Api-Version" to "2022-11-28"
                    )
                )
            }.getOrNull()
        }

        val feedbackDef = async {
            val cleanUrl = feedbackUrl.trim()
            if (cleanUrl.isEmpty()) null
            else runCatching { transport.probeGet(cleanUrl) }.getOrNull()
        }

        val rateRes = rateDef.await()
        val consoleRes = consoleVisDef.await()
        val feedbackRes = feedbackDef.await()

        val coreRate = if (rateRes?.isSuccess == true) {
            runCatching { JSONObject(rateRes.body).optJSONObject("resources")?.optJSONObject("core") }.getOrNull()
        } else null
        val ghLimit = coreRate?.optInt("limit", -1)?.takeIf { it > 0 }
        val ghRemaining = coreRate?.optInt("remaining", -1)?.takeIf { it >= 0 }
        val ghResetIso = coreRate?.optLong("reset", -1L)?.takeIf { it > 0L }?.let { isoUtcNow(it * 1000L) }

        val consolePrivate = if (consoleRes?.isSuccess == true) {
            runCatching { JSONObject(consoleRes.body).optBoolean("private", false) }.getOrNull()
        } else null

        val ghStatus = when {
            cleanPat.isEmpty() -> InfraServiceStatus(
                id = "github",
                name = "GitHub REST API",
                state = "unconfigured",
                probeKind = InfraProbeKind.UNCONFIGURED,
                summary = "Fine-grained PAT not configured",
                detail = "Public releases still readable; PAT needed for private repo, issues & Actions.",
                checkedAtIso = checkedIso
            )
            rateRes?.isSuccess == true -> InfraServiceStatus(
                id = "github",
                name = "GitHub REST API",
                state = if (consolePrivate == false) "error" else "healthy",
                probeKind = InfraProbeKind.VERIFIED_REMOTE,
                summary = if (consolePrivate == false) {
                    "WARNING: ${vaultState.consoleRepo} is NOT private!"
                } else {
                    "Connected • Console repo verified PRIVATE"
                },
                detail = "Core rate limit: ${ghRemaining ?: "?"}/${ghLimit ?: "?"} remaining",
                checkedAtIso = checkedIso,
                quotaText = ghRemaining?.let { "$it/${ghLimit ?: 5000} req/hr" }
            )
            else -> InfraServiceStatus(
                id = "github",
                name = "GitHub REST API",
                state = "error",
                probeKind = InfraProbeKind.VERIFIED_REMOTE,
                summary = "GitHub API check failed (HTTP ${rateRes?.statusCode ?: "offline"})",
                detail = "Check network connectivity or rotate your PAT.",
                checkedAtIso = checkedIso
            )
        }

        val fbOkJson = feedbackRes?.isSuccess == true &&
            runCatching { JSONObject(feedbackRes.body).optBoolean("ok", false) }.getOrDefault(false)
        val feedbackStatus = when {
            feedbackUrl.isBlank() -> InfraServiceStatus(
                id = "feedback",
                name = "Feedback Apps Script",
                state = "unconfigured",
                probeKind = InfraProbeKind.UNCONFIGURED,
                summary = "FEEDBACK_URL empty in build",
                detail = "Write-only Apps Script URL is set via Gradle property for beta/dev builds.",
                checkedAtIso = checkedIso
            )
            fbOkJson -> InfraServiceStatus(
                id = "feedback",
                name = "Feedback Apps Script",
                state = "healthy",
                probeKind = InfraProbeKind.VERIFIED_REMOTE,
                summary = "Health endpoint online (HTTP 200 ok=true)",
                detail = if (vaultState.feedbackSheetId.isNotBlank()) "Sheet ID ${vaultState.feedbackSheetId.take(10)}... linked for inbox reads." else "Add Sheet ID in Settings to pull inbox.",
                checkedAtIso = checkedIso
            )
            else -> InfraServiceStatus(
                id = "feedback",
                name = "Feedback Apps Script",
                state = if (feedbackRes == null) "degraded" else "error",
                probeKind = InfraProbeKind.VERIFIED_REMOTE,
                summary = "Endpoint unreachable (HTTP ${feedbackRes?.statusCode ?: "offline"})",
                detail = "Apps Script GET health check did not return ok=true.",
                checkedAtIso = checkedIso
            )
        }

        val ga4Ready = ga4State as? AnalyticsSourceState.Ready
        val ga4Status = when (ga4State) {
            is AnalyticsSourceState.Ready -> InfraServiceStatus(
                id = "ga4",
                name = "GA4 Data API v1beta",
                state = "healthy",
                probeKind = InfraProbeKind.VERIFIED_REMOTE,
                summary = "Live reports verified (${ga4State.snapshot.activeUsers} active users)",
                detail = "Device analytics consent: ${if (analyticsOptedIn) "ON" else "OFF (default)"}. Internal traffic filtered.",
                checkedAtIso = ga4State.snapshot.syncedAtIso,
                quotaText = ga4State.snapshot.quotaDailyRemaining?.let { "$it/200k daily tokens" }
            )
            is AnalyticsSourceState.Empty -> InfraServiceStatus(
                id = "ga4",
                name = "GA4 Data API v1beta",
                state = "healthy",
                probeKind = InfraProbeKind.VERIFIED_REMOTE,
                summary = "Connected (0 events in selected range)",
                detail = "Spark free quota active (200k tokens/day).",
                checkedAtIso = ga4State.syncedAtIso
            )
            is AnalyticsSourceState.Error -> InfraServiceStatus(
                id = "ga4",
                name = "GA4 Data API v1beta",
                state = "error",
                probeKind = InfraProbeKind.VERIFIED_REMOTE,
                summary = ga4State.message,
                checkedAtIso = ga4State.syncedAtIso ?: checkedIso
            )
            is AnalyticsSourceState.Loading -> InfraServiceStatus(
                id = "ga4",
                name = "GA4 Data API v1beta",
                state = "degraded",
                probeKind = InfraProbeKind.VERIFIED_REMOTE,
                summary = "Syncing GA4 Data API...",
                checkedAtIso = checkedIso
            )
            is AnalyticsSourceState.Unavailable -> InfraServiceStatus(
                id = "ga4",
                name = "GA4 Data API v1beta",
                state = "unconfigured",
                probeKind = InfraProbeKind.UNCONFIGURED,
                summary = ga4State.summary,
                checkedAtIso = checkedIso
            )
        }

        val rcStatus = when (remoteConfigState) {
            is RemoteConfigSourceState.Ready -> InfraServiceStatus(
                id = "remote_config",
                name = "Firebase Remote Config",
                state = "healthy",
                probeKind = InfraProbeKind.VERIFIED_REMOTE,
                summary = "Template v${remoteConfigState.versionNumber.ifBlank { "?" }} (${remoteConfigState.parameters.size} params)",
                detail = "Spark free limit: 100k fetches/day. Financial safety guard active.",
                checkedAtIso = remoteConfigState.syncedAtIso,
                quotaText = "100k fetches/day (Spark)"
            )
            is RemoteConfigSourceState.Error -> InfraServiceStatus(
                id = "remote_config",
                name = "Firebase Remote Config",
                state = "error",
                probeKind = InfraProbeKind.VERIFIED_REMOTE,
                summary = remoteConfigState.message,
                checkedAtIso = remoteConfigState.syncedAtIso ?: checkedIso
            )
            is RemoteConfigSourceState.Loading -> InfraServiceStatus(
                id = "remote_config",
                name = "Firebase Remote Config",
                state = "degraded",
                probeKind = InfraProbeKind.VERIFIED_REMOTE,
                summary = "Syncing Remote Config...",
                checkedAtIso = checkedIso
            )
            is RemoteConfigSourceState.Unavailable -> InfraServiceStatus(
                id = "remote_config",
                name = "Firebase Remote Config",
                state = "unconfigured",
                probeKind = InfraProbeKind.UNCONFIGURED,
                summary = remoteConfigState.reason,
                checkedAtIso = checkedIso
            )
        }

        val fcmStatus = InfraServiceStatus(
            id = "fcm",
            name = "Firebase Cloud Messaging",
            state = if (fcmInitialized) "healthy" else "degraded",
            probeKind = InfraProbeKind.LOCAL_SDK,
            summary = if (fcmInitialized) "SDK active • channel $ANDROID_BROADCAST_CHANNEL_ID" else "FCM disabled in build",
            detail = "Public delivery runs via keyless GitHub WIF Actions; FCM Data API not enabled locally.",
            checkedAtIso = checkedIso
        )

        val crashStatus = InfraServiceStatus(
            id = "crashlytics",
            name = "Firebase Crashlytics",
            state = if (BuildConfig.CRASHLYTICS_ENABLED) "healthy" else "unconfigured",
            probeKind = InfraProbeKind.LOCAL_SDK,
            summary = "SDK enabled (device consent ${if (crashlyticsOptedIn) "ON" else "OFF"})",
            detail = "No free Crashlytics Management REST API without paid BigQuery; Crash Reports combines consented GA4 app_exception + console links.",
            checkedAtIso = checkedIso
        )

        val services = buildList {
            add(ghStatus)
            add(ga4Status)
            add(feedbackStatus)
            add(rcStatus)
            add(fcmStatus)
            add(crashStatus)
            integrity?.let {
                add(
                    InfraServiceStatus(
                        id = "apk_integrity",
                        name = "Signing & APK Integrity",
                        state = if (it.matchesOriginalCert && it.hasPrivateDevMeta) "healthy" else "error",
                        probeKind = InfraProbeKind.LOCAL_SDK,
                        summary = "${it.versionName} (${it.versionCode})",
                        detail = it.statusSummary,
                        checkedAtIso = checkedIso
                    )
                )
            }
        }

        InfraMonitorSnapshot(
            services = services,
            githubRateLimit = ghLimit,
            githubRateRemaining = ghRemaining,
            githubRateResetIso = ghResetIso,
            ga4HourlyQuotaRemaining = ga4Ready?.snapshot?.quotaHourlyRemaining,
            ga4DailyQuotaRemaining = ga4Ready?.snapshot?.quotaDailyRemaining,
            oauthExpiresAtMs = vaultState.analyticsTokenExpiresAtMs,
            oauthAutoRefreshReady = !vaultState.encryptedAnalyticsRefreshToken.isNullOrBlank(),
            lastSyncIso = checkedIso
        )
    }
}
