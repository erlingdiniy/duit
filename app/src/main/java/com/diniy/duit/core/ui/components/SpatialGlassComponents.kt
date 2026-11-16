package com.diniy.duit.core.ui.components

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.diniy.duit.core.ui.theme.SpatialTheme

/**
 * Spatial depth tiers inspired by visionOS layered glass panels.
 */
enum class SpatialGlassTier {
    Subtle,
    Standard,
    Elevated,
    Floating
}

enum class SpatialButtonVariant {
    Primary,
    SecondaryGlass,
    TonalAccent,
    Ghost,
    Destructive
}

/**
 * Hardware/API capability helper for spatial glass rendering (Phase 6A Section 5).
 *
 * Note on Android Compose blur:
 * `Modifier.blur()` (API 31+ RenderEffect) blurs the *content drawn inside that modifier*,
 * NOT the backdrop behind a floating panel. Applying `Modifier.blur()` to a card blurs its own
 * text, while full-screen blur passes hurt scrolling performance.
 * Therefore, duit. uses multi-layer optical frost (translucent base + top inner specular luminance
 * + directional rim highlight + soft depth shadow) across API 29–35 for 60/120fps readability.
 */
object SpatialBlurCapabilities {
    val isRenderEffectSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    fun supportsHardwareRenderEffect(sdkInt: Int): Boolean =
        sdkInt >= Build.VERSION_CODES.S
}

/**
 * 1. [SpatialGlassSurface] — Core translucent floating glass container.
 * Renders:
 * - Translucent frosted fill tuned for Light/Dark Mode and [GlassIntensity]
 * - Subtle top-edge inner specular sheen (visionOS overhead illumination)
 * - Directional hairline border (bright top-left rim fading to subtle bottom-right edge)
 * - Soft ambient & spot elevation shadows
 * - Spring-based press feedback when interactive
 */
@Composable
fun SpatialGlassSurface(
    modifier: Modifier = Modifier,
    tier: SpatialGlassTier = SpatialGlassTier.Standard,
    shape: Shape = RoundedCornerShape(SpatialTheme.geometry.radiusLg),
    accentTint: Color? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    role: Role = Role.Button,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = SpatialTheme.colors
    val geometry = SpatialTheme.geometry
    val motion = SpatialTheme.motion
    val reducedMotion = rememberReducedMotionEnabled()
    val performHaptic = rememberDuitHaptic()

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = motion.resolvePressScale(
            isPressed = isPressed && onClick != null,
            enabled = enabled,
            reduceMotion = reducedMotion,
            targetScale = motion.pressedScaleCard
        ),
        animationSpec = prismaticSpring(
            reducedMotion = reducedMotion,
            dampingRatio = motion.pressDampingRatio,
            stiffness = motion.pressStiffness
        ),
        label = "spatial_surface_scale"
    )

    val baseFill = when (tier) {
        SpatialGlassTier.Subtle -> colors.glassSurfaceSubtle
        SpatialGlassTier.Standard -> colors.glassSurface
        SpatialGlassTier.Elevated -> colors.glassSurfaceElevated
        SpatialGlassTier.Floating -> colors.floatingNavSurface
    }

    val targetFill = when {
        !enabled -> baseFill.copy(alpha = (baseFill.alpha * 0.65f).coerceAtLeast(0.40f))
        isPressed && onClick != null -> baseFill.copy(alpha = (baseFill.alpha + 0.07f).coerceAtMost(0.97f))
        selected -> baseFill.copy(alpha = (baseFill.alpha + 0.05f).coerceAtMost(0.96f))
        else -> baseFill
    }

    val animatedFill by animateColorAsState(
        targetValue = targetFill,
        animationSpec = prismaticTween(reducedMotion = reducedMotion, durationMillis = motion.durationFastMs),
        label = "spatial_surface_fill"
    )

    val baseElevation: Dp = when (tier) {
        SpatialGlassTier.Subtle -> geometry.elevationNone
        SpatialGlassTier.Standard -> if (colors.isDark) geometry.elevationCardDark else geometry.elevationCardLight
        SpatialGlassTier.Elevated -> if (colors.isDark) geometry.elevationElevatedDark else geometry.elevationElevatedLight
        SpatialGlassTier.Floating -> if (colors.isDark) geometry.elevationFloatingDark else geometry.elevationFloatingLight
    }

    val animatedElevation by animateDpAsState(
        targetValue = if (isPressed && onClick != null && !reducedMotion) {
            (baseElevation - 1.dp).coerceAtLeast(0.dp)
        } else {
            baseElevation
        },
        animationSpec = prismaticSpring(reducedMotion = reducedMotion),
        label = "spatial_surface_elevation"
    )

    val rimHighlightColor = if (selected) {
        (accentTint ?: colors.lavender).copy(alpha = if (colors.isDark) 0.55f else 0.45f)
    } else {
        colors.glassHighlight
    }

    val borderBrush = remember(rimHighlightColor, colors.glassBorder) {
        Brush.linearGradient(
            colors = listOf(rimHighlightColor, colors.glassBorder)
        )
    }

    val innerGlowColor = colors.glassInnerGlow
    val tintColor = accentTint?.copy(alpha = if (colors.isDark) 0.11f else 0.08f)

    val surfaceModifier = modifier
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .then(
            if (animatedElevation > 0.dp) {
                Modifier.shadow(
                    elevation = animatedElevation,
                    shape = shape,
                    ambientColor = colors.glassShadowAmbient,
                    spotColor = colors.glassShadowSpot
                )
            } else {
                Modifier
            }
        )
        .clip(shape)
        .background(animatedFill)
        .drawWithCache {
            val sheenHeight = (size.height * 0.42f).coerceAtLeast(1f)
            val topSheenBrush = Brush.verticalGradient(
                colors = listOf(innerGlowColor, Color.Transparent),
                startY = 0f,
                endY = sheenHeight
            )
            val accentBrush = tintColor?.let {
                Brush.linearGradient(
                    colors = listOf(it, Color.Transparent),
                    start = Offset.Zero,
                    end = Offset(size.width * 0.85f, size.height * 0.85f)
                )
            }
            onDrawBehind {
                drawRect(brush = topSheenBrush)
                if (accentBrush != null) {
                    drawRect(brush = accentBrush)
                }
            }
        }
        .border(
            BorderStroke(
                width = if (selected) geometry.borderFocused else geometry.borderStandard,
                brush = borderBrush
            ),
            shape = shape
        )
        .then(
            if (onClick != null) {
                Modifier
                    .defaultMinSize(
                        minWidth = geometry.minTouchTarget,
                        minHeight = geometry.minTouchTarget
                    )
                    .clickable(
                        enabled = enabled,
                        onClickLabel = onClickLabel,
                        role = role,
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = {
                            performHaptic(HapticFeedbackType.TextHandleMove)
                            onClick()
                        }
                    )
            } else {
                Modifier
            }
        )

    Box(modifier = surfaceModifier, content = content)
}

/**
 * 2. [SpatialGlassCard] — Structured content card built on [SpatialGlassSurface].
 */
@Composable
fun SpatialGlassCard(
    modifier: Modifier = Modifier,
    tier: SpatialGlassTier = SpatialGlassTier.Standard,
    accentTint: Color? = null,
    shape: Shape = RoundedCornerShape(SpatialTheme.geometry.radiusLg),
    contentPadding: PaddingValues = SpatialTheme.spacing.cardPaddingStandard,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    SpatialGlassSurface(
        modifier = modifier,
        tier = tier,
        shape = shape,
        accentTint = accentTint,
        selected = selected,
        enabled = enabled,
        onClick = onClick,
        onClickLabel = onClickLabel
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

/**
 * 3. [SpatialGlassButton] — Accessible, spring-responsive button supporting Primary,
 * SecondaryGlass, TonalAccent, Ghost, and Destructive variants.
 */
@Composable
fun SpatialGlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: SpatialButtonVariant = SpatialButtonVariant.Primary,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    val colors = SpatialTheme.colors
    val geometry = SpatialTheme.geometry
    val motion = SpatialTheme.motion
    val typography = SpatialTheme.typography
    val reducedMotion = rememberReducedMotionEnabled()
    val performHaptic = rememberDuitHaptic()

    val isInteractive = enabled && !isLoading
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = motion.resolvePressScale(
            isPressed = isPressed,
            enabled = isInteractive,
            reduceMotion = reducedMotion,
            targetScale = motion.pressedScaleButton
        ),
        animationSpec = prismaticSpring(
            reducedMotion = reducedMotion,
            dampingRatio = motion.pressDampingRatio,
            stiffness = motion.pressStiffness
        ),
        label = "spatial_button_scale"
    )

    val shape = RoundedCornerShape(geometry.radiusMd)

    val (bgColor, fgColor, borderStroke) = when (variant) {
        SpatialButtonVariant.Primary -> Triple(
            if (isInteractive) colors.primaryAction else colors.baseSurface,
            if (isInteractive) colors.onPrimaryAction else colors.disabledText,
            BorderStroke(geometry.borderHairline, colors.glassHighlight.copy(alpha = 0.32f))
        )
        SpatialButtonVariant.SecondaryGlass -> Triple(
            if (isInteractive) colors.glassSurfaceElevated else colors.glassSurfaceSubtle,
            if (isInteractive) colors.primaryText else colors.disabledText,
            BorderStroke(
                geometry.borderStandard,
                Brush.linearGradient(listOf(colors.glassHighlight, colors.glassBorder))
            )
        )
        SpatialButtonVariant.TonalAccent -> Triple(
            if (isInteractive) colors.selectedStateOverlay else colors.glassSurfaceSubtle,
            if (isInteractive) colors.primaryText else colors.disabledText,
            BorderStroke(geometry.borderHairline, colors.lavender.copy(alpha = 0.35f))
        )
        SpatialButtonVariant.Destructive -> Triple(
            if (isInteractive) colors.errorContainer else colors.glassSurfaceSubtle,
            if (isInteractive) colors.onErrorContainer else colors.disabledText,
            BorderStroke(geometry.borderStandard, colors.error.copy(alpha = 0.38f))
        )
        SpatialButtonVariant.Ghost -> Triple(
            if (isPressed && isInteractive) colors.pressedStateOverlay else Color.Transparent,
            if (isInteractive) colors.primaryAction else colors.disabledText,
            null
        )
    }

    Box(
        modifier = modifier
            .heightIn(min = geometry.minTouchTarget)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(bgColor)
            .then(if (borderStroke != null) Modifier.border(borderStroke, shape) else Modifier)
            .clickable(
                enabled = isInteractive,
                role = Role.Button,
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    performHaptic(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            )
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {},
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides fgColor) {
            AnimatedContent(
                targetState = isLoading,
                label = "spatial_button_loading"
            ) { loading ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = fgColor,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(10.dp))
                    } else if (leadingIcon != null) {
                        leadingIcon()
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = text,
                        style = typography.buttonLabel,
                        color = fgColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * 4. [SpatialGlassIconButton] — Floating circular or rounded glass icon button
 * with mandatory TalkBack [contentDescription] and 48dp minimum touch target.
 */
@Composable
fun SpatialGlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    accentTint: Color? = null,
    shape: Shape = CircleShape
) {
    val colors = SpatialTheme.colors
    val geometry = SpatialTheme.geometry
    val iconTint = when {
        !enabled -> colors.disabledText
        selected -> accentTint ?: colors.primaryAction
        else -> colors.primaryText
    }

    SpatialGlassSurface(
        modifier = modifier.size(geometry.iconButtonSize),
        tier = if (selected) SpatialGlassTier.Elevated else SpatialGlassTier.Standard,
        shape = shape,
        accentTint = if (selected) (accentTint ?: colors.lavender) else null,
        selected = selected,
        enabled = enabled,
        onClick = onClick,
        onClickLabel = contentDescription,
        role = Role.Button
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .semantics { this.contentDescription = contentDescription },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * 5. [SpatialGlassPill] — Rounded capsule for status tags, filter chips, or quick toggles.
 */
@Composable
fun SpatialGlassPill(
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    accentColor: Color? = null,
    containerColor: Color? = null,
    contentColor: Color? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val colors = SpatialTheme.colors
    val geometry = SpatialTheme.geometry
    val typography = SpatialTheme.typography
    val shape = RoundedCornerShape(geometry.radiusPill)

    val resolvedContainer = containerColor ?: when {
        selected -> (accentColor ?: colors.lavender).copy(alpha = if (colors.isDark) 0.26f else 0.18f)
        else -> colors.glassSurfaceSubtle
    }
    val resolvedContent = contentColor ?: when {
        selected -> colors.primaryText
        else -> colors.secondaryText
    }
    val borderBrush = Brush.linearGradient(
        colors = listOf(
            if (selected) (accentColor ?: colors.lavender).copy(alpha = 0.65f) else colors.glassHighlight,
            colors.glassBorder
        )
    )

    val pillModifier = modifier
        .heightIn(min = if (onClick != null) 38.dp else geometry.pillHeight)
        .clip(shape)
        .background(resolvedContainer)
        .border(BorderStroke(geometry.borderHairline, borderBrush), shape)
        .then(
            if (onClick != null) {
                Modifier
                    .semantics { this.selected = selected }
                    .clickable(
                        role = Role.Tab,
                        onClick = onClick
                    )
            } else {
                Modifier
            }
        )
        .padding(horizontal = 12.dp, vertical = 6.dp)

    Row(
        modifier = pillModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (leadingIcon != null) {
            leadingIcon()
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = label,
            style = typography.caption,
            color = resolvedContent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 6. [SpatialGlassSection] — Organized section block with header and optional trailing action
 * that avoids deep card-in-card nesting.
 */
@Composable
fun SpatialGlassSection(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    wrapInGlassCard: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = SpatialTheme.colors
    val spacing = SpatialTheme.spacing
    val typography = SpatialTheme.typography

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = typography.sectionHeading,
                    color = colors.primaryText,
                    modifier = Modifier.semantics { heading() }
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = typography.supportingInfo,
                        color = colors.secondaryText
                    )
                }
            }
            if (actionLabel != null && onActionClick != null) {
                Spacer(Modifier.width(spacing.sm))
                SpatialGlassPill(
                    label = actionLabel,
                    selected = false,
                    onClick = onActionClick
                )
            }
        }

        if (wrapInGlassCard) {
            SpatialGlassCard(
                modifier = Modifier.fillMaxWidth(),
                tier = SpatialGlassTier.Standard,
                content = content
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
                content = content
            )
        }
    }
}

/**
 * 7. [SpatialGlassBottomSheetSurface] — VisionOS-inspired modal bottom sheet surface
 * with proper IME and navigation-bar inset ordering before `verticalScroll`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpatialGlassBottomSheetSurface(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = SpatialTheme.colors
    val geometry = SpatialTheme.geometry
    val typography = SpatialTheme.typography

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = geometry.radiusXl, topEnd = geometry.radiusXl),
        containerColor = colors.bottomSheetSurface,
        contentColor = colors.primaryText,
        scrimColor = colors.scrim,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 6.dp)
                    .size(width = 42.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.secondaryText.copy(alpha = 0.38f))
            )
        },
        modifier = modifier.semantics { paneTitle = title }
    ) {
        // Critical inset ordering: navigationBarsPadding & imePadding BEFORE verticalScroll
        // so the scroll viewport shrinks cleanly when the software keyboard opens.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 24.dp)
        ) {
            Text(
                text = title,
                style = typography.cardBalance,
                color = colors.primaryText,
                modifier = Modifier.semantics { heading() }
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = typography.supportingInfo,
                    color = colors.secondaryText
                )
            }
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

/**
 * 8. [SpatialGlassNavigationSurface] — Reusable floating capsule container for bottom navigation
 * or spatial toolbars, featuring a luminous border and soft floating shadow.
 */
@Composable
fun SpatialGlassNavigationSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(SpatialTheme.geometry.radiusFloatingNav),
    contentPadding: PaddingValues = PaddingValues(6.dp),
    content: @Composable RowScope.() -> Unit
) {
    val colors = SpatialTheme.colors
    val geometry = SpatialTheme.geometry

    Box(
        modifier = modifier
            .widthIn(max = geometry.maxFloatingNavWidth)
            .fillMaxWidth()
            .shadow(
                elevation = if (colors.isDark) geometry.elevationFloatingDark else geometry.elevationFloatingLight,
                shape = shape,
                ambientColor = colors.glassShadowAmbient,
                spotColor = colors.glassShadowSpot
            )
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        colors.floatingNavSurface.copy(alpha = (colors.floatingNavSurface.alpha + 0.04f).coerceAtMost(0.98f)),
                        colors.floatingNavSurface
                    )
                )
            )
            .border(
                BorderStroke(
                    width = geometry.borderStandard,
                    brush = Brush.linearGradient(
                        colors = listOf(colors.glassHighlight, colors.glassBorder)
                    )
                ),
                shape = shape
            )
            .padding(contentPadding)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
            content = content
        )
    }
}
