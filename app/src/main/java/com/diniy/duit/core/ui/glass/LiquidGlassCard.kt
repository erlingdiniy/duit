package com.diniy.duit.core.ui.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.diniy.duit.core.ui.components.prismaticTween
import com.diniy.duit.core.ui.components.rememberDuitHaptic
import com.diniy.duit.core.ui.components.rememberReducedMotionEnabled
import com.diniy.duit.core.ui.theme.LocalGlassIntensity
import com.diniy.duit.core.ui.theme.SpatialColorTokens
import com.diniy.duit.core.ui.theme.SpatialTheme
import com.diniy.duit.domain.model.GlassIntensity

/**
 * Optical token specification for Liquid Glass cards and interactive panels across all 3
 * [GlassIntensity] levels (Phase 10E):
 * - LOW ([GlassIntensity.SUBTLE]): opaque solid surface, flat hairline border, zero sheen or dispersion.
 * - MID ([GlassIntensity.BALANCED]): soft frosted translucency, gentle top highlight, delicate specular rim.
 * - HIGH ([GlassIntensity.RICH]): full Liquid Glass optical depth — layered specular catch-light,
 *   internal bottom reflection, subtle prismatic RGB bevel glint, and responsive spring press.
 */
@Immutable
data class LiquidGlassPanelSpec(
    val fillColor: Color,
    val topSheenAlpha: Float,
    val bottomReflectionAlpha: Float,
    val rimHighlightAlpha: Float,
    val chromaticGlintAlpha: Float,
    val borderColor: Color,
    val borderWidth: Dp,
    val shadowElevation: Dp,
    val shadowColor: Color,
    val pressScale: Float,
    val touchBloomAlpha: Float,
    val usesLiquidSpring: Boolean,
    val isDark: Boolean
)

object LiquidGlassPanelPresets {

    fun card(
        colors: SpatialColorTokens,
        intensity: GlassIntensity,
        accentTint: Color? = null,
        elevated: Boolean = false
    ): LiquidGlassPanelSpec {
        val base = if (elevated) colors.glassSurfaceElevated else colors.glassSurface
        val tintedBase = if (accentTint != null) {
            val tintMix = when (intensity) {
                GlassIntensity.SUBTLE -> if (colors.isDark) 0.14f else 0.10f
                GlassIntensity.BALANCED -> if (colors.isDark) 0.16f else 0.11f
                GlassIntensity.RICH -> if (colors.isDark) 0.18f else 0.12f
            }
            lerp(base, accentTint, tintMix)
        } else {
            base
        }

        return when (intensity) {
            GlassIntensity.SUBTLE -> LiquidGlassPanelSpec(
                fillColor = tintedBase.copy(alpha = 1f),
                topSheenAlpha = 0f,
                bottomReflectionAlpha = 0f,
                rimHighlightAlpha = 0f,
                chromaticGlintAlpha = 0f,
                borderColor = if (colors.isDark) Color(0x26FFFFFF) else Color(0x1A0F172A),
                borderWidth = 1.dp,
                shadowElevation = if (colors.isDark) 0.dp else 2.dp,
                shadowColor = if (colors.isDark) Color.Transparent else Color(0x100F172A),
                pressScale = 0.985f,
                touchBloomAlpha = 0f,
                usesLiquidSpring = false,
                isDark = colors.isDark
            )

            GlassIntensity.BALANCED -> LiquidGlassPanelSpec(
                fillColor = tintedBase.copy(alpha = if (colors.isDark) 0.86f else 0.92f),
                topSheenAlpha = if (colors.isDark) 0.08f else 0.55f,
                bottomReflectionAlpha = if (colors.isDark) 0.04f else 0.03f,
                rimHighlightAlpha = if (colors.isDark) 0.24f else 0.85f,
                chromaticGlintAlpha = 0f,
                borderColor = if (colors.isDark) Color(0x1EFFFFFF) else Color(0x140F172A),
                borderWidth = 0.85.dp,
                shadowElevation = if (colors.isDark) 5.dp else 5.dp,
                shadowColor = if (colors.isDark) Color.Black.copy(alpha = 0.40f) else Color(0x140F172A),
                pressScale = 0.98f,
                touchBloomAlpha = 0.08f,
                usesLiquidSpring = true,
                isDark = colors.isDark
            )

            GlassIntensity.RICH -> LiquidGlassPanelSpec(
                fillColor = tintedBase.copy(alpha = if (colors.isDark) 0.78f else 0.86f),
                topSheenAlpha = if (colors.isDark) 0.16f else 0.78f,
                bottomReflectionAlpha = if (colors.isDark) 0.22f else 0.08f,
                rimHighlightAlpha = if (colors.isDark) 0.44f else 0.96f,
                chromaticGlintAlpha = 0f,
                borderColor = if (colors.isDark) Color(0x28FFFFFF) else Color(0x180F172A),
                borderWidth = 1.dp,
                shadowElevation = if (colors.isDark) 10.dp else 8.dp,
                shadowColor = if (colors.isDark) Color.Black.copy(alpha = 0.52f) else Color(0x1A0F172A),
                pressScale = 0.975f,
                touchBloomAlpha = 0.18f,
                usesLiquidSpring = true,
                isDark = colors.isDark
            )
        }
    }

    fun orbButton(
        colors: SpatialColorTokens,
        intensity: GlassIntensity
    ): LiquidGlassPanelSpec = when (intensity) {
        GlassIntensity.SUBTLE -> LiquidGlassPanelSpec(
            fillColor = colors.floatingNavSurface.copy(alpha = 1f),
            topSheenAlpha = 0f,
            bottomReflectionAlpha = 0f,
            rimHighlightAlpha = 0f,
            chromaticGlintAlpha = 0f,
            borderColor = if (colors.isDark) Color(0x28FFFFFF) else Color(0x1E0F172A),
            borderWidth = 1.dp,
            shadowElevation = if (colors.isDark) 0.dp else 3.dp,
            shadowColor = if (colors.isDark) Color.Transparent else Color(0x120F172A),
            pressScale = 0.95f,
            touchBloomAlpha = 0f,
            usesLiquidSpring = false,
            isDark = colors.isDark
        )

        GlassIntensity.BALANCED -> LiquidGlassPanelSpec(
            fillColor = if (colors.isDark) Color(0xFF1D2230).copy(alpha = 0.84f) else Color(0xFFF8FAFF).copy(alpha = 0.88f),
            topSheenAlpha = if (colors.isDark) 0.12f else 0.65f,
            bottomReflectionAlpha = if (colors.isDark) 0.08f else 0.04f,
            rimHighlightAlpha = if (colors.isDark) 0.30f else 0.88f,
            chromaticGlintAlpha = 0f,
            borderColor = if (colors.isDark) Color(0x22FFFFFF) else Color(0x140F172A),
            borderWidth = 0.9.dp,
            shadowElevation = if (colors.isDark) 6.dp else 6.dp,
            shadowColor = if (colors.isDark) Color.Black.copy(alpha = 0.45f) else Color(0x160F172A),
            pressScale = 1.04f,
            touchBloomAlpha = 0.12f,
            usesLiquidSpring = true,
            isDark = colors.isDark
        )

        GlassIntensity.RICH -> LiquidGlassPanelSpec(
            fillColor = if (colors.isDark) Color(0xFF181E2B).copy(alpha = 0.46f) else Color.White.copy(alpha = 0.56f),
            topSheenAlpha = if (colors.isDark) 0.16f else 0.72f,
            bottomReflectionAlpha = if (colors.isDark) 0.14f else 0.05f,
            rimHighlightAlpha = if (colors.isDark) 0.42f else 0.92f,
            chromaticGlintAlpha = 0f,
            borderColor = if (colors.isDark) Color(0x22FFFFFF) else Color(0x120F172A),
            borderWidth = 0.65.dp,
            shadowElevation = if (colors.isDark) 6.dp else 6.dp,
            shadowColor = if (colors.isDark) Color.Black.copy(alpha = 0.38f) else Color(0x140F172A),
            pressScale = 1.07f,
            touchBloomAlpha = 0.22f,
            usesLiquidSpring = true,
            isDark = colors.isDark
        )
    }

    /**
     * Miniature raised convex chromatic glass lens spec for the hero `RM` medallion in HIGH mode.
     */
    fun heroMedallionLens(colors: SpatialColorTokens): LiquidGlassSurfaceSpec = LiquidGlassSurfaceSpec(
        kind = LiquidGlassKind.Lens,
        cornerRadius = 29.dp,
        bezel = 9.dp,
        thickness = 24.dp,
        blurRadius = 0.dp,
        ior = 1.54f,
        zoom = 1.12f,
        dispersion = 1.05f,
        saturation = 1.45f,
        tintColor = if (colors.isDark) Color(0xFF0A2447) else Color.White,
        tintAlpha = if (colors.isDark) 0.24f else 0.16f,
        rimColor = Color.White,
        rimAlpha = 0.92f,
        borderColor = Color(0x38FFFFFF),
        shadowColor = Color.Black.copy(alpha = if (colors.isDark) 0.48f else 0.24f),
        shadowElevation = 8.dp,
        isDark = colors.isDark
    )
}

/**
 * Reusable Liquid Glass card/panel container that scales optically with [LocalGlassIntensity]:
 * - LOW: crisp solid surface (zero blur, zero specular rim, zero chromatic glint).
 * - MID: soft frosted glass panel with subtle top sheen & hairline specular rim.
 * - HIGH: luminous Liquid Glass surface with diagonal catch-light, subtle bottom TIR depth,
 *   controlled prismatic RGB edge dispersion glint, touch bloom, and spring press physics.
 */
@Composable
fun LiquidGlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(26.dp),
    accentTint: Color? = null,
    elevated: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = SpatialTheme.colors
    val intensity = LocalGlassIntensity.current
    val spec = remember(colors, intensity, accentTint, elevated) {
        LiquidGlassPanelPresets.card(
            colors = colors,
            intensity = intensity,
            accentTint = accentTint,
            elevated = elevated
        )
    }
    LiquidGlassPanelBox(
        spec = spec,
        modifier = modifier,
        shape = shape,
        onClick = onClick,
        onClickLabel = onClickLabel
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

@Composable
fun LiquidGlassPanelBox(
    spec: LiquidGlassPanelSpec,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(26.dp),
    role: Role = Role.Button,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = SpatialTheme.colors
    val geometry = SpatialTheme.geometry
    val reducedMotion = rememberReducedMotionEnabled()
    val haptic = rememberDuitHaptic()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressedActive = isPressed && onClick != null
    var touchPos by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Press) {
                touchPos = interaction.pressPosition
            }
        }
    }

    val scaleAnimSpec = if (spec.usesLiquidSpring) {
        if (pressedActive) LiquidGlassSprings.Press else LiquidGlassSprings.Release
    } else {
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
    }
    val scale by animateFloatAsState(
        targetValue = if (pressedActive && !reducedMotion) spec.pressScale else 1f,
        animationSpec = scaleAnimSpec,
        label = "lg_panel_scale"
    )
    val bloomProgress by animateFloatAsState(
        targetValue = if (pressedActive && !reducedMotion && spec.touchBloomAlpha > 0f) 1f else 0f,
        animationSpec = if (pressedActive) LiquidGlassSprings.Press else LiquidGlassSprings.Release,
        label = "lg_panel_bloom"
    )
    val fill by animateColorAsState(
        targetValue = spec.fillColor,
        animationSpec = prismaticTween(reducedMotion = reducedMotion, durationMillis = 150),
        label = "lg_panel_fill"
    )

    val borderBrush: Brush = remember(spec) {
        if (spec.rimHighlightAlpha <= 0.01f) {
            SolidColor(spec.borderColor)
        } else {
            Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = spec.rimHighlightAlpha),
                    spec.borderColor,
                    Color.White.copy(alpha = (spec.rimHighlightAlpha * 0.42f).coerceIn(0f, 0.50f))
                )
            )
        }
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (spec.shadowElevation > 0.dp) {
                    Modifier.shadow(
                        elevation = spec.shadowElevation,
                        shape = shape,
                        ambientColor = spec.shadowColor,
                        spotColor = spec.shadowColor,
                        clip = false
                    )
                } else {
                    Modifier
                }
            )
            .clip(shape)
            .background(fill)
            .drawBehind {
                if (spec.topSheenAlpha > 0.01f) {
                    // Upper optical catch-light sheen (clean frosted/liquid glass depth)
                    drawRect(
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.White.copy(alpha = spec.topSheenAlpha * 0.48f),
                                0.24f to Color.White.copy(alpha = spec.topSheenAlpha * 0.14f),
                                0.52f to Color.Transparent
                            )
                        )
                    )
                }
                if (spec.bottomReflectionAlpha > 0.01f) {
                    // Subtle lower internal reflection crease (TIR depth)
                    val bottomInk = if (spec.isDark) Color(0xFF04060B) else Color(0xFF0F172A)
                    drawRect(
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.72f to Color.Transparent,
                                1.0f to bottomInk.copy(alpha = spec.bottomReflectionAlpha)
                            )
                        )
                    )
                }
                if (bloomProgress > 0.01f && spec.touchBloomAlpha > 0.01f) {
                    val center = if (touchPos == Offset.Zero) Offset(size.width / 2f, size.height / 2f) else touchPos
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = spec.touchBloomAlpha * bloomProgress),
                                Color.Transparent
                            ),
                            center = center,
                            radius = size.maxDimension * 0.55f
                        ),
                        center = center,
                        radius = size.maxDimension * 0.55f
                    )
                }
            }
            .drawWithContent {
                drawContent()
                if (spec.chromaticGlintAlpha > 0.01f) {
                    // Controlled optical RGB chromatic edge glint along curved diagonal corners (HIGH only)
                    val outline = shape.createOutline(size, layoutDirection, this)
                    val a = spec.chromaticGlintAlpha
                    val prismBrush = Brush.linearGradient(
                        colorStops = arrayOf(
                            0.0f to Color(0xFF7BE8FF).copy(alpha = a),
                            0.18f to Color.White.copy(alpha = (a * 1.15f).coerceAtMost(0.65f)),
                            0.42f to Color.Transparent,
                            0.62f to Color.Transparent,
                            0.84f to Color(0xFFC49BFF).copy(alpha = a * 0.78f),
                            1.0f to Color(0xFF7CE8A8).copy(alpha = a * 0.72f)
                        ),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height)
                    )
                    drawOutline(
                        outline = outline,
                        brush = prismBrush,
                        style = Stroke(width = 1.25.dp.toPx())
                    )
                }
            }
            .border(BorderStroke(spec.borderWidth, borderBrush), shape)
            .then(
                if (onClick != null) {
                    Modifier
                        .defaultMinSize(minWidth = geometry.minTouchTarget, minHeight = geometry.minTouchTarget)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            role = role,
                            onClickLabel = onClickLabel,
                            onClick = {
                                haptic(HapticFeedbackType.TextHandleMove)
                                onClick()
                            }
                        )
                } else {
                    Modifier
                }
            )
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.primaryText) {
            content()
        }
    }
}

/**
 * Circular Liquid Glass icon orb button (e.g. Home Settings / Calendar header buttons).
 * Adapts cleanly across LOW (solid), MID (soft frosted), and HIGH (optical convex orb + chromatic rim).
 */
@Composable
fun LiquidGlassOrbButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    val colors = SpatialTheme.colors
    val intensity = LocalGlassIntensity.current
    val spec = remember(colors, intensity) {
        LiquidGlassPanelPresets.orbButton(colors, intensity)
    }

    LiquidGlassPanelBox(
        spec = spec,
        shape = CircleShape,
        onClick = onClick,
        onClickLabel = contentDescription,
        modifier = modifier
            .size(size)
            .semantics { this.contentDescription = contentDescription }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.primaryText,
            modifier = Modifier
                .align(Alignment.Center)
                .size(21.dp)
        )
    }
}

/**
 * Compact optical glass pill button (e.g. Home section actions `All bills`, `View all`)
 * preserving 48dp minimum touch target while adapting across LOW/MID/HIGH.
 */
@Composable
fun LiquidGlassActionPill(
    onClick: () -> Unit,
    onClickLabel: String,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val colors = SpatialTheme.colors
    val intensity = LocalGlassIntensity.current
    val spec = remember(colors, intensity) {
        LiquidGlassPanelPresets.orbButton(colors, intensity).copy(
            pressScale = if (intensity == GlassIntensity.SUBTLE) 0.96f else 1.03f
        )
    }

    LiquidGlassPanelBox(
        spec = spec,
        shape = RoundedCornerShape(50),
        onClick = onClick,
        onClickLabel = onClickLabel,
        modifier = modifier.heightIn(min = 48.dp)
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            content = content
        )
    }
}
