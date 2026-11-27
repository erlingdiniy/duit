package com.diniy.duit.devcenter

import org.json.JSONArray
import org.json.JSONObject

/**
 * Read-only Google Analytics Data API v1beta client for Developer Center Insights.
 * Queries only the aggregate property metrics configured explicitly by the developer
 * using a Keystore-sealed read-only OAuth bearer token (zero embedded secrets, zero billing).
 */
class DevCenterAnalyticsClient(
    private val transport: DevHttpTransport
) {
    suspend fun loadAnalytics(
        propertyId: String,
        analyticsAccessToken: String?,
        dateRange: AnalyticsDateRange,
        syncedAtIso: String,
        hasSavedCredentials: Boolean = false,
        canSignInWithGoogle: Boolean = false,
        tokenResolver: (suspend (forceRefresh: Boolean) -> String?)? = null
    ): AnalyticsSourceState {
        val cleanId = propertyId.trim()
        var activeToken = (tokenResolver?.invoke(false) ?: analyticsAccessToken)?.trim().orEmpty()
        var retriedRefresh = false

        if (cleanId.isEmpty() || activeToken.isEmpty()) {
            val summary = when {
                cleanId.isNotEmpty() && !hasSavedCredentials ->
                    "GA4 Property ID saved. Sign in with Google below, or open Firebase."
                cleanId.isNotEmpty() && hasSavedCredentials ->
                    "Google access expired. Sign in again in Settings."
                activeToken.isNotEmpty() || hasSavedCredentials ->
                    "Google access connected. Add numeric GA4 Property ID in Settings."
                else ->
                    "Connect Google Analytics read-only access in Settings or view charts in Firebase."
            }
            return AnalyticsSourceState.Unavailable(
                summary = summary,
                hasPropertyId = cleanId.isNotEmpty(),
                hasToken = activeToken.isNotEmpty(),
                canSignInWithGoogle = canSignInWithGoogle
            )
        }

        if (!DevCenterValidators.isValidGa4PropertyId(cleanId)) {
            return AnalyticsSourceState.Error(
                message = "GA4 Property ID must be a 6 to 15 digit numeric ID.",
                syncedAtIso = syncedAtIso
            )
        }

        val url = "https://analyticsdata.googleapis.com/v1beta/properties/$cleanId:runReport"
        val realtimeUrl = "https://analyticsdata.googleapis.com/v1beta/properties/$cleanId:runRealtimeReport"

        suspend fun postWithAutoRefresh(endpointUrl: String, body: String): DevHttpResponse {
            var res = postReport(endpointUrl, authHeaders(activeToken), body, activeToken)
            if (res.statusCode == 401 && !retriedRefresh && tokenResolver != null) {
                retriedRefresh = true
                val refreshed = tokenResolver.invoke(true)?.trim().orEmpty()
                if (refreshed.isNotEmpty() && refreshed != activeToken) {
                    activeToken = refreshed
                    res = postReport(endpointUrl, authHeaders(activeToken), body, activeToken)
                }
            }
            if (!res.isSuccess) {
                val detail = when (res.statusCode) {
                    401, 403 -> "GA4 authorization failed (HTTP ${res.statusCode}). Re-sign in with Google or refresh your read-only OAuth token."
                    404 -> "GA4 Property ID was not found (HTTP 404)."
                    429 -> "GA4 free quota rate limit reached (HTTP 429). Try again later."
                    else -> "GA4 API returned HTTP ${res.statusCode}."
                }
                throw IllegalStateException(DevCenterRedactor.redact(detail, listOf(activeToken)))
            }
            return res
        }

        return try {
            val overviewBody = buildReportRequest(
                startDate = dateRange.startDate,
                dimensions = listOf("date"),
                metrics = listOf("activeUsers", "newUsers", "userEngagementDuration")
            )
            val overviewRes = postWithAutoRefresh(url, overviewBody)
            val dailyPoints = parseDailyOverview(overviewRes.body)

            val featuresBody = buildReportRequest(
                startDate = dateRange.startDate,
                dimensions = listOf("customEvent:feature"),
                metrics = listOf("eventCount", "activeUsers"),
                eventNameFilter = "feature_used",
                limit = 10
            )
            val features = runCatching {
                val featuresRes = postWithAutoRefresh(url, featuresBody)
                parseRankedItems(featuresRes.body, metricFirstIsCount = true)
            }.getOrDefault(emptyList())

            val versionsBody = buildReportRequest(
                startDate = dateRange.startDate,
                dimensions = listOf("appVersion"),
                metrics = listOf("activeUsers", "sessions"),
                limit = 8
            )
            val versionsRes = postWithAutoRefresh(url, versionsBody)
            val versions = parseRankedItems(versionsRes.body, metricFirstIsCount = false)

            val updatesBody = buildReportRequest(
                startDate = dateRange.startDate,
                dimensions = listOf("eventName"),
                metrics = listOf("eventCount", "activeUsers"),
                eventNameFilter = "app_updated",
                limit = 1
            )
            val updatesRes = postWithAutoRefresh(url, updatesBody)
            val updateAdoptions = parseSingleCount(updatesRes.body)

            val realtimeTotals = runCatching {
                val rtTotalsBody = JSONObject().apply {
                    put("metrics", JSONArray().apply {
                        put(JSONObject().put("name", "activeUsers"))
                        put(JSONObject().put("name", "eventCount"))
                    })
                }.toString()
                parseRealtimeTotals(postWithAutoRefresh(realtimeUrl, rtTotalsBody).body)
            }.getOrDefault(0L to 0L)

            val realtimeEvents = runCatching {
                val rtEventsBody = JSONObject().apply {
                    put("dimensions", JSONArray().apply { put(JSONObject().put("name", "eventName")) })
                    put("metrics", JSONArray().apply { put(JSONObject().put("name", "eventCount")) })
                    put("limit", "10")
                }.toString()
                parseRankedItems(postWithAutoRefresh(realtimeUrl, rtEventsBody).body, metricFirstIsCount = true)
            }.getOrDefault(emptyList())

            val totalActive = dailyPoints.sumOf { it.activeUsers }
            val totalNew = dailyPoints.sumOf { it.newUsers }
            val totalEngagementSec = parseTotalEngagementSeconds(overviewRes.body)
            val (rtUsers, rtEventCount) = realtimeTotals

            if (totalActive == 0L && totalNew == 0L && features.isEmpty() && versions.isEmpty() &&
                rtUsers == 0L && rtEventCount == 0L && realtimeEvents.isEmpty()
            ) {
                AnalyticsSourceState.Empty(dateRange = dateRange, syncedAtIso = syncedAtIso)
            } else {
                val returningUsers = (totalActive - totalNew).coerceAtLeast(0L)
                val returningPercent = if (totalActive > 0L) {
                    ((returningUsers * 100L) / totalActive).toInt().coerceIn(0, 100)
                } else 0
                val avgEngagement = if (totalActive > 0L) (totalEngagementSec / totalActive).coerceAtLeast(0L) else 0L

                AnalyticsSourceState.Ready(
                    AnalyticsDashboardSnapshot(
                        dateRange = dateRange,
                        activeUsers = totalActive,
                        newUsers = totalNew,
                        returningRatePercent = returningPercent,
                        avgEngagementSeconds = avgEngagement,
                        growthTrend = dailyPoints.takeLast(7),
                        popularFeatures = features,
                        appVersions = versions,
                        updateAdoptions = updateAdoptions,
                        syncedAtIso = syncedAtIso,
                        realtimeActiveUsers = rtUsers,
                        realtimeEventCount = rtEventCount,
                        realtimeEvents = realtimeEvents
                    )
                )
            }
        } catch (err: Exception) {
            AnalyticsSourceState.Error(
                message = DevCenterRedactor.redact(
                    err.message ?: "Unable to fetch GA4 report.",
                    listOf(activeToken)
                ),
                syncedAtIso = syncedAtIso
            )
        }
    }

    private fun authHeaders(token: String): Map<String, String> = mapOf(
        "Authorization" to "Bearer $token",
        "Content-Type" to "application/json",
        "Accept" to "application/json"
    )

    private suspend fun postReport(
        url: String,
        headers: Map<String, String>,
        body: String,
        secret: String
    ): DevHttpResponse = transport.request(method = "POST", url = url, headers = headers, body = body)

    internal fun buildReportRequest(
        startDate: String,
        dimensions: List<String>,
        metrics: List<String>,
        eventNameFilter: String? = null,
        limit: Int = 90
    ): String = JSONObject().apply {
        put("dateRanges", JSONArray().apply {
            put(JSONObject().apply {
                put("startDate", startDate)
                put("endDate", "today")
            })
        })
        put("dimensions", JSONArray().apply {
            dimensions.forEach { d -> put(JSONObject().put("name", d)) }
        })
        put("metrics", JSONArray().apply {
            metrics.forEach { m -> put(JSONObject().put("name", m)) }
        })
        if (eventNameFilter != null) {
            put("dimensionFilter", JSONObject().apply {
                put("filter", JSONObject().apply {
                    put("fieldName", "eventName")
                    put("stringFilter", JSONObject().apply {
                        put("matchType", "EXACT")
                        put("value", eventNameFilter)
                    })
                })
            })
        }
        put("limit", limit.toString())
    }.toString()

    internal fun parseDailyOverview(rawJson: String): List<AnalyticsDailyPoint> {
        val rows = JSONObject(rawJson).optJSONArray("rows") ?: return emptyList()
        return buildList {
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val dateRaw = row.optJSONArray("dimensionValues")?.optJSONObject(0)?.optString("value").orEmpty()
                if (dateRaw.isBlank()) continue
                val formatted = if (dateRaw.length == 8) {
                    "${dateRaw.substring(0, 4)}-${dateRaw.substring(4, 6)}-${dateRaw.substring(6, 8)}"
                } else dateRaw
                val metrics = row.optJSONArray("metricValues")
                val active = metrics?.optJSONObject(0)?.optString("value")?.toLongOrNull() ?: 0L
                val newUsers = metrics?.optJSONObject(1)?.optString("value")?.toLongOrNull() ?: 0L
                add(AnalyticsDailyPoint(dateLabel = formatted, activeUsers = active, newUsers = newUsers))
            }
        }.sortedBy { it.dateLabel }
    }

    internal fun parseTotalEngagementSeconds(rawJson: String): Long {
        val rows = JSONObject(rawJson).optJSONArray("rows") ?: return 0L
        var total = 0.0
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            val sec = row.optJSONArray("metricValues")?.optJSONObject(2)?.optString("value")?.toDoubleOrNull() ?: 0.0
            total += sec
        }
        return total.toLong()
    }

    internal fun parseRankedItems(rawJson: String, metricFirstIsCount: Boolean): List<AnalyticsRankedItem> {
        val rows = JSONObject(rawJson).optJSONArray("rows") ?: return emptyList()
        return buildList {
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val dim = row.optJSONArray("dimensionValues")?.optJSONObject(0)?.optString("value").orEmpty()
                if (dim.isBlank() || dim == "(not set)") continue
                val metrics = row.optJSONArray("metricValues")
                val v0 = metrics?.optJSONObject(0)?.optString("value")?.toLongOrNull() ?: 0L
                val v1 = metrics?.optJSONObject(1)?.optString("value")?.toLongOrNull() ?: 0L
                val count = if (metricFirstIsCount) v0 else v1
                val users = if (metricFirstIsCount) v1 else v0
                add(AnalyticsRankedItem(label = dim, count = count, users = users))
            }
        }.sortedByDescending { if (metricFirstIsCount) it.count else it.users }
    }

    internal fun parseSingleCount(rawJson: String): Long {
        val rows = JSONObject(rawJson).optJSONArray("rows") ?: return 0L
        var sum = 0L
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            sum += row.optJSONArray("metricValues")?.optJSONObject(0)?.optString("value")?.toLongOrNull() ?: 0L
        }
        return sum
    }

    internal fun parseRealtimeTotals(rawJson: String): Pair<Long, Long> {
        val rows = JSONObject(rawJson).optJSONArray("rows") ?: return 0L to 0L
        var users = 0L
        var events = 0L
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            val metrics = row.optJSONArray("metricValues")
            users += metrics?.optJSONObject(0)?.optString("value")?.toLongOrNull() ?: 0L
            events += metrics?.optJSONObject(1)?.optString("value")?.toLongOrNull() ?: 0L
        }
        return users to events
    }
}
