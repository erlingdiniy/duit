package com.diniy.duit.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.diniy.duit.domain.model.CommissionSource
import com.diniy.duit.domain.model.PrismaticAccentToken

@Entity(
    tableName = "commission_sources",
    indices = [Index(value = ["isArchived"])]
)
data class CommissionSourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val accentToken: PrismaticAccentToken,
    val isDefault: Boolean,
    val isArchived: Boolean,
    val sortOrder: Int
) {
    fun toDomain(): CommissionSource = CommissionSource(
        id = id,
        name = name,
        accentToken = accentToken,
        isDefault = isDefault,
        isArchived = isArchived,
        sortOrder = sortOrder
    )
}

fun CommissionSource.toEntity(): CommissionSourceEntity = CommissionSourceEntity(
    id = id,
    name = name,
    accentToken = accentToken,
    isDefault = isDefault,
    isArchived = isArchived,
    sortOrder = sortOrder
)
