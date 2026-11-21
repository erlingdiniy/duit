package com.diniy.duit.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.diniy.duit.domain.receipt.ReceiptRecord
import com.diniy.duit.domain.receipt.ReceiptTaxTag

/**
 * Private receipt vault record. Linked receipts use ON DELETE SET NULL so deleting a
 * transaction never silently destroys a saved receipt; it safely becomes standalone.
 */
@Entity(
    tableName = "receipts",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["transactionId"]),
        Index(value = ["dateEpochDay"]),
        Index(value = ["taxTag"])
    ]
)
data class ReceiptEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val transactionId: Long? = null,
    val merchant: String,
    val dateEpochDay: Long,
    val amountSen: Long? = null,
    val category: String = "",
    val note: String = "",
    val taxTag: String = ReceiptTaxTag.NONE.name,
    val fileRelativePath: String = "",
    val mimeType: String = "",
    val originalFileName: String = "",
    val fileSizeBytes: Long = 0L,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)

fun ReceiptEntity.toDomain(): ReceiptRecord = ReceiptRecord(
    id = id,
    transactionId = transactionId,
    merchant = merchant,
    dateEpochDay = dateEpochDay,
    amountSen = amountSen,
    category = category,
    note = note,
    taxTag = ReceiptTaxTag.fromStored(taxTag),
    fileRelativePath = fileRelativePath,
    mimeType = mimeType,
    originalFileName = originalFileName,
    fileSizeBytes = fileSizeBytes,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs
)

fun ReceiptRecord.toEntity(): ReceiptEntity = ReceiptEntity(
    id = id,
    transactionId = transactionId,
    merchant = merchant.trim(),
    dateEpochDay = dateEpochDay,
    amountSen = amountSen,
    category = category.trim(),
    note = note.trim(),
    taxTag = taxTag.name,
    fileRelativePath = fileRelativePath,
    mimeType = mimeType,
    originalFileName = originalFileName,
    fileSizeBytes = fileSizeBytes,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs
)
