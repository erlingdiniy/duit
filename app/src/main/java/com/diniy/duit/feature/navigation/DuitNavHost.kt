@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.diniy.duit.feature.navigation

import com.diniy.duit.domain.model.Wallet
import com.diniy.duit.domain.model.WalletType
import com.diniy.duit.update.EditionUpdatePrompt
import com.diniy.duit.feature.onboarding.FirstTimeSetupScreen
import com.diniy.duit.feature.onboarding.OnboardingGate
import com.diniy.duit.feature.onboarding.OnboardingScreen
import com.diniy.duit.feature.onboarding.OnboardingStage
import kotlinx.coroutines.flow.first
import androidx.compose.ui.zIndex
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.key
import androidx.compose.foundation.layout.size
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import androidx.compose.foundation.layout.padding
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
import androidx.compose.foundation.background
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
import com.diniy.duit.core.ui.components.DuitQuickActionFab
import com.diniy.duit.core.ui.components.PrismaticFloatingBottomBar
import com.diniy.duit.core.ui.components.PrismaticLoadingCard
import com.diniy.duit.core.ui.components.PrismaticNavItem
import com.diniy.duit.core.ui.components.PrismaticNavigationRail
import com.diniy.duit.core.ui.components.rememberReducedMotionEnabled
import com.diniy.duit.core.ui.glass.liquidGlassBackdropSource
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
    modifier: Modifier = Modifier,
    updatesReady: Boolean = false
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
    var showCalendar by rememberSaveable { mutableStateOf(false) }
    var showReceiptVault by rememberSaveable { mutableStateOf(false) }
    var receiptDraftTxId by rememberSaveable { mutableStateOf<Long?>(null) }
    var calendarFocusDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var showNotificationSettings by rememberSaveable { mutableStateOf(false) }
    var showReplayTour by rememberSaveable { mutableStateOf(false) }
    var activePinFlowMode by rememberSaveable { mutableStateOf<PinFlowMode?>(null) }
    var requestedPlanningTab by rememberSaveable { mutableStateOf<PlanningSectionTab?>(null) }
    var requestCommissionSection by rememberSaveable { mutableStateOf(false) }
    val celebrate = com.diniy.duit.core.ui.components.LocalCandyCelebrate.current
    var glassScrollPulse by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val glassIntensity = com.diniy.duit.core.ui.theme.LocalGlassIntensity.current
    val glassBackdropState = com.diniy.duit.core.ui.glass.rememberLiquidGlassBackdropState(
        initialQualityMode = com.diniy.duit.core.ui.glass.LiquidGlassCapabilities.qualityModeFor(glassIntensity)
    )
    LaunchedEffect(glassIntensity) {
        glassBackdropState.qualityMode =
            com.diniy.duit.core.ui.glass.LiquidGlassCapabilities.qualityModeFor(glassIntensity)
    }
    LaunchedEffect(glassScrollPulse) {
        if (glassScrollPulse > 0) {
            glassBackdropState.isScrolling = true
            kotlinx.coroutines.delay(140L)
            glassBackdropState.isScrolling = false
        }
    }
    val fabScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (kotlin.math.abs(available.y) > 1.5f) glassScrollPulse++
                return Offset.Zero
            }
        }
    }

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

    // Phase 11L: first-run gate. Only a genuine new install sees onboarding (see OnboardingGate).
    var onboardingStage by rememberSaveable { mutableStateOf(OnboardingStage.UNKNOWN) }
    var setupSaving by remember { mutableStateOf(false) }
    var setupError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        if (onboardingStage != OnboardingStage.UNKNOWN) return@LaunchedEffect
        val prefs = container.userPreferencesRepository
        val decision = runCatching {
            val flag = prefs.observeOnboardingCompleted().first()
            if (flag == true) {
                false
            } else {
                val records = container.backupRestoreCoordinator.loadStorageSummary().totalFinancialRecords
                OnboardingGate.isNewUser(flag, records, prefs.hasStoredPreferences(), appLockState.isAppLockEnabled)
            }
        }.getOrDefault(false) // on any error, never onboard
        if (decision) {
            onboardingStage = OnboardingStage.TOUR
        } else {
            // Existing install: remember it so deleting all wallets later can never trigger onboarding.
            runCatching { prefs.setOnboardingCompleted(true) }
            onboardingStage = OnboardingStage.DONE
        }
    }

    if (showReplayTour) {
        // Replay is view-only: it never touches data, permissions, PIN or the completion flag.
        OnboardingScreen(
            onFinishTour = { showReplayTour = false },
            onProceedToSetup = { showReplayTour = false },
            isReplayTour = true
        )
        return
    }

    when (onboardingStage) {
        OnboardingStage.TOUR -> {
            OnboardingScreen(
                onFinishTour = { onboardingStage = OnboardingStage.SETUP },
                onProceedToSetup = { onboardingStage = OnboardingStage.SETUP }
            )
            return
        }
        OnboardingStage.SETUP -> {
            // System Back returns to the tour instead of leaving the app mid-setup.
            androidx.activity.compose.BackHandler { onboardingStage = OnboardingStage.TOUR }
            FirstTimeSetupScreen(
                hasOsNotificationPermission = notificationState.hasOsPermission,
                onPermissionUpdated = notificationViewModel::refreshPermissionAndSync,
                onCompleteSetup = { name, type, balanceSen, enableNotifications, accent ->
                    if (!setupSaving) {
                        setupSaving = true
                        setupError = null
                        coroutineScope.launch {
                            runCatching {
                                val now = System.currentTimeMillis()
                                container.walletRepository.upsertWallet(
                                    Wallet(
                                        id = 0L,
                                        name = name,
                                        type = type,
                                        balanceSen = balanceSen,
                                        isExcludedFromSafeToSpend = type == WalletType.SAVINGS,
                                        accentToken = accent,
                                        createdAtEpochMs = now,
                                        updatedAtEpochMs = now,
                                        isIncludedInTotalBalance = true,
                                        isArchived = false
                                    )
                                )
                                container.userPreferencesRepository.setOnboardingCompleted(true)
                            }.onSuccess {
                                if (enableNotifications) notificationViewModel.setMasterEnabled(true)
                                onboardingStage = OnboardingStage.DONE
                            }.onFailure {
                                setupError = "Couldn't save your wallet. Please try again."
                            }
                            setupSaving = false
                        }
                    }
                },
                onSkipSetup = {
                    coroutineScope.launch {
                        runCatching { container.userPreferencesRepository.setOnboardingCompleted(true) }
                        onboardingStage = OnboardingStage.DONE
                    }
                },
                errorMessage = setupError
            )
            return
        }
        else -> Unit
    }

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
            onSendTestNotification = notificationViewModel::sendTestNotification,
            onOpenNotificationCentre = { showNotificationCentre = true }
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
            onOpenNotificationCentre = { showNotificationCentre = true },
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
            viewModel = settingsViewModel,
            onReplayTour = { showReplayTour = true },
            hasOsNotificationPermission = notificationState.hasOsPermission,
            onPermissionUpdated = notificationViewModel::refreshPermissionAndSync
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
        NotificationDeepLink.PLANNING_COMMISSIONS,
        NotificationDeepLink.HOME,
        NotificationDeepLink.ACTIVITY,
        NotificationDeepLink.HOME_ADD_TRANSACTION,
        NotificationDeepLink.INSIGHTS,
        NotificationDeepLink.CALENDAR -> PlanningSectionTab.GOALS
    }

    if (showNotificationCentre) {
        NotificationCentreScreen(
            uiState = notificationState,
            onBack = { showNotificationCentre = false },
            onOpenSettings = { showNotificationSettings = true },
            onMarkAsRead = notificationViewModel::markAsRead,
            onMarkAllAsRead = notificationViewModel::markAllAsRead,
            onClearHistory = notificationViewModel::clearHistory,
            onNavigateToTarget = { target, epochDay ->
                showNotificationCentre = false
                if (target == NotificationDeepLink.CALENDAR) container.pendingCalendarEpochDay = epochDay
                container.setPendingDeepLink(target)
            },
            onPermissionUpdated = notificationViewModel::refreshPermissionAndSync
        )
        return
    }

    val receiptVaultViewModel = if (com.diniy.duit.BuildConfig.PUBLIC_EDITION) {
        viewModel<com.diniy.duit.feature.receipts.ReceiptVaultViewModel>(
            factory = com.diniy.duit.feature.receipts.ReceiptVaultViewModel.factory(
                receiptRepository = container.receiptRepository,
                transactionRepository = container.transactionRepository,
                categoryRepository = container.categoryRepository,
                previewModeController = container.previewModeController
            )
        )
    } else null
    val receiptVaultState = receiptVaultViewModel?.uiState?.collectAsStateWithLifecycle()?.value

    if (showCalendar) {
        val calendarViewModel: com.diniy.duit.feature.calendar.CalendarViewModel = viewModel(
            factory = com.diniy.duit.feature.calendar.CalendarViewModel.factory(
                transactionRepository = container.transactionRepository,
                planningRepository = container.planningRepository,
                savingsRepository = container.savingsRepository,
                previewModeController = container.previewModeController,
                obligationRepository = container.obligationRepository
            )
        )
        val calendarState by calendarViewModel.uiState.collectAsStateWithLifecycle()
        // A Money Reminder tap while the Calendar is already open just moves to that date.
        LaunchedEffect(pendingDeepLink) {
            if (pendingDeepLink == NotificationDeepLink.CALENDAR) {
                container.setPendingDeepLink(null)
                calendarFocusDay = container.pendingCalendarEpochDay.also { container.pendingCalendarEpochDay = null }
            }
        }
        LaunchedEffect(calendarFocusDay) {
            calendarFocusDay?.let { calendarViewModel.selectDay(it); calendarFocusDay = null }
        }
        com.diniy.duit.feature.calendar.CalendarScreen(
            uiState = calendarState,
            onBack = { showCalendar = false },
            onPreviousMonth = calendarViewModel::previousMonth,
            onNextMonth = calendarViewModel::nextMonth,
            onToday = calendarViewModel::goToToday,
            onSelectDay = calendarViewModel::selectDay,
            onRetry = calendarViewModel::retry,
            onSaveReminder = calendarViewModel::saveReminder,
            onDeleteReminder = calendarViewModel::deleteReminder
        )
        return
    }

    if (updatesReady && onboardingStage == OnboardingStage.DONE) EditionUpdatePrompt()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: DuitRoute.HOME

    // Consume OS or in-app notification deep links strictly after passing App Lock and once NavHost graph is attached
    LaunchedEffect(pendingDeepLink, backStackEntry) {
        val target = pendingDeepLink ?: return@LaunchedEffect
        if (backStackEntry == null) return@LaunchedEffect
        if (target.planningTabKey.isEmpty()) return@LaunchedEffect // handled by the effect below
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
    // Home/Insights notification taps; runs only after App Lock, like the planning links above.
    LaunchedEffect(pendingDeepLink, backStackEntry) {
        val target = pendingDeepLink?.takeIf { it.planningTabKey.isEmpty() } ?: return@LaunchedEffect
        if (backStackEntry == null) return@LaunchedEffect
        container.setPendingDeepLink(null)
        if (target == NotificationDeepLink.CALENDAR) {
            calendarFocusDay = container.pendingCalendarEpochDay.also { container.pendingCalendarEpochDay = null }
            showCalendar = true
            return@LaunchedEffect
        }
        val route = when (target) {
            NotificationDeepLink.INSIGHTS -> DuitRoute.INSIGHTS
            NotificationDeepLink.ACTIVITY -> DuitRoute.ACTIVITY
            else -> DuitRoute.HOME
        }
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
        if (target == NotificationDeepLink.HOME_ADD_TRANSACTION) showQuickAddSheet = true
    }
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
            WidgetDeepLink.PLANNING_BUDGETS_LINK -> {
                requestedPlanningTab = PlanningSectionTab.BUDGETS
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
            val visualTheme = com.diniy.duit.core.ui.theme.LocalVisualTheme.current
            val enableLiquidChrome = !useNavigationRail &&
                visualTheme == com.diniy.duit.domain.model.VisualTheme.LIQUID_GLASS

            androidx.compose.runtime.CompositionLocalProvider(
                com.diniy.duit.core.ui.glass.LocalLiquidGlassBackdrop provides
                    if (enableLiquidChrome) glassBackdropState else null
            ) {
            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets.safeDrawing,
                bottomBar = {
                    if (!useNavigationRail) {
                        PrismaticFloatingBottomBar(
                            items = navItems,
                            currentRoute = currentRoute,
                            onSelect = { navigateToRoute(it.route) },
                            // Dock Quick Add beside the floating 4-tab capsule so it never covers
                            // bottom-right card CTAs such as Add Funds or row amounts.
                            onQuickAddClick = null,
                            onTrailingQuickAddClick = if (!WindowInsets.isImeVisible) {
                                { showQuickAddSheet = true }
                            } else {
                                null
                            }
                        )
                    }
                }
            ) { innerPadding ->
                val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
                // Generous bottom clearance so the last scrollable card & CTA scroll completely
                // above the floating bottom dock without ever being obscured.
                val extraDockBreathing = if (useNavigationRail) 20.dp else 32.dp
                val baseBottom = innerPadding.calculateBottomPadding() + extraDockBreathing
                val screenPadding = PaddingValues(
                    start = if (useNavigationRail) 12.dp else innerPadding.calculateStartPadding(layoutDirection) + 16.dp,
                    end = innerPadding.calculateEndPadding(layoutDirection) + 16.dp,
                    top = innerPadding.calculateTopPadding() + 12.dp,
                    bottom = maxOf(baseBottom, imeBottom + 16.dp)
                )

                Row(
                    modifier = if (enableLiquidChrome) {
                        Modifier
                            .fillMaxSize()
                            .liquidGlassBackdropSource(glassBackdropState)
                            .background(com.diniy.duit.core.ui.theme.SpatialTheme.colors.appBackground)
                    } else {
                        Modifier.fillMaxSize()
                    }
                ) {
                    if (useNavigationRail) {
                        PrismaticNavigationRail(
                            items = navItems,
                            currentRoute = currentRoute,
                            onSelect = { navigateToRoute(it.route) },
                            onQuickAddClick = { showQuickAddSheet = true }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .nestedScroll(fabScrollConnection)
                    ) {
                        // Phase 11K: NavHost still owns the route and back stack (Back, deep links,
                        // tab requests); it renders nothing itself.
                        NavHost(
                            navController = navController,
                            startDestination = DuitRoute.HOME,
                            enterTransition = { EnterTransition.None },
                            exitTransition = { ExitTransition.None },
                            popEnterTransition = { EnterTransition.None },
                            popExitTransition = { ExitTransition.None },
                            modifier = Modifier.size(0.dp)
                        ) {
                            composable(DuitRoute.HOME) {}
                            composable(DuitRoute.ACTIVITY) {}
                            composable(DuitRoute.PLANNING) {}
                            composable(DuitRoute.INSIGHTS) {}
                        }

                        // Keep-alive tabs: each tab composes once on first visit and then stays in
                        // memory, so switching tabs no longer re-composes a whole screen (that was the
                        // measured one-frame hitch). Hidden tabs are invisible, untouchable and hidden
                        // from TalkBack.
                        val visitedTabs = remember { mutableStateListOf<String>() }
                        if (currentRoute !in visitedTabs) visitedTabs.add(currentRoute)
                        Box(modifier = Modifier.fillMaxSize()) {
                            listOf(DuitRoute.HOME, DuitRoute.ACTIVITY, DuitRoute.PLANNING, DuitRoute.INSIGHTS).forEach { tab ->
                                if (tab in visitedTabs) {
                                    key(tab) {
                                        val selected = tab == currentRoute
                                        val tabAlpha by animateFloatAsState(
                                            targetValue = if (selected) 1f else 0f,
                                            animationSpec = if (reducedMotion) snap() else tween(if (selected) 120 else 90),
                                            label = "tab_alpha"
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .zIndex(if (selected) 1f else 0f)
                                                .graphicsLayer { alpha = tabAlpha }
                                                .then(
                                                    if (selected) {
                                                        Modifier
                                                    } else {
                                                        Modifier
                                                            .clearAndSetSemantics { }
                                                            .pointerInput(Unit) {
                                                                awaitPointerEventScope {
                                                                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                                                                }
                                                            }
                                                    }
                                                )
                                        ) {
                                            when (tab) {
                                                DuitRoute.HOME -> {
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
                                        requestCommissionSection = true
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
                                    onOpenNotifications = { showNotificationCentre = true },
                                    // Smart Money Calendar: public edition only, like the other v1.1+ features.
                                    onOpenCalendar = if (com.diniy.duit.BuildConfig.PUBLIC_EDITION) ({ showCalendar = true }) else null
                                )
                            
                                                }
                                                DuitRoute.ACTIVITY -> {
                                ActivityScreen(
                                    uiState = activityState,
                                    contentPadding = screenPadding,
                                    onSelectFilter = activityViewModel::selectFilter,
                                    onSearchQueryChange = activityViewModel::updateSearchQuery,
                                    onExtraFiltersChange = activityViewModel::updateExtraFilters,
                                    onOpenNewTransaction = openNewTransaction,
                                    onSelectTransaction = openTransactionDetail,
                                    onOpenCategories = {
                                        categoryInitialType = TransactionType.EXPENSE
                                        showCategorySheet = true
                                    },
                                    onOpenReceiptVault = if (com.diniy.duit.BuildConfig.PUBLIC_EDITION) ({
                                        receiptDraftTxId = null
                                        showReceiptVault = true
                                    }) else null,
                                    onEnablePreview = activityViewModel::enableSamplePreview,
                                    onExitPreview = activityViewModel::exitSamplePreview,
                                    onDismissError = activityViewModel::dismissError
                                )
                            
                                                }
                                                DuitRoute.PLANNING -> {
                                // Savings Challenges: public edition only, like the other v1.2 features.
                                val challengesBinding = if (com.diniy.duit.BuildConfig.PUBLIC_EDITION) {
                                    val challengesViewModel: com.diniy.duit.feature.savings.challenges.SavingsChallengesViewModel = viewModel(
                                        factory = com.diniy.duit.feature.savings.challenges.SavingsChallengesViewModel.factory(container.savingsRepository)
                                    )
                                    val challengesState by challengesViewModel.uiState.collectAsStateWithLifecycle()
                                    com.diniy.duit.feature.savings.challenges.SavingsChallengesBinding(
                                        state = challengesState,
                                        safeToSpendSen = homeState.summary.safeToSpendSen,
                                        onSave = challengesViewModel::save,
                                        onSetPaused = challengesViewModel::setPaused,
                                        onSetSkipped = challengesViewModel::setSkipped,
                                        onDelete = challengesViewModel::delete,
                                        onRetry = challengesViewModel::retry
                                    )
                                } else null
                                val taxPocketBinding = if (com.diniy.duit.BuildConfig.PUBLIC_EDITION) {
                                    val taxViewModel: com.diniy.duit.feature.tax.TaxReliefViewModel = viewModel(
                                        factory = com.diniy.duit.feature.tax.TaxReliefViewModel.factory(
                                            taxReliefRepository = container.taxReliefRepository,
                                            receiptRepository = container.receiptRepository,
                                            previewModeController = container.previewModeController
                                        )
                                    )
                                    val taxState by taxViewModel.uiState.collectAsStateWithLifecycle()
                                    com.diniy.duit.feature.tax.TaxPocketBinding(
                                        state = taxState,
                                        onSelectYear = taxViewModel::selectYear,
                                        onSaveEntry = taxViewModel::saveEntry,
                                        onUpdateStatus = taxViewModel::updateStatus,
                                        onDeleteEntry = taxViewModel::deleteEntry
                                    )
                                } else null
                                PlanningScreen(
                                    challenges = challengesBinding,
                                    taxPocket = taxPocketBinding,
                                    commissionEnabled = homeState.appSettings.commissionFeatureEnabled,
                                    uiState = planningState,
                                    contentPadding = screenPadding,
                                    onShiftMonth = planningViewModel::shiftMonth,
                                    onOpenFinancialPreferences = {
                                        settingsHubRoute = SettingsHubRoute.FINANCIAL
                                        settingsViewModel.refreshStorageSummary()
                                        showSettingsHub = true
                                    },
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
                                    onConsumeRequestedSectionTab = { requestedPlanningTab = null },
                                    requestCommissionSection = requestCommissionSection,
                                    onConsumeCommissionSection = { requestCommissionSection = false }
                                )
                            
                                                }
                                                DuitRoute.INSIGHTS -> {
                                // Money Personality: public edition only, offline and read-only.
                                val personalityBinding = if (com.diniy.duit.BuildConfig.PUBLIC_EDITION) {
                                    val personalityViewModel: com.diniy.duit.feature.personality.MoneyPersonalityViewModel = viewModel(
                                        factory = com.diniy.duit.feature.personality.MoneyPersonalityViewModel.factory(
                                            transactionRepository = container.transactionRepository,
                                            categoryRepository = container.categoryRepository,
                                            savingsRepository = container.savingsRepository,
                                            budgetRepository = container.budgetRepository,
                                            planningRepository = container.planningRepository,
                                            obligationRepository = container.obligationRepository,
                                            previewModeController = container.previewModeController
                                        )
                                    )
                                    val personalityState by personalityViewModel.uiState.collectAsStateWithLifecycle()
                                    com.diniy.duit.feature.personality.MoneyPersonalityBinding(
                                        state = personalityState,
                                        onPreviousMonth = personalityViewModel::previousMonth,
                                        onNextMonth = personalityViewModel::nextMonth,
                                        onRetry = personalityViewModel::retry
                                    )
                                } else null
                                val littleWinsBinding = if (com.diniy.duit.BuildConfig.PUBLIC_EDITION) {
                                    val littleWinsViewModel: com.diniy.duit.feature.wins.LittleWinsViewModel = viewModel(
                                        factory = com.diniy.duit.feature.wins.LittleWinsViewModel.factory(
                                            savingsRepository = container.savingsRepository,
                                            budgetRepository = container.budgetRepository,
                                            transactionRepository = container.transactionRepository,
                                            categoryRepository = container.categoryRepository,
                                            planningRepository = container.planningRepository,
                                            obligationRepository = container.obligationRepository,
                                            milestoneRepository = container.milestoneRepository,
                                            previewModeController = container.previewModeController
                                        )
                                    )
                                    val littleWinsState by littleWinsViewModel.uiState.collectAsStateWithLifecycle()
                                    com.diniy.duit.feature.wins.LittleWinsBinding(
                                        state = littleWinsState,
                                        onSelectCategory = littleWinsViewModel::selectCategory,
                                        onMarkCelebrated = littleWinsViewModel::markCelebrated
                                    )
                                } else null
                                InsightsScreen(
                                    personality = personalityBinding,
                                    littleWins = littleWinsBinding,
                                    commissionEnabled = homeState.appSettings.commissionFeatureEnabled,
                                    uiState = insightsState,
                                    contentPadding = screenPadding,
                                    onShiftMonth = insightsViewModel::shiftMonth,
                                    onSelectPeriodPreset = insightsViewModel::selectPeriodPreset,
                                    onSetCustomDateRange = insightsViewModel::setCustomDateRange,
                                    onSelectTrendHorizon = insightsViewModel::selectTrendHorizon,
                                    onInspectCategory = insightsViewModel::openCategoryBreakdown,
                                    onDismissCategorySheet = insightsViewModel::dismissCategoryBreakdown,
                                    onEnablePreview = insightsViewModel::enableSamplePreview,
                                    onExitPreview = insightsViewModel::exitSamplePreview,
                                    onAddTransaction = { openNewTransaction(TransactionType.EXPENSE) }
                                )
                            
                                                }
                                            }
                                        }
                                    }
                                }
                            }
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
            onOpenCommissionTools = if (homeState.appSettings.commissionFeatureEnabled) {
                {
                    requestCommissionSection = true
                    navigateToRoute(DuitRoute.PLANNING)
                }
            } else {
                null
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
                        celebrate("Transaction saved")
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
            val linkedReceipts = receiptVaultState?.allReceipts?.count { it.transactionId == tx.id } ?: 0
            TransactionDetailSheet(
                transaction = tx,
                walletsById = activityState.walletsById,
                isReadOnlyPreview = activityState.isSamplePreview,
                deleteError = detailDeleteError,
                linkedReceiptCount = linkedReceipts,
                onOpenReceiptsForTransaction = if (com.diniy.duit.BuildConfig.PUBLIC_EDITION && tx.type != TransactionType.TRANSFER) ({
                    inspectedTransactionId = null
                    receiptDraftTxId = if (linkedReceipts == 0) tx.id else null
                    showReceiptVault = true
                }) else null,
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

    if (showReceiptVault && receiptVaultViewModel != null && receiptVaultState != null) {
        com.diniy.duit.feature.receipts.ReceiptVaultScreen(
            state = receiptVaultState,
            fileStorage = container.receiptFileStorage,
            initialDraftTransaction = receiptDraftTxId?.let { id -> homeState.transactions.firstOrNull { it.id == id } },
            onSelectTab = receiptVaultViewModel::selectTab,
            onSearchQueryChange = receiptVaultViewModel::updateSearchQuery,
            onSaveReceipt = { rec, onSaved, onErr ->
                receiptVaultViewModel.saveReceipt(rec, onSaved = { onSaved() }, onError = onErr)
            },
            onLinkTransaction = receiptVaultViewModel::linkToTransaction,
            onDeleteReceipt = receiptVaultViewModel::deleteReceipt,
            onDismissError = receiptVaultViewModel::dismissError,
            onClose = {
                showReceiptVault = false
                receiptDraftTxId = null
            }
        )
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


private val TabOrder = listOf(DuitRoute.HOME, DuitRoute.ACTIVITY, DuitRoute.PLANNING, DuitRoute.INSIGHTS)

/** +1 when moving to a tab on the right, -1 to the left, 0 otherwise. */
private fun tabDirection(from: String?, to: String?): Int {
    val a = TabOrder.indexOf(from)
    val b = TabOrder.indexOf(to)
    return if (a < 0 || b < 0 || a == b) 0 else if (b > a) 1 else -1
}
