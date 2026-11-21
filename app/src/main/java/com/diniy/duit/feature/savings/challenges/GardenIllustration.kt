package com.diniy.duit.feature.savings.challenges

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.size
import com.diniy.duit.core.ui.components.prismaticTween
import com.diniy.duit.core.ui.components.rememberReducedMotionEnabled
import com.diniy.duit.core.ui.theme.SpatialTheme
import com.diniy.duit.domain.challenge.GardenStage

/**
 * One Growing Garden plant drawn with Compose shapes: a seed in soil, then stem, leaves, bud and flower
 * as the challenge reaches 25%, 50%, 75% and 100%. Growth animates (instantly under Reduce Motion).
 */
@Composable
fun GardenIllustration(
    stage: GardenStage,
    size: Dp,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
    description: String = "Garden stage ${stage.label()}"
) {
    val colors = SpatialTheme.colors
    val growth by animateFloatAsState(
        targetValue = stage.ordinal / GardenStage.HARVEST.ordinal.toFloat(),
        animationSpec = prismaticTween(rememberReducedMotionEnabled(), 500),
        label = "garden_growth"
    )
    val soil = colors.peach
    val seed = colors.secondaryText.copy(alpha = 0.55f)
    val leaf = colors.income
    val petal = colors.coral
    val centre = colors.sunny
    Canvas(
        modifier
            .size(size)
            .alpha(if (muted) 0.3f else 1f)
            .semantics { contentDescription = description }
    ) {
        val w = this.size.width
        val h = this.size.height
        val groundTop = h * 0.78f
        drawRoundRect(soil, topLeft = Offset(w * 0.12f, groundTop), size = Size(w * 0.76f, h * 0.16f), cornerRadius = CornerRadius(h * 0.08f))
        val baseX = w / 2f
        if (stage == GardenStage.SEED) {
            drawOval(seed, topLeft = Offset(baseX - w * 0.08f, groundTop - h * 0.07f), size = Size(w * 0.16f, h * 0.11f))
            drawOval(leaf, topLeft = Offset(baseX - w * 0.03f, groundTop - h * 0.12f), size = Size(w * 0.06f, h * 0.07f))
            return@Canvas
        }
        val stemTop = groundTop - h * (0.18f + 0.42f * growth)
        drawLine(leaf, Offset(baseX, groundTop), Offset(baseX, stemTop), strokeWidth = w * 0.06f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        // Leaves: one from Sprout, a second from Bud.
        val leafSize = Size(w * 0.22f, h * 0.11f)
        rotate(-30f, pivot = Offset(baseX, groundTop - h * 0.16f)) {
            drawOval(leaf, topLeft = Offset(baseX, groundTop - h * 0.22f), size = leafSize)
        }
        if (stage >= GardenStage.BUD) rotate(30f, pivot = Offset(baseX, groundTop - h * 0.28f)) {
            drawOval(leaf, topLeft = Offset(baseX - leafSize.width, groundTop - h * 0.34f), size = leafSize)
        }
        when (stage) {
            GardenStage.BUD -> drawOval(petal, topLeft = Offset(baseX - w * 0.07f, stemTop - h * 0.12f), size = Size(w * 0.14f, h * 0.16f))
            GardenStage.BLOOM, GardenStage.HARVEST -> {
                val r = w * (if (stage == GardenStage.HARVEST) 0.11f else 0.08f)
                val ring = w * (if (stage == GardenStage.HARVEST) 0.13f else 0.1f)
                for (i in 0 until 6) {
                    val a = Math.toRadians(i * 60.0)
                    drawCircle(petal, radius = r, center = Offset(baseX + (ring * kotlin.math.cos(a)).toFloat(), stemTop + (ring * kotlin.math.sin(a)).toFloat()))
                }
                drawCircle(centre, radius = r * 0.9f, center = Offset(baseX, stemTop))
                if (stage == GardenStage.HARVEST) {
                    drawCircle(centre, radius = w * 0.07f, center = Offset(w * 0.84f, h * 0.14f))
                    drawCircle(centre.copy(alpha = 0.4f), radius = w * 0.11f, center = Offset(w * 0.84f, h * 0.14f), style = Stroke(w * 0.015f))
                }
            }
            else -> Unit
        }
    }
}
