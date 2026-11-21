package com.diniy.duit.domain.calendar

import com.diniy.duit.domain.model.Commitment
import com.diniy.duit.domain.model.CommitmentFrequency
import com.diniy.duit.domain.model.CommitmentStatus
import com.diniy.duit.domain.model.FinanceTransaction
import com.diniy.duit.domain.model.PrismaticAccentToken
import com.diniy.duit.domain.model.SalaryCycle
import com.diniy.duit.domain.model.SavingsGoal
import com.diniy.duit.domain.model.SavingsGoalStatus
import com.diniy.duit.domain.model.TransactionType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarEventProjectorTest {
    private val kl = ZoneId.of("Asia/Kuala_Lumpur")
    private val nov = YearMonth.of(2026, 11)
    private fun day(d: Int) = LocalDate.of(2026, 11, d).toEpochDay()
    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0) =
        LocalDateTime.of(y, m, d, h, min).atZone(kl).toInstant().toEpochMilli()

    private fun txn(id: Long, type: TransactionType, sen: Long, ms: Long, commitmentId: Long? = null) =
        FinanceTransaction(id, 1, if (type == TransactionType.TRANSFER) 2 else null, type, sen, "Food", "t$id", "", commitmentId, ms, ms)

    private fun salary(received: Boolean) = SalaryCycle(
        id = 7, label = "Salary", startDateEpochDay = day(1), endDateEpochDay = day(30), paydayEpochDay = day(25),
        baseSalarySen = 400_000, committedBudgetSen = 0, isActive = true,
        receivedSalarySen = if (received) 398_000 else 0, salaryTransactionId = if (received) 99 else null
    )

    private fun bill(status: CommitmentStatus, recurring: Boolean = false, freq: CommitmentFrequency = CommitmentFrequency.ONE_TIME) =
        Commitment(id = 3, title = "Rent", amountSen = 120_000, dueDateEpochDay = day(5), status = status,
            walletId = 1, isRecurring = recurring, salaryCycleId = null, frequency = freq)

    private fun project(
        t: List<FinanceTransaction> = emptyList(), s: List<SalaryCycle> = emptyList(),
        c: List<Commitment> = emptyList(), g: List<SavingsGoal> = emptyList()
    ) = CalendarEventProjector.project(nov, t, s, c, g, kl)

    private fun CalendarMonth.all() = eventsByDay.values.flatten()

    @Test
    fun expectedSalary_isNeverCountedAsIncome() {
        val m = project(s = listOf(salary(received = false)))
        val e = m.all().single()
        assertEquals(CalendarEventStatus.EXPECTED, e.status)
        assertEquals(400_000L, e.amountSen)
        assertTrue(m.summaries.isEmpty())
    }

    @Test
    fun receivedSalary_countsOnce_throughItsTransactionOnly() {
        val m = project(t = listOf(txn(99, TransactionType.INCOME, 398_000, at(2026, 11, 25, 9))), s = listOf(salary(received = true)))
        assertEquals(CalendarEventStatus.RECEIVED, m.all().single { it.type == CalendarEventType.INCOME_DATE }.status)
        assertEquals(398_000L, m.summaries.getValue(day(25)).incomeSen)
    }

    @Test
    fun upcomingBill_isNotAnExpense_andPaidBillCountsOnlyItsPayment() {
        val upcoming = project(c = listOf(bill(CommitmentStatus.UPCOMING)))
        assertEquals(CalendarEventStatus.UPCOMING, upcoming.all().single().status)
        assertTrue(upcoming.summaries.isEmpty())

        val paid = project(c = listOf(bill(CommitmentStatus.PAID)), t = listOf(txn(50, TransactionType.EXPENSE, 120_000, at(2026, 11, 5, 10), commitmentId = 3)))
        assertEquals(CalendarEventStatus.PAID, paid.all().single { it.type == CalendarEventType.BILL }.status)
        assertEquals(120_000L, paid.summaries.getValue(day(5)).expenseSen)
    }

    @Test
    fun recurringBill_projectsLaterOccurrences_asUpcoming() {
        val m = project(c = listOf(bill(CommitmentStatus.PAID, recurring = true, freq = CommitmentFrequency.WEEKLY)))
        val bills = m.all().filter { it.type == CalendarEventType.BILL }
        assertEquals(listOf(5, 12, 19, 26).map(::day), bills.map { it.epochDay })
        assertEquals(CalendarEventStatus.PAID, bills.first().status)
        assertTrue(bills.drop(1).all { it.status == CalendarEventStatus.UPCOMING })
    }

    @Test
    fun transfers_areShown_butNeverIncomeOrExpense() {
        val m = project(t = listOf(txn(1, TransactionType.TRANSFER, 50_000, at(2026, 11, 10, 12))))
        assertEquals(TransactionType.TRANSFER, m.all().single().transactionType)
        assertNull(m.summaries[day(10)])
    }

    @Test
    fun dailyTotals_addIncomeAndExpense() {
        val ms = at(2026, 11, 3, 8)
        val m = project(t = listOf(
            txn(1, TransactionType.INCOME, 10_000, ms), txn(2, TransactionType.EXPENSE, 2_480, ms),
            txn(3, TransactionType.EXPENSE, 1_520, ms), txn(4, TransactionType.TRANSFER, 9_999, ms)
        ))
        val s = m.summaries.getValue(day(3))
        assertEquals(10_000L, s.incomeSen)
        assertEquals(4_000L, s.expenseSen)
        assertEquals(6_000L, s.netSen)
    }

    @Test
    fun localMidnight_decidesTheDay_andOtherMonthsAreExcluded() {
        val m = project(t = listOf(
            txn(1, TransactionType.EXPENSE, 100, at(2026, 10, 31, 23, 59)), // October, excluded
            txn(2, TransactionType.EXPENSE, 200, at(2026, 11, 1, 0, 0)),    // 1 Nov local (16:00 UTC on 31 Oct)
            txn(3, TransactionType.EXPENSE, 300, at(2026, 11, 30, 23, 59)), // still November
            txn(4, TransactionType.EXPENSE, 400, at(2026, 12, 1, 0, 0))     // December, excluded
        ))
        assertEquals(setOf(day(1), day(30)), m.summaries.keys)
        val (start, end) = CalendarEventProjector.monthRangeEpochMs(nov, kl)
        assertEquals(at(2026, 11, 1, 0), start)
        assertEquals(at(2026, 12, 1, 0), end)
    }

    @Test
    fun duplicates_areNotRepeated_orDoubleCounted() {
        val t = txn(1, TransactionType.EXPENSE, 500, at(2026, 11, 8, 9))
        val m = project(t = listOf(t, t), c = listOf(bill(CommitmentStatus.UPCOMING), bill(CommitmentStatus.UPCOMING)))
        assertEquals(1, m.all().count { it.type == CalendarEventType.TRANSACTION })
        assertEquals(1, m.all().count { it.type == CalendarEventType.BILL })
        assertEquals(500L, m.summaries.getValue(day(8)).expenseSen)
    }

    @Test
    fun goalDeadlines_areInformational_andArchivedGoalsHidden() {
        fun goal(id: Long, status: SavingsGoalStatus) = SavingsGoal(id, "Trip $id", 500_000, day(20), "", "star",
            PrismaticAccentToken.AQUA, status, 0, 0)
        val m = project(g = listOf(goal(1, SavingsGoalStatus.ACTIVE), goal(2, SavingsGoalStatus.ARCHIVED), goal(3, SavingsGoalStatus.COMPLETED)))
        assertEquals(listOf(CalendarEventStatus.DUE, CalendarEventStatus.COMPLETED), m.all().map { it.status })
        assertTrue(m.summaries.isEmpty())
    }

    private fun payLater(paidSen: Long, archived: Boolean = false) = com.diniy.duit.domain.model.PayLaterPlan(
        id = 8, title = "Phone", providerName = "Atome", purchaseAmountSen = 3_000, feeOrInterestSen = 0, paidSen = paidSen,
        instalmentCount = 3, firstDueDateEpochDay = LocalDate.of(2026, 10, 5).toEpochDay(), nextDueDateEpochDay = day(5),
        accentToken = PrismaticAccentToken.AQUA, note = "", isArchived = archived, createdAtEpochMs = 0, updatedAtEpochMs = 0
    )

    private fun debt(id: Long, dir: com.diniy.duit.domain.model.DebtDirection, due: Long?, paid: Long) = com.diniy.duit.domain.model.DebtRecord(
        id = id, direction = dir, counterpartyName = "Aiman", title = "Loan $id", originalAmountSen = 50_000, paidSen = paid,
        dueDateEpochDay = due, accentToken = PrismaticAccentToken.AQUA, note = "", isArchived = false,
        createdAtEpochMs = 0, updatedAtEpochMs = 0, scheduledRepaymentSen = 20_000
    )

    @Test
    fun payLaterInstalments_areBills_paidOrUpcoming_neverExpenses() {
        // Oct instalment paid (1,000 of 3,000); Nov instalment still open.
        val m = CalendarEventProjector.project(nov, emptyList(), emptyList(), emptyList(), emptyList(), kl, payLaterPlans = listOf(payLater(1_000)))
        val inst = m.all().single()
        assertEquals(CalendarEventType.BILL, inst.type)
        assertEquals(day(5), inst.epochDay)
        assertEquals(CalendarEventStatus.UPCOMING, inst.status)
        assertEquals(1_000L, inst.amountSen)
        assertTrue(m.summaries.isEmpty())
        val paid = CalendarEventProjector.project(nov, emptyList(), emptyList(), emptyList(), emptyList(), kl, payLaterPlans = listOf(payLater(2_000)))
        assertEquals(CalendarEventStatus.PAID, paid.all().single().status)
        assertTrue(CalendarEventProjector.project(nov, emptyList(), emptyList(), emptyList(), emptyList(), kl,
            payLaterPlans = listOf(payLater(0, archived = true))).eventsByDay.isEmpty())
    }

    @Test
    fun debtsIOwe_withDueDates_areBills_receivablesAndUndatedAreNot() {
        val owe = com.diniy.duit.domain.model.DebtDirection.I_OWE
        val m = CalendarEventProjector.project(nov, emptyList(), emptyList(), emptyList(), emptyList(), kl, debts = listOf(
            debt(1, owe, day(15), 0),
            debt(2, owe, day(16), 50_000),
            debt(3, com.diniy.duit.domain.model.DebtDirection.OWED_TO_ME, day(17), 0),
            debt(4, owe, null, 0)
        ))
        val bills = m.all()
        assertEquals(listOf("debt:1", "debt:2"), bills.map { it.key })
        assertEquals(20_000L, bills[0].amountSen)
        assertEquals(CalendarEventStatus.UPCOMING, bills[0].status)
        assertEquals(CalendarEventStatus.PAID, bills[1].status)
        assertTrue(m.summaries.isEmpty())
    }
}
