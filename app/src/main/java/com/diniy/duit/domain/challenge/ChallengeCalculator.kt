package com.diniy.duit.domain.challenge

import com.diniy.duit.domain.model.SavingsMovement
import com.diniy.duit.domain.model.SavingsMovementType
import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random

enum class ChallengeType { WEEK_52, DAILY, CUSTOM, RANDOM }

/**
 * A savings challenge is only a plan attached to an existing goal: it holds no money of its own.
 * Real progress always comes from that goal's savings movements ([ChallengeCalculator.netProgressSen]).
 */
data class SavingsChallenge(
    val id: Long,
    val goalId: Long,
    val type: ChallengeType,
    val baseAmountSen: Long,
    val startEpochDay: Long,
    /** Slots for DAILY/CUSTOM/RANDOM; WEEK_52 always has 52. */
    val slotCount: Int = 52,
    /** Days between slots for CUSTOM/RANDOM (DAILY = 1, WEEK_52 = 7). */
    val intervalDays: Int = 7,
    /** Fixed when created, so a Random challenge never reshuffles. */
    val randomSeed: Long = 0L,
    val isPaused: Boolean = false
)

data class ChallengeSlot(val index: Int, val dueEpochDay: Long, val amountSen: Long)

/** A sticker earned at a garden stage; kept as history even if savings later drop. */
data class EarnedSticker(val challengeId: Long, val stage: GardenStage, val earnedAtEpochMs: Long)

/** Growing Garden: a new stage at 25%, 50%, 75% and 100%. */
enum class GardenStage(val minPercent: Int) { SEED(0), SPROUT(25), BUD(50), BLOOM(75), HARVEST(100) }

data class ChallengeProgress(
    val slots: List<ChallengeSlot>,
    /** Target of the slots the user has not skipped. */
    val targetSen: Long,
    /** Net real savings into the goal since the challenge started, clamped to 0..target. */
    val savedSen: Long,
    val percent: Int,
    val stage: GardenStage,
    /** Indices covered, in order, by real savings (skipped slots excluded). */
    val coveredIndices: Set<Int>,
    /** Next slot to save for, or null when paused or finished. */
    val nextSlot: ChallengeSlot?
)

object ChallengeCalculator {
    const val MAX_SLOTS = 366

    fun schedule(challenge: SavingsChallenge): List<ChallengeSlot> {
        val base = challenge.baseAmountSen.coerceAtLeast(0L)
        val (count, interval) = when (challenge.type) {
            ChallengeType.WEEK_52 -> 52 to 7
            ChallengeType.DAILY -> challenge.slotCount to 1
            ChallengeType.CUSTOM, ChallengeType.RANDOM -> challenge.slotCount to challenge.intervalDays
        }
        val n = count.coerceIn(1, MAX_SLOTS)
        val step = interval.coerceAtLeast(1).toLong()
        val amounts: List<Long> = when (challenge.type) {
            // Week n saves n x base (RM1, RM2 ... RM52 for base RM1).
            ChallengeType.WEEK_52 -> (1..n).map { it * base }
            ChallengeType.DAILY, ChallengeType.CUSTOM -> List(n) { base }
            // Same amounts as an ascending challenge, in an order fixed by the seed.
            ChallengeType.RANDOM -> (1..n).map { it * base }.shuffled(Random(challenge.randomSeed))
        }
        return amounts.mapIndexed { i, amount -> ChallengeSlot(i + 1, challenge.startEpochDay + i * step, amount) }
    }

    /**
     * Net real savings into [goalId] from [startEpochDay] on: deposits and reassignments in count up,
     * withdrawals, transfers to spending and reassignments out count down. Amounts are always positive.
     */
    fun netProgressSen(goalId: Long, movements: List<SavingsMovement>, startEpochDay: Long, zone: ZoneId): Long =
        movements
            .distinctBy { it.id }
            .filter { Instant.ofEpochMilli(it.occurredAtEpochMs).atZone(zone).toLocalDate().toEpochDay() >= startEpochDay }
            .sumOf { m ->
                val amount = m.amountSen.coerceAtLeast(0L)
                when {
                    m.type == SavingsMovementType.REASSIGN && m.relatedGoalId == goalId && m.goalId != goalId -> amount
                    m.goalId != goalId -> 0L
                    m.type == SavingsMovementType.ALLOCATE ||
                        m.type == SavingsMovementType.TRANSFER_AND_ALLOCATE ||
                        m.type == SavingsMovementType.LINK_PROTECTED -> amount
                    m.type == SavingsMovementType.WITHDRAW_TO_WALLET ||
                        m.type == SavingsMovementType.TRANSFER_TO_SPENDING ||
                        m.type == SavingsMovementType.REASSIGN -> -amount
                    else -> 0L
                }
            }

    fun progress(
        challenge: SavingsChallenge,
        movements: List<SavingsMovement>,
        skippedIndices: Set<Int> = emptySet(),
        zone: ZoneId = ZoneId.systemDefault()
    ): ChallengeProgress {
        val slots = schedule(challenge)
        val active = slots.filter { it.index !in skippedIndices }
        val target = active.sumOf { it.amountSen }
        val saved = netProgressSen(challenge.goalId, movements, challenge.startEpochDay, zone).coerceIn(0L, target)
        val percent = if (target == 0L) 0 else ((saved * 100L) / target).toInt()

        // Real savings fill the remaining slots in order; a partly-filled slot is not covered yet.
        var left = saved
        val covered = mutableSetOf<Int>()
        for (slot in active) {
            if (left < slot.amountSen) break
            left -= slot.amountSen
            covered += slot.index
        }
        return ChallengeProgress(
            slots = slots,
            targetSen = target,
            savedSen = saved,
            percent = percent,
            stage = stageFor(percent),
            coveredIndices = covered,
            nextSlot = if (challenge.isPaused) null else active.firstOrNull { it.index !in covered }
        )
    }

    fun stageFor(percent: Int): GardenStage = GardenStage.entries.last { percent >= it.minPercent }

    /** Stickers are earned at each garden stage after SEED and kept even if savings later drop. */
    fun newlyEarnedStickers(stage: GardenStage, alreadyEarned: Set<GardenStage>): Set<GardenStage> =
        GardenStage.entries.filter { it != GardenStage.SEED && it.minPercent <= stage.minPercent && it !in alreadyEarned }.toSet()

    /**
     * True when the next amount fits comfortably within Safe to Spend. When false, the UI should offer
     * a gentle "skip or save less" instead of pressure.
     */
    fun isComfortable(nextAmountSen: Long, safeToSpendSen: Long): Boolean = safeToSpendSen > 0L && nextAmountSen <= safeToSpendSen
}
