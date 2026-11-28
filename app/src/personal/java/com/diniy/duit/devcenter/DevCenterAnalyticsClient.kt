package com.diniy.duit.devcenter

import org.json.JSONArray
import org.json.JSONObject

/**
 * Read-only Google Analytics Data API v1beta client for Developer Center 2.0 Mission Control.
 * Uses batchRunReports + runRealtimeReport with returnPropertyQuota=true and strict token redaction.
 * Excludes internal traffic via GA4 property filters / release_channel dimensions (zero PII, zero billing).
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

        val batchUrl = "https://analyticsdata.googleapis.com/v1beta/properties/$cleanId:batchRunReports"
        val realtimeUrl = "https://analyticsdata.googleapis.com/v1beta/properties/$cleanId:runRealtimeReport"

        suspend fun postWithAutoRefresh(endpointUrl: String, body: String): DevHttpResponse {
            var res = postReport(endpointUrl, authHeaders(activeToken), body)
            if (res.statusCode == 401 && !retriedRefresh && tokenResolver != null) {
                retriedRefresh = true
                val refreshed = tokenResolver.invoke(true)?.trim().orEmpty()
                if (refreshed.isNotEmpty() && refreshed != activeToken) {
                    activeToken = refreshed
                    res = postReport(endpointUrl, authHeaders(activeToken), body)
                }
            }
            if (!res.isSuccess) {
                val detail = when (res.statusCode) {
                    401, 403 -> "GA4 authorization failed (HTTP ${res.statusCode}). Re-sign in with Google in Settings."
                    404 -> "GA4 Property ID was not found (HTTP 404)."
                    429 -> "GA4 free quota rate limit reached (HTTP 429). Try again later."
                    else -> "GA4 API returned HTTP ${res.statusCode}."
                }
                throw IllegalStateException(DevCenterRedactor.redact(detail, listOf(activeToken)))
            }
            return res
        }

        return try {
            // Batch 1: Core users, sessions, DAU/WAU/MAU, daily growth, engagement
            val batch1 = buildBatchRequest(
                listOf(
                    buildReportRequestObject(dateRange.startDate, listOf("date"), listOf("activeUsers", "newUsers", "userEngagementDuration", "sessions"), limit = 90),
                    buildReportRequestObject(dateRange.startDate, emptyList(), listOf("totalUsers", "activeUsers", "newUsers", "sessions"), limit = 1),
                    buildReportRequestObject("1daysAgo", emptyList(), listOf("activeUsers"), limit = 1),
                    buildReportRequestObject("7daysAgo", emptyList(), listOf("activeUsers"), limit = 1),
                    buildReportRequestObject("28daysAgo", emptyList(), listOf("activeUsers"), limit = 1)
                )
            )
            val b1Reports = extractBatchReports(postWithAutoRefresh(batchUrl, batch1).body)

            // Batch 2: Geography, OS/device, app version, release channel
            val batch2 = buildBatchRequest(
                listOf(
                    buildReportRequestObject(dateRange.startDate, listOf("country"), listOf("activeUsers", "sessions"), limit = 12),
                    buildReportRequestObject(dateRange.startDate, listOf("operatingSystemVersion"), listOf("activeUsers", "sessions"), limit = 10),
                    buildReportRequestObject(dateRange.startDate, listOf("deviceModel"), listOf("activeUsers", "sessions"), limit = 10),
                    buildReportRequestObject(dateRange.startDate, listOf("appVersion"), listOf("activeUsers", "sessions"), limit = 10),
                    buildReportRequestObject(dateRange.startDate, listOf("customUser:release_channel"), listOf("activeUsers", "sessions"), limit = 6)
                )
            )
            val b2Reports = runCatching { extractBatchReports(postWithAutoRefresh(batchUrl, batch2).body) }.getOrDefault(emptyList())

            // Batch 3: Features, themes, update adoption, crashes (app_exception)
            val batch3 = buildBatchRequest(
                listOf(
                    buildReportRequestObject(dateRange.startDate, listOf("customEvent:feature"), listOf("eventCount", "activeUsers"), eventNameFilter = "feature_used", limit = 15),
                    buildReportRequestObject(dateRange.startDate, listOf("customEvent:theme"), listOf("eventCount", "activeUsers"), eventNameFilter = "theme_selected", limit = 6),
                    buildReportRequestObject(dateRange.startDate, listOf("eventName"), listOf("eventCount", "activeUsers"), eventNameFilter = "app_updated", limit = 1),
                    buildReportRequestObject(dateRange.startDate, listOf("appVersion"), listOf("eventCount", "activeUsers"), eventNameFilter = "app_exception", limit = 10),
                    buildReportRequestObject(dateRange.startDate, listOf("operatingSystemVersion"), listOf("eventCount", "activeUsers"), eventNameFilter = "app_exception", limit = 8)
                )
            )
            val b3Reports = runCatching { extractBatchReports(postWithAutoRefresh(batchUrl, batch3).body) }.getOrDefault(emptyList())

            // Batch 4 optional device breakdown for crashes (soft-fail if empty)
            val crashDeviceRes = runCatching {
                val singleBatch = buildBatchRequest(
                    listOf(buildReportRequestObject(dateRange.startDate, listOf("deviceModel"), listOf("eventCount", "activeUsers"), eventNameFilter = "app_exception", limit = 8))
                )
                postWithAutoRefresh(batchUrl, singleBatch).body
            }.getOrNull()
            val crashesByDevice = crashDeviceRes?.let { extractBatchReports(it).firstOrNull()?.let { r -> parseRankedObject(r, metricFirstIsCount = true) } }.orEmpty()

            val rtTotals = runCatching {
                val rtTotalsBody = JSONObject().apply {
                    put("metrics", JSONArray().apply {
                        put(JSONObject().put("name", "activeUsers"))
                        put(JSONObject().put("name", "eventCount"))
                    })
                }.toString()
                parseRealtimeTotals(postWithAutoRefresh(realtimeUrl, rtTotalsBody).body)
            }.getOrDefault(0L to 0L)

            val rtEvents = runCatching {
                val rtEventsBody = JSONObject().apply {
                    put("dimensions", JSONArray().apply { put(JSONObject().put("name", "eventName")) })
                    put("metrics", JSONArray().apply { put(JSONObject().put("name", "eventCount")) })
                    put("limit", "10")
                }.toString()
                parseRankedItems(postWithAutoRefresh(realtimeUrl, rtEventsBody).body, metricFirstIsCount = true)
            }.getOrDefault(emptyList())

            val dailyReport = b1Reports.getOrNull(0)
            val summaryReport = b1Reports.getOrNull(1)
            val dailyPoints = dailyReport?.let(::parseDailyOverviewObject).orEmpty()
            val summaryTotals = summaryReport?.let(::parseSingleRowMetrics).orEmpty()

            val totalUsers = summaryTotals.getOrElse(0) { dailyPoints.sumOf { it.activeUsers } }
            val periodActiveUsers = summaryTotals.getOrElse(1) { dailyPoints.sumOf { it.activeUsers } }.coerceAtLeast(dailyPoints.sumOf { it.activeUsers }.let { if (summaryTotals.size > 1) summaryTotals[1] else it })
            val totalNew = summaryTotals.getOrElse(2) { dailyPoints.sumOf { it.newUsers } }
            val totalSessions = summaryTotals.getOrElse(3) { 0L }
            val totalEngagementSec = dailyReport?.let(::parseTotalEngagementSecondsObject) ?: 0L

            val dau = b1Reports.getOrNull(2)?.let(::parseSingleMetricObject) ?: dailyPoints.lastOrNull()?.activeUsers ?: 0L
            val wau = b1Reports.getOrNull(3)?.let(::parseSingleMetricObject) ?: 0L
            val mau = b1Reports.getOrNull(4)?.let(::parseSingleMetricObject) ?: periodActiveUsers

            val countries = b2Reports.getOrNull(0)?.let { parseRankedObject(it, metricFirstIsCount = false) }.orEmpty()
            val androidVersions = b2Reports.getOrNull(1)?.let { parseRankedObject(it, metricFirstIsCount = false) }.orEmpty()
            val deviceModels = b2Reports.getOrNull(2)?.let { parseRankedObject(it, metricFirstIsCount = false) }.orEmpty()
            val appVersions = b2Reports.getOrNull(3)?.let { parseRankedObject(it, metricFirstIsCount = false) }.orEmpty()
            val releaseChannels = b2Reports.getOrNull(4)?.let { parseRankedObject(it, metricFirstIsCount = false) }.orEmpty()

            val features = b3Reports.getOrNull(0)?.let { parseRankedObject(it, metricFirstIsCount = true) }.orEmpty()
            val themes = b3Reports.getOrNull(1)?.let { parseRankedObject(it, metricFirstIsCount = true) }.orEmpty()
            val updateAdoptions = b3Reports.getOrNull(2)?.let(::parseSingleCountObject) ?: 0L
            val crashesByApp = b3Reports.getOrNull(3)?.let { parseRankedObject(it, metricFirstIsCount = true) }.orEmpty()
            val crashesByOs = b3Reports.getOrNull(4)?.let { parseRankedObject(it, metricFirstIsCount = true) }.orEmpty()

            val widgetItem = features.firstOrNull { it.label == "widgets" }
            val crashUsers = crashesByApp.sumOf { it.users }
            val crashEvents = crashesByApp.sumOf { it.count }
            val crashFreeUsers = if (periodActiveUsers > 0L) {
                (((periodActiveUsers - crashUsers).coerceAtLeast(0L).toDouble() / periodActiveUsers.toDouble()) * 100.0)
                    .coerceIn(0.0, 100.0)
            } else null
            val crashFreeSessions = if (totalSessions > 0L) {
                (((totalSessions - crashEvents).coerceAtLeast(0L).toDouble() / totalSessions.toDouble()) * 100.0)
                    .coerceIn(0.0, 100.0)
            } else null

            val (quotaHour, quotaDay) = b1Reports.firstOrNull()?.let(::parseQuota) ?: (null to null)
            val (rtUsers, rtEventCount) = rtTotals

            if (periodActiveUsers == 0L && totalNew == 0L && features.isEmpty() && appVersions.isEmpty() &&
                rtUsers == 0L && rtEventCount == 0L && rtEvents.isEmpty()
            ) {
                AnalyticsSourceState.Empty(dateRange = dateRange, syncedAtIso = syncedAtIso)
            } else {
                val returningUsers = (periodActiveUsers - totalNew).coerceAtLeast(0L)
                val returningPercent = if (periodActiveUsers > 0L) {
                    ((returningUsers * 100L) / periodActiveUsers).toInt().coerceIn(0, 100)
                } else 0
                val dauMauPct = if (mau > 0L) ((dau * 100L) / mau).toInt().coerceIn(0, 100) else 0
                val wauMauPct = if (mau > 0L) ((wau * 100L) / mau).toInt().coerceIn(0, 100) else 0
                val avgEngagement = if (periodActiveUsers > 0L) (totalEngagementSec / periodActiveUsers).coerceAtLeast(0L) else 0L

                AnalyticsSourceState.Ready(
                    AnalyticsDashboardSnapshot(
                        dateRange = dateRange,
                        activeUsers = periodActiveUsers,
                        newUsers = totalNew,
                        returningRatePercent = returningPercent,
                        avgEngagementSeconds = avgEngagement,
                        growthTrend = dailyPoints.takeLast(14),
                        popularFeatures = features,
                        appVersions = appVersions,
                        updateAdoptions = updateAdoptions,
                        syncedAtIso = syncedAtIso,
                        realtimeActiveUsers = rtUsers,
                        realtimeEventCount = rtEventCount,
                        realtimeEvents = rtEvents,
                        totalUsers = totalUsers,
                        dau = dau,
                        wau = wau,
                        mau = mau,
                        sessions = totalSessions,
                        returningUsers = returningUsers,
                        dauPerMauPercent = dauMauPct,
                        wauPerMauPercent = wauMauPct,
                        countries = countries,
                        androidVersions = androidVersions,
                        deviceModels = deviceModels,
                        themePopularity = themes,
                        widgetUsageCount = widgetItem?.count ?: 0L,
                        widgetUsers = widgetItem?.users ?: 0L,
                        releaseChannels = releaseChannels,
                        crashFreeUsersPercent = crashFreeUsers,
                        crashFreeSessionsPercent = crashFreeSessions,
                        crashAffectedUsers = crashUsers,
                        crashEventCount = crashEvents,
                        crashesByAppVersion = crashesByApp,
                        crashesByOsVersion = crashesByOs,
                        crashesByDevice = crashesByDevice,
                        quotaHourlyRemaining = quotaHour,
                        quotaDailyRemaining = quotaDay,
                        isCached = false
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
        body: String
    ): DevHttpResponse = transport.request(method = "POST", url = url, headers = headers, body = body)

    internal fun buildBatchRequest(requests: List<JSONObject>): String = JSONObject().apply {
        put("requests", JSONArray().apply { requests.forEach { put(it) } })
    }.toString()

    internal fun buildReportRequestObject(
        startDate: String,
        dimensions: List<String>,
        metrics: List<String>,
        eventNameFilter: String? = null,
        limit: Int = 90
    ): JSONObject = JSONObject().apply {
        put("dateRanges", JSONArray().apply {
            put(JSONObject().apply {
                put("startDate", startDate)
                put("endDate", "today")
            })
        })
        if (dimensions.isNotEmpty()) {
            put("dimensions", JSONArray().apply {
                dimensions.forEach { d -> put(JSONObject().put("name", d)) }
            })
        }
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
        put("returnPropertyQuota", true)
        put("limit", limit.toString())
    }

    internal fun buildReportRequest(
        startDate: String,
        dimensions: List<String>,
        metrics: List<String>,
        eventNameFilter: String? = null,
        limit: Int = 90
    ): String = buildReportRequestObject(startDate, dimensions, metrics, eventNameFilter, limit).toString()

    private fun extractBatchReports(rawJson: String): List<JSONObject> {
        val root = runCatching { JSONObject(rawJson) }.getOrNull() ?: return emptyList()
        val arr = root.optJSONArray("reports") ?: return if (root.has("rows")) listOf(root) else emptyList()
        return List(arr.length()) { idx -> arr.optJSONObject(idx) ?: JSONObject() }
    }

    private fun parseQuota(reportObj: JSONObject): Pair<Int?, Int?> {
        val q = reportObj.optJSONObject("propertyQuota") ?: return null to null
        val hr = q.optJSONObject("tokensPerHour")?.optInt("remaining", -1)?.takeIf { it >= 0 }
        val day = q.optJSONObject("tokensPerDay")?.optInt("remaining", -1)?.takeIf { it >= 0 }
        return hr to day
    }

    internal fun parseDailyOverview(rawJson: String): List<AnalyticsDailyPoint> =
        parseDailyOverviewObject(JSONObject(rawJson))

    private fun parseDailyOverviewObject(obj: JSONObject): List<AnalyticsDailyPoint> {
        val rows = obj.optJSONArray("rows") ?: return emptyList()
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

    private fun parseSingleRowMetrics(obj: JSONObject): List<Long> {
        val rows = obj.optJSONArray("rows") ?: return emptyList()
        val first = rows.optJSONObject(0)?.optJSONArray("metricValues") ?: return emptyList()
        return List(first.length()) { idx -> first.optJSONObject(idx)?.optString("value")?.toLongOrNull() ?: 0L }
    }

    private fun parseSingleMetricObject(obj: JSONObject): Long {
        val rows = obj.optJSONArray("rows") ?: return 0L
        var sum = 0L
        for (i in 0 until rows.length()) {
            sum += rows.optJSONObject(i)?.optJSONArray("metricValues")?.optJSONObject(0)?.optString("value")?.toLongOrNull() ?: 0L
        }
        return sum
    }

    internal fun parseTotalEngagementSeconds(rawJson: String): Long =
        parseTotalEngagementSecondsObject(JSONObject(rawJson))

    private fun parseTotalEngagementSecondsObject(obj: JSONObject): Long {
        val rows = obj.optJSONArray("rows") ?: return 0L
        var total = 0.0
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            val sec = row.optJSONArray("metricValues")?.optJSONObject(2)?.optString("value")?.toDoubleOrNull() ?: 0.0
            total += sec
        }
        return total.toLong()
    }

    internal fun parseRankedItems(rawJson: String, metricFirstIsCount: Boolean): List<AnalyticsRankedItem> =
        parseRankedObject(JSONObject(rawJson), metricFirstIsCount)

    private fun parseRankedObject(obj: JSONObject, metricFirstIsCount: Boolean): List<AnalyticsRankedItem> {
        val rows = obj.optJSONArray("rows") ?: return emptyList()
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

    private fun parseSingleCountObject(obj: JSONObject): Long {
        val rows = obj.optJSONArray("rows") ?: return 0L
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

    companion object {
        fun encodeSnapshot(s: AnalyticsDashboardSnapshot): String = JSONObject().apply {
            put("range", s.dateRange.name)
            put("activeUsers", s.activeUsers); put("newUsers", s.newUsers)
            put("returningRatePercent", s.returningRatePercent); put("avgEngagementSeconds", s.avgEngagementSeconds)
            put("updateAdoptions", s.updateAdoptions); put("syncedAtIso", s.syncedAtIso)
            put("totalUsers", s.totalUsers); put("dau", s.dau); put("wau", s.wau); put("mau", s.mau)
            put("sessions", s.sessions); put("returningUsers", s.returningUsers)
            put("dauPerMauPercent", s.dauPerMauPercent); put("wauPerMauPercent", s.wauPerMauPercent)
            put("widgetUsageCount", s.widgetUsageCount); put("widgetUsers", s.widgetUsers)
            s.crashFreeUsersPercent?.let { put("crashFreeUsersPercent", it) }
            s.crashFreeSessionsPercent?.let { put("crashFreeSessionsPercent", it) }
            put("crashAffectedUsers", s.crashAffectedUsers); put("crashEventCount", s.crashEventCount)
            put("growthTrend", JSONArray().apply {
                s.growthTrend.forEach { p ->
                    put(JSONObject().put("date", p.dateLabel).put("active", p.activeUsers).put("new", p.newUsers))
                }
            })
            put("popularFeatures", encodeRanked(s.popularFeatures))
            put("appVersions", encodeRanked(s.appVersions))
            put("countries", encodeRanked(s.countries))
            put("androidVersions", encodeRanked(s.androidVersions))
            put("deviceModels", encodeRanked(s.deviceModels))
            put("themePopularity", encodeRanked(s.themePopularity))
            put("releaseChannels", encodeRanked(s.releaseChannels))
            put("crashesByAppVersion", encodeRanked(s.crashesByAppVersion))
            put("crashesByOsVersion", encodeRanked(s.crashesByOsVersion))
            put("crashesByDevice", encodeRanked(s.crashesByDevice))
        }.toString()

        fun decodeCachedSnapshot(raw: String?): AnalyticsDashboardSnapshot? {
            if (raw.isNullOrBlank()) return null
            return runCatching {
                val o = JSONObject(raw)
                val range = AnalyticsDateRange.entries.firstOrNull { it.name == o.optString("range") }
                    ?: AnalyticsDateRange.THIRTY_DAYS
                val trendArr = o.optJSONArray("growthTrend") ?: JSONArray()
                val trend = List(trendArr.length()) { idx ->
                    val pt = trendArr.optJSONObject(idx)
                    AnalyticsDailyPoint(
                        dateLabel = pt?.optString("date").orEmpty(),
                        activeUsers = pt?.optLong("active") ?: 0L,
                        newUsers = pt?.optLong("new") ?: 0L
                    )
                }
                AnalyticsDashboardSnapshot(
                    dateRange = range,
                    activeUsers = o.optLong("activeUsers"),
                    newUsers = o.optLong("newUsers"),
                    returningRatePercent = o.optInt("returningRatePercent"),
                    avgEngagementSeconds = o.optLong("avgEngagementSeconds"),
                    growthTrend = trend,
                    popularFeatures = decodeRanked(o.optJSONArray("popularFeatures")),
                    appVersions = decodeRanked(o.optJSONArray("appVersions")),
                    updateAdoptions = o.optLong("updateAdoptions"),
                    syncedAtIso = o.optString("syncedAtIso"),
                    totalUsers = o.optLong("totalUsers"),
                    dau = o.optLong("dau"),
                    wau = o.optLong("wau"),
                    mau = o.optLong("mau"),
                    sessions = o.optLong("sessions"),
                    returningUsers = o.optLong("returningUsers"),
                    dauPerMauPercent = o.optInt("dauPerMauPercent"),
                    wauPerMauPercent = o.optInt("wauPerMauPercent"),
                    countries = decodeRanked(o.optJSONArray("countries")),
                    androidVersions = decodeRanked(o.optJSONArray("androidVersions")),
                    deviceModels = decodeRanked(o.optJSONArray("deviceModels")),
                    themePopularity = decodeRanked(o.optJSONArray("themePopularity")),
                    widgetUsageCount = o.optLong("widgetUsageCount"),
                    widgetUsers = o.optLong("widgetUsers"),
                    releaseChannels = decodeRanked(o.optJSONArray("releaseChannels")),
                    crashFreeUsersPercent = o.takeIf { it.has("crashFreeUsersPercent") }?.optDouble("crashFreeUsersPercent"),
                    crashFreeSessionsPercent = o.takeIf { it.has("crashFreeSessionsPercent") }?.optDouble("crashFreeSessionsPercent"),
                    crashAffectedUsers = o.optLong("crashAffectedUsers"),
                    crashEventCount = o.optLong("crashEventCount"),
                    crashesByAppVersion = decodeRanked(o.optJSONArray("crashesByAppVersion")),
                    crashesByOsVersion = decodeRanked(o.optJSONArray("crashesByOsVersion")),
                    crashesByDevice = decodeRanked(o.optJSONArray("crashesByDevice")),
                    isCached = true
                )
            }.getOrNull()
        }

        private fun encodeRanked(items: List<AnalyticsRankedItem>): JSONArray = JSONArray().apply {
            items.forEach { put(JSONObject().put("l", it.label).put("c", it.count).put("u", it.users)) }
        }

        private fun decodeRanked(arr: JSONArray?): List<AnalyticsRankedItem> {
            if (arr == null) return emptyList()
            return List(arr.length()) { idx ->
                val o = arr.optJSONObject(idx)
                AnalyticsRankedItem(o?.optString("l").orEmpty(), o?.optLong("c") ?: 0L, o?.optLong("u") ?: 0L)
            }.filter { it.label.isNotBlank() }
        }
    }
}
