package com.diniy.duit.widgets

import android.content.Context
import android.content.Intent
import com.diniy.duit.MainActivity
import com.diniy.duit.domain.model.TransactionType

enum class WidgetDeepLink(val actionName: String) {
    HOME("com.diniy.duit.widget.ACTION_HOME"),
    ACTIVITY("com.diniy.duit.widget.ACTION_ACTIVITY"),
    PLANNING_COMMITMENTS("com.diniy.duit.widget.ACTION_PLANNING_COMMITMENTS"),
    PLANNING_GOALS("com.diniy.duit.widget.ACTION_PLANNING_GOALS"),
    INSIGHTS("com.diniy.duit.widget.ACTION_INSIGHTS"),
    QUICK_ADD_EXPENSE("com.diniy.duit.widget.ACTION_QUICK_ADD_EXPENSE"),
    QUICK_ADD_INCOME("com.diniy.duit.widget.ACTION_QUICK_ADD_INCOME"),
    QUICK_ADD_TRANSFER("com.diniy.duit.widget.ACTION_QUICK_ADD_TRANSFER");

    fun toQuickAddTransactionType(): TransactionType? = when (this) {
        QUICK_ADD_EXPENSE -> TransactionType.EXPENSE
        QUICK_ADD_INCOME -> TransactionType.INCOME
        QUICK_ADD_TRANSFER -> TransactionType.TRANSFER
        else -> null
    }

    companion object {
        const val EXTRA_WIDGET_DEEP_LINK = "extra_duit_widget_deep_link"

        fun createLaunchIntent(context: Context, target: WidgetDeepLink): Intent {
            return Intent(context.applicationContext, MainActivity::class.java).apply {
                // Distinct action ensures Android PendingIntent cache never collapses Quick Add shortcuts.
                action = target.actionName
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_WIDGET_DEEP_LINK, target.name)
            }
        }

        fun extract(intent: Intent?): WidgetDeepLink? {
            if (intent == null) return null
            val rawExtra = intent.getStringExtra(EXTRA_WIDGET_DEEP_LINK)
            if (rawExtra != null) {
                entries.firstOrNull { it.name == rawExtra }?.let { return it }
            }
            val action = intent.action ?: return null
            return entries.firstOrNull { it.actionName == action }
        }
    }
}
