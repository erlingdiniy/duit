package com.diniy.duit.widgets

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.background
import androidx.glance.layout.Alignment
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

class DuitSavingsGoalsWidget : DuitGlanceWidgetBase() {

    @Composable
    override fun Content(snapshot: DuitWidgetsSnapshot, theme: ResolvedWidgetTheme) {
        val size = LocalSize.current
        val spec = WidgetLayoutPolicy.resolve(size.width.value, size.height.value, DuitWidgetKind.SAVINGS_GOALS)
        val data = snapshot.savingsGoals
        val visibleGoals = data.goals.take(spec.maxItems)

        val a11ySummary = if (visibleGoals.isEmpty()) {
            "Savings Goals widget. Protected Savings: ${snapshot.accessibleMoney(data.totalProtectedSavingsSen)}. Tap to open Planning Goals."
        } else {
            val primary = visibleGoals.first()
            "Savings Goals widget. Primary goal ${primary.name}, saved ${snapshot.accessibleMoney(primary.savedSen)}, progress ${snapshot.formatPercent(primary.progressPercentDisplay)}. Tap to open Planning Goals."
        }

        PrismaticWidgetFrame(
            theme = theme,
            deepLink = WidgetDeepLink.PLANNING_GOALS,
            accessibilityLabel = a11ySummary,
            compactPadding = spec.useCompactLabels
        ) {
            WidgetHeaderBar(
                title = if (spec.useCompactLabels) "Savings" else "Savings Goals",
                theme = theme,
                accentColor = theme.lavenderAccent,
                iconRes = R.drawable.ic_solar_shield,
                badgeText = if (spec.showHeaderBadge && data.activeGoalsCount > 0) "${data.activeGoalsCount} active" else null,
                isAppLockEnabled = snapshot.isAppLockEnabled
            )

            Spacer(GlanceModifier.height(6.dp))

            if (visibleGoals.isEmpty()) {
                WidgetEmptyBlock(
                    title = "No active goals",
                    subtitle = "Protected Savings ${snapshot.formatMoney(data.totalProtectedSavingsSen)} · Tap to create a goal.",
                    theme = theme
                )
                return@PrismaticWidgetFrame
            }

            when (spec.tier) {
                WidgetLayoutTier.COMPACT -> {
                    val primary = visibleGoals.first()
                    Column(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .background(ImageProvider(theme.tileBgRes))
                            .padding(10.dp)
                    ) {
                        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = primary.name,
                                maxLines = 1,
                                style = TextStyle(color = theme.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                                modifier = GlanceModifier.defaultWeight()
                            )
                            Spacer(GlanceModifier.width(4.dp))
                            Text(
                                text = snapshot.formatPercent(primary.progressPercentDisplay),
                                maxLines = 1,
                                style = TextStyle(color = theme.lavenderAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            )
                        }
                        Spacer(GlanceModifier.height(4.dp))
                        Text(
                            text = snapshot.formatMoney(primary.savedSen),
                            maxLines = 1,
                            style = TextStyle(color = theme.aquaAccent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        )
                        Spacer(GlanceModifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = primary.progressFraction.coerceIn(0f, 1f),
                            modifier = GlanceModifier.fillMaxWidth().height(6.dp),
                            color = theme.lavenderAccent,
                            backgroundColor = theme.progressTrack
                        )
                    }
                }
                WidgetLayoutTier.MEDIUM -> {
                    visibleGoals.forEachIndexed { index, goal ->
                        if (index > 0) Spacer(GlanceModifier.height(6.dp))
                        GoalProgressCard(goal = goal, snapshot = snapshot, theme = theme, showTargetAndRemaining = true)
                    }
                }
                WidgetLayoutTier.EXPANDED -> {
                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                        WidgetMetricTile(
                            label = "Protected Savings",
                            value = snapshot.formatMoney(data.totalProtectedSavingsSen),
                            theme = theme,
                            valueColor = theme.aquaAccent,
                            modifier = GlanceModifier.defaultWeight()
                        )
                        Spacer(GlanceModifier.width(6.dp))
                        WidgetMetricTile(
                            label = "Allocated to Goals",
                            value = "${snapshot.formatMoney(data.totalAllocatedToGoalsSen)} (${snapshot.formatPercent(data.overallProgressPercentDisplay)})",
                            theme = theme,
                            valueColor = theme.lavenderAccent,
                            modifier = GlanceModifier.defaultWeight()
                        )
                    }
                    Spacer(GlanceModifier.height(8.dp))
                    visibleGoals.forEachIndexed { index, goal ->
                        if (index > 0) Spacer(GlanceModifier.height(6.dp))
                        GoalProgressCard(goal = goal, snapshot = snapshot, theme = theme, showTargetAndRemaining = true)
                    }
                }
            }
        }
    }

    @Composable
    private fun GoalProgressCard(
        goal: WidgetSavingsGoalItem,
        snapshot: DuitWidgetsSnapshot,
        theme: ResolvedWidgetTheme,
        showTargetAndRemaining: Boolean
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(ImageProvider(theme.tileBgRes))
                .padding(horizontal = 10.dp, vertical = 7.dp)
        ) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = goal.name,
                    maxLines = 1,
                    style = TextStyle(color = theme.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                    modifier = GlanceModifier.defaultWeight()
                )
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    text = snapshot.formatPercent(goal.progressPercentDisplay),
                    maxLines = 1,
                    style = TextStyle(color = theme.lavenderAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                )
            }
            Spacer(GlanceModifier.height(4.dp))
            LinearProgressIndicator(
                progress = goal.progressFraction.coerceIn(0f, 1f),
                modifier = GlanceModifier.fillMaxWidth().height(6.dp),
                color = theme.lavenderAccent,
                backgroundColor = theme.progressTrack
            )
            if (showTargetAndRemaining) {
                Spacer(GlanceModifier.height(4.dp))
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    Text(
                        text = "${snapshot.formatMoney(goal.savedSen)} / ${snapshot.formatMoney(goal.targetSen)}",
                        maxLines = 1,
                        style = TextStyle(color = theme.textSecondary, fontSize = 10.sp),
                        modifier = GlanceModifier.defaultWeight()
                    )
                    Spacer(GlanceModifier.width(4.dp))
                    Text(
                        text = "Left ${snapshot.formatMoney(goal.remainingSen)}",
                        maxLines = 1,
                        style = TextStyle(color = theme.aquaAccent, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                    )
                }
            }
        }
    }
}

class DuitSavingsGoalsWidgetReceiver : DuitGlanceWidgetReceiver(DuitSavingsGoalsWidget())
