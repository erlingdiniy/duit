package com.diniy.duit.core.ui.components

import androidx.compose.ui.draw.rotate
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.diniy.duit.core.ui.theme.LocalPrismaticGlass

/**
 * Lightweight animated placeholder card shown while local data flows initialize.
 */
@Composable
fun PrismaticLoadingCard(
    modifier: Modifier = Modifier
) {
    val reducedMotion = rememberReducedMotionEnabled()
    val alpha = if (reducedMotion) {
        0.35f
    } else {
        val infiniteTransition = rememberInfiniteTransition(label = "loading_pulse")
        val animatedAlpha by infiniteTransition.animateFloat(
            initialValue = 0.24f,
            targetValue = 0.50f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 850),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse_alpha"
        )
        animatedAlpha
    }
    val barColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)

    PrismaticGlassCard(
        modifier = modifier.fillMaxWidth(),
        tier = GlassCardTier.Standard
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.36f)
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(barColor)
        )
        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.62f)
                .height(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(barColor)
        )
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(barColor)
        )
    }
}

/**
 * Refined empty state card used when no user records exist yet.
 */
@Composable
fun PrismaticEmptyStateCard(
    @DrawableRes iconRes: Int,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
    onSecondaryActionClick: (() -> Unit)? = null
) {
    val glass = LocalPrismaticGlass.current

    // Gentle one-time entrance (220 ms); skipped under Reduce Motion.
    val reduced = rememberReducedMotionEnabled()
    var entered by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(reduced) }
    androidx.compose.runtime.LaunchedEffect(Unit) { entered = true }
    val enter by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = prismaticTween(reduced, 220),
        label = "empty_state_enter"
    )
    CandyBubbleCard(
        tone = CandyTone.Peach,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = enter
                translationY = (1f - enter) * 24f
            },
        contentPadding = PaddingValues(18.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            val spatialColors = com.diniy.duit.core.ui.theme.SpatialTheme.colors
            val isLiquidGlass = com.diniy.duit.core.ui.theme.LocalVisualTheme.current ==
                com.diniy.duit.domain.model.VisualTheme.LIQUID_GLASS
            Box(
                modifier = Modifier
                    .size(if (isLiquidGlass) 44.dp else 48.dp)
                    .rotate(if (isLiquidGlass) 0f else -8f)
                    .clip(RoundedCornerShape(if (isLiquidGlass) 14.dp else 17.dp))
                    .background(
                        if (isLiquidGlass) spatialColors.primaryAction.copy(alpha = if (spatialColors.isDark) 0.16f else 0.10f)
                        else glass.lavenderAccent
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    tint = if (isLiquidGlass) spatialColors.primaryAction else spatialColors.onCoral,
                    modifier = Modifier
                        .size(if (isLiquidGlass) 22.dp else 24.dp)
                        .rotate(if (isLiquidGlass) 0f else 8f)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (actionLabel != null && onActionClick != null) {
            Spacer(Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                PrismaticButton(
                    text = actionLabel,
                    onClick = onActionClick,
                    variant = PrismaticButtonVariant.Primary,
                    modifier = Modifier.weight(1f)
                )
                if (secondaryActionLabel != null && onSecondaryActionClick != null) {
                    PrismaticButton(
                        text = secondaryActionLabel,
                        onClick = onSecondaryActionClick,
                        variant = PrismaticButtonVariant.Secondary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * Polite live-region error banner for recoverable local persistence or validation errors.
 */
@Composable
fun PrismaticErrorBanner(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null
) {
    PrismaticGlassCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        tier = GlassCardTier.Elevated,
        accentTint = MaterialTheme.colorScheme.error,
        contentPadding = PaddingValues(16.dp)
    ) {
        Text(
            text = "Unable to complete action",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (onRetry != null || onDismiss != null) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onRetry != null) {
                    PrismaticButton(
                        text = "Retry",
                        onClick = onRetry,
                        variant = PrismaticButtonVariant.Secondary
                    )
                }
                if (onDismiss != null) {
                    PrismaticButton(
                        text = "Dismiss",
                        onClick = onDismiss,
                        variant = PrismaticButtonVariant.Ghost
                    )
                }
            }
        }
    }
}

/**
 * Disclosure banner shown whenever in-memory Sample Preview Mode is active.
 */
@Composable
fun SamplePreviewBanner(
    onExitPreview: () -> Unit,
    modifier: Modifier = Modifier
) {
    val glass = LocalPrismaticGlass.current

    PrismaticGlassCard(
        modifier = modifier.fillMaxWidth(),
        tier = GlassCardTier.Elevated,
        accentTint = glass.peachAccent,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Preview Mode",
                    style = MaterialTheme.typography.titleSmall,
                    color = glass.cautionText
                )
                Text(
                    text = "Showing sample figures only. Your saved data stays untouched.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            PrismaticButton(
                text = "Exit",
                onClick = onExitPreview,
                variant = PrismaticButtonVariant.Secondary
            )
        }
    }
}

/**
 * Semantic status pill with accessible contrast.
 */
@Composable
fun PrismaticBadge(
    label: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(containerColor)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            maxLines = 1
        )
    }
}
