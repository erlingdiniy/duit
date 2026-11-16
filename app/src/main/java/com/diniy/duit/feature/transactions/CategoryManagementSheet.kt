package com.diniy.duit.feature.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.diniy.duit.core.ui.components.GlassCardTier
import com.diniy.duit.core.ui.components.PrismaticBadge
import com.diniy.duit.core.ui.components.PrismaticButton
import com.diniy.duit.core.ui.components.PrismaticButtonVariant
import com.diniy.duit.core.ui.components.PrismaticGlassCard
import com.diniy.duit.core.ui.components.PrismaticModalBottomSheet
import com.diniy.duit.core.ui.components.PrismaticTextField
import com.diniy.duit.core.ui.theme.LocalPrismaticGlass
import com.diniy.duit.domain.model.Category
import com.diniy.duit.domain.model.PrismaticAccentToken
import com.diniy.duit.domain.model.TransactionType

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CategoryManagementSheet(
    categories: List<Category>,
    initialType: TransactionType = TransactionType.EXPENSE,
    onCreateCategory: (
        name: String,
        type: TransactionType,
        iconKey: String,
        accent: PrismaticAccentToken,
        onError: (String) -> Unit,
        onDone: () -> Unit
    ) -> Unit,
    onRenameCategory: (
        id: Long,
        newName: String,
        iconKey: String,
        accent: PrismaticAccentToken,
        onError: (String) -> Unit,
        onDone: () -> Unit
    ) -> Unit,
    onToggleArchive: (id: Long, archived: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val glass = LocalPrismaticGlass.current
    var selectedType by rememberSaveable {
        mutableStateOf(
            if (initialType == TransactionType.INCOME) TransactionType.INCOME else TransactionType.EXPENSE
        )
    }
    var editingCategoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var nameInput by rememberSaveable { mutableStateOf("") }
    var selectedIconKey by rememberSaveable { mutableStateOf("custom") }
    var selectedAccent by rememberSaveable { mutableStateOf(PrismaticAccentToken.LAVENDER) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }

    val filteredCategories = categories.filter { it.type == selectedType }

    PrismaticModalBottomSheet(
        title = "Categories",
        subtitle = "Create, rename, or archive categories without altering past transactions.",
        onDismissRequest = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(TransactionType.EXPENSE, TransactionType.INCOME).forEach { type ->
                    FilterChip(
                        selected = selectedType == type,
                        onClick = {
                            selectedType = type
                            editingCategoryId = null
                            nameInput = ""
                            errorMessage = null
                        },
                        label = { Text(type.displayName) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    )
                }
            }

            PrismaticGlassCard(
                modifier = Modifier.fillMaxWidth(),
                tier = GlassCardTier.Muted,
                contentPadding = PaddingValues(14.dp)
            ) {
                Text(
                    text = if (editingCategoryId == null) "New ${selectedType.displayName} Category" else "Rename Category",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(10.dp))
                PrismaticTextField(
                    value = nameInput,
                    onValueChange = {
                        nameInput = it
                        errorMessage = null
                    },
                    label = "Category name",
                    placeholder = if (selectedType == TransactionType.EXPENSE) "e.g. Coffee & Snacks" else "e.g. Side Project",
                    errorText = errorMessage
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Icon",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CategoryIconMapper.selectableIcons.take(8).forEach { (key, label) ->
                        FilterChip(
                            selected = selectedIconKey == key,
                            onClick = { selectedIconKey = key },
                            leadingIcon = {
                                Icon(
                                    imageVector = CategoryIconMapper.iconFor(key, selectedType),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = { Text(label) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PrismaticAccentToken.entries.forEach { token ->
                        FilterChip(
                            selected = selectedAccent == token,
                            onClick = { selectedAccent = token },
                            label = { Text(token.name.lowercase().replace("_", " ").replaceFirstChar { it.uppercase() }) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (editingCategoryId != null) {
                        PrismaticButton(
                            text = "Cancel Edit",
                            onClick = {
                                editingCategoryId = null
                                nameInput = ""
                                errorMessage = null
                            },
                            variant = PrismaticButtonVariant.Secondary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    PrismaticButton(
                        text = if (editingCategoryId == null) "Add Category" else "Save Name",
                        onClick = {
                            val currentEditId = editingCategoryId
                            if (currentEditId == null) {
                                onCreateCategory(
                                    nameInput,
                                    selectedType,
                                    selectedIconKey,
                                    selectedAccent,
                                    { errorMessage = it },
                                    {
                                        nameInput = ""
                                        errorMessage = null
                                    }
                                )
                            } else {
                                onRenameCategory(
                                    currentEditId,
                                    nameInput,
                                    selectedIconKey,
                                    selectedAccent,
                                    { errorMessage = it },
                                    {
                                        editingCategoryId = null
                                        nameInput = ""
                                        errorMessage = null
                                    }
                                )
                            }
                        },
                        variant = PrismaticButtonVariant.Primary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            HorizontalDivider(color = glass.glassBorderSubtle)

            Text(
                text = "${selectedType.displayName} Categories (${filteredCategories.size})",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                filteredCategories.forEach { category ->
                    val (bg, fg) = CategoryIconMapper.accentContainerColors(category.accentToken)
                    PrismaticGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        tier = if (category.isArchived) GlassCardTier.Muted else GlassCardTier.Standard,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(bg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = CategoryIconMapper.iconFor(category.iconKey, category.type),
                                        contentDescription = null,
                                        tint = fg,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = category.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (category.isArchived) {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                    if (category.isArchived) {
                                        PrismaticBadge(
                                            label = "Archived",
                                            containerColor = glass.peachContainer,
                                            contentColor = glass.onPeachContainer
                                        )
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                PrismaticButton(
                                    text = "Rename",
                                    onClick = {
                                        editingCategoryId = category.id
                                        nameInput = category.name
                                        selectedIconKey = category.iconKey
                                        selectedAccent = category.accentToken
                                        errorMessage = null
                                    },
                                    variant = PrismaticButtonVariant.Ghost
                                )
                                PrismaticButton(
                                    text = if (category.isArchived) "Restore" else "Archive",
                                    onClick = { onToggleArchive(category.id, !category.isArchived) },
                                    variant = PrismaticButtonVariant.Ghost
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
