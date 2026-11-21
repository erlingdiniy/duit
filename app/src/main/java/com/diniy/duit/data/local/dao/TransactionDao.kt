package com.diniy.duit.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.diniy.duit.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY occurredAtEpochMs DESC, id DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY occurredAtEpochMs DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE walletId = :walletId OR targetWalletId = :walletId ORDER BY occurredAtEpochMs DESC, id DESC")
    fun observeForWallet(walletId: Long): Flow<List<TransactionEntity>>

    /** Transactions in [startEpochMs, endEpochMs), e.g. one calendar month. Query only, no schema change. */
    @Query("SELECT * FROM transactions WHERE occurredAtEpochMs >= :startEpochMs AND occurredAtEpochMs < :endEpochMs ORDER BY occurredAtEpochMs DESC, id DESC")
    fun observeBetween(startEpochMs: Long, endEpochMs: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE submissionId = :submissionId LIMIT 1")
    suspend fun findBySubmissionId(submissionId: String): TransactionEntity?

    @Query("SELECT COUNT(*) FROM transactions WHERE walletId = :walletId OR targetWalletId = :walletId")
    suspend fun countForWallet(walletId: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)
}
