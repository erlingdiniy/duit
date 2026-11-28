package com.diniy.duit.devcenter

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.diniy.duit.R
import java.util.concurrent.TimeUnit

object DevAlertsNotifier {
    fun ensureChannel(context: Context) {
        val ctx = context.applicationContext
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val channel = NotificationChannel(
            DEV_ALERTS_CHANNEL_ID,
            "Developer Alerts",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Scheduled broadcast reminders and unseen feedback alerts inside Developer Center"
            lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
        }
        nm.createNotificationChannel(channel)
    }

    fun postDeveloperAlert(
        context: Context,
        notificationId: Int,
        title: String,
        body: String
    ): Boolean {
        val ctx = context.applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return false
        ensureChannel(ctx)

        val publicVersion = NotificationCompat.Builder(ctx, DEV_ALERTS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_duit)
            .setContentTitle("Developer Center alert")
            .setContentText("Unlock duit. to review this operator reminder.")
            .setAutoCancel(true)
            .build()

        val notification = NotificationCompat.Builder(ctx, DEV_ALERTS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_duit)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setAutoCancel(true)
            .build()

        return runCatching {
            NotificationManagerCompat.from(ctx).notify(notificationId, notification)
            true
        }.getOrDefault(false)
    }
}

/**
 * Local WorkManager reminder for a scheduled announcement. Never auto-sends public pushes silently:
 * marks the scheduled item ready and alerts the developer to confirm dispatch interactively.
 */
class DevScheduledBroadcastWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_SCHEDULED_ID) ?: return Result.success()
        val vault = DevCenterVault(DataStoreDevCenterStorage(applicationContext))
        val scheduled = vault.getState().scheduledBroadcasts.firstOrNull { it.id == id }
            ?: return Result.success()
        if (scheduled.status != "scheduled") return Result.success()
        vault.updateScheduledBroadcastStatus(id, "due")
        DevAlertsNotifier.postDeveloperAlert(
            context = applicationContext,
            notificationId = 800_000 + (id.hashCode() and 0x7FFF),
            title = "Scheduled announcement ready",
            body = "\"${scheduled.title}\" (${scheduled.audience.label}) is ready in Developer Center to confirm & send."
        )
        return Result.success()
    }

    companion object {
        private const val KEY_SCHEDULED_ID = "scheduled_id"

        fun scheduleReminder(context: Context, item: ScheduledBroadcast, nowMs: Long = System.currentTimeMillis()) {
            val wm = runCatching { WorkManager.getInstance(context.applicationContext) }.getOrNull() ?: return
            val delayMs = (item.scheduledEpochMs - nowMs).coerceAtLeast(5_000L)
            val req = OneTimeWorkRequestBuilder<DevScheduledBroadcastWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setInputData(Data.Builder().putString(KEY_SCHEDULED_ID, item.id).build())
                .build()
            wm.enqueueUniqueWork("dev_sched_${item.id}", ExistingWorkPolicy.REPLACE, req)
        }

        fun cancelReminder(context: Context, id: String) {
            runCatching { WorkManager.getInstance(context.applicationContext) }.getOrNull()
                ?.cancelUniqueWork("dev_sched_$id")
        }
    }
}

/**
 * Optional battery-friendly background check (6h, metered/network constrained) alerting the developer
 * only if unseen feedback rows appear in Google Sheets while OAuth session is active.
 */
class DevFeedbackAlertWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val vault = DevCenterVault(DataStoreDevCenterStorage(applicationContext))
        val state = vault.getState()
        if (!state.feedbackAlertsEnabled || state.feedbackSheetId.isBlank()) return Result.success()
        val token = vault.readAnalyticsToken() ?: vault.readSheetsToken() ?: return Result.success()
        val client = DevCenterFeedbackClient(HttpsUrlConnectionTransport())
        val loaded = client.loadFeedback(
            sheetId = state.feedbackSheetId,
            range = state.feedbackSheetRange,
            sheetsAccessToken = token,
            importedCsvRaw = null,
            importedCsvLabel = null
        )
        if (loaded is FeedbackSourceState.Ready) {
            val unseen = loaded.items.filter { it.id !in state.seenFeedbackIds }
            if (unseen.isNotEmpty()) {
                DevAlertsNotifier.postDeveloperAlert(
                    context = applicationContext,
                    notificationId = 810_001,
                    title = "${unseen.size} new duit. feedback item(s)",
                    body = "Open Developer Center -> Feedback Inbox to triage."
                )
            }
        }
        return Result.success()
    }

    companion object {
        private const val UNIQUE_PERIODIC_NAME = "duit_dev_feedback_alerts"

        fun syncSchedule(context: Context, enabled: Boolean) {
            val wm = runCatching { WorkManager.getInstance(context.applicationContext) }.getOrNull() ?: return
            if (!enabled) {
                wm.cancelUniqueWork(UNIQUE_PERIODIC_NAME)
                return
            }
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            val req = PeriodicWorkRequestBuilder<DevFeedbackAlertWorker>(6L, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            wm.enqueueUniquePeriodicWork(UNIQUE_PERIODIC_NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
        }
    }
}
