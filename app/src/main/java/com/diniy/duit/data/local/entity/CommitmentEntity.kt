package com.diniy.duit.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.diniy.duit.domain.model.Commitment
import com.diniy.duit.domain.model.CommitmentFrequency
import com.diniy.duit.domain.model.CommitmentStatus

@Entity(
    tableName = "commitments",
    indices = [
        Index(value = ["status"]),
        Index(value = ["dueDateEpochDay"]),
        Index(value = ["salaryCycleId"])
    ]
)
data class CommitmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val amountSen: Long,
    val dueDateEpochDay: Long,
    val status: CommitmentStatus,
    val walletId: Long?,
    val isRecurring: Boolean,
    val salaryCycleId: Long?,
    @ColumnInfo(defaultValue = "'Bills & Utilities'")
    val category: String = "Bills & Utilities",
    @ColumnInfo(defaultValue = "NULL")
    val categoryId: Long? = null,
    @ColumnInfo(defaultValue = "'MONTHLY'")
    val frequency: CommitmentFrequency = CommitmentFrequency.MONTHLY,
    @ColumnInfo(defaultValue = "NULL")
    val customIntervalDays: Int? = null,
    @ColumnInfo(defaultValue = "NULL")
    val anchorDayOfMonth: Int? = null,
    @ColumnInfo(defaultValue = "0")
    val paidSen: Long = 0L,
    @ColumnInfo(defaultValue = "NULL")
    val nextDueDateEpochDay: Long? = null,
    @ColumnInfo(defaultValue = "''")
    val note: String = "",
    @ColumnInfo(defaultValue = "0")
    val isArchived: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val createdAtEpochMs: Long = 0L,
    @ColumnInfo(defaultValue = "0")
    val updatedAtEpochMs: Long = 0L
) {
    fun toDomain(): Commitment = Commitment(
        id = id,
        title = title,
        amountSen = amountSen,
        dueDateEpochDay = dueDateEpochDay,
        status = status,
        walletId = walletId,
        isRecurring = isRecurring,
        salaryCycleId = salaryCycleId,
        category = category,
        categoryId = categoryId,
        frequency = frequency,
        customIntervalDays = customIntervalDays,
        anchorDayOfMonth = anchorDayOfMonth,
        paidSen = paidSen,
        nextDueDateEpochDay = nextDueDateEpochDay,
        note = note,
        isArchived = isArchived,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs
    )
}

fun Commitment.toEntity(): CommitmentEntity = CommitmentEntity(
    id = id,
    title = title,
    amountSen = amountSen,
    dueDateEpochDay = dueDateEpochDay,
    status = status,
    walletId = walletId,
    isRecurring = isRecurring,
    salaryCycleId = salaryCycleId,
    category = category,
    categoryId = categoryId,
    frequency = frequency,
    customIntervalDays = customIntervalDays,
    anchorDayOfMonth = anchorDayOfMonth,
    paidSen = paidSen,
    nextDueDateEpochDay = nextDueDateEpochDay,
    note = note,
    isArchived = isArchived,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs
)
