package com.diniy.duit.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.diniy.duit.core.ui.components.GlassCardTier
import com.diniy.duit.core.ui.components.PrismaticBadge
import com.diniy.duit.core.ui.components.PrismaticGlassCard
import com.diniy.duit.core.ui.components.PrismaticModalBottomSheet
import com.diniy.duit.core.ui.theme.LocalPrismaticGlass
import com.diniy.duit.domain.model.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettingsSheet(
    currentTheme: ThemeMode,
    onSelectTheme: (ThemeMode) -> Unit,
    onDismiss: () -> Unit
) {
    val glass = LocalPrismaticGlass.current

    PrismaticModalBottomSheet(
        title = "Appearance",
        subtitle = "Switch between Light, Dark, or System Prismatic Glass themes.",
        onDismissRequest = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ThemeMode.entries.forEach { mode ->
                val isSelected = mode == currentTheme
                PrismaticGlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {},
                    tier = if (isSelected) GlassCardTier.Elevated else GlassCardTier.Standard,
                    accentTint = if (isSelected) glass.lavenderAccent else null,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    onClick = { onSelectTheme(mode) },
                    onClickLabel = "Use ${mode.label} theme"
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = null,
                            modifier = Modifier.semantics { Role.RadioButton }
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = mode.label,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = mode.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isSelected) {
                            PrismaticBadge(
                                label = "Active",
                                containerColor = glass.lavenderContainer,
                                contentColor = glass.onLavenderContainer
                            )
                        }
                    }
                }
            }
        }
    }
}
