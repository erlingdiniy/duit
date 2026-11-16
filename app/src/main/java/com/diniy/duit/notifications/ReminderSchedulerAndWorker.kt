package com.diniy.duit.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.diniy.duit.DuitApplication
import com.diniy.duit.widgets.GlanceWidgetUpdateCoordinator
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class ReminderDispatchWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? DuitApplication ?: return Result.success()
        val container = app.container
        return runCatching {
            val now = Instant.now()
            val eval = container.reminderSyncEngine.syncAndComputeNext(nowInstant = now, zoneId = ZoneId.systemDefault())
            val nextTrigger = eval.nextScheduledTriggerEpochMs
            if (nextTrigger != null) {
                container.reminderScheduler.scheduleNextTriggerAt(nextTrigger, now.toEpochMilli())
            } else {
                container.reminderScheduler.cancelScheduledFutureTrigger()
            }
            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }
}

interface ReminderScheduler {
    fun ensureDailySafetyNet()
    fun enqueueImmediateSync()
    fun scheduleNextTriggerAt(triggerAtEpochMs: Long, nowEpochMs: Long = System.currentTimeMillis())
    fun cancelScheduledFutureTrigger()
    fun cancelAllWork()
}

class WorkManagerReminderScheduler(
    context: Context
) : ReminderScheduler {

    private val appContext = context.applicationContext

    private fun workManagerOrNull(): WorkManager? =
        runCatching { WorkManager.getInstance(appContext) }.getOrNull()

    override fun ensureDailySafetyNet() {
        val wm = workManagerOrNull() ?: return
        val dailyRequest = PeriodicWorkRequestBuilder<ReminderDispatchWorker>(24L, TimeUnit.HOURS)
            .build()
        wm.enqueueUniquePeriodicWork(
            UNIQUE_DAILY_GUARD,
            ExistingPeriodicWorkPolicy.KEEP,
            dailyRequest
        )
    }

    override fun enqueueImmediateSync() {
        val wm = workManagerOrNull() ?: return
        ensureDailySafetyNet()
        val nowRequest = OneTimeWorkRequestBuilder<ReminderDispatchWorker>().build()
        wm.enqueueUniqueWork(
            UNIQUE_IMMEDIATE_SYNC,
            ExistingWorkPolicy.REPLACE,
            nowRequest
        )
    }

    override fun scheduleNextTriggerAt(triggerAtEpochMs: Long, nowEpochMs: Long) {
        val wm = workManagerOrNull() ?: return
        val delayMs = (triggerAtEpochMs - nowEpochMs).coerceAtLeast(1_000L)
        val request = OneTimeWorkRequestBuilder<ReminderDispatchWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .build()
        wm.enqueueUniqueWork(
            UNIQUE_NEXT_SCHEDULED,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    override fun cancelScheduledFutureTrigger() {
        workManagerOrNull()?.cancelUniqueWork(UNIQUE_NEXT_SCHEDULED)
    }

    override fun cancelAllWork() {
        val wm = workManagerOrNull() ?: return
        wm.cancelUniqueWork(UNIQUE_IMMEDIATE_SYNC)
        wm.cancelUniqueWork(UNIQUE_NEXT_SCHEDULED)
        wm.cancelUniqueWork(UNIQUE_DAILY_GUARD)
    }

    companion object {
        private const val UNIQUE_IMMEDIATE_SYNC = "duit_reminder_sync_now"
        private const val UNIQUE_NEXT_SCHEDULED = "duit_reminder_next_trigger"
        private const val UNIQUE_DAILY_GUARD = "duit_reminder_daily_guard"
    }
}

class ReminderBootAndTimeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (
            action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED ||
            action == Intent.ACTION_DATE_CHANGED
        ) {
            val appContext = context.applicationContext
            WorkManagerReminderScheduler(appContext).enqueueImmediateSync()
            GlanceWidgetUpdateCoordinator(appContext).enqueueRefresh()
        }
    }
}
