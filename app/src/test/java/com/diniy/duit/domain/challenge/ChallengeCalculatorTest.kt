package com.diniy.duit.domain.challenge

import com.diniy.duit.domain.model.SavingsMovement
import com.diniy.duit.domain.model.SavingsMovementType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChallengeCalculatorTest {
    private val kl = ZoneId.of("Asia/Kuala_Lumpur")
    private val start = LocalDate.of(2026, 11, 2).toEpochDay()
    private fun at(m: Int, d: Int, h: Int = 12) = LocalDateTime.of(2026, m, d, h, 0).atZone(kl).toInstant().toEpochMilli()

    private var nextId = 1L
    private fun move(type: SavingsMovementType, sen: Long, ms: Long = at(11, 10), goal: Long = 1, related: Long? = null) =
        SavingsMovement(nextId++, goal, 1, 1, related, type, sen, null, "", ms)

    private fun week52(base: Long = 100) = SavingsChallenge(1, goalId = 1, type = ChallengeType.WEEK_52, baseAmountSen = base, startEpochDay = start)

    @Test
    fun week52_ascendingWeekly_totalsRm1378ForRm1Base() {
        val slots = ChallengeCalculator.schedule(week52())
        assertEquals(52, slots.size)
        assertEquals(100L, slots.first().amountSen)
        assertEquals(5_200L, slots.last().amountSen)
        assertEquals(start + 51 * 7, slots.last().dueEpochDay)
        assertEquals(137_800L, slots.sumOf { it.amountSen })
    }

    @Test
    fun dailyCustomAndRandom_schedules() {
        val daily = ChallengeCalculator.schedule(SavingsChallenge(2, 1, ChallengeType.DAILY, 500, start, slotCount = 30))
        assertEquals(30, daily.size)
        assertTrue(daily.all { it.amountSen == 500L })
        assertEquals(start + 29, daily.last().dueEpochDay)

        val custom = ChallengeCalculator.schedule(SavingsChallenge(3, 1, ChallengeType.CUSTOM, 2_000, start, slotCount = 6, intervalDays = 14))
        assertEquals(listOf(0L, 14L, 28L, 42L, 56L, 70L), custom.map { it.dueEpochDay - start })

        val r1 = ChallengeCalculator.schedule(SavingsChallenge(4, 1, ChallengeType.RANDOM, 100, start, slotCount = 20, randomSeed = 42))
        val r2 = ChallengeCalculator.schedule(SavingsChallenge(4, 1, ChallengeType.RANDOM, 100, start, slotCount = 20, randomSeed = 42))
        assertEquals(r1, r2) // fixed seed: never reshuffles
        assertEquals((1..20L).map { it * 100 }.toSet(), r1.map { it.amountSen }.toSet())
        assertFalse(r1.map { it.amountSen } == (1..20L).map { it * 100 })
    }

    @Test
    fun realSavingsOnly_withdrawalsReduce_andOtherGoalsOrEarlierMovesIgnored() {
        val movements = listOf(
            move(SavingsMovementType.ALLOCATE, 600),
            move(SavingsMovementType.TRANSFER_AND_ALLOCATE, 400),
            move(SavingsMovementType.LINK_PROTECTED, 500),
            move(SavingsMovementType.WITHDRAW_TO_WALLET, 300),
            move(SavingsMovementType.TRANSFER_TO_SPENDING, 200),
            move(SavingsMovementType.ALLOCATE, 9_999, goal = 2),             // another goal
            move(SavingsMovementType.ALLOCATE, 7_777, ms = at(11, 1, 23))    // before the start day (KL time)
        )
        assertEquals(1_000L, ChallengeCalculator.netProgressSen(1, movements, start, kl))
    }

    @Test
    fun reassign_movesProgressBetweenGoals() {
        val movements = listOf(
            move(SavingsMovementType.ALLOCATE, 1_000, goal = 1),
            move(SavingsMovementType.REASSIGN, 400, goal = 1, related = 2)
        )
        assertEquals(600L, ChallengeCalculator.netProgressSen(1, movements, start, kl))
        assertEquals(400L, ChallengeCalculator.netProgressSen(2, movements, start, kl))
    }

    @Test
    fun noDoubleCounting_duplicatesOnce_andProgressClampedToTarget() {
        val m = move(SavingsMovementType.ALLOCATE, 1_000)
        assertEquals(1_000L, ChallengeCalculator.netProgressSen(1, listOf(m, m), start, kl))
        val p = ChallengeCalculator.progress(SavingsChallenge(1, 1, ChallengeType.DAILY, 100, start, slotCount = 5), listOf(m), zone = kl)
        assertEquals(500L, p.targetSen)
        assertEquals(500L, p.savedSen)
        assertEquals(100, p.percent)
        assertNull(p.nextSlot)
        val negative = ChallengeCalculator.progress(week52(), listOf(move(SavingsMovementType.WITHDRAW_TO_WALLET, 500)), zone = kl)
        assertEquals(0L, negative.savedSen)
    }

    @Test
    fun coveredSlots_skip_andPause() {
        val c = SavingsChallenge(1, 1, ChallengeType.DAILY, 100, start, slotCount = 4)
        val saved = listOf(move(SavingsMovementType.ALLOCATE, 250))
        val p = ChallengeCalculator.progress(c, saved, zone = kl)
        assertEquals(setOf(1, 2), p.coveredIndices)
        assertEquals(3, p.nextSlot!!.index)

        val skipped = ChallengeCalculator.progress(c, saved, skippedIndices = setOf(2), zone = kl)
        assertEquals(300L, skipped.targetSen) // a skipped slot leaves the target, no catch-up owed
        assertEquals(setOf(1, 3), skipped.coveredIndices)
        assertEquals(4, skipped.nextSlot!!.index)

        assertNull(ChallengeCalculator.progress(c.copy(isPaused = true), saved, zone = kl).nextSlot)
    }

    @Test
    fun gardenStages_andHistoricalStickers() {
        assertEquals(GardenStage.SEED, ChallengeCalculator.stageFor(24))
        assertEquals(GardenStage.SPROUT, ChallengeCalculator.stageFor(25))
        assertEquals(GardenStage.BUD, ChallengeCalculator.stageFor(50))
        assertEquals(GardenStage.BLOOM, ChallengeCalculator.stageFor(75))
        assertEquals(GardenStage.HARVEST, ChallengeCalculator.stageFor(100))
        assertEquals(setOf(GardenStage.SPROUT, GardenStage.BUD), ChallengeCalculator.newlyEarnedStickers(GardenStage.BUD, emptySet()))
        // After a withdrawal drops the stage, earned stickers are kept and nothing new is earned.
        assertTrue(ChallengeCalculator.newlyEarnedStickers(GardenStage.SPROUT, setOf(GardenStage.SPROUT, GardenStage.BUD)).isEmpty())
    }

    @Test
    fun lowSafeToSpend_isNeverComfortable() {
        assertTrue(ChallengeCalculator.isComfortable(1_000, 5_000))
        assertFalse(ChallengeCalculator.isComfortable(6_000, 5_000))
        assertFalse(ChallengeCalculator.isComfortable(100, 0))
        assertFalse(ChallengeCalculator.isComfortable(100, -2_000))
    }
}
