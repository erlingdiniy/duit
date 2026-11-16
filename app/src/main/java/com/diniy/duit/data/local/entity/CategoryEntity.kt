package com.diniy.duit.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.diniy.duit.domain.model.Category
import com.diniy.duit.domain.model.PrismaticAccentToken
import com.diniy.duit.domain.model.TransactionType

@Entity(
    tableName = "categories",
    indices = [Index(value = ["type", "isArchived"])]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val type: TransactionType,
    val iconKey: String,
    val accentToken: PrismaticAccentToken,
    val isDefault: Boolean,
    val isArchived: Boolean,
    val sortOrder: Int
) {
    fun toDomain(): Category = Category(
        id = id,
        name = name,
        type = type,
        iconKey = iconKey,
        accentToken = accentToken,
        isDefault = isDefault,
        isArchived = isArchived,
        sortOrder = sortOrder
    )
}

fun Category.toEntity(): CategoryEntity = CategoryEntity(
    id = id,
    name = name,
    type = type,
    iconKey = iconKey,
    accentToken = accentToken,
    isDefault = isDefault,
    isArchived = isArchived,
    sortOrder = sortOrder
)
