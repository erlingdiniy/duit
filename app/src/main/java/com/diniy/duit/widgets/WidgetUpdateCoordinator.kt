package com.diniy.duit.widgets

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.diniy.duit.DuitApplication

interface WidgetUpdateCoordinator {
    fun enqueueRefresh()
    suspend fun refreshAllWidgetsNow()
}

class GlanceWidgetUpdateCoordinator(
    context: Context
) : WidgetUpdateCoordinator {

    private val appContext = context.applicationContext

    override fun enqueueRefresh() {
        val wm = runCatching { WorkManager.getInstance(appContext) }.getOrNull() ?: return
        val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>().build()
        wm.enqueueUniqueWork(UNIQUE_WIDGET_REFRESH, ExistingWorkPolicy.REPLACE, request)
    }

    override suspend fun refreshAllWidgetsNow() {
        runCatching { DuitBalanceWidget().updateAll(appContext) }
        runCatching { DuitUpcomingPaymentsWidget().updateAll(appContext) }
        runCatching { DuitSavingsGoalsWidget().updateAll(appContext) }
        runCatching { DuitQuickAddWidget().updateAll(appContext) }
    }

    companion object {
        private const val UNIQUE_WIDGET_REFRESH = "duit_widget_refresh_now"
    }
}

class WidgetRefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? DuitApplication ?: return Result.success()
        app.container.widgetUpdateCoordinator.refreshAllWidgetsNow()
        return Result.success()
    }
}
