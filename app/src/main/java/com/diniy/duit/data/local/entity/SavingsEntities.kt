package com.diniy.duit.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.diniy.duit.domain.model.PrismaticAccentToken
import com.diniy.duit.domain.model.SavingsAllocation
import com.diniy.duit.domain.model.SavingsGoal
import com.diniy.duit.domain.model.SavingsGoalStatus
import com.diniy.duit.domain.model.SavingsMovement
import com.diniy.duit.domain.model.SavingsMovementType

@Entity(
    tableName = "savings_goals",
    indices = [Index(value = ["status"])]
)
data class SavingsGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val targetAmountSen: Long,
    val targetDateEpochDay: Long?,
    val description: String,
    val iconKey: String,
    val accentToken: PrismaticAccentToken,
    val status: SavingsGoalStatus,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
) {
    fun toDomain(): SavingsGoal = SavingsGoal(
        id = id,
        name = name,
        targetAmountSen = targetAmountSen,
        targetDateEpochDay = targetDateEpochDay,
        description = description,
        iconKey = iconKey,
        accentToken = accentToken,
        status = status,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs
    )
}

fun SavingsGoal.toEntity(): SavingsGoalEntity = SavingsGoalEntity(
    id = id,
    name = name,
    targetAmountSen = targetAmountSen,
    targetDateEpochDay = targetDateEpochDay,
    description = description,
    iconKey = iconKey,
    accentToken = accentToken,
    status = status,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs
)

@Entity(
    tableName = "savings_wallet_allocations",
    foreignKeys = [
        ForeignKey(
            entity = SavingsGoalEntity::class,
            parentColumns = ["id"],
            childColumns = ["goalId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = WalletEntity::class,
            parentColumns = ["id"],
            childColumns = ["walletId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["goalId", "walletId"], unique = true),
        Index(value = ["walletId"])
    ]
)
data class SavingsAllocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val goalId: Long,
    val walletId: Long,
    val allocatedSen: Long,
    val updatedAtEpochMs: Long
) {
    fun toDomain(): SavingsAllocation = SavingsAllocation(
        id = id,
        goalId = goalId,
        walletId = walletId,
        allocatedSen = allocatedSen,
        updatedAtEpochMs = updatedAtEpochMs
    )
}

@Entity(
    tableName = "savings_movements",
    indices = [
        Index(value = ["goalId"]),
        Index(value = ["occurredAtEpochMs"]),
        Index(value = ["submissionId"], unique = true)
    ]
)
data class SavingsMovementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val goalId: Long,
    val sourceWalletId: Long?,
    val targetWalletId: Long?,
    val relatedGoalId: Long?,
    val type: SavingsMovementType,
    val amountSen: Long,
    val linkedTransactionId: Long?,
    val note: String,
    val occurredAtEpochMs: Long,
    val submissionId: String? = null
) {
    fun toDomain(): SavingsMovement = SavingsMovement(
        id = id,
        goalId = goalId,
        sourceWalletId = sourceWalletId,
        targetWalletId = targetWalletId,
        relatedGoalId = relatedGoalId,
        type = type,
        amountSen = amountSen,
        linkedTransactionId = linkedTransactionId,
        note = note,
        occurredAtEpochMs = occurredAtEpochMs,
        submissionId = submissionId
    )
}

fun SavingsMovement.toEntity(): SavingsMovementEntity = SavingsMovementEntity(
    id = id,
    goalId = goalId,
    sourceWalletId = sourceWalletId,
    targetWalletId = targetWalletId,
    relatedGoalId = relatedGoalId,
    type = type,
    amountSen = amountSen,
    linkedTransactionId = linkedTransactionId,
    note = note,
    occurredAtEpochMs = occurredAtEpochMs,
    submissionId = submissionId
)
