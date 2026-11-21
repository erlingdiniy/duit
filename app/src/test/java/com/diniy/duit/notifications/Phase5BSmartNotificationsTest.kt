package com.diniy.duit.notifications

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.diniy.duit.MainActivity
import com.diniy.duit.data.local.DuitDatabase
import com.diniy.duit.data.repository.OfflineFirstFinanceRepository
import com.diniy.duit.domain.calculator.ReminderCalculator
import com.diniy.duit.domain.calculator.ReminderCandidate
import com.diniy.duit.domain.model.CommissionRecord
import com.diniy.duit.domain.model.CommissionStatus
import com.diniy.duit.domain.model.Commitment
import com.diniy.duit.domain.model.CommitmentFrequency
import com.diniy.duit.domain.model.CommitmentStatus
import com.diniy.duit.domain.model.DebtDirection
import com.diniy.duit.domain.model.DebtRecord
import com.diniy.duit.domain.model.PayLaterPlan
import com.diniy.duit.domain.model.PrismaticAccentToken
import com.diniy.duit.domain.model.SalaryCycle
import com.diniy.duit.domain.model.SavingsGoal
import com.diniy.duit.domain.model.SavingsGoalStatus
import com.diniy.duit.domain.model.Wallet
import com.diniy.duit.domain.model.WalletType
import com.diniy.duit.feature.notifications.NotificationViewModel
import com.diniy.duit.feature.notifications.TestNotificationStatus
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase5BSmartNotificationsTest {

    private lateinit var context: Context
    private lateinit var db: DuitDatabase
    private lateinit var financeRepo: OfflineFirstFinanceRepository
    private lateinit var historyRepo: RoomNotificationHistoryRepository
    private val klZone: ZoneId = ZoneId.of("Asia/Kuala_Lumpur")

    private class FakeNotifPrefsRepo(
        initial: NotificationSettingsSnapshot = NotificationSettingsSnapshot()
    ) : NotificationPreferencesRepository {
        private val flow = MutableStateFlow(initial)
        override fun observeSettings(): Flow<NotificationSettingsSnapshot> = flow.asStateFlow()
        override suspend fun setMasterEnabled(enabled: Boolean) { flow.value = flow.value.copy(masterEnabled = enabled) }
        override suspend fun setCategoryEnabled(category: NotificationCategory, enabled: Boolean) {
            val next = if (enabled) flow.value.enabledCategories + category else flow.value.enabledCategories - category
            flow.value = flow.value.copy(enabledCategories = next)
        }
        override suspend fun setOffsetEnabled(offset: ReminderOffset, enabled: Boolean) {
            val next = if (enabled) flow.value.enabledOffsets + offset else flow.value.enabledOffsets - offset
            flow.value = flow.value.copy(enabledOffsets = next)
        }
        override suspend fun setBudgetThresholdEnabled(threshold: BudgetAlertThreshold, enabled: Boolean) {
            val next = if (enabled) flow.value.enabledBudgetThresholds + threshold else flow.value.enabledBudgetThresholds - threshold
            flow.value = flow.value.copy(enabledBudgetThresholds = next)
        }
        override suspend fun setOverdueEnabled(enabled: Boolean) { flow.value = flow.value.copy(overdueEnabled = enabled) }
        override suspend fun setReminderTime(hour: Int, minute: Int) {
            flow.value = flow.value.copy(reminderHour = hour, reminderMinute = minute)
        }
        override suspend fun setHideLockScreenAmounts(hide: Boolean) {
            flow.value = flow.value.copy(hideLockScreenAmounts = hide)
        }
        override suspend fun applyPortableSettings(snapshot: NotificationSettingsSnapshot) {
            flow.value = snapshot
        }
        override suspend fun resetDefaults() { flow.value = NotificationSettingsSnapshot() }
    }

    private class FakeSecurityRepo(
        initial: SecurityConfigSnapshot = SecurityConfigSnapshot()
    ) : SecurityRepository {
        private val flow = MutableStateFlow(initial)
        override fun observeSecurityConfig(): Flow<SecurityConfigSnapshot> = flow.asStateFlow()
        override suspend fun savePinAndEnableAppLock(encryptedPinEnvelope: String) = Unit
        override suspend fun updatePinEnvelope(encryptedPinEnvelope: String) = Unit
        override suspend fun disableAppLock() = Unit
        override suspend fun setBiometricEnabled(enabled: Boolean) = Unit
        override suspend fun setAutoLockTimeout(timeout: AutoLockTimeout) = Unit
        override suspend fun setHideSensitiveBalances(hidden: Boolean) {
            flow.value = flow.value.copy(hideSensitiveBalances = hidden)
        }
        override suspend fun setPrivacyModeEnabled(enabled: Boolean) {
            flow.value = flow.value.copy(privacyModeEnabled = enabled)
        }
        override suspend fun setAlwaysMaskWidgetAmounts(alwaysMask: Boolean) {
            flow.value = flow.value.copy(alwaysMaskWidgetAmounts = alwaysMask)
        }
        override suspend fun resetPrivacyDefaults() {
            flow.value = flow.value.copy(hideSensitiveBalances = false, privacyModeEnabled = false, alwaysMaskWidgetAmounts = false)
        }
        override suspend fun recordFailedAttempt(failedAttempts: Int, lockoutUntilEpochMs: Long) = Unit
        override suspend fun clearFailedAttempts() = Unit
        override suspend fun recordBackgroundedAt(epochMs: Long) = Unit
        override suspend fun clearAllSecurityState() = Unit
        fun setAppLock(enabled: Boolean) { flow.value = flow.value.copy(isAppLockEnabled = enabled, encryptedPinEnvelope = if (enabled) "env" else null) }
    }

    private class SpyNotificationPoster(
        var permissionGranted: Boolean = true
    ) : SystemNotificationPoster {
        data class Posted(val candidate: ReminderCandidate, val masked: Boolean)
        val posted = mutableListOf<Posted>()
        val cancelledIds = mutableSetOf<Int>()
        var testNotificationsSent = 0

        override fun hasOsNotificationPermission(): Boolean = permissionGranted

        override fun postReminder(candidate: ReminderCandidate, maskSensitiveContent: Boolean) {
            posted += Posted(candidate, maskSensitiveContent)
        }

        override fun sendTestNotification(): TestNotificationResult {
            if (!permissionGranted) return TestNotificationResult.PERMISSION_DENIED
            testNotificationsSent += 1
            return TestNotificationResult.SENT
        }

        override fun cancelStaleNotifications(previouslyPostedIds: Set<Int>, stillActiveIds: Set<Int>) {
            cancelledIds.addAll(previouslyPostedIds - stillActiveIds)
        }
    }

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, DuitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        financeRepo = OfflineFirstFinanceRepository(db)
        historyRepo = RoomNotificationHistoryRepository(db.notificationHistoryDao())
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun reminderCalculator_allCategoriesOffsetsTimezoneAndExclusionsWorkAccurately() {
        val today = LocalDate.of(2026, 11, 16)
        val nowInstant = ZonedDateTime.of(today, LocalTime.of(9, 5), klZone).toInstant()
        val nov19 = LocalDate.of(2026, 11, 19).toEpochDay() // +3 days -> D3 matches today!
        val nov23 = LocalDate.of(2026, 11, 23).toEpochDay() // +7 days -> D7 matches today!
        val nov16 = today.toEpochDay() // Due today -> D0 matches today!
        val nov14 = LocalDate.of(2026, 11, 14).toEpochDay() // 2 days overdue!

        val settings = NotificationSettingsSnapshot(
            masterEnabled = true,
            enabledCategories = NotificationCategory.entries.toSet(),
            enabledOffsets = setOf(ReminderOffset.DAYS_7, ReminderOffset.DAYS_3, ReminderOffset.DAY_1, ReminderOffset.DUE_DAY),
            overdueEnabled = true,
            reminderHour = 9,
            reminderMinute = 0,
            hideLockScreenAmounts = false
        )

        val commitments = listOf(
            Commitment(1L, "Rent", 120_000L, nov19, CommitmentStatus.UPCOMING, 1L, true, null, frequency = CommitmentFrequency.MONTHLY),
            Commitment(2L, "Paid Bill", 50_000L, nov16, CommitmentStatus.PAID, 1L, false, null, paidSen = 50_000L, frequency = CommitmentFrequency.ONE_TIME),
            Commitment(3L, "Overdue Water", 3_500L, nov14, CommitmentStatus.UPCOMING, 1L, false, null, frequency = CommitmentFrequency.ONE_TIME)
        )
        val payLater = listOf(
            PayLaterPlan(10L, "Chair", "Atome", 60_000L, 0L, 0L, 3, nov16, nov16, PrismaticAccentToken.PEACH, "", false, 1L, 1L)
        )
        val debts = listOf(
            // Scheduled I_OWE with due date -> INCLUDED
            DebtRecord(20L, DebtDirection.I_OWE, "Ali", "Loan", 40_000L, 0L, nov23, PrismaticAccentToken.PEACH, "", false, 1L, 1L, scheduledRepaymentSen = 15_000L),
            // Unscheduled I_OWE (no due date) -> EXCLUDED!
            DebtRecord(21L, DebtDirection.I_OWE, "Open Debt", "No date", 90_000L, 0L, null, PrismaticAccentToken.PEACH, "", false, 1L, 1L),
            // Receivable OWED_TO_ME even with due date -> EXCLUDED!
            DebtRecord(22L, DebtDirection.OWED_TO_ME, "Budi", "Dinner", 20_000L, 0L, nov16, PrismaticAccentToken.AQUA, "", false, 1L, 1L)
        )
        val goals = listOf(
            SavingsGoal(30L, "Japan", 200_000L, nov23, "", "travel", PrismaticAccentToken.SKY_BLUE, SavingsGoalStatus.ACTIVE, 1L, 1L),
            SavingsGoal(31L, "No Target", 100_000L, null, "", "savings", PrismaticAccentToken.AQUA, SavingsGoalStatus.ACTIVE, 1L, 1L)
        )
        val activeCycle = SalaryCycle(40L, "Nov Cycle", LocalDate.of(2026, 11, 5).toEpochDay(), Long.MAX_VALUE, LocalDate.of(2026, 12, 5).toEpochDay(), 300_000L, 0L, true, isOpenEnded = true)
        val commissions = listOf(
            CommissionRecord(50L, "Tier Bonus", "Company", 80_000L, CommissionStatus.PENDING, nov14, payoutDateEpochDay = nov19, linkedTransactionId = null, notes = ""),
            CommissionRecord(51L, "No Payout Date", "Other", 40_000L, CommissionStatus.PENDING, nov14, payoutDateEpochDay = null, linkedTransactionId = null, notes = "")
        )

        val result = ReminderCalculator.evaluate(
            settings = settings,
            commitments = commitments,
            payLaterPlans = payLater,
            debts = debts,
            savingsGoals = goals,
            savingsAllocations = emptyList(),
            activeCycle = activeCycle,
            commissions = commissions,
            nowInstant = nowInstant,
            zoneId = klZone
        )

        // Paid bill, unscheduled debt, OWED_TO_ME receivable, undated goal, and undated commission MUST NOT generate reminders!
        val allDueKeys = result.dueNowCandidates.map { it.category to it.entityId }.toSet()
        assertTrue((NotificationCategory.COMMITMENTS to 1L) in allDueKeys) // D3 on Nov 19
        assertFalse((NotificationCategory.COMMITMENTS to 2L) in allDueKeys) // Paid excluded
        assertTrue((NotificationCategory.COMMITMENTS to 3L) in allDueKeys) // Overdue Nov 14
        assertTrue((NotificationCategory.PAYLATER to 10L) in allDueKeys) // Due today Nov 16
        assertTrue((NotificationCategory.DEBT_REPAYMENTS to 20L) in allDueKeys) // D7 on Nov 23
        assertFalse((NotificationCategory.DEBT_REPAYMENTS to 21L) in allDueKeys) // Unscheduled excluded
        assertFalse((NotificationCategory.DEBT_REPAYMENTS to 22L) in allDueKeys) // Receivable excluded
        assertTrue((NotificationCategory.SAVINGS_GOALS to 30L) in allDueKeys) // D7 on Nov 23
        assertFalse((NotificationCategory.SAVINGS_GOALS to 31L) in allDueKeys) // Undated goal excluded
        assertTrue((NotificationCategory.COMMISSIONS to 50L) in allDueKeys) // D3 on Nov 19
        assertFalse((NotificationCategory.COMMISSIONS to 51L) in allDueKeys) // No payout date excluded

        // Time-zone boundary check: 08:59 in KL means same 09:00 due-day reminder is UPCOMING, not dueNow!
        val beforeReminder = ZonedDateTime.of(today, LocalTime.of(8, 59), klZone).toInstant()
        val earlyEval = ReminderCalculator.evaluate(
            settings = settings,
            commitments = commitments,
            payLaterPlans = payLater,
            debts = debts,
            savingsGoals = goals,
            savingsAllocations = emptyList(),
            activeCycle = activeCycle,
            commissions = commissions,
            nowInstant = beforeReminder,
            zoneId = klZone
        )
        assertTrue(earlyEval.dueNowCandidates.isEmpty())
        assertTrue(earlyEval.upcomingCandidates.isNotEmpty())
        assertEquals(ZonedDateTime.of(today, LocalTime.of(9, 0), klZone).toInstant().toEpochMilli(), earlyEval.nextScheduledTriggerEpochMs)

        // Switching timezone to UTC at 08:59 KL (= 00:59 UTC): still before 09:00 UTC
        val utcEval = ReminderCalculator.evaluate(
            settings = settings,
            commitments = commitments,
            payLaterPlans = payLater,
            debts = debts,
            savingsGoals = goals,
            savingsAllocations = emptyList(),
            activeCycle = activeCycle,
            commissions = commissions,
            nowInstant = beforeReminder,
            zoneId = ZoneId.of("UTC")
        )
        assertEquals(ZonedDateTime.of(today, LocalTime.of(9, 0), ZoneId.of("UTC")).toInstant().toEpochMilli(), utcEval.nextScheduledTriggerEpochMs)
    }

    @Test
    fun syncEngine_deduplicatesCancelsOnPaymentReschedulesRecurringAndMasksPrivacy(): Unit = runBlocking {
        financeRepo.ensureDefaultCategoriesSeeded()
        val now = System.currentTimeMillis()
        val walletId = financeRepo.upsertWallet(Wallet(0L, "MAE", WalletType.BANK, 200_000L, false, PrismaticAccentToken.AQUA, now, now))
        val today = LocalDate.of(2026, 11, 16)
        val nov19 = today.plusDays(3).toEpochDay()
        val triggerInstant = ZonedDateTime.of(today, LocalTime.of(9, 30), klZone).toInstant()

        val commId = financeRepo.upsertCommitmentDetail(
            Commitment(0L, "Unifi Fibre", 14_000L, nov19, CommitmentStatus.UPCOMING, walletId, true, null, frequency = CommitmentFrequency.MONTHLY)
        )

        val prefsRepo = FakeNotifPrefsRepo(NotificationSettingsSnapshot(hideLockScreenAmounts = false))
        val securityRepo = FakeSecurityRepo(SecurityConfigSnapshot(isAppLockEnabled = false))
        val poster = SpyNotificationPoster()
        val engine = ReminderSyncEngine(
            planningRepository = financeRepo,
            obligationRepository = financeRepo,
            savingsRepository = financeRepo,
            commissionRepository = financeRepo,
            notificationPreferencesRepository = prefsRepo,
            notificationHistoryRepository = historyRepo,
            notificationHistoryDao = db.notificationHistoryDao(),
            securityRepository = securityRepo,
            notificationPoster = poster
        )

        // First sync posts D3 reminder ONCE and stores 1 NotificationHistory item (0 financial transactions created!)
        engine.syncAndComputeNext(triggerInstant, klZone)
        assertEquals(1, poster.posted.size)
        assertFalse(poster.posted.single().masked)
        assertTrue(poster.posted.single().candidate.detailedBody.contains("RM 140.00"))
        assertEquals(1, historyRepo.observeHistory().first().size)
        assertEquals(1, historyRepo.observeUnreadCount().first())
        assertTrue(financeRepo.observeTransactions().first().isEmpty())

        // Second sync for same date & offset MUST NOT duplicate
        engine.syncAndComputeNext(triggerInstant, klZone)
        assertEquals(1, poster.posted.size)
        assertEquals(1, historyRepo.observeHistory().first().size)

        // Editing due date to Nov 30 schedules new offsets instead of old Nov 19 D3
        val updated = financeRepo.observeCommitments().first().single().copy(dueDateEpochDay = LocalDate.of(2026, 11, 30).toEpochDay())
        financeRepo.upsertCommitmentDetail(updated)
        val afterEdit = engine.syncAndComputeNext(triggerInstant, klZone)
        assertTrue(afterEdit.upcomingCandidates.any { it.targetEpochDay == LocalDate.of(2026, 11, 30).toEpochDay() })

        // Paying recurring commitment advances due date to Dec 30 -> Nov 30 reminder cancels cleanly!
        financeRepo.upsertCommitmentDetail(updated.copy(dueDateEpochDay = nov19))
        financeRepo.recordCommitmentPayment(commId, walletId, 14_000L, "Paid Nov", now, "sub-notif-pay")
        val afterPay = engine.syncAndComputeNext(triggerInstant, klZone)
        val dec19 = LocalDate.of(2026, 12, 19).toEpochDay()
        assertEquals(dec19, financeRepo.observeCommitments().first().single().dueDateEpochDay)
        // Dec 19 > 14-day window from Nov 16, so zero active notifications right now -> posted ID cancelled!
        assertTrue(afterPay.dueNowCandidates.isEmpty())
        assertTrue(ReminderCalculator.stableNotificationId(NotificationCategory.COMMITMENTS, commId) in poster.cancelledIds)

        // App Lock enabled -> masks sensitive amounts on any newly fired reminder & omits amountSen from history
        securityRepo.setAppLock(true)
        val todayBillId = financeRepo.upsertCommitmentDetail(
            Commitment(0L, "Private Loan Bill", 50_000L, today.toEpochDay(), CommitmentStatus.UPCOMING, walletId, false, null, frequency = CommitmentFrequency.ONE_TIME)
        )
        engine.syncAndComputeNext(triggerInstant, klZone)
        val lastPosted = poster.posted.last()
        assertTrue(lastPosted.masked)
        assertFalse(lastPosted.candidate.resolveBody(true).contains("RM 500.00"))
        val latestHistory = historyRepo.observeHistory().first().first { it.entityId == todayBillId }
        assertNull(latestHistory.amountSen)

        // Mark as read & clear history without affecting real transaction ledger
        historyRepo.markAsRead(latestHistory.id)
        historyRepo.markAllAsRead()
        assertEquals(0, historyRepo.observeUnreadCount().first())
        historyRepo.clearHistory()
        assertTrue(historyRepo.observeHistory().first().isEmpty())
        assertEquals(1, financeRepo.observeTransactions().first().size)
    }

    @Test
    fun preferencesChanges_disableMasterCategoryOrOffset(): Unit = runBlocking {
        val today = LocalDate.of(2026, 11, 16)
        val nowInstant = ZonedDateTime.of(today, LocalTime.of(10, 0), klZone).toInstant()
        val comm = Commitment(1L, "Rent", 100_000L, today.toEpochDay(), CommitmentStatus.UPCOMING, 1L, true, null, frequency = CommitmentFrequency.MONTHLY)

        val masterOff = NotificationSettingsSnapshot(masterEnabled = false)
        val offEval = ReminderCalculator.evaluate(masterOff, listOf(comm), emptyList(), emptyList(), emptyList(), emptyList(), null, emptyList(), nowInstant, klZone)
        assertTrue(offEval.dueNowCandidates.isEmpty())
        assertTrue(offEval.upcomingCandidates.isEmpty())

        val catOff = NotificationSettingsSnapshot(enabledCategories = NotificationCategory.DEFAULT_ENABLED - NotificationCategory.COMMITMENTS)
        val catEval = ReminderCalculator.evaluate(catOff, listOf(comm), emptyList(), emptyList(), emptyList(), emptyList(), null, emptyList(), nowInstant, klZone)
        assertTrue(catEval.dueNowCandidates.isEmpty())

        val d0Off = NotificationSettingsSnapshot(enabledOffsets = setOf(ReminderOffset.DAYS_7, ReminderOffset.DAYS_3, ReminderOffset.DAY_1))
        val d0Eval = ReminderCalculator.evaluate(d0Off, listOf(comm), emptyList(), emptyList(), emptyList(), emptyList(), null, emptyList(), nowInstant, klZone)
        assertTrue(d0Eval.dueNowCandidates.isEmpty())
    }

    @Test
    fun v8ToV9Migration_addsNotificationHistoryWithoutDisturbingFinancialData(): Unit = runBlocking {
        context.cacheDir.mkdirs()
        val dbFile = File(context.cacheDir, "test_phase5b_v8_v9_${System.nanoTime()}.db")
        if (dbFile.exists()) dbFile.delete()

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbFile.absolutePath)
                .callback(object : SupportSQLiteOpenHelper.Callback(8) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE IF NOT EXISTS wallets (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, type TEXT NOT NULL, balanceSen INTEGER NOT NULL, isExcludedFromSafeToSpend INTEGER NOT NULL, accentToken TEXT NOT NULL, createdAtEpochMs INTEGER NOT NULL, updatedAtEpochMs INTEGER NOT NULL, isIncludedInTotalBalance INTEGER NOT NULL, isArchived INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS transactions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, walletId INTEGER NOT NULL, targetWalletId INTEGER, type TEXT NOT NULL, amountSen INTEGER NOT NULL, category TEXT NOT NULL, title TEXT NOT NULL, note TEXT NOT NULL, commitmentId INTEGER, occurredAtEpochMs INTEGER NOT NULL, createdAtEpochMs INTEGER NOT NULL, categoryId INTEGER, submissionId TEXT, FOREIGN KEY(walletId) REFERENCES wallets(id) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(targetWalletId) REFERENCES wallets(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_walletId ON transactions(walletId)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_targetWalletId ON transactions(targetWalletId)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_occurredAtEpochMs ON transactions(occurredAtEpochMs)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_commitmentId ON transactions(commitmentId)")
                        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_transactions_submissionId ON transactions(submissionId)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS salary_cycles (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, label TEXT NOT NULL, startDateEpochDay INTEGER NOT NULL, endDateEpochDay INTEGER NOT NULL, paydayEpochDay INTEGER NOT NULL, baseSalarySen INTEGER NOT NULL, committedBudgetSen INTEGER NOT NULL, isActive INTEGER NOT NULL)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_salary_cycles_isActive ON salary_cycles(isActive)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS commitments (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, amountSen INTEGER NOT NULL, dueDateEpochDay INTEGER NOT NULL, status TEXT NOT NULL, walletId INTEGER, isRecurring INTEGER NOT NULL, salaryCycleId INTEGER)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_commitments_status ON commitments(status)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_commitments_dueDateEpochDay ON commitments(dueDateEpochDay)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_commitments_salaryCycleId ON commitments(salaryCycleId)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS commissions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, sourceLabel TEXT NOT NULL, amountSen INTEGER NOT NULL, status TEXT NOT NULL, earnedDateEpochDay INTEGER NOT NULL, payoutDateEpochDay INTEGER, linkedTransactionId INTEGER, notes TEXT NOT NULL)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_commissions_status ON commissions(status)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_commissions_earnedDateEpochDay ON commissions(earnedDateEpochDay)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS daily_sales_records (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, recordDateEpochDay INTEGER NOT NULL, grossSalesSen INTEGER NOT NULL, netSalesSen INTEGER NOT NULL, unitsSold INTEGER NOT NULL, notes TEXT NOT NULL, createdAtEpochMs INTEGER NOT NULL)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_daily_sales_records_recordDateEpochDay ON daily_sales_records(recordDateEpochDay)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS categories (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, type TEXT NOT NULL, iconKey TEXT NOT NULL, accentToken TEXT NOT NULL, isDefault INTEGER NOT NULL, isArchived INTEGER NOT NULL, sortOrder INTEGER NOT NULL)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_categories_type_isArchived ON categories(type, isArchived)")

                        DuitDatabase.MIGRATION_2_3.migrate(db)
                        DuitDatabase.MIGRATION_3_4.migrate(db)
                        DuitDatabase.MIGRATION_4_5.migrate(db)
                        DuitDatabase.MIGRATION_5_6.migrate(db)
                        DuitDatabase.MIGRATION_6_7.migrate(db)
                        DuitDatabase.MIGRATION_7_8.migrate(db)

                        db.execSQL("INSERT INTO wallets VALUES (1, 'Existing MAE', 'BANK', 310000, 0, 'AQUA', 1000, 1000, 1, 0)")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        helper.writableDatabase.close()
        helper.close()

        val migratedDb = Room.databaseBuilder(context, DuitDatabase::class.java, dbFile.absolutePath)
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .addMigrations(
                DuitDatabase.MIGRATION_1_2,
                DuitDatabase.MIGRATION_2_3,
                DuitDatabase.MIGRATION_3_4,
                DuitDatabase.MIGRATION_4_5,
                DuitDatabase.MIGRATION_5_6,
                DuitDatabase.MIGRATION_6_7,
                DuitDatabase.MIGRATION_7_8,
                DuitDatabase.MIGRATION_8_9,
                DuitDatabase.MIGRATION_9_10
            )
            .allowMainThreadQueries()
            .build()

        val repo = OfflineFirstFinanceRepository(migratedDb)
        assertEquals(310_000L, repo.observeWallets().first().single().balanceSen)

        val hRepo = RoomNotificationHistoryRepository(migratedDb.notificationHistoryDao())
        assertTrue(hRepo.observeHistory().first().isEmpty())
        assertTrue(
            hRepo.recordDeliveredIfAbsent(
                "COMMITMENTS:1:20776:D0", 101, NotificationCategory.COMMITMENTS,
                1L, 20776L, "Bill due today", "Pay Rent", 120_000L, false,
                NotificationDeepLink.PLANNING_COMMITMENTS, System.currentTimeMillis()
            )
        )
        assertEquals(1, hRepo.observeUnreadCount().first())

        migratedDb.close()
        dbFile.delete()
    }

    @Test
    @Config(sdk = [33])
    fun mainActivity_requestPermission_handlesAllowAndDenyWithoutRequestCodeCrash() {
        // 1. "Don't Allow" path with a 32-bit ActivityResultRegistry requestCode.
        // In FragmentActivity 1.2.5 this threw "IllegalArgumentException: Can only use lower 16 bits for requestCode".
        val denyController = Robolectric.buildActivity(MainActivity::class.java).create().start().resume()
        val denyActivity = denyController.get()
        @Suppress("DEPRECATION")
        denyActivity.validateRequestPermissionsRequestCode(0x0001_2345)

        val deniedResults = mutableListOf<Boolean>()
        val denyLauncher = denyActivity.activityResultRegistry.register(
            "bug_5b_001_perm_deny_test",
            ActivityResultContracts.RequestPermission()
        ) { granted -> deniedResults += granted }
        denyLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        val denyRequest = shadowOf(denyActivity).lastRequestedPermission
        assertTrue(denyRequest.requestCode ushr 16 != 0)
        assertTrue(denyRequest.requestedPermissions.contains(Manifest.permission.POST_NOTIFICATIONS))
        denyActivity.onRequestPermissionsResult(
            denyRequest.requestCode,
            denyRequest.requestedPermissions,
            intArrayOf(PackageManager.PERMISSION_DENIED)
        )
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(false), deniedResults)
        denyController.pause().stop().destroy()

        // 2. "Allow" path with a 32-bit ActivityResultRegistry requestCode.
        val allowController = Robolectric.buildActivity(MainActivity::class.java).create().start().resume()
        val allowActivity = allowController.get()
        val allowedResults = mutableListOf<Boolean>()
        val allowLauncher = allowActivity.activityResultRegistry.register(
            "bug_5b_001_perm_allow_test",
            ActivityResultContracts.RequestPermission()
        ) { granted -> allowedResults += granted }
        allowLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        val allowRequest = shadowOf(allowActivity).lastRequestedPermission
        assertTrue(allowRequest.requestCode ushr 16 != 0)
        allowActivity.onRequestPermissionsResult(
            allowRequest.requestCode,
            allowRequest.requestedPermissions,
            intArrayOf(PackageManager.PERMISSION_GRANTED)
        )
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(true), allowedResults)
        allowController.pause().stop().destroy()
    }

    @Test
    @Config(sdk = [34])
    fun androidNotificationHelper_sendTestNotification_postsExactContentOnceAndCreatesNoFinancialOrHistoryData(): Unit = runBlocking {
        val helper = AndroidNotificationHelper(context)
        val sysNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNotificationManager = shadowOf(sysNotificationManager)

        // 1. Without POST_NOTIFICATIONS, no notification is posted and permission denied is returned.
        shadowOf(context.applicationContext as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertEquals(TestNotificationResult.PERMISSION_DENIED, helper.sendTestNotification())
        assertTrue(shadowNotificationManager.allNotifications.isEmpty())

        // 2. With POST_NOTIFICATIONS granted, posts one immediate local notification on existing channel.
        shadowOf(context.applicationContext as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertEquals(TestNotificationResult.SENT, helper.sendTestNotification())
        val posted = shadowNotificationManager.getNotification(AndroidNotificationHelper.TEST_NOTIFICATION_ID)
        assertEquals("duit. Test Notification", posted.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Notifications are working correctly.", posted.extras.getString(Notification.EXTRA_TEXT))
        assertEquals(NotificationCategory.COMMITMENTS.channelId, posted.channelId)

        // Repeated calls overwrite the same fixed notification ID instead of stacking duplicates.
        assertEquals(TestNotificationResult.SENT, helper.sendTestNotification())
        assertEquals(1, shadowNotificationManager.allNotifications.size)

        // Zero financial records or notification history entries were created.
        assertTrue(financeRepo.observeTransactions().first().isEmpty())
        assertTrue(financeRepo.observeCommitments().first().isEmpty())
        assertTrue(historyRepo.observeHistory().first().isEmpty())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun notificationViewModel_sendTestNotification_enforcesRapidTapCooldownAndPermissionCheck(): Unit {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val store = ViewModelStore()
        val vmScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            runBlocking(Dispatchers.IO) {
                var nowMs = 1_000_000L
                val spyPoster = SpyNotificationPoster(permissionGranted = false)
                var immediateSyncCalls = 0
                var periodicCalls = 0
                val spyScheduler = object : ReminderScheduler {
                    override fun ensureDailySafetyNet() { periodicCalls += 1 }
                    override fun enqueueImmediateSync() { immediateSyncCalls += 1 }
                    override fun scheduleNextTriggerAt(triggerAtEpochMs: Long, nowEpochMs: Long) { periodicCalls += 1 }
                    override fun cancelScheduledFutureTrigger() = Unit
                    override fun cancelAllWork() = Unit
                }
                val fakeSecurityRepo = FakeSecurityRepo()
                val appLockManager = AppLockManager(
                    securityRepository = fakeSecurityRepo,
                    pinHashEngine = PinHashEngine(
                        object : SecretProtector {
                            override fun seal(plaintext: String): String = plaintext
                            override fun unseal(ciphertext: String): String = ciphertext
                        }
                    ),
                    biometricGateway = object : BiometricAuthGateway {
                        override fun checkAvailability(): BiometricAvailability = BiometricAvailability.UNAVAILABLE
                        override fun authenticate(
                            activity: androidx.fragment.app.FragmentActivity,
                            title: String,
                            subtitle: String,
                            negativeButtonText: String,
                            onResult: (BiometricAuthResult) -> Unit
                        ) = Unit
                    },
                    scope = vmScope,
                    onFactoryResetFinancialData = {}
                )
                val viewModel = ViewModelProvider(
                    store,
                    object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                            NotificationViewModel(
                                planningRepository = financeRepo,
                                obligationRepository = financeRepo,
                                savingsRepository = financeRepo,
                                commissionRepository = financeRepo,
                                preferencesRepository = FakeNotifPrefsRepo(),
                                historyRepository = historyRepo,
                                reminderScheduler = spyScheduler,
                                notificationPoster = spyPoster,
                                appLockManager = appLockManager,
                                clockMs = { nowMs }
                            ) as T
                    }
                )[NotificationViewModel::class.java]

                // 1. Denied permission blocks delivery and sets PERMISSION_DENIED status.
                viewModel.sendTestNotification()
                assertEquals(0, spyPoster.testNotificationsSent)
                assertEquals(0, periodicCalls)
                assertEquals(
                    TestNotificationStatus.PERMISSION_DENIED,
                    withTimeout(5_000) { viewModel.uiState.first { !it.isLoading } }.testNotificationStatus
                )

                // 2. Grant permission and verify rapid taps within cooldown only send one notification.
                spyPoster.permissionGranted = true
                viewModel.refreshPermissionAndSync()
                val immediateSyncBeforeTest = immediateSyncCalls
                viewModel.sendTestNotification()
                assertEquals(1, spyPoster.testNotificationsSent)
                assertEquals(
                    TestNotificationStatus.SENT,
                    withTimeout(5_000) { viewModel.uiState.first { it.testNotificationStatus == TestNotificationStatus.SENT } }.testNotificationStatus
                )

                nowMs += 500L
                viewModel.sendTestNotification()
                assertEquals(1, spyPoster.testNotificationsSent)
                assertEquals(
                    TestNotificationStatus.THROTTLED,
                    withTimeout(5_000) { viewModel.uiState.first { it.testNotificationStatus == TestNotificationStatus.THROTTLED } }.testNotificationStatus
                )

                // 3. After cooldown elapses, another tap succeeds without scheduling any WorkManager reminder work.
                nowMs += NotificationViewModel.TEST_COOLDOWN_MS
                viewModel.sendTestNotification()
                assertEquals(2, spyPoster.testNotificationsSent)
                assertEquals(0, periodicCalls)
                assertEquals(immediateSyncBeforeTest, immediateSyncCalls)
            }
        } finally {
            store.clear()
            vmScope.cancel()
            Dispatchers.resetMain()
        }
    }
}
