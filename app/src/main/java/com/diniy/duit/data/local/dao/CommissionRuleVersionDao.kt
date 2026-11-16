package com.diniy.duit.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.diniy.duit.data.local.entity.CommissionRuleVersionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CommissionRuleVersionDao {
    @Query("SELECT * FROM commission_rule_versions ORDER BY effectiveFromEpochDay DESC, id DESC")
    fun observeAll(): Flow<List<CommissionRuleVersionEntity>>

    @Query("SELECT COUNT(*) FROM commission_rule_versions")
    suspend fun countAll(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: CommissionRuleVersionEntity): Long

    @Query("DELETE FROM commission_rule_versions WHERE id = :id")
    suspend fun deleteById(id: Long)
}
