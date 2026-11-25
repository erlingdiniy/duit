package com.diniy.duit.notifications

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeveloperBroadcastsTest {

    private val app: Application
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun channelIsSeparateFromMoneyRemindersAndHasNoInAppToggleCategory() {
        assertNotEquals(NotificationCategory.MONEY_REMINDERS.channelId, DeveloperBroadcastSpec.CHANNEL_ID)
        assertTrue(NotificationCategory.entries.none { it.channelId == DeveloperBroadcastSpec.CHANNEL_ID })
    }

    @Test
    fun topicSelectionAndSenderValidationAreStrict() {
        val betaTopic = DeveloperBroadcastSpec.defaultTopicForVersion("1.2.0-beta.1")
        val betaInactive = DeveloperBroadcastSpec.inactiveTopicForVersion("1.2.0-beta.1")
        val stableTopic = DeveloperBroadcastSpec.defaultTopicForVersion("1.2.0")
        val stableInactive = DeveloperBroadcastSpec.inactiveTopicForVersion("1.2.0")

        assertEquals(DeveloperBroadcastSpec.TOPIC_BETA, betaTopic)
        assertEquals(DeveloperBroadcastSpec.TOPIC_PUBLIC, betaInactive)
        assertEquals(DeveloperBroadcastSpec.TOPIC_PUBLIC, stableTopic)
        assertEquals(DeveloperBroadcastSpec.TOPIC_BETA, stableInactive)
        assertTrue(betaTopic != betaInactive)
        assertTrue(stableTopic != stableInactive)
        assertTrue(DeveloperBroadcastSpec.isValidTopic(betaTopic))
        assertTrue(DeveloperBroadcastSpec.isValidTopic(stableTopic))

        assertTrue(DeveloperBroadcastSpec.isAllowedSender("/topics/$betaTopic", betaTopic))
        assertTrue(DeveloperBroadcastSpec.isAllowedSender("123456789012", betaTopic))
        assertFalse(DeveloperBroadcastSpec.isAllowedSender("/topics/some_other_topic", betaTopic))
        assertFalse(DeveloperBroadcastSpec.isAllowedSender("/topics/$stableTopic", betaTopic))
    }

    @Test
    fun routeAllowlistAndPayloadSanitizerRejectUnsafeInputs() {
        assertEquals(NotificationDeepLink.INSIGHTS, DeveloperBroadcastSpec.resolveSafeRoute("insights"))
        assertEquals(NotificationDeepLink.PLANNING_GOALS, DeveloperBroadcastSpec.resolveSafeRoute("planning"))
        assertEquals(NotificationDeepLink.PLANNING_BUDGETS, DeveloperBroadcastSpec.resolveSafeRoute("budgets"))
        assertEquals(NotificationDeepLink.PLANNING_COMMITMENTS, DeveloperBroadcastSpec.resolveSafeRoute("bills"))
        assertEquals(NotificationDeepLink.ACTIVITY, DeveloperBroadcastSpec.resolveSafeRoute("activity"))
        assertEquals(NotificationDeepLink.CALENDAR, DeveloperBroadcastSpec.resolveSafeRoute("calendar"))

        // Malicious or arbitrary external URLs must fall back safely to HOME
        assertEquals(NotificationDeepLink.HOME, DeveloperBroadcastSpec.resolveSafeRoute("https://evil.example/phish"))
        assertEquals(NotificationDeepLink.HOME, DeveloperBroadcastSpec.resolveSafeRoute("intent://settings"))
        assertEquals(NotificationDeepLink.HOME, DeveloperBroadcastSpec.resolveSafeRoute("javascript:alert(1)"))
        assertEquals(NotificationDeepLink.HOME, DeveloperBroadcastSpec.resolveSafeRoute(null))

        // Empty payload ignored
        assertNull(DeveloperBroadcastSpec.parseAndSanitize("", "   ", emptyMap()))

        // Control chars stripped, lengths capped, notification ID isolated in 700_000..799_999
        val msg = DeveloperBroadcastSpec.parseAndSanitize(
            notificationTitle = "New Beta\u0007 " + "A".repeat(150),
            notificationBody = "Update ready\u0000 " + "B".repeat(500),
            data = mapOf(
                DeveloperBroadcastSpec.DATA_KEY_ROUTE to "insights",
                DeveloperBroadcastSpec.DATA_KEY_BROADCAST_ID to "beta-v120"
            )
        )
        assertNotNull(msg)
        msg!!
        assertTrue(msg.title.length <= 100)
        assertTrue(msg.body.length <= 400)
        assertFalse(msg.title.contains('\u0007'))
        assertFalse(msg.body.contains('\u0000'))
        assertEquals(NotificationDeepLink.INSIGHTS, msg.deepLink)
        assertTrue(msg.notificationId in 700_000..799_999)
    }

    @Test
    fun backgroundIntentExtractionConsumesExtrasOnce() {
        val intent = Intent().apply {
            putExtra(DeveloperBroadcastSpec.DATA_KEY_ROUTE, "calendar")
            putExtra("google.message_id", "0:12345")
        }
        assertEquals(NotificationDeepLink.CALENDAR, DeveloperBroadcastSpec.extractBackgroundBroadcastDeepLink(intent))
        // Second read must return null so rotation/restore never replays navigation
        assertNull(DeveloperBroadcastSpec.extractBackgroundBroadcastDeepLink(intent))

        // Normal launcher intent with no broadcast markers returns null
        assertNull(DeveloperBroadcastSpec.extractBackgroundBroadcastDeepLink(Intent(Intent.ACTION_MAIN)))
    }

    @Test
    fun notifierCreatesSeparateChannelAndRespectsPostNotificationsPermission() {
        val msg = DeveloperBroadcastSpec.parseAndSanitize(
            notificationTitle = "duit. Beta Update",
            notificationBody = "Thanks for testing closed beta!",
            data = mapOf(DeveloperBroadcastSpec.DATA_KEY_ROUTE to "home")
        )!!

        // Denied on Android 13+ -> returns false safely without posting
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertFalse(DeveloperBroadcastNotifier.postBroadcast(app, msg))

        // Granted -> creates separate channel and posts with private lockscreen visibility
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertTrue(DeveloperBroadcastNotifier.postBroadcast(app, msg))

        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = nm.getNotificationChannel(DeveloperBroadcastSpec.CHANNEL_ID)
        assertNotNull(channel)
        assertEquals(DeveloperBroadcastSpec.CHANNEL_NAME, channel!!.name.toString())
        assertEquals(NotificationCompat.VISIBILITY_PRIVATE, channel.lockscreenVisibility)

        val posted = shadowOf(nm).getNotification(msg.notificationId)
        assertNotNull(posted)
        assertEquals(DeveloperBroadcastSpec.CHANNEL_ID, posted.channelId)
        assertNotNull(posted.publicVersion)
        assertNotNull(posted.contentIntent)
    }
}
