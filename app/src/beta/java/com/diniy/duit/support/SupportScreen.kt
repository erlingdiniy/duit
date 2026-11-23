package com.diniy.duit.support

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.diniy.duit.core.ui.components.CandyBubbleCard
import com.diniy.duit.core.ui.components.CandyIconBlob
import com.diniy.duit.core.ui.components.CandyStatusBadge
import com.diniy.duit.core.ui.components.CandyTone
import com.diniy.duit.core.ui.components.PrismaticButton
import com.diniy.duit.core.ui.components.PrismaticButtonVariant
import com.diniy.duit.core.ui.components.rememberReducedMotionEnabled
import com.diniy.duit.core.ui.theme.SpatialTheme

/**
 * Settings > Support duit. stays visible in public builds without bundling any personal QR code,
 * recipient details, or payment services.
 */
@Suppress("UNUSED_PARAMETER")
internal fun isSupportPageAvailable(context: Context): Boolean = true

/** Settings > Support duit. Plain informational modal: no payments or tracking happen in the app. */
@Composable
internal fun SupportDuitScreen(onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        SupportContent(onClose = onClose)
    }
}

@Composable
internal fun SupportContent(onClose: () -> Unit) {
    val colors = SpatialTheme.colors
    val type = SpatialTheme.typography
    val reduced = rememberReducedMotionEnabled()
    val pop = remember { Animatable(if (reduced) 1f else 0.6f) }
    LaunchedEffect(Unit) {
        if (!reduced) pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow))
    }

    Box(Modifier.fillMaxSize().statusBarsPadding().padding(12.dp), contentAlignment = Alignment.Center) {
        CandyBubbleCard(
            tone = CandyTone.Peach,
            shape = RoundedCornerShape(32.dp),
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            ) {
                CandyIconBlob(
                    Icons.Rounded.LocalCafe,
                    CandyTone.Coral,
                    size = 68.dp,
                    tilt = -8f,
                    solid = true,
                    modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value }
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Support duit. 💛",
                    style = type.screenHeading,
                    color = colors.primaryText,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    "Thank you for using duit.",
                    style = type.cardTitle,
                    color = colors.secondaryText,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "duit. is made by an independent developer. Voluntary donations are paused for now while keeping your financial records local and private.",
                    style = type.supportingInfo,
                    color = colors.secondaryText,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.glassSurface, RoundedCornerShape(24.dp))
                        .padding(horizontal = 20.dp, vertical = 24.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Donations are temporarily unavailable",
                            style = type.cardTitle,
                            color = colors.primaryText,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Thank you for wanting to support duit.! Direct donations are paused for now. You can still help by sharing feedback or reporting bugs.",
                            style = type.supportingInfo,
                            color = colors.secondaryText,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                CandyStatusBadge("100% optional. duit. stays free!", CandyTone.Mint)
                Spacer(Modifier.height(10.dp))
                PrismaticButton(
                    text = "Close",
                    onClick = onClose,
                    variant = PrismaticButtonVariant.Ghost,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
