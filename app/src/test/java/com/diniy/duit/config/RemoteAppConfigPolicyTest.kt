package com.diniy.duit.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteAppConfigPolicyTest {

    @Test
    fun safeKeysAllowProductNoticesAndRejectFinancialOrBackupKeywords() {
        for (k in RemoteAppConfigPolicy.ALLOWED_KEYS) {
            assertTrue("Expected safe key $k", RemoteAppConfigPolicy.isSafeParameterKey(k))
        }
        listOf(
            "disable_wallets",
            "disable_backup_restore",
            "block_export_csv",
            "transaction_limit",
            "receipt_vault_disabled",
            "room_database_lock",
            "bypass_pin_check",
            "arbitrary_unknown_flag"
        ).forEach { unsafe ->
            assertFalse("Expected $unsafe to be blocked", RemoteAppConfigPolicy.isSafeParameterKey(unsafe))
        }
    }

    @Test
    fun sanitizerClampsRolloutPercentLengthsAndControlChars() {
        val snap = RemoteAppConfigPolicy.sanitizeSnapshot(
            maintenanceBannerEnabled = true,
            maintenanceBannerTitle = "  Service\u0007 Notice \n\t Title " + "x".repeat(120),
            maintenanceBannerMessage = "Body\u0000 " + "y".repeat(300),
            experimentalInsightsTipsEnabled = true,
            gradualRolloutNoticeEnabled = true,
            gradualRolloutPercent = 250,
            minRecommendedVersionCode = -42L,
            lastFetchEpochMs = 123L,
            source = "remote_activated"
        )
        assertTrue(snap.maintenanceBannerTitle.length <= 80)
        assertTrue(snap.maintenanceBannerMessage.length <= 240)
        assertFalse(snap.maintenanceBannerTitle.contains('\u0007'))
        assertEquals(100, snap.gradualRolloutPercent)
        assertEquals(0L, snap.minRecommendedVersionCode)
    }
}
