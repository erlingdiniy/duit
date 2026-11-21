package com.diniy.duit.data.repository

import com.diniy.duit.data.local.dao.TaxReliefDao
import com.diniy.duit.data.local.entity.toDomain
import com.diniy.duit.data.local.entity.toEntity
import com.diniy.duit.domain.tax.TaxReliefCatalog
import com.diniy.duit.domain.tax.TaxReliefEntry
import com.diniy.duit.domain.tax.TaxReliefReviewStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

interface TaxReliefRepository {
    fun observeAllEntries(): Flow<List<TaxReliefEntry>>
    suspend fun saveEntry(entry: TaxReliefEntry): Long
    suspend fun updateReviewStatus(id: Long, status: TaxReliefReviewStatus)
    suspend fun deleteEntry(id: Long)
}

class OfflineFirstTaxReliefRepository(
    private val taxReliefDao: TaxReliefDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : TaxReliefRepository {

    override fun observeAllEntries(): Flow<List<TaxReliefEntry>> =
        taxReliefDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun saveEntry(entry: TaxReliefEntry): Long = withContext(ioDispatcher) {
        require(entry.assessmentYear in TaxReliefCatalog.supportedYears) {
            "Unsupported assessment year: ${entry.assessmentYear}"
        }
        require(TaxReliefCatalog.categoryFor(entry.assessmentYear, entry.categoryKey) != null) {
            "Category ${entry.categoryKey} is not valid for YA ${entry.assessmentYear}"
        }
        require(entry.title.trim().isNotEmpty()) { "Please enter an item title." }
        require(entry.eligibleAmountSen > 0L) { "Eligible amount must be greater than RM 0.00." }
        if (entry.totalSpentSen != null) {
            require(entry.totalSpentSen >= entry.eligibleAmountSen) {
                "Eligible amount cannot exceed total spent."
            }
        }
        taxReliefDao.upsert(entry.toEntity())
    }

    override suspend fun updateReviewStatus(id: Long, status: TaxReliefReviewStatus) = withContext(ioDispatcher) {
        taxReliefDao.updateReviewStatus(id, status.name, System.currentTimeMillis())
    }

    override suspend fun deleteEntry(id: Long) = withContext(ioDispatcher) {
        taxReliefDao.deleteById(id)
    }
}
