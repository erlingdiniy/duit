package com.diniy.duit.core.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Formats epoch days and timestamps cleanly for MYR local ledger views.
 */
object DateTimeFormatters {
    private val shortDateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    private val compactDayFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

    fun formatEpochDay(epochDay: Long): String {
        return LocalDate.ofEpochDay(epochDay).format(shortDateFormatter)
    }

    fun formatCompactEpochDay(epochDay: Long): String {
        return LocalDate.ofEpochDay(epochDay).format(compactDayFormatter)
    }

    fun formatTimestamp(epochMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        val zoned = Instant.ofEpochMilli(epochMs).atZone(zoneId)
        return "${zoned.toLocalDate().format(compactDayFormatter)} · ${zoned.toLocalTime().format(timeFormatter)}"
    }

    fun formatDateOnly(epochMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        val localDate = Instant.ofEpochMilli(epochMs).atZone(zoneId).toLocalDate()
        return localDate.format(shortDateFormatter)
    }

    fun formatTimeOnly(epochMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        val localTime = Instant.ofEpochMilli(epochMs).atZone(zoneId).toLocalTime()
        return localTime.format(timeFormatter)
    }

    fun formatDateGroupHeader(
        date: LocalDate,
        today: LocalDate = LocalDate.now()
    ): String {
        return when (date) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> date.format(shortDateFormatter)
        }
    }

    fun toLocalDate(epochMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): LocalDate {
        return Instant.ofEpochMilli(epochMs).atZone(zoneId).toLocalDate()
    }
}
