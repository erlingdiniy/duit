package com.diniy.duit.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.room.InvalidationTracker
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.diniy.duit.DuitApplication
import com.diniy.duit.R
import com.diniy.duit.core.currency.MyrFormatter
import com.diniy.duit.core.ui.state.PrivacyMask
import com.diniy.duit.data.local.DuitDatabase
import com.diniy.duit.data.repository.OfflineFirstFinanceRepository
import com.diniy.duit.data.repository.UserPreferencesRepository
import com.diniy.duit.domain.calculator.FinanceSummaryCalculator
import com.diniy.duit.domain.model.AnimationIntensity
import com.diniy.duit.domain.model.AppSettingsSnapshot
import com.diniy.duit.domain.model.BudgetPeriodType
import com.diniy.duit.domain.model.CommissionFormulaConfig
import com.diniy.duit.domain.model.DashboardSectionKey
import com.diniy.duit.domain.model.GlassIntensity
import com.diniy.duit.domain.model.CommissionRecord
import com.diniy.duit.domain.model.CommissionStatus
import com.diniy.duit.domain.model.Commitment
import com.diniy.duit.domain.model.CommitmentFrequency
import com.diniy.duit.domain.model.CommitmentStatus
import com.diniy.duit.domain.model.DebtDirection
import com.diniy.duit.domain.model.DebtRecord
import com.diniy.duit.domain.model.FinanceTransaction
import com.diniy.duit.domain.model.PayLaterPlan
import com.diniy.duit.domain.model.PrismaticAccentToken
import com.diniy.duit.domain.model.SalaryCycle
import com.diniy.duit.domain.model.SavingsAllocation
import com.diniy.duit.domain.model.SavingsGoal
import com.diniy.duit.domain.model.SavingsGoalStatus
import com.diniy.duit.domain.model.ThemeMode
import com.diniy.duit.domain.model.TransactionType
import com.diniy.duit.domain.model.Wallet
import com.diniy.duit.domain.model.WalletType
import com.diniy.duit.security.AppLockManager
import com.diniy.duit.security.AutoLockTimeout
import com.diniy.duit.security.BiometricAuthGateway
import com.diniy.duit.security.BiometricAuthResult
import com.diniy.duit.security.BiometricAvailability
import com.diniy.duit.security.PinHashEngine
import com.diniy.duit.security.SecretProtector
import com.diniy.duit.security.SecurityConfigSnapshot
import com.diniy.duit.security.SecurityRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase5CHomeWidgetsTest {

    private lateinit var context: Context
    private lateinit var db: DuitDatabase
    private lateinit var financeRepo: OfflineFirstFinanceRepository

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
            state.value = state.value.copy(encryptedPinEnvelope = null, isAppLockEnabled = false)
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
            state.value = state.value.copy(hideSensitiveBalances = false, privacyModeEnabled = false, alwaysMaskWidgetAmounts = false)
        }
        override suspend fun recordFailedAttempt(failedAttempts: Int, lockoutUntilEpochMs: Long) = Unit
        override suspend fun clearFailedAttempts() = Unit
        override suspend fun recordBackgroundedAt(epochMs: Long) = Unit
        override suspend fun clearAllSecurityState() {
            state.value = SecurityConfigSnapshot()
        }
    }

    private class FakeUserPrefsRepo(
        initial: ThemeMode = ThemeMode.SYSTEM
    ) : UserPreferencesRepository {
        val settingsFlow = MutableStateFlow(AppSettingsSnapshot(themeMode = initial))
        override fun observeAppSettings(): Flow<AppSettingsSnapshot> = settingsFlow.asStateFlow()
        override fun observeThemeMode(): Flow<ThemeMode> = MutableStateFlow(settingsFlow.value.themeMode)
        override fun observeCommissionFormulaConfig(): Flow<CommissionFormulaConfig> = MutableStateFlow(settingsFlow.value.commissionFormulaConfig)
        override suspend fun setThemeMode(mode: ThemeMode) { settingsFlow.value = settingsFlow.value.copy(themeMode = mode) }
        override suspend fun setGlassIntensity(intensity: GlassIntensity) { settingsFlow.value = settingsFlow.value.copy(glassIntensity = intensity) }
        override suspend fun setHapticFeedbackEnabled(enabled: Boolean) { settingsFlow.value = settingsFlow.value.copy(hapticFeedbackEnabled = enabled) }
        override suspend fun setReduceMotion(enabled: Boolean) { settingsFlow.value = settingsFlow.value.copy(reduceMotion = enabled) }
        override suspend fun setAnimationIntensity(intensity: AnimationIntensity) { settingsFlow.value = settingsFlow.value.copy(animationIntensity = intensity) }
        override suspend fun setDashboardSectionVisible(section: DashboardSectionKey, visible: Boolean) {
            val next = if (visible) settingsFlow.value.hiddenDashboardSections - section else settingsFlow.value.hiddenDashboardSections + section
            settingsFlow.value = settingsFlow.value.copy(hiddenDashboardSections = next)
        }
        override suspend fun setDashboardSectionOrder(order: List<DashboardSectionKey>) { settingsFlow.value = settingsFlow.value.copy(dashboardSectionOrder = order) }
        override suspend fun restoreDefaultDashboardLayout() {
            settingsFlow.value = settingsFlow.value.copy(hiddenDashboardSections = emptySet(), dashboardSectionOrder = DashboardSectionKey.entries.toList())
        }
        override suspend fun setDefaultWalletId(walletId: Long?) { settingsFlow.value = settingsFlow.value.copy(defaultWalletId = walletId) }
        override suspend fun setDefaultBudgetPeriodType(periodType: BudgetPeriodType) { settingsFlow.value = settingsFlow.value.copy(defaultBudgetPeriodType = periodType) }
        override suspend fun setAutoAdvanceRecurringCommitments(enabled: Boolean) { settingsFlow.value = settingsFlow.value.copy(autoAdvanceRecurringCommitments = enabled) }
        override suspend fun setCommissionTrackingEnabled(enabled: Boolean) { settingsFlow.value = settingsFlow.value.copy(commissionTrackingEnabled = enabled) }
        override suspend fun saveCommissionFormulaConfig(config: CommissionFormulaConfig) { settingsFlow.value = settingsFlow.value.copy(commissionFormulaConfig = config) }
        override suspend fun resetCommissionFormulaConfig() { settingsFlow.value = settingsFlow.value.copy(commissionFormulaConfig = CommissionFormulaConfig()) }
        override suspend fun applyPortableSettings(snapshot: AppSettingsSnapshot) { settingsFlow.value = snapshot }
        override suspend fun restoreDefaultPreferences() { settingsFlow.value = AppSettingsSnapshot() }
    }

    private val today = LocalDate.of(2026, 11, 16)

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, DuitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        financeRepo = OfflineFirstFinanceRepository(db)
    }

    @After
    fun teardown() {
        db.close()
    }

    // 1 & 2. Balance widget calculations + cycle-aware Safe to Spend accuracy + transfers/commissions exclusion
    @Test
    fun balanceWidgetCalculations_matchFinanceSummaryCalculatorAndExcludeOutOfCycleAndPendingCommissions() {
        val cycle = SalaryCycle(
            id = 1L,
            label = "Nov Cycle",
            startDateEpochDay = LocalDate.of(2026, 11, 1).toEpochDay(),
            endDateEpochDay = LocalDate.of(2026, 11, 30).toEpochDay(),
            paydayEpochDay = LocalDate.of(2026, 11, 25).toEpochDay(),
            baseSalarySen = 400_000L,
            committedBudgetSen = 100_000L,
            isActive = true,
            isOpenEnded = false
        )
        val wallets = listOf(
            Wallet(1L, "Main", WalletType.BANK, 250_000L, false, PrismaticAccentToken.AQUA, 1L, 1L),
            Wallet(2L, "Vault", WalletType.SAVINGS, 100_000L, true, PrismaticAccentToken.LAVENDER, 1L, 1L)
        )
        val transactions = listOf(
            FinanceTransaction(1L, 1L, 2L, TransactionType.TRANSFER, 50_000L, "Transfer", "To Vault", "", null, 1000L, 1000L),
            FinanceTransaction(2L, 1L, null, TransactionType.EXPENSE, 12_000L, "Food", "Lunch", "", null, 2000L, 2000L)
        )
        val commitments = listOf(
            Commitment(1L, "Maxis Fibre", 10_000L, LocalDate.of(2026, 11, 25).toEpochDay(), CommitmentStatus.UPCOMING, null, true, 1L),
            Commitment(2L, "Next Month Fibre", 10_000L, LocalDate.of(2026, 12, 25).toEpochDay(), CommitmentStatus.UPCOMING, null, true, 1L)
        )
        val commissions = listOf(
            CommissionRecord(1L, "Pending Bonus", "Client", 80_000L, CommissionStatus.PENDING, today.toEpochDay(), null, null, "")
        )

        val expected = FinanceSummaryCalculator.calculate(
            wallets = wallets,
            transactions = transactions,
            commitments = commitments,
            commissions = commissions,
            activeCycle = cycle,
            today = today
        )
        val snapshot = WidgetSnapshotCalculator.build(
            wallets = wallets,
            transactions = transactions,
            commitments = commitments,
            commissions = commissions,
            savingsAllocations = emptyList(),
            savingsGoals = emptyList(),
            payLaterPlans = emptyList(),
            debts = emptyList(),
            activeCycle = cycle,
            security = SecurityConfigSnapshot(),
            themeMode = ThemeMode.SYSTEM,
            today = today
        )

        assertEquals(expected.safeToSpendSen, snapshot.balance.safeToSpendSen)
        assertEquals(240_000L, snapshot.balance.safeToSpendSen) // 250k liquid - 10k in-cycle (Dec bill excluded)
        assertEquals(350_000L, snapshot.balance.totalBalanceSen) // pending 80k commission excluded
        assertEquals(100_000L, snapshot.balance.protectedSavingsSen)
        assertEquals(10_000L, snapshot.balance.currentCycleReservedSen)
        assertEquals(10_000L, snapshot.balance.upcomingFutureCommitmentsSen)
        assertEquals(0L, snapshot.balance.cycleIncomeSen) // internal transfer not counted as income
        assertEquals(12_000L, snapshot.balance.cycleExpenseSen) // internal transfer not counted as expense
        assertEquals("Nov Cycle", snapshot.balance.cycleLabel)
    }

    // 3 & 4. Upcoming commitments + PayLater next instalment (not full outstanding) + scheduled vs unscheduled debts
    @Test
    fun upcomingPaymentsWidget_prioritizesOverdue_usesNextPayLaterInstalment_andExcludesUnscheduledOrPaid() {
        val cycle = SalaryCycle(
            id = 1L,
            label = "Nov 2026",
            startDateEpochDay = LocalDate.of(2026, 11, 1).toEpochDay(),
            endDateEpochDay = LocalDate.of(2026, 11, 30).toEpochDay(),
            paydayEpochDay = LocalDate.of(2026, 11, 25).toEpochDay(),
            baseSalarySen = 300_000L,
            committedBudgetSen = 50_000L,
            isActive = true,
            isOpenEnded = false
        )
        val commitments = listOf(
            Commitment(1L, "Overdue Water", 3_500L, LocalDate.of(2026, 11, 10).toEpochDay(), CommitmentStatus.UPCOMING, null, true, 1L),
            Commitment(2L, "Paid Electric", 8_000L, LocalDate.of(2026, 11, 18).toEpochDay(), CommitmentStatus.PAID, 1L, true, 1L),
            Commitment(3L, "Archived Bill", 9_000L, LocalDate.of(2026, 11, 19).toEpochDay(), CommitmentStatus.UPCOMING, null, true, 1L, isArchived = true),
            Commitment(4L, "Dec Internet", 10_000L, LocalDate.of(2026, 12, 20).toEpochDay(), CommitmentStatus.UPCOMING, null, true, 1L)
        )
        val payLater = listOf(
            PayLaterPlan(
                id = 10L,
                title = "Headphones",
                providerName = "Atome",
                purchaseAmountSen = 60_000L,
                feeOrInterestSen = 0L,
                paidSen = 20_000L, // 40,000 remaining across 2 instalments => 20,000 next
                instalmentCount = 3,
                firstDueDateEpochDay = LocalDate.of(2026, 10, 20).toEpochDay(),
                nextDueDateEpochDay = LocalDate.of(2026, 11, 20).toEpochDay(),
                accentToken = PrismaticAccentToken.PEACH,
                note = "",
                isArchived = false,
                createdAtEpochMs = 1L,
                updatedAtEpochMs = 1L
            )
        )
        val debts = listOf(
            DebtRecord(20L, DebtDirection.I_OWE, "Ali", "Scheduled Loan", 50_000L, 10_000L, LocalDate.of(2026, 11, 22).toEpochDay(), PrismaticAccentToken.SKY_BLUE, "", false, 1L, 1L, scheduledRepaymentSen = 15_000L),
            DebtRecord(21L, DebtDirection.I_OWE, "Bala", "Unscheduled Debt", 30_000L, 0L, null, PrismaticAccentToken.SKY_BLUE, "", false, 1L, 1L),
            DebtRecord(22L, DebtDirection.OWED_TO_ME, "Chan", "Receivable", 25_000L, 0L, LocalDate.of(2026, 11, 21).toEpochDay(), PrismaticAccentToken.AQUA, "", false, 1L, 1L)
        )

        val snapshot = WidgetSnapshotCalculator.build(
            wallets = emptyList(),
            transactions = emptyList(),
            commitments = commitments,
            commissions = emptyList(),
            savingsAllocations = emptyList(),
            savingsGoals = emptyList(),
            payLaterPlans = payLater,
            debts = debts,
            activeCycle = cycle,
            security = SecurityConfigSnapshot(),
            themeMode = ThemeMode.SYSTEM,
            today = today
        )

        val items = snapshot.upcomingPayments.items
        assertEquals(4, items.size) // Overdue Water, Atome, Ali Scheduled, Dec Internet (paid/archived/unscheduled/receivable excluded)
        assertEquals("Overdue Water", items[0].title)
        assertTrue(items[0].isOverdue)
        val atomeItem = items.first { it.kind == WidgetObligationKind.PAYLATER_INSTALMENT }
        // Must show next instalment (200.00), NOT full 400.00 outstanding!
        assertEquals(20_000L, atomeItem.amountSen)
        assertTrue(atomeItem.statusLabel.contains("2 left"))
        assertEquals(38_500L, snapshot.upcomingPayments.currentCycleReservedSen) // 3,500 + 20,000 + 15,000
        assertEquals(10_000L, snapshot.upcomingPayments.upcomingFutureBillsSen)
    }

    // 5. Savings goal progress calculations & no leakage of unlinked vault funds
    @Test
    fun savingsGoalsWidget_tracksAllocatedFundsOnlyAndExcludesUnlinkedVaultFundsFromGoals() {
        val wallets = listOf(
            Wallet(1L, "Vault", WalletType.SAVINGS, 150_000L, true, PrismaticAccentToken.LAVENDER, 1L, 1L)
        )
        val goals = listOf(
            SavingsGoal(1L, "Emergency Fund", 100_000L, LocalDate.of(2026, 12, 31).toEpochDay(), "", "shield", PrismaticAccentToken.AQUA, SavingsGoalStatus.ACTIVE, 1L, 1L),
            SavingsGoal(2L, "Old Goal", 50_000L, null, "", "home", PrismaticAccentToken.SKY_BLUE, SavingsGoalStatus.COMPLETED, 1L, 1L)
        )
        val allocs = listOf(
            SavingsAllocation(1L, goalId = 1L, walletId = 1L, allocatedSen = 40_000L, updatedAtEpochMs = 1L)
        )

        val snapshot = WidgetSnapshotCalculator.build(
            wallets = wallets,
            transactions = emptyList(),
            commitments = emptyList(),
            commissions = emptyList(),
            savingsAllocations = allocs,
            savingsGoals = goals,
            payLaterPlans = emptyList(),
            debts = emptyList(),
            activeCycle = null,
            security = SecurityConfigSnapshot(),
            themeMode = ThemeMode.LIGHT,
            today = today
        )

        assertEquals(1, snapshot.savingsGoals.goals.size)
        val g = snapshot.savingsGoals.goals.single()
        assertEquals(40_000L, g.savedSen) // Only allocated 40k, NOT 150k total vault!
        assertEquals(60_000L, g.remainingSen)
        assertEquals("40%", g.progressPercentDisplay)
        assertEquals(150_000L, snapshot.savingsGoals.totalProtectedSavingsSen)
        assertEquals(110_000L, snapshot.savingsGoals.unallocatedProtectedWalletSen)
    }

    // 6. Privacy masking (PrivacyMode, HideSensitiveBalances, AppLock default masking, accessibility safety)
    @Test
    fun privacyMasking_masksAmountsAndAccessibilityLabelsAcrossAllPrivacyAndAppLockStates() {
        val wallets = listOf(Wallet(1L, "Main", WalletType.BANK, 203_800L, false, PrismaticAccentToken.AQUA, 1L, 1L))
        val goals = listOf(SavingsGoal(1L, "Emergency", 100_000L, null, "", "shield", PrismaticAccentToken.AQUA, SavingsGoalStatus.ACTIVE, 1L, 1L))
        val allocs = listOf(SavingsAllocation(1L, 1L, 1L, 50_000L, 1L))

        val unmasked = WidgetSnapshotCalculator.build(
            wallets, emptyList(), emptyList(), emptyList(), allocs, goals, emptyList(), emptyList(), null,
            SecurityConfigSnapshot(), ThemeMode.SYSTEM, today
        )
        assertFalse(unmasked.shouldMaskAmounts)
        assertEquals(MyrFormatter.format(203_800L), unmasked.formatMoney(203_800L))
        assertEquals("50%", unmasked.formatPercent("50%"))

        listOf(
            SecurityConfigSnapshot(hideSensitiveBalances = true),
            SecurityConfigSnapshot(privacyModeEnabled = true),
            SecurityConfigSnapshot(isAppLockEnabled = true, encryptedPinEnvelope = "sealed")
        ).forEach { sec ->
            val masked = WidgetSnapshotCalculator.build(
                wallets, emptyList(), emptyList(), emptyList(), allocs, goals, emptyList(), emptyList(), null,
                sec, ThemeMode.DARK, today
            )
            assertTrue(masked.shouldMaskAmounts)
            assertEquals(PrivacyMask.MASKED_MYR, masked.formatMoney(203_800L))
            assertEquals("••%", masked.formatPercent("50%"))
            assertFalse(masked.accessibleMoney(203_800L).contains("2,038"))
            assertEquals("hidden for privacy", masked.accessibleMoney(203_800L))
        }
    }

    // 7. App Lock deep-link protection & non-colliding Quick Add PendingIntent intents
    @Test
    fun widgetDeepLinkAndAppLock_preservesDistinctActionsAndRequiresUnlockWhenLocked() {
        val expIntent = WidgetDeepLink.createLaunchIntent(context, WidgetDeepLink.QUICK_ADD_EXPENSE)
        val incIntent = WidgetDeepLink.createLaunchIntent(context, WidgetDeepLink.QUICK_ADD_INCOME)
        val trfIntent = WidgetDeepLink.createLaunchIntent(context, WidgetDeepLink.QUICK_ADD_TRANSFER)

        assertNotEquals(expIntent.action, incIntent.action)
        assertNotEquals(incIntent.action, trfIntent.action)
        assertFalse(expIntent.filterEquals(incIntent))
        assertEquals(WidgetDeepLink.QUICK_ADD_EXPENSE, WidgetDeepLink.extract(expIntent))
        assertEquals(WidgetDeepLink.QUICK_ADD_INCOME, WidgetDeepLink.extract(incIntent))
        assertEquals(WidgetDeepLink.QUICK_ADD_TRANSFER, WidgetDeepLink.extract(trfIntent))
        assertEquals(TransactionType.TRANSFER, WidgetDeepLink.QUICK_ADD_TRANSFER.toQuickAddTransactionType())

        val app = ApplicationProvider.getApplicationContext<DuitApplication>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val secRepo = FakeSecurityRepo(SecurityConfigSnapshot(isAppLockEnabled = true, encryptedPinEnvelope = "pin::123456"))
        val pinEngine = PinHashEngine(
            object : SecretProtector {
                override fun seal(plaintext: String): String = "pin::$plaintext"
                override fun unseal(ciphertext: String): String? = ciphertext.removePrefix("pin::")
            }
        )
        val lockManager = AppLockManager(
            securityRepository = secRepo,
            pinHashEngine = pinEngine,
            biometricGateway = object : BiometricAuthGateway {
                override fun checkAvailability() = BiometricAvailability.UNAVAILABLE
                override fun authenticate(
                    activity: androidx.fragment.app.FragmentActivity,
                    title: String,
                    subtitle: String,
                    negativeButtonText: String,
                    onResult: (BiometricAuthResult) -> Unit
                ) = Unit
            },
            scope = scope,
            onFactoryResetFinancialData = {}
        )

        app.container.setPendingWidgetDeepLink(WidgetDeepLink.QUICK_ADD_EXPENSE)
        assertTrue(lockManager.uiState.value.isLocked)
        assertEquals(WidgetDeepLink.QUICK_ADD_EXPENSE, app.container.pendingWidgetDeepLink.value)
        app.container.setPendingWidgetDeepLink(null)
        scope.cancel()
    }

    // 8. Live repository mutations + InvalidationTracker triggers + privacy/theme updates
    @Test
    fun widgetDataProvider_reflectsRepositoryMutationsInvalidationsPrivacyAndThemeChanges(): Unit = runBlocking {
        val secRepo = FakeSecurityRepo()
        val prefsRepo = FakeUserPrefsRepo(ThemeMode.LIGHT)
        val provider = WidgetDataProvider(
            walletRepository = financeRepo,
            transactionRepository = financeRepo,
            planningRepository = financeRepo,
            commissionRepository = financeRepo,
            savingsRepository = financeRepo,
            obligationRepository = financeRepo,
            securityRepository = secRepo,
            userPreferencesRepository = prefsRepo
        )

        val invalidatedTables = mutableSetOf<String>()
        val observer = object : InvalidationTracker.Observer(arrayOf("wallets", "transactions", "commitments")) {
            override fun onInvalidated(tables: Set<String>) {
                invalidatedTables.addAll(tables)
            }
        }
        db.invalidationTracker.addObserver(observer)

        val walletId = financeRepo.upsertWallet(
            Wallet(0L, "MAE", WalletType.BANK, 100_000L, false, PrismaticAccentToken.AQUA, 1L, 1L)
        )
        financeRepo.recordTransaction(
            FinanceTransaction(0L, walletId, null, TransactionType.EXPENSE, 15_000L, "Food", "Dinner", "", null, 10L, 10L)
        )

        val afterTx = provider.loadSnapshot()
        assertEquals(85_000L, afterTx.balance.safeToSpendSen)
        assertTrue("wallets" in invalidatedTables)
        assertTrue("transactions" in invalidatedTables)
        assertEquals(ThemeMode.LIGHT, afterTx.themeMode)

        secRepo.setHideSensitiveBalances(true)
        prefsRepo.setThemeMode(ThemeMode.DARK)
        val afterPrivacyTheme = provider.loadSnapshot()
        assertTrue(afterPrivacyTheme.shouldMaskAmounts)
        assertEquals(ThemeMode.DARK, afterPrivacyTheme.themeMode)
        assertEquals(PrivacyMask.MASKED_MYR, afterPrivacyTheme.formatMoney(afterPrivacyTheme.balance.safeToSpendSen))

        db.invalidationTracker.removeObserver(observer)
    }

    // 9, 10, 11, 12, 15. Compact, Medium, Expanded & Intermediate size layout selection
    @Test
    fun widgetLayoutPolicy_adaptsContentStructureAcrossCompactMediumExpandedAndIntermediateSizes() {
        // Compact 2x2
        val balCompact = WidgetLayoutPolicy.resolve(110f, 110f, DuitWidgetKind.BALANCE)
        assertEquals(WidgetLayoutTier.COMPACT, balCompact.tier)
        assertFalse(balCompact.showSecondaryMetrics)
        assertTrue(balCompact.useCompactLabels)

        // Intermediate 3x2 (e.g. 185x120)
        val upgIntermediate = WidgetLayoutPolicy.resolve(185f, 120f, DuitWidgetKind.UPCOMING_PAYMENTS)
        assertEquals(WidgetLayoutTier.COMPACT, upgIntermediate.tier)
        assertEquals(1, upgIntermediate.maxItems)

        // Intermediate narrow-tall (140x176) shows 2 items cleanly without clipping
        val upgNarrowTall = WidgetLayoutPolicy.resolve(140f, 176f, DuitWidgetKind.UPCOMING_PAYMENTS)
        assertEquals(2, upgNarrowTall.maxItems)
        assertTrue(upgNarrowTall.showSecondaryMetrics)

        // Medium 4x2 (250x110)
        val balMedium = WidgetLayoutPolicy.resolve(250f, 110f, DuitWidgetKind.BALANCE)
        val upgMedium = WidgetLayoutPolicy.resolve(250f, 110f, DuitWidgetKind.UPCOMING_PAYMENTS)
        val qaMedium = WidgetLayoutPolicy.resolve(250f, 70f, DuitWidgetKind.QUICK_ADD)
        assertEquals(WidgetLayoutTier.MEDIUM, balMedium.tier)
        assertEquals(3, upgMedium.maxItems)
        assertEquals(WidgetLayoutTier.MEDIUM, qaMedium.tier)
        assertEquals(3, qaMedium.maxItems)

        // Expanded 4x3 / 4x4
        val savExpanded = WidgetLayoutPolicy.resolve(260f, 225f, DuitWidgetKind.SAVINGS_GOALS)
        val qaExpanded = WidgetLayoutPolicy.resolve(260f, 190f, DuitWidgetKind.QUICK_ADD)
        assertEquals(WidgetLayoutTier.EXPANDED, savExpanded.tier)
        assertEquals(4, savExpanded.maxItems)
        assertEquals(WidgetLayoutTier.EXPANDED, qaExpanded.tier)
        assertEquals(6, qaExpanded.maxItems)
    }

    // 13 & 14. Empty states & deleted records
    @Test
    fun emptyAndDeletedStates_returnSafeNonNegativeDefaults() {
        val emptySnapshot = WidgetSnapshotCalculator.build(
            wallets = emptyList(),
            transactions = emptyList(),
            commitments = emptyList(),
            commissions = emptyList(),
            savingsAllocations = emptyList(),
            savingsGoals = emptyList(),
            payLaterPlans = emptyList(),
            debts = emptyList(),
            activeCycle = null,
            security = SecurityConfigSnapshot(),
            themeMode = ThemeMode.SYSTEM,
            today = today
        )
        assertTrue(emptySnapshot.balance.isEmptyLedger)
        assertEquals(0L, emptySnapshot.balance.safeToSpendSen)
        assertTrue(emptySnapshot.upcomingPayments.items.isEmpty())
        assertTrue(emptySnapshot.savingsGoals.goals.isEmpty())
        assertFalse(emptySnapshot.quickAdd.hasActiveWallets)
    }

    // 16 & 17. Theme resolution + Manifest AppWidgetProvider registration & widget recreation
    @Test
    fun widgetThemesAndManifestProviders_resolveForLightDarkSystemAndAllFourReceivers() {
        assertEquals(R.drawable.bg_widget_surface_light, ResolvedWidgetTheme.forThemeMode(ThemeMode.LIGHT).surfaceBgRes)
        assertEquals(R.drawable.bg_widget_surface_dark, ResolvedWidgetTheme.forThemeMode(ThemeMode.DARK).surfaceBgRes)
        assertEquals(R.drawable.bg_widget_surface_auto, ResolvedWidgetTheme.forThemeMode(ThemeMode.SYSTEM).surfaceBgRes)

        val pm = context.packageManager
        val receivers = pm.queryBroadcastReceivers(
            Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).setPackage(context.packageName),
            PackageManager.GET_META_DATA
        )
        val providerClasses = receivers.map { it.activityInfo.name }.toSet()

        assertTrue(DuitBalanceWidgetReceiver::class.java.name in providerClasses)
        assertTrue(DuitUpcomingPaymentsWidgetReceiver::class.java.name in providerClasses)
        assertTrue(DuitSavingsGoalsWidgetReceiver::class.java.name in providerClasses)
        assertTrue(DuitQuickAddWidgetReceiver::class.java.name in providerClasses)

        // All four providers must declare android.appwidget.provider metadata with horizontal|vertical resizeMode.
        val expectedXmlByClass = mapOf(
            DuitBalanceWidgetReceiver::class.java.name to R.xml.duit_balance_widget_info,
            DuitUpcomingPaymentsWidgetReceiver::class.java.name to R.xml.duit_upcoming_widget_info,
            DuitSavingsGoalsWidgetReceiver::class.java.name to R.xml.duit_savings_widget_info,
            DuitQuickAddWidgetReceiver::class.java.name to R.xml.duit_quick_add_widget_info
        )
        receivers.forEach { resolveInfo ->
            val cls = resolveInfo.activityInfo.name
            val expectedXml = expectedXmlByClass[cls]
            if (expectedXml != null) {
                assertEquals(expectedXml, resolveInfo.activityInfo.metaData.getInt(AppWidgetManager.META_DATA_APPWIDGET_PROVIDER))
                val parser = context.resources.getXml(expectedXml)
                var resizeModeAttr: String? = null
                var event = parser.eventType
                while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                    if (event == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "appwidget-provider") {
                        resizeModeAttr = parser.getAttributeValue("http://schemas.android.com/apk/res/android", "resizeMode")
                    }
                    event = parser.next()
                }
                parser.close()
                // android:resizeMode="horizontal|vertical" compiles to bitmask 0x3 ("3") or literal flag string.
                assertTrue("Expected both horizontal & vertical resize on $cls, got $resizeModeAttr", resizeModeAttr == "3" || resizeModeAttr == "0x3" || (resizeModeAttr?.contains("horizontal") == true && resizeModeAttr.contains("vertical")))
            }
        }

        // Verify receiver recreation instantiates cleanly.
        // Safe to Spend (Phase 11N recovery) lays out against the real cell size.
        assertEquals(androidx.glance.appwidget.SizeMode.Exact, DuitBalanceWidgetReceiver().glanceAppWidget.sizeMode)
        assertEquals(androidx.glance.appwidget.SizeMode.Exact, DuitUpcomingPaymentsWidgetReceiver().glanceAppWidget.sizeMode)
        assertEquals(androidx.glance.appwidget.SizeMode.Exact, DuitSavingsGoalsWidgetReceiver().glanceAppWidget.sizeMode)
        assertEquals(androidx.glance.appwidget.SizeMode.Exact, DuitQuickAddWidgetReceiver().glanceAppWidget.sizeMode)
        assertTrue(ComponentName(context, DuitQuickAddWidgetReceiver::class.java).className.isNotBlank())
    }
}
