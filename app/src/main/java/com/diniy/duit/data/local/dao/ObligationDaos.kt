package com.diniy.duit.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.diniy.duit.data.local.entity.DebtEntity
import com.diniy.duit.data.local.entity.ObligationPaymentEntity
import com.diniy.duit.data.local.entity.PayLaterPlanEntity
import com.diniy.duit.domain.model.ObligationType
import kotlinx.coroutines.flow.Flow

@Dao
interface PayLaterDao {
    @Query("SELECT * FROM paylater_plans ORDER BY isArchived ASC, nextDueDateEpochDay ASC, id DESC")
    fun observeAll(): Flow<List<PayLaterPlanEntity>>

    @Query("SELECT * FROM paylater_plans WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): PayLaterPlanEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(plan: PayLaterPlanEntity): Long

    @Update
    suspend fun update(plan: PayLaterPlanEntity): Int

    @Query("UPDATE paylater_plans SET isArchived = :archived, updatedAtEpochMs = :updatedAtEpochMs WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean, updatedAtEpochMs: Long)

    @Query("DELETE FROM paylater_plans WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface DebtDao {
    @Query("SELECT * FROM debts ORDER BY isArchived ASC, CASE WHEN dueDateEpochDay IS NULL THEN 1 ELSE 0 END, dueDateEpochDay ASC, updatedAtEpochMs DESC")
    fun observeAll(): Flow<List<DebtEntity>>

    @Query("SELECT * FROM debts WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): DebtEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(debt: DebtEntity): Long

    @Update
    suspend fun update(debt: DebtEntity): Int

    @Query("UPDATE debts SET isArchived = :archived, updatedAtEpochMs = :updatedAtEpochMs WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean, updatedAtEpochMs: Long)

    @Query("DELETE FROM debts WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface ObligationPaymentDao {
    @Query("SELECT * FROM obligation_payments ORDER BY paidAtEpochMs DESC, id DESC")
    fun observeAll(): Flow<List<ObligationPaymentEntity>>

    @Query("SELECT * FROM obligation_payments WHERE obligationType = :type AND obligationId = :obligationId ORDER BY paidAtEpochMs DESC, id DESC")
    suspend fun getForObligation(type: ObligationType, obligationId: Long): List<ObligationPaymentEntity>

    @Query("SELECT * FROM obligation_payments WHERE linkedTransactionId = :txId LIMIT 1")
    suspend fun findByTransactionId(txId: Long): ObligationPaymentEntity?

    @Query("SELECT * FROM obligation_payments WHERE submissionId = :submissionId LIMIT 1")
    suspend fun findBySubmissionId(submissionId: String): ObligationPaymentEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(payment: ObligationPaymentEntity): Long

    @Update
    suspend fun update(payment: ObligationPaymentEntity): Int

    @Query("DELETE FROM obligation_payments WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM obligation_payments WHERE obligationType = :type AND obligationId = :obligationId")
    suspend fun deleteForObligation(type: ObligationType, obligationId: Long)
}
