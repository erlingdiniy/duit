package com.diniy.duit.devcenter.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.diniy.duit.core.ui.components.PrismaticButton
import com.diniy.duit.core.ui.components.PrismaticButtonVariant
import com.diniy.duit.core.ui.components.SpatialGlassCard
import com.diniy.duit.core.ui.components.SpatialGlassTier
import com.diniy.duit.core.ui.components.rememberReducedMotionEnabled
import com.diniy.duit.core.ui.theme.CandyBodyFamily
import com.diniy.duit.core.ui.theme.LocalVisualTheme
import com.diniy.duit.core.ui.theme.SpatialTheme
import com.diniy.duit.domain.model.VisualTheme

/**
 * Semantic Developer Center theme tokens adapting Candy Bubble 2.0 and Pure Liquid Glass,
 * with a clean seam ready for future Mono. high-contrast monochrome support.
 */
enum class DevVisualSurfaceStyle {
    CANDY_BUBBLE,
    LIQUID_GLASS,
    MONO_READY
}

val LocalDevBottomContentPadding = staticCompositionLocalOf<Dp> { 28.dp }

@Composable
internal fun devListContentPadding(
    top: Dp = 8.dp,
    horizontal: Dp = 16.dp
): PaddingValues = PaddingValues(
    start = horizontal,
    top = top,
    end = horizontal,
    bottom = LocalDevBottomContentPadding.current
)

data class DevCenterThemeTokens(
    val style: DevVisualSurfaceStyle,
    val primaryAccent: Color,
    val secondaryAccent: Color,
    val healthyTint: Color,
    val warningTint: Color,
    val dangerTint: Color,
    val chartStrokeWidthDp: Float,
    val cardCornerDp: Int,
    val innerCornerDp: Int,
    val sectionSpacingDp: Int,
    val gridGapDp: Int,
    val contentHorizontalPaddingDp: Int,
    val kpiTextStyle: TextStyle,
    val compactKpiTextStyle: TextStyle,
    val navContainerColor: Color,
    val navIndicatorColor: Color,
    val navActiveContentColor: Color,
    val navInactiveContentColor: Color,
    val dividerColor: Color,
    val skeletonBaseColor: Color,
    val skeletonHighlightColor: Color
)

@Composable
internal fun rememberDevThemeTokens(): DevCenterThemeTokens {
    val colors = SpatialTheme.colors
    val visual = LocalVisualTheme.current
    val kpiBase = TextStyle(
        fontFamily = CandyBodyFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.4).sp,
        fontFeatureSettings = "tnum"
    )
    val kpiCompact = TextStyle(
        fontFamily = CandyBodyFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.2).sp,
        fontFeatureSettings = "tnum"
    )
    return when (visual) {
        VisualTheme.LIQUID_GLASS -> DevCenterThemeTokens(
            style = DevVisualSurfaceStyle.LIQUID_GLASS,
            primaryAccent = colors.primaryAction,
            secondaryAccent = colors.skyBlue,
            healthyTint = colors.success,
            warningTint = colors.warning,
            dangerTint = colors.error,
            chartStrokeWidthDp = 2.5f,
            cardCornerDp = 24,
            innerCornerDp = 16,
            sectionSpacingDp = 12,
            gridGapDp = 10,
            contentHorizontalPaddingDp = 16,
            kpiTextStyle = kpiBase,
            compactKpiTextStyle = kpiCompact,
            navContainerColor = colors.floatingNavSurface.copy(alpha = if (colors.isDark) 0.88f else 0.92f),
            navIndicatorColor = colors.primaryAction.copy(alpha = if (colors.isDark) 0.22f else 0.14f),
            navActiveContentColor = colors.primaryAction,
            navInactiveContentColor = colors.secondaryText,
            dividerColor = colors.divider,
            skeletonBaseColor = colors.baseSurface,
            skeletonHighlightColor = colors.elevatedSurface
        )
        VisualTheme.CANDY_BUBBLE -> DevCenterThemeTokens(
            style = DevVisualSurfaceStyle.CANDY_BUBBLE,
            primaryAccent = colors.coral,
            secondaryAccent = colors.aqua,
            healthyTint = colors.success,
            warningTint = colors.warning,
            dangerTint = colors.error,
            chartStrokeWidthDp = 3f,
            cardCornerDp = 26,
            innerCornerDp = 18,
            sectionSpacingDp = 12,
            gridGapDp = 10,
            contentHorizontalPaddingDp = 16,
            kpiTextStyle = kpiBase,
            compactKpiTextStyle = kpiCompact,
            navContainerColor = colors.floatingNavSurface,
            navIndicatorColor = colors.coral,
            navActiveContentColor = colors.onCoral,
            navInactiveContentColor = colors.secondaryText,
            dividerColor = colors.divider,
            skeletonBaseColor = colors.baseSurface,
            skeletonHighlightColor = colors.elevatedSurface
        )
    }
}

@Composable
internal fun DevMetricTile(
    title: String,
    value: String,
    sub: String,
    modifier: Modifier = Modifier,
    accentTint: Color? = null,
    icon: ImageVector? = null
) {
    val colors = SpatialTheme.colors
    val typography = SpatialTheme.typography
    val tokens = rememberDevThemeTokens()
    val tint = accentTint ?: tokens.primaryAccent

    SpatialGlassCard(
        modifier = modifier.heightIn(min = 102.dp),
        tier = SpatialGlassTier.Standard,
        accentTint = accentTint,
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = typography.caption,
                color = colors.secondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 6.dp)
            )
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(tint.copy(alpha = if (colors.isDark) 0.22f else 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (colors.isDark) tint else colors.readableOn(tint),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = value,
            style = if (value.length > 8) tokens.compactKpiTextStyle else tokens.kpiTextStyle,
            color = colors.primaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = sub,
            style = MaterialTheme.typography.bodySmall,
            color = colors.secondaryText,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun DevSparklineChart(
    labels: List<String>,
    values: List<Float>,
    unitSuffix: String,
    modifier: Modifier = Modifier
) {
    val colors = SpatialTheme.colors
    val devTokens = rememberDevThemeTokens()
    if (values.isEmpty()) {
        Text(
            text = "No trend points available.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.secondaryText
        )
        return
    }
    val maxVal = (values.maxOrNull() ?: 1f).coerceAtLeast(1f)
    val peakIndex = values.indices.maxByOrNull { values[it] } ?: values.lastIndex
    val latest = values.last().toLong()
    val peak = values[peakIndex].toLong()
    val a11y = "Trend chart: latest $latest$unitSuffix, peak $peak$unitSuffix across ${values.size} points"
    val guideColor = colors.divider.copy(alpha = if (colors.isDark) 0.65f else 0.55f)

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp)
                .semantics { contentDescription = a11y }
        ) {
            val w = size.width
            val h = size.height
            val padY = 8.dp.toPx()
            val padX = 4.dp.toPx()
            val usableW = (w - padX * 2f).coerceAtLeast(1f)
            val usableH = (h - padY * 2f).coerceAtLeast(1f)

            // Horizontal guide lines (top, mid, baseline)
            listOf(0f, 0.5f, 1f).forEach { fraction ->
                val gy = padY + fraction * usableH
                drawLine(
                    color = guideColor,
                    start = Offset(padX, gy),
                    end = Offset(w - padX, gy),
                    strokeWidth = 1.dp.toPx()
                )
            }

            val points = if (values.size == 1) {
                val y = h - padY - (values[0] / maxVal).coerceIn(0f, 1f) * usableH
                listOf(Offset(padX, y), Offset(w - padX, y))
            } else {
                values.mapIndexed { idx, v ->
                    val x = padX + (idx.toFloat() / (values.size - 1).toFloat()) * usableW
                    val y = h - padY - ((v / maxVal).coerceIn(0f, 1f) * usableH)
                    Offset(x, y)
                }
            }
            val linePath = Path().apply {
                points.forEachIndexed { idx, pt ->
                    if (idx == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
                }
            }
            val fillPath = Path().apply {
                addPath(linePath)
                lineTo(points.last().x, h - padY)
                lineTo(points.first().x, h - padY)
                close()
            }
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(devTokens.primaryAccent.copy(alpha = 0.24f), Color.Transparent)
                )
            )
            drawPath(
                path = linePath,
                color = devTokens.primaryAccent,
                style = Stroke(
                    width = devTokens.chartStrokeWidthDp.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // Peak marker (if distinct from latest)
            val peakPt = points[peakIndex.coerceIn(0, points.lastIndex)]
            if (peakIndex != points.lastIndex) {
                drawCircle(
                    color = devTokens.secondaryAccent,
                    radius = 3.5.dp.toPx(),
                    center = peakPt
                )
            }

            // Latest point marker with soft outer halo
            val latestPt = points.last()
            drawCircle(
                color = devTokens.primaryAccent.copy(alpha = 0.22f),
                radius = 7.dp.toPx(),
                center = latestPt
            )
            drawCircle(
                color = devTokens.primaryAccent,
                radius = 4.dp.toPx(),
                center = latestPt
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = labels.firstOrNull().orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = colors.secondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "Peak $peak$unitSuffix",
                style = MaterialTheme.typography.labelSmall,
                color = colors.primaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1.2f)
            )
            Text(
                text = labels.lastOrNull().orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = colors.secondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
internal fun DevRankedBarList(
    rows: List<Triple<String, Long, String>>,
    accent: Color = rememberDevThemeTokens().primaryAccent
) {
    val colors = SpatialTheme.colors
    if (rows.isEmpty()) {
        Text(
            text = "No entries observed in this period.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.secondaryText
        )
        return
    }
    val maxCount = rows.maxOf { it.second }.coerceAtLeast(1L)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { (label, count, detail) ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.primaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.secondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                LinearProgressIndicator(
                    progress = { (count.toFloat() / maxCount.toFloat()).coerceIn(0.03f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = accent,
                    trackColor = colors.baseSurface
                )
            }
        }
    }
}

@Composable
internal fun DevLoadingSkeletonCard(
    lines: Int = 3,
    modifier: Modifier = Modifier
) {
    val tokens = rememberDevThemeTokens()
    val reducedMotion = rememberReducedMotionEnabled()
    val pulseAlpha = if (reducedMotion) {
        0.55f
    } else {
        val transition = rememberInfiniteTransition(label = "dev_skeleton")
        val animated by transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.80f,
            animationSpec = infiniteRepeatable(
                animation = tween(900),
                repeatMode = RepeatMode.Reverse
            ),
            label = "dev_skeleton_alpha"
        )
        animated
    }

    SpatialGlassCard(
        modifier = modifier.fillMaxWidth(),
        tier = SpatialGlassTier.Standard
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.42f)
                    .height(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(tokens.skeletonBaseColor.copy(alpha = pulseAlpha))
            )
            repeat(lines.coerceIn(1, 5)) { idx ->
                val widthFraction = when (idx % 3) {
                    0 -> 0.92f
                    1 -> 0.74f
                    else -> 0.58f
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth(widthFraction)
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(tokens.skeletonBaseColor.copy(alpha = pulseAlpha))
                )
            }
        }
    }
}

@Composable
internal fun DevEmptyStateCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val colors = SpatialTheme.colors
    val tokens = rememberDevThemeTokens()
    SpatialGlassCard(
        modifier = modifier.fillMaxWidth(),
        tier = SpatialGlassTier.Standard
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(tokens.primaryAccent.copy(alpha = if (colors.isDark) 0.20f else 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (colors.isDark) tokens.primaryAccent else colors.primaryAction,
                    modifier = Modifier.size(22.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.primaryText,
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.secondaryText,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(2.dp))
                PrismaticButton(
                    text = actionLabel,
                    onClick = onAction,
                    variant = PrismaticButtonVariant.Secondary,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
internal fun DevErrorStateCard(
    title: String,
    message: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null
) {
    val colors = SpatialTheme.colors
    SpatialGlassCard(
        modifier = modifier.fillMaxWidth(),
        tier = SpatialGlassTier.Standard,
        accentTint = colors.error
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = colors.error,
                modifier = Modifier.size(20.dp)
            )
            DevSectionTitle(title = title, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = colors.secondaryText
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PrismaticButton(
                text = primaryLabel,
                onClick = onPrimary,
                variant = PrismaticButtonVariant.Primary,
                modifier = Modifier.weight(1f)
            )
            if (secondaryLabel != null && onSecondary != null) {
                PrismaticButton(
                    text = secondaryLabel,
                    onClick = onSecondary,
                    variant = PrismaticButtonVariant.Secondary,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
internal fun DevUnavailableCard(
    title: String,
    summary: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val colors = SpatialTheme.colors
    SpatialGlassCard(
        modifier = Modifier.fillMaxWidth(),
        tier = SpatialGlassTier.Standard,
        accentTint = colors.warning
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            DevSectionTitle(title, modifier = Modifier.weight(1f).padding(end = 8.dp))
            DevToneBadge(label = "Unavailable", tone = "warning")
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = summary,
            style = MaterialTheme.typography.bodySmall,
            color = colors.secondaryText
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(10.dp))
            PrismaticButton(
                text = actionLabel,
                onClick = onAction,
                variant = PrismaticButtonVariant.Secondary,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
