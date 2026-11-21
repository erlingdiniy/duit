package com.diniy.duit.data.repository

import com.diniy.duit.data.local.dao.MilestoneDao
import com.diniy.duit.data.local.entity.EarnedMilestoneEntity
import com.diniy.duit.data.local.entity.toDomain
import com.diniy.duit.domain.wins.PersistedMilestone
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

interface MilestoneRepository {
    fun observeEarnedMilestones(): Flow<List<PersistedMilestone>>
    /** Persists newly earned milestones without duplicating existing ones. */
    suspend fun recordEarnedMilestones(keys: Set<String>, earnedAtEpochMs: Long = System.currentTimeMillis())
    suspend fun markCelebrated(keys: List<String>, celebratedAtEpochMs: Long = System.currentTimeMillis())
}

class OfflineFirstMilestoneRepository(
    private val milestoneDao: MilestoneDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : MilestoneRepository {

    override fun observeEarnedMilestones(): Flow<List<PersistedMilestone>> =
        milestoneDao.observeEarned().map { list -> list.map { it.toDomain() } }

    override suspend fun recordEarnedMilestones(keys: Set<String>, earnedAtEpochMs: Long) = withContext(ioDispatcher) {
        if (keys.isEmpty()) return@withContext
        val entities = keys.map { key ->
            EarnedMilestoneEntity(milestoneKey = key, earnedAtEpochMs = earnedAtEpochMs, celebratedAtEpochMs = null)
        }
        milestoneDao.insertIgnore(entities)
        Unit
    }

    override suspend fun markCelebrated(keys: List<String>, celebratedAtEpochMs: Long) = withContext(ioDispatcher) {
        if (keys.isEmpty()) return@withContext
        milestoneDao.markCelebrated(keys, celebratedAtEpochMs)
    }
}
