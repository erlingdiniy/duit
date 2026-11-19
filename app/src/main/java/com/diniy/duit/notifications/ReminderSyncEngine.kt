package com.diniy.duit.notifications

import com.diniy.duit.data.local.dao.NotificationHistoryDao
import com.diniy.duit.data.repository.BudgetRepository
import com.diniy.duit.data.repository.CategoryRepository
import com.diniy.duit.data.repository.CommissionRepository
import com.diniy.duit.data.repository.ObligationRepository
import com.diniy.duit.data.repository.PlanningRepository
import com.diniy.duit.data.repository.SavingsRepository
import com.diniy.duit.data.repository.TransactionRepository
import com.diniy.duit.domain.calculator.ReminderCalculator
import com.diniy.duit.domain.calculator.ReminderEvaluationResult
import com.diniy.duit.security.SecurityRepository
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId

interface ReminderSyncDelegate {
    suspend fun syncAndComputeNext(
        nowInstant: Instant = Instant.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): ReminderEvaluationResult
}

/**
 * Coordinates persisted ledger data, notification preferences, privacy lock state,
 * deduplicated Notification History persistence, and OS notification posting/cancellation.
 */
class ReminderSyncEngine(
    private val planningRepository: PlanningRepository,
    private val obligationRepository: ObligationRepository,
    private val savingsRepository: SavingsRepository,
    private val commissionRepository: CommissionRepository,
    private val notificationPreferencesRepository: NotificationPreferencesRepository,
    private val notificationHistoryRepository: NotificationHistoryRepository,
    private val notificationHistoryDao: NotificationHistoryDao,
    private val securityRepository: SecurityRepository,
    private val notificationPoster: SystemNotificationPoster,
    private val budgetRepository: BudgetRepository? = null,
    private val transactionRepository: TransactionRepository? = null,
    private val categoryRepository: CategoryRepository? = null,
    private val userPreferencesRepository: com.diniy.duit.data.repository.UserPreferencesRepository? = null
) : ReminderSyncDelegate {

    override suspend fun syncAndComputeNext(
        nowInstant: Instant,
        zoneId: ZoneId
    ): ReminderEvaluationResult {
        val settings = notificationPreferencesRepository.observeSettings().first()
        val security = securityRepository.observeSecurityConfig().first()
        val maskSensitive = security.isAppLockEnabled ||
            security.privacyModeEnabled ||
            security.hideSensitiveBalances ||
            settings.hideLockScreenAmounts

        val evaluation = ReminderCalculator.evaluate(
            settings = settings,
            commitments = planningRepository.observeCommitments().first(),
            payLaterPlans = obligationRepository.observePayLaterPlans().first(),
            debts = obligationRepository.observeDebts().first(),
            savingsGoals = savingsRepository.observeSavingsGoals().first(),
            savingsAllocations = savingsRepository.observeSavingsAllocations().first(),
            activeCycle = planningRepository.observeActiveCycle().first(),
            // Commission reminders follow the public-edition toggle; records themselves are untouched.
            commissions = if (userPreferencesRepository?.observeAppSettings()?.first()?.commissionFeatureEnabled == false) {
                emptyList()
            } else {
                commissionRepository.observeCommissions().first()
            },
            nowInstant = nowInstant,
            zoneId = zoneId,
            budgets = budgetRepository?.observeBudgets()?.first() ?: emptyList(),
            transactions = transactionRepository?.observeTransactions()?.first() ?: emptyList(),
            categories = categoryRepository?.observeCategories()?.first() ?: emptyList()
        )

        val previouslyPostedIds = notificationHistoryDao.getAllDeliveredNotificationIds().toSet()
        notificationPoster.cancelStaleNotifications(
            previouslyPostedIds = previouslyPostedIds,
            stillActiveIds = evaluation.activeNotificationIds
        )

        if (settings.masterEnabled) {
            val nowMs = nowInstant.toEpochMilli()
            for (candidate in evaluation.dueNowCandidates) {
                val title = candidate.resolveTitle(maskSensitive)
                val body = candidate.resolveBody(maskSensitive)
                val inserted = notificationHistoryRepository.recordDeliveredIfAbsent(
                    dedupKey = candidate.dedupKey,
                    notificationId = candidate.notificationId,
                    category = candidate.category,
                    entityId = candidate.entityId,
                    targetEpochDay = candidate.targetEpochDay,
                    title = title,
                    message = body,
                    amountSen = if (maskSensitive) null else candidate.amountSen,
                    isOverdue = candidate.isOverdue,
                    deepLink = candidate.deepLink,
                    deliveredAtEpochMs = nowMs
                )
                if (inserted) {
                    notificationPoster.postReminder(
                        candidate = candidate,
                        maskSensitiveContent = maskSensitive
                    )
                }
            }
        }

        return evaluation
    }
}
