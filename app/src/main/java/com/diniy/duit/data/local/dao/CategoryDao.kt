package com.diniy.duit.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.diniy.duit.data.local.entity.CategoryEntity
import com.diniy.duit.domain.model.PrismaticAccentToken
import com.diniy.duit.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY type ASC, sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun countAll(): Int

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(category: CategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Update
    suspend fun update(category: CategoryEntity)

    @Query("UPDATE categories SET isArchived = :archived WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)
}

object DefaultCategorySeed {
    fun defaults(): List<CategoryEntity> = listOf(
        // Expense defaults
        CategoryEntity(0L, "Food & Dining", TransactionType.EXPENSE, "food", PrismaticAccentToken.PEACH, true, false, 1),
        CategoryEntity(0L, "Groceries", TransactionType.EXPENSE, "groceries", PrismaticAccentToken.AQUA, true, false, 2),
        CategoryEntity(0L, "Transport", TransactionType.EXPENSE, "transport", PrismaticAccentToken.SKY_BLUE, true, false, 3),
        CategoryEntity(0L, "Bills & Utilities", TransactionType.EXPENSE, "bills", PrismaticAccentToken.LAVENDER, true, false, 4),
        CategoryEntity(0L, "Shopping", TransactionType.EXPENSE, "shopping", PrismaticAccentToken.PEACH, true, false, 5),
        CategoryEntity(0L, "Health", TransactionType.EXPENSE, "health", PrismaticAccentToken.AQUA, true, false, 6),
        CategoryEntity(0L, "Other Expense", TransactionType.EXPENSE, "custom", PrismaticAccentToken.SLATE, true, false, 7),
        // Income defaults
        CategoryEntity(0L, "Salary", TransactionType.INCOME, "salary", PrismaticAccentToken.AQUA, true, false, 1),
        CategoryEntity(0L, "Commission", TransactionType.INCOME, "commission", PrismaticAccentToken.LAVENDER, true, false, 2),
        CategoryEntity(0L, "Freelance", TransactionType.INCOME, "freelance", PrismaticAccentToken.SKY_BLUE, true, false, 3),
        CategoryEntity(0L, "Bonus", TransactionType.INCOME, "bonus", PrismaticAccentToken.PEACH, true, false, 4),
        CategoryEntity(0L, "Refund", TransactionType.INCOME, "refund", PrismaticAccentToken.AQUA, true, false, 5),
        CategoryEntity(0L, "Other Income", TransactionType.INCOME, "custom", PrismaticAccentToken.SLATE, true, false, 6)
    )
}
