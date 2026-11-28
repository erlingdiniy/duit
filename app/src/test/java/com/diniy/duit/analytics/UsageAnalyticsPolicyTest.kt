package com.diniy.duit.analytics

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
class UsageAnalyticsPolicyTest {

    @Test
    fun allNineteenFeaturesAreAllowlistedAndProduceCleanParams() {
        assertEquals(19, AnalyticsFeature.entries.size)
        for (feature in AnalyticsFeature.entries) {
            val params = UsageAnalyticsPolicy.buildFeatureUsedParams(
                feature = feature,
                versionName = "1.2.0-beta.4",
                isPrivateDev = false,
                isQaTest = false
            )
            assertEquals(feature.key, params["feature"])
            assertEquals("beta", params["release_channel"])
            assertNull(params["traffic_type"])
            assertTrue(params.keys.all { it in UsageAnalyticsPolicy.ALLOWED_PARAM_KEYS })
        }
    }

    @Test
    fun releaseChannelsAndInternalTrafficMarkersStaySeparated() {
        assertEquals("stable", UsageAnalyticsPolicy.resolveReleaseChannel("1.2.0", isPrivateDev = false))
        assertEquals("beta", UsageAnalyticsPolicy.resolveReleaseChannel("1.3.0-beta.2", isPrivateDev = false))
        assertEquals("private_dev", UsageAnalyticsPolicy.resolveReleaseChannel("1.2.0-dev.4", isPrivateDev = true))

        assertNull(UsageAnalyticsPolicy.resolveTrafficType(isPrivateDev = false, isQaTest = false))
        assertEquals("internal", UsageAnalyticsPolicy.resolveTrafficType(isPrivateDev = false, isQaTest = true))
        assertEquals("internal", UsageAnalyticsPolicy.resolveTrafficType(isPrivateDev = true, isQaTest = false))

        val internalUpdate = UsageAnalyticsPolicy.buildAppUpdatedParams(
            versionName = "1.2.0-dev.4",
            isPrivateDev = true,
            isQaTest = false
        )
        assertEquals("private_dev", internalUpdate["release_channel"])
        assertEquals("1.2.0-dev.4", internalUpdate["app_version"])
        assertEquals("internal", internalUpdate["traffic_type"])

        val defaultPrivateDev = UsageAnalyticsPolicy.buildDefaultEventParams(
            versionName = "1.2.0-dev.5",
            isPrivateDev = true,
            isQaTest = false
        )
        assertEquals(mapOf("release_channel" to "private_dev", "traffic_type" to "internal"), defaultPrivateDev)

        val defaultPublicBeta = UsageAnalyticsPolicy.buildDefaultEventParams(
            versionName = "1.3.0-beta.2",
            isPrivateDev = false,
            isQaTest = false
        )
        assertEquals(mapOf("release_channel" to "beta"), defaultPublicBeta)
        assertFalse(defaultPublicBeta.containsKey("traffic_type"))
    }

    @Test
    fun sanitizerRejectsUnknownKeysAmountsTokensEmailsAndLongValues() {
        val dirty = mapOf(
            "feature" to "home",
            "amount" to "RM 1,500.00",
            "wallet_name" to "Maybank",
            "result" to "user@example.com",
            "app_version" to "1.2.0/github_pat_11SECRET",
            "release_channel" to "beta",
            "extra_url" to "https://example.com"
        )
        val clean = UsageAnalyticsPolicy.sanitizeParams(dirty)
        assertEquals(mapOf("feature" to "home", "release_channel" to "beta"), clean)
        assertFalse(clean.containsKey("amount"))
        assertFalse(clean.containsKey("wallet_name"))
        assertFalse(clean.containsKey("result"))
        assertFalse(clean.containsKey("app_version"))

        val oversized = UsageAnalyticsPolicy.sanitizeParams(
            mapOf("feature" to "x".repeat(45))
        )
        assertTrue(oversized.isEmpty())

        val validTheme = UsageAnalyticsPolicy.buildThemeSelectedParams(
            themeKey = "liquid_glass",
            versionName = "1.3.0",
            isPrivateDev = false,
            isQaTest = false
        )
        assertEquals(mapOf("theme" to "liquid_glass", "release_channel" to "stable"), validTheme)
        val invalidTheme = UsageAnalyticsPolicy.buildThemeSelectedParams(
            themeKey = "RM_SECRET_THEME",
            versionName = "1.3.0",
            isPrivateDev = true,
            isQaTest = false
        )
        assertFalse(invalidTheme.containsKey("theme"))
    }

    @Test
    fun consentDefaultsOff_isIndependentFromCrashConsent_andExcludedFromBackup() {
        val defaultSettings = AppSettingsSnapshot()
        assertFalse(defaultSettings.usageAnalyticsEnabled)
        assertFalse(defaultSettings.crashReportingEnabled)

        val independentCrashOnly = defaultSettings.copy(crashReportingEnabled = true, usageAnalyticsEnabled = false)
        assertTrue(independentCrashOnly.crashReportingEnabled)
        assertFalse(independentCrashOnly.usageAnalyticsEnabled)

        val snapshotWithAnalytics = AppSettingsSnapshot(usageAnalyticsEnabled = true, crashReportingEnabled = false)
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
                appSettings = snapshotWithAnalytics,
                notificationSettings = NotificationSettingsSnapshot(),
                privacyPreferences = PortablePrivacyPreferences()
            )
        )
        assertFalse(json.contains("usageAnalyticsEnabled"))
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
        assertFalse(decoded.preferences.appSettings.usageAnalyticsEnabled)
    }
}
