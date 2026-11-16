package com.diniy.duit.feature.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.diniy.duit.data.repository.CommissionRepository
import com.diniy.duit.data.repository.ObligationRepository
import com.diniy.duit.data.repository.PlanningRepository
import com.diniy.duit.data.repository.SavingsRepository
import com.diniy.duit.domain.calculator.ReminderCalculator
import com.diniy.duit.domain.calculator.ReminderCandidate
import com.diniy.duit.domain.model.CommissionRecord
import com.diniy.duit.domain.model.Commitment
import com.diniy.duit.domain.model.DebtRecord
import com.diniy.duit.domain.model.PayLaterPlan
import com.diniy.duit.domain.model.SalaryCycle
import com.diniy.duit.domain.model.SavingsAllocation
import com.diniy.duit.domain.model.SavingsGoal
import com.diniy.duit.notifications.BudgetAlertThreshold
import com.diniy.duit.notifications.NotificationCategory
import com.diniy.duit.notifications.NotificationHistoryItem
import com.diniy.duit.notifications.NotificationHistoryRepository
import com.diniy.duit.notifications.NotificationPreferencesRepository
import com.diniy.duit.notifications.NotificationSettingsSnapshot
import com.diniy.duit.notifications.ReminderOffset
import com.diniy.duit.notifications.ReminderScheduler
import com.diniy.duit.notifications.SystemNotificationPoster
import com.diniy.duit.notifications.TestNotificationResult
import com.diniy.duit.security.AppLockManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class TestNotificationStatus {
    SENT,
    PERMISSION_DENIED,
    THROTTLED,
    FAILED
}

data class NotificationsUiState(
    val isLoading: Boolean = true,
    val settings: NotificationSettingsSnapshot = NotificationSettingsSnapshot(),
    val overdueItems: List<ReminderCandidate> = emptyList(),
    val upcomingReminders: List<ReminderCandidate> = emptyList(),
    val history: List<NotificationHistoryItem> = emptyList(),
    val unreadCount: Int = 0,
    val hasOsPermission: Boolean = true,
    val isAppLockOrPrivacyActive: Boolean = false,
    val testNotificationStatus: TestNotificationStatus? = null,
    val isTestNotificationCoolingDown: Boolean = false
)

class NotificationViewModel(
    planningRepository: PlanningRepository,
    obligationRepository: ObligationRepository,
    savingsRepository: SavingsRepository,
    commissionRepository: CommissionRepository,
    private val preferencesRepository: NotificationPreferencesRepository,
    private val historyRepository: NotificationHistoryRepository,
    private val reminderScheduler: ReminderScheduler,
    private val notificationPoster: SystemNotificationPoster,
    appLockManager: AppLockManager,
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    private val permissionRefreshTick = MutableStateFlow(0)
    private val testFeedbackState = MutableStateFlow<Pair<TestNotificationStatus?, Boolean>>(null to false)
    private val testSendLock = Any()
    private var lastTestNotificationAtMs: Long = Long.MIN_VALUE
    private var cooldownJob: Job? = null

    private val domainDataFlow = combine(
        planningRepository.observeCommitments(),
        obligationRepository.observePayLaterPlans(),
        obligationRepository.observeDebts(),
        savingsRepository.observeSavingsGoals(),
        savingsRepository.observeSavingsAllocations()
    ) { commitments, plans, debts, goals, allocs ->
        DomainBundle1(commitments, plans, debts, goals, allocs)
    }

    private val metaFlow = combine(
        planningRepository.observeActiveCycle(),
        commissionRepository.observeCommissions(),
        preferencesRepository.observeSettings(),
        historyRepository.observeHistory(),
        historyRepository.observeUnreadCount()
    ) { activeCycle, commissions, settings, history, unreadCount ->
        MetaBundle(activeCycle, commissions, settings, history, unreadCount)
    }

    val uiState: StateFlow<NotificationsUiState> = combine(
        domainDataFlow,
        metaFlow,
        appLockManager.uiState,
        permissionRefreshTick,
        testFeedbackState
    ) { d1, meta, securityState, _, testFeedback ->
        val evaluation = ReminderCalculator.evaluate(
            settings = meta.settings,
            commitments = d1.commitments,
            payLaterPlans = d1.plans,
            debts = d1.debts,
            savingsGoals = d1.goals,
            savingsAllocations = d1.allocs,
            activeCycle = meta.activeCycle,
            commissions = meta.commissions
        )
        val allUpcoming = (evaluation.dueNowCandidates.filter { !it.isOverdue } + evaluation.upcomingCandidates)
            .distinctBy { it.dedupKey }
            .sortedBy { it.targetEpochDay }
        val osPermissionGranted = notificationPoster.hasOsNotificationPermission()
        val effectiveTestStatus = if (osPermissionGranted && testFeedback.first == TestNotificationStatus.PERMISSION_DENIED) {
            null
        } else {
            testFeedback.first
        }

        NotificationsUiState(
            isLoading = false,
            settings = meta.settings,
            overdueItems = evaluation.overdueItems,
            upcomingReminders = allUpcoming,
            history = meta.history,
            unreadCount = meta.unreadCount,
            hasOsPermission = osPermissionGranted,
            isAppLockOrPrivacyActive = securityState.isAppLockEnabled || securityState.privacySettings.isAnyMaskActive,
            testNotificationStatus = effectiveTestStatus,
            isTestNotificationCoolingDown = testFeedback.second
        )
    }
        .flowOn(Dispatchers.Default)
        .catch {
            emit(NotificationsUiState(isLoading = false))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = NotificationsUiState(isLoading = true)
        )

    fun refreshPermissionAndSync() {
        permissionRefreshTick.value += 1
        if (notificationPoster.hasOsNotificationPermission() &&
            testFeedbackState.value.first == TestNotificationStatus.PERMISSION_DENIED
        ) {
            testFeedbackState.value = null to false
        }
        reminderScheduler.enqueueImmediateSync()
    }

    fun sendTestNotification() {
        permissionRefreshTick.value += 1
        if (!notificationPoster.hasOsNotificationPermission()) {
            testFeedbackState.value = TestNotificationStatus.PERMISSION_DENIED to false
            return
        }

        val nowMs = clockMs()
        val result = synchronized(testSendLock) {
            if (lastTestNotificationAtMs != Long.MIN_VALUE && nowMs - lastTestNotificationAtMs < TEST_COOLDOWN_MS) {
                TestNotificationResult.RATE_LIMITED
            } else {
                notificationPoster.sendTestNotification().also { posted ->
                    if (posted == TestNotificationResult.SENT) {
                        lastTestNotificationAtMs = nowMs
                    }
                }
            }
        }

        when (result) {
            TestNotificationResult.SENT -> {
                testFeedbackState.value = TestNotificationStatus.SENT to true
                cooldownJob?.cancel()
                cooldownJob = viewModelScope.launch {
                    delay(TEST_COOLDOWN_MS)
                    testFeedbackState.value = testFeedbackState.value.first to false
                }
            }
            TestNotificationResult.PERMISSION_DENIED -> {
                testFeedbackState.value = TestNotificationStatus.PERMISSION_DENIED to false
            }
            TestNotificationResult.RATE_LIMITED -> {
                testFeedbackState.value = TestNotificationStatus.THROTTLED to true
            }
            TestNotificationResult.FAILED -> {
                testFeedbackState.value = TestNotificationStatus.FAILED to false
            }
        }
    }

    fun dismissTestNotificationStatus() {
        testFeedbackState.value = null to testFeedbackState.value.second
    }

    fun setMasterEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setMasterEnabled(enabled)
            if (enabled) reminderScheduler.enqueueImmediateSync()
            else reminderScheduler.cancelAllWork()
        }
    }

    fun setCategoryEnabled(category: NotificationCategory, enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setCategoryEnabled(category, enabled)
            reminderScheduler.enqueueImmediateSync()
        }
    }

    fun setOffsetEnabled(offset: ReminderOffset, enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setOffsetEnabled(offset, enabled)
            reminderScheduler.enqueueImmediateSync()
        }
    }

    fun setBudgetThresholdEnabled(threshold: BudgetAlertThreshold, enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setBudgetThresholdEnabled(threshold, enabled)
            reminderScheduler.enqueueImmediateSync()
        }
    }

    fun setOverdueEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setOverdueEnabled(enabled)
            reminderScheduler.enqueueImmediateSync()
        }
    }

    fun setReminderTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            preferencesRepository.setReminderTime(hour, minute)
            reminderScheduler.enqueueImmediateSync()
        }
    }

    fun setHideLockScreenAmounts(hide: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setHideLockScreenAmounts(hide)
            reminderScheduler.enqueueImmediateSync()
        }
    }

    fun markAsRead(id: Long) {
        viewModelScope.launch { historyRepository.markAsRead(id) }
    }

    fun markAllAsRead() {
        viewModelScope.launch { historyRepository.markAllAsRead() }
    }

    fun clearHistory() {
        viewModelScope.launch { historyRepository.clearHistory() }
    }

    private data class DomainBundle1(
        val commitments: List<Commitment>,
        val plans: List<PayLaterPlan>,
        val debts: List<DebtRecord>,
        val goals: List<SavingsGoal>,
        val allocs: List<SavingsAllocation>
    )

    private data class MetaBundle(
        val activeCycle: SalaryCycle?,
        val commissions: List<CommissionRecord>,
        val settings: NotificationSettingsSnapshot,
        val history: List<NotificationHistoryItem>,
        val unreadCount: Int
    )

    companion object {
        const val TEST_COOLDOWN_MS = 2_000L

        fun factory(
            planningRepository: PlanningRepository,
            obligationRepository: ObligationRepository,
            savingsRepository: SavingsRepository,
            commissionRepository: CommissionRepository,
            preferencesRepository: NotificationPreferencesRepository,
            historyRepository: NotificationHistoryRepository,
            reminderScheduler: ReminderScheduler,
            notificationPoster: SystemNotificationPoster,
            appLockManager: AppLockManager
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return NotificationViewModel(
                    planningRepository = planningRepository,
                    obligationRepository = obligationRepository,
                    savingsRepository = savingsRepository,
                    commissionRepository = commissionRepository,
                    preferencesRepository = preferencesRepository,
                    historyRepository = historyRepository,
                    reminderScheduler = reminderScheduler,
                    notificationPoster = notificationPoster,
                    appLockManager = appLockManager
                ) as T
            }
        }
    }
}
