package com.diniy.duit.widgets

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.diniy.duit.R

class DuitUpcomingPaymentsWidget : DuitGlanceWidgetBase() {

    @Composable
    override fun Content(snapshot: DuitWidgetsSnapshot, theme: ResolvedWidgetTheme) {
        val size = LocalSize.current
        val spec = WidgetLayoutPolicy.resolve(size.width.value, size.height.value, DuitWidgetKind.UPCOMING_PAYMENTS)
        val data = snapshot.upcomingPayments
        val visibleItems = data.items.take(spec.maxItems)

        val a11ySummary = if (visibleItems.isEmpty()) {
            "Upcoming Payments widget. All obligations caught up. Tap to open Planning Commitments."
        } else {
            val first = visibleItems.first()
            "Upcoming Payments widget. Next: ${first.title}, due ${first.dueLabel}, amount ${snapshot.accessibleMoney(first.amountSen)}. Tap to open Planning Commitments."
        }

        PrismaticWidgetFrame(
            theme = theme,
            deepLink = WidgetDeepLink.PLANNING_COMMITMENTS,
            accessibilityLabel = a11ySummary,
            compactPadding = spec.useCompactLabels
        ) {
            val badge = when {
                !spec.showHeaderBadge -> null
                data.overdueCount > 0 -> "${data.overdueCount} Overdue"
                data.items.isNotEmpty() -> "${data.items.size} Due"
                else -> "Clear"
            }

            WidgetHeaderBar(
                title = if (spec.useCompactLabels) "Upcoming" else "Upcoming Payments",
                theme = theme,
                accentColor = theme.peachAccent,
                iconRes = R.drawable.ic_solar_planning,
                badgeText = badge,
                isAppLockEnabled = snapshot.isAppLockEnabled
            )

            Spacer(GlanceModifier.height(6.dp))

            if (visibleItems.isEmpty()) {
                WidgetEmptyBlock(
                    title = "All caught up",
                    subtitle = "No unpaid commitments, PayLater instalments, or scheduled debts.",
                    theme = theme
                )
                return@PrismaticWidgetFrame
            }

            when (spec.tier) {
                WidgetLayoutTier.COMPACT -> {
                    val next = visibleItems.first()
                    Column(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .background(ImageProvider(theme.tileBgRes))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = next.title,
                            maxLines = 1,
                            style = TextStyle(
                                color = theme.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(GlanceModifier.height(4.dp))
                        Text(
                            text = snapshot.formatMoney(next.amountSen),
                            maxLines = 1,
                            style = TextStyle(
                                color = if (next.isOverdue) theme.peachAccent else theme.aquaAccent,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(GlanceModifier.height(2.dp))
                        Text(
                            text = if (next.isOverdue) "Overdue · ${next.dueLabel}" else "Due ${next.dueLabel}",
                            maxLines = 1,
                            style = TextStyle(
                                color = if (next.isOverdue) theme.peachAccent else theme.textSecondary,
                                fontSize = 10.sp
                            )
                        )
                    }
                    if (spec.showSecondaryMetrics && visibleItems.size > 1) {
                        Spacer(GlanceModifier.height(6.dp))
                        ObligationRow(item = visibleItems[1], snapshot = snapshot, theme = theme, compact = true)
                    }
                }
                WidgetLayoutTier.MEDIUM -> {
                    visibleItems.forEachIndexed { index, item ->
                        if (index > 0) Spacer(GlanceModifier.height(5.dp))
                        ObligationRow(item = item, snapshot = snapshot, theme = theme, compact = true)
                    }
                    if (spec.showFooterDetails) {
                        Spacer(GlanceModifier.defaultWeight())
                        Text(
                            text = "Cycle reserved ${snapshot.formatMoney(data.currentCycleReservedSen)} · Future ${snapshot.formatMoney(data.upcomingFutureBillsSen)}",
                            maxLines = 1,
                            style = TextStyle(color = theme.textSecondary, fontSize = 10.sp)
                        )
                    }
                }
                WidgetLayoutTier.EXPANDED -> {
                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                        WidgetMetricTile(
                            label = "Cycle Reserved",
                            value = snapshot.formatMoney(data.currentCycleReservedSen),
                            theme = theme,
                            valueColor = theme.peachAccent,
                            modifier = GlanceModifier.defaultWeight()
                        )
                        Spacer(GlanceModifier.width(6.dp))
                        WidgetMetricTile(
                            label = "Future Bills",
                            value = snapshot.formatMoney(data.upcomingFutureBillsSen),
                            theme = theme,
                            valueColor = theme.skyAccent,
                            modifier = GlanceModifier.defaultWeight()
                        )
                        if (data.overdueCount > 0) {
                            Spacer(GlanceModifier.width(6.dp))
                            WidgetMetricTile(
                                label = "Overdue (${data.overdueCount})",
                                value = snapshot.formatMoney(data.overdueSen),
                                theme = theme,
                                valueColor = theme.peachAccent,
                                modifier = GlanceModifier.defaultWeight()
                            )
                        }
                    }
                    Spacer(GlanceModifier.height(8.dp))
                    visibleItems.forEachIndexed { index, item ->
                        if (index > 0) Spacer(GlanceModifier.height(5.dp))
                        ObligationRow(item = item, snapshot = snapshot, theme = theme, compact = false)
                    }
                }
            }
        }
    }

    @Composable
    private fun ObligationRow(
        item: WidgetObligationItem,
        snapshot: DuitWidgetsSnapshot,
        theme: ResolvedWidgetTheme,
        compact: Boolean
    ) {
        val statusColor = when {
            item.isOverdue -> theme.peachAccent
            item.isCurrentCycle -> theme.aquaAccent
            else -> theme.skyAccent
        }
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(ImageProvider(theme.tileBgRes))
                .padding(horizontal = 10.dp, vertical = if (compact) 6.dp else 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = item.title,
                    maxLines = 1,
                    style = TextStyle(
                        color = theme.textPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = if (compact) {
                        "${item.kind.shortTag} · ${item.dueLabel}"
                    } else {
                        "${item.kind.shortTag} · ${item.dueLabel} · ${item.statusLabel}"
                    },
                    maxLines = 1,
                    style = TextStyle(
                        color = if (item.isOverdue) theme.peachAccent else theme.textSecondary,
                        fontSize = 10.sp
                    )
                )
            }
            Spacer(GlanceModifier.width(6.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = snapshot.formatMoney(item.amountSen),
                    maxLines = 1,
                    style = TextStyle(
                        color = statusColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                if (!compact) {
                    Box(
                        modifier = GlanceModifier
                            .background(
                                ImageProvider(
                                    if (item.isOverdue) R.drawable.bg_widget_pill_peach
                                    else if (item.isCurrentCycle) R.drawable.bg_widget_pill_aqua
                                    else R.drawable.bg_widget_pill_sky
                                )
                            )
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = if (item.isCurrentCycle) "In cycle" else "Future",
                            maxLines = 1,
                            style = TextStyle(color = statusColor, fontSize = 9.sp, fontWeight = FontWeight.Medium)
                        )
                    }
                }
            }
        }
    }
}

class DuitUpcomingPaymentsWidgetReceiver : DuitGlanceWidgetReceiver(DuitUpcomingPaymentsWidget())
