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
import java.util.Locale

data class DeveloperBroadcastMessage(
    val notificationId: Int,
    val title: String,
    val body: String,
    val deepLink: NotificationDeepLink
)

/**
 * Pure specification, payload sanitization, and safe route allowlist for Phase 8 Developer Broadcasts.
 * - Uses a dedicated Android channel completely separate from Money Reminders and user reminder toggles.
 * - Default subscribed via FCM topic with zero in-app ON/OFF toggle (users may still manage OS channel settings).
 * - Never uploads financial data, never opens external URLs, and never embeds admin credentials.
 */
object DeveloperBroadcastSpec {
    const val CHANNEL_ID = "duit_developer_broadcasts"
    const val CHANNEL_NAME = "Developer Broadcasts"
    const val CHANNEL_DESCRIPTION = "Important announcements and release updates from the duit. developer"

    const val TOPIC_BETA = "duit_beta_broadcasts"
    const val TOPIC_PUBLIC = "duit_announcements"

    const val DATA_KEY_TITLE = "title"
    const val DATA_KEY_BODY = "body"
    const val DATA_KEY_ROUTE = "route"
    const val DATA_KEY_BROADCAST_ID = "broadcast_id"

    private const val MAX_TITLE_LENGTH = 100
    private const val MAX_BODY_LENGTH = 400
    private const val NOTIFICATION_ID_BASE = 700_000
    private const val NOTIFICATION_ID_RANGE = 99_999

    private val VALID_TOPIC_REGEX = Regex("^[a-zA-Z0-9-_.~%]{1,128}$")
    private val CONTROL_CHARS_REGEX = Regex("[\\p{Cntrl}&&[^\n\t]]")

    fun defaultTopicForVersion(versionName: String): String =
        if (versionName.contains("-beta.", ignoreCase = true)) TOPIC_BETA else TOPIC_PUBLIC

    fun inactiveTopicForVersion(versionName: String): String =
        if (versionName.contains("-beta.", ignoreCase = true)) TOPIC_PUBLIC else TOPIC_BETA

    fun isValidTopic(topic: String): Boolean = VALID_TOPIC_REGEX.matches(topic)

    /**
     * Accepts broadcasts sent to our expected topic ("/topics/<topic>") or direct Firebase Console
     * device test sends (non-topic sender IDs). Rejects unrelated foreign topics.
     */
    fun isAllowedSender(from: String?, expectedTopic: String): Boolean {
        if (from.isNullOrBlank()) return true
        return if (from.startsWith("/topics/")) {
            from == "/topics/$expectedTopic"
        } else {
            // Numeric Firebase sender ID (e.g. Firebase Console test message to a single test device)
            from.all { it.isDigit() }
        }
    }

    /** Strictly maps an incoming route token to an allowlisted internal screen; unknown or URL inputs fall back to HOME. */
    fun resolveSafeRoute(rawRoute: String?): NotificationDeepLink {
        val cleaned = rawRoute?.trim()?.lowercase(Locale.ROOT).orEmpty()
        return when (cleaned) {
            "home", "" -> NotificationDeepLink.HOME
            "activity", "receipts" -> NotificationDeepLink.ACTIVITY
            "planning", "goals", "tax" -> NotificationDeepLink.PLANNING_GOALS
            "budgets" -> NotificationDeepLink.PLANNING_BUDGETS
            "bills", "commitments" -> NotificationDeepLink.PLANNING_COMMITMENTS
            "insights", "wins", "personality" -> NotificationDeepLink.INSIGHTS
            "calendar" -> NotificationDeepLink.CALENDAR
            else -> NotificationDeepLink.HOME
        }
    }

    /** Extracts a safe allowlisted deep link from a background FCM launcher intent, if present. */
    fun extractBackgroundBroadcastDeepLink(intent: Intent?): NotificationDeepLink? {
        if (intent == null) return null
        val rawRoute = intent.getStringExtra(DATA_KEY_ROUTE)
        val hasBroadcastMarker = rawRoute != null ||
            intent.hasExtra(DATA_KEY_BROADCAST_ID) ||
            intent.hasExtra("google.message_id")
        if (!hasBroadcastMarker) return null
        intent.removeExtra(DATA_KEY_ROUTE)
        intent.removeExtra(DATA_KEY_BROADCAST_ID)
        intent.removeExtra("google.message_id")
        return resolveSafeRoute(rawRoute)
    }

    fun parseAndSanitize(
        notificationTitle: String?,
        notificationBody: String?,
        data: Map<String, String>
    ): DeveloperBroadcastMessage? {
        val rawTitle = notificationTitle?.takeIf { it.isNotBlank() } ?: data[DATA_KEY_TITLE]
        val rawBody = notificationBody?.takeIf { it.isNotBlank() } ?: data[DATA_KEY_BODY]

        val cleanTitle = sanitizeText(rawTitle, MAX_TITLE_LENGTH)
        val cleanBody = sanitizeText(rawBody, MAX_BODY_LENGTH)
        if (cleanTitle.isEmpty() && cleanBody.isEmpty()) return null

        val finalTitle = cleanTitle.ifEmpty { "duit. Update" }
        val finalBody = cleanBody.ifEmpty { "Open duit. to view the latest announcement." }
        val route = resolveSafeRoute(data[DATA_KEY_ROUTE])

        val idSeed = data[DATA_KEY_BROADCAST_ID]?.takeIf { it.isNotBlank() } ?: "$finalTitle|$finalBody"
        val offset = (idSeed.hashCode() and 0x7FFFFFFF) % NOTIFICATION_ID_RANGE
        return DeveloperBroadcastMessage(
            notificationId = NOTIFICATION_ID_BASE + offset,
            title = finalTitle,
            body = finalBody,
            deepLink = route
        )
    }

    private fun sanitizeText(raw: String?, maxLen: Int): String {
        if (raw.isNullOrBlank()) return ""
        val cleaned = CONTROL_CHARS_REGEX.replace(raw, "").trim()
        return if (cleaned.length <= maxLen) cleaned else cleaned.take(maxLen - 1).trimEnd() + "…"
    }
}

object DeveloperBroadcastNotifier {

    fun ensureChannel(context: Context) {
        val sysManager = context.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        val channel = NotificationChannel(
            DeveloperBroadcastSpec.CHANNEL_ID,
            DeveloperBroadcastSpec.CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = DeveloperBroadcastSpec.CHANNEL_DESCRIPTION
            lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
        }
        sysManager.createNotificationChannel(channel)
    }

    fun hasNotificationPermission(context: Context): Boolean {
        val appContext = context.applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return NotificationManagerCompat.from(appContext).areNotificationsEnabled()
    }

    fun postBroadcast(context: Context, message: DeveloperBroadcastMessage): Boolean {
        val appContext = context.applicationContext
        if (!hasNotificationPermission(appContext)) return false
        ensureChannel(appContext)

        val tapIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(AndroidNotificationHelper.EXTRA_DEEP_LINK_TARGET, message.deepLink.name)
            putExtra(AndroidNotificationHelper.EXTRA_NOTIFICATION_ID, message.notificationId)
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            message.notificationId,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val publicVersion = NotificationCompat.Builder(appContext, DeveloperBroadcastSpec.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_duit)
            .setContentTitle("duit. announcement")
            .setContentText("Unlock to read this update from duit.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val notification = NotificationCompat.Builder(appContext, DeveloperBroadcastSpec.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_duit)
            .setContentTitle(message.title)
            .setContentText(message.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        return runCatching {
            NotificationManagerCompat.from(appContext).notify(message.notificationId, notification)
            true
        }.getOrDefault(false)
    }
}
