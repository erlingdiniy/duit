package com.diniy.duit.domain.calculator

import com.diniy.duit.core.util.DateTimeFormatters
import com.diniy.duit.domain.model.FinanceTransaction
import com.diniy.duit.domain.model.ReportingPeriodMode
import com.diniy.duit.domain.model.SalaryCycle
import com.diniy.duit.domain.model.TransactionType
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class PeriodCashflowReport(
    val periodLabel: String,
    val startEpochDay: Long,
    val endEpochDay: Long?,
    val incomeSen: Long,
    val expenseSen: Long,
    val receivedSalarySen: Long
)

/**
 * Pure domain utilities for Salary Cycle transitions and Calendar Month reporting
 * over the exact same underlying [FinanceTransaction] records.
 */
object PeriodReportingCalculator {

    /**
     * Reconciles cycle start/end boundaries chronologically by [SalaryCycle.startDateEpochDay].
     * Each cycle ends the day immediately before the next confirmed cycle begins.
     * The latest cycle remains open-ended and active.
     */
    fun reconcileCycleBoundaries(cycles: List<SalaryCycle>): List<SalaryCycle> {
        if (cycles.isEmpty()) return emptyList()
        val sortedAsc = cycles.sortedWith(
            compareBy<SalaryCycle> { it.startDateEpochDay }.thenBy { it.id }
        )
        return sortedAsc.mapIndexed { index, cycle ->
            val next = sortedAsc.getOrNull(index + 1)
            if (next != null) {
                val endDay = (next.startDateEpochDay - 1L).coerceAtLeast(cycle.startDateEpochDay)
                cycle.copy(
                    endDateEpochDay = endDay,
                    isActive = false,
                    isOpenEnded = false
                )
            } else {
                cycle.copy(
                    endDateEpochDay = Long.MAX_VALUE,
                    isActive = true,
                    isOpenEnded = true
                )
            }
        }
    }

    fun calculatePeriodReport(
        mode: ReportingPeriodMode,
        selectedMonth: YearMonth,
        activeCycle: SalaryCycle?,
        transactions: List<FinanceTransaction>,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): PeriodCashflowReport {
        return when (mode) {
            ReportingPeriodMode.SALARY_CYCLE -> {
                if (activeCycle == null) {
                    val start = selectedMonth.atDay(1).toEpochDay()
                    val end = selectedMonth.atEndOfMonth().toEpochDay()
                    filterAndSum(
                        label = "No active salary cycle",
                        startDay = start,
                        endDay = end,
                        isOpenEnded = false,
                        transactions = emptyList(),
                        zoneId = zoneId
                    )
                } else {
                    val endDay = if (activeCycle.isOpenEnded) LocalDate.now(zoneId).toEpochDay().coerceAtLeast(activeCycle.startDateEpochDay)
                    else activeCycle.endDateEpochDay
                    val filtered = transactions.filter { tx ->
                        val txDay = DateTimeFormatters.toLocalDate(tx.occurredAtEpochMs, zoneId).toEpochDay()
                        txDay >= activeCycle.startDateEpochDay && (activeCycle.isOpenEnded || txDay <= endDay)
                    }
                    filterAndSum(
                        label = activeCycle.label,
                        startDay = activeCycle.startDateEpochDay,
                        endDay = if (activeCycle.isOpenEnded) null else activeCycle.endDateEpochDay,
                        isOpenEnded = activeCycle.isOpenEnded,
                        transactions = filtered,
                        fallbackSalarySen = activeCycle.receivedSalarySen,
                        zoneId = zoneId
                    )
                }
            }

            ReportingPeriodMode.CALENDAR_MONTH -> {
                val startDay = selectedMonth.atDay(1).toEpochDay()
                val endDay = selectedMonth.atEndOfMonth().toEpochDay()
                val filtered = transactions.filter { tx ->
                    val txDay = DateTimeFormatters.toLocalDate(tx.occurredAtEpochMs, zoneId).toEpochDay()
                    txDay in startDay..endDay
                }
                filterAndSum(
                    label = "${selectedMonth.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${selectedMonth.year}",
                    startDay = startDay,
                    endDay = endDay,
                    isOpenEnded = false,
                    transactions = filtered,
                    zoneId = zoneId
                )
            }
        }
    }

    private fun filterAndSum(
        label: String,
        startDay: Long,
        endDay: Long?,
        isOpenEnded: Boolean,
        transactions: List<FinanceTransaction>,
        fallbackSalarySen: Long = 0L,
        @Suppress("UNUSED_PARAMETER") zoneId: ZoneId
    ): PeriodCashflowReport {
        val income = transactions
            .filter { it.type == TransactionType.INCOME }
            .sumOf { it.amountSen }
        val expense = transactions
            .filter { it.type == TransactionType.EXPENSE }
            .sumOf { it.amountSen }
        val salaryFromTx = transactions
            .filter { it.type == TransactionType.INCOME && it.category.equals("Salary", ignoreCase = true) }
            .sumOf { it.amountSen }

        return PeriodCashflowReport(
            periodLabel = label,
            startEpochDay = startDay,
            endEpochDay = if (isOpenEnded) null else endDay,
            incomeSen = income,
            expenseSen = expense,
            receivedSalarySen = if (salaryFromTx > 0L) salaryFromTx else fallbackSalarySen
        )
    }
}
