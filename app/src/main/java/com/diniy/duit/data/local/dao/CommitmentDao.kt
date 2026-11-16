package com.diniy.duit.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.diniy.duit.data.local.entity.CommitmentEntity
import com.diniy.duit.domain.model.CommitmentStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface CommitmentDao {
    @Query("SELECT * FROM commitments ORDER BY isArchived ASC, dueDateEpochDay ASC, id ASC")
    fun observeAll(): Flow<List<CommitmentEntity>>

    @Query("SELECT * FROM commitments WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CommitmentEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(commitment: CommitmentEntity): Long

    @Update
    suspend fun update(commitment: CommitmentEntity): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(commitment: CommitmentEntity): Long

    @Query("UPDATE commitments SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: CommitmentStatus)

    @Query("UPDATE commitments SET isArchived = :archived, updatedAtEpochMs = :updatedAtEpochMs WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean, updatedAtEpochMs: Long)

    @Query("DELETE FROM commitments WHERE id = :id")
    suspend fun deleteById(id: Long)
}
