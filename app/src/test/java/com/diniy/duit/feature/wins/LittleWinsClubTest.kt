package com.diniy.duit.feature.wins

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.diniy.duit.backup.BackupCryptoEngine
import com.diniy.duit.backup.BackupSerializer
import com.diniy.duit.backup.PortablePreferencesBackup
import com.diniy.duit.backup.PortablePrivacyPreferences
import com.diniy.duit.core.ui.theme.DuitTheme
import com.diniy.duit.data.local.DuitDatabase
import com.diniy.duit.data.repository.OfflineFirstFinanceRepository
import com.diniy.duit.data.repository.OfflineFirstMilestoneRepository
import com.diniy.duit.domain.challenge.EarnedSticker
import com.diniy.duit.domain.challenge.GardenStage
import com.diniy.duit.domain.model.AppSettingsSnapshot
import com.diniy.duit.domain.model.Budget
import com.diniy.duit.domain.model.BudgetPeriodType
import com.diniy.duit.domain.model.BudgetScope
import com.diniy.duit.domain.model.Commitment
import com.diniy.duit.domain.model.CommitmentStatus
import com.diniy.duit.domain.model.FinanceTransaction
import com.diniy.duit.domain.model.PrismaticAccentToken
import com.diniy.duit.domain.model.SavingsAllocation
import com.diniy.duit.domain.model.SavingsGoal
import com.diniy.duit.domain.model.SavingsGoalStatus
import com.diniy.duit.domain.model.SavingsMovement
import com.diniy.duit.domain.model.SavingsMovementType
import com.diniy.duit.domain.model.ThemeMode
import com.diniy.duit.domain.model.TransactionType
import com.diniy.duit.domain.wins.LittleWinType
import com.diniy.duit.domain.wins.LittleWinsCalculator
import com.diniy.duit.domain.wins.LittleWinsInputs
import com.diniy.duit.domain.wins.PersistedMilestone
import com.diniy.duit.notifications.NotificationSettingsSnapshot
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LittleWinsClubTest {

    @get:Rule val rule = createEmptyComposeRule()
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val kl = ZoneId.of("Asia/Kuala_Lumpur")
    private val nov2026 = YearMonth.of(2026, 11)

    private fun emptyInputs() = LittleWinsInputs(
        goals = emptyList(),
        allocations = emptyList(),
        movements = emptyList(),
        budgets = emptyList(),
        transactions = emptyList(),
        categories = emptyList(),
        commitments = emptyList(),
        payLaterPlans = emptyList(),
        debts = emptyList(),
        obligationPayments = emptyList(),
        challengeStickers = emptyList()
    )

    @Test
    fun calculator_awardsFromVerifiedRecords_preservesHistory_recalculatesLiveProgress() {
        val goal = SavingsGoal(1, "Emergency", 100_000, null, "", "star", PrismaticAccentToken.AQUA, SavingsGoalStatus.ACTIVE, 1, 1)
        val alloc = SavingsAllocation(1, 1, 1, 60_000, 1) // 60% of RM 1,000 + >= RM 500 cushion
        val budgetOct = Budget(1, "Oct All", BudgetScope.OVERALL, BudgetPeriodType.MONTHLY, "2026-10", null, null, "", 50_000, PrismaticAccentToken.AQUA, "", false, 1, 1)
        val octTx = FinanceTransaction(1, 1, null, TransactionType.EXPENSE, 10_000, "Food", "Lunch", "", null,
            LocalDateTime.of(2026, 10, 10, 12, 0).atZone(kl).toInstant().toEpochMilli(), 1)
        val paidBill = Commitment(1, "Fibre", 10_000, LocalDate.of(2026, 11, 5).toEpochDay(), CommitmentStatus.PAID, 1, false, null)
        val challengeSticker = EarnedSticker(1, GardenStage.SPROUT, 5_000L)

        val inputs = emptyInputs().copy(
            goals = listOf(goal),
            allocations = listOf(alloc),
            budgets = listOf(budgetOct),
            transactions = listOf(octTx),
            commitments = listOf(paidBill),
            challengeStickers = listOf(challengeSticker)
        )
        val items = LittleWinsCalculator.evaluate(inputs, persisted = emptyList(), currentMonth = nov2026, zone = kl)
            .associateBy { it.type }

        assertTrue(items.getValue(LittleWinType.SAVINGS_FIRST_DEPOSIT).isCurrentlyMet)
        assertTrue(items.getValue(LittleWinType.SAVINGS_GOAL_25).isCurrentlyMet)
        assertTrue(items.getValue(LittleWinType.SAVINGS_GOAL_50).isCurrentlyMet)
        assertFalse(items.getValue(LittleWinType.SAVINGS_GOAL_100).isCurrentlyMet)
        assertTrue(items.getValue(LittleWinType.SAVINGS_CUSHION_500).isCurrentlyMet)
        assertTrue(items.getValue(LittleWinType.BUDGET_FIRST_PLAN).isCurrentlyMet)
        assertTrue(items.getValue(LittleWinType.BUDGET_ON_TRACK).isCurrentlyMet)
        assertTrue(items.getValue(LittleWinType.BUDGET_COMPLETED_MONTH).isCurrentlyMet)
        assertTrue(items.getValue(LittleWinType.COMMITMENT_FIRST_PAID).isCurrentlyMet)
        assertTrue(items.getValue(LittleWinType.COMMITMENT_CLEAR_MONTH).isCurrentlyMet)
        assertTrue(items.getValue(LittleWinType.CHALLENGE_SPROUT).isEarned)

        // REASSIGN alone does NOT award First Seed
        val reassignOnly = emptyInputs().copy(
            movements = listOf(SavingsMovement(1, 1, 1, 1, 2, SavingsMovementType.REASSIGN, 50_000, null, "", 1))
        )
        val reassignItems = LittleWinsCalculator.evaluate(reassignOnly, emptyList(), nov2026, kl).associateBy { it.type }
        assertFalse(reassignItems.getValue(LittleWinType.SAVINGS_FIRST_DEPOSIT).isCurrentlyMet)

        // Historical sticker stays earned even if live allocation drops to 0, while live progress recalculates to 0%!
        val historical = listOf(PersistedMilestone(LittleWinType.SAVINGS_CUSHION_500.key, earnedAtEpochMs = 999L, celebratedAtEpochMs = 1000L))
        val afterSpend = LittleWinsCalculator.evaluate(emptyInputs(), historical, nov2026, kl).associateBy { it.type }
        val cushion = afterSpend.getValue(LittleWinType.SAVINGS_CUSHION_500)
        assertTrue(cushion.isEarned)
        assertFalse(cushion.isCurrentlyMet)
        assertEquals(0, cushion.progressPercent)
        assertFalse(cushion.needsCelebration)
    }

    @Test
    fun migration12To13_preservesData_deduplicatesRewards_andBackupRoundTrips() = runBlocking {
        val schemaFile = File("schemas/com.diniy.duit.data.local.DuitDatabase/12.json")
            .takeIf { it.exists() }
            ?: File("app/schemas/com.diniy.duit.data.local.DuitDatabase/12.json")
        assertTrue("Expected v12 schema at ${schemaFile.absolutePath}", schemaFile.exists())
        val schema = JSONObject(schemaFile.readText()).getJSONObject("database")
        val entities = schema.getJSONArray("entities")
        val dbFile = File(context.cacheDir, "mig12to13_${System.nanoTime()}.db")

        FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbFile.absolutePath)
                .callback(object : SupportSQLiteOpenHelper.Callback(12) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        for (i in 0 until entities.length()) {
                            val e = entities.getJSONObject(i)
                            val table = e.getString("tableName")
                            db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
                            val indices = e.optJSONArray("indices") ?: continue
                            for (j in 0 until indices.length()) {
                                db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                            }
                        }
                        db.execSQL("INSERT INTO wallets VALUES (1, 'MAE', 'BANK', 263800, 0, 'AQUA', 1000, 1000, 1, 0)")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build()
        ).apply { writableDatabase.close(); close() }

        val db = Room.databaseBuilder(context, DuitDatabase::class.java, dbFile.absolutePath)
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .addMigrations(DuitDatabase.MIGRATION_12_13)
            .allowMainThreadQueries()
            .build()
        try {
            val financeRepo = OfflineFirstFinanceRepository(db)
            val milestoneRepo = OfflineFirstMilestoneRepository(db.milestoneDao())
            assertEquals(263_800L, financeRepo.observeWallets().first().single().balanceSen)

            // Duplicate inserts must produce only 1 persisted sticker
            milestoneRepo.recordEarnedMilestones(setOf(LittleWinType.SAVINGS_FIRST_DEPOSIT.key), earnedAtEpochMs = 10_000L)
            milestoneRepo.recordEarnedMilestones(setOf(LittleWinType.SAVINGS_FIRST_DEPOSIT.key), earnedAtEpochMs = 20_000L)
            val earned = milestoneRepo.observeEarnedMilestones().first()
            assertEquals(1, earned.size)
            assertEquals(10_000L, earned.single().earnedAtEpochMs)
            assertEquals(null, earned.single().celebratedAtEpochMs)

            milestoneRepo.markCelebrated(listOf(LittleWinType.SAVINGS_FIRST_DEPOSIT.key), celebratedAtEpochMs = 30_000L)
            assertEquals(30_000L, milestoneRepo.observeEarnedMilestones().first().single().celebratedAtEpochMs)

            // Encrypted backup/restore round-trip at v13
            val snap = db.backupDao().exportFullSnapshot()
            val prefs = PortablePreferencesBackup(AppSettingsSnapshot(), NotificationSettingsSnapshot(), PortablePrivacyPreferences())
            val json = BackupSerializer.encodePayload(snap, prefs, createdAtEpochMs = 5000L)
            val enc = BackupCryptoEngine.encrypt(json, "Secret123!".toCharArray())
            val bundle = BackupSerializer.decodePayload(
                BackupCryptoEngine.inspectEnvelope(enc),
                BackupCryptoEngine.decrypt(enc, "Secret123!".toCharArray())
            )
            assertEquals(13, bundle.preview.dbSchemaVersion)
            assertEquals(1, bundle.ledger.earnedMilestones.size)
            assertEquals(LittleWinType.SAVINGS_FIRST_DEPOSIT.key, bundle.ledger.earnedMilestones.single().milestoneKey)

            // Old v12 backup restores cleanly with empty milestones
            val v12Json = JSONObject(json).apply {
                put("dbSchemaVersion", 12)
                getJSONObject("ledger").remove("earnedMilestones")
            }
            val v12Enc = BackupCryptoEngine.encrypt(v12Json.toString(), "Secret123!".toCharArray(), dbSchemaVersion = 12)
            val v12Bundle = BackupSerializer.decodePayload(
                BackupCryptoEngine.inspectEnvelope(v12Enc),
                BackupCryptoEngine.decrypt(v12Enc, "Secret123!".toCharArray())
            )
            assertTrue(v12Bundle.ledger.earnedMilestones.isEmpty())
        } finally {
            db.close()
            dbFile.delete()
        }
    }

    @Test
    fun screen_rendersInDarkModeAndLargeFont_withCelebrationBanner() {
        val persisted = listOf(PersistedMilestone(LittleWinType.SAVINGS_FIRST_DEPOSIT.key, 10_000L, celebratedAtEpochMs = null))
        val items = LittleWinsCalculator.evaluate(emptyInputs(), persisted, nov2026, kl)
        val state = LittleWinsUiState(isLoading = false, items = items)
        Robolectric.buildActivity(ComponentActivity::class.java).setup().get().setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 2f, fontScale = 2f)) {
                DuitTheme(themeMode = ThemeMode.DARK) {
                    LittleWinsClubScreen(state = state, onSelectCategory = {}, onMarkCelebrated = {}, onClose = {})
                }
            }
        }
        rule.onNodeWithText("Little Wins Club").assertIsDisplayed()
        rule.onNodeWithText("New Sticker Unlocked").assertExists()
        rule.onNodeWithText("Nice!").assertExists()
    }
}
