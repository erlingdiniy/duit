package com.diniy.duit.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.diniy.duit.data.local.entity.SalaryCycleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SalaryCycleDao {
    @Query("SELECT * FROM salary_cycles ORDER BY startDateEpochDay DESC, id DESC")
    fun observeAll(): Flow<List<SalaryCycleEntity>>

    @Query("SELECT * FROM salary_cycles WHERE isActive = 1 ORDER BY startDateEpochDay DESC LIMIT 1")
    fun observeActive(): Flow<SalaryCycleEntity?>

    @Query("SELECT * FROM salary_cycles ORDER BY startDateEpochDay ASC, id ASC")
    suspend fun getAllOnce(): List<SalaryCycleEntity>

    @Query("SELECT * FROM salary_cycles WHERE id = :id")
    suspend fun getById(id: Long): SalaryCycleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(cycle: SalaryCycleEntity): Long

    @Update
    suspend fun updateAll(cycles: List<SalaryCycleEntity>)

    @Query("DELETE FROM salary_cycles WHERE id = :id")
    suspend fun deleteById(id: Long)
}
