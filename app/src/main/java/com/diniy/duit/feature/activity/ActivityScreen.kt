package com.diniy.duit.feature.activity

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.diniy.duit.R
import com.diniy.duit.core.currency.MyrFormatter
import com.diniy.duit.core.ui.components.PrismaticEmptyStateCard
import com.diniy.duit.core.ui.components.PrismaticErrorBanner
import com.diniy.duit.core.ui.components.PrismaticLoadingCard
import com.diniy.duit.core.ui.components.SamplePreviewBanner
import com.diniy.duit.core.ui.components.SpatialGlassCard
import com.diniy.duit.core.ui.components.SpatialGlassIconButton
import com.diniy.duit.core.ui.components.SpatialGlassPill
import com.diniy.duit.core.ui.components.SpatialGlassTier
import com.diniy.duit.core.ui.state.LocalPrivacySettings
import com.diniy.duit.core.ui.state.PrivacyMask
import com.diniy.duit.core.ui.state.privacySafeAmountSemantics
import com.diniy.duit.core.ui.theme.SpatialTheme
import com.diniy.duit.core.util.DateTimeFormatters
import com.diniy.duit.domain.model.Category
import com.diniy.duit.domain.model.FinanceTransaction
import com.diniy.duit.domain.model.TransactionType
import com.diniy.duit.domain.model.Wallet
import com.diniy.duit.feature.transactions.CategoryIconMapper

@Composable
fun ActivityScreen(
    uiState: ActivityUiState,
    contentPadding: PaddingValues,
    onSelectFilter: (ActivityFilter) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onOpenNewTransaction: (TransactionType) -> Unit,
    onSelectTransaction: (FinanceTransaction) -> Unit,
    onOpenCategories: () -> Unit,
    onEnablePreview: () -> Unit,
    onExitPreview: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SpatialTheme.colors
    val typography = SpatialTheme.typography
    val spacing = SpatialTheme.spacing
    val geometry = SpatialTheme.geometry

    val totalCount = uiState.dateSections.sumOf { it.transactions.size }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(spacing.sm)
    ) {
        // 1. Spatial Header
        item(key = "activity_header", contentType = "header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Activity",
                        style = typography.screenHeading,
                        color = colors.primaryText,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        text = if (totalCount > 0) {
                            "$totalCount transaction(s) · Income, expenses & transfers"
                        } else {
                            "Income, expenses & internal wallet transfers"
                        },
                        style = typography.supportingInfo,
                        color = colors.secondaryText
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SpatialGlassIconButton(
                        icon = Icons.Rounded.Tune,
                        contentDescription = "Manage categories",
                        onClick = onOpenCategories
                    )
                    SpatialGlassIconButton(
                        icon = Icons.Rounded.Add,
                        contentDescription = "New transaction",
                        selected = true,
                        accentTint = colors.lavender,
                        onClick = {
                            val defaultType = when (uiState.selectedFilter) {
                                ActivityFilter.INCOME -> TransactionType.INCOME
                                ActivityFilter.TRANSFER -> TransactionType.TRANSFER
                                else -> TransactionType.EXPENSE
                            }
                            onOpenNewTransaction(defaultType)
                        }
                    )
                }
            }
        }

        item(key = "preview_banner", contentType = "banner") {
            AnimatedVisibility(visible = uiState.isSamplePreview) {
                SamplePreviewBanner(onExitPreview = onExitPreview)
            }
        }

        if (uiState.errorMessage != null) {
            item(key = "error", contentType = "error") {
                PrismaticErrorBanner(
                    message = uiState.errorMessage,
                    onDismiss = onDismissError
                )
            }
        }

        // 2. Frosted Spatial Search Bar
        item(key = "search_bar", contentType = "search") {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onSearchQueryChange,
                singleLine = true,
                textStyle = typography.transactionDescription.copy(color = colors.primaryText),
                placeholder = {
                    Text(
                        text = "Search title, category, wallet, or notes",
                        style = typography.supportingInfo,
                        color = colors.secondaryText
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = colors.secondaryText
                    )
                },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Clear search",
                                tint = colors.secondaryText
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(geometry.radiusMd),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = colors.glassSurfaceElevated,
                    unfocusedContainerColor = colors.glassSurface,
                    focusedBorderColor = colors.primaryAction,
                    unfocusedBorderColor = colors.glassBorder
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = geometry.inputFieldHeight)
            )
        }

        // 3. Spatial Filter Pills
        item(key = "filters", contentType = "filters") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ActivityFilter.entries.forEach { filter ->
                    val selected = uiState.selectedFilter == filter
                    val accent = when (filter) {
                        ActivityFilter.ALL -> colors.lavender
                        ActivityFilter.EXPENSE -> colors.expense
                        ActivityFilter.INCOME -> colors.income
                        ActivityFilter.TRANSFER -> colors.skyBlue
                    }
                    SpatialGlassPill(
                        label = filter.label,
                        selected = selected,
                        accentColor = accent,
                        onClick = { onSelectFilter(filter) }
                    )
                }
            }
        }

        // 4. Grouped Date Sections with Compact Transaction Rows & Subtle Dividers
        when {
            uiState.isLoading -> {
                item(key = "loading", contentType = "loading") { PrismaticLoadingCard() }
            }

            uiState.dateSections.isEmpty() -> {
                item(key = "empty", contentType = "empty") {
                    val hasActiveSearch = uiState.searchQuery.isNotBlank() ||
                        uiState.selectedFilter != ActivityFilter.ALL
                    PrismaticEmptyStateCard(
                        iconRes = R.drawable.ic_solar_activity,
                        title = if (hasActiveSearch) {
                            "No matching transactions"
                        } else {
                            "No activity yet"
                        },
                        message = if (hasActiveSearch) {
                            "Try clearing your search or switching the filter pill above."
                        } else {
                            "Record income, expenses, or wallet transfers. Transfers move funds between wallets without inflating cashflow totals."
                        },
                        actionLabel = if (!hasActiveSearch && !uiState.isSamplePreview) {
                            "Add Transaction"
                        } else {
                            null
                        },
                        onActionClick = if (!hasActiveSearch && !uiState.isSamplePreview) {
                            { onOpenNewTransaction(TransactionType.EXPENSE) }
                        } else {
                            null
                        },
                        secondaryActionLabel = if (!uiState.isSamplePreview && !hasActiveSearch) {
                            "View Sample"
                        } else {
                            null
                        },
                        onSecondaryActionClick = if (!uiState.isSamplePreview && !hasActiveSearch) {
                            onEnablePreview
                        } else {
                            null
                        }
                    )
                }
            }

            else -> {
                uiState.dateSections.forEach { section ->
                    item(key = "date_group_${section.date}", contentType = "date_group") {
                        SpatialDateTransactionGroup(
                            section = section,
                            walletsById = uiState.walletsById,
                            categoriesById = uiState.categoriesById,
                            onSelectTransaction = onSelectTransaction
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpatialDateTransactionGroup(
    section: ActivityDateSection,
    walletsById: Map<Long, Wallet>,
    categoriesById: Map<Long, Category>,
    onSelectTransaction: (FinanceTransaction) -> Unit
) {
    val colors = SpatialTheme.colors
    val typography = SpatialTheme.typography

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 6.dp, start = 4.dp, end = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = section.headerLabel,
                style = typography.caption,
                color = colors.secondaryText,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = "${section.transactions.size} item(s)",
                style = typography.caption,
                color = colors.secondaryText
            )
        }

        SpatialGlassCard(
            modifier = Modifier.fillMaxWidth(),
            tier = SpatialGlassTier.Standard,
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            section.transactions.forEachIndexed { idx, transaction ->
                if (idx > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = colors.divider
                    )
                }
                SpatialCompactTransactionRow(
                    transaction = transaction,
                    walletsById = walletsById,
                    categoriesById = categoriesById,
                    onClick = { onSelectTransaction(transaction) }
                )
            }
        }
    }
}

@Composable
private fun SpatialCompactTransactionRow(
    transaction: FinanceTransaction,
    walletsById: Map<Long, Wallet>,
    categoriesById: Map<Long, Category>,
    onClick: () -> Unit
) {
    val colors = SpatialTheme.colors
    val typography = SpatialTheme.typography
    val geometry = SpatialTheme.geometry
    val privacy = LocalPrivacySettings.current

    val sourceWalletName = walletsById[transaction.walletId]?.name ?: "Wallet"
    val targetWalletName = transaction.targetWalletId?.let { walletsById[it]?.name }

    val matchedCategory = transaction.categoryId?.let { categoriesById[it] }
    val iconKey = matchedCategory?.iconKey
        ?: CategoryIconMapper.inferIconKeyFromName(transaction.category, transaction.type)

    val (iconBg, iconFg) = when (transaction.type) {
        TransactionType.INCOME -> colors.incomeContainer to colors.onIncomeContainer
        TransactionType.EXPENSE -> colors.expenseContainer to colors.onExpenseContainer
        TransactionType.TRANSFER -> colors.savingsContainer to colors.onSavingsContainer
    }

    val amountText = if (privacy.isAnyMaskActive) {
        PrivacyMask.MASKED_MYR
    } else {
        when (transaction.type) {
            TransactionType.INCOME -> MyrFormatter.format(transaction.amountSen, includePlusSign = true)
            TransactionType.EXPENSE -> MyrFormatter.format(-transaction.amountSen)
            TransactionType.TRANSFER -> MyrFormatter.format(transaction.amountSen)
        }
    }

    val amountColor = when (transaction.type) {
        TransactionType.INCOME -> colors.income
        TransactionType.EXPENSE -> colors.primaryText
        TransactionType.TRANSFER -> colors.savings
    }

    val subtitle = if (transaction.type == TransactionType.TRANSFER && targetWalletName != null) {
        "$sourceWalletName -> $targetWalletName · ${DateTimeFormatters.formatTimeOnly(transaction.occurredAtEpochMs)}"
    } else {
        "${transaction.category} · $sourceWalletName · ${DateTimeFormatters.formatTimeOnly(transaction.occurredAtEpochMs)}"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                role = Role.Button,
                onClickLabel = "Inspect ${transaction.title}",
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .privacySafeAmountSemantics(
                label = "${transaction.title}, $subtitle",
                isMasked = privacy.isAnyMaskActive,
                visibleFormattedAmount = amountText
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(geometry.radiusSm))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = CategoryIconMapper.iconFor(iconKey, transaction.type),
                    contentDescription = null,
                    tint = iconFg,
                    modifier = Modifier.size(19.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.title,
                    style = typography.transactionDescription,
                    color = colors.primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = typography.caption,
                    color = colors.secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = amountText,
                style = typography.transactionAmount,
                color = amountColor,
                maxLines = 1
            )
            if (transaction.type == TransactionType.TRANSFER) {
                Spacer(Modifier.height(3.dp))
                SpatialGlassPill(
                    label = "Transfer",
                    selected = false,
                    accentColor = colors.skyBlue,
                    containerColor = colors.savingsContainer,
                    contentColor = colors.onSavingsContainer
                )
            }
        }
    }
}
