package com.diniy.duit.diagnostics

import android.app.Application
import com.diniy.duit.backup.BackupCryptoEngine
import com.diniy.duit.backup.BackupEnvelopeHeader
import com.diniy.duit.backup.BackupSerializer
import com.diniy.duit.backup.PortablePreferencesBackup
import com.diniy.duit.backup.PortablePrivacyPreferences
import com.diniy.duit.data.local.dao.LedgerDatabaseSnapshot
import com.diniy.duit.domain.model.AppSettingsSnapshot
import com.diniy.duit.notifications.NotificationSettingsSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CrashReportingPolicyTest {

    @Test
    fun redactMessage_stripsFinancialCredentialsTokensAndPaths() {
        val raw = "Failed backup RM 6,538.00 /data/user/0/com.diniy.duit.beta/filesDir/receipts/tx_99.jpg " +
            "user@example.com github_pat_11SECRETVALUE_999 Bearer abc.def.ghi " +
            "https://objects.githubusercontent.com/asset?X-Amz-Signature=topsecret PIN 482910"
        val clean = CrashMetadataSanitizer.redactMessage(raw)

        assertFalse(clean.contains("6,538.00"))
        assertFalse(clean.contains("receipts/tx_99.jpg"))
        assertFalse(clean.contains("user@example.com"))
        assertFalse(clean.contains("github_pat_11"))
        assertFalse(clean.contains("abc.def.ghi"))
        assertFalse(clean.contains("objects.githubusercontent.com"))
        assertFalse(clean.contains("482910"))
        assertTrue(clean.contains("[REDACTED_AMOUNT]"))
        assertTrue(clean.contains("[REDACTED_EMAIL]"))
        assertTrue(clean.contains("[REDACTED_TOKEN]"))
        assertTrue(clean.length <= 100)
    }

    @Test
    fun sanitizeThrowable_preservesStackAndDropsRawCause() {
        val rootCause = IllegalStateException("Wallet Cash balance RM 7,638.00 mismatch")
        val outer = RuntimeException("Restore error for user@example.com", rootCause)
        val sanitized = CrashMetadataSanitizer.sanitizeThrowable(CrashDiagnosticCategory.RESTORE_ENGINE, outer)

        assertEquals(CrashDiagnosticCategory.RESTORE_ENGINE, sanitized.category)
        assertEquals("RuntimeException", sanitized.originalType)
        assertNull(sanitized.cause)
        assertFalse(sanitized.message.orEmpty().contains("user@example.com"))
        assertFalse(sanitized.message.orEmpty().contains("7,638.00"))
        assertTrue(sanitized.message.orEmpty().startsWith("RESTORE_ENGINE:RuntimeException:"))
        assertEquals(outer.stackTrace.size, sanitized.stackTrace.size)
    }

    @Test
    fun resolveReleaseChannel_distinguishesStableBetaAndPrivateDev() {
        assertEquals("stable", CrashMetadataSanitizer.resolveReleaseChannel("1.2.0", isPrivateDev = false))
        assertEquals("beta", CrashMetadataSanitizer.resolveReleaseChannel("1.3.0-beta.1", isPrivateDev = false))
        assertEquals("private_dev", CrashMetadataSanitizer.resolveReleaseChannel("1.2.0-dev.3", isPrivateDev = true))
    }

    @Test
    fun consentDefaultsOff_andBackupRestoreDoesNotEnableCrashReporting() {
        assertFalse(AppSettingsSnapshot().crashReportingEnabled)

        val snapshotWithOptIn = AppSettingsSnapshot(crashReportingEnabled = true)
        val emptyLedger = LedgerDatabaseSnapshot(
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
        val json = BackupSerializer.encodePayload(
            ledger = emptyLedger,
            preferences = PortablePreferencesBackup(
                appSettings = snapshotWithOptIn,
                notificationSettings = NotificationSettingsSnapshot(),
                privacyPreferences = PortablePrivacyPreferences()
            )
        )
        val header = BackupEnvelopeHeader(
            formatVersion = BackupCryptoEngine.CURRENT_FORMAT_VERSION,
            dbSchemaVersion = BackupCryptoEngine.CURRENT_DB_SCHEMA_VERSION,
            createdAtEpochMs = 0L,
            kdf = "PBKDF2WithHmacSHA256",
            iterations = 150_000,
            salt = ByteArray(0),
            iv = ByteArray(0),
            ciphertext = ByteArray(0)
        )
        val decoded = BackupSerializer.decodePayload(header, json)
        assertFalse(decoded.preferences.appSettings.crashReportingEnabled)
    }
}
