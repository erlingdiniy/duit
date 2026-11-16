package com.diniy.duit.core.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.diniy.duit.core.ui.theme.LocalPrismaticGlass

enum class PrismaticButtonVariant {
    Primary,
    Secondary,
    Ghost
}

/**
 * Accessible rounded button enforcing a minimum 48dp touch height and
 * smooth loading state transitions.
 */
@Composable
fun PrismaticButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: PrismaticButtonVariant = PrismaticButtonVariant.Primary,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    val glass = LocalPrismaticGlass.current
    val shape = RoundedCornerShape(16.dp)
    val contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
    val isInteractive = enabled && !isLoading

    when (variant) {
        PrismaticButtonVariant.Primary -> {
            Button(
                onClick = onClick,
                enabled = isInteractive,
                shape = shape,
                contentPadding = contentPadding,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = modifier
                    .heightIn(min = 48.dp)
                    .semantics(mergeDescendants = true) {}
            ) {
                ButtonInnerContent(
                    text = text,
                    isLoading = isLoading,
                    leadingIcon = leadingIcon
                )
            }
        }

        PrismaticButtonVariant.Secondary -> {
            OutlinedButton(
                onClick = onClick,
                enabled = isInteractive,
                shape = shape,
                contentPadding = contentPadding,
                border = BorderStroke(
                    1.dp,
                    if (isInteractive) glass.glassBorderSubtle.copy(alpha = 0.25f)
                    else MaterialTheme.colorScheme.outlineVariant
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = glass.glassSurfaceElevated,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    disabledContainerColor = glass.glassSurfaceMuted,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = modifier
                    .heightIn(min = 48.dp)
                    .semantics(mergeDescendants = true) {}
            ) {
                ButtonInnerContent(
                    text = text,
                    isLoading = isLoading,
                    leadingIcon = leadingIcon
                )
            }
        }

        PrismaticButtonVariant.Ghost -> {
            TextButton(
                onClick = onClick,
                enabled = isInteractive,
                shape = shape,
                contentPadding = contentPadding,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = modifier
                    .heightIn(min = 48.dp)
                    .semantics(mergeDescendants = true) {}
            ) {
                ButtonInnerContent(
                    text = text,
                    isLoading = isLoading,
                    leadingIcon = leadingIcon
                )
            }
        }
    }
}

@Composable
private fun ButtonInnerContent(
    text: String,
    isLoading: Boolean,
    leadingIcon: (@Composable () -> Unit)?
) {
    AnimatedContent(
        targetState = isLoading,
        label = "button_loading_content"
    ) { loading ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(10.dp))
            } else if (leadingIcon != null) {
                leadingIcon()
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
