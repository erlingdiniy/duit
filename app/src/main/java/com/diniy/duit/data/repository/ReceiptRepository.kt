package com.diniy.duit.data.repository

import com.diniy.duit.data.local.dao.ReceiptDao
import com.diniy.duit.data.local.entity.toDomain
import com.diniy.duit.data.local.entity.toEntity
import com.diniy.duit.data.receipt.ReceiptFileStorage
import com.diniy.duit.domain.receipt.ReceiptRecord
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

interface ReceiptRepository {
    fun observeReceipts(): Flow<List<ReceiptRecord>>
    fun observeReceiptsForTransaction(transactionId: Long): Flow<List<ReceiptRecord>>
    suspend fun saveReceipt(record: ReceiptRecord): Long
    suspend fun updateTransactionLink(receiptId: Long, transactionId: Long?)
    suspend fun deleteReceipt(receiptId: Long)
}

class OfflineFirstReceiptRepository(
    private val receiptDao: ReceiptDao,
    private val fileStorage: ReceiptFileStorage,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ReceiptRepository {

    override fun observeReceipts(): Flow<List<ReceiptRecord>> =
        receiptDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeReceiptsForTransaction(transactionId: Long): Flow<List<ReceiptRecord>> =
        receiptDao.observeForTransaction(transactionId).map { list -> list.map { it.toDomain() } }

    override suspend fun saveReceipt(record: ReceiptRecord): Long = withContext(ioDispatcher) {
        val now = System.currentTimeMillis()
        val existing = if (record.id > 0L) receiptDao.getById(record.id) else null
        val normalized = record.copy(
            merchant = record.merchant.trim(),
            category = record.category.trim(),
            note = record.note.trim(),
            createdAtEpochMs = existing?.createdAtEpochMs ?: record.createdAtEpochMs.takeIf { it > 0L } ?: now,
            updatedAtEpochMs = now
        )
        val savedId = if (existing == null) {
            receiptDao.insert(normalized.toEntity())
        } else {
            receiptDao.update(normalized.toEntity())
            existing.id
        }
        // Clean up replaced private file only after DB update succeeds.
        if (existing != null &&
            existing.fileRelativePath.isNotBlank() &&
            existing.fileRelativePath != normalized.fileRelativePath
        ) {
            fileStorage.deleteFile(existing.fileRelativePath)
        }
        savedId
    }

    override suspend fun updateTransactionLink(receiptId: Long, transactionId: Long?) = withContext(ioDispatcher) {
        receiptDao.updateTransactionLink(receiptId, transactionId, System.currentTimeMillis())
    }

    override suspend fun deleteReceipt(receiptId: Long) = withContext(ioDispatcher) {
        val existing = receiptDao.getById(receiptId)
        receiptDao.deleteById(receiptId)
        if (existing != null && existing.fileRelativePath.isNotBlank()) {
            fileStorage.deleteFile(existing.fileRelativePath)
        }
    }
}
