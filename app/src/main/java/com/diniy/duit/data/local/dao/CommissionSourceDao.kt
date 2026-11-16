package com.diniy.duit.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.diniy.duit.data.local.entity.CommissionSourceEntity
import com.diniy.duit.domain.model.PrismaticAccentToken
import kotlinx.coroutines.flow.Flow

@Dao
interface CommissionSourceDao {
    @Query("SELECT * FROM commission_sources ORDER BY isArchived ASC, sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<CommissionSourceEntity>>

    @Query("SELECT COUNT(*) FROM commission_sources")
    suspend fun countAll(): Int

    @Query("SELECT * FROM commission_sources WHERE id = :id")
    suspend fun getById(id: Long): CommissionSourceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(source: CommissionSourceEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(sources: List<CommissionSourceEntity>)

    @Update
    suspend fun update(source: CommissionSourceEntity)

    @Query("UPDATE commission_sources SET isArchived = :archived WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)
}

object DefaultCommissionSourceSeed {
    fun defaults(): List<CommissionSourceEntity> = listOf(
        CommissionSourceEntity(0L, "Company", PrismaticAccentToken.LAVENDER, isDefault = true, isArchived = false, sortOrder = 1),
        CommissionSourceEntity(0L, "Lenovo", PrismaticAccentToken.AQUA, isDefault = true, isArchived = false, sortOrder = 2),
        CommissionSourceEntity(0L, "ASUS", PrismaticAccentToken.SKY_BLUE, isDefault = true, isArchived = false, sortOrder = 3),
        CommissionSourceEntity(0L, "Acer", PrismaticAccentToken.PEACH, isDefault = true, isArchived = false, sortOrder = 4)
    )
}
