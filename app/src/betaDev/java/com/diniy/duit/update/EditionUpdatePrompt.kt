package com.diniy.duit.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.diniy.duit.BuildConfig
import com.diniy.duit.core.ui.components.CandyBubbleCard
import com.diniy.duit.core.ui.components.CandyIconBlob
import com.diniy.duit.core.ui.components.CandyTone
import com.diniy.duit.core.ui.theme.SpatialTheme

/**
 * Compile-time updater lock for the private developer-enabled build (`betaDev`):
 * public GitHub release auto-updates cannot run or overwrite Developer Center with a public APK.
 */
@Composable
internal fun EditionUpdatePrompt() = Unit

/** Settings > About card explaining why public in-app updates are disabled on this private build. */
@Composable
internal fun EditionUpdateSettingsCard() {
    val colors = SpatialTheme.colors
    val type = SpatialTheme.typography
    CandyBubbleCard(
        tone = CandyTone.Mint,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CandyIconBlob(Icons.Rounded.Security, CandyTone.Mint, size = 44.dp, tilt = -6f)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("App updates", style = type.cardTitle, color = colors.primaryText)
                Text(
                    "Private developer build · Protected",
                    style = type.caption,
                    color = colors.secondaryText
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Current version: v${BuildConfig.VERSION_NAME}", style = type.supportingInfo, color = colors.primaryText)
        Spacer(Modifier.height(4.dp))
        Text(
            "Public in-app updates are disabled so public APKs cannot replace Developer Center. Install private updates manually.",
            style = type.caption,
            color = colors.secondaryText
        )
    }
}
