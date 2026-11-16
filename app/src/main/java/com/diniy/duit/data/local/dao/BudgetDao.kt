package com.diniy.duit.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.diniy.duit.data.local.entity.BudgetEntity
import com.diniy.duit.domain.model.BudgetPeriodType
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets ORDER BY isArchived ASC, updatedAtEpochMs DESC, id DESC")
    fun observeAll(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): BudgetEntity?

    @Query(
        """
        SELECT * FROM budgets
        WHERE isArchived = 0
          AND periodType = :periodType
          AND yearMonth = :yearMonth
          AND ((:salaryCycleId IS NULL AND salaryCycleId IS NULL) OR salaryCycleId = :salaryCycleId)
        """
    )
    suspend fun getActiveForPeriod(
        periodType: BudgetPeriodType,
        yearMonth: String,
        salaryCycleId: Long?
    ): List<BudgetEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(budget: BudgetEntity): Long

    @Update
    suspend fun update(budget: BudgetEntity): Int

    @Query("UPDATE budgets SET isArchived = :archived, updatedAtEpochMs = :updatedAtEpochMs WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean, updatedAtEpochMs: Long)

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun deleteById(id: Long)
}
