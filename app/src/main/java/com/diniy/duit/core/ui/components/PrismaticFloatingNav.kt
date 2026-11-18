package com.diniy.duit.core.ui.components

import androidx.compose.ui.graphics.drawOutline
import androidx.compose.animation.core.animateDpAsState

import androidx.compose.ui.graphics.drawscope.translate

import androidx.compose.ui.draw.drawBehind

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.diniy.duit.core.ui.theme.SpatialTheme
import com.diniy.duit.domain.model.TransactionType
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class PrismaticNavItem(
    val route: String,
    val label: String,
    @param:DrawableRes val iconRes: Int,
    val contentDescription: String
)

/**
 * Phase 6B Prismatic Spatial Glass floating bottom navigation bar.
 * Built on [SpatialGlassNavigationSurface] and [SpatialTheme] tokens:
 * - Translucent floating capsule with directional specular rim and soft depth shadow
 * - Central floating `+` quick-add action flanked by Home/Activity and Planning/Insights
 * - Spring-ready active tab lens indicator + horizontal drag gesture support
 * - Full gesture navigation & cutout insets
 */
@Composable
fun PrismaticFloatingBottomBar(
    items: List<PrismaticNavItem>,
    currentRoute: String,
    onSelect: (PrismaticNavItem) -> Unit,
    modifier: Modifier = Modifier,
    onQuickAddClick: (() -> Unit)? = null
) {
    if (items.isEmpty()) return

    val colors = SpatialTheme.colors
    val geometry = SpatialTheme.geometry
    val reducedMotion = rememberReducedMotionEnabled()
    val performHaptic = rememberDuitHaptic()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val selectedIndex = items.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    val pillShape = RoundedCornerShape(geometry.radiusFloatingNav)
    val activeIndicatorShape = RoundedCornerShape(24.dp)

    val hasCenterAction = onQuickAddClick != null && items.size == 4
    val totalSlots = if (hasCenterAction) items.size + 1 else items.size

    fun itemIndexToVisualSlot(itemIdx: Int): Int =
        if (hasCenterAction && itemIdx >= 2) itemIdx + 1 else itemIdx

    fun visualSlotToItemIndex(slotIdx: Int): Int? {
        if (!hasCenterAction) return slotIdx.coerceIn(0, items.lastIndex)
        return when {
            slotIdx == 2 -> null
            slotIdx > 2 -> (slotIdx - 1).coerceIn(0, items.lastIndex)
            else -> slotIdx.coerceIn(0, items.lastIndex)
        }
    }

    val targetVisualSlot = itemIndexToVisualSlot(selectedIndex).toFloat()
    val animatedSlot = remember { Animatable(targetVisualSlot) }
    var isDragging by remember { mutableStateOf(false) }
    var dragHoverIndex by remember { mutableIntStateOf(selectedIndex) }
    var rowHeightPx by remember { mutableIntStateOf(with(density) { 56.dp.roundToPx() }) }

    LaunchedEffect(targetVisualSlot, isDragging, reducedMotion) {
        if (!isDragging) {
            if (reducedMotion) {
                animatedSlot.snapTo(targetVisualSlot)
            } else {
                animatedSlot.animateTo(
                    targetValue = targetVisualSlot,
                    animationSpec = spring(
                        // Phase 11K: critically damped (no overshoot/wobble), ~250ms glide
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        colors.appBackground.copy(alpha = 0.86f),
                        colors.appBackground.copy(alpha = 0.98f)
                    )
                )
            )
            .navigationBarsPadding()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .widthIn(max = geometry.maxFloatingNavWidth)
                .fillMaxWidth()
                // Candy Bubble nav: sticker capsule (outline + hard offset shadow)
                .drawBehind {
                    val o = 3.dp.toPx()
                    val outline = pillShape.createOutline(size, layoutDirection, this)
                    translate(left = 0f, top = o) { drawOutline(outline, colors.sticker) }
                }
                .clip(pillShape)
                .background(colors.floatingNavSurface)
                .border(BorderStroke(2.dp, colors.sticker), pillShape)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            val totalWidthPx = with(density) { maxWidth.toPx() }
            val slotWidthPx = (totalWidthPx / totalSlots).coerceAtLeast(1f)
            val slotWidthDp = maxWidth / totalSlots

            val dragModifier = Modifier.pointerInput(totalSlots, slotWidthPx, selectedIndex) {
                detectHorizontalDragGestures(
                    onDragStart = { startOffset ->
                        val slot = (startOffset.x / slotWidthPx).toInt().coerceIn(0, totalSlots - 1)
                        val mapped = visualSlotToItemIndex(slot)
                        if (mapped != null) {
                            isDragging = true
                            dragHoverIndex = mapped
                        }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        if (!isDragging) return@detectHorizontalDragGestures
                        change.consume()
                        val deltaFraction = dragAmount / slotWidthPx
                        val nextSlot = (animatedSlot.value + deltaFraction)
                            .coerceIn(0f, (totalSlots - 1).toFloat())
                        scope.launch { animatedSlot.snapTo(nextSlot) }
                        val nearestSlot = nextSlot.roundToInt().coerceIn(0, totalSlots - 1)
                        val hoveredItem = visualSlotToItemIndex(nearestSlot)
                        if (hoveredItem != null && hoveredItem != dragHoverIndex) {
                            dragHoverIndex = hoveredItem
                            performHaptic(HapticFeedbackType.TextHandleMove)
                        }
                    },
                    onDragEnd = {
                        if (!isDragging) return@detectHorizontalDragGestures
                        isDragging = false
                        val targetItem = dragHoverIndex.coerceIn(0, items.lastIndex)
                        if (targetItem != selectedIndex) {
                            performHaptic(HapticFeedbackType.TextHandleMove)
                            onSelect(items[targetItem])
                        } else {
                            scope.launch {
                                if (reducedMotion) {
                                    animatedSlot.snapTo(targetVisualSlot)
                                } else {
                                    animatedSlot.animateTo(
                                        targetValue = targetVisualSlot,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    )
                                }
                            }
                        }
                    },
                    onDragCancel = {
                        isDragging = false
                        scope.launch { animatedSlot.animateTo(targetVisualSlot) }
                    }
                )
            }

            Box(modifier = Modifier.fillMaxWidth().then(dragModifier)) {
                val indicatorHeightDp = with(density) { rowHeightPx.toDp() }
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = (animatedSlot.value * slotWidthPx).roundToInt(),
                                y = 0
                            )
                        }
                        .width(slotWidthDp)
                        .height(indicatorHeightDp)
                        .clip(activeIndicatorShape)
                        .background(colors.coral)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { size ->
                            if (size.height > 0) rowHeightPx = size.height
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val activeHighlightIndex = if (isDragging) dragHoverIndex else selectedIndex

                    if (hasCenterAction) {
                        items.take(2).forEachIndexed { idx, item ->
                            FloatingNavTabItem(
                                item = item,
                                isSelected = idx == activeHighlightIndex,
                                reducedMotion = reducedMotion,
                                onClick = {
                                    if (idx != selectedIndex) {
                                        performHaptic(HapticFeedbackType.SegmentTick)
                                        onSelect(item)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            SpatialQuickAddCenterButton(
                                onClick = {
                                    performHaptic(HapticFeedbackType.TextHandleMove)
                                    onQuickAddClick?.invoke()
                                }
                            )
                        }

                        items.drop(2).forEachIndexed { offsetIdx, item ->
                            val idx = offsetIdx + 2
                            FloatingNavTabItem(
                                item = item,
                                isSelected = idx == activeHighlightIndex,
                                reducedMotion = reducedMotion,
                                onClick = {
                                    if (idx != selectedIndex) {
                                        performHaptic(HapticFeedbackType.SegmentTick)
                                        onSelect(item)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        items.forEachIndexed { index, item ->
                            FloatingNavTabItem(
                                item = item,
                                isSelected = index == activeHighlightIndex,
                                reducedMotion = reducedMotion,
                                onClick = {
                                    if (index != selectedIndex) {
                                        performHaptic(HapticFeedbackType.SegmentTick)
                                        onSelect(item)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Experiment 01: stand-alone floating quick action (bottom-right, above the nav bar).
 * Coral sticker, 60dp (>= 48dp touch target), subtle press sink + squash; opens the existing
 * quick-action sheet via [onClick]. Reduce Motion disables the press motion.
 */
@Composable
fun DuitQuickActionFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val performHaptic = rememberDuitHaptic()
    SpatialQuickAddCenterButton(
        onClick = {
            performHaptic(HapticFeedbackType.ContextClick)
            onClick()
        },
        modifier = modifier,
        buttonSize = 60.dp
    )
}

@Composable
private fun SpatialQuickAddCenterButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: androidx.compose.ui.unit.Dp = 52.dp
) {
    val colors = SpatialTheme.colors
    val motion = SpatialTheme.motion
    val reducedMotion = rememberReducedMotionEnabled()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val geometry = SpatialTheme.geometry
    val shift by animateDpAsState(
        targetValue = if (isPressed && !reducedMotion) 2.dp else 0.dp,
        animationSpec = prismaticSpring(
            reducedMotion = reducedMotion,
            dampingRatio = motion.pressDampingRatio,
            stiffness = motion.pressStiffness
        ),
        label = "quick_add_center_shift"
    )
    val buttonShape = RoundedCornerShape(buttonSize * 0.36f)
    val stickerInk = colors.sticker
    val press by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        animationSpec = prismaticSpring(
            reducedMotion = reducedMotion,
            dampingRatio = motion.pressDampingRatio,
            stiffness = motion.pressStiffness
        ),
        label = "quick_add_center_press"
    )
    val (squashX, squashY) = motion.resolveSquash(press, reducedMotion)

    Box(
        modifier = modifier
            .size(buttonSize)
            .graphicsLayer {
                scaleX = squashX
                scaleY = squashY
            }
            .drawBehind {
                val o = (3.dp - shift).toPx()
                val outline = buttonShape.createOutline(size, layoutDirection, this)
                translate(left = o, top = o) { drawOutline(outline, stickerInk) }
            }
            .offset(x = shift, y = shift)
            .clip(buttonShape)
            .background(colors.coral)
            .border(BorderStroke(geometry.stickerBorder, stickerInk), buttonShape)
            .semantics {
                contentDescription = "Quick add transaction or action"
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClickLabel = "Quick add transaction",
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = null,
            tint = colors.onCoral,
            modifier = Modifier
                .size(26.dp)
                .graphicsLayer { rotationZ = if (reducedMotion) 0f else 90f * press }
        )
    }
}

@Composable
private fun FloatingNavTabItem(
    item: PrismaticNavItem,
    isSelected: Boolean,
    reducedMotion: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SpatialTheme.colors
    val interactionSource = remember { MutableInteractionSource() }

    val contentColor by animateColorAsState(
        targetValue = if (isSelected) colors.onCoral else colors.secondaryText,
        animationSpec = prismaticTween(reducedMotion = reducedMotion, durationMillis = 180),
        label = "nav_item_tint"
    )

    val iconTint by animateColorAsState(
        targetValue = if (isSelected) colors.onCoral else colors.secondaryText,
        animationSpec = prismaticTween(reducedMotion = reducedMotion, durationMillis = 180),
        label = "nav_icon_tint"
    )

    val iconScale by animateFloatAsState(
        targetValue = if (isSelected && !reducedMotion) 1.06f else 1f,
        animationSpec = prismaticSpring(reducedMotion = reducedMotion, dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "nav_icon_scale"
    )

    Column(
        modifier = modifier
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(24.dp))
            .semantics(mergeDescendants = true) {
                selected = isSelected
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClickLabel = "Open ${item.label}",
                onClick = onClick
            )
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(id = item.iconRes),
            contentDescription = item.contentDescription,
            tint = iconTint,
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                }
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Adaptive leading translucent navigation rail for medium/expanded windows and phone landscape.
 */
@Composable
fun PrismaticNavigationRail(
    items: List<PrismaticNavItem>,
    currentRoute: String,
    onSelect: (PrismaticNavItem) -> Unit,
    modifier: Modifier = Modifier,
    onQuickAddClick: (() -> Unit)? = null
) {
    val colors = SpatialTheme.colors
    val reducedMotion = rememberReducedMotionEnabled()
    val performHaptic = rememberDuitHaptic()
    val railShape = RoundedCornerShape(28.dp)

    Column(
        modifier = modifier
            .fillMaxHeight()
            .statusBarsPadding()
            .navigationBarsPadding()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start))
            .padding(start = 12.dp, top = 12.dp, bottom = 12.dp)
            .clip(railShape)
            .background(colors.floatingNavSurface)
            .border(BorderStroke(1.dp, colors.glassBorder), railShape)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "duit.",
            style = SpatialTheme.typography.cardTitle,
            color = colors.primaryText,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        if (onQuickAddClick != null) {
            SpatialQuickAddCenterButton(
                onClick = {
                    performHaptic(HapticFeedbackType.TextHandleMove)
                    onQuickAddClick()
                }
            )
            Spacer(Modifier.height(4.dp))
        }

        items.forEach { item ->
            val isSelected = item.route == currentRoute
            val pillTint by animateColorAsState(
                targetValue = if (isSelected) colors.floatingNavActivePill else Color.Transparent,
                animationSpec = prismaticTween(reducedMotion = reducedMotion, durationMillis = 180),
                label = "rail_pill"
            )
            Box(
                modifier = Modifier
                    .widthIn(min = 72.dp, max = 88.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(pillTint)
            ) {
                FloatingNavTabItem(
                    item = item,
                    isSelected = isSelected,
                    reducedMotion = reducedMotion,
                    onClick = {
                        if (!isSelected) {
                            performHaptic(HapticFeedbackType.TextHandleMove)
                            onSelect(item)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Compact Spatial Glass quick-add sheet opened by the central `+` button in the floating bottom bar.
 * Preserves immediate access to Expense, Income, Transfer, Commission Calculator, and Category management.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpatialQuickAddSheet(
    onDismiss: () -> Unit,
    onSelectTransactionType: (TransactionType) -> Unit,
    onOpenCommissionTools: () -> Unit,
    onOpenCategories: () -> Unit
) {
    val colors = SpatialTheme.colors
    val spacing = SpatialTheme.spacing

    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    // Slide the sheet away first, then run exactly one action.
    fun closeThen(action: () -> Unit) {
        if (closing) return
        closing = true
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            onDismiss()
            action()
        }
    }

    SpatialGlassBottomSheetSurface(
        title = "Quick Action",
        subtitle = "Record a transaction or open financial tools",
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs)
            ) {
                QuickAddTile(
                    title = "Expense",
                    subtitle = "Record spend",
                    accentColor = colors.expense,
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.ArrowUpward,
                            contentDescription = null,
                            tint = colors.expense
                        )
                    },
                    onClick = {
                        closeThen { onSelectTransactionType(TransactionType.EXPENSE) }
                    },
                    modifier = Modifier.weight(1f)
                )
                QuickAddTile(
                    title = "Income",
                    subtitle = "Salary or credit",
                    accentColor = colors.income,
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.ArrowDownward,
                            contentDescription = null,
                            tint = colors.income
                        )
                    },
                    onClick = {
                        closeThen { onSelectTransactionType(TransactionType.INCOME) }
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs)
            ) {
                QuickAddTile(
                    title = "Transfer",
                    subtitle = "Between wallets",
                    accentColor = colors.skyBlue,
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.SwapHoriz,
                            contentDescription = null,
                            tint = colors.skyBlue
                        )
                    },
                    onClick = {
                        closeThen { onSelectTransactionType(TransactionType.TRANSFER) }
                    },
                    modifier = Modifier.weight(1f)
                )
                QuickAddTile(
                    title = "Commission",
                    subtitle = "Income · sales, GP & payout",
                    accentColor = colors.lavender,
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.Calculate,
                            contentDescription = null,
                            tint = colors.lavender
                        )
                    },
                    onClick = {
                        closeThen { onOpenCommissionTools() }
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            SpatialCompactListRow(
                title = "Manage Categories",
                subtitle = "Customize income and expense categories",
                leadingBadgeColor = colors.aqua,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Category,
                        contentDescription = null,
                        tint = colors.aqua,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onClick = {
                    closeThen { onOpenCategories() }
                }
            )
        }
    }
}

@Composable
private fun QuickAddTile(
    title: String,
    subtitle: String,
    accentColor: Color,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SpatialTheme.colors
    val typography = SpatialTheme.typography

    SpatialGlassCard(
        modifier = modifier,
        tier = SpatialGlassTier.Standard,
        accentTint = accentColor,
        contentPadding = PaddingValues(14.dp),
        onClick = onClick,
        onClickLabel = title
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(accentColor.copy(alpha = if (colors.isDark) 0.22f else 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = title,
            style = typography.cardTitle,
            color = colors.primaryText
        )
        Text(
            text = subtitle,
            style = typography.caption,
            color = colors.secondaryText
        )
    }
}
