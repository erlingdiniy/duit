package com.diniy.duit.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.diniy.duit.domain.wins.PersistedMilestone

/**
 * Persisted Little Wins Club milestone sticker. Unique index on [milestoneKey] guarantees
 * each milestone is awarded once, preserved historically, and never duplicated.
 */
@Entity(
    tableName = "earned_milestones",
    indices = [Index(value = ["milestoneKey"], unique = true)]
)
data class EarnedMilestoneEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val milestoneKey: String,
    val earnedAtEpochMs: Long,
    val celebratedAtEpochMs: Long? = null
)

fun EarnedMilestoneEntity.toDomain(): PersistedMilestone = PersistedMilestone(
    milestoneKey = milestoneKey,
    earnedAtEpochMs = earnedAtEpochMs,
    celebratedAtEpochMs = celebratedAtEpochMs
)
