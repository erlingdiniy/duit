package com.diniy.duit.data.local

import android.content.Context
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
import com.diniy.duit.data.local.entity.MoneyReminderEntity
import com.diniy.duit.data.local.entity.WalletEntity
import com.diniy.duit.data.local.entity.toDomain
import com.diniy.duit.domain.calendar.CalendarEventProjector
import com.diniy.duit.domain.calendar.CalendarEventType
import com.diniy.duit.domain.model.AppSettingsSnapshot
import com.diniy.duit.domain.model.CommitmentFrequency
import com.diniy.duit.domain.model.MoneyReminder
import com.diniy.duit.domain.model.PrismaticAccentToken
import com.diniy.duit.domain.model.WalletType
import com.diniy.duit.notifications.NotificationSettingsSnapshot
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MoneyReminderDataTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs = PortablePreferencesBackup(AppSettingsSnapshot(), NotificationSettingsSnapshot(), PortablePrivacyPreferences())
    private fun day(m: Int, d: Int) = LocalDate.of(2026, m, d).toEpochDay()

    private fun memoryDb() = Room.inMemoryDatabaseBuilder(context, DuitDatabase::class.java).allowMainThreadQueries().build()

    private fun wallet(balance: Long) = WalletEntity(name = "MAE", type = WalletType.BANK, balanceSen = balance,
        isExcludedFromSafeToSpend = false, accentToken = PrismaticAccentToken.AQUA, createdAtEpochMs = 1, updatedAtEpochMs = 1)

    private fun reminder(id: Long, date: Long, repeat: CommitmentFrequency = CommitmentFrequency.ONE_TIME) =
        MoneyReminderEntity(id = id, title = "Check bonus", dateEpochDay = date, repeatFrequency = repeat, note = "HR", createdAtEpochMs = 5, updatedAtEpochMs = 5)

    @Test
    fun migration9To10_keepsExistingData_andAddsRemindersTable() = runBlocking {
        val dbFile = File(context.cacheDir.apply { mkdirs() }, "reminders_v9_${System.nanoTime()}.db")
        // Build a real v9 database from the exported v9 schema, with financial data in it.
        val v9 = JSONObject(File("schemas/com.diniy.duit.data.local.DuitDatabase/9.json").readText()).getJSONObject("database")
        FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(dbFile.absolutePath)
                .callback(object : SupportSQLiteOpenHelper.Callback(9) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        val entities = v9.getJSONArray("entities")
                        for (i in 0 until entities.length()) {
                            val e = entities.getJSONObject(i)
                            val table = e.getString("tableName")
                            db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
                            val indices = e.optJSONArray("indices") ?: continue
                            for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                        }
                        db.execSQL("INSERT INTO wallets VALUES (1, 'Existing MAE', 'BANK', 310000, 0, 'AQUA', 1000, 1000, 1, 0)")
                        db.execSQL("INSERT INTO transactions (id, walletId, targetWalletId, type, amountSen, category, title, note, commitmentId, occurredAtEpochMs, createdAtEpochMs) VALUES (1, 1, NULL, 'EXPENSE', 2480, 'Food', 'Lunch', '', NULL, 1000, 1000)")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build()
        ).apply { writableDatabase.close(); close() }

        // Room validates every table against the v10 entities after migrating (fails on any mismatch).
        val db = Room.databaseBuilder(context, DuitDatabase::class.java, dbFile.absolutePath)
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .addMigrations(DuitDatabase.MIGRATION_9_10, DuitDatabase.MIGRATION_10_11, DuitDatabase.MIGRATION_11_12, DuitDatabase.MIGRATION_12_13)
            .allowMainThreadQueries().build()
        try {
            assertEquals(310_000L, db.walletDao().observeAll().first().single().balanceSen)
            assertEquals(2_480L, db.transactionDao().observeAll().first().single().amountSen)
            db.moneyReminderDao().insert(reminder(0, day(11, 20)))
            assertEquals("Check bonus", db.moneyReminderDao().observeAll().first().single().title)
        } finally {
            db.close(); dbFile.delete()
        }
    }

    @Test
    fun backupRoundTrip_keepsReminders_andRestoreMovesNoMoney() = runBlocking {
        val source = memoryDb()
        val restored = memoryDb()
        try {
            source.walletDao().upsert(wallet(310_000))
            source.moneyReminderDao().insert(reminder(7, day(11, 25), CommitmentFrequency.MONTHLY))
            val snapshot = source.backupDao().exportFullSnapshot()
            val bytes = BackupCryptoEngine.encrypt(BackupSerializer.encodePayload(snapshot, prefs, createdAtEpochMs = 1), "Pass1234".toCharArray())
            val bundle = BackupSerializer.decodePayload(BackupCryptoEngine.inspectEnvelope(bytes), BackupCryptoEngine.decrypt(bytes, "Pass1234".toCharArray()))
            assertEquals(BackupCryptoEngine.CURRENT_DB_SCHEMA_VERSION, bundle.header.dbSchemaVersion)
            assertEquals(snapshot.moneyReminders, bundle.ledger.moneyReminders)

            restored.backupDao().replaceAllLedgerDataAtomically(bundle.ledger)
            assertEquals(CommitmentFrequency.MONTHLY, restored.moneyReminderDao().observeAll().first().single().repeatFrequency)
            assertEquals(310_000L, restored.walletDao().observeAll().first().single().balanceSen)
            assertTrue(restored.transactionDao().observeAll().first().isEmpty())
        } finally {
            source.close(); restored.close()
        }
    }

    @Test
    fun olderV9Backup_withoutReminders_stillRestores() = runBlocking {
        val db = memoryDb()
        try {
            db.walletDao().upsert(wallet(5_000))
            val payload = JSONObject(BackupSerializer.encodePayload(db.backupDao().exportFullSnapshot(), prefs, createdAtEpochMs = 1, dbSchemaVersion = 9))
            payload.getJSONObject("ledger").remove("moneyReminders")
            val bytes = BackupCryptoEngine.encrypt(payload.toString(), "Pass1234".toCharArray(), dbSchemaVersion = 9)
            val bundle = BackupSerializer.decodePayload(BackupCryptoEngine.inspectEnvelope(bytes), BackupCryptoEngine.decrypt(bytes, "Pass1234".toCharArray()))
            assertEquals(9, bundle.preview.dbSchemaVersion)
            assertTrue(bundle.ledger.moneyReminders.isEmpty())
            db.moneyReminderDao().insert(reminder(0, day(11, 1)))
            db.backupDao().replaceAllLedgerDataAtomically(bundle.ledger)
            assertTrue(db.moneyReminderDao().observeAll().first().isEmpty())
            assertEquals(5_000L, db.walletDao().observeAll().first().single().balanceSen)
        } finally {
            db.close()
        }
    }

    @Test
    fun repository_insertsUpdatesAndDeletes_withoutTouchingMoney() = runBlocking {
        val db = memoryDb()
        try {
            db.walletDao().upsert(wallet(12_000))
            val repo = com.diniy.duit.data.repository.OfflineFirstFinanceRepository(db)
            val id = repo.saveMoneyReminder(MoneyReminder(0, "Road tax", day(11, 30)))
            repo.saveMoneyReminder(MoneyReminder(id, "Renew road tax", day(12, 1), CommitmentFrequency.YEARLY))
            val saved = repo.observeMoneyReminders().first().single()
            assertEquals("Renew road tax", saved.title)
            assertEquals(CommitmentFrequency.YEARLY, saved.repeat)
            repo.deleteMoneyReminder(id)
            assertTrue(repo.observeMoneyReminders().first().isEmpty())
            assertEquals(12_000L, db.walletDao().observeAll().first().single().balanceSen)
            assertTrue(db.transactionDao().observeAll().first().isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun reminders_projectOnTheirDates_withoutMoney() {
        val reminders = listOf(
            reminder(1, day(10, 31), CommitmentFrequency.MONTHLY).toDomain(), // month-end anchor: 30 Nov
            reminder(2, day(11, 3), CommitmentFrequency.WEEKLY).toDomain(),
            reminder(3, day(12, 1)).toDomain(),                                // next month only
            MoneyReminder(4, "Dup", day(11, 9)), MoneyReminder(4, "Dup", day(11, 9))
        )
        val m = CalendarEventProjector.project(YearMonth.of(2026, 11), emptyList(), emptyList(), emptyList(), emptyList(),
            ZoneId.of("Asia/Kuala_Lumpur"), reminders)
        val events = m.eventsByDay.values.flatten()
        assertTrue(events.all { it.type == CalendarEventType.REMINDER })
        assertEquals(listOf(day(11, 30)), events.filter { it.key.startsWith("reminder:1:") }.map { it.epochDay })
        assertEquals(listOf(3, 10, 17, 24).map { day(11, it) }, events.filter { it.key.startsWith("reminder:2:") }.map { it.epochDay })
        assertEquals(1, events.count { it.key.startsWith("reminder:4:") })
        assertTrue(events.none { it.key.startsWith("reminder:3:") })
        events.forEach { assertNull(it.amountSen) }
        assertTrue(m.summaries.isEmpty())
    }
}
