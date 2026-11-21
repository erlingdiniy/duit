package com.diniy.duit.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.diniy.duit.data.local.entity.ReceiptEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReceiptDao {

    @Query("SELECT * FROM receipts ORDER BY dateEpochDay DESC, id DESC")
    fun observeAll(): Flow<List<ReceiptEntity>>

    @Query("SELECT * FROM receipts WHERE transactionId = :transactionId ORDER BY dateEpochDay DESC, id DESC")
    fun observeForTransaction(transactionId: Long): Flow<List<ReceiptEntity>>

    @Query("SELECT * FROM receipts WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ReceiptEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ReceiptEntity): Long

    @Update
    suspend fun update(entity: ReceiptEntity)

    @Query("UPDATE receipts SET transactionId = :transactionId, updatedAtEpochMs = :updatedAtEpochMs WHERE id = :id")
    suspend fun updateTransactionLink(id: Long, transactionId: Long?, updatedAtEpochMs: Long)

    @Query("DELETE FROM receipts WHERE id = :id")
    suspend fun deleteById(id: Long)
}
