package com.diniy.duit.core.ui.glass

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.diniy.duit.core.ui.components.CandyTone
import com.diniy.duit.core.ui.theme.PrismaticPalette
import com.diniy.duit.core.ui.theme.SpatialColorTokens
import com.diniy.duit.domain.model.GlassIntensity
import kotlin.math.PI

/**
 * Hardware capability tiers for native Android Liquid Glass rendering (Phase 10A).
 *
 * - [FullAgsl]: API 33+ GPU [android.graphics.RuntimeShader] (Snell bevel refraction,
 *   saturation boost, optional chromatic dispersion, diagonal specular rim, touch bloom)
 *   chained over [android.graphics.RenderEffect.createBlurEffect].
 * - [FrostedRenderEffect]: API 31-32 native [android.graphics.RenderEffect] backdrop blur
 *   paired with Canvas-rendered specular rim and Candy Bubble tint veil.
 * - [OpticalFallback]: API 29-30 (and low-RAM / reduced-effect mode) translucent Candy frost
 *   veil with inner luminance gradient and crisp specular rim (zero GPU shader cost).
 */
enum class LiquidGlassTier {
    FullAgsl,
    FrostedRenderEffect,
    OpticalFallback
}

/**
 * Runtime quality override (mirrors zakisheriff/Liquid-Glass `quality.ts` governor).
 */
enum class LiquidGlassQualityMode {
    AUTO,
    FULL,
    FROSTED,
    FALLBACK
}

/**
 * Optical mode ported from `zakisheriff/Liquid-Glass` (`refraction.ts`).
 * - [Surface]: bars & cards — inward bevel refraction, frosted panel, zero chromatic split.
 * - [Lens]: active floating droplet/pill — outward rim bend, subtle magnification, soft RGB dispersion.
 */
enum class LiquidGlassKind {
    Surface,
    Lens
}

@Immutable
data class LiquidGlassSurfaceSpec(
    val kind: LiquidGlassKind,
    val cornerRadius: Dp,
    val bezel: Dp,
    val thickness: Dp,
    val blurRadius: Dp,
    val ior: Float = 1.5f,
    val zoom: Float = 1.0f,
    val stretch: Float = 0f,
    val dispersion: Float = 0f,
    val saturation: Float = 1.25f,
    val tintColor: Color,
    val tintAlpha: Float,
    val rimColor: Color,
    val rimAlpha: Float,
    val borderColor: Color,
    val shadowColor: Color,
    val shadowElevation: Dp = 10.dp,
    val isDark: Boolean = false
)

object LiquidGlassCapabilities {

    /**
     * Maps the persisted [GlassIntensity] preference to Liquid Glass rendering tiers:
     * - [GlassIntensity.SUBTLE]: reversible classic Candy Bubble fallback (zero GPU shader cost).
     * - [GlassIntensity.BALANCED]: auto hardware tier + scroll governor.
     * - [GlassIntensity.RICH]: full hardware tier with stronger refraction & specular rim.
     */
    fun qualityModeFor(intensity: GlassIntensity): LiquidGlassQualityMode = when (intensity) {
        GlassIntensity.SUBTLE -> LiquidGlassQualityMode.FALLBACK
        GlassIntensity.BALANCED -> LiquidGlassQualityMode.AUTO
        GlassIntensity.RICH -> LiquidGlassQualityMode.FULL
    }

    fun resolveTier(
        sdkInt: Int = Build.VERSION.SDK_INT,
        isLowRamDevice: Boolean = false,
        qualityMode: LiquidGlassQualityMode = LiquidGlassQualityMode.AUTO
    ): LiquidGlassTier {
        val hardwareMax = when {
            sdkInt >= Build.VERSION_CODES.TIRAMISU -> LiquidGlassTier.FullAgsl
            sdkInt >= Build.VERSION_CODES.S -> LiquidGlassTier.FrostedRenderEffect
            else -> LiquidGlassTier.OpticalFallback
        }
        return when (qualityMode) {
            LiquidGlassQualityMode.FALLBACK -> LiquidGlassTier.OpticalFallback
            LiquidGlassQualityMode.FROSTED ->
                if (hardwareMax == LiquidGlassTier.OpticalFallback) LiquidGlassTier.OpticalFallback
                else LiquidGlassTier.FrostedRenderEffect
            LiquidGlassQualityMode.FULL -> hardwareMax
            LiquidGlassQualityMode.AUTO ->
                if (isLowRamDevice) LiquidGlassTier.OpticalFallback else hardwareMax
        }
    }

    fun isLowRam(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        return am?.isLowRamDevice == true
    }

    /**
     * Smooth refraction factor for the scroll governor (mirrors `quality.ts` scrolling pause).
     * While scrolling or in low-RAM mode, AGSL refraction smoothly attenuates while blur + rim remain.
     */
    fun resolveRefractionFactor(
        tier: LiquidGlassTier,
        isScrolling: Boolean,
        reduceMotion: Boolean
    ): Float = when {
        tier != LiquidGlassTier.FullAgsl -> 0f
        reduceMotion -> 0.40f
        // Keep optical refraction visibly alive during scroll on API 33+ GPUs while still
        // gently smoothing peak displacement under high fling velocity.
        isScrolling -> 0.72f
        else -> 1f
    }
}

/**
 * Damped harmonic spring presets mathematically ported from `zakisheriff/Liquid-Glass`
 * (`src/engine/spring.ts`: `springFrom(response, dampingFraction)` -> `k = (2*PI/r)^2`).
 */
object LiquidGlassSprings {

    private fun fromResponse(responseSec: Float, dampingRatio: Float): SpringSpec<Float> {
        val omega = (2.0 * PI / responseSec).toFloat()
        return spring(dampingRatio = dampingRatio, stiffness = omega * omega)
    }

    /** Finger down: quick, crisp, near-critically damped. */
    val Press: SpringSpec<Float> = fromResponse(0.22f, 0.86f)

    /** Finger up: soft settle with gentle jelly rebound. */
    val Release: SpringSpec<Float> = fromResponse(0.42f, 0.56f)

    /** Active finger tracking during horizontal tab drag. */
    val Drag: SpringSpec<Float> = fromResponse(0.12f, 0.92f)

    /** Tab lens gliding into its snapped slot. */
    val Snap: SpringSpec<Float> = fromResponse(0.36f, 0.72f)

    /** Lens inflating out of resting active pill on touch/drag. */
    val Lift: SpringSpec<Float> = fromResponse(0.26f, 0.78f)

    /** Lens deflating back into resting pill. */
    val Settle: SpringSpec<Float> = fromResponse(0.42f, 0.52f)

    /** Velocity-driven anisotropic jelly stretch/squash. */
    val Jelly: SpringSpec<Float> = fromResponse(0.30f, 0.36f)
}

/**
 * Pure optical Liquid Glass presets faithfully ported from `zakisheriff/Liquid-Glass` (`base.css`).
 * Zero Candy Bubble cream/coral forced into glass: crisp cool white glass in Light,
 * deep neutral charcoal glass in Dark, clear refracting droplet lens, diagonal specular rims.
 */
object LiquidGlassPresets {

    fun floatingNavBar(
        colors: SpatialColorTokens,
        intensity: GlassIntensity = GlassIntensity.BALANCED
    ): LiquidGlassSurfaceSpec {
        val rich = intensity == GlassIntensity.RICH
        return if (colors.isDark) {
            LiquidGlassSurfaceSpec(
                kind = LiquidGlassKind.Surface,
                cornerRadius = 32.dp,
                bezel = if (rich) 18.dp else 16.dp,
                thickness = if (rich) 30.dp else 26.dp,
                blurRadius = if (rich) 10.dp else 8.dp,
                ior = 1.52f,
                saturation = if (rich) 1.80f else 1.65f,
                tintColor = Color(0xFF0E1117),
                tintAlpha = if (rich) 0.36f else 0.44f,
                rimColor = Color.White,
                rimAlpha = if (rich) 0.46f else 0.36f,
                borderColor = Color(0x12FFFFFF),
                shadowColor = Color.Black.copy(alpha = 0.50f),
                shadowElevation = 14.dp,
                isDark = true
            )
        } else {
            LiquidGlassSurfaceSpec(
                kind = LiquidGlassKind.Surface,
                cornerRadius = 32.dp,
                bezel = if (rich) 18.dp else 16.dp,
                thickness = if (rich) 30.dp else 26.dp,
                blurRadius = if (rich) 9.dp else 7.dp,
                ior = 1.52f,
                saturation = if (rich) 2.00f else 1.85f,
                tintColor = Color(0xFFF8FAFF),
                tintAlpha = if (rich) 0.44f else 0.52f,
                rimColor = Color.White,
                rimAlpha = 0.88f,
                borderColor = Color(0x100F172A),
                shadowColor = Color(0x180F172A),
                shadowElevation = 12.dp,
                isDark = false
            )
        }
    }

    /**
     * Smooth translucent Liquid Glass active tab lens (restored clean `p10b2` optics).
     * Slides cleanly behind crisp foreground icons/labels with soft specular edge (zero harsh
     * outlines, zero unintended white sticker pill, zero blue glyph artifacts).
     */
    fun floatingNavLens(
        colors: SpatialColorTokens,
        intensity: GlassIntensity = GlassIntensity.BALANCED,
        motionBoost: Float = 0f
    ): LiquidGlassSurfaceSpec {
        val rich = intensity == GlassIntensity.RICH
        val boost = motionBoost.coerceIn(0f, 1f)
        return if (colors.isDark) {
            LiquidGlassSurfaceSpec(
                kind = LiquidGlassKind.Lens,
                cornerRadius = 24.dp,
                bezel = if (rich) 16.dp else 14.dp,
                thickness = if (rich) 28.dp else 24.dp,
                blurRadius = 4.dp,
                ior = 1.52f,
                zoom = (if (rich) 1.12f else 1.09f) + 0.04f * boost,
                dispersion = 0f,
                saturation = 1.70f,
                tintColor = Color(0xFF1C2333),
                tintAlpha = if (rich) 0.28f else 0.34f,
                rimColor = Color.White,
                rimAlpha = if (rich) 0.54f else 0.44f,
                borderColor = Color(0x1AFFFFFF),
                shadowColor = Color.Black.copy(alpha = 0.36f),
                shadowElevation = 6.dp,
                isDark = true
            )
        } else {
            LiquidGlassSurfaceSpec(
                kind = LiquidGlassKind.Lens,
                cornerRadius = 24.dp,
                bezel = if (rich) 16.dp else 14.dp,
                thickness = if (rich) 28.dp else 24.dp,
                blurRadius = 5.dp,
                ior = 1.52f,
                zoom = (if (rich) 1.12f else 1.09f) + 0.04f * boost,
                dispersion = 0f,
                saturation = 1.85f,
                // Subtle cool optical tint (avoids flat white sticker pill look on Light canvas)
                tintColor = Color(0xFFEAF2FF),
                tintAlpha = if (rich) 0.34f else 0.40f,
                rimColor = Color.White,
                rimAlpha = 0.86f,
                borderColor = Color(0x120F172A),
                shadowColor = Color(0x140F172A),
                shadowElevation = 5.dp,
                isDark = false
            )
        }
    }

    /**
     * Detached optical Liquid Glass Quick Add orb docked beside the floating bottom bar.
     */
    fun quickAddFab(
        colors: SpatialColorTokens,
        intensity: GlassIntensity = GlassIntensity.BALANCED
    ): LiquidGlassSurfaceSpec {
        val rich = intensity == GlassIntensity.RICH
        return if (colors.isDark) {
            LiquidGlassSurfaceSpec(
                kind = LiquidGlassKind.Lens,
                cornerRadius = 28.dp,
                bezel = if (rich) 17.dp else 15.dp,
                thickness = if (rich) 30.dp else 26.dp,
                blurRadius = if (rich) 5.dp else 6.dp,
                ior = 1.52f,
                zoom = if (rich) 1.12f else 1.09f,
                dispersion = 0f,
                saturation = 1.70f,
                tintColor = Color(0xFF111622),
                tintAlpha = if (rich) 0.38f else 0.44f,
                rimColor = Color.White,
                rimAlpha = if (rich) 0.52f else 0.44f,
                borderColor = Color(0x16FFFFFF),
                shadowColor = Color.Black.copy(alpha = 0.52f),
                shadowElevation = 14.dp,
                isDark = true
            )
        } else {
            LiquidGlassSurfaceSpec(
                kind = LiquidGlassKind.Lens,
                cornerRadius = 28.dp,
                bezel = if (rich) 17.dp else 15.dp,
                thickness = if (rich) 30.dp else 26.dp,
                blurRadius = if (rich) 5.dp else 6.dp,
                ior = 1.52f,
                zoom = if (rich) 1.12f else 1.09f,
                dispersion = 0f,
                saturation = 1.90f,
                tintColor = Color(0xFFF6FAFF),
                tintAlpha = if (rich) 0.46f else 0.54f,
                rimColor = Color.White,
                rimAlpha = 0.90f,
                borderColor = Color(0x120F172A),
                shadowColor = Color(0x1C0F172A),
                shadowElevation = 12.dp,
                isDark = false
            )
        }
    }

    fun heroCard(colors: SpatialColorTokens, tone: CandyTone): LiquidGlassSurfaceSpec {
        val accent = when (tone) {
            CandyTone.Coral -> colors.coral
            CandyTone.Mint -> colors.aqua
            CandyTone.Sunny -> colors.sunny
            CandyTone.Peach -> colors.peach
            CandyTone.Sky -> colors.skyBlue
            CandyTone.Neutral -> if (colors.isDark) PrismaticPalette.DarkGlassElevated else Color.White
        }
        return if (colors.isDark) {
            val blendedTint = androidx.compose.ui.graphics.lerp(PrismaticPalette.DarkGlassBase, accent, 0.24f)
            LiquidGlassSurfaceSpec(
                kind = LiquidGlassKind.Surface,
                cornerRadius = 28.dp,
                bezel = 18.dp,
                thickness = 24.dp,
                blurRadius = 18.dp,
                ior = 1.50f,
                saturation = 1.25f,
                tintColor = blendedTint,
                tintAlpha = 0.66f,
                rimColor = PrismaticPalette.Cream,
                rimAlpha = 0.56f,
                borderColor = Color.White.copy(alpha = 0.16f),
                shadowColor = Color.Black.copy(alpha = 0.42f),
                shadowElevation = 14.dp
            )
        } else {
            val blendedTint = androidx.compose.ui.graphics.lerp(Color(0xFFFFFDF9), accent, 0.22f)
            LiquidGlassSurfaceSpec(
                kind = LiquidGlassKind.Surface,
                cornerRadius = 28.dp,
                bezel = 18.dp,
                thickness = 24.dp,
                blurRadius = 16.dp,
                ior = 1.50f,
                saturation = 1.35f,
                tintColor = blendedTint,
                tintAlpha = 0.68f,
                rimColor = Color.White,
                rimAlpha = 0.94f,
                borderColor = PrismaticPalette.DeepTeal.copy(alpha = 0.12f),
                shadowColor = PrismaticPalette.DeepTeal.copy(alpha = 0.14f),
                shadowElevation = 12.dp
            )
        }
    }
}
