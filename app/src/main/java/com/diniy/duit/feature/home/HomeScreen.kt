package com.diniy.duit.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.diniy.duit.R
import com.diniy.duit.core.currency.MyrFormatter
import com.diniy.duit.core.ui.components.PrismaticBackground
import com.diniy.duit.core.ui.components.PrismaticEmptyStateCard
import com.diniy.duit.core.ui.components.PrismaticErrorBanner
import com.diniy.duit.core.ui.components.PrismaticLoadingCard
import com.diniy.duit.core.ui.components.SamplePreviewBanner
import com.diniy.duit.core.ui.components.SpatialAnimatedCounterText
import com.diniy.duit.core.ui.components.SpatialBentoMetricCard
import com.diniy.duit.core.ui.components.SpatialBentoMetricItem
import com.diniy.duit.core.ui.components.SpatialBentoSummaryRow
import com.diniy.duit.core.ui.components.SpatialButtonVariant
import com.diniy.duit.core.ui.components.SpatialCompactListRow
import com.diniy.duit.core.ui.components.SpatialGlassBottomSheetSurface
import com.diniy.duit.core.ui.components.SpatialGlassButton
import com.diniy.duit.core.ui.components.SpatialGlassCard
import com.diniy.duit.core.ui.components.SpatialGlassIconButton
import com.diniy.duit.core.ui.components.SpatialGlassPill
import com.diniy.duit.core.ui.components.SpatialGlassSection
import com.diniy.duit.core.ui.components.SpatialGlassTier
import com.diniy.duit.core.ui.components.prismaticTween
import com.diniy.duit.core.ui.components.rememberDuitHaptic
import com.diniy.duit.core.ui.components.rememberReducedMotionEnabled
import com.diniy.duit.core.ui.state.LocalPrivacySettings
import com.diniy.duit.core.ui.state.PrivacyMask
import com.diniy.duit.core.ui.state.maskedAmount
import com.diniy.duit.core.ui.state.maskedBalance
import com.diniy.duit.core.ui.state.privacySafeAmountSemantics
import com.diniy.duit.core.ui.theme.DuitTheme
import com.diniy.duit.core.ui.theme.SpatialTheme
import com.diniy.duit.core.util.DateTimeFormatters
import com.diniy.duit.domain.calculator.ObligationCalculator
import com.diniy.duit.domain.model.Commitment
import com.diniy.duit.domain.model.CommitmentStatus
import com.diniy.duit.domain.model.DashboardSectionKey
import com.diniy.duit.domain.model.FinanceTransaction
import com.diniy.duit.domain.model.PrismaticAccentToken
import com.diniy.duit.domain.model.SalaryCycle
import com.diniy.duit.domain.model.ThemeMode
import com.diniy.duit.domain.model.TransactionType
import com.diniy.duit.domain.model.Wallet
import com.diniy.duit.domain.model.WalletType
import com.diniy.duit.domain.preview.SamplePreviewData
import com.diniy.duit.feature.transactions.CategoryIconMapper

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    contentPadding: PaddingValues,
    onSelectTheme: (ThemeMode) -> Unit,
    onToggleSamplePreview: (Boolean) -> Unit,
    onAddWallet: (
        name: String,
        type: WalletType,
        openingBalanceInput: String,
        excludeFromSafeToSpend: Boolean,
        includeInTotalBalance: Boolean,
        accent: PrismaticAccentToken,
        onSuccess: () -> Unit,
        onValidationError: (String) -> Unit
    ) -> Unit,
    onSelectWallet: (Wallet) -> Unit,
    onOpenTransactionEditor: (TransactionType) -> Unit,
    onDismissError: () -> Unit,
    onOpenCommissionCalculator: () -> Unit,
    onOpenSavingsGoals: () -> Unit = {},
    onOpenCommitments: () -> Unit = {},
    onOpenActivity: () -> Unit = {},
    onSelectTransaction: (FinanceTransaction) -> Unit = {},
    onOpenSecuritySettings: () -> Unit = {},
    onOpenSettingsHub: () -> Unit = onOpenSecuritySettings,
    onToggleBalanceVisibility: () -> Unit = {},
    unreadNotificationCount: Int = 0,
    onOpenNotifications: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showThemeSheet by rememberSaveable { mutableStateOf(false) }
    var showAddWalletSheet by rememberSaveable { mutableStateOf(false) }
    var showAllWalletsSheet by rememberSaveable { mutableStateOf(false) }
    val performHaptic = rememberDuitHaptic()
    val spacing = SpatialTheme.spacing
    val walletsById = remember(uiState.wallets, uiState.archivedWallets) {
        (uiState.wallets + uiState.archivedWallets).associateBy { it.id }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(spacing.sectionSpacing)
        ) {
            // 1. Compact Uncluttered Spatial Header
            item(key = "header", contentType = "header") {
                SpatialHomeHeader(
                    isSamplePreview = uiState.isSamplePreview,
                    activeCycleLabel = uiState.activeSalaryCycle?.label,
                    unreadNotificationCount = unreadNotificationCount,
                    onToggleBalanceVisibility = {
                        performHaptic(HapticFeedbackType.TextHandleMove)
                        onToggleBalanceVisibility()
                    },
                    onOpenNotifications = onOpenNotifications,
                    onOpenSettingsHub = onOpenSettingsHub
                )
            }

            if (uiState.errorMessage != null) {
                item(key = "error", contentType = "error") {
                    PrismaticErrorBanner(
                        message = uiState.errorMessage,
                        onDismiss = onDismissError
                    )
                }
            }

            item(key = "preview_banner", contentType = "banner") {
                AnimatedVisibility(visible = uiState.isSamplePreview) {
                    SamplePreviewBanner(onExitPreview = { onToggleSamplePreview(false) })
                }
            }

            if (uiState.isLoading) {
                item(key = "loading_hero", contentType = "loading") { PrismaticLoadingCard() }
                item(key = "loading_secondary", contentType = "loading") { PrismaticLoadingCard() }
            } else {
                // 2. Primary Hero — Safe to Spend (Always Pinned)
                item(key = "hero_safe_to_spend", contentType = "hero") {
                    SpatialSafeToSpendHeroCard(
                        safeToSpendSen = uiState.summary.safeToSpendSen,
                        upcomingUnreservedSen = uiState.summary.upcomingCommitmentsUnreservedSen,
                        activeReservePeriodLabel = uiState.summary.activeReservePeriodLabel,
                        onToggleBalanceVisibility = {
                            performHaptic(HapticFeedbackType.TextHandleMove)
                            onToggleBalanceVisibility()
                        }
                    )
                }

                val hiddenSections = uiState.appSettings.hiddenDashboardSections
                val orderedSections = uiState.appSettings.dashboardSectionOrder

                orderedSections.filter { it !in hiddenSections }.forEach { section ->
                    when (section) {
                        // 3. Secondary Financial Summary (Bento: Total Balance, Protected Savings, Current Cycle Reserved)
                        DashboardSectionKey.SECONDARY_METRICS -> {
                            item(key = "secondary_metrics", contentType = "metrics") {
                                SpatialSecondaryFinancialBento(
                                    totalBalanceSen = uiState.summary.totalBalanceSen,
                                    walletCount = uiState.summary.walletCount,
                                    savingsBalanceSen = uiState.summary.savingsBalanceSen,
                                    goalAllocatedSen = uiState.summary.goalAllocatedSen,
                                    activeGoalsCount = uiState.summary.activeGoalsCount,
                                    currentCycleReservedSen = uiState.summary.upcomingCommitmentsUnreservedSen,
                                    upcomingFutureBillsSen = uiState.summary.upcomingFutureCommitmentsSen,
                                    upcomingFutureCount = uiState.summary.upcomingFutureCommitmentsCount,
                                    upcomingCount = uiState.summary.upcomingCommitmentsCount,
                                    onOpenSavingsGoals = onOpenSavingsGoals,
                                    onOpenCommitments = onOpenCommitments,
                                    onOpenAllWallets = { showAllWalletsSheet = true }
                                )
                            }
                        }

                        // 4. Compact Pill Strip (Replaces oversized nested QuickActionsStrip card)
                        DashboardSectionKey.QUICK_ACTIONS -> {
                            item(key = "quick_actions", contentType = "actions") {
                                SpatialQuickActionPillStrip(
                                    onRecordExpense = { onOpenTransactionEditor(TransactionType.EXPENSE) },
                                    onRecordIncome = { onOpenTransactionEditor(TransactionType.INCOME) },
                                    onTransferFunds = { onOpenTransactionEditor(TransactionType.TRANSFER) },
                                    onOpenCalculator = onOpenCommissionCalculator
                                )
                            }
                        }

                        // 5. Pending Commissions Notice
                        DashboardSectionKey.PENDING_COMMISSIONS -> {
                            if (uiState.appSettings.commissionTrackingEnabled && uiState.summary.pendingCommissionsSen > 0L) {
                                item(key = "pending_commissions", contentType = "notice") {
                                    SpatialPendingCommissionsCard(
                                        pendingCommissionsSen = uiState.summary.pendingCommissionsSen,
                                        onClick = onOpenCommissionCalculator
                                    )
                                }
                            }
                        }

                        // 6. Upcoming Payments (Current-cycle vs Future-cycle clearly separated)
                        DashboardSectionKey.UPCOMING_COMMITMENTS -> {
                            if (uiState.upcomingCommitments.isNotEmpty()) {
                                item(key = "upcoming_payments_section", contentType = "upcoming_payments") {
                                    SpatialUpcomingPaymentsPreviewSection(
                                        commitments = uiState.upcomingCommitments,
                                        activeCycle = uiState.activeSalaryCycle,
                                        isSamplePreview = uiState.isSamplePreview,
                                        onViewAllCommitments = onOpenCommitments
                                    )
                                }
                            }
                        }

                        // 7. Compact Wallet Preview + Recent Transactions
                        DashboardSectionKey.WALLETS -> {
                            if (uiState.wallets.isEmpty() && uiState.archivedWallets.isEmpty() && !uiState.isSamplePreview) {
                                item(key = "empty_ledger", contentType = "empty") {
                                    PrismaticEmptyStateCard(
                                        iconRes = R.drawable.ic_solar_wallet,
                                        title = "Ready for your first wallet",
                                        message = "Add a bank account, e-wallet, or cash wallet to track your Ringgit balance privately on-device.",
                                        actionLabel = "Add Wallet",
                                        onActionClick = { showAddWalletSheet = true },
                                        secondaryActionLabel = "View Sample",
                                        onSecondaryActionClick = { onToggleSamplePreview(true) }
                                    )
                                }
                            } else {
                                item(key = "wallets_preview_section", contentType = "wallets_preview") {
                                    SpatialWalletsPreviewSection(
                                        activeWallets = uiState.wallets,
                                        archivedCount = uiState.archivedWallets.size,
                                        isSamplePreview = uiState.isSamplePreview,
                                        onSelectWallet = onSelectWallet,
                                        onAddWallet = { showAddWalletSheet = true },
                                        onViewAllWallets = { showAllWalletsSheet = true }
                                    )
                                }
                            }

                            if (uiState.transactions.isNotEmpty()) {
                                item(key = "recent_transactions_section", contentType = "recent_transactions") {
                                    SpatialRecentTransactionsPreviewSection(
                                        transactions = uiState.transactions.take(4),
                                        walletsById = walletsById,
                                        isSamplePreview = uiState.isSamplePreview,
                                        onSelectTransaction = onSelectTransaction,
                                        onViewAllActivity = onOpenActivity
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showThemeSheet) {
        ThemeSettingsSheet(
            currentTheme = uiState.themeMode,
            onSelectTheme = onSelectTheme,
            onDismiss = { showThemeSheet = false }
        )
    }

    if (showAddWalletSheet) {
        AddWalletSheet(
            onSaveWallet = { name, type, openingBalance, excludedFromSafe, includeInTotal, accent, onValidationError ->
                onAddWallet(
                    name,
                    type,
                    openingBalance,
                    excludedFromSafe,
                    includeInTotal,
                    accent,
                    { showAddWalletSheet = false },
                    onValidationError
                )
            },
            onDismiss = { showAddWalletSheet = false }
        )
    }

    if (showAllWalletsSheet) {
        AllWalletsBottomSheet(
            activeWallets = uiState.wallets,
            archivedWallets = uiState.archivedWallets,
            isSamplePreview = uiState.isSamplePreview,
            onSelectWallet = { wallet ->
                showAllWalletsSheet = false
                onSelectWallet(wallet)
            },
            onAddWallet = {
                showAllWalletsSheet = false
                showAddWalletSheet = true
            },
            onDismiss = { showAllWalletsSheet = false }
        )
    }
}

@Composable
private fun SpatialHomeHeader(
    isSamplePreview: Boolean,
    activeCycleLabel: String?,
    unreadNotificationCount: Int,
    onToggleBalanceVisibility: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenSettingsHub: () -> Unit
) {
    val colors = SpatialTheme.colors
    val typography = SpatialTheme.typography
    val privacySettings = LocalPrivacySettings.current
    val isMasked = privacySettings.isAnyMaskActive

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "duit.",
                    style = typography.screenHeading,
                    color = colors.primaryText,
                    modifier = Modifier.semantics { heading() }
                )
                if (isSamplePreview) {
                    SpatialGlassPill(
                        label = "Sample",
                        selected = true,
                        accentColor = colors.peach
                    )
                }
            }
            Text(
                text = activeCycleLabel?.let { "$it · Personal MYR" } ?: "Personal MYR Overview",
                style = typography.supportingInfo,
                color = colors.secondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SpatialGlassIconButton(
                icon = if (isMasked) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                contentDescription = if (isMasked) "Show sensitive amounts" else "Hide sensitive amounts",
                selected = isMasked,
                accentTint = colors.lavender,
                onClick = onToggleBalanceVisibility
            )

            Box {
                SpatialGlassIconButton(
                    icon = Icons.Rounded.Notifications,
                    contentDescription = "Notification centre",
                    onClick = onOpenNotifications
                )
                if (unreadNotificationCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 10.dp, end = 10.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(colors.peach)
                    )
                }
            }

            SpatialGlassIconButton(
                icon = Icons.Rounded.Settings,
                contentDescription = "Settings hub",
                onClick = onOpenSettingsHub
            )
        }
    }
}

@Composable
private fun SpatialSafeToSpendHeroCard(
    safeToSpendSen: Long,
    upcomingUnreservedSen: Long,
    activeReservePeriodLabel: String,
    onToggleBalanceVisibility: () -> Unit
) {
    val colors = SpatialTheme.colors
    val typography = SpatialTheme.typography
    val spacing = SpatialTheme.spacing
    val privacySettings = LocalPrivacySettings.current
    val isMasked = privacySettings.isAnyMaskActive
    val reducedMotion = rememberReducedMotionEnabled()
    val isTight = safeToSpendSen < 0L
    // Candy Pop hero: solid grape sticker (bubblegum when overcommitted) with matching ink.
    val heroFill = if (isTight) colors.bubblegum else colors.primaryAction
    val heroInk = if (isTight) com.diniy.duit.core.ui.theme.PrismaticPalette.GrapeInk else colors.onPrimaryAction

    SpatialGlassCard(
        modifier = Modifier.fillMaxWidth(),
        tier = SpatialGlassTier.Elevated,
        containerColor = heroFill,
        sticker = true,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(SpatialTheme.geometry.radiusXl),
        contentPadding = spacing.cardPaddingHero
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Safe to Spend",
                    style = typography.cardTitle,
                    color = heroInk
                )
                Text(
                    text = activeReservePeriodLabel,
                    style = typography.caption,
                    color = heroInk.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SpatialGlassPill(
                    label = if (isTight) "Overcommitted" else "Available",
                    selected = true,
                    accentColor = if (isTight) colors.peach else colors.aqua,
                    containerColor = if (isTight) colors.warningContainer else colors.incomeContainer,
                    contentColor = if (isTight) colors.onWarningContainer else colors.onIncomeContainer
                )
                Crossfade(
                    targetState = isMasked,
                    animationSpec = prismaticTween<Float>(reducedMotion, durationMillis = 180),
                    label = "hero_privacy_eye"
                ) { masked ->
                    SpatialGlassIconButton(
                        icon = if (masked) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (masked) "Show sensitive amounts" else "Hide sensitive amounts",
                        selected = masked,
                        accentTint = colors.lavender,
                        onClick = onToggleBalanceVisibility
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        SpatialAnimatedCounterText(
            amountSen = safeToSpendSen,
            accessibilityLabel = "Safe to Spend",
            style = typography.heroBalance,
            color = heroInk
        )

        Spacer(Modifier.height(10.dp))

        HorizontalDivider(color = heroInk.copy(alpha = 0.2f))

        Spacer(Modifier.height(10.dp))

        Text(
            text = if (upcomingUnreservedSen > 0L) {
                "Excludes Protected Savings and ${maskedBalance(upcomingUnreservedSen)} reserved for current-cycle obligations."
            } else {
                "Excludes Protected Savings. All current-cycle obligations are settled."
            },
            style = typography.supportingInfo,
            color = heroInk.copy(alpha = 0.88f)
        )
    }
}

@Composable
private fun SpatialSecondaryFinancialBento(
    totalBalanceSen: Long,
    walletCount: Int,
    savingsBalanceSen: Long,
    goalAllocatedSen: Long,
    activeGoalsCount: Int,
    currentCycleReservedSen: Long,
    upcomingFutureBillsSen: Long,
    upcomingFutureCount: Int,
    upcomingCount: Int,
    onOpenSavingsGoals: () -> Unit,
    onOpenCommitments: () -> Unit,
    onOpenAllWallets: () -> Unit
) {
    val colors = SpatialTheme.colors
    val spacing = SpatialTheme.spacing

    val totalBalanceCaption = if (walletCount == 1) "1 included wallet" else "$walletCount included wallets"
    val savingsCaption = when {
        activeGoalsCount > 0 -> "$activeGoalsCount goal(s) · ${maskedBalance(goalAllocatedSen)} allocated"
        else -> "Protected from Safe to Spend"
    }
    val reservedCaption = when {
        upcomingFutureBillsSen > 0L ->
            "Future cycle: ${maskedBalance(upcomingFutureBillsSen)} ($upcomingFutureCount unreserved)"
        upcomingCount > 0 -> "$upcomingCount due in active cycle"
        else -> "No unpaid bills in active cycle"
    }

    Column(verticalArrangement = Arrangement.spacedBy(spacing.bentoGap)) {
        SpatialBentoSummaryRow(
            leftMetric = SpatialBentoMetricItem(
                label = "Total Balance",
                amountSen = totalBalanceSen,
                caption = totalBalanceCaption,
                accentColor = colors.skyBlue,
                onClick = onOpenAllWallets
            ),
            rightMetric = SpatialBentoMetricItem(
                label = "Protected Savings",
                amountSen = savingsBalanceSen,
                caption = savingsCaption,
                accentColor = colors.aqua,
                onClick = onOpenSavingsGoals
            )
        )

        SpatialBentoMetricCard(
            item = SpatialBentoMetricItem(
                label = "Current Cycle Reserved",
                amountSen = currentCycleReservedSen,
                caption = reservedCaption,
                accentColor = colors.peach,
                onClick = onOpenCommitments
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SpatialQuickActionPillStrip(
    onRecordExpense: () -> Unit,
    onRecordIncome: () -> Unit,
    onTransferFunds: () -> Unit,
    onOpenCalculator: () -> Unit
) {
    val colors = SpatialTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SpatialGlassPill(
            label = "Expense",
            accentColor = colors.expense,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.ArrowUpward,
                    contentDescription = null,
                    tint = colors.expense,
                    modifier = Modifier.size(15.dp)
                )
            },
            onClick = onRecordExpense
        )
        SpatialGlassPill(
            label = "Income",
            accentColor = colors.income,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.ArrowDownward,
                    contentDescription = null,
                    tint = colors.income,
                    modifier = Modifier.size(15.dp)
                )
            },
            onClick = onRecordIncome
        )
        SpatialGlassPill(
            label = "Transfer",
            accentColor = colors.skyBlue,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.SwapHoriz,
                    contentDescription = null,
                    tint = colors.skyBlue,
                    modifier = Modifier.size(15.dp)
                )
            },
            onClick = onTransferFunds
        )
        SpatialGlassPill(
            label = "Commissions",
            accentColor = colors.lavender,
            onClick = onOpenCalculator
        )
    }
}

@Composable
private fun SpatialPendingCommissionsCard(
    pendingCommissionsSen: Long,
    onClick: () -> Unit
) {
    val colors = SpatialTheme.colors

    SpatialCompactListRow(
        title = "Pending Commissions",
        subtitle = "Awaiting payout · Excluded from Safe to Spend",
        trailingPrimaryText = maskedBalance(pendingCommissionsSen),
        trailingPrimaryColor = colors.lavender,
        leadingBadgeColor = colors.lavender,
        onClick = onClick
    )
}

@Composable
private fun SpatialUpcomingPaymentsPreviewSection(
    commitments: List<Commitment>,
    activeCycle: SalaryCycle?,
    isSamplePreview: Boolean,
    onViewAllCommitments: () -> Unit
) {
    val colors = SpatialTheme.colors
    val typography = SpatialTheme.typography
    val privacy = LocalPrivacySettings.current
    val previewItems = commitments.take(4)

    SpatialGlassSection(
        title = if (isSamplePreview) "Upcoming Payments · Sample" else "Upcoming Payments",
        subtitle = "Current-cycle bills are reserved; future-cycle bills are not deducted yet",
        actionLabel = "Commitments",
        onActionClick = onViewAllCommitments
    ) {
        SpatialGlassCard(
            modifier = Modifier.fillMaxWidth(),
            tier = SpatialGlassTier.Standard,
            contentPadding = PaddingValues(vertical = 6.dp)
        ) {
            previewItems.forEachIndexed { idx, commitment ->
                if (idx > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = colors.divider
                    )
                }
                val isInCurrentCycle = ObligationCalculator.isDueWithinHorizon(
                    dueDateEpochDay = commitment.dueDateEpochDay,
                    activeCycle = activeCycle
                )
                val remainingSen = (commitment.amountSen - commitment.paidSen.coerceAtLeast(0L)).coerceAtLeast(0L)
                val cycleBadgeLabel = when {
                    commitment.status == CommitmentStatus.PAID -> "Paid"
                    isInCurrentCycle -> "Current cycle"
                    else -> "Future cycle"
                }
                val dueSub = if (commitment.status == CommitmentStatus.PARTIALLY_PAID) {
                    "Due ${DateTimeFormatters.formatEpochDay(commitment.dueDateEpochDay)} · Paid ${maskedAmount(commitment.paidSen)}"
                } else {
                    "Due ${DateTimeFormatters.formatEpochDay(commitment.dueDateEpochDay)}"
                }
                val formattedRemaining = maskedBalance(remainingSen)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            role = Role.Button,
                            onClickLabel = "Open ${commitment.title}",
                            onClick = onViewAllCommitments
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .privacySafeAmountSemantics(
                            label = "${commitment.title}, $dueSub, $cycleBadgeLabel",
                            isMasked = privacy.isAnyMaskActive,
                            visibleFormattedAmount = formattedRemaining
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = commitment.title,
                            style = typography.transactionDescription,
                            color = colors.primaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = dueSub,
                            style = typography.caption,
                            color = colors.secondaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = formattedRemaining,
                            style = typography.transactionAmount,
                            color = colors.primaryText
                        )
                        Spacer(Modifier.height(4.dp))
                        SpatialGlassPill(
                            label = cycleBadgeLabel,
                            selected = isInCurrentCycle,
                            accentColor = if (isInCurrentCycle) colors.peach else colors.lavender,
                            containerColor = if (isInCurrentCycle) colors.warningContainer else colors.selectedStateOverlay,
                            contentColor = if (isInCurrentCycle) colors.onWarningContainer else colors.primaryText
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpatialWalletsPreviewSection(
    activeWallets: List<Wallet>,
    archivedCount: Int,
    isSamplePreview: Boolean,
    onSelectWallet: (Wallet) -> Unit,
    onAddWallet: () -> Unit,
    onViewAllWallets: () -> Unit
) {
    val colors = SpatialTheme.colors
    val typography = SpatialTheme.typography
    val previewWallets = activeWallets.take(3)
    val totalWalletsCount = activeWallets.size + archivedCount

    SpatialGlassSection(
        title = if (isSamplePreview) "Wallets · Sample" else "Wallets",
        subtitle = if (activeWallets.size > 3 || archivedCount > 0) {
            "Showing ${previewWallets.size} of ${activeWallets.size} active" +
                if (archivedCount > 0) " · $archivedCount archived" else ""
        } else null,
        actionLabel = if (totalWalletsCount > 3 || archivedCount > 0) {
            "View All ($totalWalletsCount)"
        } else if (!isSamplePreview) {
            "+ Add"
        } else null,
        onActionClick = if (totalWalletsCount > 3 || archivedCount > 0) {
            onViewAllWallets
        } else {
            onAddWallet
        }
    ) {
        if (activeWallets.isEmpty()) {
            SpatialCompactListRow(
                title = "All wallets are archived",
                subtitle = "Tap to view $archivedCount archived wallet(s) or add a new active wallet",
                trailingPrimaryText = "View All",
                trailingPrimaryColor = colors.primaryAction,
                onClick = onViewAllWallets
            )
        } else {
            SpatialGlassCard(
                modifier = Modifier.fillMaxWidth(),
                tier = SpatialGlassTier.Standard,
                contentPadding = PaddingValues(vertical = 6.dp)
            ) {
                previewWallets.forEachIndexed { idx, wallet ->
                    if (idx > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = colors.divider
                        )
                    }
                    CompactWalletRowItem(
                        wallet = wallet,
                        onClick = { onSelectWallet(wallet) }
                    )
                }

                if (!isSamplePreview && (totalWalletsCount > 3 || archivedCount > 0)) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = colors.divider
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                role = Role.Button,
                                onClickLabel = "Add wallet",
                                onClick = onAddWallet
                            )
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            tint = colors.primaryAction,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Add Wallet",
                            style = typography.buttonLabel,
                            color = colors.primaryAction
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactWalletRowItem(
    wallet: Wallet,
    onClick: () -> Unit
) {
    val colors = SpatialTheme.colors
    val typography = SpatialTheme.typography
    val privacy = LocalPrivacySettings.current
    val accentColor = when (wallet.accentToken) {
        PrismaticAccentToken.AQUA -> colors.aqua
        PrismaticAccentToken.LAVENDER -> colors.lavender
        PrismaticAccentToken.SKY_BLUE -> colors.skyBlue
        PrismaticAccentToken.PEACH -> colors.peach
        PrismaticAccentToken.SLATE -> colors.slate
    }
    val tags = buildList {
        add(wallet.type.displayName)
        if (!wallet.isIncludedInTotalBalance) add("Hidden")
        else if (wallet.isExcludedFromSafeToSpend) add("Protected")
        if (wallet.isArchived) add("Archived")
    }
    val formattedBalance = maskedBalance(wallet.balanceSen)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                role = Role.Button,
                onClickLabel = "Manage ${wallet.name}",
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .privacySafeAmountSemantics(
                label = "${wallet.name}, ${tags.joinToString(", ")}",
                isMasked = privacy.isAnyMaskActive,
                visibleFormattedAmount = formattedBalance
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
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(accentColor)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = wallet.name,
                    style = typography.transactionDescription,
                    color = colors.primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = tags.joinToString(" · "),
                    style = typography.caption,
                    color = colors.secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = formattedBalance,
            style = typography.transactionAmount,
            color = colors.primaryText
        )
    }
}

@Composable
private fun SpatialRecentTransactionsPreviewSection(
    transactions: List<FinanceTransaction>,
    walletsById: Map<Long, Wallet>,
    isSamplePreview: Boolean,
    onSelectTransaction: (FinanceTransaction) -> Unit,
    onViewAllActivity: () -> Unit
) {
    val colors = SpatialTheme.colors
    val typography = SpatialTheme.typography
    val privacy = LocalPrivacySettings.current

    SpatialGlassSection(
        title = if (isSamplePreview) "Recent Transactions · Sample" else "Recent Transactions",
        actionLabel = "View All",
        onActionClick = onViewAllActivity
    ) {
        SpatialGlassCard(
            modifier = Modifier.fillMaxWidth(),
            tier = SpatialGlassTier.Standard,
            contentPadding = PaddingValues(vertical = 6.dp)
        ) {
            transactions.forEachIndexed { idx, tx ->
                if (idx > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = colors.divider
                    )
                }
                val walletName = walletsById[tx.walletId]?.name ?: "Wallet"
                val targetName = tx.targetWalletId?.let { walletsById[it]?.name }
                val iconKey = CategoryIconMapper.inferIconKeyFromName(tx.category, tx.type)
                val (badgeBg, badgeFg) = when (tx.type) {
                    TransactionType.INCOME -> colors.incomeContainer to colors.onIncomeContainer
                    TransactionType.EXPENSE -> colors.expenseContainer to colors.onExpenseContainer
                    TransactionType.TRANSFER -> colors.savingsContainer to colors.onSavingsContainer
                }
                val amountColor = when (tx.type) {
                    TransactionType.INCOME -> colors.income
                    TransactionType.EXPENSE -> colors.primaryText
                    TransactionType.TRANSFER -> colors.savings
                }
                val formattedAmount = if (privacy.isAnyMaskActive) {
                    PrivacyMask.MASKED_MYR
                } else {
                    when (tx.type) {
                        TransactionType.INCOME -> MyrFormatter.format(tx.amountSen, includePlusSign = true)
                        TransactionType.EXPENSE -> MyrFormatter.format(-tx.amountSen)
                        TransactionType.TRANSFER -> MyrFormatter.format(tx.amountSen)
                    }
                }
                val subtitle = if (tx.type == TransactionType.TRANSFER && targetName != null) {
                    "$walletName -> $targetName · ${DateTimeFormatters.formatDateOnly(tx.occurredAtEpochMs)}"
                } else {
                    "${tx.category} · $walletName · ${DateTimeFormatters.formatDateOnly(tx.occurredAtEpochMs)}"
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            role = Role.Button,
                            onClickLabel = "Inspect ${tx.title}",
                            onClick = { onSelectTransaction(tx) }
                        )
                        .padding(horizontal = 16.dp, vertical = 11.dp)
                        .privacySafeAmountSemantics(
                            label = "${tx.title}, $subtitle",
                            isMasked = privacy.isAnyMaskActive,
                            visibleFormattedAmount = formattedAmount
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
                                .size(36.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(badgeBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = CategoryIconMapper.iconFor(iconKey, tx.type),
                                contentDescription = null,
                                tint = badgeFg,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tx.title,
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
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = formattedAmount,
                        style = typography.transactionAmount,
                        color = amountColor
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AllWalletsBottomSheet(
    activeWallets: List<Wallet>,
    archivedWallets: List<Wallet>,
    isSamplePreview: Boolean,
    onSelectWallet: (Wallet) -> Unit,
    onAddWallet: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = SpatialTheme.colors
    val typography = SpatialTheme.typography

    SpatialGlassBottomSheetSurface(
        title = "All Wallets",
        subtitle = "Tap any wallet to edit, adjust balance, or archive",
        onDismissRequest = onDismiss
    ) {
        if (!isSamplePreview) {
            SpatialGlassButton(
                text = "Add New Wallet",
                onClick = onAddWallet,
                variant = SpatialButtonVariant.Primary,
                leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(14.dp))
        }

        if (activeWallets.isNotEmpty()) {
            Text(
                text = "Active Wallets (${activeWallets.size})",
                style = typography.cardTitle,
                color = colors.primaryText,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            SpatialGlassCard(
                modifier = Modifier.fillMaxWidth(),
                tier = SpatialGlassTier.Standard,
                contentPadding = PaddingValues(vertical = 6.dp)
            ) {
                activeWallets.forEachIndexed { idx, wallet ->
                    if (idx > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = colors.divider
                        )
                    }
                    CompactWalletRowItem(
                        wallet = wallet,
                        onClick = { onSelectWallet(wallet) }
                    )
                }
            }
        }

        if (archivedWallets.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Archived Wallets (${archivedWallets.size})",
                style = typography.cardTitle,
                color = colors.secondaryText,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            SpatialGlassCard(
                modifier = Modifier.fillMaxWidth(),
                tier = SpatialGlassTier.Subtle,
                contentPadding = PaddingValues(vertical = 6.dp)
            ) {
                archivedWallets.forEachIndexed { idx, wallet ->
                    if (idx > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = colors.divider
                        )
                    }
                    CompactWalletRowItem(
                        wallet = wallet,
                        onClick = { onSelectWallet(wallet) }
                    )
                }
            }
        }
    }
}

@Preview(name = "Home Sample Light", showBackground = true)
@Composable
private fun HomeDashboardLightPreview() {
    DuitTheme(themeMode = ThemeMode.LIGHT) {
        PrismaticBackground {
            HomeScreen(
                uiState = HomeUiState(
                    isLoading = false,
                    isSamplePreview = true,
                    themeMode = ThemeMode.LIGHT,
                    summary = SamplePreviewData.summary,
                    wallets = SamplePreviewData.wallets,
                    transactions = SamplePreviewData.transactions,
                    upcomingCommitments = SamplePreviewData.commitments
                ),
                contentPadding = PaddingValues(16.dp),
                onSelectTheme = {},
                onToggleSamplePreview = {},
                onAddWallet = { _, _, _, _, _, _, _, _ -> },
                onSelectWallet = {},
                onOpenTransactionEditor = {},
                onDismissError = {},
                onOpenCommissionCalculator = {}
            )
        }
    }
}

@Preview(name = "Home Sample Dark", showBackground = true)
@Composable
private fun HomeDashboardDarkPreview() {
    DuitTheme(themeMode = ThemeMode.DARK) {
        PrismaticBackground {
            HomeScreen(
                uiState = HomeUiState(
                    isLoading = false,
                    isSamplePreview = true,
                    themeMode = ThemeMode.DARK,
                    summary = SamplePreviewData.summary,
                    wallets = SamplePreviewData.wallets,
                    transactions = SamplePreviewData.transactions,
                    upcomingCommitments = SamplePreviewData.commitments
                ),
                contentPadding = PaddingValues(16.dp),
                onSelectTheme = {},
                onToggleSamplePreview = {},
                onAddWallet = { _, _, _, _, _, _, _, _ -> },
                onSelectWallet = {},
                onOpenTransactionEditor = {},
                onDismissError = {},
                onOpenCommissionCalculator = {}
            )
        }
    }
}
