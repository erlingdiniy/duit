package com.diniy.duit.domain.model

/**
 * A custom money reminder (Smart Money Calendar event type 6). Informational only: it never creates
 * a transaction or changes a wallet balance. [repeat] reuses the commitment schedule rules.
 */
data class MoneyReminder(
    val id: Long,
    val title: String,
    val dateEpochDay: Long,
    val repeat: CommitmentFrequency = CommitmentFrequency.ONE_TIME,
    val customIntervalDays: Int? = null,
    val note: String = "",
    val createdAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L
) {
    /**
     * Occurrence days in [fromEpochDay, toEpochDay]. Monthly/yearly repeats keep the original day
     * (a 31st falls back to month-end, 29 Feb to 28 Feb) via the commitment schedule rules.
     */
    fun occurrencesBetween(fromEpochDay: Long, toEpochDay: Long): List<Long> {
        val anchor = java.time.LocalDate.ofEpochDay(dateEpochDay).dayOfMonth
        val days = mutableListOf<Long>()
        var day = dateEpochDay
        var guard = 0
        while (day <= toEpochDay && guard++ < 2_000) {
            if (day >= fromEpochDay) days += day
            if (repeat == CommitmentFrequency.ONE_TIME) break
            val next = com.diniy.duit.domain.calculator.ObligationCalculator.advanceDueDate(day, repeat, customIntervalDays, anchor)
            if (next <= day) break
            day = next
        }
        return days
    }
}
