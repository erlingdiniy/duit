package com.diniy.duit.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.diniy.duit.data.local.entity.TaxReliefEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaxReliefDao {

    @Query("SELECT * FROM tax_relief_entries ORDER BY assessmentYear DESC, dateEpochDay DESC, id DESC")
    fun observeAll(): Flow<List<TaxReliefEntryEntity>>

    @Query("SELECT * FROM tax_relief_entries WHERE assessmentYear = :assessmentYear ORDER BY dateEpochDay DESC, id DESC")
    fun observeByYear(assessmentYear: Int): Flow<List<TaxReliefEntryEntity>>

    @Upsert
    suspend fun upsert(entity: TaxReliefEntryEntity): Long

    @Query("UPDATE tax_relief_entries SET reviewStatus = :status, updatedAtEpochMs = :updatedAtEpochMs WHERE id = :id")
    suspend fun updateReviewStatus(id: Long, status: String, updatedAtEpochMs: Long)

    @Query("DELETE FROM tax_relief_entries WHERE id = :id")
    suspend fun deleteById(id: Long)
}
