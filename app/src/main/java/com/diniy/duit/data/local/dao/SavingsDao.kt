package com.diniy.duit.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.diniy.duit.data.local.entity.SavingsAllocationEntity
import com.diniy.duit.data.local.entity.SavingsGoalEntity
import com.diniy.duit.data.local.entity.SavingsMovementEntity
import com.diniy.duit.domain.model.SavingsGoalStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface SavingsGoalDao {
    @Query("SELECT * FROM savings_goals ORDER BY CASE status WHEN 'ACTIVE' THEN 0 WHEN 'COMPLETED' THEN 1 ELSE 2 END, updatedAtEpochMs DESC")
    fun observeAll(): Flow<List<SavingsGoalEntity>>

    @Query("SELECT * FROM savings_goals WHERE id = :id")
    suspend fun getById(id: Long): SavingsGoalEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(goal: SavingsGoalEntity): Long

    @Update
    suspend fun update(goal: SavingsGoalEntity): Int

    @Query("UPDATE savings_goals SET status = :status, updatedAtEpochMs = :updatedAtEpochMs WHERE id = :id")
    suspend fun updateStatus(id: Long, status: SavingsGoalStatus, updatedAtEpochMs: Long)

    @Query("DELETE FROM savings_goals WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface SavingsAllocationDao {
    @Query("SELECT * FROM savings_wallet_allocations ORDER BY id ASC")
    fun observeAll(): Flow<List<SavingsAllocationEntity>>

    @Query("SELECT * FROM savings_wallet_allocations WHERE goalId = :goalId")
    suspend fun getForGoal(goalId: Long): List<SavingsAllocationEntity>

    @Query("SELECT * FROM savings_wallet_allocations WHERE goalId = :goalId AND walletId = :walletId LIMIT 1")
    suspend fun getBucket(goalId: Long, walletId: Long): SavingsAllocationEntity?

    @Query("SELECT COALESCE(SUM(allocatedSen), 0) FROM savings_wallet_allocations WHERE walletId = :walletId")
    suspend fun sumForWallet(walletId: Long): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(allocation: SavingsAllocationEntity): Long

    @Query("DELETE FROM savings_wallet_allocations WHERE goalId = :goalId")
    suspend fun deleteAllForGoal(goalId: Long)

    @Query("DELETE FROM savings_wallet_allocations WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface SavingsMovementDao {
    @Query("SELECT * FROM savings_movements ORDER BY occurredAtEpochMs DESC, id DESC")
    fun observeAll(): Flow<List<SavingsMovementEntity>>

    @Query("SELECT * FROM savings_movements WHERE goalId = :goalId OR relatedGoalId = :goalId ORDER BY occurredAtEpochMs DESC, id DESC")
    fun observeForGoal(goalId: Long): Flow<List<SavingsMovementEntity>>

    @Query("SELECT * FROM savings_movements WHERE submissionId = :submissionId LIMIT 1")
    suspend fun findBySubmissionId(submissionId: String): SavingsMovementEntity?

    @Query("SELECT COUNT(*) FROM savings_movements WHERE sourceWalletId = :walletId OR targetWalletId = :walletId")
    suspend fun countForWallet(walletId: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(movement: SavingsMovementEntity): Long
}
