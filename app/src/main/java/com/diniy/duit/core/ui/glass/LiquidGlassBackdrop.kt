package com.diniy.duit.core.ui.glass

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.diniy.duit.core.ui.components.rememberReducedMotionEnabled
import kotlin.math.roundToInt

/**
 * Shared backdrop capture state connecting one scrollable content host
 * ([liquidGlassBackdropSource]) with floating Liquid Glass overlays ([LiquidGlassSurface]).
 *
 * Uses Compose hardware-backed [GraphicsLayer] (`RenderNode`) so no CPU bitmap copies
 * or DOM mirroring are required. Keep floating glass surfaces to <= 2 per screen.
 */
@Stable
class LiquidGlassBackdropState internal constructor(
    internal val backdropLayer: GraphicsLayer
) {
    internal var sourceBoundsInRoot by mutableStateOf(Rect.Zero)
    internal var hasRecordedBackdrop by mutableStateOf(false)
    var isScrolling by mutableStateOf(false)
    var qualityMode by mutableStateOf(LiquidGlassQualityMode.AUTO)
}

/**
 * Optional CompositionLocal supplying the active screen's [LiquidGlassBackdropState] to floating
 * chrome (bottom navigation & Quick Add FAB). `null` gracefully falls back to classic Candy Bubble.
 */
val LocalLiquidGlassBackdrop = compositionLocalOf<LiquidGlassBackdropState?> { null }

@Composable
fun rememberLiquidGlassBackdropState(
    initialQualityMode: LiquidGlassQualityMode = LiquidGlassQualityMode.AUTO
): LiquidGlassBackdropState {
    val layer = rememberGraphicsLayer()
    return remember(layer) {
        LiquidGlassBackdropState(layer).apply {
            qualityMode = initialQualityMode
        }
    }
}

/**
 * Marks a container (typically the screen's scrollable content) as the live optical backdrop
 * sampled by floating [LiquidGlassSurface] overlays positioned above it.
 */
fun Modifier.liquidGlassBackdropSource(state: LiquidGlassBackdropState): Modifier =
    this
        .onGloballyPositioned { coords ->
            state.sourceBoundsInRoot = coords.boundsInRoot()
        }
        .drawWithContent {
            if (state.qualityMode == LiquidGlassQualityMode.FALLBACK) {
                state.hasRecordedBackdrop = false
                drawContent()
            } else if (size.width >= 1f && size.height >= 1f) {
                state.backdropLayer.record(
                    size = IntSize(size.width.roundToInt(), size.height.roundToInt())
                ) {
                    this@drawWithContent.drawContent()
                }
                state.hasRecordedBackdrop = true
                drawLayer(state.backdropLayer)
            } else {
                drawContent()
            }
        }

/**
 * Records a container into [state] ONLY (without painting it to screen), used as an isolated
 * optical refraction source for the active chromatic bottom-nav lens.
 */
fun Modifier.liquidGlassHiddenBackdropSource(state: LiquidGlassBackdropState): Modifier =
    this
        .onGloballyPositioned { coords ->
            state.sourceBoundsInRoot = coords.boundsInRoot()
        }
        .drawWithContent {
            if (size.width >= 1f && size.height >= 1f) {
                state.backdropLayer.record(
                    size = IntSize(size.width.roundToInt(), size.height.roundToInt())
                ) {
                    this@drawWithContent.drawContent()
                }
                state.hasRecordedBackdrop = true
            }
        }

/**
 * Reusable native Android Liquid Glass surface.
 *
 * - API 33+ ([LiquidGlassTier.FullAgsl]): live GPU backdrop blur + AGSL bevel refraction,
 *   saturation lift, diagonal specular rim, touch bloom.
 * - API 31-32 ([LiquidGlassTier.FrostedRenderEffect]): live GPU backdrop blur + tint veil + rim.
 * - API 29-30 / Low-RAM ([LiquidGlassTier.OpticalFallback]): calibrated translucent frost veil + rim.
 */
@Composable
fun LiquidGlassSurface(
    backdropState: LiquidGlassBackdropState?,
    spec: LiquidGlassSurfaceSpec,
    modifier: Modifier = Modifier,
    qualityModeOverride: LiquidGlassQualityMode? = null,
    touchPosition: Offset = Offset.Zero,
    touchAlpha: Float = 0f,
    content: @Composable BoxScope.() -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val reducedMotion = rememberReducedMotionEnabled()
    val isLowRam = remember(context) { LiquidGlassCapabilities.isLowRam(context) }

    val effectiveQualityMode = qualityModeOverride ?: backdropState?.qualityMode ?: LiquidGlassQualityMode.AUTO
    val tier = LiquidGlassCapabilities.resolveTier(
        sdkInt = Build.VERSION.SDK_INT,
        isLowRamDevice = isLowRam,
        qualityMode = effectiveQualityMode
    )
    val isSolidMode = tier == LiquidGlassTier.OpticalFallback && spec.tintAlpha >= 0.99f && spec.rimAlpha <= 0.01f

    val targetRefract = LiquidGlassCapabilities.resolveRefractionFactor(
        tier = tier,
        isScrolling = backdropState?.isScrolling == true,
        reduceMotion = reducedMotion
    )
    val animatedRefract by animateFloatAsState(
        targetValue = targetRefract,
        animationSpec = tween(durationMillis = if (reducedMotion) 0 else 160),
        label = "lg_refract_governor"
    )

    val shape = RoundedCornerShape(spec.cornerRadius)
    val surfaceLayer = rememberGraphicsLayer()
    val shaderHandle = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) LiquidGlassShaderHandle() else null
    }
    var surfaceBoundsInRoot by remember { mutableStateOf(Rect.Zero) }

    val radiusPx = with(density) { spec.cornerRadius.toPx() }
    val bezelPx = with(density) { spec.bezel.toPx() }
    val thicknessPx = with(density) { spec.thickness.toPx() }
    val blurPx = with(density) { spec.blurRadius.toPx() }

    val borderBrush = remember(spec.rimColor, spec.rimAlpha, spec.borderColor, spec.isDark, tier, isSolidMode) {
        if (isSolidMode) {
            SolidColor(spec.borderColor)
        } else if (spec.isDark) {
            // Soft obsidian glass hairline (never a harsh white wireframe border)
            Brush.linearGradient(
                colors = listOf(
                    spec.rimColor.copy(alpha = (spec.rimAlpha * 0.42f).coerceIn(0f, 0.28f)),
                    spec.borderColor,
                    spec.rimColor.copy(alpha = (spec.rimAlpha * 0.18f).coerceIn(0f, 0.14f))
                )
            )
        } else {
            // Silky translucent Light glass hairline (never a dark black wire)
            Brush.linearGradient(
                colors = listOf(
                    spec.rimColor.copy(alpha = 0.86f),
                    spec.borderColor,
                    spec.rimColor.copy(alpha = 0.52f)
                )
            )
        }
    }
    val borderWidth = if (tier == LiquidGlassTier.FullAgsl) 0.5.dp else 1.dp
    val borderModifier = if (isSolidMode && spec.borderColor.alpha <= 0.001f) {
        Modifier
    } else {
        Modifier.border(BorderStroke(borderWidth, borderBrush), shape)
    }

    Box(
        modifier = modifier
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
            .onGloballyPositioned { coords ->
                surfaceBoundsInRoot = coords.boundsInRoot()
            }
            .drawBehind {
                if (isSolidMode) {
                    // LOW intensity: clean opaque solid UI — zero blur, refraction, gloss or shader cost
                    drawRect(color = spec.tintColor)
                    return@drawBehind
                }

                val canSampleBackdrop = backdropState != null &&
                    backdropState.hasRecordedBackdrop &&
                    size.width >= 2f &&
                    size.height >= 2f &&
                    surfaceBoundsInRoot != Rect.Zero

                if (canSampleBackdrop && tier == LiquidGlassTier.FullAgsl &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    shaderHandle != null
                ) {
                    val dx = surfaceBoundsInRoot.left - backdropState!!.sourceBoundsInRoot.left
                    val dy = surfaceBoundsInRoot.top - backdropState.sourceBoundsInRoot.top
                    surfaceLayer.record(
                        size = IntSize(size.width.roundToInt(), size.height.roundToInt())
                    ) {
                        translate(left = -dx, top = -dy) {
                            drawLayer(backdropState.backdropLayer)
                        }
                    }
                    surfaceLayer.renderEffect = shaderHandle.buildRenderEffect(
                        widthPx = size.width,
                        heightPx = size.height,
                        radiusPx = radiusPx,
                        bezelPx = bezelPx,
                        thicknessPx = thicknessPx,
                        blurPx = blurPx,
                        spec = spec,
                        refractFactor = animatedRefract,
                        touchPos = touchPosition,
                        touchAlpha = touchAlpha
                    ).asComposeRenderEffect()
                    drawLayer(surfaceLayer)
                } else if (canSampleBackdrop && tier == LiquidGlassTier.FrostedRenderEffect &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ) {
                    val dx = surfaceBoundsInRoot.left - backdropState!!.sourceBoundsInRoot.left
                    val dy = surfaceBoundsInRoot.top - backdropState.sourceBoundsInRoot.top
                    surfaceLayer.record(
                        size = IntSize(size.width.roundToInt(), size.height.roundToInt())
                    ) {
                        translate(left = -dx, top = -dy) {
                            drawLayer(backdropState.backdropLayer)
                        }
                    }
                    surfaceLayer.renderEffect = createBackdropBlurEffect(blurPx)?.asComposeRenderEffect()
                    drawLayer(surfaceLayer)
                    // Candy tint veil + top inner gloss
                    drawRect(color = spec.tintColor.copy(alpha = (spec.tintAlpha + 0.06f).coerceIn(0f, 0.92f)))
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                spec.rimColor.copy(alpha = spec.rimAlpha * 0.24f),
                                Color.Transparent
                            )
                        )
                    )
                } else {
                    // API 29-30 / Low-RAM optical frost fallback (zero shader cost, prevents unblurred text ghosting)
                    val fallbackAlpha = if (spec.kind == LiquidGlassKind.Lens) {
                        0.92f
                    } else {
                        0.96f
                    }
                    drawRect(color = spec.tintColor.copy(alpha = fallbackAlpha))
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                spec.rimColor.copy(alpha = spec.rimAlpha * 0.32f),
                                Color.Transparent,
                                spec.shadowColor.copy(alpha = 0.08f)
                            )
                        )
                    )
                }

                // Touch bloom for non-AGSL tiers
                if (tier != LiquidGlassTier.FullAgsl && touchAlpha > 0.01f) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                spec.rimColor.copy(alpha = 0.26f * touchAlpha),
                                Color.Transparent
                            ),
                            center = touchPosition,
                            radius = size.maxDimension * 0.45f
                        ),
                        center = touchPosition,
                        radius = size.maxDimension * 0.45f
                    )
                }
            }
            .then(borderModifier),
        content = content
    )
}
