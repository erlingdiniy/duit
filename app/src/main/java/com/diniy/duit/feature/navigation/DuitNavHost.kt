package com.diniy.duit.feature.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.diniy.duit.DuitContainer
import com.diniy.duit.R
import com.diniy.duit.core.ui.components.PrismaticBackground
import com.diniy.duit.core.ui.components.PrismaticFloatingBottomBar
import com.diniy.duit.core.ui.components.PrismaticLoadingCard
import com.diniy.duit.core.ui.components.PrismaticNavItem
import com.diniy.duit.core.ui.components.PrismaticNavigationRail
import com.diniy.duit.core.ui.components.rememberReducedMotionEnabled
import com.diniy.duit.domain.model.FinanceTransaction
import com.diniy.duit.domain.model.TransactionType
import com.diniy.duit.feature.activity.ActivityScreen
import com.diniy.duit.feature.activity.ActivityViewModel
import com.diniy.duit.feature.budgets.PlanningSectionTab
import com.diniy.duit.feature.home.HomeScreen
import com.diniy.duit.feature.home.HomeViewModel
import com.diniy.duit.feature.insights.InsightsScreen
import com.diniy.duit.feature.insights.InsightsViewModel
import com.diniy.duit.feature.notifications.NotificationCentreScreen
import com.diniy.duit.feature.notifications.NotificationSettingsScreen
import com.diniy.duit.feature.notifications.NotificationViewModel
import com.diniy.duit.feature.planning.PlanningScreen
import com.diniy.duit.feature.planning.PlanningViewModel
import com.diniy.duit.feature.security.PinFlowMode
import com.diniy.duit.feature.security.PinSetupScreen
import com.diniy.duit.feature.security.PinUnlockScreen
import com.diniy.duit.feature.security.SecuritySettingsScreen
import com.diniy.duit.feature.settings.SettingsHubContainerScreen
import com.diniy.duit.feature.settings.SettingsHubRoute
import com.diniy.duit.feature.settings.SettingsViewModel
import com.diniy.duit.feature.transactions.CategoryManagementSheet
import com.diniy.duit.feature.transactions.TransactionDetailSheet
import com.diniy.duit.feature.transactions.TransactionEditorSheet
import com.diniy.duit.feature.wallets.EditWalletSheet
import com.diniy.duit.feature.wallets.WalletDetailSheet
import com.diniy.duit.notifications.NotificationDeepLink
import com.diniy.duit.security.AppLockUiState
import com.diniy.duit.widgets.WidgetDeepLink

object DuitRoute {
    const val HOME = "home"
    const val ACTIVITY = "activity"
    const val PLANNING = "planning"
    const val INSIGHTS = "insights"
}

@Composable
fun DuitAppShell(
    container: DuitContainer,
    appLockState: AppLockUiState,
    onTriggerBiometric: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lockManager = container.appLockManager

    // Wait until security state loads from DataStore so locked screens never flash.
    if (!appLockState.isReady) {
        PrismaticBackground(modifier = modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                PrismaticLoadingCard()
            }
        }
        return
    }

    // While locked, unmount the financial NavHost completely so lock screen cannot be bypassed.
    if (appLockState.isLocked) {
        PinUnlockScreen(
            appLockState = appLockState,
            onSubmitPin = lockManager::verifyPinAndUnlock,
            onTriggerBiometric = onTriggerBiometric,
            onDismissError = lockManager::clearAuthError,
            onConfirmFactoryReset = { lockManager.resetProtectedAccessAndWipeData() }
        )
        return
    }

    val coroutineScope = rememberCoroutineScope()
    var showSettingsHub by rememberSaveable { mutableStateOf(false) }
    var settingsHubRoute by rememberSaveable { mutableStateOf(SettingsHubRoute.HUB) }
    var showSecuritySettings by rememberSaveable { mutableStateOf(false) }
    var showNotificationCentre by rememberSaveable { mutableStateOf(false) }
    var showNotificationSettings by rememberSaveable { mutableStateOf(false) }
    var activePinFlowMode by rememberSaveable { mutableStateOf<PinFlowMode?>(null) }
    var requestedPlanningTab by rememberSaveable { mutableStateOf<PlanningSectionTab?>(null) }

    val pendingDeepLink by container.pendingNotificationDeepLink.collectAsStateWithLifecycle()
    val pendingWidgetDeepLink by container.pendingWidgetDeepLink.collectAsStateWithLifecycle()

    LaunchedEffect(pendingDeepLink, pendingWidgetDeepLink) {
        if (pendingDeepLink != null || pendingWidgetDeepLink != null) {
            showSettingsHub = false
            showSecuritySettings = false
            showNotificationCentre = false
            showNotificationSettings = false
        }
    }

    val settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(
            userPreferencesRepository = container.userPreferencesRepository,
            notificationPreferencesRepository = container.notificationPreferencesRepository,
            walletRepository = container.walletRepository,
            appLockManager = container.appLockManager,
            backupRestoreCoordinator = container.backupRestoreCoordinator
        )
    )
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()

    val notificationViewModel: NotificationViewModel = viewModel(
        factory = NotificationViewModel.factory(
            planningRepository = container.planningRepository,
            obligationRepository = container.obligationRepository,
            savingsRepository = container.savingsRepository,
            commissionRepository = container.commissionRepository,
            preferencesRepository = container.notificationPreferencesRepository,
            historyRepository = container.notificationHistoryRepository,
            reminderScheduler = container.reminderScheduler,
            notificationPoster = container.notificationPoster,
            appLockManager = container.appLockManager
        )
    )
    val notificationState by notificationViewModel.uiState.collectAsStateWithLifecycle()

    if (activePinFlowMode != null) {
        PinSetupScreen(
            mode = activePinFlowMode!!,
            onVerifyCurrentPin = lockManager::verifyCurrentPin,
            onSetupNewPin = { pin ->
                lockManager.setupPinAndEnableAppLock(pin).also {
                    if (it.isSuccess) container.reminderScheduler.enqueueImmediateSync()
                }
            },
            onChangePin = lockManager::changePin,
            onDisableWithPin = { pin ->
                lockManager.disableAppLockWithPin(pin).also {
                    if (it.isSuccess) container.reminderScheduler.enqueueImmediateSync()
                }
            },
            onSuccess = { activePinFlowMode = null },
            onCancel = { activePinFlowMode = null }
        )
        return
    }

    if (showNotificationSettings) {
        NotificationSettingsScreen(
            uiState = notificationState,
            onBack = { showNotificationSettings = false },
            onSetMasterEnabled = notificationViewModel::setMasterEnabled,
            onSetCategoryEnabled = notificationViewModel::setCategoryEnabled,
            onSetOffsetEnabled = notificationViewModel::setOffsetEnabled,
            onSetBudgetThresholdEnabled = notificationViewModel::setBudgetThresholdEnabled,
            onSetOverdueEnabled = notificationViewModel::setOverdueEnabled,
            onSetReminderTime = notificationViewModel::setReminderTime,
            onSetHideLockScreenAmounts = notificationViewModel::setHideLockScreenAmounts,
            onPermissionUpdated = notificationViewModel::refreshPermissionAndSync,
            onSendTestNotification = notificationViewModel::sendTestNotification
        )
        return
    }

    if (showSecuritySettings) {
        SecuritySettingsScreen(
            appLockState = appLockState,
            onBack = { showSecuritySettings = false },
            onOpenPinFlow = { mode -> activePinFlowMode = mode },
            onToggleBiometric = lockManager::setBiometricEnabled,
            onSelectAutoLockTimeout = lockManager::setAutoLockTimeout,
            onToggleHideBalances = { hidden ->
                lockManager.setHideSensitiveBalances(hidden)
                container.reminderScheduler.enqueueImmediateSync()
            },
            onTogglePrivacyMode = { enabled ->
                lockManager.setPrivacyModeEnabled(enabled)
                container.reminderScheduler.enqueueImmediateSync()
            },
            onToggleAlwaysMaskWidgetAmounts = { alwaysMask ->
                lockManager.setAlwaysMaskWidgetAmounts(alwaysMask)
            },
            onLockNow = {
                showSecuritySettings = false
                showSettingsHub = false
                lockManager.lockNow()
            },
            onOpenNotificationsSettings = { showNotificationSettings = true }
        )
        return
    }

    val navController = rememberNavController()

    val navigateToRoute: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    var pendingOpenCategoriesFromSettings by rememberSaveable { mutableStateOf(false) }

    if (showSettingsHub) {
        SettingsHubContainerScreen(
            uiState = settingsState,
            route = settingsHubRoute,
            onNavigateRoute = { settingsHubRoute = it },
            onBackToApp = {
                settingsHubRoute = SettingsHubRoute.HUB
                showSettingsHub = false
            },
            onOpenNotifications = { showNotificationSettings = true },
            onOpenSecurity = { showSecuritySettings = true },
            onOpenSalaryCycles = {
                showSettingsHub = false
                requestedPlanningTab = PlanningSectionTab.GOALS
                navigateToRoute(DuitRoute.PLANNING)
            },
            onOpenCategories = {
                showSettingsHub = false
                pendingOpenCategoriesFromSettings = true
                navigateToRoute(DuitRoute.ACTIVITY)
            },
            onVerifyPin = lockManager::verifyCurrentPin,
            viewModel = settingsViewModel
        )
        return
    }

    fun resolvePlanningTab(link: NotificationDeepLink): PlanningSectionTab = when (link) {
        NotificationDeepLink.PLANNING_COMMITMENTS,
        NotificationDeepLink.PLANNING_PAYLATER,
        NotificationDeepLink.PLANNING_DEBTS -> PlanningSectionTab.COMMITMENTS
        NotificationDeepLink.PLANNING_BUDGETS -> PlanningSectionTab.BUDGETS
        NotificationDeepLink.PLANNING_GOALS,
        NotificationDeepLink.PLANNING_SALARY,
        NotificationDeepLink.PLANNING_COMMISSIONS -> PlanningSectionTab.GOALS
    }

    if (showNotificationCentre) {
        NotificationCentreScreen(
            uiState = notificationState,
            onBack = { showNotificationCentre = false },
            onOpenSettings = { showNotificationSettings = true },
            onMarkAsRead = notificationViewModel::markAsRead,
            onMarkAllAsRead = notificationViewModel::markAllAsRead,
            onClearHistory = notificationViewModel::clearHistory,
            onNavigateToTarget = { target ->
                showNotificationCentre = false
                container.setPendingDeepLink(target)
            },
            onPermissionUpdated = notificationViewModel::refreshPermissionAndSync
        )
        return
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: DuitRoute.HOME

    // Consume OS or in-app notification deep links strictly after passing App Lock and once NavHost graph is attached
    LaunchedEffect(pendingDeepLink, backStackEntry) {
        val target = pendingDeepLink ?: return@LaunchedEffect
        if (backStackEntry == null) return@LaunchedEffect
        container.setPendingDeepLink(null)
        requestedPlanningTab = resolvePlanningTab(target)
        navController.navigate(DuitRoute.PLANNING) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    val reducedMotion = rememberReducedMotionEnabled()

    // Shared transaction / category / wallet modal state
    var showTransactionEditor by rememberSaveable { mutableStateOf(false) }
    var showQuickAddSheet by rememberSaveable { mutableStateOf(false) }
    var editorInitialType by rememberSaveable { mutableStateOf(TransactionType.EXPENSE) }
    var editingTransactionId by rememberSaveable { mutableStateOf<Long?>(null) }

    var inspectedTransactionId by rememberSaveable { mutableStateOf<Long?>(null) }
    var detailDeleteError by rememberSaveable { mutableStateOf<String?>(null) }

    var showCategorySheet by rememberSaveable { mutableStateOf(false) }
    var categoryInitialType by rememberSaveable { mutableStateOf(TransactionType.EXPENSE) }

    LaunchedEffect(pendingOpenCategoriesFromSettings) {
        if (pendingOpenCategoriesFromSettings) {
            pendingOpenCategoriesFromSettings = false
            categoryInitialType = TransactionType.EXPENSE
            showCategorySheet = true
        }
    }

    var inspectedWalletId by rememberSaveable { mutableStateOf<Long?>(null) }
    var walletActionError by rememberSaveable { mutableStateOf<String?>(null) }
    var editingWalletId by rememberSaveable { mutableStateOf<Long?>(null) }

    val homeViewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.factory(
            walletRepository = container.walletRepository,
            transactionRepository = container.transactionRepository,
            planningRepository = container.planningRepository,
            commissionRepository = container.commissionRepository,
            savingsRepository = container.savingsRepository,
            obligationRepository = container.obligationRepository,
            preferencesRepository = container.userPreferencesRepository,
            previewModeController = container.previewModeController
        )
    )
    val activityViewModel: ActivityViewModel = viewModel(
        factory = ActivityViewModel.factory(
            transactionRepository = container.transactionRepository,
            walletRepository = container.walletRepository,
            categoryRepository = container.categoryRepository,
            planningRepository = container.planningRepository,
            previewModeController = container.previewModeController
        )
    )
    val planningViewModel: PlanningViewModel = viewModel(
        factory = PlanningViewModel.factory(
            planningRepository = container.planningRepository,
            commissionRepository = container.commissionRepository,
            savingsRepository = container.savingsRepository,
            budgetRepository = container.budgetRepository,
            categoryRepository = container.categoryRepository,
            obligationRepository = container.obligationRepository,
            transactionRepository = container.transactionRepository,
            walletRepository = container.walletRepository,
            previewModeController = container.previewModeController,
            userPreferencesRepository = container.userPreferencesRepository
        )
    )
    val insightsViewModel: InsightsViewModel = viewModel(
        factory = InsightsViewModel.factory(
            walletRepository = container.walletRepository,
            transactionRepository = container.transactionRepository,
            planningRepository = container.planningRepository,
            commissionRepository = container.commissionRepository,
            savingsRepository = container.savingsRepository,
            budgetRepository = container.budgetRepository,
            categoryRepository = container.categoryRepository,
            obligationRepository = container.obligationRepository,
            previewModeController = container.previewModeController
        )
    )

    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val activityState by activityViewModel.uiState.collectAsStateWithLifecycle()
    val planningState by planningViewModel.uiState.collectAsStateWithLifecycle()
    val insightsState by insightsViewModel.uiState.collectAsStateWithLifecycle()

    val navItems = listOf(
        PrismaticNavItem(
            route = DuitRoute.HOME,
            label = stringResource(R.string.nav_home),
            iconRes = R.drawable.ic_solar_home,
            contentDescription = "Home dashboard"
        ),
        PrismaticNavItem(
            route = DuitRoute.ACTIVITY,
            label = stringResource(R.string.nav_activity),
            iconRes = R.drawable.ic_solar_activity,
            contentDescription = "Activity ledger"
        ),
        PrismaticNavItem(
            route = DuitRoute.PLANNING,
            label = stringResource(R.string.nav_planning),
            iconRes = R.drawable.ic_solar_planning,
            contentDescription = "Planning and commitments"
        ),
        PrismaticNavItem(
            route = DuitRoute.INSIGHTS,
            label = stringResource(R.string.nav_insights),
            iconRes = R.drawable.ic_solar_insights,
            contentDescription = "Financial insights"
        )
    )

    val openNewTransaction: (TransactionType) -> Unit = { type ->
        editingTransactionId = null
        editorInitialType = type
        showTransactionEditor = true
    }

    // Consume Home Screen Widget deep links strictly after App Lock authentication & NavHost graph attachment.
    LaunchedEffect(pendingWidgetDeepLink, backStackEntry) {
        val target = pendingWidgetDeepLink ?: return@LaunchedEffect
        if (backStackEntry == null) return@LaunchedEffect
        container.setPendingWidgetDeepLink(null)
        when (target) {
            WidgetDeepLink.HOME -> navigateToRoute(DuitRoute.HOME)
            WidgetDeepLink.ACTIVITY -> navigateToRoute(DuitRoute.ACTIVITY)
            WidgetDeepLink.PLANNING_COMMITMENTS -> {
                requestedPlanningTab = PlanningSectionTab.COMMITMENTS
                navigateToRoute(DuitRoute.PLANNING)
            }
            WidgetDeepLink.PLANNING_GOALS -> {
                requestedPlanningTab = PlanningSectionTab.GOALS
                navigateToRoute(DuitRoute.PLANNING)
            }
            WidgetDeepLink.INSIGHTS -> navigateToRoute(DuitRoute.INSIGHTS)
            WidgetDeepLink.QUICK_ADD_EXPENSE,
            WidgetDeepLink.QUICK_ADD_INCOME,
            WidgetDeepLink.QUICK_ADD_TRANSFER -> {
                navigateToRoute(DuitRoute.HOME)
                target.toQuickAddTransactionType()?.let(openNewTransaction)
            }
        }
    }

    val openTransactionDetail: (FinanceTransaction) -> Unit = { tx ->
        detailDeleteError = null
        inspectedTransactionId = tx.id
    }

    PrismaticBackground(
        modifier = modifier.semantics { testTagsAsResourceId = true }
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavigationRail = maxWidth >= 600.dp
            val layoutDirection = LocalLayoutDirection.current

            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets.safeDrawing,
                bottomBar = {
                    if (!useNavigationRail) {
                        PrismaticFloatingBottomBar(
                            items = navItems,
                            currentRoute = currentRoute,
                            onSelect = { navigateToRoute(it.route) },
                            onQuickAddClick = { showQuickAddSheet = true }
                        )
                    }
                }
            ) { innerPadding ->
                val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
                val baseBottom = innerPadding.calculateBottomPadding() + 20.dp
                val screenPadding = PaddingValues(
                    start = if (useNavigationRail) 12.dp else innerPadding.calculateStartPadding(layoutDirection) + 16.dp,
                    end = innerPadding.calculateEndPadding(layoutDirection) + 16.dp,
                    top = innerPadding.calculateTopPadding() + 12.dp,
                    bottom = maxOf(baseBottom, imeBottom + 16.dp)
                )

                Row(modifier = Modifier.fillMaxSize()) {
                    if (useNavigationRail) {
                        PrismaticNavigationRail(
                            items = navItems,
                            currentRoute = currentRoute,
                            onSelect = { navigateToRoute(it.route) },
                            onQuickAddClick = { showQuickAddSheet = true }
                        )
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                        NavHost(
                            navController = navController,
                            startDestination = DuitRoute.HOME,
                            enterTransition = {
                                if (reducedMotion) EnterTransition.None
                                else fadeIn(animationSpec = tween(200))
                            },
                            exitTransition = {
                                if (reducedMotion) ExitTransition.None
                                else fadeOut(animationSpec = tween(160))
                            },
                            popEnterTransition = {
                                if (reducedMotion) EnterTransition.None
                                else fadeIn(animationSpec = tween(200))
                            },
                            popExitTransition = {
                                if (reducedMotion) ExitTransition.None
                                else fadeOut(animationSpec = tween(160))
                            },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            composable(DuitRoute.HOME) {
                                HomeScreen(
                                    uiState = homeState,
                                    contentPadding = screenPadding,
                                    onSelectTheme = homeViewModel::setThemeMode,
                                    onToggleSamplePreview = homeViewModel::setSamplePreview,
                                    onAddWallet = homeViewModel::addWallet,
                                    onSelectWallet = { wallet ->
                                        walletActionError = null
                                        inspectedWalletId = wallet.id
                                    },
                                    onOpenTransactionEditor = openNewTransaction,
                                    onDismissError = homeViewModel::dismissError,
                                    onOpenCommissionCalculator = {
                                        requestedPlanningTab = PlanningSectionTab.COMMITMENTS
                                        navigateToRoute(DuitRoute.PLANNING)
                                    },
                                    onOpenSavingsGoals = {
                                        requestedPlanningTab = PlanningSectionTab.GOALS
                                        navigateToRoute(DuitRoute.PLANNING)
                                    },
                                    onOpenCommitments = {
                                        requestedPlanningTab = PlanningSectionTab.COMMITMENTS
                                        navigateToRoute(DuitRoute.PLANNING)
                                    },
                                    onOpenActivity = { navigateToRoute(DuitRoute.ACTIVITY) },
                                    onSelectTransaction = openTransactionDetail,
                                    onOpenSecuritySettings = { showSecuritySettings = true },
                                    onOpenSettingsHub = {
                                        settingsHubRoute = SettingsHubRoute.HUB
                                        settingsViewModel.refreshStorageSummary()
                                        showSettingsHub = true
                                    },
                                    onToggleBalanceVisibility = {
                                        coroutineScope.launch {
                                            lockManager.toggleBalanceVisibilityFromHome()
                                            container.reminderScheduler.enqueueImmediateSync()
                                        }
                                    },
                                    unreadNotificationCount = notificationState.unreadCount,
                                    onOpenNotifications = { showNotificationCentre = true }
                                )
                            }
                            composable(DuitRoute.ACTIVITY) {
                                ActivityScreen(
                                    uiState = activityState,
                                    contentPadding = screenPadding,
                                    onSelectFilter = activityViewModel::selectFilter,
                                    onSearchQueryChange = activityViewModel::updateSearchQuery,
                                    onOpenNewTransaction = openNewTransaction,
                                    onSelectTransaction = openTransactionDetail,
                                    onOpenCategories = {
                                        categoryInitialType = TransactionType.EXPENSE
                                        showCategorySheet = true
                                    },
                                    onEnablePreview = activityViewModel::enableSamplePreview,
                                    onExitPreview = activityViewModel::exitSamplePreview,
                                    onDismissError = activityViewModel::dismissError
                                )
                            }
                            composable(DuitRoute.PLANNING) {
                                PlanningScreen(
                                    uiState = planningState,
                                    contentPadding = screenPadding,
                                    onShiftMonth = planningViewModel::shiftMonth,
                                    onSelectPeriodMode = planningViewModel::selectPeriodMode,
                                    onSaveSalaryCycle = planningViewModel::saveSalaryCycle,
                                    onDeleteSalaryCycle = planningViewModel::deleteSalaryCycle,
                                    onSaveDailySales = planningViewModel::saveDailySalesRecord,
                                    onDeleteDailySales = planningViewModel::deleteDailySalesRecord,
                                    onSaveRuleVersion = planningViewModel::saveCommissionRuleVersion,
                                    onCreateSource = planningViewModel::createCommissionSource,
                                    onRenameSource = planningViewModel::renameCommissionSource,
                                    onToggleArchiveSource = planningViewModel::setCommissionSourceArchived,
                                    onSaveCommission = planningViewModel::saveCommissionRecord,
                                    onDeleteCommission = planningViewModel::deleteCommissionRecord,
                                    onRecordPayment = planningViewModel::recordCommissionPayment,
                                    onDeletePayment = planningViewModel::deleteCommissionPayment,
                                    onSaveSavingsGoal = planningViewModel::saveSavingsGoal,
                                    onCompleteSavingsGoal = planningViewModel::completeSavingsGoal,
                                    onArchiveSavingsGoal = planningViewModel::archiveSavingsGoal,
                                    onDeleteSavingsGoal = planningViewModel::deleteSavingsGoal,
                                    onAllocateToGoal = planningViewModel::allocateToGoal,
                                    onTransferToProtectedAndAllocate = planningViewModel::transferToProtectedWalletAndAllocate,
                                    onWithdrawFromGoal = planningViewModel::withdrawFromGoal,
                                    onTransferGoalToSpending = planningViewModel::transferFromGoalToSpendingWallet,
                                    onReassignGoalAllocation = planningViewModel::reassignGoalAllocation,
                                    onSelectBudgetPeriodType = planningViewModel::selectBudgetPeriodType,
                                    onShiftBudgetPeriod = planningViewModel::shiftBudgetPeriod,
                                    onSaveBudget = planningViewModel::saveBudget,
                                    onSetBudgetArchived = planningViewModel::setBudgetArchived,
                                    onDeleteBudget = planningViewModel::deleteBudget,
                                    onCopyPreviousMonthBudgets = planningViewModel::copyPreviousMonthBudgets,
                                    onSaveCommitmentDetail = planningViewModel::saveCommitmentDetail,
                                    onSetCommitmentArchived = planningViewModel::setCommitmentArchived,
                                    onDeleteCommitment = planningViewModel::deleteCommitment,
                                    onRecordCommitmentPayment = planningViewModel::recordCommitmentPayment,
                                    onOpenNextCommitmentOccurrence = planningViewModel::openNextRecurringOccurrence,
                                    onSavePayLaterPlan = planningViewModel::savePayLaterPlan,
                                    onSetPayLaterArchived = planningViewModel::setPayLaterArchived,
                                    onDeletePayLaterPlan = planningViewModel::deletePayLaterPlan,
                                    onRecordPayLaterPayment = planningViewModel::recordPayLaterPayment,
                                    onSaveDebt = planningViewModel::saveDebt,
                                    onSetDebtArchived = planningViewModel::setDebtArchived,
                                    onDeleteDebt = planningViewModel::deleteDebt,
                                    onRecordDebtSettlement = planningViewModel::recordDebtSettlement,
                                    onOpenCategories = {
                                        categoryInitialType = TransactionType.EXPENSE
                                        showCategorySheet = true
                                    },
                                    onEnablePreview = planningViewModel::enableSamplePreview,
                                    onExitPreview = planningViewModel::exitSamplePreview,
                                    onDismissError = planningViewModel::dismissError,
                                    requestedSectionTab = requestedPlanningTab,
                                    onConsumeRequestedSectionTab = { requestedPlanningTab = null }
                                )
                            }
                            composable(DuitRoute.INSIGHTS) {
                                InsightsScreen(
                                    uiState = insightsState,
                                    contentPadding = screenPadding,
                                    onShiftMonth = insightsViewModel::shiftMonth,
                                    onSelectPeriodPreset = insightsViewModel::selectPeriodPreset,
                                    onSetCustomDateRange = insightsViewModel::setCustomDateRange,
                                    onSelectTrendHorizon = insightsViewModel::selectTrendHorizon,
                                    onInspectCategory = insightsViewModel::openCategoryBreakdown,
                                    onDismissCategorySheet = insightsViewModel::dismissCategoryBreakdown,
                                    onEnablePreview = insightsViewModel::enableSamplePreview,
                                    onExitPreview = insightsViewModel::exitSamplePreview
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showQuickAddSheet) {
        com.diniy.duit.core.ui.components.SpatialQuickAddSheet(
            onDismiss = { showQuickAddSheet = false },
            onSelectTransactionType = openNewTransaction,
            onOpenCommissionTools = {
                requestedPlanningTab = PlanningSectionTab.COMMITMENTS
                navigateToRoute(DuitRoute.PLANNING)
            },
            onOpenCategories = {
                categoryInitialType = TransactionType.EXPENSE
                showCategorySheet = true
            }
        )
    }

    if (showTransactionEditor) {
        val existingTx = editingTransactionId?.let { id ->
            homeState.transactions.firstOrNull { it.id == id }
        }
        TransactionEditorSheet(
            wallets = homeState.wallets + homeState.archivedWallets,
            categories = activityState.categories,
            initialType = editorInitialType,
            existingTransaction = existingTx,
            defaultWalletId = homeState.appSettings.defaultWalletId,
            isSubmitting = activityState.isSubmitting,
            onManageCategories = { type ->
                categoryInitialType = type
                showCategorySheet = true
            },
            onSubmit = { transaction, startNewCycle, cycleLabel, onError ->
                activityViewModel.saveTransaction(
                    transaction = transaction,
                    startNewSalaryCycle = startNewCycle,
                    salaryCycleLabel = cycleLabel,
                    onSuccess = {
                        showTransactionEditor = false
                        editingTransactionId = null
                    },
                    onError = onError
                )
            },
            onDismiss = {
                showTransactionEditor = false
                editingTransactionId = null
            }
        )
    }

    inspectedTransactionId?.let { txId ->
        val tx = homeState.transactions.firstOrNull { it.id == txId }
        if (tx != null) {
            TransactionDetailSheet(
                transaction = tx,
                walletsById = activityState.walletsById,
                isReadOnlyPreview = activityState.isSamplePreview,
                deleteError = detailDeleteError,
                onEdit = {
                    inspectedTransactionId = null
                    editingTransactionId = tx.id
                    editorInitialType = tx.type
                    showTransactionEditor = true
                },
                onConfirmDelete = { id ->
                    activityViewModel.deleteTransaction(
                        transactionId = id,
                        onSuccess = {
                            inspectedTransactionId = null
                            detailDeleteError = null
                        },
                        onError = { err -> detailDeleteError = err }
                    )
                },
                onDismiss = {
                    inspectedTransactionId = null
                    detailDeleteError = null
                }
            )
        }
    }

    inspectedWalletId?.let { wId ->
        val allWallets = homeState.wallets + homeState.archivedWallets
        val wallet = allWallets.firstOrNull { it.id == wId }
        if (wallet != null) {
            val walletTxs = remember(homeState.transactions, wId) {
                homeState.transactions.filter { it.walletId == wId || it.targetWalletId == wId }
            }
            WalletDetailSheet(
                wallet = wallet,
                walletTransactions = walletTxs,
                walletsById = allWallets.associateBy { it.id },
                isReadOnlyPreview = homeState.isSamplePreview,
                actionError = walletActionError,
                onEditWallet = {
                    inspectedWalletId = null
                    editingWalletId = wallet.id
                },
                onToggleArchive = { archived ->
                    homeViewModel.setWalletArchived(
                        walletId = wallet.id,
                        archived = archived,
                        onSuccess = {
                            walletActionError = null
                            inspectedWalletId = null
                        },
                        onError = { err -> walletActionError = err }
                    )
                },
                onDeleteWallet = {
                    homeViewModel.deleteWalletSafely(
                        walletId = wallet.id,
                        onSuccess = {
                            walletActionError = null
                            inspectedWalletId = null
                        },
                        onError = { err -> walletActionError = err }
                    )
                },
                onSelectTransaction = { tx ->
                    inspectedWalletId = null
                    openTransactionDetail(tx)
                },
                onDismiss = {
                    inspectedWalletId = null
                    walletActionError = null
                }
            )
        }
    }

    editingWalletId?.let { wId ->
        val allWallets = homeState.wallets + homeState.archivedWallets
        val wallet = allWallets.firstOrNull { it.id == wId }
        if (wallet != null) {
            val txCount = homeState.transactions.count { it.walletId == wId || it.targetWalletId == wId }
            EditWalletSheet(
                wallet = wallet,
                transactionCount = txCount,
                onSave = { updated, onError ->
                    homeViewModel.updateWallet(
                        wallet = updated,
                        onSuccess = { editingWalletId = null },
                        onError = onError
                    )
                },
                onDismiss = { editingWalletId = null }
            )
        }
    }

    if (showCategorySheet) {
        CategoryManagementSheet(
            categories = activityState.categories,
            initialType = categoryInitialType,
            onCreateCategory = activityViewModel::createCategory,
            onRenameCategory = activityViewModel::renameCategory,
            onToggleArchive = activityViewModel::setCategoryArchived,
            onDismiss = { showCategorySheet = false }
        )
    }
}
