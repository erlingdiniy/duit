package com.diniy.duit.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.diniy.duit.domain.model.CommissionPayment

@Entity(
    tableName = "commission_payments",
    indices = [
        Index(value = ["commissionId"]),
        Index(value = ["receivedAtEpochMs"]),
        Index(value = ["submissionId"], unique = true)
    ]
)
data class CommissionPaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val commissionId: Long,
    val sourceId: Long?,
    val sourceName: String,
    val walletId: Long,
    val linkedTransactionId: Long?,
    val receivedAtEpochMs: Long,
    val actualGrossSen: Long,
    val epfDeductionSen: Long?,
    val socsoDeductionSen: Long?,
    val eisDeductionSen: Long?,
    val actualNetSen: Long,
    val notes: String,
    val submissionId: String? = null
) {
    fun toDomain(): CommissionPayment = CommissionPayment(
        id = id,
        commissionId = commissionId,
        sourceId = sourceId,
        sourceName = sourceName,
        walletId = walletId,
        linkedTransactionId = linkedTransactionId,
        receivedAtEpochMs = receivedAtEpochMs,
        actualGrossSen = actualGrossSen,
        epfDeductionSen = epfDeductionSen,
        socsoDeductionSen = socsoDeductionSen,
        eisDeductionSen = eisDeductionSen,
        actualNetSen = actualNetSen,
        notes = notes,
        submissionId = submissionId
    )
}

fun CommissionPayment.toEntity(): CommissionPaymentEntity = CommissionPaymentEntity(
    id = id,
    commissionId = commissionId,
    sourceId = sourceId,
    sourceName = sourceName,
    walletId = walletId,
    linkedTransactionId = linkedTransactionId,
    receivedAtEpochMs = receivedAtEpochMs,
    actualGrossSen = actualGrossSen,
    epfDeductionSen = epfDeductionSen,
    socsoDeductionSen = socsoDeductionSen,
    eisDeductionSen = eisDeductionSen,
    actualNetSen = actualNetSen,
    notes = notes,
    submissionId = submissionId
)
