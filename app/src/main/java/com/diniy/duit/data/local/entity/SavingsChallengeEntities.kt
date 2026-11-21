package com.diniy.duit.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.diniy.duit.domain.challenge.ChallengeType
import com.diniy.duit.domain.challenge.GardenStage
import com.diniy.duit.domain.challenge.SavingsChallenge

/** Schema v11. A plan on an existing goal: no wallet, no balance, no money of its own. */
@Entity(
    tableName = "savings_challenges",
    foreignKeys = [
        ForeignKey(entity = SavingsGoalEntity::class, parentColumns = ["id"], childColumns = ["goalId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["goalId"])]
)
data class SavingsChallengeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val goalId: Long,
    val type: String,
    val baseAmountSen: Long,
    val startEpochDay: Long,
    val slotCount: Int,
    val intervalDays: Int,
    val randomSeed: Long,
    val isPaused: Boolean,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)

/** Slots the user chose to skip (done is never stored: it comes from real savings movements). */
@Entity(
    tableName = "challenge_skipped_slots",
    primaryKeys = ["challengeId", "slotIndex"],
    foreignKeys = [
        ForeignKey(entity = SavingsChallengeEntity::class, parentColumns = ["id"], childColumns = ["challengeId"], onDelete = ForeignKey.CASCADE)
    ]
)
data class ChallengeSkippedSlotEntity(
    val challengeId: Long,
    val slotIndex: Int
)

/** Earned stickers are history: no foreign key, so they stay after a challenge is removed. */
@Entity(
    tableName = "challenge_stickers",
    indices = [Index(value = ["challengeId", "stage"], unique = true)]
)
data class ChallengeStickerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val challengeId: Long,
    val stage: String,
    val earnedAtEpochMs: Long
)

fun SavingsChallengeEntity.toDomain() = SavingsChallenge(
    id = id,
    goalId = goalId,
    type = ChallengeType.entries.firstOrNull { it.name == type } ?: ChallengeType.CUSTOM,
    baseAmountSen = baseAmountSen,
    startEpochDay = startEpochDay,
    slotCount = slotCount,
    intervalDays = intervalDays,
    randomSeed = randomSeed,
    isPaused = isPaused
)

fun SavingsChallenge.toEntity(createdAtEpochMs: Long, updatedAtEpochMs: Long) = SavingsChallengeEntity(
    id = id, goalId = goalId, type = type.name, baseAmountSen = baseAmountSen, startEpochDay = startEpochDay,
    slotCount = slotCount, intervalDays = intervalDays, randomSeed = randomSeed, isPaused = isPaused,
    createdAtEpochMs = createdAtEpochMs, updatedAtEpochMs = updatedAtEpochMs
)

fun ChallengeStickerEntity.stageOrNull(): GardenStage? = GardenStage.entries.firstOrNull { it.name == stage }
