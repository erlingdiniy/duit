package com.diniy.duit.notifications

import com.diniy.duit.domain.calculator.ReminderCalculator
import com.diniy.duit.domain.model.SavingsAllocation
import com.diniy.duit.domain.model.SavingsGoal
import com.diniy.duit.domain.model.SavingsGoalStatus
import com.diniy.duit.domain.model.Commitment
import com.diniy.duit.domain.model.CommitmentFrequency
import com.diniy.duit.domain.model.CommitmentStatus
import com.diniy.duit.domain.model.PrismaticAccentToken
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase11MNotificationCopyTest {
    private val zone = ZoneId.of("Asia/Kuala_Lumpur")
    private val today = LocalDate.of(2026, 11, 16)
    private val now = ZonedDateTime.of(today, LocalTime.of(10, 0), zone).toInstant()

    private fun eval(settings: NotificationSettingsSnapshot, goals: List<SavingsGoal> = emptyList(),
                     allocs: List<SavingsAllocation> = emptyList(), bills: List<Commitment> = emptyList()) =
        ReminderCalculator.evaluate(settings, bills, emptyList(), emptyList(), goals, allocs, null, emptyList(), now, zone)

    private fun goal(status: SavingsGoalStatus = SavingsGoalStatus.ACTIVE) = SavingsGoal(
        1L, "Trip", 100_000L, null, "", "", PrismaticAccentToken.entries.first(), status, 0L, 0L
    )

    @Test fun dailyAndRecapAreOptIn_andDailyCopyRotatesOnConsecutiveDays() {
        assertFalse(NotificationCategory.DAILY in NotificationSettingsSnapshot().enabledCategories)
        val s = NotificationSettingsSnapshot(enabledCategories = setOf(NotificationCategory.DAILY))
        val e = eval(s)
        val todayC = e.dueNowCandidates.single()
        val tomorrow = e.upcomingCandidates.single()
        assertNotEquals(todayC.detailedTitle, tomorrow.detailedTitle)
        assertNotEquals(todayC.detailedBody, tomorrow.detailedBody)
    }

    @Test fun savingsMilestone_firesOnlyForRecentFundsAndUsesHighestMilestone() {
        val s = NotificationSettingsSnapshot(enabledCategories = setOf(NotificationCategory.SAVINGS_GOALS))
        val recent = SavingsAllocation(1, 1, 1, 60_000L, now.toEpochMilli() - 1000)
        val c = eval(s, listOf(goal()), listOf(recent)).dueNowCandidates.single()
        assertEquals("SAVINGS_GOALS:1:M50", c.dedupKey)
        assertEquals("Your Trip savings goal reached 50%.", c.detailedBody)
        val old = recent.copy(updatedAtEpochMs = now.toEpochMilli() - 5 * 86_400_000L)
        assertTrue(eval(s, listOf(goal()), listOf(old)).dueNowCandidates.isEmpty())
        val done = recent.copy(allocatedSen = 100_000L)
        assertEquals("You did it! 🥳", eval(s, listOf(goal(SavingsGoalStatus.COMPLETED)), listOf(done)).dueNowCandidates.single().detailedTitle)
    }

    @Test fun billCopy_matchesSpec_andPrivateVersionHidesNameAndAmount() {
        val s = NotificationSettingsSnapshot(enabledCategories = setOf(NotificationCategory.COMMITMENTS))
        val bill = Commitment(1L, "Rent", 100_000L, today.plusDays(3).toEpochDay(), CommitmentStatus.UPCOMING, 1L, true, null, frequency = CommitmentFrequency.MONTHLY)
        val c = eval(s, bills = listOf(bill)).dueNowCandidates.first { it.offsetDaysBefore == 3 }
        assertEquals("Psst, a bill is coming! 🔔", c.detailedTitle)
        assertEquals("Your Rent bill of RM 1,000.00 is due in 3 days.", c.detailedBody)
        assertFalse(c.privateBody.contains("Rent") || c.privateBody.contains("1,000"))
    }
}
