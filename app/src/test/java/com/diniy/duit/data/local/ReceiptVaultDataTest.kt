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
import com.diniy.duit.data.local.entity.WalletEntity
import com.diniy.duit.data.receipt.ReceiptFileStorage
import com.diniy.duit.data.repository.OfflineFirstFinanceRepository
import com.diniy.duit.data.repository.OfflineFirstReceiptRepository
import com.diniy.duit.domain.model.AppSettingsSnapshot
import com.diniy.duit.domain.model.FinanceTransaction
import com.diniy.duit.domain.model.PrismaticAccentToken
import com.diniy.duit.domain.model.TransactionType
import com.diniy.duit.domain.model.WalletType
import com.diniy.duit.domain.receipt.ReceiptRecord
import com.diniy.duit.domain.receipt.ReceiptTaxTag
import com.diniy.duit.notifications.NotificationSettingsSnapshot
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReceiptVaultDataTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun memoryDb(): DuitDatabase =
        Room.inMemoryDatabaseBuilder(context, DuitDatabase::class.java).allowMainThreadQueries().build()

    private fun tempStorage(): Pair<File, ReceiptFileStorage> {
        val dir = File(context.cacheDir, "receipt_test_${System.nanoTime()}").apply { mkdirs() }
        return dir to ReceiptFileStorage(dir)
    }

    @Test
    fun migration11To12_preservesExistingData_andCreatesReceiptsTable() = runBlocking {
        val schemaFile = File("schemas/com.diniy.duit.data.local.DuitDatabase/11.json")
            .takeIf { it.exists() }
            ?: File("app/schemas/com.diniy.duit.data.local.DuitDatabase/11.json")
        assertTrue("Expected v11 schema at ${schemaFile.absolutePath}", schemaFile.exists())
        val schema = JSONObject(schemaFile.readText()).getJSONObject("database")
        val entities = schema.getJSONArray("entities")
        val dbFile = File(context.cacheDir, "mig11to12_${System.nanoTime()}.db")

        FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbFile.absolutePath)
                .callback(object : SupportSQLiteOpenHelper.Callback(11) {
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
                        db.execSQL("INSERT INTO wallets VALUES (1, 'MAE', 'BANK', 250000, 0, 'AQUA', 1000, 1000, 1, 0)")
                        db.execSQL(
                            "INSERT INTO transactions (id, walletId, targetWalletId, type, amountSen, category, title, note, commitmentId, occurredAtEpochMs, createdAtEpochMs, categoryId, submissionId) " +
                                "VALUES (10, 1, NULL, 'EXPENSE', 4500, 'Medical', 'Clinic Visit', '', NULL, 1700000000000, 1700000000000, NULL, NULL)"
                        )
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build()
        ).apply { writableDatabase.close(); close() }

        val (dir, storage) = tempStorage()
        val db = Room.databaseBuilder(context, DuitDatabase::class.java, dbFile.absolutePath)
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .addMigrations(DuitDatabase.MIGRATION_11_12, DuitDatabase.MIGRATION_12_13)
            .allowMainThreadQueries()
            .build()
        try {
            val financeRepo = OfflineFirstFinanceRepository(db)
            val receiptRepo = OfflineFirstReceiptRepository(db.receiptDao(), storage)

            assertEquals(250_000L, financeRepo.observeWallets().first().single().balanceSen)
            assertEquals(10L, financeRepo.observeTransactions().first().single().id)

            val savedId = receiptRepo.saveReceipt(
                ReceiptRecord(
                    transactionId = 10L,
                    merchant = "Klinik Sejahtera",
                    dateEpochDay = 20400L,
                    amountSen = 4500L,
                    category = "Medical",
                    note = "Fever consult",
                    taxTag = ReceiptTaxTag.MEDICAL
                )
            )
            assertTrue(savedId > 0L)
            val loaded = receiptRepo.observeReceipts().first().single()
            assertEquals("Klinik Sejahtera", loaded.merchant)
            assertEquals(10L, loaded.transactionId)
            assertEquals(ReceiptTaxTag.MEDICAL, loaded.taxTag)
            // Wallet balance never changes when saving receipts.
            assertEquals(250_000L, financeRepo.observeWallets().first().single().balanceSen)
        } finally {
            db.close()
            dbFile.delete()
            dir.deleteRecursively()
        }
    }

    @Test
    fun deletingLinkedTransaction_keepsReceiptAsStandalone_andDeletingReceiptDeletesFileOnly() = runBlocking {
        val db = memoryDb()
        val (dir, storage) = tempStorage()
        try {
            val financeRepo = OfflineFirstFinanceRepository(db)
            val receiptRepo = OfflineFirstReceiptRepository(db.receiptDao(), storage)
            val walletId = db.walletDao().upsert(
                WalletEntity(
                    name = "Cash",
                    type = WalletType.CASH,
                    balanceSen = 100_000L,
                    isExcludedFromSafeToSpend = false,
                    accentToken = PrismaticAccentToken.AQUA,
                    createdAtEpochMs = 1L,
                    updatedAtEpochMs = 1L
                )
            )
            financeRepo.recordTransaction(
                FinanceTransaction(
                    id = 0L,
                    walletId = walletId,
                    targetWalletId = null,
                    type = TransactionType.EXPENSE,
                    amountSen = 2_500L,
                    category = "Food",
                    title = "Nasi Lemak",
                    note = "",
                    commitmentId = null,
                    occurredAtEpochMs = 1_700_000_000_000L,
                    createdAtEpochMs = 1_700_000_000_000L
                )
            )
            val txId = financeRepo.observeTransactions().first().single().id
            assertEquals(97_500L, financeRepo.observeWallets().first().single().balanceSen)

            assertTrue(storage.writeRestoredFile("receipt_demo.jpg", byteArrayOf(1, 2, 3, 4)))
            val receiptId = receiptRepo.saveReceipt(
                ReceiptRecord(
                    transactionId = txId,
                    merchant = "Warung Mak",
                    dateEpochDay = 20400L,
                    amountSen = 2_500L,
                    fileRelativePath = "receipt_demo.jpg",
                    mimeType = "image/jpeg",
                    originalFileName = "warung.jpg",
                    fileSizeBytes = 4L
                )
            )

            // Deleting transaction must preserve receipt as standalone (ON DELETE SET NULL).
            financeRepo.deleteTransaction(txId)
            val afterTxDelete = receiptRepo.observeReceipts().first().single()
            assertNull(afterTxDelete.transactionId)
            assertEquals(100_000L, financeRepo.observeWallets().first().single().balanceSen)

            // Deleting receipt deletes private file and leaves wallet balance unchanged.
            receiptRepo.deleteReceipt(receiptId)
            assertTrue(receiptRepo.observeReceipts().first().isEmpty())
            assertNull(storage.resolveFile("receipt_demo.jpg"))
            assertEquals(100_000L, financeRepo.observeWallets().first().single().balanceSen)
        } finally {
            db.close()
            dir.deleteRecursively()
        }
    }

    @Test
    fun encryptedBackupRestore_roundTripsMetadataAndFiles_andBackwardCompatV11() = runBlocking {
        val db = memoryDb()
        val (dir, storage) = tempStorage()
        val (restoreDir, restoreStorage) = tempStorage()
        try {
            val financeRepo = OfflineFirstFinanceRepository(db)
            val receiptRepo = OfflineFirstReceiptRepository(db.receiptDao(), storage)
            val walletId = db.walletDao().upsert(
                WalletEntity(
                    name = "Bank",
                    type = WalletType.BANK,
                    balanceSen = 50_000L,
                    isExcludedFromSafeToSpend = false,
                    accentToken = PrismaticAccentToken.AQUA,
                    createdAtEpochMs = 1L,
                    updatedAtEpochMs = 1L
                )
            )
            financeRepo.recordTransaction(
                FinanceTransaction(
                    id = 0L,
                    walletId = walletId,
                    targetWalletId = null,
                    type = TransactionType.EXPENSE,
                    amountSen = 12_000L,
                    category = "Education",
                    title = "Bookstore",
                    note = "",
                    commitmentId = null,
                    occurredAtEpochMs = 1_700_000_000_000L,
                    createdAtEpochMs = 1_700_000_000_000L
                )
            )
            val txId = financeRepo.observeTransactions().first().single().id
            val pdfBytes = "%PDF-1.4\n%receipt".encodeToByteArray()
            assertTrue(storage.writeRestoredFile("receipt_books.pdf", pdfBytes))

            receiptRepo.saveReceipt(
                ReceiptRecord(
                    transactionId = txId,
                    merchant = "Popular Bookstore",
                    dateEpochDay = 20405L,
                    amountSen = 12_000L,
                    category = "Education",
                    note = "Textbooks",
                    taxTag = ReceiptTaxTag.LIFESTYLE,
                    fileRelativePath = "receipt_books.pdf",
                    mimeType = "application/pdf",
                    originalFileName = "books.pdf",
                    fileSizeBytes = pdfBytes.size.toLong()
                )
            )
            receiptRepo.saveReceipt(
                ReceiptRecord(
                    transactionId = null,
                    merchant = "Standalone Warranty",
                    dateEpochDay = 20406L,
                    amountSen = null,
                    taxTag = ReceiptTaxTag.NONE
                )
            )

            val snap = db.backupDao().exportFullSnapshot()
            val files = mapOf("receipt_books.pdf" to storage.readFileBytes("receipt_books.pdf")!!)
            val prefs = PortablePreferencesBackup(
                AppSettingsSnapshot(),
                NotificationSettingsSnapshot(),
                PortablePrivacyPreferences()
            )
            val json = BackupSerializer.encodePayload(snap, prefs, createdAtEpochMs = 5000L, receiptFiles = files)
            val encrypted = BackupCryptoEngine.encrypt(json, "Secret123!".toCharArray())

            val header = BackupCryptoEngine.inspectEnvelope(encrypted)
            val plain = BackupCryptoEngine.decrypt(encrypted, "Secret123!".toCharArray())
            val bundle = BackupSerializer.decodePayload(header, plain)

            assertEquals(BackupCryptoEngine.CURRENT_DB_SCHEMA_VERSION, bundle.preview.dbSchemaVersion)
            assertEquals(2, bundle.preview.receiptCount)
            assertArrayEquals(pdfBytes, bundle.receiptFiles["receipt_books.pdf"])

            // Restore into clean DB + clean storage
            val db2 = memoryDb()
            try {
                db2.backupDao().replaceAllLedgerDataAtomically(bundle.ledger)
                restoreStorage.replaceAllFiles(bundle.receiptFiles)
                val restoredReceipts = db2.receiptDao().observeAll().first()
                assertEquals(2, restoredReceipts.size)
                assertArrayEquals(pdfBytes, restoreStorage.readFileBytes("receipt_books.pdf"))
            } finally {
                db2.close()
            }

            // Old v11 backup (without receipts / receiptFiles) restores cleanly
            val v11Json = JSONObject(json).apply {
                put("dbSchemaVersion", 11)
                getJSONObject("ledger").remove("receipts")
                remove("receiptFiles")
            }
            val v11Bytes = BackupCryptoEngine.encrypt(v11Json.toString(), "Secret123!".toCharArray(), dbSchemaVersion = 11)
            val v11Header = BackupCryptoEngine.inspectEnvelope(v11Bytes)
            val v11Bundle = BackupSerializer.decodePayload(v11Header, BackupCryptoEngine.decrypt(v11Bytes, "Secret123!".toCharArray()))
            assertTrue(v11Bundle.ledger.receipts.isEmpty())
            assertTrue(v11Bundle.receiptFiles.isEmpty())

            // Dangling transactionId must be rejected by relational validation
            val badJson = JSONObject(json).apply {
                getJSONObject("ledger").getJSONArray("receipts").getJSONObject(0).put("transactionId", 999999L)
            }
            val badBytes = BackupCryptoEngine.encrypt(badJson.toString(), "Secret123!".toCharArray())
            val badHeader = BackupCryptoEngine.inspectEnvelope(badBytes)
            val threw = runCatching {
                BackupSerializer.decodePayload(badHeader, BackupCryptoEngine.decrypt(badBytes, "Secret123!".toCharArray()))
            }.exceptionOrNull()
            assertTrue(threw is BackupFormatException)
        } finally {
            db.close()
            dir.deleteRecursively()
            restoreDir.deleteRecursively()
        }
    }

    @Test
    fun receiptFileStorage_rejectsPathTraversal() {
        val (dir, storage) = tempStorage()
        try {
            assertNull(storage.sanitizeFileName("../secret.db"))
            assertNull(storage.sanitizeFileName("sub/file.jpg"))
            assertNull(storage.sanitizeFileName("..\\win.jpg"))
            assertFalse(storage.writeRestoredFile("../escape.jpg", byteArrayOf(1, 2, 3)))
            assertEquals("receipt_ok.jpg", storage.sanitizeFileName("receipt_ok.jpg"))
        } finally {
            dir.deleteRecursively()
        }
    }
}
