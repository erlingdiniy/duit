package com.diniy.duit.widgets

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.diniy.duit.R

class DuitQuickAddWidget : DuitGlanceWidgetBase() {

    @Composable
    override fun Content(snapshot: DuitWidgetsSnapshot, theme: ResolvedWidgetTheme) {
        val size = LocalSize.current
        val spec = WidgetLayoutPolicy.resolve(size.width.value, size.height.value, DuitWidgetKind.QUICK_ADD)
        val context = LocalContext.current

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ImageProvider(theme.surfaceBgRes))
                .semantics { contentDescription = "duit Quick Add shortcuts widget" }
                .padding(if (spec.useCompactLabels) 10.dp else 14.dp)
        ) {
            if (size.height.value >= 92f) {
                WidgetHeaderBar(
                    title = if (spec.useCompactLabels) "Quick Add" else "duit. Quick Add",
                    theme = theme,
                    accentColor = theme.skyAccent,
                    iconRes = R.drawable.ic_solar_add,
                    badgeText = if (spec.showHeaderBadge && snapshot.isAppLockEnabled) "PIN Lock" else null,
                    isAppLockEnabled = snapshot.isAppLockEnabled
                )
                Spacer(GlanceModifier.height(8.dp))
            }

            when (spec.tier) {
                WidgetLayoutTier.COMPACT -> {
                    if (spec.useHorizontalLayout) {
                        Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                            QuickAddShortcutTile(
                                label = "Expense",
                                iconRes = R.drawable.ic_solar_add,
                                pillBgRes = R.drawable.bg_widget_pill_peach,
                                accentColor = theme.peachAccent,
                                theme = theme,
                                target = WidgetDeepLink.QUICK_ADD_EXPENSE,
                                compact = true,
                                modifier = GlanceModifier.defaultWeight()
                            )
                            Spacer(GlanceModifier.width(8.dp))
                            QuickAddShortcutTile(
                                label = "Income",
                                iconRes = R.drawable.ic_solar_wallet,
                                pillBgRes = R.drawable.bg_widget_pill_aqua,
                                accentColor = theme.aquaAccent,
                                theme = theme,
                                target = WidgetDeepLink.QUICK_ADD_INCOME,
                                compact = true,
                                modifier = GlanceModifier.defaultWeight()
                            )
                        }
                    } else {
                        QuickAddShortcutTile(
                            label = "Add Expense",
                            iconRes = R.drawable.ic_solar_add,
                            pillBgRes = R.drawable.bg_widget_pill_peach,
                            accentColor = theme.peachAccent,
                            theme = theme,
                            target = WidgetDeepLink.QUICK_ADD_EXPENSE,
                            compact = true,
                            modifier = GlanceModifier.fillMaxWidth().defaultWeight()
                        )
                        Spacer(GlanceModifier.height(6.dp))
                        QuickAddShortcutTile(
                            label = "Add Income",
                            iconRes = R.drawable.ic_solar_wallet,
                            pillBgRes = R.drawable.bg_widget_pill_aqua,
                            accentColor = theme.aquaAccent,
                            theme = theme,
                            target = WidgetDeepLink.QUICK_ADD_INCOME,
                            compact = true,
                            modifier = GlanceModifier.fillMaxWidth().defaultWeight()
                        )
                    }
                }
                WidgetLayoutTier.MEDIUM -> {
                    Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        QuickAddShortcutTile(
                            label = "Expense",
                            iconRes = R.drawable.ic_solar_add,
                            pillBgRes = R.drawable.bg_widget_pill_peach,
                            accentColor = theme.peachAccent,
                            theme = theme,
                            target = WidgetDeepLink.QUICK_ADD_EXPENSE,
                            compact = size.height.value < 92f,
                            modifier = GlanceModifier.defaultWeight()
                        )
                        Spacer(GlanceModifier.width(6.dp))
                        QuickAddShortcutTile(
                            label = "Income",
                            iconRes = R.drawable.ic_solar_wallet,
                            pillBgRes = R.drawable.bg_widget_pill_aqua,
                            accentColor = theme.aquaAccent,
                            theme = theme,
                            target = WidgetDeepLink.QUICK_ADD_INCOME,
                            compact = size.height.value < 92f,
                            modifier = GlanceModifier.defaultWeight()
                        )
                        Spacer(GlanceModifier.width(6.dp))
                        QuickAddShortcutTile(
                            label = "Transfer",
                            iconRes = R.drawable.ic_solar_activity,
                            pillBgRes = R.drawable.bg_widget_pill_sky,
                            accentColor = theme.skyAccent,
                            theme = theme,
                            target = WidgetDeepLink.QUICK_ADD_TRANSFER,
                            compact = size.height.value < 92f,
                            modifier = GlanceModifier.defaultWeight()
                        )
                    }
                }
                WidgetLayoutTier.EXPANDED -> {
                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                        QuickAddShortcutTile(
                            label = "Add Expense",
                            iconRes = R.drawable.ic_solar_add,
                            pillBgRes = R.drawable.bg_widget_pill_peach,
                            accentColor = theme.peachAccent,
                            theme = theme,
                            target = WidgetDeepLink.QUICK_ADD_EXPENSE,
                            compact = false,
                            modifier = GlanceModifier.defaultWeight()
                        )
                        Spacer(GlanceModifier.width(8.dp))
                        QuickAddShortcutTile(
                            label = "Add Income",
                            iconRes = R.drawable.ic_solar_wallet,
                            pillBgRes = R.drawable.bg_widget_pill_aqua,
                            accentColor = theme.aquaAccent,
                            theme = theme,
                            target = WidgetDeepLink.QUICK_ADD_INCOME,
                            compact = false,
                            modifier = GlanceModifier.defaultWeight()
                        )
                        Spacer(GlanceModifier.width(8.dp))
                        QuickAddShortcutTile(
                            label = "Add Transfer",
                            iconRes = R.drawable.ic_solar_activity,
                            pillBgRes = R.drawable.bg_widget_pill_sky,
                            accentColor = theme.skyAccent,
                            theme = theme,
                            target = WidgetDeepLink.QUICK_ADD_TRANSFER,
                            compact = false,
                            modifier = GlanceModifier.defaultWeight()
                        )
                    }
                    Spacer(GlanceModifier.height(8.dp))
                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                        QuickAddShortcutTile(
                            label = "Activity",
                            iconRes = R.drawable.ic_solar_activity,
                            pillBgRes = R.drawable.bg_widget_pill_lavender,
                            accentColor = theme.lavenderAccent,
                            theme = theme,
                            target = WidgetDeepLink.ACTIVITY,
                            compact = true,
                            modifier = GlanceModifier.defaultWeight()
                        )
                        Spacer(GlanceModifier.width(8.dp))
                        QuickAddShortcutTile(
                            label = "Commitments",
                            iconRes = R.drawable.ic_solar_planning,
                            pillBgRes = R.drawable.bg_widget_pill_peach,
                            accentColor = theme.peachAccent,
                            theme = theme,
                            target = WidgetDeepLink.PLANNING_COMMITMENTS,
                            compact = true,
                            modifier = GlanceModifier.defaultWeight()
                        )
                        Spacer(GlanceModifier.width(8.dp))
                        QuickAddShortcutTile(
                            label = "Insights",
                            iconRes = R.drawable.ic_solar_insights,
                            pillBgRes = R.drawable.bg_widget_pill_sky,
                            accentColor = theme.skyAccent,
                            theme = theme,
                            target = WidgetDeepLink.INSIGHTS,
                            compact = true,
                            modifier = GlanceModifier.defaultWeight()
                        )
                    }
                    Spacer(GlanceModifier.defaultWeight())
                    Text(
                        text = if (snapshot.isAppLockEnabled) {
                            "App Lock active · Shortcuts require authentication before entry"
                        } else {
                            "Opens transaction editor · Review before saving"
                        },
                        maxLines = 1,
                        style = TextStyle(color = theme.textSecondary, fontSize = 10.sp)
                    )
                }
            }
        }
    }

    @Composable
    private fun QuickAddShortcutTile(
        label: String,
        @DrawableRes iconRes: Int,
        @DrawableRes pillBgRes: Int,
        accentColor: ColorProvider,
        theme: ResolvedWidgetTheme,
        target: WidgetDeepLink,
        compact: Boolean,
        modifier: GlanceModifier = GlanceModifier
    ) {
        val context = LocalContext.current
        Column(
            modifier = modifier
                .background(ImageProvider(pillBgRes))
                .clickable(actionStartActivity(WidgetDeepLink.createLaunchIntent(context, target)))
                .semantics { contentDescription = "$label shortcut" }
                .padding(horizontal = 8.dp, vertical = if (compact) 8.dp else 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                provider = ImageProvider(iconRes),
                contentDescription = null,
                colorFilter = ColorFilter.tint(accentColor),
                modifier = GlanceModifier.size(if (compact) 16.dp else 20.dp)
            )
            Spacer(GlanceModifier.height(4.dp))
            Text(
                text = label,
                maxLines = 1,
                style = TextStyle(
                    color = theme.textPrimary,
                    fontSize = if (compact) 11.sp else 12.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

class DuitQuickAddWidgetReceiver : DuitGlanceWidgetReceiver(DuitQuickAddWidget())
