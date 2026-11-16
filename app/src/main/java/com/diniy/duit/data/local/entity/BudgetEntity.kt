package com.diniy.duit.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.diniy.duit.domain.model.Budget
import com.diniy.duit.domain.model.BudgetPeriodType
import com.diniy.duit.domain.model.BudgetScope
import com.diniy.duit.domain.model.PrismaticAccentToken

@Entity(
    tableName = "budgets",
    foreignKeys = [
        ForeignKey(
            entity = SalaryCycleEntity::class,
            parentColumns = ["id"],
            childColumns = ["salaryCycleId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["periodType", "yearMonth", "salaryCycleId", "isArchived"]),
        Index(value = ["scope", "categoryId"]),
        Index(value = ["salaryCycleId"]),
        Index(value = ["categoryId"])
    ]
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val scope: BudgetScope,
    val periodType: BudgetPeriodType,
    val yearMonth: String,
    val salaryCycleId: Long?,
    val categoryId: Long?,
    val categoryName: String,
    val limitSen: Long,
    val accentToken: PrismaticAccentToken,
    val note: String,
    val isArchived: Boolean,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
) {
    fun toDomain(): Budget = Budget(
        id = id,
        name = name,
        scope = scope,
        periodType = periodType,
        yearMonth = yearMonth,
        salaryCycleId = salaryCycleId,
        categoryId = categoryId,
        categoryName = categoryName,
        limitSen = limitSen,
        accentToken = accentToken,
        note = note,
        isArchived = isArchived,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs
    )
}

fun Budget.toEntity(): BudgetEntity = BudgetEntity(
    id = id,
    name = name,
    scope = scope,
    periodType = periodType,
    yearMonth = yearMonth,
    salaryCycleId = salaryCycleId,
    categoryId = categoryId,
    categoryName = categoryName,
    limitSen = limitSen,
    accentToken = accentToken,
    note = note,
    isArchived = isArchived,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs
)
