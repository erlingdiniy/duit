package com.diniy.duit.core.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

enum class GlassCardTier {
    Standard,
    Elevated,
    Muted
}

/**
 * Reusable Candy Pop card (name kept from Phase 6A for API stability).
 * Backed by [SpatialGlassCard]; pass [sticker] = true for the Sticker Pop outline + offset shadow.
 */
@Composable
fun PrismaticGlassCard(
    modifier: Modifier = Modifier,
    tier: GlassCardTier = GlassCardTier.Standard,
    accentTint: Color? = null,
    shape: Shape = MaterialTheme.shapes.large,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    enabled: Boolean = true,
    sticker: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val spatialTier = when (tier) {
        GlassCardTier.Standard -> SpatialGlassTier.Standard
        GlassCardTier.Elevated -> SpatialGlassTier.Elevated
        GlassCardTier.Muted -> SpatialGlassTier.Subtle
    }

    SpatialGlassCard(
        modifier = modifier,
        tier = spatialTier,
        accentTint = accentTint,
        shape = shape,
        contentPadding = contentPadding,
        enabled = enabled,
        onClick = onClick,
        onClickLabel = onClickLabel,
        sticker = sticker,
        content = content
    )
}
