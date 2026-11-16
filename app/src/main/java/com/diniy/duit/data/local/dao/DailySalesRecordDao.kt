package com.diniy.duit.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.diniy.duit.data.local.entity.DailySalesRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailySalesRecordDao {
    @Query("SELECT * FROM daily_sales_records ORDER BY recordDateEpochDay DESC, id DESC")
    fun observeAll(): Flow<List<DailySalesRecordEntity>>

    @Query("SELECT * FROM daily_sales_records WHERE id = :id")
    suspend fun getById(id: Long): DailySalesRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: DailySalesRecordEntity): Long

    @Query("DELETE FROM daily_sales_records WHERE id = :id")
    suspend fun deleteById(id: Long)
}
