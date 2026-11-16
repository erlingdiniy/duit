package com.diniy.duit.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.diniy.duit.data.local.entity.CommissionEntity
import com.diniy.duit.domain.model.CommissionStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface CommissionDao {
    @Query("SELECT * FROM commissions ORDER BY earnedDateEpochDay DESC, id DESC")
    fun observeAll(): Flow<List<CommissionEntity>>

    @Query("SELECT * FROM commissions WHERE id = :id")
    suspend fun getById(id: Long): CommissionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(commission: CommissionEntity): Long

    @Update
    suspend fun update(commission: CommissionEntity)

    @Query("UPDATE commissions SET status = :status, payoutDateEpochDay = :payoutDateEpochDay WHERE id = :id")
    suspend fun updateStatus(id: Long, status: CommissionStatus, payoutDateEpochDay: Long?)

    @Query("DELETE FROM commissions WHERE id = :id")
    suspend fun deleteById(id: Long)
}
