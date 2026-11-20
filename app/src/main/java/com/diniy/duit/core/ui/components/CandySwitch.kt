package com.diniy.duit.core.ui.components

import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.diniy.duit.BuildConfig
import com.diniy.duit.core.ui.theme.SpatialTheme

/**
 * Soft Candy switch (public edition): no outline in either state, coral track when on, soft peach
 * track when off, white thumb. Focus and pressed states keep Material's own indication. The personal
 * edition keeps whatever colours the call site used before ([personal]).
 */
@Composable
fun candySwitchColors(personal: SwitchColors = SwitchDefaults.colors()): SwitchColors {
    if (!BuildConfig.PUBLIC_EDITION) return personal
    val colors = SpatialTheme.colors
    return SwitchDefaults.colors(
        checkedThumbColor = Color.White,
        checkedTrackColor = colors.coral,
        checkedBorderColor = Color.Transparent,
        checkedIconColor = colors.coral,
        uncheckedThumbColor = Color.White,
        uncheckedTrackColor = colors.peach.copy(alpha = if (colors.isDark) 0.45f else 0.55f),
        uncheckedBorderColor = Color.Transparent,
        uncheckedIconColor = colors.secondaryText
    )
}
