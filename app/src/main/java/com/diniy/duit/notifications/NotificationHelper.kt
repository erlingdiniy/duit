package com.diniy.duit.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.diniy.duit.MainActivity
import com.diniy.duit.R
import com.diniy.duit.domain.calculator.ReminderCandidate

enum class TestNotificationResult {
    SENT,
    PERMISSION_DENIED,
    RATE_LIMITED,
    FAILED
}

interface SystemNotificationPoster {
    fun hasOsNotificationPermission(): Boolean
    fun postReminder(
        candidate: ReminderCandidate,
        maskSensitiveContent: Boolean
    )
    fun sendTestNotification(): TestNotificationResult
    fun cancelStaleNotifications(
        previouslyPostedIds: Set<Int>,
        stillActiveIds: Set<Int>
    )
}

class AndroidNotificationHelper(
    private val context: Context
) : SystemNotificationPoster {

    private val appContext = context.applicationContext
    private val notificationManager: NotificationManagerCompat
        get() = NotificationManagerCompat.from(appContext)

    fun ensureChannelsCreated() {
        val sysManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        NotificationCategory.forThisEdition.forEach { category ->
            val channel = NotificationChannel(
                category.channelId,
                category.displayName,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = category.description
                lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
            }
            sysManager.createNotificationChannel(channel)
        }
    }

    override fun hasOsNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return notificationManager.areNotificationsEnabled()
    }

    private fun buildTapPendingIntent(
        requestCode: Int,
        deepLink: NotificationDeepLink? = null,
        notificationId: Int? = null,
        targetEpochDay: Long? = null
    ): PendingIntent {
        val tapIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (deepLink != null) {
                putExtra(EXTRA_DEEP_LINK_TARGET, deepLink.name)
            }
            if (notificationId != null) {
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            }
            if (targetEpochDay != null) {
                putExtra(EXTRA_TARGET_EPOCH_DAY, targetEpochDay)
                // Unique per occurrence: FLAG_UPDATE_CURRENT can never rewrite another notification's date.
                data = android.net.Uri.parse("duit://notification/${notificationId ?: requestCode}/$targetEpochDay")
            }
        }
        return PendingIntent.getActivity(
            appContext,
            requestCode,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun resolveUsableTestChannelId(): String? {
        val sysManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return NotificationCategory.COMMITMENTS.channelId
        val usable = NotificationCategory.entries.firstOrNull { category ->
            val channel = sysManager.getNotificationChannel(category.channelId)
            channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
        }
        return usable?.channelId
    }

    override fun postReminder(
        candidate: ReminderCandidate,
        maskSensitiveContent: Boolean
    ) {
        if (!hasOsNotificationPermission()) return
        ensureChannelsCreated()

        val title = candidate.resolveTitle(maskSensitiveContent)
        val body = candidate.resolveBody(maskSensitiveContent)
        val pendingIntent = buildTapPendingIntent(
            requestCode = candidate.notificationId,
            deepLink = candidate.deepLink,
            notificationId = candidate.notificationId,
            targetEpochDay = candidate.targetEpochDay.takeIf { candidate.deepLink == NotificationDeepLink.CALENDAR }
        )

        // Lock-screen public notification never exposes amounts or personal titles.
        val publicNotification = NotificationCompat.Builder(appContext, candidate.category.channelId)
            .setSmallIcon(R.drawable.ic_stat_duit)
            .setContentTitle("duit. reminder")
            .setContentText("You have a reminder. Unlock to view.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val notification = NotificationCompat.Builder(appContext, candidate.category.channelId)
            .setSmallIcon(R.drawable.ic_stat_duit)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicNotification)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        runCatching {
            notificationManager.notify(candidate.notificationId, notification)
        }
    }

    override fun sendTestNotification(): TestNotificationResult {
        if (!hasOsNotificationPermission()) return TestNotificationResult.PERMISSION_DENIED
        ensureChannelsCreated()
        val channelId = resolveUsableTestChannelId() ?: return TestNotificationResult.PERMISSION_DENIED

        val notification = NotificationCompat.Builder(appContext, channelId)
            .setSmallIcon(R.drawable.ic_stat_duit)
            .setContentTitle(TEST_NOTIFICATION_TITLE)
            .setContentText(TEST_NOTIFICATION_MESSAGE)
            .setStyle(NotificationCompat.BigTextStyle().bigText(TEST_NOTIFICATION_MESSAGE))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(buildTapPendingIntent(requestCode = TEST_NOTIFICATION_ID))
            .setAutoCancel(true)
            .build()

        return runCatching {
            notificationManager.notify(TEST_NOTIFICATION_ID, notification)
            TestNotificationResult.SENT
        }.getOrElse { error ->
            if (error is SecurityException) TestNotificationResult.PERMISSION_DENIED
            else TestNotificationResult.FAILED
        }
    }

    override fun cancelStaleNotifications(
        previouslyPostedIds: Set<Int>,
        stillActiveIds: Set<Int>
    ) {
        val toCancel = previouslyPostedIds - stillActiveIds
        toCancel.forEach { id ->
            runCatching { notificationManager.cancel(id) }
        }
    }

    companion object {
        const val EXTRA_DEEP_LINK_TARGET = "extra_duit_deep_link_target"
        const val EXTRA_NOTIFICATION_ID = "extra_duit_notification_id"
        const val EXTRA_TARGET_EPOCH_DAY = "extra_duit_target_epoch_day"
        const val TEST_NOTIFICATION_ID = -5001
        const val TEST_NOTIFICATION_TITLE = "duit. Test Notification"
        const val TEST_NOTIFICATION_MESSAGE = "Notifications are working correctly."
    }
}
