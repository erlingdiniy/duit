package com.diniy.duit.devcenter.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.diniy.duit.core.ui.components.PrismaticButton
import com.diniy.duit.core.ui.components.PrismaticButtonVariant
import com.diniy.duit.core.ui.components.SpatialGlassCard
import com.diniy.duit.core.ui.components.SpatialGlassTier
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

data class DevCenterThemeTokens(
    val style: DevVisualSurfaceStyle,
    val primaryAccent: Color,
    val secondaryAccent: Color,
    val healthyTint: Color,
    val warningTint: Color,
    val dangerTint: Color,
    val chartStrokeWidthDp: Float,
    val cardCornerDp: Int
)

@Composable
internal fun rememberDevThemeTokens(): DevCenterThemeTokens {
    val colors = SpatialTheme.colors
    val visual = LocalVisualTheme.current
    return when (visual) {
        VisualTheme.LIQUID_GLASS -> DevCenterThemeTokens(
            style = DevVisualSurfaceStyle.LIQUID_GLASS,
            primaryAccent = colors.primaryAction,
            secondaryAccent = colors.skyBlue,
            healthyTint = colors.success,
            warningTint = colors.warning,
            dangerTint = colors.error,
            chartStrokeWidthDp = 2.4f,
            cardCornerDp = 24
        )
        VisualTheme.CANDY_BUBBLE -> DevCenterThemeTokens(
            style = DevVisualSurfaceStyle.CANDY_BUBBLE,
            primaryAccent = colors.lavender,
            secondaryAccent = colors.aqua,
            healthyTint = colors.success,
            warningTint = colors.warning,
            dangerTint = colors.error,
            chartStrokeWidthDp = 3f,
            cardCornerDp = 28
        )
    }
}

@Composable
internal fun DevMetricTile(
    title: String,
    value: String,
    sub: String,
    modifier: Modifier = Modifier,
    accentTint: Color? = null
) {
    val colors = SpatialTheme.colors
    val typography = SpatialTheme.typography
    SpatialGlassCard(
        modifier = modifier,
        tier = GlassCardTierBridge.standard(),
        accentTint = accentTint,
        contentPadding = PaddingValues(14.dp)
    ) {
        Text(
            text = title,
            style = typography.caption,
            color = colors.secondaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = colors.primaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = sub,
            style = MaterialTheme.typography.bodySmall,
            color = colors.secondaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private object GlassCardTierBridge {
    fun standard() = SpatialGlassTier.Standard
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
    val latest = values.last().toLong()
    val peak = values.maxOrNull()?.toLong() ?: 0L
    val a11y = "Trend chart: latest $latest$unitSuffix, peak $peak$unitSuffix across ${values.size} points"

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .semantics { contentDescription = a11y }
        ) {
            val w = size.width
            val h = size.height
            val padY = 6.dp.toPx()
            val usableH = (h - padY * 2f).coerceAtLeast(1f)
            val points = if (values.size == 1) {
                listOf(
                    Offset(0f, h - padY - (values[0] / maxVal) * usableH),
                    Offset(w, h - padY - (values[0] / maxVal) * usableH)
                )
            } else {
                values.mapIndexed { idx, v ->
                    val x = (idx.toFloat() / (values.size - 1).toFloat()) * w
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
                lineTo(points.last().x, h)
                lineTo(points.first().x, h)
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
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = labels.firstOrNull().orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = colors.secondaryText
            )
            Text(
                text = "Peak: $peak$unitSuffix",
                style = MaterialTheme.typography.labelSmall,
                color = colors.secondaryText
            )
            Text(
                text = labels.lastOrNull().orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = colors.secondaryText
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { (label, count, detail) ->
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
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
                        color = colors.secondaryText
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
            DevSectionTitle(title)
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
