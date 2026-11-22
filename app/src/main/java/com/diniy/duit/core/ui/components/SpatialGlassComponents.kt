package com.diniy.duit.core.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.drawOutline
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
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
 * 1. [SpatialGlassSurface] — Core Candy Pop surface (Phase 6C; name kept for API stability).
 * Renders:
 * - Opaque candy fill (no glass sheen, no gradients) tuned for Light/Dark Mode
 * - Optional solid accent tint for coloured cards
 * - Soft grape-tinted depth shadow whose strength follows the "depth" (GlassIntensity) setting
 * - [sticker] = true: Sticker Pop outline + hard offset shadow that "sinks" on press
 * - Bouncy spring squash on press when interactive (disabled under Reduce Motion)
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
    sticker: Boolean = false,
    containerColor: Color? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = SpatialTheme.colors
    val geometry = SpatialTheme.geometry
    val motion = SpatialTheme.motion
    val isLiquidGlass = com.diniy.duit.core.ui.theme.LocalVisualTheme.current ==
        com.diniy.duit.domain.model.VisualTheme.LIQUID_GLASS
    val glassIntensity = com.diniy.duit.core.ui.theme.LocalGlassIntensity.current
    val effectiveSticker = sticker && !isLiquidGlass
    val reducedMotion = rememberReducedMotionEnabled()
    val performHaptic = rememberDuitHaptic()

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressedActive = isPressed && onClick != null && enabled

    val scale by animateFloatAsState(
        targetValue = motion.resolvePressScale(
            isPressed = pressedActive && !effectiveSticker,
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

    // Sticker press: the card slides into its own offset shadow (Candy Bubble only).
    val stickerShift by animateDpAsState(
        targetValue = if (effectiveSticker && pressedActive) geometry.stickerOffset - 1.dp else 0.dp,
        animationSpec = prismaticSpring(
            reducedMotion = reducedMotion,
            dampingRatio = motion.pressDampingRatio,
            stiffness = motion.pressStiffness
        ),
        label = "spatial_surface_sticker_shift"
    )

    val isLiquidElevated = isLiquidGlass && (tier == SpatialGlassTier.Elevated || tier == SpatialGlassTier.Floating)
    val isLiquidLow = glassIntensity == com.diniy.duit.domain.model.GlassIntensity.SUBTLE
    val isLiquidHigh = glassIntensity == com.diniy.duit.domain.model.GlassIntensity.RICH

    val baseFill = containerColor ?: when {
        isLiquidGlass && tier == SpatialGlassTier.Subtle -> colors.baseSurface
        isLiquidGlass && tier == SpatialGlassTier.Standard -> colors.elevatedSurface
        isLiquidGlass && isLiquidElevated -> when (glassIntensity) {
            com.diniy.duit.domain.model.GlassIntensity.SUBTLE -> colors.elevatedSurface
            com.diniy.duit.domain.model.GlassIntensity.BALANCED ->
                if (colors.isDark) Color(0xFF171A23).copy(alpha = 0.90f) else Color.White.copy(alpha = 0.88f)
            com.diniy.duit.domain.model.GlassIntensity.RICH ->
                if (colors.isDark) Color(0xFF161A25).copy(alpha = 0.80f) else Color.White.copy(alpha = 0.80f)
        }
        tier == SpatialGlassTier.Subtle -> colors.glassSurfaceSubtle
        tier == SpatialGlassTier.Standard -> colors.glassSurface
        tier == SpatialGlassTier.Elevated -> colors.glassSurfaceElevated
        else -> colors.floatingNavSurface
    }
    val tintAlpha = when {
        isLiquidGlass && selected -> if (colors.isDark) 0.16f else 0.10f
        isLiquidGlass -> if (colors.isDark) 0.06f else 0.04f
        selected -> if (colors.isDark) 0.30f else 0.22f
        else -> if (colors.isDark) 0.11f else 0.14f
    }
    val tinted = accentTint?.let { lerp(baseFill, it, tintAlpha) }
        ?: if (selected) colors.selectedStateOverlay.compositeOverSolid(baseFill) else baseFill
    val targetFill = when {
        !enabled -> tinted.copy(alpha = 0.6f)
        pressedActive -> colors.pressedStateOverlay.compositeOverSolid(tinted)
        else -> tinted
    }

    val animatedFill by animateColorAsState(
        targetValue = targetFill,
        animationSpec = prismaticTween(reducedMotion = reducedMotion, durationMillis = motion.durationFastMs),
        label = "spatial_surface_fill"
    )

    val baseElevation: Dp = when {
        effectiveSticker -> 0.dp // the hard offset shadow replaces soft elevation
        isLiquidGlass && !isLiquidElevated -> 0.dp
        isLiquidElevated -> when (glassIntensity) {
            com.diniy.duit.domain.model.GlassIntensity.SUBTLE -> if (colors.isDark) 0.dp else 1.dp
            com.diniy.duit.domain.model.GlassIntensity.BALANCED -> 5.dp
            com.diniy.duit.domain.model.GlassIntensity.RICH -> if (colors.isDark) 9.dp else 8.dp
        }
        tier == SpatialGlassTier.Subtle -> geometry.elevationNone
        tier == SpatialGlassTier.Standard -> if (colors.isDark) geometry.elevationCardDark else geometry.elevationCardLight
        tier == SpatialGlassTier.Elevated -> if (colors.isDark) geometry.elevationElevatedDark else geometry.elevationElevatedLight
        else -> if (colors.isDark) geometry.elevationFloatingDark else geometry.elevationFloatingLight
    }

    val animatedElevation by animateDpAsState(
        targetValue = if (pressedActive && !reducedMotion) (baseElevation / 2) else baseElevation,
        animationSpec = prismaticSpring(reducedMotion = reducedMotion),
        label = "spatial_surface_elevation"
    )

    val stickerInk = colors.sticker
    val stickerOffset = geometry.stickerOffset
    val liquidRimBrush = if (isLiquidElevated && !isLiquidLow) {
        val rimPeak = if (isLiquidHigh) (if (colors.isDark) 0.38f else 0.92f) else (if (colors.isDark) 0.22f else 0.72f)
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = rimPeak),
                if (colors.isDark) Color(0x22FFFFFF) else Color(0x160F172A),
                Color.White.copy(alpha = rimPeak * 0.28f)
            )
        )
    } else null
    val outline: BorderStroke? = when {
        effectiveSticker -> BorderStroke(geometry.stickerBorder, stickerInk)
        selected -> BorderStroke(if (isLiquidGlass) 1.5.dp else geometry.borderFocused, accentTint ?: colors.primaryAction)
        isLiquidElevated && liquidRimBrush != null -> BorderStroke(0.75.dp, liquidRimBrush)
        isLiquidGlass -> BorderStroke(0.75.dp, colors.divider)
        colors.isDark -> BorderStroke(geometry.borderHairline, colors.glassBorder)
        else -> null
    }

    val surfaceModifier = modifier
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .then(
            if (effectiveSticker) {
                Modifier.drawBehind {
                    // Public: the shadow stays put while the face slides onto it, so ink never peeks out top-left on press.
                    val o = (if (com.diniy.duit.BuildConfig.PUBLIC_EDITION) stickerOffset else stickerOffset - stickerShift).toPx()
                    val outlineShape = shape.createOutline(size, layoutDirection, this)
                    translate(left = o, top = o) { drawOutline(outlineShape, stickerInk) }
                }
            } else {
                Modifier
            }
        )
        .offset(x = stickerShift, y = stickerShift)
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
        .then(if (outline != null) Modifier.border(outline, shape) else Modifier)
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

    // Un-coloured Text/Icon inside a card must follow the theme ink, not default black.
    CompositionLocalProvider(LocalContentColor provides colors.primaryText) {
        Box(modifier = surfaceModifier, content = content)
    }
}

/** Composites a translucent overlay onto an opaque base, returning an opaque colour. */
internal fun Color.compositeOverSolid(base: Color): Color = lerp(base, this.copy(alpha = 1f), this.alpha)

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
    sticker: Boolean = false,
    containerColor: Color? = null,
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
        onClickLabel = onClickLabel,
        sticker = sticker,
        containerColor = containerColor
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

    val isLiquidGlass = com.diniy.duit.core.ui.theme.LocalVisualTheme.current ==
        com.diniy.duit.domain.model.VisualTheme.LIQUID_GLASS
    val shape = RoundedCornerShape(if (isLiquidGlass) 14.dp else geometry.radiusMd)
    val isSticker = !isLiquidGlass && variant == SpatialButtonVariant.Primary && isInteractive
    val lastClickAt = remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    // Public edition: soft Candy shadow (translucent ink, smaller lift, thinner tinted outline).
    val softSticker = com.diniy.duit.BuildConfig.PUBLIC_EDITION
    val stickerInk = if (softSticker) colors.sticker.copy(alpha = if (colors.isDark) 0.45f else 0.18f) else colors.sticker
    val stickerOffset = if (softSticker) 3.dp else geometry.stickerOffset
    val stickerBorder = if (softSticker) BorderStroke(1.5.dp, colors.sticker.copy(alpha = if (colors.isDark) 0.55f else 0.28f))
        else BorderStroke(geometry.stickerBorder, colors.sticker)
    val stickerShift by animateDpAsState(
        targetValue = if (isSticker && isPressed) stickerOffset - 1.dp else 0.dp,
        animationSpec = prismaticSpring(
            reducedMotion = reducedMotion,
            dampingRatio = motion.pressDampingRatio,
            stiffness = motion.pressStiffness
        ),
        label = "spatial_button_sticker_shift"
    )

    val (bgColor, fgColor, borderStroke) = if (isLiquidGlass) {
        when (variant) {
            SpatialButtonVariant.Primary -> Triple(
                if (isInteractive) colors.primaryAction else colors.baseSurface,
                if (isInteractive) colors.onPrimaryAction else colors.disabledText,
                null
            )
            SpatialButtonVariant.SecondaryGlass -> Triple(
                if (isInteractive) colors.elevatedSurface else colors.baseSurface,
                if (isInteractive) colors.primaryAction else colors.disabledText,
                BorderStroke(0.75.dp, if (isInteractive) colors.divider else colors.divider.copy(alpha = 0.5f))
            )
            SpatialButtonVariant.TonalAccent -> Triple(
                if (isInteractive) colors.primaryAction.copy(alpha = if (colors.isDark) 0.18f else 0.10f) else colors.baseSurface,
                if (isInteractive) colors.primaryAction else colors.disabledText,
                null
            )
            SpatialButtonVariant.Destructive -> Triple(
                if (isInteractive) colors.errorContainer else colors.baseSurface,
                if (isInteractive) colors.onErrorContainer else colors.disabledText,
                null
            )
            SpatialButtonVariant.Ghost -> Triple(
                if (isPressed && isInteractive) colors.pressedStateOverlay else Color.Transparent,
                if (isInteractive) colors.primaryAction else colors.disabledText,
                null
            )
        }
    } else {
        when (variant) {
            // Candy Pop: Primary is the Sticker Pop action (outline + hard offset shadow).
            SpatialButtonVariant.Primary -> Triple(
                if (isInteractive) colors.primaryAction else colors.baseSurface,
                if (isInteractive) colors.onPrimaryAction else colors.disabledText,
                if (isInteractive) stickerBorder else null
            )
            SpatialButtonVariant.SecondaryGlass -> Triple(
                if (isInteractive) colors.glassSurfaceElevated else colors.glassSurfaceSubtle,
                if (isInteractive) colors.primaryAction else colors.disabledText,
                BorderStroke(geometry.borderFocused, colors.primaryAction.copy(alpha = if (isInteractive) 0.35f else 0.12f))
            )
            SpatialButtonVariant.TonalAccent -> Triple(
                if (isInteractive) colors.selectedStateOverlay.compositeOverSolid(colors.glassSurfaceElevated) else colors.glassSurfaceSubtle,
                if (isInteractive) colors.primaryAction else colors.disabledText,
                null
            )
            SpatialButtonVariant.Destructive -> Triple(
                if (isInteractive) colors.errorContainer else colors.glassSurfaceSubtle,
                if (isInteractive) colors.onErrorContainer else colors.disabledText,
                null
            )
            SpatialButtonVariant.Ghost -> Triple(
                if (isPressed && isInteractive) colors.pressedStateOverlay else Color.Transparent,
                if (isInteractive) colors.primaryAction else colors.disabledText,
                null
            )
        }
    }

    Box(
        modifier = modifier
            .heightIn(min = geometry.minTouchTarget)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (isSticker) {
                    Modifier.drawBehind {
                        // Public: the shadow stays put while the face slides onto it (no top-left ink flash on press).
                        val o = (if (softSticker) stickerOffset else stickerOffset - stickerShift).toPx()
                        val outline = shape.createOutline(size, layoutDirection, this)
                        translate(left = o, top = o) { drawOutline(outline, stickerInk) }
                    }
                } else {
                    Modifier
                }
            )
            .offset(x = stickerShift, y = stickerShift)
            .clip(shape)
            .background(bgColor)
            .then(if (borderStroke != null) Modifier.border(borderStroke, shape) else Modifier)
            .clickable(
                enabled = isInteractive,
                role = Role.Button,
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    // Ignore rapid repeat taps so a Save can't fire twice.
                    val now = android.os.SystemClock.uptimeMillis()
                    if (now - lastClickAt.longValue >= 600L) {
                        lastClickAt.longValue = now
                        performHaptic(
                            if (variant == SpatialButtonVariant.Primary) HapticFeedbackType.Confirm
                            else HapticFeedbackType.TextHandleMove
                        )
                        onClick()
                    }
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
    val isLiquidGlass = com.diniy.duit.core.ui.theme.LocalVisualTheme.current ==
        com.diniy.duit.domain.model.VisualTheme.LIQUID_GLASS
    val iconTint = when {
        !enabled -> colors.disabledText
        selected -> accentTint ?: colors.primaryAction
        else -> colors.primaryText
    }

    if (isLiquidGlass && enabled) {
        com.diniy.duit.core.ui.glass.LiquidGlassOrbButton(
            icon = icon,
            contentDescription = contentDescription,
            onClick = onClick,
            modifier = modifier,
            size = geometry.iconButtonSize
        )
        return
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
    val isLiquidGlass = com.diniy.duit.core.ui.theme.LocalVisualTheme.current ==
        com.diniy.duit.domain.model.VisualTheme.LIQUID_GLASS
    val shape = RoundedCornerShape(if (isLiquidGlass) 14.dp else geometry.radiusPill)

    // Candy Pop: selected pills become grape "stickers"; Liquid Glass: crisp solid active pill / quiet hairline idle pill.
    val resolvedContainer = containerColor ?: when {
        isLiquidGlass && selected -> colors.primaryAction
        isLiquidGlass -> colors.elevatedSurface
        selected -> accentColor ?: colors.primaryAction
        else -> colors.glassSurfaceElevated
    }
    val resolvedContent = contentColor ?: when {
        isLiquidGlass && selected -> colors.onPrimaryAction
        isLiquidGlass -> colors.secondaryText
        selected && accentColor == null -> colors.onPrimaryAction
        selected -> com.diniy.duit.core.ui.theme.PrismaticPalette.DeepTeal // accents are bright candy in both modes
        else -> colors.secondaryText
    }
    val pillBorder = when {
        isLiquidGlass && selected -> null
        isLiquidGlass -> BorderStroke(0.75.dp, colors.divider)
        selected -> BorderStroke(2.dp, colors.sticker)
        else -> BorderStroke(geometry.borderHairline, colors.glassBorder)
    }

    val pillModifier = modifier
        // Public edition: tappable pills meet the 48dp minimum touch target.
        .heightIn(min = if (onClick != null) (if (com.diniy.duit.BuildConfig.PUBLIC_EDITION) 48.dp else 44.dp) else geometry.pillHeight)
        .then(if (onClick != null) Modifier.minimumInteractiveComponentSize() else Modifier)
        .clip(shape)
        .background(resolvedContainer)
        .then(if (pillBorder != null) Modifier.border(pillBorder, shape) else Modifier)
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
    val publicSpacing = com.diniy.duit.BuildConfig.PUBLIC_EDITION
    val isLiquidGlass = com.diniy.duit.core.ui.theme.LocalVisualTheme.current ==
        com.diniy.duit.domain.model.VisualTheme.LIQUID_GLASS

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = if (isLiquidGlass) 28.dp else 36.dp, topEnd = if (isLiquidGlass) 28.dp else 36.dp),
        containerColor = colors.bottomSheetSurface,
        contentColor = colors.primaryText,
        scrimColor = colors.scrim,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = if (publicSpacing) 10.dp else 12.dp, bottom = if (publicSpacing) 14.dp else 10.dp)
                    .size(width = if (isLiquidGlass) 38.dp else 48.dp, height = if (isLiquidGlass) 4.dp else 6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (isLiquidGlass) colors.secondaryText.copy(alpha = 0.28f) else colors.coral.copy(alpha = 0.7f))
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
                .padding(top = 4.dp, bottom = 28.dp)
                // Conditional fields (transfer target, cycle label…) grow in instead of jumping.
                .animateContentSize(animationSpec = prismaticSpring(rememberReducedMotionEnabled(), 0.85f, androidx.compose.animation.core.Spring.StiffnessMediumLow))
        ) {
            Text(
                text = title,
                style = typography.screenHeading.copy(fontSize = 24.sp, lineHeight = 30.sp),
                color = colors.primaryText,
                modifier = Modifier.semantics { heading() }
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(if (publicSpacing) 6.dp else 4.dp))
                Text(
                    text = subtitle,
                    style = typography.supportingInfo,
                    color = colors.secondaryText
                )
            }
            Spacer(Modifier.height(if (publicSpacing) 20.dp else 16.dp))
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
            .background(colors.floatingNavSurface)
            .border(BorderStroke(geometry.borderHairline, colors.glassBorder), shape)
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
