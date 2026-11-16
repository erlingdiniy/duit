package com.diniy.duit.feature.budgets

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.diniy.duit.core.currency.MyrFormatter
import com.diniy.duit.core.ui.components.GlassCardTier
import com.diniy.duit.core.ui.components.PrismaticBadge
import com.diniy.duit.core.ui.components.PrismaticButton
import com.diniy.duit.core.ui.components.PrismaticButtonVariant
import com.diniy.duit.core.ui.components.PrismaticErrorBanner
import com.diniy.duit.core.ui.components.PrismaticGlassCard
import com.diniy.duit.core.ui.components.PrismaticModalBottomSheet
import com.diniy.duit.core.ui.components.PrismaticTextField
import com.diniy.duit.core.ui.theme.LocalPrismaticGlass
import com.diniy.duit.core.util.DateTimeFormatters
import com.diniy.duit.domain.calculator.BudgetCalculator
import com.diniy.duit.domain.calculator.BudgetProgress
import com.diniy.duit.domain.model.Budget
import com.diniy.duit.domain.model.BudgetPeriodType
import com.diniy.duit.domain.model.BudgetScope
import com.diniy.duit.domain.model.BudgetWarningLevel
import com.diniy.duit.domain.model.Category
import com.diniy.duit.domain.model.PrismaticAccentToken
import com.diniy.duit.domain.model.SalaryCycle
import com.diniy.duit.domain.model.TransactionType
import com.diniy.duit.feature.savings.SavingsIconMapper
import com.diniy.duit.feature.transactions.CategoryIconMapper
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BudgetEditorSheet(
    existingBudget: Budget?,
    initialPeriodType: BudgetPeriodType,
    initialYearMonth: YearMonth,
    initialCycleId: Long?,
    salaryCycles: List<SalaryCycle>,
    categories: List<Category>,
    onSave: (Budget, onError: (String) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    val expenseCategories = categories.filter { it.type == TransactionType.EXPENSE && !it.isArchived }

    var scope by rememberSaveable(existingBudget?.id) {
        mutableStateOf(existingBudget?.scope ?: BudgetScope.CATEGORY)
    }
    var periodType by rememberSaveable(existingBudget?.id) {
        mutableStateOf(existingBudget?.periodType ?: initialPeriodType)
    }
    var yearMonthStr by rememberSaveable(existingBudget?.id) {
        mutableStateOf(
            existingBudget?.yearMonth?.takeIf { it.isNotBlank() } ?: initialYearMonth.toString()
        )
    }
    var selectedCycleId by rememberSaveable(existingBudget?.id) {
        mutableStateOf(
            existingBudget?.salaryCycleId
                ?: initialCycleId
                ?: salaryCycles.lastOrNull { it.isActive }?.id
                ?: salaryCycles.lastOrNull()?.id
        )
    }
    var selectedCategoryId by rememberSaveable(existingBudget?.id) {
        mutableStateOf(
            existingBudget?.categoryId
                ?: expenseCategories.firstOrNull {
                    existingBudget == null || it.name.equals(existingBudget.categoryName, ignoreCase = true)
                }?.id
                ?: expenseCategories.firstOrNull()?.id
        )
    }
    var limitInput by rememberSaveable(existingBudget?.id) {
        mutableStateOf(
            if (existingBudget != null && existingBudget.limitSen > 0L) {
                MyrFormatter.formatInputValue(existingBudget.limitSen)
            } else {
                ""
            }
        )
    }
    var customName by rememberSaveable(existingBudget?.id) {
        mutableStateOf(existingBudget?.name.orEmpty())
    }
    var note by rememberSaveable(existingBudget?.id) {
        mutableStateOf(existingBudget?.note.orEmpty())
    }
    var accentToken by rememberSaveable(existingBudget?.id) {
        mutableStateOf(existingBudget?.accentToken ?: PrismaticAccentToken.LAVENDER)
    }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var isSubmitting by rememberSaveable { mutableStateOf(false) }

    val selectedCategory = expenseCategories.firstOrNull { it.id == selectedCategoryId }

    PrismaticModalBottomSheet(
        title = if (existingBudget == null) "New Budget" else "Edit Budget",
        subtitle = "Set a spending limit without affecting wallet balances or Safe to Spend.",
        onDismissRequest = onDismiss
    ) {
        if (errorMessage != null) {
            PrismaticErrorBanner(
                message = errorMessage!!,
                onDismiss = { errorMessage = null }
            )
            Spacer(Modifier.height(10.dp))
        }

        Text(
            text = "Budget scope",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BudgetScope.entries.forEach { option ->
                FilterChip(
                    selected = scope == option,
                    onClick = {
                        scope = option
                        errorMessage = null
                        if (option == BudgetScope.OVERALL) {
                            accentToken = PrismaticAccentToken.LAVENDER
                        }
                    },
                    label = { Text(option.displayName) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Period type",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BudgetPeriodType.entries.forEach { option ->
                FilterChip(
                    selected = periodType == option,
                    onClick = {
                        periodType = option
                        errorMessage = null
                    },
                    label = { Text(option.label) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        if (periodType == BudgetPeriodType.MONTHLY) {
            val currentYm = runCatching { YearMonth.parse(yearMonthStr) }.getOrElse { initialYearMonth }
            PrismaticGlassCard(
                modifier = Modifier.fillMaxWidth(),
                tier = GlassCardTier.Muted,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { yearMonthStr = currentYm.minusMonths(1).toString() }) {
                        Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous month")
                    }
                    Text(
                        text = BudgetCalculator.formatYearMonthLabel(currentYm.toString()),
                        style = MaterialTheme.typography.titleSmall
                    )
                    IconButton(onClick = { yearMonthStr = currentYm.plusMonths(1).toString() }) {
                        Icon(Icons.Rounded.ChevronRight, contentDescription = "Next month")
                    }
                }
            }
        } else {
            if (salaryCycles.isEmpty()) {
                Text(
                    text = "Create a salary cycle first in Planning to attach a cycle budget.",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalPrismaticGlass.current.cautionText
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    salaryCycles.forEach { cycle ->
                        FilterChip(
                            selected = selectedCycleId == cycle.id,
                            onClick = {
                                selectedCycleId = cycle.id
                                errorMessage = null
                            },
                            label = { Text(cycle.label) }
                        )
                    }
                }
            }
        }

        if (scope == BudgetScope.CATEGORY) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Expense category",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                expenseCategories.forEach { cat ->
                    FilterChip(
                        selected = selectedCategoryId == cat.id,
                        onClick = {
                            selectedCategoryId = cat.id
                            accentToken = cat.accentToken
                            errorMessage = null
                        },
                        label = { Text(cat.name) },
                        leadingIcon = {
                            Icon(
                                imageVector = CategoryIconMapper.iconFor(cat.iconKey, TransactionType.EXPENSE),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        PrismaticTextField(
            value = limitInput,
            onValueChange = {
                limitInput = it
                errorMessage = null
            },
            label = "Spending limit",
            placeholder = "500.00",
            prefixText = "RM ",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Next
            )
        )

        Spacer(Modifier.height(10.dp))

        PrismaticTextField(
            value = customName,
            onValueChange = { customName = it },
            label = "Label (optional)",
            placeholder = if (scope == BudgetScope.OVERALL) "Overall Monthly Cap" else (selectedCategory?.name ?: "Category limit"),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next
            )
        )

        Spacer(Modifier.height(10.dp))

        PrismaticTextField(
            value = note,
            onValueChange = { note = it },
            label = "Note (optional)",
            placeholder = "Personal reminder for this spending cap",
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done
            )
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Accent colour",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            PrismaticAccentToken.entries.forEach { token ->
                val (accentColor, _, _) = SavingsIconMapper.accentColors(token)
                FilterChip(
                    selected = accentToken == token,
                    onClick = { accentToken = token },
                    label = {
                        Text(
                            token.name.lowercase()
                                .replace('_', ' ')
                                .replaceFirstChar { it.uppercase() }
                        )
                    },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(accentColor)
                        )
                    }
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PrismaticButton(
                text = "Cancel",
                onClick = onDismiss,
                variant = PrismaticButtonVariant.Secondary,
                enabled = !isSubmitting,
                modifier = Modifier.weight(1f)
            )
            PrismaticButton(
                text = if (existingBudget == null) "Save Budget" else "Save Changes",
                isLoading = isSubmitting,
                enabled = !isSubmitting,
                onClick = {
                    val limitSen = MyrFormatter.parseToSen(limitInput)
                    if (limitSen == null || limitSen <= 0L) {
                        errorMessage = "Enter a spending limit greater than RM 0.00."
                        return@PrismaticButton
                    }
                    if (periodType == BudgetPeriodType.SALARY_CYCLE && selectedCycleId == null) {
                        errorMessage = "Select a salary cycle for this budget."
                        return@PrismaticButton
                    }
                    if (scope == BudgetScope.CATEGORY && selectedCategory == null && existingBudget?.categoryName.isNullOrBlank()) {
                        errorMessage = "Select an expense category."
                        return@PrismaticButton
                    }

                    val catName = if (scope == BudgetScope.CATEGORY) {
                        selectedCategory?.name ?: existingBudget?.categoryName.orEmpty()
                    } else {
                        ""
                    }
                    val now = System.currentTimeMillis()
                    val candidate = Budget(
                        id = existingBudget?.id ?: 0L,
                        name = customName.trim(),
                        scope = scope,
                        periodType = periodType,
                        yearMonth = if (periodType == BudgetPeriodType.MONTHLY) yearMonthStr else "",
                        salaryCycleId = if (periodType == BudgetPeriodType.SALARY_CYCLE) selectedCycleId else null,
                        categoryId = if (scope == BudgetScope.CATEGORY) (selectedCategory?.id ?: existingBudget?.categoryId) else null,
                        categoryName = catName,
                        limitSen = limitSen,
                        accentToken = accentToken,
                        note = note.trim(),
                        isArchived = existingBudget?.isArchived ?: false,
                        createdAtEpochMs = existingBudget?.createdAtEpochMs ?: now,
                        updatedAtEpochMs = now
                    )
                    isSubmitting = true
                    onSave(candidate) { err ->
                        isSubmitting = false
                        errorMessage = err
                    }
                },
                modifier = Modifier.weight(1.4f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BudgetDetailSheet(
    progress: BudgetProgress,
    categories: List<Category>,
    isPreview: Boolean,
    onEdit: () -> Unit,
    onToggleArchive: (id: Long, archived: Boolean, onError: (String) -> Unit) -> Unit,
    onDelete: (id: Long, onError: (String) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    val glass = LocalPrismaticGlass.current
    val budget = progress.budget
    val category = budget.categoryId?.let { id -> categories.firstOrNull { it.id == id } }
    val (accentColor, badgeBg, badgeFg) = SavingsIconMapper.accentColors(budget.accentToken)

    var errorText by rememberSaveable { mutableStateOf<String?>(null) }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }

    val statusColor = when (progress.warningLevel) {
        BudgetWarningLevel.SAFE -> accentColor
        BudgetWarningLevel.APPROACHING_80 -> glass.peachAccent
        BudgetWarningLevel.AT_LIMIT_100, BudgetWarningLevel.EXCEEDED -> MaterialTheme.colorScheme.error
    }

    PrismaticModalBottomSheet(
        title = budget.name,
        subtitle = "${budget.periodType.label} · ${progress.periodLabel}",
        onDismissRequest = onDismiss
    ) {
        if (errorText != null) {
            PrismaticErrorBanner(
                message = errorText!!,
                onDismiss = { errorText = null }
            )
            Spacer(Modifier.height(10.dp))
        }

        PrismaticGlassCard(
            modifier = Modifier.fillMaxWidth(),
            tier = GlassCardTier.Elevated,
            accentTint = statusColor,
            contentPadding = PaddingValues(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(badgeBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (budget.scope == BudgetScope.OVERALL) {
                                Icons.Rounded.AccountBalanceWallet
                            } else {
                                CategoryIconMapper.iconFor(category?.iconKey, TransactionType.EXPENSE)
                            },
                            contentDescription = null,
                            tint = badgeFg,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = MyrFormatter.format(progress.spentSen),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "of ${MyrFormatter.format(progress.limitSen)} limit",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                PrismaticBadge(
                    label = if (budget.isArchived) "Archived" else progress.usedPercentDisplay,
                    containerColor = when (progress.warningLevel) {
                        BudgetWarningLevel.SAFE -> badgeBg
                        BudgetWarningLevel.APPROACHING_80 -> glass.peachContainer
                        BudgetWarningLevel.AT_LIMIT_100, BudgetWarningLevel.EXCEEDED -> MaterialTheme.colorScheme.errorContainer
                    },
                    contentColor = when (progress.warningLevel) {
                        BudgetWarningLevel.SAFE -> badgeFg
                        BudgetWarningLevel.APPROACHING_80 -> glass.onPeachContainer
                        BudgetWarningLevel.AT_LIMIT_100, BudgetWarningLevel.EXCEEDED -> MaterialTheme.colorScheme.onErrorContainer
                    }
                )
            }

            Spacer(Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { progress.progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = statusColor,
                trackColor = glass.glassSurfaceMuted,
                strokeCap = StrokeCap.Round
            )

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (progress.warningLevel) {
                        BudgetWarningLevel.EXCEEDED -> "Over budget by ${MyrFormatter.format(progress.overspentSen)}"
                        BudgetWarningLevel.AT_LIMIT_100 -> "100% of limit used · RM 0.00 left"
                        BudgetWarningLevel.APPROACHING_80 -> "${MyrFormatter.format(progress.remainingSen)} left · Approaching limit"
                        BudgetWarningLevel.SAFE -> "${MyrFormatter.format(progress.remainingSen)} remaining"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = when (progress.warningLevel) {
                        BudgetWarningLevel.EXCEEDED -> MaterialTheme.colorScheme.error
                        BudgetWarningLevel.AT_LIMIT_100, BudgetWarningLevel.APPROACHING_80 -> glass.cautionText
                        BudgetWarningLevel.SAFE -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                if (progress.warningLevel != BudgetWarningLevel.SAFE) {
                    Icon(
                        imageVector = Icons.Rounded.WarningAmber,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (budget.note.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = budget.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Eligible expenses in period (${progress.matchingTransactions.size})",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(6.dp))

        if (progress.matchingTransactions.isEmpty()) {
            Text(
                text = "No eligible expense transactions recorded for this period yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                progress.matchingTransactions.take(15).forEach { tx ->
                    PrismaticGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        tier = GlassCardTier.Muted,
                        contentPadding = PaddingValues(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tx.title.ifBlank { tx.category },
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "${tx.category} · ${DateTimeFormatters.formatTimestamp(tx.occurredAtEpochMs)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "-${MyrFormatter.format(tx.amountSen)}",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        if (!isPreview) {
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = glass.glassBorderSubtle)
            Spacer(Modifier.height(12.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PrismaticButton(
                    text = "Edit Budget",
                    onClick = onEdit,
                    variant = PrismaticButtonVariant.Secondary
                )
                PrismaticButton(
                    text = if (budget.isArchived) "Restore" else "Archive",
                    onClick = {
                        onToggleArchive(budget.id, !budget.isArchived) { err ->
                            errorText = err
                        }
                    },
                    variant = PrismaticButtonVariant.Ghost
                )
                PrismaticButton(
                    text = "Delete",
                    onClick = { showDeleteConfirm = true },
                    variant = PrismaticButtonVariant.Ghost
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete \"${budget.name}\"?") },
            text = {
                Text("This permanently removes this budget limit. Your transactions and wallet balances remain untouched.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete(budget.id) { err -> errorText = err }
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}
