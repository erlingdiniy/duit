package com.diniy.duit.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.diniy.duit.data.local.entity.EarnedMilestoneEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MilestoneDao {

    @Query("SELECT * FROM earned_milestones ORDER BY earnedAtEpochMs ASC, id ASC")
    fun observeEarned(): Flow<List<EarnedMilestoneEntity>>

    /** IGNORE on unique milestoneKey: each sticker is awarded once and never duplicated. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(items: List<EarnedMilestoneEntity>): List<Long>

    @Query("UPDATE earned_milestones SET celebratedAtEpochMs = :celebratedAtEpochMs WHERE milestoneKey IN (:keys) AND celebratedAtEpochMs IS NULL")
    suspend fun markCelebrated(keys: List<String>, celebratedAtEpochMs: Long)
}
