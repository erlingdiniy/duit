package com.diniy.duit.devcenter.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.diniy.duit.core.ui.components.PrismaticBackground
import com.diniy.duit.core.ui.components.rememberDuitHaptic
import com.diniy.duit.core.ui.components.rememberReducedMotionEnabled
import com.diniy.duit.core.ui.glass.LiquidGlassCapabilities
import com.diniy.duit.core.ui.glass.LiquidGlassPresets
import com.diniy.duit.core.ui.glass.LiquidGlassSprings
import com.diniy.duit.core.ui.glass.LiquidGlassSurface
import com.diniy.duit.core.ui.glass.LocalLiquidGlassBackdrop
import com.diniy.duit.core.ui.glass.liquidGlassBackdropSource
import com.diniy.duit.core.ui.glass.rememberLiquidGlassBackdropState
import com.diniy.duit.core.ui.theme.LocalGlassIntensity
import com.diniy.duit.core.ui.theme.LocalVisualTheme
import com.diniy.duit.core.ui.theme.SpatialTheme
import com.diniy.duit.devcenter.DevAuthUiState
import com.diniy.duit.devcenter.DevCenterSection
import com.diniy.duit.devcenter.DevCenterUiState
import com.diniy.duit.devcenter.DevCenterViewModel
import com.diniy.duit.domain.model.GlassIntensity
import com.diniy.duit.domain.model.VisualTheme
import kotlin.math.roundToInt

internal enum class DevPrimaryDestination(
    val label: String,
    val icon: ImageVector,
    val section: DevCenterSection?
) {
    OVERVIEW("Overview", Icons.Rounded.Dashboard, DevCenterSection.OVERVIEW),
    ANALYTICS("Analytics", Icons.Rounded.Analytics, DevCenterSection.ANALYTICS),
    INBOX("Inbox", Icons.Rounded.Inbox, DevCenterSection.FEEDBACK),
    NOTIFY("Notify", Icons.Rounded.Notifications, DevCenterSection.NOTIFICATIONS),
    MORE("More", Icons.Rounded.GridView, null)
}

internal fun DevCenterSection.isMoreSubModule(): Boolean = when (this) {
    DevCenterSection.RELEASES,
    DevCenterSection.CRASH_REPORTS,
    DevCenterSection.REMOTE_CONFIG,
    DevCenterSection.GITHUB,
    DevCenterSection.INFRASTRUCTURE,
    DevCenterSection.AUDIT_LOGS,
    DevCenterSection.SETTINGS -> true
    DevCenterSection.OVERVIEW,
    DevCenterSection.ANALYTICS,
    DevCenterSection.FEEDBACK,
    DevCenterSection.NOTIFICATIONS -> false
}

private enum class DevActiveScreenKey {
    OVERVIEW,
    ANALYTICS,
    INBOX,
    NOTIFY,
    MORE_HUB,
    RELEASES,
    CRASH_REPORTS,
    REMOTE_CONFIG,
    GITHUB,
    INFRASTRUCTURE,
    AUDIT_LOGS,
    SETTINGS
}

private fun DevCenterSection.toScreenKey(): DevActiveScreenKey = when (this) {
    DevCenterSection.OVERVIEW -> DevActiveScreenKey.OVERVIEW
    DevCenterSection.ANALYTICS -> DevActiveScreenKey.ANALYTICS
    DevCenterSection.FEEDBACK -> DevActiveScreenKey.INBOX
    DevCenterSection.NOTIFICATIONS -> DevActiveScreenKey.NOTIFY
    DevCenterSection.RELEASES -> DevActiveScreenKey.RELEASES
    DevCenterSection.CRASH_REPORTS -> DevActiveScreenKey.CRASH_REPORTS
    DevCenterSection.REMOTE_CONFIG -> DevActiveScreenKey.REMOTE_CONFIG
    DevCenterSection.GITHUB -> DevActiveScreenKey.GITHUB
    DevCenterSection.INFRASTRUCTURE -> DevActiveScreenKey.INFRASTRUCTURE
    DevCenterSection.AUDIT_LOGS -> DevActiveScreenKey.AUDIT_LOGS
    DevCenterSection.SETTINGS -> DevActiveScreenKey.SETTINGS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperCenterShell(
    uiState: DevCenterUiState,
    authState: DevAuthUiState,
    viewModel: DevCenterViewModel,
    onBack: () -> Unit,
    onLockNow: () -> Unit
) {
    val colors = SpatialTheme.colors
    val typography = SpatialTheme.typography
    val visualTheme = LocalVisualTheme.current
    val glassIntensity = LocalGlassIntensity.current
    val reducedMotion = rememberReducedMotionEnabled()

    var isMoreHubRoot by rememberSaveable { mutableStateOf(false) }

    // Synchronize ViewModel-driven navigation (from tests, Quick Access shortcuts, or deep actions).
    LaunchedEffect(uiState.activeSection) {
        isMoreHubRoot = false
    }

    val isInMoreSubModule = !isMoreHubRoot && uiState.activeSection.isMoreSubModule()
    val activeDestination = when {
        isMoreHubRoot || isInMoreSubModule -> DevPrimaryDestination.MORE
        uiState.activeSection == DevCenterSection.OVERVIEW -> DevPrimaryDestination.OVERVIEW
        uiState.activeSection == DevCenterSection.ANALYTICS -> DevPrimaryDestination.ANALYTICS
        uiState.activeSection == DevCenterSection.FEEDBACK -> DevPrimaryDestination.INBOX
        uiState.activeSection == DevCenterSection.NOTIFICATIONS -> DevPrimaryDestination.NOTIFY
        else -> DevPrimaryDestination.MORE
    }
    val activeScreenKey = if (isMoreHubRoot) DevActiveScreenKey.MORE_HUB else uiState.activeSection.toScreenKey()

    val navigateBackAction: () -> Unit = {
        when {
            isInMoreSubModule -> isMoreHubRoot = true
            isMoreHubRoot || uiState.activeSection != DevCenterSection.OVERVIEW -> {
                isMoreHubRoot = false
                viewModel.selectSection(DevCenterSection.OVERVIEW)
            }
            else -> onBack()
        }
    }

    BackHandler(onBack = navigateBackAction)

    uiState.pendingKillSwitchModal?.let { pending ->
        KillSwitchConfirmDialog(
            pending = pending,
            onDismiss = viewModel::dismissKillSwitchModal,
            onConfirm = viewModel::confirmKillSwitchToggle
        )
    }

    val desiredQualityMode = remember(glassIntensity) {
        LiquidGlassCapabilities.qualityModeFor(glassIntensity)
    }
    val backdropState = rememberLiquidGlassBackdropState(initialQualityMode = desiredQualityMode)
    SideEffect {
        if (backdropState.qualityMode != desiredQualityMode) {
            backdropState.qualityMode = desiredQualityMode
        }
    }
    val useLiquidBackdrop = visualTheme == VisualTheme.LIQUID_GLASS && glassIntensity != GlassIntensity.SUBTLE

    val onSelectPrimaryDestination: (DevPrimaryDestination) -> Unit = { dest ->
        if (dest == DevPrimaryDestination.MORE) {
            isMoreHubRoot = true
        } else {
            isMoreHubRoot = false
            dest.section?.let(viewModel::selectSection)
        }
    }

    val onOpenSubSection: (DevCenterSection) -> Unit = { sec ->
        isMoreHubRoot = false
        viewModel.selectSection(sec)
    }

    val headerTitle = when {
        isMoreHubRoot -> "More"
        isInMoreSubModule -> uiState.activeSection.label
        else -> activeDestination.label
    }

    val statusDotColor = when {
        uiState.errorBanner != null -> colors.error
        uiState.dashboard.health.connected -> colors.success
        uiState.isRefreshing -> colors.warning
        else -> colors.warning
    }
    val statusDotLabel = when {
        uiState.errorBanner != null -> "Connection warning"
        uiState.dashboard.health.connected -> "Backend live"
        uiState.isRefreshing -> "Syncing backend"
        else -> "Snapshot offline or cached"
    }

    PrismaticBackground {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent()
                            viewModel.authManager.recordUserActivity()
                        }
                    }
                }
        ) {
            val isExpanded = maxWidth >= 600.dp
            val bottomContentPadding = if (isExpanded) 28.dp else 104.dp

            CompositionLocalProvider(
                LocalDevBottomContentPadding provides bottomContentPadding,
                LocalLiquidGlassBackdrop provides if (useLiquidBackdrop) backdropState else null
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                ) {
                    if (isExpanded) {
                        NavigationRail(
                            containerColor = Color.Transparent,
                            modifier = Modifier
                                .fillMaxHeight()
                                .statusBarsPadding()
                                .navigationBarsPadding()
                                .verticalScroll(rememberScrollState())
                        ) {
                            Spacer(Modifier.height(8.dp))
                            DevPrimaryDestination.entries.forEach { dest ->
                                val isSelected = dest == activeDestination
                                val badgeCount = if (dest == DevPrimaryDestination.INBOX) uiState.unreadFeedbackCount else 0
                                NavigationRailItem(
                                    selected = isSelected,
                                    onClick = { onSelectPrimaryDestination(dest) },
                                    icon = {
                                        DevNavIconWithBadge(
                                            icon = dest.icon,
                                            contentDescription = dest.label,
                                            badgeCount = badgeCount,
                                            tint = if (isSelected) colors.primaryAction else colors.secondaryText
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = dest.label,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .let { base ->
                                    if (useLiquidBackdrop && !isExpanded) {
                                        base.liquidGlassBackdropSource(backdropState)
                                    } else {
                                        base
                                    }
                                }
                                .background(colors.appBackground)
                                .statusBarsPadding()
                        ) {
                            // Top bar — clean single-line heading + live dot + refresh + lock
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = navigateBackAction,
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                        contentDescription = if (isInMoreSubModule) "Back to More" else "Back",
                                        tint = colors.primaryText
                                    )
                                }
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = headerTitle,
                                        style = typography.screenHeading,
                                        color = colors.primaryText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .weight(1f, fill = false)
                                            .semantics { heading() }
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(statusDotColor)
                                            .semantics { contentDescription = statusDotLabel }
                                    )
                                }
                                if (uiState.isRefreshing) {
                                    Box(
                                        modifier = Modifier.size(48.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp
                                        )
                                    }
                                } else {
                                    IconButton(
                                        onClick = viewModel::refreshAll,
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Refresh,
                                            contentDescription = "Refresh status",
                                            tint = colors.primaryText
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = onLockNow,
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Lock,
                                        contentDescription = "Lock Developer Center",
                                        tint = colors.primaryText
                                    )
                                }
                            }

                            uiState.tokenExpirationNotice?.let { notice ->
                                DevStatusBanner(
                                    message = notice,
                                    isSuccess = false,
                                    onDismiss = { onOpenSubSection(DevCenterSection.SETTINGS) },
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                )
                            }

                            uiState.statusBanner?.let { banner ->
                                DevStatusBanner(
                                    message = banner,
                                    isSuccess = true,
                                    onDismiss = viewModel::clearBanners,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                )
                            }

                            uiState.errorBanner?.let { err ->
                                DevStatusBanner(
                                    message = err,
                                    isSuccess = false,
                                    onDismiss = viewModel::clearBanners,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                )
                            }

                            PullToRefreshBox(
                                isRefreshing = uiState.isRefreshing,
                                onRefresh = viewModel::refreshAll,
                                modifier = Modifier.weight(1f).fillMaxWidth()
                            ) {
                                Crossfade(
                                    targetState = activeScreenKey,
                                    animationSpec = tween(durationMillis = if (reducedMotion) 0 else 150),
                                    label = "dev_center_screen_crossfade"
                                ) { key ->
                                    when (key) {
                                        DevActiveScreenKey.OVERVIEW -> DevOverviewScreen(
                                            uiState = uiState,
                                            onSelectSection = onOpenSubSection,
                                            onSelectDateRange = viewModel::selectAnalyticsDateRange,
                                            onTriggerSync = {
                                                if (uiState.vault.isBackendEnrolled) viewModel.triggerBackendSync()
                                                else viewModel.refreshAll()
                                            },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        DevActiveScreenKey.ANALYTICS -> DevAnalyticsScreen(
                                            uiState = uiState,
                                            onSelectDateRange = viewModel::selectAnalyticsDateRange,
                                            onRefresh = viewModel::refreshAnalytics,
                                            onSignInWithGoogle = { launchUrl ->
                                                viewModel.startAnalyticsGoogleSignIn(onLaunchBrowser = launchUrl)
                                            },
                                            onTriggerSync = viewModel::triggerBackendSync,
                                            onOpenSettings = { onOpenSubSection(DevCenterSection.SETTINGS) },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        DevActiveScreenKey.INBOX -> DevFeedbackScreen(
                                            uiState = uiState,
                                            viewModel = viewModel,
                                            onOpenSettings = { onOpenSubSection(DevCenterSection.SETTINGS) },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        DevActiveScreenKey.NOTIFY -> DevNotificationsScreen(
                                            uiState = uiState,
                                            viewModel = viewModel,
                                            onOpenSettings = { onOpenSubSection(DevCenterSection.SETTINGS) },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        DevActiveScreenKey.MORE_HUB -> DevMoreHubScreen(
                                            uiState = uiState,
                                            onOpenSection = onOpenSubSection,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        DevActiveScreenKey.RELEASES -> DevReleasesScreen(
                                            uiState = uiState,
                                            viewModel = viewModel,
                                            onSelectFilter = viewModel::selectReleaseFilter,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        DevActiveScreenKey.CRASH_REPORTS -> DevCrashReportsScreen(
                                            uiState = uiState,
                                            onSelectDateRange = viewModel::selectAnalyticsDateRange,
                                            onOpenSettings = { onOpenSubSection(DevCenterSection.SETTINGS) },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        DevActiveScreenKey.REMOTE_CONFIG -> DevRemoteConfigScreen(
                                            uiState = uiState,
                                            viewModel = viewModel,
                                            onOpenSettings = { onOpenSubSection(DevCenterSection.SETTINGS) },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        DevActiveScreenKey.GITHUB -> DevGitHubScreen(
                                            uiState = uiState,
                                            viewModel = viewModel,
                                            onOpenSettings = { onOpenSubSection(DevCenterSection.SETTINGS) },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        DevActiveScreenKey.INFRASTRUCTURE -> DevInfrastructureScreen(
                                            uiState = uiState,
                                            viewModel = viewModel,
                                            onOpenSettings = { onOpenSubSection(DevCenterSection.SETTINGS) },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        DevActiveScreenKey.AUDIT_LOGS -> DevAuditLogsScreen(
                                            uiState = uiState,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        DevActiveScreenKey.SETTINGS -> DevSettingsScreen(
                                            uiState = uiState,
                                            authState = authState,
                                            viewModel = viewModel,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                        }

                        if (!isExpanded) {
                            DevCenterBottomBar(
                                destinations = DevPrimaryDestination.entries,
                                activeDestination = activeDestination,
                                unreadInboxCount = uiState.unreadFeedbackCount,
                                onSelect = onSelectPrimaryDestination,
                                modifier = Modifier.align(Alignment.BottomCenter)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DevNavIconWithBadge(
    icon: ImageVector,
    contentDescription: String?,
    badgeCount: Int,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(width = 34.dp, height = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        if (badgeCount > 0) {
            BadgedBox(
                badge = {
                    Badge(modifier = Modifier.offset(x = 5.dp, y = (-3).dp)) {
                        Text(
                            text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                            maxLines = 1
                        )
                    }
                }
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
            }
        } else {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun DevCenterBottomBar(
    destinations: List<DevPrimaryDestination>,
    activeDestination: DevPrimaryDestination,
    unreadInboxCount: Int,
    onSelect: (DevPrimaryDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SpatialTheme.colors
    val geometry = SpatialTheme.geometry
    val visualTheme = LocalVisualTheme.current
    val glassIntensity = LocalGlassIntensity.current
    val backdropState = LocalLiquidGlassBackdrop.current
    val useLiquidGlass = visualTheme == VisualTheme.LIQUID_GLASS && backdropState != null
    val isLiquidHigh = useLiquidGlass && glassIntensity == GlassIntensity.RICH
    val reducedMotion = rememberReducedMotionEnabled()
    val performHaptic = rememberDuitHaptic()
    val density = LocalDensity.current

    val selectedIndex = destinations.indexOf(activeDestination).coerceAtLeast(0)
    val animatedIndex = remember { Animatable(selectedIndex.toFloat()) }
    var rowHeightPx by remember { mutableIntStateOf(with(density) { 56.dp.roundToPx() }) }

    LaunchedEffect(selectedIndex, reducedMotion, useLiquidGlass) {
        val target = selectedIndex.toFloat()
        if (reducedMotion) {
            animatedIndex.snapTo(target)
        } else {
            animatedIndex.animateTo(
                targetValue = target,
                animationSpec = if (useLiquidGlass) {
                    LiquidGlassSprings.Snap
                } else {
                    spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                }
            )
        }
    }

    val scrimBrush = Brush.verticalGradient(
        colors = listOf(
            Color.Transparent,
            colors.appBackground.copy(alpha = 0.78f),
            colors.appBackground.copy(alpha = 0.96f)
        )
    )
    val pillShape = RoundedCornerShape(geometry.radiusFloatingNav)
    val indicatorShape = RoundedCornerShape(24.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(scrimBrush)
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        val barContent: @Composable () -> Unit = {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp)
            ) {
                val slotWidthDp = maxWidth / destinations.size
                val slotWidthPx = with(density) { slotWidthDp.toPx() }
                val indicatorHeightDp = with(density) { rowHeightPx.toDp() }

                val liquidLensBackdrop = backdropState
                if (liquidLensBackdrop != null && visualTheme == VisualTheme.LIQUID_GLASS) {
                    val lensSpec = remember(colors, glassIntensity) {
                        LiquidGlassPresets.floatingNavLens(colors, glassIntensity)
                    }
                    LiquidGlassSurface(
                        backdropState = liquidLensBackdrop,
                        spec = lensSpec,
                        qualityModeOverride = LiquidGlassCapabilities.qualityModeFor(glassIntensity),
                        modifier = Modifier
                            .offset {
                                IntOffset(x = (animatedIndex.value * slotWidthPx).roundToInt(), y = 0)
                            }
                            .width(slotWidthDp)
                            .height(indicatorHeightDp)
                    ) {}
                } else {
                    val pillColor = if (visualTheme == VisualTheme.LIQUID_GLASS) {
                        colors.primaryAction.copy(alpha = if (colors.isDark) 0.22f else 0.14f)
                    } else {
                        colors.coral
                    }
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(x = (animatedIndex.value * slotWidthPx).roundToInt(), y = 0)
                            }
                            .width(slotWidthDp)
                            .height(indicatorHeightDp)
                            .clip(indicatorShape)
                            .background(pillColor)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { size ->
                            if (size.height > 0) rowHeightPx = size.height
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    destinations.forEachIndexed { idx, dest ->
                        val isSelected = idx == selectedIndex
                        val selectedInk = if (visualTheme == VisualTheme.LIQUID_GLASS) {
                            if (isLiquidHigh && colors.isDark) Color(0xFF38A3FF) else colors.primaryAction
                        } else {
                            colors.onCoral
                        }
                        val tint by animateColorAsState(
                            targetValue = if (isSelected) selectedInk else colors.secondaryText,
                            animationSpec = tween(durationMillis = if (reducedMotion) 0 else 150),
                            label = "dev_nav_ink"
                        )
                        val badgeCount = if (dest == DevPrimaryDestination.INBOX) unreadInboxCount else 0
                        val interactionSource = remember { MutableInteractionSource() }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 52.dp)
                                .clip(indicatorShape)
                                .semantics(mergeDescendants = true) {
                                    selected = isSelected
                                }
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                    role = Role.Tab,
                                    onClickLabel = "Open ${dest.label}",
                                    onClick = {
                                        if (!isSelected || dest == DevPrimaryDestination.MORE) {
                                            performHaptic(HapticFeedbackType.SegmentTick)
                                            onSelect(dest)
                                        }
                                    }
                                )
                                .padding(start = 2.dp, end = 2.dp, top = 8.dp, bottom = 5.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            DevNavIconWithBadge(
                                icon = dest.icon,
                                contentDescription = null,
                                badgeCount = badgeCount,
                                tint = tint
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = dest.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = tint,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        val capsuleMod = Modifier
            .widthIn(max = geometry.maxFloatingNavWidth)
            .fillMaxWidth()

        val liquidCapsuleBackdrop = backdropState
        if (liquidCapsuleBackdrop != null && visualTheme == VisualTheme.LIQUID_GLASS) {
            val barSpec = remember(colors, glassIntensity) {
                LiquidGlassPresets.floatingNavBar(colors, glassIntensity)
            }
            LiquidGlassSurface(
                backdropState = liquidCapsuleBackdrop,
                spec = barSpec,
                qualityModeOverride = LiquidGlassCapabilities.qualityModeFor(glassIntensity),
                modifier = capsuleMod,
                content = { barContent() }
            )
        } else if (visualTheme == VisualTheme.LIQUID_GLASS) {
            Box(
                modifier = capsuleMod
                    .clip(pillShape)
                    .background(colors.floatingNavSurface.copy(alpha = if (colors.isDark) 0.90f else 0.94f))
                    .border(BorderStroke(1.dp, colors.glassBorder), pillShape)
            ) {
                barContent()
            }
        } else {
            Box(
                modifier = capsuleMod
                    .drawBehind {
                        val o = 3.dp.toPx()
                        val outline = pillShape.createOutline(size, layoutDirection, this)
                        translate(left = 0f, top = o) { drawOutline(outline, colors.sticker) }
                    }
                    .clip(pillShape)
                    .background(colors.floatingNavSurface)
                    .border(BorderStroke(2.dp, colors.sticker), pillShape)
            ) {
                barContent()
            }
        }
    }
}
