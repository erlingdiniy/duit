package com.diniy.duit.devcenter

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.diniy.duit.BuildConfig
import com.diniy.duit.data.local.DuitDatabase
import com.diniy.duit.feature.settings.activeLicenseNotices
import com.diniy.duit.notifications.DeveloperBroadcastSpec
import com.diniy.duit.notifications.DeveloperBroadcasts
import com.diniy.duit.notifications.NotificationCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compile-time verification for the private `betaDev` build (`com.diniy.duit.beta`):
 * confirms v1.2.0 feature parity + Firebase FCM preservation + integrated Developer Center +
 * compile-time removal of the public GitHub in-app auto-updater.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BetaDevEditionVerificationTest {

    @Test
    fun betaDev_hasOriginalPackage_fcm_v120Parity_developerCenter_andNoPublicUpdater() {
        val context = ApplicationProvider.getApplicationContext<Application>()

        assertEquals("com.diniy.duit.beta", BuildConfig.APPLICATION_ID)
        assertTrue(BuildConfig.VERSION_NAME.endsWith("-dev"))
        assertFalse(BuildConfig.VERSION_NAME.contains("-beta."))
        assertTrue(BuildConfig.VERSION_CODE >= 1_002_000)

        assertTrue(BuildConfig.PUBLIC_EDITION)
        assertTrue(BuildConfig.OPTIONAL_COMMISSION)
        assertTrue(BuildConfig.FCM_ENABLED)
        assertTrue(BuildConfig.DEVELOPER_CENTER_ENABLED)
        assertTrue(BuildConfig.PRIVATE_DEV_EDITION)
        assertEquals("", BuildConfig.BETA_REPOSITORY)

        assertTrue(DeveloperCenterEntryPoint.isAvailable)
        assertNotNull(DeveloperCenterEntryPoint.privacySummaryOrNull(fcmEnabled = true))

        // Core Developer Center classes must exist on betaDev classpath
        val requiredDevClasses = listOf(
            "com.diniy.duit.devcenter.DevCenterVault",
            "com.diniy.duit.devcenter.DevCenterAuthManager",
            "com.diniy.duit.devcenter.DevCenterGitHubClient",
            "com.diniy.duit.devcenter.DevCenterFeedbackClient",
            "com.diniy.duit.devcenter.DevCenterViewModel",
            "com.diniy.duit.devcenter.ui.DeveloperCenterShellKt"
        )
        for (className in requiredDevClasses) {
            assertTrue("betaDev must include $className", runCatching { Class.forName(className) }.isSuccess)
        }

        // Public GitHub auto-updater implementation must be excluded at compile time
        val excludedUpdaterClasses = listOf(
            "com.diniy.duit.update.BetaUpdates",
            "com.diniy.duit.update.BetaUpdateModel",
            "com.diniy.duit.update.BetaInstallStatusReceiver",
            "com.diniy.duit.update.UpdateMemory"
        )
        for (className in excludedUpdaterClasses) {
            assertTrue("betaDev must exclude $className", runCatching { Class.forName(className) }.isFailure)
        }

        // Firebase Developer Broadcasts & Money Reminders preserved
        assertTrue(
            runCatching { Class.forName("com.diniy.duit.notifications.DuitBroadcastMessagingService") }.isSuccess
        )
        assertEquals(DeveloperBroadcastSpec.TOPIC_PUBLIC, DeveloperBroadcasts.defaultTopic())
        assertTrue(NotificationCategory.forThisEdition.contains(NotificationCategory.MONEY_REMINDERS))
        assertTrue(activeLicenseNotices().any { it.name.contains("Firebase", ignoreCase = true) })

        // Room Database opens cleanly under v14 schema without error
        val db = androidx.room.Room.inMemoryDatabaseBuilder(context, DuitDatabase::class.java).build()
        try {
            assertTrue(db.openHelper.writableDatabase.version == 14)
        } finally {
            db.close()
        }
    }
}
