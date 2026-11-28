package com.diniy.duit.devcenter

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Feedback Inbox client matching feedback-backend/Code.gs columns A:F:
 * Timestamp | Category | Message | Email | App Version | Status.
 * Supports Google OAuth auto-refresh, manual Google Sheets API token, CSV import via SAF,
 * and optional status write-back when spreadsheets scope is granted.
 */
class DevCenterFeedbackClient(
    private val transport: DevHttpTransport
) {
    suspend fun loadFeedback(
        sheetId: String,
        range: String,
        sheetsAccessToken: String?,
        importedCsvRaw: String?,
        importedCsvLabel: String?,
        tokenResolver: (suspend (forceRefresh: Boolean) -> String?)? = null
    ): FeedbackSourceState {
        val cleanSheetId = sheetId.trim()
        val manualToken = sheetsAccessToken?.trim().orEmpty()
        var activeToken = (if (manualToken.isNotEmpty()) manualToken else tokenResolver?.invoke(false))?.trim().orEmpty()

        if (cleanSheetId.isNotEmpty() && activeToken.isNotEmpty()) {
            val encodedId = URLEncoder.encode(cleanSheetId, "UTF-8")
            val encodedRange = URLEncoder.encode(range.ifBlank { DEFAULT_FEEDBACK_RANGE }, "UTF-8")
            val url = "https://sheets.googleapis.com/v4/spreadsheets/$encodedId/values/$encodedRange"
            val result = runCatching {
                var res = transport.request(
                    method = "GET",
                    url = url,
                    headers = mapOf("Authorization" to "Bearer $activeToken", "Accept" to "application/json")
                )
                if (res.statusCode == 401 && manualToken.isEmpty() && tokenResolver != null) {
                    val refreshed = tokenResolver(true)?.trim().orEmpty()
                    if (refreshed.isNotEmpty() && refreshed != activeToken) {
                        activeToken = refreshed
                        res = transport.request(
                            method = "GET",
                            url = url,
                            headers = mapOf("Authorization" to "Bearer $activeToken", "Accept" to "application/json")
                        )
                    }
                }
                res
            }
            val res = result.getOrElse { err ->
                return FeedbackSourceState.Error(
                    DevCenterRedactor.redact("Google Sheets request failed: ${err.message}", listOf(activeToken))
                )
            }
            if (!res.isSuccess) {
                val hint = if (res.statusCode == 401 || res.statusCode == 403) {
                    "Google Sheets denied access (HTTP ${res.statusCode}). Re-sign in with Google Sheets access or import CSV."
                } else {
                    "Google Sheets returned HTTP ${res.statusCode}. Try again or import CSV."
                }
                return FeedbackSourceState.Error(DevCenterRedactor.redact(hint, listOf(activeToken)))
            }
            val rows = parseSheetsValuesJson(res.body)
            return FeedbackSourceState.Ready(
                sourceLabel = if (manualToken.isNotEmpty()) "Google Sheets (Token)" else "Google Sheets (OAuth)",
                items = mapFeedbackRows(rows)
            )
        }

        if (!importedCsvRaw.isNullOrBlank()) {
            val rows = parseCsvRows(importedCsvRaw)
            return FeedbackSourceState.Ready(
                sourceLabel = importedCsvLabel?.let { "CSV: $it" } ?: "Imported CSV",
                items = mapFeedbackRows(rows),
                notice = "Loaded locally from CSV export."
            )
        }

        val guide = if (cleanSheetId.isNotEmpty()) {
            "Sheet ID saved. Sign in with Google in Settings (or add a Sheets token / CSV export)."
        } else {
            "Feedback is stored in your private Google Sheet via Apps Script. Import a CSV export or configure Sheet ID + Google Sign-In in Settings."
        }
        return FeedbackSourceState.Unconfigured(guide)
    }

    suspend fun updateSheetRowStatus(
        sheetId: String,
        range: String,
        rowNumber: Int,
        newSheetStatus: String,
        tokenResolver: suspend (forceRefresh: Boolean) -> String?
    ): Result<String> {
        val cleanId = sheetId.trim()
        require(cleanId.isNotEmpty()) { "Sheet ID is not configured." }
        require(rowNumber >= 2) { "Cannot overwrite header row." }
        val sheetName = range.substringBefore("!", "Feedback").trim().ifBlank { "Feedback" }
        val encodedId = URLEncoder.encode(cleanId, "UTF-8")
        val targetRange = URLEncoder.encode("$sheetName!F$rowNumber", "UTF-8")
        val url = "https://sheets.googleapis.com/v4/spreadsheets/$encodedId/values/$targetRange?valueInputOption=RAW"
        val body = JSONObject().put("values", JSONArray().put(JSONArray().put(newSheetStatus))).toString()
        val token = tokenResolver(false)?.trim().orEmpty()
        if (token.isEmpty()) return Result.failure(IllegalStateException("Google OAuth token required."))

        return runCatching {
            val res = transport.request(
                method = "PUT",
                url = url,
                headers = mapOf(
                    "Authorization" to "Bearer $token",
                    "Content-Type" to "application/json",
                    "Accept" to "application/json"
                ),
                body = body
            )
            if (!res.isSuccess) {
                val hint = if (res.statusCode == 403) {
                    "Sheet status write skipped: Google token has read-only Sheets scope."
                } else {
                    "Sheets status update failed (HTTP ${res.statusCode})."
                }
                throw IllegalStateException(DevCenterRedactor.redact(hint, listOf(token)))
            }
            newSheetStatus
        }
    }

    internal fun parseSheetsValuesJson(rawJson: String): List<List<String>> {
        val root = runCatching { JSONObject(rawJson) }.getOrNull() ?: return emptyList()
        val values = root.optJSONArray("values") ?: return emptyList()
        return buildList {
            for (i in 0 until values.length()) {
                val rowArr = values.optJSONArray(i) ?: continue
                add(List(rowArr.length()) { idx -> rowArr.optString(idx, "") })
            }
        }
    }

    internal fun parseCsvRows(csvText: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < csvText.length) {
            val ch = csvText[i]
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < csvText.length && csvText[i + 1] == '"') {
                        cell.append('"')
                        i += 1
                    } else {
                        inQuotes = false
                    }
                } else {
                    cell.append(ch)
                }
            } else {
                when (ch) {
                    '"' -> inQuotes = true
                    ',' -> {
                        row.add(cell.toString())
                        cell.setLength(0)
                    }
                    '\r' -> {
                        if (i + 1 < csvText.length && csvText[i + 1] == '\n') i += 1
                        row.add(cell.toString())
                        rows.add(row)
                        row = mutableListOf()
                        cell.setLength(0)
                    }
                    '\n' -> {
                        row.add(cell.toString())
                        rows.add(row)
                        row = mutableListOf()
                        cell.setLength(0)
                    }
                    else -> cell.append(ch)
                }
            }
            i += 1
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row.add(cell.toString())
            rows.add(row)
        }
        return rows
    }

    internal fun cleanCellValue(raw: String?, maxLen: Int): String {
        var str = raw?.trim().orEmpty()
        // feedback-backend/Code.gs escapes leading formula characters with a leading single quote.
        if (str.length >= 2 && str[0] == '\'' && str[1] in charArrayOf('=', '+', '-', '@')) {
            str = str.substring(1)
        }
        return str.take(maxLen)
    }

    internal fun mapFeedbackRows(rows: List<List<String>>): List<FeedbackEntry> {
        val items = mutableListOf<FeedbackEntry>()
        for ((index, row) in rows.withIndex()) {
            val col0 = cleanCellValue(row.getOrNull(0), 64)
            val col1 = cleanCellValue(row.getOrNull(1), 64)
            if (index == 0 && col0.equals("timestamp", ignoreCase = true) && col1.equals("category", ignoreCase = true)) {
                continue
            }
            val message = cleanCellValue(row.getOrNull(2), 1200)
            if (message.isBlank()) continue
            val category = when (col1) {
                "Bug Report", "Feature Request", "General Feedback" -> col1
                else -> "General Feedback"
            }
            items += FeedbackEntry(
                id = "fb-row-${index + 1}",
                timestampIso = col0,
                category = category,
                message = message,
                email = cleanCellValue(row.getOrNull(3), 120),
                appVersion = cleanCellValue(row.getOrNull(4), 40).ifBlank { "unknown" },
                status = cleanCellValue(row.getOrNull(5), 32).ifBlank { "New" },
                sheetRowNumber = index + 1
            )
        }
        return items.sortedByDescending { it.timestampIso }
    }
}
