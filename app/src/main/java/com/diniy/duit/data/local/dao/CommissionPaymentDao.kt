package com.diniy.duit.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.diniy.duit.data.local.entity.CommissionPaymentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CommissionPaymentDao {
    @Query("SELECT * FROM commission_payments ORDER BY receivedAtEpochMs DESC, id DESC")
    fun observeAll(): Flow<List<CommissionPaymentEntity>>

    @Query("SELECT * FROM commission_payments WHERE commissionId = :commissionId ORDER BY receivedAtEpochMs DESC, id DESC")
    suspend fun getForCommission(commissionId: Long): List<CommissionPaymentEntity>

    @Query("SELECT * FROM commission_payments WHERE id = :id")
    suspend fun getById(id: Long): CommissionPaymentEntity?

    @Query("SELECT * FROM commission_payments WHERE submissionId = :submissionId LIMIT 1")
    suspend fun findBySubmissionId(submissionId: String): CommissionPaymentEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(payment: CommissionPaymentEntity): Long

    @Query("DELETE FROM commission_payments WHERE id = :id")
    suspend fun deleteById(id: Long)
}
