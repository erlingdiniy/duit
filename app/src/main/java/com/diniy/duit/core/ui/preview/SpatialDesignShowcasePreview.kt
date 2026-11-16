package com.diniy.duit.core.ui.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.diniy.duit.core.ui.components.PrismaticBackground
import com.diniy.duit.core.ui.components.SpatialAnimatedCounterText
import com.diniy.duit.core.ui.components.SpatialBentoMetricItem
import com.diniy.duit.core.ui.components.SpatialBentoSummaryRow
import com.diniy.duit.core.ui.components.SpatialButtonVariant
import com.diniy.duit.core.ui.components.SpatialCompactListRow
import com.diniy.duit.core.ui.components.SpatialGlassButton
import com.diniy.duit.core.ui.components.SpatialGlassCard
import com.diniy.duit.core.ui.components.SpatialGlassIconButton
import com.diniy.duit.core.ui.components.SpatialGlassNavigationSurface
import com.diniy.duit.core.ui.components.SpatialGlassPill
import com.diniy.duit.core.ui.components.SpatialGlassSection
import com.diniy.duit.core.ui.components.SpatialGlassTier
import com.diniy.duit.core.ui.theme.DuitTheme
import com.diniy.duit.core.ui.theme.SpatialTheme
import com.diniy.duit.domain.model.ThemeMode

/**
 * Isolated Compose Preview showcase for the Phase 6A Prismatic Spatial Glass design system.
 * Not wired into production navigation (Phase 6A Section 11).
 */
@Composable
private fun SpatialDesignShowcaseContent() {
    val colors = SpatialTheme.colors
    val typography = SpatialTheme.typography
    val spacing = SpatialTheme.spacing

    PrismaticBackground {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "duit.",
                        style = typography.screenHeading,
                        color = colors.primaryText
                    )
                    Text(
                        text = "Prismatic Spatial Glass Foundation",
                        style = typography.supportingInfo,
                        color = colors.secondaryText
                    )
                }
                SpatialGlassIconButton(
                    icon = Icons.Rounded.Notifications,
                    contentDescription = "Notifications",
                    onClick = {}
                )
            }

            // Hero Glass Card + Animated Financial Counter
            SpatialGlassCard(
                modifier = Modifier.fillMaxWidth(),
                tier = SpatialGlassTier.Elevated,
                accentTint = colors.aqua,
                contentPadding = spacing.cardPaddingHero
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Safe to Spend",
                        style = typography.caption,
                        color = colors.secondaryText
                    )
                    SpatialGlassIconButton(
                        icon = Icons.Rounded.Visibility,
                        contentDescription = "Hide sensitive amounts",
                        onClick = {}
                    )
                }
                Spacer(Modifier.height(4.dp))
                SpatialAnimatedCounterText(
                    amountSen = 183_800L,
                    accessibilityLabel = "Safe to Spend",
                    style = typography.heroBalance,
                    color = colors.primaryText
                )
                Spacer(Modifier.height(6.dp))
                SpatialGlassPill(
                    label = "Nov 2026 cycle · 12 days left",
                    selected = true,
                    accentColor = colors.aqua
                )
            }

            // Bento Summary Components
            SpatialBentoSummaryRow(
                leftMetric = SpatialBentoMetricItem(
                    label = "Income",
                    amountSen = 320_000L,
                    caption = "Active cycle",
                    accentColor = colors.income
                ),
                rightMetric = SpatialBentoMetricItem(
                    label = "Expense",
                    amountSen = 136_200L,
                    caption = "Tracked spend",
                    accentColor = colors.expense
                )
            )

            // Floating Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm)
            ) {
                SpatialGlassButton(
                    text = "Add Transaction",
                    onClick = {},
                    variant = SpatialButtonVariant.Primary,
                    leadingIcon = {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                    },
                    modifier = Modifier.weight(1f)
                )
                SpatialGlassButton(
                    text = "Transfer",
                    onClick = {},
                    variant = SpatialButtonVariant.SecondaryGlass,
                    modifier = Modifier.weight(1f)
                )
            }

            // Section + Compact List Row
            SpatialGlassSection(
                title = "Recent Activity",
                actionLabel = "See All",
                onActionClick = {}
            ) {
                SpatialCompactListRow(
                    title = "Food & Dining",
                    subtitle = "2:14 PM · Main Wallet",
                    trailingPrimaryText = "- RM 28.50",
                    trailingPrimaryColor = colors.expense
                )
            }

            // Floating Navigation Surface
            SpatialGlassNavigationSurface {
                SpatialGlassPill(label = "Home", selected = true)
                SpatialGlassPill(label = "Activity", selected = false)
                SpatialGlassPill(label = "Planning", selected = false)
                SpatialGlassPill(label = "Insights", selected = false)
            }
        }
    }
}

@Preview(name = "Spatial Glass Showcase — Light", showBackground = true, widthDp = 390)
@Composable
private fun SpatialDesignShowcaseLightPreview() {
    DuitTheme(themeMode = ThemeMode.LIGHT) {
        SpatialDesignShowcaseContent()
    }
}

@Preview(name = "Spatial Glass Showcase — Dark", showBackground = true, widthDp = 390)
@Composable
private fun SpatialDesignShowcaseDarkPreview() {
    DuitTheme(themeMode = ThemeMode.DARK) {
        SpatialDesignShowcaseContent()
    }
}
