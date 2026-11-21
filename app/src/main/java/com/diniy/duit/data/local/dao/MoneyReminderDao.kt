package com.diniy.duit.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.diniy.duit.data.local.entity.MoneyReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MoneyReminderDao {
    @Query("SELECT * FROM money_reminders ORDER BY dateEpochDay ASC, id ASC")
    fun observeAll(): Flow<List<MoneyReminderEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(reminder: MoneyReminderEntity): Long

    @Update
    suspend fun update(reminder: MoneyReminderEntity)

    @Query("DELETE FROM money_reminders WHERE id = :id")
    suspend fun deleteById(id: Long)
}
