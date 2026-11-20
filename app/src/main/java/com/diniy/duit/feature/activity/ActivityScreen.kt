package com.diniy.duit.feature.activity

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import com.diniy.duit.core.ui.components.CandyIconBlob
import com.diniy.duit.core.ui.components.CandyMoneyText
import com.diniy.duit.core.ui.components.CandyStatusBadge
import com.diniy.duit.core.ui.components.CandyTone
import com.diniy.duit.core.ui.components.candyPressable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
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
    onExtraFiltersChange: (ActivityExtraFilters) -> Unit = {},
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
                // Phase 11F: mint bubble button; the duplicate "+" was removed (use the floating +).
                com.diniy.duit.core.ui.components.CandyMintIconButton(
                    icon = Icons.Rounded.Tune,
                    contentDescription = "Manage categories",
                    onClick = onOpenCategories,
                    pressedTilt = 0f
                )
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
                    CandyIconBlob(Icons.Rounded.Search, CandyTone.Coral, size = 34.dp, tilt = -6f, solid = true)
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
                shape = RoundedCornerShape(50),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = colors.glassSurfaceElevated,
                    unfocusedContainerColor = colors.glassSurface,
                    focusedBorderColor = colors.coral,
                    unfocusedBorderColor = Color.Transparent
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

        // Public edition: category, wallet and date filters (display-only; records are never changed).
        if (com.diniy.duit.BuildConfig.PUBLIC_EDITION) {
            item(key = "extra_filters", contentType = "filters") {
                val extra = uiState.extraFilters
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterMenuPill(
                        label = extra.category ?: "Category",
                        active = extra.category != null,
                        options = listOf<Pair<String, String?>>("All categories" to null) + uiState.categoryOptions.map { it to it },
                        onSelect = { onExtraFiltersChange(extra.copy(category = it)) }
                    )
                    FilterMenuPill(
                        label = extra.walletId?.let { uiState.walletsById[it]?.name } ?: "Wallet",
                        active = extra.walletId != null,
                        options = listOf<Pair<String, Long?>>("All wallets" to null) + uiState.wallets.map { it.name to it.id },
                        onSelect = { onExtraFiltersChange(extra.copy(walletId = it)) }
                    )
                    FilterMenuPill(
                        label = if (extra.dateRange == ActivityDateRange.ALL_TIME) "Date" else extra.dateRange.label,
                        active = extra.dateRange != ActivityDateRange.ALL_TIME,
                        options = ActivityDateRange.entries.map { it.label to it },
                        onSelect = { onExtraFiltersChange(extra.copy(dateRange = it)) }
                    )
                    if (extra.isActive) {
                        SpatialGlassPill(label = "Clear", onClick = { onExtraFiltersChange(ActivityExtraFilters()) })
                    }
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
                        uiState.selectedFilter != ActivityFilter.ALL || uiState.extraFilters.isActive
                    PrismaticEmptyStateCard(
                        iconRes = R.drawable.ic_solar_activity,
                        title = if (hasActiveSearch) {
                            "No matching transactions"
                        } else {
                            "No activity yet"
                        },
                        message = if (hasActiveSearch) {
                            "Try a different search, or clear the filters above."
                        } else {
                            if (com.diniy.duit.BuildConfig.PUBLIC_EDITION) "Every ringgit you record shows up here. Add an expense, income or transfer to get started."
                            else "Record income, expenses, or wallet transfers. Transfers move funds between wallets without inflating cashflow totals."
                        },
                        actionLabel = when {
                            !hasActiveSearch && !uiState.isSamplePreview -> "Add Transaction"
                            hasActiveSearch && com.diniy.duit.BuildConfig.PUBLIC_EDITION -> "Clear search & filters"
                            else -> null
                        },
                        onActionClick = when {
                            !hasActiveSearch && !uiState.isSamplePreview -> { { onOpenNewTransaction(TransactionType.EXPENSE) } }
                            hasActiveSearch && com.diniy.duit.BuildConfig.PUBLIC_EDITION -> {
                                {
                                    onSearchQueryChange("")
                                    onSelectFilter(ActivityFilter.ALL)
                                    onExtraFiltersChange(ActivityExtraFilters())
                                }
                            }
                            else -> null
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
                .padding(top = 6.dp, bottom = 8.dp, start = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = section.headerLabel,
                style = typography.cardTitle.copy(fontSize = 17.sp),
                color = colors.primaryText,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() }
            )
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(colors.coral)
                    .padding(horizontal = 10.dp, vertical = 2.dp)
            ) {
                Text("${section.transactions.size}", style = typography.caption, color = colors.onCoral)
            }
        }

        SpatialGlassCard(
            modifier = Modifier.fillMaxWidth(),
            tier = SpatialGlassTier.Standard,
            shape = RoundedCornerShape(28.dp),
            contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp)
        ) {
            section.transactions.forEachIndexed { idx, transaction ->
                SpatialCompactTransactionRow(
                    transaction = transaction,
                    index = idx,
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
    index: Int,
    walletsById: Map<Long, Wallet>,
    categoriesById: Map<Long, Category>,
    onClick: () -> Unit
) {
    val colors = SpatialTheme.colors
    val typography = SpatialTheme.typography
    val privacy = LocalPrivacySettings.current

    val sourceWalletName = walletsById[transaction.walletId]?.name ?: "Wallet"
    val targetWalletName = transaction.targetWalletId?.let { walletsById[it]?.name }

    val matchedCategory = transaction.categoryId?.let { categoriesById[it] }
    val iconKey = matchedCategory?.iconKey
        ?: CategoryIconMapper.inferIconKeyFromName(transaction.category, transaction.type)

    val tone = when (transaction.type) {
        TransactionType.INCOME -> CandyTone.Mint
        TransactionType.EXPENSE -> CandyTone.Coral
        TransactionType.TRANSFER -> CandyTone.Sunny
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
        "$sourceWalletName → $targetWalletName"
    } else {
        "${transaction.category} · $sourceWalletName"
    }
    val timeLabel = DateTimeFormatters.formatTimeOnly(transaction.occurredAtEpochMs)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .candyPressable(onClick = onClick, onClickLabel = "Inspect ${transaction.title}")
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .privacySafeAmountSemantics(
                label = "${transaction.title}, $subtitle, $timeLabel",
                isMasked = privacy.isAnyMaskActive,
                visibleFormattedAmount = amountText
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CandyIconBlob(
            icon = CategoryIconMapper.iconFor(iconKey, transaction.type),
            tone = tone,
            size = 44.dp,
            tilt = if (index % 2 == 0) -6f else 5f
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = transaction.title,
                style = typography.transactionDescription,
                color = colors.primaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = typography.caption,
                color = colors.secondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            CandyMoneyText(amountText, typography.transactionAmount.copy(fontSize = 16.sp), amountColor, minorScale = 0.72f)
            if (transaction.type == TransactionType.TRANSFER) {
                Spacer(Modifier.height(3.dp))
                CandyStatusBadge("Transfer", CandyTone.Sunny)
            } else {
                Text(timeLabel, style = typography.caption, color = colors.secondaryText, maxLines = 1)
            }
        }
    }
}

/** A pill that opens a small menu of choices; shows the current choice as its label. */
@Composable
private fun <T> FilterMenuPill(label: String, active: Boolean, options: List<Pair<String, T>>, onSelect: (T) -> Unit) {
    var open by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val haptic = com.diniy.duit.core.ui.components.rememberDuitHaptic()
    androidx.compose.foundation.layout.Box {
        SpatialGlassPill(
            label = "$label ▾",
            selected = active,
            onClick = { open = true },
            modifier = Modifier.semantics { contentDescription = "Filter by $label" }
        )
        androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (text, value) ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        open = false
                        haptic(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        onSelect(value)
                    }
                )
            }
        }
    }
}
