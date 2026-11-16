package com.diniy.duit.widgets

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

enum class DuitWidgetKind {
    BALANCE,
    UPCOMING_PAYMENTS,
    SAVINGS_GOALS,
    QUICK_ADD
}

enum class WidgetLayoutTier {
    COMPACT,
    MEDIUM,
    EXPANDED
}

data class WidgetAdaptiveSpec(
    val tier: WidgetLayoutTier,
    val maxItems: Int,
    val showSecondaryMetrics: Boolean,
    val showHeaderBadge: Boolean,
    val showFooterDetails: Boolean,
    val useCompactLabels: Boolean,
    val useHorizontalLayout: Boolean
)

/**
 * Pure adaptive sizing engine mapping any launcher widget width/height in dp
 * (including intermediate grid spans) to structured content placement rules.
 */
object WidgetLayoutPolicy {

    val RESPONSIVE_SIZES: Set<DpSize> = setOf(
        DpSize(110.dp, 64.dp),
        DpSize(110.dp, 110.dp),
        DpSize(140.dp, 176.dp),
        DpSize(184.dp, 110.dp),
        DpSize(184.dp, 176.dp),
        DpSize(250.dp, 110.dp),
        DpSize(250.dp, 176.dp),
        DpSize(280.dp, 240.dp)
    )

    fun resolve(
        widthDp: Float,
        heightDp: Float,
        kind: DuitWidgetKind
    ): WidgetAdaptiveSpec {
        val w = widthDp.coerceAtLeast(0f)
        val h = heightDp.coerceAtLeast(0f)

        val tier = when {
            w >= 230f && h >= 165f -> WidgetLayoutTier.EXPANDED
            w >= 210f && h >= 98f -> WidgetLayoutTier.MEDIUM
            w >= 165f && h >= 150f -> WidgetLayoutTier.MEDIUM
            kind == DuitWidgetKind.QUICK_ADD && w >= 210f && h >= 56f -> WidgetLayoutTier.MEDIUM
            else -> WidgetLayoutTier.COMPACT
        }

        val maxItems = when (kind) {
            DuitWidgetKind.BALANCE -> when (tier) {
                WidgetLayoutTier.COMPACT -> 1
                WidgetLayoutTier.MEDIUM -> 3
                WidgetLayoutTier.EXPANDED -> 5
            }
            DuitWidgetKind.UPCOMING_PAYMENTS -> when (tier) {
                WidgetLayoutTier.COMPACT -> if (h >= 150f) 2 else 1
                WidgetLayoutTier.MEDIUM -> if (w >= 230f) 3 else 2
                WidgetLayoutTier.EXPANDED -> if (h >= 220f) 5 else 4
            }
            DuitWidgetKind.SAVINGS_GOALS -> when (tier) {
                WidgetLayoutTier.COMPACT -> 1
                WidgetLayoutTier.MEDIUM -> if (h >= 145f) 2 else 1
                WidgetLayoutTier.EXPANDED -> if (h >= 220f) 4 else 3
            }
            DuitWidgetKind.QUICK_ADD -> when (tier) {
                WidgetLayoutTier.COMPACT -> 2
                WidgetLayoutTier.MEDIUM -> 3
                WidgetLayoutTier.EXPANDED -> 6
            }
        }

        val useHorizontal = when (kind) {
            DuitWidgetKind.QUICK_ADD -> w >= 155f || h < 95f
            else -> w >= 195f
        }

        return WidgetAdaptiveSpec(
            tier = tier,
            maxItems = maxItems,
            showSecondaryMetrics = tier != WidgetLayoutTier.COMPACT || h >= 145f,
            showHeaderBadge = w >= 150f && h >= 88f,
            showFooterDetails = tier == WidgetLayoutTier.EXPANDED || (tier == WidgetLayoutTier.MEDIUM && h >= 135f),
            useCompactLabels = w < 175f || h < 90f,
            useHorizontalLayout = useHorizontal
        )
    }
}
