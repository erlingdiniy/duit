package com.diniy.duit.feature.tax

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.diniy.duit.backup.BackupCryptoEngine
import com.diniy.duit.backup.BackupFormatException
import com.diniy.duit.backup.BackupSerializer
import com.diniy.duit.backup.PortablePreferencesBackup
import com.diniy.duit.backup.PortablePrivacyPreferences
import com.diniy.duit.core.ui.state.LocalPrivacySettings
import com.diniy.duit.core.ui.state.PrivacySettings
import com.diniy.duit.core.ui.theme.DuitTheme
import com.diniy.duit.data.local.DuitDatabase
import com.diniy.duit.data.local.entity.ReceiptEntity
import com.diniy.duit.data.local.entity.TaxReliefEntryEntity
import com.diniy.duit.data.local.entity.WalletEntity
import com.diniy.duit.data.receipt.ReceiptFileStorage
import com.diniy.duit.data.repository.OfflineFirstReceiptRepository
import com.diniy.duit.data.repository.OfflineFirstTaxReliefRepository
import com.diniy.duit.domain.model.AppSettingsSnapshot
import com.diniy.duit.notifications.NotificationSettingsSnapshot
import com.diniy.duit.domain.model.PrismaticAccentToken
import com.diniy.duit.domain.model.ThemeMode
import com.diniy.duit.domain.model.WalletType
import com.diniy.duit.domain.receipt.ReceiptRecord
import com.diniy.duit.domain.receipt.ReceiptTaxTag
import com.diniy.duit.domain.tax.TaxReliefCalculator
import com.diniy.duit.domain.tax.TaxReliefCatalog
import com.diniy.duit.domain.tax.TaxReliefEntry
import com.diniy.duit.domain.tax.TaxReliefReviewStatus
import com.diniy.duit.domain.tax.TaxSummaryCsvBuilder
import java.io.File
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MyTaxPocketTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun memoryDb(): DuitDatabase =
        Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DuitDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    @Test
    fun catalog_enforcesVerifiedLhdnYearSpecificRules() {
        val ya2024 = TaxReliefCatalog.categoriesForYear(2024).associateBy { it.key }
        val ya2025 = TaxReliefCatalog.categoriesForYear(2025).associateBy { it.key }
        val ya2026 = TaxReliefCatalog.categoriesForYear(2026).associateBy { it.key }

        // Education & Medical Insurance raised from RM3,000 (YA2024) to RM4,000 (YA2025+)
        assertEquals(300_000L, ya2024["EDUCATION_MEDICAL_INSURANCE"]?.maxLimitSen)
        assertEquals(400_000L, ya2025["EDUCATION_MEDICAL_INSURANCE"]?.maxLimitSen)
        assertEquals(400_000L, ya2026["EDUCATION_MEDICAL_INSURANCE"]?.maxLimitSen)

        // First Home Loan Interest introduced YA2025+ (absent in YA2024)
        assertNull(ya2024["FIRST_HOME_LOAN_INTEREST"])
        assertEquals(700_000L, ya2025["FIRST_HOME_LOAN_INTEREST"]?.maxLimitSen)
        assertEquals(700_000L, ya2026["FIRST_HOME_LOAN_INTEREST"]?.maxLimitSen)

        // Domestic Tourism introduced YA2026 only
        assertNull(ya2024["DOMESTIC_TOURISM"])
        assertNull(ya2025["DOMESTIC_TOURISM"])
        assertEquals(100_000L, ya2026["DOMESTIC_TOURISM"]?.maxLimitSen)
    }

    @Test
    fun calculator_capsReadyItems_separatesPending_excludesExcluded_andNeverAutoClaimsReceiptTags() {
        val d2026 = LocalDate.of(2026, 5, 10).toEpochDay()
        val taggedReceipt = ReceiptRecord(
            id = 77L,
            merchant = "Clinic Jaya",
            dateEpochDay = d2026,
            amountSen = 35_000L,
            taxTag = ReceiptTaxTag.MEDICAL
        )
        val entries = listOf(
            // Partial eligible RM 1,800 out of RM 2,000 spent (READY)
            TaxReliefEntry(
                id = 1L,
                assessmentYear = 2026,
                categoryKey = "LIFESTYLE",
                title = "Laptop",
                dateEpochDay = d2026,
                totalSpentSen = 200_000L,
                eligibleAmountSen = 180_000L,
                reviewStatus = TaxReliefReviewStatus.READY
            ),
            // Another RM 1,200 READY -> raw RM 3,000, capped at RM 2,500 (overCap RM 500)
            TaxReliefEntry(
                id = 2L,
                assessmentYear = 2026,
                categoryKey = "LIFESTYLE",
                title = "Books",
                dateEpochDay = d2026,
                totalSpentSen = 120_000L,
                eligibleAmountSen = 120_000L,
                reviewStatus = TaxReliefReviewStatus.READY
            ),
            // NEEDS_REVIEW stays out of readyCappedSen
            TaxReliefEntry(
                id = 3L,
                assessmentYear = 2026,
                categoryKey = "SPORTS",
                title = "Badminton",
                dateEpochDay = d2026,
                eligibleAmountSen = 25_000L,
                reviewStatus = TaxReliefReviewStatus.NEEDS_REVIEW
            ),
            // EXCLUDED ignored in both ready and pending
            TaxReliefEntry(
                id = 4L,
                assessmentYear = 2026,
                categoryKey = "SPORTS",
                title = "Snack bar",
                dateEpochDay = d2026,
                eligibleAmountSen = 10_000L,
                reviewStatus = TaxReliefReviewStatus.EXCLUDED
            )
        )

        val summary = TaxReliefCalculator.summarize(2026, entries, listOf(taggedReceipt))
        val lifestyle = summary.categories.first { it.spec.key == "LIFESTYLE" }
        val sports = summary.categories.first { it.spec.key == "SPORTS" }
        val medical = summary.categories.first { it.spec.key == "MEDICAL_SELF_FAMILY" }

        assertEquals(300_000L, lifestyle.readyRawSen)
        assertEquals(250_000L, lifestyle.readyCappedSen)
        assertEquals(50_000L, lifestyle.overCapSen)
        assertEquals(0L, lifestyle.remainingCapSen)

        assertEquals(0L, sports.readyCappedSen)
        assertEquals(25_000L, sports.pendingRawSen)
        assertEquals(1, sports.excludedCount)

        // Tagged receipt is NEVER auto-claimed into medical ready total; only surfaced as suggestion
        assertEquals(0L, medical.readyCappedSen)
        assertEquals(250_000L, summary.totalReadyCappedSen)
        assertEquals(25_000L, summary.totalPendingSen)
        assertEquals(1, summary.unlinkedTaggedReceipts.size)
        assertEquals(77L, summary.unlinkedTaggedReceipts.single().id)

        // CSV includes disclaimer and capped totals
        val csv = TaxSummaryCsvBuilder.buildCsv(summary, mapOf(77L to taggedReceipt))
        assertTrue(csv.contains("YA 2026"))
        assertTrue(csv.contains("2500.00"))
        assertTrue(csv.contains("Verify eligibility with LHDN MyTax"))
    }

    @Test
    fun repository_validatesBounds_andReceiptDeletionUnlinksTaxEntryWithoutDeletingIt() = runBlocking {
        val db = memoryDb()
        val tempDir = File(ApplicationProvider.getApplicationContext<Context>().cacheDir, "tax_test_${UUID.randomUUID()}")
        try {
            val taxRepo = OfflineFirstTaxReliefRepository(db.taxReliefDao())
            val receiptRepo = OfflineFirstReceiptRepository(db.receiptDao(), ReceiptFileStorage(tempDir))
            val day = LocalDate.of(2026, 6, 1).toEpochDay()

            // Rejects eligible > totalSpent
            val bad = runCatching {
                taxRepo.saveEntry(
                    TaxReliefEntry(
                        assessmentYear = 2026,
                        categoryKey = "LIFESTYLE",
                        title = "Invalid",
                        dateEpochDay = day,
                        totalSpentSen = 10_000L,
                        eligibleAmountSen = 15_000L
                    )
                )
            }
            assertTrue(bad.isFailure)

            // Rejects YA-invalid category (DOMESTIC_TOURISM not valid in YA 2024)
            val wrongYear = runCatching {
                taxRepo.saveEntry(
                    TaxReliefEntry(
                        assessmentYear = 2024,
                        categoryKey = "DOMESTIC_TOURISM",
                        title = "Museum",
                        dateEpochDay = LocalDate.of(2024, 6, 1).toEpochDay(),
                        eligibleAmountSen = 5_000L
                    )
                )
            }
            assertTrue(wrongYear.isFailure)

            // Link receipt -> delete receipt -> tax entry survives with receiptId == null
            val receiptId = receiptRepo.saveReceipt(
                ReceiptRecord(
                    merchant = "Popular",
                    dateEpochDay = day,
                    amountSen = 15_000L,
                    taxTag = ReceiptTaxTag.LIFESTYLE
                )
            )
            val taxId = taxRepo.saveEntry(
                TaxReliefEntry(
                    assessmentYear = 2026,
                    categoryKey = "LIFESTYLE",
                    title = "Books",
                    dateEpochDay = day,
                    totalSpentSen = 15_000L,
                    eligibleAmountSen = 12_000L,
                    receiptId = receiptId,
                    reviewStatus = TaxReliefReviewStatus.READY
                )
            )
            receiptRepo.deleteReceipt(receiptId)
            val saved = taxRepo.observeAllEntries().first().single { it.id == taxId }
            assertNull(saved.receiptId)
            assertEquals(12_000L, saved.eligibleAmountSen)
        } finally {
            db.close()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun migration13to14_preservesExistingData_andCreatesTaxReliefTable() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "mig_13_14_${UUID.randomUUID()}.db"
        val dbFile = ctx.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()

        // Create v13 schema from exported 13.json
        val schemaJson = JSONObject(File("schemas/com.diniy.duit.data.local.DuitDatabase/13.json").readText())
        val entities = schemaJson.getJSONObject("database").getJSONArray("entities")
        val sqlite = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        try {
            sqlite.version = 13
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                val tableName = e.getString("tableName")
                val createSql = e.getString("createSql").replace("\${TABLE_NAME}", tableName)
                sqlite.execSQL(createSql)
                val indices = e.optJSONArray("indices")
                if (indices != null) {
                    for (j in 0 until indices.length()) {
                        val idxSql = indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", tableName)
                        sqlite.execSQL(idxSql)
                    }
                }
            }
            sqlite.execSQL(
                "INSERT INTO wallets VALUES (1, 'Main', 'CASH', 153800, 0, 'CORAL', 1, 1, 1, 0)"
            )
        } finally {
            sqlite.close()
        }

        val migrated = Room.databaseBuilder(ctx, DuitDatabase::class.java, dbName)
            .addMigrations(DuitDatabase.MIGRATION_13_14)
            .allowMainThreadQueries()
            .build()
        try {
            val wallets = migrated.walletDao().observeAll().first()
            assertEquals(1, wallets.size)
            assertEquals(153_800L, wallets.single().balanceSen)

            val insertedId = migrated.taxReliefDao().upsert(
                TaxReliefEntryEntity(
                    assessmentYear = 2026,
                    categoryKey = "DOMESTIC_TOURISM",
                    title = "National Museum",
                    dateEpochDay = LocalDate.of(2026, 8, 1).toEpochDay(),
                    totalSpentSen = 4_000L,
                    eligibleAmountSen = 4_000L,
                    receiptId = null,
                    reviewStatus = "READY",
                    note = "Family ticket",
                    createdAtEpochMs = 10L,
                    updatedAtEpochMs = 10L
                )
            )
            assertTrue(insertedId > 0L)
            assertEquals(1, migrated.taxReliefDao().observeAll().first().size)
        } finally {
            migrated.close()
            dbFile.delete()
        }
    }

    @Test
    fun encryptedBackupRestoreV14_roundTripsTaxEntries_backwardCompatV13_andRejectsDanglingReceipt() = runBlocking {
        val db = memoryDb()
        try {
            db.walletDao().upsert(
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
            val receiptId = db.receiptDao().insert(
                ReceiptEntity(
                    transactionId = null,
                    merchant = "Dental Care",
                    dateEpochDay = LocalDate.of(2026, 3, 12).toEpochDay(),
                    amountSen = 30_000L,
                    category = "Medical",
                    note = "",
                    taxTag = "MEDICAL",
                    fileRelativePath = "",
                    mimeType = "",
                    originalFileName = "",
                    fileSizeBytes = 0L,
                    createdAtEpochMs = 1L,
                    updatedAtEpochMs = 1L
                )
            )
            db.taxReliefDao().upsert(
                TaxReliefEntryEntity(
                    assessmentYear = 2026,
                    categoryKey = "MEDICAL_SELF_FAMILY",
                    title = "Dental scaling",
                    dateEpochDay = LocalDate.of(2026, 3, 12).toEpochDay(),
                    totalSpentSen = 30_000L,
                    eligibleAmountSen = 25_000L,
                    receiptId = receiptId,
                    reviewStatus = "READY",
                    note = "Partial eligible",
                    createdAtEpochMs = 2L,
                    updatedAtEpochMs = 2L
                )
            )

            val snap = db.backupDao().exportFullSnapshot()
            val prefs = PortablePreferencesBackup(
                AppSettingsSnapshot(),
                NotificationSettingsSnapshot(),
                PortablePrivacyPreferences()
            )
            val json = BackupSerializer.encodePayload(snap, prefs, createdAtEpochMs = 9999L)
            val encrypted = BackupCryptoEngine.encrypt(json, "Secret123!".toCharArray())
            val header = BackupCryptoEngine.inspectEnvelope(encrypted)
            val bundle = BackupSerializer.decodePayload(header, BackupCryptoEngine.decrypt(encrypted, "Secret123!".toCharArray()))

            assertEquals(BackupCryptoEngine.CURRENT_DB_SCHEMA_VERSION, bundle.preview.dbSchemaVersion)
            assertEquals(1, bundle.ledger.taxReliefEntries.size)
            assertEquals(25_000L, bundle.ledger.taxReliefEntries.single().eligibleAmountSen)

            // Restore into fresh DB
            val db2 = memoryDb()
            try {
                db2.backupDao().replaceAllLedgerDataAtomically(bundle.ledger)
                val restored = db2.taxReliefDao().observeAll().first()
                assertEquals(1, restored.size)
                assertEquals(receiptId, restored.single().receiptId)
            } finally {
                db2.close()
            }

            // Backward compatibility with v13 backup (no taxReliefEntries key)
            val v13Json = JSONObject(json).apply {
                put("dbSchemaVersion", 13)
                getJSONObject("ledger").remove("taxReliefEntries")
            }
            val v13Bytes = BackupCryptoEngine.encrypt(v13Json.toString(), "Secret123!".toCharArray(), dbSchemaVersion = 13)
            val v13Bundle = BackupSerializer.decodePayload(
                BackupCryptoEngine.inspectEnvelope(v13Bytes),
                BackupCryptoEngine.decrypt(v13Bytes, "Secret123!".toCharArray())
            )
            assertTrue(v13Bundle.ledger.taxReliefEntries.isEmpty())

            // Dangling receiptId rejected
            val badJson = JSONObject(json).apply {
                getJSONObject("ledger").getJSONArray("taxReliefEntries").getJSONObject(0).put("receiptId", 99999L)
            }
            val badBytes = BackupCryptoEngine.encrypt(badJson.toString(), "Secret123!".toCharArray())
            val failed = runCatching {
                BackupSerializer.decodePayload(
                    BackupCryptoEngine.inspectEnvelope(badBytes),
                    BackupCryptoEngine.decrypt(badBytes, "Secret123!".toCharArray())
                )
            }
            assertTrue(failed.exceptionOrNull() is BackupFormatException)
        } finally {
            db.close()
        }
    }

    @Test
    fun ui_rendersMaskedAmountsInPrivacyMode_andYearCategories() {
        val summary = TaxReliefCalculator.summarize(
            2026,
            listOf(
                TaxReliefEntry(
                    id = 1L,
                    assessmentYear = 2026,
                    categoryKey = "LIFESTYLE",
                    title = "Tech book",
                    dateEpochDay = LocalDate.of(2026, 2, 1).toEpochDay(),
                    eligibleAmountSen = 12_000L,
                    reviewStatus = TaxReliefReviewStatus.READY
                )
            ),
            emptyList()
        )
        val binding = TaxPocketBinding(
            state = TaxPocketUiState(isLoading = false, selectedYear = 2026, summary = summary),
            onSelectYear = {},
            onSaveEntry = { _, _, _ -> },
            onUpdateStatus = { _, _ -> },
            onDeleteEntry = {}
        )

        composeRule.setContent {
            val baseDensity = androidx.compose.ui.platform.LocalDensity.current
            DuitTheme(themeMode = ThemeMode.DARK) {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(
                        density = baseDensity.density,
                        fontScale = 2.0f
                    ),
                    LocalPrivacySettings provides PrivacySettings(privacyModeEnabled = true)
                ) {
                    MyTaxPocketScreen(binding = binding, onClose = {})
                }
            }
        }

        composeRule.onNodeWithText("My Tax Pocket").assertIsDisplayed()
        composeRule.onAllNodesWithText("YA 2026")[0].assertIsDisplayed()
        composeRule.onAllNodesWithText("1 item")[0].assertIsDisplayed()
        composeRule.onNodeWithText("Lifestyle").assertIsDisplayed()
        // Privacy Mode masks RM 120.00
        assertFalse(runCatching { composeRule.onNodeWithText("RM 120.00").assertIsDisplayed() }.isSuccess)
    }
}
