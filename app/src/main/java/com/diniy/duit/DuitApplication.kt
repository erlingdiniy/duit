package com.diniy.duit

import android.app.Application
import android.content.res.Configuration
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.room.InvalidationTracker
import com.diniy.duit.backup.BackupRestoreCoordinator
import com.diniy.duit.core.ui.state.PreviewModeController
import com.diniy.duit.data.local.DuitDatabase
import com.diniy.duit.data.preferences.UserPreferencesDataStore
import com.diniy.duit.data.repository.BudgetRepository
import com.diniy.duit.data.repository.CategoryRepository
import com.diniy.duit.data.repository.CommissionRepository
import com.diniy.duit.data.repository.OfflineFirstFinanceRepository
import com.diniy.duit.data.repository.OfflineUserPreferencesRepository
import com.diniy.duit.data.repository.ObligationRepository
import com.diniy.duit.data.repository.PlanningRepository
import com.diniy.duit.data.repository.SavingsRepository
import com.diniy.duit.data.repository.TransactionRepository
import com.diniy.duit.data.repository.UserPreferencesRepository
import com.diniy.duit.data.repository.WalletRepository
import com.diniy.duit.notifications.AndroidNotificationHelper
import com.diniy.duit.notifications.DataStoreNotificationPreferencesRepository
import com.diniy.duit.notifications.NotificationDeepLink
import com.diniy.duit.notifications.NotificationHistoryRepository
import com.diniy.duit.notifications.NotificationPreferencesRepository
import com.diniy.duit.notifications.ReminderScheduler
import com.diniy.duit.notifications.ReminderSyncEngine
import com.diniy.duit.notifications.RoomNotificationHistoryRepository
import com.diniy.duit.notifications.SystemNotificationPoster
import com.diniy.duit.notifications.WorkManagerReminderScheduler
import com.diniy.duit.security.AndroidKeyStoreSecretProtector
import com.diniy.duit.security.AndroidXBiometricAuthGateway
import com.diniy.duit.security.AppLockManager
import com.diniy.duit.security.BiometricAuthGateway
import com.diniy.duit.security.DataStoreSecurityRepository
import com.diniy.duit.security.PinHashEngine
import com.diniy.duit.security.SecurityRepository
import com.diniy.duit.widgets.GlanceWidgetUpdateCoordinator
import com.diniy.duit.widgets.WidgetDataProvider
import com.diniy.duit.widgets.WidgetDeepLink
import com.diniy.duit.widgets.WidgetUpdateCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lightweight offline-first dependency container for duit.
 */
class DuitContainer(application: Application) {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val database: DuitDatabase by lazy { DuitDatabase.getInstance(application) }
    private val preferencesDataStore: UserPreferencesDataStore by lazy {
        UserPreferencesDataStore(application)
    }
    private val financeRepository: OfflineFirstFinanceRepository by lazy {
        OfflineFirstFinanceRepository(database)
    }

    val walletRepository: WalletRepository get() = financeRepository
    val transactionRepository: TransactionRepository get() = financeRepository
    val categoryRepository: CategoryRepository get() = financeRepository
    val planningRepository: PlanningRepository get() = financeRepository
    val commissionRepository: CommissionRepository get() = financeRepository
    val savingsRepository: SavingsRepository get() = financeRepository
    val budgetRepository: BudgetRepository get() = financeRepository
    val obligationRepository: ObligationRepository get() = financeRepository
    val userPreferencesRepository: UserPreferencesRepository by lazy {
        OfflineUserPreferencesRepository(preferencesDataStore)
    }
    val previewModeController: PreviewModeController by lazy { PreviewModeController() }

    val securityRepository: SecurityRepository by lazy { DataStoreSecurityRepository(application) }
    val biometricGateway: BiometricAuthGateway by lazy { AndroidXBiometricAuthGateway(application) }
    val pinHashEngine: PinHashEngine by lazy { PinHashEngine(AndroidKeyStoreSecretProtector()) }
    val appLockManager: AppLockManager by lazy {
        AppLockManager(
            securityRepository = securityRepository,
            pinHashEngine = pinHashEngine,
            biometricGateway = biometricGateway,
            scope = applicationScope,
            onFactoryResetFinancialData = {
                withContext(Dispatchers.IO) {
                    database.clearAllTables()
                }
            }
        )
    }

    val notificationPreferencesRepository: NotificationPreferencesRepository by lazy {
        DataStoreNotificationPreferencesRepository(application)
    }
    val notificationHistoryRepository: NotificationHistoryRepository by lazy {
        RoomNotificationHistoryRepository(database.notificationHistoryDao())
    }
    private val androidNotificationHelper: AndroidNotificationHelper by lazy {
        AndroidNotificationHelper(application)
    }
    val notificationPoster: SystemNotificationPoster get() = androidNotificationHelper
    val reminderScheduler: ReminderScheduler by lazy {
        WorkManagerReminderScheduler(application)
    }
    val reminderSyncEngine: ReminderSyncEngine by lazy {
        ReminderSyncEngine(
            planningRepository = planningRepository,
            obligationRepository = obligationRepository,
            savingsRepository = savingsRepository,
            commissionRepository = commissionRepository,
            notificationPreferencesRepository = notificationPreferencesRepository,
            notificationHistoryRepository = notificationHistoryRepository,
            notificationHistoryDao = database.notificationHistoryDao(),
            securityRepository = securityRepository,
            notificationPoster = notificationPoster,
            budgetRepository = budgetRepository,
            transactionRepository = transactionRepository,
            categoryRepository = categoryRepository
        )
    }

    private val _pendingNotificationDeepLink = MutableStateFlow<NotificationDeepLink?>(null)
    val pendingNotificationDeepLink: StateFlow<NotificationDeepLink?> = _pendingNotificationDeepLink.asStateFlow()

    fun setPendingDeepLink(target: NotificationDeepLink?) {
        _pendingNotificationDeepLink.value = target
    }

    val widgetDataProvider: WidgetDataProvider by lazy {
        WidgetDataProvider(
            walletRepository = walletRepository,
            transactionRepository = transactionRepository,
            planningRepository = planningRepository,
            commissionRepository = commissionRepository,
            savingsRepository = savingsRepository,
            obligationRepository = obligationRepository,
            securityRepository = securityRepository,
            userPreferencesRepository = userPreferencesRepository
        )
    }
    val widgetUpdateCoordinator: WidgetUpdateCoordinator by lazy {
        GlanceWidgetUpdateCoordinator(application)
    }
    val backupRestoreCoordinator: BackupRestoreCoordinator by lazy {
        BackupRestoreCoordinator(
            backupDao = database.backupDao(),
            userPreferencesRepository = userPreferencesRepository,
            notificationPreferencesRepository = notificationPreferencesRepository,
            notificationHistoryRepository = notificationHistoryRepository,
            securityRepository = securityRepository,
            reminderScheduler = reminderScheduler,
            widgetUpdateCoordinator = widgetUpdateCoordinator
        )
    }

    private val _pendingWidgetDeepLink = MutableStateFlow<WidgetDeepLink?>(null)
    val pendingWidgetDeepLink: StateFlow<WidgetDeepLink?> = _pendingWidgetDeepLink.asStateFlow()

    fun setPendingWidgetDeepLink(target: WidgetDeepLink?) {
        _pendingWidgetDeepLink.value = target
    }

    fun initializeNotificationsAndObservers() {
        androidNotificationHelper.ensureChannelsCreated()
        val reminderTables = setOf(
            "commitments",
            "paylater_plans",
            "debts",
            "savings_goals",
            "savings_wallet_allocations",
            "salary_cycles",
            "commissions",
            "transactions",
            "budgets"
        )
        database.invalidationTracker.addObserver(
            object : InvalidationTracker.Observer(
                arrayOf(
                    "wallets",
                    "commitments",
                    "paylater_plans",
                    "debts",
                    "savings_goals",
                    "savings_wallet_allocations",
                    "salary_cycles",
                    "commissions",
                    "transactions",
                    "budgets",
                    "categories"
                )
            ) {
                override fun onInvalidated(tables: Set<String>) {
                    if (tables.any { it in reminderTables }) {
                        reminderScheduler.enqueueImmediateSync()
                    }
                    widgetUpdateCoordinator.enqueueRefresh()
                }
            }
        )
        reminderScheduler.enqueueImmediateSync()
        widgetUpdateCoordinator.enqueueRefresh()

        applicationScope.launch {
            securityRepository.observeSecurityConfig()
                .distinctUntilChangedBy {
                    listOf(
                        it.isAppLockEnabled,
                        it.hideSensitiveBalances,
                        it.privacyModeEnabled,
                        it.alwaysMaskWidgetAmounts
                    )
                }
                .drop(1)
                .collectLatest {
                    widgetUpdateCoordinator.refreshAllWidgetsNow()
                }
        }
        applicationScope.launch {
            userPreferencesRepository.observeThemeMode()
                .distinctUntilChanged()
                .drop(1)
                .collectLatest {
                    widgetUpdateCoordinator.refreshAllWidgetsNow()
                }
        }
    }
}

class DuitApplication : Application() {
    lateinit var container: DuitContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DuitContainer(this)
        container.initializeNotificationsAndObservers()
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    container.appLockManager.onAppForegrounded()
                }

                override fun onStop(owner: LifecycleOwner) {
                    container.appLockManager.onAppBackgrounded()
                }
            }
        )
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::container.isInitialized) {
            container.widgetUpdateCoordinator.enqueueRefresh()
        }
    }
}
