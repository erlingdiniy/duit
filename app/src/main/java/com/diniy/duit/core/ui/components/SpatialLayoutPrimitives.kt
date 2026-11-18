package com.diniy.duit.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.diniy.duit.core.ui.state.LocalPrivacySettings
import com.diniy.duit.core.ui.state.PrivacyMask
import com.diniy.duit.core.ui.state.privacySafeAmountSemantics
import com.diniy.duit.core.ui.theme.SpatialTheme

/**
 * Computes edge-to-edge safe [PaddingValues] for scrollable screens (Phase 6A Section 8).
 * - Prevents Android status bar overlap at the top.
 * - Reserves bottom clearance for either the system gesture bar or the floating navigation capsule.
 * - Smoothly incorporates software keyboard (`WindowInsets.ime`) padding without double-padding.
 */
@Composable
fun rememberSpatialScreenPadding(
    hasFloatingBottomBar: Boolean = false,
    horizontalMargin: Dp = SpatialTheme.spacing.screenMarginCompact,
    topExtraPadding: Dp = SpatialTheme.spacing.screenTopPadding,
    bottomExtraPadding: Dp = if (hasFloatingBottomBar) {
        SpatialTheme.spacing.floatingNavBottomClearance
    } else {
        SpatialTheme.spacing.screenBottomPadding
    }
): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    val safeDrawing = WindowInsets.safeDrawing.asPaddingValues()
    val ime = WindowInsets.ime.asPaddingValues()

    val start = safeDrawing.calculateStartPadding(layoutDirection) + horizontalMargin
    val end = safeDrawing.calculateEndPadding(layoutDirection) + horizontalMargin
    val top = safeDrawing.calculateTopPadding() + topExtraPadding
    val navBottom = safeDrawing.calculateBottomPadding() + bottomExtraPadding
    val imeBottom = ime.calculateBottomPadding() + SpatialTheme.spacing.md

    return PaddingValues(
        start = start,
        top = top,
        end = end,
        bottom = maxOf(navBottom, imeBottom)
    )
}

/**
 * Reusable screen container providing the ambient Prismatic Spatial Glass background
 * and pre-computed edge-to-edge [PaddingValues] for child `LazyColumn` or scroll containers.
 */
@Composable
fun SpatialScreenContainer(
    modifier: Modifier = Modifier,
    hasFloatingBottomBar: Boolean = false,
    content: @Composable (contentPadding: PaddingValues) -> Unit
) {
    val screenPadding = rememberSpatialScreenPadding(hasFloatingBottomBar = hasFloatingBottomBar)
    PrismaticBackground(modifier = modifier.fillMaxSize()) {
        content(screenPadding)
    }
}

/**
 * Reusable section header primitive with accessible heading semantics and optional trailing action.
 */
@Composable
fun SpatialSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    val colors = SpatialTheme.colors
    val typography = SpatialTheme.typography

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = typography.sectionHeading,
                color = colors.primaryText,
                modifier = Modifier.semantics { heading() }
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = typography.supportingInfo,
                    color = colors.secondaryText
                )
            }
        }
        if (actionLabel != null && onActionClick != null) {
            Spacer(Modifier.width(12.dp))
            SpatialGlassPill(
                label = actionLabel,
                onClick = onActionClick
            )
        }
    }
}

data class SpatialBentoMetricItem(
    val label: String,
    val amountSen: Long,
    val caption: String? = null,
    val accentColor: Color? = null,
    val isBalanceMask: Boolean = true,
    val onClick: (() -> Unit)? = null
)

/**
 * Bento-style 2-column financial summary grid inspired by Reference 1 & Reference 2.
 * Uses tabular numerals and privacy-safe accessibility descriptions without deep nesting.
 */
@Composable
fun SpatialBentoSummaryRow(
    leftMetric: SpatialBentoMetricItem,
    rightMetric: SpatialBentoMetricItem,
    modifier: Modifier = Modifier
) {
    val spacing = SpatialTheme.spacing
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.bentoGap)
    ) {
        SpatialBentoMetricCard(
            item = leftMetric,
            modifier = Modifier.weight(1f)
        )
        SpatialBentoMetricCard(
            item = rightMetric,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun SpatialBentoMetricCard(
    item: SpatialBentoMetricItem,
    modifier: Modifier = Modifier
) {
    val colors = SpatialTheme.colors
    val typography = SpatialTheme.typography
    val spacing = SpatialTheme.spacing
    val privacySettings = LocalPrivacySettings.current

    val isMasked = if (item.isBalanceMask) {
        privacySettings.isAnyMaskActive
    } else {
        privacySettings.privacyModeEnabled
    }
    val formattedText = if (item.isBalanceMask) {
        PrivacyMask.formatBalance(item.amountSen, privacySettings)
    } else {
        PrivacyMask.formatAmount(item.amountSen, privacySettings)
    }

    SpatialGlassCard(
        modifier = modifier.privacySafeAmountSemantics(
            label = item.label,
            isMasked = isMasked,
            visibleFormattedAmount = formattedText
        ),
        tier = SpatialGlassTier.Standard,
        accentTint = item.accentColor,
        contentPadding = spacing.cardPaddingCompact,
        onClick = item.onClick,
        onClickLabel = item.label
    ) {
        Text(
            text = item.label,
            style = typography.caption,
            color = colors.secondaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = formattedText,
            style = typography.cardBalance,
            color = colors.primaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!item.caption.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = item.caption,
                style = typography.supportingInfo,
                color = item.accentColor?.let { colors.readableOn(it) } ?: colors.secondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Compact, single-surface spatial list row for transactions, bills, or settings items.
 */
@Composable
fun SpatialCompactListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailingPrimaryText: String? = null,
    trailingPrimaryColor: Color? = null,
    trailingSecondaryText: String? = null,
    leadingBadgeColor: Color? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val colors = SpatialTheme.colors
    val typography = SpatialTheme.typography
    val spacing = SpatialTheme.spacing
    val geometry = SpatialTheme.geometry

    SpatialGlassCard(
        modifier = modifier.fillMaxWidth(),
        tier = SpatialGlassTier.Standard,
        shape = RoundedCornerShape(geometry.radiusMd),
        contentPadding = PaddingValues(
            horizontal = spacing.compactRowHorizontalPadding,
            vertical = spacing.compactRowVerticalPadding
        ),
        onClick = onClick,
        onClickLabel = title
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (leadingIcon != null) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(geometry.radiusSm))
                            .background(
                                (leadingBadgeColor ?: colors.lavender).copy(
                                    alpha = if (colors.isDark) 0.22f else 0.16f
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        leadingIcon()
                    }
                    Spacer(Modifier.width(12.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = typography.transactionDescription,
                        color = colors.primaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            style = typography.caption,
                            color = colors.secondaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (trailingPrimaryText != null || trailingSecondaryText != null) {
                Spacer(Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.End) {
                    if (trailingPrimaryText != null) {
                        Text(
                            text = trailingPrimaryText,
                            style = typography.transactionAmount,
                            color = trailingPrimaryColor ?: colors.primaryText,
                            maxLines = 1
                        )
                    }
                    if (!trailingSecondaryText.isNullOrBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = trailingSecondaryText,
                            style = typography.caption,
                            color = colors.secondaryText,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/**
 * Motion-ready expandable glass container that respects Reduce Motion.
 */
@Composable
fun SpatialExpandableCard(
    title: String,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    badgeLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = SpatialTheme.colors
    val typography = SpatialTheme.typography
    val reducedMotion = rememberReducedMotionEnabled()

    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = prismaticSpring(reducedMotion = reducedMotion),
        label = "spatial_expand_chevron"
    )

    SpatialGlassCard(
        modifier = modifier.fillMaxWidth(),
        tier = if (expanded) SpatialGlassTier.Elevated else SpatialGlassTier.Standard,
        onClick = onToggleExpanded,
        onClickLabel = if (expanded) "Collapse $title" else "Expand $title"
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = typography.cardTitle,
                    color = colors.primaryText
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = typography.supportingInfo,
                        color = colors.secondaryText
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (badgeLabel != null) {
                    SpatialGlassPill(label = badgeLabel, selected = expanded)
                }
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = colors.secondaryText,
                    modifier = Modifier.graphicsLayer { rotationZ = chevronRotation }
                )
            }
        }

        if (reducedMotion) {
            if (expanded) {
                Spacer(Modifier.height(12.dp))
                content()
            }
        } else {
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(prismaticTween(false, 180)) + expandVertically(prismaticSpring(false)),
                exit = fadeOut(prismaticTween(false, 140)) + shrinkVertically(prismaticSpring(false))
            ) {
                Column(modifier = Modifier.padding(top = 12.dp), content = content)
            }
        }
    }
}
