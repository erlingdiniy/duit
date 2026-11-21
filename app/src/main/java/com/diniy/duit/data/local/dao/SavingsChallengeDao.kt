package com.diniy.duit.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.diniy.duit.data.local.entity.ChallengeSkippedSlotEntity
import com.diniy.duit.data.local.entity.ChallengeStickerEntity
import com.diniy.duit.data.local.entity.SavingsChallengeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavingsChallengeDao {
    @Query("SELECT * FROM savings_challenges ORDER BY startEpochDay DESC, id DESC")
    fun observeChallenges(): Flow<List<SavingsChallengeEntity>>

    @Query("SELECT * FROM savings_challenges WHERE id = :id")
    suspend fun getChallenge(id: Long): SavingsChallengeEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertChallenge(challenge: SavingsChallengeEntity): Long

    @Update
    suspend fun updateChallenge(challenge: SavingsChallengeEntity)

    @Query("DELETE FROM savings_challenges WHERE id = :id")
    suspend fun deleteChallenge(id: Long)

    @Query("SELECT * FROM challenge_skipped_slots")
    fun observeSkippedSlots(): Flow<List<ChallengeSkippedSlotEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSkippedSlot(slot: ChallengeSkippedSlotEntity)

    @Query("DELETE FROM challenge_skipped_slots WHERE challengeId = :challengeId AND slotIndex = :slotIndex")
    suspend fun deleteSkippedSlot(challengeId: Long, slotIndex: Int)

    @Query("SELECT * FROM challenge_stickers ORDER BY earnedAtEpochMs ASC, id ASC")
    fun observeStickers(): Flow<List<ChallengeStickerEntity>>

    /** IGNORE on the unique (challengeId, stage) index: a sticker is earned once and never duplicated. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertStickers(stickers: List<ChallengeStickerEntity>)
}
