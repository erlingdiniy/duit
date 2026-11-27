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
        syncedAtIso: String
    ): AnalyticsSourceState {
        val cleanId = propertyId.trim()
        val cleanToken = analyticsAccessToken?.trim().orEmpty()

        if (cleanId.isEmpty() || cleanToken.isEmpty()) {
            val summary = when {
                cleanId.isNotEmpty() ->
                    "GA4 Property ID saved. Add a read-only analytics OAuth token in Developer Settings, or open Firebase Console below."
                cleanToken.isNotEmpty() ->
                    "Analytics token saved. Add your numeric GA4 Property ID in Developer Settings to load aggregate reports."
                else ->
                    "GA4 Data API credentials are optional and not configured yet. Use Firebase Console below or add a numeric Property ID and read-only token in Settings."
            }
            return AnalyticsSourceState.Unavailable(
                summary = summary,
                hasPropertyId = cleanId.isNotEmpty(),
                hasToken = cleanToken.isNotEmpty()
            )
        }

        if (!DevCenterValidators.isValidGa4PropertyId(cleanId)) {
            return AnalyticsSourceState.Error(
                message = "GA4 Property ID must be a 6 to 15 digit numeric ID.",
                syncedAtIso = syncedAtIso
            )
        }

        val url = "https://analyticsdata.googleapis.com/v1beta/properties/$cleanId:runReport"
        val headers = mapOf(
            "Authorization" to "Bearer $cleanToken",
            "Content-Type" to "application/json",
            "Accept" to "application/json"
        )

        return try {
            val overviewBody = buildReportRequest(
                startDate = dateRange.startDate,
                dimensions = listOf("date"),
                metrics = listOf("activeUsers", "newUsers", "userEngagementDuration")
            )
            val overviewRes = postReport(url, headers, overviewBody, cleanToken)
            val dailyPoints = parseDailyOverview(overviewRes.body)

            val featuresBody = buildReportRequest(
                startDate = dateRange.startDate,
                dimensions = listOf("customEvent:feature"),
                metrics = listOf("eventCount", "activeUsers"),
                eventNameFilter = "feature_used",
                limit = 10
            )
            val featuresRes = postReport(url, headers, featuresBody, cleanToken)
            val features = parseRankedItems(featuresRes.body, metricFirstIsCount = true)

            val versionsBody = buildReportRequest(
                startDate = dateRange.startDate,
                dimensions = listOf("appVersion"),
                metrics = listOf("activeUsers", "sessions"),
                limit = 8
            )
            val versionsRes = postReport(url, headers, versionsBody, cleanToken)
            val versions = parseRankedItems(versionsRes.body, metricFirstIsCount = false)

            val updatesBody = buildReportRequest(
                startDate = dateRange.startDate,
                dimensions = listOf("eventName"),
                metrics = listOf("eventCount", "activeUsers"),
                eventNameFilter = "app_updated",
                limit = 1
            )
            val updatesRes = postReport(url, headers, updatesBody, cleanToken)
            val updateAdoptions = parseSingleCount(updatesRes.body)

            val totalActive = dailyPoints.sumOf { it.activeUsers }
            val totalNew = dailyPoints.sumOf { it.newUsers }
            val totalEngagementSec = parseTotalEngagementSeconds(overviewRes.body)

            if (totalActive == 0L && totalNew == 0L && features.isEmpty() && versions.isEmpty()) {
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
                        syncedAtIso = syncedAtIso
                    )
                )
            }
        } catch (err: Exception) {
            AnalyticsSourceState.Error(
                message = DevCenterRedactor.redact(
                    err.message ?: "Unable to fetch GA4 report.",
                    listOf(cleanToken)
                ),
                syncedAtIso = syncedAtIso
            )
        }
    }

    private suspend fun postReport(
        url: String,
        headers: Map<String, String>,
        body: String,
        secret: String
    ): DevHttpResponse {
        val res = transport.request(method = "POST", url = url, headers = headers, body = body)
        if (!res.isSuccess) {
            val detail = when (res.statusCode) {
                401, 403 -> "GA4 authorization failed (HTTP ${res.statusCode}). Refresh your read-only OAuth token."
                404 -> "GA4 Property ID was not found (HTTP 404)."
                429 -> "GA4 free quota rate limit reached (HTTP 429). Try again later."
                else -> "GA4 API returned HTTP ${res.statusCode}."
            }
            throw IllegalStateException(DevCenterRedactor.redact(detail, listOf(secret)))
        }
        return res
    }

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
}
