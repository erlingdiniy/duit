package com.diniy.duit.notifications

import android.content.Context
import com.diniy.duit.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Beta/public edition FCM Developer Broadcasts initializer.
 * - Creates the dedicated "Developer Broadcasts" Android channel (separate from Money Reminders).
 * - Subscribes to the build's default topic automatically with no in-app ON/OFF toggle.
 * - Never uploads financial data, never logs sensitive tokens, and embeds zero admin credentials.
 */
object DeveloperBroadcasts {

    fun defaultTopic(): String = DeveloperBroadcastSpec.defaultTopicForVersion(BuildConfig.VERSION_NAME)

    fun initialize(context: Context) {
        val appContext = context.applicationContext
        DeveloperBroadcastNotifier.ensureChannel(appContext)
        subscribeDefaultTopic(appContext)
    }

    fun subscribeDefaultTopic(context: Context) {
        val topic = defaultTopic()
        if (!DeveloperBroadcastSpec.isValidTopic(topic)) return
        runCatching {
            val appContext = context.applicationContext
            if (FirebaseApp.getApps(appContext).isEmpty()) {
                FirebaseApp.initializeApp(appContext) ?: return
            }
            FirebaseMessaging.getInstance().subscribeToTopic(topic)
        }
    }
}

class DuitBroadcastMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        // Re-confirm default topic subscription on token refresh; never upload or log token/user data.
        DeveloperBroadcasts.subscribeDefaultTopic(applicationContext)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val expectedTopic = DeveloperBroadcasts.defaultTopic()
        if (!DeveloperBroadcastSpec.isAllowedSender(remoteMessage.from, expectedTopic)) return

        val parsed = DeveloperBroadcastSpec.parseAndSanitize(
            notificationTitle = remoteMessage.notification?.title,
            notificationBody = remoteMessage.notification?.body,
            data = remoteMessage.data
        ) ?: return

        DeveloperBroadcastNotifier.postBroadcast(applicationContext, parsed)
    }
}
