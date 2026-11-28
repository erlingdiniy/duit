package com.diniy.duit.devcenter

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.diniy.duit.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies the unified Public Edition (`beta`) build configuration:
 * - Ships under `com.diniy.duit.beta` with public OTA updates (`BetaUpdates`) enabled.
 * - Excludes Private Developer Edition artifacts (`PrivateDevUpdates`, `DevAnalyticsOAuthRedirectActivity`).
 * - Includes zero-secret Owner Mode (`DeveloperCenterEntryPoint`) hidden from ordinary users by default.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DeveloperCenterIsolationBetaTest {

    @Test
    fun unifiedBeta_preservesPublicPrivacy_andExcludesPrivateDevEditionAndSecrets() {
        assertTrue(BuildConfig.PUBLIC_EDITION)
        assertFalse(BuildConfig.PRIVATE_DEV_EDITION)
        assertTrue(BuildConfig.DEVELOPER_CENTER_ENABLED)
        assertTrue(BuildConfig.FCM_ENABLED)
        assertEquals("com.diniy.duit.beta", BuildConfig.APPLICATION_ID)
        assertTrue("Unified versionCode must exceed v1.4.0-dev.1", BuildConfig.VERSION_CODE >= 11_000_000)
        assertEquals("", BuildConfig.DEV_ANALYTICS_OAUTH_CLIENT_ID)
        assertTrue(DeveloperCenterEntryPoint.isAvailable)
        assertFalse(DeveloperCenterEntryPoint.isOwnerEnrolledFast())
        assertNull(DeveloperCenterEntryPoint.privacySummaryOrNull(BuildConfig.FCM_ENABLED))

        assertNotNullContext(ApplicationProvider.getApplicationContext<Application>())

        // Public updater MUST be included; Private-only updater & OAuth activity MUST be excluded.
        assertTrue(runCatching { Class.forName("com.diniy.duit.update.BetaUpdates") }.isSuccess)
        assertTrue(runCatching { Class.forName("com.diniy.duit.devcenter.DevBackendClient") }.isSuccess)
        assertTrue(runCatching { Class.forName("com.diniy.duit.devcenter.AndroidKeyStoreDeviceKeyManager") }.isSuccess)

        val excludedPrivateOnlyClasses = listOf(
            "com.diniy.duit.update.PrivateDevUpdates",
            "com.diniy.duit.update.PrivateDevUpdateModel",
            "com.diniy.duit.update.PrivateDevInstallStatusReceiver",
            "com.diniy.duit.devcenter.DevAnalyticsOAuthRedirectActivity"
        )
        for (className in excludedPrivateOnlyClasses) {
            val loaded = runCatching { Class.forName(className) }.isSuccess
            assertFalse("$className must remain excluded from unified public APK", loaded)
        }
    }

    private fun assertNotNullContext(app: Application) {
        assertTrue(app.packageName.isNotBlank())
    }
}
