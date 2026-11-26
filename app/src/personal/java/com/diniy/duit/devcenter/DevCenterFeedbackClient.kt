package com.diniy.duit.devcenter

import org.json.JSONObject
import java.net.URLEncoder

/**
 * Read-only User Feedback client matching feedback-backend/Code.gs columns A:F:
 * Timestamp | Category | Message | Email | App Version | Status.
 * Supports either an authorized Google Sheets API read-only token or local CSV import via SAF.
 * Never invents synthetic or placeholder feedback items.
 */
class DevCenterFeedbackClient(
    private val transport: DevHttpTransport
) {
    suspend fun loadFeedback(
        sheetId: String,
        range: String,
        sheetsAccessToken: String?,
        importedCsvRaw: String?,
        importedCsvLabel: String?
    ): FeedbackSourceState {
        val cleanSheetId = sheetId.trim()
        val cleanToken = sheetsAccessToken?.trim().orEmpty()

        if (cleanSheetId.isNotEmpty() && cleanToken.isNotEmpty()) {
            val encodedId = URLEncoder.encode(cleanSheetId, "UTF-8")
            val encodedRange = URLEncoder.encode(range.ifBlank { DEFAULT_FEEDBACK_RANGE }, "UTF-8")
            val url = "https://sheets.googleapis.com/v4/spreadsheets/$encodedId/values/$encodedRange"
            val result = runCatching {
                transport.request(
                    method = "GET",
                    url = url,
                    headers = mapOf(
                        "Authorization" to "Bearer $cleanToken",
                        "Accept" to "application/json"
                    )
                )
            }
            val res = result.getOrElse { err ->
                return FeedbackSourceState.Error(
                    DevCenterRedactor.redact("Google Sheets request failed: ${err.message}", listOf(cleanToken))
                )
            }
            if (!res.isSuccess) {
                return FeedbackSourceState.Error(
                    DevCenterRedactor.redact(
                        "Google Sheets returned HTTP ${res.statusCode}. Refresh your read-only OAuth token or import CSV.",
                        listOf(cleanToken)
                    )
                )
            }
            val rows = parseSheetsValuesJson(res.body)
            return FeedbackSourceState.Ready(
                sourceLabel = "Google Sheets (Read-Only)",
                items = mapFeedbackRows(rows)
            )
        }

        if (!importedCsvRaw.isNullOrBlank()) {
            val rows = parseCsvRows(importedCsvRaw)
            return FeedbackSourceState.Ready(
                sourceLabel = importedCsvLabel?.let { "CSV: $it" } ?: "Imported CSV",
                items = mapFeedbackRows(rows),
                notice = "Loaded locally from read-only CSV export."
            )
        }

        val guide = if (cleanSheetId.isNotEmpty()) {
            "Sheet ID saved. Add a read-only Google Sheets OAuth token in Developer Settings, or import an exported CSV."
        } else {
            "Feedback is stored in your private Google Sheet via Apps Script. Import a CSV export or configure Sheet ID + read-only OAuth token in Settings."
        }
        return FeedbackSourceState.Unconfigured(guide)
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
                status = cleanCellValue(row.getOrNull(5), 32).ifBlank { "New" }
            )
        }
        return items.sortedByDescending { it.timestampIso }
    }
}
