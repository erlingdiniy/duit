package com.diniy.duit.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import com.diniy.duit.core.ui.theme.LocalPrismaticGlass
import com.diniy.duit.core.ui.theme.PrismaticPalette

/**
 * Calm, edge-to-edge Prismatic Glass canvas with soft ambient lighting.
 * Uses lightweight radial gradients rather than expensive blurs for smooth scrolling.
 */
@Composable
fun PrismaticBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val glass = LocalPrismaticGlass.current
    val baseBackground = MaterialTheme.colorScheme.background
    val aquaAlpha = if (glass.isDark) 0.08f else 0.11f
    val lavenderAlpha = if (glass.isDark) 0.09f else 0.10f
    val skyAlpha = if (glass.isDark) 0.07f else 0.09f
    val peachAlpha = if (glass.isDark) 0.05f else 0.07f

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(baseBackground)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val radius = maxOf(w, h) * 0.68f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        PrismaticPalette.Aqua.copy(alpha = aquaAlpha),
                        PrismaticPalette.Aqua.copy(alpha = 0f)
                    ),
                    center = Offset(w * 0.14f, h * 0.08f),
                    radius = radius
                ),
                radius = radius,
                center = Offset(w * 0.14f, h * 0.08f)
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        PrismaticPalette.Lavender.copy(alpha = lavenderAlpha),
                        PrismaticPalette.Lavender.copy(alpha = 0f)
                    ),
                    center = Offset(w * 0.90f, h * 0.18f),
                    radius = radius * 0.88f
                ),
                radius = radius * 0.88f,
                center = Offset(w * 0.90f, h * 0.18f)
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        PrismaticPalette.SkyBlue.copy(alpha = skyAlpha),
                        PrismaticPalette.SkyBlue.copy(alpha = 0f)
                    ),
                    center = Offset(w * 0.18f, h * 0.86f),
                    radius = radius * 0.80f
                ),
                radius = radius * 0.80f,
                center = Offset(w * 0.18f, h * 0.86f)
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        PrismaticPalette.Peach.copy(alpha = peachAlpha),
                        PrismaticPalette.Peach.copy(alpha = 0f)
                    ),
                    center = Offset(w * 0.86f, h * 0.80f),
                    radius = radius * 0.65f
                ),
                radius = radius * 0.65f,
                center = Offset(w * 0.86f, h * 0.80f)
            )
        }
        content()
    }
}
