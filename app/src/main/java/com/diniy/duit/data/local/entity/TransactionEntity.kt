package com.diniy.duit.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.diniy.duit.domain.model.FinanceTransaction
import com.diniy.duit.domain.model.TransactionType

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = WalletEntity::class,
            parentColumns = ["id"],
            childColumns = ["walletId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = WalletEntity::class,
            parentColumns = ["id"],
            childColumns = ["targetWalletId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["walletId"]),
        Index(value = ["targetWalletId"]),
        Index(value = ["occurredAtEpochMs"]),
        Index(value = ["commitmentId"]),
        Index(value = ["submissionId"], unique = true)
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val walletId: Long,
    val targetWalletId: Long?,
    val type: TransactionType,
    val amountSen: Long,
    val category: String,
    val title: String,
    val note: String,
    val commitmentId: Long?,
    val occurredAtEpochMs: Long,
    val createdAtEpochMs: Long,
    val categoryId: Long? = null,
    val submissionId: String? = null
) {
    fun toDomain(): FinanceTransaction = FinanceTransaction(
        id = id,
        walletId = walletId,
        targetWalletId = targetWalletId,
        type = type,
        amountSen = amountSen,
        category = category,
        title = title,
        note = note,
        commitmentId = commitmentId,
        occurredAtEpochMs = occurredAtEpochMs,
        createdAtEpochMs = createdAtEpochMs,
        categoryId = categoryId,
        submissionId = submissionId
    )
}

fun FinanceTransaction.toEntity(): TransactionEntity = TransactionEntity(
    id = id,
    walletId = walletId,
    targetWalletId = targetWalletId,
    type = type,
    amountSen = amountSen,
    category = category,
    title = title,
    note = note,
    commitmentId = commitmentId,
    occurredAtEpochMs = occurredAtEpochMs,
    createdAtEpochMs = createdAtEpochMs,
    categoryId = categoryId,
    submissionId = submissionId
)
