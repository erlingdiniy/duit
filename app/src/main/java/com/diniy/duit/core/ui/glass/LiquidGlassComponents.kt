package com.diniy.duit.core.ui.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
import com.diniy.duit.core.ui.components.CandyTone
import com.diniy.duit.core.ui.components.PrismaticNavItem
import com.diniy.duit.core.ui.components.rememberDuitHaptic
import com.diniy.duit.core.ui.components.rememberReducedMotionEnabled
import com.diniy.duit.core.ui.theme.SpatialTheme
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.tanh

/**
 * Phase 10A Prototype: One hero Liquid Glass card with Candy Bubble 2.0 warmth,
 * live refracted backdrop, diagonal specular rim, and touch-point light bloom.
 *
 * Reserve for 1 hero/featured card per screen — never apply to every list row.
 */
@Composable
fun LiquidGlassHeroCard(
    backdropState: LiquidGlassBackdropState?,
    tone: CandyTone,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = SpatialTheme.colors
    val reducedMotion = rememberReducedMotionEnabled()
    val haptic = rememberDuitHaptic()
    val spec = remember(colors, tone) { LiquidGlassPresets.heroCard(colors, tone) }

    var isPressed by remember { mutableStateOf(false) }
    var touchPos by remember { mutableStateOf(Offset.Zero) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed && !reducedMotion) 1.018f else 1f,
        animationSpec = if (isPressed) LiquidGlassSprings.Press else LiquidGlassSprings.Release,
        label = "lg_card_scale"
    )
    val touchAlpha by animateFloatAsState(
        targetValue = if (isPressed && !reducedMotion) 1f else 0f,
        animationSpec = if (isPressed) LiquidGlassSprings.Press else LiquidGlassSprings.Release,
        label = "lg_card_bloom"
    )

    val pressModifier = if (onClick != null) {
        Modifier.pointerInput(onClick) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                touchPos = down.position
                isPressed = true
                val up = waitForUpOrCancellation()
                isPressed = false
                if (up != null) {
                    haptic(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            }
        }
    } else {
        Modifier
    }

    LiquidGlassSurface(
        backdropState = backdropState,
        spec = spec,
        touchPosition = touchPos,
        touchAlpha = touchAlpha,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(pressModifier)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            content = content
        )
    }
}

/**
 * Phase 10A Prototype: Floating Liquid Glass Bottom Navigation Bar.
 *
 * Preserves Phase 9's exact 4 bottom tabs (`Home`, `Activity`, `Planning`, `Insights`) while
 * reproducing `zakisheriff/Liquid-Glass` `GlassNav` / `LensTrack` physics:
 * - Translucent refracting outer capsule over scrolling page content
 * - Active selection inflates into a refracting droplet lens on touch/drag (magnification +
 *   chromatic rim + overhang + velocity-driven jelly stretch), then settles into the nearest tab
 * - Full Reduce Motion fallback & TalkBack tab semantics
 */
@Composable
fun LiquidGlassFloatingNavBar(
    backdropState: LiquidGlassBackdropState?,
    items: List<PrismaticNavItem>,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit,
    modifier: Modifier = Modifier,
    previewLiftedLens: Boolean = false
) {
    if (items.isEmpty()) return

    val colors = SpatialTheme.colors
    val geometry = SpatialTheme.geometry
    val typography = SpatialTheme.typography
    val reducedMotion = rememberReducedMotionEnabled()
    val haptic = rememberDuitHaptic()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val barSpec = remember(colors) { LiquidGlassPresets.floatingNavBar(colors) }
    val lensSpec = remember(colors) { LiquidGlassPresets.floatingNavLens(colors) }

    val safeIndex = selectedIndex.coerceIn(0, items.lastIndex)
    val animatedSlot = remember { Animatable(safeIndex.toFloat()) }
    var isDragging by remember { mutableStateOf(false) }
    var dragHoverIndex by remember { mutableIntStateOf(safeIndex) }
    var jellyTarget by remember { mutableFloatStateOf(0f) }
    var rowHeightPx by remember { mutableIntStateOf(with(density) { 56.dp.roundToPx() }) }

    LaunchedEffect(safeIndex, isDragging, reducedMotion) {
        if (!isDragging) {
            jellyTarget = 0f
            if (reducedMotion) {
                animatedSlot.snapTo(safeIndex.toFloat())
            } else {
                animatedSlot.animateTo(
                    targetValue = safeIndex.toFloat(),
                    animationSpec = LiquidGlassSprings.Snap
                )
            }
        }
    }

    val isLifted = (isDragging || previewLiftedLens) && !reducedMotion
    val lift by animateFloatAsState(
        targetValue = if (isLifted) 1f else 0f,
        animationSpec = if (isLifted) LiquidGlassSprings.Lift else LiquidGlassSprings.Settle,
        label = "lg_lens_lift"
    )
    val jelly by animateFloatAsState(
        targetValue = if (reducedMotion) 0f else jellyTarget,
        animationSpec = LiquidGlassSprings.Jelly,
        label = "lg_lens_jelly"
    )
    val barSwell by animateFloatAsState(
        targetValue = if (isLifted) 1.016f else 1f,
        animationSpec = if (isLifted) LiquidGlassSprings.Press else LiquidGlassSprings.Release,
        label = "lg_bar_swell"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        LiquidGlassSurface(
            backdropState = backdropState,
            spec = barSpec,
            modifier = Modifier
                .widthIn(max = geometry.maxFloatingNavWidth)
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = barSwell
                    scaleY = 1f
                }
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                val totalWidthPx = with(density) { maxWidth.toPx() }
                val slotWidthPx = (totalWidthPx / items.size).coerceAtLeast(1f)
                val slotWidthDp = maxWidth / items.size

                val dragModifier = Modifier.pointerInput(items.size, slotWidthPx, safeIndex) {
                    detectHorizontalDragGestures(
                        onDragStart = { startOffset ->
                            val slot = (startOffset.x / slotWidthPx).toInt().coerceIn(0, items.lastIndex)
                            isDragging = true
                            dragHoverIndex = slot
                            haptic(HapticFeedbackType.TextHandleMove)
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            if (!isDragging) return@detectHorizontalDragGestures
                            change.consume()
                            val deltaFraction = dragAmount / slotWidthPx
                            val nextSlot = (animatedSlot.value + deltaFraction)
                                .coerceIn(0f, items.lastIndex.toFloat())
                            scope.launch { animatedSlot.snapTo(nextSlot) }
                            // Velocity-driven jelly stretch (capped via tanh like spring.ts createJelly)
                            jellyTarget = (0.14f * tanh(abs(dragAmount) / 28.0)).toFloat()
                            val nearest = nextSlot.roundToInt().coerceIn(0, items.lastIndex)
                            if (nearest != dragHoverIndex) {
                                dragHoverIndex = nearest
                                haptic(HapticFeedbackType.TextHandleMove)
                            }
                        },
                        onDragEnd = {
                            if (!isDragging) return@detectHorizontalDragGestures
                            isDragging = false
                            jellyTarget = 0f
                            val target = dragHoverIndex.coerceIn(0, items.lastIndex)
                            if (target != safeIndex) {
                                haptic(HapticFeedbackType.SegmentTick)
                                onSelectIndex(target)
                            } else {
                                scope.launch {
                                    animatedSlot.animateTo(
                                        targetValue = safeIndex.toFloat(),
                                        animationSpec = LiquidGlassSprings.Snap
                                    )
                                }
                            }
                        },
                        onDragCancel = {
                            isDragging = false
                            jellyTarget = 0f
                            scope.launch {
                                animatedSlot.animateTo(
                                    targetValue = safeIndex.toFloat(),
                                    animationSpec = LiquidGlassSprings.Snap
                                )
                            }
                        }
                    )
                }

                Box(modifier = Modifier.fillMaxWidth().then(dragModifier)) {
                    val indicatorHeightDp = with(density) { rowHeightPx.toDp() }
                    val lensScaleX = (1f + 0.06f * lift) * (1f + jelly)
                    val lensScaleY = (1f + 0.12f * lift) * (1f - jelly * 0.55f)

                    // Floating Liquid Lens indicator (inflates into droplet on lift/drag)
                    LiquidGlassSurface(
                        backdropState = backdropState,
                        spec = lensSpec,
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    x = (animatedSlot.value * slotWidthPx).roundToInt(),
                                    y = 0
                                )
                            }
                            .width(slotWidthDp)
                            .height(indicatorHeightDp)
                            .graphicsLayer {
                                scaleX = lensScaleX
                                scaleY = lensScaleY
                            }
                    ) {}

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onSizeChanged { size ->
                                if (size.height > 0) rowHeightPx = size.height
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val activeIdx = if (isDragging) dragHoverIndex else safeIndex
                        items.forEachIndexed { idx, item ->
                            val isSelected = idx == activeIdx
                            val fgColor by animateColorAsState(
                                targetValue = if (isSelected) colors.primaryAction else colors.secondaryText,
                                label = "lg_nav_fg"
                            )
                            val iconScale by animateFloatAsState(
                                targetValue = if (isSelected && !reducedMotion) (1.06f + 0.06f * lift) else 1f,
                                animationSpec = LiquidGlassSprings.Snap,
                                label = "lg_nav_icon_scale"
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 54.dp)
                                    .semantics(mergeDescendants = true) {
                                        contentDescription = item.contentDescription
                                        selected = isSelected
                                    }
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        role = Role.Tab
                                    ) {
                                        if (idx != safeIndex) {
                                            haptic(HapticFeedbackType.SegmentTick)
                                            onSelectIndex(idx)
                                        }
                                    }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    painter = painterResource(item.iconRes),
                                    contentDescription = null,
                                    tint = fgColor,
                                    modifier = Modifier
                                        .size(21.dp)
                                        .graphicsLayer {
                                            scaleX = iconScale
                                            scaleY = iconScale
                                        }
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    text = item.label,
                                    style = typography.caption,
                                    color = fgColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
