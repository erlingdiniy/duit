package com.diniy.duit.domain.calendar

import com.diniy.duit.domain.calculator.ObligationCalculator
import com.diniy.duit.domain.model.Commitment
import com.diniy.duit.domain.model.CommitmentFrequency
import com.diniy.duit.domain.model.CommitmentStatus
import com.diniy.duit.domain.model.FinanceTransaction
import com.diniy.duit.domain.model.SalaryCycle
import com.diniy.duit.domain.model.SavingsGoal
import com.diniy.duit.domain.model.SavingsGoalStatus
import com.diniy.duit.domain.model.TransactionType
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

enum class CalendarEventType { INCOME_DATE, BILL, TRANSACTION, GOAL_DEADLINE }

/** EXPECTED/UPCOMING/DUE are plans only; RECEIVED/PAID mark plans already settled by a real transaction. */
enum class CalendarEventStatus { EXPECTED, RECEIVED, UPCOMING, PAID, RECORDED, DUE, COMPLETED }

data class CalendarEvent(
    /** Stable unique key, e.g. "bill:12:20410", so the same occurrence is never shown twice. */
    val key: String,
    val type: CalendarEventType,
    val epochDay: Long,
    val title: String,
    val amountSen: Long?,
    val status: CalendarEventStatus,
    val transactionType: TransactionType? = null
)

/** Real money only: built from recorded INCOME and EXPENSE transactions, never from plans or transfers. */
data class DailyMoneySummary(val epochDay: Long, val incomeSen: Long, val expenseSen: Long) {
    val netSen: Long get() = incomeSen - expenseSen
}

data class CalendarMonth(
    val month: YearMonth,
    val eventsByDay: Map<Long, List<CalendarEvent>>,
    val summaries: Map<Long, DailyMoneySummary>
)

/**
 * Pure, read-only projection of one month for the Smart Money Calendar (event types 1-5).
 * It never writes or creates transactions: salary dates, bills and goal deadlines are informational.
 */
object CalendarEventProjector {

    /** [start, end) in epoch ms for [month] in [zone], to query only that month's transactions. */
    fun monthRangeEpochMs(month: YearMonth, zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> =
        month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli() to
            month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()

    fun project(
        month: YearMonth,
        transactions: List<FinanceTransaction>,
        salaryCycles: List<SalaryCycle>,
        commitments: List<Commitment>,
        goals: List<SavingsGoal>,
        zone: ZoneId = ZoneId.systemDefault()
    ): CalendarMonth {
        val first = month.atDay(1).toEpochDay()
        val last = month.atEndOfMonth().toEpochDay()
        val inMonth = first..last
        val events = mutableListOf<CalendarEvent>()

        // 1. Salary & income dates: expected until a salary transaction is linked.
        salaryCycles.filter { it.paydayEpochDay in inMonth }.forEach { cycle ->
            val received = cycle.salaryTransactionId != null || cycle.receivedSalarySen > 0
            events += CalendarEvent(
                key = "salary:${cycle.id}",
                type = CalendarEventType.INCOME_DATE,
                epochDay = cycle.paydayEpochDay,
                title = cycle.label,
                amountSen = if (received) cycle.receivedSalarySen else cycle.baseSalarySen,
                status = if (received) CalendarEventStatus.RECEIVED else CalendarEventStatus.EXPECTED
            )
        }

        // 2. Bills & commitments: the current occurrence plus later recurring ones in this month.
        commitments.filter { !it.isArchived }.forEach { c ->
            val paid = c.status == CommitmentStatus.PAID
            var day = c.dueDateEpochDay
            var guard = 0
            while (day <= last && guard++ < MAX_OCCURRENCES) {
                if (day in inMonth) {
                    val isCurrent = day == c.dueDateEpochDay
                    events += CalendarEvent(
                        key = "bill:${c.id}:$day",
                        type = CalendarEventType.BILL,
                        epochDay = day,
                        title = c.title,
                        amountSen = c.amountSen,
                        status = if (isCurrent && paid) CalendarEventStatus.PAID else CalendarEventStatus.UPCOMING
                    )
                }
                if (!c.isRecurring || c.frequency == CommitmentFrequency.ONE_TIME) break
                val next = ObligationCalculator.advanceDueDate(day, c.frequency, c.customIntervalDays, c.anchorDayOfMonth)
                if (next <= day) break
                day = next
            }
        }

        // 3. Daily transactions (transfers shown, but tagged so they never count as income or expense).
        val monthTransactions = transactions
            .distinctBy { it.id }
            .map { it to Instant.ofEpochMilli(it.occurredAtEpochMs).atZone(zone).toLocalDate().toEpochDay() }
            .filter { (_, day) -> day in inMonth }
        monthTransactions.forEach { (t, day) ->
            events += CalendarEvent(
                key = "txn:${t.id}",
                type = CalendarEventType.TRANSACTION,
                epochDay = day,
                title = t.title.ifBlank { t.category },
                amountSen = t.amountSen,
                status = CalendarEventStatus.RECORDED,
                transactionType = t.type
            )
        }

        // 5. Savings goal deadlines (archived goals hidden).
        goals.filter { it.status != SavingsGoalStatus.ARCHIVED && it.targetDateEpochDay in inMonth }.forEach { g ->
            events += CalendarEvent(
                key = "goal:${g.id}",
                type = CalendarEventType.GOAL_DEADLINE,
                epochDay = g.targetDateEpochDay!!,
                title = g.name,
                amountSen = g.targetAmountSen,
                status = if (g.status == SavingsGoalStatus.COMPLETED) CalendarEventStatus.COMPLETED else CalendarEventStatus.DUE
            )
        }

        // 4. Daily money summary: recorded income and expenses only.
        val summaries = monthTransactions
            .filter { (t, _) -> t.type != TransactionType.TRANSFER }
            .groupBy({ it.second }, { it.first })
            .mapValues { (day, list) ->
                DailyMoneySummary(
                    epochDay = day,
                    incomeSen = list.filter { it.type == TransactionType.INCOME }.sumOf { it.amountSen },
                    expenseSen = list.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountSen }
                )
            }

        return CalendarMonth(
            month = month,
            eventsByDay = events.distinctBy { it.key }.groupBy { it.epochDay }.toSortedMap(),
            summaries = summaries.toSortedMap()
        )
    }

    private const val MAX_OCCURRENCES = 400
}
