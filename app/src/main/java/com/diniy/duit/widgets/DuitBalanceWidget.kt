package com.diniy.duit.widgets

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.diniy.duit.R

class DuitBalanceWidget : DuitGlanceWidgetBase() {

    @Composable
    override fun Content(snapshot: DuitWidgetsSnapshot, theme: ResolvedWidgetTheme) {
        val size = LocalSize.current
        val spec = WidgetLayoutPolicy.resolve(size.width.value, size.height.value, DuitWidgetKind.BALANCE)
        val balance = snapshot.balance
        val safeText = snapshot.formatMoney(balance.safeToSpendSen)
        val a11ySummary = "duit Balance widget. Safe to Spend: ${snapshot.accessibleMoney(balance.safeToSpendSen)}. Tap to open Home."

        PrismaticWidgetFrame(
            theme = theme,
            deepLink = WidgetDeepLink.HOME,
            accessibilityLabel = a11ySummary,
            compactPadding = spec.useCompactLabels
        ) {
            WidgetHeaderBar(
                title = if (spec.useCompactLabels) "duit." else "duit. Overview",
                theme = theme,
                accentColor = theme.aquaAccent,
                iconRes = R.drawable.ic_solar_wallet,
                badgeText = if (spec.showHeaderBadge) balance.cycleLabel else null,
                isAppLockEnabled = snapshot.isAppLockEnabled
            )

            Spacer(GlanceModifier.height(if (spec.tier == WidgetLayoutTier.COMPACT) 6.dp else 8.dp))

            if (balance.isEmptyLedger) {
                WidgetEmptyBlock(
                    title = "No wallets yet",
                    subtitle = "Tap to create your first wallet in duit.",
                    theme = theme
                )
                return@PrismaticWidgetFrame
            }

            // Primary Hero Metric: Safe to Spend (identical across sizes, scaled cleanly).
            Text(
                text = "Safe to Spend",
                maxLines = 1,
                style = TextStyle(
                    color = theme.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            Text(
                text = safeText,
                maxLines = 1,
                style = TextStyle(
                    color = theme.aquaAccent,
                    fontSize = when (spec.tier) {
                        WidgetLayoutTier.COMPACT -> 19.sp
                        WidgetLayoutTier.MEDIUM -> 21.sp
                        WidgetLayoutTier.EXPANDED -> 24.sp
                    },
                    fontWeight = FontWeight.Bold
                )
            )

            when (spec.tier) {
                WidgetLayoutTier.COMPACT -> {
                    Spacer(GlanceModifier.defaultWeight())
                    Text(
                        text = balance.cycleLabel,
                        maxLines = 1,
                        style = TextStyle(
                            color = theme.textSecondary,
                            fontSize = 10.sp
                        )
                    )
                }
                WidgetLayoutTier.MEDIUM -> {
                    Spacer(GlanceModifier.height(8.dp))
                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                        WidgetMetricTile(
                            label = "Total",
                            value = snapshot.formatMoney(balance.totalBalanceSen),
                            theme = theme,
                            modifier = GlanceModifier.defaultWeight()
                        )
                        Spacer(GlanceModifier.width(6.dp))
                        WidgetMetricTile(
                            label = "Savings",
                            value = snapshot.formatMoney(balance.protectedSavingsSen),
                            theme = theme,
                            valueColor = theme.lavenderAccent,
                            modifier = GlanceModifier.defaultWeight()
                        )
                        if (size.width.value >= 230f) {
                            Spacer(GlanceModifier.width(6.dp))
                            WidgetMetricTile(
                                label = "Reserved",
                                value = snapshot.formatMoney(balance.currentCycleReservedSen),
                                theme = theme,
                                valueColor = theme.peachAccent,
                                modifier = GlanceModifier.defaultWeight()
                            )
                        }
                    }
                }
                WidgetLayoutTier.EXPANDED -> {
                    Spacer(GlanceModifier.height(10.dp))
                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                        WidgetMetricTile(
                            label = "Total Balance",
                            value = snapshot.formatMoney(balance.totalBalanceSen),
                            theme = theme,
                            modifier = GlanceModifier.defaultWeight()
                        )
                        Spacer(GlanceModifier.width(8.dp))
                        WidgetMetricTile(
                            label = "Protected Savings",
                            value = snapshot.formatMoney(balance.protectedSavingsSen),
                            theme = theme,
                            valueColor = theme.lavenderAccent,
                            modifier = GlanceModifier.defaultWeight()
                        )
                    }
                    Spacer(GlanceModifier.height(6.dp))
                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                        WidgetMetricTile(
                            label = "Cycle Reserved",
                            value = snapshot.formatMoney(balance.currentCycleReservedSen),
                            theme = theme,
                            valueColor = theme.peachAccent,
                            modifier = GlanceModifier.defaultWeight()
                        )
                        Spacer(GlanceModifier.width(8.dp))
                        WidgetMetricTile(
                            label = "Income · Expense",
                            value = if (snapshot.shouldMaskAmounts) {
                                "${snapshot.formatMoney(balance.cycleIncomeSen)}"
                            } else {
                                "+${snapshot.formatMoney(balance.cycleIncomeSen)}"
                            },
                            theme = theme,
                            valueColor = theme.skyAccent,
                            modifier = GlanceModifier.defaultWeight()
                        )
                    }
                    Spacer(GlanceModifier.defaultWeight())
                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                        Text(
                            text = "${balance.walletCount} active wallets · Future bills ${snapshot.formatMoney(balance.upcomingFutureCommitmentsSen)}",
                            maxLines = 1,
                            style = TextStyle(color = theme.textSecondary, fontSize = 10.sp),
                            modifier = GlanceModifier.defaultWeight()
                        )
                        Spacer(GlanceModifier.width(6.dp))
                        Text(
                            text = "Updated ${balance.lastUpdatedLabel}",
                            maxLines = 1,
                            style = TextStyle(color = theme.textSecondary, fontSize = 10.sp)
                        )
                    }
                }
            }
        }
    }
}

class DuitBalanceWidgetReceiver : DuitGlanceWidgetReceiver(DuitBalanceWidget())
