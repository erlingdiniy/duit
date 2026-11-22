package com.diniy.duit.feature.home

import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.collectIsPressedAsState
import com.diniy.duit.core.ui.components.candyEntrance
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import com.diniy.duit.core.ui.components.CandyBubbleCard
import com.diniy.duit.core.ui.components.CandyDateTile
import com.diniy.duit.core.ui.components.CandyDecor
import com.diniy.duit.core.ui.components.CandyIconBlob
import com.diniy.duit.core.ui.components.CandyMoneyText
import com.diniy.duit.core.ui.components.CandyPerforation
import com.diniy.duit.core.ui.components.CandyProgressRing
import com.diniy.duit.core.ui.components.CandySectionHeader
import com.diniy.duit.core.ui.components.CandyStatusBadge
import com.diniy.duit.core.ui.components.CandySticker
import com.diniy.duit.core.ui.components.CandyTicketShape
import com.diniy.duit.core.ui.components.CandyTone
import com.diniy.duit.core.ui.components.SpatialGlassSurface
import com.diniy.duit.core.ui.components.candyPressable
import com.diniy.duit.core.ui.components.candyTone
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
import androidx.compose.material.icons.rounded.CalendarMonth
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
import com.diniy.duit.core.ui.glass.LiquidGlassActionPill
import com.diniy.duit.core.ui.glass.LiquidGlassCapabilities
import com.diniy.duit.core.ui.glass.LiquidGlassOrbButton
import com.diniy.duit.core.ui.glass.LiquidGlassPanel
import com.diniy.duit.core.ui.glass.LiquidGlassPanelBox
import com.diniy.duit.core.ui.glass.LiquidGlassPanelPresets
import com.diniy.duit.core.ui.glass.LiquidGlassSurface
import com.diniy.duit.core.ui.glass.liquidGlassBackdropSource
import com.diniy.duit.core.ui.glass.rememberLiquidGlassBackdropState
import com.diniy.duit.core.ui.theme.DuitTheme
import com.diniy.duit.core.ui.theme.LocalGlassIntensity
import com.diniy.duit.core.ui.theme.LocalVisualTheme
import com.diniy.duit.core.ui.theme.SpatialTheme
import com.diniy.duit.domain.model.GlassIntensity
import com.diniy.duit.domain.model.VisualTheme
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
    onOpenCalendar: (() -> Unit)? = null,
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
                    onOpenSettingsHub = onOpenSettingsHub,
                    onOpenCalendar = onOpenCalendar
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
                val rawOrder = uiState.appSettings.dashboardSectionOrder
                val isDefaultOrder = rawOrder == DashboardSectionKey.entries.toList()
                // Phase 9: default Home order prioritizes Safe to Spend -> Balances -> Upcoming bills -> Recent activity -> Wallets.
                val orderedSections = if (isDefaultOrder) {
                    listOf(
                        DashboardSectionKey.SECONDARY_METRICS,
                        DashboardSectionKey.QUICK_ACTIONS,
                        DashboardSectionKey.PENDING_COMMISSIONS,
                        DashboardSectionKey.UPCOMING_COMMITMENTS,
                        DashboardSectionKey.WALLETS
                    )
                } else {
                    rawOrder
                }

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
                        // Quick actions now live in the floating + sheet.
                        DashboardSectionKey.QUICK_ACTIONS -> Unit

                        // 5. Pending Commissions Notice
                        DashboardSectionKey.PENDING_COMMISSIONS -> {
                            if (uiState.appSettings.commissionFeatureEnabled && uiState.appSettings.commissionTrackingEnabled && uiState.summary.pendingCommissionsSen > 0L) {
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

                        // 7. Recent Activity + Compact Wallet Preview
                        DashboardSectionKey.WALLETS -> {
                            if (uiState.transactions.isNotEmpty()) {
                                item(key = "recent_transactions_section", contentType = "recent_transactions") {
                                    SpatialRecentTransactionsPreviewSection(
                                        transactions = uiState.transactions.take(3),
                                        walletsById = walletsById,
                                        isSamplePreview = uiState.isSamplePreview,
                                        onSelectTransaction = onSelectTransaction,
                                        onViewAllActivity = onOpenActivity
                                    )
                                }
                            }

                            if (uiState.wallets.isEmpty() && uiState.archivedWallets.isEmpty() && !uiState.isSamplePreview) {
                                item(key = "empty_ledger", contentType = "empty") {
                                    PrismaticEmptyStateCard(
                                        iconRes = R.drawable.ic_solar_wallet,
                                        title = "Ready for your first wallet",
                                        message = if (com.diniy.duit.BuildConfig.PUBLIC_EDITION) "Add where you keep your money, like a bank account, e-wallet or cash. Your balances stay on this phone."
                                        else "Add a bank account, e-wallet, or cash wallet to track your Ringgit balance privately on-device.",
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

    val celebrate = com.diniy.duit.core.ui.components.LocalCandyCelebrate.current
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
                    { showAddWalletSheet = false; celebrate("Wallet created") },
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
    onOpenSettingsHub: () -> Unit,
    onOpenCalendar: (() -> Unit)? = null
) {
    // Experiment 01: privacy lives in the hero, notifications in Settings. Callbacks stay wired.
    val colors = SpatialTheme.colors
    val typography = SpatialTheme.typography

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp)
            // Phase 11I: tighter gap to the Safe to Spend card (layout height trimmed; nothing is clipped)
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val trim = 10.dp.roundToPx()
                layout(placeable.width, (placeable.height - trim).coerceAtLeast(0)) { placeable.place(0, 0) }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = buildAnnotatedString {
                        append("duit")
                        withStyle(SpanStyle(color = colors.coral)) { append(".") }
                    },
                    style = typography.screenHeading.copy(fontSize = 34.sp, lineHeight = 38.sp),
                    color = colors.primaryText,
                    modifier = Modifier.semantics { heading() }
                )
                if (isSamplePreview) {
                    CandySticker(text = "Sample", tone = CandyTone.Sunny, tilt = 4f)
                }
            }
            // Phase 11I: cycle subtitle removed; the cycle is shown inside the Safe to Spend card.
        }

        val isLiquidGlass = LocalVisualTheme.current == VisualTheme.LIQUID_GLASS
        if (onOpenCalendar != null) {
            if (isLiquidGlass) {
                LiquidGlassOrbButton(
                    icon = androidx.compose.material.icons.Icons.Rounded.CalendarMonth,
                    contentDescription = "Money Calendar",
                    onClick = onOpenCalendar
                )
            } else {
                com.diniy.duit.core.ui.components.CandyMintIconButton(
                    icon = androidx.compose.material.icons.Icons.Rounded.CalendarMonth,
                    contentDescription = "Money Calendar",
                    onClick = onOpenCalendar
                )
            }
            Spacer(Modifier.width(8.dp))
        }
        HomeSettingsButton(onClick = onOpenSettingsHub)
    }
}

/** Phase 11E: mint Candy Bubble settings button; Phase 10E: optical Liquid Glass orb in Liquid Glass theme. */
@Composable
private fun HomeSettingsButton(onClick: () -> Unit) {
    if (LocalVisualTheme.current == VisualTheme.LIQUID_GLASS) {
        LiquidGlassOrbButton(
            icon = Icons.Rounded.Settings,
            contentDescription = "Settings hub",
            onClick = onClick
        )
    } else {
        com.diniy.duit.core.ui.components.CandyMintIconButton(
            icon = Icons.Rounded.Settings,
            contentDescription = "Settings hub",
            onClick = onClick
        )
    }
}

@Composable
private fun HomeSectionHeader(
    title: String,
    count: Int? = null,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    if (LocalVisualTheme.current != VisualTheme.LIQUID_GLASS) {
        CandySectionHeader(
            title = title,
            count = count,
            subtitle = subtitle,
            actionLabel = actionLabel,
            onAction = onAction
        )
        return
    }
    val colors = SpatialTheme.colors
    val typography = SpatialTheme.typography
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    style = typography.sectionHeading,
                    color = colors.primaryText,
                    modifier = Modifier.semantics { heading() }
                )
                if (count != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(colors.primaryAction.copy(alpha = if (colors.isDark) 0.22f else 0.14f))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = count.toString(),
                            style = typography.caption,
                            color = colors.primaryAction
                        )
                    }
                }
            }
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = typography.caption,
                    color = colors.secondaryText
                )
            }
        }
        if (actionLabel != null && onAction != null) {
            LiquidGlassActionPill(
                onClick = onAction,
                onClickLabel = actionLabel
            ) {
                Text(
                    text = actionLabel,
                    style = typography.caption,
                    color = colors.primaryAction
                )
            }
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
    if (LocalVisualTheme.current == VisualTheme.LIQUID_GLASS) {
        LiquidGlassSafeToSpendHeroCard(
            safeToSpendSen = safeToSpendSen,
            upcomingUnreservedSen = upcomingUnreservedSen,
            activeReservePeriodLabel = activeReservePeriodLabel,
            onToggleBalanceVisibility = onToggleBalanceVisibility
        )
        return
    }
    val colors = SpatialTheme.colors
    val typography = SpatialTheme.typography
    val privacySettings = LocalPrivacySettings.current
    val isMasked = privacySettings.isAnyMaskActive
    val isTight = safeToSpendSen < 0L
    val heroFill = if (isTight) colors.expenseContainer else colors.coral
    val heroInk = if (isTight) colors.onExpenseContainer else colors.onCoral
    val trayColor = if (isTight) heroInk.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.38f)
    val sunny = colors.sunny
    val stickerInk = colors.sticker

    // Outer box lets the coin sticker break out of the card edge.
    Box(modifier = Modifier.fillMaxWidth().candyEntrance("home_hero")) {
        SpatialGlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, end = 4.dp),
            containerColor = heroFill,
            sticker = true,
            shape = RoundedCornerShape(32.dp)
        ) {
            Box(
                Modifier
                    .matchParentSize()
                    .drawBehind {
                        // Layered candy bubbles: big soft cream, small sunny drop.
                        drawCircle(Color.White.copy(alpha = 0.20f), radius = size.width * 0.36f, center = Offset(size.width * 0.98f, size.height * 0.02f))
                        drawCircle(Color.White.copy(alpha = 0.12f), radius = size.width * 0.16f, center = Offset(size.width * 0.66f, size.height * 0.10f))
                        drawCircle(sunny.copy(alpha = 0.55f), radius = size.width * 0.09f, center = Offset(size.width * 0.04f, size.height * 0.98f))
                    }
            )
            Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 18.dp)) {
                Text(text = "Safe to spend", style = typography.cardTitle.copy(fontSize = 18.sp), color = heroInk)
                Text(
                    text = activeReservePeriodLabel,
                    style = typography.caption,
                    color = heroInk.copy(alpha = 0.82f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 56.dp)
                )

                Spacer(Modifier.height(12.dp))

                SpatialAnimatedCounterText(
                    amountSen = safeToSpendSen,
                    accessibilityLabel = "Safe to Spend",
                    style = typography.heroBalance.copy(fontSize = 50.sp, lineHeight = 56.sp, letterSpacing = (-1.5).sp),
                    color = heroInk,
                    splitMinorScale = 0.46f
                )

                Spacer(Modifier.height(8.dp))

                CandySticker(
                    text = if (isTight) "Overcommitted" else "Good to go",
                    tone = if (isTight) CandyTone.Sunny else CandyTone.Mint,
                    tilt = -3f,
                    leadingIcon = if (isTight) Icons.Rounded.Warning else Icons.Rounded.CheckCircle
                )

                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(trayColor)
                        .padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = heroInk,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (upcomingUnreservedSen > 0L) {
                            "${maskedBalance(upcomingUnreservedSen)} set aside for this cycle's bills. Savings protected."
                        } else {
                            "This cycle's bills are settled. Savings protected."
                        },
                        style = typography.caption,
                        color = heroInk,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(6.dp))
                    // Hide/Show amount lives here (single control, synced with global privacy state).
                    Row(
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(50))
                            .background(heroInk)
                            .candyPressable(
                                onClick = onToggleBalanceVisibility,
                                onClickLabel = if (isMasked) "Show sensitive amounts" else "Hide sensitive amounts"
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isMasked) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = null,
                            tint = heroFill,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (isMasked) "Show" else "Hide",
                            style = typography.caption,
                            color = heroFill
                        )
                    }
                }
            }
        }

        // Decorative coin sticker (hidden from TalkBack).
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 14.dp)
                .size(58.dp)
                .rotate(14f)
                .clearAndSetSemantics { }
                .drawBehind {
                    translate(left = 3.dp.toPx(), top = 3.dp.toPx()) {
                        drawCircle(stickerInk)
                    }
                }
                .clip(CircleShape)
                .background(sunny)
                .border(BorderStroke(2.5.dp, stickerInk), CircleShape)
                .drawBehind {
                    drawCircle(stickerInk.copy(alpha = 0.25f), radius = size.minDimension * 0.34f, style = Stroke(width = 2.dp.toPx()))
                },
            contentAlignment = Alignment.Center
        ) {
            Text(text = "RM", style = typography.cardTitle.copy(fontSize = 15.sp), color = colors.onSunny)
        }
    }
}

/**
 * Phase 10E Liquid Glass Safe-to-Spend Hero Card (Home only):
 * - LOW: crisp solid optical blue card, flat border, solid inner tray, flat RM badge.
 * - MID: soft frosted optical blue glass, delicate top catch-light sheen, frosted RM medallion.
 * - HIGH: full Liquid Glass with specular bevel, subtle chromatic corner glint, and a genuine
 *   AGSL convex chromatic glass `RM` lens medallion refracting the hero optical highlight.
 */
@Composable
private fun LiquidGlassSafeToSpendHeroCard(
    safeToSpendSen: Long,
    upcomingUnreservedSen: Long,
    activeReservePeriodLabel: String,
    onToggleBalanceVisibility: () -> Unit
) {
    val colors = SpatialTheme.colors
    val typography = SpatialTheme.typography
    val intensity = LocalGlassIntensity.current
    val privacySettings = LocalPrivacySettings.current
    val isMasked = privacySettings.isAnyMaskActive
    val isTight = safeToSpendSen < 0L
    val isLow = intensity == GlassIntensity.SUBTLE
    val isHigh = intensity == GlassIntensity.RICH

    val heroBase = when {
        isTight -> colors.expenseContainer
        colors.isDark -> Color(0xFF0A54BD)
        else -> Color(0xFF0062CC)
    }
    val heroInk = if (isTight) colors.onExpenseContainer else Color.White
    val trayFill = when {
        isTight -> heroInk.copy(alpha = if (isLow) 0.14f else 0.10f)
        isLow -> if (colors.isDark) Color(0xFF063B8A) else Color(0xFF004B9E)
        isHigh -> Color(0xFF002D66).copy(alpha = if (colors.isDark) 0.44f else 0.32f)
        else -> Color(0xFF00387A).copy(alpha = if (colors.isDark) 0.36f else 0.26f)
    }
    val hidePillBg = when {
        isTight -> heroInk
        colors.isDark -> Color(0xFF0B2545).copy(alpha = if (isLow) 1f else 0.86f)
        else -> Color(0xFFF2F7FF).copy(alpha = if (isLow) 1f else 0.92f)
    }
    val hidePillInk = when {
        isTight -> heroBase
        colors.isDark -> Color(0xFFD6EAFF)
        else -> Color(0xFF0055B8)
    }

    val heroSpec = remember(colors, intensity, heroBase) {
        LiquidGlassPanelPresets.card(
            colors = colors,
            intensity = intensity,
            elevated = true
        ).copy(
            fillColor = heroBase,
            topSheenAlpha = when (intensity) {
                GlassIntensity.SUBTLE -> 0f
                GlassIntensity.BALANCED -> 0.26f
                GlassIntensity.RICH -> 0.44f
            },
            bottomReflectionAlpha = when (intensity) {
                GlassIntensity.SUBTLE -> 0f
                GlassIntensity.BALANCED -> 0.10f
                GlassIntensity.RICH -> 0.24f
            },
            rimHighlightAlpha = when (intensity) {
                GlassIntensity.SUBTLE -> 0f
                GlassIntensity.BALANCED -> 0.46f
                GlassIntensity.RICH -> 0.72f
            },
            chromaticGlintAlpha = if (isHigh) 0.42f else 0f,
            borderColor = if (isLow) {
                if (colors.isDark) Color(0x2EFFFFFF) else Color(0x24002B5C)
            } else {
                Color(0x38FFFFFF)
            }
        )
    }
    val desiredQuality = remember(intensity) { LiquidGlassCapabilities.qualityModeFor(intensity) }
    val heroBackdrop = rememberLiquidGlassBackdropState(initialQualityMode = desiredQuality)
    androidx.compose.runtime.SideEffect {
        if (heroBackdrop.qualityMode != desiredQuality) {
            heroBackdrop.qualityMode = desiredQuality
        }
    }

    Box(modifier = Modifier.fillMaxWidth().candyEntrance("home_hero")) {
        LiquidGlassPanelBox(
            spec = heroSpec,
            shape = RoundedCornerShape(32.dp),
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isHigh) Modifier.liquidGlassBackdropSource(heroBackdrop) else Modifier)
        ) {
            // Subtle calibrated optical depth highlight inside hero (HIGH/MID only; zero bubbles in LOW)
            if (!isLow) {
                Box(
                    Modifier
                        .matchParentSize()
                        .drawBehind {
                            val topGlowAlpha = if (isHigh) 0.22f else 0.12f
                            drawCircle(
                                brush = androidx.compose.ui.graphics.Brush.radialGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = topGlowAlpha),
                                        Color.Transparent
                                    ),
                                    center = Offset(size.width * 0.88f, size.height * 0.14f),
                                    radius = size.width * 0.52f
                                ),
                                center = Offset(size.width * 0.88f, size.height * 0.14f),
                                radius = size.width * 0.52f
                            )
                        }
                )
            }

            Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 18.dp)) {
                Text(text = "Safe to spend", style = typography.cardTitle.copy(fontSize = 18.sp), color = heroInk)
                Text(
                    text = activeReservePeriodLabel,
                    style = typography.caption,
                    color = heroInk.copy(alpha = 0.86f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 68.dp)
                )

                Spacer(Modifier.height(12.dp))

                SpatialAnimatedCounterText(
                    amountSen = safeToSpendSen,
                    accessibilityLabel = "Safe to Spend",
                    style = typography.heroBalance.copy(fontSize = 50.sp, lineHeight = 56.sp, letterSpacing = (-1.5).sp),
                    color = heroInk,
                    splitMinorScale = 0.46f
                )

                Spacer(Modifier.height(8.dp))

                // Optical status chip (upright, clean contrast across all intensities)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (isLow) {
                                if (colors.isDark) Color(0xFF0B2545) else Color(0xFFF2F7FF)
                            } else {
                                Color.White.copy(alpha = if (colors.isDark) 0.18f else 0.90f)
                            }
                        )
                        .border(
                            BorderStroke(
                                width = if (isLow) 1.dp else 0.75.dp,
                                color = if (isLow) Color(0x24FFFFFF) else Color.White.copy(alpha = 0.55f)
                            ),
                            RoundedCornerShape(50)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    val statusTint = if (colors.isDark && !isLow) Color.White else hidePillInk
                    Icon(
                        imageVector = if (isTight) Icons.Rounded.Warning else Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = statusTint,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (isTight) "Overcommitted" else "Good to go",
                        style = typography.caption.copy(fontFamily = typography.cardTitle.fontFamily),
                        color = statusTint,
                        maxLines = 1
                    )
                }

                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(trayFill)
                        .then(
                            if (!isLow) {
                                Modifier.border(
                                    BorderStroke(0.75.dp, Color.White.copy(alpha = if (isHigh) 0.28f else 0.18f)),
                                    RoundedCornerShape(18.dp)
                                )
                            } else {
                                Modifier
                            }
                        )
                        .padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = heroInk,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (upcomingUnreservedSen > 0L) {
                            "${maskedBalance(upcomingUnreservedSen)} set aside for this cycle's bills. Savings protected."
                        } else {
                            "This cycle's bills are settled. Savings protected."
                        },
                        style = typography.caption,
                        color = heroInk,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(6.dp))
                    Row(
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(50))
                            .background(hidePillBg)
                            .then(
                                if (!isLow) {
                                    Modifier.border(
                                        BorderStroke(0.75.dp, Color.White.copy(alpha = if (isHigh) 0.55f else 0.32f)),
                                        RoundedCornerShape(50)
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .candyPressable(
                                onClick = onToggleBalanceVisibility,
                                onClickLabel = if (isMasked) "Show sensitive amounts" else "Hide sensitive amounts"
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isMasked) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = null,
                            tint = hidePillInk,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (isMasked) "Show" else "Hide",
                            style = typography.caption,
                            color = hidePillInk
                        )
                    }
                }
            }
        }

        // Upright optical RM medallion (decorative, hidden from TalkBack):
        // HIGH = genuine AGSL convex chromatic glass lens; MID = soft frosted disc; LOW = flat solid disc.
        val medallionMod = Modifier
            .align(Alignment.TopEnd)
            .padding(top = 16.dp, end = 16.dp)
            .size(54.dp)
            .clearAndSetSemantics { }

        if (isHigh) {
            val lensSpec = remember(colors) { LiquidGlassPanelPresets.heroMedallionLens(colors) }
            LiquidGlassSurface(
                backdropState = heroBackdrop,
                spec = lensSpec,
                qualityModeOverride = desiredQuality,
                modifier = medallionMod
            ) {
                Box(modifier = Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "RM",
                        style = typography.cardTitle.copy(fontSize = 15.sp),
                        color = Color.White
                    )
                }
            }
        } else {
            Box(
                modifier = medallionMod
                    .clip(CircleShape)
                    .background(
                        if (isLow) {
                            if (colors.isDark) Color(0xFF063B8A) else Color(0xFF004B9E)
                        } else {
                            Color.White.copy(alpha = 0.22f)
                        }
                    )
                    .border(
                        BorderStroke(
                            width = if (isLow) 1.dp else 0.85.dp,
                            color = Color.White.copy(alpha = if (isLow) 0.26f else 0.50f)
                        ),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "RM",
                    style = typography.cardTitle.copy(fontSize = 15.sp),
                    color = heroInk
                )
            }
        }
    }
}

@Composable
private fun HomeGlassOrBubbleCard(
    tone: CandyTone,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(SpatialTheme.geometry.radiusLg),
    decor: CandyDecor = CandyDecor.Bubbles,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    if (LocalVisualTheme.current == VisualTheme.LIQUID_GLASS) {
        val accent = if (tone == CandyTone.Neutral) null else candyTone(tone).fill
        LiquidGlassPanel(
            modifier = modifier,
            shape = shape,
            accentTint = accent,
            contentPadding = contentPadding,
            onClick = onClick,
            onClickLabel = onClickLabel,
            content = content
        )
    } else {
        CandyBubbleCard(
            tone = tone,
            modifier = modifier,
            shape = shape,
            decor = decor,
            contentPadding = contentPadding,
            onClick = onClick,
            onClickLabel = onClickLabel,
            content = content
        )
    }
}

@Composable
private fun HomeGlassOrSpatialCard(
    modifier: Modifier = Modifier,
    tier: SpatialGlassTier = SpatialGlassTier.Standard,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(28.dp),
    contentPadding: PaddingValues = PaddingValues(vertical = 6.dp, horizontal = 4.dp),
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    if (LocalVisualTheme.current == VisualTheme.LIQUID_GLASS) {
        LiquidGlassPanel(
            modifier = modifier,
            shape = shape,
            elevated = tier == SpatialGlassTier.Elevated,
            contentPadding = contentPadding,
            content = content
        )
    } else {
        SpatialGlassCard(
            modifier = modifier,
            tier = tier,
            shape = shape,
            contentPadding = contentPadding,
            content = content
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
    val typography = SpatialTheme.typography
    val isLiquidGlass = LocalVisualTheme.current == VisualTheme.LIQUID_GLASS
    val glassIntensity = LocalGlassIntensity.current
    val privacy = LocalPrivacySettings.current
    val walletCaption = if (walletCount == 1) "1 wallet" else "$walletCount wallets"
    val goalFraction = if (savingsBalanceSen > 0L) {
        (goalAllocatedSen.toFloat() / savingsBalanceSen.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val totalText = maskedBalance(totalBalanceSen)
    val savingsText = maskedBalance(savingsBalanceSen)

    Column(
        modifier = Modifier.candyEntrance("home_summary"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Wallet pocket: total balance across included wallets
            HomeGlassOrBubbleCard(
                tone = CandyTone.Sky,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .privacySafeAmountSemantics("Total balance, $walletCaption", privacy.isAnyMaskActive, totalText),
                shape = if (isLiquidGlass) RoundedCornerShape(26.dp) else RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 28.dp, bottomEnd = 12.dp),
                onClick = onOpenAllWallets,
                onClickLabel = "View all wallets"
            ) {
                CandyIconBlob(Icons.Rounded.AccountBalanceWallet, CandyTone.Sky, size = 40.dp, tilt = -8f, solid = true)
                Spacer(Modifier.height(12.dp))
                Text("Total balance", style = typography.caption, color = colors.secondaryText)
                CandyMoneyText(totalText, typography.cardBalance, colors.primaryText)
                Spacer(Modifier.height(8.dp))
                CandyStatusBadge(walletCaption, CandyTone.Sky)
            }

            // Savings jar: protected balance with goal-allocation ring
            HomeGlassOrBubbleCard(
                tone = CandyTone.Sunny,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .privacySafeAmountSemantics("Protected savings", privacy.isAnyMaskActive, savingsText),
                shape = if (isLiquidGlass) RoundedCornerShape(26.dp) else RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 12.dp, bottomEnd = 28.dp),
                onClick = onOpenSavingsGoals,
                onClickLabel = "Open savings goals"
            ) {
                CandyProgressRing(fraction = goalFraction, tone = CandyTone.Sunny, size = 40.dp, stroke = 5.dp) {
                    Icon(Icons.Rounded.Savings, contentDescription = null, tint = candyTone(CandyTone.Sunny).ink, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text("Protected savings", style = typography.caption, color = colors.secondaryText)
                CandyMoneyText(savingsText, typography.cardBalance, colors.primaryText)
                Spacer(Modifier.height(8.dp))
                CandyStatusBadge(
                    if (activeGoalsCount > 0) "$activeGoalsCount goal(s) · ${(goalFraction * 100).toInt()}% set" else "Protected",
                    CandyTone.Sunny
                )
            }
        }

        // Bill ticket: current-cycle reserve with a perforated next-cycle stub
        val peach = candyTone(CandyTone.Peach)
        val reservedText = maskedBalance(currentCycleReservedSen)
        val futureText = maskedBalance(upcomingFutureBillsSen)
        val ticketContent: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(0.66f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CandyIconBlob(Icons.AutoMirrored.Rounded.ReceiptLong, CandyTone.Peach, size = 32.dp, tilt = 6f, solid = true)
                        Spacer(Modifier.width(8.dp))
                        Text("Reserved this cycle", style = typography.caption, color = colors.secondaryText, modifier = Modifier.weight(1f))
                        if (upcomingCount > 0) {
                            CandySticker(text = "$upcomingCount due", tone = CandyTone.Coral, tilt = 4f)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    CandyMoneyText(reservedText, typography.largeBalance.copy(fontSize = 26.sp), colors.primaryText)
                    Text(
                        text = if (upcomingCount > 0) "$upcomingCount bill(s) due this cycle" else "No unpaid bills this cycle",
                        style = typography.caption,
                        color = peach.ink
                    )
                }
                CandyPerforation(
                    color = peach.fill,
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(horizontal = 10.dp)
                )
                Column(
                    modifier = Modifier.weight(0.34f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Next cycle", style = typography.caption, color = colors.secondaryText)
                    CandyMoneyText(futureText, typography.transactionAmount, colors.primaryText, minorScale = 0.72f)
                    Text(
                        text = if (upcomingFutureCount > 0) "$upcomingFutureCount not reserved" else "Nothing yet",
                        style = typography.caption,
                        color = colors.secondaryText,
                        maxLines = 2
                    )
                }
            }
        }
        if (isLiquidGlass) {
            val ticketSpec = remember(colors, glassIntensity, peach.fill) {
                LiquidGlassPanelPresets.card(
                    colors = colors,
                    intensity = glassIntensity,
                    accentTint = peach.fill
                )
            }
            LiquidGlassPanelBox(
                spec = ticketSpec,
                modifier = Modifier
                    .fillMaxWidth()
                    .privacySafeAmountSemantics("Current cycle reserved", privacy.isAnyMaskActive, reservedText),
                shape = CandyTicketShape(notchFraction = 0.66f),
                onClick = onOpenCommitments,
                onClickLabel = "Open commitments",
                content = ticketContent
            )
        } else {
            SpatialGlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .privacySafeAmountSemantics("Current cycle reserved", privacy.isAnyMaskActive, reservedText),
                shape = CandyTicketShape(notchFraction = 0.66f),
                containerColor = peach.container,
                onClick = onOpenCommitments,
                onClickLabel = "Open commitments",
                content = ticketContent
            )
        }
    }
}

@Composable
private fun SpatialPendingCommissionsCard(
    pendingCommissionsSen: Long,
    onClick: () -> Unit
) {
    val colors = SpatialTheme.colors
    val typography = SpatialTheme.typography
    val privacy = LocalPrivacySettings.current
    val amount = maskedBalance(pendingCommissionsSen)

    HomeGlassOrBubbleCard(
        tone = CandyTone.Sunny,
        decor = CandyDecor.Corner,
        modifier = Modifier
            .fillMaxWidth()
            .privacySafeAmountSemantics("Pending commissions, awaiting payout", privacy.isAnyMaskActive, amount),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        onClick = onClick,
        onClickLabel = "Open commissions"
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CandyIconBlob(Icons.Rounded.Payments, CandyTone.Sunny, size = 40.dp, tilt = 6f, solid = true)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Pending commissions", style = typography.transactionDescription, color = colors.primaryText)
                Text("Awaiting payout · not in Safe to Spend", style = typography.caption, color = colors.secondaryText)
            }
            Spacer(Modifier.width(8.dp))
            CandyMoneyText(amount, typography.transactionAmount, candyTone(CandyTone.Sunny).ink, minorScale = 0.72f)
        }
    }
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
    val previewItems = commitments.take(3)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HomeSectionHeader(
            title = if (isSamplePreview) "Coming up · Sample" else "Coming up",
            count = commitments.size,
            actionLabel = "All bills",
            onAction = onViewAllCommitments
        )
        HomeGlassOrSpatialCard(
            modifier = Modifier.fillMaxWidth(),
            tier = SpatialGlassTier.Standard,
            shape = RoundedCornerShape(28.dp),
            contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp)
        ) {
        previewItems.forEach { commitment ->
            val isInCurrentCycle = ObligationCalculator.isDueWithinHorizon(
                dueDateEpochDay = commitment.dueDateEpochDay,
                activeCycle = activeCycle
            )
            val remainingSen = (commitment.amountSen - commitment.paidSen.coerceAtLeast(0L)).coerceAtLeast(0L)
            val (badgeLabel, tone) = when {
                commitment.status == CommitmentStatus.PAID -> "Paid" to CandyTone.Mint
                isInCurrentCycle -> "This cycle" to CandyTone.Coral
                else -> "Next cycle" to CandyTone.Sky
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
                    .clip(RoundedCornerShape(20.dp))
                    .candyPressable(onClick = onViewAllCommitments, onClickLabel = "Open ${commitment.title}")
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .privacySafeAmountSemantics(
                        label = "${commitment.title}, $dueSub, $badgeLabel",
                        isMasked = privacy.isAnyMaskActive,
                        visibleFormattedAmount = formattedRemaining
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CandyDateTile(epochDay = commitment.dueDateEpochDay, tone = tone)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = commitment.title,
                        style = typography.transactionDescription,
                        color = colors.primaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = dueSub,
                        style = typography.caption,
                        color = colors.secondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    CandyMoneyText(formattedRemaining, typography.transactionAmount, colors.primaryText, minorScale = 0.72f)
                    Spacer(Modifier.height(4.dp))
                    CandyStatusBadge(badgeLabel, tone)
                }
            }
        }
        }
    }
}

private fun walletTone(token: PrismaticAccentToken): CandyTone = when (token) {
    PrismaticAccentToken.AQUA -> CandyTone.Mint
    PrismaticAccentToken.LAVENDER -> CandyTone.Coral
    PrismaticAccentToken.SKY_BLUE -> CandyTone.Sky
    PrismaticAccentToken.PEACH -> CandyTone.Peach
    PrismaticAccentToken.SLATE -> CandyTone.Sunny
}

private fun walletIcon(type: WalletType): ImageVector = when (type) {
    WalletType.CASH -> Icons.Rounded.Payments
    WalletType.BANK -> Icons.Rounded.AccountBalance
    WalletType.EWALLET -> Icons.Rounded.Smartphone
    WalletType.SAVINGS -> Icons.Rounded.Savings
}

private fun walletTags(wallet: Wallet): List<String> = buildList {
    add(wallet.type.displayName)
    if (!wallet.isIncludedInTotalBalance) add("Hidden")
    else if (wallet.isExcludedFromSafeToSpend) add("Protected")
    if (wallet.isArchived) add("Archived")
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
    val privacy = LocalPrivacySettings.current
    val totalWalletsCount = activeWallets.size + archivedCount

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HomeSectionHeader(
            title = if (isSamplePreview) "Wallets · Sample" else "Wallets",
            count = activeWallets.size,
            subtitle = if (archivedCount > 0) "$archivedCount archived" else null,
            actionLabel = if (totalWalletsCount > 0) "View all" else null,
            onAction = onViewAllWallets
        )
        if (activeWallets.isEmpty()) {
            SpatialCompactListRow(
                title = "All wallets are archived",
                subtitle = "Tap to view $archivedCount archived wallet(s) or add a new active wallet",
                trailingPrimaryText = "View All",
                trailingPrimaryColor = colors.primaryAction,
                onClick = onViewAllWallets
            )
        } else {
            // Wallet "pockets": a swipeable row of distinct candy cards.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                activeWallets.take(6).forEachIndexed { idx, wallet ->
                    val tone = walletTone(wallet.accentToken)
                    val tags = walletTags(wallet)
                    val balance = maskedBalance(wallet.balanceSen)
                    HomeGlassOrBubbleCard(
                        tone = tone,
                        decor = CandyDecor.Corner,
                        modifier = Modifier
                            .width(156.dp)
                            .fillMaxHeight()
                            .privacySafeAmountSemantics("${wallet.name}, ${tags.joinToString(", ")}", privacy.isAnyMaskActive, balance),
                        contentPadding = PaddingValues(14.dp),
                        onClick = { onSelectWallet(wallet) },
                        onClickLabel = "Manage ${wallet.name}"
                    ) {
                        CandyIconBlob(walletIcon(wallet.type), tone, size = 36.dp, tilt = if (idx % 2 == 0) -7f else 6f, solid = true)
                        Spacer(Modifier.height(12.dp))
                        Text(wallet.name, style = typography.transactionDescription, color = colors.primaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(tags.joinToString(" · "), style = typography.caption, color = colors.secondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(8.dp))
                        CandyMoneyText(balance, typography.cardBalance.copy(fontSize = 18.sp), colors.primaryText)
                    }
                }
                if (!isSamplePreview) {
                    val dash = colors.secondaryText.copy(alpha = 0.45f)
                    Column(
                        modifier = Modifier
                            .width(112.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(26.dp))
                            .drawBehind {
                                drawRoundRect(
                                    color = dash,
                                    cornerRadius = CornerRadius(26.dp.toPx()),
                                    style = Stroke(
                                        width = 2.dp.toPx(),
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))
                                    )
                                )
                            }
                            .candyPressable(onClick = onAddWallet, onClickLabel = "Add wallet")
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CandyIconBlob(Icons.Rounded.Add, CandyTone.Coral, size = 40.dp, solid = true)
                        Spacer(Modifier.height(8.dp))
                        Text("Add wallet", style = typography.caption, color = colors.primaryText)
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
    val tone = walletTone(wallet.accentToken)
    val tags = walletTags(wallet)
    val formattedBalance = maskedBalance(wallet.balanceSen)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .candyPressable(onClick = onClick, onClickLabel = "Manage ${wallet.name}")
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .privacySafeAmountSemantics(
                label = "${wallet.name}, ${tags.joinToString(", ")}",
                isMasked = privacy.isAnyMaskActive,
                visibleFormattedAmount = formattedBalance
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CandyIconBlob(walletIcon(wallet.type), tone, size = 38.dp, tilt = -5f, solid = true)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(wallet.name, style = typography.transactionDescription, color = colors.primaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(tags.joinToString(" · "), style = typography.caption, color = colors.secondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(12.dp))
        CandyMoneyText(formattedBalance, typography.transactionAmount, colors.primaryText, minorScale = 0.72f)
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

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HomeSectionHeader(
            title = if (isSamplePreview) "Recent activity · Sample" else "Recent activity",
            actionLabel = "View all",
            onAction = onViewAllActivity
        )
        HomeGlassOrSpatialCard(
            modifier = Modifier.fillMaxWidth(),
            tier = SpatialGlassTier.Standard,
            shape = RoundedCornerShape(28.dp),
            contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp)
        ) {
            transactions.forEachIndexed { idx, tx ->
                val walletName = walletsById[tx.walletId]?.name ?: "Wallet"
                val targetName = tx.targetWalletId?.let { walletsById[it]?.name }
                val iconKey = CategoryIconMapper.inferIconKeyFromName(tx.category, tx.type)
                val tone = when (tx.type) {
                    TransactionType.INCOME -> CandyTone.Mint
                    TransactionType.EXPENSE -> CandyTone.Coral
                    TransactionType.TRANSFER -> CandyTone.Sunny
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
                    "$walletName → $targetName"
                } else {
                    "${tx.category} · $walletName"
                }
                val dateLabel = DateTimeFormatters.formatDateOnly(tx.occurredAtEpochMs)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .candyPressable(onClick = { onSelectTransaction(tx) }, onClickLabel = "Inspect ${tx.title}")
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .privacySafeAmountSemantics(
                            label = "${tx.title}, $subtitle, $dateLabel",
                            isMasked = privacy.isAnyMaskActive,
                            visibleFormattedAmount = formattedAmount
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CandyIconBlob(
                        icon = CategoryIconMapper.iconFor(iconKey, tx.type),
                        tone = tone,
                        size = 44.dp,
                        tilt = if (idx % 2 == 0) -6f else 5f
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(tx.title, style = typography.transactionDescription, color = colors.primaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(subtitle, style = typography.caption, color = colors.secondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        CandyMoneyText(formattedAmount, typography.transactionAmount.copy(fontSize = 16.sp), amountColor, minorScale = 0.72f)
                        Text(dateLabel, style = typography.caption, color = colors.secondaryText, maxLines = 1)
                    }
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
            // Room for the button's soft shadow before the first section title.
            Spacer(Modifier.height(if (com.diniy.duit.BuildConfig.PUBLIC_EDITION) 20.dp else 14.dp))
        }

        if (activeWallets.isNotEmpty()) {
            Text(
                text = "Active Wallets (${activeWallets.size})",
                style = typography.cardTitle,
                color = colors.primaryText,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            HomeGlassOrSpatialCard(
                modifier = Modifier.fillMaxWidth(),
                tier = SpatialGlassTier.Standard,
                shape = RoundedCornerShape(24.dp),
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
            HomeGlassOrSpatialCard(
                modifier = Modifier.fillMaxWidth(),
                tier = SpatialGlassTier.Subtle,
                shape = RoundedCornerShape(24.dp),
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
