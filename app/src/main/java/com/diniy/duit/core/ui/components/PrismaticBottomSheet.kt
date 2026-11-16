package com.diniy.duit.core.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Reusable Prismatic Spatial Glass modal bottom sheet with rounded 28dp top corners,
 * accessible pane title, proper IME/navigation-bar inset ordering before `verticalScroll`,
 * and scrollable content body.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrismaticModalBottomSheet(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable ColumnScope.() -> Unit
) {
    SpatialGlassBottomSheetSurface(
        title = title,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        subtitle = subtitle,
        sheetState = sheetState,
        content = content
    )
}
