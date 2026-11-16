package com.diniy.duit.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.diniy.duit.domain.model.DebtDirection
import com.diniy.duit.domain.model.DebtRecord
import com.diniy.duit.domain.model.ObligationPayment
import com.diniy.duit.domain.model.ObligationType
import com.diniy.duit.domain.model.PayLaterPlan
import com.diniy.duit.domain.model.PrismaticAccentToken

@Entity(
    tableName = "paylater_plans",
    indices = [
        Index(value = ["isArchived", "nextDueDateEpochDay"])
    ]
)
data class PayLaterPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val providerName: String,
    val purchaseAmountSen: Long,
    val feeOrInterestSen: Long,
    val paidSen: Long,
    val instalmentCount: Int,
    val firstDueDateEpochDay: Long,
    val nextDueDateEpochDay: Long,
    val accentToken: PrismaticAccentToken,
    val note: String,
    val isArchived: Boolean,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
) {
    fun toDomain(): PayLaterPlan = PayLaterPlan(
        id = id,
        title = title,
        providerName = providerName,
        purchaseAmountSen = purchaseAmountSen,
        feeOrInterestSen = feeOrInterestSen,
        paidSen = paidSen,
        instalmentCount = instalmentCount,
        firstDueDateEpochDay = firstDueDateEpochDay,
        nextDueDateEpochDay = nextDueDateEpochDay,
        accentToken = accentToken,
        note = note,
        isArchived = isArchived,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs
    )
}

fun PayLaterPlan.toEntity(): PayLaterPlanEntity = PayLaterPlanEntity(
    id = id,
    title = title,
    providerName = providerName,
    purchaseAmountSen = purchaseAmountSen,
    feeOrInterestSen = feeOrInterestSen,
    paidSen = paidSen,
    instalmentCount = instalmentCount,
    firstDueDateEpochDay = firstDueDateEpochDay,
    nextDueDateEpochDay = nextDueDateEpochDay,
    accentToken = accentToken,
    note = note,
    isArchived = isArchived,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs
)

@Entity(
    tableName = "debts",
    indices = [
        Index(value = ["direction", "isArchived", "dueDateEpochDay"])
    ]
)
data class DebtEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val direction: DebtDirection,
    val counterpartyName: String,
    val title: String,
    val originalAmountSen: Long,
    val paidSen: Long,
    val dueDateEpochDay: Long?,
    val accentToken: PrismaticAccentToken,
    val note: String,
    val isArchived: Boolean,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    @ColumnInfo(defaultValue = "NULL")
    val scheduledRepaymentSen: Long? = null
) {
    fun toDomain(): DebtRecord = DebtRecord(
        id = id,
        direction = direction,
        counterpartyName = counterpartyName,
        title = title,
        originalAmountSen = originalAmountSen,
        paidSen = paidSen,
        dueDateEpochDay = dueDateEpochDay,
        accentToken = accentToken,
        note = note,
        isArchived = isArchived,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        scheduledRepaymentSen = scheduledRepaymentSen
    )
}

fun DebtRecord.toEntity(): DebtEntity = DebtEntity(
    id = id,
    direction = direction,
    counterpartyName = counterpartyName,
    title = title,
    originalAmountSen = originalAmountSen,
    paidSen = paidSen,
    dueDateEpochDay = dueDateEpochDay,
    accentToken = accentToken,
    note = note,
    isArchived = isArchived,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs,
    scheduledRepaymentSen = scheduledRepaymentSen
)

@Entity(
    tableName = "obligation_payments",
    indices = [
        Index(value = ["obligationType", "obligationId"]),
        Index(value = ["linkedTransactionId"]),
        Index(value = ["submissionId"], unique = true)
    ]
)
data class ObligationPaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val obligationType: ObligationType,
    val obligationId: Long,
    val walletId: Long,
    val linkedTransactionId: Long?,
    val amountSen: Long,
    val occurrenceEpochDay: Long?,
    val paidAtEpochMs: Long,
    val note: String,
    val submissionId: String? = null
) {
    fun toDomain(): ObligationPayment = ObligationPayment(
        id = id,
        obligationType = obligationType,
        obligationId = obligationId,
        walletId = walletId,
        linkedTransactionId = linkedTransactionId,
        amountSen = amountSen,
        occurrenceEpochDay = occurrenceEpochDay,
        paidAtEpochMs = paidAtEpochMs,
        note = note,
        submissionId = submissionId
    )
}
