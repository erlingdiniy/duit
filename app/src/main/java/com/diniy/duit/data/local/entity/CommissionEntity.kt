package com.diniy.duit.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.diniy.duit.domain.model.CommissionRecord
import com.diniy.duit.domain.model.CommissionStatus

@Entity(
    tableName = "commissions",
    indices = [
        Index(value = ["status"]),
        Index(value = ["earnedDateEpochDay"])
    ]
)
data class CommissionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val sourceLabel: String,
    val amountSen: Long,
    val status: CommissionStatus,
    val earnedDateEpochDay: Long,
    val payoutDateEpochDay: Long?,
    val linkedTransactionId: Long?,
    val notes: String,
    val sourceId: Long? = null,
    val yearMonth: String = "",
    val estimatedGrossSen: Long? = null,
    val actualGrossSen: Long = 0L,
    val receivedNetSen: Long = 0L,
    val adjustmentNote: String = ""
) {
    fun toDomain(): CommissionRecord = CommissionRecord(
        id = id,
        title = title,
        sourceLabel = sourceLabel,
        amountSen = amountSen,
        status = status,
        earnedDateEpochDay = earnedDateEpochDay,
        payoutDateEpochDay = payoutDateEpochDay,
        linkedTransactionId = linkedTransactionId,
        notes = notes,
        sourceId = sourceId,
        yearMonth = yearMonth,
        estimatedGrossSen = estimatedGrossSen,
        actualGrossSen = actualGrossSen,
        receivedNetSen = receivedNetSen,
        adjustmentNote = adjustmentNote
    )
}

fun CommissionRecord.toEntity(): CommissionEntity = CommissionEntity(
    id = id,
    title = title,
    sourceLabel = sourceLabel,
    amountSen = amountSen,
    status = status,
    earnedDateEpochDay = earnedDateEpochDay,
    payoutDateEpochDay = payoutDateEpochDay,
    linkedTransactionId = linkedTransactionId,
    notes = notes,
    sourceId = sourceId,
    yearMonth = yearMonth,
    estimatedGrossSen = estimatedGrossSen,
    actualGrossSen = actualGrossSen,
    receivedNetSen = receivedNetSen,
    adjustmentNote = adjustmentNote
)
