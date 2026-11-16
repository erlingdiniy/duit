package com.diniy.duit.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.diniy.duit.data.local.entity.WalletEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WalletDao {
    @Query("SELECT * FROM wallets ORDER BY isArchived ASC, createdAtEpochMs ASC")
    fun observeAll(): Flow<List<WalletEntity>>

    @Query("SELECT * FROM wallets WHERE isArchived = 0 ORDER BY createdAtEpochMs ASC")
    fun observeActive(): Flow<List<WalletEntity>>

    @Query("SELECT * FROM wallets WHERE id = :id")
    suspend fun getById(id: Long): WalletEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(wallet: WalletEntity): Long

    @Update
    suspend fun update(wallet: WalletEntity)

    @Query("UPDATE wallets SET balanceSen = balanceSen + :deltaSen, updatedAtEpochMs = :updatedAtEpochMs WHERE id = :walletId")
    suspend fun adjustBalance(walletId: Long, deltaSen: Long, updatedAtEpochMs: Long)

    @Query("UPDATE wallets SET isArchived = :archived, updatedAtEpochMs = :updatedAtEpochMs WHERE id = :walletId")
    suspend fun setArchived(walletId: Long, archived: Boolean, updatedAtEpochMs: Long)

    @Query("DELETE FROM wallets WHERE id = :id")
    suspend fun deleteById(id: Long)
}
