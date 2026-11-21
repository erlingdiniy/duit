package com.diniy.duit.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Candy card dialog: fades and scales in once (about 260 ms), never taller than the screen allows. */
@Composable
fun CandyDialog(
    onDismiss: () -> Unit,
    dismissible: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val reduced = rememberReducedMotionEnabled()
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.86f).dp
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = dismissible
        )
    ) {
        val enter = remember { Animatable(if (reduced) 1f else 0f) }
        LaunchedEffect(Unit) { if (!reduced) enter.animateTo(1f, tween(260)) }
        CandyBubbleCard(
            tone = CandyTone.Peach,
            shape = RoundedCornerShape(32.dp),
            contentPadding = PaddingValues(22.dp),
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .graphicsLayer {
                    alpha = enter.value
                    val s = 0.92f + 0.08f * enter.value
                    scaleX = s
                    scaleY = s
                }
        ) { content() }
    }
}
