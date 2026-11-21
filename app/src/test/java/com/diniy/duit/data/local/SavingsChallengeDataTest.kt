package com.diniy.duit.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.diniy.duit.backup.BackupCryptoEngine
import com.diniy.duit.backup.BackupFormatException
import com.diniy.duit.backup.BackupSerializer
import com.diniy.duit.backup.PortablePreferencesBackup
import com.diniy.duit.backup.PortablePrivacyPreferences
import com.diniy.duit.data.local.entity.SavingsChallengeEntity
import com.diniy.duit.data.local.entity.WalletEntity
import com.diniy.duit.data.repository.OfflineFirstFinanceRepository
import com.diniy.duit.domain.challenge.ChallengeType
import com.diniy.duit.domain.challenge.GardenStage
import com.diniy.duit.domain.challenge.SavingsChallenge
import com.diniy.duit.domain.model.AppSettingsSnapshot
import com.diniy.duit.domain.model.PrismaticAccentToken
import com.diniy.duit.domain.model.SavingsGoal
import com.diniy.duit.domain.model.SavingsGoalStatus
import com.diniy.duit.domain.model.WalletType
import com.diniy.duit.notifications.NotificationSettingsSnapshot
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SavingsChallengeDataTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs = PortablePreferencesBackup(AppSettingsSnapshot(), NotificationSettingsSnapshot(), PortablePrivacyPreferences())
    private fun memoryDb() = Room.inMemoryDatabaseBuilder(context, DuitDatabase::class.java).allowMainThreadQueries().build()

    private fun goal(name: String = "Japan Trip") = SavingsGoal(0, name, 500_000, null, "", "star", PrismaticAccentToken.AQUA,
        SavingsGoalStatus.ACTIVE, 1, 1)

    private fun challenge(goalId: Long, id: Long = 0) = SavingsChallenge(id, goalId, ChallengeType.WEEK_52, 100, 20_400, randomSeed = 7)

    private suspend fun seeded(db: DuitDatabase): Pair<OfflineFirstFinanceRepository, Long> {
        db.walletDao().upsert(WalletEntity(name = "MAE", type = WalletType.BANK, balanceSen = 310_000,
            isExcludedFromSafeToSpend = false, accentToken = PrismaticAccentToken.AQUA, createdAtEpochMs = 1, updatedAtEpochMs = 1))
        val repo = OfflineFirstFinanceRepository(db)
        return repo to repo.upsertSavingsGoal(goal())
    }

    @Test
    fun migration10To11_keepsExistingData_andAddsChallengeTables() = runBlocking {
        val dbFile = File(context.cacheDir.apply { mkdirs() }, "challenges_v10_${System.nanoTime()}.db")
        val v10 = JSONObject(File("schemas/com.diniy.duit.data.local.DuitDatabase/10.json").readText()).getJSONObject("database")
        FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(dbFile.absolutePath)
                .callback(object : SupportSQLiteOpenHelper.Callback(10) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        val entities = v10.getJSONArray("entities")
                        for (i in 0 until entities.length()) {
                            val e = entities.getJSONObject(i)
                            val table = e.getString("tableName")
                            db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
                            val indices = e.optJSONArray("indices") ?: continue
                            for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                        }
                        db.execSQL("INSERT INTO wallets VALUES (1, 'MAE', 'BANK', 310000, 0, 'AQUA', 1000, 1000, 1, 0)")
                        db.execSQL("INSERT INTO savings_goals (id, name, targetAmountSen, targetDateEpochDay, description, iconKey, accentToken, status, createdAtEpochMs, updatedAtEpochMs) VALUES (1, 'Japan', 500000, NULL, '', 'star', 'AQUA', 'ACTIVE', 1, 1)")
                        db.execSQL("INSERT INTO savings_wallet_allocations (id, goalId, walletId, allocatedSen, updatedAtEpochMs) VALUES (1, 1, 1, 120000, 1)")
                        db.execSQL("INSERT INTO money_reminders (id, title, dateEpochDay, createdAtEpochMs, updatedAtEpochMs) VALUES (1, 'Check bonus', 20400, 1, 1)")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build()
        ).apply { writableDatabase.close(); close() }

        // Room validates every table against the v11 entities after migrating.
        val db = Room.databaseBuilder(context, DuitDatabase::class.java, dbFile.absolutePath)
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .addMigrations(DuitDatabase.MIGRATION_10_11, DuitDatabase.MIGRATION_11_12, DuitDatabase.MIGRATION_12_13)
            .allowMainThreadQueries().build()
        try {
            val repo = OfflineFirstFinanceRepository(db)
            assertEquals(310_000L, repo.observeWallets().first().single().balanceSen)
            assertEquals(120_000L, repo.observeSavingsAllocations().first().single().allocatedSen)
            assertEquals("Check bonus", repo.observeMoneyReminders().first().single().title)
            repo.saveSavingsChallenge(challenge(goalId = 1))
            assertEquals(ChallengeType.WEEK_52, repo.observeSavingsChallenges().first().single().type)
        } finally {
            db.close(); dbFile.delete()
        }
    }

    @Test
    fun repository_crud_skips_stickers_withoutTouchingMoney() = runBlocking {
        val db = memoryDb()
        try {
            val (repo, goalId) = seeded(db)
            val id = repo.saveSavingsChallenge(challenge(goalId))
            repo.saveSavingsChallenge(challenge(goalId, id).copy(isPaused = true))
            assertTrue(repo.observeSavingsChallenges().first().single().isPaused)

            repo.setChallengeSlotSkipped(id, 3, skipped = true)
            repo.setChallengeSlotSkipped(id, 3, skipped = true) // idempotent
            repo.setChallengeSlotSkipped(id, 5, skipped = true)
            repo.setChallengeSlotSkipped(id, 5, skipped = false)
            assertEquals(mapOf(id to setOf(3)), repo.observeChallengeSkippedSlots().first())

            repo.recordEarnedStickers(id, setOf(GardenStage.SPROUT, GardenStage.BUD))
            repo.recordEarnedStickers(id, setOf(GardenStage.SPROUT)) // earned once only
            assertEquals(2, repo.observeChallengeStickers().first().size)

            // Deleting the challenge keeps its stickers as history.
            repo.deleteSavingsChallenge(id)
            assertTrue(repo.observeSavingsChallenges().first().isEmpty())
            assertTrue(repo.observeChallengeSkippedSlots().first().isEmpty())
            assertEquals(2, repo.observeChallengeStickers().first().size)

            assertEquals(310_000L, repo.observeWallets().first().single().balanceSen)
            assertTrue(repo.observeSavingsMovements().first().isEmpty())
            assertTrue(repo.observeSavingsAllocations().first().isEmpty())
            assertTrue(repo.observeTransactions().first().isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun invalidChallenge_isRejected_andDeletingTheGoalRemovesItsChallenge() = runBlocking {
        val db = memoryDb()
        try {
            val (repo, goalId) = seeded(db)
            try { repo.saveSavingsChallenge(challenge(goalId).copy(baseAmountSen = 0)); fail() } catch (_: IllegalArgumentException) {}
            try { repo.saveSavingsChallenge(challenge(goalId).copy(slotCount = 0)); fail() } catch (_: IllegalArgumentException) {}
            repo.saveSavingsChallenge(challenge(goalId))
            repo.deleteSavingsGoalSafely(goalId)
            assertTrue(repo.observeSavingsChallenges().first().isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun backupRoundTrip_keepsChallenges_olderBackupsRestore_danglingGoalRejected() = runBlocking {
        val source = memoryDb()
        val target = memoryDb()
        try {
            val (repo, goalId) = seeded(source)
            val id = repo.saveSavingsChallenge(challenge(goalId))
            repo.setChallengeSlotSkipped(id, 2, true)
            repo.recordEarnedStickers(id, setOf(GardenStage.SPROUT))
            val snapshot = source.backupDao().exportFullSnapshot()

            fun roundTrip(json: String, schema: Int) = BackupCryptoEngine.encrypt(json, "Pass1234".toCharArray(), dbSchemaVersion = schema).let {
                BackupSerializer.decodePayload(BackupCryptoEngine.inspectEnvelope(it), BackupCryptoEngine.decrypt(it, "Pass1234".toCharArray()))
            }
            val bundle = roundTrip(BackupSerializer.encodePayload(snapshot, prefs, createdAtEpochMs = 1), 11)
            assertEquals(snapshot.savingsChallenges, bundle.ledger.savingsChallenges)
            target.backupDao().replaceAllLedgerDataAtomically(bundle.ledger)
            val restored = OfflineFirstFinanceRepository(target)
            assertEquals(1, restored.observeSavingsChallenges().first().size)
            assertEquals(mapOf(id to setOf(2)), restored.observeChallengeSkippedSlots().first())
            assertEquals(GardenStage.SPROUT, restored.observeChallengeStickers().first().single().stage)

            // A v10 backup has no challenge arrays at all.
            val old = JSONObject(BackupSerializer.encodePayload(snapshot, prefs, createdAtEpochMs = 1, dbSchemaVersion = 10))
            listOf("savingsChallenges", "challengeSkippedSlots", "challengeStickers").forEach { old.getJSONObject("ledger").remove(it) }
            val oldBundle = roundTrip(old.toString(), 10)
            assertTrue(oldBundle.ledger.savingsChallenges.isEmpty())
            target.backupDao().replaceAllLedgerDataAtomically(oldBundle.ledger)
            assertTrue(restored.observeSavingsChallenges().first().isEmpty())
            assertEquals(310_000L, restored.observeWallets().first().single().balanceSen)

            // A challenge pointing at a goal that isn't in the backup is refused before anything is replaced.
            val broken = snapshot.copy(savingsChallenges = snapshot.savingsChallenges + SavingsChallengeEntity(
                id = 99, goalId = 999, type = "DAILY", baseAmountSen = 100, startEpochDay = 1, slotCount = 3, intervalDays = 1,
                randomSeed = 0, isPaused = false, createdAtEpochMs = 1, updatedAtEpochMs = 1))
            try { roundTrip(BackupSerializer.encodePayload(broken, prefs, createdAtEpochMs = 1), 11); fail() } catch (_: BackupFormatException) {}
        } finally {
            source.close(); target.close()
        }
    }
}
