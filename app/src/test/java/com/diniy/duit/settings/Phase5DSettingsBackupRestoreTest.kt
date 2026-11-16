package com.diniy.duit.settings

import android.content.Context
import androidx.fragment.app.FragmentActivity
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.diniy.duit.backup.BackupAuthOrIntegrityException
import com.diniy.duit.backup.BackupCryptoEngine
import com.diniy.duit.backup.BackupFormatException
import com.diniy.duit.backup.BackupRestoreCoordinator
import com.diniy.duit.backup.BackupSerializer
import com.diniy.duit.backup.BackupVersionException
import com.diniy.duit.backup.PortablePreferencesBackup
import com.diniy.duit.backup.PortablePrivacyPreferences
import com.diniy.duit.core.ui.state.PrivacyMask
import com.diniy.duit.data.local.DuitDatabase
import com.diniy.duit.data.local.dao.LedgerDatabaseSnapshot
import com.diniy.duit.data.local.entity.BudgetEntity
import com.diniy.duit.data.local.entity.CategoryEntity
import com.diniy.duit.data.local.entity.CommissionEntity
import com.diniy.duit.data.local.entity.CommissionPaymentEntity
import com.diniy.duit.data.local.entity.CommissionRuleVersionEntity
import com.diniy.duit.data.local.entity.CommissionSourceEntity
import com.diniy.duit.data.local.entity.CommitmentEntity
import com.diniy.duit.data.local.entity.DailySalesRecordEntity
import com.diniy.duit.data.local.entity.DebtEntity
import com.diniy.duit.data.local.entity.NotificationHistoryEntity
import com.diniy.duit.data.local.entity.ObligationPaymentEntity
import com.diniy.duit.data.local.entity.PayLaterPlanEntity
import com.diniy.duit.data.local.entity.SalaryCycleEntity
import com.diniy.duit.data.local.entity.SavingsAllocationEntity
import com.diniy.duit.data.local.entity.SavingsGoalEntity
import com.diniy.duit.data.local.entity.SavingsMovementEntity
import com.diniy.duit.data.local.entity.TransactionEntity
import com.diniy.duit.data.local.entity.WalletEntity
import com.diniy.duit.data.preferences.UserPreferencesDataStore
import com.diniy.duit.data.repository.OfflineFirstFinanceRepository
import com.diniy.duit.data.repository.OfflineUserPreferencesRepository
import com.diniy.duit.domain.calculator.FinanceSummaryCalculator
import com.diniy.duit.domain.calculator.ReminderCalculator
import com.diniy.duit.domain.model.AnimationIntensity
import com.diniy.duit.domain.model.AppSettingsSnapshot
import com.diniy.duit.domain.model.Budget
import com.diniy.duit.domain.model.BudgetPeriodType
import com.diniy.duit.domain.model.BudgetScope
import com.diniy.duit.domain.model.Category
import com.diniy.duit.domain.model.CommissionFormulaConfig
import com.diniy.duit.domain.model.CommissionStatus
import com.diniy.duit.domain.model.Commitment
import com.diniy.duit.domain.model.CommitmentFrequency
import com.diniy.duit.domain.model.CommitmentStatus
import com.diniy.duit.domain.model.DashboardSectionKey
import com.diniy.duit.domain.model.DebtDirection
import com.diniy.duit.domain.model.FinanceTransaction
import com.diniy.duit.domain.model.GlassIntensity
import com.diniy.duit.domain.model.ObligationType
import com.diniy.duit.domain.model.PrismaticAccentToken
import com.diniy.duit.domain.model.SavingsGoalStatus
import com.diniy.duit.domain.model.SavingsMovementType
import com.diniy.duit.domain.model.ThemeMode
import com.diniy.duit.domain.model.TransactionType
import com.diniy.duit.domain.model.Wallet
import com.diniy.duit.domain.model.WalletType
import com.diniy.duit.notifications.BudgetAlertThreshold
import com.diniy.duit.notifications.NotificationCategory
import com.diniy.duit.notifications.NotificationDeepLink
import com.diniy.duit.notifications.NotificationPreferencesRepository
import com.diniy.duit.notifications.NotificationSettingsSnapshot
import com.diniy.duit.notifications.ReminderOffset
import com.diniy.duit.notifications.ReminderScheduler
import com.diniy.duit.notifications.RoomNotificationHistoryRepository
import com.diniy.duit.security.AppLockManager
import com.diniy.duit.security.AutoLockTimeout
import com.diniy.duit.security.BiometricAuthGateway
import com.diniy.duit.security.BiometricAuthResult
import com.diniy.duit.security.BiometricAvailability
import com.diniy.duit.security.PinHashEngine
import com.diniy.duit.security.SecretProtector
import com.diniy.duit.security.SecurityConfigSnapshot
import com.diniy.duit.security.SecurityRepository
import com.diniy.duit.widgets.WidgetSnapshotCalculator
import com.diniy.duit.widgets.WidgetUpdateCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.ZoneId
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase5DSettingsBackupRestoreTest {

    private lateinit var context: Context
    private lateinit var db: DuitDatabase
    private lateinit var financeRepo: OfflineFirstFinanceRepository

    private class InMemorySecretProtector : SecretProtector {
        override fun seal(plaintext: String): String = "test_sealed::$plaintext"
        override fun unseal(ciphertext: String): String? =
            if (ciphertext.startsWith("test_sealed::")) ciphertext.removePrefix("test_sealed::") else null
    }

    private class FakeSecurityRepo(
        initial: SecurityConfigSnapshot = SecurityConfigSnapshot()
    ) : SecurityRepository {
        val state = MutableStateFlow(initial)
        override fun observeSecurityConfig(): Flow<SecurityConfigSnapshot> = state.asStateFlow()
        override suspend fun savePinAndEnableAppLock(encryptedPinEnvelope: String) {
            state.value = state.value.copy(encryptedPinEnvelope = encryptedPinEnvelope, isAppLockEnabled = true)
        }
        override suspend fun updatePinEnvelope(encryptedPinEnvelope: String) {
            state.value = state.value.copy(encryptedPinEnvelope = encryptedPinEnvelope)
        }
        override suspend fun disableAppLock() {
            state.value = state.value.copy(
                encryptedPinEnvelope = null,
                isAppLockEnabled = false,
                isBiometricEnabled = false,
                failedAttempts = 0,
                lockoutUntilEpochMs = 0L
            )
        }
        override suspend fun setBiometricEnabled(enabled: Boolean) {
            state.value = state.value.copy(isBiometricEnabled = enabled)
        }
        override suspend fun setAutoLockTimeout(timeout: AutoLockTimeout) {
            state.value = state.value.copy(autoLockTimeout = timeout)
        }
        override suspend fun setHideSensitiveBalances(hidden: Boolean) {
            state.value = state.value.copy(hideSensitiveBalances = hidden)
        }
        override suspend fun setPrivacyModeEnabled(enabled: Boolean) {
            state.value = state.value.copy(privacyModeEnabled = enabled)
        }
        override suspend fun setAlwaysMaskWidgetAmounts(alwaysMask: Boolean) {
            state.value = state.value.copy(alwaysMaskWidgetAmounts = alwaysMask)
        }
        override suspend fun resetPrivacyDefaults() {
            state.value = state.value.copy(
                hideSensitiveBalances = false,
                privacyModeEnabled = false,
                alwaysMaskWidgetAmounts = false
            )
        }
        override suspend fun recordFailedAttempt(failedAttempts: Int, lockoutUntilEpochMs: Long) {
            state.value = state.value.copy(failedAttempts = failedAttempts, lockoutUntilEpochMs = lockoutUntilEpochMs)
        }
        override suspend fun clearFailedAttempts() {
            state.value = state.value.copy(failedAttempts = 0, lockoutUntilEpochMs = 0L)
        }
        override suspend fun recordBackgroundedAt(epochMs: Long) {
            state.value = state.value.copy(lastBackgroundedEpochMs = epochMs)
        }
        override suspend fun clearAllSecurityState() {
            state.value = SecurityConfigSnapshot()
        }
    }

    private class FakeNotificationPrefsRepo(
        initial: NotificationSettingsSnapshot = NotificationSettingsSnapshot()
    ) : NotificationPreferencesRepository {
        val state = MutableStateFlow(initial)
        override fun observeSettings(): Flow<NotificationSettingsSnapshot> = state.asStateFlow()
        override suspend fun setMasterEnabled(enabled: Boolean) {
            state.value = state.value.copy(masterEnabled = enabled)
        }
        override suspend fun setCategoryEnabled(category: NotificationCategory, enabled: Boolean) {
            val next = state.value.enabledCategories.toMutableSet()
            if (enabled) next.add(category) else next.remove(category)
            state.value = state.value.copy(enabledCategories = next)
        }
        override suspend fun setOffsetEnabled(offset: ReminderOffset, enabled: Boolean) {
            val next = state.value.enabledOffsets.toMutableSet()
            if (enabled) next.add(offset) else next.remove(offset)
            state.value = state.value.copy(enabledOffsets = next)
        }
        override suspend fun setBudgetThresholdEnabled(threshold: BudgetAlertThreshold, enabled: Boolean) {
            val next = state.value.enabledBudgetThresholds.toMutableSet()
            if (enabled) next.add(threshold) else next.remove(threshold)
            state.value = state.value.copy(enabledBudgetThresholds = next)
        }
        override suspend fun setOverdueEnabled(enabled: Boolean) {
            state.value = state.value.copy(overdueEnabled = enabled)
        }
        override suspend fun setReminderTime(hour: Int, minute: Int) {
            state.value = state.value.copy(reminderHour = hour, reminderMinute = minute)
        }
        override suspend fun setHideLockScreenAmounts(hide: Boolean) {
            state.value = state.value.copy(hideLockScreenAmounts = hide)
        }
        override suspend fun applyPortableSettings(snapshot: NotificationSettingsSnapshot) {
            state.value = snapshot
        }
        override suspend fun resetDefaults() {
            state.value = NotificationSettingsSnapshot()
        }
    }

    private class RecordingReminderScheduler : ReminderScheduler {
        var immediateSyncCount = 0
        override fun ensureDailySafetyNet() {}
        override fun enqueueImmediateSync() {
            immediateSyncCount++
        }
        override fun scheduleNextTriggerAt(triggerAtEpochMs: Long, nowEpochMs: Long) {}
        override fun cancelScheduledFutureTrigger() {}
        override fun cancelAllWork() {}
    }

    private class RecordingWidgetCoordinator : WidgetUpdateCoordinator {
        var immediateRefreshCount = 0
        override fun enqueueRefresh() {}
        override suspend fun refreshAllWidgetsNow() {
            immediateRefreshCount++
        }
    }

    private fun createAppLockManager(
        secRepo: FakeSecurityRepo,
        scope: CoroutineScope,
        clockMs: Long = 1_750_000_000_000L
    ): AppLockManager {
        return AppLockManager(
            securityRepository = secRepo,
            pinHashEngine = PinHashEngine(InMemorySecretProtector(), iterations = 1_000),
            biometricGateway = object : BiometricAuthGateway {
                override fun checkAvailability(): BiometricAvailability = BiometricAvailability.AVAILABLE
                override fun authenticate(
                    activity: FragmentActivity,
                    title: String,
                    subtitle: String,
                    negativeButtonText: String,
                    onResult: (BiometricAuthResult) -> Unit
                ) = onResult(BiometricAuthResult.Succeeded)
            },
            scope = scope,
            onFactoryResetFinancialData = {},
            currentTimeMs = { clockMs },
            cryptoDispatcher = Dispatchers.Unconfined
        )
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, DuitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        financeRepo = OfflineFirstFinanceRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun settingsPersistence_appearanceMotionDashboardFinancial_persistsAcrossRepositoryAndApplies() = runBlocking {
        val dataStore = UserPreferencesDataStore(context)
        val prefsRepo = OfflineUserPreferencesRepository(dataStore)
        prefsRepo.restoreDefaultPreferences()

        val customOrder = listOf(
            DashboardSectionKey.UPCOMING_COMMITMENTS,
            DashboardSectionKey.WALLETS,
            DashboardSectionKey.QUICK_ACTIONS,
            DashboardSectionKey.SECONDARY_METRICS,
            DashboardSectionKey.PENDING_COMMISSIONS
        )
        val formula = CommissionFormulaConfig(
            formulaName = "Store Tier",
            isConfigured = true,
            isEnabled = true,
            minimumSalesThresholdSen = 250_000L,
            rateBps = 1250,
            flatBonusSen = 15_000L
        )

        prefsRepo.setThemeMode(ThemeMode.DARK)
        prefsRepo.setGlassIntensity(GlassIntensity.RICH)
        prefsRepo.setHapticFeedbackEnabled(false)
        prefsRepo.setReduceMotion(true)
        prefsRepo.setAnimationIntensity(AnimationIntensity.SUBTLE)
        prefsRepo.setDashboardSectionVisible(DashboardSectionKey.PENDING_COMMISSIONS, false)
        prefsRepo.setDashboardSectionOrder(customOrder)
        prefsRepo.setDefaultWalletId(42L)
        prefsRepo.setDefaultBudgetPeriodType(BudgetPeriodType.SALARY_CYCLE)
        prefsRepo.setAutoAdvanceRecurringCommitments(false)
        prefsRepo.setCommissionTrackingEnabled(false)
        prefsRepo.saveCommissionFormulaConfig(formula)

        val snapshot = prefsRepo.observeAppSettings().first()
        assertEquals(ThemeMode.DARK, snapshot.themeMode)
        assertEquals(GlassIntensity.RICH, snapshot.glassIntensity)
        assertFalse(snapshot.hapticFeedbackEnabled)
        assertTrue(snapshot.reduceMotion)
        assertEquals(AnimationIntensity.SUBTLE, snapshot.animationIntensity)
        assertEquals(setOf(DashboardSectionKey.PENDING_COMMISSIONS), snapshot.hiddenDashboardSections)
        assertEquals(customOrder, snapshot.dashboardSectionOrder)
        assertEquals(42L, snapshot.defaultWalletId)
        assertEquals(BudgetPeriodType.SALARY_CYCLE, snapshot.defaultBudgetPeriodType)
        assertFalse(snapshot.autoAdvanceRecurringCommitments)
        assertFalse(snapshot.commissionTrackingEnabled)
        assertEquals(1250, snapshot.commissionFormulaConfig.rateBps)
        assertEquals(15_000L, snapshot.commissionFormulaConfig.flatBonusSen)

        prefsRepo.restoreDefaultDashboardLayout()
        val afterLayoutReset = prefsRepo.observeAppSettings().first()
        assertTrue(afterLayoutReset.hiddenDashboardSections.isEmpty())
        assertEquals(DashboardSectionKey.entries.toList(), afterLayoutReset.dashboardSectionOrder)
        assertEquals(ThemeMode.DARK, afterLayoutReset.themeMode)
    }

    @Test
    fun financialPreference_autoAdvanceRecurringToggle_controlsCommitmentPaymentBehavior() = runBlocking {
        val walletId = 1L
        db.backupDao().insertWallets(
            listOf(
                WalletEntity(
                    id = walletId,
                    name = "Maybank",
                    type = WalletType.BANK,
                    balanceSen = 500_000L,
                    isExcludedFromSafeToSpend = false,
                    accentToken = PrismaticAccentToken.AQUA,
                    createdAtEpochMs = 1L,
                    updatedAtEpochMs = 1L,
                    isIncludedInTotalBalance = true,
                    isArchived = false
                )
            )
        )
        val c1 = financeRepo.upsertCommitmentDetail(
            Commitment(
                id = 0L,
                title = "Internet Bill",
                amountSen = 12_900L,
                dueDateEpochDay = LocalDate.of(2026, 6, 15).toEpochDay(),
                status = CommitmentStatus.UPCOMING,
                walletId = walletId,
                isRecurring = true,
                salaryCycleId = null,
                category = "Bills",
                frequency = CommitmentFrequency.MONTHLY
            )
        )
        financeRepo.recordCommitmentPayment(
            commitmentId = c1,
            walletId = walletId,
            amountSen = 12_900L,
            note = "Manual control",
            paidAtEpochMs = 1_750_000_000_000L,
            submissionId = "sub_no_advance",
            autoAdvanceRecurring = false
        )
        val updatedNoAdvance = db.commitmentDao().getById(c1)!!
        assertEquals(CommitmentStatus.PAID, updatedNoAdvance.status)
        assertEquals(LocalDate.of(2026, 6, 15).toEpochDay(), updatedNoAdvance.dueDateEpochDay)

        val c2 = financeRepo.upsertCommitmentDetail(
            Commitment(
                id = 0L,
                title = "Phone Plan",
                amountSen = 5_000L,
                dueDateEpochDay = LocalDate.of(2026, 6, 15).toEpochDay(),
                status = CommitmentStatus.UPCOMING,
                walletId = walletId,
                isRecurring = true,
                salaryCycleId = null,
                category = "Bills",
                frequency = CommitmentFrequency.MONTHLY
            )
        )
        financeRepo.recordCommitmentPayment(
            commitmentId = c2,
            walletId = walletId,
            amountSen = 5_000L,
            note = "Auto advance",
            paidAtEpochMs = 1_750_000_000_000L,
            submissionId = "sub_auto_advance",
            autoAdvanceRecurring = true
        )
        val updatedAutoAdvance = db.commitmentDao().getById(c2)!!
        assertEquals(CommitmentStatus.UPCOMING, updatedAutoAdvance.status)
        assertEquals(LocalDate.of(2026, 7, 15).toEpochDay(), updatedAutoAdvance.dueDateEpochDay)
    }

    @Test
    fun dashboardCustomization_hidingSectionsNeverModifiesFinancialTotalsOrSafeToSpend() {
        val wallets = listOf(
            Wallet(1L, "Main", WalletType.BANK, 300_000L, false, PrismaticAccentToken.AQUA, 1L, 1L)
        )
        val commitments = listOf(
            Commitment(
                id = 1L, title = "Rent", amountSen = 80_000L,
                dueDateEpochDay = LocalDate.of(2026, 6, 20).toEpochDay(),
                status = CommitmentStatus.UPCOMING, walletId = 1L, isRecurring = true,
                salaryCycleId = null, category = "Housing", frequency = CommitmentFrequency.MONTHLY
            )
        )
        val summaryBefore = FinanceSummaryCalculator.calculate(
            wallets = wallets, transactions = emptyList(), commitments = commitments,
            commissions = emptyList(), savingsAllocations = emptyList(), savingsGoals = emptyList()
        )
        val customHidden = AppSettingsSnapshot(hiddenDashboardSections = DashboardSectionKey.entries.toSet())
        val summaryAfter = FinanceSummaryCalculator.calculate(
            wallets = wallets, transactions = emptyList(), commitments = commitments,
            commissions = emptyList(), savingsAllocations = emptyList(), savingsGoals = emptyList()
        )
        assertEquals(DashboardSectionKey.entries.size, customHidden.hiddenDashboardSections.size)
        assertEquals(summaryBefore.safeToSpendSen, summaryAfter.safeToSpendSen)
        assertEquals(220_000L, summaryAfter.safeToSpendSen)
    }

    @Test
    fun notificationSettings_budgetThresholds80_90_100_triggerAndRespectUserToggles() {
        val zone = ZoneId.of("Asia/Kuala_Lumpur")
        val nowInstant = LocalDate.of(2026, 6, 15).atTime(10, 0).atZone(zone).toInstant()
        val txMs = LocalDate.of(2026, 6, 10).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

        val catFood = Category(10L, "Food", TransactionType.EXPENSE, "tag", PrismaticAccentToken.AQUA, isDefault = true, sortOrder = 0)
        val catTransport = Category(11L, "Transport", TransactionType.EXPENSE, "tag", PrismaticAccentToken.SKY_BLUE, isDefault = true, sortOrder = 1)

        val budgets = listOf(
            Budget(
                id = 1L, name = "Food Budget", scope = BudgetScope.CATEGORY,
                periodType = BudgetPeriodType.MONTHLY, yearMonth = "2026-06", salaryCycleId = null,
                categoryId = 10L, categoryName = "Food", limitSen = 10_000L, accentToken = PrismaticAccentToken.AQUA,
                note = "", isArchived = false, createdAtEpochMs = 1L, updatedAtEpochMs = 1L
            ),
            Budget(
                id = 2L, name = "Transport Budget", scope = BudgetScope.CATEGORY,
                periodType = BudgetPeriodType.MONTHLY, yearMonth = "2026-06", salaryCycleId = null,
                categoryId = 11L, categoryName = "Transport", limitSen = 10_000L, accentToken = PrismaticAccentToken.SKY_BLUE,
                note = "", isArchived = false, createdAtEpochMs = 1L, updatedAtEpochMs = 1L
            )
        )
        val transactions = listOf(
            FinanceTransaction(
                id = 101L, walletId = 1L, targetWalletId = null, type = TransactionType.EXPENSE,
                amountSen = 9_200L, category = "Food", title = "Dinner", note = "",
                commitmentId = null, occurredAtEpochMs = txMs, createdAtEpochMs = txMs, categoryId = 10L
            ),
            FinanceTransaction(
                id = 102L, walletId = 1L, targetWalletId = null, type = TransactionType.EXPENSE,
                amountSen = 10_500L, category = "Transport", title = "Fuel", note = "",
                commitmentId = null, occurredAtEpochMs = txMs, createdAtEpochMs = txMs, categoryId = 11L
            )
        )

        val allThresholdsSettings = NotificationSettingsSnapshot(
            masterEnabled = true,
            enabledCategories = setOf(NotificationCategory.BUDGETS),
            enabledBudgetThresholds = setOf(
                BudgetAlertThreshold.PERCENT_80,
                BudgetAlertThreshold.PERCENT_90,
                BudgetAlertThreshold.PERCENT_100
            )
        )
        val evalAll = ReminderCalculator.evaluate(
            settings = allThresholdsSettings,
            commitments = emptyList(),
            payLaterPlans = emptyList(),
            debts = emptyList(),
            savingsGoals = emptyList(),
            savingsAllocations = emptyList(),
            activeCycle = null,
            commissions = emptyList(),
            nowInstant = nowInstant,
            zoneId = zone,
            budgets = budgets,
            transactions = transactions,
            categories = listOf(catFood, catTransport)
        )
        assertEquals(2, evalAll.dueNowCandidates.size)
        val foodAlert = evalAll.dueNowCandidates.first { it.entityId == 1L }
        val transportAlert = evalAll.dueNowCandidates.first { it.entityId == 2L }
        assertTrue(foodAlert.dedupKey.contains("90"))
        assertTrue(foodAlert.detailedTitle.contains("90%"))
        assertEquals(NotificationDeepLink.PLANNING_BUDGETS, foodAlert.deepLink)
        assertTrue(transportAlert.dedupKey.contains("100"))
        assertTrue(transportAlert.detailedTitle.contains("100%"))
        assertTrue(transportAlert.isOverdue)

        val only80Settings = allThresholdsSettings.copy(enabledBudgetThresholds = setOf(BudgetAlertThreshold.PERCENT_80))
        val eval80 = ReminderCalculator.evaluate(
            settings = only80Settings,
            commitments = emptyList(),
            payLaterPlans = emptyList(),
            debts = emptyList(),
            savingsGoals = emptyList(),
            savingsAllocations = emptyList(),
            activeCycle = null,
            commissions = emptyList(),
            nowInstant = nowInstant,
            zoneId = zone,
            budgets = budgets,
            transactions = transactions,
            categories = listOf(catFood, catTransport)
        )
        assertTrue(eval80.dueNowCandidates.all { it.dedupKey.contains("80") })

        val disabledBudgetsSettings = allThresholdsSettings.copy(enabledCategories = emptySet())
        val evalDisabled = ReminderCalculator.evaluate(
            settings = disabledBudgetsSettings,
            commitments = emptyList(),
            payLaterPlans = emptyList(),
            debts = emptyList(),
            savingsGoals = emptyList(),
            savingsAllocations = emptyList(),
            activeCycle = null,
            commissions = emptyList(),
            nowInstant = nowInstant,
            zoneId = zone,
            budgets = budgets,
            transactions = transactions,
            categories = listOf(catFood, catTransport)
        )
        assertTrue(evalDisabled.dueNowCandidates.isEmpty())
    }

    @Test
    fun oneTapHideShowAmount_togglesGlobalPrivacyAndSyncsWidgetsImmediately() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val secRepo = FakeSecurityRepo(SecurityConfigSnapshot(isAppLockEnabled = false))
            val appLock = createAppLockManager(secRepo, scope)

            assertFalse(appLock.uiState.value.privacySettings.isAnyMaskActive)
            appLock.toggleBalanceVisibilityFromHome()
            assertTrue(secRepo.state.value.hideSensitiveBalances)
            assertTrue(appLock.uiState.value.privacySettings.isAnyMaskActive)

            val snapMasked = WidgetSnapshotCalculator.build(
                wallets = emptyList(), transactions = emptyList(), commitments = emptyList(),
                commissions = emptyList(), savingsAllocations = emptyList(), savingsGoals = emptyList(),
                payLaterPlans = emptyList(), debts = emptyList(), activeCycle = null,
                security = secRepo.state.value, themeMode = ThemeMode.SYSTEM
            )
            assertTrue(snapMasked.shouldMaskAmounts)
            assertEquals(PrivacyMask.MASKED_MYR, snapMasked.formatMoney(150_000L))

            appLock.toggleBalanceVisibilityFromHome()
            assertFalse(secRepo.state.value.hideSensitiveBalances)
            assertFalse(appLock.uiState.value.privacySettings.isAnyMaskActive)

            secRepo.setPrivacyModeEnabled(true)
            secRepo.setHideSensitiveBalances(true)
            assertTrue(appLock.uiState.value.privacySettings.isAnyMaskActive)
            appLock.toggleBalanceVisibilityFromHome()
            assertFalse(appLock.uiState.value.privacySettings.isAnyMaskActive)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun securityAlwaysMaskWidgetAmounts_overridesVisibleInAppBalances() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val secRepo = FakeSecurityRepo(
                SecurityConfigSnapshot(
                    isAppLockEnabled = false,
                    privacyModeEnabled = false,
                    hideSensitiveBalances = false,
                    alwaysMaskWidgetAmounts = true
                )
            )
            val appLock = createAppLockManager(secRepo, scope)
            assertFalse(appLock.uiState.value.privacySettings.isAnyMaskActive)

            val widgetSnap = WidgetSnapshotCalculator.build(
                wallets = emptyList(), transactions = emptyList(), commitments = emptyList(),
                commissions = emptyList(), savingsAllocations = emptyList(), savingsGoals = emptyList(),
                payLaterPlans = emptyList(), debts = emptyList(), activeCycle = null,
                security = secRepo.state.value, themeMode = ThemeMode.SYSTEM
            )
            assertTrue(widgetSnap.shouldMaskAmounts)
            assertEquals(PrivacyMask.MASKED_MYR, widgetSnap.formatMoney(250_000L))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun encryptedBackup_usesUniqueSaltAndIv_andRoundTripsLosslessly() {
        val sampleJson = """{"hello":"duit","amountSen":123456}"""
        val pw = "StrongPass#2026".toCharArray()
        val blob1 = BackupCryptoEngine.encrypt(sampleJson, pw, createdAtEpochMs = 1000L)
        val blob2 = BackupCryptoEngine.encrypt(sampleJson, pw, createdAtEpochMs = 1000L)

        assertFalse(blob1.contentEquals(blob2))

        val hdr1 = BackupCryptoEngine.inspectEnvelope(blob1)
        val hdr2 = BackupCryptoEngine.inspectEnvelope(blob2)
        assertFalse(hdr1.salt.contentEquals(hdr2.salt))
        assertFalse(hdr1.iv.contentEquals(hdr2.iv))
        assertEquals(BackupCryptoEngine.KDF_ALGORITHM, hdr1.kdf)
        assertEquals(BackupCryptoEngine.DEFAULT_ITERATIONS, hdr1.iterations)

        val decrypted = BackupCryptoEngine.decrypt(blob1, pw)
        assertEquals(JSONObject(sampleJson).toString(), JSONObject(decrypted).toString())
    }

    @Test
    fun encryptedBackup_wrongPassword_corruptedFile_andUnsupportedVersion_allFailSafely() {
        val payload = """{"test":"duit-integrity"}"""
        val pw = "CorrectPass99!".toCharArray()
        val encrypted = BackupCryptoEngine.encrypt(payload, pw, dbSchemaVersion = 9, createdAtEpochMs = 5000L)

        try {
            BackupCryptoEngine.decrypt(encrypted, "WrongPass99!".toCharArray())
            fail("Expected BackupAuthOrIntegrityException for wrong password")
        } catch (_: BackupAuthOrIntegrityException) {
            // expected
        }

        val json = JSONObject(String(encrypted, StandardCharsets.UTF_8))
        val origCipher = Base64.getDecoder().decode(json.getString("ciphertextB64"))
        origCipher[origCipher.size / 2] = (origCipher[origCipher.size / 2].toInt() xor 0xFF).toByte()
        json.put("ciphertextB64", Base64.getEncoder().encodeToString(origCipher))
        val tamperedBytes = json.toString().toByteArray(StandardCharsets.UTF_8)
        try {
            BackupCryptoEngine.decrypt(tamperedBytes, pw)
            fail("Expected BackupAuthOrIntegrityException for corrupted ciphertext")
        } catch (_: BackupAuthOrIntegrityException) {
            // expected
        }

        try {
            BackupCryptoEngine.inspectEnvelope("not a valid duit backup".toByteArray(StandardCharsets.UTF_8))
            fail("Expected BackupFormatException")
        } catch (_: BackupFormatException) {
            // expected
        }

        val futureFmtJson = JSONObject(String(encrypted, StandardCharsets.UTF_8)).apply {
            put("formatVersion", 99)
        }.toString().toByteArray(StandardCharsets.UTF_8)
        try {
            BackupCryptoEngine.inspectEnvelope(futureFmtJson)
            fail("Expected BackupVersionException for unsupported formatVersion")
        } catch (_: BackupVersionException) {
            // expected
        }

        val futureSchemaBytes = BackupCryptoEngine.encrypt(payload, pw, dbSchemaVersion = 999, createdAtEpochMs = 5000L)
        try {
            BackupCryptoEngine.inspectEnvelope(futureSchemaBytes)
            fail("Expected BackupVersionException for unsupported dbSchemaVersion")
        } catch (_: BackupVersionException) {
            // expected
        }
    }

    @Test
    fun backupAndRestore_fullEndToEnd_preservesAllEntitiesAndPreferences_withoutOverwritingPin() = runBlocking {
        val dao = db.backupDao()
        val sourceWalletId = 10L
        val savingsWalletId = 11L
        val catId = 20L
        val cycleId = 30L
        val goalId = 80L
        val srcId = 100L
        val commId = 101L

        val seeded = LedgerDatabaseSnapshot(
            wallets = listOf(
                WalletEntity(
                    id = sourceWalletId, name = "CIMB", type = WalletType.BANK, balanceSen = 450_000L,
                    isExcludedFromSafeToSpend = false, accentToken = PrismaticAccentToken.SKY_BLUE,
                    createdAtEpochMs = 100L, updatedAtEpochMs = 100L, isIncludedInTotalBalance = true, isArchived = false
                ),
                WalletEntity(
                    id = savingsWalletId, name = "Tabung Haji", type = WalletType.SAVINGS, balanceSen = 200_000L,
                    isExcludedFromSafeToSpend = true, accentToken = PrismaticAccentToken.AQUA,
                    createdAtEpochMs = 100L, updatedAtEpochMs = 100L, isIncludedInTotalBalance = true, isArchived = false
                )
            ),
            categories = listOf(
                CategoryEntity(
                    id = catId, name = "Dining", type = TransactionType.EXPENSE,
                    iconKey = "tag", accentToken = PrismaticAccentToken.SKY_BLUE, isDefault = false, isArchived = false, sortOrder = 3
                )
            ),
            salaryCycles = listOf(
                SalaryCycleEntity(
                    id = cycleId, label = "June Cycle",
                    startDateEpochDay = LocalDate.of(2026, 6, 1).toEpochDay(),
                    endDateEpochDay = LocalDate.of(2026, 6, 30).toEpochDay(),
                    paydayEpochDay = LocalDate.of(2026, 6, 25).toEpochDay(),
                    baseSalarySen = 500_000L, committedBudgetSen = 100_000L,
                    isActive = true, receivedSalarySen = 500_000L, salaryTransactionId = null, isOpenEnded = false
                )
            ),
            commitments = listOf(
                CommitmentEntity(
                    id = 50L, title = "Fibre Internet", amountSen = 12_900L,
                    dueDateEpochDay = LocalDate.of(2026, 6, 28).toEpochDay(),
                    status = CommitmentStatus.UPCOMING, walletId = sourceWalletId, isRecurring = true,
                    salaryCycleId = cycleId, category = "Utilities", categoryId = catId,
                    frequency = CommitmentFrequency.MONTHLY, customIntervalDays = null, anchorDayOfMonth = 28,
                    paidSen = 0L, nextDueDateEpochDay = null, note = "", isArchived = false,
                    createdAtEpochMs = 100L, updatedAtEpochMs = 100L
                )
            ),
            commissionSources = listOf(
                CommissionSourceEntity(
                    id = srcId, name = "Store Sales", accentToken = PrismaticAccentToken.LAVENDER,
                    isDefault = false, isArchived = false, sortOrder = 1
                )
            ),
            commissions = listOf(
                CommissionEntity(
                    id = commId, title = "May Payout", sourceLabel = "Store Sales", amountSen = 180_000L,
                    status = CommissionStatus.PENDING, earnedDateEpochDay = LocalDate.of(2026, 6, 1).toEpochDay(),
                    payoutDateEpochDay = LocalDate.of(2026, 6, 30).toEpochDay(), linkedTransactionId = null,
                    notes = "", sourceId = srcId, yearMonth = "2026-05", estimatedGrossSen = 180_000L,
                    actualGrossSen = 0L, receivedNetSen = 0L, adjustmentNote = ""
                )
            ),
            commissionPayments = listOf(
                CommissionPaymentEntity(
                    id = 102L, commissionId = commId, sourceId = srcId, sourceName = "Store Sales",
                    walletId = sourceWalletId, linkedTransactionId = null, receivedAtEpochMs = 100L,
                    actualGrossSen = 50_000L, epfDeductionSen = null, socsoDeductionSen = null, eisDeductionSen = null,
                    actualNetSen = 50_000L, notes = "Partial", submissionId = "cp_102"
                )
            ),
            commissionRuleVersions = listOf(
                CommissionRuleVersionEntity(
                    id = 103L, ruleName = "Q2 Rule", effectiveFromEpochDay = LocalDate.of(2026, 6, 1).toEpochDay(),
                    basicSalarySen = 200_000L, minMonthlySalesSen = 200_000L, employerCostRateBps = 1300,
                    standardRateBps = 1000, incentiveBaselineThresholdSen = 300_000L, incentiveRateBps = 200,
                    createdAtEpochMs = 100L
                )
            ),
            dailySalesRecords = listOf(
                DailySalesRecordEntity(
                    id = 104L, recordDateEpochDay = LocalDate.of(2026, 6, 5).toEpochDay(),
                    grossSalesSen = 25_000L, netSalesSen = 25_000L, unitsSold = 4, notes = "Good day",
                    createdAtEpochMs = 100L, grossProfitSen = 12_000L
                )
            ),
            savingsGoals = listOf(
                SavingsGoalEntity(
                    id = goalId, name = "Emergency Fund", targetAmountSen = 1_000_000L,
                    targetDateEpochDay = LocalDate.of(2027, 1, 1).toEpochDay(),
                    description = "Rainy day", iconKey = "shield", accentToken = PrismaticAccentToken.AQUA,
                    status = SavingsGoalStatus.ACTIVE, createdAtEpochMs = 100L, updatedAtEpochMs = 100L
                )
            ),
            savingsAllocations = listOf(
                SavingsAllocationEntity(
                    id = 81L, goalId = goalId, walletId = savingsWalletId, allocatedSen = 150_000L, updatedAtEpochMs = 100L
                )
            ),
            savingsMovements = listOf(
                SavingsMovementEntity(
                    id = 82L, goalId = goalId, sourceWalletId = savingsWalletId, targetWalletId = null,
                    relatedGoalId = null, type = SavingsMovementType.ALLOCATE, amountSen = 150_000L,
                    linkedTransactionId = null, note = "Initial", occurredAtEpochMs = 100L, submissionId = "sm_82"
                )
            ),
            budgets = listOf(
                BudgetEntity(
                    id = 90L, name = "Dining Limit", scope = BudgetScope.CATEGORY,
                    periodType = BudgetPeriodType.MONTHLY, yearMonth = "2026-06", salaryCycleId = cycleId,
                    categoryId = catId, categoryName = "Dining", limitSen = 60_000L,
                    accentToken = PrismaticAccentToken.SKY_BLUE, note = "", isArchived = false,
                    createdAtEpochMs = 100L, updatedAtEpochMs = 100L
                )
            ),
            payLaterPlans = listOf(
                PayLaterPlanEntity(
                    id = 60L, title = "Monitor", providerName = "SPayLater",
                    purchaseAmountSen = 90_000L, feeOrInterestSen = 0L, paidSen = 30_000L,
                    instalmentCount = 3, firstDueDateEpochDay = LocalDate.of(2026, 6, 1).toEpochDay(),
                    nextDueDateEpochDay = LocalDate.of(2026, 7, 1).toEpochDay(),
                    accentToken = PrismaticAccentToken.PEACH, note = "0% promo", isArchived = false,
                    createdAtEpochMs = 100L, updatedAtEpochMs = 100L
                )
            ),
            debts = listOf(
                DebtEntity(
                    id = 70L, direction = DebtDirection.I_OWE, counterpartyName = "Ali",
                    title = "Concert", originalAmountSen = 40_000L, paidSen = 10_000L,
                    dueDateEpochDay = LocalDate.of(2026, 7, 15).toEpochDay(),
                    accentToken = PrismaticAccentToken.SKY_BLUE, note = "", isArchived = false,
                    createdAtEpochMs = 100L, updatedAtEpochMs = 100L, scheduledRepaymentSen = 10_000L
                )
            ),
            obligationPayments = listOf(
                ObligationPaymentEntity(
                    id = 105L, obligationType = ObligationType.DEBT, obligationId = 70L,
                    walletId = sourceWalletId, linkedTransactionId = null, amountSen = 10_000L,
                    occurrenceEpochDay = null, paidAtEpochMs = 100L, note = "Part 1", submissionId = "ob_105"
                )
            ),
            transactions = listOf(
                TransactionEntity(
                    id = 40L, walletId = sourceWalletId, targetWalletId = null, type = TransactionType.EXPENSE,
                    amountSen = 2_550L, category = "Dining", title = "Nasi Lemak, Kopi \"O\"", note = "Morning\nmeal",
                    commitmentId = null, occurredAtEpochMs = 1_750_000_000_000L, createdAtEpochMs = 1_750_000_000_000L,
                    categoryId = catId, submissionId = "tx_backup_1"
                )
            ),
            notificationHistory = listOf(
                NotificationHistoryEntity(
                    id = 106L, dedupKey = "k1", notificationId = 77,
                    category = NotificationCategory.COMMITMENTS.name, entityId = 50L, targetEpochDay = 20_000L,
                    title = "Bill due", message = "Pay soon", amountSen = 12_900L, isOverdue = false, isRead = false,
                    deepLink = NotificationDeepLink.PLANNING_COMMITMENTS.name, deliveredAtEpochMs = 100L
                )
            )
        )
        dao.replaceAllLedgerDataAtomically(seeded)

        val userPrefsRepo = OfflineUserPreferencesRepository(UserPreferencesDataStore(context))
        userPrefsRepo.restoreDefaultPreferences()
        userPrefsRepo.setThemeMode(ThemeMode.DARK)
        userPrefsRepo.setGlassIntensity(GlassIntensity.SUBTLE)
        userPrefsRepo.setDefaultWalletId(sourceWalletId)

        val notifPrefsRepo = FakeNotificationPrefsRepo(
            NotificationSettingsSnapshot(
                masterEnabled = true,
                enabledBudgetThresholds = setOf(BudgetAlertThreshold.PERCENT_90, BudgetAlertThreshold.PERCENT_100),
                reminderHour = 18,
                reminderMinute = 30
            )
        )
        val secRepo1 = FakeSecurityRepo(
            SecurityConfigSnapshot(
                isAppLockEnabled = true,
                encryptedPinEnvelope = "DEVICE_1_PIN_ENVELOPE",
                isBiometricEnabled = true,
                hideSensitiveBalances = true,
                alwaysMaskWidgetAmounts = true
            )
        )
        val coordinator1 = BackupRestoreCoordinator(
            backupDao = dao,
            userPreferencesRepository = userPrefsRepo,
            notificationPreferencesRepository = notifPrefsRepo,
            notificationHistoryRepository = RoomNotificationHistoryRepository(db.notificationHistoryDao()),
            securityRepository = secRepo1,
            reminderScheduler = RecordingReminderScheduler(),
            widgetUpdateCoordinator = RecordingWidgetCoordinator(),
            ioDispatcher = Dispatchers.Unconfined
        )

        val pw = "MyBackup#2026".toCharArray()
        val backupBytes = coordinator1.createEncryptedBackup(pw)
        val originalSnapshot = dao.exportFullSnapshot()

        val txCsv = coordinator1.exportTransactionsCsv()
        assertTrue(txCsv.contains("\"Nasi Lemak, Kopi \"\"O\"\"\""))
        assertTrue(txCsv.contains("\"Morning\nmeal\""))
        val ledgerCsv = coordinator1.exportFullLedgerCsv()
        assertTrue(ledgerCsv.contains("WALLET,10,"))
        assertTrue(ledgerCsv.contains("SAVINGS_GOAL,80,"))

        val db2 = Room.inMemoryDatabaseBuilder(context, DuitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val userPrefsRepo2 = OfflineUserPreferencesRepository(UserPreferencesDataStore(context))
            userPrefsRepo2.restoreDefaultPreferences()
            val notifPrefsRepo2 = FakeNotificationPrefsRepo()
            val secRepo2 = FakeSecurityRepo(
                SecurityConfigSnapshot(
                    isAppLockEnabled = true,
                    encryptedPinEnvelope = "DEVICE_2_PIN_MUST_STAY",
                    isBiometricEnabled = true,
                    hideSensitiveBalances = false,
                    alwaysMaskWidgetAmounts = false
                )
            )
            val scheduler2 = RecordingReminderScheduler()
            val widgetCoord2 = RecordingWidgetCoordinator()
            val coordinator2 = BackupRestoreCoordinator(
                backupDao = db2.backupDao(),
                userPreferencesRepository = userPrefsRepo2,
                notificationPreferencesRepository = notifPrefsRepo2,
                notificationHistoryRepository = RoomNotificationHistoryRepository(db2.notificationHistoryDao()),
                securityRepository = secRepo2,
                reminderScheduler = scheduler2,
                widgetUpdateCoordinator = widgetCoord2,
                ioDispatcher = Dispatchers.Unconfined
            )

            val decryptedBundle = coordinator2.decryptAndPreviewBackup(backupBytes, pw)
            assertEquals(2, decryptedBundle.preview.walletCount)
            assertEquals(1, decryptedBundle.preview.transactionCount)
            assertEquals(1, decryptedBundle.preview.commitmentCount)
            assertEquals(1, decryptedBundle.preview.payLaterPlanCount)
            assertEquals(1, decryptedBundle.preview.debtCount)
            assertEquals(1, decryptedBundle.preview.savingsGoalCount)
            assertEquals(1, decryptedBundle.preview.budgetCount)
            assertEquals(1, decryptedBundle.preview.commissionCount)

            coordinator2.restoreBackupAtomically(decryptedBundle)

            val restoredSnapshot = db2.backupDao().exportFullSnapshot()
            assertEquals(originalSnapshot, restoredSnapshot)

            val restoredAppSettings = userPrefsRepo2.observeAppSettings().first()
            assertEquals(ThemeMode.DARK, restoredAppSettings.themeMode)
            assertEquals(GlassIntensity.SUBTLE, restoredAppSettings.glassIntensity)
            assertEquals(sourceWalletId, restoredAppSettings.defaultWalletId)
            val restoredNotif = notifPrefsRepo2.observeSettings().first()
            assertEquals(18, restoredNotif.reminderHour)
            assertEquals(
                setOf(BudgetAlertThreshold.PERCENT_90, BudgetAlertThreshold.PERCENT_100),
                restoredNotif.enabledBudgetThresholds
            )

            assertEquals("DEVICE_2_PIN_MUST_STAY", secRepo2.state.value.encryptedPinEnvelope)
            assertTrue(secRepo2.state.value.isAppLockEnabled)
            assertTrue(secRepo2.state.value.isBiometricEnabled)
            assertTrue(secRepo2.state.value.hideSensitiveBalances)
            assertTrue(secRepo2.state.value.alwaysMaskWidgetAmounts)
            assertTrue(scheduler2.immediateSyncCount > 0)
            assertTrue(widgetCoord2.immediateRefreshCount > 0)
        } finally {
            db2.close()
        }
    }

    @Test
    fun restoreRollback_onForeignKeyFailure_leavesOriginalDatabaseCompletelyIntact() = runBlocking {
        db.backupDao().insertWallets(
            listOf(
                WalletEntity(
                    id = 1L, name = "Original Cash", type = WalletType.CASH, balanceSen = 77_000L,
                    isExcludedFromSafeToSpend = false, accentToken = PrismaticAccentToken.AQUA,
                    createdAtEpochMs = 1L, updatedAtEpochMs = 1L, isIncludedInTotalBalance = true, isArchived = false
                )
            )
        )

        val brokenLedger = emptySnapshot().copy(
            transactions = listOf(
                TransactionEntity(
                    id = 55L, walletId = 999L, targetWalletId = null, type = TransactionType.EXPENSE,
                    amountSen = 1_000L, category = "Food", title = "Orphan", note = "",
                    commitmentId = null, occurredAtEpochMs = 1L, createdAtEpochMs = 1L, categoryId = null, submissionId = "broken"
                )
            )
        )
        val validJson = BackupSerializer.encodePayload(
            brokenLedger,
            PortablePreferencesBackup(
                AppSettingsSnapshot(),
                NotificationSettingsSnapshot(),
                PortablePrivacyPreferences()
            ),
            createdAtEpochMs = 1000L
        )
        val fakeBytes = BackupCryptoEngine.encrypt(validJson, "Pass1234".toCharArray())
        val hdr = BackupCryptoEngine.inspectEnvelope(fakeBytes)
        val decJson = BackupCryptoEngine.decrypt(fakeBytes, "Pass1234".toCharArray())
        try {
            BackupSerializer.decodePayload(hdr, decJson)
            fail("Expected BackupFormatException for dangling FK")
        } catch (_: BackupFormatException) {
            // expected
        }

        try {
            db.backupDao().replaceAllLedgerDataAtomically(brokenLedger)
            fail("Expected SQLiteConstraintException from Room @Transaction")
        } catch (_: Exception) {
            // expected FK failure
        }

        val walletsAfter = db.backupDao().getAllWallets()
        assertEquals(1, walletsAfter.size)
        assertEquals("Original Cash", walletsAfter.first().name)
        assertEquals(77_000L, walletsAfter.first().balanceSen)
    }

    @Test
    fun deleteAllFinancialData_andRestoreDefaults_preservePinBiometricAndSchema() = runBlocking {
        val wId = 5L
        db.backupDao().insertWallets(
            listOf(
                WalletEntity(
                    id = wId, name = "Bank", type = WalletType.BANK, balanceSen = 50_000L,
                    isExcludedFromSafeToSpend = false, accentToken = PrismaticAccentToken.AQUA,
                    createdAtEpochMs = 1L, updatedAtEpochMs = 1L, isIncludedInTotalBalance = true, isArchived = false
                )
            )
        )
        db.backupDao().insertTransactions(
            listOf(
                TransactionEntity(
                    id = 6L, walletId = wId, targetWalletId = null, type = TransactionType.INCOME,
                    amountSen = 50_000L, category = "Salary", title = "Pay", note = "",
                    commitmentId = null, occurredAtEpochMs = 10L, createdAtEpochMs = 10L,
                    categoryId = null, submissionId = "del_1"
                )
            )
        )
        db.notificationHistoryDao().insertIgnore(
            NotificationHistoryEntity(
                dedupKey = "hist1", notificationId = 1, category = NotificationCategory.COMMITMENTS.name,
                entityId = 1L, targetEpochDay = 10L, title = "Reminder", message = "Due",
                amountSen = 100L, isOverdue = false, isRead = false,
                deepLink = NotificationDeepLink.PLANNING_COMMITMENTS.name, deliveredAtEpochMs = 10L
            )
        )

        val userPrefs = OfflineUserPreferencesRepository(UserPreferencesDataStore(context))
        userPrefs.setThemeMode(ThemeMode.DARK)
        userPrefs.setDefaultWalletId(wId)
        val notifPrefs = FakeNotificationPrefsRepo(
            NotificationSettingsSnapshot(masterEnabled = true, reminderHour = 21, reminderMinute = 0)
        )
        val secRepo = FakeSecurityRepo(
            SecurityConfigSnapshot(
                isAppLockEnabled = true,
                encryptedPinEnvelope = "IMPORTANT_PIN_HASH",
                isBiometricEnabled = true,
                hideSensitiveBalances = true
            )
        )
        val coordinator = BackupRestoreCoordinator(
            backupDao = db.backupDao(),
            userPreferencesRepository = userPrefs,
            notificationPreferencesRepository = notifPrefs,
            notificationHistoryRepository = RoomNotificationHistoryRepository(db.notificationHistoryDao()),
            securityRepository = secRepo,
            reminderScheduler = RecordingReminderScheduler(),
            widgetUpdateCoordinator = RecordingWidgetCoordinator(),
            ioDispatcher = Dispatchers.Unconfined
        )

        coordinator.clearNonEssentialData()
        assertEquals(0, coordinator.loadStorageSummary().notificationHistoryCount)
        assertEquals(1, coordinator.loadStorageSummary().walletCount)

        coordinator.restoreDefaultPreferences()
        assertEquals(ThemeMode.SYSTEM, userPrefs.observeAppSettings().first().themeMode)
        assertFalse(secRepo.state.value.hideSensitiveBalances)
        assertEquals("IMPORTANT_PIN_HASH", secRepo.state.value.encryptedPinEnvelope)
        assertEquals(1, coordinator.loadStorageSummary().transactionCount)

        coordinator.deleteAllFinancialData()
        val summaryAfterDelete = coordinator.loadStorageSummary()
        assertEquals(0, summaryAfterDelete.totalFinancialRecords)
        assertNull(userPrefs.observeAppSettings().first().defaultWalletId)
        assertEquals("IMPORTANT_PIN_HASH", secRepo.state.value.encryptedPinEnvelope)
        assertTrue(secRepo.state.value.isAppLockEnabled)
        assertTrue(secRepo.state.value.isBiometricEnabled)

        db.backupDao().insertWallets(
            listOf(
                WalletEntity(
                    name = "Fresh Start", type = WalletType.CASH, balanceSen = 1_000L,
                    isExcludedFromSafeToSpend = false, accentToken = PrismaticAccentToken.AQUA,
                    createdAtEpochMs = 2L, updatedAtEpochMs = 2L, isIncludedInTotalBalance = true, isArchived = false
                )
            )
        )
        assertEquals(1, coordinator.loadStorageSummary().walletCount)
    }

    private fun emptySnapshot() = LedgerDatabaseSnapshot(
        wallets = emptyList(),
        categories = emptyList(),
        salaryCycles = emptyList(),
        commitments = emptyList(),
        commissionSources = emptyList(),
        commissions = emptyList(),
        commissionPayments = emptyList(),
        commissionRuleVersions = emptyList(),
        dailySalesRecords = emptyList(),
        savingsGoals = emptyList(),
        savingsAllocations = emptyList(),
        savingsMovements = emptyList(),
        budgets = emptyList(),
        payLaterPlans = emptyList(),
        debts = emptyList(),
        obligationPayments = emptyList(),
        transactions = emptyList(),
        notificationHistory = emptyList()
    )
}
