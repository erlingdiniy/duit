package com.diniy.duit.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.diniy.duit.domain.tax.TaxReliefEntry
import com.diniy.duit.domain.tax.TaxReliefReviewStatus

/**
 * Room v14 entity storing user-logged tax relief entries for My Tax Pocket.
 * Linked optionally to Receipt Vault with ON DELETE SET NULL so deleting a receipt preserves tax history.
 */
@Entity(
    tableName = "tax_relief_entries",
    foreignKeys = [
        ForeignKey(
            entity = ReceiptEntity::class,
            parentColumns = ["id"],
            childColumns = ["receiptId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["assessmentYear"]),
        Index(value = ["assessmentYear", "categoryKey"]),
        Index(value = ["receiptId"])
    ]
)
data class TaxReliefEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val assessmentYear: Int,
    val categoryKey: String,
    val title: String,
    val dateEpochDay: Long,
    val totalSpentSen: Long?,
    val eligibleAmountSen: Long,
    val receiptId: Long?,
    val reviewStatus: String,
    val note: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)

fun TaxReliefEntryEntity.toDomain(): TaxReliefEntry = TaxReliefEntry(
    id = id,
    assessmentYear = assessmentYear,
    categoryKey = categoryKey,
    title = title,
    dateEpochDay = dateEpochDay,
    totalSpentSen = totalSpentSen,
    eligibleAmountSen = eligibleAmountSen,
    receiptId = receiptId,
    reviewStatus = TaxReliefReviewStatus.fromStored(reviewStatus),
    note = note,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs
)

fun TaxReliefEntry.toEntity(nowMs: Long = System.currentTimeMillis()): TaxReliefEntryEntity =
    TaxReliefEntryEntity(
        id = id,
        assessmentYear = assessmentYear,
        categoryKey = categoryKey,
        title = title.trim(),
        dateEpochDay = dateEpochDay,
        totalSpentSen = totalSpentSen,
        eligibleAmountSen = eligibleAmountSen,
        receiptId = receiptId,
        reviewStatus = reviewStatus.name,
        note = note.trim(),
        createdAtEpochMs = if (createdAtEpochMs > 0L) createdAtEpochMs else nowMs,
        updatedAtEpochMs = nowMs
    )
